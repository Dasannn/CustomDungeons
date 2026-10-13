#!/usr/bin/env bash
set -euo pipefail
ROOT=$(cd "$(dirname "$0")/.." && pwd)
SERVER=/home/dasan/Desktop/Proyectos/plugins/servidor/Servidor-agentes
[[ ${CD_TARGET:-agents} == agents ]] || { echo 'Solo servidor de agentes / 25566.' >&2; exit 2; }
export CD_TARGET=agents
case ${1:---plan} in
  --plan-zones) T58_RUN_PART=zones exec node "$ROOT/scripts/t58-bots.cjs" --plan ;;
  --run-zones) export T58_RUN_PART=zones ;;
  --restore-zones) [[ $# == 2 ]] || exit 2; export T58_RUN_PART=zones T58_RECOVERY_SOURCE; T58_RECOVERY_SOURCE=$(realpath -- "$2") ;;
  --plan) exec node "$ROOT/scripts/t58-bots.cjs" --plan ;;
  --run) ;;
  *) echo 'Uso: scripts/test-t58-bots.sh {--plan|--run|--plan-zones|--run-zones|--restore-zones directorio-zones}' >&2; exit 2 ;;
esac
# Each cold workload owns a fresh Paper JVM. The parent succeeds only when both children pass.
if [[ -z ${T58_RUN_PART:-} ]]; then
  export T58_ACCEPTANCE_RESULTS="$ROOT/.agent/t58-bots/$(date -u +%Y%m%dT%H%M%SZ)-$$"
  mkdir -p "$T58_ACCEPTANCE_RESULTS"
  T58_RUN_PART=warden "$0" --run
  T58_RUN_PART=trio "$0" --run
  python3 - "$T58_ACCEPTANCE_RESULTS" <<'PYACCEPT'
import json, sys
from pathlib import Path
root=Path(sys.argv[1])
warden=json.loads((root/'warden/summary.json').read_text())
trio=json.loads((root/'trio/summary.json').read_text())
required={'warden-dungeon','warden-room','warden-live','grab_throw','levitation_cage','drain_grab','bomb_mark','tactical_summon','roots','anchor_spear','soul_chain','charge','decoys','zero','profile'}
assert {s['scenario'] for s in warden}==required and len(warden)==15
assert {s['scenario'] for s in trio}=={'cold-trio','zero'}
assert all(s['seconds']>=120 for s in warden+trio)
result={'acceptance':False,'scenarios':17,'coldWarden':json.loads((root/'warden/cold-windows.json').read_text()),'coldTrio':json.loads((root/'trio/cold-windows.json').read_text()),'warmProfile':json.loads((root/'warden/spark-summary.json').read_text())}
(root/'acceptance.json').write_text(json.dumps(result,indent=2))
PYACCEPT
  node "$ROOT/scripts/t58-profile-windows.cjs" --suite "$T58_ACCEPTANCE_RESULTS/acceptance.json"
  exit 0
fi
[[ $T58_RUN_PART == warden || $T58_RUN_PART == trio || $T58_RUN_PART == zones ]] || { echo 'Invalid cold workload.' >&2; exit 2; }
port_open() { (exec 3<>/dev/tcp/127.0.0.1/25566) 2>/dev/null; }
if [[ -n ${T58_RECOVERY_SOURCE:-} && $T58_RECOVERY_SOURCE != "$ROOT/.agent/t58-bots/"*/zones ]]; then
  echo 'La recuperación necesita el directorio zones de una ejecución propia.' >&2; exit 2
fi
if port_open || pgrep -f '[p]aper-26[.]3' >/dev/null; then echo 'Servidor ocupado: no se arranca otro.' >&2; exit 1; fi
while [[ -r /sys/class/thermal/thermal_zone0/temp ]] && (( $(cat /sys/class/thermal/thermal_zone0/temp) >= 75000 )); do
  echo 'Esperando temperatura inferior a 75 °C…'; sleep 10
