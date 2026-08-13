// Smoke test kariery: przechodzi kilka sezonów w przeglądarce i sprawdza,
// czy gra nie sypie błędami i czy kluczowe systemy faktycznie działają.
//
//   node tests/smoke.mjs [liczba_sezonów]
//
// Wymaga zainstalowanego Playwrighta z Chromium (npm install && npx playwright install chromium).
// Gotową przeglądarkę można wskazać zmienną CHROMIUM_PATH.

import { chromium } from 'playwright';
import { fileURLToPath } from 'node:url';
import { dirname, resolve } from 'node:path';

const here = dirname(fileURLToPath(import.meta.url));
const gamePath = resolve(here, '..', 'index.html');
const SEASONS = Number(process.argv[2] || 2);
const SEASON_WEEKS = 26;

const fail = [];
function check(name, ok, detail) {
  console.log((ok ? 'OK   ' : 'BŁĄD ') + name + (detail ? ' — ' + detail : ''));
  if (!ok) fail.push(name);
}

// CHROMIUM_PATH pozwala wskazać gotową przeglądarkę (np. w sandboksie CI/agenta)
const exe = process.env.CHROMIUM_PATH;
const browser = await chromium.launch(exe ? { executablePath: exe } : {});
const page = await browser.newPage({ viewport: { width: 400, height: 880 } });

const jsErrors = [];
const network = [];
page.on('pageerror', e => jsErrors.push(e.message));
page.on('console', m => { if (m.type() === 'error') jsErrors.push('console: ' + m.text()); });
page.on('request', r => {
  const u = r.url();
  if (!u.startsWith('file://') && !u.startsWith('data:')) network.push(u);
});
page.on('dialog', d => d.accept());

const safeClick = async sel => {
  try { await page.click(sel, { timeout: 3000 }); return true; } catch { return false; }
};

await page.goto('file://' + gamePath);
await page.fill('#nick-input', 'żółw');           // polskie znaki muszą przejść
await page.click('#start-btn');
await page.waitForSelector('#game-screen:not(.hidden)');

// samouczek pokazuje się przy pierwszej karierze
const tutorialShown = await page.isVisible('#tutorial-overlay:not(.hidden)');
const tutorialSteps = tutorialShown ? await page.textContent('#tut-step') : '';
await safeClick('#tut-skip');
// ekran pomocy
await safeClick('#btn-help');
const helpSections = await page.$$eval('#help-body .help-sec h4', e => e.length).catch(() => 0);
await safeClick('#help-close');

const seen = { live: 0, choices: 0, missed: 0 };

for (let i = 0; i < SEASON_WEEKS * SEASONS + 2; i++) {
  await drainOverlays();
  await safeClick('.nav-btn[data-view="train"]');
  // selektor rozwiązywany przy każdym kliknięciu — render() podmienia przyciski w locie
  for (let k = 0; k < 4; k++) {
    const n = await page.$$eval('.act:not([disabled])', els => els.length).catch(() => 0);
    if (!n) break;
    if (!await safeClick('.act:not([disabled]) >> nth=' + Math.floor(Math.random() * n))) break;
  }
  // co jakiś czas spróbuj wpłynąć na skład
  if (i % 11 === 0) {
    await safeClick('.nav-btn[data-view="team"]');
    await safeClick('[data-captain]:not([disabled])');
    await drainOverlays();
  }
  await safeClick('.nav-btn[data-view="career"]');
  await safeClick('#cta-next');
  await drainOverlays();
  if (i % 7 === 0) {
    await safeClick('.nav-btn[data-view="offers"]');
    if (await page.$('[data-accept]')) { await safeClick('[data-accept]'); await drainOverlays(); }
  }
}

