# Zestaw startowy sieci UltraHC

Gotowe pliki do pierwszego uruchomienia sieci **Velocity → lobby → areny**.
Pełny przewodnik krok-po-kroku: [`../docs/SIEC.md`](../docs/SIEC.md).

| Plik | Do czego |
|------|----------|
| `velocity/velocity.toml` | konfiguracja proxy (bind, serwery, modern forwarding) |
| `velocity/forwarding.secret` | wspólny sekret — **zmień na losowy** |
| `lobby/server.properties` | Paper lobby (port 30065, `online-mode=false`) |
| `arena/server.properties` | Paper arena (port 30101 — kopiuj i zmieniaj port) |
| `paper-global-proxies.yml` | fragment `config/paper-global.yml` (sekcja `proxies`) dla każdego Paper |
| `start-velocity.sh` | start proxy |
| `start-paper.sh` | start serwera Paper: `./start-paper.sh <katalog> [RAM]` |

> To są **przykłady** — przed produkcją zmień sekret forwardingu, hasła bazy
> i adresy/porty. Nazwy aren w `velocity.toml` muszą zgadzać się z `server.instance-name`
> w `config.yml` każdej areny.
