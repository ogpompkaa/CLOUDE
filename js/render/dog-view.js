/*
 * DOGVIEW – jedyne miejsce, przez które gra rozmawia z wyglądem psa.
 *
 * Wejście: „migawka” stanu psa z logiki (pozycja, kierunek, stan/animacja, nastrój,
 * noszony przedmiot, założone akcesoria). Wyjście: obraz na canvasie, punkty orientacyjne
 * (np. czubek głowy dla serduszek) i wynik trafienia w strefę dotyku.
 *
 * setSkin(skin) podmienia CAŁY wygląd w locie. Stan gry (imię, poziom, więź, ekwipunek)
 * nie jest tu w ogóle przechowywany, więc podmiana nie może go zresetować.
 */
(function (P) {
  'use strict';

  const U = P.util;
  const M = U.mat;

  class DogView {
    constructor() {
      this.skin = null;
      this.renderer = null;
      this.animator = new P.Animator();
      this.body = P.DogBody.fromProportions();
      this.facingAnim = 1;
      this.debug = false;
      this.skinMat = M.identity();
      this.lastPose = null;
    }

    setSkin(skin) {
      this.skin = skin;
      this.body = P.DogBody.fromProportions(skin.proportions);
      this.renderer = skin.renderer === 'frames' ? new P.FramesRenderer(skin) : new P.RigRenderer(skin);
      this.clips = Object.assign({}, P.DogClips, skin.clips || {});
      // animator zostaje – płynne przejście bez „skoku” do pozycji startowej
    }

    /** Stan obsługiwany przez skórkę (z uwzględnieniem łańcucha zastępstw). */
    resolveAnim(stateId) {
      const skin = this.skin;
      if (skin.renderer === 'frames') return P.DogStates.resolve(stateId, (s) => !!skin.animations[s]);
      return P.DogStates.resolve(stateId, (s) => !!this.clips[s] && !(skin.disabledStates || []).includes(s));
    }

    update(dt, snap) {
      if (!this.renderer) return;
      const target = snap.facing < 0 ? -1 : 1;
      const step = dt / 0.16;
      if (this.facingAnim < target) this.facingAnim = Math.min(target, this.facingAnim + step * 2);
      else if (this.facingAnim > target) this.facingAnim = Math.max(target, this.facingAnim - step * 2);

      if (this.skin.renderer === 'frames') {
        this.renderer.update(dt, snap);
        this.renderer.compute();
      } else {
        const s = Object.assign({}, snap, { anim: this.resolveAnim(snap.anim) });
        this.animator.update(dt, s, this.clips, this.body.height);
        const pose = this.animator.pose(this.clips, s);
        this.lastPose = pose;
        this.renderer.compute(pose, dt);
      }
      this.snap = snap;
      this._updateSkinMat(snap);
    }

    _updateSkinMat(snap) {
      const k = this.body.height / this.skin.fit.shoulderHeightPx;
      const native = this.skin.facing === 'left' ? -1 : 1;
      let fx = this.facingAnim;
      if (Math.abs(fx) < 0.08) fx = fx < 0 ? -0.08 : 0.08;
      this.skinMat = M.scale(M.translate(M.identity(), snap.x, -(snap.lift || 0)), k * fx * native, k);
    }

    /** Punkt ze skin space -> świat. */
    toWorld(pt) { return pt ? M.apply(this.skinMat, pt[0], pt[1]) : null; }

    landmark(name) {
      if (!this.renderer) return null;
      const pt = this.renderer.landmark(name);
      if (pt) return this.toWorld(pt);
      // awaryjnie: z logicznych proporcji
      const b = this.body;
      const x = this.snap ? this.snap.x : 0;
      const f = this.snap && this.snap.facing < 0 ? -1 : 1;
      const est = { head: [x + f * b.halfLength * 0.9, -b.height * 1.15], headTop: [x + f * b.halfLength * 0.8, -b.headTop],
        back: [x, -b.height * 0.95], belly: [x, -b.height * 0.55], tail: [x - f * b.halfLength, -b.height], mouth: [x + f * b.mouthReach, -b.height * 0.9] };
      return est[name] || [x, -b.height * 0.7];
    }

    /** Punkt zaczepienia (np. obroża) w świecie – do smyczy i efektów. */
    anchorWorld(name) {
      if (!this.renderer || !this.renderer.anchorMatrix) return null;
      const m = this.renderer.anchorMatrix(name);
      return m ? this.toWorld(M.apply(m, 0, 0)) : null;
    }

    /** Która strefa ciała została dotknięta (punkt w świecie). Zwraca id strefy albo null. */
    hitTest(wx, wy) {
      if (!this.skin) return null;
      const zones = this.skin.zones;
      const ids = Object.keys(zones).sort((a, b) => zones[b].priority - zones[a].priority);
      for (const id of ids) {
        const z = zones[id];
        const lm = this.landmark(z.landmark);
        if (!lm) continue;
        const r = z.radius * this.body.height;
        if (Math.hypot(wx - lm[0], wy - lm[1]) <= r) return id;
      }
      // logiczna bryła psa – elipsa z proporcji, nie z obrazka
      const b = this.body;
      const cx = this.snap ? this.snap.x : 0;
      const cy = -b.height * 0.7 - ((this.snap && this.snap.lift) || 0);
      const dx = (wx - cx) / (b.halfLength + 0.15 * b.height);
      const dy = (wy - cy) / (b.height * 0.8);
      return dx * dx + dy * dy <= 1 ? 'back' : null;
    }

    _extras(snap) {
      const extras = [];
      const anchors = this.skin.anchors;
      const shpx = this.skin.fit.shoulderHeightPx;
      const add = (anchorName, itemId) => {
        const def = this.renderer.anchorDef ? this.renderer.anchorDef(anchorName) : anchors[anchorName];
        if (!itemId || !def || !P.ItemArt.has(itemId)) return;
        extras.push({ z: def.z === undefined ? 100 : def.z, anchor: anchorName, draw: (ctx, d) => P.ItemArt.draw(ctx, itemId, d, shpx) });
      };
      const eq = snap.equipment || {};
      add('neck', eq.neck);
      add('head', eq.head);
      add('mouth', snap.carrying);
      return extras;
    }

    /** Rysuje psa. cameraMat: świat -> piksele canvasu (z uwzględnieniem DPR). */
    draw(ctx, cameraMat, opts) {
      if (!this.renderer || !this.snap) return;
      const screenMat = M.multiply(cameraMat, this.skinMat);
      if (!(opts && opts.noShadow)) this.drawShadow(ctx, cameraMat);
      ctx.save();
      ctx.imageSmoothingEnabled = true;
      ctx.imageSmoothingQuality = 'high';
      this.renderer.draw(ctx, screenMat, this.lastPose || {}, this._extras(this.snap));
      ctx.restore();
      if (this.debug) this.drawDebug(ctx, cameraMat, screenMat);
    }

    drawShadow(ctx, cameraMat) {
      const b = this.body;
      const lift = this.snap.lift || 0;
      const s = U.clamp(1 - lift * 1.5, 0.4, 1);
      const p = M.apply(cameraMat, this.snap.x, 0);
      const sc = Math.hypot(cameraMat[0], cameraMat[1]);
      ctx.save();
      ctx.setTransform(1, 0, 0, 1, 0, 0);
      ctx.beginPath();
      ctx.ellipse(p[0], p[1] + 2, b.halfLength * 1.15 * sc * s, b.height * 0.1 * sc * s, 0, 0, Math.PI * 2);
      ctx.fillStyle = 'rgba(40,24,10,' + (0.22 * s).toFixed(3) + ')';
      ctx.fill();
      ctx.restore();
    }

    drawDebug(ctx, cameraMat) {
      const toScreen = (wpt) => M.apply(cameraMat, wpt[0], wpt[1]);
      const fromSkin = (spt) => toScreen(this.toWorld(spt));
      const sc = Math.hypot(cameraMat[0], cameraMat[1]);
      ctx.save();
      ctx.setTransform(1, 0, 0, 1, 0, 0);
      ctx.lineWidth = 1.5;
      ctx.font = '600 ' + Math.round(10 * (window.devicePixelRatio || 1)) + 'px system-ui, sans-serif';
      // ziemia
      const g0 = toScreen([this.snap.x - 2, 0]);
      const g1 = toScreen([this.snap.x + 2, 0]);
      ctx.strokeStyle = 'rgba(0,160,255,0.8)';
      ctx.setLineDash([6, 4]);
      ctx.beginPath(); ctx.moveTo(g0[0], g0[1]); ctx.lineTo(g1[0], g1[1]); ctx.stroke();
      ctx.setLineDash([]);
      // kości
      for (const b of this.renderer.debugBones()) {
        const p = fromSkin(b.pivot);
        ctx.strokeStyle = 'rgba(255,0,170,0.9)';
        for (const c of b.children) { const q = fromSkin(c); ctx.beginPath(); ctx.moveTo(p[0], p[1]); ctx.lineTo(q[0], q[1]); ctx.stroke(); }
        ctx.fillStyle = '#ff00aa';
        ctx.beginPath(); ctx.arc(p[0], p[1], 3.5, 0, Math.PI * 2); ctx.fill();
      }
      // punkty styku
      ctx.fillStyle = '#ff3b30';
      for (const c of this.renderer.contactPoints()) { const p = fromSkin(c); ctx.fillRect(p[0] - 3, p[1] - 3, 6, 6); }
      // strefy dotyku
      const zones = this.skin.zones;
      for (const id of Object.keys(zones)) {
        const lm = this.landmark(zones[id].landmark);
        if (!lm) continue;
        const p = toScreen(lm);
        ctx.strokeStyle = 'rgba(30,200,90,0.95)';
        ctx.beginPath(); ctx.arc(p[0], p[1], zones[id].radius * this.body.height * sc, 0, Math.PI * 2); ctx.stroke();
        ctx.fillStyle = 'rgba(30,140,60,1)';
        ctx.fillText(id, p[0] + 4, p[1] - 4);
      }
      // punkty zaczepienia
      for (const name of Object.keys(this.skin.anchors)) {
        const am = this.renderer.anchorMatrix(name);
        if (!am) continue;
        const o = fromSkin(M.apply(am, 0, 0));
        const x = fromSkin(M.apply(am, 30, 0));
        ctx.strokeStyle = '#ffb300';
        ctx.beginPath(); ctx.moveTo(o[0], o[1]); ctx.lineTo(x[0], x[1]); ctx.stroke();
        ctx.fillStyle = '#b37700';
        ctx.fillText('⚓ ' + name, o[0] + 4, o[1] + 12);
      }
      // logiczna bryła
      const b = this.body;
      const c = toScreen([this.snap.x, -b.height * 0.7 - (this.snap.lift || 0)]);
      ctx.strokeStyle = 'rgba(0,120,255,0.6)';
      ctx.setLineDash([3, 3]);
      ctx.beginPath(); ctx.ellipse(c[0], c[1], (b.halfLength + 0.15 * b.height) * sc, b.height * 0.8 * sc, 0, 0, Math.PI * 2); ctx.stroke();
      ctx.setLineDash([]);
      ctx.restore();
    }
  }

  P.DogView = DogView;
})(window.Pies = window.Pies || {});
