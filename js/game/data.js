/*
 * DANE GRY: przedmioty, sztuczki, poziomy więzi, potrzeby.
 * Nic tutaj nie zależy od skórki psa.
 */
(function (P) {
  'use strict';

  const ITEMS = {
    food:    { name: 'Karma', desc: 'Porcja karmy do miski.', kind: 'consumable', icon: 'bowl', price: 8, pack: 5 },
    treat:   { name: 'Smakołyk', desc: 'Nagroda za sztuczki – przyspiesza naukę.', kind: 'consumable', icon: 'treat', price: 6, pack: 3 },
    shampoo: { name: 'Szampon', desc: 'Do kąpieli po błotnistym spacerze.', kind: 'consumable', icon: 'bath', price: 10, pack: 2 },
    bone:    { name: 'Kość do gryzienia', desc: 'Długa zabawa i spokój na kanapie.', kind: 'consumable', icon: 'bone', price: 12, pack: 1 },
    stick:   { name: 'Patyk', desc: 'Pamiątka ze spaceru. Pies jest z niego dumny.', kind: 'collectible', icon: 'stick' },
    ball:    { name: 'Piłka tenisowa', desc: 'Ulubiona zabawka do aportowania.', kind: 'toy', icon: 'ball' },
    'collar-red':   { name: 'Czerwona obroża', kind: 'wearable', slot: 'neck', icon: 'collar', color: '#e0453a', price: 0 },
    'collar-blue':  { name: 'Niebieska obroża', kind: 'wearable', slot: 'neck', icon: 'collar', color: '#2f74e0', price: 35 },
    'collar-green': { name: 'Obroża z dzwoneczkiem', kind: 'wearable', slot: 'neck', icon: 'collar', color: '#34b26b', price: 55 },
    'collar-gold':  { name: 'Obroża z medalem', kind: 'wearable', slot: 'neck', icon: 'collar', color: '#2b2b35', price: 110, level: 5 },
    'bandana':      { name: 'Bandana w kratę', kind: 'wearable', slot: 'neck', icon: 'bandana', color: '#e2453c', price: 45, level: 2 },
    'bowtie':       { name: 'Elegancka mucha', kind: 'wearable', slot: 'neck', icon: 'bowtie', color: '#2d6cdf', price: 70, level: 4 },
    'bow-pink':     { name: 'Różowa kokardka', kind: 'wearable', slot: 'head', icon: 'bow', color: '#ff6fa8', price: 40, level: 2 },
    'cap-blue':     { name: 'Czapeczka z daszkiem', kind: 'wearable', slot: 'head', icon: 'cap', color: '#3b7be8', price: 80, level: 3 },
  };

  const SHOP = ['food', 'treat', 'shampoo', 'bone', 'collar-blue', 'collar-green', 'bandana', 'bow-pink', 'cap-blue', 'bowtie', 'collar-gold'];

  // Sztuczki – stan animacji z kontraktu DogStates. Logika nie wie, jak wyglądają.
  const TRICKS = [
    { id: 'sit',  name: 'Siad',        command: 'Siad!',     anim: 'sit',  level: 1, duration: 2.2 },
    { id: 'paw',  name: 'Podaj łapę',  command: 'Łapa!',     anim: 'paw',  level: 2, duration: 2.4 },
    { id: 'lie',  name: 'Waruj',       command: 'Waruj!',    anim: 'lie',  level: 3, duration: 2.6 },
    { id: 'bark', name: 'Daj głos',    command: 'Głos!',     anim: 'bark', level: 3, duration: 1.7 },
    { id: 'beg',  name: 'Służ',        command: 'Służ!',     anim: 'beg',  level: 4, duration: 2.4 },
    { id: 'roll', name: 'Turlaj się',  command: 'Turlaj!',   anim: 'roll', level: 5, duration: 1.2 },
  ];

  const BOND_TIERS = [
    { min: 0,   name: 'Nieśmiały' },
    { min: 150, name: 'Znajomy' },
    { min: 350, name: 'Kumpel' },
    { min: 600, name: 'Przyjaciel' },
    { min: 850, name: 'Najlepszy przyjaciel' },
  ];

  // Potrzeby (0–100, więcej = lepiej) i spadek na godzinę gry.
  const NEEDS = [
    { id: 'food',   name: 'Najedzenie', icon: 'bowl',   perHour: 22 },
    { id: 'water',  name: 'Woda',       icon: 'drop',   perHour: 28 },
    { id: 'energy', name: 'Energia',    icon: 'bolt',   perHour: 14 },
    { id: 'fun',    name: 'Zabawa',     icon: 'ball',   perHour: 26 },
    { id: 'walk',   name: 'Spacer',     icon: 'leash',  perHour: 12 },
    { id: 'clean',  name: 'Czystość',   icon: 'bath',   perHour: 3 },
  ];

  P.GameData = {
    ITEMS, SHOP, TRICKS, BOND_TIERS, NEEDS,
    trick: (id) => TRICKS.find((t) => t.id === id),
    bondTier(bond) {
      let tier = BOND_TIERS[0], idx = 0;
      BOND_TIERS.forEach((t, i) => { if (bond >= t.min) { tier = t; idx = i; } });
      const next = BOND_TIERS[idx + 1];
      return { index: idx, name: tier.name, min: tier.min, next: next ? next.min : 1000 };
    },
    xpToNext: (level) => 40 + 30 * (level - 1),
  };
})(window.Pies = window.Pies || {});
