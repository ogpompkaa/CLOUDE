/*
 * ANIMATOR – odtwarza klipy szkieletowe dla stanu podanego przez logikę.
 * Obsługuje płynne przejścia (crossfade), synchronizację kroku z prędkością
 * ruchu oraz mruganie. Nie wie nic o grafikach – zwraca tylko pozę.
 */
(function (P) {
  'use strict';

  const U = P.util;

  function emptyPose() { return { root: {}, bones: {}, eyes: 'open', mouth: 'closed', blink: true }; }

  function lerpPose(a, b, w) {
    const out = emptyPose();
    const rootKeys = ['x', 'y', 'r', 'lift'];
    for (const k of rootKeys) out.root[k] = U.lerp(a.root[k] || 0, b.root[k] || 0, w);
    for (const k of ['sx', 'sy']) out.root[k] = U.lerp(a.root[k] === undefined ? 1 : a.root[k], b.root[k] === undefined ? 1 : b.root[k], w);
    const names = new Set(Object.keys(a.bones).concat(Object.keys(b.bones)));
    names.forEach((n) => {
      const x = a.bones[n] || {};
      const y = b.bones[n] || {};
      out.bones[n] = {
        r: U.lerp(x.r || 0, y.r || 0, w),
        x: U.lerp(x.x || 0, y.x || 0, w),
        y: U.lerp(x.y || 0, y.y || 0, w),
        sx: U.lerp(x.sx === undefined ? 1 : x.sx, y.sx === undefined ? 1 : y.sx, w),
        sy: U.lerp(x.sy === undefined ? 1 : x.sy, y.sy === undefined ? 1 : y.sy, w),
      };
    });
    const dom = w < 0.5 ? a : b;
    out.eyes = dom.eyes;
    out.mouth = dom.mouth;
    out.blink = dom.blink;
    return out;
  }

  class Animator {
    constructor() {
      this.cur = null;
      this.prev = null;
      this.fade = 1;
      this.fadeDur = 0.22;
      this.blinkIn = U.rand(1.5, 4);
      this.blinkLeft = 0;
    }

    _track(id) { return { id, t: 0, phase: 0 }; }

    setState(id) {
      if (this.cur && this.cur.id === id) return;
      if (this.cur) {
        this.prev = this.cur;
        this.fade = 0;
      }
      this.cur = this._track(id);
    }

    _advance(track, dt, clip, snap, heightScale) {
      track.t += dt;
      if (!clip) return;
      if (clip.gaitDistance && snap.speed > 0.01) {
        track.phase = (track.phase + (dt * snap.speed) / (clip.gaitDistance * heightScale)) % 1;
      } else {
        track.phase = (track.phase + dt / (clip.period || 1)) % 1;
      }
    }

    update(dt, snap, clips, heightScale) {
      this.setState(snap.anim);
      this._advance(this.cur, dt, clips[this.cur.id], snap, heightScale || 1);
      if (this.prev) {
        this._advance(this.prev, dt, clips[this.prev.id], snap, heightScale || 1);
        this.fade = Math.min(1, this.fade + dt / this.fadeDur);
        if (this.fade >= 1) this.prev = null;
      }
      // mruganie
      if (this.blinkLeft > 0) this.blinkLeft -= dt;
      else {
        this.blinkIn -= dt;
        if (this.blinkIn <= 0) {
          this.blinkLeft = 0.13;
          this.blinkIn = U.chance(0.2) ? 0.25 : U.rand(2, 5.5);
        }
      }
    }

    _sample(track, clips, snap) {
      const clip = clips[track.id];
      if (!clip) return emptyPose();
      const progress = snap.animDuration > 0 && track === this.cur ? U.clamp(snap.animTime / snap.animDuration, 0, 1) : (track.t / (clip.period || 1)) % 1;
      const raw = clip.sample({ t: track.t, phase: track.phase, progress, mood: snap.mood || { happy: 0.5, energy: 0.5 } }) || {};
      const pose = emptyPose();
      Object.assign(pose.root, raw.root || {});
      pose.bones = raw.bones || {};
      if (raw.eyes) pose.eyes = raw.eyes;
      if (raw.mouth) pose.mouth = raw.mouth;
      if (raw.blink === false) pose.blink = false;
      return pose;
    }

    pose(clips, snap) {
      let pose = this._sample(this.cur, clips, snap);
      if (this.prev && this.fade < 1) pose = lerpPose(this._sample(this.prev, clips, snap), pose, U.smooth(this.fade));
      if (pose.blink !== false && pose.eyes === 'open' && this.blinkLeft > 0) pose.eyes = 'closed';
      if (snap.expression) Object.assign(pose, snap.expression);
      return pose;
    }
  }

  P.Animator = Animator;
  P.lerpPose = lerpPose;
})(window.Pies = window.Pies || {});
