#!/usr/bin/env bash
set -euo pipefail
ROOT=$(cd "$(dirname "$0")/.." && pwd)
SERVER=/home/dasan/Desktop/Proyectos/plugins/servidor/Servidor-agentes
[[ ${CD_TARGET:-agents} == agents ]] || { echo 'Solo servidor de agentes / 25566.' >&2; exit 2; }
export CD_TARGET=agents
case ${1:---plan} in
  --plan) exec node "$ROOT/scripts/t52-bots.cjs" ;;
  --run) ;;
  *) echo 'Uso: scripts/test-t52-bots.sh {--plan|--run}' >&2; exit 2 ;;
esac
port_open() { (exec 3<>/dev/tcp/127.0.0.1/25566) 2>/dev/null; }
if port_open || pgrep -f '[p]aper-26[.]3' >/dev/null; then
  echo 'Servidor ocupado: no se despliega, arranca ni reinicia.' >&2; exit 1
fi
export GRADLE_OPTS="${GRADLE_OPTS:-} -Dorg.gradle.daemon=false -Dorg.gradle.workers.max=2 -XX:ActiveProcessorCount=2"
export T52_RESULTS="$ROOT/.agent/t52-bots/$(date -u +%Y%m%dT%H%M%SZ)-$$"
mkdir -p "$T52_RESULTS"
operation() {
  local output status attempt
  for ((attempt=0; ; attempt++)); do
    if output=$("$ROOT/scripts/test-server.sh" "$@" 2>&1); then
      printf '%s\n' "$output" | tee -a "$T52_RESULTS/lifecycle.log"; return 0
    else status=$?; fi
    printf '%s\n' "$output" | tee -a "$T52_RESULTS/lifecycle.log" >&2
    if [[ $output == *'Otra operación del servidor está en curso.'* ]] && ((attempt<10)); then
      sleep 60; sleep 60
    else return "$status"; fi
  done
}
owned=false
cleanup() {
  if $owned; then operation log 100 > "$T52_RESULTS/server.log" || true; operation stop; fi
}
trap cleanup EXIT INT TERM
operation deploy
# Recheck immediately before starting. Do not take over an existing server.
if port_open || pgrep -f '[p]aper-26[.]3' >/dev/null; then echo 'Paper está activo; no se arranca.' >&2; exit 1; fi
operation start
owned=true
node "$ROOT/scripts/t52-bots.cjs" --run --server-owned-by-runner | tee "$T52_RESULTS/results.log"
