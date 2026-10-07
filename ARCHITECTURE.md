# Arquitectura — CustomDungeons

Ver `docs/constitution.md` (principios) y `docs/spec.md` (requisitos). Estado: **v1.0.1 publicada**, **v1.1.0 en construcción** (T35a–T45). Las secciones marcadas *(pendiente: TNN)* describen el diseño aprobado de tareas aún no integradas.

## Stack
- Java 25, Gradle 9.8 (Kotlin DSL), un único módulo. `paper-api` 26.3 (`compileOnly`).
- `paper-plugin.yml`; comandos con Brigadier (Paper Commands API); Dialog API para entradas de texto/número.
- HikariCP + driver SQLite/MySQL cargados en runtime por el *library loader* de Paper (no se sombrean).
- Vault: `compileOnly`, dependencia opcional (soft). Sin dependencia de código con LuckPerms, WorldGuard ni Multiverse (integración por comandos y permisos).
- Tests: JUnit 5 + Mockito para lógica y menús (sin servidor), `PaperApiTestBootstrap` compartido para registros de Bukkit. Instantáneas de GUI (`./gradlew guiSnapshots` → JSON → `scripts/render-gui.py` → PNG).

## Paquetes
Raíz `dev.dasan.customdungeons`. Cada paquete tiene una responsabilidad y depende solo de los de abajo en la lista o de `model`.

| Paquete | Responsabilidad |
|---|---|
| `model` | Records inmutables: `DungeonDef`, `RoomDef`, `RoomAmbience`, `SpawnerDef`, `SpawnerPreset`, `WaveDef`, `WaveEntry`, `MobTemplate`, `AbilityInstance`, `ComboDef`, `PhaseDef`, `RewardDef`, `ScalingDef`, `Region`, `Point`, enums (`UnlockMode`, `SpawnMode`, `Trigger`, `TargetMode`, `HookEvent`). Los campos nuevos se añaden **de forma aditiva** conservando los constructores previos (YAML antiguos siguen cargando). Sin Bukkit salvo `Location`/`ItemStack`. |
| `config` | `ConfigLoader`/`PluginConfig` y `AmbienceSettings` (`config.yml`), `ConfigMigration` (catálogos versionados con históricos en `defaults-history/`), `DefinitionCodec` (YAML ↔ `model`), `DefinitionStore` (caché + E/S asíncrona de `dungeons/`, `mobs/`, `spawners/`), `SpawnerPresets` (resolución de plantillas), `Validator`, `EntityHeights`. |
| `text` | `Messages` (catálogo es/en) y `Text` (colores `&`, `&#RRGGBB`, MiniMessage → `Component`). |
| `storage` | `SqlStorage` (Hikari, SQLite/MySQL), repositorios asíncronos (`CompletableFuture`): partidas, sesiones activas, bloques temporales, claims, estadísticas. Migraciones por versión de esquema. |
| `runtime` | Contratos entre sesión y habilidades: `SessionContext`, `ActiveMob`, `TempBlocks`, `TickScheduler`. |
| `session` | `SessionStateMachine` (puro) y `DungeonSession`/`DungeonSessionRuntime`; `SessionManager` (una sesión por dungeon); `SessionTicker` (la única tarea por partida); `WaveScheduler`, `RoomProgress`, `DoorService`, `KeyService` (llaves de portador y por comando), `SessionBossBar`, `SessionAmbience`/`AmbiencePolicy`/`AmbienceEffects`, `ScoreboardTemplates`/`SidebarData`/`SessionSidebar`, `SessionChunks`, `RecoveryService`, `RunRecorder`, `JoinRules`/`JoinSpamGuard`. |
| `mob` | `MobFactory` (entidad desde `MobTemplate` + escalado), `BossController` (fases, música, BossBar), `LiveTestService` (probar en vivo), `MobKeys` (PDC), `Scaling`. |
| `ability` | `Ability`, `AbilityRegistry`, `AbilityEngine`, `AbilityContext`, `ParamSpec`, `TargetSelector`, `Telegraph`, `ComboRunner`, `Effects`. Una clase por habilidad en `ability.impl`. |
| `reward` | `RewardService`: ítems, Vault, XP, comandos y claims para supervivientes. |
| `integration` | `VaultHook`, `CommandHooks` (comandos de consola por transición de estado). |
| `gui` | Framework de menús GUI v2 (ver «GUI v2»): `Menu`, `PagedMenu`, `Button`, `Draft`, `EditLocks`, `GuiTheme`, `GuiLayout`, `Inputs`/`PendingInputs` (Dialog API), `MenuListener`. `gui.menu`: menús concretos. `gui.wizard`: estado, reglas, progreso y borradores del asistente. |
| `tool` | Herramientas de admin con PDC (`ToolService`, `ToolType`, `ToolMaterials`, `ToolInventory`, `ToolListener`), selecciones, `PreviewRenderer` (partículas de previsualización), `WizardParticles`, `SpawnerMarkers`, y el **modo construcción** (`BuildModeService`, `BuildModeListener`, `BuildTools`). |
| `update` | Actualizador firmado: `UpdateService` (GitHub Releases), `SignatureVerifier` (Ed25519), `JarInspector`, `SemVer`. |
| `command` | Árbol Brigadier `/customdungeon` (join, leave, create, build, key give, tool, update, reload, test…), `ClaimHandler`. |
| `listener` | Eventos Bukkit que solo enrutan (`AbilityProtectionListener`); el resto de listeners vive junto a su servicio (`SessionListener`, `ToolListener`, `BuildModeListener`, `MenuListener`). |

