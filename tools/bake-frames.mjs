#!/usr/bin/env node
/*
 * Wypalanie skórki warstwowej do skórki KLATKOWEJ (PNG) – przykład formatu "frames".
 *
 * Tworzy assets/skins/demo-klatki/ z paskami klatek PNG i plikiem skin.js. Skórka ta
 * celowo zawiera tylko część animacji – reszta pokazuje działanie łańcucha zastępstw.
 * Jest wzorem dla prawdziwego psa dostarczonego jako seria zdjęć/klatek.
 *
 * Wymaga Playwright (narzędzie deweloperskie, gra go nie potrzebuje):
 *   node tools/bake-frames.mjs
 */
import { createRequire } from 'node:module';
import http from 'node:http';
import { readFileSync, existsSync, mkdirSync, writeFileSync, statSync } from 'node:fs';
import { join, extname, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';

const ROOT = join(dirname(fileURLToPath(import.meta.url)), '..');
const require = createRequire(import.meta.url);
let chromium;
try { ({ chromium } = require('playwright')); } catch (e) { ({ chromium } = createRequire('/opt/node22/lib/node_modules/')('playwright')); }

const SOURCE = 'cartoon-beagle';
const TARGET = 'demo-klatki';
const FRAME = [240, 220];
const SHOULDER = 104; // px wysokości w kłębie w klatce
const ORIGIN = [104, 206]; // ziemia pod środkiem psa w klatce
// stan: [liczba klatek, fps, prędkość (dla chodu/biegu)]
const ANIMS = {
  idle: [8, 6, 0], walk: [8, 9, 0.85], run: [6, 13, 2.5], sit: [4, 4, 0], sleep: [6, 3, 0], eat: [6, 8, 0], happy: [6, 10, 0],
};

const types = { '.html': 'text/html', '.js': 'text/javascript', '.svg': 'image/svg+xml', '.png': 'image/png' };
const server = http.createServer((req, res) => {
  const p = join(ROOT, decodeURIComponent(req.url.split('?')[0]));
  if (!existsSync(p) || statSync(p).isDirectory()) { res.writeHead(404); res.end(); return; }
  res.writeHead(200, { 'content-type': types[extname(p)] || 'application/octet-stream' });
  res.end(readFileSync(p));
}).listen(0);
const base = 'http://localhost:' + server.address().port + '/';

const scripts = ['js/core/util.js', 'js/dog/states.js', 'js/dog/body.js', 'js/render/skins.js', 'js/render/animations.js', 'js/render/animator.js',
  'js/render/rig-renderer.js', 'js/render/frames-renderer.js', 'js/render/item-art.js', 'js/render/dog-view.js', 'assets/skins/manifest.js'];

const browser = await chromium.launch().catch(() => chromium.launch({ executablePath: '/opt/pw-browsers/chromium-1194/chrome-linux/chrome' }));
const page = await browser.newPage();
// strona z tego samego źródła – inaczej canvas z grafikami byłby „skażony” i toDataURL by nie działał
await page.goto(base + '__pusta').catch(() => {});
await page.setContent('<html><head><base href="' + base + '"></head><body>' + scripts.map((s) => '<script src="' + s + '"></script>').join('') + '</body></html>', { waitUntil: 'load' });

const result = await page.evaluate(async ({ SOURCE, FRAME, SHOULDER, ORIGIN, ANIMS }) => {
  await Pies.skins.loadManifest([SOURCE]);
  const skin = Pies.skins.get(SOURCE);
  await Pies.skins.preload(skin);
  const out = {};
  for (const [state, [count, fps, speed]] of Object.entries(ANIMS)) {
    const strip = document.createElement('canvas');
    strip.width = FRAME[0] * count;
    strip.height = FRAME[1];
    const ctx = strip.getContext('2d');
    const view = new Pies.DogView();
    view.setSkin(skin);
    const ppu = SHOULDER / view.body.height;
    const snap = { x: 0, facing: 1, anim: state, animTime: 0, animDuration: 0, speed, lift: 0, mood: { happy: 0.6, energy: 0.8 }, equipment: {}, carrying: null };
    const clip = Pies.DogClips[state];
    const cycle = clip.gaitDistance && speed ? (clip.gaitDistance * view.body.height) / speed : clip.period;
    for (let i = 0; i < 90; i++) view.update(1 / 60, snap); // rozgrzewka sprężyn
    let lm = null, mouth = null, neck = null, neckRot = 0;
    for (let f = 0; f < count; f++) {
      const steps = Math.max(1, Math.round((cycle / count) * 60));
      for (let s = 0; s < steps; s++) view.update(1 / 60, snap);
      const ox = f * FRAME[0] + ORIGIN[0];
      view.draw(ctx, [ppu, 0, 0, ppu, ox, ORIGIN[1]], { noShadow: true });
      if (f === 0) {
        const toFrame = (p) => [Math.round(p[0] * ppu + ORIGIN[0]), Math.round(p[1] * ppu + ORIGIN[1])];
        lm = {};
        for (const k of ['head', 'headTop', 'back', 'belly', 'tail', 'mouth']) lm[k] = toFrame(view.landmark(k));
        mouth = toFrame(view.anchorWorld('mouth'));
        neck = toFrame(view.anchorWorld('neck'));
        const am = view.renderer.anchorMatrix('neck');
        neckRot = Math.round((Math.atan2(am[1], am[0]) * 180) / Math.PI);
      }
    }
    out[state] = { png: strip.toDataURL('image/png'), count, fps, landmarks: lm, mouth, neck, neckRot };
  }
  return out;
}, { SOURCE, FRAME, SHOULDER, ORIGIN, ANIMS });

const dir = join(ROOT, 'assets', 'skins', TARGET);
mkdirSync(join(dir, 'frames'), { recursive: true });
for (const [state, r] of Object.entries(result)) {
  writeFileSync(join(dir, 'frames', state + '.png'), Buffer.from(r.png.split(',')[1], 'base64'));
}

const fmt = (p) => '[' + p.join(', ') + ']';
let cfg = `/*
 * SKÓRKA KLATKOWA (przykład): „Beagle – test klatek”
 * Wygenerowana przez tools/bake-frames.mjs z warstwowego beagle'a, żeby pokazać format "frames":
 * każda animacja to pasek klatek PNG (sheet) albo lista plików (frames).
 * Celowo ma tylko 7 animacji – brakujące stany gra zastępuje automatycznie
 * (np. excited -> happy, lie -> sit, roll -> bellyup -> lie -> sit). Opis pól: docs/WYGLAD-PSA.md
 */
Pies.skins.register({
  id: '${TARGET}',
  name: 'Beagle – test klatek',
  description: 'Przykład skórki z gotowych klatek PNG (format dla serii zdjęć). Ma tylko część animacji.',
  version: 1,
  renderer: 'frames',
  facing: 'right',
  frameSize: ${fmt(FRAME)},
  // origin: punkt na ziemi pod środkiem psa w każdej klatce; shoulderHeightPx: wysokość w kłębie w klatce
  fit: { shoulderHeightPx: ${SHOULDER}, origin: ${fmt(ORIGIN)} },
  proportions: { heightScale: 1.0, lengthRatio: 1.1, mouthReach: 0.94, headHeight: 1.58 },

  animations: {
`;
for (const [state, r] of Object.entries(result)) {
  const L = r.landmarks;
  cfg += `    ${state}: {
      sheet: { src: 'frames/${state}.png', frameWidth: ${FRAME[0]}, frameHeight: ${FRAME[1]}, count: ${r.count} },
      fps: ${r.fps},
      landmarks: { head: ${fmt(L.head)}, headTop: ${fmt(L.headTop)}, back: ${fmt(L.back)}, belly: ${fmt(L.belly)}, tail: ${fmt(L.tail)}, mouth: ${fmt(L.mouth)} },
      anchors: { neck: { at: ${fmt(r.neck)}, rotation: ${r.neckRot}, width: 33 }, mouth: { at: ${fmt(r.mouth)} } },
    },
`;
}
cfg += `  },
});
`;
writeFileSync(join(dir, 'skin.js'), cfg);
console.log('Zapisano skórkę ' + TARGET + ' (' + Object.keys(result).length + ' animacji).');
await browser.close();
server.close();
