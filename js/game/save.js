/*
 * ZAPIS GRY
 *
 * Stan gry jest podzielony na niezależne sekcje. Wygląd psa (appearance.skinId) to
 * osobne pole – zmiana skórki nadpisuje WYŁĄCZNIE je, a imię (profile), postępy i więź
 * (progress), potrzeby (needs) oraz ekwipunek (inventory) zostają nietknięte.
 */
(function (P) {
  'use strict';

  const U = P.util;
  const KEY = 'pies.save.v1';
  const BACKUP_KEY = 'pies.save.v1.backup';
  const VERSION = 1;

  function defaults() {
    const now = Date.now();
    return {
      v: VERSION,
      profile: { name: '', createdAt: now },
      progress: {
        level: 1, xp: 0, bond: 60, coins: 30,
        tricks: { sit: 0.1 },
        stats: { walks: 0, fetches: 0, pets: 0, tricks: 0, meals: 0, baths: 0, sticks: 0 },
        lastDaily: '',
      },
      needs: { food: 55, water: 70, energy: 85, fun: 45, walk: 40, clean: 85 },
      inventory: {
        items: { food: 6, treat: 5, shampoo: 2, bone: 1, ball: 1, stick: 0, 'collar-red': 1 },
        equipped: { neck: 'collar-red', head: null },
      },
      appearance: { skinId: null },
      world: { lights: true, foodBowl: 0, waterBowl: 0.8 },
      settings: { rewardTricks: true },
      lastSeen: now,
    };
  }

  function migrate(data) {
    if (!data || typeof data !== 'object') return null;
    if (!data.v) data.v = 1;
    // przyszłe migracje: if (data.v < 2) { ...; data.v = 2; }
    return U.defaults(data, defaults());
  }

  function load() {
    const raw = U.storage.get(KEY);
    if (!raw) return { data: defaults(), fresh: true };
    try {
      return { data: migrate(JSON.parse(raw)), fresh: false };
    } catch (e) {
      console.warn('[Pies] Uszkodzony zapis – próba kopii zapasowej.', e);
      try {
        const b = U.storage.get(BACKUP_KEY);
        if (b) return { data: migrate(JSON.parse(b)), fresh: false, restored: true };
      } catch (e2) { /* brak kopii */ }
      return { data: defaults(), fresh: true };
    }
  }

  let lastBackup = 0;
  function save(data) {
    data.lastSeen = Date.now();
    const json = JSON.stringify(data);
    const ok = U.storage.set(KEY, json);
    if (Date.now() - lastBackup > 60000) { U.storage.set(BACKUP_KEY, json); lastBackup = Date.now(); }
    return ok;
  }

  function reset() {
    const raw = U.storage.get(KEY);
    if (raw) U.storage.set(BACKUP_KEY, raw);
    U.storage.remove(KEY);
  }

  P.Save = { KEY, load, save, reset, defaults, migrate };
})(window.Pies = window.Pies || {});
