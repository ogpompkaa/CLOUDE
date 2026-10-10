/*
 * SKÓRKA: „Husky (tymczasowy cartoon)”
 * Druga skórka testowa: inne proporcje (dłuższe łapy, większy pies), stojące uszy
 * i zakręcony ogon. Używa DOKŁADNIE tych samych animacji i logiki co beagle –
 * różnice wynikają wyłącznie z tej konfiguracji. Opis pól: docs/WYGLAD-PSA.md
 */
Pies.skins.register({
  id: 'cartoon-husky',
  name: 'Husky (tymczasowy)',
  description: 'Druga skórka testowa: inne proporcje, stojące uszy, zakręcony ogon.',
  version: 1,
  renderer: 'rig',
  facing: 'right',
  fit: { shoulderHeightPx: 304 },
  // pies o 8% wyższy od wzorcowego – logika sama dostosuje zasięg pyska i strefy dotyku
  proportions: { heightScale: 1.08, lengthRatio: 1.05, mouthReach: 0.92, headHeight: 1.62 },
  motion: { body: 1, head: 1, ears: 0.7, tail: 0.6, legs: 1 },

  parts: {
    body: {
      src: 'parts/body.svg', pivot: [165, 100], parent: 'root', at: [0, -238], z: 20,
      contacts: [[172, 152], [258, 162], [64, 158], [16, 106], [160, 36], [312, 112]],
    },
    neck: { src: 'parts/neck.svg', pivot: [56, 106], parent: 'body', at: [276, 72], rotation: 16, z: 22 },
    head: { src: 'parts/head.svg', pivot: [82, 150], parent: 'neck', at: [58, 30], rotation: -16, z: 40, contacts: [[150, 166], [222, 112], [96, 22]] },
    eyes: {
      expression: 'eyes',
      variants: { open: 'parts/eyes-open.svg', closed: 'parts/eyes-closed.svg', happy: 'parts/eyes-happy.svg', sad: 'parts/eyes-sad.svg' },
      pivot: [30, 30], parent: 'head', at: [150, 80], z: 42,
    },
    mouth: {
      expression: 'mouth',
      variants: { closed: 'parts/mouth-closed.svg', open: 'parts/mouth-open.svg', tongue: 'parts/mouth-tongue.svg', bark: 'parts/mouth-bark.svg' },
      pivot: [84, 8], parent: 'head', at: [222, 112], z: 41,
    },
    // Uszy stojące: pivot u PODSTAWY ucha, a motion: -1 odwraca kierunek ruchu
    // (animacje opisują „koniec ucha do tyłu” jako obrót dodatni, co dla stojącego ucha jest odwrotne).
    earNear: {
      src: 'parts/ear-near.svg', pivot: [36, 100], parent: 'head', at: [92, 44], rotation: 10, z: 45, motion: -1,
      dynamics: { stiffness: 260, damping: 16, inertia: 0.25, bounce: 60 },
    },
    earFar: {
      src: 'parts/ear-far.svg', pivot: [36, 100], parent: 'head', at: [118, 36], rotation: 4, z: 30, motion: -1,
      dynamics: { stiffness: 260, damping: 16, inertia: 0.25, bounce: 60 },
    },
    tail: {
      src: 'parts/tail.svg', pivot: [35, 146], parent: 'body', at: [34, 64], rotation: -18, z: 15,
      dynamics: { stiffness: 220, damping: 14, inertia: 0.3, bounce: 80 },
    },
    frontLegNear: { src: 'parts/front-leg-near.svg', pivot: [40, 34], parent: 'body', at: [256, 120], z: 34 },
    frontPawNear: { src: 'parts/front-paw-near.svg', pivot: [34, 18], parent: 'frontLegNear', at: [40, 110], z: 33, contacts: [[28, 160], [62, 160]] },
    frontLegFar:  { src: 'parts/front-leg-far.svg',  pivot: [40, 34], parent: 'body', at: [238, 116], z: 12 },
    frontPawFar:  { src: 'parts/front-paw-far.svg',  pivot: [34, 18], parent: 'frontLegFar', at: [40, 110], z: 11, contacts: [[28, 160], [62, 160]] },
    backLegNear: { src: 'parts/back-leg-near.svg', pivot: [56, 42], parent: 'body', at: [80, 122], rotation: 4, z: 32 },
    backPawNear: { src: 'parts/back-paw-near.svg', pivot: [34, 18], parent: 'backLegNear', at: [52, 116], rotation: -4, z: 31, contacts: [[28, 160], [62, 160]] },
    backLegFar:  { src: 'parts/back-leg-far.svg',  pivot: [56, 42], parent: 'body', at: [98, 118], rotation: 4, z: 10 },
    backPawFar:  { src: 'parts/back-paw-far.svg',  pivot: [34, 18], parent: 'backLegFar', at: [52, 116], rotation: -4, z: 9, contacts: [[28, 160], [62, 160]] },
  },

  anchors: {
    neck:  { part: 'neck', at: [60, 70], rotation: -4, width: 94, z: 24 },
    head:  { part: 'head', at: [106, 26], rotation: -8, width: 118, z: 46 },
    mouth: { part: 'head', at: [204, 136], rotation: 0, z: 43 },
  },

  landmarks: {
    head:    { part: 'head', at: [132, 96] },
    headTop: { part: 'head', at: [110, -40] },
    back:    { part: 'body', at: [150, 64] },
    belly:   { part: 'body', at: [196, 142] },
    tail:    { part: 'tail', at: [46, 30] },
    mouth:   { part: 'head', at: [206, 132] },
  },
});
