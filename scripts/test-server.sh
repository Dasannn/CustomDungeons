#!/usr/bin/env bash
set -euo pipefail
SERVER="$HOME/Desktop/Proyectos/plugins/servidor/Servidor"
JAVA=/usr/lib/jvm/temurin-25-jdk-arm64/bin/java
ROOT=$(cd "$(dirname "$0")/.." && pwd)
SESSION=cd-test
# Keep screen sockets in a writable, private directory shared by test worktrees.
export SCREENDIR="$SERVER/.customdungeons-screen"
mkdir -p "$SCREENDIR"
chmod 700 "$SCREENDIR"
mkdir -p "$ROOT/.agent"
# Shared lock prevents simultaneous lifecycle operations from different worktrees.
exec 9>"$SERVER/.customdungeons-test.lock"
flock -n 9 || { echo 'Otra operación del servidor está en curso.' >&2; exit 1; }
running() { pgrep -f '[p]aper-26.3[^ ]*\.jar' >/dev/null; }
# Sessions created from sandboxed agents can outlive their server: drop orphans (session without Paper).
clean_orphans() {
  screen -wipe >/dev/null 2>&1 || true
  if screen -ls | rg -q "[.]${SESSION}[[:space:]]" && ! pgrep -f 'paper-26[.]3' >/dev/null; then
    screen -S "$SESSION" -X quit >/dev/null 2>&1 || true
    screen -wipe >/dev/null 2>&1 || true
  fi
}
has_session() { clean_orphans; screen -ls | rg -q "[.]${SESSION}[[:space:]]"; }
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
    running && { echo 'Ya hay un proceso paper-26.3; no se arranca otro.' >&2; exit 1; }
    has_session && { echo 'La sesión cd-test ya existe.' >&2; exit 1; }
    CONSOLE="$ROOT/.agent/server-console.log"
    : > "$CONSOLE"
    screen -dmS "$SESSION" bash -c 'exec 9>&-; cd "$1"; export JAVA_HOME="$2"; exec ./start.sh > "$3" 2>&1' _ "$SERVER" "${JAVA%/bin/java}" "$CONSOLE"
    for ((i=0; i<180; i++)); do
      if rg -q 'Done \(' "$CONSOLE" && rg -q 'Done \(' "$SERVER/logs/latest.log"; then echo 'Servidor listo.'; exit 0; fi
      if ((i >= 5)) && ! has_session; then echo "El servidor terminó antes de arrancar. Revisa $CONSOLE" >&2; exit 1; fi
      sleep 1
    done
    screen -S "$SESSION" -p 0 -X stuff $'stop\r' || true
    echo 'Timeout de arranque; se ha solicitado stop.' >&2
    exit 1
    ;;
  stop)
    if ! has_session; then
      running && { echo 'Hay un servidor ajeno a cd-test; no se detiene.' >&2; exit 1; }
      echo 'Servidor ya detenido.'; exit 0
    fi
    screen -S "$SESSION" -p 0 -X stuff $'stop\r'
    for ((i=0; i<180; i++)); do
      if ! has_session && ! running; then echo 'Servidor detenido.'; exit 0; fi
      sleep 1
    done
    echo 'Timeout de apagado; revisa el proceso sin forzar su terminación.' >&2; exit 1
    ;;
  cmd)
    [[ $# == 2 && "$2" != *$'\n'* && "$2" != *$'\r'* ]] || { echo 'Indica un único comando.' >&2; exit 1; }
    has_session || { echo 'No existe la sesión cd-test.' >&2; exit 1; }
    screen -S "$SESSION" -p 0 -X stuff "$2"$'\r'
    ;;
  log)
    [[ "${2:-80}" =~ ^[1-9][0-9]*$ ]] || { echo 'Número de líneas inválido.' >&2; exit 1; }
    tail -n "${2:-80}" "$SERVER/logs/latest.log"
    ;;
  *) echo 'Uso: test-server.sh {deploy|start|stop|cmd "comando"|log [n]}' >&2; exit 2 ;;
esac
