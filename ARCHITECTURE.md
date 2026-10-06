# Arquitectura — CustomDungeons

Ver `docs/constitution.md` (principios) y `docs/spec.md` (requisitos).

## Stack
- Java 25, Gradle (Kotlin DSL), un único módulo. `paper-api` 26.3 (`compileOnly`).
- `paper-plugin.yml`; comandos con Brigadier (Paper Commands API).
- HikariCP + driver SQLite/MySQL cargados en runtime por el *library loader* de Paper (no se sombrean).
- Vault: `compileOnly`, dependencia opcional (soft). Sin dependencia de código con LuckPerms, WorldGuard ni Multiverse (integración por comandos y permisos).
- Tests: JUnit 5 para lógica pura (sin servidor).

## Paquetes
Raíz `dev.dasan.customdungeons`. Cada paquete tiene una responsabilidad y depende solo de los de abajo en la lista o de `model`.

| Paquete | Responsabilidad |
|---|---|
| `model` | Records inmutables: `DungeonDef`, `RoomDef`, `SpawnerDef`, `WaveDef`, `WaveEntry`, `MobTemplate`, `AbilityInstance`, `RewardDef`, `Region`. Sin Bukkit salvo `Location`/`ItemStack`. |
| `config` | Carga/guarda `config.yml`, `messages.yml`, `dungeons/*.yml`, `mobs/*.yml` ↔ `model`. Validación. Traducción de colores `&`/hex/MiniMessage → `Component`. |
| `storage` | `Database` (Hikari, SQLite/MySQL), repositorios asíncronos (`CompletableFuture`) para partidas, cooldowns, claims, stats. Migraciones simples por versión de esquema. |
| `session` | `DungeonSession` (máquina de estados), `SessionManager` (una por dungeon), `SessionTicker` (la única tarea por partida), seguimiento de jugadores y mobs, puertas, BossBar, recuperación al arranque. |
| `mob` | `MobFactory`: crea la entidad desde `MobTemplate`, aplica stats/equipo/pociones y escalado, marca PDC (`session`, `template`). `BossController`: fases, música, BossBar de jefe. `LiveTestService`: "probar en vivo" fuera de partida. |
| `ability` | `Ability` (interfaz), `AbilityRegistry`, `AbilityContext`, disparadores, selector de objetivos, telegraph, `ComboRunner`, `TempBlockService`. Una clase por habilidad en `ability.impl`. |
| `reward` | Entrega a supervivientes (ítems, Vault, XP, comandos, claims). |
| `integration` | Vault (economía), ganchos de comandos por dungeon (`CommandHooks`, usados para Multiverse-Portals u otros). |
| `gui` | Framework mínimo de menús (`Menu`, `Button`, paginación, borradores) + menús concretos + `ParamEditor` genérico + Dialog API. |
| `tool` | Herramientas de admin (ítems PDC), selecciones por admin, previsualización de partículas. |
| `command` | Árbol Brigadier `/customdungeon`, alias desde config. |
| `listener` | Eventos Bukkit; solo enrutan a `session`, `ability`, `tool`, `gui`. Sin lógica propia. |

## Flujo de una partida
```
portal MV-Portals / jugador → /customdungeon join [jugador] <dungeon> → SessionManager.get(dungeon).join(player)
  LIBRE → LOBBY: teleport al lobby, cuenta atrás (SessionTicker)
  LOBBY → EN_CURSO: cierra entrada, sala 0, programa oleada 0
  SessionTicker (cada tick, 1 tarea por partida):
    - planificador de oleadas: lanza entradas según modo/retardo → MobFactory
    - habilidades CADA_X_SEG / JUGADOR_EN_RANGO / VIDA_BAJO_% (contadores de ticks)
    - telegraphs pendientes, bloques temporales con caducidad
    - correa de mobs (cada 20 ticks), BossBar (al cambiar), tiempo límite
  Eventos (listener) → AL_GOLPEAR, AL_RECIBIR_DAÑO, AL_MORIR, muerte de jugador, movimiento
  última oleada de la sala → AUTOMATICO: abrir puerta | LLAVE: soltar llave (PDC) y esperar clic en puerta
  puerta abierta → checkpoint++ ; primera oleada al entrar un jugador ; última sala → COMPLETADA
  COMPLETADA → reward a supervivientes ; FALLIDA → nada ; ambas → RESETEO → LIBRE
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
- Fases de jefe: `BossController` comprueba umbrales en `AL_RECIBIR_DAÑO`; aplica cambios de la fase una sola vez; música en bucle con `Player#playSound`/`stopSound` por clave configurable.
- Estado por mob (cooldowns, fases disparadas) en un objeto ligero dentro de `DungeonSession`, indexado por UUID. Nada en tareas propias.
- Proyectiles lanzados por habilidades se marcan con PDC para atribuir daño y evitar daño a bloques (`EntityExplodeEvent` → limpiar lista de bloques).
- `TempBlockService`: coloca bloques solo sobre aire, registra en memoria y en BD/archivo para restaurar tras crash.

## Persistencia
- Definiciones: YAML (versionable, editable a mano). Caché en memoria; escritura asíncrona al guardar desde la GUI.
- Datos de juego: BD. Todo vía `CompletableFuture`; el resultado vuelve al hilo principal con el scheduler cuando toca Bukkit.
- Excepción permitida: `ItemStack.serializeItemsAsBytes`/`deserializeItemsFromBytes` puede ejecutarse en el executor de BD (solo NBT/DataFixer, sin acceso a mundo). Los `ItemStack` resultantes se entregan al jugador ya en el hilo principal.
- Recuperación: `active_sessions` y bloques temporales persistidos → `RecoveryService` al `onEnable`.

## Puntos de extensión
- Portales: los gestiona Multiverse-Portals, que ejecuta `join` (RF-INT-01).
- Instancias: `SessionManager` ya es el único que mapea dungeon → sesión.

## Rendimiento
- Comprobación de región en `PlayerMoveEvent` solo si cambia de bloque y el jugador está en `Set<UUID>` de sesiones.
- Partículas con `receivers` filtrados por distancia; densidad desde config.
- Chunk tickets (`Chunk#addPluginChunkTicket`) en salas de partidas activas; se liberan al resetear.

## Pruebas
- Unitarias: máquina de estados, planificador de oleadas, reparto de premios, validación de config, parseo de colores.
- Integración: servidor de pruebas en `~/Desktop/Proyectos/plugins/servidor/Servidor` (`start.sh`), comandos `test`/`debug`, checklist por funcionalidad, spark para MSPT.
