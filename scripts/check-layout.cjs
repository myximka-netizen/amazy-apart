const { chromium } = require(require.resolve('playwright', { paths: [process.env.AMAZY_BROWSER_TOOLS || process.cwd()] }));
const assert = require('node:assert/strict');
const fs = require('node:fs/promises');
(async () => {
  const launch = { executablePath: process.env.AMAZY_CHROME || '/usr/bin/google-chrome', headless: true, args: ['--no-sandbox', '--disable-dev-shm-usage'] };
  const browser = await chromium.launch(launch);
  const context = await browser.newContext({ ignoreHTTPSErrors: true });
  await context.route('**/mc.yandex.ru/**', route => route.abort());
  const page = await context.newPage();
  const errors = [];
  page.on('pageerror', error => errors.push(error.message));
  await fs.mkdir('artifacts/layout', { recursive: true });
  await page.setViewportSize({ width: 1440, height: 1100 });
  await page.goto('http://127.0.0.1:4173/', { waitUntil: 'domcontentloaded' });
  await page.locator('h1').waitFor();
  assert.equal(await page.locator('[aria-labelledby="location-heading"]').count(), 0, 'Duplicate location cards removed');
  const phoneNumbers = ['Kzc5OTU1MDg1ODA4', 'Kzc5OTk5OTQ3MzU0'].map(value => Buffer.from(value, 'base64').toString());
  const hasPhoneText = text => phoneNumbers.some(number => text.replace(/[\s()+-]/g, '').includes(number.slice(1)));
  assert.ok(!hasPhoneText(await page.locator('body').innerText()), 'Visible phone is artwork, not harvestable DOM text');
  const vendorReady = await page.waitForFunction(() => document.querySelector('#hr-widget')?.childElementCount > 0, null, { timeout: 22000 }).then(() => true).catch(() => false);
  await page.evaluate(() => document.fonts.ready);
  console.log(JSON.stringify({ liveWidget: vendorReady, frameCount: page.frames().length }));
  await page.screenshot({ path: 'artifacts/layout/desktop.png' });
  for (const { width, height, firstScreen } of [
    { width: 1440, height: 900, firstScreen: true },
    { width: 1366, height: 768, firstScreen: true },
    { width: 1280, height: 800, firstScreen: true },
    { width: 1024, height: 768 },
    { width: 768, height: 1024 },
    { width: 390, height: 844, firstScreen: true },
    { width: 375, height: 812, firstScreen: true },
    { width: 320, height: 700 },
  ]) {
    await page.setViewportSize({ width, height });
    await page.waitForTimeout(150);
    const metrics = await page.evaluate(() => ({ viewport: innerWidth, width: document.documentElement.scrollWidth, headerWidth: document.querySelector('header').scrollWidth, h1: getComputedStyle(document.querySelector('h1')).fontSize, wrapper: getComputedStyle(document.querySelector('#hr-widget').parentElement).backgroundColor, heroGold: getComputedStyle(document.querySelector('h1 span')).color }));
    if (metrics.width > width + 1) {
      console.log('Overflow elements:', await page.evaluate(() => [...document.querySelectorAll('body *')].filter(element => element.getBoundingClientRect().right > innerWidth + 1).map(element => ({ tag: element.tagName, className: element.className, text: element.textContent?.slice(0, 80), right: element.getBoundingClientRect().right })).slice(-20)));
      await page.screenshot({ path: `artifacts/layout/overflow-${width}.png`, fullPage: true });
    }
    assert.ok(metrics.width <= width + 1, 'Horizontal overflow at ' + width + ': ' + JSON.stringify(metrics));
    assert.ok(metrics.headerWidth <= width + 1, 'Header overflow at ' + width);
    assert.equal(metrics.wrapper, 'rgba(0, 0, 0, 0)', 'Booking wrapper should remain transparent');
    if (width >= 1024) assert.ok(await page.locator('header nav a[href="/owners/"]').first().isVisible(), 'Owners must be in desktop navigation');
    console.log(JSON.stringify({ width, ...metrics }));
    if (vendorReady) {
      const booking = await page.locator('#hr-widget').boundingBox();
      console.log(JSON.stringify({ viewport: `${width}x${height}`, bookingTop: booking.y, bookingBottom: booking.y + booking.height }));
      if (firstScreen) assert.ok(booking.height > 0 && booking.y + booking.height <= height, `Booking must fit the first screen at ${width}x${height}: ${JSON.stringify(booking)}`);
    }
    if (width === 1366) await page.screenshot({ path: 'artifacts/layout/desktop.png' });
    if (width === 390) await page.screenshot({ path: 'artifacts/layout/mobile.png', fullPage: false });
  }
  await page.getByRole('button', { name: 'Меню', exact: true }).click();
  await page.locator('#mobile-navigation a[href="/owners/"]').click();
  await page.waitForURL('**/owners/');
  assert.equal(await page.locator('#mobile-navigation').count(), 0, 'Menu closes after navigation');
  assert.equal(await page.locator('[aria-labelledby="location-heading"]').count(), 0, 'Duplicate location cards removed');
  await page.setViewportSize({ width: 1280, height: 900 });
  await page.goto('http://127.0.0.1:4173/contacts/', { waitUntil: 'domcontentloaded' });
  await page.locator('main [data-phone-display="max"]').waitFor();
  assert.ok(!hasPhoneText(await page.locator('body').innerText()), 'Contact page must not expose number text');
  for (const json of await page.locator('script[type="application/ld+json"]').allTextContents()) assert.ok(!hasPhoneText(json), 'Hydrated SEO data must not expose numbers');
  assert.equal(await page.locator('main a[href^="tel:"]').getAttribute('href'), `tel:${phoneNumbers[0]}`, 'Phone link target unchanged');
  assert.equal(await page.locator('main a[href^="https://wa.me/"]').getAttribute('href'), `https://wa.me/${phoneNumbers[0].slice(1)}`, 'WhatsApp target unchanged');
  for (const kind of ['main', 'max']) {
    assert.ok((await page.request.get(`http://127.0.0.1:4173/contact-${kind}.svg`)).ok(), 'Phone artwork loads');
    const mask = await page.locator(`main [data-phone-display="${kind}"]`).evaluate(node => getComputedStyle(node).maskImage);
    assert.ok(mask.includes(`contact-${kind}.svg`), 'Phone artwork is applied');
  }
  await context.grantPermissions(['clipboard-read', 'clipboard-write'], { origin: 'http://127.0.0.1:4173' });
  await page.getByRole('button', { name: 'Скопировать номер', exact: true }).click();
  assert.equal(await page.evaluate(() => navigator.clipboard.readText()), phoneNumbers[0], 'Existing copy action preserves the complete number');
  await page.evaluate(() => document.fonts.ready);
  await page.screenshot({ path: 'artifacts/layout/contacts.png' });
  await context.close();

  // Exercise failure and successful retry without relying on a third-party outage.
  const failure = await browser.newContext();
  await failure.route('**/mc.yandex.ru/**', route => route.abort());
  await failure.route('https://homereserve.ru/widget.js*', route => route.abort());
  const fallback = await failure.newPage();
  await fallback.goto('http://127.0.0.1:4173/', { waitUntil: 'domcontentloaded' });
  const retry = fallback.getByRole('button', { name: 'Попробовать снова', exact: true });
  await fallback.locator('#hr-widget').locator('..').getByRole('status').waitFor();
  await fallback.waitForFunction(() => document.querySelector('#hr-widget')?.parentElement.querySelector('a[href="https://homereserve.ru/HYkUIAGFQD"]'));
  assert.ok(await retry.isVisible(), 'Retry only becomes visible on widget failure');
  await failure.unroute('https://homereserve.ru/widget.js*');
  await failure.route('https://homereserve.ru/widget.js*', route => route.fulfill({ contentType: 'application/javascript', headers: { 'Access-Control-Allow-Origin': '*' }, body: 'window.homereserve = { initWidgetSearch() { document.getElementById("hr-widget").innerHTML = "<div data-qa-widget>Booking widget loaded</div>"; }, initWidgetList() {} };' }));
  await retry.click();
  await fallback.locator('[data-qa-widget]').waitFor();
  await fallback.waitForFunction(() => !document.querySelector('#hr-widget')?.parentElement.querySelector('[role="status"]'));
  assert.equal(await fallback.locator('#hr-widget').locator('..').locator('a, button, [role="status"]').count(), 0, 'No extra controls remain after the widget loads');
  await failure.close();
  await browser.close();
  assert.deepEqual(errors, [], 'Browser runtime errors');
  console.log('PASS: desktop/mobile layout, owners navigation, booking/retry, phone artwork, unchanged contact links and phone copy.');
})().catch(error => { console.error(error); process.exit(1); });
