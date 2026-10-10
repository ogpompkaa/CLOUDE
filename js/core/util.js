/*
 * Wspólne narzędzia: matematyka, macierze 2D, bezpieczny localStorage, prosta szyna zdarzeń.
 * Wszystkie moduły gry rejestrują się w globalnej przestrzeni nazw window.Pies,
 * dzięki czemu gra działa zarówno z serwera, jak i po otwarciu pliku index.html z dysku.
 */
(function (P) {
  'use strict';

  const TAU = Math.PI * 2;

  const U = {
    TAU,
    clamp: (v, a, b) => (v < a ? a : v > b ? b : v),
    lerp: (a, b, t) => a + (b - a) * t,
    rand: (a, b) => a + Math.random() * (b - a),
    randInt: (a, b) => Math.floor(a + Math.random() * (b - a + 1)),
    chance: (p) => Math.random() < p,
    pick: (arr) => arr[Math.floor(Math.random() * arr.length)],
    deg: (d) => (d * Math.PI) / 180,
    smooth: (t) => t * t * (3 - 2 * t),
    sin: (phase) => Math.sin(phase * TAU),
    cos: (phase) => Math.cos(phase * TAU),
    /** Wygładzanie wykładnicze niezależne od liczby klatek. */
    approach: (current, target, rate, dt) => current + (target - current) * (1 - Math.exp(-rate * dt)),
    /** Losowanie z wagami: [{weight, value}] -> value */
    weighted(entries) {
      let total = 0;
      for (const e of entries) total += Math.max(0, e.weight);
      if (total <= 0) return null;
      let r = Math.random() * total;
      for (const e of entries) {
        r -= Math.max(0, e.weight);
        if (r <= 0) return e.value;
      }
      return entries[entries.length - 1].value;
    },
    deepClone: (o) => JSON.parse(JSON.stringify(o)),
    /** Łączy obiekt z wartościami domyślnymi (rekurencyjnie, bez nadpisywania istniejących kluczy). */
    defaults(target, defs) {
      const out = target && typeof target === 'object' ? target : {};
      for (const k of Object.keys(defs)) {
        const d = defs[k];
        if (out[k] === undefined) out[k] = d && typeof d === 'object' && !Array.isArray(d) ? U.deepClone(d) : d;
        else if (d && typeof d === 'object' && !Array.isArray(d) && out[k] && typeof out[k] === 'object') U.defaults(out[k], d);
      }
      return out;
    },
  };

  /* ---------- Macierze afiniczne 2D: [a, b, c, d, e, f] jak w CanvasRenderingContext2D ---------- */
  U.mat = {
    identity: () => [1, 0, 0, 1, 0, 0],
    multiply(m, n) {
      return [
        m[0] * n[0] + m[2] * n[1],
        m[1] * n[0] + m[3] * n[1],
        m[0] * n[2] + m[2] * n[3],
        m[1] * n[2] + m[3] * n[3],
        m[0] * n[4] + m[2] * n[5] + m[4],
        m[1] * n[4] + m[3] * n[5] + m[5],
      ];
    },
    translate: (m, x, y) => U.mat.multiply(m, [1, 0, 0, 1, x, y]),
    rotate(m, rad) {
      const c = Math.cos(rad), s = Math.sin(rad);
      return U.mat.multiply(m, [c, s, -s, c, 0, 0]);
    },
    scale: (m, sx, sy) => U.mat.multiply(m, [sx, 0, 0, sy, 0, 0]),
    apply: (m, x, y) => [m[0] * x + m[2] * y + m[4], m[1] * x + m[3] * y + m[5]],
    invert(m) {
      const det = m[0] * m[3] - m[1] * m[2];
      if (!det) return U.mat.identity();
      const id = 1 / det;
      return [
        m[3] * id, -m[1] * id, -m[2] * id, m[0] * id,
        (m[2] * m[5] - m[3] * m[4]) * id,
        (m[1] * m[4] - m[0] * m[5]) * id,
      ];
    },
  };

  /* ---------- localStorage odporny na tryb prywatny / zablokowane dane witryny ---------- */
  U.storage = {
    get(key) {
      try { return window.localStorage.getItem(key); } catch (e) { return null; }
    },
    set(key, value) {
      try { window.localStorage.setItem(key, value); return true; } catch (e) { return false; }
    },
    remove(key) {
      try { window.localStorage.removeItem(key); } catch (e) { /* brak dostępu */ }
    },
  };

  /* ---------- Szyna zdarzeń ---------- */
  U.createEmitter = function () {
    const handlers = {};
    return {
      on(type, fn) { (handlers[type] = handlers[type] || []).push(fn); return () => this.off(type, fn); },
      off(type, fn) { const h = handlers[type]; if (h) handlers[type] = h.filter((x) => x !== fn); },
      emit(type, payload) { (handlers[type] || []).slice().forEach((fn) => fn(payload)); },
    };
  };

  P.util = U;
})(window.Pies = window.Pies || {});
