#!/usr/bin/env bash
set -euo pipefail
# Dos servidores: el de AGENTES (por defecto, puerto 25566) y el del USUARIO (CD_TARGET=user, 25565).
# Los agentes nunca deben tocar el del usuario; el arquitecto lo usa solo para desplegar avisando.
if [[ "${CD_TARGET:-agents}" == user ]]; then
  SERVER="$HOME/Desktop/Proyectos/plugins/servidor/Servidor"; PORT=25565; SESSION=cd-test
else
  SERVER="$HOME/Desktop/Proyectos/plugins/servidor/Servidor-agentes"; PORT=25566; SESSION=cd-agents
fi
# Receipts belong exclusively to agent runners; never let one select the user server.
if [[ -n ${CD_START_OWNER_FILE:-} && ${CD_TARGET:-agents} != agents ]]; then
  echo 'Los recibos de runner solo pueden usar agentes / 25566.' >&2; exit 2
fi
JAVA=/usr/lib/jvm/temurin-25-jdk-arm64/bin/java
ROOT=$(cd "$(dirname "$0")/.." && pwd)
if [[ -n ${CD_START_OWNER_FILE:-} && $CD_START_OWNER_FILE != "$ROOT/.agent/"* ]]; then
  echo 'Recibo de arranque fuera de .agent.' >&2; exit 2
