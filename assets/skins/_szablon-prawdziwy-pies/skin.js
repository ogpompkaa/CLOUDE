/*
 * SZABLON SKÓRKI WARSTWOWEJ DLA PRAWDZIWEGO PSA
 *
 * 1. Skopiuj cały folder do assets/skins/moj-pies/ (nazwa folderu = id poniżej).
 * 2. Wstaw grafiki do parts/ (lista w parts/PLIKI.txt).
 * 3. Podmień wartości oznaczone  <-- ZMIEŃ  na zmierzone w swoich grafikach
 *    (w edytorze graficznym odczytaj pozycję kursora w pikselach, (0,0) = lewy górny róg).
 * 4. Dopisz 'moj-pies' do assets/skins/manifest.js.
 * 5. W grze: Wygląd -> Wybierz -> Studio wyglądu (włącz szkielet) i dopracuj punkty.
 *
 * Wartości startowe skopiowano z tymczasowego beagle'a – pokazują rząd wielkości.
 * Opis każdego pola: docs/WYGLAD-PSA.md (rozdział 6).
 */
Pies.skins.register({
  id: 'moj-pies',                         // <-- ZMIEŃ: nazwa folderu skórki
  name: 'Mój pies',                       // <-- ZMIEŃ: nazwa w panelu „Wygląd”
  description: 'Prawdziwy pies na podstawie zdjęć.',
  version: 1,
  renderer: 'rig',
  facing: 'right',                        // 'left', jeśli pies na zdjęciach patrzy w lewo

  // Wysokość w kłębie (ziemia -> szczyt łopatek) w pikselach Twoich grafik, w pozie stojącej.
  fit: { shoulderHeightPx: 288 },         // <-- ZMIEŃ

  // Proporcje logiczne psa – z nich korzysta logika gry (nie z rozmiaru obrazków).
  proportions: {
    heightScale: 1.0,   // wielkość psa względem psa wzorcowego (mały ~0.7, duży ~1.3)
    lengthRatio: 1.1,   // długość tułowia / wysokość w kłębie
    mouthReach: 0.94,   // jak daleko od środka psa jest pysk (miski, piłka)
    headHeight: 1.58,   // czubek głowy nad ziemią (w wysokościach w kłębie)
  },

  motion: { body: 1, head: 1, ears: 1, tail: 1, legs: 1 },

  parts: {
    // pivot = punkt obrotu W TEJ grafice; at = gdzie ten punkt leży W GRAFICE RODZICA
    body: {
      src: 'parts/body.png',
      pivot: [165, 100],                  // <-- ZMIEŃ: środek tułowia
      parent: 'root',
      at: [0, -222],                      // <-- ZMIEŃ: pivot tułowia nad ziemią (ujemne Y = w górę)
      z: 20,
      contacts: [[172, 152], [258, 162], [64, 158], [16, 106], [160, 36], [312, 112]], // brzuch, pierś, zad, tył, grzbiet, przód
    },
    neck: { src: 'parts/neck.png', pivot: [56, 106], parent: 'body', at: [276, 72], rotation: 18, z: 22 },
    head: {
      src: 'parts/head.png', pivot: [82, 150], parent: 'neck', at: [58, 30], rotation: -18, z: 40,
      contacts: [[150, 166], [222, 112], [96, 22]], // podbródek, nos, czubek głowy
    },
    eyes: {
      expression: 'eyes',
      variants: { open: 'parts/eyes-open.png', closed: 'parts/eyes-closed.png', happy: 'parts/eyes-happy.png' },
      pivot: [30, 30], parent: 'head', at: [150, 80], z: 42,
    },
    mouth: {
      expression: 'mouth',
      variants: { closed: 'parts/mouth-closed.png', open: 'parts/mouth-open.png', tongue: 'parts/mouth-tongue.png', bark: 'parts/mouth-bark.png' },
      pivot: [84, 8], parent: 'head', at: [222, 112], z: 41,
    },
    // Uszy opadające: pivot u nasady (u góry). Uszy stojące: pivot u nasady (u dołu) + motion: -1.
    earNear: { src: 'parts/ear-near.png', pivot: [40, 18], parent: 'head', at: [86, 64], rotation: 8, z: 45, dynamics: { stiffness: 70, damping: 7, inertia: 0.7, bounce: 380 } },
    earFar:  { src: 'parts/ear-far.png',  pivot: [40, 18], parent: 'head', at: [110, 56], rotation: -2, z: 30, dynamics: { stiffness: 70, damping: 7, inertia: 0.7, bounce: 380 } },
    tail:    { src: 'parts/tail.png', pivot: [30, 148], parent: 'body', at: [32, 66], rotation: -38, z: 15, dynamics: { stiffness: 160, damping: 10, inertia: 0.4, bounce: 160 } },
    // Łapy: górny segment (ramię/udo) + dolny (przedramię/podudzie z łapą). contacts = podeszwa.
    frontLegNear: { src: 'parts/front-leg-near.png', pivot: [40, 34], parent: 'body', at: [256, 120], z: 34 },
    frontPawNear: { src: 'parts/front-paw-near.png', pivot: [34, 18], parent: 'frontLegNear', at: [40, 110], z: 33, contacts: [[28, 144], [62, 144]] },
    frontLegFar:  { src: 'parts/front-leg-far.png',  pivot: [40, 34], parent: 'body', at: [238, 116], z: 12 },
    frontPawFar:  { src: 'parts/front-paw-far.png',  pivot: [34, 18], parent: 'frontLegFar', at: [40, 110], z: 11, contacts: [[28, 144], [62, 144]] },
    backLegNear:  { src: 'parts/back-leg-near.png',  pivot: [56, 42], parent: 'body', at: [80, 122], rotation: 4, z: 32 },
    backPawNear:  { src: 'parts/back-paw-near.png',  pivot: [34, 18], parent: 'backLegNear', at: [52, 116], rotation: -4, z: 31, contacts: [[28, 144], [62, 144]] },
    backLegFar:   { src: 'parts/back-leg-far.png',   pivot: [56, 42], parent: 'body', at: [98, 118], rotation: 4, z: 10 },
    backPawFar:   { src: 'parts/back-paw-far.png',   pivot: [34, 18], parent: 'backLegFar', at: [52, 116], rotation: -4, z: 9, contacts: [[28, 144], [62, 144]] },
  },

  // Punkty zaczepienia akcesoriów z ekwipunku (te same przedmioty pasują do każdej skórki).
  anchors: {
    neck:  { part: 'neck', at: [60, 70], rotation: -4, width: 92, z: 24 },   // width = grubość szyi w px
    head:  { part: 'head', at: [104, 24], rotation: -8, width: 120, z: 46 }, // czapka/kokardka
    mouth: { part: 'head', at: [204, 136], rotation: 0, z: 43 },             // piłka/patyk/kość w pysku
  },

  // Punkty orientacyjne: strefy dotyku i efekty (serduszka, „Zzz”).
  landmarks: {
    head:    { part: 'head', at: [132, 96] },
    headTop: { part: 'head', at: [112, 18] },
    back:    { part: 'body', at: [150, 64] },
    belly:   { part: 'body', at: [196, 142] },
    tail:    { part: 'tail', at: [30, 40] },
    mouth:   { part: 'head', at: [206, 132] },
  },
});
