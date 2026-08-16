# Stawianie sieci UltraHC — krok po kroku

Ten przewodnik prowadzi od zera do działającej sieci: **Velocity (proxy) → lobby → areny**.
Pliki startowe znajdziesz w katalogu [`examples/`](../examples).

Architektura (patrz też `DECYZJE.md` i `STRUKTURA.md`):

```
                        gracz :25565
                            │
                     ┌──────▼──────┐
                     │  Velocity   │  proxy (routing, jeden adres wejścia)
                     └──┬───────┬──┘
              ┌─────────┘       └─────────┐
        ┌─────▼─────┐               ┌─────▼─────┐
        │   lobby   │  role: LOBBY  │  arena-N  │  role: ARENA (×N, do 10 gier)
        │ NPC, GUI, │               │ gra UHC,  │
        │  topki    │               │ świat 1k× │
        └───────────┘               └───────────┘
                     wspólna baza (SQLite lokalnie / MySQL w sieci)
```

Jeden plugin **UltraHC** ląduje na lobby i na każdej arenie — rolę wybiera `config.yml`
(`server.role: LOBBY | ARENA`). W sieci wieloserwerowej użyj **MySQL**, aby wszystkie
instancje widziały te same profile i rejestr gier.

---

## 0. Wymagania

- **Java 21** (`java -version` → 21).
- Paczka serwera **Paper 1.21.x** (`paper.jar`) — https://papermc.io/downloads/paper
- **Velocity** (`velocity.jar`) — https://papermc.io/downloads/velocity
- Zbudowany plugin: `mvn -B clean package` → `ultrahc-paper/target/UltraHC-*.jar`
- (opcjonalnie) Wtyczki miękkich zależności na **lobby**: Citizens, DecentHolograms.
  Na dowolnym serwerze: PlaceholderAPI, Chunky. Brak którejkolwiek = plugin startuje
  dalej, po prostu pomija ten fragment.

---

## 1. Zbuduj plugin

```bash
mvn -B clean package
# wynik: ultrahc-paper/target/UltraHC-0.1.0-SNAPSHOT.jar
```

## 2. Rozłóż katalogi

```
siec/
├── velocity/          # z examples/velocity/
│   ├── velocity.jar
│   ├── velocity.toml
│   └── forwarding.secret
├── lobby/
│   ├── paper.jar
│   ├── server.properties          # z examples/lobby/
│   └── plugins/UltraHC-*.jar
└── arena-1/
    ├── paper.jar
    ├── server.properties          # z examples/arena/
    └── plugins/UltraHC-*.jar
```

Areny 2, 3, … to kopie `arena-1` ze zmienionym `server-port` (30102, 30103, …) i wpisem
w `velocity.toml` (`arena-2`, `arena-3`, …).

## 3. Ustaw wspólny sekret forwardingu

Modern forwarding = proxy autoryzuje graczy, serwery mu ufają po współdzielonym sekrecie.

1. Wpisz losowy ciąg (min. 32 znaki) do `velocity/forwarding.secret`.
2. Ten **sam** sekret wklej do `config/paper-global.yml` każdego serwera Paper —
   sekcja `proxies.velocity` (wzór: [`examples/paper-global-proxies.yml`](../examples/paper-global-proxies.yml)).
   Plik `paper-global.yml` powstaje po pierwszym uruchomieniu serwera; scal tylko sekcję
   `proxies`, nie nadpisuj całości.
3. W `server.properties` każdego Paper musi być `online-mode=false` (autoryzuje proxy).
   W `velocity.toml` — `online-mode = true`.

## 4. Skonfiguruj plugin (config.yml)

Po pierwszym uruchomieniu serwera z pluginem powstanie `plugins/UltraHC/config.yml`.

**Lobby** (`plugins/UltraHC/config.yml`):
```yaml
server:
  role: LOBBY
  instance-name: "lobby"
```

**Każda arena**:
```yaml
server:
  role: ARENA
  instance-name: "arena-1"   # arena-2, arena-3, ... — MUSI zgadzać się z velocity.toml
```

