# Kariera Pro Playera

Symulator kariery zawodnika CS2 w jednym pliku HTML. Zaczynasz jako nikomu nieznany gracz
z FACEIT-a i próbujesz dojść na szczyt światowego rankingu — trenując, wygrywając mecze,
negocjując kontrakty i budując pozycję w szatni.

Gra jest fanowskim projektem z fikcyjnymi drużynami, zawodnikami i turniejami.
Nie jest powiązana z żadnym wydawcą ani realną organizacją esportową.

## Uruchomienie

Otwórz `index.html` w przeglądarce. To wszystko — żadnego serwera, budowania ani instalacji.
Wszystkie zasoby, łącznie z krojami pisma, są wbudowane w plik, więc gra działa bez internetu.
Postęp zapisuje się automatycznie w `localStorage`; można go też wyeksportować jako tekst
z zakładki Profil.

Interfejs jest po polsku i zaprojektowany pod ekran telefonu, ale działa na każdej szerokości.

## Jak się gra

Sezon trwa 26 tygodni. Co tydzień masz **4 punkty energii** i kalendarz, w którym czekają
mecze ligowe, dwa turnieje (w tym Major) oraz playoff dla najlepszej czwórki.

- **Trening** — aim, refleks, game sense, utility, teamplay, rozmowy w składzie, stream,
  media, odpoczynek i regeneracja. Kondycja i psychika nie resetują się co tydzień,
  więc przetrenowanie realnie psuje formę, a w skrajnym przypadku kończy się kontuzją
  albo wypaleniem i opuszczeniem meczu.
- **Mecze** — rozgrywane runda po rundzie z widocznym wynikiem, Twoimi fragami i ADR.
  W kluczowych momentach (runda pistoletowa, strata pięciu rund, piłka meczowa) podejmujesz
  decyzje, których szansa powodzenia zależy od Twoich statystyk, psychiki i formy.
  W zakładce Profil można przełączyć mecze na tryb błyskawiczny.
- **Kariera** — oferty transferowe z negocjacjami (pensja, gwarancja miejsca, wybór roli,
  bonus za trofea, odstępne), cele od zarządu na sezon, sponsorzy odblokowywani hype'em.
- **Drużyna** — możesz walczyć o rolę kapitana i prosić zarząd o transfery, jeśli masz
  wystarczającą pozycję w zespole. Przywództwo rośnie z rozmów w składzie.
- **Świat** — 44 rywali AI z własnymi drużynami i karierami, żywy ranking Top 20,
  oraz rywal kariery, który trafia do Twojej ligi i którego trzeba wyprzedzić.
- **Pieniądze** — sprzęt, trener, analityk, psycholog, menedżer, fizjoterapeuta i mieszkanie,
  a do tego portfel inwestycyjny liczony do punktów Hall of Fame.

Kariera kończy się przejściem na emeryturę (samodzielnie albo z wiekiem), a jej podsumowaniem
jest wynik Hall of Fame liczony z trofeów, rankingu, ratingu i zarobków.

## Testy

W repozytorium jest smoke test, który przechodzi kilka sezonów w prawdziwej przeglądarce
i sprawdza, czy gra nie wyrzuca błędów, czy mecze i decyzje działają, czy statystyki
mieszczą się w sensownych granicach oraz czy zapis da się wyeksportować i wczytać.

```bash
npm install
npx playwright install chromium
npm test                 # dwa sezony
node tests/smoke.mjs 5   # dłuższy przebieg
```

Jeśli masz już gdzieś Chromium, wskaż je zmienną `CHROMIUM_PATH`, żeby pominąć pobieranie.

Test uruchamia się też automatycznie w GitHub Actions przy każdym pushu
(`.github/workflows/smoke.yml`).

## Struktura

| Plik | Zawartość |
|---|---|
| `index.html` | cała gra: style, ikony SVG, kroje w base64 i logika |
| `tests/smoke.mjs` | smoke test w Playwrighcie |
| `.github/workflows/smoke.yml` | uruchomienie testu w CI |