## Flujo de una partida
```
portal MV-Portals / jugador → /customdungeon join [jugador] <dungeon> → SessionManager.get(dungeon).join(player)
  (rechazo si la dungeon se está vaciando: queda algún jugador de la partida anterior en su área o regiones)
  LIBRE → LOBBY: se persiste la posición previa del jugador (destino PREVIOUS) antes del teleport al lobby
    startMode AUTO:   mínimo de jugadores → cuenta atrás lobbyCountdownSeconds
    startMode PLATES: todas las placas de inicio pisadas → cuenta atrás corta (cancelable)
  LOBBY → EN_CURSO: se abre la puerta de entrada (si existe), título y sonido; TP a la sala 0 solo si teleportOnStart
    cinemática opcional (espectador temporal, ruta calculada desde área/salas)          (pendiente: T41)
  SessionTicker (cada tick, 1 tarea por partida):
    - activación por entrada: cada 10 ticks, la sala se activa cuando entra el primer jugador
    - planificador de oleadas → MobFactory; habilidades por contador; telegraphs; bloques temporales
    - correa de mobs (cada 20 ticks), BossBar, tiempo límite, scoreboard (≤1/s, solo filas/título que cambian)
  Eventos → AL_GOLPEAR, AL_RECIBIR_DAÑO, AL_MORIR, muerte de jugador, movimiento, desconexión
  sala limpia → AUTOMÁTICO: abrir puerta | LLAVE: soltar llave del portador (o «último mob», *) |
                LLAVE EXTERNA: esperar llave entregada por `key give` (puzzle)
  puerta abierta (ambiente: sonido/partículas/título) → siguiente sala; última sala → COMPLETADA
  COMPLETADA → premio a supervivientes → finishMode IMMEDIATE | DELAYED (gracia) | NONE (bloqueo + red 300 s);
               placas de salida activas (TP individual al destino EXIT | PREVIOUS)
  FALLIDA → mismo finishMode, sin placas de salida; tiempo límite agotado o parada administrativa → TP forzado
  Al completar o fallar: restaurar puertas y bloques, limpiar llaves y entidades; bloquear hasta vaciar área y regiones
  RESETEO → liberar ticker, BossBar y chunk tickets → LIBRE
  desconexión voluntaria → abandono; al reconectar: morir en la posición guardada y reaparecer en cama/spawn (pendiente: T44)
  cada transición de estado → CommandHooks (on-lobby-open, on-full, on-start, on-complete, on-fail, on-free)
```

