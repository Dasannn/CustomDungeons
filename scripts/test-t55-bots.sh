#!/usr/bin/env bash
set -euo pipefail
ROOT=$(cd "$(dirname "$0")/.." && pwd)
SERVER=/home/dasan/Desktop/Proyectos/plugins/servidor/Servidor-agentes
[[ ${CD_TARGET:-agents} == agents ]] || { echo 'Solo servidor de agentes / 25566.' >&2; exit 2; }
export CD_TARGET=agents
case ${1:---plan} in
  --plan) exec node "$ROOT/scripts/t55-bots.cjs" ;;
  --run) ;;
  --affected) export T55_SUITE=affected ;;
  --projectiles) export T55_SUITE=projectiles ;;
  *) echo 'Uso: scripts/test-t55-bots.sh {--plan|--run|--affected|--projectiles}' >&2; exit 2 ;;
esac
port_open() { (exec 3<>/dev/tcp/127.0.0.1/25566) 2>/dev/null; }
if port_open || pgrep -f '[p]aper-26[.]3' >/dev/null; then
  echo 'Servidor ocupado: no se despliega, arranca ni reinicia.' >&2; exit 1
fi
node --check "$ROOT/scripts/t55-bots.cjs"
export GRADLE_OPTS="${GRADLE_OPTS:-} -Dorg.gradle.daemon=false -Dorg.gradle.workers.max=2 -XX:ActiveProcessorCount=2"
export T55_RESULTS="$ROOT/.agent/t55-bots/$(date -u +%Y%m%dT%H%M%SZ)-$$"
mkdir -p "$T55_RESULTS"
operation() {
  local output status attempt
  for ((attempt=0; ; attempt++)); do
    if output=$(taskset -c 2,3 "$ROOT/scripts/test-server.sh" "$@" 2>&1); then
      printf '%s\n' "$output" | tee -a "$T55_RESULTS/lifecycle.log"; return 0
    else status=$?; fi
    printf '%s\n' "$output" | tee -a "$T55_RESULTS/lifecycle.log" >&2
    if [[ $output == *'Otra operación del servidor está en curso.'* ]] && ((attempt<10)); then
      sleep 60; sleep 60
    else return "$status"; fi
  done
}
export T55_ID="t55-$(date -u +%s)-$$"
node "$ROOT/scripts/t55-bots.cjs" --prepare
owned=false
cleanup() {
  local status=$?
  trap - EXIT INT TERM
  if $owned; then
    operation stop || status=1
    cp "$SERVER/logs/latest.log" "$T55_RESULTS/server.log" || status=1
    if rg -n "World boss (spawn|tick) failed|No se pudo (invocar|procesar) el jefe|Could not (spawn|process) the (world )?boss|Error occurred while enabling CustomDungeons|Could not pass event .*CustomDungeons" "$T55_RESULTS/server.log"; then status=1; fi
    if port_open; then echo 'El puerto 25566 sigue abierto.' >&2; status=1;
    else echo 'PASS: servidor apagado, puerto 25566 cerrado.' | tee -a "$T55_RESULTS/results.log"; fi
  fi
  rm -f -- "$SERVER/plugins/CustomDungeons/mobs/$T55_ID.yml"
  echo "Evidencia: $T55_RESULTS"
  exit "$status"
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM
operation deploy
# Recheck immediately before starting. Do not take over an existing server.
if port_open || pgrep -f '[p]aper-26[.]3' >/dev/null; then echo 'Paper está activo; no se arranca.' >&2; exit 1; fi
operation start
owned=true
node "$ROOT/scripts/t55-bots.cjs" --run --server-owned-by-runner | tee "$T55_RESULTS/results.log"

if [[ ${T55_SUITE:-full} != full ]]; then exit 0; fi

operation stop
owned=false
cp "$SERVER/logs/latest.log" "$T55_RESULTS/server-before.log"
if port_open || pgrep -f '[p]aper-26[.]3' >/dev/null; then echo 'Paper sigue activo; no se reinicia.' >&2; exit 1; fi
operation start
owned=true
T55_PHASE=after node "$ROOT/scripts/t55-bots.cjs" --run --server-owned-by-runner | tee -a "$T55_RESULTS/results.log"
