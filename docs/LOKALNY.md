# Szybki test lokalny (jeden serwer) — na wieczór

Cel: w ~15 minut zobaczyć pełny cykl gry solo na własnym kompie. Sieć Velocity
dołożysz później (patrz [`SIEC.md`](SIEC.md)).

## 1. Zdobądź plugin (jar)

**Masz Maven + Java 21:**
```
mvn -B clean package
```
→ `ultrahc-paper/target/UltraHC-0.1.0-SNAPSHOT.jar`

**Nie masz Mavena:** wejdź na GitHub → zakładka **Actions** → ostatni zielony
build → sekcja **Artifacts** → pobierz **UltraHC-paper** (to gotowy jar).

## 2. Serwer Paper

1. Pobierz `paper-1.21.4.jar` z https://papermc.io/downloads/paper do pustego folderu.
2. Odpal raz (`java -jar paper-1.21.4.jar --nogui`), poczekaj aż wygeneruje pliki, zatrzymaj (`stop`).
3. W `eula.txt` ustaw `eula=true`.
4. (opcjonalnie, dla płynności) wrzuć **Chunky** do `plugins/` — async pre-generacja mapy.

## 3. Wgraj plugin i odpal raz

- `UltraHC-*.jar` → `plugins/`
- Odpal serwer, poczekaj aż wstanie, `stop`. Powstanie `plugins/UltraHC/{config.yml, messages.yml}`.

## 4. Ustaw pod szybki test solo

W `plugins/UltraHC/config.yml` zmień:

```yaml
server:
  role: ARENA
game:
  team-size: 1
  min-players-to-countdown: 1     # start juz przy 1 graczu
  countdown-seconds: 10
  no-pvp-seconds: 30              # PvP po 30 s (do testow)
border:
  shrink-start-min: 2             # granica rusza po 2 min
arena-showdown:
  teleport-min: 4                 # arenka po 4 min
```

`storage.type` zostaw `SQLITE`. Poczekalnia = główny świat serwera (domyślnie).

## 5. Graj

1. Odpal serwer, wejdź na `localhost` (wersja klienta **1.21.4**, tryb offline/„crack" działa bo `online-mode=false`).
2. Wylądujesz w **poczekalni** (auto-join). Po 10 s → odliczanie z tytułami → **START** → rozrzut na mapie.
3. Jesteś op, więc na czacie/TAB masz rangę **OWNER** (fallback op→OWNER).

### Komendy do sprawdzania
```
/uhc gameinfo            # stan gry
/uhc forcestart          # wymuś start (gdyby nie ruszył)
/uhc forceend            # zakończ grę
/uhc givexp <ty> 50000   # waluta na klasy/receptury
/uhc classes             # GUI klas
/uhc shop                # GUI receptur
/uhc quests              # questy
/uhc menu                # Hub (GUI)
/party                   # party (test z altem)
```

## 6. Na co patrzeć

- **Poczekalnia:** osobny scoreboard (POCZEKALNIA, tryb, gracze, statystyki), przedmiot „Powrót do huba" (bez proxy nie przeniesie — normalne), pierścień cząstek przy wejściu.
- **Gra:** scoreboard ULTRAHC (bez czerwonych numerów), actionbar z fazą, bossbar, HP pod nickiem, kolory drużyn (z altem), liczby obrażeń przy ciosach, efekt eliminacji (piorun), reguły UHC (brak regenu, HARD).
- **Dropy:** kop kamień → surowce; kop rudę → nic (UHC).
- **PvP:** klikanie bez cooldownu (1.8), brak sweepa.
- **Blokady:** Nether/End portale nieaktywne, skrzynia Endera zablokowana, brak niszczenia bloków w poczekalni.
- **Awans:** `/uhc givepd <ty> 5000` → spirala cząstek + fajerwerk + tytuł.

## 7. Czego NIE zobaczysz bez Velocity
Transferów między serwerami: powrotu do huba z poczekalni, wyboru areny z huba,
cross-server party. To wymaga proxy — konfiguracja w [`SIEC.md`](SIEC.md).

## 8. Coś nie działa?
- Skopiuj log z konsoli (błąd/stacktrace) i podeślij — zdiagnozuję.
- Częste: zła wersja klienta (musi być **1.21.4**), zły `role`, brak `eula=true`.
- Lag przy starcie gry = generacja świata; wrzuć **Chunky** (patrz krok 2.4).
