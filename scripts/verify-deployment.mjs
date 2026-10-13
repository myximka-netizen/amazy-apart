// Read-only production gate. A successful build does not verify hosting settings.
import assert from 'node:assert/strict';

const input = process.argv[2];
if (!input || !/^https?:\/\//.test(input)) {
  console.error('Usage: npm run verify:deployment -- https://amazy-apart.ru [--check-hosts]');
  process.exit(1);
}
const base = new URL(input).origin;
const canonicalOrigin = 'https://amazy-apart.ru';
const checkHosts = process.argv.includes('--check-hosts');
let failures = 0;
async function request(path) {
  return fetch(new URL(path, base), { redirect: 'manual', signal: AbortSignal.timeout(20000) });
}
async function check(name, run) {
  try { await run(); console.log('PASS ' + name); }
  catch (error) { failures++; console.error('FAIL ' + name + ': ' + error.message); }
}
function canonical(html) {
  const tags = html.match(/<link\b[^>]*>/gi) || [];
  return tags.filter(tag => /\brel=["']canonical["']/i.test(tag)).map(tag => tag.match(/\bhref=["']([^"']+)["']/i)?.[1]);
}
const pages = ['/', '/apartments/', '/contacts/', '/owners/', '/apartment/1/', '/apartments/dinamo/', '/en/apartments/', '/zh/contacts/'];
// A small number of concurrent requests keeps the check suitable for production.
for (let offset = 0; offset < pages.length; offset += 4) {
  await Promise.all(pages.slice(offset, offset + 4).map(path => check(path, async () => {
    const response = await request(path);
    assert.equal(response.status, 200, 'expected HTTP 200');
    const html = await response.text();
    assert.deepEqual(canonical(html), [canonicalOrigin + path], 'self canonical in initial HTML');
    assert.match(html, /<h1\b[^>]*>.+?<\/h1>/s, 'prerendered heading');
    assert.doesNotMatch(html, /<meta\b[^>]*name=["']robots["'][^>]*content=["'][^"']*noindex/i);
  })));
}
for (const path of ['/apartments', '/en', '/zh/contacts']) {
  await check(path + ' preserves query on permanent redirect', async () => {
    const query = '?utm_source=routing-check';
    const response = await request(path + query);
    assert.ok([301, 308].includes(response.status), 'expected HTTP 301/308, got ' + response.status);
    assert.equal(new URL(response.headers.get('location'), base).href, base + path + '/' + query);
  });
}
for (const path of ['/__routing_check_missing__/', '/apartment/999999/', '/en/__routing_check_missing__/', '/assets/__routing_check_missing__.js']) {
  await check(path + ' returns HTTP 404', async () => {
    const response = await request(path);
    assert.equal(response.status, 404, 'expected HTTP 404');
    if (!path.startsWith('/assets/')) {
      const html = await response.text();
      assert.match(html, /<h1\b[^>]*>404<\/h1>/, 'custom 404 page');
      assert.match(html, /noindex/, '404 is noindex');
      assert.deepEqual(canonical(html), [], '404 has no canonical');
    }
  });
}
if (checkHosts) {
  for (const host of ['http://amazy-apart.ru', 'https://www.amazy-apart.ru', 'https://myximka-netizen-amazy-apart-4060.twc1.net']) {
    await check(host + ' redirects to primary domain', async () => {
      const uri = '/contacts/?utm_source=routing-check';
      const response = await request(host + uri);
      assert.ok([301, 308].includes(response.status), 'expected HTTP 301/308, got ' + response.status);
      assert.equal(new URL(response.headers.get('location'), host).href, canonicalOrigin + uri);
    });
  }
}
if (failures) {
  console.error(`${failures} deployment checks failed. Check Timeweb settings and the deployed build; do not report production routing as fixed.`);
  process.exitCode = 1;
} else console.log('Deployment routing checks passed.');