async function drainOverlays() {
  for (let g = 0; g < 40; g++) {
    if (await page.isVisible('#tutorial-overlay:not(.hidden)')) { await safeClick('#tut-skip'); continue; }
    if (await page.isVisible('#help-overlay:not(.hidden)')) { await safeClick('#help-close'); continue; }
    if (await page.isVisible('#choice-overlay:not(.hidden)')) {
      seen.choices++;
      const opts = await page.$$('[data-choice]');
      if (!opts.length) break;
      try { await opts[Math.floor(Math.random() * opts.length)].click({ timeout: 3000 }); } catch { /* wyścig z timerem */ }
      continue;
    }
    if (await page.isVisible('#live-overlay:not(.hidden)')) {
      seen.live++;
      await safeClick('#lv-skip');
      continue;
    }
    if (await page.isVisible('#match-overlay:not(.hidden)')) { await safeClick('#mo-close'); continue; }
    if (await page.isVisible('#modal-overlay:not(.hidden)')) {
      if (/Nie zagrasz/.test(await page.textContent('#modal-title'))) seen.missed++;
      await safeClick('#modal-close');
      continue;
    }
    break;
  }
}

const st = await page.evaluate(() => JSON.parse(localStorage.getItem('cs2-player-career-v1')));
const c = st.career;
const avgRating = c.matches ? c.rating / c.matches : 0;
const kd = c.deaths ? c.kills / c.deaths : 0;

check('brak błędów JavaScript', jsErrors.length === 0, jsErrors.slice(0, 3).join(' | '));
check('zero zapytań sieciowych (offline)', network.length === 0, network.slice(0, 2).join(' '));
check('sezony przechodzą', st.season > SEASONS, 'sezon ' + st.season);
check('mecze się rozgrywają', c.matches >= 8 * SEASONS, c.matches + ' meczów');
check('mecze na żywo działają', seen.live > 0, seen.live + ' rozegranych na żywo');
check('decyzje się pojawiają', seen.choices > 0, seen.choices + ' wyborów');
check('rating w rozsądnym zakresie', avgRating > 0.6 && avgRating < 1.6, avgRating.toFixed(2));
check('K/D w rozsądnym zakresie', kd > 0.5 && kd < 2.0, kd.toFixed(2));
check('historia meczów zapisana', (c.recent || []).length > 0, (c.recent || []).length + ' wpisów');
check('ranking świata żyje', Array.isArray(st.world) && st.world.length > 20, (st.world || []).length + ' rywali');
check('cel od zarządu istnieje', !!st.goal, st.goal && st.goal.kind);
check('samouczek startuje przy nowej karierze', tutorialShown, tutorialSteps);
check('ekran pomocy ma sekcje', helpSections >= 5, helpSections + ' sekcji');

// zapis i odczyt
await page.click('.nav-btn[data-view="profile"]');
await page.click('#btn-export');
const dump = await page.inputValue('#save-text');
await page.click('#save-close');
await page.click('#btn-import');
await page.fill('#save-text', dump);
await page.click('#save-load-btn');
await page.waitForSelector('#game-screen:not(.hidden)');
const nickAfter = await page.textContent('#pc-nick');
check('eksport i import zapisu', nickAfter.trim().toLowerCase() === 'żółw', nickAfter);

// karta gracza i wykres
await page.click('.nav-btn[data-view="profile"]');
const chartPoints = await page.$$eval('#rating-chart .chart-dot', els => els.length);
if (c.seasons.length >= 2) {
  check('wykres ratingu ma punkty', chartPoints === c.seasons.length, chartPoints + ' punktów');
} else {
  const txt = await page.textContent('#rating-chart');
  check('wykres czeka na drugi sezon', /drugim/.test(txt), txt.trim());
}
check('karta gracza wyrenderowana', (await page.$$('#player-card .pcard')).length === 1);

await browser.close();

console.log('\nPodsumowanie: ' + (fail.length ? fail.length + ' niepowodzeń' : 'wszystko przeszło') +
  ' · mecze ' + c.matches + ' · rating ' + avgRating.toFixed(2) + ' · tier ' + st.team.tier);
process.exit(fail.length ? 1 : 0);