**Baza — sieć wieloserwerowa (MySQL):** na **wszystkich** serwerach ten sam blok:
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
Pojedyncza maszyna testowa może zostać na `type: SQLITE` (każdy serwer własny plik) —
ale wtedy lobby i arena nie współdzielą profili. Do realnych testów sieci użyj MySQL.

Nazwa serwera areny w `instance-name` musi być **identyczna** jak klucz w `velocity.toml`
(`[servers]`), bo po tej nazwie proxy przenosi gracza na wybraną grę.

## 5. Odpal

```bash
# z examples/ (nadaj prawa: chmod +x *.sh)
./start-velocity.sh                    # w katalogu velocity/
./start-paper.sh siec/lobby 3G
./start-paper.sh siec/arena-1 4G
./start-paper.sh siec/arena-2 4G
```

Łącz się na **adres proxy** (`localhost:25565`) — trafisz na lobby. Kompasem/`/play`
otwierasz wybór areny; plugin przydziela grę i przenosi Cię na arenę.

---

## 6. Pierwsza konfiguracja na żywo (lobby)

Ustaw punkty lobby (wykonaj stojąc w docelowym miejscu):

```
/setworldspawn             # spawn lobby (komenda vanilla — gdzie ląduje gracz)
/uhc setnpc mietek         # NPC klas/info
/uhc setnpc krzysiu        # NPC questów
/uhc setnpc sklepikarz     # NPC sklepu receptur
/uhc sethologram kills     # hologram topki zabójstw (też: wins, level)
/uhc sethologram wins
/uhc sethologram level
```

Panel admina i zarządzanie instancjami:

```
/uhc admin                 # GUI admina (sezon, instancje, reload)
/uhc reload                # przeładowanie config.yml / messages.yml
```

Tryb gry (SOLO/DUO/TRIO/SQUAD) ustawiasz na arenach przez `game.team-size`
(1/2/3/4) w ich `config.yml`. Party (`/party`) trzyma graczy w jednej drużynie.

---

## 7. Placeholdery (PlaceholderAPI)

Jeśli na serwerze jest **PlaceholderAPI**, plugin rejestruje ekspansję `ultrahc`.
Dostępne:

| Placeholder                | Znaczenie                                  |
|----------------------------|--------------------------------------------|
| `%ultrahc_level%`          | poziom (gwiazdka)                          |
| `%ultrahc_xp%`             | waluta sklepowa (spec: „XP")               |
| `%ultrahc_pd%`             | PD w bieżącym poziomie                      |
| `%ultrahc_pd_required%`    | PD potrzebne do następnego poziomu         |
| `%ultrahc_kills%`          | zabójstwa (dożywotnio)                     |
| `%ultrahc_wins%`           | wygrane                                     |
| `%ultrahc_class%`          | wybrana klasa                              |
| `%ultrahc_star%`           | etykieta poziomu, np. „3 gwiazdka"         |
| `%ultrahc_rank%`           | ranga/prefiks bez kolorów                  |
| `%ultrahc_party_size%`     | liczebność party (0 = brak)                |
| `%ultrahc_party_leader%`   | `true/false` — czy gracz jest liderem      |

Użyjesz ich w dowolnym pluginie wspierającym PlaceholderAPI (TAB, holo, scoreboard-y itd.).

---

## 8. Najczęstsze problemy

- **„If you wish to use IP forwarding…" / kick przy wejściu** — niezgodny sekret
  forwardingu albo `online-mode` po złej stronie. Sprawdź krok 3.
- **Areny się nie pokazują w wyborze** — `instance-name` areny ≠ klucz w `velocity.toml`,
  albo serwery nie mają wspólnej bazy (MySQL). Sprawdź `network.instance-stale-seconds`.
- **Profile się „resetują" między lobby a areną** — obie strony na SQLite (osobne pliki).
  Przełącz na MySQL (krok 4).
- **Brak NPC/hologramów** — brak Citizens/DecentHolograms na lobby (to opcjonalne),
  albo nieustawione pozycje (`/uhc setnpc ...`).
