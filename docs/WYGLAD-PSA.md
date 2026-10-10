# Wygląd psa: jak podmienić grafiki na prawdziwego psa

Ten dokument opisuje modułowy system wyglądu psa: co gdzie leży, jakie pliki przygotować
i które parametry zmienić, żeby w miejsce tymczasowej postaci cartoon pojawił się
**prawdziwy pies odwzorowany ze zdjęć**, bez przebudowy mechanik gry.

---

## 1. Zasada: zachowanie osobno, wygląd osobno

```
 LOGIKA (nie zna grafik)                    WYGLĄD (wymienna skórka)
 ─────────────────────────                  ──────────────────────────────────────
 js/dog/states.js   kontrakt stanów  ──┐    assets/skins/<id>/skin.js   konfiguracja
 js/dog/body.js     proporcje, strefy  │    assets/skins/<id>/parts/…   warstwy ciała
 js/dog/brain.js    psie zachowania    ├──► js/render/dog-view.js       fasada wyglądu
 js/dog/tasks.js    kroki zachowań     │    js/render/rig-renderer.js   renderer warstw
 js/game/*.js       potrzeby, zapis,   │    js/render/frames-renderer.js renderer klatek
                    ekwipunek, sklep ──┘    js/render/animations.js     animacje szkieletu
```

* Logika mówi wyłącznie: *„pies jest w stanie `sit`, stoi w x = 3.2, patrzy w prawo,
  niesie piłkę, ma czerwoną obrożę”*. To jest **migawka** (`game.snapshot()`).
* Skórka decyduje, **jak** to wygląda: warstwy na szkielecie albo gotowe klatki/zdjęcia.
* Jedyny punkt styku to `js/main.js`: przy zmianie skórki przekazuje do gry **proporcje
  logiczne** z konfiguracji (`game.setBody(...)`). Gra nigdy nie dostaje obrazków ani ich wymiarów.
* Zmiana skórki (`game.setSkin(id)`) zapisuje wyłącznie `appearance.skinId`. Imię (`profile`),
  poziom, XP, więź, umiejętności, statystyki (`progress`), potrzeby (`needs`) i ekwipunek
  (`inventory`) zostają nietknięte. Sprawdza to test `tests/smoke.mjs`.

---

## 2. Dwie drogi dodania prawdziwego psa

| | **A. Warstwy + szkielet** (`renderer: 'rig'`) | **B. Klatki** (`renderer: 'frames'`) |
|---|---|---|
| Co dostarczasz | osobne grafiki części ciała (tułów, głowa, uszy, ogon, 8 segmentów łap, warianty oczu i pyska) | gotowe klatki dla każdego stanu (seria zdjęć / sprite sheet) |
| Animacje | **wszystkie 23 działają od razu** (wspólna biblioteka ruchu) | tylko te, które dostarczysz; reszta przez zastępstwa |
| Akcesoria (obroża, czapka, piłka w pysku) | prawidłowa kolejność warstw (np. ucho nad obrożą) | rysowane na wierzchu klatki |
| Nakład pracy | wycięcie ~16 warstw z kilku zdjęć + dopasowanie punktów | dużo zdjęć/klatek, proste ustawienie |
| Wzór w repozytorium | `assets/skins/cartoon-beagle/`, `assets/skins/cartoon-husky/` | `assets/skins/demo-klatki/` |

Obie drogi można łączyć w czasie: zacząć od kilku klatek (B), a potem przejść na warstwy (A),
zmieniając tylko `renderer` i zawartość folderu skórki.

---

## 3. Krok po kroku

1. **Skopiuj szablon**: `assets/skins/_szablon-prawdziwy-pies/` → `assets/skins/moj-pies/`
   (nazwa folderu = identyfikator skórki; tylko małe litery, cyfry i myślniki).
2. **Wstaw grafiki** do `assets/skins/moj-pies/parts/` (warstwy) albo `frames/` (klatki).
3. **Uzupełnij `skin.js`**: `id: 'moj-pies'`, `name`, `renderer` i pola z rozdziału 6.
   Dla klatek użyj pliku `skin-klatki.js` z szablonu (zmień jego nazwę na `skin.js`).
