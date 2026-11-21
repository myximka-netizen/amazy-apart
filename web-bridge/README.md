# Web Bridge pilot
The first adapter is for the host extranet of Sutochno.ru. Nothing is sent or imported in the default inspect mode. Obtain permission for automation and comply with the platform's messaging rules; stop on CAPTCHA or access restrictions.

## Install on the Chatwoot Windows host
Run install.ps1 from the existing Compose project directory. It downloads only this module and preserves local config and credentials.
Use your existing Compose flags (including existing overrides) and append -f web-bridge/compose.web-bridge.yaml. Example for a base named docker-compose.yaml:
```powershell
docker compose -f docker-compose.yaml -f web-bridge/compose.web-bridge.yaml up -d --no-deps --build web-bridge
docker compose -f docker-compose.yaml -f web-bridge/compose.web-bridge.yaml exec web-bridge python bridge.py status
```
Open http://127.0.0.1:6080/vnc.html, click Connect, use the local VNC password, sign in manually, open Messages and a thread. Do not send credentials, cookies or tokens to anybody. VNC is loopback only; never expose it via a tunnel.
Run the same compose exec command with probe instead of status. Probe prints structural metadata only, but review it for private data before sharing. No static selectors have been guessed. If your browser opens a new tab, use the original tab for the probe.
 
## Activation after calibration
In Chatwoot create an API inbox with callback http://web-bridge:8080/hook/SECRET (SECRET is the local WEB_BRIDGE_SECRET). Put the inbox ID and your Chatwoot API token into .env.local, never Git. Disable auto-assignment if desired. Add yourself as an inbox member.
Set config.local.json using actual selectors and canonical URLs for up to ten explicit pilot threads. Message selectors must uniquely identify rows; message_id_attribute is the stable ID attribute on each row. incoming/outgoing are CSS selectors matching the row itself; message_text is a unique child. ready and thread_title must be unique. No automatic enumeration yet. Confirm rules and use an authorized test thread. Set mode read first (default import_existing=false baselines currently visible history), then send only after read verification. Restart with the same Compose command plus --force-recreate. Text only, no attachments or historical outgoing sync. Send mode has no automatic AI replies; private notes are ignored. Disable any existing AI auto-send rules on the test inbox.

## Delivery
Persistent SQLite deduplicates website message IDs and Chatwoot outgoing IDs. No blind retry after uncertain delivery; inspect the website and reconcile the ledger before re-enabling outgoing. A message typed in Chatwoot may appear sent there even if delivery has not been confirmed by the bridge; inspect status counts. The baseline covers only rendered messages, and pagination/virtual scrolling is not implemented. Opening threads may mark messages read on the original website. Chatwoot callback is internal only, protected by a random secret path; prevent logging it. Profile and message ledger are local sensitive data, back up securely. One browser/account per service. Do not run untrusted pages in the Chromium container (sandbox disabled in this pilot).
