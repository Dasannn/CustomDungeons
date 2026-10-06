> **Dos servidores.** Los agentes y bots usan el servidor de **agentes** (`servidor/Servidor-agentes`, puerto **25566**), que es el destino por defecto de `scripts/test-server.sh`. El servidor del **usuario** (`servidor/Servidor`, puerto 25565) solo se toca con `CD_TARGET=user scripts/test-server.sh ...` y avisando antes.

# Servidor de pruebas

Preparación de T18 en `feat/t18-prep`, 6 de octubre de 2026 (los timestamps del log reproducidos abajo son los del servidor). Servidor: `/home/dasan/Desktop/Proyectos/plugins/servidor/Servidor`; Paper **26.3-157-main@4728a90**, API `26.3.build.157-beta`, Java 25 en `/usr/lib/jvm/temurin-25-jdk-arm64`.

## Control con el script

Desde este worktree:

```bash
scripts/test-server.sh start
scripts/test-server.sh cmd 'mv list'
scripts/test-server.sh log 80
scripts/test-server.sh stop
```

`cmd` envía un único comando a la consola sin `/` inicial; no admite saltos de línea. Su éxito indica envío, por lo que siempre hay que leer el log para comprobar el resultado. `start` espera `Done (`; `stop` espera el cierre, sin matar procesos por fuerza. Ambos tienen timeout de 180 segundos. `deploy` compila el jar y sustituye `plugins/CustomDungeons.jar`, con el servidor detenido; **no se ejecutó** en esta preparación.

El script usa la sesión `screen` `cd-test`, sockets privados en `.customdungeons-screen`, un lock compartido `.customdungeons-test.lock`, y consola temporal en `.agent/server-console.log`. No arranques una segunda instancia si indica que Paper ya está activo. Si indica `Otra operación del servidor está en curso.`, espera dos minutos y reintenta, como máximo diez reintentos; no borres el lock. En esta ejecución no hubo contención.

En el sandbox actual, finalizar la llamada de terminal puede terminar los procesos descendientes de `screen`. Ejecuta todo el ciclo `start` → `cmd` → `log` → `stop` en la misma llamada/sesión de terminal, manteniéndola abierta hasta apagar. El primer arranque llegó a `Done`, pero su sesión terminó al cerrarse la llamada; sus comandos posteriores no llegaron a la consola (`No screen session found`). Se retiró solo el socket muerto con:

```bash
SCREENDIR=/home/dasan/Desktop/Proyectos/plugins/servidor/Servidor/.customdungeons-screen screen -wipe
```

## Plugins

Se añadieron únicamente estos dos jars desde la API de Modrinth, filtrando `26.3` en `game_versions` y `paper` en `loaders`, y seleccionando la publicación más reciente. T18 menciona Core 5.3.0, pero la petición de preparación requiere la última versión compatible.

| Plugin | Versión | Archivo | Publicación UTC |
|---|---|---|---|
| 5.8.1 | 5.8.1 | `multiverse-core-5.8.1.jar` | 2026-08-28T11:02:26.105984Z |
| 5.3.0 | 5.3.0 | `multiverse-portals-5.3.0.jar` | 2026-08-21T16:05:19.108815Z |

SHA-512 verificados contra la API **antes de copiar**:

- `multiverse-core-5.8.1.jar`:

  ```text
  322c1f4dc1abcd30e0f55aa9f378c7031846d1e59a5156480f9199050dc104c19468c9891c47be35e4dde68742ec4420ea974ec8850db6a259f97ffa1b1c51ea
  ```

- `multiverse-portals-5.3.0.jar`:

  ```text
  7314d9f7c14fa4706b1283c80a1b3baf2c113f299510d98033be201c07979b283c637d1cc29a2a3f026779b7e70cc7ca53c92649af215a0f887acd803991f8e3
  ```

Procedimiento de descarga utilizado (metadata y descargas temporales en `.agent/`, ignorado por Git):

