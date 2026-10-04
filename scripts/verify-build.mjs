import assert from 'node:assert/strict';
import { readFile, access, readdir } from 'node:fs/promises';
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
const maxUrl = 'https://max.ru/u/f9LHodD0cOIwf5cut6Q6zehywppvSEDtNHLjrHEdFoocJ4wMC6UtJJZ7TJk';
const seenResources = new Set();
const forbiddenWidgetFragments = [['code.', 'ji', 'vo.ru/widget'].join(''), ['Hr4', 'ChPwXXA'].join('')];
const assertNoForbiddenWidget = (text, label) => {
  for (const fragment of forbiddenWidgetFragments) {
    assert.ok(!text.includes(fragment), label + ': forbidden chat widget fragment ' + fragment);
  }
};
const phoneDigits = ['Kzc5OTU1MDg1ODA4', 'Kzc5OTk5OTQ3MzU0'].map(value => Buffer.from(value, 'base64').toString().replace(/\D/g, ''));
const hasPhone = text => phoneDigits.some(number => text.replace(/[\s()+-]/g, '').includes(number));
const phoneHref = 'tel:+' + phoneDigits[0];
const whatsappHref = 'https://wa.me/' + phoneDigits[0];
for (const url of urls) {
  const path = new URL(url).pathname;
  const html = await readFile(resolve(dist, '.' + path, 'index.html'), 'utf8');
  assertNoForbiddenWidget(html, url);
  // The owner explicitly keeps number-bearing hrefs; all other HTML must be free of the numbers.
  const withoutContactHrefs = html.replace(/\bhref="(?:tel:[^"]+|https:\/\/wa\.me\/[^"]+)"/g, '');
  assert.ok(!hasPhone(withoutContactHrefs), url + ': no phone outside retained contact hrefs');
  const phoneLinks = tags(html, 'a').filter(link => link.href?.startsWith('tel:'));
  assert.ok(phoneLinks.length > 0, url + ': phone links preserved');
  assert.ok(phoneLinks.every(link => link.href === phoneHref), url + ': exact phone destination preserved');
  const whatsappLinks = tags(html, 'a').filter(link => link.href?.startsWith('https://wa.me/'));
  assert.ok(whatsappLinks.length > 0, url + ': WhatsApp links preserved');
  assert.ok(whatsappLinks.every(link => link.href === whatsappHref || link.href.startsWith(whatsappHref + '?')), url + ': WhatsApp destination preserved');
  assert.ok(!html.includes('aria-labelledby="location-heading"'), url + ': duplicate location cards removed');
  const lang = path.startsWith('/en/') ? 'en' : path.startsWith('/zh/') ? 'zh' : 'ru';
  assert.match(html, new RegExp('<html[^>]*lang="' + lang + '"'), url + ': HTML language');
  assert.equal([...html.matchAll(/<title\b/g)].length, 1, url + ': one title');
  const meta = tags(html, 'meta');
  const links = tags(html, 'link');
  const maxLinks = tags(html, 'a').filter(a => a.href === maxUrl);
  assert.ok(maxLinks.length > 0, url + ': real MAX profile link');
  for (const link of maxLinks) {
    assert.equal(link.target, '_blank');
    assert.match(link.rel, /noopener/);
  }
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
    assert.ok(!hasPhone(match[1]), url + ': no phone in structured data, including nested entities');
    assert.ok(!/"telephone"\s*:/.test(match[1]), url + ': no telephone fields in structured data');
    assert.ok(data['@type'], url + ': schema type');
    if (data['@type'] === 'Organization' || data['@type'] === 'LodgingBusiness') assert.ok(data.sameAs.includes(maxUrl), url + ': MAX sameAs');
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
    assert.ok(html.includes(locale.hero.title), url + ': approved hero title');
    assert.ok(html.includes(locale.hero.subtitle), url + ': restored hero subtitle');
    assert.ok(html.includes(locale.hero.description), url + ': approved hero description');
    assert.ok(html.includes(locale.hero.directBooking), url + ': visible direct booking benefit');
    assert.ok(html.includes(locale.guarantee.terms), url + ': visible comparison conditions');
    assert.ok(html.includes('Online5'), url + ': existing direct booking offer');
  }
  assert.ok(!html.includes('https://max.ru/u/+7'), 'Never manufacture a MAX URL from a phone number');
}
for (const file of await readdir(resolve(dist, 'assets'))) {
  if (!file.endsWith('.js')) continue;
  const javascript = await readFile(resolve(dist, 'assets', file), 'utf8');
  assert.ok(!hasPhone(javascript), file + ': no literal phone in shipped JavaScript');
  assertNoForbiddenWidget(javascript, file);
}
for (const kind of ['main', 'max']) {
  const svg = await readFile(resolve(dist, `contact-${kind}.svg`), 'utf8');
  assert.match(svg, /<path\b/);
  assert.ok(!/<text\b/.test(svg) && !hasPhone(svg), 'Phone artwork contains outlines, not number text');
}
console.log('Verified phone omission from HTML text, structured data and JavaScript literals; existing phone/WhatsApp destinations preserved.');
await localResource('/logo.png');
const og = await readFile(resolve(dist, 'og-image.png'));
assert.equal(og.readUInt32BE(16), 1730);
assert.equal(og.readUInt32BE(20), 909);
const notFound = await readFile(resolve(dist, '404.html'), 'utf8');
assert.match(notFound, /noindex/);
console.log('Verified 42 localized pages: metadata, canonical, hreflang, local links/assets, JSON-LD, preview images, guarantee copy and 404.');

for (const [name, size] of [['favicon-16x16.png',16], ['favicon-32x32.png',32], ['favicon-48x48.png',48], ['favicon.png',48], ['apple-touch-icon.png',180], ['icon-192x192.png',192], ['icon-512x512.png',512], ['icon-maskable-512x512.png',512]]) {
  const png = await readFile(resolve(dist, name));
  assert.equal(png.readUInt32BE(16), size, name + ': width');
  assert.equal(png.readUInt32BE(20), size, name + ': height');
}
const manifest = JSON.parse(await readFile(resolve(dist, 'manifest.json'), 'utf8'));
for (const icon of manifest.icons) {
  const png = await readFile(resolve(dist, '.' + new URL(icon.src, origin).pathname));
  assert.equal(icon.sizes, png.readUInt32BE(16) + 'x' + png.readUInt32BE(20));
}
const ico = await readFile(resolve(dist, 'favicon.ico'));
assert.equal(ico.readUInt16LE(2), 1);
assert.equal(ico.readUInt16LE(4), 3);
const logo = await readFile(resolve(dist, 'logo.png'));
assert.equal(logo[25], 6, 'Logo must retain RGBA transparency');
assert.ok(manifest.icons.some(icon => icon.purpose === 'maskable'), 'Separate maskable icon');
console.log('Verified exact MAX links, sameAs, native favicon/mobile icon sizes and transparent logo.');
