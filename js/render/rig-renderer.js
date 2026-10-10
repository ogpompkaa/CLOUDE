/*
 * RENDERER WARSTWOWY („rig”)
 *
 * Składa psa z osobnych obrazków części ciała (tułów, głowa, uszy, ogon, łapy…)
 * zawieszonych na kanonicznym szkielecie. Każda część ma w konfiguracji skórki:
 *   src / variants – plik(i) grafiki
 *   pivot  – punkt obrotu w pikselach własnego obrazka
 *   parent – część, do której jest przyczepiona (albo "root")
 *   at     – gdzie w obrazku rodzica leży pivot tej części (dla "root": względem ziemi pod psem)
 *   z      – kolejność rysowania, rotation – obrót spoczynkowy, scale – skala
 *   motion – mnożnik siły animacji, dynamics – sprężystość (uszy, ogon)
 *   contacts – punkty, którymi część może dotykać ziemi (łapy, brzuch, zad)
 *
 * Współrzędne „skin space”: piksele grafiki, oś Y w dół, (0,0) = ziemia pod środkiem psa.
 */
(function (P) {
  'use strict';

  const U = P.util;
  const M = U.mat;
  const S = P.DogStates;

  class RigRenderer {
    constructor(skin) {
      this.skin = skin;
      this.shpx = skin.fit.shoulderHeightPx;
      this.springs = {};
      this.mats = {};
      this.rootTrack = { y: null, v: 0, a: 0 };
      this.lastPose = null;
    }

    motionFactor(id) {
      const part = this.skin.parts[id];
      const group = S.boneGroup[id];
      return (group ? this.skin.motion[group] : 1) * (part ? part.motion : 1);
    }

    /** Lokalny obrót części (stopnie) = spoczynkowy + animacja × mnożniki. */
    targetAngle(id, pose) {
      const part = this.skin.parts[id];
      const b = pose.bones[id];
      return part.rotation + (b && b.r ? b.r * this.motionFactor(id) : 0);
    }

    /* -------- dynamika (sprężyste uszy i ogon) -------- */
    stepDynamics(dt, pose) {
      if (!(dt > 0)) return;
      const parts = this.skin.parts;
      const cumulative = {};
      const cum = (id) => {
        if (id === 'root' || !parts[id]) return pose.root.r || 0;
        if (cumulative[id] !== undefined) return cumulative[id];
        const v = cum(parts[id].parent) + this.angleOf(id, pose);
        cumulative[id] = v;
        return v;
      };
      const accY = this.rootTrack.a / this.shpx;
      const order = this.skin.drawOrder;
      for (const id of order) {
        const part = parts[id];
        const dyn = part.dynamics;
        if (!dyn) continue;
        const st = this.springs[id] || (this.springs[id] = { angle: this.targetAngle(id, pose), vel: 0, pc: null, pv: 0 });
        const target = this.targetAngle(id, pose);
        const pc = cum(part.parent);
        let pAcc = 0;
        if (st.pc !== null) {
          const pv = (pc - st.pc) / dt;
          pAcc = (pv - st.pv) / dt;
          st.pv = pv;
        }
        st.pc = pc;
        const k = dyn.stiffness || 120;
        const d = dyn.damping || 10;
        let acc = k * (target - st.angle) - d * st.vel - (dyn.inertia || 0) * pAcc - (dyn.bounce || 0) * accY;
        acc = U.clamp(acc, -60000, 60000);
        st.vel = U.clamp(st.vel + acc * dt, -2400, 2400);
        st.angle += st.vel * dt;
        const lim = dyn.maxOffset || 70;
        st.angle = U.clamp(st.angle, target - lim, target + lim);
      }
    }

    angleOf(id, pose) {
      const st = this.springs[id];
      return st && this.skin.parts[id].dynamics ? st.angle : this.targetAngle(id, pose);
    }

    /* -------- składanie macierzy w skin space -------- */
    compute(pose, dt) {
      const skin = this.skin;
      const parts = skin.parts;
      const shpx = this.shpx;
      this.stepDynamics(dt, pose);

      const root = pose.root || {};
      const rp = skin.fit.rotationPivot;
      let m0 = M.translate(M.identity(), (root.x || 0) * shpx, (root.y || 0) * shpx);
      m0 = M.translate(m0, rp[0], rp[1]);
      m0 = M.rotate(m0, U.deg(root.r || 0));
      m0 = M.scale(m0, root.sx === undefined ? 1 : root.sx, root.sy === undefined ? 1 : root.sy);
      m0 = M.translate(m0, -rp[0], -rp[1]);

      const mats = {};
      const visit = (id, parentMat) => {
        const part = parts[id];
        const b = pose.bones[id] || {};
        let m = M.translate(parentMat, part.at[0] + (b.x || 0) * shpx, part.at[1] + (b.y || 0) * shpx);
        m = M.rotate(m, U.deg(this.angleOf(id, pose)));
        const sx = part.scale * (b.sx === undefined ? 1 : b.sx) * (part.flipX ? -1 : 1);
        const sy = part.scale * (b.sy === undefined ? 1 : b.sy);
        m = M.scale(m, sx, sy);
        m = M.translate(m, -part.pivot[0], -part.pivot[1]);
        mats[id] = m;
        for (const c of part.children) visit(c, m);
      };
      for (const id of skin.rootParts) visit(id, m0);

      // stawianie na ziemi: najniższy punkt styku dotyka y = 0
      let maxY = -Infinity;
      for (const id of Object.keys(parts)) {
        const cs = parts[id].contacts;
        for (let i = 0; i < cs.length; i++) {
          const pt = M.apply(mats[id], cs[i][0], cs[i][1]);
          if (pt[1] > maxY) maxY = pt[1];
        }
      }
      if (maxY === -Infinity) maxY = 0;
      const lift = (root.lift || 0) * shpx;
      const shift = -maxY - lift;
      const g = [1, 0, 0, 1, 0, shift];
      for (const id of Object.keys(mats)) mats[id] = M.multiply(g, mats[id]);

      // śledzenie ruchu pionowego (dla sprężyn)
      const tr = this.rootTrack;
      if (dt > 0) {
        if (tr.y !== null) {
          const v = (shift - tr.y) / dt;
          tr.a = U.approach(tr.a, (v - tr.v) / dt, 30, dt);
          tr.v = v;
        }
        tr.y = shift;
      }
      this.mats = mats;
      this.lastPose = pose;
      return mats;
    }

    partSize(part, img) {
      if (part.size) return part.size;
      if (img) return [img.naturalWidth || img.width, img.naturalHeight || img.height];
      return [part.pivot[0] * 2, part.pivot[1] * 2];
    }

    imageFor(part, pose) {
      const imgs = this.skin.images;
      if (part.variants && part.expression) {
        const want = part.expression === 'eyes' ? pose.eyes : pose.mouth;
        const v = S.resolveExpression(part.expression, want, (name) => !!(part.variants[name] && imgs[part.variants[name]]));
        if (v) return imgs[part.variants[v]];
        return part.src ? imgs[part.src] : null;
      }
      return imgs[part.src];
    }

    /** Lista elementów do narysowania (części + przedmioty na punktach zaczepienia), posortowana po z. */
    drawList(extras) {
      const list = this.skin.drawOrder.map((id) => ({ z: this.skin.parts[id].z, part: id }));
      for (const e of extras || []) list.push(e);
      return list.sort((a, b) => a.z - b.z);
    }

    draw(ctx, screenMat, pose, extras) {
      const parts = this.skin.parts;
      for (const item of this.drawList(extras)) {
        if (item.part) {
          const part = parts[item.part];
          const img = this.imageFor(part, pose);
          const size = this.partSize(part, img);
          const m = M.multiply(screenMat, this.mats[item.part]);
          ctx.setTransform(m[0], m[1], m[2], m[3], m[4], m[5]);
          if (img) {
            const scale = Math.max(Math.hypot(m[0], m[1]), Math.hypot(m[2], m[3]));
            const r = P.skins.raster(img, size[0], size[1], scale);
            ctx.drawImage(r, 0, 0, size[0], size[1]);
          } else {
            ctx.fillStyle = 'rgba(120,120,140,0.25)';
            ctx.strokeStyle = 'rgba(120,120,140,0.8)';
            ctx.lineWidth = 2;
            ctx.fillRect(0, 0, size[0], size[1]);
            ctx.strokeRect(0, 0, size[0], size[1]);
          }
        } else if (item.draw) {
          const m = M.multiply(screenMat, this.anchorMatrix(item.anchor));
          ctx.setTransform(m[0], m[1], m[2], m[3], m[4], m[5]);
          item.draw(ctx, this.skin.anchors[item.anchor]);
        }
      }
    }

    /** Macierz punktu zaczepienia (obroża, czapka, przedmiot w pysku) w skin space. */
    anchorMatrix(name) {
      const a = this.skin.anchors[name];
      if (!a) return null;
      const base = this.mats[a.part] || M.identity();
      let m = M.translate(base, a.at[0], a.at[1]);
      return M.rotate(m, U.deg(a.rotation || 0));
    }

    /** Punkt orientacyjny (głowa, grzbiet, brzuch, ogon…) w skin space. */
    landmark(name) {
      const parts = this.skin.parts;
      const lm = this.skin.landmarks[name];
      if (lm && this.mats[lm.part]) return M.apply(this.mats[lm.part], lm.at[0], lm.at[1]);
      // domyślne punkty – środek odpowiedniej części
      const fallbackPart = { head: 'head', headTop: 'head', back: 'body', belly: 'body', tail: 'tail', mouth: 'head' }[name];
      const part = parts[fallbackPart];
      if (!part || !this.mats[fallbackPart]) return null;
      const size = this.partSize(part, this.skin.images[part.src]);
      const local = {
        head: [size[0] * 0.5, size[1] * 0.45], headTop: [size[0] * 0.45, 0], back: [size[0] * 0.5, size[1] * 0.3],
        belly: [size[0] * 0.55, size[1] * 0.85], tail: [size[0] * 0.5, size[1] * 0.2], mouth: [size[0] * 0.9, size[1] * 0.7],
      }[name];
      return M.apply(this.mats[fallbackPart], local[0], local[1]);
    }

    contactPoints() {
      const out = [];
      const parts = this.skin.parts;
      for (const id of Object.keys(parts)) for (const c of parts[id].contacts) out.push(M.apply(this.mats[id], c[0], c[1]));
      return out;
    }

    debugBones() {
      const out = [];
      const parts = this.skin.parts;
      for (const id of Object.keys(parts)) {
        const part = parts[id];
        const pv = M.apply(this.mats[id], part.pivot[0], part.pivot[1]);
        const children = part.children.map((c) => {
          const cp = parts[c];
          return M.apply(this.mats[c], cp.pivot[0], cp.pivot[1]);
        });
        out.push({ id, pivot: pv, children });
      }
      return out;
    }
  }

  P.RigRenderer = RigRenderer;
})(window.Pies = window.Pies || {});
