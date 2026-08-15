# CoreKit

A production-grade **foundation** for Paper plugins — the scaffolding a senior
developer stands up before writing a single feature. It is not a game mechanic;
it is the clean, opinionated base you clone features onto.

- **Platform:** Paper `1.21.x` (Java 21)
- **Build:** Gradle (Kotlin DSL) with the Shadow plugin
- **Storage:** HikariCP + SQLite, fully asynchronous
- **UI/Text:** Adventure + MiniMessage, file-based i18n
- **Commands:** Paper's modern Brigadier API (subcommand trees, native tab-completion)

## What's inside

| Concern | Class | Notes |
|---|---|---|
| Early registration | `bootstrap/CoreKitBootstrap` | Paper `PluginBootstrap`; runs before worlds load. |
| Lifecycle wiring | `CoreKitPlugin` | Strict start-up order; storage failure disables the plugin. |
| Configuration | `config/ConfigManager`, `config/Settings` | Reloadable, back-fills new keys, typed snapshot. |
| Localisation | `lang/MessageService` | MiniMessage, bundled-fallback, dotted-key lookups. |
| Storage | `storage/DatabaseManager` | Off-thread executor, WAL, graceful drain on shutdown. |
| Data access | `storage/PlayerProfileRepository` | `CompletableFuture` DAO — the template for new tables. |
| Events | `listener/PlayerConnectionListener` | Async join/quit persistence; no main-thread I/O. |
| Commands | `command/CommandRegistrar` | `/corekit reload\|info\|profile` (alias `/ck`). |

## Build

```bash
./gradlew build
# → build/libs/CoreKit-1.0.0-SNAPSHOT.jar   (shaded, server-ready)
```

Drop the jar into a Paper `1.21.x` server's `plugins/` folder. For a throwaway
test server:

```bash
./gradlew runServer
```

## Key design decisions

- **Nothing blocks the main thread.** All database access is dispatched to a
  dedicated executor and returned via `CompletableFuture`; results that touch the
  Bukkit API hop back with `DatabaseManager#sync`. This is the single biggest
  divider between a hobby plugin and a production one.
- **SQLite is shaded but *not* relocated.** The JDBC driver is discovered through
  the `META-INF/services/java.sql.Driver` SPI and by the literal class name
  `org.sqlite.JDBC`; relocating it silently breaks driver discovery. HikariCP,
  by contrast, *is* relocated to avoid clashing with other plugins.
- **WAL journalling** is enabled once (it persists in the DB file header),
  letting reads run concurrently with the single SQLite writer.
- **paper-plugin.yml + Brigadier**, not the legacy `plugin.yml commands` block —
  real subcommand trees and per-node permission gating.
- **Typed config + i18n from day one.** Features read a `Settings` record and
  message keys, never raw YAML paths scattered across the codebase.

## Extending it

1. **New table** → copy `PlayerProfileRepository`; add its `CREATE TABLE` to
   `DatabaseManager#applySchema`.
2. **New command** → add a `Commands.literal(...)` branch in `CommandRegistrar`.
3. **New config option** → add a field to `Settings`, a default to `config.yml`,
   and bump `config-version` if operators should be nudged to review it.
4. **New message** → add the key to every `lang/*.yml`; English is the fallback.
5. **Swap to MySQL** → `DatabaseManager` is the only class that knows the backend;
   change the Hikari config and schema DDL there.