## Scoreboard de sesión (T42)
- `ScoreboardTemplates` compila `scoreboard.enabled/title/footer/refresh-seconds/lines.<estado>` al cargar los servicios. Cadenas o `{text, when}` con condiciones cerradas; colores vía `Text`, placeholders insertados como componentes literales, datos ausentes ocultos y separadores normalizados. Diseño predeterminado de hasta diez filas contando título y pie. Validación conservadora de todas las condiciones juntas (hasta 15 entradas); configuración inválida avisa y usa la plantilla predeterminada. En runtime, un exceso se recorta a 15 con aviso único por plantilla.
- `SidebarData` toma datos de la sesión en memoria: grupo inicial/activos, bajas por jugador acumuladas (incluidos quienes salen), duración congelada al finalizar, placas distintas ocupadas, mobs vivos en la región actual, jefe estable por orden de aparición y fases activadas. La oleada solo se resume con un único spawner. Objetivo y corazones salen del catálogo; hasta diez corazones, después «❤ ×N». Tiempo restante rojo bajo un minuto. `SidebarData.Inputs` conserva solo datos simples y la revisión del catálogo; su igualdad decide si es necesario crear componentes.
- `SessionSidebar` guarda la referencia previa por jugador y crea un scoreboard privado con identidades de fila estables y formato numérico vacío (API pública Paper). El ticker existente comprueba el intervalo antes de copiar colecciones o consultar jugadores; captura datos como máximo una vez por segundo y solo construye componentes al cambiar esos datos. Después compara componentes y publica solo cambios. Las excepciones de captura, render y API se registran en el borde del panel, se libera su propiedad y se intenta restaurar el previo sin interrumpir join, la partida ni su limpieza. Al salir, desconectar, quedar sin vidas, abandonar el área final o deshabilitar, restaura el previo; si otro plugin toma el control, cede sin volver a imponer el panel. `PlayerChangedWorldEvent` reconcilia la pertenencia vigente: lobby/combate conservan sus participantes al cambiar de mundo; un participante desvinculado o que sale físicamente tras finalizar recupera el previo. COMPLETADA/FALLIDA se muestran mientras T38 retiene a los participantes; DELAYED usa su cuenta atrás real, IMMEDIATE no añade ninguna espera. Las plantillas `intro` no activan la cinemática pendiente de T41.

## Ambiente de puertas y salas (T43)
- `RoomDef.ambience` es opcional y aditivo; conserva los constructores previos. `RoomAmbience` contiene solo los campos cambiados; ausente hereda `ambience.defaults`, cadena/lista vacía desactiva. Codec y todas las reconstrucciones de salas conservan los overrides. `AmbienceSettings` compila defaults y valida claves/tipos/rangos; defaults inválidos avisan y usan el valor épico de respaldo. Los títulos predeterminados traducibles usan `@clave` de `messages.yml`; `{room}` inserta el nombre/id de la sala.
- `SessionAmbience` corre en el ticker existente: entrada ligada a la activación T38, efectos y comprobación de sala cada diez ticks, partículas cada segundo (máximo 32 muestras por jugador; filtro por distancia y presupuesto global). Las puertas emiten muestras repartidas por su región, sonido de portón y retumbe; título y temblor optativos. La limpieza incluye también la última sala.
- `AmbiencePolicy` separa la propiedad de efectos, el muestreo y la música para pruebas puras. `AmbienceEffects` no sustituye efectos existentes; los renueva con leases de 20–60 ticks y registra solo los propios en PDC del jugador. Cambios externos revocan la propiedad, incluso si son idénticos. Salida de región, cambio de mundo, muerte, desconexión y fin limpian solo leases reconocidos. Al conectar/reactivar se limpia el journal persistido tras caída; cualquier lease sin journal caduca rápidamente, sin penalización ni E/S adicional durante el juego.
- Música de sala en `RECORDS`, con longitudes de la configuración musical existente; se detiene al salir o limpiar. `BossController` la detiene antes de reproducir música de jefe/fase; el jefe tiene prioridad y la sala puede reanudarse después. No se crean tareas nuevas.
- GUI verde de seis filas según §4.11: Ambiente en slot 43 del editor de sala (37/39/41/43), selectores existentes, efectos con nivel/partículas, entrada manual de sonidos para resource packs, reset con Shift. Duraciones, densidades numéricas, retumbe y título de puerta en el submenú de efectos; desactivación de partículas escribiendo vacío con Shift+izquierdo; densidades por clic derecho y reset de partículas/densidad con Shift+derecho. Rangos compartidos temporalmente en `AmbienceSettings.RANGES`, con TODO de integración de `NumericRange` en T46.

## Habilidades
```java
interface Ability {
  String id();
  List<ParamSpec<?>> params();          // tipo, rango, default → genera el editor GUI
  void execute(AbilityContext ctx);     // caster, objetivos resueltos, params, session
}
```
- `AbilityRegistry` registra todas las implementaciones al arrancar; `AbilityInstance` (model) = id + disparador + selector + valores.
- Combos: `ComboRunner` ejecuta los pasos desde el `SessionTicker` (contador de ticks por paso); cancelado si el mob muere.
- Fases de jefe: `BossController` comprueba umbrales en `AL_RECIBIR_DAÑO`; aplica cambios de la fase una sola vez; música en bucle por clave configurable.
- Estado por mob (cooldowns, fases disparadas) en un objeto ligero dentro de la sesión, indexado por UUID. Nada en tareas propias.
- Proyectiles de habilidades marcados con PDC; las explosiones no dañan bloques.
- `TempBlocks`: coloca bloques solo sobre aire, registra en memoria y BD para restaurar tras crash.

