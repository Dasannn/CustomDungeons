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
| `model` | Records inmutables: `DungeonDef`, `RoomDef`, `SpawnerDef`, `SpawnerPreset`, `WaveDef`, `WaveEntry`, `MobTemplate`, `AbilityInstance`, `ComboDef`, `PhaseDef`, `RewardDef`, `ScalingDef`, `Region`, `Point`, enums (`UnlockMode`, `SpawnMode`, `Trigger`, `TargetMode`, `HookEvent`). Los campos nuevos se añaden **de forma aditiva** conservando los constructores previos (YAML antiguos siguen cargando). Sin Bukkit salvo `Location`/`ItemStack`. |
| `config` | `ConfigLoader`/`PluginConfig` (`config.yml`), `ConfigMigration` (catálogos versionados con históricos en `defaults-history/`), `DefinitionCodec` (YAML ↔ `model`), `DefinitionStore` (caché + E/S asíncrona de `dungeons/`, `mobs/`, `spawners/`), `SpawnerPresets` (resolución de plantillas), `Validator`, `EntityHeights`. |
| `text` | `Messages` (catálogo es/en) y `Text` (colores `&`, `&#RRGGBB`, MiniMessage → `Component`). |
| `storage` | `SqlStorage` (Hikari, SQLite/MySQL), repositorios asíncronos (`CompletableFuture`): partidas, sesiones activas, bloques temporales, claims, estadísticas. Migraciones por versión de esquema. |
| `runtime` | Contratos entre sesión y habilidades: `SessionContext`, `ActiveMob`, `TempBlocks`, `TickScheduler`. |
| `session` | `SessionStateMachine` (puro) y `DungeonSession`/`DungeonSessionRuntime`; `SessionManager` (una sesión por dungeon); `SessionTicker` (la única tarea por partida); `WaveScheduler`, `RoomProgress`, `DoorService`, `KeyService` (llaves de portador y por comando), `SessionBossBar`, `SessionChunks`, `RecoveryService`, `RunRecorder`, `JoinRules`/`JoinSpamGuard`. |
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
    - correa de mobs (cada 20 ticks), BossBar, tiempo límite, scoreboard (≤1/s, pendiente: T42)
  Eventos → AL_GOLPEAR, AL_RECIBIR_DAÑO, AL_MORIR, muerte de jugador, movimiento, desconexión
  sala limpia → AUTOMÁTICO: abrir puerta | LLAVE: soltar llave del portador (o «último mob», *) |
                LLAVE EXTERNA: esperar llave entregada por `key give` (puzzle)
  puerta abierta (ambiente: sonido/partículas/título, pendiente: T43) → siguiente sala; última sala → COMPLETADA
  COMPLETADA → premio a supervivientes → finishMode IMMEDIATE | DELAYED (gracia) | NONE (bloqueo + red 300 s);
               placas de salida activas (TP individual al destino EXIT | PREVIOUS)
  FALLIDA → mismo finishMode, sin placas de salida; tiempo límite agotado o parada administrativa → TP forzado
  Al completar o fallar: restaurar puertas y bloques, limpiar llaves y entidades; bloquear hasta vaciar área y regiones
  RESETEO → liberar ticker, BossBar y chunk tickets → LIBRE
  desconexión voluntaria → abandono; al reconectar: morir en la posición guardada y reaparecer en cama/spawn (pendiente: T44)
  cada transición de estado → CommandHooks (on-lobby-open, on-full, on-start, on-complete, on-fail, on-free)
```

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
- **Validator**: errores bloquean guardar/activar; avisos (p. ej. vida > 1024 recortada, llave en la última sala) se muestran sin invalidar.
- **Rangos numéricos** (T46): `config.NumericRanges` define límites, decimales y origen mediante `NumericRange`; el marcador `vanillaZero` permite el cero de los stats sin aceptar overrides de vida inferiores a 1. `gui.NumericInputs` usa esa definición en lore y `Inputs` (cuerpo del diálogo y alternativa por clics); el Validator usa los mismos objetos. Los parámetros específicos se adaptan desde el `ParamSpec` registrado, conservando intacto el contrato T01. El Validator puede recibir el registro real por constructor; el constructor anterior usa las habilidades predeterminadas.
  - Escala editable 0–16, con 4 decimales y 0 = vanilla. Sobre 10, el editor y el Validator avisan sin bloquear. `EntityHeights` y la estimación de espacio de la prueba en vivo usan la escala efectiva (`max(0.0625, escala)` para overrides positivos).
  - Límites comprobados por inspección estática de Paper **26.3 build 157**, sin ejecutar el servidor: `Attributes` registra `scale` con 0.0625–16 y `max_health` desde 1 hasta el máximo de Spigot (predeterminado 1024); `MobEffectInstance` recorta el amplificador a 0–255 (nivel visible 1–256). La API pública conserva `Attribute.SCALE`, `MAX_HEALTH`, `MOVEMENT_SPEED` y `KNOCKBACK_RESISTANCE` ([API Paper](https://jd.papermc.io/paper/26.3/org/bukkit/attribute/Attribute.html), [PotionEffect](https://jd.papermc.io/paper/26.3/org/bukkit/potion/PotionEffect.html)). Esta inspección es evidencia de desarrollo: el plugin no usa NMS.
  - Vida 1–1024, velocidad 0–1, daño 0–1000 y resistencia 0–1 son restricciones **del plugin**, no rangos completos de Minecraft: Paper permite ampliar vida/velocidad/daño en [spigot.yml](https://docs.papermc.io/paper/reference/spigot-configuration/), y el atributo de resistencia de esta versión admite -2–1. La vida usa el límite predeterminado 1024 aunque un servidor lo amplíe. Los demás topes de edición son del plugin; los amplificadores limitados a 10 en algunas habilidades también.

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
- Una tarea por partida activa (`SessionTicker`), una por prueba en vivo (`LiveTestService`) y una compartida de previsualización (`PreviewRenderer`, cada 10 ticks) que solo trabaja mientras hay admins con herramientas, asistente o modo construcción activos. Asistente, modo construcción, scoreboard *(T42)*, ambiente *(T43)* y cinemática *(T41)* se apoyan en estas tareas; ninguna clase crea tareas repetitivas nuevas.

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
