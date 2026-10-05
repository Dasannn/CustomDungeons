# Arquitectura — CustomDungeons

Ver `docs/constitution.md` (principios) y `docs/spec.md` (requisitos).

## Stack
- Java 25, Gradle (Kotlin DSL), un único módulo. `paper-api` 26.3 (`compileOnly`).
- `paper-plugin.yml`; comandos con Brigadier (Paper Commands API).
- HikariCP + driver SQLite/MySQL cargados en runtime por el *library loader* de Paper (no se sombrean).
- Vault: `compileOnly`, dependencia opcional (soft). Sin dependencia de código con LuckPerms.
- Tests: JUnit 5 para lógica pura (sin servidor).

## Paquetes
Raíz `dev.dasan.customdungeons` (provisional). Cada paquete tiene una responsabilidad y depende solo de los de abajo en la lista o de `model`.

| Paquete | Responsabilidad |
|---|---|
| `model` | Records inmutables: `DungeonDef`, `RoomDef`, `SpawnerDef`, `WaveDef`, `WaveEntry`, `MobTemplate`, `AbilityInstance`, `RewardDef`, `Region`. Sin Bukkit salvo `Location`/`ItemStack`. |
| `config` | Carga/guarda `config.yml`, `messages.yml`, `dungeons/*.yml`, `mobs/*.yml` ↔ `model`. Validación. Traducción de colores `&`/hex/MiniMessage → `Component`. |
| `storage` | `Database` (Hikari, SQLite/MySQL), repositorios asíncronos (`CompletableFuture`) para partidas, cooldowns, claims, stats. Migraciones simples por versión de esquema. |
| `session` | `DungeonSession` (máquina de estados), `SessionManager` (una por dungeon), `SessionTicker` (la única tarea por partida), seguimiento de jugadores y mobs, puertas, BossBar, recuperación al arranque. |
| `mob` | `MobFactory`: crea la entidad desde `MobTemplate`, aplica stats/equipo/pociones, marca PDC (`session`, `template`). |
| `ability` | `Ability` (interfaz), `AbilityRegistry`, `AbilityContext`, disparadores, selector de objetivos, telegraph, `TempBlockService`. Una clase por habilidad en `ability.impl`. |
| `reward` | Cálculo del reparto y entrega (ítems, Vault, XP, comandos, claims). |
| `gui` | Framework mínimo de menús (`Menu`, `Button`, paginación, borradores) + menús concretos + `ParamEditor` genérico + Dialog API. |
| `tool` | Herramientas de admin (ítems PDC), selecciones por admin, previsualización de partículas. |
| `command` | Árbol Brigadier `/customdungeon`, alias desde config. |
| `listener` | Eventos Bukkit; solo enrutan a `session`, `ability`, `tool`, `gui`. Sin lógica propia. |

## Flujo de una partida
```
join → SessionManager.get(dungeon).join(player)
  LIBRE → LOBBY: teleport al lobby, cuenta atrás (SessionTicker)
  LOBBY → EN_CURSO: cierra entrada, sala 0, programa oleada 0
  SessionTicker (cada tick, 1 tarea por partida):
    - planificador de oleadas: lanza entradas según modo/retardo → MobFactory
    - habilidades CADA_X_SEG / JUGADOR_EN_RANGO / VIDA_BAJO_% (contadores de ticks)
    - telegraphs pendientes, bloques temporales con caducidad
    - correa de mobs (cada 20 ticks), BossBar (al cambiar), tiempo límite
  Eventos (listener) → AL_GOLPEAR, AL_RECIBIR_DAÑO, AL_MORIR, muerte de jugador, movimiento
  sala limpia → abrir puerta (restaurar aire), checkpoint++ ; última → COMPLETADA
  COMPLETADA → reward ; FALLIDA → nada ; ambas → RESETEO → LIBRE
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
- Estado por mob (cooldowns, fases disparadas) en un objeto ligero dentro de `DungeonSession`, indexado por UUID. Nada en tareas propias.
- Proyectiles lanzados por habilidades se marcan con PDC para atribuir daño y evitar daño a bloques (`EntityExplodeEvent` → limpiar lista de bloques).
- `TempBlockService`: coloca bloques solo sobre aire, registra en memoria y en BD/archivo para restaurar tras crash.

## Persistencia
- Definiciones: YAML (versionable, editable a mano). Caché en memoria; escritura asíncrona al guardar desde la GUI.
- Datos de juego: BD. Todo vía `CompletableFuture`; el resultado vuelve al hilo principal con el scheduler cuando toca Bukkit.
- Recuperación: `active_sessions` y bloques temporales persistidos → `RecoveryService` al `onEnable`.

## Puntos de extensión para fases futuras
- Portales: llamarán a `SessionManager.join(...)` (RF-PAR-01). No se implementa nada por adelantado.
- Instancias: `SessionManager` ya es el único que mapea dungeon → sesión.

## Rendimiento
- Comprobación de región en `PlayerMoveEvent` solo si cambia de bloque y el jugador está en `Set<UUID>` de sesiones.
- Partículas con `receivers` filtrados por distancia; densidad desde config.
- Chunk tickets (`Chunk#addPluginChunkTicket`) en salas de partidas activas; se liberan al resetear.

## Pruebas
- Unitarias: máquina de estados, planificador de oleadas, reparto de premios, validación de config, parseo de colores.
- Integración: servidor de pruebas en `~/Desktop/Proyectos/plugins/servidor/Servidor` (`start.sh`), comandos `test`/`debug`, checklist por funcionalidad, spark para MSPT.
