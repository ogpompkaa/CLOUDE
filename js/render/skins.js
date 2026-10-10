/*
 * REJESTR SKÓREK (zestawów wyglądu psa)
 *
 * Każda skórka to folder assets/skins/<id>/ z plikiem skin.js, który wywołuje
 *   Pies.skins.register({ id: '<id>', ... })
 * oraz z grafikami, do których prowadzą ścieżki w konfiguracji.
 * Lista ładowanych skórek jest w assets/skins/manifest.js.
 *
 * Rejestr: sprawdza konfigurację (błędy/ostrzeżenia), uzupełnia wartości domyślne,
 * wczytuje grafiki i udostępnia skórkę rendererom. Nic tutaj nie dotyka stanu gry.
 */
(function (P) {
  'use strict';

  const U = P.util;
  const S = P.DogStates;
  const map = {};
  const order = [];

  const DEFAULT_MOTION = { body: 1, head: 1, ears: 1, tail: 1, legs: 1 };

  function isPoint(v) { return Array.isArray(v) && v.length === 2 && v.every((n) => typeof n === 'number' && isFinite(n)); }

  /* ------------------------------ walidacja ------------------------------ */
  function validate(cfg) {
    const errors = [];
    const warnings = [];
    if (!cfg || typeof cfg !== 'object') return { errors: ['Konfiguracja skórki nie jest obiektem.'], warnings };
    if (!cfg.id || typeof cfg.id !== 'string') errors.push('Brak pola `id` (tekstowy identyfikator skórki).');
    if (cfg.renderer !== 'rig' && cfg.renderer !== 'frames') errors.push('Pole `renderer` musi mieć wartość "rig" (warstwy) albo "frames" (klatki).');
    if (!cfg.fit || !(cfg.fit.shoulderHeightPx > 0)) errors.push('Brak `fit.shoulderHeightPx` – wysokości w kłębie w pikselach grafiki (potrzebne do skalowania).');

    if (cfg.renderer === 'rig') {
      const parts = cfg.parts || {};
      if (!parts.body) errors.push('Skórka warstwowa musi mieć część `body` (tułów).');
      for (const id of Object.keys(parts)) {
        const p = parts[id];
        const where = 'parts.' + id;
        if (!p.src && !p.variants) errors.push(where + ': brak `src` albo `variants`.');
        if (!isPoint(p.pivot)) errors.push(where + ': `pivot` musi być punktem [x, y] w pikselach obrazka.');
        if (p.at !== undefined && !isPoint(p.at)) errors.push(where + ': `at` musi być punktem [x, y].');
        if (p.parent && p.parent !== 'root' && !parts[p.parent]) errors.push(where + ': rodzic "' + p.parent + '" nie istnieje.');
        if (S.bones.indexOf(id) < 0) warnings.push(where + ': nazwa spoza kanonicznego szkieletu – animacje nie będą jej poruszać (to dozwolone dla ozdób).');
        if (p.variants && !p.expression) warnings.push(where + ': ma `variants`, ale brak `expression` ("eyes" albo "mouth").');
      }
      for (const b of ['head', 'tail', 'earNear', 'frontLegNear', 'backLegNear']) {
        if (!parts[b]) warnings.push('Brak części `' + b + '` – odpowiadające jej ruchy zostaną pominięte.');
      }
      // cykle w hierarchii
      for (const id of Object.keys(parts)) {
        const seen = [];
        let cur = id;
        while (cur && cur !== 'root' && parts[cur]) {
          if (seen.indexOf(cur) >= 0) { errors.push('Cykl w hierarchii części przy `' + id + '`.'); break; }
          seen.push(cur);
          cur = parts[cur].parent;
        }
      }
    }

    if (cfg.renderer === 'frames') {
      const anims = cfg.animations || {};
      if (!anims.idle) errors.push('Skórka klatkowa musi mieć co najmniej animację `idle`.');
      if (!isPoint((cfg.fit || {}).origin)) errors.push('Skórka klatkowa wymaga `fit.origin` – punktu na ziemi pod środkiem psa (w pikselach klatki).');
      for (const id of Object.keys(anims)) {
        if (!S.byId[id]) warnings.push('animations.' + id + ': nieznany stan – nie zostanie użyty.');
        const a = anims[id];
        if (!a.frames && !a.sheet) errors.push('animations.' + id + ': brak `frames` (lista plików) albo `sheet` (arkusz klatek).');
      }
      const missing = S.ids.filter((s) => !anims[s]);
      if (missing.length) warnings.push('Brakujące animacje (użyte zostaną zastępcze): ' + missing.join(', ') + '.');
    }

    if (cfg.anchors) {
      for (const k of Object.keys(cfg.anchors)) {
        const a = cfg.anchors[k];
        if (!isPoint(a.at)) errors.push('anchors.' + k + ': `at` musi być punktem [x, y].');
        if (cfg.renderer === 'rig' && a.part && !(cfg.parts || {})[a.part]) errors.push('anchors.' + k + ': część "' + a.part + '" nie istnieje.');
      }
    }
    return { errors, warnings };
  }

  /* --------------------------- normalizacja --------------------------- */
  function normalize(cfg) {
    const skin = U.deepClone(cfg);
    skin.basePath = skin.basePath || 'assets/skins/' + skin.id + '/';
    skin.name = skin.name || skin.id;
    skin.facing = skin.facing === 'left' ? 'left' : 'right';
    skin.motion = Object.assign({}, DEFAULT_MOTION, skin.motion || {});
    skin.proportions = Object.assign({}, P.DogBody.DEFAULT_PROPORTIONS, skin.proportions || {});
    skin.zones = P.DogBody.zones(skin.zones);
    skin.anchors = skin.anchors || {};
    skin.landmarks = skin.landmarks || {};
    skin.fit = Object.assign({ origin: [0, 0] }, skin.fit);

    if (skin.renderer === 'rig') {
      const parts = skin.parts;
      for (const id of Object.keys(parts)) {
        const p = parts[id];
        p.id = id;
        p.parent = p.parent || 'root';
        p.at = p.at || [0, 0];
        p.z = typeof p.z === 'number' ? p.z : 0;
        p.scale = p.scale === undefined ? 1 : p.scale;
        p.rotation = p.rotation || 0;
        p.motion = p.motion === undefined ? 1 : p.motion;
        p.contacts = p.contacts || [];
        p.children = [];
      }
      for (const id of Object.keys(parts)) {
        const p = parts[id];
        if (p.parent !== 'root') parts[p.parent].children.push(id);
      }
      skin.rootParts = Object.keys(parts).filter((id) => parts[id].parent === 'root');
      skin.drawOrder = Object.keys(parts).sort((a, b) => parts[a].z - parts[b].z);
      // punkt obrotu całego psa (np. przy turlaniu) – domyślnie środek tułowia
      if (!isPoint(skin.fit.rotationPivot)) {
        const b = parts.body;
        skin.fit.rotationPivot = b.parent === 'root' ? b.at.slice() : [0, -skin.fit.shoulderHeightPx * 0.75];
      }
    }

    if (skin.renderer === 'frames') {
      skin.frameSize = skin.frameSize || null;
      for (const id of Object.keys(skin.animations)) {
        const a = skin.animations[id];
        a.fps = a.fps || 8;
        a.loop = a.loop !== false;
      }
    }
    return skin;
  }

  /* ------------------------------ rejestr ------------------------------ */
  function register(cfg) {
    const report = validate(cfg);
    const id = cfg && cfg.id;
    if (report.errors.length) {
      console.error('[Pies] Skórka "' + id + '" odrzucona:\n - ' + report.errors.join('\n - '));
      map[id || '?'] = { id: id || '?', invalid: true, report };
      return null;
    }
    if (report.warnings.length) console.info('[Pies] Skórka "' + id + '" – uwagi:\n - ' + report.warnings.join('\n - '));
    const skin = normalize(cfg);
    skin.report = report;
    skin.images = {};
    skin.loaded = false;
    if (!map[id]) order.push(id);
    map[id] = skin;
    return skin;
  }

  function get(id) { const s = map[id]; return s && !s.invalid ? s : null; }
  function list() { return order.map((id) => map[id]).filter((s) => s && !s.invalid); }
  function invalidList() { return Object.keys(map).map((k) => map[k]).filter((s) => s.invalid); }

  /** Czy skórka ma animację dla danego stanu (bez zastępstw). */
  function hasAnimation(skin, stateId) {
    if (!skin) return false;
    if (skin.renderer === 'rig') return !(skin.disabledStates || []).includes(stateId);
    return !!skin.animations[stateId];
  }

  /* --------------------------- wczytywanie --------------------------- */
  function loadScript(src) {
    return new Promise((resolve) => {
      const el = document.createElement('script');
      el.src = src;
      el.async = false;
      el.onload = () => resolve(true);
      el.onerror = () => { console.warn('[Pies] Nie udało się wczytać ' + src); resolve(false); };
      document.head.appendChild(el);
    });
  }

  function loadManifest(ids, root) {
    const base = root || 'assets/skins/';
    return Promise.all(ids.map((id) => loadScript(base + id + '/skin.js')));
  }

  function collectSources(skin) {
    const out = [];
    const add = (s) => { if (s && out.indexOf(s) < 0) out.push(s); };
    if (skin.renderer === 'rig') {
      for (const id of Object.keys(skin.parts)) {
        const p = skin.parts[id];
        add(p.src);
        if (p.variants) Object.keys(p.variants).forEach((v) => add(p.variants[v]));
      }
    } else {
      for (const id of Object.keys(skin.animations)) {
        const a = skin.animations[id];
        if (a.frames) a.frames.forEach(add);
        if (a.sheet) add(a.sheet.src);
      }
    }
    return out;
  }

  function preload(skin) {
    if (skin.loaded) return Promise.resolve(skin);
    if (skin._loading) return skin._loading;
    const sources = collectSources(skin);
    skin.missingImages = [];
    skin._loading = Promise.all(sources.map((src) => new Promise((resolve) => {
      const img = new Image();
      img.decoding = 'async';
      img.onload = () => { skin.images[src] = img; resolve(); };
      img.onerror = () => { skin.images[src] = null; skin.missingImages.push(src); resolve(); };
      img.src = skin.basePath + src;
    }))).then(() => {
      skin.loaded = true;
      if (skin.missingImages.length) console.warn('[Pies] Skórka "' + skin.id + '": brak plików: ' + skin.missingImages.join(', '));
      return skin;
    });
    return skin._loading;
  }

  /* -------- pamięć podręczna rastrów (ostre SVG i szybkie rysowanie dużych zdjęć) -------- */
  const rasterCache = new Map();
  function raster(img, w, h, scale) {
    if (!img) return null;
    // kubełki skali co 2^(1/4), żeby nie tworzyć nowych bitmap przy każdej zmianie rozmiaru
    const bucket = Math.pow(2, Math.ceil(Math.log2(Math.max(scale, 0.05)) * 4) / 4);
    const tw = Math.max(1, Math.round(w * bucket));
    const th = Math.max(1, Math.round(h * bucket));
    if (tw > 4096 || th > 4096) return img;
    const key = img.src + '|' + tw + 'x' + th;
    let c = rasterCache.get(key);
    if (c) return c;
    c = document.createElement('canvas');
    c.width = tw;
    c.height = th;
    const g = c.getContext('2d');
    g.imageSmoothingEnabled = true;
    g.imageSmoothingQuality = 'high';
    const nw = img.naturalWidth || w;
    const nh = img.naturalHeight || h;
    const isVector = /\.svg(\?|$)/i.test(img.src);
    if (!isVector && (nw > tw * 2 || nh > th * 2)) {
      // stopniowe zmniejszanie dużych zdjęć – mniej ząbków i migotania
      let src = img, sw = nw, sh = nh;
      while (sw > tw * 2 && sh > th * 2) {
        const step = document.createElement('canvas');
        step.width = Math.round(sw / 2);
        step.height = Math.round(sh / 2);
        const sg = step.getContext('2d');
        sg.imageSmoothingQuality = 'high';
        sg.drawImage(src, 0, 0, step.width, step.height);
        src = step; sw = step.width; sh = step.height;
      }
      g.drawImage(src, 0, 0, tw, th);
    } else {
      g.drawImage(img, 0, 0, tw, th);
    }
    if (rasterCache.size > 600) rasterCache.clear();
    rasterCache.set(key, c);
    return c;
  }

  P.skins = {
    register, get, list, invalidList, validate, hasAnimation,
    loadManifest, preload, raster,
    defaultId: () => (order.find((id) => get(id)) || null),
  };
})(window.Pies = window.Pies || {});
