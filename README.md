# CustomDungeons

Plugin para **Paper 26.3 y Java 25** que permite crear dungeons de varias salas mediante GUI y herramientas: oleadas, mobs personalizados, habilidades, combos, fases de jefe y premios para los supervivientes. Cada dungeon admite una sola partida activa; no crea instancias por grupo.

## Instalación

1. Detén el servidor Paper 26.3 y comprueba que utiliza Java 25.
2. Copia `CustomDungeons-1.0.0.jar` a `plugins/` y arranca el servidor. Paper descarga las bibliotecas de base de datos en el primer arranque; necesita acceso a sus repositorios.
3. Revisa `plugins/CustomDungeons/config.yml`. Prepara un mundo dedicado y configura `dungeon-world.name` (por defecto `dungeons`); `auto-create: true` permite crearlo vacío si no existe. Construye suelo y salas antes de jugar.
4. Reinicia tras ajustar la configuración y concede los permisos de administración a quienes editarán las dungeons.

Todas las integraciones son opcionales; CustomDungeons arranca sin ellas:

| Plugin | Uso |
|---|---|
| Vault y un proveedor de economía | Premios en dinero. Sin economía disponible se omite el dinero y se avisa en consola. |
| LuckPerms | Gestionar los nodos de permiso de CustomDungeons. |
| WorldGuard | Proteger el terreno; CustomDungeons no sustituye esa protección. Consulta la [guía de flags y spawn de mobs](docs/guides/worldguard.md). |
| Multiverse-Core | Crear y cargar el mundo dedicado. |
| Multiverse-Portals 5.3.0+ | Entrada mediante portales que ejecutan el comando `join`. |

## Primeros pasos

1. Ejecuta `/customdungeon`. En **Biblioteca de mobs**, crea una plantilla con un id único (minúsculas, números, `_` o `-`, hasta 32 caracteres). Elige tipo, estadísticas, equipo, pociones y habilidades; añade combos o fases si será un jefe. **Guardar** valida y persiste la plantilla.
2. En el editor del mob, usa **Probar en vivo** para invocarlo junto a ti con habilidades, combos y fases. Requiere `customdungeons.admin.test`; puedes alternar invulnerabilidad y detener la prueba desde el menú. También termina al alejarte más de 48 bloques o al vencer `live-test.max-seconds` (300 por defecto).
3. Vuelve a la lista de dungeons y usa **Añadir** para crear, por ejemplo, `cripta`. En **Ajustes**, define lobby, salida, vidas, jugadores, cuenta atrás y cooldown. El máximo `0` significa sin límite; por defecto, morir deja caer el inventario.
4. Usa las herramientas del menú o `/customdungeon tool <tipo>`. `region` y `door` seleccionan dos esquinas con clic izquierdo/derecho sobre bloques; `point` registra tu posición con clic derecho; `spawner` registra el punto adyacente a la cara pulsada. Vuelve al campo correspondiente de la GUI y haz clic izquierdo para aplicar la selección o el punto.
5. Añade salas en orden: región, checkpoint, puerta y modo de desbloqueo. Cada sala necesita spawners con ubicación, radio y oleadas; cada entrada de oleada referencia una plantilla, cantidad y retardo. La última sala puede quedar sin puerta. Para puertas con llave, selecciona una plantilla portadora presente en la sala.
6. Configura el premio, activa la dungeon y pulsa **Guardar**; corrige los errores de validación que aparezcan. `/customdungeon show cripta` previsualiza sus elementos durante 30 segundos. Solo puede editarla un administrador a la vez y debe estar libre.
7. Ejecuta `/customdungeon test cripta` para probar sin premios. Usa `skipwave` e `invulnerable` durante la prueba; finaliza con `stop cripta`. Después comprueba la entrada normal con `/customdungeon join cripta`.

Para un portal, selecciona su volumen con `/mvp wand` (o `//wand` de WorldEdit) y ejecuta como administrador:

