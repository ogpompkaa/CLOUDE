#!/usr/bin/env bash
# Start proxy Velocity. Umiesc velocity.jar w tym katalogu.
# Pobierz: https://papermc.io/downloads/velocity
set -euo pipefail
cd "$(dirname "$0")"
java -Xms512M -Xmx1G \
  -XX:+UseG1GC -XX:G1HeapRegionSize=4M -XX:+UnlockExperimentalVMOptions \
  -jar velocity.jar
