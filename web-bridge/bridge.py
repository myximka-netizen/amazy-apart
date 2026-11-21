"""Single-account pilot. Browser profile and delivery ledger never leave /data."""
import asyncio
import hashlib
import hmac
import json
import os
import sqlite3
import sys
from contextlib import asynccontextmanager
from pathlib import Path
from urllib.parse import urlsplit
import urllib.request
import httpx
from fastapi import FastAPI, HTTPException, Request
from playwright.async_api import async_playwright

CONFIG = Path("/config/config.json")
DATA = Path("/data")
ACCOUNT = int(os.getenv("CHATWOOT_ACCOUNT_ID", "2"))
INBOX = int(os.getenv("CHATWOOT_INBOX_ID", "0"))
SECRET = os.getenv("WEB_BRIDGE_SECRET", "")
TOKEN = os.getenv("CHATWOOT_API_TOKEN", "")
BASE = os.getenv("CHATWOOT_URL", "http://rails:3000").rstrip("/")
ORIGIN = "extranet.sutochno.ru"
state = {"mode": "starting", "status": "starting"}
ui = None

def safe_url(url):
    p = urlsplit(url)
    return p.scheme == "https" and p.hostname == ORIGIN and p.port in (None, 443) and not p.username and not p.password

def key(*parts):
    return hashlib.sha256(json.dumps(parts, ensure_ascii=False).encode()).hexdigest()

def db():
    c = sqlite3.connect(DATA / "ledger.sqlite3")
    c.row_factory = sqlite3.Row
    return c

def init_db():
    with db() as c:
        c.executescript("""
        CREATE TABLE IF NOT EXISTS threads (url TEXT PRIMARY KEY, cw_id INTEGER UNIQUE, baseline INTEGER DEFAULT 0);
        CREATE TABLE IF NOT EXISTS inbound (thread TEXT, external_id TEXT, status TEXT, PRIMARY KEY(thread,external_id));
        CREATE TABLE IF NOT EXISTS outbound (id INTEGER PRIMARY KEY, cw_id INTEGER, content TEXT, status TEXT);
        """)
        c.execute("UPDATE outbound SET status='uncertain' WHERE status='sending'")
        c.execute("UPDATE inbound SET status='uncertain' WHERE status='sending'")

def outgoing_event(p):
    conv = p.get("conversation") or {}
    account = p.get("account") or {}
    inbox = p.get("inbox") or {}
    return (p.get("event") == "message_created" and p.get("message_type") in ("outgoing", 1)
            and p.get("private") is False and account.get("id") == ACCOUNT
            and inbox.get("id", conv.get("inbox_id")) == INBOX
            and isinstance(conv.get("id"), int) and isinstance(p.get("id"), int))

def validate(cfg):
    if cfg.get("adapter") != "sutochno" or cfg.get("mode") not in ("inspect","read","send"):
        raise ValueError("invalid adapter/mode")
    if not safe_url(cfg["start_url"]):
        raise ValueError("invalid start URL")
    if cfg["mode"] == "inspect":
        return
    if not SECRET or not TOKEN or INBOX <= 0:
        raise ValueError("missing Chatwoot settings")
    urls = cfg.get("thread_urls", [])
    if not urls or len(urls) != len(set(urls)) or not all(safe_url(u) for u in urls):
        raise ValueError("explicit unique pilot thread URLs required")
    if len(urls) > 10:
        raise ValueError("pilot limit is ten threads")
    required = ["ready","thread_title","message","message_id_attribute","message_text","incoming","outgoing"]
    if cfg["mode"] == "send":
        required += ["composer","send_button"]
    if any(not cfg["selectors"].get(x) for x in required):
        raise ValueError("selectors not calibrated")

class SutochnoAdapter:
    def __init__(self,cfg):
        self.s = cfg["selectors"]

    async def open_thread(self,page,url):
        if not safe_url(url):
            raise ValueError("foreign URL")
        await page.goto(url, wait_until="domcontentloaded", timeout=30000)
        await page.locator(self.s["ready"]).wait_for(state="visible",timeout=12000)
        if page.url != url:
            raise ValueError("thread URL changed; check login/canonical URL")

    async def snapshot(self,page):
        rows = page.locator(self.s["message"])
        n = await rows.count()
        if n == 0:
            raise ValueError("empty transcript; refusing baseline")
        result = []
        seen = set()
        for i in range(n):
            row = rows.nth(i)
            mid = await row.get_attribute(self.s["message_id_attribute"])
            # Incoming/outgoing selectors are CSS selectors matching the message row itself.
            a = await row.evaluate("(e,s)=>e.matches(s)",self.s["incoming"])
            b = await row.evaluate("(e,s)=>e.matches(s)",self.s["outgoing"])
            if not mid or mid in seen or a == b:
                raise ValueError("ambiguous message identity/direction")
            seen.add(mid)
            text = (await row.locator(self.s["message_text"]).inner_text()).strip()
            result.append((mid,"incoming" if a else "outgoing",text))
        return result

    async def send(self,page,url,content):
        await self.open_thread(page,url)
        before = {m[0] for m in await self.snapshot(page)}
        box = page.locator(self.s["composer"])
        await box.fill(content)
        if page.url != url:
            raise ValueError("thread changed")
        await page.locator(self.s["send_button"]).click(timeout=10000)
        for _ in range(10):
            await asyncio.sleep(1)
            if page.url != url:
                break
            if any(i not in before and d == "outgoing" and t == content for i,d,t in await self.snapshot(page)):
                return
        raise RuntimeError("delivery not confirmed")

