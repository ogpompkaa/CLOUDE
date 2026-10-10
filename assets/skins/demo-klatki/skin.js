/*
 * SKÓRKA KLATKOWA (przykład): „Beagle – test klatek”
 * Wygenerowana przez tools/bake-frames.mjs z warstwowego beagle'a, żeby pokazać format "frames":
 * każda animacja to pasek klatek PNG (sheet) albo lista plików (frames).
 * Celowo ma tylko 7 animacji – brakujące stany gra zastępuje automatycznie
 * (np. excited -> happy, lie -> sit, roll -> bellyup -> lie -> sit). Opis pól: docs/WYGLAD-PSA.md
 */
Pies.skins.register({
  id: 'demo-klatki',
  name: 'Beagle – test klatek',
  description: 'Przykład skórki z gotowych klatek PNG (format dla serii zdjęć). Ma tylko część animacji.',
  version: 1,
  renderer: 'frames',
  facing: 'right',
  frameSize: [240, 220],
  // origin: punkt na ziemi pod środkiem psa w każdej klatce; shoulderHeightPx: wysokość w kłębie w klatce
  fit: { shoulderHeightPx: 104, origin: [104, 206] },
  proportions: { heightScale: 1.0, lengthRatio: 1.1, mouthReach: 0.94, headHeight: 1.58 },

  animations: {
    idle: {
      sheet: { src: 'frames/idle.png', frameWidth: 240, frameHeight: 220, count: 8 },
      fps: 6,
      landmarks: { head: [170, 71], headTop: [162, 43], back: [99, 113], belly: [115, 141], tail: [32, 84], mouth: [197, 83] },
      anchors: { neck: { at: [149, 104], rotation: 12, width: 33 }, mouth: { at: [197, 84] } },
    },
    walk: {
      sheet: { src: 'frames/walk.png', frameWidth: 240, frameHeight: 220, count: 8 },
      fps: 9,
      landmarks: { head: [171, 72], headTop: [164, 44], back: [99, 114], belly: [115, 142], tail: [39, 78], mouth: [198, 85] },
      anchors: { neck: { at: [150, 106], rotation: 14, width: 33 }, mouth: { at: [198, 86] } },
    },
    run: {
      sheet: { src: 'frames/run.png', frameWidth: 240, frameHeight: 220, count: 6 },
      fps: 13,
      landmarks: { head: [170, 69], headTop: [164, 41], back: [99, 109], belly: [114, 138], tail: [22, 96], mouth: [195, 83] },
      anchors: { neck: { at: [148, 102], rotation: 12, width: 33 }, mouth: { at: [194, 84] } },
    },
    sit: {
      sheet: { src: 'frames/sit.png', frameWidth: 240, frameHeight: 220, count: 4 },
      fps: 4,
      landmarks: { head: [147, 65], headTop: [139, 38], back: [91, 142], belly: [122, 151], tail: [40, 205], mouth: [174, 77] },
      anchors: { neck: { at: [129, 100], rotation: 2, width: 33 }, mouth: { at: [173, 78] } },
    },
    sleep: {
      sheet: { src: 'frames/sleep.png', frameWidth: 240, frameHeight: 220, count: 6 },
      fps: 3,
      landmarks: { head: [193, 142], headTop: [218, 128], back: [99, 137], belly: [115, 166], tail: [30, 168], mouth: [187, 171] },
      anchors: { neck: { at: [156, 136], rotation: 61, width: 33 }, mouth: { at: [185, 171] } },
    },
    eat: {
      sheet: { src: 'frames/eat.png', frameWidth: 240, frameHeight: 220, count: 6 },
      fps: 8,
      landmarks: { head: [188, 132], headTop: [217, 128], back: [99, 113], belly: [115, 141], tail: [29, 85], mouth: [172, 157] },
      anchors: { neck: { at: [157, 115], rotation: 77, width: 33 }, mouth: { at: [171, 156] } },
    },
    happy: {
      sheet: { src: 'frames/happy.png', frameWidth: 240, frameHeight: 220, count: 6 },
      fps: 10,
      landmarks: { head: [167, 67], headTop: [155, 40], back: [100, 111], belly: [115, 140], tail: [36, 78], mouth: [195, 76] },
      anchors: { neck: { at: [149, 103], rotation: 10, width: 33 }, mouth: { at: [195, 77] } },
    },
  },
});
