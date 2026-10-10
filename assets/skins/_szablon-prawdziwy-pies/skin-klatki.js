/*
 * SZABLON SKÓRKI KLATKOWEJ DLA PRAWDZIWEGO PSA (seria zdjęć / klatek)
 *
 * Użyj go zamiast skin.js, jeśli masz gotowe klatki dla poszczególnych stanów:
 * usuń skin.js i zmień nazwę tego pliku na skin.js.
 * Wystarczy samo `idle` – brakujące stany gra zastąpi automatycznie (docs/WYGLAD-PSA.md, rozdział 5).
 * Działający przykład: assets/skins/demo-klatki/skin.js
 */
Pies.skins.register({
  id: 'moj-pies',                         // <-- ZMIEŃ: nazwa folderu skórki
  name: 'Mój pies (klatki)',
  description: 'Prawdziwy pies – seria zdjęć.',
  version: 1,
  renderer: 'frames',
  facing: 'right',
  frameSize: [600, 500],                  // <-- ZMIEŃ: rozmiar każdej klatki

  // origin = punkt na ziemi pod środkiem psa (ten sam w każdej klatce); shoulderHeightPx = wysokość w kłębie w klatce
  fit: { shoulderHeightPx: 260, origin: [280, 480] },   // <-- ZMIEŃ

  proportions: { heightScale: 1.0, lengthRatio: 1.1, mouthReach: 0.94, headHeight: 1.58 },

  // Domyślne punkty (w pikselach klatki) – stany mogą mieć własne.
  landmarks: { head: [430, 170], headTop: [410, 90], back: [270, 260], belly: [300, 360], tail: [90, 220], mouth: [500, 210] },
  anchors: {
    neck: { at: [380, 250], rotation: 15, width: 80 },
    head: { at: [410, 110], rotation: -8, width: 110 },
    mouth: { at: [505, 215] },
  },

  animations: {
    // Lista osobnych plików…
    idle: { frames: ['frames/idle-01.png', 'frames/idle-02.png', 'frames/idle-03.png', 'frames/idle-04.png'], fps: 4 },
    // …albo arkusz klatek ułożonych obok siebie.
    walk: { sheet: { src: 'frames/walk.png', frameWidth: 600, frameHeight: 500, count: 8 }, fps: 9 },
    sit:  { frames: ['frames/sit-01.png', 'frames/sit-02.png'], fps: 2,
            landmarks: { head: [420, 120], headTop: [400, 40] },            // głowa wyżej niż w pozie stojącej
            anchors: { neck: { at: [370, 200], rotation: 5, width: 80 } } },
    sleep: { frames: ['frames/sleep-01.png', 'frames/sleep-02.png'], fps: 1,
             anchors: { head: { at: [0, 0], hidden: true } } },              // czapka zdjęta do snu
    // run, lie, eat, happy, shake… – dodawaj kolejne stany, gdy będą zdjęcia
  },
});