class Chatwoot:
    async def post(self,path,data):
        async with httpx.AsyncClient(timeout=20,trust_env=False) as client:
            res = await client.post(f"{BASE}/api/v1/accounts/{ACCOUNT}/{path}",json=data,headers={"api_access_token":TOKEN})
            res.raise_for_status()
            return res.json()

    async def conversation(self,url,name):
        with db() as c:
            row = c.execute("SELECT cw_id FROM threads WHERE url=?",(url,)).fetchone()
            if row and row["cw_id"]:
                return row["cw_id"]
            # NULL without a baseline is an unresolved creation attempt; never blindly create another.
            if row:
                raise RuntimeError("conversation creation needs manual reconciliation")
            c.execute("INSERT INTO threads(url,cw_id) VALUES (?,NULL)",(url,))
        r = await self.post("contacts",{"inbox_id":INBOX,"name":name,"identifier":"sutochno-"+key(url)})
        contact = r.get("payload",r)
        contact = contact.get("contact",contact)
        inbox = next(x for x in contact["contact_inboxes"] if x["inbox"]["id"] == INBOX)
        r = await self.post("conversations",{"source_id":inbox["source_id"],"inbox_id":INBOX,"contact_id":contact["id"],"status":"open"})
        cid = int(r["id"])
        with db() as c:
            c.execute("UPDATE threads SET cw_id=? WHERE url=?",(cid,url))
        return cid

    async def incoming(self,cid,content):
        await self.post(f"conversations/{cid}/messages",{"content":content,"message_type":"incoming","private":False})

async def scan(page,adapter,cfg,cw,url):
    await adapter.open_thread(page,url)
    messages = await adapter.snapshot(page)
    with db() as c:
        row = c.execute("SELECT cw_id,baseline FROM threads WHERE url=?",(url,)).fetchone()
        if row and row["cw_id"] is None and not row["baseline"]:
            raise RuntimeError("unresolved contact creation")
        if row is None and not cfg.get("import_existing",False):
            # Create a local baseline without creating a Chatwoot conversation yet.
            c.execute("INSERT INTO threads(url,cw_id,baseline) VALUES (?,NULL,1)",(url,))
            for mid,d,t in messages:
                if d == "incoming":
                    c.execute("INSERT OR IGNORE INTO inbound VALUES (?,?,?)",(url,mid,"baseline"))
            return
    for mid,d,t in messages:
        if d != "incoming" or not t:
            continue
        with db() as c:
            if c.execute("SELECT 1 FROM inbound WHERE thread=? AND external_id=?",(url,mid)).fetchone():
                continue
        name = (await page.locator(adapter.s["thread_title"]).inner_text()).strip() or "Гость Суточно.ру"
        # A baseline with no CW mapping is promoted exactly once.
        with db() as c:
            c.execute("DELETE FROM threads WHERE url=? AND cw_id IS NULL AND baseline=1",(url,))
        cid = await cw.conversation(url,name)
        with db() as c:
            c.execute("INSERT INTO inbound VALUES (?,?,?)",(url,mid,"sending"))
        try:
            await cw.incoming(cid,t)
        except Exception:
            with db() as c:
                c.execute("UPDATE inbound SET status='uncertain' WHERE thread=? AND external_id=?",(url,mid))
            raise
        with db() as c:
            c.execute("UPDATE inbound SET status='sent' WHERE thread=? AND external_id=?",(url,mid))

async def drain(page,adapter,cfg):
    with db() as c:
        rows = c.execute("SELECT o.id,o.content,t.url FROM outbound o JOIN threads t ON t.cw_id=o.cw_id WHERE o.status='queued' ORDER BY o.id LIMIT 10").fetchall()
    for row in rows:
        if row["url"] not in cfg["thread_urls"]:
            continue
        with db() as c:
            c.execute("UPDATE outbound SET status='sending' WHERE id=?",(row["id"],))
        try:
            await adapter.send(page,row["url"],row["content"])
        except Exception:
            status = "uncertain"
        else:
            status = "sent"
        with db() as c:
            c.execute("UPDATE outbound SET status=? WHERE id=?",(status,row["id"]))
        if status == "uncertain":
            raise RuntimeError("delivery needs manual review")

