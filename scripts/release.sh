#!/usr/bin/env bash
# Publica una release firmada de CustomDungeons.
# Uso: scripts/release.sh <archivo-de-notas.md>
# Requisitos: rama main limpia y sincronizada, versión ya fijada en build.gradle.kts,
# clave privada en CD_SIGNING_KEY (por defecto ~/.config/customdungeons/release-signing.key) y gh autenticado.
set -euo pipefail
ROOT=$(cd "$(dirname "$0")/.." && pwd)
cd "$ROOT"
NOTES=${1:?"Uso: scripts/release.sh <notas.md>"}
[[ -f "$NOTES" ]] || { echo "No existe el archivo de notas: $NOTES" >&2; exit 1; }

[[ "$(git rev-parse --abbrev-ref HEAD)" == main ]] || { echo "Debe ejecutarse en main." >&2; exit 1; }
[[ -z "$(git status --porcelain)" ]] || { echo "Hay cambios sin commitear." >&2; exit 1; }
git fetch -q origin main
[[ "$(git rev-parse HEAD)" == "$(git rev-parse origin/main)" ]] || { echo "main no está sincronizada con origin/main." >&2; exit 1; }

VERSION=$(sed -n 's/^version = "\(.*\)"/\1/p' build.gradle.kts)
[[ "$VERSION" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]] || { echo "Versión no publicable: '$VERSION' (quita -SNAPSHOT)." >&2; exit 1; }
TAG="v$VERSION"
git rev-parse -q --verify "refs/tags/$TAG" >/dev/null && { echo "La etiqueta $TAG ya existe." >&2; exit 1; }

# Build limpio: evita recursos cacheados con una versión antigua.
rm -rf build/libs build/resources
./gradlew build -q --no-daemon
JAR="build/libs/CustomDungeons-$VERSION.jar"
[[ -f "$JAR" ]] || { echo "No se generó $JAR" >&2; exit 1; }
INNER=$(unzip -p "$JAR" paper-plugin.yml | sed -n "s/^version: '\{0,1\}\([^']*\)'\{0,1\}$/\1/p")
[[ "$INNER" == "$VERSION" ]] || { echo "paper-plugin.yml dice '$INNER' y el build '$VERSION'." >&2; exit 1; }

scripts/sign-release.sh "$JAR"
openssl pkeyutl -verify -rawin -pubin -inkey docs/reference/release-signing.pub -in "$JAR" -sigfile "$JAR.sig" >/dev/null \
  || { echo "La firma no verifica con la clave pública del repo." >&2; exit 1; }

git tag -a "$TAG" -m "CustomDungeons $TAG"
git push -q origin "$TAG"
gh release create "$TAG" "$JAR" "$JAR.sig" --title "CustomDungeons $TAG" --notes-file "$NOTES"
echo "Publicada $TAG con firma."