fi
# Keep screen sockets in a writable, private directory shared by test worktrees.
export SCREENDIR="$SERVER/.customdungeons-screen"
mkdir -p "$SCREENDIR"
chmod 700 "$SCREENDIR"
# Keep the socket path relative so long server paths fit Linux's 108-byte limit.
screen() { (cd "$SCREENDIR" && SCREENDIR=. command screen "$@"); }
mkdir -p "$ROOT/.agent"
# Shared lock prevents simultaneous lifecycle operations from different worktrees.
exec 9>"$SERVER/.customdungeons-test.lock"
flock -n 9 || { echo 'Otra operación del servidor está en curso.' >&2; exit 1; }
# Sessions created from sandboxed agents can outlive their server. Process checks (pgrep) are NOT
# reliable from a sandbox (separate PID namespace), so liveness is checked through the game port.
port_open() { (exec 3<>/dev/tcp/127.0.0.1/$PORT) 2>/dev/null; }
running() { port_open; }
clean_orphans() {
  screen -wipe >/dev/null 2>&1 || true
  if screen -ls | rg -q "[.]${SESSION}[[:space:]]" && ! port_open && ! running; then
    screen -S "$SESSION" -X quit >/dev/null 2>&1 || true
    screen -wipe >/dev/null 2>&1 || true
  fi
}
has_session() { screen -ls | rg -q "[.](${SESSION}|ca[0-9a-f]{12})[[:space:]]"; }
# A Screen PID plus the socket identity binds a receipt to this exact session, including
# across PID namespaces. Querying that socket checks liveness without guessing via pgrep.
session_id() {
  local name=${1:-$SESSION}
  screen -ls | rg -o "^[[:space:]]*[0-9]+[.]${name}[[:space:]]" | tr -d '[:space:]'
}
socket_identity() { stat -Lc '%d:%i' -- "$SCREENDIR/$1" 2>/dev/null; }
instance_live() {
  local id=$1 identity=$2
  [[ $id =~ ^[0-9]+[.](${SESSION}|ca[0-9a-f]{12})$ && $identity =~ ^[0-9]+:[0-9]+$ ]] || return 1
  [[ $(socket_identity "$id") == "$identity" ]] || return 1
  # -Q waits for a reply/signal and can hang while holding the shared lock.
  # Selecting the current window is a harmless socket command without that reply.
  (cd "$SCREENDIR" && SCREENDIR=. timeout 5 screen -S "$id" -X select .) >/dev/null 2>&1
}
owned_instance() {
  local id identity extra
  if [[ ! -s $CD_START_OWNER_FILE ]] || ! IFS=$'\t' read -r id identity extra < "$CD_START_OWNER_FILE" \
      || [[ -n $extra || ! $id =~ ^[0-9]+[.]ca[0-9a-f]{12}$ ]] || ! instance_live "$id" "$identity"; then
    echo 'Recibo ausente, vacío o de una instancia que ya no coincide; no se envía ningún comando.' >&2
    return 1
  fi
  TARGET_SESSION=$id; TARGET_IDENTITY=$identity
}
case "${1:-}" in
  deploy)
    running && { echo 'Hay un servidor Paper en ejecución; no se despliega.' >&2; exit 1; }
    cd "$ROOT"
    JAVA_HOME="${JAVA%/bin/java}" ./gradlew jar
    JAR=
    for candidate in "$ROOT"/build/libs/CustomDungeons-*.jar; do
      [[ -f "$candidate" && "$candidate" != *-sources.jar && "$candidate" != *-javadoc.jar ]] || continue
      if [[ -z "$JAR" || "$candidate" -nt "$JAR" ]]; then JAR="$candidate"; fi
    done
    [[ -n "$JAR" ]] || { echo 'No se encontró un jar de CustomDungeons en build/libs.' >&2; exit 1; }
    cp -- "$JAR" "$SERVER/plugins/CustomDungeons.jar"
    ;;
  start)
    port_open && { echo "El puerto $PORT ya está en uso: hay un servidor corriendo." >&2; exit 1; }
    running && { echo 'Ya hay un proceso paper-26.3; no se arranca otro.' >&2; exit 1; }
    # An owning runner must not quit another Screen that is still booting or has no
    # game port yet. Only manual lifecycle operations retain the orphan cleanup.
    if [[ -z ${CD_START_OWNER_FILE:-} ]]; then clean_orphans; fi
    has_session && { echo 'La sesión cd-test ya existe.' >&2; exit 1; }
    CONSOLE="$ROOT/.agent/server-console.log"
    : > "$CONSOLE"
    # Do not leave a stale receipt behind after a failed launch. Signals are deferred only
    # over launch + receipt publication, both under the same lifecycle lock.
    if [[ -n ${CD_START_OWNER_FILE:-} ]]; then rm -f -- "$CD_START_OWNER_FILE"; fi
    START_NAME=$SESSION
    # A compact random name distinguishes later sessions even if PID/inode are reused.
    if [[ -n ${CD_START_OWNER_FILE:-} ]]; then
      IFS= read -r start_nonce < /proc/sys/kernel/random/uuid
      start_nonce=${start_nonce//-/}
      START_NAME="ca${start_nonce:0:12}"
    fi
    start_signal=0
    trap 'start_signal=130' INT
    trap 'start_signal=143' TERM
    screen -dmS "$START_NAME" bash -c 'exec 9>&-; cd "$1"; export JAVA_HOME="$2"; exec ./start.sh > "$3" 2>&1' _ "$SERVER" "${JAVA%/bin/java}" "$CONSOLE"
    STARTED_SESSION=$(session_id "$START_NAME")
    STARTED_IDENTITY=$(socket_identity "$STARTED_SESSION")
    instance_live "$STARTED_SESSION" "$STARTED_IDENTITY" || { echo 'No se pudo identificar la instancia arrancada.' >&2; exit 1; }
    if [[ -n ${CD_START_OWNER_FILE:-} ]]; then
      printf '%s\t%s\n' "$STARTED_SESSION" "$STARTED_IDENTITY" > "$CD_START_OWNER_FILE.tmp"
      mv -- "$CD_START_OWNER_FILE.tmp" "$CD_START_OWNER_FILE"
    fi
    trap - INT TERM
    ((start_signal==0)) || exit "$start_signal"
    for ((i=0; i<180; i++)); do
      if rg -q 'Done \(' "$CONSOLE" && rg -q 'Done \(' "$SERVER/logs/latest.log"; then echo 'Servidor listo.'; exit 0; fi
      if ((i >= 5)) && ! instance_live "$STARTED_SESSION" "$STARTED_IDENTITY"; then echo "El servidor terminó antes de arrancar. Revisa $CONSOLE" >&2; exit 1; fi
      sleep 1
    done
    if instance_live "$STARTED_SESSION" "$STARTED_IDENTITY"; then
      screen -S "$STARTED_SESSION" -p 0 -X stuff $'stop\r' || true
    fi
    echo 'Timeout de arranque; se ha solicitado stop.' >&2
    exit 1
    ;;
  stop)
    TARGET_SESSION=$SESSION; TARGET_IDENTITY=
    if [[ -n ${CD_START_OWNER_FILE:-} ]]; then
      # Validation happens after acquiring the lock, even if cleanup interrupted start.
      owned_instance || exit 0
    elif ! has_session; then
      running && { echo 'Hay un servidor ajeno a cd-test; no se detiene.' >&2; exit 1; }
      echo 'Servidor ya detenido.'; exit 0
    fi
    screen -S "$TARGET_SESSION" -p 0 -X stuff $'stop\r'
    for ((i=0; i<180; i++)); do
      if [[ -n $TARGET_IDENTITY ]]; then
        if ! instance_live "$TARGET_SESSION" "$TARGET_IDENTITY"; then
          echo 'Instancia propia detenida.'; exit 0
        fi
      elif ! has_session && ! running; then echo 'Servidor detenido.'; exit 0; fi
      sleep 1
    done
    echo 'Timeout de apagado; revisa el proceso sin forzar su terminación.' >&2; exit 1
    ;;
  cmd)
    [[ $# == 2 && "$2" != *$'\n'* && "$2" != *$'\r'* ]] || { echo 'Indica un único comando.' >&2; exit 1; }
    TARGET_SESSION=$SESSION; TARGET_IDENTITY=
    if [[ -n ${CD_START_OWNER_FILE:-} ]]; then owned_instance || exit 1
    else has_session || { echo 'No existe la sesión cd-test.' >&2; exit 1; }; fi
    screen -S "$TARGET_SESSION" -p 0 -X stuff "$2"$'\r'
    ;;
  log)
    [[ "${2:-80}" =~ ^[1-9][0-9]*$ ]] || { echo 'Número de líneas inválido.' >&2; exit 1; }
    tail -n "${2:-80}" "$SERVER/logs/latest.log"
    ;;
  *) echo 'Uso: test-server.sh {deploy|start|stop|cmd "comando"|log [n]}' >&2; exit 2 ;;
esac
