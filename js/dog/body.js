/*
 * LOGICZNE CIAŁO PSA
 *
 * Logika gry nigdy nie mierzy obrazków. Rozmiar psa, zasięg pyska i strefy dotyku
 * wynikają z PROPORCJI zadeklarowanych w konfiguracji skórki (albo z wartości domyślnych).
 *
 * Jednostka świata = wysokość w kłębie psa wzorcowego (heightScale = 1).
 * Oś Y świata rośnie w dół, ziemia ma y = 0 (tak samo jak na ekranie).
 */
(function (P) {
  'use strict';

  const DEFAULT_PROPORTIONS = {
    heightScale: 1.0,   // wysokość w kłębie względem psa wzorcowego (1 = średni pies)
    lengthRatio: 1.5,   // długość tułowia (zad -> pierś) / wysokość w kłębie
    mouthReach: 0.95,   // odległość pyska od środka ciała (w wysokościach w kłębie) – do misek, piłki
    headHeight: 1.45,   // wysokość czubka głowy nad ziemią (w wysokościach w kłębie)
  };

  // Strefy interakcji. Pozycję strefy podaje renderer jako „punkt orientacyjny” (landmark),
  // który porusza się razem z animacją. Promień jest w wysokościach w kłębie, więc nie zależy
  // od rozdzielczości grafik. Skórka może nadpisać promienie w polu `zones`.
  const DEFAULT_ZONES = {
    head:  { landmark: 'head',  radius: 0.34, priority: 3 },
    tail:  { landmark: 'tail',  radius: 0.24, priority: 2 },
    belly: { landmark: 'belly', radius: 0.24, priority: 1 },
    back:  { landmark: 'back',  radius: 0.42, priority: 0 },
  };

  function fromProportions(proportions) {
    const p = Object.assign({}, DEFAULT_PROPORTIONS, proportions || {});
    const h = p.heightScale;
    return {
      proportions: p,
      height: h,                         // jednostki świata
      length: p.lengthRatio * h,
      mouthReach: p.mouthReach * h,
      headTop: p.headHeight * h,
      halfLength: (p.lengthRatio * h) / 2,
    };
  }

  function zones(overrides) {
    const out = {};
    for (const k of Object.keys(DEFAULT_ZONES)) out[k] = Object.assign({}, DEFAULT_ZONES[k], (overrides || {})[k] || {});
    for (const k of Object.keys(overrides || {})) if (!out[k]) out[k] = Object.assign({ priority: 0, radius: 0.3, landmark: k }, overrides[k]);
    return out;
  }

  P.DogBody = { DEFAULT_PROPORTIONS, DEFAULT_ZONES, fromProportions, zones };
})(window.Pies = window.Pies || {});