```bash
mkdir -p .agent
python3 - <<'PYDOWNLOAD'
import urllib.request, json, pathlib, hashlib
server = pathlib.Path('/home/dasan/Desktop/Proyectos/plugins/servidor/Servidor')
for slug in ['multiverse-core', 'multiverse-portals']:
    request = urllib.request.Request(
        f'https://api.modrinth.com/v2/project/{slug}/version',
        headers={'User-Agent': 'CustomDungeons-T18-prep'})
    data = json.load(urllib.request.urlopen(request))
    versions = [v for v in data
                if '26.3' in v['game_versions'] and 'paper' in v['loaders']]
    versions.sort(key=lambda v: v['date_published'], reverse=True)
    if not versions:
        raise SystemExit(f'No compatible version: {slug}')
    v = versions[0]
    pathlib.Path(f'.agent/{slug}-version.json').write_text(json.dumps(v, indent=2))
    f = next(f for f in v['files'] if f['primary'])
    data = urllib.request.urlopen(f['url']).read()
    if hashlib.sha512(data).hexdigest() != f['hashes']['sha512']:
        raise SystemExit('Hash incorrecto ' + slug)
    dest = server / 'plugins' / f['filename']
    if dest.exists():
        raise SystemExit('Ya existe ' + str(dest))
    (pathlib.Path('.agent') / f['filename']).write_bytes(data)
    dest.write_bytes(data)
PYDOWNLOAD
```

En esta ejecución la selección y la instalación se hicieron en dos llamadas equivalentes, con comprobación previa de Paper detenido y snapshot SHA-512 de los jars existentes. Los jars anteriores conservaron sus hashes. No se editaron ni reemplazaron otros plugins o sus configuraciones; al arrancar y cargar mundos, los propios plugins escriben sus datos habituales.

Plugins preexistentes que el log reconoce: CustomDungeons 1.0.0-SNAPSHOT; CustomCrafting 4.19.1.0-ld.1; Essentials/EssentialsAntiBuild/EssentialsProtect 2.22.1-dev+24-49a2f10; LuckPerms 5.5.71; NBTAPI 2.16.1; SocialBlueprint 1.0; Vault 1.7.3-b131; ViaBackwards/ViaVersion 5.12.0; WolfyUtilities 4.19.1.2; WorldEdit 7.4.6-beta-02+2c90a77a1; WorldGuard 7.0.19+2400-f395a16. Los `.bak` no son plugins activos. Spark viene integrado en Paper.

