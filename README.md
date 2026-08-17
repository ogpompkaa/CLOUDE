# UltraHC

Niestandardowy tryb UHC dla sieci Paper 1.21.x + Velocity. Dwie waluty (XP sklepowe
i PD/gwiazdki) rozdzielone od natywnego expa, 8 klas, 8 receptur, questy, sezony,
topki na hologramach, NPC, oraz architektura sieciowa pod ~1000 graczy w wielu grach.

> Pełne decyzje projektowe i uzasadnienia: [`DECYZJE.md`](DECYZJE.md).
> Mapa modułów i pakietów: [`STRUKTURA.md`](STRUKTURA.md).

## Wymagania

- **Java 21**, **Maven 3.9+**
- **Paper 1.21.x** (serwery lobby i areny)
- **Velocity 3.3.x** (proxy) — dla sieci wieloserwerowej
- **MySQL/MariaDB** — dla produkcji/sieci (dev może działać na SQLite)
- Opcjonalnie na lobby: **Citizens** (NPC) i **DecentHolograms** (hologramy topek)
- Opcjonalnie gdziekolwiek: **PlaceholderAPI** (placeholdery `%ultrahc_*%`), **Chunky** (pre-gen)

## Budowanie

```bash
mvn clean package
```

Artefakty:
- `ultrahc-paper/target/UltraHC-0.1.0-SNAPSHOT.jar` — plugin gry (rola LOBBY albo ARENA)
- `ultrahc-velocity/target/UltraHC-Velocity-0.1.0-SNAPSHOT.jar` — plugin proxy (routing)

## Trzy waluty (nie mylić!)

| System | Wyświetlane | W kodzie | Do czego |
|---|---|---|---|
| Waluta sklepowa | `XP` | `ShopCurrencyManager` / kolumna `credits` | klasy, receptury |
| Punkty progresji | `PD` / „gwiazdki" | `LevelsManager` | poziomy, sezony |
| Natywny exp MC | — | nietykany | tylko receptura Zaklinacz |

---

## Wariant A — pojedynczy serwer (dev / testy)

Najszybszy sposób sprawdzenia mechanik gry.
> Gotowy przewodnik krok-po-kroku na pierwszy test solo: [`docs/LOKALNY.md`](docs/LOKALNY.md).

1. Wgraj `UltraHC-*.jar` do `plugins/` na serwerze Paper 1.21.x (Java 21).
2. Wystartuj serwer — powstaną `plugins/UltraHC/{config.yml, messages.yml, ultrahc.db}`.
3. W `config.yml` ustaw `server.role: ARENA` i (na czas testów) obniż progi:
   ```yaml
   game:
     min-players-to-countdown: 2
     countdown-seconds: 10
     no-pvp-seconds: 30
   ```
4. Restart. Wejdź 2 graczami (lub altem), `/uhc join`. Po odliczaniu ruszy gra.
5. Przydatne komendy: `/uhc gameinfo`, `/uhc forcestart`, `/uhc forceend`,
   `/uhc givexp <gracz> 30000`, `/uhc classes`, `/uhc shop`, `/uhc quests`.

`storage.type: SQLITE` (domyślnie) wystarcza dla jednego serwera.

---

## Wariant B — sieć (produkcja, do ~1000 graczy)

