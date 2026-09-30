import { createServer } from 'vite';
import { readFile, writeFile, mkdir } from 'node:fs/promises';
import { resolve } from 'node:path';
const root = process.cwd();
const template = await readFile(resolve(root, 'dist/index.html'), 'utf8');
const manifest = JSON.parse(await readFile(resolve(root, 'dist/.vite/manifest.json'), 'utf8'));
const routes = ['/', '/apartments/', '/apartments/dinamo/', '/apartments/leningradskiy-prospekt/', '/apartments/carskaya-ploshad/', '/business-travel/', '/long-stay/', '/about/', '/contacts/', '/owners/', '/offers/', '/apartment/1/', '/apartment/2/', '/apartment/3/'];
const languages = ['ru', 'en', 'zh'];
const urls = languages.flatMap(language => routes.map(route => (language === 'ru' ? '' : `/${language}`) + route));
const server = await createServer({ server: { middlewareMode: true }, appType: 'custom', mode: 'production', optimizeDeps: { noDiscovery: true, include: [] } });
try {
  const { render } = await server.ssrLoadModule('/src/entry-server.tsx');
  for (const url of [...urls, '/404/']) {
    const rendered = await render(url);
    let html = template.replace('<html lang="ru">', `<html lang="${rendered.language}">`).replace('<!--app-head-->', rendered.head).replace('<!--app-html-->', rendered.html);
    for (const [source, asset] of Object.entries(manifest)) {
      if (asset.file && source.startsWith('src/assets/')) html = html.replaceAll(`/${source}`, `/${asset.file}`);
    }
    const directory = resolve(root, 'dist', url.slice(1));
    await mkdir(directory, { recursive: true });
    await writeFile(resolve(directory, 'index.html'), html);
    if (url === '/404/') await writeFile(resolve(root, 'dist/404.html'), html);
  }
  const escape = value => value.replaceAll('&', '&amp;').replaceAll('"', '&quot;');
  const entries = languages.flatMap(language => routes.map(route => {
    const loc = 'https://amazy-apart.ru' + (language === 'ru' ? '' : `/${language}`) + route;
    const alternates = [...languages, 'x-default'].map(alt => `<xhtml:link rel="alternate" hreflang="${alt}" href="${escape('https://amazy-apart.ru' + (alt === 'en' || alt === 'zh' ? `/${alt}` : '') + route)}" />`).join('');
    return `<url><loc>${loc}</loc>${alternates}</url>`;
  }));
  const sitemap = `<?xml version="1.0" encoding="UTF-8"?>\n<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9" xmlns:xhtml="http://www.w3.org/1999/xhtml">${entries.join('\n')}</urlset>\n`;
  await writeFile(resolve(root, 'dist/sitemap.xml'), sitemap);
  await writeFile(resolve(root, 'public/sitemap.xml'), sitemap);
  console.log(`Prerendered ${urls.length} localized pages and 404; sitemap updated.`);
} finally { await server.close(); }
