/*
 * KONTRAKT STANÓW I ANIMACJI PSA
 *
 * To jest wspólny słownik, którym posługują się logika gry i warstwa wyglądu.
 * Logika (mózg psa) mówi tylko: „pies jest teraz w stanie `sit`”.
 * Skórka decyduje, JAK ten stan wygląda (warstwy + szkielet albo klatki/zdjęcia).
 *
 * Każda skórka może dostarczyć dowolny podzbiór animacji. Brakujące stany są
 * automatycznie zastępowane przez łańcuch `fallback` (np. `excited` -> `happy` -> `idle`),
 * więc nowa skórka działa od razu, nawet jeśli ma tylko animację `idle`.
 */
(function (P) {
  'use strict';

  // id: identyfikator używany w kodzie i w plikach skórek
  // label: nazwa wyświetlana w studiu wyglądu
  // fallback: stan zastępczy, gdy skórka nie ma tej animacji
  // oneShot: animacja odtwarzana raz (logika podaje czas trwania), reszta jest zapętlona
  const LIST = [
    { id: 'idle',     label: 'Spoczynek',          fallback: null },
    { id: 'walk',     label: 'Chód',               fallback: 'idle' },
    { id: 'run',      label: 'Bieg',               fallback: 'walk' },
    { id: 'sit',      label: 'Siad',               fallback: 'idle' },
    { id: 'lie',      label: 'Leżenie (waruj)',    fallback: 'sit' },
    { id: 'sleep',    label: 'Sen',                fallback: 'lie' },
    { id: 'eat',      label: 'Jedzenie',           fallback: 'sniff' },
    { id: 'drink',    label: 'Picie',              fallback: 'eat' },
    { id: 'play',     label: 'Zaproszenie do zabawy (ukłon)', fallback: 'excited' },
    { id: 'pet',      label: 'Głaskanie',          fallback: 'happy' },
    { id: 'happy',    label: 'Radość (merdanie)',  fallback: 'idle' },
    { id: 'excited',  label: 'Ekscytacja (skoki)', fallback: 'happy' },
    { id: 'beg',      label: 'Proszenie (służ)',   fallback: 'sit' },
    { id: 'bark',     label: 'Szczekanie',         fallback: 'idle' },
    { id: 'sniff',    label: 'Węszenie',           fallback: 'idle' },
    { id: 'dig',      label: 'Kopanie',            fallback: 'sniff' },
    { id: 'shake',    label: 'Otrzepywanie',       fallback: 'excited', oneShot: true },
    { id: 'scratch',  label: 'Drapanie się łapą',  fallback: 'sit' },
    { id: 'bellyup',  label: 'Brzuszek do góry',   fallback: 'lie' },
    { id: 'roll',     label: 'Turlanie (sztuczka)', fallback: 'bellyup', oneShot: true },
    { id: 'paw',      label: 'Podaj łapę',         fallback: 'sit' },
    { id: 'curious',  label: 'Zaciekawienie',      fallback: 'idle' },
    { id: 'sad',      label: 'Smutek / skomlenie', fallback: 'idle' },
  ];

  // Kanały mimiki. Skórka warstwowa może mieć warianty grafik oczu i pyska.
  // Brakujący wariant zastępowany jest kolejnym z listy.
  const EXPRESSIONS = {
    eyes: { open: null, closed: 'open', happy: 'closed', sad: 'open' },
    mouth: { closed: null, open: 'closed', tongue: 'open', bark: 'open' },
  };

  // Kanoniczny szkielet psa (widok z boku, pies patrzy w prawo).
  // Skórka warstwowa może pominąć dowolną kość poza `body`.
  const BONES = [
    'body', 'neck', 'head', 'eyes', 'mouth', 'earNear', 'earFar', 'tail',
    'frontLegNear', 'frontPawNear', 'frontLegFar', 'frontPawFar',
    'backLegNear', 'backPawNear', 'backLegFar', 'backPawFar',
  ];

  // Grupy ruchu – skórka może wzmocnić/osłabić całą grupę (motion.tail = 1.3 itd.).
  const BONE_GROUP = {
    body: 'body', neck: 'head', head: 'head', eyes: 'head', mouth: 'head',
    earNear: 'ears', earFar: 'ears', tail: 'tail',
    frontLegNear: 'legs', frontPawNear: 'legs', frontLegFar: 'legs', frontPawFar: 'legs',
    backLegNear: 'legs', backPawNear: 'legs', backLegFar: 'legs', backPawFar: 'legs',
  };

  const byId = {};
  LIST.forEach((s) => { byId[s.id] = s; });

  P.DogStates = {
    list: LIST,
    ids: LIST.map((s) => s.id),
    byId,
    expressions: EXPRESSIONS,
    bones: BONES,
    boneGroup: BONE_GROUP,
    isOneShot: (id) => !!(byId[id] && byId[id].oneShot),
    label: (id) => (byId[id] ? byId[id].label : id),
    /** Łańcuch zastępczy dla stanu, np. chainFor('excited') -> ['excited','happy','idle'] */
    chainFor(id) {
      const chain = [];
      let cur = byId[id] ? id : 'idle';
      while (cur && chain.indexOf(cur) < 0) { chain.push(cur); cur = byId[cur].fallback; }
      if (chain.indexOf('idle') < 0) chain.push('idle');
      return chain;
    },
    /** Pierwszy stan z łańcucha, który skórka faktycznie obsługuje. */
    resolve(id, hasFn) {
      const chain = this.chainFor(id);
      for (const s of chain) if (hasFn(s)) return s;
      return 'idle';
    },
    /** Wariant mimiki z uwzględnieniem zastępstw. */
    resolveExpression(channel, variant, hasFn) {
      const map = EXPRESSIONS[channel];
      let cur = map && variant in map ? variant : Object.keys(map || {})[0];
      const seen = [];
      while (cur && seen.indexOf(cur) < 0) {
        if (hasFn(cur)) return cur;
        seen.push(cur);
        cur = map[cur];
      }
      return null;
    },
  };
})(window.Pies = window.Pies || {});