Fuentes de versiones: [API Core](https://api.modrinth.com/v2/project/multiverse-core/version), [API Portals](https://api.modrinth.com/v2/project/multiverse-portals/version).

## Mundo cd_dungeons

Se creó con Multiverse, entorno NORMAL y tipo FLAT vacío: una capa de `minecraft:air`, bioma `minecraft:the_void`, sin estructuras ni ajuste de spawn. No requiere instalar otro generador. La consulta corregida devolvió `No Generator Plugins found.`. En esta versión el comando para listar generadores es **`mv generators list`**, aunque la documentación web aún describe `mv generators`.

Comandos enviados en el ciclo de creación (misma sesión de terminal):

```bash
scripts/test-server.sh start
scripts/test-server.sh cmd 'mv version'
scripts/test-server.sh cmd 'mv generators'
scripts/test-server.sh cmd 'mv list'
scripts/test-server.sh cmd 'mvp list'
scripts/test-server.sh cmd 'mv create cd_dungeons normal --world-type flat --generator-settings {"layers":[{"block":"minecraft:air","height":1}],"biome":"minecraft:the_void","structure_overrides":[]} --no-structures --no-adjust-spawn'
scripts/test-server.sh cmd 'mv info cd_dungeons'
scripts/test-server.sh cmd 'mv list'
scripts/test-server.sh cmd 'mv generators --page 1'
scripts/test-server.sh stop
```

Los dos intentos de consulta `mv generators`/`mv generators --page 1` mostraron ayuda, sin modificar mundos. Se corrigió la consulta al verificar la persistencia con un segundo ciclo:

```bash
scripts/test-server.sh start
scripts/test-server.sh cmd 'mv generators list'
scripts/test-server.sh cmd 'mv info cd_dungeons'
scripts/test-server.sh cmd 'mv list'
scripts/test-server.sh log 52
scripts/test-server.sh stop
```

Entre envío y lectura se esperaron 3–8 segundos en la creación y 4 segundos en la verificación. No vuelvas a ejecutar `mv create` si el mundo ya existe. Multiverse lo registra como `minecraft:cd_dungeons`, nombre Bukkit `cd_dungeons`, con `auto-load: true`, generador vacío y settings flat persistidos en `plugins/Multiverse-Core/worlds.yml`.

El mundo vacío no ofrece una superficie segura: construye lobby, salas y salida antes de permitir jugadores. La configuración actual de CustomDungeons conserva `dungeon-world.name: dungeons` y `auto-create: false`; para la integración futura hay que apuntarla a `cd_dungeons`. Esa configuración no se cambió en esta preparación.

## Evidencia y alcance

Extractos del ciclo de creación en `logs/latest.log` (conservados temporalmente en `.agent/t18-server.log`, sin versionar):

```text
[00:34:47] [Server thread/INFO]: [Multiverse-Core] Enabling Multiverse-Core v5.8.1
[00:34:49] [Server thread/INFO]: [Multiverse-Core] Version 5.8.1 (API v5.8) Enabled - By dumptruckman, Rigby, fernferret, lithium3141, main--, benwoo1110 and Zax71
[00:34:53] [Server thread/INFO]: [Multiverse-Portals] Enabling Multiverse-Portals v5.3.0
[00:34:54] [Server thread/INFO]: [Multiverse-Portals] Version 5.3.0 (API v5.3) Enabled - By Rigby, fernferret and benwoo1110
[00:35:40] [Server thread/INFO]: - World Type: FLAT
[00:35:40] [Server thread/INFO]: - Generator Settings: {"layers":[{"block":"minecraft:air","height":1}],"biome":"minecraft:the_void","structure_overrides":[]}
[00:35:40] [Server thread/INFO]: - Structures: false
[00:35:42] [Server thread/INFO]: World 'cd_dungeons' created!
[00:36:03] [Server thread/INFO]: World Type: FLAT
[00:36:03] [Server thread/INFO]: Generator Settings: {"layers":[{"block":"minecraft:air","height":1}],"biome":"minecraft:the_void","structure_overrides":[]}
[00:36:03] [Server thread/INFO]: Generate Structures: false
[00:36:12] [Server thread/INFO]: Stopping server
[00:36:12] [Server thread/INFO]: [Multiverse-Portals] Disabling Multiverse-Portals v5.3.0
[00:36:12] [Server thread/INFO]: [Multiverse-Core] Disabling Multiverse-Core v5.8.1
```

Ambos plugins llegaron a Enabled sin excepciones. Hubo avisos de spawn inseguro del Nether en el primer arranque, actualizaciones disponibles de Essentials y consulta de actualización HTTP 404 de SocialBlueprint; no son errores de carga de Multiverse. El mundo se guardó y el servidor se apagó mediante `stop`. El ciclo posterior confirmó carga persistente y apagado limpio (log final, copia temporal `.agent/t18-restart.log`):

```text
[00:36:54] [Server thread/INFO]: [WorldGuard] Loaded configuration for world 'cd_dungeons'
[00:37:04] [Server thread/INFO]: No Generator Plugins found.
[00:37:04] [Server thread/INFO]: World Key: minecraft:cd_dungeons
[00:37:04] [Server thread/INFO]: World Type: FLAT
[00:37:08] [Server thread/INFO]: Stopping server
[00:37:08] [Server thread/INFO]: [Multiverse-Portals] Disabling Multiverse-Portals v5.3.0
[00:37:08] [Server thread/INFO]: [Multiverse-Core] Disabling Multiverse-Core v5.8.1
```

Pendientes para T18 completo tras T09/T14/T15/T16: portal y entrada con clientes, criterios funcionales 1–7, prueba de 50 mobs y spark/MSPT, armadura visible en 26.3 y comportamiento de WorldGuard/combate. No se marca T18 completada.

Guías relacionadas: [Multiverse-Portals](multiverse-portals.md), [WorldGuard](worldguard.md), [habilidades](../reference/habilidades.md).