4. **Dopisz `'moj-pies'` do `assets/skins/manifest.js`**. Pierwsza pozycja na liście jest
   domyślna dla nowych graczy. Obecni gracze zmieniają wygląd w panelu „Wygląd”.
5. **Otwórz grę → Wygląd → Wybierz**. Postęp gracza zostaje bez zmian.
6. **Wygląd → Otwórz studio wyglądu**. Przejrzyj każdą animację z włączonym szkieletem
   i strefami. Popraw `pivot`, `at`, `contacts` i `anchors` (rozdział 8).
   Studio pokazuje też raport: brakujące pliki, ostrzeżenia, użyte zastępstwa.
7. **Uruchom test**: `node tests/smoke.mjs` (wymaga Playwright). Wszystkie punkty muszą być ✓.

Kodu gry (`js/`) nie trzeba zmieniać ani przy pierwszym, ani przy kolejnych psach.

---

## 4. Przygotowanie zdjęć

**Ogólne wymagania**
* Widok **z boku (profil)**, pies **patrzy w prawo**. Jeśli zdjęcia są lustrzane, ustaw
  `facing: 'left'` zamiast je odwracać.
* **PNG z przezroczystym tłem** (albo SVG). Wszystkie warstwy w tej samej skali, czyli
  wycięte z jednego zdjęcia albo przeskalowane do wspólnej wysokości w kłębie.
* Wysokość w kłębie (ziemia → szczyt łopatek) w grafikach: **400–800 px**. Mniej daje
  rozmycie na dużych ekranach, więcej to niepotrzebnie ciężkie pliki. Gra sama skaluje
  i wygładza grafiki (`P.skins.raster`).
* Spójne światło i kolory na wszystkich zdjęciach; najlepiej sesja w jednym miejscu.

**Warstwy dla renderera `rig`** (nazwy plików dowolne, ścieżki podajesz w `skin.js`):

| Część (`parts.<id>`) | Co to jest | Wskazówki |
|---|---|---|
| `body` | tułów bez łap, głowy i ogona | jedyna część obowiązkowa |
| `neck` | szyja | dolna krawędź schowana pod tułowiem, górna pod głową |
| `head` | głowa z nosem, bez uszu, oczu i pyska (albo z nimi, jeśli nie chcesz mimiki) | |
| `eyes` | oczy, warianty `open`, `closed`, `happy`, `sad` | wystarczą `open` i `closed`; reszta ma zastępstwa |
| `mouth` | pysk, warianty `closed`, `open`, `tongue`, `bark` | wystarczy `closed`; wtedy pysk się nie otwiera |
| `earNear`, `earFar` | ucho bliższe i dalsze (dalsze za głową) | punkt obrotu u nasady ucha |
| `tail` | ogon | punkt obrotu u nasady |
| `frontLegNear` / `frontPawNear` | przednia bliższa: ramię / przedramię z łapą | patrz „zakładka na stawie” niżej |
| `frontLegFar` / `frontPawFar` | przednia dalsza | zwykle trochę ciemniejsza (cień) |
| `backLegNear` / `backPawNear` | tylna bliższa: udo / podudzie z łapą | |
| `backLegFar` / `backPawFar` | tylna dalsza | |

**Zakładka na stawie.** Każdy segment kończyny wycinaj z zapasem i z **zaokrąglonym końcem
wokół stawu**: górny koniec przedramienia to półokrąg o środku w łokciu, a dolny koniec
ramienia nachodzi na niego. Dzięki temu przy zginaniu nie widać szczeliny. Tak samo
zrobione są cartoonowe łapy, więc zajrzyj do `assets/skins/cartoon-beagle/parts/*.svg`.

**Dalsza para łap i ucho** są rysowane za tułowiem lub głową. Jeśli masz tylko zdjęcia
bliższej strony, użyj tych samych plików z lekkim przyciemnieniem w edytorze
(albo wskaż ten sam plik w `src`).