## Definiciones, plantillas y borradores
- **Dungeons** (`dungeons/<id>.yml`), **mobs** (`mobs/<id>.yml`) y **plantillas de spawner** (`spawners/<id>.yml`, T36). Un `SpawnerDef` con `presetId` resuelve sus oleadas desde la plantilla al iniciar la partida; ubicación y radio son de la sala. «Hacer propio» copia las oleadas y desvincula. Borrar una plantilla en uso convierte esas salas en copias locales.
- **Área** opcional (`DungeonDef.area`, T29): si existe, el Validator exige que salas, puertas (incluida la entrada), lobby, checkpoints, spawners y placas estén dentro. El punto de salida debe quedar fuera del área y de todas las regiones de salas, puertas y entrada, también al elegir el destino PREVIOUS.
- **Borradores**: los editores trabajan sobre un `Draft` con referencia esperada (la versión vigente: publicada o borrador persistente). Guardar valida con el Validator y detecta conflictos con otros admins (`EditLocks`). El asistente (T29) y el modo construcción (T40) guardan borradores persistentes por dungeon y admin, reanudables.
- **Vidas**: 1–100 (límite del plugin), definidos por `config.DungeonLimits.MIN_LIVES/MAX_LIVES`, compartidos por el Validator y la carga de valores predeterminados; definición disponible para los rangos visibles de T46.
- **Validator**: errores bloquean guardar/activar; avisos (p. ej. vida > 1024 recortada, llave en la última sala) se muestran sin invalidar.
- **Rangos numéricos** (T46): cada campo numérico tiene una única definición de rango y origen (límite de Minecraft o del plugin), compartida por el Validator, `ParamSpec`, los botones de la GUI (lore "Rango: min–max · origen") y los diálogos de entrada. Escala de mob 0–16 (atributo `scale` vanilla).

## GUI v2 (T35a/T35b)
- Diseño en `docs/reference/gui-design-v2.md`: borde del color de la categoría, resumen en slot 4, ❔ ayuda en slot 8, cabeceras de sección (cristal blanco con brillo; lima ✔ / rojo ✖), relleno gris, fila inferior contextual, `GRAY_DYE` con motivo para lo no disponible, lenguaje de iconos único.
- **Ciclo de vida**: cada `Menu` liga su listener a la **instancia concreta de inventario** (`bindInventoryListener`); los cierres de inventarios anteriores no desregistran el actual; si `openInventory` se cancela, se libera al instante. Por defecto toda ranura que no sea de entrada cancela clics; las ranuras de entrada se declaran con `reservedInputSlots()` y el relleno nunca las ocupa. Los depósitos (equipo, premio) se devuelven una sola vez; al cerrar por muerte, al suelo.
- **Asistente** (T29, `WizardMenu` + `gui.wizard`): 7 pasos (Área → Lobby y salida → Spawners → Salas → Reglas → Premio → Revisión); reutiliza los menús del editor como submenús; scoreboard de progreso, BossBar e instrucciones mientras dura; validación cacheada por apertura.
- **Modo construcción** (T40): `/customdungeon build <id>`; respaldo persistente del inventario (ver Persistencia), barra de 9 herramientas (puerta de entrada con Shift en 3; inicio/salida con Shift en 5), deshacer (20 acciones), partículas siempre activas y actionbar con sala y recuentos de placas. `PlateTool` comparte la colocación y los mensajes de T38; deshacer compara las listas persistidas y comprueba todos los bloques cargados antes de revertirlos, sin sobrescribir cambios ajenos. `WizardParticles` incluye puerta de entrada y placas de inicio/salida con colores distintos, bajo el presupuesto compartido del `PreviewRenderer`.

