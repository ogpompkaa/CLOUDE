#!/usr/bin/env node
/*
 * Generator TYMCZASOWYCH grafik psa (styl 2D cartoon) – osobny plik SVG dla każdej części ciała.
 *
 * Gra NIE uruchamia tego skryptu. Czyta wyłącznie gotowe pliki z assets/skins/<id>/parts/
 * oraz konfigurację assets/skins/<id>/skin.js – dokładnie tak samo, jak przeczyta
 * grafiki prawdziwego psa. Generator służy tylko do odtworzenia/poprawek placeholderów:
 *
 *   node tools/generate-cartoon-skins.mjs
 *
 * Uwaga: zmiana geometrii tutaj wymaga przeniesienia punktów (pivot/at/contacts) do skin.js.
 */
import { mkdirSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const ROOT = join(dirname(fileURLToPath(import.meta.url)), '..');
const OUTLINE = '#3a2418';
const SW = 5; // grubość konturu w pikselach grafiki

function svg(w, h, body, defs = '') {
  return `<svg xmlns="http://www.w3.org/2000/svg" width="${w}" height="${h}" viewBox="0 0 ${w} ${h}">\n<defs>${defs}</defs>\n${body}\n</svg>\n`;
}

/** Część z kształtem, wypełnieniem bazowym, dodatkowymi plamami (przyciętymi do kształtu) i cieniowaniem. */
function shapePart({ w, h, shape, stroke, base, layers = [], shade = 0.24, light = 0.14, outline = OUTLINE, strokeWidth = SW }) {
  const defs = `
<clipPath id="c"><path d="${shape}"/></clipPath>
<linearGradient id="shade" x1="0" y1="0" x2="0" y2="1">
  <stop offset="0" stop-color="#fff" stop-opacity="${light}"/>
  <stop offset="0.45" stop-color="#fff" stop-opacity="0"/>
  <stop offset="1" stop-color="#3a1a05" stop-opacity="${shade}"/>
</linearGradient>`;
  const body = `<path d="${shape}" fill="${base}"/>
<g clip-path="url(#c)">
${layers.join('\n')}
<rect x="0" y="0" width="${w}" height="${h}" fill="url(#shade)"/>
</g>
<path d="${stroke || shape}" fill="none" stroke="${outline}" stroke-width="${strokeWidth}" stroke-linejoin="round" stroke-linecap="round"/>`;
  return svg(w, h, body, defs);
}

/* ============================== PALETY ============================== */
const SKINS = {
  'cartoon-beagle': {
    ears: 'floppy', tail: 'beagle', eyes: 'brown', markings: 'beagle', legExtra: 0,
    c: {
      coat: '#d98b45', coatLight: '#f0ae68', coatDark: '#b9692e',
      saddle: '#4b3324', saddleLight: '#6c4a35',
      white: '#fbf2e4', whiteShade: '#e8d6bd',
      ear: '#a65b27', earFar: '#76401c', earInner: '#8a4a1f',
      legFarTint: '#c27a3c', whiteFar: '#dccab0',
      nose: '#2a1a12', iris: '#3b2414',
    },
  },
  'cartoon-husky': {
    ears: 'pointy', tail: 'curl', eyes: 'blue', markings: 'husky', legExtra: 16,
    c: {
      coat: '#8f98a8', coatLight: '#b4bccb', coatDark: '#6a7383',
      saddle: '#4a5262', saddleLight: '#5f687a',
      white: '#f7f8fb', whiteShade: '#dde1ea',
      ear: '#59616f', earFar: '#454c58', earInner: '#f0b9bd',
      legFarTint: '#7b8494', whiteFar: '#d3d8e2',
      nose: '#1f2128', iris: '#3f95e0',
    },
  },
};

/* ============================== CZĘŚCI ============================== */
function body(s) {
  const { c } = s;
  const shape = 'M 62 48 C 110 34 205 30 252 40 C 292 48 318 80 316 110 C 314 142 292 162 258 164 C 222 166 204 152 172 152 C 132 152 104 162 72 160 C 32 158 12 130 14 102 C 16 72 34 54 62 48 Z';
  const layers = [
    `<path d="M 24 62 C 60 34 150 24 266 38 C 272 64 252 94 206 100 C 160 106 92 102 46 94 C 20 88 14 74 24 62 Z" fill="${c.saddle}"/>`,
    `<ellipse cx="150" cy="46" rx="80" ry="9" fill="${c.saddleLight}" opacity="0.8"/>`,
    `<ellipse cx="302" cy="128" rx="44" ry="50" fill="${c.white}"/>`,
    `<ellipse cx="214" cy="172" rx="78" ry="22" fill="${c.white}"/>`,
    s.markings === 'husky' ? `<ellipse cx="60" cy="150" rx="50" ry="22" fill="${c.white}"/>` : '',
    `<path d="M 296 146 l 5 9 M 286 152 l 4 9 M 306 138 l 6 7" stroke="${c.whiteShade}" stroke-width="3" stroke-linecap="round"/>`,
  ];
  return shapePart({ w: 330, h: 180, shape, base: c.coat, layers });
}

function neck(s) {
  const { c } = s;
  const shape = 'M 18 118 C 12 84 16 46 32 26 C 48 8 80 8 94 26 C 106 46 104 84 98 118 Z';
  const stroke = 'M 18 118 C 12 84 16 46 32 26 C 48 8 80 8 94 26 C 106 46 104 84 98 118';
  const layers = [
    `<path d="M 0 0 L 40 0 C 30 40 30 80 34 130 L 0 130 Z" fill="${s.markings === 'husky' ? c.saddle : c.coatDark}" opacity="${s.markings === 'husky' ? 1 : 0.55}"/>`,
    `<ellipse cx="98" cy="84" rx="28" ry="50" fill="${c.white}"/>`,
  ];
  return shapePart({ w: 110, h: 130, shape, stroke, base: c.coat, layers, shade: 0.18 });
}

function head(s) {
  const { c } = s;
  const shape = 'M 64 158 C 30 138 24 88 46 54 C 70 20 124 14 156 38 C 172 50 176 62 190 72 C 210 82 228 88 230 110 C 232 134 214 148 192 150 C 170 152 150 160 124 166 C 100 170 80 168 64 158 Z';
  const layers = [];
  if (s.markings === 'beagle') {
    layers.push(`<path d="M 138 26 C 150 44 160 62 176 72 C 196 84 220 90 234 104 L 240 170 L 128 176 C 150 150 150 120 140 96 C 132 74 128 46 138 26 Z" fill="${c.white}"/>`);
    layers.push(`<ellipse cx="80" cy="40" rx="50" ry="22" fill="${c.coatLight}" opacity="0.55"/>`);
  } else {
    // maska husky: ciemna czapka, jasny pysk, policzki i „brwi”
    layers.push(`<path d="M 20 20 L 200 20 C 170 60 150 70 120 80 C 90 90 60 100 20 110 Z" fill="${c.saddle}"/>`);
    layers.push(`<path d="M 104 92 C 130 86 160 74 186 74 C 214 80 236 92 236 120 L 236 180 L 60 180 C 70 140 84 104 104 92 Z" fill="${c.white}"/>`);
    layers.push(`<ellipse cx="150" cy="104" rx="30" ry="22" fill="${c.white}"/>`);
    layers.push(`<ellipse cx="146" cy="58" rx="9" ry="6" fill="${c.white}"/>`);
  }
  layers.push(`<ellipse cx="162" cy="128" rx="18" ry="10" fill="#ff7088" opacity="0.28"/>`);
  layers.push(`<ellipse cx="224" cy="98" rx="14.5" ry="12" fill="${c.nose}"/>`);
  layers.push(`<ellipse cx="219" cy="93" rx="5.5" ry="3.2" fill="#fff" opacity="0.55"/>`);
  return shapePart({ w: 240, h: 200, shape, base: c.coat, layers, shade: 0.2 });
}

function eyes(s, variant) {
  const { c } = s;
  const w = 60, h = 60;
  let body = '';
  const brow = (d) => `<path d="${d}" fill="none" stroke="${OUTLINE}" stroke-width="4" stroke-linecap="round" opacity="0.85"/>`;
  const openEye = () => {
    if (s.eyes === 'blue') {
      return `<ellipse cx="30" cy="31" rx="12.5" ry="14.5" fill="#fff" stroke="${OUTLINE}" stroke-width="3"/>
<ellipse cx="32" cy="32" rx="9" ry="11" fill="${c.iris}"/>
<ellipse cx="33" cy="33" rx="5" ry="6.5" fill="#14161c"/>
<circle cx="27" cy="26" r="4.2" fill="#fff"/><circle cx="36" cy="38" r="2" fill="#fff"/>`;
    }
    return `<ellipse cx="30" cy="31" rx="12.5" ry="14.5" fill="#24150e"/>
<ellipse cx="31" cy="33" rx="8.5" ry="10" fill="${c.iris}" opacity="0.9"/>
<ellipse cx="31" cy="33" rx="5.5" ry="6.5" fill="#140b06"/>
<circle cx="25" cy="25" r="5" fill="#fff"/><circle cx="35.5" cy="38" r="2.4" fill="#fff"/>`;
  };
  if (variant === 'open') body = openEye() + brow('M 17 11 Q 30 5 43 11');
  if (variant === 'sad') body = openEye() + brow('M 15 8 Q 28 9 43 1');
  if (variant === 'closed') body = `<path d="M 16 31 Q 30 41 44 31" fill="none" stroke="${OUTLINE}" stroke-width="4.5" stroke-linecap="round"/>` + brow('M 17 15 Q 30 10 43 15');
  if (variant === 'happy') body = `<path d="M 16 36 Q 30 19 44 36" fill="none" stroke="${OUTLINE}" stroke-width="4.5" stroke-linecap="round"/>` + brow('M 17 10 Q 30 3 43 10');
  return svg(w, h, body);
}

function mouth(s, variant) {
  const w = 100, h = 80;
  const st = `stroke="${OUTLINE}" stroke-width="4.5" stroke-linecap="round" stroke-linejoin="round"`;
  const tongueFill = '#ff7b8d';
  let body = '';
  if (variant === 'closed') {
    body = `<path d="M 84 4 L 84 10 C 84 20 74 26 62 24 C 50 22 42 15 30 17" fill="none" ${st}/>`;
  } else if (variant === 'open' || variant === 'tongue' || variant === 'bark') {
    const drop = variant === 'bark' ? 64 : variant === 'tongue' ? 50 : 46;
    const shape = `M 84 6 C 82 20 72 26 60 24 C 48 22 38 15 26 17 C 30 ${drop - 6} 58 ${drop + 4} 76 ${drop - 8} C 86 ${drop - 16} 87 24 84 6 Z`;
    body = `<clipPath id="m"><path d="${shape}"/></clipPath>
<path d="${shape}" fill="#5c1f26"/>
<g clip-path="url(#m)"><ellipse cx="60" cy="${drop}" rx="20" ry="11" fill="${tongueFill}"/><path d="M 30 16 C 50 26 70 24 84 10 L 84 0 L 26 0 Z" fill="#fff" opacity="0.9"/></g>
<path d="${shape}" fill="none" ${st}/>`;
    if (variant === 'tongue') {
      body += `<path d="M 50 ${drop - 10} C 48 ${drop + 8} 54 ${drop + 22} 64 ${drop + 22} C 74 ${drop + 22} 78 ${drop + 8} 74 ${drop - 10} Z" fill="${tongueFill}" ${st}/>
<path d="M 62 ${drop - 4} L 63 ${drop + 12}" stroke="#e0566c" stroke-width="3" stroke-linecap="round"/>`;
    }
  }
  return svg(w, h, body);
}

function tail(s) {
  const { c } = s;
  if (s.tail === 'curl') {
    const shape = 'M 20 150 C 6 110 4 60 18 30 C 30 6 62 0 68 20 C 72 34 58 42 48 34 C 40 54 40 100 50 150 Z';
    const layers = [
      `<path d="M 30 150 C 26 110 28 70 40 44 C 50 34 60 36 70 30 L 70 150 Z" fill="${c.white}" opacity="0.9"/>`,
      `<ellipse cx="52" cy="22" rx="22" ry="16" fill="${c.white}"/>`,
    ];
    return shapePart({ w: 76, h: 160, shape, base: c.coat, layers, shade: 0.18 });
  }
  const shape = 'M 18 150 C 12 110 14 60 26 16 C 28 8 38 8 40 16 C 46 60 48 110 44 150 Z';
  const layers = [
    `<ellipse cx="33" cy="22" rx="18" ry="34" fill="${c.white}"/>`,
    `<path d="M 0 60 C 20 70 20 120 16 160 L 0 160 Z" fill="${c.coatDark}" opacity="0.6"/>`,
  ];
  return shapePart({ w: 60, h: 160, shape, base: c.coat, layers, shade: 0.15 });
}

function ear(s, far) {
  const { c } = s;
  if (s.ears === 'pointy') {
    const shape = 'M 8 104 C 10 72 18 32 32 8 C 36 2 42 2 46 8 C 58 34 64 72 64 104 Z';
    const layers = [
      `<path d="M 20 102 C 22 78 28 46 38 24 C 46 46 52 78 52 102 Z" fill="${far ? '#c99a9e' : c.earInner}"/>`,
    ];
    return shapePart({ w: 72, h: 112, shape, base: far ? c.earFar : c.ear, layers, shade: 0.15, light: 0.1 });
  }
  const shape = 'M 24 12 C 52 2 72 20 72 54 C 74 96 70 126 56 144 C 46 156 26 154 18 138 C 8 106 10 56 14 30 C 16 18 18 14 24 12 Z';
  const layers = [
    `<path d="M 50 0 C 70 30 76 90 64 160 L 90 160 L 90 0 Z" fill="#000" opacity="0.14"/>`,
    `<path d="M 30 30 C 40 62 40 100 33 132" fill="none" stroke="#fff" stroke-opacity="0.16" stroke-width="7" stroke-linecap="round"/>`,
  ];
  return shapePart({ w: 90, h: 160, shape, base: far ? c.earFar : c.ear, layers, shade: 0.2, light: 0.1 });
}

/* ---- kończyny: górny segment (ramię/udo) i dolny (przedramię/podudzie + łapa) ---- */
function legUpper(s, kind, far) {
  const { c } = s;
  const tint = far ? c.legFarTint : c.coat;
  if (kind === 'front') {
    const shape = 'M 10 36 A 30 30 0 0 1 70 36 C 70 66 62 90 58 110 A 18 18 0 0 1 22 110 C 18 90 10 66 10 36 Z';
    const stroke = 'M 10 36 C 10 66 18 90 22 110 A 18 18 0 0 0 58 110 C 62 90 70 66 70 36';
    const layers = [`<rect x="0" y="86" width="80" height="60" fill="${far ? c.whiteFar : c.white}"/>`];
    if (s.markings === 'husky') layers.push(`<rect x="0" y="60" width="80" height="80" fill="${far ? c.whiteFar : c.white}"/>`);
    return shapePart({ w: 80, h: 140, shape, stroke: far ? shape : stroke, base: tint, layers, shade: 0.22 });
  }
  const shape = 'M 16 42 A 40 40 0 0 1 96 42 C 96 76 80 100 70 118 A 18 18 0 0 1 34 116 C 26 96 16 74 16 42 Z';
  const stroke = 'M 16 42 C 16 74 26 96 34 116 A 18 18 0 0 0 70 118 C 80 100 96 76 96 42';
  const layers = [
    `<ellipse cx="66" cy="38" rx="22" ry="16" fill="#fff" opacity="0.12"/>`,
    s.markings === 'husky' ? `<rect x="0" y="92" width="110" height="60" fill="${far ? c.whiteFar : c.white}"/>` : '',
  ];
  return shapePart({ w: 110, h: 150, shape, stroke: far ? shape : stroke, base: tint, layers, shade: 0.26 });
}

function legLower(s, kind, far) {
  const { c } = s;
  const e = s.legExtra;
  const H = 150 + e;
  const y = (v) => v + e; // wydłużenie przedramienia/podudzia
  const fill = far ? c.whiteFar : c.white;
  const shape = `M 19 18 C 19 40 20 70 20 ${y(92)} C 20 ${y(110)} 16 ${y(118)} 18 ${y(126)} C 14 ${y(136)} 18 ${y(144)} 28 ${y(144)} L 56 ${y(144)} C 68 ${y(144)} 74 ${y(136)} 70 ${y(126)} C 66 ${y(118)} 56 ${y(116)} 50 ${y(108)} C 48 ${y(84)} 49 50 49 18 A 15 15 0 0 0 19 18 Z`;
  const stroke = `M 19 18 C 19 40 20 70 20 ${y(92)} C 20 ${y(110)} 16 ${y(118)} 18 ${y(126)} C 14 ${y(136)} 18 ${y(144)} 28 ${y(144)} L 56 ${y(144)} C 68 ${y(144)} 74 ${y(136)} 70 ${y(126)} C 66 ${y(118)} 56 ${y(116)} 50 ${y(108)} C 48 ${y(84)} 49 50 49 18`;
  const layers = [];
  if (kind === 'back' && s.markings === 'beagle') layers.push(`<rect x="0" y="0" width="80" height="34" fill="${far ? c.legFarTint : c.coat}"/>`);
  layers.push(`<path d="M 50 ${y(134)} L 52 ${y(143)} M 60 ${y(131)} L 62 ${y(142)}" stroke="${OUTLINE}" stroke-width="3" stroke-linecap="round" opacity="0.7"/>`);
  return shapePart({ w: 80, h: H, shape, stroke, base: fill, layers, shade: 0.2, light: 0.08 });
}

/* ============================== ZAPIS ============================== */
let count = 0;
for (const [id, s] of Object.entries(SKINS)) {
  const dir = join(ROOT, 'assets', 'skins', id, 'parts');
  mkdirSync(dir, { recursive: true });
  const files = {
    'body.svg': body(s),
    'neck.svg': neck(s),
    'head.svg': head(s),
    'tail.svg': tail(s),
    'ear-near.svg': ear(s, false),
    'ear-far.svg': ear(s, true),
    'front-leg-near.svg': legUpper(s, 'front', false),
    'front-leg-far.svg': legUpper(s, 'front', true),
    'front-paw-near.svg': legLower(s, 'front', false),
    'front-paw-far.svg': legLower(s, 'front', true),
    'back-leg-near.svg': legUpper(s, 'back', false),
    'back-leg-far.svg': legUpper(s, 'back', true),
    'back-paw-near.svg': legLower(s, 'back', false),
    'back-paw-far.svg': legLower(s, 'back', true),
  };
  for (const v of ['open', 'closed', 'happy', 'sad']) files[`eyes-${v}.svg`] = eyes(s, v);
  for (const v of ['closed', 'open', 'tongue', 'bark']) files[`mouth-${v}.svg`] = mouth(s, v);
  for (const [name, content] of Object.entries(files)) {
    const p = join(dir, name);
    mkdirSync(dirname(p), { recursive: true });
    writeFileSync(p, content);
    count++;
  }
}
console.log(`Zapisano ${count} plików SVG.`);