**Klatki dla renderera `frames`:** jedna seria zdjęć na stan (np. 6–12 klatek chodu, 4–8 klatek
merdania przy siedzeniu). Wszystkie klatki w **tym samym rozmiarze** (`frameSize`) i z psem
**w tym samym miejscu**: punkt na ziemi pod środkiem psa (`fit.origin`) musi być w każdej
klatce w tych samych współrzędnych. Klatki mogą być osobnymi plikami (`frames: [...]`)
albo poziomym/siatkowym arkuszem (`sheet`).

---

## 5. Stany psa (kontrakt animacji)

Lista jest w `js/dog/states.js`. Logika używa tylko tych nazw. Brakującą animację zastępuje
pierwszy dostępny stan z łańcucha.

| Stan | Znaczenie | Zastępstwo |
|---|---|---|
| `idle` | spoczynek (**jedyny wymagany** w skórce klatkowej) | – |
| `walk` / `run` | chód / bieg (aport, spacer) | `idle` / `walk` |
| `sit` | siad (sztuczka, czekanie przy drzwiach) | `idle` |
| `lie` / `sleep` | leżenie (waruj) / sen | `sit` / `lie` |
| `eat` / `drink` | jedzenie / picie z miski | `sniff` / `eat` |
| `play` | ukłon zapraszający do zabawy | `excited` |
| `pet` | głaskanie po głowie | `happy` |
| `happy` / `excited` | merdanie / podskoki z radości | `idle` / `happy` |
| `beg` | służ (prośba na tylnych łapach) | `sit` |
| `bark` | szczekanie | `idle` |
| `sniff` / `dig` | węszenie / kopanie dołka | `idle` / `sniff` |
| `shake` | otrzepywanie po kąpieli lub kałuży (jednorazowe) | `excited` |
| `scratch` | drapanie się łapą za uchem | `sit` |
| `bellyup` / `roll` | brzuszek do góry / turlanie (jednorazowe) | `lie` / `bellyup` |
| `paw` | podaj łapę | `sit` |
| `curious` / `sad` | zaciekawienie / smutek, skomlenie | `idle` |

**Zalecane minimum dla skórki klatkowej:** `idle`, `walk`, `run`, `sit`, `lie`, `sleep`,
`eat`, `happy`. Wtedy wszystkie mechaniki wyglądają naturalnie.

---

## 6. Opis wszystkich pól konfiguracji (`skin.js`)

Współrzędne są **zawsze w pikselach grafiki**: lewy górny róg obrazka to (0, 0), oś Y w dół.

### Pola wspólne

| Pole | Wymagane | Opis |
|---|---|---|
| `id` | tak | identyfikator = nazwa folderu (`assets/skins/<id>/`) |
| `name`, `description` | – | nazwa i opis w panelu „Wygląd” |
| `version` | – | numer wersji skórki (dla Twojej wygody) |
| `renderer` | tak | `'rig'` (warstwy) albo `'frames'` (klatki) |
| `basePath` | – | inny folder z grafikami; domyślnie `assets/skins/<id>/` |
| `facing` | – | kierunek psa na grafikach: `'right'` (domyślnie) albo `'left'` |
| `fit.shoulderHeightPx` | tak | wysokość w kłębie w pikselach grafiki (w pozie stojącej); od niej zależy skala psa w grze |
| `fit.origin` | klatki | punkt na ziemi pod środkiem psa w klatce |
| `fit.rotationPivot` | – | (rig) środek obrotu całego psa przy turlaniu; domyślnie pivot tułowia |
| `proportions.heightScale` | – | wielkość psa względem psa wzorcowego (1 = średni; 0.7 = mały; 1.3 = duży). Skaluje psa, zasięg pyska i strefy dotyku |
| `proportions.lengthRatio` | – | długość tułowia / wysokość w kłębie (logiczna bryła, kolizje ze ścianami) |
| `proportions.mouthReach` | – | odległość pyska od środka psa w wysokościach w kłębie; tu pies staje przy misce i przy piłce |
| `proportions.headHeight` | – | wysokość czubka głowy nad ziemią w wysokościach w kłębie |
| `motion.body/head/ears/tail/legs` | – | mnożnik siły animacji grupy kości (np. `tail: 1.3` to żywsze merdanie, `ears: 0.5` to sztywne uszy) |
| `zones.<strefa>.radius` | – | promień strefy dotyku (`head`, `back`, `belly`, `tail`) w wysokościach w kłębie |
| `zones.<strefa>.priority`, `.landmark` | – | kolejność sprawdzania i punkt orientacyjny, na którym leży strefa |