## Persistencia
- Definiciones: YAML (versionable, editable a mano). Caché en memoria; escritura asíncrona al guardar desde la GUI. La carga inicial y `reload` leen, decodifican y validan en segundo plano, después de las escrituras pendientes; ambas cachés se publican juntas en el hilo principal. Mientras cargan, se rechazan joins, pruebas de partida y edición; al deshabilitar no se publican resultados pendientes.
- Catálogos de mensajes y `config.yml` versionados: al subir la versión se guarda el catálogo anterior en `defaults-history/`; `ConfigMigration` actualiza los textos no personalizados y añade las claves nuevas.
- Datos de juego: BD. Todo vía `CompletableFuture`; el resultado vuelve al hilo principal con el scheduler cuando toca Bukkit.
- Excepción permitida: `ItemStack.serializeItemsAsBytes`/`deserializeItemsFromBytes` puede ejecutarse en el executor de BD (solo NBT/DataFixer, sin acceso a mundo). `ItemStack.serialize()` (YAML) solo en el hilo principal. Los `ItemStack` resultantes se entregan al jugador ya en el hilo principal.
- Inventario de construcción (T40): cada respaldo tiene generación UUID y estado persistente `ACTIVE → RESTORED`. Solo el marcador `active:<generación>` permite restaurar esa copia exacta; un respaldo sin marcador nunca reemplaza el inventario actual. Salir o cancelar escribe `restored:<generación>` en memoria y conserva los bytes originales. Únicamente un `PlayerJoinEvent` real, que carga conjuntamente inventario y marcador desde el registro vanilla guardado, confirma la persistencia de la restauración y permite retirar esa generación. Reactivar el plugin con jugadores conectados no constituye confirmación y no borra respaldos. Esto evita E/S síncrona adicional (`saveData`) durante el juego; los respaldos sin confirmación se conservan.
- Posición previa de cada jugador (destino PREVIOUS) y salida de respaldo persistidas antes del TP al lobby; recuperación con timeout de 10 s por consulta/carga y fallback PREVIOUS → EXIT → spawn del mundo principal. Posición de desconexión (RF-DESC-01) pendiente de T44.
- Recuperación: `active_sessions`, puertas (incluida la de entrada) y bloques temporales persistidos → `RecoveryService` al `onEnable`. Una caída del servidor nunca se penaliza como desconexión voluntaria.

## Actualizador y releases
- `/customdungeon update` consulta GitHub Releases, descarga el jar y su `.sig`, verifica la firma Ed25519 con la clave pública embebida e inspecciona el jar (`plugin` y versión) antes de dejarlo en `plugins/update/`. Sin firma válida no se instala nada.
- Releases con `scripts/release.sh` (build limpio, versión del jar, firma con `scripts/sign-release.sh`, verificación, tag y `gh release create`). La clave privada vive solo en la Pi.

## Tareas programadas (presupuesto)
- Una tarea por partida activa (`SessionTicker`), una por prueba en vivo (`LiveTestService`) y una compartida de previsualización (`PreviewRenderer`, cada 10 ticks) que solo trabaja mientras hay admins con herramientas, asistente o modo construcción activos. Asistente, modo construcción, scoreboard, ambiente (T43) y cinemática *(T41)* se apoyan en estas tareas; ninguna clase crea tareas repetitivas nuevas.

## Puntos de extensión
- Portales: los gestiona Multiverse-Portals, que ejecuta `join` (RF-INT-01).
- Puzzles: cualquier mecanismo externo (bloques de comando, otros plugins) entrega llaves con `/customdungeon key give` (RF-LLA-03).
- Instancias: `SessionManager` es el único que mapea dungeon → sesión.

## Rendimiento
- Comprobación de región por posición solo para jugadores en sesión (activación por entrada cada 10 ticks, no por `PlayerMoveEvent`).
- Partículas con receptores filtrados y muestras acotadas (≤ 256 por previsualización); geometría calculada fuera del hilo principal.
- Chunk tickets en salas de partidas activas; se liberan al resetear.
- Medido (Guarida del Warden, jefe a escala 5): 0,19 ms/tick atribuidos a CustomDungeons (objetivo RNF-02 ≤ 2 ms).

## Pruebas
- Unitarias y de menús: máquina de estados, planificador de oleadas, premios, validación, colores, ciclo de vida de menús, respaldos de inventario.
- Instantáneas de GUI revisadas como PNG antes de integrar cualquier cambio de GUI (`docs/guides/gui-snapshots.md`).
- Integración: servidor de **agentes** `~/Desktop/Proyectos/plugins/servidor/Servidor-agentes` (puerto 25566, por defecto en `scripts/test-server.sh`) con bots mineflayer; el servidor del usuario (25565) solo con `CD_TARGET=user`. spark para MSPT.
