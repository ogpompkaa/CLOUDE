#!/usr/bin/env bash
# Uniwersalny start serwera Paper (lobby albo arena) z flagami Aikara.
# Uzycie:
#   ./start-paper.sh <sciezka-do-katalogu-serwera> [RAM]
# Przyklad:
#   ./start-paper.sh servers/lobby 3G
#   ./start-paper.sh servers/arena-1 4G
# W katalogu serwera musi lezec paper.jar (https://papermc.io/downloads/paper).
set -euo pipefail

DIR="${1:?Podaj katalog serwera, np. servers/lobby}"
RAM="${2:-3G}"
cd "$DIR"

java -Xms"$RAM" -Xmx"$RAM" \
  -XX:+UseG1GC -XX:+ParallelRefProcEnabled -XX:MaxGCPauseMillis=200 \
  -XX:+UnlockExperimentalVMOptions -XX:+DisableExplicitGC \
  -XX:+AlwaysPreTouch -XX:G1NewSizePercent=30 -XX:G1MaxNewSizePercent=40 \
  -XX:G1HeapRegionSize=8M -XX:G1ReservePercent=20 -XX:G1HeapWastePercent=5 \
  -XX:G1MixedGCCountTarget=4 -XX:InitiatingHeapOccupancyPercent=15 \
  -XX:G1MixedGCLiveThresholdPercent=90 -XX:G1RSetUpdatingPauseTimePercent=5 \
  -XX:SurvivorRatio=32 -XX:+PerfDisableSharedMem -XX:MaxTenuringThreshold=1 \
  -Dusing.aikars.flags=https://mcflags.emc.gs -Daikars.new.flags=true \
  -jar paper.jar --nogui
