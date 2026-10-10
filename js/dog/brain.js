/*
 * MÓZG PSA – wybiera, co pies robi sam z siebie, gdy gracz niczego nie zleca.
 * Zachowania są psie: domaga się spaceru przy drzwiach, przynosi piłkę, szczeka na
 * listonosza za oknem, goni ogon, drapie się łapą, prosi przy misce, wita się z radością.
 * Mózg nie zna grafik – posługuje się wyłącznie stanami z DogStates i pozycjami w świecie.
 */
(function (P) {
  'use strict';

  const U = P.util;
  const T = P.DogTasks;

  function cooldownOk(g, key, sec) {
    const now = g.time;
    if (g.brainCd[key] && now - g.brainCd[key] < sec) return false;
    g.brainCd[key] = now;
    return true;
  }

  const Brain = {
    /** Zwraca sekwencję zadań albo null. */
    pick(g) {
      if (g.scene === 'park') return Brain.parkStep(g);
      const n = g.state.needs;
      const w = g.state.world;
      const R = g.room;

      // 1. Sen
      if (n.energy < 20 || (g.isNight() && n.energy < 55) || (!w.lights && n.energy < 85)) return g.seqSleep();
      // 2. Jedzenie i picie
      if (n.food < 60 && w.foodBowl > 0.05) return g.seqEat();
      if (n.water < 55 && w.waterBowl > 0.05) return g.seqDrink();
      if (n.food < 25 && cooldownOk(g, 'begFood', 40)) {
        return [
          T.moveTo((gg) => gg.bowlSpot(R.food).x, 'walk', 'Idzie do pustej miski'),
          T.face((gg) => gg.bowlSpot(R.food).facing),
          T.play('sit', 1.0, { label: 'Patrzy na pustą miskę' }),
          T.play('bark', 1.4, { label: 'Głodny! Napełnij miskę', onStart: (gg) => gg.fx('bark', 'mouth') }),
          T.play('sad', 3.5, { label: 'Głodny! Napełnij miskę' }),
        ];
      }
      if (n.water < 25 && cooldownOk(g, 'begWater', 40)) {
        return [
          T.moveTo((gg) => gg.bowlSpot(R.water).x, 'walk', 'Idzie do miski z wodą'),
          T.face((gg) => gg.bowlSpot(R.water).facing),
          T.play('sad', 3.5, { label: 'Spragniony! Nalej wody' }),
        ];
      }
      // 3. Spacer – pies czeka przy drzwiach i skomli
      if (n.walk < 22 && cooldownOk(g, 'door', 35)) {
        return [
          T.moveTo(R.door - 0.75, 'walk', 'Idzie pod drzwi'),
          T.face(1),
          T.play('sit', 1.2, { label: 'Czeka pod drzwiami' }),
          T.play('bark', 1.2, { label: 'Chce na spacer!', onStart: (gg) => gg.fx('bark', 'mouth') }),
          T.play('sad', 3, { label: 'Chce na spacer!' }),
        ];
      }
      // 4. Brud swędzi
      if (n.clean < 25 && cooldownOk(g, 'itch', 30)) {
        return [T.play('sit', 0.6, { label: 'Swędzi go…' }), T.play('scratch', 2.2, { label: 'Przydałaby się kąpiel' })];
      }
      // 5. Nuda – przynosi piłkę i zaprasza do zabawy
      if (n.fun < 35 && n.energy > 30 && g.ball.state === 'floor' && cooldownOk(g, 'bringToy', 45)) return g.seqBringToy();

      // 6. Zwykłe psie zajęcia
      const happy = g.mood().happy;
      const choice = U.weighted([
        { weight: 3, value: 'wander' },
        { weight: 2, value: 'sniff' },
        { weight: 2, value: 'sit' },
        { weight: n.energy < 70 ? 3 : 1, value: 'lie' },
        { weight: 1, value: 'scratch' },
        { weight: 1.2, value: 'window' },
        { weight: n.energy > 50 && happy > 0.5 ? 1 : 0.2, value: 'tail' },
        { weight: happy > 0.6 ? 1.2 : 0.3, value: 'playbow' },
        { weight: happy > 0.7 ? 0.6 : 0.1, value: 'belly' },
        { weight: 0.5, value: 'shake' },
        { weight: g.ball.state === 'floor' && n.energy > 40 ? 0.8 : 0, value: 'toy' },
      ]);
      const randX = () => U.rand(R.minX + 0.4, R.maxX - 0.8);
      switch (choice) {
        case 'wander': return [T.moveTo(randX(), 'walk', 'Spaceruje po pokoju'), T.play('idle', U.rand(1.5, 3.5), { label: 'Rozgląda się' })];
        case 'sniff': return [T.moveTo(randX(), 'walk', 'Węszy'), T.play('sniff', U.rand(1.6, 3), { label: 'Coś wywęszył na podłodze' })];
        case 'sit': return [T.play('sit', U.rand(3, 6), { label: 'Siedzi i obserwuje' })];
        case 'lie': return [T.play('lie', U.rand(4, 8), { label: 'Leży i odpoczywa' })];
        case 'scratch': return [T.play('sit', 0.5, { label: 'Drapie się za uchem' }), T.play('scratch', U.rand(1.2, 2), { label: 'Drapie się za uchem' })];
        case 'window': {
          const seq = [T.moveTo(R.window + U.rand(-0.3, 0.3), 'walk', 'Idzie do okna'), T.play('curious', 2, { label: 'Patrzy przez okno', onStart: (gg) => gg.fx('question', 'headTop') })];
          if (U.chance(0.45)) seq.push(T.play('bark', 1.8, { label: 'Szczeka na listonosza!', fx: { type: 'bark', at: 'mouth', every: 0.6 } }));
          return seq;
        }
        case 'tail': return [T.play('curious', 0.4, { label: 'Zauważył własny ogon…' }), T.spin(3, 'Goni własny ogon!'), T.play('happy', 0.8)];
        case 'playbow': return [T.play('play', 1.6, { label: 'Zaprasza do zabawy' })];
        case 'belly': return [T.play('lie', 0.6, { label: 'Przewraca się na plecy' }), T.play('bellyup', 3, { label: 'Wystawia brzuszek do drapania' })];
        case 'shake': return [T.play('shake', 1.3, { label: 'Otrzepuje się' })];
        case 'toy': return g.seqBringToy(true);
        default: return [T.play('idle', 2)];
      }
    },

    /** Spacer w parku: pies idzie do kolejnych obiektów i reaguje na nie po psiemu. */
    parkStep(g) {
      const park = g.park;
      if (!park) return null;
      if (g.dog.x >= park.length - 0.2) return [T.call((gg) => { gg.endWalk(true); })];
      const next = park.objects.find((o) => !o.done && o.x > g.dog.x + 0.05);
      if (!next) return [T.moveTo(park.length, 'walk', 'Wraca do domu'), T.call((gg) => { gg.endWalk(true); })];
      const gait = U.chance(0.25) && g.state.needs.energy > 35 ? 'run' : 'walk';
      const seq = [];
      const stopX = next.x - g.body.mouthReach * 0.9;
      seq.push(T.moveTo(stopX, gait, gait === 'run' ? 'Biegnie przed siebie' : 'Spacer na smyczy'));
      seq.push(T.face(1));
      seq.push(...g.parkEvent(next));
      return seq;
    },
  };

  P.DogBrain = Brain;
})(window.Pies = window.Pies || {});
