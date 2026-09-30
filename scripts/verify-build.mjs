import assert from 'node:assert/strict';
import { readFile, access } from 'node:fs/promises';
import { resolve, extname } from 'node:path';

const origin = 'https://amazy-apart.ru';
const dist = resolve('dist');
const sitemap = await readFile(resolve(dist, 'sitemap.xml'), 'utf8');
const urls = [...sitemap.matchAll(/<loc>([^<]+)<\/loc>/g)].map(match => match[1]);
assert.equal(urls.length, 42, 'Expected 42 localized pages');
assert.equal(new Set(urls).size, 42, 'Sitemap URLs must be unique');
const attrs = tag => Object.fromEntries([...tag.matchAll(/([\w:-]+)="([^"]*)"/g)].map(m => [m[1].toLowerCase(), m[2].replaceAll('&amp;', '&')]));
const tags = (html, name) => [...html.matchAll(new RegExp('<' + name + '\\b[^>]*>', 'g'))].map(m => attrs(m[0]));
async function localResource(url) {
  const pathname = decodeURIComponent(new URL(url, origin).pathname);
  const file = resolve(dist, '.' + pathname, extname(pathname) ? '' : 'index.html');
  await access(file).catch(() => { throw new Error('Missing local resource: ' + pathname); });
}
const seenResources = new Set();
for (const url of urls) {
  const path = new URL(url).pathname;
  const html = await readFile(resolve(dist, '.' + path, 'index.html'), 'utf8');
  const lang = path.startsWith('/en/') ? 'en' : path.startsWith('/zh/') ? 'zh' : 'ru';
  assert.match(html, new RegExp('<html[^>]*lang="' + lang + '"'), url + ': HTML language');
  assert.equal([...html.matchAll(/<title\b/g)].length, 1, url + ': one title');
  const meta = tags(html, 'meta');
  const links = tags(html, 'link');
  const metadata = name => meta.find(m => m.name === name || m.property === name)?.content;
  assert.equal(meta.filter(m => m.name === 'description').length, 1, url + ': one description');
  assert.ok(metadata('description')?.length > 20, url + ': description');
  const canonical = links.filter(l => l.rel === 'canonical');
  assert.equal(canonical.length, 1, url + ': one canonical');
  assert.equal(canonical[0].href, url, url + ': canonical matches route');
  assert.equal(metadata('og:url'), url);
  assert.equal(metadata('twitter:image'), metadata('og:image'));
  assert.match(metadata('robots'), /max-image-preview:large/);
  const alternates = links.filter(l => l.rel === 'alternate' && l.hreflang);
  assert.deepEqual(alternates.map(l => l.hreflang).sort(), ['en', 'ru', 'x-default', 'zh']);
  for (const alternate of alternates) assert.ok(urls.includes(alternate.href), 'Unknown hreflang URL: ' + alternate.href);
  const image = metadata('og:image');
  assert.ok(image?.startsWith(origin + '/'), url + ': absolute local OG image');
  await localResource(image);
  if (/\/apartment\/[123]\/$/.test(path)) assert.ok(image.includes('/assets/apartment-'), 'Apartment preview must use its own photo');
  else {
    assert.equal(image, origin + '/og-image.png');
    assert.equal(metadata('og:image:width'), '1730');
    assert.equal(metadata('og:image:height'), '909');
  }
  for (const match of html.matchAll(/<script\b[^>]*type="application\/ld\+json"[^>]*>([\s\S]*?)<\/script>/g)) {
    const data = JSON.parse(match[1]);
    assert.ok(data['@type'], url + ': schema type');
    assert.ok(!match[1].includes('/hero-image.jpg'), 'Obsolete schema image');
    if (data['@type'] === 'LodgingBusiness') assert.equal(data.image, origin + '/og-image.png');
  }
  for (const tag of [...tags(html, 'a'), ...tags(html, 'img'), ...tags(html, 'script'), ...links]) {
    const value = tag.src || tag.href;
    if (!value || value.startsWith('#') || /^(?:tel:|mailto:|data:|javascript:)/.test(value)) continue;
    const resource = new URL(value, url);
    if (resource.origin !== origin || seenResources.has(resource.pathname)) continue;
    seenResources.add(resource.pathname);
    await localResource(resource.href);
  }
  if (['/', '/en/', '/zh/'].includes(path)) {
    const locale = JSON.parse(await readFile(resolve('src/locales', lang + '.json'), 'utf8'));
    assert.ok(html.includes(locale.guarantee.hero), url + ': visible guarantee');
    assert.ok(html.includes(locale.guarantee.terms), url + ': visible comparison conditions');
    assert.ok(html.includes('Online5'), url + ': existing direct booking offer');
  }
  assert.ok(!html.includes('https://max.ru/u/+7'), 'Never manufacture a MAX URL from a phone number');
}
await localResource('/logo.png');
const og = await readFile(resolve(dist, 'og-image.png'));
assert.equal(og.readUInt32BE(16), 1730);
assert.equal(og.readUInt32BE(20), 909);
const notFound = await readFile(resolve(dist, '404.html'), 'utf8');
assert.match(notFound, /noindex/);
console.log('Verified 42 localized pages: metadata, canonical, hreflang, local links/assets, JSON-LD, preview images, guarantee copy and 404.');