async def worker(cfg):
    global ui
    async with async_playwright() as pw:
        context = await pw.chromium.launch_persistent_context(str(DATA/"chromium"),headless=False,viewport=None, args=["--no-sandbox"])
        try:
            ui = context.pages[0] if context.pages else await context.new_page()
            try:
                await ui.goto(cfg["start_url"],wait_until="domcontentloaded",timeout=30000)
            except Exception:
                pass
            if cfg["mode"] == "inspect":
                state["status"] = "manual_login"
                await asyncio.Event().wait()
            adapter = SutochnoAdapter(cfg)
            page = await context.new_page()
            cw = Chatwoot()
            while True:
                try:
                    for url in cfg["thread_urls"]:
                        await scan(page,adapter,cfg,cw,url)
                    if cfg["mode"] == "send":
                        with db() as c:
                            if c.execute("SELECT 1 FROM outbound WHERE status='uncertain' LIMIT 1").fetchone():
                                raise RuntimeError("unresolved outgoing delivery")
                        await drain(page,adapter,cfg)
                    state["status"] = "running"
                except Exception as e:
                    state["status"] = "paused:"+type(e).__name__
                await asyncio.sleep(max(20,int(cfg.get("poll_seconds",30))))
        finally:
            await context.close()

@asynccontextmanager
async def lifespan(app):
    DATA.mkdir(exist_ok=True)
    init_db()
    cfg = json.loads(CONFIG.read_text())
    validate(cfg)
    app.state.cfg = cfg
    state["mode"] = cfg["mode"]
    task = asyncio.create_task(worker(cfg))
    def stopped(t):
        if not t.cancelled():
            state["status"] = "browser_stopped"
    task.add_done_callback(stopped)
    try:
        yield
    finally:
        task.cancel()
        await asyncio.gather(task,return_exceptions=True)

app=FastAPI(lifespan=lifespan,docs_url=None,redoc_url=None,openapi_url=None)

@app.get("/health")
async def health():
    return {"service":"web-bridge"}

def auth(token):
    if not SECRET or not hmac.compare_digest(SECRET,token):
        raise HTTPException(403)

@app.get("/status")
async def status(request:Request):
    auth(request.headers.get("x-bridge-secret",""))
    with db() as c:
        counts = {t:{r[0]:r[1] for r in c.execute(f"SELECT status,count(*) FROM {t} GROUP BY status")} for t in ("inbound","outbound")}
    return {**state,"counts":counts,"account":ACCOUNT,"inbox":INBOX}

@app.get("/probe")
async def probe(request:Request):
    auth(request.headers.get("x-bridge-secret",""))
    if ui is None or ui.is_closed() or not safe_url(ui.url):
        raise HTTPException(409,"Open the host extranet in the browser first")
    # No text, actual hrefs, input values, screenshots, cookies, IDs or HTML are returned.
    return await ui.evaluate("""() => {
      const out = new Map();
      for(const e of document.querySelectorAll('a,button,input,textarea,[contenteditable],[role],[data-testid],div,li,article,section,span')){
        if(!e.getClientRects().length) continue;
        const clean = s => (s||'').split(/\\s+/).filter(x=>/^[A-Za-z_-][A-Za-z0-9_-]{0,55}$/.test(x)&&!/[0-9]{3}/.test(x)).slice(0,5);
        const x={tag:e.tagName.toLowerCase(),classes:clean(typeof e.className==='string'?e.className:''),role:clean(e.getAttribute('role')),testid:clean(e.getAttribute('data-testid')),data_attribute_names:[...e.attributes].map(a=>a.name).filter(a=>a.startsWith('data-')).slice(0,12),editable:e.hasAttribute('contenteditable'),href:e.hasAttribute('href')};
        const k=JSON.stringify(x); out.set(k,{...x,count:(out.get(k)?.count||0)+1});
      }
      return [...out.values()].slice(0,500);
    }""")

@app.post("/hook/{token}")
async def hook(token:str,request:Request):
    auth(token)
    p=await request.json()
    if not outgoing_event(p):
        return {"ignored":True}
    if request.app.state.cfg["mode"] != "send":
        return {"ignored":"sending_disabled"}
    if p.get("attachments") or not isinstance(p.get("content"),str) or not p["content"].strip():
        return {"ignored":"text_only"}
    cid=p["conversation"]["id"]
    with db() as c:
        row=c.execute("SELECT url FROM threads WHERE cw_id=?",(cid,)).fetchone()
        if not row or row["url"] not in request.app.state.cfg["thread_urls"]:
            return {"ignored":"unmapped"}
        c.execute("INSERT OR IGNORE INTO outbound VALUES (?,?,?,?)",(p["id"],cid,p["content"].strip(),"queued"))
    return {"queued":True}

if __name__=="__main__":
    op=sys.argv[1] if len(sys.argv)>1 else "status"
    if op not in ("status","probe"):
        raise SystemExit("Use status or probe")
    req=urllib.request.Request("http://127.0.0.1:8080/"+op,headers={"x-bridge-secret":SECRET})
    with urllib.request.urlopen(req,timeout=20) as r:
        print(json.dumps(json.load(r),ensure_ascii=False,indent=2))
