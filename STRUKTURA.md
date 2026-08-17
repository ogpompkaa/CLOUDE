# STRUKTURA PROJEKTU — UltraHC

Maven multi-module. Komentarze/logi PL, nazwy w kodzie EN. Managery oddzielone
od komend i eventów. Komunikaty w `messages.yml`, balans w `config.yml`.

## Moduły Maven
```
ultrahc/                         (parent pom, packaging=pom)
├── ultrahc-common/              wspólny kod: modele, DAO, config, messages, rejestr instancji
├── ultrahc-paper/               plugin Paper (rola LOBBY albo ARENA wg config)
├── ultrahc-velocity/            plugin Velocity (transfery, routing)
└── pom.xml
```
Stack: Paper API 1.21.x, Java 21, Velocity API 3.x. Zależności: paper-api,
velocity-api, driver JDBC (sqlite + mysql), HikariCP (pool dla MySQL). Bez
Citizens/DecentHolograms (patrz DECYZJE).

## ultrahc-common
```
pl.ultrahc.common
├── model/            Player­Profile, Team, GameInstanceInfo, SeasonInfo, ClassDef, RecipeDef, Quest
├── storage/          Storage (iface), SqliteStorage, MysqlStorage, dao/{PlayerDao,SeasonDao,QuestDao,LeaderboardDao}
├── config/           ConfigManager, MessagesManager  (ładują config.yml / messages.yml)
├── currency/         CurrencyManager.DISPLAY_NAME (stała nazwy waluty sklepowej)
├── instances/        InstanceRegistry (odczyt/zapis tabeli instances; iface + Mysql/Redis impl)
└── util/             Text (kolory/format), TimeUtil (HH:MM:SS), NumberUtil (549,2)
```

## ultrahc-paper — managery (rola decyduje, które wstają)
```
pl.ultrahc.paper
├── UltraHcPlugin            onEnable: wczytaj config/messages, DB, wg role.* startuj managery
├── manager/
│   ├── EconomyManager       Kredyty (waluta sklepowa)            [BOTH]
│   ├── LevelsManager        PD + gwiazdki + krzywa poziomów      [BOTH]
│   ├── ClassesManager       definicje klas, odblokowania, kit    [BOTH]
│   ├── ShopManager          sklep receptur/klas za Kredyty       [LOBBY]
│   ├── RecipeManager        rejestracja receptur + efekty        [ARENA]
│   ├── DropsManager         drop ze stone/liści, per-klasa bonus [ARENA]
│   ├── BorderManager        kurczenie granicy                    [ARENA]
│   ├── QuestsManager        questy dzienne/tygodniowe/stałe      [LOBBY]
│   ├── LeaderboardsManager  Top 10 kille/wygrane/poziomy         [LOBBY]
│   ├── HologramManager      natywne TextDisplay (iface)          [LOBBY]
│   ├── NpcManager           Mietek/Krzysiu/Sklepikarz (iface)    [LOBBY]
│   ├── GameManager          cykl życia gry (WAITING→...→END)     [ARENA]
│   ├── InstanceManager      rejestracja instancji, heartbeat     [BOTH]
│   ├── TeamManager          drużyny (solo=1), kille, sojusznicy  [ARENA]
│   ├── ScoreboardManager    tablica boczna ULTRAHC               [ARENA]
│   ├── WorldManager         pula światów, pre-gen, kasowanie     [ARENA]
│   ├── CompassManager       sojusznik / wróg (po 18 min)         [ARENA]
│   ├── HeadManager          główki konsumowalne + efekty         [ARENA]
│   ├── RewardManager        naliczanie Kredytów/PD (czas/kill/win)[ARENA]
│   └── SeasonManager        start/koniec sezonu, reset, Top 3    [LOBBY]
├── command/
│   ├── admin/  ForceStart, ForceEnd, GiveCurrency, GivePd, ResetSeason,
│   │           EditHologram, PlayerStats, InstanceAdmin
│   └── player/ ClassCmd, ShopCmd, StatsCmd, LeaveCmd
├── listener/  PvpListener, DropsListener, MiningListener, DeathListener,
│              JoinQuitListener, HeadConsumeListener, NpcInteractListener,
│              ClassAbilityListener, CompassUseListener, BorderDamageListener
└── gui/       ArenaSelectGui, ShopGui, ClassGui, QuestGui, SeasonAdminGui,
               InstanceAdminGui, PandoraBoxGui
```

