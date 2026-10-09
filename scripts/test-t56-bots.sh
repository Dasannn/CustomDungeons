#!/usr/bin/env bash
set -euo pipefail
ROOT=$(cd "$(dirname "$0")/.." && pwd)
SERVER=/home/dasan/Desktop/Proyectos/plugins/servidor/Servidor-agentes
[[ ${CD_TARGET:-agents} == agents ]] || { echo 'Solo servidor de agentes / 25566.' >&2; exit 2; }
export CD_TARGET=agents
scenario="$ROOT/scripts/t56-bots.cjs"
case ${1:---plan} in
  --plan) exec node "$ROOT/scripts/t56-bots.cjs" ;;
  --run|--all) ;;
  --fair) export T56_FAIR_ONLY=1 ;;
  --lifecycle) scenario="$ROOT/scripts/t56-lifecycle-bots.cjs" ;;
  *) echo 'Uso: scripts/test-t56-bots.sh {--plan|--run|--lifecycle|--all|--fair}' >&2; exit 2 ;;
esac
port_open() { (exec 3<>/dev/tcp/127.0.0.1/25566) 2>/dev/null; }
if port_open || pgrep -f '[p]aper-26[.]3' >/dev/null; then
  echo 'Servidor ocupado: no se despliega, arranca ni reinicia.' >&2; exit 1
fi
# One heavy job at a time; let the Pi cool before deployment and Paper startup.
if [[ -r /sys/class/thermal/thermal_zone0/temp ]]; then
  for ((cooling=0; $(cat /sys/class/thermal/thermal_zone0/temp)>=72000; cooling++)); do
    ((cooling<30)) || { echo 'Temperatura alta; no se arranca Paper.' >&2; exit 1; }
    echo 'Esperando a que la Pi baje de 72 °C…'
    sleep 10
  done
fi
node --check "$ROOT/scripts/t56-bots.cjs"
export GRADLE_OPTS="${GRADLE_OPTS:-} -Dorg.gradle.daemon=false -Dorg.gradle.workers.max=2 -XX:ActiveProcessorCount=2"
export T56_RESULTS="$ROOT/.agent/t56-bots/$(date -u +%Y%m%dT%H%M%SZ)-$$"
mkdir -p "$T56_RESULTS"
operation() {
  local output status attempt
  for ((attempt=0; ; attempt++)); do
    if output=$(taskset -c 2,3 "$ROOT/scripts/test-server.sh" "$@" 2>&1); then
      printf '%s\n' "$output" | tee -a "$T56_RESULTS/lifecycle.log"; return 0
    else status=$?; fi
    printf '%s\n' "$output" | tee -a "$T56_RESULTS/lifecycle.log" >&2
    if [[ $output == *'Otra operación del servidor está en curso.'* ]] && ((attempt<10)); then
      sleep 60; sleep 60
    else return "$status"; fi
  done
}
export T56_ID="t56-$(date -u +%s)-$$"
node "$ROOT/scripts/t56-bots.cjs" --prepare
owned=false
cleanup() {
  local status=$?
  trap - EXIT INT TERM
  if $owned; then
    operation stop || status=1
    cp "$SERVER/logs/latest.log" "$T56_RESULTS/server.log" || status=1
    if rg -n "World boss (spawn|tick) failed|No se pudo (invocar|procesar) el jefe|Could not (spawn|process) the (world )?boss|Error occurred while enabling CustomDungeons|Could not pass event .*CustomDungeons" "$T56_RESULTS/server.log"; then status=1; fi
    if port_open; then echo 'El puerto 25566 sigue abierto.' >&2; status=1;
    else echo 'PASS: servidor apagado, puerto 25566 cerrado.' | tee -a "$T56_RESULTS/results.log"; fi
  fi
  rm -f -- "$SERVER/plugins/CustomDungeons/mobs/$T56_ID.yml" "$SERVER/plugins/CustomDungeons/mobs/$T56_ID-zero.yml" "$SERVER/plugins/CustomDungeons/mobs/$T56_ID-fair.yml"
  echo "Evidencia: $T56_RESULTS"
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
if [[ ${1:---plan} == --all ]]; then
  node "$ROOT/scripts/t56-lifecycle-bots.cjs" --run --server-owned-by-runner | tee "$T56_RESULTS/lifecycle-results.log"
  mv "$T56_RESULTS/results.json" "$T56_RESULTS/lifecycle-results.json"
fi
node "$scenario" --run --server-owned-by-runner | tee "$T56_RESULTS/results.log"

