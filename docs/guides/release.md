# Releases firmadas

El actualizador requiere un jar y una firma Ed25519 pura de sus bytes. La clave
pública de confianza está en `docs/reference/release-signing.pub` y en
`SignatureVerifier`; la clave privada nunca entra en Git ni en sus worktrees.
El mantenedor conserva la clave correspondiente en
`~/.config/customdungeons/release-signing.key` con permisos `600`.
No se genera otra clave para una release: no sería aceptada por plugins instalados.

## Proceso del mantenedor

1. Ajustar la versión de release en el proceso de versionado del proyecto y ejecutar
   `./gradlew build`. El `paper-plugin.yml` debe identificar `CustomDungeons` con
   exactamente esa versión semántica.
2. Firmar `scripts/sign-release.sh build/libs/CustomDungeons-<semver>.jar`.
   Puede elegirse una clave externa con `CD_SIGNING_KEY=/ruta/externa/release-signing.key`.
   El script rechaza claves dentro del repositorio, comprueba la firma con la clave
   pública de confianza y produce `<jar>.sig` (64 bytes). No imprime la clave.
3. Publicar, mediante el proceso autorizado del mantenedor, una release con tag
   `v<semver>` (también se acepta `<semver>`) y los assets exactos
   `CustomDungeons-<semver>.jar` y `CustomDungeons-<semver>.jar.sig`.
   No modificar el jar después de firmarlo. Las URLs deben ser HTTPS.

## Administración del servidor

El permiso es `customdungeons.admin.update`, incluido en `customdungeons.admin`;
la consola puede utilizarlo. `/customdungeon update check` solo consulta la última
release. `/customdungeon update` solicita confirmar con
`/customdungeon update confirm` en un máximo de 60 segundos. La confirmación está
ligada al emisor y al repositorio configurado, se consume una vez y no descarga
nada si la versión remota no es estrictamente mayor (los metadatos de build no
incrementan la precedencia).

Las descargas se limitan a 20 MiB y la firma a 64 bytes, incluidos cuerpos chunked.
Todas las peticiones y redirecciones usan HTTPS, con certificado de confianza,
User-Agent y límites de 10 segundos. Tras verificar primero la firma y después
el descriptor, el archivo temporal de `plugins/CustomDungeons/update-tmp` se mueve
atómicamente a `Bukkit.getUpdateFolderFile()`, conservando el nombre del jar cargado
para que Bukkit lo reemplace. Si ambos directorios están en sistemas de archivos
distintos, la operación falla sin copiar. Los temporales se limpian al finalizar.
Nunca se carga el jar descargado ni se reinicia el servidor automáticamente.

`updater.enabled` y `updater.check-on-startup` son `true` por defecto;
`updater.repository` es `Dasannn/CustomDungeons`. En el arranque solo se avisa en
consola de nuevas versiones o errores. Se admite `updater.api-base-url`
(`https://api.github.com/` por defecto) para pruebas contra una API HTTPS local:
debe terminar en `/` y servir `repos/<repository>/releases/latest`, con `tag_name`,
`html_url` y `assets` (`name`, `size`, `browser_download_url`). No se admiten HTTP,
credenciales en URLs ni certificados sin confianza. Las claves de firma generadas
en tests se inyectan únicamente en tests; la clave de producción no es configurable.