## ultrahc-velocity
```
pl.ultrahc.velocity
├── UltraHcVelocity         rejestracja serwerów, plugin-messaging
├── TransferService         wysyłanie gracza LOBBY↔ARENA
└── InstanceRouter          wybór instancji wg stanu z rejestru
```

## Pliki zasobów
```
ultrahc-paper/src/main/resources/
├── plugin.yml       komendy + permissiony (ultrahc.admin.*, ultrahc.player.*)
├── config.yml       CAŁY balans (border, rewards, drops, levels, ceny, czasy, world, network, storage)
└── messages.yml     WSZYSTKIE komunikaty PL + etykiety scoreboardu
```

## Kolejność implementacji (po Twoim „ok")
1. Szkielet: parent pom, common (config/messages/storage DAO, SQLite), plugin.yml, config.yml, messages.yml, role LOBBY/ARENA.
2. Economy + Levels + baza per-gracz (fundament walut).
3. GameManager + TeamManager + WorldManager (cykl gry, pula światów, start/countdown).
4. Border + Scoreboard (odwzorowanie układu ULTRAHC).
5. Drops + Head + PvP/no-PvP + Compass.
6. Classes (kity + pasywki, w tym Yeti/Wędkarz fallback).
7. Shop + Recipes (Sklepikarz).
8. Leaderboards + Hologramy + NPC (Mietek/Krzysiu).
9. Quests.
10. Season admin + komendy admina + GUI.
11. Velocity: transfery + routing instancji (skala 10 gier).

Po każdym systemie: krótka lista „co i jak przetestować na serwerze".

## Aktualizacja — stan bieżący (po implementacji)

Względem szkicu doszło:

- `ultrahc-common/game/` — czysta, testowana logika bez Bukkita:
  `LevelCurve`, `RewardTiers`, `BorderCurve` (+ testy JUnit w `src/test`).
- `ultrahc-common/instances/` — `InstanceRegistry`, `InstanceInfo` (rejestr sieciowy,
  SQLite/MySQL); `storage/MysqlStorage` (HikariCP).
- `paper/gui/` — pełne GUI: `LobbyMenuGui` (Hub), `ClassGui`, `ShopGui`, `StatsGui`,
  `QuestGui`, `LeaderboardGui`, `SpectateGui`, `AdminGui`, `InstanceAdminGui`,
  `SeasonAdminGui`, `ArenaSelectGui`.
- `paper/hologram/` (`HologramManager`+DecentHolograms) i `paper/npc/`
  (`NpcManager`+Citizens) — integracje przez reflection (softdepend).
- `paper/manager/` — doszły m.in.: `RewardManager`, `ScoreboardService` (bez migotania,
  kolory drużyn), `BossBarService`, `RankService`+`RankFormat`, `CompassManager`,
  `HeadManager`, `AbilityScheduler`, `LeaderboardsManager`, `QuestsManager`,
  `SeasonManager`, `ShopManager`, `RecipeManager`, `InstanceManager`, `ClassesManager`.
- `paper/util/` — `Feedback` (title/dźwięki/cząstki, config-driven), `TimeUtil`,
  `NumberUtil` (+ testy).
- `paper/config/` — `ConfigValidator` (walidacja wartości).
- `paper/listener/` — m.in. `CombatListener` (PvP, śmierć, reconnect+combat-tag),
  `DropsListener`, `ClassAbilityListener`, `HeadListener`, `CompassListener`,
  `CompassLobbyListener`, `ChatListener` (czat z rangą), `DetectorListener`,
  `PandoraListener` (stawiana skrzynka), `ProfileListener`.
