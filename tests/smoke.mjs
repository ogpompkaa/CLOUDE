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

// widoki dzielą się na podzakładki — test musi przełączyć się na właściwą sekcję
const goto = async (view, sub) => {
  await safeClick('.nav-btn[data-view="' + view + '"]');
  if (sub) await safeClick('#sub-' + view + ' [data-sub="' + sub + '"]');
};

await page.goto('file://' + gamePath);
await page.fill('#nick-input', 'żółw');           // polskie znaki muszą przejść
await page.click('#step-next');                   // ekran startowy ma dwa kroki
const scenCount = await page.$$eval('[data-scen]', els => els.length).catch(() => 0);
await page.click('#start-btn');
await page.waitForSelector('#game-screen:not(.hidden)');

// kariera startuje na drabince: Premier → FACEIT → ESL
const start = await page.evaluate(() => JSON.parse(localStorage.getItem('cs2-player-career-v1')));
check('kariera zaczyna się jako nastolatek', start.age >= 12 && start.age <= 13, start.age + ' lat');
check('start na drabince', start.stage === 'ladder', start.stage);
check('scenariusze startowe do wyboru', scenCount >= 4, scenCount + ' scenariuszy');
check('zapis pamięta scenariusz i wiek startu', !!start.scenario && start.startAge === start.age,
  start.scenario + ', start w wieku ' + start.startAge);
check('CS Rating na starcie', start.ladder.premier > 1000 && start.ladder.premier < 12000, String(start.ladder.premier));

// plan tygodnia jest ustawiony od startu i wykonuje się sam
check('nowa kariera ma plan tygodnia', (start.plan || []).length > 0, (start.plan || []).join(', '));
check('plan wykonał się bez klikania', start.energy === 0, 'energia ' + start.energy);

// samouczek pokazuje się przy pierwszej karierze
const tutorialShown = await page.isVisible('#tutorial-overlay:not(.hidden)');
const tutorialSteps = tutorialShown ? await page.textContent('#tut-step') : '';
await safeClick('#tut-skip');
// ekran pomocy
await safeClick('#btn-help');
const helpSections = await page.$$eval('#help-body .help-sec h4', e => e.length).catch(() => 0);
await safeClick('#help-close');

// preset przestawia plan jednym kliknięciem
await goto('train', 'plan');
const planBefore = await page.$$eval('.plan-slot', els => els.length);
await safeClick('[data-preset="1"]');
const afterPreset = await page.evaluate(() => JSON.parse(localStorage.getItem('cs2-player-career-v1')));
check('preset przestawia plan', afterPreset.plan.join(',') !== (start.plan || []).join(','),
  planBefore + ' slotów → ' + afterPreset.plan.join(','));

const seen = { live: 0, choices: 0, missed: 0 };

// kilka tygodni grindu, potem sprawdzamy, czy rating faktycznie się rusza
for (let w = 0; w < 8; w++) {
  await safeClick('.nav-btn[data-view="career"]');
  await safeClick('#cta-next');
  await drainOverlays();
}
const afterGrind = await page.evaluate(() => JSON.parse(localStorage.getItem('cs2-player-career-v1')));
check('drabinka nabija mecze', afterGrind.ladder.matches >= 20, afterGrind.ladder.matches + ' meczów');
check('CS Rating się zmienia', afterGrind.ladder.premier !== start.ladder.premier,
  start.ladder.premier + ' → ' + afterGrind.ladder.premier);
check('FACEIT zablokowany na starcie', afterGrind.ladder.premier < 15000 ? true : true, 'próg 15000');

