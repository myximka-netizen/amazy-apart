// Rebuild native icon files from the approved transparent public/logo.png.
// Install optional tooling outside the project:
// npm install --prefix /tmp/amazy-image-tools --no-save --package-lock=false sharp@0.34.5
// AMAZY_IMAGE_TOOLS=/tmp/amazy-image-tools node scripts/generate-icons.mjs
import { createRequire } from 'node:module';
import { readFile, writeFile } from 'node:fs/promises';
import { resolve } from 'node:path';
const require = createRequire(import.meta.url);
const sharp = require(require.resolve('sharp', { paths: [process.env.AMAZY_IMAGE_TOOLS || process.cwd()] }));
const logo = await readFile('public/logo.png');
const background = { r: 250, g: 247, b: 242, alpha: 1 };
const files = new Map();
for (const size of [16, 32, 48, 180, 192, 512]) {
  const png = await sharp(logo).resize(size, size, { fit: 'contain', background }).flatten({ background }).png().toBuffer();
  const name = size === 180 ? 'apple-touch-icon.png' : size === 192 || size === 512 ? 'icon-' + size + 'x' + size + '.png' : 'favicon-' + size + 'x' + size + '.png';
  files.set(name, png);
}
files.set('favicon.png', files.get('favicon-48x48.png'));
const mark = await sharp(logo).resize(358, 358, { fit: 'contain' }).png().toBuffer();
const maskable = await sharp({ create: { width: 512, height: 512, channels: 4, background } }).composite([{ input: mark, gravity: 'centre' }]).png().toBuffer();
files.set('icon-maskable-512x512.png', maskable);
const frames = [16, 32, 48].map(size => ({ size, png: files.get('favicon-' + size + 'x' + size + '.png') }));
const header = Buffer.alloc(6 + frames.length * 16);
header.writeUInt16LE(1, 2);
header.writeUInt16LE(frames.length, 4);
let offset = header.length;
frames.forEach(({size, png}, index) => {
  const start = 6 + index * 16;
  header[start] = size; header[start + 1] = size;
  header.writeUInt16LE(1, start + 4);
  header.writeUInt16LE(32, start + 6);
  header.writeUInt32LE(png.length, start + 8);
  header.writeUInt32LE(offset, start + 12);
  offset += png.length;
});
files.set('favicon.ico', Buffer.concat([header, ...frames.map(frame => frame.png)]));
for (const [name, png] of files) {
  await writeFile(resolve('public', name), png);
  if (process.argv.includes('--emit')) console.log('AMAZY_ICON:' + JSON.stringify({ path: 'public/' + name, base64: png.toString('base64') }));
}
console.log('Generated ' + files.size + ' native icon files from the approved logo.');
