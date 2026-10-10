/*
 * START GRY – łączy logikę (Game), wygląd (DogView + skórki), scenę i interfejs.
 * To jedyne miejsce, w którym skórka zamienia się w logiczne proporcje dla gry
 * (game.setBody). Gra nigdy nie dostaje grafik ani wymiarów obrazków.
 */
(function (P) {
  'use strict';

  const U = P.util;
  const $ = (s) => document.querySelector(s);

  const canvas = $('#scene');
  const ctx = canvas.getContext('2d');
  const scene = new P.Scene();
  const dogView = new P.DogView();
  let game = null;
  let ui = null;
  let lastT = 0;
  let snapCamera = true;
  let uiTimer = 0;
  let resetting = false;

  /* ======================= studio wyglądu ======================= */
  const studio = {
    active: false, state: 'idle', debug: true, accessories: true, cycle: false, facing: 1, t: 0, cycleT: 0,
    open() { this.active = true; this.t = 0; this.state = 'idle'; dogView.debug = this.debug; $('#studio-hud').classList.remove('hidden'); updateStudioHud(); },
    close() { this.active = false; dogView.debug = false; $('#studio-hud').classList.add('hidden'); },
    setState(id) { this.state = id; this.t = 0; updateStudioHud(); },
    set(key, v) { this[key] = v; if (key === 'debug') dogView.debug = v; },
    flip() { this.facing *= -1; },
    snapshot() {
      const one = P.DogStates.isOneShot(this.state);
      const dur = this.state === 'roll' ? 1.2 : 1.3;
      return {
        x: game.dog.x, facing: this.facing, anim: this.state,
        animTime: one ? (this.t % (dur + 0.6)) : this.t, animDuration: one ? dur : 0,
        speed: this.state === 'walk' ? 0.85 : this.state === 'run' ? 2.5 : 0, lift: 0,
        carrying: this.accessories ? (this.state === 'run' || this.state === 'walk' || this.state === 'sit' ? 'ball' : null) : null,
        equipment: this.accessories ? game.state.inventory.equipped : {},
        mood: { happy: 0.7, energy: 0.8 }, expression: null,
      };
    },
  };
  function updateStudioHud() {
    const r = dogView.skin ? dogView.resolveAnim(studio.state) : studio.state;
    $('#studio-state').textContent = P.DogStates.label(studio.state) + (r !== studio.state ? ' → ' + r : '');
  }

  /* ======================= skórki ======================= */
  async function switchSkin(id, silent) {
    let skin = P.skins.get(id);
    if (!skin) {
      skin = P.skins.get(P.skins.defaultId());
      if (!silent) ui.toast('Wygląd „' + id + '” jest niedostępny – używam domyślnego.', 'warn');
    }
    await P.skins.preload(skin);
    dogView.setSkin(skin);
    game.setBody(dogView.body);
    renderAvatar();
    if (studio.active) updateStudioHud();
    if (!silent) ui.toast('Nowy wygląd: ' + skin.name + '. Imię, poziom i więź bez zmian.', 'reward');
    return skin;
  }

  /** Miniatura skórki na osobnym canvasie (karta w panelu, avatar, okno imienia). */
  async function renderSkinThumb(cv, skin, opts) {
    const o = opts || {};
    await P.skins.preload(skin);
    const dpr = Math.min(window.devicePixelRatio || 1, 2);
    const w = cv.clientWidth || cv.width, h = cv.clientHeight || cv.height;
    cv.width = Math.round(w * dpr);
    cv.height = Math.round(h * dpr);
    const c = cv.getContext('2d');
    const v = new P.DogView();
    v.setSkin(skin);
    const snap = { x: 0, facing: 1, anim: o.anim || 'sit', animTime: 0, animDuration: 0, speed: 0, lift: 0, mood: { happy: 0.9, energy: 1 }, equipment: game ? game.state.inventory.equipped : {}, carrying: null };
    for (let i = 0; i < 40; i++) v.update(1 / 60, snap);
    let ppu, cx, gy;
    if (o.head) {
      const head = v.landmark('head');
      ppu = h / (0.85 * v.body.height);
      cx = w / 2 - head[0] * ppu;
      gy = h / 2 - head[1] * ppu;
    } else {
      ppu = Math.min(h / (v.body.headTop * 1.2 + 0.15), w / (v.body.length * 1.9));
      cx = w / 2;
      gy = h - 0.1 * ppu;
    }
    c.clearRect(0, 0, cv.width, cv.height);
    v.draw(c, [ppu * dpr, 0, 0, ppu * dpr, cx * dpr, gy * dpr], { noShadow: !!o.head });
  }

  function renderAvatar() {
    const box = $('#avatar');
    let cv = box.querySelector('canvas');
    if (!cv) { cv = document.createElement('canvas'); box.appendChild(cv); }
    renderSkinThumb(cv, dogView.skin, { head: true, anim: 'idle' });
  }

  const host = {
    studio,
    skins: () => P.skins.list(),
    invalidSkins: () => P.skins.invalidList(),
    currentSkin: () => dogView.skin,
    currentSkinId: () => (dogView.skin ? dogView.skin.id : null),
    selectSkin: (id) => game.setSkin(id),
    renderSkinThumb: (cv, skin) => renderSkinThumb(cv, skin),
    resolveAnim: (id) => dogView.resolveAnim(id),
    resetGame() {
      resetting = true; // blokuje zapis przy zamykaniu strony, żeby nie przywrócić starego stanu
      P.Save.reset();
      location.reload();
    },
  };

  /* ======================= pętla ======================= */
  function resize() {
    const stage = $('#stage');
    const dpr = Math.min(window.devicePixelRatio || 1, 2.5);
    const w = stage.clientWidth, h = stage.clientHeight;
    canvas.width = Math.round(w * dpr);
    canvas.height = Math.round(h * dpr);
    scene.layout(w, h, dpr);
    snapCamera = true;
  }

  function resolveFx(at) {
    const d = game.dog;
    if (at === 'paws') return [d.x + d.facing * dogView.body.halfLength * 0.8, -0.04];
    return dogView.landmark(at) || [d.x, -dogView.body.height];
  }

  function frame(t) {
    const dt = Math.min(0.05, Math.max(0, (t - lastT) / 1000 || 0));
    lastT = t;
    let snap;
    if (studio.active) {
      studio.t += dt;
      if (studio.cycle) {
        studio.cycleT += dt;
        if (studio.cycleT > 2.8) {
          studio.cycleT = 0;
          const ids = P.DogStates.ids;
          studio.setState(ids[(ids.indexOf(studio.state) + 1) % ids.length]);
          if (ui.sheetId === 'studio') ui.rerender();
        }
      }
      snap = studio.snapshot();
    } else {
      game.update(dt);
      snap = game.snapshot();
    }
    dogView.update(dt, snap);
    scene.zoomTarget = studio.active ? 1.6 : 1;
    scene.follow(game.dog.x, dt, game.scene, game.room.width, snapCamera);
    snapCamera = false;
    scene.updateParticles(dt);

    ctx.setTransform(1, 0, 0, 1, 0, 0);
    ctx.clearRect(0, 0, canvas.width, canvas.height);
    scene.drawBackground(ctx, game);
    const cam = scene.matrix();
    ctx.setTransform(cam[0], cam[1], cam[2], cam[3], cam[4], cam[5]);
    scene.drawBall(ctx, game, dogView.body);
    dogView.draw(ctx, cam);
    if (game.scene === 'park' && !studio.active) {
      ctx.setTransform(cam[0], cam[1], cam[2], cam[3], cam[4], cam[5]);
      scene.drawLeash(ctx, dogView.anchorWorld('neck'));
    }
    scene.drawLighting(ctx, game);
    scene.drawParticles(ctx);

    uiTimer -= dt;
    if (uiTimer <= 0) { ui.update(); uiTimer = 0.2; }
    requestAnimationFrame(frame);
  }

  /* ======================= dotyk ======================= */
  function worldFromEvent(e) {
    const r = canvas.getBoundingClientRect();
    return scene.screenToWorld(e.clientX - r.left, e.clientY - r.top);
  }

  function setupInput() {
    let stroke = null;
    canvas.addEventListener('pointerdown', (e) => {
      if (studio.active) return;
      const [wx, wy] = worldFromEvent(e);
      const b = game.ball;
      const br = 0.085 * dogView.body.height;
      if (game.scene === 'home' && b.state === 'floor' && Math.hypot(wx - b.x, wy - (b.y - br)) < Math.max(0.28, br * 2.5)) {
        game.throwBall();
        ui.pulseHint();
        return;
      }
      const zone = dogView.hitTest(wx, wy);
      if (zone) {
        game.pet(zone);
        ui.pulseHint();
        stroke = { zone, dist: 0, x: e.clientX, y: e.clientY };
        try { canvas.setPointerCapture(e.pointerId); } catch (err) { /* brak wsparcia */ }
        return;
      }
      if (wy > -0.35 && game.scene === 'home') game.callTo(wx);
    });
    canvas.addEventListener('pointermove', (e) => {
      const [wx, wy] = worldFromEvent(e);
      canvas.style.cursor = dogView.hitTest(wx, wy) ? 'grab' : 'pointer';
      if (!stroke) return;
      stroke.dist += Math.hypot(e.clientX - stroke.x, e.clientY - stroke.y);
      stroke.x = e.clientX;
      stroke.y = e.clientY;
      if (stroke.dist > 110) {
        stroke.dist = 0;
        const zone = dogView.hitTest(wx, wy) || stroke.zone;
        game.pet(zone === 'tail' ? 'back' : zone);
      }
    });
    const end = () => { stroke = null; };
    canvas.addEventListener('pointerup', end);
    canvas.addEventListener('pointercancel', end);
  }

  /* ======================= zapis ======================= */
  function saveNow() { if (game && !resetting) P.Save.save(game.state); }

  /* ======================= start ======================= */
  async function boot() {
    const loaded = P.Save.load();
    const state = loaded.data;
    const awaySec = (Date.now() - (state.lastSeen || Date.now())) / 1000;
    game = new P.Game(state);
    ui = new P.UI(game, host);
    ui.build();
    resize();
    new (window.ResizeObserver || function () { return { observe() {} }; })(resize).observe($('#stage'));
    window.addEventListener('resize', resize);

    await P.skins.loadManifest(P.skinManifest || []);
    if (!P.skins.list().length) {
      $('#loading').textContent = 'Brak poprawnej skórki psa – sprawdź assets/skins/manifest.js i konsolę.';
      return;
    }
    const wanted = state.appearance.skinId || P.skins.defaultId();
    if (!state.appearance.skinId) state.appearance.skinId = wanted;
    await switchSkin(wanted, !!P.skins.get(wanted));

    game.on('skin', (id) => switchSkin(id));
    game.on('toast', (m) => ui.toast(m.text, m.kind));
    game.on('fx', (f) => scene.spawn(f.type, resolveFx(f.at)));
    game.on('scene', () => { snapCamera = true; scene.particles = []; ui.update(); });
    game.on('levelup', (l) => {
      ui.toast('Poziom ' + l.level + '! +15 monet' + (l.unlocked.length ? ' · Nowa sztuczka: ' + l.unlocked.join(', ') : ''), 'level');
      scene.spawn('star', resolveFx('headTop'));
    });
    game.on('equip', () => renderAvatar());

    if (!loaded.fresh) game.applyOffline(awaySec);
    setupInput();
    setTimeout(() => ui.pulseHint(), 9000);
    $('#app').classList.remove('booting');
    requestAnimationFrame((t) => { lastT = t; requestAnimationFrame(frame); });

    const start = () => {
      if (game.claimDaily()) ui.toast('Codzienna nagroda: +20 monet!', 'reward');
      if (!loaded.fresh && awaySec > 600) game.greet(awaySec / 60);
    };
    if (!state.profile.name) {
      renderSkinThumb($('#name-preview'), dogView.skin, { anim: 'happy' });
      ui.askName((name) => { game.rename(name); saveNow(); game.greet(5); start(); });
    } else {
      start();
    }
    if (loaded.restored) ui.toast('Przywrócono zapis z kopii zapasowej.', 'warn');

    setInterval(saveNow, 5000);
    document.addEventListener('visibilitychange', () => { if (document.hidden) saveNow(); });
    window.addEventListener('pagehide', saveNow);
    window.PiesApp = { game, dogView, scene, ui, switchSkin, studio, saveNow };
  }

  boot().catch((e) => {
    console.error(e);
    const l = $('#loading');
    if (l) l.textContent = 'Błąd uruchamiania: ' + e.message;
  });
})(window.Pies = window.Pies || {});
