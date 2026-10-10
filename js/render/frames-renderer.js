/*
 * RENDERER KLATKOWY („frames”)
 *
 * Dla skórek złożonych z gotowych klatek (np. wycięte zdjęcia prawdziwego psa w kolejnych
 * pozach albo arkusz sprite'ów). Każdy stan ma listę klatek i tempo (fps). Brakujące stany
 * zastępowane są wg DogStates (fallback), więc wystarczy zacząć od samego `idle`.
 *
 * fit.origin – punkt w klatce leżący na ziemi pod środkiem psa (wspólny dla wszystkich klatek).
 * landmarks / anchors – punkty w pikselach klatki; można je nadpisać dla konkretnej animacji.
 */
(function (P) {
  'use strict';

  const U = P.util;
  const M = U.mat;
  const S = P.DogStates;

  class FramesRenderer {
    constructor(skin) {
      this.skin = skin;
      this.shpx = skin.fit.shoulderHeightPx;
      this.anim = 'idle';
      this.animId = 'idle';
      this.t = 0;
      this.progress = 0;
      this.base = M.identity();
    }

    resolve(stateId) {
      return S.resolve(stateId, (s) => !!this.skin.animations[s]);
    }

    update(dt, snap) {
      const id = this.resolve(snap.anim);
      if (id !== this.animId || snap.anim !== this.anim) { this.t = 0; }
      this.anim = snap.anim;
      this.animId = id;
      this.t += dt;
      this.progress = snap.animDuration > 0 ? U.clamp(snap.animTime / snap.animDuration, 0, 0.9999) : 0;
    }

    frameInfo() {
      const a = this.skin.animations[this.animId];
      const count = a.frames ? a.frames.length : a.sheet.count;
      let idx;
      if (!a.loop && S.isOneShot(this.anim)) idx = Math.floor(this.progress * count);
      else idx = a.loop ? Math.floor(this.t * a.fps) % count : Math.min(count - 1, Math.floor(this.t * a.fps));
      return { a, idx, count };
    }

    compute() {
      const o = this.skin.fit.origin;
      this.base = M.translate(M.identity(), -o[0], -o[1]);
      return this.base;
    }

    draw(ctx, screenMat, pose, extras) {
      const { a, idx } = this.frameInfo();
      const m = M.multiply(screenMat, this.base);
      ctx.setTransform(m[0], m[1], m[2], m[3], m[4], m[5]);
      const scale = Math.hypot(m[0], m[1]);
      if (a.frames) {
        const img = this.skin.images[a.frames[idx]];
        if (img) {
          const w = this.skin.frameSize ? this.skin.frameSize[0] : img.naturalWidth;
          const h = this.skin.frameSize ? this.skin.frameSize[1] : img.naturalHeight;
          ctx.drawImage(P.skins.raster(img, w, h, scale), 0, 0, w, h);
        }
      } else {
        const sh = a.sheet;
        const img = this.skin.images[sh.src];
        if (img) {
          const cols = sh.columns || Math.floor(img.naturalWidth / sh.frameWidth);
          const sx = (idx % cols) * sh.frameWidth;
          const sy = Math.floor(idx / cols) * sh.frameHeight;
          ctx.drawImage(img, sx, sy, sh.frameWidth, sh.frameHeight, 0, 0, sh.frameWidth, sh.frameHeight);
        }
      }
      for (const e of extras || []) {
        const am = this.anchorMatrix(e.anchor);
        if (!am) continue;
        const mm = M.multiply(screenMat, am);
        ctx.setTransform(mm[0], mm[1], mm[2], mm[3], mm[4], mm[5]);
        e.draw(ctx, this.anchorDef(e.anchor));
      }
    }

    anchorDef(name) {
      const a = this.skin.animations[this.animId];
      return (a.anchors && a.anchors[name]) || this.skin.anchors[name] || null;
    }

    anchorMatrix(name) {
      const def = this.anchorDef(name);
      if (!def || def.hidden) return null;
      return M.rotate(M.translate(this.base, def.at[0], def.at[1]), U.deg(def.rotation || 0));
    }

    landmark(name) {
      const a = this.skin.animations[this.animId];
      const lm = (a.landmarks && a.landmarks[name]) || this.skin.landmarks[name];
      if (!lm) return null;
      const pt = Array.isArray(lm) ? lm : lm.at;
      return M.apply(this.base, pt[0], pt[1]);
    }

    contactPoints() { return []; }
    debugBones() { return []; }
  }

  P.FramesRenderer = FramesRenderer;
})(window.Pies = window.Pies || {});
