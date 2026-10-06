#!/usr/bin/env bash
set -euo pipefail
[[ $# == 1 && -f "$1" ]] || { echo 'Uso: sign-release.sh <jar>' >&2; exit 2; }
JAR=$1
NAME=$(basename -- "$JAR")
VERSION=${NAME#CustomDungeons-}
VERSION=${VERSION%.jar}
[[ "$NAME" == CustomDungeons-*.jar && ${#VERSION} -le 256 && "$VERSION" =~ ^(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)(-[0-9A-Za-z-]+(\.[0-9A-Za-z-]+)*)?(\+[0-9A-Za-z-]+(\.[0-9A-Za-z-]+)*)?$ ]] || {
  echo 'Nombre de jar inválido: debe ser CustomDungeons-<semver>.jar.' >&2; exit 2;
}
PRE=${VERSION%%+*}
if [[ "$PRE" == *-* ]]; then
  PRE=${PRE#*-}
  IFS=. read -r -a IDENTIFIERS <<< "$PRE"
  for IDENTIFIER in "${IDENTIFIERS[@]}"; do
    if [[ "$IDENTIFIER" =~ ^[0-9]+$ && ${#IDENTIFIER} -gt 1 && "$IDENTIFIER" == 0* ]]; then
      echo 'Pre-release inválida: identificador numérico con cero inicial.' >&2; exit 2
    fi
  done
fi
KEY=${CD_SIGNING_KEY:-"$HOME/.config/customdungeons/release-signing.key"}
[[ -f "$KEY" ]] || { echo 'No se encontró la clave de firma externa.' >&2; exit 1; }
ROOT=$(cd "$(dirname "$0")/.." && pwd -P)
KEY_PATH=$(realpath -- "$KEY")
# Never read a private key located inside this worktree or its shared git repository.
COMMON_GIT=$(git -C "$ROOT" rev-parse --path-format=absolute --git-common-dir)
MAIN_REPO=$(dirname -- "$COMMON_GIT")
KEY_GIT=$(git -C "$(dirname -- "$KEY_PATH")" rev-parse --path-format=absolute --git-common-dir 2>/dev/null || true)
[[ "$KEY_GIT" != "$COMMON_GIT" ]] || { echo 'La clave privada debe estar fuera del repositorio.' >&2; exit 1; }
case "$KEY_PATH" in
  "$ROOT"/*|"$MAIN_REPO"/*) echo 'La clave privada debe estar fuera del repositorio.' >&2; exit 1 ;;
esac
umask 077
TEMP=$(mktemp -- "${JAR}.sig.tmp.XXXXXX")
trap 'rm -f -- "$TEMP"' EXIT
openssl pkeyutl -sign -rawin -inkey "$KEY_PATH" -in "$JAR" -out "$TEMP"
# Also checks that the maintainer supplied the Ed25519 key trusted by the plugin.
openssl pkeyutl -verify -rawin -pubin -inkey "$ROOT/docs/reference/release-signing.pub" -in "$JAR" -sigfile "$TEMP" >/dev/null
[[ $(wc -c < "$TEMP") == 64 ]] || { echo 'La firma debe tener 64 bytes.' >&2; exit 1; }
mv -f -- "$TEMP" "${JAR}.sig"
echo "Firma generada: ${JAR}.sig"