### Renderer warstwowy (`rig`)

| Pole części `parts.<id>` | Opis |
|---|---|
| `src` | ścieżka do grafiki (względem folderu skórki) |
| `variants` + `expression` | warianty mimiki: `expression: 'eyes'` (open/closed/happy/sad) albo `'mouth'` (closed/open/tongue/bark) |
| `pivot: [x, y]` | **punkt obrotu w tej grafice**: staw łapy, nasada ucha lub ogona, środek tułowia |
| `parent` | część, do której jest przyczepiona; `'root'` tylko dla tułowia |
| `at: [x, y]` | **gdzie w grafice rodzica leży `pivot` tej części**. Dla `root`: względem ziemi pod środkiem psa (np. `[0, -222]` = 222 px nad ziemią) |
| `z` | kolejność rysowania (większe na wierzchu). Wzór: łapy dalsze 9–12, ogon 15, tułów 20, szyja 22, ucho dalsze 30, łapy bliższe 31–34, głowa 40, pysk 41, oczy 42, ucho bliższe 45 |
| `rotation` | obrót spoczynkowy w stopniach (dodatni = zgodnie z ruchem wskazówek zegara) |
| `scale` | skala części (np. `0.9`, żeby zmniejszyć zbyt duże ucho) |
| `flipX` | odbicie grafiki części w poziomie |
| `motion` | mnożnik ruchu tej jednej części; **`-1` odwraca kierunek** (np. stojące uszy z pivotem u dołu) |
| `dynamics` | sprężystość: `{ stiffness, damping, inertia, bounce, maxOffset }`. Miękkie, opadające uszy: `stiffness 60–90, damping 6–8, inertia 0.6–0.8, bounce 300–400`. Stojące: `stiffness 220+, damping 14+, bounce < 100` |
| `contacts: [[x, y], …]` | punkty, którymi część może dotknąć ziemi (podeszwy łap, brzuch, zad, grzbiet, podbródek). Renderer sam stawia psa na najniższym punkcie, więc ta sama animacja pasuje do krótkich i długich łap |
| `size: [w, h]` | rozmiar grafiki; zwykle niepotrzebny (odczytywany z pliku) |

| Pozostałe pola rig | Opis |
|---|---|
| `anchors.neck` | obroża/bandana: `{ part: 'neck', at, rotation, width, z }`; `width` = grubość szyi w px (do niej skaluje się obroża) |
| `anchors.head` | czapka, kokardka: `{ part: 'head', at, rotation, width, z }` |
| `anchors.mouth` | piłka, patyk, kość w pysku: `{ part: 'head', at, rotation, z }` |
| `landmarks.<nazwa>` | `{ part, at }`: `head` (środek głowy, strefa głaskania), `headTop` (serduszka, „Zzz”), `back`, `belly`, `tail`, `mouth` |
| `clips` | (zaawansowane) własne wersje animacji z `js/render/animations.js` dla tej skórki |
| `disabledStates` | lista stanów, których skórka ma nie używać (zostaną zastąpione) |

### Renderer klatkowy (`frames`)

| Pole | Opis |
|---|---|
| `frameSize: [w, h]` | rozmiar każdej klatki |
| `animations.<stan>.frames` | lista plików klatek, np. `['frames/idle-01.png', 'frames/idle-02.png']` |
| `animations.<stan>.sheet` | zamiast `frames`: `{ src, frameWidth, frameHeight, count, columns }` (arkusz klatek) |
| `animations.<stan>.fps` | tempo odtwarzania |
| `animations.<stan>.loop` | `false` dla animacji jednorazowych (`shake`, `roll`); domyślnie `true` |
| `animations.<stan>.landmarks` | punkty orientacyjne w klatce dla tego stanu (głowa przy jedzeniu jest gdzie indziej niż przy siedzeniu) |
| `animations.<stan>.anchors` | punkty zaczepienia akcesoriów dla tego stanu; `hidden: true` ukrywa akcesorium w tym stanie |
| `landmarks`, `anchors` (główne) | wartości domyślne dla stanów bez własnych |