done
export GRADLE_OPTS="${GRADLE_OPTS:-} -Dorg.gradle.daemon=false -Dorg.gradle.workers.max=2 -XX:ActiveProcessorCount=2"
export T58_RESULTS="${T58_ACCEPTANCE_RESULTS:-$ROOT/.agent/t58-bots/$(date -u +%Y%m%dT%H%M%SZ)-$$}/$T58_RUN_PART"
mkdir -p "$T58_RESULTS/probe-classes"
export CD_START_OWNER_FILE="$T58_RESULTS/start-owned"
IFS= read -r run_nonce < /proc/sys/kernel/random/uuid
run_nonce=${run_nonce//-/}
export T58_ID="t58-${run_nonce:0:6}"
[[ $T58_RUN_PART != zones ]] || export T58_ID="t58b-${run_nonce:0:6}"
owned=false;probe_created=false;probe="$SERVER/plugins/$T58_ID-probe.jar"
operation() {
  local output status attempt
  for ((attempt=0; ; attempt++)); do
    if output=$(
      if [[ $1 == deploy ]]; then taskset -c 2,3 "$ROOT/scripts/test-server.sh" "$@";
      else "$ROOT/scripts/test-server.sh" "$@"; fi 2>&1
    ); then printf '%s\n' "$output" | tee -a "$T58_RESULTS/lifecycle.log"; return 0; else status=$?; fi
    printf '%s\n' "$output" | tee -a "$T58_RESULTS/lifecycle.log" >&2
    if [[ $output == *'Otra operación del servidor está en curso.'* ]] && ((attempt<10)); then sleep 60; sleep 60; else return "$status"; fi
  done
}
cleanup() {
  local status=$?
  trap - EXIT INT TERM
  if $owned; then operation stop || status=1; cp "$SERVER/logs/latest.log" "$T58_RESULTS/server.log" || status=1; fi
  # Remove only files listed by this run. Preserve everything that pre-existed it.
  if [[ -f "$T58_RESULTS/created.json" ]]; then node "$ROOT/scripts/t58-bots.cjs" --remove-fixtures || status=1; fi
  if $probe_created; then rm -f -- "$probe" "$SERVER/plugins/.paper-remapped/$(basename "$probe")"; fi
  if port_open && $owned; then echo 'El puerto 25566 sigue abierto.' >&2; status=1; fi
  echo "Evidencia: $T58_RESULTS"
  exit "$status"
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM
if [[ $T58_RUN_PART == zones ]]; then
  navigation_root="$ROOT/.agent/t58-navigation"
  if ! node -e 'process.exit(require(process.argv[1]).version==="2.4.5"?0:1)' "$navigation_root/node_modules/mineflayer-pathfinder/package.json" 2>/dev/null; then
    npm --cache "$ROOT/.agent/npm-cache" install --prefix "$navigation_root" --no-save --package-lock=false --ignore-scripts --omit=dev mineflayer-pathfinder@2.4.5
  fi
  export T58_NAV_PACKAGE="$navigation_root/package.json"
fi
node --check "$ROOT/scripts/t58-bots.cjs"
if [[ -z ${T58_RECOVERY_SOURCE:-} ]]; then node "$ROOT/scripts/t58-bots.cjs" --prepare; else
  prior_nonce=$(node "$ROOT/scripts/t58-recovery.cjs" "$T58_RECOVERY_SOURCE" "$SERVER" "$ROOT/scripts/t58-arena-v1.json")
  if [[ -f $T58_RECOVERY_SOURCE/arena-original.json.gz ]]; then
    cp -- "$T58_RECOVERY_SOURCE/arena-original.json.gz" "$T58_RESULTS/arena-original.json.gz"
    export T58_RECOVER_NONCE="$prior_nonce" T58_RECOVER_MODE=restore
  else
    export T58_RECOVER_NONCE="$T58_ID" T58_RECOVER_MODE=restore-flat
    echo 'Journal antiguo solo en memoria: se recupera la base plana exclusiva del runner.'
  fi
fi
operation deploy
probe_cp=$(python3 - "$ROOT" <<'PY'
from pathlib import Path
import sys
jars=sorted(Path('/home/dasan/.gradle/caches/modules-2/files-2.1').rglob('*.jar'))
print(':'.join([str(Path(sys.argv[1])/'build/classes/java/main')]+[str(p) for p in jars]))
PY
)
/usr/lib/jvm/temurin-25-jdk-arm64/bin/javac -cp "$probe_cp" -d "$T58_RESULTS/probe-classes" "$ROOT/scripts/T58GameplayProbe.java" "$ROOT/scripts/T58Arena.java"
printf 'name: T58GameplayProbe\nversion: 1\nmain: T58GameplayProbe\napi-version: "26.3"\ndepend: [CustomDungeons]\n' > "$T58_RESULTS/probe-classes/plugin.yml"
cp "$ROOT/scripts/t58-arena-v1.json" "$T58_RESULTS/probe-classes/t58-arena-v1.json"
/usr/lib/jvm/temurin-25-jdk-arm64/bin/jar cf "$T58_RESULTS/probe.jar" -C "$T58_RESULTS/probe-classes" .
# Atomic exclusive publication: never replace an existing plugin with this name.
python3 - "$T58_RESULTS/probe.jar" "$probe" <<'PYPUBLISH'
from pathlib import Path
import os, shutil, sys, tempfile
source, target=map(Path,sys.argv[1:])
# Both publication names are inside the server mount; linking across sandbox roots is forbidden.
fd, staging=tempfile.mkstemp(prefix='.'+target.name+'.',suffix='.tmp',dir=target.parent)
try:
    with os.fdopen(fd,'wb') as out, source.open('rb') as incoming:
        shutil.copyfileobj(incoming,out)
    os.link(staging,target)  # Atomic and fails rather than replacing an existing plugin.
finally:
    Path(staging).unlink(missing_ok=True)
PYPUBLISH
probe_created=true
if port_open || pgrep -f '[p]aper-26[.]3' >/dev/null; then echo 'Paper está activo; no se arranca.' >&2; exit 1; fi
owned=true
operation start
if [[ -n ${T58_RECOVERY_SOURCE:-} ]]; then
  node "$ROOT/scripts/t58-bots.cjs" --recover-arena | tee "$T58_RESULTS/results.log"
else
  node "$ROOT/scripts/t58-bots.cjs" --run | tee "$T58_RESULTS/results.log"
fi
operation stop
cp "$SERVER/logs/latest.log" "$T58_RESULTS/server.log"
owned=false
if [[ -n ${T58_RECOVERY_SOURCE:-} ]]; then echo 'Arena recuperada; servidor apagado. No declara aceptación.'; exit 0; fi
if [[ ${T58_NATIVE_HOLE:-0} == 1 || ${T58_NATIVE_CORNER:-0} == 1 || ${T58_NATIVE_RETURN:-0} == 1 || ${T58_NATIVE_RAMP:-0} == 1 ]]; then
  [[ $T58_RUN_PART == zones && -f $T58_RESULTS/native-proof.json ]] || exit 1
  echo 'Diagnóstico vanilla guardado; no declara aceptación.'
  exit 0
fi
SPARK_JAR=$(rg --files "$SERVER/libraries/me/lucko/spark-paper" | rg 'spark-paper-.*[.]jar$' | sort | tail -1)
cold_label=warden-dungeon
[[ $T58_RUN_PART == trio ]] && cold_label=cold-trio
[[ $T58_RUN_PART == zones ]] && cold_label=zones-room-vortex
/usr/lib/jvm/temurin-25-jdk-arm64/bin/java -cp "$SPARK_JAR" "$ROOT/scripts/T58SparkWindows.java" \
  "$T58_RESULTS/$cold_label-0-30.sparkprofile" "$T58_RESULTS/$cold_label-31-91.sparkprofile" "$T58_RESULTS/$cold_label-91-end.sparkprofile" \
  | tee "$T58_RESULTS/cold-windows.json"
if [[ -z ${T58_ACCEPTANCE_RESULTS:-} ]]; then
  node "$ROOT/scripts/t58-profile-windows.cjs" "$T58_RESULTS/cold-windows.json"
fi
if [[ $T58_RUN_PART == warden || $T58_RUN_PART == zones ]]; then
/usr/lib/jvm/temurin-25-jdk-arm64/bin/java -cp "$SPARK_JAR" "$ROOT/scripts/SparkCombatProfile.java" "$T58_RESULTS/zero.sparkprofile" "$T58_RESULTS/profile.sparkprofile" | tee "$T58_RESULTS/spark-summary.json"
if [[ -z ${T58_ACCEPTANCE_RESULTS:-} ]]; then
python3 - "$T58_RESULTS/spark-summary.json" <<'PYSPARK'
import json, sys
assert json.load(open(sys.argv[1]))['activePluginMsPerTick'] <= .5, 'T58a plugin cost exceeds 0.5 ms/tick'
PYSPARK
fi
fi

evidence_logs=("$T58_RESULTS/server.log")
if [[ -f $T58_RESULTS/observed-server.log ]]; then evidence_logs+=("$T58_RESULTS/observed-server.log"); fi
if rg -q 'T58_PROBE_ERROR|Could not pass event.*CustomDungeons|Error occurred while (enabling|disabling) CustomDungeons' "${evidence_logs[@]}"; then
  echo 'Error del plugin o la sonda en el log; revisar evidencia.' >&2; exit 1
fi

if [[ $T58_RUN_PART == zones && -z ${T58_SCENARIOS:-} && ${T58_SECONDS:-120} == 120 ]]; then
  node - "$T58_RESULTS" "$ROOT" <<'JSACCEPT'
const fs=require('node:fs'),path=require('node:path');
const dir=process.argv[2],root=process.argv[3];
const read=name=>JSON.parse(fs.readFileSync(path.join(dir,name)));
const acceptance=require(root+'/scripts/t58-zone-acceptance.cjs').validateSuite(read('summary.json'),read('cold-windows.json'),read('spark-summary.json'));
fs.writeFileSync(path.join(dir,'acceptance.json'),JSON.stringify(acceptance,null,2));
console.log('PASS T58b '+JSON.stringify(acceptance));
JSACCEPT
fi
