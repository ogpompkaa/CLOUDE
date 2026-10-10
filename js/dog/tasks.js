/*
 * ZADANIA PSA – małe kroki zachowania (podejdź, odegraj stan, poczekaj, wykonaj akcję).
 * Mózg i akcje gracza układają z nich sekwencje. Zadania ustawiają tylko logiczny stan psa:
 * pozycję, kierunek, prędkość i id stanu z DogStates. O wyglądzie decyduje skórka.
 */
(function (P) {
  'use strict';

  const U = P.util;

  /** Podejście do punktu x. gait: 'walk' | 'run'. */
  function moveTo(x, gait, label) {
    return {
      label,
      start(g) { this.tx = g.clampX(typeof x === 'function' ? x(g) : x); },
      update(g, dt) {
        const d = g.dog;
        if (typeof x === 'function') this.tx = g.clampX(x(g));
        const dx = this.tx - d.x;
        const speed = g.gaitSpeed(gait);
        if (Math.abs(dx) <= Math.max(0.03, speed * dt)) {
          d.x = this.tx;
          d.speed = 0;
          return true;
        }
        d.facing = dx > 0 ? 1 : -1;
        d.anim = gait === 'run' ? 'run' : 'walk';
        d.speed = speed;
        d.x += Math.sign(dx) * speed * dt;
        return false;
      },
      end(g) { g.dog.speed = 0; },
    };
  }

  /** Odegranie stanu przez określony czas (albo do spełnienia warunku). */
  function play(anim, duration, opts) {
    const o = opts || {};
    return {
      label: o.label,
      start(g) {
        const d = g.dog;
        d.anim = anim;
        d.animTime = 0;
        d.animDuration = P.DogStates.isOneShot(anim) ? duration : 0;
        d.expression = o.expression || null;
        d.speed = 0;
        this.t = 0;
        this.fxT = 0;
        if (o.onStart) o.onStart(g);
      },
      update(g, dt) {
        this.t += dt;
        if (o.onTick) o.onTick(g, dt, this.t);
        if (o.fx) {
          this.fxT -= dt;
          if (this.fxT <= 0) { g.fx(o.fx.type, o.fx.at || 'headTop'); this.fxT = o.fx.every || 1.2; }
        }
        if (o.until && o.until(g, this.t)) return true;
        return duration !== Infinity && this.t >= duration;
      },
      end(g) {
        g.dog.expression = null;
        if (o.onEnd) o.onEnd(g);
      },
      extend(sec) { duration = Math.max(duration, this.t + sec); },
      anim,
    };
  }

  function face(dirOrFn) {
    return {
      start(g) {
        const v = typeof dirOrFn === 'function' ? dirOrFn(g) : dirOrFn;
        if (v) g.dog.facing = v > 0 ? 1 : -1;
      },
      update() { return true; },
    };
  }

  /** Natychmiastowa akcja. Może zwrócić tablicę zadań do wstawienia na początek kolejki. */
  function call(fn, label) {
    return {
      label,
      start(g) { this.inject = fn(g) || null; },
      update(g) {
        if (this.inject && this.inject.length) g.queue.unshift(...this.inject);
        return true;
      },
    };
  }

  /** Kręcenie się w kółko (gonienie ogona). */
  function spin(turns, label) {
    return {
      label,
      start(g) { this.t = 0; this.n = 0; g.dog.anim = 'run'; g.dog.speed = 0.6; },
      update(g, dt) {
        this.t += dt;
        if (this.t > 0.22) { this.t = 0; this.n++; g.dog.facing *= -1; }
        return this.n >= turns * 2;
      },
      end(g) { g.dog.speed = 0; },
    };
  }

  P.DogTasks = { moveTo, play, face, call, spin, rand: U.rand };
})(window.Pies = window.Pies || {});