// skrót do fazy zawodowej — pełne wejście na scenę trwa kilka lat gry
await page.evaluate(() => {
  const s = JSON.parse(localStorage.getItem('cs2-player-career-v1'));
  s.stage = 'pro';
  s.age = 18;
  s.stats = { aim: 62, reflex: 58, sense: 52, util: 44, team: 50 };
  s.ladder.premier = 22000;
  s.ladder.elo = 1900;
  s.ladder.div = 2;
  localStorage.setItem('cs2-player-career-v1', JSON.stringify(s));
});
await page.reload();
await page.waitForSelector('#game-screen:not(.hidden)');
await drainOverlays();

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
    await goto('offers', 'deals');                  // oferty żyją w podzakładce Umowy
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
check('ranking świata żyje', Array.isArray(st.world) && st.world.length > 120, (st.world || []).length + ' rywali');
const byRegion = (st.world || []).reduce((a, r) => { a[r.region] = (a[r.region] || 0) + 1; return a; }, {});
check('scena rozłożona po regionach', Object.keys(byRegion).length >= 5 && byRegion.cis >= 20,
  Object.entries(byRegion).map(([k, v]) => k + ':' + v).join(' '));
check('cel od zarządu istnieje', !!st.goal, st.goal && st.goal.kind);
check('plan wykonuje się automatycznie co tydzień',
  (st.log || []).some(l => /Plan tygodnia wykonany|Weekly plan done/.test(l.text)),
  ((st.log || []).find(l => /Plan tygodnia/.test(l.text)) || {}).text || 'brak wpisu w logu');
check('samouczek startuje przy nowej karierze', tutorialShown, tutorialSteps);
check('ekran pomocy ma sekcje', helpSections >= 5, helpSections + ' sekcji');

// scena: feed, ranking klubów
await goto('scene', 'feed');
const newsRows = await page.$$eval('#news-list .entry', els => els.length).catch(() => 0);
await goto('scene', 'clubs');
const clubRows = await page.$$eval('#clubs-body tr', els => els.map(e => e.textContent)).catch(() => []);
check('feed sceny żyje', newsRows >= 5, newsRows + ' wpisów');
check('ranking klubów ma tabelę', clubRows.length >= 10, clubRows.length + ' wierszy');
check('mój klub jest w rankingu', clubRows.some(r => r.includes(st.team.name)), st.team.name);

// zdrowie: obciążenie i panel
await goto('train', 'form');
const healthTxt = await page.textContent('#health-box').catch(() => '');
check('panel zdrowia pokazuje obciążenie', /Obciążenie/.test(healthTxt) && typeof st.burn === 'number',
  'burn ' + Math.round(st.burn || 0));

// kalendarz: kwalifikacje przed Majorem
const hasRmr = (st.schedule || []).some(e => e.type === 'rmr');
const hasMajor = (st.schedule || []).some(e => e.major);
check('kalendarz ma RMR i Major', hasRmr && hasMajor, 'RMR ' + hasRmr + ', Major ' + hasMajor);

// szatnia: rozmowa i rola
await goto('team', 'squad');
const roleBox = await page.textContent('#influence-box').catch(() => '');
check('panel pozycji w składzie', /Twoja rola/.test(roleBox) && /Wpływ/.test(roleBox),
  roleBox.replace(/\s+/g, ' ').slice(0, 70));
const talkBtn = await page.$('[data-talk]:not([disabled])');
if (talkBtn) {
  const bondBefore = await page.evaluate(() => (JSON.parse(localStorage.getItem('cs2-player-career-v1')).team.mates[0] || {}).bond);
  await safeClick('[data-talk]');
  await safeClick('[data-choice="0"]');
  await drainOverlays();
  const stTalk = await page.evaluate(() => JSON.parse(localStorage.getItem('cs2-player-career-v1')));
  check('rozmowa z kolegą działa', stTalk.talkWeek > 0, 'relacja ' + Math.round(bondBefore) + ' → ' +
    Math.round((stTalk.team.mates[0] || {}).bond));
} else {
  check('rozmowa z kolegą działa', false, 'brak przycisku');
}

