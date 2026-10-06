# Plan de implementación — CustomDungeons MVP v1.0

> **Para agentes:** este plan se ejecuta tarea a tarea desde `docs/tasks.md`. Cada tarea se construye en su propio worktree, la revisa otro agente y el arquitecto la integra en `main`.

**Objetivo:** construir el MVP descrito en `docs/spec.md` respetando `docs/constitution.md` y `ARCHITECTURE.md`.

**Arquitectura:** un único módulo Gradle, solo `paper-api`. Los contratos compartidos (records de `model`, interfaces de `runtime`, `Ability`) se crean primero en T01 para que el resto de tareas se construyan en paralelo contra ellos sin pisarse.

**Stack:** Java 25 · Gradle 9.8 (Kotlin DSL, wrapper) · `io.papermc.paper:paper-api:26.3.build.157-beta` (`compileOnly`) · Brigadier (Paper Commands) · Dialog API · HikariCP 7.1.0 + sqlite-jdbc 3.53.4.0 + mysql-connector-j 26.7.0 (cargados por `PluginLoader`) · VaultAPI 1.7.1 (`compileOnly`, JitPack) · JUnit 5.

**Spec:** `docs/spec.md` (IDs `RF-*`, `RNF-*`).

## Restricciones globales
Aplican a todas las tareas aunque la tarea no las repita.
- Paquete raíz `dev.dasan.customdungeons`. Java 25. Solo API pública de Paper; **prohibido NMS/CraftBukkit**.
- `paper-plugin.yml` con `api-version: '26.3'`. Dependencias de servidor opcionales: Vault, WorldGuard, Multiverse-Core, Multiverse-Portals, LuckPerms (`required: false`).
- **Una sola tarea programada por partida activa** (`SessionTicker`), una por prueba en vivo (`LiveTestService`) y una de previsualización de herramientas que solo corre mientras algún admin sostiene una (T08). Ninguna otra clase crea tareas repetitivas.
- **Nada de E/S en el hilo principal durante el juego** (excepción: carga en `onEnable`, `reload` y cierre en `onDisable`): BD con `CompletableFuture` en un executor propio; escritura de YAML asíncrona. El resultado vuelve al hilo principal con `Bukkit.getScheduler().runTask`.
- **Ningún texto visible en código**: todo sale de `messages.yml` vía `Messages`. Colores `&`, `&#RRGGBB` y MiniMessage.
- **Ninguna habilidad rompe bloques ni prende fuego.** Bloques temporales solo con `TempBlocks`.
- Mobs de partida marcados con PDC `customdungeons:session` (UUID) y `customdungeons:template` (id).
- Comando principal `/customdungeon`. Permisos exactamente como en spec §12.
- Prefijo por defecto `&8[&6CustomDungeons&8] `.
- Lógica no trivial → al menos un test JUnit que falle si se rompe. `./gradlew build` debe pasar en cada commit.
- Commits en inglés, estilo `feat: ...`/`fix: ...`/`test: ...`. Sin líneas `Co-Authored-By`.

## Foco de revisión
Situaciones que la spec implica y que más pueden romper la experiencia. Cada una tiene su test en la tarea indicada.
1. **Sala bloqueada para siempre**: un mob de la oleada muere por caída, lava, `/kill`, se descarga o se elimina sin que lo mate un jugador → la sala debe contarlo como eliminado igualmente (T07, T09).
2. **Llave perdida**: la llave cae al vacío o lava, desaparece, o su portador muere fuera de alcance → la llave reaparece junto a la puerta; nunca se puede quedar la partida sin llave (T09).
3. **Spam de portal**: un jugador parado en el portal de MV-Portals dispara `join` muchas veces por segundo → `join` es idempotente y el mensaje de rechazo tiene enfriamiento de 3 s por jugador (T09, T16).
4. **Plantilla borrada o rota**: una oleada referencia una plantilla de mob que no existe o un YAML editado a mano tiene valores inválidos → al guardar se bloquea con error; al cargar se desactiva esa dungeon con aviso en consola, sin crashear el plugin (T02).
5. **Objetivos fuera de la partida**: admins en espectador, jugadores de otras partidas o mobs ajenos cerca → las habilidades solo afectan a jugadores de la propia partida (o al admin en "probar en vivo") (T05).

## Oleadas de trabajo y paralelismo

```
Oleada A  T01 Fundación y contratos                          (1 agente)
Oleada B  T02 Config/mensajes  T03 Storage  T04 GUI base
          T05 Motor habilidades  T06 Mobs y jefes  T07 Núcleo de partida (puro)   (paralelo)
Oleada C  T08 Herramientas  T09 Partida en runtime
          T10 Habilidades A  T11 Habilidades B  T12 Habilidades C  T13 Habilidades D   (paralelo)
Oleada D  T14 Cierre de partida (premios, hooks, recuperación)
          T15 GUI de dungeons  T17 GUI de mobs + probar en vivo                       (paralelo)
Oleada E  T16 Comandos
Oleada F  T18 Pruebas integradas en servidor + guías    T19 Release
```
Dependencias exactas en `docs/tasks.md`. En la Raspberry Pi se ejecutan **como máximo 3 agentes constructores a la vez** (Gradle consume ~1,5 GB por build); el resto espera en cola.

## Flujo por tarea
1. **Arquitecto**: crea `git worktree add ../CustomDungeons-wt/<tNN-nombre> -b feat/<tNN-nombre> main` y lanza el constructor.
2. **Constructor (Codex)**: implementa solo su tarea, TDD donde haya lógica pura, `./gradlew build` en verde, commit(s) en su rama. No toca archivos fuera de la lista salvo la línea de registro indicada.
3. **Revisor (Codex, otra sesión)**: revisa el diff contra la tarea, spec, constitución y foco de revisión; ejecuta `./gradlew build`; para tareas con runtime, despliega en el servidor de pruebas con `scripts/test-server.sh` y comprueba el criterio de aceptación. Devuelve `APROBADO` o lista de cambios.
4. **Arquitecto**: verifica, rebasea sobre `main`, resuelve conflictos de registro, `./gradlew build`, merge `--no-ff` a `main`, push, borra worktree y rama local.

## Entorno de pruebas
- Servidor: `~/Desktop/Proyectos/plugins/servidor/Servidor`, Paper 26.3 actualizado al build que coincide con la API (T01). Java 25 en `/usr/lib/jvm/temurin-25-jdk-arm64`.
- Control: `scripts/test-server.sh {deploy|start|stop|cmd "<comando>"|log}` (T01). Usa `screen` (sesión `cd-test`) y lee `logs/latest.log`. RCON activo en el puerto 25575.
- Los plugins existentes del servidor no se tocan. Multiverse-Core y Multiverse-Portals se instalan en T18.
