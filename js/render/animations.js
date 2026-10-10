/*
 * BIBLIOTEKA ANIMACJI SZKIELETOWYCH (wspólna dla wszystkich skórek warstwowych)
 *
 * Animacje NIE znają grafik. Opisują ruch kanonicznych kości (patrz DogStates.bones):
 *   r  – obrót w stopniach względem pozy spoczynkowej części (dodatni = zgodnie z ruchem wskazówek zegara,
 *        dla psa patrzącego w prawo: łapa do tyłu, głowa w dół, ogon do przodu, ucho końcem do tyłu)
 *   x,y – przesunięcie w wysokościach w kłębie (więc działa przy każdej rozdzielczości grafik)
 *   sx,sy – skala
 * Pole root opisuje całego psa: r (obrót wokół środka tułowia), sx/sy, lift (wysokość nad ziemią).
 * Po złożeniu pozy renderer sam „stawia” psa na ziemi na podstawie punktów styku (contacts)
 * ze skórki – dzięki temu ta sama animacja pasuje do psa z krótkimi i długimi łapami.
 *
 * Skórka może zmienić siłę ruchu grup kości (motion.tail, motion.ears…) albo pojedynczej
 * części (parts.<id>.motion), a także nadpisać dowolny klip w polu `clips`.
 */
