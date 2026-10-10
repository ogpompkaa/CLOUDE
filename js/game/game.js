/*
 * GRA – stan psa, potrzeby, świat (pokój i park), akcje gracza, nagrody.
 *
 * Ten moduł NIE importuje niczego z warstwy wyglądu. Z zewnątrz dostaje jedynie
 * logiczne ciało psa (setBody – proporcje z konfiguracji skórki), a na zewnątrz
 * oddaje „migawkę” (snapshot) dla DogView. Zmiana skórki (setSkin) zapisuje tylko
 * appearance.skinId – imię, postępy, więź i ekwipunek pozostają bez zmian.
 */
(function (P) {
  'use strict';

  const U = P.util;
  const T = P.DogTasks;
  const D = P.GameData;

  const ROOM = { width: 7.6, bed: 1.25, toys: 2.65, window: 3.35, center: 3.8, food: 5.35, water: 6.0, door: 6.95, minX: 0.3, maxX: 7.3 };
  const GRAVITY = 22;

  class Game {
    constructor(state) {
      this.state = state;
      this.events = U.createEmitter();
      this.body = P.DogBody.fromProportions();
      this.room = ROOM;
      this.time = 0;
      this.scene = 'home';
      this.park = null;
      this.queue = [];
      this.task = null;
      this.brainCd = {};
      this.idleWait = 0.6;
      this.petLog = [];
      this.alerts = {};
      this.dog = {
        x: ROOM.center, facing: 1, anim: 'idle', animTime: 0, animDuration: 0, speed: 0, lift: 0,
        carrying: null, expression: null, status: 'Odpoczywa', asleep: false,
      };
      this.ball = { x: ROOM.toys + 0.3, y: 0, vx: 0, vy: 0, state: 'floor' };
    }

    /* ============================ narzędzia ============================ */
    on(type, fn) { return this.events.on(type, fn); }
    emit(type, payload) { this.events.emit(type, payload); }
    name() { return this.state.profile.name || 'Piesek'; }
    toast(text, kind) { this.emit('toast', { text, kind: kind || 'info' }); }
    fx(type, at) { this.emit('fx', { type, at: at || 'headTop' }); }

    setBody(body) {
      this.body = body;
      this.dog.x = this.clampX(this.dog.x);
    }

    clampX(x) {
      if (this.scene === 'park') return Math.max(0, x);
      const m = this.body.halfLength * 0.9;
      return U.clamp(x, ROOM.minX + m, ROOM.maxX - m);
    }

    gaitSpeed(gait) {
      const h = Math.sqrt(this.body.height);
      return (gait === 'run' ? 2.5 : 0.85) * h;
    }

    /** Miejsce, w którym pies staje, żeby pyskiem sięgnąć do miski / przedmiotu w punkcie bx. */
    bowlSpot(bx) {
      const side = this.dog.x <= bx ? -1 : 1;
      let x = bx + side * this.body.mouthReach;
      if (this.clampX(x) !== x) { x = bx - side * this.body.mouthReach; return { x: this.clampX(x), facing: side }; }
      return { x, facing: -side };
    }

    isNight() { const h = new Date().getHours(); return h >= 22 || h < 7; }

    mood() {
      const n = this.state.needs;
      const avg = (n.food + n.water + n.energy + n.fun + n.walk + n.clean) / 600;
      return { happy: U.clamp(avg * 0.75 + (this.state.progress.bond / 1000) * 0.25, 0, 1), energy: n.energy / 100 };
    }

    needAdd(id, v) {
      const n = this.state.needs;
      n[id] = U.clamp(n[id] + v, 0, 100);
    }

    addXp(v) {
      const p = this.state.progress;
      p.xp += v;
      let need = D.xpToNext(p.level);
      while (p.xp >= need && p.level < 50) {
        p.xp -= need;
        p.level++;
        p.coins += 15;
        const unlocked = D.TRICKS.filter((t) => t.level === p.level).map((t) => t.name);
        this.emit('levelup', { level: p.level, unlocked });
        need = D.xpToNext(p.level);
      }
    }

    addBond(v) {
      const p = this.state.progress;
      const before = D.bondTier(p.bond).index;
      p.bond = U.clamp(p.bond + v, 0, 1000);
      const after = D.bondTier(p.bond);
      if (after.index > before) this.toast('Więź rośnie! ' + this.name() + ' to teraz: ' + after.name, 'bond');
    }

    addCoins(v) { this.state.progress.coins += v; }
    item(id) { return this.state.inventory.items[id] || 0; }
    useItem(id, n) { const it = this.state.inventory.items; it[id] = Math.max(0, (it[id] || 0) - (n || 1)); }

    /* ============================ pętla ============================ */
    update(dt) {
      this.time += dt;
      this.decayNeeds(dt);
      this.updateBall(dt);
      this.updateTasks(dt);
      this.dog.animTime += dt;
      this.checkAlerts();
    }

    decayNeeds(dt) {
      const n = this.state.needs;
      const asleep = this.dog.asleep;
      for (const def of D.NEEDS) {
        let rate = def.perHour / 3600;
        if (asleep) {
          if (def.id === 'energy') continue;
          rate *= def.id === 'fun' ? 0 : 0.5;
        }
        if (def.id === 'energy' && (this.dog.anim === 'run' || this.dog.anim === 'excited')) rate *= 4;
        n[def.id] = U.clamp(n[def.id] - rate * dt, 0, 100);
      }
      // zaniedbanie powoli osłabia więź
      const avg = (n.food + n.water + n.fun + n.walk) / 4;
      if (avg < 20) this.state.progress.bond = Math.max(0, this.state.progress.bond - dt * 0.004);
    }

    applyOffline(seconds) {
      const s = U.clamp(seconds, 0, 10 * 3600);
      if (s < 60) return;
      const n = this.state.needs;
      for (const def of D.NEEDS) {
        if (def.id === 'energy') continue;
        n[def.id] = Math.max(8, n[def.id] - (def.perHour / 3600) * s * 0.5);
      }
      n.energy = Math.min(100, n.energy + (s / 3600) * 20);
    }

    checkAlerts() {
      const n = this.state.needs;
      const msgs = { food: 'jest głodny', water: 'chce pić', energy: 'jest bardzo zmęczony', fun: 'się nudzi', walk: 'bardzo chce na spacer', clean: 'jest brudny' };
      for (const id of Object.keys(msgs)) {
        if (n[id] < 22 && !this.alerts[id]) { this.alerts[id] = true; this.toast(this.name() + ' ' + msgs[id] + '!', 'warn'); }
        if (n[id] > 35) this.alerts[id] = false;
      }
    }

    updateTasks(dt) {
      if (!this.task) {
        if (this.queue.length) {
          this.task = this.queue.shift();
          this.task.start(this);
          if (this.task.label) this.dog.status = this.task.label;
        } else {
          this.idleWait -= dt;
          if (this.dog.anim !== 'idle' && !this.dog.asleep && this.idleWait > 0.3) this.setIdle();
          if (this.idleWait <= 0) {
            const seq = P.DogBrain.pick(this);
            this.idleWait = U.rand(0.4, 1.4);
            if (seq && seq.length) this.queue = seq;
          }
          return;
        }
      }
      if (this.task.update(this, dt)) {
        if (this.task.end) this.task.end(this);
        this.task = null;
        if (!this.queue.length) this.idleWait = U.rand(0.5, 1.6);
      }
    }

    setIdle() {
      const d = this.dog;
      d.anim = 'idle';
      d.animDuration = 0;
      d.speed = 0;
      d.status = 'Odpoczywa';
    }

    /** Przerywa bieżące zajęcie psa i zleca nową sekwencję. */
    interrupt(seq) {
      if (this.task && this.task.end) this.task.end(this);
      this.task = null;
      if (this.dog.carrying === 'ball') this.dropBall();
      if (this.dog.carrying === 'bone') this.dog.carrying = null;
      this.dog.asleep = false;
      this.queue = seq.slice();
      this.idleWait = 0;
    }

    snapshot() {
      const d = this.dog;
      return {
        x: d.x, facing: d.facing, anim: d.anim, animTime: d.animTime, animDuration: d.animDuration,
        speed: d.speed, lift: d.lift, carrying: d.carrying, expression: d.expression,
        equipment: this.state.inventory.equipped, mood: this.mood(),
      };
    }

    /* ============================ piłka ============================ */
    updateBall(dt) {
      const b = this.ball;
      if (this.scene !== 'home' || b.state === 'held') return;
      const lo = ROOM.minX + 0.1, hi = ROOM.maxX - 0.1;
      if (b.state === 'air') {
        b.vy += GRAVITY * dt;
        b.x += b.vx * dt;
        b.y += b.vy * dt;
        if (b.y >= 0) {
          b.y = 0;
          if (Math.abs(b.vy) > 1.6) { b.vy = -b.vy * 0.42; b.vx *= 0.78; this.emit('sound', 'bounce'); }
          else { b.vy = 0; b.state = 'floor'; }
        }
      } else {
        b.x += b.vx * dt;
        b.vx *= Math.exp(-2.4 * dt);
        if (Math.abs(b.vx) < 0.02) b.vx = 0;
      }
      if (b.x < lo) { b.x = lo; b.vx = Math.abs(b.vx) * 0.6; }
      if (b.x > hi) { b.x = hi; b.vx = -Math.abs(b.vx) * 0.6; }
    }

    ballResting() { return this.ball.state === 'floor' && Math.abs(this.ball.vx) < 0.25; }

    pickUpBall() {
      this.ball.state = 'held';
      this.dog.carrying = 'ball';
    }

    dropBall() {
      const d = this.dog;
      this.ball.state = 'floor';
      this.ball.x = this.clampX(d.x + d.facing * this.body.mouthReach);
      this.ball.y = 0;
      this.ball.vx = d.facing * 0.4;
      d.carrying = null;
    }

    chaseBallTask() {
      return {
        label: 'Goni piłkę!',
        start() { this.t = 0; },
        update(g, dt) {
          this.t += dt;
          const spot = g.bowlSpot(g.ball.x);
          const d = g.dog;
          const dx = spot.x - d.x;
          const speed = g.gaitSpeed('run');
          if (Math.abs(dx) > 0.04) {
            d.anim = 'run';
            d.speed = speed;
            d.facing = dx > 0 ? 1 : -1;
            d.x = g.clampX(d.x + Math.sign(dx) * Math.min(Math.abs(dx), speed * dt));
          } else {
            d.speed = 0;
            d.anim = 'idle';
            d.facing = spot.facing;
            if (g.ballResting()) return true;
          }
          return this.t > 10;
        },
        end(g) { g.dog.speed = 0; },
      };
    }

    /* ============================ sekwencje ============================ */
    seqSleep() {
      return [
        T.moveTo(ROOM.bed, 'walk', 'Idzie na posłanie'),
        T.play('lie', 1.2, { label: 'Układa się do snu' }),
        T.play('sleep', Infinity, {
          label: 'Śpi',
          fx: { type: 'zzz', at: 'headTop', every: 1.8 },
          onStart: (g) => { g.dog.asleep = true; },
          onTick: (g, dt) => { g.needAdd('energy', dt * (g.state.world.lights ? 1.1 : 2.2)); },
          until: (g) => g.state.needs.energy >= 99 && (g.state.world.lights || !g.isNight()),
          onEnd: (g) => { g.dog.asleep = false; },
        }),
        T.play('shake', 1.3, { label: 'Przeciąga się po drzemce' }),
      ];
    }

    seqEat() {
      const spot = () => this.bowlSpot(ROOM.food);
      return [
        T.moveTo(() => spot().x, this.state.needs.food < 40 ? 'run' : 'walk', 'Idzie do miski'),
        T.face(() => spot().facing),
        T.play('eat', Infinity, {
          label: 'Je z miski',
          onTick: (g, dt) => {
            const w = g.state.world;
            const eat = Math.min(w.foodBowl, dt / 4.5);
            w.foodBowl -= eat;
            g.needAdd('food', eat * 55);
          },
          until: (g) => g.state.world.foodBowl <= 0.001 || g.state.needs.food >= 100,
          onEnd: (g) => { g.state.progress.stats.meals++; g.addXp(4); g.addBond(2); },
        }),
        T.play('happy', 1.2, { label: 'Najedzony i zadowolony' }),
      ];
    }

    seqDrink() {
      const spot = () => this.bowlSpot(ROOM.water);
      return [
        T.moveTo(() => spot().x, 'walk', 'Idzie się napić'),
        T.face(() => spot().facing),
        T.play('drink', Infinity, {
          label: 'Pije wodę',
          onTick: (g, dt) => {
            const w = g.state.world;
            const d = Math.min(w.waterBowl, dt / 10);
            w.waterBowl -= d;
            g.needAdd('water', d * 110);
          },
          until: (g, t) => g.state.world.waterBowl <= 0.001 || g.state.needs.water >= 100 || t > 6,
        }),
      ];
    }

    seqBringToy(casual) {
      const spot = () => this.bowlSpot(this.ball.x);
      return [
        T.moveTo(() => spot().x, casual ? 'walk' : 'run', 'Idzie po piłkę'),
        T.face(() => spot().facing),
        T.play('sniff', 0.4, { label: 'Bierze piłkę' }),
        T.call((g) => { if (g.ball.state === 'floor') g.pickUpBall(); }),
        T.moveTo(ROOM.center + U.rand(-0.4, 0.4), 'walk', 'Niesie piłkę do Ciebie'),
        T.play('sit', 0.6, { label: 'Kładzie piłkę przed Tobą' }),
        T.call((g) => { if (g.dog.carrying === 'ball') g.dropBall(); }),
        T.play('play', 2.4, { label: 'Rzuć piłkę! (przycisk „Piłka”)' }),
      ];
    }

    seqFetch() {
      return [
        T.play('excited', 0.5, { label: 'Piłka!' }),
        this.chaseBallTask(),
        T.play('sniff', 0.35, { label: 'Łapie piłkę' }),
        T.call((g) => { g.pickUpBall(); g.fx('exclaim', 'headTop'); }),
        T.moveTo(ROOM.center + U.rand(-0.3, 0.3), 'run', 'Przynosi piłkę!'),
        T.play('sit', 0.6, { label: 'Oddaje piłkę' }),
        T.call((g) => {
          if (g.dog.carrying === 'ball') g.dropBall();
          g.needAdd('fun', 14);
          g.needAdd('energy', -3);
          g.addBond(5);
          g.addXp(6);
          g.addCoins(1);
          g.state.progress.stats.fetches++;
        }),
        T.play('happy', 1.3, { label: 'Jeszcze raz!', onStart: (g) => g.fx('hearts', 'head') }),
      ];
    }

    /* ============================ akcje gracza ============================ */
    feed() {
      if (this.scene !== 'home') return this.toast('Najpierw wróćcie do domu.');
      const w = this.state.world;
      if (w.foodBowl > 0.6) return this.toast('Miska jest jeszcze pełna.');
      if (this.item('food') < 1) return this.toast('Skończyła się karma – kup ją w sklepie.', 'warn');
      this.useItem('food');
      w.foodBowl = 1;
      this.emit('sound', 'kibble');
      if (this.dog.asleep) return this.toast('Miska pełna. ' + this.name() + ' zje po drzemce.');
      if (this.state.needs.food > 88) return this.toast(this.name() + ' nie jest teraz głodny – zje później.');
      this.interrupt(this.seqEat());
    }

    giveWater() {
      if (this.scene !== 'home') return this.toast('Najpierw wróćcie do domu.');
      const w = this.state.world;
      if (w.waterBowl > 0.85) return this.toast('W misce jest świeża woda.');
      w.waterBowl = 1;
      this.emit('sound', 'water');
      if (!this.dog.asleep && this.state.needs.water < 90) this.interrupt(this.seqDrink());
    }

    throwBall() {
      if (this.scene !== 'home') return this.toast('Piłka została w domu – na spacerze są patyki!');
      const b = this.ball;
      if (b.state === 'held' && this.dog.carrying === 'ball') {
        if (this.task && this.task.label === 'Rzuć piłkę! (przycisk „Piłka”)') { this.dropBall(); }
        else return this.toast(this.name() + ' trzyma piłkę – poczekaj, aż ją odda.');
      }
      if (this.state.needs.energy < 8) return this.toast(this.name() + ' jest zbyt zmęczony na zabawę.');
      const d = this.dog;
      const far = d.x < ROOM.center ? U.rand(ROOM.maxX - 1.6, ROOM.maxX - 0.5) : U.rand(ROOM.minX + 0.5, ROOM.minX + 1.6);
      const flight = 0.85;
      b.x = ROOM.center;
      b.y = -1.4;
      b.vx = (far - b.x) / (flight * 1.6);
      b.vy = (1.4 - (GRAVITY * flight * flight) / 2) / flight;
      b.state = 'air';
      this.emit('sound', 'throw');
      if (this.dog.asleep) return this.toast(this.name() + ' śpi i nie widział rzutu.');
      this.interrupt(this.seqFetch());
    }

    pet(zone) {
      const d = this.dog;
      if (d.asleep) {
        this.interrupt([
          T.play('curious', 1.2, { label: 'Zaspany… budzi się', onStart: (g) => g.fx('question', 'headTop') }),
          T.play('sit', 1.2, { label: 'Przeciera oczy' }),
        ]);
        return 'wake';
      }
      const t = this.task;
      if (t && (t.anim === 'pet' || t.anim === 'bellyup' || (t.anim === 'happy' && t.label && t.label.indexOf('Głaskanie') === 0)) && t.extend) {
        t.extend(1.6);
        this.rewardPet(t.anim === 'bellyup' ? 1.4 : 1);
        this.fx('hearts', 'head');
        return 'extend';
      }
      if (this.scene === 'park') {
        this.interrupt([T.play('happy', 1.4, { label: 'Cieszy się z pieszczot', onStart: (g) => g.fx('hearts', 'head') })]);
        this.rewardPet(1);
        return zone;
      }
      if (d.carrying === 'ball') return zone;
      switch (zone) {
        case 'tail':
          this.interrupt([
            T.play('curious', 0.4, { label: 'Hej, to mój ogon!', onStart: (g) => g.fx('exclaim', 'headTop') }),
            T.spin(2, 'Goni własny ogon!'),
            T.play('excited', 0.62, { label: 'Hau! Jeszcze!' }),
          ]);
          this.needAdd('fun', 4);
          return zone;
        case 'belly': {
          const lying = ['lie', 'sleep', 'bellyup', 'sit'].indexOf(d.anim) >= 0;
          if (!lying && D.bondTier(this.state.progress.bond).index < 1) {
            this.interrupt([T.play('curious', 1, { label: 'Jeszcze się wstydzi brzuszka…' })]);
            return zone;
          }
          const seq = [];
          if (d.anim !== 'lie' && d.anim !== 'bellyup') seq.push(T.play('lie', 0.45, { label: 'Kładzie się' }));
          seq.push(T.play('bellyup', 2.6, { label: 'Drapanie po brzuszku!', fx: { type: 'hearts', at: 'head', every: 0.8 } }));
          this.interrupt(seq);
          this.rewardPet(1.5);
          return zone;
        }
        case 'head':
          this.interrupt([T.play('pet', 1.9, { label: 'Głaskanie po głowie', onStart: (g) => g.fx('hearts', 'head') })]);
          this.rewardPet(1);
          return zone;
        default:
          this.interrupt([T.play('happy', 1.7, { label: 'Głaskanie po grzbiecie', onStart: (g) => g.fx('hearts', 'head') })]);
          this.rewardPet(1);
          return zone;
      }
    }

    rewardPet(mult) {
      const now = this.time;
      this.petLog = this.petLog.filter((t) => now - t < 60);
      const factor = Math.max(0.15, 1 - this.petLog.length * 0.1);
      this.petLog.push(now);
      this.addBond(3 * factor * mult);
      this.needAdd('fun', 3 * factor * mult);
      if (this.petLog.length <= 6) this.addXp(1);
      this.state.progress.stats.pets++;
    }

    comeToCenter(gait) {
      return Math.abs(this.dog.x - ROOM.center) > 1.0 && this.scene === 'home' ? [T.moveTo(ROOM.center + U.rand(-0.3, 0.3), gait || 'run', 'Przybiega do Ciebie')] : [];
    }

    command(trickId) {
      const tr = D.trick(trickId);
      if (!tr) return;
      if (this.state.progress.level < tr.level) return this.toast('Sztuczka „' + tr.name + '” odblokuje się na poziomie ' + tr.level + '.');
      if (this.dog.carrying === 'ball') this.dropBall();
      this.interrupt([
        ...this.comeToCenter('run'),
        T.play('curious', 0.55, { label: '„' + tr.command + '”' }),
        T.call((g) => g.resolveTrick(tr)),
      ]);
      this.emit('sound', 'command');
    }

    resolveTrick(tr) {
      const p = this.state.progress;
      const skill = p.tricks[tr.id] || 0;
      let chance = 0.12 + 0.86 * skill;
      const n = this.state.needs;
      if (n.energy < 15 || n.fun < 10) chance *= 0.5;
      if (Math.random() < chance) {
        const useTreat = this.state.settings.rewardTricks && this.item('treat') > 0;
        const gain = (useTreat ? 0.14 : 0.05) * (1 + p.bond / 1000);
        const was = skill;
        p.tricks[tr.id] = Math.min(1, skill + gain);
        p.stats.tricks++;
        this.addXp(5);
        this.addCoins(was >= 1 ? 2 : 1);
        this.addBond(2);
        if (was < 1 && p.tricks[tr.id] >= 1) this.toast(this.name() + ' opanował sztuczkę „' + tr.name + '”!', 'bond');
        const seq = [T.play(tr.anim, tr.duration, { label: 'Wykonuje: ' + tr.name, onStart: (g) => { g.fx('star', 'headTop'); if (tr.anim === 'bark') g.fx('bark', 'mouth'); } })];
        if (useTreat) seq.push(T.play('eat', 0.7, { label: 'Mniam, smakołyk!', onStart: (g) => { g.useItem('treat'); g.needAdd('food', 2); } }));
        seq.push(T.play('happy', 0.9, { label: 'Dumny z siebie' }));
        return seq;
      }
      p.tricks[tr.id] = Math.min(1, skill + 0.02);
      const wrong = U.pick(D.TRICKS.filter((t) => t.id !== tr.id && t.anim !== 'roll'));
      const seq = [T.play('curious', 1.3, { label: 'Przechyla głowę… jeszcze nie rozumie', onStart: (g) => g.fx('question', 'headTop') })];
      if (skill > 0.25 && Math.random() < 0.5) seq.push(T.play(wrong.anim, 1.4, { label: 'Pomylił komendę – zrobił „' + wrong.name + '”' }));
      return seq;
    }

    /** „Do mnie!” – pies przybiega i siada przy opiekunie. */
    callDog() {
      if (this.dog.asleep) return this.pet('head');
      if (this.scene === 'park') {
        this.interrupt([T.play('happy', 1.2, { label: 'Patrzy na Ciebie i merda', onStart: (g) => g.fx('hearts', 'head') })]);
        return;
      }
      this.interrupt([
        T.moveTo(ROOM.center + U.rand(-0.25, 0.25), Math.abs(this.dog.x - ROOM.center) > 1.2 ? 'run' : 'walk', 'Przybiega na zawołanie'),
        T.play('sit', 2.2, { label: 'Siedzi przy Tobie', onStart: (g) => { g.fx('hearts', 'head'); g.addBond(0.5); } }),
      ]);
    }

    /** Dotknięcie podłogi: pies idzie we wskazane miejsce i je obwąchuje. */
    callTo(x) {
      if (this.dog.asleep || this.scene !== 'home' || this.dog.carrying === 'ball') return;
      const far = Math.abs(x - this.dog.x) > 1.6;
      this.interrupt([
        T.moveTo(x, far ? 'run' : 'walk', 'Idzie, gdzie pokazujesz'),
        T.play('sniff', 1.2, { label: 'Obwąchuje to miejsce' }),
      ]);
    }

    giveTreat() {
      if (this.item('treat') < 1) return this.toast('Brak smakołyków – kup je w sklepie.', 'warn');
      if (this.dog.asleep) return this.toast(this.name() + ' śpi. Smakołyk poczeka.');
      this.interrupt([
        ...this.comeToCenter('run'),
        T.play('beg', 1.0, { label: 'Prosi o smakołyk' }),
        T.play('eat', 0.8, { label: 'Mniam!', onStart: (g) => { g.useItem('treat'); g.needAdd('food', 4); g.needAdd('fun', 3); g.addBond(5); g.addXp(2); } }),
        T.play('happy', 1.1, { label: 'Merda z wdzięczności', onStart: (g) => g.fx('hearts', 'head') }),
      ]);
    }

    giveBone() {
      if (this.scene !== 'home') return this.toast('Kość zostawmy na powrót do domu.');
      if (this.item('bone') < 1) return this.toast('Brak kości – kup ją w sklepie.', 'warn');
      this.interrupt([
        T.call((g) => { g.useItem('bone'); g.dog.carrying = 'bone'; }),
        T.play('excited', 0.62, { label: 'Kość!!!' }),
        T.moveTo(ROOM.bed + 0.2, 'walk', 'Niesie kość na posłanie'),
        T.play('lie', 10, {
          label: 'Gryzie kość',
          onTick: (g, dt) => { g.needAdd('fun', dt * 2.4); },
          onEnd: (g) => { g.dog.carrying = null; g.addBond(4); g.addXp(3); },
        }),
      ]);
    }

    bath() {
      if (this.scene !== 'home') return this.toast('Kąpiel dopiero w domu.');
      if (this.state.needs.clean > 92) return this.toast(this.name() + ' jest czysty jak łza.');
      if (this.item('shampoo') < 1) return this.toast('Brak szamponu – kup go w sklepie.', 'warn');
      this.interrupt([
        ...this.comeToCenter('walk'),
        T.play('sit', 3.4, {
          label: 'Kąpiel z pianką',
          expression: { eyes: 'closed' },
          fx: { type: 'bubbles', at: 'back', every: 0.3 },
          onStart: (g) => { g.useItem('shampoo'); g.emit('sound', 'water'); },
          onEnd: (g) => { g.state.needs.clean = 100; },
        }),
        T.play('shake', 1.3, { label: 'Otrzepuje się – uwaga, mokro!', fx: { type: 'drops', at: 'back', every: 0.2 } }),
        T.play('happy', 1.1, { label: 'Pachnący i zadowolony', onStart: (g) => { g.addXp(6); g.addBond(2); g.needAdd('fun', -4); g.state.progress.stats.baths++; } }),
      ]);
    }

    toggleLights() {
      const w = this.state.world;
      w.lights = !w.lights;
      this.toast(w.lights ? 'Światło zapalone.' : 'Światło zgaszone – czas na drzemkę.');
      if (!w.lights && !this.dog.asleep && this.state.needs.energy < 85 && this.scene === 'home') this.interrupt(this.seqSleep());
    }

    greet(minutesAway) {
      if (this.scene !== 'home') return;
      this.interrupt([
        T.moveTo(ROOM.center, 'run', 'Biegnie się przywitać!'),
        T.play('excited', minutesAway > 120 ? 2.5 : 1.3, { label: 'Cieszy się, że wróciłeś!', fx: { type: 'hearts', at: 'head', every: 0.6 } }),
        T.play('happy', 1.3, { label: 'Merda ogonem' }),
      ]);
      this.addBond(3);
    }

    /* ============================ spacer ============================ */
    startWalk() {
      if (this.scene === 'park') return;
      if (this.state.needs.energy < 12) return this.toast(this.name() + ' jest zbyt zmęczony na spacer.');
      this.interrupt([
        T.moveTo(ROOM.door - 0.6, 'run', 'Biegnie do drzwi!'),
        T.face(1),
        T.play('excited', 1.24, { label: 'Spacer!!!', fx: { type: 'exclaim', at: 'headTop', every: 2 } }),
        T.call((g) => { g.enterPark(); }),
      ]);
    }

    generatePark() {
      const objects = [];
      let x = 2.6;
      let sticks = 0;
      const length = 30;
      while (x < length - 2) {
        const type = U.weighted([
          { weight: 3, value: 'tree' }, { weight: 1.5, value: 'bush' }, { weight: sticks < 2 ? 1.2 : 0, value: 'stick' },
          { weight: 1, value: 'puddle' }, { weight: 1, value: 'digspot' }, { weight: 1, value: 'bird' }, { weight: 1, value: 'hydrant' },
        ]);
        if (type === 'stick') sticks++;
        objects.push({ type, x, done: false, seed: Math.random() });
        x += U.rand(2.4, 3.6);
      }
      return { length, objects, gained: 0, startedAt: this.time };
    }

    enterPark() {
      this.scene = 'park';
      this.park = this.generatePark();
      this.dog.x = 0.4;
      this.dog.facing = 1;
      this.queue = [];
      this.emit('scene', 'park');
    }

    parkEvent(obj) {
      const mark = (gain, extra) => T.call((g) => {
        obj.done = true;
        g.needAdd('walk', gain);
        g.park.gained += gain;
        if (extra) extra(g);
      });
      switch (obj.type) {
        case 'tree': return [T.play('sniff', 1.8, { label: 'Obwąchuje drzewo' }), mark(10)];
        case 'hydrant': return [T.play('sniff', 1.4, { label: 'Czyta psie wiadomości' }), mark(14)];
        case 'bush': return [T.play('sniff', 1.1, { label: 'Coś szeleści w krzakach…' }), T.play('curious', 0.7), mark(5)];
        case 'stick': return [
          T.play('sniff', 0.45, { label: 'Patyk!' }),
          mark(4, (g) => { g.dog.carrying = 'stick'; g.park.stick = true; g.toast(g.name() + ' znalazł patyk!'); }),
          T.play('happy', 0.9, { label: 'Dumnie niesie patyk' }),
        ];
        case 'puddle': return [
          mark(3),
          T.moveTo(obj.x + 0.7, 'run', 'Wskakuje w kałużę!'),
          T.call((g) => { g.needAdd('clean', -18); g.fx('splash', 'paws'); }),
          T.play('shake', 1.3, { label: 'Otrzepuje się z błota', fx: { type: 'drops', at: 'back', every: 0.2 } }),
        ];
        case 'digspot': return [
          T.play('dig', 2.2, { label: 'Kopie dołek!', fx: { type: 'dirt', at: 'paws', every: 0.25 } }),
          mark(6, (g) => { g.needAdd('fun', 8); g.needAdd('clean', -8); }),
        ];
        case 'bird': return [
          T.play('curious', 0.6, { label: 'Ptak!', onStart: (g) => g.fx('exclaim', 'headTop') }),
          mark(4, (g) => { obj.flown = g.time; g.needAdd('fun', 5); }),
          T.play('bark', 1.4, { label: 'Hau! Hau!', fx: { type: 'bark', at: 'mouth', every: 0.55 } }),
        ];
        default: return [mark(3)];
      }
    }

    endWalk(auto) {
      if (this.scene !== 'park') return;
      const park = this.park;
      const frac = U.clamp(this.dog.x / park.length, 0.15, 1);
      this.needAdd('walk', 30 * frac);
      this.needAdd('fun', 15 * frac);
      this.needAdd('energy', -16 * frac);
      const coins = Math.round(12 * frac);
      this.addCoins(coins);
      this.addXp(Math.round(22 * frac));
      this.addBond(10 * frac);
      this.state.progress.stats.walks++;
      if (park.stick) { this.state.inventory.items.stick = this.item('stick') + 1; this.state.progress.stats.sticks++; }
      this.dog.carrying = null;
      this.scene = 'home';
      this.park = null;
      this.task = null;
      this.queue = [];
      this.dog.x = ROOM.door - 0.4;
      this.dog.facing = -1;
      this.emit('scene', 'home');
      this.toast((auto ? 'Koniec spaceru! ' : 'Wracacie do domu. ') + '+' + coins + ' monet', 'reward');
      const seq = [T.moveTo(ROOM.center + U.rand(-0.5, 0.5), 'walk', 'Wraca do domu')];
      seq.push(this.state.needs.clean < 60 ? T.play('shake', 1.3, { label: 'Otrzepuje się', fx: { type: 'drops', at: 'back', every: 0.2 } }) : T.play('happy', 1.2, { label: 'Zadowolony po spacerze' }));
      this.queue = seq;
    }

    /* ============================ ekwipunek i sklep ============================ */
    equip(itemId) {
      const def = D.ITEMS[itemId];
      if (!def || def.kind !== 'wearable' || this.item(itemId) < 1) return;
      this.state.inventory.equipped[def.slot] = itemId;
      this.emit('equip', itemId);
    }

    unequip(slot) {
      this.state.inventory.equipped[slot] = null;
      this.emit('equip', null);
    }

    buy(itemId) {
      const def = D.ITEMS[itemId];
      if (!def || !def.price) return false;
      const p = this.state.progress;
      if (def.level && p.level < def.level) { this.toast('Dostępne od poziomu ' + def.level + '.'); return false; }
      if (def.kind === 'wearable' && this.item(itemId) > 0) { this.toast('Już to masz.'); return false; }
      if (p.coins < def.price) { this.toast('Za mało monet.', 'warn'); return false; }
      p.coins -= def.price;
      const it = this.state.inventory.items;
      it[itemId] = (it[itemId] || 0) + (def.pack || 1);
      this.toast('Kupiono: ' + def.name + (def.pack > 1 ? ' ×' + def.pack : ''), 'reward');
      this.emit('sound', 'coin');
      return true;
    }

    rename(name) {
      const n = String(name || '').trim().slice(0, 16);
      if (n) this.state.profile.name = n;
    }

    /** Zmiana wyglądu: zapisuje tylko identyfikator skórki. Nic więcej. */
    setSkin(skinId) {
      this.state.appearance.skinId = skinId;
      this.emit('skin', skinId);
    }

    claimDaily() {
      const today = new Date().toISOString().slice(0, 10);
      const p = this.state.progress;
      if (p.lastDaily === today) return false;
      p.lastDaily = today;
      p.coins += 20;
      return true;
    }
  }

  Game.ROOM = ROOM;
  P.Game = Game;
})(window.Pies = window.Pies || {});
