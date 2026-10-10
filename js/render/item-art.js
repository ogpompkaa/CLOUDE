/*
 * GRAFIKI PRZEDMIOTÓW NA PSIE (obroże, akcesoria na głowę, rzeczy noszone w pysku).
 *
 * Przedmioty są niezależne od skórki: rysują się w układzie punktu zaczepienia (anchor)
 * zdefiniowanego w skórce. Rozmiar wynika z `anchor.width` (grubość szyi / szerokość głowy
 * w pikselach grafiki) albo z wysokości w kłębie – nigdy z wymiarów konkretnego obrazka.
 * Dzięki temu obroża kupiona dla psa tymczasowego pasuje też do prawdziwego psa.
 */
(function (P) {
  'use strict';

  function roundRect(ctx, x, y, w, h, r) {
    ctx.beginPath();
    ctx.moveTo(x + r, y);
    ctx.arcTo(x + w, y, x + w, y + h, r);
    ctx.arcTo(x + w, y + h, x, y + h, r);
    ctx.arcTo(x, y + h, x, y, r);
    ctx.arcTo(x, y, x + w, y, r);
    ctx.closePath();
  }

  const OUTLINE = '#3a2418';

  function collar(ctx, a, color, extra) {
    const w = a.width || 80;
    const t = Math.max(10, w * 0.2);
    ctx.lineJoin = 'round';
    ctx.lineWidth = Math.max(3, w * 0.045);
    ctx.strokeStyle = OUTLINE;
    roundRect(ctx, -w / 2, -t / 2, w, t, t / 2);
    ctx.fillStyle = color;
    ctx.fill();
    ctx.stroke();
    ctx.fillStyle = 'rgba(255,255,255,0.35)';
    roundRect(ctx, -w / 2 + t * 0.4, -t / 2 + t * 0.18, w - t * 0.8, t * 0.22, t * 0.11);
    ctx.fill();
    // przywieszka / dzwoneczek po stronie gardła (+x)
    const tx = w / 2 - t * 0.6;
    if (extra === 'bell') {
      ctx.beginPath();
      ctx.arc(tx, t * 0.95, t * 0.62, 0, Math.PI * 2);
      ctx.fillStyle = '#ffd34d';
      ctx.fill();
      ctx.stroke();
      ctx.beginPath();
      ctx.arc(tx - t * 0.2, t * 0.75, t * 0.18, 0, Math.PI * 2);
      ctx.fillStyle = 'rgba(255,255,255,0.7)';
      ctx.fill();
    } else {
      ctx.beginPath();
      ctx.arc(tx, t * 0.95, t * 0.5, 0, Math.PI * 2);
      ctx.fillStyle = extra === 'gold' ? '#ffcf4a' : '#d9dde6';
      ctx.fill();
      ctx.stroke();
    }
  }

  function bandana(ctx, a) {
    const w = a.width || 80;
    ctx.lineJoin = 'round';
    ctx.lineWidth = Math.max(3, w * 0.045);
    ctx.strokeStyle = OUTLINE;
    ctx.beginPath();
    ctx.moveTo(-w / 2, -w * 0.08);
    ctx.lineTo(w / 2 + w * 0.05, -w * 0.1);
    ctx.lineTo(w * 0.32, w * 0.62);
    ctx.closePath();
    ctx.fillStyle = '#e2453c';
    ctx.fill();
    ctx.save();
    ctx.clip();
    ctx.strokeStyle = 'rgba(255,255,255,0.45)';
    ctx.lineWidth = w * 0.05;
    for (let i = -3; i < 6; i++) {
      ctx.beginPath();
      ctx.moveTo(-w + i * w * 0.22, -w);
      ctx.lineTo(i * w * 0.22, w);
      ctx.stroke();
    }
    ctx.restore();
    ctx.strokeStyle = OUTLINE;
    ctx.lineWidth = Math.max(3, w * 0.045);
    ctx.stroke();
  }

  function bowtie(ctx, a) {
    const w = (a.width || 80) * 0.7;
    ctx.lineJoin = 'round';
    ctx.lineWidth = Math.max(3, w * 0.06);
    ctx.strokeStyle = OUTLINE;
    ctx.save();
    ctx.translate((a.width || 80) * 0.35, w * 0.15);
    ctx.beginPath();
    ctx.moveTo(0, 0);
    ctx.lineTo(-w * 0.45, -w * 0.3);
    ctx.lineTo(-w * 0.45, w * 0.3);
    ctx.closePath();
    ctx.moveTo(0, 0);
    ctx.lineTo(w * 0.45, -w * 0.3);
    ctx.lineTo(w * 0.45, w * 0.3);
    ctx.closePath();
    ctx.fillStyle = '#2d6cdf';
    ctx.fill();
    ctx.stroke();
    ctx.beginPath();
    ctx.arc(0, 0, w * 0.12, 0, Math.PI * 2);
    ctx.fillStyle = '#5b93f5';
    ctx.fill();
    ctx.stroke();
    ctx.restore();
  }

  function bow(ctx, a) {
    const w = (a.width || 120) * 0.42;
    ctx.lineJoin = 'round';
    ctx.lineWidth = Math.max(3, w * 0.08);
    ctx.strokeStyle = OUTLINE;
    ctx.fillStyle = '#ff6fa8';
    for (const s of [-1, 1]) {
      ctx.beginPath();
      ctx.ellipse(s * w * 0.42, -w * 0.25, w * 0.42, w * 0.28, s * -0.5, 0, Math.PI * 2);
      ctx.fill();
      ctx.stroke();
    }
    ctx.beginPath();
    ctx.arc(0, -w * 0.25, w * 0.17, 0, Math.PI * 2);
    ctx.fillStyle = '#ff9cc4';
    ctx.fill();
    ctx.stroke();
  }

  function cap(ctx, a) {
    const w = (a.width || 120) * 0.62;
    ctx.lineJoin = 'round';
    ctx.lineWidth = Math.max(3, w * 0.06);
    ctx.strokeStyle = OUTLINE;
    ctx.beginPath();
    ctx.moveTo(-w * 0.5, 0);
    ctx.quadraticCurveTo(-w * 0.45, -w * 0.62, w * 0.05, -w * 0.62);
    ctx.quadraticCurveTo(w * 0.5, -w * 0.6, w * 0.5, 0);
    ctx.closePath();
    ctx.fillStyle = '#3b7be8';
    ctx.fill();
    ctx.stroke();
    ctx.beginPath();
    ctx.moveTo(w * 0.35, -w * 0.02);
    ctx.quadraticCurveTo(w * 0.85, -w * 0.06, w * 0.95, w * 0.08);
    ctx.lineTo(w * 0.35, w * 0.08);
    ctx.closePath();
    ctx.fillStyle = '#2a5fc0';
    ctx.fill();
    ctx.stroke();
    ctx.beginPath();
    ctx.arc(0, -w * 0.64, w * 0.08, 0, Math.PI * 2);
    ctx.fillStyle = '#ffd34d';
    ctx.fill();
    ctx.stroke();
  }

  function ball(ctx, a, shpx) {
    const r = shpx * 0.085;
    ctx.lineWidth = Math.max(3, r * 0.16);
    ctx.strokeStyle = OUTLINE;
    ctx.beginPath();
    ctx.arc(0, 0, r, 0, Math.PI * 2);
    const g = ctx.createRadialGradient(-r * 0.3, -r * 0.35, r * 0.1, 0, 0, r);
    g.addColorStop(0, '#f2ff7a');
    g.addColorStop(1, '#b8d61f');
    ctx.fillStyle = g;
    ctx.fill();
    ctx.stroke();
    ctx.strokeStyle = 'rgba(255,255,255,0.85)';
    ctx.lineWidth = r * 0.14;
    ctx.beginPath();
    ctx.arc(-r * 0.9, 0, r * 0.8, -0.9, 0.9);
    ctx.stroke();
    ctx.beginPath();
    ctx.arc(r * 0.9, 0, r * 0.8, Math.PI - 0.9, Math.PI + 0.9);
    ctx.stroke();
  }

  function stick(ctx, a, shpx) {
    const l = shpx * 0.55;
    const t = shpx * 0.05;
    ctx.save();
    ctx.rotate(-0.12);
    ctx.lineJoin = 'round';
    ctx.lineWidth = Math.max(3, t * 0.35);
    ctx.strokeStyle = OUTLINE;
    roundRect(ctx, -l / 2, -t / 2, l, t, t / 2);
    ctx.fillStyle = '#9b6a3c';
    ctx.fill();
    ctx.stroke();
    ctx.beginPath();
    ctx.moveTo(l * 0.15, -t / 2);
    ctx.lineTo(l * 0.28, -t * 1.8);
    ctx.stroke();
    ctx.restore();
  }

  function bone(ctx, a, shpx) {
    const l = shpx * 0.34;
    const t = shpx * 0.06;
    ctx.lineJoin = 'round';
    ctx.lineWidth = Math.max(3, t * 0.3);
    ctx.strokeStyle = OUTLINE;
    ctx.fillStyle = '#fff4dc';
    ctx.beginPath();
    roundRect(ctx, -l / 2, -t / 2, l, t, t / 3);
    ctx.fill();
    ctx.stroke();
    for (const sx of [-1, 1]) for (const sy of [-1, 1]) {
      ctx.beginPath();
      ctx.arc(sx * l / 2, sy * t * 0.45, t * 0.55, 0, Math.PI * 2);
      ctx.fill();
      ctx.stroke();
    }
    ctx.beginPath();
    roundRect(ctx, -l / 2 + 2, -t / 2 + 2, l - 4, t - 4, t / 3);
    ctx.fill();
  }

  const ART = {
    'collar-red': (ctx, a) => collar(ctx, a, '#e0453a'),
    'collar-blue': (ctx, a) => collar(ctx, a, '#2f74e0'),
    'collar-green': (ctx, a) => collar(ctx, a, '#34b26b', 'bell'),
    'collar-gold': (ctx, a) => collar(ctx, a, '#2b2b35', 'gold'),
    'bandana': bandana,
    'bowtie': bowtie,
    'bow-pink': bow,
    'cap-blue': cap,
    'ball': ball,
    'stick': stick,
    'bone': bone,
  };

  P.ItemArt = {
    has: (id) => !!ART[id],
    draw(ctx, id, anchorDef, shpx) {
      const fn = ART[id];
      if (fn) fn(ctx, anchorDef || {}, shpx || 250);
    },
    drawBall: ball,
    drawStick: stick,
    drawBone: bone,
  };
})(window.Pies = window.Pies || {});
