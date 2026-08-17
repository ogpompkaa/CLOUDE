# DECYZJE — UltraHC (custom UHC dla Paper 1.21.x)

Dokument decyzyjny do zatwierdzenia **przed** kodowaniem. Wszystkie liczby to
propozycje wartości startowych do `config.yml` — nic nie jest zahardkodowane.
Po Twoim „ok" implementuję system po systemie.

---

## 0. Trzy waluty — jasne rozdzielenie (najważniejsza pułapka)

W kodzie istnieją **trzy całkowicie odrębne systemy**, nigdy nie mieszane:

| System | Spec nazywa | Nazwa w kodzie | Etykieta w messages.yml | Do czego |
|---|---|---|---|---|
| Waluta sklepowa | „XP" | `Credits` / `CurrencyManager` (`EconomyManager`) | konfig. `Kredyty` (scoreboard label `Xp:`) | kupno klas i receptur |
| Punkty progresji | „PD" | `ProgressPoints` / `LevelsManager` | `PD`, poziomy = „gwiazdki" | poziomy gwiazdek, sezony |
| Natywny exp MC | „doświadczenie" | *nietykane* — tylko odczyt w recepturze `Zaklinacz` | — | vanilla enchanting |

**Nazwa waluty sklepowej (DECYZJA: „XP"):** wyświetlana jako **`XP`**. W kodzie
system pozostaje **osobny i jednoznacznie nazwany** (`ShopCurrencyManager`, pole
`shopXp`/`credits`, kolumna DB `credits`), żeby nigdy nie mylić go z natywnym
expem MC. Nazwa wyświetlana = jedna stała `ShopCurrencyManager.DISPLAY_NAME =
"XP"` + klucz `messages.yml → currency.name`. Scoreboard: `scoreboard.currency_label`
(domyślnie `Xp:`). Zmiana nazwy w przyszłości = jedna edycja stałej.

> Uwaga: to jedyne miejsce, gdzie „Xp" na ekranie ≠ waluta sklepowa w kodzie.
> W bazie danych kolumna nazywa się `credits`, nigdy `xp`.

---

## 1. Architektura sieci (1000 graczy, 10 świeżych światów naraz)

### Założenie bazowe (zgodnie z Twoim faktem)
Jeden Paper **nie** utrzyma 1000 graczy ani 10 generowanych światów 1000×1000.
Rozdzielamy obciążenie na wiele JVM za proxy.

### Topologia
```
                        ┌─────────────┐
   gracze ───TCP───►    │  Velocity   │  (proxy, routing, transfery)
                        └──────┬──────┘
              ┌────────────────┼───────────────────────────┐
              ▼                ▼                            ▼
        ┌───────────┐   ┌───────────┐                ┌───────────┐
        │  LOBBY-1  │   │  ARENA-1  │   ...  x10+    │  ARENA-N  │
        │  (Paper)  │   │  (Paper)  │                │  (Paper)  │
        └─────┬─────┘   └─────┬─────┘                └─────┬─────┘
              │               │                            │
              └───────────────┴───────── MySQL ────────────┘
                       (opc. Redis: pub/sub + rejestr instancji)
```

### Kluczowa decyzja skalowania: **1 serwer areny = 1 aktywna gra**
- 1 arena-serwer hostuje **jedną** grę UHC (100 graczy, 1 świat 1000×1000) na
  jednym wątku głównym → realny ~20 TPS na przyzwoitym sprzęcie.
- 10 równoległych gier = 10 arena-serwerów + lobby + proxy (~12 JVM).
- „Dodawanie kolejnych gier" = uruchomienie kolejnego arena-serwera; rejestruje
  się sam w proxy (Velocity `try`/dynamic register) i w rejestrze instancji.
- **Nigdy nie generujemy 10 światów na jednej maszynie** — każdy arena-serwer
  obsługuje cykl życia dokładnie jednego świata naraz, więc „10 światów naraz"
  jest fizycznie rozłożone na 10 procesów/maszyn.

> Alternatywę (wiele gier na jednym Paperze, izolacja przez multiworld) odrzucam:
> jeden wątek główny + 10× ticking świata + 10× generacja = zjazd TPS. Podział
> per-proces jest jedynym realnym sposobem na 1000 graczy przy sensownym TPS.

### Koszt generacji świata → pre-generacja z puli „ciepłych" światów
Generacja 1000×1000 jest droga. Dlatego:
- Każdy arena-serwer utrzymuje **pulę gotowych światów** (`world.pool_size`,
  domyślnie 1 gotowy + 1 w trakcie generacji).
- Świat następnej gry jest **pre-generowany asynchronicznie** (własny
  chunk-pregen w tempie limitowanym, żeby nie dławić TPS aktywnej gry, albo
  Chunky jako opcjonalny helper) **w trakcie** trwania bieżącej gry.
- Po grze: świat jest **unload + skasowany z dysku + regen** nowego losowego
  seeda w tle. Gra nigdy nie czeka na generację (bierze gotowy z puli).
- Reżim dyskowy: świat areny na tmpfs/SSD, kasowanie natychmiast po `GameEnd`.

### Koordynacja między serwerami (rejestr instancji, matchmaking, transfer)
- **Wymagana infra minimalna:** Velocity + Paper + **MySQL**. Rejestr instancji
  to tabela `instances` (stan: `WAITING/COUNTDOWN/RUNNING/ENDING`, liczba graczy,
  rozmiar drużyny, tryb, heartbeat). Lobby odpytuje ją co `matchmaking.poll_ms`
  (domyślnie 1500 ms) i buduje GUI wyboru areny. Transfer gracza = Velocity
  plugin-message `Connect` + wpis do kolejki instancji.
- **Opcjonalny upgrade:** Redis pub/sub zamiast pollingu (niższa latencja,
  natychmiastowe eventy stanu). Za flagą `network.redis.enabled=false`.
  Nie dokładam Redisa domyślnie — nie jest konieczny do działania.

### SQLite vs MySQL — rozstrzygnięcie
- **DEV / pojedynczy serwer:** SQLite (`storage.type: SQLITE`) — zero infra.
- **PRODUKCJA / sieć:** MySQL/MariaDB (`storage.type: MYSQL`) — bo SQLite to
  jeden plik, nie znosi zapisów z wielu serwerów. Warstwa DAO (`PlayerDao`,
  `SeasonDao`, ...) jest identyczna dla obu; różni się tylko sterownik i SQL
  dialekt. Zmiana = jedna flaga w config, **zero zmian w logice**.
- Zapisy per-gracz robi tylko serwer, na którym gracz aktualnie jest (brak
  kontencji). Dane per-gra są wyłącznie w pamięci arena-serwera (izolacja
  per-instancja).

### Podział artefaktów (deploy)
- **Jeden plugin Paper** z rolą `server.role: LOBBY | ARENA` w config — te same
  klasy, rola decyduje które managery wstają (brak duplikacji kodu).
- **Jeden plugin Velocity** (transfery, routing, glue).
- Build: Maven **multi-module** (`parent → common → paper → velocity`).

---

## 2. Kurczenie granicy mapy (DECYZJA: 3 fazy + arenka)

Punkt odniesienia ze zrzutu: ~29 min → ~549 ⇒ ~24 bloki/min. Trzy fazy:

| Faza | Kiedy | Zachowanie |
|---|---|---|
| **1 — normalna** | min 10 → 30 | kurczenie 24 bloki/min |
| **2 — przyspieszenie** | min 30 → 45 | kurczenie `blocks_per_min_fast` (30/min) |
| **3 — arenka (sudden-death)** | min 45 | teleport wszystkich żywych na małą arenkę |

```yaml
border:
  start: 1000
  shrink_start_min: 10
  blocks_per_min: 24            # faza 1
  accelerate_min: 30           # start fazy 2
  blocks_per_min_fast: 30      # faza 2
  center: spawn
arena_showdown:
  teleport_min: 45             # start fazy 3 (sudden-death)
  mode: BORDER_CLAMP           # BORDER_CLAMP | SCHEMATIC
  size: 40                     # rozmiar arenki (średnica bordera)
  collapse_to_min: 60          # arenka zaciska się do 0 do tej minuty (dobija remis)
```

**Przebieg liczb:** min 30 → 1000 − 24·20 = **520**; min 45 → 520 − 30·15 = **70**
(tuż przed teleportem). O 45. min wszyscy żywi lądują na arence (`BORDER_CLAMP`:
recenter bordera na spawn + twardy zacisk do `size=40` + TP graczy do środka).
Arenka dalej się zaciska do 0 (`collapse_to_min=60`), więc sudden-death **zawsze
się rozstrzyga** — ostatnia żywa drużyna wygrywa. Sanity-check ze zrzutem:
min 29 → 1000 − 24·(29−10) = **544** ≈ 549 ✔.

Implementacja faz przez `WorldBorder.setSize(target, sekundy)`; scoreboard
pokazuje bieżący rozmiar z jednym miejscem po przecinku, separator „," (PL):
`549,2`. `SCHEMATIC` (wklejenie gotowej budowli arenki) — opcja na później.

---

## 3. Krzywa poziomów (PD → gwiazdki)

Spec: 750, 2800, 4500, 6500, 8500, potem **+2000 liniowo**. Rozbieram to na
**tablicę override + wzór parametryzowany** (przełącznik liniowy/geometryczny):

```yaml
levels:
  mode: LINEAR            # LINEAR | GEOMETRIC
  # wzór dla poziomów spoza tablicy override:
  linear:
    slope: 2000           # przyrost na poziom
    intercept: 500        # required(n) = slope*n + intercept  (n>=3 → 6500,8500,...)
  geometric:
    base: 750
    growth: 1.35          # required(n) = base * growth^n
  overrides:              # dokładne progi ze spec, nadpisują wzór:
    0: 750
    1: 2800
    2: 4500
```
- `LINEAR` z `slope=2000, intercept=500` odtwarza spec dla n≥3 (6500, 8500,
  10500, ...), a override 0–2 daje dokładnie 750/2800/4500. Zgadza się z tabelą
  do „33 gwiazdka = 66 500" (2000·33+500 = 66 500 ✔).
- Przełączenie na `GEOMETRIC` = rosnące wykładniczo progi bez rekompilacji.

---

## 4. Zachowania graniczne

### Śmierć gracza
- Gracz → tryb **spectator** (zostaje na świecie, obserwuje), znika z „Zywi".
- **Zabójca** = ostatni wrogi gracz, który zadał obrażenia w oknie combat-tag
  `combat.tag_seconds` (domyślnie 10 s). Jeśli śmierć środowiskowa (granica,
  upadek, lawa) **z** aktywnym tagiem → kill idzie do taggera; bez taggera →
  brak zabójcy (nagrody za kill nikt nie dostaje).
- Nagrody za kill (Kredyty + PD) i **główka** (konsumowalna) lecą do zabójcy.
- Drużyna **wyeliminowana**, gdy **wszyscy** jej członkowie martwi.
- Czas przeżycia liczony **per-gra do momentu śmierci** — nagrody czasowe
  naliczane inkrementalnie w trakcie gry, więc są już zaksięgowane.

### Wyjście gracza w trakcie gry (combat-log / disconnect)
- Okno powrotu `game.reconnect_grace_seconds` (domyślnie 180 s): gracz „offline
  ale żywy", ciało/pozycja zamrożone. Powrót → wraca do gry.
- Brak powrotu w oknie → **eliminacja** (jak śmierć; jeśli był combat-tagged →
  kill dla taggera = kara za combat-log, inaczej brak zabójcy).
- Zarobione dotąd nagrody pozostają (były księgowane na bieżąco).

### Wygrana
- **Wygrywa ostatnia żywa drużyna.** Każdy jej członek: nagroda za wygraną
  (Kredyty + PD), `wins += 1`.
- Solo = drużyna 1-osobowa (ta sama ścieżka kodu).

### Remis / wymuszony koniec (DECYZJA: sudden-death)
- **Nie ma współdzielonej wygranej.** Przy >1 żywej drużynie rozstrzyga
  **sudden-death na arence**: o 45. min wszyscy żywi są teleportowani na arenkę,
  która zaciska się do 0 (`arena_showdown.collapse_to_min`). Zamykająca się
  granica dobija pozostałych → zostaje jedna drużyna = wygrany.
- Twardy bezpiecznik `game.hard_time_cap_min` (domyślnie 90) na wypadek patologii
  — jeśli mimo wszystko >1 drużyna żywa w tym momencie, o wygranej decyduje
  kolejno: **więcej killi → więcej pozostałych serc** (czysty tiebreak, bez
  dzielenia nagrody).

---

## 5. Klasy zależne od biomu (Yeti, Wędkarz)

Umiejętności są **wyzwalane terenem**, nie gwarantowane biomem:
- **Wędkarz** — woda + łowienie. Woda w losowym 1000×1000 jest praktycznie
  pewna → działa naturalnie, bez wymuszeń.
- **Yeti** — Speed na śniegu/lodzie. Zimny biom może się nie wygenerować.

Rozwiązanie (konfig):
```yaml
world:
  generation:
    require_biomes:
      enabled: true                # gwarancja biomów przez wybór seeda
      biomes: [SNOWY_PLAINS, SNOWY_TAIGA, ICE_SPIKES, FROZEN_RIVER]
      search_radius: 500           # próbkowanie wokół spawn podczas pre-genu
      max_seed_attempts: 40        # ile seedów przejrzeć zanim fallback
```
- **Primary:** przy pre-generacji świata próbkujemy biomy w promieniu; jeśli brak
  wymaganego zimnego biomu → odrzuć seed, generuj następny (do `max_seed_attempts`).
  Bezpieczne, bo pre-gen jest w tle z puli.
- **Fallback (gdy `enabled=false` lub wyczerpano próby):** pasywka Yeti działa na
  **dowolnym bloku śniegu/lodu** (warstwy śniegu z pogody, packed/blue ice
  postawiony w grze) — nigdy nie „martwa" klasa, tylko rzadziej użyteczna.

---

## 6. Zależności — rekomendacje (wybrane, uzasadnione)

### NPC (Mietek / Krzysiu / Sklepikarz) → **Citizens** (DECYZJA)
- Impl `CitizensNpcManager` za interfejsem `NpcManager`. Skiny w modelu gracza,
  trwałość NPC, kliknięcie → GUI (`NPCRightClickEvent`). NPC tylko w lobby.
- `plugin.yml: softdepend: [Citizens]`; jeśli Citizens brak → log ostrzeżenia po
  PL i graceful-skip rejestracji NPC (reszta pluginu działa).
- Interfejs zachowany, więc ewentualny powrót do wariantu bez zależności = swap.

### Hologramy topek → **DecentHolograms** (DECYZJA)
- Impl `DecentHologramsManager` za interfejsem `HologramManager`. Trzy holo w
  lobby (Top 10 Kille / Wygrane / Poziomy), odświeżane z `LeaderboardsManager`.
- `plugin.yml: softdepend: [DecentHolograms]`; brak → log PL + skip holo.
- Edycja pozycji przez GUI admina (spec wymaga) mapowana na API DecentHolograms.

---

## 7. Proponowane wartości nagród (z config) — 1:1 ze spec

```yaml
rewards:
  currency:                 # Kredyty (waluta sklepowa)
    per_kill: 100
    per_win: 1000
    time:                   # co interval_min (losowo 1-2 min) wg progu:
      interval_min: [1, 2]
      tiers:
        - {to_min: 5,   amount: 4.2}
        - {to_min: 15,  amount: 5.8}
        - {to_min: 30,  amount: 7.7}
        - {to_min: 999, amount: 9.3}   # powyżej 30 min
  progress:                 # PD (poziomy gwiazdek)
    per_kill: 30
    per_win: 250
    time:
      interval_min: 2
      tiers:
        - {to_min: 5,   amount: 3.06}
        - {to_min: 15,  amount: 4.24}
        - {to_min: 30,  amount: 5.62}
        - {to_min: 999, amount: 6.8}
```

Dropy ze stone / liści, główki, ceny klas i receptur — wszystko trafia do
`config.yml` z liczbami dokładnie ze spec (wypiszę w sekcji Drops/Shop przy
implementacji tych systemów, żeby ten dokument nie puchł).

---

## 8. Start gry i timeline (z config)

```yaml
game:
  team_size: 1                     # 1=SOLO, 2=DUO, 3=TRIO, 4=SQUAD (parametr)
  min_players_to_countdown: 30     # 30. gracz uruchamia odliczanie
  max_players: 100
  countdown_seconds: 180           # 3 min po zebraniu 30
  no_pvp_seconds: 600              # 10 min bez PvP
  enemy_compass_unlock_min: 18     # kompas widzi wroga po 18 min
  reconnect_grace_seconds: 180
  hard_time_cap_min: 90
  tie_shared_win: true
combat:
  tag_seconds: 10
```

Timeline: zbieranie do 30 → 30. gracz → 3 min countdown → **START** → 10 min bez
PvP → (18 min: kompas wroga) → (10 min: start kurczenia granicy) → ... → ostatnia
drużyna / cap.

---

## 9. Sezony (bez auto-daty, GUI admina)

Admin GUI: start sezonu, koniec sezonu (wypłata Top 3 wg poziomu → nagrody
Kredyty/rangi/prefix `#1 UHC`…), **reset poziomów i receptur**, **klasy
zostają**. Wszystko manualne — brak triggerów czasowych.

---

## Decyzje ZATWIERDZONE (2026-08-15)

1. **Waluta sklepowa = „XP"** (wyświetlana), w kodzie osobny `ShopCurrencyManager`.
2. **Granica 3-fazowa:** 24/min (10–30), przyspieszenie 30/min (30–45), o 45 min
   **teleport na arenkę** zaciskaną do 0 → sudden-death.
3. **Citizens** (NPC) + **DecentHolograms** (topki), za interfejsami.
4. **SQLite teraz**, MySQL później — ta sama warstwa DAO.
5. **1 arena-serwer = 1 gra** + pula pre-generowanych światów — przyjęte.
6. Remis → **sudden-death** (arenka), bez współdzielonej wygranej.
