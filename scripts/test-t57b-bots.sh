#!/usr/bin/env bash
set -euo pipefail
ROOT=$(cd "$(dirname "$0")/.." && pwd)
SERVER=/home/dasan/Desktop/Proyectos/plugins/servidor/Servidor-agentes
[[ ${CD_TARGET:-agents} == agents ]] || { echo 'Solo servidor de agentes / 25566.' >&2; exit 2; }
export CD_TARGET=agents
scenario="$ROOT/scripts/t57b-bots.cjs"
case ${1:---plan} in
  --plan) exec node "$ROOT/scripts/t57b-bots.cjs" ;;
  --run) ;;
  --affected) export T57B_AFFECTED=1 ;;
  *) echo 'Uso: scripts/test-t57b-bots.sh {--plan|--run|--affected}' >&2; exit 2 ;;
esac
port_open() { (exec 3<>/dev/tcp/127.0.0.1/${T57B_PORT:-25566}) 2>/dev/null; }
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
node --check "$ROOT/scripts/t57b-bots.cjs"
export GRADLE_OPTS="${GRADLE_OPTS:-} -Dorg.gradle.daemon=false -Dorg.gradle.workers.max=2 -XX:ActiveProcessorCount=2"
export T57B_RESULTS="$ROOT/.agent/t57b-bots/$(date -u +%Y%m%dT%H%M%SZ)-$$"
mkdir -p "$T57B_RESULTS"
export CD_START_OWNER_FILE="$T57B_RESULTS/start-owned"
operation() {
  local output status attempt
  for ((attempt=0; ; attempt++)); do
    if output=$(taskset -c 2,3 "$ROOT/scripts/test-server.sh" "$@" 2>&1); then
      printf '%s\n' "$output" | tee -a "$T57B_RESULTS/lifecycle.log"; return 0
    else status=$?; fi
    printf '%s\n' "$output" | tee -a "$T57B_RESULTS/lifecycle.log" >&2
    if [[ $output == *'Otra operación del servidor está en curso.'* ]] && ((attempt<10)); then
      sleep 60; sleep 60
    else return "$status"; fi
  done
}
export T57B_ID="t57b-$(date -u +%s)"
owned=false
cleanup() {
  local status=$?
  trap - EXIT INT TERM
  # The lifecycle script validates the receipt under its lock. Do not check existence
  # here: start may still be publishing it when this trap receives an interruption.
  if $owned; then
    operation stop || status=1
    cp "$SERVER/logs/latest.log" "$T57B_RESULTS/server.log" || status=1
    if rg -n "World boss (spawn|tick) failed|No se pudo (invocar|procesar) el jefe|Could not (spawn|process) the (world )?boss|Error occurred while enabling CustomDungeons|Could not pass event .*CustomDungeons" "$T57B_RESULTS/server.log"; then status=1; fi
    if port_open; then echo 'El puerto 25566 sigue abierto.' >&2; status=1;
    else echo 'PASS: servidor apagado, puerto 25566 cerrado.' | tee -a "$T57B_RESULTS/results.log"; fi
  fi
  for ability in vortex inverted_gravity cracked_floor sweep falling_pillars poison_pools charged_beam arrow_rain rift zero profile control dungeon-pillar; do
    rm -f -- "$SERVER/plugins/CustomDungeons/mobs/$T57B_ID-$ability.yml"
  done
  rm -f -- "$SERVER/plugins/CustomDungeons/dungeons/$T57B_ID-pillar.yml"
  echo "Evidencia: $T57B_RESULTS"
  exit "$status"
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM
node "$ROOT/scripts/t57b-bots.cjs" --prepare
operation deploy
# Recheck immediately before starting. Do not take over an existing server.
if port_open || pgrep -f '[p]aper-26[.]3' >/dev/null; then echo 'Paper está activo; no se arranca.' >&2; exit 1; fi
owned=true
operation start
node "$scenario" --run --server-owned-by-runner | tee "$T57B_RESULTS/results.log"
if [[ ${T57B_AFFECTED:-0} == 1 ]]; then exit 0; fi

# Decode with the installed spark protobuf implementation; no dependency download.
SPARK_JAR=$(rg --files "$SERVER/libraries/me/lucko/spark-paper" | rg 'spark-paper-.*[.]jar$' | sort | tail -1)
[[ -n "$SPARK_JAR" ]] || { echo 'No se encontró spark-paper.' >&2; exit 1; }
/usr/lib/jvm/temurin-25-jdk-arm64/bin/java -cp "$SPARK_JAR" "$ROOT/scripts/SparkZoneProfile.java" "$T57B_RESULTS/zero.sparkprofile" "$T57B_RESULTS/profile.sparkprofile" | tee "$T57B_RESULTS/spark-summary.json"
