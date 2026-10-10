/*
 * INTERFEJS – HUD, przyciski opieki, panele (sztuczki, plecak, sklep, wygląd, profil),
 * powiadomienia i okno nadawania imienia. Czyta stan gry i wywołuje jej akcje.
 * Z warstwą wyglądu komunikuje się wyłącznie przez obiekt `host` z main.js.
 */
(function (P) {
  'use strict';

  const D = P.GameData;
  const I = P.Icons;
  const $ = (sel, root) => (root || document).querySelector(sel);

  const NEED_COLORS = { food: '#ff7a59', water: '#4d8fea', energy: '#f5b324', fun: '#ff5c8a', walk: '#2bb3a3', clean: '#9b7bea' };
  const ITEM_COLORS = { bowl: '#ff7a59', treat: '#f5b324', bath: '#9b7bea', bone: '#c08a5a', stick: '#9b6a3c', ball: '#9ccc3c', collar: '#e0453a', bandana: '#e2453c', bowtie: '#2d6cdf', bow: '#ff6fa8', cap: '#3b7be8' };

  function el(tag, cls, html) {
    const e = document.createElement(tag);
    if (cls) e.className = cls;
    if (html !== undefined) e.innerHTML = html;
    return e;
  }
  const icon = (name) => '<span class="ico">' + (I[name] || '') + '</span>';

  class UI {
    constructor(game, host) {
      this.game = game;
      this.host = host;
      this.sheetId = null;
      this.hintShown = true;
    }

    build() {
      document.querySelectorAll('[data-icon]').forEach((n) => { n.innerHTML = I[n.dataset.icon] || ''; });
      this.buildNeeds();
      this.buildActions();
      this.buildTabs();
      $('#btn-light').addEventListener('click', () => { this.game.toggleLights(); this.update(); });
      $('#btn-walk-home').addEventListener('click', () => this.game.endWalk(false));
      $('#btn-studio-close').addEventListener('click', () => { this.host.studio.close(); this.closeSheet(); });
      document.querySelectorAll('#sheet [data-close]').forEach((n) => n.addEventListener('click', () => this.closeSheet()));
      document.addEventListener('keydown', (e) => { if (e.key === 'Escape') this.closeSheet(); });
    }

    buildNeeds() {
      const box = $('#needs');
      box.innerHTML = '';
      this.needEls = {};
      for (const n of D.NEEDS) {
        const row = el('div', 'need');
        row.innerHTML = '<div class="badge" style="background:' + NEED_COLORS[n.id] + '">' + icon(n.icon) + '</div>' +
          '<div class="meta"><div class="name">' + n.name + '</div><div class="meter"><i></i></div></div>';
        row.title = n.name;
        box.appendChild(row);
        this.needEls[n.id] = row;
      }
    }

    buildActions() {
      const g = this.game;
      const defs = [
        { id: 'feed', label: 'Karm', icon: 'bowl', color: '#ff7a59', count: 'food', run: () => g.feed() },
        { id: 'water', label: 'Woda', icon: 'drop', color: '#4d8fea', run: () => g.giveWater() },
        { id: 'ball', label: 'Piłka', icon: 'ball', color: '#8fc23a', run: () => g.throwBall() },
        { id: 'treat', label: 'Smakołyk', icon: 'treat', color: '#f5b324', count: 'treat', run: () => g.giveTreat() },
        { id: 'walk', label: 'Spacer', icon: 'leash', color: '#2bb3a3', run: () => (g.scene === 'park' ? g.endWalk(false) : g.startWalk()) },
        { id: 'bath', label: 'Kąpiel', icon: 'bath', color: '#9b7bea', count: 'shampoo', run: () => g.bath() },
        { id: 'bone', label: 'Kość', icon: 'bone', color: '#c08a5a', count: 'bone', run: () => g.giveBone() },
        { id: 'call', label: 'Do mnie!', icon: 'paw', color: '#ef7d57', run: () => g.callDog() },
      ];
      const box = $('#actions');
      box.innerHTML = '';
      this.actionEls = {};
      for (const d of defs) {
        const b = el('button', 'action');
        b.innerHTML = '<span class="bubble" style="background:' + d.color + '">' + icon(d.icon) + '</span><span class="label">' + d.label + '</span>' + (d.count ? '<span class="count">0</span>' : '');
        b.addEventListener('click', () => { d.run(); this.update(); this.pulseHint(); });
        box.appendChild(b);
        this.actionEls[d.id] = { el: b, def: d };
      }
    }

    buildTabs() {
      const tabs = [
        { id: 'tricks', label: 'Sztuczki', icon: 'star' },
        { id: 'bag', label: 'Plecak', icon: 'bag' },
        { id: 'shop', label: 'Sklep', icon: 'shop' },
        { id: 'looks', label: 'Wygląd', icon: 'palette' },
        { id: 'profile', label: 'Profil', icon: 'user' },
      ];
      const box = $('#tabbar');
      box.innerHTML = '';
      this.tabEls = {};
      for (const t of tabs) {
        const b = el('button', 'tab', icon(t.icon) + '<span class="label">' + t.label + '</span>');
        b.addEventListener('click', () => this.openSheet(t.id));
        box.appendChild(b);
        this.tabEls[t.id] = b;
      }
    }

    pulseHint() {
      const h = $('#tap-hint');
      if (h && this.hintShown) { this.hintShown = false; h.classList.add('gone'); }
    }

    /* ---------------- odświeżanie ---------------- */
    update() {
      const g = this.game;
      const st = g.state;
      const p = st.progress;
      $('#dog-name').textContent = g.name();
      $('#status-name').textContent = g.name();
      $('#status').textContent = '· ' + (g.dog.status || 'Odpoczywa');
      $('#lvl').textContent = 'Poz. ' + p.level;
      $('#xp-fill').style.width = Math.round((p.xp / D.xpToNext(p.level)) * 100) + '%';
      $('#bond-name').textContent = D.bondTier(p.bond).name;
      $('#coins').textContent = p.coins;
      for (const n of D.NEEDS) {
        const v = st.needs[n.id];
        const row = this.needEls[n.id];
        const bar = row.querySelector('i');
        bar.style.width = Math.round(v) + '%';
        bar.style.background = v < 25 ? '#ef5350' : v < 50 ? '#ff9f43' : NEED_COLORS[n.id];
        row.classList.toggle('low', v < 25);
        row.title = n.name + ': ' + Math.round(v) + '%';
      }
      for (const id of Object.keys(this.actionEls)) {
        const { el: b, def } = this.actionEls[id];
        if (def.count) b.querySelector('.count').textContent = g.item(def.count);
      }
      const walk = this.actionEls.walk.el;
      walk.querySelector('.label').textContent = g.scene === 'park' ? 'Do domu' : 'Spacer';
      walk.querySelector('.bubble').innerHTML = icon(g.scene === 'park' ? 'home' : 'leash');
      ['feed', 'water', 'ball', 'bath', 'bone'].forEach((id) => this.actionEls[id].el.classList.toggle('disabled', g.scene === 'park'));
      $('#walk-hud').classList.toggle('hidden', g.scene !== 'park' || this.host.studio.active);
      if (g.scene === 'park' || this.host.studio.active) this.pulseHint();
      if (g.park) $('#walk-fill').style.width = Math.round(Math.min(1, g.dog.x / g.park.length) * 100) + '%';
      const light = $('#btn-light');
      light.classList.toggle('off', !st.world.lights);
      light.innerHTML = icon(st.world.lights ? 'bulb' : 'moon');
      light.classList.toggle('hidden', g.scene !== 'home');
      if (this.sheetId === 'tricks' || this.sheetId === 'profile') this.refreshLive();
    }

    refreshLive() {
      // drobne odświeżenie otwartego panelu bez przebudowy (paski umiejętności, statystyki)
      if (this.sheetId === 'tricks') {
        const st = this.game.state.progress;
        D.TRICKS.forEach((t) => {
          const bar = document.querySelector('[data-skill="' + t.id + '"]');
          if (bar) bar.style.width = Math.round((st.tricks[t.id] || 0) * 100) + '%';
        });
      }
    }

    /* ---------------- powiadomienia ---------------- */
    toast(text, kind) {
      const box = $('#toasts');
      const t = el('div', 'toast ' + (kind || ''), text);
      box.appendChild(t);
      while (box.children.length > 3) box.removeChild(box.firstChild);
      setTimeout(() => { t.classList.add('out'); setTimeout(() => t.remove(), 350); }, 2600);
    }

    /* ---------------- panele ---------------- */
    openSheet(id) {
      this.sheetId = id;
      const titles = { tricks: 'Sztuczki', bag: 'Plecak', shop: 'Sklep', looks: 'Wygląd psa', profile: 'Profil', studio: 'Studio wyglądu' };
      $('#sheet-title').textContent = titles[id] || '';
      const body = $('#sheet-body');
      body.innerHTML = '';
      const sheet = $('#sheet');
      sheet.classList.remove('hidden');
      sheet.classList.toggle('studio', id === 'studio');
      Object.keys(this.tabEls).forEach((k) => this.tabEls[k].classList.toggle('active', k === id));
      this['render_' + id](body);
    }

    closeSheet() {
      if (this.sheetId === 'studio') this.host.studio.close();
      this.sheetId = null;
      $('#sheet').classList.add('hidden');
      Object.keys(this.tabEls).forEach((k) => this.tabEls[k].classList.remove('active'));
    }

    rerender() { if (this.sheetId) { const body = $('#sheet-body'); const top = body.scrollTop; this.openSheet(this.sheetId); body.scrollTop = top; } }

    render_tricks(body) {
      const g = this.game;
      const st = g.state;
      body.appendChild(el('p', 'muted', 'Wydaj komendę – pies przybiegnie i spróbuje ją wykonać. Każde udane powtórzenie podnosi umiejętność. Smakołyk jako nagroda przyspiesza naukę prawie trzykrotnie.'));
      const row = el('label', 'toggle-row', '<span>Nagradzaj smakołykiem <span class="muted">(masz: ' + g.item('treat') + ')</span></span><span class="switch"><input type="checkbox"' + (st.settings.rewardTricks ? ' checked' : '') + '><span></span></span>');
      row.querySelector('input').addEventListener('change', (e) => { st.settings.rewardTricks = e.target.checked; });
      body.appendChild(row);
      for (const t of D.TRICKS) {
        const locked = st.progress.level < t.level;
        const skill = st.progress.tricks[t.id] || 0;
        const r = el('div', 'trick' + (locked ? ' locked' : ''));
        r.innerHTML = '<div class="info"><div class="title">' + t.name + ' <span class="muted">„' + t.command + '”</span></div>' +
          '<div class="skill"><i data-skill="' + t.id + '" style="width:' + Math.round(skill * 100) + '%"></i></div>' +
          '<div class="sub">' + (locked ? 'Odblokowanie na poziomie ' + t.level : skill >= 1 ? 'Opanowana do perfekcji' : 'Umiejętność: ' + Math.round(skill * 100) + '%') + '</div></div>';
        const b = el('button', 'btn ' + (locked ? '' : 'primary'), locked ? icon('lock') : 'Komenda');
        b.disabled = locked;
        b.addEventListener('click', () => { g.command(t.id); this.closeSheet(); });
        r.appendChild(b);
        body.appendChild(r);
      }
    }

    itemCard(id, opts) {
      const def = D.ITEMS[id];
      const c = el('div', 'item-card' + (opts.cls ? ' ' + opts.cls : ''));
      c.innerHTML = '<div class="top"><div class="thumb" style="background:' + (def.color || ITEM_COLORS[def.icon] || '#c08a5a') + '">' + icon(def.icon) + '</div>' +
        '<div><div class="title">' + def.name + '</div>' + (opts.sub ? '<div class="sub">' + opts.sub + '</div>' : '') + '</div></div>' +
        (def.desc ? '<div class="sub">' + def.desc + '</div>' : '') + '<div class="row"></div>';
      return c;
    }

    render_bag(body) {
      const g = this.game;
      const inv = g.state.inventory;
      body.appendChild(el('div', 'sec-title', 'Zapasy'));
      const grid = el('div', 'grid-list');
      const uses = { food: ['Do miski', () => g.feed()], treat: ['Daj', () => g.giveTreat()], shampoo: ['Kąpiel', () => g.bath()], bone: ['Daj kość', () => g.giveBone()] };
      for (const id of ['food', 'treat', 'shampoo', 'bone', 'ball', 'stick']) {
        const def = D.ITEMS[id];
        const c = this.itemCard(id, { sub: 'Masz: ' + g.item(id) });
        const row = c.querySelector('.row');
        if (uses[id]) {
          const b = el('button', 'btn', uses[id][0]);
          b.disabled = g.item(id) < 1;
          b.addEventListener('click', () => { uses[id][1](); this.closeSheet(); });
          row.appendChild(b);
        } else if (id === 'ball') {
          const b = el('button', 'btn', 'Rzuć');
          b.addEventListener('click', () => { g.throwBall(); this.closeSheet(); });
          row.appendChild(b);
        } else {
          row.appendChild(el('span', 'sub', def.kind === 'collectible' ? 'Kolekcja ze spacerów' : ''));
        }
        grid.appendChild(c);
      }
      body.appendChild(grid);

      body.appendChild(el('div', 'sec-title', 'Akcesoria (pasują do każdego wyglądu psa)'));
      const grid2 = el('div', 'grid-list');
      const owned = Object.keys(D.ITEMS).filter((id) => D.ITEMS[id].kind === 'wearable' && g.item(id) > 0);
      if (!owned.length) grid2.appendChild(el('p', 'muted', 'Brak akcesoriów – zajrzyj do sklepu.'));
      for (const id of owned) {
        const def = D.ITEMS[id];
        const on = inv.equipped[def.slot] === id;
        const c = this.itemCard(id, { sub: def.slot === 'neck' ? 'Na szyję' : 'Na głowę', cls: on ? 'equipped' : '' });
        const b = el('button', 'btn ' + (on ? '' : 'teal'), on ? 'Zdejmij' : 'Załóż');
        b.addEventListener('click', () => { if (on) g.unequip(def.slot); else g.equip(id); this.rerender(); });
        c.querySelector('.row').appendChild(b);
        grid2.appendChild(c);
      }
      body.appendChild(grid2);
    }

    render_shop(body) {
      const g = this.game;
      const p = g.state.progress;
      body.appendChild(el('p', 'muted', 'Monety zdobywasz na spacerach, za sztuczki, aportowanie, nowe poziomy i codzienną wizytę. Masz: <b>' + p.coins + '</b>.'));
      const grid = el('div', 'grid-list');
      for (const id of D.SHOP) {
        const def = D.ITEMS[id];
        const locked = def.level && p.level < def.level;
        const ownedWearable = def.kind === 'wearable' && g.item(id) > 0;
        const c = this.itemCard(id, { sub: def.pack > 1 ? 'Paczka ×' + def.pack : def.kind === 'wearable' ? (def.slot === 'neck' ? 'Na szyję' : 'Na głowę') : '', cls: locked ? 'locked' : '' });
        const row = c.querySelector('.row');
        row.appendChild(el('span', 'price', icon('coin') + def.price));
        const b = el('button', 'btn primary', locked ? 'Poz. ' + def.level : ownedWearable ? 'Masz' : 'Kup');
        b.disabled = locked || ownedWearable || p.coins < def.price;
        b.addEventListener('click', () => { if (g.buy(id)) { this.update(); this.rerender(); } });
        row.appendChild(b);
        grid.appendChild(c);
      }
      body.appendChild(grid);
    }

    render_looks(body) {
      const host = this.host;
      const current = host.currentSkinId();
      body.appendChild(el('p', 'muted', 'Wygląd psa to wymienny zestaw grafik (skórka). Zmiana wyglądu <b>nie zmienia</b> imienia, poziomu, więzi, umiejętności ani ekwipunku. Docelowo trafi tu prawdziwy pies na podstawie zdjęć – instrukcja: <b>docs/WYGLAD-PSA.md</b>.'));
      body.appendChild(el('div', 'sec-title', 'Dostępne wyglądy'));
      const grid = el('div', 'grid-list');
      for (const skin of host.skins()) {
        const c = el('div', 'item-card skin-card' + (skin.id === current ? ' equipped' : ''));
        const cv = document.createElement('canvas');
        c.appendChild(cv);
        const warn = skin.report.warnings.length;
        const b2 = el('div', 'body', '<div class="title">' + skin.name + '</div><div class="sub">' + (skin.description || '') + '</div>' +
          '<div class="chips"><span class="tag">' + (skin.renderer === 'rig' ? 'Warstwy + szkielet' : 'Klatki') + '</span>' +
          '<span class="tag ' + (warn ? 'warn' : 'ok') + '">' + (warn ? warn + ' uwag' : 'Konfiguracja OK') + '</span></div>');
        const btn = el('button', 'btn ' + (skin.id === current ? '' : 'teal'), skin.id === current ? icon('check') + 'Wybrany' : 'Wybierz');
        btn.disabled = skin.id === current;
        btn.addEventListener('click', () => { host.selectSkin(skin.id); setTimeout(() => this.rerender(), 120); });
        b2.appendChild(btn);
        c.appendChild(b2);
        grid.appendChild(c);
        requestAnimationFrame(() => host.renderSkinThumb(cv, skin));
      }
      body.appendChild(grid);
      const bad = host.invalidSkins();
      if (bad.length) {
        body.appendChild(el('div', 'sec-title', 'Odrzucone konfiguracje'));
        for (const s of bad) body.appendChild(el('div', 'report', '<b>' + s.id + '</b><ul>' + s.report.errors.map((e) => '<li>' + e + '</li>').join('') + '</ul>'));
      }
      body.appendChild(el('div', 'sec-title', 'Dla twórców wyglądu'));
      body.appendChild(el('p', 'muted', 'Studio pokazuje każdą animację z kontraktu psa, szkielet, punkty styku, strefy dotyku i punkty zaczepienia akcesoriów – do dopasowania grafik prawdziwego psa.'));
      const sb = el('button', 'btn primary', icon('sparkle') + 'Otwórz studio wyglądu');
      sb.addEventListener('click', () => { host.studio.open(); this.openSheet('studio'); });
      body.appendChild(sb);
    }

    render_studio(body) {
      const host = this.host;
      const studio = host.studio;
      const skin = host.currentSkin();
      body.appendChild(el('p', 'muted', 'Skórka: <b>' + skin.name + '</b> (' + skin.id + '). Gra jest wstrzymana na czas podglądu.'));
      const chips = el('div', 'chips');
      for (const s of P.DogStates.list) {
        const resolved = host.resolveAnim(s.id);
        const b = el('button', 'chip-btn' + (studio.state === s.id ? ' active' : ''), s.label + (resolved !== s.id ? '<span class="alt">→ ' + resolved + '</span>' : ''));
        b.title = s.id;
        b.addEventListener('click', () => { studio.setState(s.id); chips.querySelectorAll('.chip-btn').forEach((x) => x.classList.remove('active')); b.classList.add('active'); });
        chips.appendChild(b);
      }
      body.appendChild(el('div', 'sec-title', 'Animacje (stany psa)'));
      body.appendChild(chips);
      body.appendChild(el('div', 'sec-title', 'Podgląd'));
      const toggles = [
        ['Szkielet, punkty styku, strefy dotyku', 'debug'],
        ['Akcesoria i piłka w pysku', 'accessories'],
        ['Automatycznie przełączaj animacje', 'cycle'],
      ];
      for (const [label, key] of toggles) {
        const row = el('label', 'toggle-row', '<span>' + label + '</span><span class="switch"><input type="checkbox"' + (studio[key] ? ' checked' : '') + '><span></span></span>');
        row.querySelector('input').addEventListener('change', (e) => studio.set(key, e.target.checked));
        body.appendChild(row);
      }
      const flip = el('button', 'btn', icon('flip') + 'Obróć psa');
      flip.addEventListener('click', () => studio.flip());
      body.appendChild(flip);
      const rep = skin.report;
      const lines = [];
      rep.warnings.forEach((w) => lines.push('<li>' + w + '</li>'));
      (skin.missingImages || []).forEach((m) => lines.push('<li>Brak pliku: ' + m + '</li>'));
      body.appendChild(el('div', 'sec-title', 'Raport konfiguracji'));
      body.appendChild(el('div', 'report', lines.length ? '<ul>' + lines.join('') + '</ul>' : 'Brak uwag – wszystkie wymagane pola i pliki są na miejscu.'));
    }

    render_profile(body) {
      const g = this.game;
      const p = g.state.progress;
      const tier = D.bondTier(p.bond);
      body.appendChild(el('div', 'sec-title', 'Imię'));
      const f = el('form', 'field', '<input maxlength="16" value="' + g.name().replace(/"/g, '&quot;') + '" aria-label="Imię psa"><button class="btn primary" type="submit">Zapisz</button>');
      f.addEventListener('submit', (e) => { e.preventDefault(); g.rename(f.querySelector('input').value); this.update(); this.toast('Zapisano imię: ' + g.name(), 'reward'); });
      body.appendChild(f);
      body.appendChild(el('div', 'sec-title', 'Więź'));
      body.appendChild(el('div', '', '<b>' + tier.name + '</b> <span class="muted">' + Math.round(p.bond) + ' / 1000</span><div class="bondbar"><i style="width:' + (p.bond / 10) + '%"></i></div>'));
      body.appendChild(el('div', 'sec-title', 'Statystyki'));
      const s = p.stats;
      const stats = [[p.level, 'poziom'], [s.walks, 'spacerów'], [s.fetches, 'aportów'], [s.pets, 'pieszczot'], [s.tricks, 'udanych sztuczek'], [s.meals, 'posiłków'], [s.baths, 'kąpieli'], [s.sticks, 'patyków']];
      body.appendChild(el('div', 'stats-grid', stats.map(([v, l]) => '<div class="stat"><b>' + v + '</b><span>' + l + '</span></div>').join('')));
      body.appendChild(el('div', 'sec-title', 'Gra'));
      body.appendChild(el('p', 'muted', 'Postęp zapisuje się automatycznie w tej przeglądarce. Zmiana wyglądu psa nigdy go nie resetuje.'));
      // potwierdzenie w samej stronie (okna confirm() bywają blokowane, np. w osadzonych widokach)
      const reset = el('button', 'btn', 'Zacznij od nowa');
      let armed = null;
      reset.addEventListener('click', () => {
        if (armed) { clearTimeout(armed); this.host.resetGame(); return; }
        reset.textContent = 'Kliknij ponownie – imię, poziom, więź i ekwipunek zostaną usunięte';
        reset.classList.add('primary');
        armed = setTimeout(() => { armed = null; reset.textContent = 'Zacznij od nowa'; reset.classList.remove('primary'); }, 4000);
      });
      body.appendChild(reset);
    }

    /* ---------------- imię ---------------- */
    askName(onDone) {
      const m = $('#name-modal');
      m.classList.remove('hidden');
      const input = $('#name-input');
      setTimeout(() => input.focus(), 50);
      $('#name-form').onsubmit = (e) => {
        e.preventDefault();
        const v = input.value.trim();
        if (!v) return;
        m.classList.add('hidden');
        onDone(v);
      };
    }
  }

  P.UI = UI;
})(window.Pies = window.Pies || {});
