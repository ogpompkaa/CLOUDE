#!/usr/bin/env node
/*
 * Test dymny gry (Playwright, przeglądarka bez okna).
 * Sprawdza przede wszystkim wymagania architektoniczne systemu wyglądu:
 *  - wszystkie skórki z manifestu przechodzą walidację,
 *  - każda skórka renderuje każdy stan z kontraktu (z zastępstwami) bez błędów,
 *  - podmiana skórki NIE zmienia imienia, postępów, więzi, umiejętności ani ekwipunku,
 *  - wybrana skórka przetrwa przeładowanie strony, a brakująca skórka nie psuje zapisu,
 *  - strefy dotyku wynikają z punktów orientacyjnych, a nie z rozmiaru obrazków,
 *  - podstawowe psie mechaniki (jedzenie, aport, spacer, sztuczki) działają.
 *
 *   node tests/smoke.mjs
 */
import { createRequire } from 'node:module';
import http from 'node:http';
import { readFileSync, existsSync, statSync } from 'node:fs';
import { join, extname, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';

const ROOT = join(dirname(fileURLToPath(import.meta.url)), '..');
const require = createRequire(import.meta.url);
let chromium;
try { ({ chromium } = require('playwright')); } catch (e) { ({ chromium } = createRequire('/opt/node22/lib/node_modules/')('playwright')); }

const types = { '.html': 'text/html', '.js': 'text/javascript', '.css': 'text/css', '.svg': 'image/svg+xml', '.png': 'image/png' };
const server = http.createServer((req, res) => {
  let p = join(ROOT, decodeURIComponent(req.url.split('?')[0]));
  if (existsSync(p) && statSync(p).isDirectory()) p = join(p, 'index.html');
  if (!existsSync(p)) { res.writeHead(404); res.end(); return; }
  res.writeHead(200, { 'content-type': types[extname(p)] || 'application/octet-stream' });
  res.end(readFileSync(p));
}).listen(0);
const URL = 'http://localhost:' + server.address().port + '/';

let failures = 0;
const ok = (cond, msg) => { console.log((cond ? '  ✓ ' : '  ✗ ') + msg); if (!cond) failures++; };

const browser = await chromium.launch().catch(() => chromium.launch({ executablePath: '/opt/pw-browsers/chromium-1194/chrome-linux/chrome' }));
const context = await browser.newContext({ viewport: { width: 420, height: 860 } });
// bez zewnętrznych czcionek – test ma działać offline
await context.route(/fonts\.(googleapis|gstatic)\.com/, (r) => r.abort());
const page = await context.newPage();
const errors = [];
page.on('pageerror', (e) => errors.push(e.message));
page.on('console', (m) => { if (m.type() === 'error' && !/fonts|ERR_FAILED|net::/i.test(m.text())) errors.push(m.text()); });

const ready = () => page.waitForFunction(() => window.PiesApp && window.PiesApp.dogView.skin && window.PiesApp.dogView.snap, null, { timeout: 15000 });

console.log('Uruchamianie gry…');
await page.goto(URL);
await ready();
await page.fill('#name-input', 'Burek');
await page.click('#name-form button');

console.log('Skórki:');
const skins = await page.evaluate(() => Pies.skins.list().map((s) => ({ id: s.id, renderer: s.renderer, warnings: s.report.warnings.length, missing: (s.missingImages || []).length })));
const invalid = await page.evaluate(() => Pies.skins.invalidList().map((s) => s.id + ': ' + s.report.errors.join('; ')));
ok(skins.length >= 2, 'zarejestrowano ' + skins.length + ' skórek: ' + skins.map((s) => s.id + ' (' + s.renderer + ')').join(', '));
ok(invalid.length === 0, 'brak odrzuconych konfiguracji' + (invalid.length ? ' – ' + invalid.join(' | ') : ''));

console.log('Każdy stan w każdej skórce:');
const renderReport = await page.evaluate(async () => {
  const out = [];
  const states = Pies.DogStates.ids;
  for (const skin of Pies.skins.list()) {
    await Pies.skins.preload(skin);
    const v = new Pies.DogView();
    v.setSkin(skin);
    const c = document.createElement('canvas');
    c.width = 300; c.height = 300;
    const ctx = c.getContext('2d');
    let failed = [];
    for (const st of states) {
      try {
        const snap = { x: 0, facing: 1, anim: st, animTime: 0.4, animDuration: Pies.DogStates.isOneShot(st) ? 1.2 : 0, speed: 0.5, lift: 0, mood: { happy: 0.5, energy: 0.5 }, equipment: { neck: 'collar-red', head: 'cap-blue' }, carrying: 'ball' };
        for (let i = 0; i < 20; i++) v.update(1 / 60, snap);
        v.draw(ctx, [100, 0, 0, 100, 150, 280]);
        const head = v.landmark('head');
        if (!head || !isFinite(head[0]) || !isFinite(head[1])) failed.push(st + ' (landmark)');
      } catch (e) { failed.push(st + ': ' + e.message); }
    }
    out.push({ id: skin.id, missing: skin.missingImages.length, failed });
  }
  return out;
});
for (const r of renderReport) ok(r.failed.length === 0 && r.missing === 0, r.id + ': ' + (r.failed.length ? 'błędy: ' + r.failed.join(', ') : 'wszystkie stany OK') + (r.missing ? ', brak plików: ' + r.missing : ''));

console.log('Podmiana wyglądu nie resetuje stanu gry:');
await page.evaluate(() => {
  const g = PiesApp.game;
  g.addXp(130);
  g.addBond(240);
  g.state.progress.coins = 500;
  g.buy('collar-blue');
  g.buy('cap-blue');
  g.equip('collar-blue');
  g.equip('cap-blue');
  g.state.progress.tricks.paw = 0.42;
  g.state.inventory.items.stick = 3;
});
const before = await page.evaluate(() => JSON.parse(JSON.stringify({ profile: PiesApp.game.state.profile, progress: PiesApp.game.state.progress, inventory: PiesApp.game.state.inventory, needs: PiesApp.game.state.needs })));
const target = 'cartoon-husky';
await page.evaluate((id) => PiesApp.game.setSkin(id), target);
await page.waitForFunction((id) => PiesApp.dogView.skin.id === id, target);
const after = await page.evaluate(() => JSON.parse(JSON.stringify({ profile: PiesApp.game.state.profile, progress: PiesApp.game.state.progress, inventory: PiesApp.game.state.inventory, needs: PiesApp.game.state.needs, skinId: PiesApp.game.state.appearance.skinId, body: PiesApp.game.body })));
ok(after.skinId === target, 'appearance.skinId = ' + after.skinId);
ok(JSON.stringify(after.profile) === JSON.stringify(before.profile), 'imię bez zmian (' + after.profile.name + ')');
ok(JSON.stringify(after.progress) === JSON.stringify(before.progress), 'poziom, XP, więź, monety, umiejętności, statystyki bez zmian (poz. ' + after.progress.level + ', więź ' + Math.round(after.progress.bond) + ')');
ok(JSON.stringify(after.inventory) === JSON.stringify(before.inventory), 'ekwipunek i założone akcesoria bez zmian');
ok(Math.abs(after.body.height - 1.08) < 1e-6, 'logika dostała nowe proporcje z konfiguracji (wysokość ' + after.body.height + ')');

console.log('Zapis i przeładowanie:');
await page.evaluate(() => PiesApp.saveNow());
await page.reload();
await ready();
const reloaded = await page.evaluate(() => ({ skin: PiesApp.dogView.skin.id, name: PiesApp.game.state.profile.name, level: PiesApp.game.state.progress.level, neck: PiesApp.game.state.inventory.equipped.neck }));
ok(reloaded.skin === target && reloaded.name === 'Burek' && reloaded.neck === 'collar-blue', 'po przeładowaniu: skórka ' + reloaded.skin + ', imię ' + reloaded.name + ', obroża ' + reloaded.neck);

await page.evaluate(() => { PiesApp.game.state.appearance.skinId = 'nieistniejacy-pies'; PiesApp.saveNow(); });
await page.reload();
await ready();
const fallback = await page.evaluate(() => ({ skin: PiesApp.dogView.skin.id, saved: PiesApp.game.state.appearance.skinId, name: PiesApp.game.state.profile.name, level: PiesApp.game.state.progress.level }));
ok(fallback.skin === 'cartoon-beagle' && fallback.name === 'Burek' && fallback.level === reloaded.level, 'brakująca skórka -> domyślny wygląd, zapis nietknięty (' + fallback.skin + ', zapamiętane: ' + fallback.saved + ')');

console.log('Strefy dotyku:');
const zones = await page.evaluate(() => {
  const v = PiesApp.dogView;
  const res = {};
  for (const id of ['head', 'tail', 'back']) { const z = v.skin.zones[id]; const lm = v.landmark(z.landmark); res[id] = v.hitTest(lm[0], lm[1]); }
  res.empty = v.hitTest(v.snap.x + 3, -2.5);
  return res;
});
ok(zones.head === 'head' && zones.tail === 'tail' && zones.back === 'back' && zones.empty === null, 'trafienia: ' + JSON.stringify(zones));

console.log('Dotyk na ekranie:');
await page.evaluate(() => { const g = PiesApp.game; g.interrupt([Pies.DogTasks.play('idle', 30)]); });
await page.waitForTimeout(400);
const headPt = await page.evaluate(() => {
  const v = PiesApp.dogView, sc = PiesApp.scene;
  const lm = v.landmark('head');
  const r = document.getElementById('scene').getBoundingClientRect();
  const m = sc.matrix();
  return [r.left + (m[0] * lm[0] + m[4]) / sc.dpr, r.top + (m[3] * lm[1] + m[5]) / sc.dpr];
});
await page.mouse.click(headPt[0], headPt[1]);
await page.waitForTimeout(150);
const petLabel = await page.evaluate(() => PiesApp.game.dog.status);
ok(petLabel === 'Głaskanie po głowie', 'kliknięcie w głowę psa -> ' + petLabel);

console.log('Psie mechaniki (przyspieszony czas):');
const sim = await page.evaluate(() => {
  const g = PiesApp.game;
  const tick = (sec) => { for (let i = 0; i < sec * 30; i++) g.update(1 / 30); };
  const r = {};
  g.state.needs.food = 30;
  g.state.world.foodBowl = 0;
  g.feed();
  tick(12);
  r.ate = g.state.needs.food > 60 && g.state.world.foodBowl < 0.05;
  const fetches = g.state.progress.stats.fetches;
  g.state.needs.energy = 90;
  g.throwBall();
  tick(14);
  r.fetched = g.state.progress.stats.fetches === fetches + 1 && g.ball.state === 'floor';
  const walks = g.state.progress.stats.walks;
  g.startWalk();
  tick(5);
  r.inPark = g.scene === 'park';
  tick(8);
  g.endWalk(false);
  r.walked = g.scene === 'home' && g.state.progress.stats.walks === walks + 1;
  const tricks = g.state.progress.stats.tricks;
  g.state.progress.tricks.sit = 1;
  // nawet opanowana sztuczka ma ~2% szansy na pomyłkę – dajemy psu kilka podejść
  for (let i = 0; i < 4 && g.state.progress.stats.tricks === tricks; i++) { g.command('sit'); tick(6); }
  r.trick = g.state.progress.stats.tricks === tricks + 1;
  g.state.needs.clean = 30;
  g.bath();
  tick(9);
  r.bath = g.state.needs.clean > 95;
  return r;
});
ok(sim.ate, 'karmienie: pies podchodzi do miski i je');
ok(sim.fetched, 'aport: pies goni piłkę, przynosi i oddaje');
ok(sim.inPark && sim.walked, 'spacer: wyjście do parku i powrót do domu');
ok(sim.trick, 'sztuczka „Siad” na komendę');
ok(sim.bath, 'kąpiel i otrzepywanie');

ok(errors.length === 0, 'brak błędów w konsoli' + (errors.length ? ': ' + errors.slice(0, 5).join(' | ') : ''));

await browser.close();
server.close();
console.log(failures ? '\n' + failures + ' test(ów) nie przeszło.' : '\nWszystko działa.');
process.exit(failures ? 1 : 0);
