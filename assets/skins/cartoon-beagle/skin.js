/*
 * SKÓRKA: „Beagle (tymczasowy cartoon)”
 * Renderer warstwowy – pies złożony z osobnych grafik części ciała na wspólnym szkielecie.
 *
 * To jest WZORCOWA konfiguracja. Opis wszystkich pól: docs/WYGLAD-PSA.md
 * Wszystkie współrzędne są w pikselach grafik (lewy górny róg obrazka = 0,0; Y w dół).
 * Pies na grafikach patrzy w PRAWO.
 */
Pies.skins.register({
  id: 'cartoon-beagle',               // = nazwa folderu assets/skins/cartoon-beagle/
  name: 'Beagle (tymczasowy)',
  description: 'Tymczasowa postać w stylu 2D cartoon – do podmiany na prawdziwego psa.',
  version: 1,
  renderer: 'rig',
  facing: 'right',

  // Skalowanie: ile pikseli grafiki ma pies w kłębie (od ziemi do szczytu łopatek w pozie stojącej).
  fit: { shoulderHeightPx: 288 },

  // Proporcje logiczne – z nich korzysta logika gry (zasięg pyska, strefy dotyku), nie z obrazków.
  proportions: { heightScale: 1.0, lengthRatio: 1.1, mouthReach: 0.94, headHeight: 1.58 },

  // Siła ruchu grup kości (1 = domyślna). Np. ogon 1.2 = żywsze merdanie.
  motion: { body: 1, head: 1, ears: 1, tail: 1.1, legs: 1 },

  parts: {
    // ---------- tułów (korzeń szkieletu) ----------
    body: {
      src: 'parts/body.svg',
      pivot: [165, 100],            // środek tułowia w body.svg
      parent: 'root',
      at: [0, -222],                // 222 px nad ziemią pod środkiem psa
      z: 20,
      // punkty, którymi tułów może dotknąć ziemi (brzuch, pierś, zad, grzbiet – przy turlaniu)
      contacts: [[172, 152], [258, 162], [64, 158], [16, 106], [160, 36], [312, 112]],
    },
    neck: { src: 'parts/neck.svg', pivot: [56, 106], parent: 'body', at: [276, 72], rotation: 18, z: 22 },
    head: {
      src: 'parts/head.svg', pivot: [82, 150], parent: 'neck', at: [58, 30], rotation: -18, z: 40,
      contacts: [[150, 166], [222, 112], [96, 22]], // podbródek, nos i czubek głowy (gdy pies kładzie głowę)
    },
    eyes: {
      expression: 'eyes',             // wariant wybiera mimika: open / closed / happy / sad
      variants: { open: 'parts/eyes-open.svg', closed: 'parts/eyes-closed.svg', happy: 'parts/eyes-happy.svg', sad: 'parts/eyes-sad.svg' },
      pivot: [30, 30], parent: 'head', at: [150, 80], z: 42,
    },
    mouth: {
      expression: 'mouth',            // closed / open / tongue / bark
      variants: { closed: 'parts/mouth-closed.svg', open: 'parts/mouth-open.svg', tongue: 'parts/mouth-tongue.svg', bark: 'parts/mouth-bark.svg' },
      pivot: [84, 8], parent: 'head', at: [222, 112], z: 41,
    },
    // ---------- uszy (sprężyste) ----------
    earNear: {
      src: 'parts/ear-near.svg', pivot: [40, 18], parent: 'head', at: [86, 64], rotation: 8, scale: 0.92, z: 45,
      dynamics: { stiffness: 70, damping: 7, inertia: 0.7, bounce: 380 },
    },
    earFar: {
      src: 'parts/ear-far.svg', pivot: [40, 18], parent: 'head', at: [110, 56], rotation: -2, scale: 0.9, z: 30,
      dynamics: { stiffness: 70, damping: 7, inertia: 0.7, bounce: 380 },
    },
    // ---------- ogon ----------
    tail: {
      src: 'parts/tail.svg', pivot: [30, 148], parent: 'body', at: [32, 66], rotation: -38, z: 15,
      dynamics: { stiffness: 160, damping: 10, inertia: 0.4, bounce: 160 },
    },
    // ---------- łapy przednie: ramię + przedramię z łapą ----------
    frontLegNear: { src: 'parts/front-leg-near.svg', pivot: [40, 34], parent: 'body', at: [256, 120], z: 34 },
    frontPawNear: { src: 'parts/front-paw-near.svg', pivot: [34, 18], parent: 'frontLegNear', at: [40, 110], z: 33, contacts: [[28, 144], [62, 144]] },
    frontLegFar:  { src: 'parts/front-leg-far.svg',  pivot: [40, 34], parent: 'body', at: [238, 116], z: 12 },
    frontPawFar:  { src: 'parts/front-paw-far.svg',  pivot: [34, 18], parent: 'frontLegFar', at: [40, 110], z: 11, contacts: [[28, 144], [62, 144]] },
    // ---------- łapy tylne: udo + podudzie z łapą ----------
    backLegNear: { src: 'parts/back-leg-near.svg', pivot: [56, 42], parent: 'body', at: [80, 122], rotation: 4, z: 32 },
    backPawNear: { src: 'parts/back-paw-near.svg', pivot: [34, 18], parent: 'backLegNear', at: [52, 116], rotation: -4, z: 31, contacts: [[28, 144], [62, 144]] },
    backLegFar:  { src: 'parts/back-leg-far.svg',  pivot: [56, 42], parent: 'body', at: [98, 118], rotation: 4, z: 10 },
    backPawFar:  { src: 'parts/back-paw-far.svg',  pivot: [34, 18], parent: 'backLegFar', at: [52, 116], rotation: -4, z: 9, contacts: [[28, 144], [62, 144]] },
  },

  // Punkty zaczepienia przedmiotów z ekwipunku (niezależnych od skórki).
  // width = grubość szyi / szerokość głowy w px grafiki – do niej skaluje się obroża czy czapka.
  anchors: {
    neck:  { part: 'neck', at: [60, 70], rotation: -4, width: 92, z: 24 },
    head:  { part: 'head', at: [104, 24], rotation: -8, width: 120, z: 46 },
    mouth: { part: 'head', at: [204, 136], rotation: 0, z: 43 },
  },

  // Punkty orientacyjne – na nich opierają się strefy dotyku i efekty (serduszka, „Zzz”).
  landmarks: {
    head:    { part: 'head', at: [132, 96] },
    headTop: { part: 'head', at: [112, 18] },
    back:    { part: 'body', at: [150, 64] },
    belly:   { part: 'body', at: [196, 142] },
    tail:    { part: 'tail', at: [30, 40] },
    mouth:   { part: 'head', at: [206, 132] },
  },

  // Opcjonalnie: promienie stref dotyku (w wysokościach w kłębie).
  zones: { head: { radius: 0.36 }, tail: { radius: 0.24 } },
});