Architektura: **Velocity** (proxy) → **1 lobby** + **N aren** (1 arena-serwer = 1 gra
= 1 świat 1000×1000). Skalowanie „w bok": dokładasz kolejne arena-serwery.
Uzasadnienie w [`DECYZJE.md`](DECYZJE.md#1-architektura-sieci).

> **Gotowy zestaw startowy** (przykładowe `velocity.toml`, `server.properties`, skrypty)
> w [`examples/`](examples), a przewodnik krok-po-kroku w [`docs/SIEC.md`](docs/SIEC.md).

### 1. Baza danych (MySQL) — WYMAGANA w sieci

SQLite to jeden plik i nie działa między procesami. Na **wszystkich** serwerach
(lobby + areny) ustaw w `config.yml`:

```yaml
storage:
  type: MYSQL
  mysql:
    host: "10.0.0.5"
    port: 3306
    database: "ultrahc"
    user: "ultrahc"
    password: "..."
    pool-size: 10
```

### 2. Velocity

- W `velocity.toml` zarejestruj serwery: `lobby`, `arena-1`, `arena-2`, ... `arena-10`.
- Wgraj `UltraHC-Velocity-*.jar` do `plugins/`. Po pierwszym starcie ustaw
  `plugins/ultrahc/registry.properties`:
  ```properties
  jdbc-url=jdbc:mysql://10.0.0.5:3306/ultrahc
  user=ultrahc
  password=...
  team-size=1
  instance-stale-seconds=10
  ```
- Komenda `/play` na proxy routuje gracza do dołączalnej areny wg rejestru.

### 3. Serwery aren

Na **każdym** arena-serwerze w `config.yml`:
```yaml
server:
  role: ARENA
  instance-name: "arena-1"   # MUSI pasować do nazwy w velocity.toml
```
Każdy heartbeatuje swój stan do tabeli `instances`; lobby i proxy z niej czytają.

**Poczekalnia areny.** Po transferze gracz ląduje w **trwałym świecie-poczekalni**
areny (domyślnie główny świat serwera; `arena.lobby.world`) — tam są hologramy topek
i NPC, gracze czekają na zebranie się grupy. Po odliczaniu plugin przenosi ich do
**świeżo wygenerowanego świata meczu**, a po grze — z powrotem do poczekalni. Ustaw
spawn poczekalni stojąc w miejscu: `/uhc setlobbyspawn`. Wizualia (`/uhc setnpc`,
`/uhc sethologram`) ustawiasz w świecie poczekalni tak samo jak w hubie.

### 4. Lobby

```yaml
server:
  role: LOBBY
```
- (Opcjonalnie) wgraj **Citizens** i **DecentHolograms**.
- Ustaw NPC i hologramy in-game (stojąc w miejscu):
  - `/uhc setnpc mietek` · `/uhc setnpc krzysiu` · `/uhc setnpc sklepikarz`
  - `/uhc sethologram kills` · `/uhc sethologram wins` · `/uhc sethologram level`
- Gracz w lobby: **PPM kompasem** → GUI wyboru areny → transfer na arena-serwer.

---

## Konfiguracja — kluczowe sekcje `config.yml`

Cały balans jest w `config.yml` (nic nie jest zahardkodowane). Najważniejsze:

- `game.team-size` — tryb areny: 1=SOLO, 2=DUO, 3=TRIO, 4=SQUAD. `party.*` — party (max, wygasanie).
  W sieci: różne areny z różnym `team-size`; lobby pokazuje party tylko areny, w które się zmieszczą.
- `game.*` — progi startu, czasy (no-PvP, countdown, kompas wroga, reconnect), killstreak, niskie HP,
  bezpieczny rozrzut (`spawn-attempts`), serca pod nickiem (`show-health-below-name`).
- `world.rules.*` — **reguły UHC per-świat**: `natural-regeneration: false` (rdzeń trybu),
  `difficulty: HARD`, brak cyklu pogody/phantomów, `keep-inventory: false`.
- `effects.elimination.*` — piorun + słup cząstek + dźwięk w miejscu eliminacji.
- `border.*` + `arena-showdown.*` — kurczenie 3-fazowe i arenka (sudden-death).
- `rewards.*` — nagrody XP/PD za czas/kille/wygraną (progi ze spec).
- `levels.*` — krzywa poziomów (override 0–2 + wzór LINEAR/GEOMETRIC).
- `drops.*`, `heads.*` — szanse dropów i efekty główek.
- `classes.*`, `shop.recipes.*` — ceny i parametry umiejętności/receptur.
- `quests.definitions.*`, `season.*` — questy i nagrody sezonowe.
- `effects.*` — dźwięki (klucze Minecraft) i cząstki (enum) dla zdarzeń.
- `enchanter.disable-vanilla-table` — wyłącza zwykły stół do zaklęć (wymusza recepturę Zaklinacza).
- `world.pregen.*` — async pre-generacja mapy przez Chunky.
- `border.warning-blocks` / `warning-seconds` — czerwony ekran przy kurczeniu.

Wszystkie komunikaty do graczy i etykiety scoreboardu: `messages.yml` (PL) — nic
nie jest zahardkodowane w kodzie.

## Komendy

**Gracz:** `/uhc help` · `menu` (Hub) · `classes` / `class [id]` · `buyclass <id>` ·
`shop` / `buyrecipe <id>` · `quests` · `balance` · `join` / `leave` · `spectate` (po śmierci).

**Party:** `/party` (GUI) · `invite <nick>` · `accept` / `deny` · `leave` · `kick <nick>` ·
`disband` · `list` · `/pc <wiadomość>` (czat party).

**Admin (`ultrahc.admin`):** `/uhc admin` (panel) · `instances` · `forcestart` · `forceend` ·
`givexp <gracz> <ile>` · `givepd <gracz> <ile>` · `resetseason` · `season [start|end]` ·
`stats <gracz>` · `setstat <gracz> <pole> <wartość>` · `setnpc <id>` · `sethologram <id>` · `reload`.
Wszystkie z tab-completion.

## GUI

Hub (menu główne), Klasy, Sklep, Statystyki, Questy (paski postępu), Topki, Obserwator
(spectate), Panel admina, Zarządzanie instancjami, Zarządzanie sezonem.

## Rangi serwerowe

Rangi **OWNER / ADMIN / MOD / HELPER / SVIP / VIP / GRACZ** (prefiks na czacie, TAB i nad
głową) oparte na uprawnieniach — konfigurowalne w `config.yml` (`ranks.groups`: prefiks,
kolor nicku, waga, węzeł uprawnienia). Gracz dostaje rangę o najwyższej wadze, do której
ma uprawnienie; **GRACZ** to domyślny fallback. Nadawaj np. LuckPermsem:
```
/lp user <gracz> permission set ultrahc.rank.vip true
```
System jest niezależny od poziomu „gwiazdki" — pełny prefiks łączy rangę serwerową
z prefiksem poziomu/podium.

## PlaceholderAPI

Gdy obecne PlaceholderAPI, plugin rejestruje ekspansję `ultrahc`:
`%ultrahc_level%`, `%ultrahc_xp%`, `%ultrahc_pd%`, `%ultrahc_pd_required%`,
`%ultrahc_kills%`, `%ultrahc_wins%`, `%ultrahc_class%`, `%ultrahc_star%`,
`%ultrahc_rank%`, `%ultrahc_party_size%`, `%ultrahc_party_leader%`.
Brak PlaceholderAPI = placeholdery po prostu nieaktywne (patrz [`docs/SIEC.md`](docs/SIEC.md#7-placeholdery-placeholderapi)).

## Testy

`mvn test` — 37 testów jednostkowych czystej logiki (bez serwera): krzywa poziomów
(tabela ze spec + wielokrotny awans), progi nagród, matematyka granicy
(sanity min29≈544), formatery czasu/liczb, warstwa DAO na tymczasowym SQLite
(profil, topki, reset sezonu, questy, party), oraz reguły party (zaproszenia,
akceptacja, leave/kick/disband, limity, wygasanie zaproszeń).

## Uwagi

- Integracje **Citizens / DecentHolograms / Chunky** są przez reflection (softdepend) —
  plugin działa też bez nich (odpowiednie funkcje po prostu nieaktywne). Warstwę tę
  należy przetestować na serwerze z tymi pluginami.
- Generacja świata 1000×1000 blokuje wątek główny — pula pre-generowanych światów +
  async pre-gen (Chunky) łagodzą to między grami (patrz `DECYZJE.md`).