(function (P) {
  'use strict';

  const TAU = Math.PI * 2;
  const sin = (x) => Math.sin(x * TAU);
  const cos = (x) => Math.cos(x * TAU);
  const pos = (v) => (v > 0 ? v : 0);

  /** Cykl kończyny: [udo/ramię, podudzie z łapą]. offset = przesunięcie fazy. */
  function leg(phase, amp, bend, offset) {
    const a = phase + offset;
    return [-amp * sin(a), bend * pos(cos(a))];
  }

  function wag(t, speed, amp, center) { return (center || 0) + amp * sin(t * speed); }

  /* ---------- pozy bazowe używane przez kilka klipów ---------- */
  function standBones(t, breath) {
    const b = breath === undefined ? 1 : breath;
    return {
      body: { r: 0, sy: 1 + 0.012 * b * sin(t / 3.2), y: -0.004 * b * sin(t / 3.2) },
      neck: { r: 1.5 * sin(t / 3.2 + 0.2) },
      head: { r: -1 * sin(t / 3.2 + 0.4) },
    };
  }

  function sitBones(t) {
    const breathe = sin(t / 3.4);
    return {
      body: { r: -44, sy: 1 + 0.01 * breathe },
      neck: { r: 34 + 1.5 * breathe },
      head: { r: 9 },
      frontLegNear: { r: 44 }, frontPawNear: { r: 0 },
      frontLegFar: { r: 41 }, frontPawFar: { r: 2 },
      // udo poziomo do przodu, podudzie skrócone perspektywicznie (łapa pod kolanem)
      backLegNear: { r: -48 }, backPawNear: { r: 82, sy: 0.55 },
      backLegFar: { r: -44 }, backPawFar: { r: 80, sy: 0.55 },
      tail: { r: -50 },
    };
  }

  function lieBones(t) {
    const breathe = sin(t / 3.8);
    return {
      body: { r: 0, sy: 1 + 0.014 * breathe },
      neck: { r: -4 + 1.5 * breathe },
      head: { r: 2 },
      frontLegNear: { r: -74 }, frontPawNear: { r: -16 },
      frontLegFar: { r: -70 }, frontPawFar: { r: -18 },
      backLegNear: { r: -74 }, backPawNear: { r: 128 },
      backLegFar: { r: -68 }, backPawFar: { r: 120 },
      tail: { r: -78 },
    };
  }

  function merge(base, extra) {
    const out = {};
    for (const k of Object.keys(base)) out[k] = Object.assign({}, base[k]);
    for (const k of Object.keys(extra)) {
      out[k] = out[k] || {};
      for (const f of Object.keys(extra[k])) {
        const v = extra[k][f];
        if (f === 'r' || f === 'x' || f === 'y') out[k][f] = (out[k][f] || 0) + v;
        else out[k][f] = v;
      }
    }
    return out;
  }

  const CLIPS = {
    idle: {
      period: 3.2,
      sample({ t, mood }) {
        const bones = standBones(t);
        bones.tail = { r: wag(t, 1.1 + mood.happy * 1.4, 6 + mood.happy * 10) };
        bones.earNear = { r: 2 * sin(t / 5) };
        bones.earFar = { r: 2 * sin(t / 5 + 0.2) };
        bones.frontLegNear = { r: -1.5 * sin(t / 6) };
        bones.backLegNear = { r: 1.5 * sin(t / 6) };
        return { bones, eyes: 'open', mouth: mood.happy > 0.75 ? 'tongue' : 'closed' };
      },
    },

    walk: {
      period: 0.95,
      gaitDistance: 0.85, // ile jednostek świata pies pokonuje w jednym cyklu kroku
      sample({ phase, t, mood }) {
        const p = phase;
        const [fnU, fnL] = leg(p, 22, 42, 0.25);
        const [ffU, ffL] = leg(p, 22, 42, 0.75);
        const [bnU, bnL] = leg(p, 20, 36, 0.0);
        const [bfU, bfL] = leg(p, 20, 36, 0.5);
        return {
          bones: {
            body: { r: 1.2 * sin(p * 2), y: -0.008 * cos(p * 2) },
            neck: { r: -2 * sin(p * 2 + 0.15) },
            head: { r: 2.2 * sin(p * 2 + 0.3) },
            frontLegNear: { r: fnU }, frontPawNear: { r: fnL },
            frontLegFar: { r: ffU }, frontPawFar: { r: ffL },
            backLegNear: { r: bnU }, backPawNear: { r: bnL },
            backLegFar: { r: bfU }, backPawFar: { r: bfL },
            tail: { r: wag(t, 2 + mood.happy * 1.5, 12 + mood.happy * 8, 4) },
            earNear: { r: 4 * sin(p * 2) }, earFar: { r: 4 * sin(p * 2 + 0.1) },
          },
          eyes: 'open',
          mouth: mood.happy > 0.7 ? 'tongue' : 'closed',
        };
      },
    },

    run: {
      period: 0.46,
      gaitDistance: 1.25,
      sample({ phase, t }) {
        const p = phase;
        const [fnU, fnL] = leg(p, 44, 70, 0.0);
        const [ffU, ffL] = leg(p, 44, 70, 0.07);
        const [bnU, bnL] = leg(p, 38, 60, 0.5);
        const [bfU, bfL] = leg(p, 38, 60, 0.57);
        return {
          root: { lift: 0.07 * pos(sin(p + 0.12)) },
          bones: {
            body: { r: 6 * sin(p + 0.25), sx: 1 + 0.03 * sin(p + 0.5) },
            neck: { r: -6 - 4 * sin(p + 0.35) },
            head: { r: 6 + 3 * sin(p + 0.4) },
            frontLegNear: { r: fnU }, frontPawNear: { r: fnL },
            frontLegFar: { r: ffU }, frontPawFar: { r: ffL },
            backLegNear: { r: bnU }, backPawNear: { r: bnL },
            backLegFar: { r: bfU }, backPawFar: { r: bfL },
            tail: { r: -42 + 8 * sin(t * 4) },
            earNear: { r: 38 + 10 * sin(p) }, earFar: { r: 34 + 10 * sin(p + 0.1) },
          },
          eyes: 'open',
          mouth: 'tongue',
        };
      },
    },

    sit: {
      period: 3.4,
      sample({ t, mood }) {
        const bones = sitBones(t);
        bones.tail.r += wag(t, 1.2 + mood.happy * 1.6, 5 + mood.happy * 9);
        return { bones, eyes: 'open', mouth: mood.happy > 0.75 ? 'tongue' : 'closed' };
      },
    },

    lie: {
      period: 3.8,
      sample({ t, mood }) {
        const bones = lieBones(t);
        bones.tail.r += wag(t, 1, 3 + mood.happy * 6);
        return { bones, eyes: 'open', mouth: 'closed' };
      },
    },

    sleep: {
      period: 4.6,
      sample({ t }) {
        const breathe = sin(t / 4.6);
        const bones = merge(lieBones(t), {
          body: { sy: 1 + 0.025 * breathe },
          neck: { r: 52 },
          head: { r: 26 + 1.2 * breathe },
          tail: { r: -14 },
          earNear: { r: 6 }, earFar: { r: 6 },
        });
        return { bones, eyes: 'closed', mouth: 'closed', blink: false };
      },
    },

    eat: {
      period: 0.6,
      sample({ t }) {
        const chew = sin(t * 3.4);
        const bones = merge(standBones(t, 0.4), {
          neck: { r: 64 },
          head: { r: 32 + 5 * chew },
          frontLegNear: { r: -7 }, frontLegFar: { r: -4 },
          backLegNear: { r: 4 }, backLegFar: { r: 6 },
          tail: { r: wag(t, 2.4, 13, 2) },
          earNear: { r: -6 + 3 * chew }, earFar: { r: -6 + 3 * chew },
        });
        return { bones, eyes: 'open', mouth: chew > 0 ? 'open' : 'closed' };
      },
    },

    drink: {
      period: 0.3,
      sample({ t }) {
        const lap = sin(t * 5);
        const bones = merge(standBones(t, 0.3), {
          neck: { r: 66 },
          head: { r: 34 + 3 * lap },
          frontLegNear: { r: -8 }, frontLegFar: { r: -5 },
          tail: { r: wag(t, 1.6, 9, 0) },
          earNear: { r: -8 }, earFar: { r: -8 },
        });
        return { bones, eyes: 'open', mouth: lap > -0.2 ? 'tongue' : 'open' };
      },
    },

    play: {
      period: 0.5,
      sample({ t }) {
        const hop = pos(sin(t * 2));
        return {
          root: { lift: 0.035 * hop },
          bones: {
            body: { r: 17 },
            neck: { r: -46 },
            head: { r: -4 + 3 * sin(t * 2) },
            frontLegNear: { r: -70 }, frontPawNear: { r: -14 },
            frontLegFar: { r: -64 }, frontPawFar: { r: -16 },
            backLegNear: { r: -17 }, backPawNear: { r: 0 },
            backLegFar: { r: -15 }, backPawFar: { r: 0 },
            tail: { r: wag(t, 3.4, 24, 6) },
            earNear: { r: -10 }, earFar: { r: -10 },
          },
          eyes: 'open',
          mouth: 'tongue',
        };
      },
    },

    pet: {
      period: 1,
      sample({ t }) {
        const sway = sin(t * 0.8);
        const bones = merge(sitBones(t), {
          body: { r: 2 * sway },
          neck: { r: -8 },
          head: { r: -14 + 3 * sway },
          tail: { r: wag(t, 3.2, 12, 4) },
          earNear: { r: 14 }, earFar: { r: 14 },
        });
        return { bones, eyes: 'happy', mouth: 'tongue' };
      },
    },

    happy: {
      period: 0.62,
      sample({ t }) {
        const w = sin(t * 3.2);
        const bones = merge(standBones(t, 0.6), {
          body: { r: 2.4 * w },
          neck: { r: -6 + 2 * w },
          head: { r: -4 - 2 * w },
          tail: { r: 30 * sin(t * 3.4) - 4 },
          earNear: { r: -6 }, earFar: { r: -6 },
        });
        return { root: { x: 0.008 * w }, bones, eyes: 'happy', mouth: 'tongue' };
      },
    },

    excited: {
      period: 0.62,
      sample({ phase, t }) {
        const air = Math.sin(Math.PI * phase);
        return {
          root: { lift: 0.24 * Math.pow(air, 0.9) },
          bones: {
            body: { r: -9 * air },
            neck: { r: -10 - 4 * air },
            head: { r: -2 },
            frontLegNear: { r: -38 * air }, frontPawNear: { r: 58 * air },
            frontLegFar: { r: -32 * air }, frontPawFar: { r: 52 * air },
            backLegNear: { r: 26 * air }, backPawNear: { r: -6 * air },
            backLegFar: { r: 22 * air }, backPawFar: { r: -6 * air },
            tail: { r: 30 * sin(t * 4) },
            earNear: { r: 26 * air - 8 }, earFar: { r: 22 * air - 8 },
          },
          eyes: 'open',
          mouth: 'tongue',
        };
      },
    },

    beg: {
      period: 1.4,
      sample({ t }) {
        const bal = sin(t / 1.4);
        return {
          bones: {
            body: { r: -68 + 2.5 * bal },
            neck: { r: 52 - 2 * bal },
            head: { r: 14 },
            frontLegNear: { r: 16 + 4 * sin(t * 1.4) }, frontPawNear: { r: 86 },
            frontLegFar: { r: 10 + 4 * sin(t * 1.4 + 0.2) }, frontPawFar: { r: 82 },
            backLegNear: { r: -24 }, backPawNear: { r: 100 },
            backLegFar: { r: -20 }, backPawFar: { r: 96 },
            tail: { r: -40 + wag(t, 2.6, 8) },
            earNear: { r: 4 }, earFar: { r: 4 },
          },
          eyes: 'open',
          mouth: 'tongue',
        };
      },
    },

    bark: {
      period: 0.55,
      sample({ phase, t }) {
        const k = pos(sin(phase));
        const bones = merge(standBones(t, 0.3), {
          body: { r: -2.5 * k },
          neck: { r: -12 - 9 * k },
          head: { r: -9 * k },
          frontLegNear: { r: -6 }, frontLegFar: { r: -4 },
          backLegNear: { r: 5 }, backLegFar: { r: 6 },
          tail: { r: 12 + 6 * sin(t * 4) },
          earNear: { r: -10 + 12 * k }, earFar: { r: -10 + 12 * k },
        });
        return { bones, eyes: 'open', mouth: k > 0.25 ? 'bark' : 'closed' };
      },
    },

    sniff: {
      period: 0.5,
      sample({ t }) {
        const twitch = sin(t * 6.5);
        const bones = merge(standBones(t, 0.3), {
          neck: { r: 60 },
          head: { r: 28 + 3 * twitch },
          frontLegNear: { r: -6 }, frontLegFar: { r: -3 },
          tail: { r: wag(t, 1.6, 10, 6) },
          earNear: { r: 4 }, earFar: { r: 4 },
        });
        return { bones, eyes: 'open', mouth: 'closed' };
      },
    },

    dig: {
      period: 0.4,
      sample({ t }) {
        const s = sin(t * 5);
        return {
          bones: {
            body: { r: 11 },
            neck: { r: 36 },
            head: { r: 22 },
            frontLegNear: { r: -18 + 34 * s }, frontPawNear: { r: 30 * pos(s) },
            frontLegFar: { r: -18 - 34 * s }, frontPawFar: { r: 30 * pos(-s) },
            backLegNear: { r: -12 }, backLegFar: { r: -10 },
            tail: { r: wag(t, 4, 16, 14) },
            earNear: { r: 6 + 4 * s }, earFar: { r: 6 - 4 * s },
          },
          eyes: 'open',
          mouth: 'open',
        };
      },
    },

    shake: {
      period: 1.3,
      oneShot: true,
      sample({ progress, t }) {
        const k = Math.sin(Math.PI * Math.min(1, progress));
        const f = (o) => sin(t * 9 + o);
        const bones = merge(standBones(t, 0.2), {
          body: { r: 7 * k * f(0), sx: 1 + 0.05 * k * f(0.25) },
          neck: { r: 7 * k * f(0.15) },
          head: { r: 12 * k * f(0.3) },
          tail: { r: 24 * k * f(0.4) },
          earNear: { r: 34 * k * f(0.45) }, earFar: { r: 30 * k * f(0.5) },
          frontLegNear: { r: -5 }, frontLegFar: { r: -3 }, backLegNear: { r: 5 }, backLegFar: { r: 6 },
        });
        return { bones, eyes: 'closed', mouth: 'closed', blink: false };
      },
    },

    scratch: {
      period: 0.3,
      sample({ t }) {
        const s = sin(t * 7);
        const bones = merge(sitBones(t), {
          body: { r: 6 },
          neck: { r: 12 },
          head: { r: 14 },
          frontLegNear: { r: -6 }, frontLegFar: { r: -5 },
          tail: { r: 6 },
        });
        bones.backLegNear = { r: -112 };
        bones.backPawNear = { r: 52 + 26 * s };
        return { bones, eyes: 'happy', mouth: 'open', blink: false };
      },
    },

    bellyup: {
      period: 1.5,
      sample({ t }) {
        const w = sin(t / 1.5);
        return {
          root: { sy: -1, r: 0 },
          bones: {
            body: { r: 0, sy: 1 + 0.015 * w },
            neck: { r: 60 },
            head: { r: 26 + 3 * w },
            frontLegNear: { r: -34 + 10 * sin(t * 1.3) }, frontPawNear: { r: 64 },
            frontLegFar: { r: -26 + 10 * sin(t * 1.3 + 0.3) }, frontPawFar: { r: 70 },
            backLegNear: { r: -26 + 8 * sin(t * 1.1 + 0.5) }, backPawNear: { r: 72 },
            backLegFar: { r: -18 + 8 * sin(t * 1.1 + 0.8) }, backPawFar: { r: 76 },
            tail: { r: -70 + wag(t, 2.6, 14) },
            earNear: { r: -40 }, earFar: { r: -40 },
          },
          eyes: 'happy',
          mouth: 'tongue',
        };
      },
    },

    roll: {
      period: 1.2,
      oneShot: true,
      sample({ progress, t }) {
        const p = Math.min(1, Math.max(0, progress));
        const sy = Math.cos(p * TAU);
        const tuck = Math.sin(Math.PI * p);
        return {
          root: { sy: sy, lift: 0.05 * tuck },
          bones: merge(lieBones(t), {
            frontLegNear: { r: 40 * tuck }, frontPawNear: { r: 50 * tuck },
            frontLegFar: { r: 40 * tuck }, frontPawFar: { r: 50 * tuck },
            backLegNear: { r: 30 * tuck }, backPawNear: { r: -40 * tuck },
            backLegFar: { r: 30 * tuck }, backPawFar: { r: -40 * tuck },
            neck: { r: 60 * tuck },
            head: { r: 26 * tuck },
            tail: { r: 30 * tuck },
          }),
          eyes: 'happy',
          mouth: 'tongue',
          blink: false,
        };
      },
    },

    paw: {
      period: 1,
      sample({ t }) {
        const bones = sitBones(t);
        bones.frontLegNear = { r: -58 + 6 * sin(t * 2) };
        bones.frontPawNear = { r: 30 + 5 * sin(t * 2 + 0.2) };
        bones.head.r -= 8;
        bones.neck.r -= 4;
        bones.tail.r += wag(t, 2.6, 10);
        return { bones, eyes: 'open', mouth: 'tongue' };
      },
    },

    curious: {
      period: 2,
      sample({ t }) {
        const bones = merge(standBones(t, 0.6), {
          neck: { r: -12 },
          head: { r: -12 + 4 * sin(t / 2) },
          tail: { r: wag(t, 0.8, 6, 6) },
          earNear: { r: -14 }, earFar: { r: -14 },
        });
        return { bones, eyes: 'open', mouth: 'closed' };
      },
    },

    sad: {
      period: 3,
      sample({ t }) {
        const bones = merge(standBones(t, 0.7), {
          neck: { r: 22 },
          head: { r: 16 + 1.5 * sin(t / 3) },
          tail: { r: -92 + 2 * sin(t / 3) },
          earNear: { r: 12 }, earFar: { r: 12 },
          frontLegNear: { r: 2 }, backLegNear: { r: -2 },
        });
        return { bones, eyes: 'sad', mouth: 'closed' };
      },
    },
  };

  P.DogClips = CLIPS;
})(window.Pies = window.Pies || {});
