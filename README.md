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

- `game.*` — rozmiar drużyny, progi startu, czasy (no-PvP, countdown, kompas wroga, reconnect).
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

**Admin (`ultrahc.admin`):** `/uhc admin` (panel) · `instances` · `forcestart` · `forceend` ·
`givexp <gracz> <ile>` · `givepd <gracz> <ile>` · `resetseason` · `season [start|end]` ·
`stats <gracz>` · `setstat <gracz> <pole> <wartość>` · `setnpc <id>` · `sethologram <id>` · `reload`.
Wszystkie z tab-completion.

## GUI

Hub (menu główne), Klasy, Sklep, Statystyki, Questy (paski postępu), Topki, Obserwator
(spectate), Panel admina, Zarządzanie instancjami, Zarządzanie sezonem.

## Testy

`mvn test` — testy jednostkowe czystej logiki: krzywa poziomów (tabela ze spec),
progi nagród, matematyka granicy (sanity min29≈544), formatery czasu/liczb.

## Uwagi

- Integracje **Citizens / DecentHolograms / Chunky** są przez reflection (softdepend) —
  plugin działa też bez nich (odpowiednie funkcje po prostu nieaktywne). Warstwę tę
  należy przetestować na serwerze z tymi pluginami.
- Generacja świata 1000×1000 blokuje wątek główny — pula pre-generowanych światów +
  async pre-gen (Chunky) łagodzą to między grami (patrz `DECYZJE.md`).
