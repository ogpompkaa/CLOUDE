/*
 * SCENA – tło (pokój albo park), przedmioty w świecie, oświetlenie i efekty cząsteczkowe.
 * Rysuje w jednostkach świata (1 = wysokość w kłębie psa wzorcowego, ziemia y = 0, Y w dół).
 * Scena nie wie nic o skórce psa – pozycje efektów dostaje jako punkty w świecie.
 */
(function (P) {
  'use strict';

  const U = P.util;
  const OUT = '#3a2418';

  function hash(n) { const s = Math.sin(n * 127.1 + 311.7) * 43758.5453; return s - Math.floor(s); }

  function roundRect(ctx, x, y, w, h, r) {
    ctx.beginPath();
    ctx.moveTo(x + r, y);
    ctx.arcTo(x + w, y, x + w, y + h, r);
    ctx.arcTo(x + w, y + h, x, y + h, r);
    ctx.arcTo(x, y + h, x, y, r);
    ctx.arcTo(x, y, x + w, y, r);
    ctx.closePath();
  }

  /** Kolory nieba wg pory dnia (godzina lokalna). */
  function skyColors(hour) {
    const keys = [
      { h: 0, top: '#0e1633', bot: '#25305c' },
      { h: 5.5, top: '#2c3a6e', bot: '#f2a37a' },
      { h: 7.5, top: '#79b6ef', bot: '#cfe8ff' },
      { h: 12, top: '#5aa6f0', bot: '#bfe1ff' },
      { h: 17.5, top: '#6aa3e0', bot: '#ffd2a1' },
      { h: 19.5, top: '#3b3f7a', bot: '#ff9a76' },
      { h: 21, top: '#151d42', bot: '#3a3f73' },
      { h: 24, top: '#0e1633', bot: '#25305c' },
    ];
    let a = keys[0], b = keys[1];
    for (let i = 0; i < keys.length - 1; i++) if (hour >= keys[i].h && hour <= keys[i + 1].h) { a = keys[i]; b = keys[i + 1]; break; }
    const t = (hour - a.h) / (b.h - a.h || 1);
    const mix = (c1, c2) => {
      const p = (c) => [parseInt(c.slice(1, 3), 16), parseInt(c.slice(3, 5), 16), parseInt(c.slice(5, 7), 16)];
      const x = p(c1), y = p(c2);
      return 'rgb(' + x.map((v, i) => Math.round(U.lerp(v, y[i], t))).join(',') + ')';
    };
    return { top: mix(a.top, b.top), bot: mix(a.bot, b.bot), night: hour < 6 || hour >= 20.5 };
  }

  class Scene {
    constructor() {
      this.particles = [];
      this.camX = 3.8;
      this.time = 0;
      this.zoom = 1;
      this.zoomTarget = 1;
    }

    /* ---------------- kamera ---------------- */
    layout(w, h, dpr) {
      this.w = w;
      this.h = h;
      this.dpr = dpr;
      const viewW = U.clamp(w / 140, 3.4, 7.6); // na szerokim ekranie widać więcej pokoju
      this.ppu = Math.min(h / 3.0, w / viewW);
      this.floorDepth = Math.min(0.62 * this.ppu, h * 0.24);
      this.groundY = h - this.floorDepth;
      this.viewW = w / this.ppu;
    }

    follow(targetX, dt, scene, roomWidth, snap) {
      this.zoom = snap ? this.zoomTarget : U.approach(this.zoom, this.zoomTarget, 5, dt);
      let tx = targetX;
      if (this.zoomTarget > 1) {
        tx = targetX;
      } else if (scene === 'home') {
        const half = this.viewW / 2;
        tx = this.viewW >= roomWidth ? roomWidth / 2 : U.clamp(targetX, half, roomWidth - half);
      } else {
        tx = targetX + this.viewW * 0.12;
      }
      this.camX = snap ? tx : U.approach(this.camX, tx, 3.2, dt);
    }

    get scale() { return this.ppu * this.zoom; }

    matrix() {
      const k = this.scale * this.dpr;
      return [k, 0, 0, k, (this.w / 2 - this.camX * this.scale) * this.dpr, this.groundY * this.dpr];
    }

    screenToWorld(sx, sy) { return [(sx - this.w / 2) / this.scale + this.camX, (sy - this.groundY) / this.scale]; }

    /* ---------------- rysowanie tła ---------------- */
    drawBackground(ctx, game) {
      this.time = game.time;
      const m = this.matrix();
      ctx.setTransform(m[0], m[1], m[2], m[3], m[4], m[5]);
      if (game.scene === 'park') this.drawPark(ctx, game);
      else this.drawRoom(ctx, game);
    }

    worldBounds() {
      const k = this.scale;
      const top = -this.groundY / k;
      const half = this.w / k / 2;
      return { left: this.camX - half - 0.2, right: this.camX + half + 0.2, top: top - 0.1, bottom: this.floorDepth / k + 0.1 };
    }

    drawRoom(ctx, game) {
      const R = game.room;
      const b = this.worldBounds();
      const L = Math.min(b.left, -0.5), Rr = Math.max(b.right, R.width + 0.5);
      // ściana
      const wall = ctx.createLinearGradient(0, b.top, 0, 0);
      wall.addColorStop(0, '#f3dfc3');
      wall.addColorStop(1, '#ead0ad');
      ctx.fillStyle = wall;
      ctx.fillRect(L, b.top, Rr - L, -b.top);
      // tapeta w delikatne łapki
      ctx.fillStyle = 'rgba(190,140,95,0.13)';
      for (let gx = Math.floor(L / 0.7) * 0.7; gx < Rr; gx += 0.7) {
        for (let gy = -0.6; gy > b.top; gy -= 0.7) {
          const off = (Math.round(gy / 0.7) % 2) * 0.35;
          this.paw(ctx, gx + off, gy, 0.07);
        }
      }
      // listwa przypodłogowa
      ctx.fillStyle = '#d7a877';
      ctx.fillRect(L, -0.14, Rr - L, 0.14);
      ctx.fillStyle = 'rgba(255,255,255,0.35)';
      ctx.fillRect(L, -0.14, Rr - L, 0.025);
      // podłoga
      const fd = this.floorDepth / this.scale + 0.2;
      const floor = ctx.createLinearGradient(0, 0, 0, fd);
      floor.addColorStop(0, '#c98b52');
      floor.addColorStop(1, '#a96b39');
      ctx.fillStyle = floor;
      ctx.fillRect(L, 0, Rr - L, fd);
      ctx.strokeStyle = 'rgba(90,50,20,0.22)';
      ctx.lineWidth = 0.012;
      let yy = 0.07;
      for (let i = 0; yy < fd; i++) {
        ctx.beginPath(); ctx.moveTo(L, yy); ctx.lineTo(Rr, yy); ctx.stroke();
        const step = 0.9 + hash(i) * 0.6;
        for (let x = L + hash(i + 7) * step; x < Rr; x += step) { ctx.beginPath(); ctx.moveTo(x, yy); ctx.lineTo(x, yy + 0.07 + i * 0.03); ctx.stroke(); }
        yy += 0.07 + i * 0.03;
      }
      // dywan
      ctx.fillStyle = '#7fb5a6';
      ctx.beginPath(); ctx.ellipse(R.center, 0.17, 1.55, 0.13, 0, 0, Math.PI * 2); ctx.fill();
      ctx.strokeStyle = '#f4ead6'; ctx.lineWidth = 0.025;
      ctx.beginPath(); ctx.ellipse(R.center, 0.17, 1.4, 0.1, 0, 0, Math.PI * 2); ctx.stroke();

      this.drawWindow(ctx, R.window, game);
      this.drawFrame(ctx, 1.45, -1.95);
      this.drawClock(ctx, 5.45, -2.05);
      this.drawDoor(ctx, R.door);
      this.drawPlant(ctx, 0.15);
      this.drawShelf(ctx, 5.7, -1.45);
      this.drawBed(ctx, R.bed, game);
      this.drawBasket(ctx, R.toys - 0.25);
      this.drawBowl(ctx, R.food, game.state.world.foodBowl, 'food');
      this.drawBowl(ctx, R.water, game.state.world.waterBowl, 'water');
    }

    paw(ctx, x, y, s) {
      ctx.beginPath(); ctx.ellipse(x, y, s, s * 0.85, 0, 0, Math.PI * 2); ctx.fill();
      for (let i = -1.5; i <= 1.5; i++) { ctx.beginPath(); ctx.ellipse(x + i * s * 0.72, y - s * (1.25 - Math.abs(i) * 0.18), s * 0.32, s * 0.4, 0, 0, Math.PI * 2); ctx.fill(); }
    }

    drawWindow(ctx, cx, game) {
      const w = 1.55, h = 1.3, top = -2.6;
      const hour = new Date().getHours() + new Date().getMinutes() / 60;
      const sky = skyColors(hour);
      const g = ctx.createLinearGradient(0, top, 0, top + h);
      g.addColorStop(0, sky.top);
      g.addColorStop(1, sky.bot);
      ctx.fillStyle = g;
      ctx.fillRect(cx - w / 2, top, w, h);
      ctx.save();
      ctx.beginPath(); ctx.rect(cx - w / 2, top, w, h); ctx.clip();
      if (sky.night) {
        ctx.fillStyle = '#fff';
        for (let i = 0; i < 14; i++) { ctx.globalAlpha = 0.5 + 0.5 * Math.sin(this.time * 2 + i); ctx.fillRect(cx - w / 2 + hash(i) * w, top + hash(i + 3) * h * 0.6, 0.018, 0.018); }
        ctx.globalAlpha = 1;
        ctx.fillStyle = '#fff6d8';
        ctx.beginPath(); ctx.arc(cx + 0.38, top + 0.3, 0.13, 0, Math.PI * 2); ctx.fill();
        ctx.fillStyle = sky.top;
        ctx.beginPath(); ctx.arc(cx + 0.44, top + 0.26, 0.11, 0, Math.PI * 2); ctx.fill();
      } else {
        ctx.fillStyle = 'rgba(255,240,170,0.95)';
        ctx.beginPath(); ctx.arc(cx - 0.4, top + 0.32, 0.14, 0, Math.PI * 2); ctx.fill();
        ctx.fillStyle = 'rgba(255,255,255,0.9)';
        const cxx = cx - w / 2 + ((this.time * 0.03) % 1) * (w + 0.8) - 0.4;
        [0, 0.14, 0.28].forEach((o, i) => { ctx.beginPath(); ctx.arc(cxx + o, top + 0.55 - (i === 1 ? 0.06 : 0), 0.1, 0, Math.PI * 2); ctx.fill(); });
      }
      // dachy i drzewa za oknem
      ctx.fillStyle = sky.night ? '#1d2440' : '#88b97e';
      ctx.beginPath(); ctx.ellipse(cx - 0.3, top + h + 0.05, 0.6, 0.32, 0, 0, Math.PI * 2); ctx.fill();
      ctx.fillStyle = sky.night ? '#262e52' : '#9fcb8d';
      ctx.beginPath(); ctx.ellipse(cx + 0.5, top + h + 0.1, 0.55, 0.3, 0, 0, Math.PI * 2); ctx.fill();
      ctx.restore();
      // rama
      ctx.lineJoin = 'round';
      ctx.strokeStyle = '#fffaf1';
      ctx.lineWidth = 0.07;
      ctx.strokeRect(cx - w / 2, top, w, h);
      ctx.lineWidth = 0.04;
      ctx.beginPath(); ctx.moveTo(cx, top); ctx.lineTo(cx, top + h); ctx.moveTo(cx - w / 2, top + h * 0.45); ctx.lineTo(cx + w / 2, top + h * 0.45); ctx.stroke();
      ctx.fillStyle = '#fffaf1';
      roundRect(ctx, cx - w / 2 - 0.12, top + h, w + 0.24, 0.08, 0.03); ctx.fill();
      // zasłony
      ctx.fillStyle = '#e78a6c';
      for (const s of [-1, 1]) {
        ctx.beginPath();
        const x0 = cx + s * (w / 2 + 0.05);
        ctx.moveTo(x0 - s * 0.3, top - 0.18);
        ctx.lineTo(x0 + s * 0.22, top - 0.18);
        ctx.quadraticCurveTo(x0 + s * 0.12, top + h * 0.6, x0 + s * 0.26, top + h + 0.3);
        ctx.lineTo(x0 - s * 0.08, top + h + 0.3);
        ctx.quadraticCurveTo(x0 - s * 0.22, top + 0.4, x0 - s * 0.3, top - 0.18);
        ctx.fill();
      }
      ctx.fillStyle = '#c96a4f';
      roundRect(ctx, cx - w / 2 - 0.45, top - 0.24, w + 0.9, 0.07, 0.03); ctx.fill();
    }

    drawFrame(ctx, x, y) {
      ctx.fillStyle = '#9c6b45';
      roundRect(ctx, x - 0.42, y - 0.32, 0.84, 0.64, 0.04); ctx.fill();
      ctx.fillStyle = '#fbf3e3';
      ctx.fillRect(x - 0.34, y - 0.24, 0.68, 0.48);
      ctx.fillStyle = '#e9a25e';
      this.paw(ctx, x, y + 0.06, 0.1);
      ctx.fillStyle = '#f3c9a0';
      ctx.beginPath(); ctx.arc(x - 0.2, y - 0.12, 0.05, 0, Math.PI * 2); ctx.fill();
    }

    drawClock(ctx, x, y) {
      const d = new Date();
      ctx.fillStyle = '#fffaf1';
      ctx.strokeStyle = '#a47450';
      ctx.lineWidth = 0.04;
      ctx.beginPath(); ctx.arc(x, y, 0.24, 0, Math.PI * 2); ctx.fill(); ctx.stroke();
      ctx.strokeStyle = OUT;
      ctx.lineCap = 'round';
      const hA = ((d.getHours() % 12) + d.getMinutes() / 60) / 12 * Math.PI * 2;
      const mA = d.getMinutes() / 60 * Math.PI * 2;
      ctx.lineWidth = 0.03;
      ctx.beginPath(); ctx.moveTo(x, y); ctx.lineTo(x + Math.sin(hA) * 0.12, y - Math.cos(hA) * 0.12); ctx.stroke();
      ctx.lineWidth = 0.02;
      ctx.beginPath(); ctx.moveTo(x, y); ctx.lineTo(x + Math.sin(mA) * 0.18, y - Math.cos(mA) * 0.18); ctx.stroke();
    }

    drawShelf(ctx, x, y) {
      ctx.fillStyle = '#b07a4c';
      roundRect(ctx, x - 0.5, y, 1.0, 0.06, 0.02); ctx.fill();
      ctx.fillStyle = '#5f9ea0'; roundRect(ctx, x - 0.4, y - 0.26, 0.12, 0.26, 0.02); ctx.fill();
      ctx.fillStyle = '#e07a5f'; roundRect(ctx, x - 0.26, y - 0.22, 0.1, 0.22, 0.02); ctx.fill();
      ctx.fillStyle = '#f2cc8f'; roundRect(ctx, x - 0.14, y - 0.3, 0.12, 0.3, 0.02); ctx.fill();
      ctx.fillStyle = '#fff4dc';
      ctx.beginPath(); ctx.arc(x + 0.25, y - 0.1, 0.1, Math.PI, 0); ctx.fill();
    }

    drawDoor(ctx, x) {
      const w = 0.95, h = 2.25;
      ctx.fillStyle = '#b98454';
      roundRect(ctx, x - w / 2 - 0.06, -h - 0.06, w + 0.12, h + 0.06, 0.04); ctx.fill();
      const g = ctx.createLinearGradient(x - w / 2, 0, x + w / 2, 0);
      g.addColorStop(0, '#d9a373'); g.addColorStop(1, '#c38d5e');
      ctx.fillStyle = g;
      ctx.fillRect(x - w / 2, -h, w, h);
      ctx.strokeStyle = 'rgba(90,50,20,0.25)';
      ctx.lineWidth = 0.025;
      ctx.strokeRect(x - w / 2 + 0.12, -h + 0.15, w - 0.24, 0.85);
      ctx.strokeRect(x - w / 2 + 0.12, -h + 1.15, w - 0.24, 0.9);
      ctx.fillStyle = '#ffd26b';
      ctx.beginPath(); ctx.arc(x - w / 2 + 0.14, -1.05, 0.05, 0, Math.PI * 2); ctx.fill();
      // smycz na haczyku
      const hx = x - w / 2 - 0.28;
      ctx.fillStyle = '#8a8f99';
      ctx.fillRect(hx - 0.03, -1.62, 0.06, 0.04);
      ctx.strokeStyle = '#e0453a';
      ctx.lineWidth = 0.04;
      ctx.beginPath(); ctx.moveTo(hx, -1.6); ctx.quadraticCurveTo(hx - 0.12, -1.1, hx, -0.82); ctx.quadraticCurveTo(hx + 0.1, -1.1, hx, -1.6); ctx.stroke();
      ctx.fillStyle = '#c0c4cc';
      ctx.beginPath(); ctx.arc(hx, -0.8, 0.04, 0, Math.PI * 2); ctx.fill();
      // wycieraczka
      ctx.fillStyle = '#8c6239';
      roundRect(ctx, x - 0.55, 0.05, 1.1, 0.12, 0.04); ctx.fill();
    }

    drawPlant(ctx, x) {
      ctx.fillStyle = '#5c9a64';
      for (let i = 0; i < 7; i++) {
        const a = -Math.PI / 2 + (i - 3) * 0.32;
        ctx.save(); ctx.translate(x, -0.55); ctx.rotate(a + Math.PI / 2);
        ctx.beginPath(); ctx.ellipse(0, -0.35, 0.08, 0.35, 0, 0, Math.PI * 2); ctx.fill();
        ctx.restore();
      }
      ctx.fillStyle = '#d9774f';
      ctx.beginPath(); ctx.moveTo(x - 0.25, -0.6); ctx.lineTo(x + 0.25, -0.6); ctx.lineTo(x + 0.19, 0.02); ctx.lineTo(x - 0.19, 0.02); ctx.closePath(); ctx.fill();
      ctx.fillStyle = 'rgba(255,255,255,0.2)'; ctx.fillRect(x - 0.25, -0.6, 0.5, 0.06);
    }

    drawBed(ctx, x) {
      const w = 1.9;
      ctx.fillStyle = 'rgba(60,30,10,0.18)';
      ctx.beginPath(); ctx.ellipse(x, 0.08, w / 2 + 0.05, 0.1, 0, 0, Math.PI * 2); ctx.fill();
      ctx.fillStyle = '#5b8fb9';
      ctx.beginPath(); ctx.ellipse(x, -0.12, w / 2, 0.24, 0, 0, Math.PI * 2); ctx.fill();
      ctx.fillStyle = '#7fb0d6';
      ctx.beginPath(); ctx.ellipse(x, -0.17, w / 2 - 0.14, 0.13, 0, 0, Math.PI * 2); ctx.fill();
      ctx.strokeStyle = 'rgba(255,255,255,0.35)';
      ctx.lineWidth = 0.025;
      ctx.beginPath(); ctx.ellipse(x, -0.2, w / 2 - 0.05, 0.2, 0, Math.PI * 1.1, Math.PI * 1.9); ctx.stroke();
    }

    drawBasket(ctx, x) {
      ctx.fillStyle = '#c99a5b';
      ctx.beginPath(); ctx.moveTo(x - 0.32, -0.42); ctx.lineTo(x + 0.32, -0.42); ctx.lineTo(x + 0.26, 0.02); ctx.lineTo(x - 0.26, 0.02); ctx.closePath(); ctx.fill();
      ctx.strokeStyle = 'rgba(110,70,30,0.45)'; ctx.lineWidth = 0.02;
      for (let i = 1; i < 4; i++) { ctx.beginPath(); ctx.moveTo(x - 0.31 + i * 0.01, -0.42 + i * 0.11); ctx.lineTo(x + 0.31 - i * 0.01, -0.42 + i * 0.11); ctx.stroke(); }
      // sznur do przeciągania wystający z kosza
      ctx.strokeStyle = '#e46f6f'; ctx.lineWidth = 0.06; ctx.lineCap = 'round';
      ctx.beginPath(); ctx.moveTo(x - 0.1, -0.42); ctx.quadraticCurveTo(x - 0.05, -0.62, x + 0.15, -0.58); ctx.stroke();
      ctx.strokeStyle = '#fff0e0';
      ctx.beginPath(); ctx.moveTo(x + 0.15, -0.58); ctx.lineTo(x + 0.22, -0.48); ctx.stroke();
      ctx.fillStyle = '#b88a4f';
      roundRect(ctx, x - 0.36, -0.46, 0.72, 0.07, 0.03); ctx.fill();
    }

    drawBowl(ctx, x, fill, kind) {
      const w = 0.5, h = 0.17;
      ctx.fillStyle = 'rgba(60,30,10,0.18)';
      ctx.beginPath(); ctx.ellipse(x, 0.04, w / 2 + 0.06, 0.05, 0, 0, Math.PI * 2); ctx.fill();
      if (fill > 0.01) {
        if (kind === 'food') {
          ctx.fillStyle = '#9a5a2c';
          const mh = 0.04 + 0.1 * fill;
          ctx.beginPath(); ctx.ellipse(x, -h + 0.02, w / 2 - 0.05, mh, 0, Math.PI, 0); ctx.fill();
          ctx.fillStyle = '#b8743f';
          for (let i = 0; i < 9 * fill; i++) { ctx.beginPath(); ctx.arc(x - 0.16 + hash(i) * 0.32, -h - hash(i + 4) * mh * 0.8 + 0.01, 0.025, 0, Math.PI * 2); ctx.fill(); }
        } else {
          ctx.fillStyle = 'rgba(90,170,240,0.9)';
          ctx.beginPath(); ctx.ellipse(x, -h + 0.02 + (1 - fill) * 0.06, w / 2 - 0.05, 0.035, 0, 0, Math.PI * 2); ctx.fill();
          ctx.fillStyle = 'rgba(255,255,255,0.6)';
          ctx.fillRect(x - 0.08, -h + 0.015 + (1 - fill) * 0.06, 0.07, 0.012);
        }
      }
      const g = ctx.createLinearGradient(x - w / 2, 0, x + w / 2, 0);
      const c = kind === 'food' ? ['#ef6f5a', '#d4503d'] : ['#5aa0e8', '#3b7fd0'];
      g.addColorStop(0, c[0]); g.addColorStop(1, c[1]);
      ctx.fillStyle = g;
      ctx.beginPath();
      ctx.moveTo(x - w / 2, -h);
      ctx.lineTo(x + w / 2, -h);
      ctx.lineTo(x + w / 2 - 0.06, 0);
      ctx.lineTo(x - w / 2 + 0.06, 0);
      ctx.closePath();
      ctx.fill();
      ctx.fillStyle = 'rgba(255,255,255,0.35)';
      roundRect(ctx, x - w / 2, -h - 0.015, w, 0.04, 0.02); ctx.fill();
      ctx.fillStyle = 'rgba(255,255,255,0.85)';
      if (kind === 'food') this.paw(ctx, x, -0.07, 0.025);
      else { ctx.beginPath(); ctx.moveTo(x, -0.12); ctx.quadraticCurveTo(x + 0.04, -0.06, x, -0.04); ctx.quadraticCurveTo(x - 0.04, -0.06, x, -0.12); ctx.fill(); }
    }

    /* ---------------- park ---------------- */
    drawPark(ctx, game) {
      const b = this.worldBounds();
      const hour = new Date().getHours() + new Date().getMinutes() / 60;
      const sky = skyColors(hour);
      const g = ctx.createLinearGradient(0, b.top, 0, 0);
      g.addColorStop(0, sky.top);
      g.addColorStop(1, sky.bot);
      ctx.fillStyle = g;
      ctx.fillRect(b.left, b.top, b.right - b.left, -b.top + 0.01);
      // warstwy paralaksy
      const layer = (factor, color, amp, base, freq, seedOff) => {
        ctx.save();
        const shift = this.camX * (1 - factor);
        ctx.translate(shift, 0);
        ctx.fillStyle = color;
        ctx.beginPath();
        const l = b.left - shift, r = b.right - shift;
        ctx.moveTo(l, 0);
        for (let x = l; x <= r + 0.2; x += 0.2) ctx.lineTo(x, base - amp * (0.5 + 0.5 * Math.sin(x * freq + seedOff) * Math.cos(x * freq * 0.37 + seedOff)));
        ctx.lineTo(r + 0.2, 0);
        ctx.closePath();
        ctx.fill();
        ctx.restore();
      };
      layer(0.15, sky.night ? '#232c52' : '#a9cfe0', 0.8, -1.2, 0.5, 1);
      layer(0.35, sky.night ? '#1f3a3a' : '#8cc08a', 0.6, -0.55, 0.8, 4);
      // drzewa w tle
      ctx.save();
      const f = 0.6, shift = this.camX * (1 - f);
      ctx.translate(shift, 0);
      for (let i = Math.floor((b.left - shift) / 1.3) - 1; i < (b.right - shift) / 1.3 + 1; i++) {
        const x = i * 1.3 + hash(i) * 0.6;
        const s = 0.5 + hash(i + 9) * 0.35;
        ctx.fillStyle = sky.night ? '#1d3b2c' : '#6aa86d';
        ctx.beginPath(); ctx.arc(x, -0.5 - s, s * 0.55, 0, Math.PI * 2); ctx.fill();
        ctx.fillStyle = sky.night ? '#2a2018' : '#8a6040';
        ctx.fillRect(x - 0.04, -0.5 - s * 0.5, 0.08, s * 0.5 + 0.5);
      }
      ctx.restore();
      // trawa i ścieżka
      const fd = this.floorDepth / this.scale + 0.2;
      ctx.fillStyle = sky.night ? '#2d5a3a' : '#7dbb63';
      ctx.fillRect(b.left, -0.18, b.right - b.left, fd + 0.2);
      ctx.fillStyle = sky.night ? '#5b4a3a' : '#e3c08d';
      ctx.fillRect(b.left, -0.05, b.right - b.left, 0.3);
      ctx.fillStyle = sky.night ? '#4b3c2e' : '#d1aa75';
      for (let i = Math.floor(b.left / 0.5); i < b.right / 0.5; i++) { ctx.beginPath(); ctx.ellipse(i * 0.5 + hash(i) * 0.3, 0.06 + hash(i + 1) * 0.12, 0.05, 0.015, 0, 0, Math.PI * 2); ctx.fill(); }
      ctx.strokeStyle = sky.night ? '#3e7a4d' : '#5fa34a';
      ctx.lineWidth = 0.02;
      for (let i = Math.floor(b.left / 0.12); i < b.right / 0.12; i++) {
        const x = i * 0.12 + hash(i) * 0.06;
        ctx.beginPath(); ctx.moveTo(x, -0.05); ctx.lineTo(x + 0.02, -0.12 - hash(i + 2) * 0.06); ctx.stroke();
        ctx.beginPath(); ctx.moveTo(x, 0.3); ctx.lineTo(x - 0.02, 0.36 + hash(i + 5) * 0.08); ctx.stroke();
      }
      // dom na starcie i furtka na końcu
      this.drawHouse(ctx, -1.6);
      if (game.park) {
        this.drawGate(ctx, game.park.length + 0.6);
        for (const o of game.park.objects) if (o.x > b.left - 2 && o.x < b.right + 2) this.drawParkObject(ctx, o, game);
      }
    }

    drawHouse(ctx, x) {
      ctx.fillStyle = '#f3dcc0';
      ctx.fillRect(x - 1.2, -2.2, 2.4, 2.2);
      ctx.fillStyle = '#c8553d';
      ctx.beginPath(); ctx.moveTo(x - 1.45, -2.15); ctx.lineTo(x, -3.2); ctx.lineTo(x + 1.45, -2.15); ctx.closePath(); ctx.fill();
      ctx.fillStyle = '#b98454';
      ctx.fillRect(x + 0.35, -1.5, 0.6, 1.5);
      ctx.fillStyle = '#9fd1f0';
      ctx.fillRect(x - 0.9, -1.6, 0.7, 0.6);
    }

    drawGate(ctx, x) {
      ctx.fillStyle = '#fffaf1';
      for (let i = -3; i <= 3; i++) { roundRect(ctx, x + i * 0.18 - 0.04, -0.9, 0.08, 0.9, 0.03); ctx.fill(); }
      ctx.fillRect(x - 0.6, -0.7, 1.2, 0.06);
      ctx.fillRect(x - 0.6, -0.3, 1.2, 0.06);
      this.textAt(ctx, 'Do domu', x, -1.0, 0.16, '#3a2418', '#fffaf1');
    }

    /** Tekst rysowany w przestrzeni ekranu (ostry w każdej przeglądarce), pozycja w świecie. */
    textAt(ctx, text, x, y, size, fill, stroke) {
      const m = ctx.getTransform();
      const sx = m.a * x + m.c * y + m.e;
      const sy = m.b * x + m.d * y + m.f;
      const px = size * m.a;
      ctx.save();
      ctx.setTransform(1, 0, 0, 1, 0, 0);
      ctx.font = '800 ' + px.toFixed(1) + 'px Nunito, system-ui, sans-serif';
      ctx.textAlign = 'center';
      ctx.lineJoin = 'round';
      if (stroke) { ctx.lineWidth = Math.max(2, px * 0.22); ctx.strokeStyle = stroke; ctx.strokeText(text, sx, sy); }
      ctx.fillStyle = fill;
      ctx.fillText(text, sx, sy);
      ctx.restore();
    }

    drawParkObject(ctx, o, game) {
      const x = o.x;
      switch (o.type) {
        case 'tree': {
          const s = 1 + o.seed * 0.3;
          ctx.fillStyle = '#8a5a36';
          roundRect(ctx, x - 0.11 * s, -1.7 * s, 0.22 * s, 1.7 * s, 0.05); ctx.fill();
          ctx.fillStyle = '#4f9a57';
          [[0, -2.2, 0.75], [-0.45, -1.75, 0.5], [0.45, -1.8, 0.52]].forEach(([dx, dy, r]) => { ctx.beginPath(); ctx.arc(x + dx * s, dy * s, r * s, 0, Math.PI * 2); ctx.fill(); });
          ctx.fillStyle = 'rgba(255,255,255,0.12)';
          ctx.beginPath(); ctx.arc(x - 0.2 * s, -2.4 * s, 0.3 * s, 0, Math.PI * 2); ctx.fill();
          break;
        }
        case 'bush':
          ctx.fillStyle = '#3f8a4a';
          [[-0.3, -0.25, 0.3], [0, -0.38, 0.36], [0.32, -0.24, 0.28]].forEach(([dx, dy, r]) => { ctx.beginPath(); ctx.arc(x + dx, dy, r, 0, Math.PI * 2); ctx.fill(); });
          ctx.fillStyle = '#e65a6a';
          [[-0.2, -0.38], [0.15, -0.5], [0.35, -0.3]].forEach(([dx, dy]) => { ctx.beginPath(); ctx.arc(x + dx, dy, 0.04, 0, Math.PI * 2); ctx.fill(); });
          break;
        case 'hydrant':
          ctx.fillStyle = '#e0453a';
          roundRect(ctx, x - 0.12, -0.55, 0.24, 0.55, 0.05); ctx.fill();
          ctx.beginPath(); ctx.arc(x, -0.55, 0.13, Math.PI, 0); ctx.fill();
          ctx.fillRect(x - 0.2, -0.38, 0.4, 0.08);
          ctx.fillStyle = 'rgba(255,255,255,0.3)'; ctx.fillRect(x - 0.08, -0.5, 0.04, 0.45);
          break;
        case 'stick':
          if (!o.done) { ctx.save(); ctx.translate(x, -0.03); ctx.scale(1 / 288, 1 / 288); P.ItemArt.drawStick(ctx, {}, 288); ctx.restore(); }
          break;
        case 'puddle':
          ctx.fillStyle = 'rgba(80,120,170,0.75)';
          ctx.beginPath(); ctx.ellipse(x + 0.5, 0.1, 0.55, 0.08, 0, 0, Math.PI * 2); ctx.fill();
          ctx.fillStyle = 'rgba(255,255,255,0.4)';
          ctx.beginPath(); ctx.ellipse(x + 0.35, 0.09, 0.15, 0.02, 0, 0, Math.PI * 2); ctx.fill();
          break;
        case 'digspot':
          ctx.fillStyle = o.done ? '#6b4423' : '#a0703f';
          ctx.beginPath(); ctx.ellipse(x + 0.2, 0.02, 0.3, o.done ? 0.06 : 0.1, 0, Math.PI, 0); ctx.fill();
          if (o.done) { ctx.fillStyle = '#3d2614'; ctx.beginPath(); ctx.ellipse(x + 0.2, 0.06, 0.18, 0.04, 0, 0, Math.PI * 2); ctx.fill(); }
          break;
        case 'bird': {
          let bx = x + 0.6, by = -0.08;
          if (o.flown) { const t = game.time - o.flown; bx += t * 1.6; by -= t * 1.4; if (t > 4) break; }
          ctx.fillStyle = '#5b7fb0';
          ctx.beginPath(); ctx.ellipse(bx, by - 0.08, 0.09, 0.07, 0, 0, Math.PI * 2); ctx.fill();
          ctx.beginPath(); ctx.arc(bx + 0.07, by - 0.15, 0.05, 0, Math.PI * 2); ctx.fill();
          ctx.fillStyle = '#ffb347';
          ctx.beginPath(); ctx.moveTo(bx + 0.12, by - 0.15); ctx.lineTo(bx + 0.17, by - 0.14); ctx.lineTo(bx + 0.12, by - 0.12); ctx.fill();
          if (o.flown) { ctx.fillStyle = '#5b7fb0'; ctx.beginPath(); ctx.ellipse(bx - 0.02, by - 0.14 + Math.sin(game.time * 20) * 0.03, 0.08, 0.025, -0.5, 0, Math.PI * 2); ctx.fill(); }
          break;
        }
        default: break;
      }
    }

    /** Piłka leżąca/lecąca w pokoju (gdy pies jej nie trzyma). */
    drawBall(ctx, game, body) {
      const b = game.ball;
      if (game.scene !== 'home' || b.state === 'held') return;
      const r = 0.085 * body.height;
      ctx.fillStyle = 'rgba(40,24,10,0.2)';
      ctx.beginPath(); ctx.ellipse(b.x, 0.02, r * U.clamp(1 + b.y * 0.3, 0.4, 1), r * 0.3, 0, 0, Math.PI * 2); ctx.fill();
      ctx.save();
      ctx.translate(b.x, b.y - r);
      ctx.rotate(b.x / r);
      const k = 1 / 288;
      ctx.scale(k * body.height, k * body.height);
      P.ItemArt.drawBall(ctx, {}, 288);
      ctx.restore();
    }

    /** Smycz w parku: od obroży psa do ręki opiekuna poza kadrem. */
    drawLeash(ctx, from) {
      if (!from) return;
      const hand = [this.camX + this.viewW * 0.42, -this.groundY / this.scale - 0.2];
      ctx.strokeStyle = '#e0453a';
      ctx.lineWidth = 0.035;
      ctx.lineCap = 'round';
      ctx.beginPath();
      ctx.moveTo(from[0], from[1]);
      const mx = (from[0] + hand[0]) / 2, my = Math.max(from[1], hand[1]) + 0.35;
      ctx.quadraticCurveTo(mx, my, hand[0], hand[1]);
      ctx.stroke();
    }

    drawLighting(ctx, game) {
      const night = skyColors(new Date().getHours() + new Date().getMinutes() / 60).night;
      let a = 0;
      if (game.scene === 'home' && !game.state.world.lights) a = 0.5;
      else if (night) a = game.scene === 'park' ? 0.32 : 0.12;
      if (a <= 0) return;
      ctx.setTransform(1, 0, 0, 1, 0, 0);
      const W = this.w * this.dpr, H = this.h * this.dpr;
      const g = ctx.createRadialGradient(W * 0.5, H * 0.75, H * 0.1, W * 0.5, H * 0.6, Math.max(W, H) * 0.8);
      g.addColorStop(0, 'rgba(15,20,55,' + (a * 0.6).toFixed(3) + ')');
      g.addColorStop(1, 'rgba(10,12,40,' + a.toFixed(3) + ')');
      ctx.fillStyle = g;
      ctx.fillRect(0, 0, W, H);
    }

    /* ---------------- cząsteczki ---------------- */
    spawn(type, at) {
      const [x, y] = at;
      const add = (o) => this.particles.push(Object.assign({ x, y, vx: 0, vy: 0, life: 0, max: 1.2, size: 0.1, rot: 0 }, o));
      switch (type) {
        case 'hearts': for (let i = 0; i < 2; i++) add({ type, x: x + U.rand(-0.15, 0.15), y: y - 0.1, vx: U.rand(-0.15, 0.15), vy: -U.rand(0.5, 0.8), max: 1.4, size: U.rand(0.08, 0.12) }); break;
        case 'zzz': add({ type, text: 'z', x: x + 0.1, y: y - 0.1, vx: 0.25, vy: -0.35, max: 2.2, size: 0.16 }); break;
        case 'bubbles': for (let i = 0; i < 3; i++) add({ type, x: x + U.rand(-0.5, 0.5), y: y + U.rand(-0.3, 0.1), vx: U.rand(-0.1, 0.1), vy: -U.rand(0.2, 0.5), max: 1.6, size: U.rand(0.04, 0.1) }); break;
        case 'drops': for (let i = 0; i < 4; i++) { const a = U.rand(-Math.PI, 0); add({ type, x: x + U.rand(-0.3, 0.3), y, vx: Math.cos(a) * U.rand(1, 2), vy: Math.sin(a) * U.rand(1, 2), max: 0.9, size: 0.04, gravity: 6 }); } break;
        case 'dirt': for (let i = 0; i < 3; i++) add({ type, x, y: y - 0.05, vx: U.rand(-1.6, -0.6), vy: -U.rand(0.8, 1.6), max: 0.8, size: U.rand(0.03, 0.05), gravity: 7 }); break;
        case 'splash': for (let i = 0; i < 8; i++) { const a = U.rand(-Math.PI * 0.95, -Math.PI * 0.05); add({ type: 'drops', x, y: y - 0.02, vx: Math.cos(a) * U.rand(0.8, 2), vy: Math.sin(a) * U.rand(1, 2.4), max: 0.8, size: 0.045, gravity: 7 }); } break;
        case 'bark': add({ type, text: U.pick(['Hau!', 'Hau hau!', 'Wrrr… Hau!']), x: x + 0.15, y: y - 0.2, vx: 0.15, vy: -0.4, max: 1.1, size: 0.2 }); break;
        case 'question': add({ type: 'text', text: '?', x, y: y - 0.2, vy: -0.3, max: 1.2, size: 0.32, color: '#3b7be8' }); break;
        case 'exclaim': add({ type: 'text', text: '!', x, y: y - 0.2, vy: -0.3, max: 1.0, size: 0.34, color: '#e0453a' }); break;
        case 'star': for (let i = 0; i < 4; i++) add({ type, x: x + U.rand(-0.3, 0.3), y: y + U.rand(-0.2, 0.1), vx: U.rand(-0.3, 0.3), vy: -U.rand(0.3, 0.7), max: 1.1, size: U.rand(0.06, 0.1), rot: U.rand(0, 3) }); break;
        default: break;
      }
    }

    updateParticles(dt) {
      for (const p of this.particles) {
        p.life += dt;
        p.x += p.vx * dt;
        p.y += p.vy * dt;
        if (p.gravity) p.vy += p.gravity * dt;
        if (p.type === 'zzz') p.vx = 0.25 + Math.sin(p.life * 3) * 0.15;
      }
      this.particles = this.particles.filter((p) => p.life < p.max && p.y < 0.4);
    }

    drawParticles(ctx) {
      const m = this.matrix();
      ctx.setTransform(m[0], m[1], m[2], m[3], m[4], m[5]);
      for (const p of this.particles) {
        const a = U.clamp(1 - p.life / p.max, 0, 1);
        const fadeIn = U.clamp(p.life / 0.15, 0, 1);
        ctx.globalAlpha = a * fadeIn;
        switch (p.type) {
          case 'hearts': this.heart(ctx, p.x, p.y, p.size * (0.8 + fadeIn * 0.2)); break;
          case 'zzz':
            this.textAt(ctx, 'Z', p.x, p.y, p.size * (1 + p.life * 0.4), '#5a6fd6', 'rgba(255,255,255,0.8)');
            break;
          case 'bubbles':
            ctx.fillStyle = 'rgba(255,255,255,0.75)';
            ctx.strokeStyle = 'rgba(140,190,240,0.9)';
            ctx.lineWidth = 0.012;
            ctx.beginPath(); ctx.arc(p.x, p.y, p.size, 0, Math.PI * 2); ctx.fill(); ctx.stroke();
            ctx.fillStyle = '#fff'; ctx.beginPath(); ctx.arc(p.x - p.size * 0.35, p.y - p.size * 0.35, p.size * 0.25, 0, Math.PI * 2); ctx.fill();
            break;
          case 'drops':
            ctx.fillStyle = '#62a8f0';
            ctx.beginPath(); ctx.arc(p.x, p.y, p.size, 0, Math.PI * 2); ctx.fill();
            break;
          case 'dirt':
            ctx.fillStyle = '#7a5230';
            ctx.fillRect(p.x - p.size / 2, p.y - p.size / 2, p.size, p.size);
            break;
          case 'bark':
          case 'text':
            this.textAt(ctx, p.text, p.x, p.y, p.size, p.color || OUT, '#fff');
            break;
          case 'star': this.star(ctx, p.x, p.y, p.size, p.rot + p.life * 3); break;
          default: break;
        }
      }
      ctx.globalAlpha = 1;
    }

    heart(ctx, x, y, s) {
      ctx.fillStyle = '#ff5c8a';
      ctx.beginPath();
      ctx.moveTo(x, y + s * 0.35);
      ctx.bezierCurveTo(x - s * 1.1, y - s * 0.3, x - s * 0.5, y - s * 1.05, x, y - s * 0.45);
      ctx.bezierCurveTo(x + s * 0.5, y - s * 1.05, x + s * 1.1, y - s * 0.3, x, y + s * 0.35);
      ctx.fill();
      ctx.fillStyle = 'rgba(255,255,255,0.55)';
      ctx.beginPath(); ctx.arc(x - s * 0.38, y - s * 0.45, s * 0.14, 0, Math.PI * 2); ctx.fill();
    }

    star(ctx, x, y, s, rot) {
      ctx.fillStyle = '#ffcf3f';
      ctx.beginPath();
      for (let i = 0; i < 10; i++) {
        const r = i % 2 ? s * 0.45 : s;
        const a = rot + (i * Math.PI) / 5;
        ctx.lineTo(x + Math.cos(a) * r, y + Math.sin(a) * r);
      }
      ctx.closePath();
      ctx.fill();
    }
  }

  P.Scene = Scene;
})(window.Pies = window.Pies || {});