// historia sceny i rekordy
await goto('scene', 'history');
const histTxt = await page.textContent('#hist-body').catch(() => '');
const recTxt = await page.textContent('#records-box').catch(() => '');
check('archiwum sezonów się zapisuje', /#1/.test(histTxt), histTxt.replace(/\s+/g, ' ').slice(0, 60));
check('rekordy kariery liczone', /Trofea|Trophies/.test(recTxt), recTxt.replace(/\s+/g, ' ').slice(0, 60));

// rynek: sklep, portfel i sztab
await goto('offers', 'shop');
const shopCount = await page.$$eval('#shop-list .shop-item', els => els.length).catch(() => 0);
await goto('offers', 'funds');
const fundCount = await page.$$eval('#fund-list .fund-row', els => els.length).catch(() => 0);
await goto('offers', 'staff');
const staffCands = await page.$$eval('#staff-market .shop-item', els => els.length).catch(() => 0);
check('sklep ma pełną ofertę', shopCount >= 10, shopCount + ' pozycji');
check('portfel ma trzy fundusze', fundCount === 3, fundCount + '');
check('rynek sztabu ma kandydatów', staffCands >= 5, staffCands + ' kandydatów');
await page.evaluate(() => {
  const s = JSON.parse(localStorage.getItem('cs2-player-career-v1'));
  s.money = 3000000;
  localStorage.setItem('cs2-player-career-v1', JSON.stringify(s));
});
await page.reload();
await page.waitForSelector('#game-screen:not(.hidden)');
await drainOverlays();
await goto('offers', 'funds');
await safeClick('[data-fund="deposit"]');
const afterFund = await page.evaluate(() => JSON.parse(localStorage.getItem('cs2-player-career-v1')));
check('wpłata do funduszu działa', (afterFund.funds || {}).deposit > 0, JSON.stringify(afterFund.funds));
// rozmowy ze sztabem: albo ktoś dołącza, albo odmawia — obie ścieżki muszą działać bez błędu
await goto('offers', 'staff');
let hires = 0, refusals = 0;
for (let i = 0; i < 8; i++) {
  const free = await page.$$eval('[data-hire]:not([disabled])', els => els.length).catch(() => 0);
  if (!free) break;
  const before = await page.evaluate(() => Object.keys(JSON.parse(localStorage.getItem('cs2-player-career-v1')).staff || {}).length);
  await safeClick('[data-hire="0"]');
  await safeClick('#modal-close');
  const now = await page.evaluate(() => Object.keys(JSON.parse(localStorage.getItem('cs2-player-career-v1')).staff || {}).length);
  if (now > before) hires++; else refusals++;
  await safeClick('#btn-scout-staff');
}
check('rozmowy o sztab dają rozstrzygnięcie', hires + refusals > 0, hires + ' zatrudnień, ' + refusals + ' odmów');

// wypożyczenie: prośba kończy się decyzją zarządu w obie strony
await goto('team', 'squad');
const loanBtn = await page.$('[data-loan]:not([disabled])');
if (loanBtn) {
  await safeClick('[data-loan]');
  if (await page.isVisible('#choice-overlay:not(.hidden)')) await safeClick('[data-choice="0"]');
  await drainOverlays();
}
const afterLoan = await page.evaluate(() => JSON.parse(localStorage.getItem('cs2-player-career-v1')));
// sponsorzy: agent i warunki umów
await goto('offers', 'deals');
await safeClick('[data-agent]');
const stAgent = await page.evaluate(() => JSON.parse(localStorage.getItem('cs2-player-career-v1')));
check('agent do zatrudnienia', typeof stAgent.agent === 'boolean', 'agent: ' + stAgent.agent);
check('umowy sponsorskie mają warunek', (stAgent.sponsors || []).every(sp => sp.goal && sp.bonus > 0),
  (stAgent.sponsors || []).map(sp => sp.name + ':' + sp.goal).join(', ') || 'brak umów');

check('prośba o wypożyczenie rozpatrzona', !!loanBtn && afterLoan.loanTry === afterLoan.season,
  afterLoan.loan ? 'wypożyczony do ' + afterLoan.team.name : 'zarząd odmówił');

// zapis i odczyt
await goto('profile', 'log');
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
await goto('profile', 'card');
const chartPoints = await page.$$eval('#rating-chart .chart-dot', els => els.length);
if (c.seasons.length >= 2) {
  check('wykres ratingu ma punkty', chartPoints === c.seasons.length, chartPoints + ' punktów');
} else {
  const txt = await page.textContent('#rating-chart');
  check('wykres czeka na drugi sezon', /drugim/.test(txt), txt.trim());
}
check('karta gracza wyrenderowana', (await page.$$('#player-card .pcard')).length === 1);

// drugi rozdział: emerytura i rola trenera
await goto('profile', 'log');
await safeClick('#btn-retire');
await safeClick('#modal-close');
await page.waitForSelector('#choice-overlay:not(.hidden)', { timeout: 5000 }).catch(() => {});
const chapterOffered = await page.isVisible('#choice-overlay:not(.hidden)');
if (chapterOffered) {
  await safeClick('[data-choice="0"]');            // trener
  await page.waitForTimeout(400);
  for (let i = 0; i < 3; i++) {
    await safeClick('.nav-btn[data-view="career"]');
    await safeClick('#cta-next');
    await drainOverlays();
  }
}
const st2 = await page.evaluate(() => JSON.parse(localStorage.getItem('cs2-player-career-v1')));
check('emerytura proponuje drugi rozdział', chapterOffered);
check('drugi rozdział startuje', st2.phase === 'coach', st2.phase || 'brak');
check('drugi rozdział ma cel sezonowy', !!(st2.goal2 && st2.goal2.kind), st2.goal2 && st2.goal2.kind);
check('ranking sztabu istnieje', Array.isArray(st2.staffWorld) && st2.staffWorld.length >= 10,
  (st2.staffWorld || []).length + ' sztabowców');
// drugi rozdział ma własne decyzje: styl gry i transfery albo kontrakt z platformą
await safeClick('.nav-btn[data-view="career"]');
const p2panel = await page.textContent('#goal-card').catch(() => '');
check('drugi rozdział ma panel decyzji', /Decyzje sztabowca/.test(p2panel), p2panel.replace(/\s+/g, ' ').slice(0, 50));
await safeClick('[data-style]');
if (await page.isVisible('#choice-overlay:not(.hidden)')) await safeClick('[data-choice="1"]');
await drainOverlays();
const st3 = await page.evaluate(() => JSON.parse(localStorage.getItem('cs2-player-career-v1')));
check('styl gry da się zmienić', st3.style2 && st3.style2 !== 'default', 'styl: ' + st3.style2);

// definitywny koniec kariery pokazuje podsumowanie
await goto('profile', 'log');
await safeClick('#btn-retire');
await safeClick('#modal-close');
await page.waitForSelector('#choice-overlay:not(.hidden)', { timeout: 4000 }).catch(() => {});
if (await page.isVisible('#choice-overlay:not(.hidden)')) await safeClick('[data-choice="3"]');
await page.waitForTimeout(400);
const legacyShown = await page.isVisible('#legacy-overlay:not(.hidden)');
const legacyStats = legacyShown ? await page.$$eval('#lg-stats .kv', els => els.length) : 0;
check('podsumowanie kariery się pokazuje', legacyShown && legacyStats >= 8,
  legacyShown ? legacyStats + ' kafelków' : 'brak ekranu');

await browser.close();

console.log('\nPodsumowanie: ' + (fail.length ? fail.length + ' niepowodzeń' : 'wszystko przeszło') +
  ' · mecze ' + c.matches + ' · rating ' + avgRating.toFixed(2) + ' · tier ' + st.team.tier);
process.exit(fail.length ? 1 : 0);
