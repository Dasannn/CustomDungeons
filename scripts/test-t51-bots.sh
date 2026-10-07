#!/usr/bin/env bash
set -euo pipefail
# Run only after the architect/user releases and stops the agents server.
ROOT=$(cd "$(dirname "$0")/.." && pwd)
SERVER=/home/dasan/Desktop/Proyectos/plugins/servidor/Servidor-agentes
[[ ${CD_TARGET:-agents} == agents ]] || { echo 'Solo CD_TARGET=agents / 25566.' >&2; exit 2; }
export CD_TARGET=agents
case ${1:---plan} in
  --plan) exec node "$ROOT/scripts/t51-bots.cjs" --plan ;;
  --self-check)
    node --test "$ROOT/scripts/test-t51-bots.cjs"
    exec node "$ROOT/scripts/t51-bots.cjs" --self-check ;;
  --run) ;;
  *) echo 'Uso: CD_TARGET=agents scripts/test-t51-bots.sh {--plan|--self-check|--run}' >&2; exit 2 ;;
esac
[[ $# == 1 ]] || { echo 'Se requiere únicamente --run.' >&2; exit 2; }
port_open() { (exec 3<>/dev/tcp/127.0.0.1/25566) 2>/dev/null; }
require_stopped() {
  if port_open || pgrep -f '[p]aper-26[.]3' >/dev/null; then
    echo 'Servidor ocupado o proceso Paper existente: no se despliega ni arranca. Libéralo y apágalo antes.' >&2
    return 1
  fi
}
require_stopped
node "$ROOT/scripts/t51-bots.cjs" --self-check
# A separate suite lock keeps simultaneous T51 runners apart; each server operation
# still acquires test-server.sh's normal shared lock.
exec 8>"$SERVER/.customdungeons-t51-bots.lock"
for ((attempt=0; ; attempt++)); do
  if flock -n 8; then break; fi
  ((attempt < 10)) || { echo 'Otra suite T51 sigue en curso.' >&2; exit 1; }
  echo 'Otra suite T51 en curso; reintento en dos minutos.' >&2
  sleep 60; sleep 60
done
require_stopped
export T51_RESULTS="$ROOT/.agent/t51-bots/$(date -u +%Y%m%dT%H%M%SZ)-$$"
mkdir -p "$T51_RESULTS"
# test-server.sh deploy invokes jar. Force its Gradle invocation to stop its daemon.
export GRADLE_OPTS="${GRADLE_OPTS:-} -Dorg.gradle.daemon=false -Dorg.gradle.workers.max=2 -XX:ActiveProcessorCount=2"
owned=false
bot_pid=
operation() {
  local output status attempt
  for ((attempt=0; ; attempt++)); do
    if output=$(taskset -c 2,3 "$ROOT/scripts/test-server.sh" "$@" 2>&1); then
      printf '%s\n' "$output" | tee -a "$T51_RESULTS/lifecycle.log"
      return 0
    else status=$?; fi
    printf '%s\n' "$output" | tee -a "$T51_RESULTS/lifecycle.log" >&2
    if [[ $output == *'Otra operación del servidor está en curso.'* ]] && ((attempt < 10)); then
      echo 'Reintento de operación en dos minutos.' >&2; sleep 60; sleep 60
    else return "$status"; fi
  done
}
no_foreign_player() {
  [[ ! -e "$T51_RESULTS/foreign-player" ]] || {
    echo 'Ha entrado otro jugador: se cancela la suite y se deja el servidor encendido.' >&2; return 1;
  }
}
revoke_bot_op() {
  local name
  [[ -f "$T51_RESULTS/state.json" ]] || return 0
  name=$(node -e 'const s=require(process.argv[1]); if(/^T51B[0-9a-f]{8}$/.test(s.name))process.stdout.write(s.name)' "$T51_RESULTS/state.json") || return 1
  [[ -n $name ]] || return 0
  if port_open; then operation cmd "deop $name"; return; fi
  # A failed second start leaves owned=false but the OP still persists in ops.json.
  # Serialize this offline edit with every lifecycle operation, preserving other OPs.
  (
    exec 9>"$SERVER/.customdungeons-test.lock"
    flock -w 120 9 || return 1
    if port_open || pgrep -f '[p]aper-26[.]3' >/dev/null; then
      echo 'Paper vuelve a estar activo; no se edita ops.json sin conexión.' >&2; return 1
    fi
    node - "$SERVER/ops.json" "$name" <<'NODE'
const fs = require('node:fs');
const [file, name] = process.argv.slice(2);
if (!fs.existsSync(file)) process.exit(0);
if (!fs.lstatSync(file).isFile()) throw new Error('ops.json must be a regular file');
const operators = JSON.parse(fs.readFileSync(file, 'utf8'));
if (!Array.isArray(operators)) throw new Error('Invalid operator list');
const remaining = operators.filter(op => op.name?.toLowerCase() !== name.toLowerCase());
if (remaining.length === operators.length) process.exit(0);
const temporary = file + '.t51-' + process.pid + '.tmp';
try {
  fs.writeFileSync(temporary, JSON.stringify(remaining, null, 2) + '\n', { flag: 'wx', mode: fs.statSync(file).mode & 0o777 });
  fs.renameSync(temporary, file);
} finally { if (fs.existsSync(temporary)) fs.unlinkSync(temporary); }
NODE
  )
}
cleanup() {
  local status=$?
  trap - EXIT INT TERM
  if [[ -n $bot_pid ]] && kill -0 "$bot_pid" 2>/dev/null; then
    kill -TERM "$bot_pid" 2>/dev/null || true
    wait "$bot_pid" || true
  fi
  revoke_bot_op || status=1
  if $owned; then
    if no_foreign_player; then
      operation stop || status=1
      cp "$SERVER/logs/latest.log" "$T51_RESULTS/shutdown-server.log" || status=1
      if rg -n 'generated an exception|Exception|\bERROR\b|Build mode entry failed' "$T51_RESULTS/shutdown-server.log" \
          | rg -v 'Minecraft Services Discovery|com\.mojang\.authlib|SocketTimeoutException'; then
        echo 'Hay excepciones en el log de cierre.' >&2; status=1
      fi
      if port_open; then echo 'El puerto 25566 sigue abierto.' >&2; status=1; fi
    else status=1; fi
  fi
  echo "Evidencia: $T51_RESULTS"
  exit "$status"
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM
operation deploy
require_stopped
operation start
owned=true
node "$ROOT/scripts/t51-bots.cjs" --run --server-owned-by-runner --phase before > "$T51_RESULTS/before-controller.log" 2>&1 &
bot_pid=$!
ready=false
for ((second=0; second<1200; second++)); do
  no_foreign_player
  if [[ -e "$T51_RESULTS/ready-to-restart" ]]; then ready=true; break; fi
  kill -0 "$bot_pid" 2>/dev/null || { wait "$bot_pid"; echo 'El bot terminó sin preparar el reinicio.' >&2; exit 1; }
  sleep 1
done
$ready || { echo 'Timeout preparando reinicio.' >&2; exit 1; }
no_foreign_player
operation stop
owned=false
wait "$bot_pid"
bot_pid=
require_stopped
operation start
owned=true
node "$ROOT/scripts/t51-bots.cjs" --run --server-owned-by-runner --phase after > "$T51_RESULTS/after-controller.log" 2>&1 &
bot_pid=$!
wait "$bot_pid"
bot_pid=
no_foreign_player
echo 'T51: 16 escenarios de bots pasaron; ver JSONL e inventarios en la evidencia.'