---

## 7. Proporcje, strefy dotyku i logika

* Logika gry używa **wyłącznie** `proportions` (lub wartości domyślnych z `js/dog/body.js`):
  * miejsce, w którym pies staje przy misce lub piłce: `mouthReach`;
  * prędkość chodu i biegu: pierwiastek z `heightScale`;
  * granice pokoju i logiczna bryła psa: `lengthRatio`.
* Strefy dotyku (`head`, `back`, `belly`, `tail`) to okręgi o promieniu w wysokościach
  w kłębie, położone na **punktach orientacyjnych**, które poruszają się z animacją.
  Głaskanie po głowie trafia więc w głowę także przy leżeniu i jedzeniu. Rozmiar plików PNG
  nie ma znaczenia.
* Każda strefa wywołuje inną psią reakcję: głowa to głaskanie, grzbiet to merdanie,
  brzuch to przewrót na plecy (od poziomu więzi „Znajomy”), a ogon to gonienie własnego ogona.

---

## 8. Najczęstsze problemy przy dopasowaniu

| Objaw w studiu | Co zmienić |
|---|---|
| Pies lewituje albo zapada się w podłogę | `contacts` podeszew łap (punkty muszą leżeć na dole łapy) |
| Łapa obraca się wokół złego punktu | `pivot` tej części (środek stawu) |
| Łapa odczepia się od tułowia | `at` tej części (punkt stawu w grafice rodzica) |
| Ucho lub ogon rusza się w odwrotną stronę | `motion: -1` w tej części |
| Ucho trzęsie się albo jest sztywne | `dynamics.stiffness` / `damping` |
| Ogon merda za słabo lub za mocno | `motion.tail` (cała skórka) albo `parts.tail.motion` |
| Obroża pływa obok szyi | `anchors.neck.at`, `rotation`, `width` |
| Piłka nie jest w pysku | `anchors.mouth.at` |
| Serduszka pojawiają się w złym miejscu | `landmarks.headTop` / `landmarks.head` |
| Głaskanie nie reaguje na głowę | `landmarks.head` albo `zones.head.radius` |
| Pies za duży lub za mały względem pokoju | `proportions.heightScale` (wielkość psa), a nie `fit.shoulderHeightPx` (ten musi odpowiadać grafikom) |
| Pies przy misce stoi za daleko lub za blisko | `proportions.mouthReach` |
| Klatki „skaczą” | ten sam `fit.origin` dla wszystkich klatek, równy rozmiar klatek |
| Grafika rozmyta | wyższa rozdzielczość plików (wysokość w kłębie ≥ 400 px) |
| W panelu „Odrzucone konfiguracje” jest Twoja skórka | przeczytaj listę błędów (np. brak `fit.shoulderHeightPx` albo `pivot`) |

---

## 9. Narzędzia

* `node tools/generate-cartoon-skins.mjs`: odtwarza tymczasowe grafiki SVG beagle'a i husky.
  Gra go nie używa; czyta tylko gotowe pliki, tak jak przeczyta zdjęcia psa.
* `node tools/bake-frames.mjs`: zamienia skórkę warstwową w klatkową (wzór `demo-klatki`).
* `node tests/smoke.mjs`: test całej gry, w tym gwarancji, że podmiana wyglądu nie resetuje postępów.
  Oba ostatnie narzędzia wymagają Playwright (`npm install`), gra sama niczego nie potrzebuje.

Szablon do skopiowania: `assets/skins/_szablon-prawdziwy-pies/` (nie jest wczytywany przez grę,
dopóki nie dopiszesz go do manifestu pod nową nazwą).