```text
/mvp create entrada_cripta
/mvp modify entrada_cripta action-type command
/mvp modify entrada_cripta action "console:customdungeon join %player% cripta"
/mvp modify entrada_cripta action-success-message @disabled
/mvp info entrada_cripta
```

El placeholder del portal es **`%player%`**; `console:` ejecuta la entrada como consola. El jugador necesita `multiverse.portal.access.entrada_cripta`, `customdungeons.player.join` y, si la dungeon lo exige, `customdungeons.join.cripta`. CustomDungeons valida estado, permisos, límite y cooldown incluso al entrar por consola. Consulta la [guía de Multiverse-Portals](docs/guides/multiverse-portals.md) y la [prueba de cruce real](docs/guides/pruebas-integradas.md#reproducción-de-entrada-y-partida).

## Comandos y permisos

Todos los subcomandos de la tabla llevan `/customdungeon` delante. Los alias se configuran en `command-aliases` y, por defecto, no hay ninguno. Referencia: [spec §12](docs/spec.md#12-comandos-y-permisos-rf-cmd), [árbol de comandos](src/main/java/dev/dasan/customdungeons/command/CustomDungeonCommand.java) y [declaración de permisos](src/main/resources/paper-plugin.yml).

| Comando | Permiso | Uso |
|---|---|---|
| `/customdungeon` | `customdungeons.admin.edit` | Abrir el menú (jugador). |
| `tool <tipo>` | `customdungeons.admin.tools` | Recibir `region`, `door`, `spawner` o `point` (jugador). |
| `test <dungeon>` | `customdungeons.admin.test` | Iniciar una prueba sin premios (jugador). |
| `start <dungeon>` | `customdungeons.admin.control` | Forzar el inicio de un lobby existente. |
| `stop <dungeon>` | `customdungeons.admin.control` | Detener una partida existente. |
| `reset <dungeon>` | `customdungeons.admin.control` | Resetear una partida existente. |
| `show <dungeon>` | `customdungeons.admin.edit` | Previsualizar durante 30 segundos (jugador). |
| `reload` | `customdungeons.admin.reload` | Recargar definiciones y mensajes; requiere todas las dungeons libres. |
| `debug` | `customdungeons.admin.debug` | Alternar depuración y registro de ticks de la partida (jugador). |
| `join <dungeon>` | `customdungeons.player.join` | Entrar; añade `customdungeons.join.<id>` si se exige permiso específico. |
| `join <jugador> <dungeon>` | Consola o `customdungeons.admin.join.others` | Introducir un jugador conectado; el destinatario sigue necesitando sus permisos de entrada. |
| `leave` | `customdungeons.player.leave` | Abandonar la partida (jugador). |
| `stats` | `customdungeons.player.stats` | Consultar estadísticas propias (jugador). |
| `claim` | `customdungeons.player.claim` | Recoger premios pendientes (jugador). |
| `skipwave` | `customdungeons.admin.test` o `customdungeons.admin.debug`, según el modo | Saltar oleada solo dentro de una partida de prueba o con debug activado. |
| `invulnerable` | `customdungeons.admin.test` o `customdungeons.admin.debug`, según el modo | Alternar invulnerabilidad con las mismas condiciones que `skipwave`. |
| Bypass de cooldown (sin comando) | `customdungeons.bypass.cooldown` | Ignorar el cooldown de entrada. |
| Bypass de límite (sin comando) | `customdungeons.bypass.limit` | Ignorar el máximo de jugadores. |

`customdungeons.admin` agrupa los nodos de administración y se concede a operadores por defecto. `customdungeons.player` y `customdungeons.player.*` agrupan `join`, `leave`, `stats` y `claim`, habilitados por defecto. Los bypass y `customdungeons.join.*` son de operador por defecto. La variante con nombre también permite a un jugador introducirse a sí mismo, manteniendo sus comprobaciones de entrada.

## Configuración y datos

Los archivos están en `plugins/CustomDungeons/`:

- `config.yml`: prefijo, idioma (`es`/`en`), base de datos, mundo, valores iniciales de dungeon, límites de mobs y partículas, tipos que admiten armadura, sonidos, alias y duración de pruebas en vivo. Consulta el [archivo predeterminado](src/main/resources/config.yml).
- `messages.yml` / `messages_en.yml`: textos personalizables. El prefijo predeterminado es `&8[&6CustomDungeons&8] `; se aceptan colores `&`, hex `&#RRGGBB` y MiniMessage.
- `dungeons/<id>.yml` y `mobs/<id>.yml`: definiciones guardadas por la GUI, también editables a mano.
- SQLite (predeterminado): `database.type: sqlite`, archivo `data.db`, sin servidor externo. MySQL: `database.type: mysql` y claves `host`, `port`, `database`, `user`, `password`, `pool-size`; prepara la base de datos y un usuario con permisos para crear y actualizar sus tablas.

**`/customdungeon reload` no recarga la configuración operativa de `config.yml`: reinicia el servidor para aplicar sus cambios.** Recarga dungeons, mobs y mensajes cuando no hay sesiones activas; el código también relee el prefijo y el idioma, pero no reconstruye los servicios de base de datos, límites, valores por defecto o alias. Una definición inválida genera avisos y puede dejar la dungeon desactivada.

## Recuperación tras caídas

Al arrancar, las partidas interrumpidas quedan **abortadas**, se limpian mobs y llaves y se restauran puertas y bloques temporales desde los datos persistidos. Los jugadores afectados vuelven a la salida al conectarse; las partidas no se reanudan. Si un mundo aún no está cargado, sus bloques se restauran cuando se cargue, y las entidades antiguas se limpian al cargar sus chunks.

Conserva copias del mundo, la carpeta del plugin y la base de datos: son necesarias para recuperar el estado. Comprueba en consola `Run recovery` y carga los mundos implicados antes de volver a abrir el acceso. Los premios que no caben en el inventario quedan pendientes para `/customdungeon claim`.

## Compatibilidad con otros plugins
- **EssentialsX AntiBuild**: cancela los drops de los jugadores sin `essentials.build.drop.*`, de modo que al morir en una dungeon (o al aplicar la penalización por desconexión) los ítems desaparecen. Concede ese permiso en el mundo de dungeons, sin dar permisos de construcción ni OP; por ejemplo con LuckPerms: `lp group default permission set essentials.build.drop.* true world=cd_dungeons`. Detalles en `docs/guides/pruebas-integradas.md` (T49).

## Guías y referencias

- [Servidor de pruebas y mundo dedicado](docs/guides/servidor-de-pruebas.md).
- [Dungeon demo: construcción, configuración y recorrido en la GUI](docs/guides/dungeon-demo.md).
- [Multiverse-Portals](docs/guides/multiverse-portals.md) y [WorldGuard](docs/guides/worldguard.md).
- [Pruebas integradas: resultados y pendientes](docs/guides/pruebas-integradas.md). El informe conserva hallazgos de T18; consulta su estado posterior en [tareas](docs/tasks.md#bugs-de-t18-ver-docsguidespruebas-integradasmd).
- [Habilidades y parámetros](docs/reference/habilidades.md); [ejemplos de dungeons](docs/reference/ejemplos/dungeons/) y [plantillas de mobs](docs/reference/ejemplos/mobs/). Adapta mundos, coordenadas y referencias antes de usarlos.
- [Especificación](docs/spec.md) y [arquitectura](ARCHITECTURE.md).

## Compilar

Con JDK 25, desde la raíz del repositorio:

```bash
./gradlew build
```

Gradle ejecuta las pruebas y genera **`build/libs/CustomDungeons-1.0.0.jar`**. Copia ese archivo a `plugins/` con el servidor detenido.
