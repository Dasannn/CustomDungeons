# Plan de implementación — CustomDungeons

Estado: v1.0, v1.0.1, v1.1.0 y v1.1.1 publicadas. **v1.2 en diseño** (ver «Plan v1.2»); T21 (verificación humana) sigue abierta.

## Plan v1.0 (MVP, completado)

> **Para agentes:** este plan se ejecuta tarea a tarea desde `docs/tasks.md`. Cada tarea se construye en su propio worktree, la revisa otro agente y el arquitecto la integra en `main`.

**Objetivo:** construir el MVP descrito en `docs/spec.md` respetando `docs/constitution.md` y `ARCHITECTURE.md`.

**Arquitectura:** un único módulo Gradle, solo `paper-api`. Los contratos compartidos (records de `model`, interfaces de `runtime`, `Ability`) se crean primero en T01 para que el resto de tareas se construyan en paralelo contra ellos sin pisarse.

**Stack:** Java 25 · Gradle 9.8 (Kotlin DSL, wrapper) · `io.papermc.paper:paper-api:26.3.build.157-beta` (`compileOnly`) · Brigadier (Paper Commands) · Dialog API · HikariCP 7.1.0 + sqlite-jdbc 3.53.4.0 + mysql-connector-j 26.7.0 (cargados por `PluginLoader`) · VaultAPI 1.7.1 (`compileOnly`, JitPack) · JUnit 5.

**Spec:** `docs/spec.md` (IDs `RF-*`, `RNF-*`).

## Restricciones globales
Aplican a todas las tareas aunque la tarea no las repita.
- Paquete raíz `dev.dasan.customdungeons`. Java 25. Solo API pública de Paper; **prohibido NMS/CraftBukkit**.
- `paper-plugin.yml` con `api-version: '26.3'`. Dependencias de servidor opcionales: Vault, WorldGuard, Multiverse-Core, Multiverse-Portals, LuckPerms (`required: false`).
- **Una sola tarea programada por partida activa** (`SessionTicker`), una por prueba en vivo (`LiveTestService`) y una de previsualización de herramientas que solo corre mientras algún admin sostiene una, hay un `show` activo (T08) o existen restauraciones de cinemática pendientes (T41, RF-INI-06). Ninguna otra clase crea tareas repetitivas.
- **Nada de E/S en el hilo principal durante el juego** (excepción: carga en `onEnable`, `reload` y cierre en `onDisable`): BD con `CompletableFuture` en un executor propio; escritura de YAML asíncrona. El resultado vuelve al hilo principal con `Bukkit.getScheduler().runTask`.
- **Ningún texto visible en código**: todo sale de `messages.yml` vía `Messages`. Colores `&`, `&#RRGGBB` y MiniMessage.
- **Ninguna habilidad rompe bloques ni prende fuego.** Bloques temporales solo con `TempBlocks`.
- Mobs de partida marcados con PDC `customdungeons:session` (UUID) y `customdungeons:template` (id).
- Comando principal `/customdungeon`. Permisos exactamente como en spec §12.
- Prefijo por defecto `&8[&6CustomDungeons&8] `.
- Lógica no trivial → al menos un test JUnit que falle si se rompe. `./gradlew build` debe pasar en cada commit.
- Commits en inglés, estilo `feat: ...`/`fix: ...`/`test: ...`. Sin líneas `Co-Authored-By`.
- Cara pública de GitHub en inglés: `README.md`, descripción del repositorio y notas de release. La documentación interna (`docs/`, `ARCHITECTURE.md`) sigue en español.

## Foco de revisión
Situaciones que la spec implica y que más pueden romper la experiencia. Cada una tiene su test en la tarea indicada.
1. **Sala bloqueada para siempre**: un mob de la oleada muere por caída, lava, `/kill`, se descarga o se elimina sin que lo mate un jugador → la sala debe contarlo como eliminado igualmente (T07, T09).
2. **Llave perdida**: la llave cae al vacío o lava, desaparece, o su portador muere fuera de alcance → la llave reaparece junto a la puerta; nunca se puede quedar la partida sin llave (T09).
3. **Spam de portal**: un jugador parado en el portal de MV-Portals dispara `join` muchas veces por segundo → `join` es idempotente y el mensaje de rechazo tiene enfriamiento de 3 s por jugador (T09, T16).
4. **Plantilla borrada o rota**: una oleada referencia una plantilla de mob que no existe o un YAML editado a mano tiene valores inválidos → al guardar se bloquea con error; al cargar se desactiva esa dungeon con aviso en consola, sin crashear el plugin (T02). Excepción RF-GUI-06: los números fuera de rango se recortan en memoria con aviso en consola y en el editor, sin modificar el YAML ni desactivar la dungeon (T46).
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
- Servidor de **agentes**: `~/Desktop/Proyectos/plugins/servidor/Servidor-agentes` (puerto 25566, Xmx2G), destino por defecto de `scripts/test-server.sh {deploy|start|stop|cmd|log}`. Servidor del **usuario** (25565): solo con `CD_TARGET=user` y avisando. Java 25 en `/usr/lib/jvm/temurin-25-jdk-arm64`.
- Bots mineflayer en `~/Desktop/Proyectos/plugins/servidor/bots/` (online-mode=false + ViaVersion/ViaBackwards).
- Los plugins existentes de los servidores no se tocan.
- Raspberry Pi 5 (8 GB): **1 trabajo pesado a la vez** (Codex compilando o servidor; con dos a la vez la Pi llega a 82 °C); Codex y Gradle limitados a 2 núcleos (`taskset`), `org.gradle.workers.max=2`; no se lanza un trabajo con la Pi ≥ 75 °C y se pausan a ≥ 82 °C.

## Plan v1.2 (en diseño)

**Objetivo:** jefes del mundo configurables en la GUI (zona, aparición por comando, recompensas), mobs más inteligentes y variados sin romper nada (inteligencia 0 por defecto), con el sistema de mobs separado de dungeons por una frontera vigilada para que las ramas por servidor añadan jefes en código. Principios: optimizado, escalable, calidad muy alta, combate justo (constitución §8–§9).

**Spec:** `docs/spec.md` §9b–§9e (RF-MOB-08, RF-IA, RF-HAB2, RF-JEFES) y RF-GUI-06 (T53). **Decisiones:** `docs/decisions/0001`, `0002`. **Arquitectura:** `ARCHITECTURE.md` «Sistema de mobs (v1.2)».

### Oleadas v1.2
```
1. T53 bugs de prueba en vivo (objetivo forzado, sonidos solo al admin)       — hecho (1.378 tests, bots OK)
2. T54 frontera de mobs: MobHost/MobsPlatform, registros, test de frontera    — hecho (1.404 tests)
3. T55 jefes del mundo: menú Jefes, zona, boss spawn/list/despawn, recompensas — hecho (1.462 tests, bots OK, 0,17 ms/tick)
4. T56 inteligencia: niveles 0–5, memoria, disparadores, objetivos, adaptación, punto débil — diseño aprobado; maquetas pendientes
5. T57 habilidades nuevas (agarre, jaula, drenaje, marca bomba, esbirros + catálogo elegido) — selección pendiente
Cierre: release v1.2.0 (con aprobación del usuario)
```
Cada tarea con diseño pendiente pasa por: diseño por secciones con el usuario → spec → ARCHITECTURE → este plan → tareas con aceptación → maquetas PNG (si hay GUI) → construcción.

### Restricciones añadidas
- **Frontera:** `BoundaryTest` en verde en cada tarea; el sistema de mobs solo habla con dungeons por `MobHost`/`MobsPlatform`.
- **Rendimiento:** jefes del mundo e inteligencia medidos con bots en el servidor de agentes (MSPT con jefe de nivel 5 y 5+ bots), objetivo de la constitución (≤ 2 ms por encuentro). Búsqueda de punto de aparición sin cargar chunks en el hilo principal.
- **Justicia:** cada habilidad o adaptación nueva incluye en su test el aviso, la duración máxima, la forma de escape y su contrapartida.
- **Ramas:** ninguna tarea introduce valores propios de un servidor en `main`.

## Plan v1.1

**Objetivo:** editor visual coherente (GUI v2), creación guiada y en el mundo, plantillas reutilizables y partidas más dinámicas (inicio por placas, salida controlada, puzzles, scoreboard, ambiente y cinemática), sin romper dungeons ni configuraciones de v1.0.

**Spec:** RF-ASI-*, RF-SPW-*, RF-LLA-*, RF-INI-*, RF-CON-*, RF-SCB-01, RF-AMB-01, RF-DESC-01. **Arquitectura:** `ARCHITECTURE.md` (GUI v2, borradores, plantillas, modo construcción, flujo de partida actualizado).

### Restricciones añadidas
- **Compatibilidad:** todo campo nuevo del modelo es aditivo y opcional; YAML de v1.0 cargan con el comportamiento anterior (p. ej. sin `teleportOnStart` → `true`).
- **Catálogos versionados:** cada tarea que cambie textos sube la versión de `messages.yml`/`messages_en.yml` y guarda el histórico anterior; si dos ramas trabajan en paralelo, solo una sube la versión y el arquitecto ajusta al integrar. Paridad es/en obligatoria (`MessageKeysTest`).
- **GUI:** toda pantalla nueva pasa por maqueta PNG aprobada por el usuario antes de construirla; toda tarea de GUI entrega instantáneas que el arquitecto revisa antes de integrar.
- **Inventarios del jugador:** cualquier código que retire ítems del jugador (modo construcción, menús con entrada, llaves, desconexión) necesita respaldo o devolución verificable y tests de caída/desconexión.

### Foco de revisión v1.1
6. **Duplicación/pérdida de ítems** en menús con ranuras de entrada y en el modo construcción (T35b, T40).
7. **Llaves**: una llave antigua o de otra sala nunca abre ni bloquea otra puerta; al terminar se limpian todas las de la sesión (T39).
8. **Partida que no arranca o no se vacía**: placas mal configuradas, puerta de entrada ausente, jugadores que se quedan dentro tras completar (T38).
9. **Penalización injusta**: una caída del servidor nunca se trata como desconexión voluntaria (T44).
10. **Coste por tick**: activación por entrada, scoreboard, ambiente y cinemática dentro del presupuesto de tareas (ARCHITECTURE «Tareas programadas»).

### Foco de revisión v1.2 (lecciones de T53–T55; el constructor las comprueba antes de entregar)
11. **Propiedad por marca, nunca por proximidad**: una entidad o proyectil pertenece a un encuentro solo si se marcó con PDC al crearla o lanzarla.
12. **Permisos en cada acción**, no solo al abrir el menú (un helper único).
13. **Callbacks asíncronos**: vuelven al hilo principal solo si el plugin sigue habilitado y la operación sigue vigente; al deshabilitar se invalidan.
14. **Sin recorridos globales por tick**: nada de recorrer todos los jugadores o entidades del mundo; búsquedas espaciales con radio, calculadas una vez por tick y compartidas.
15. **Configuración en vivo**: ningún valor de `config.yml` congelado en estáticos; `reload` debe surtir efecto donde la spec lo prometa.

### Oleadas v1.1
```
Hecho     T20–T28, T30–T34 (v1.0.1 y mejoras), T35a/T35b GUI v2, T36 plantillas de spawner,
          T37 correcciones, T29 asistente, T39 llaves por comando, T40 modo construcción,
          T38 inicio y final de partida (placas, puerta de entrada, vaciado), T42 scoreboard,
          T46 rangos visibles y escala 16, T43 ambiente, T44 desconexión,
          T41 cinemática
          T45 prueba integrada (12/12 OK), T48 reaparición segura, T47 contenido de ejemplo
          T49 investigación de drops (EssentialsX AntiBuild)
Publicado release v1.1.0
Hecho     T51 modo construcción (NPE al crear una dungeon y entrar sin guardar; 16/16 bots)
Hecho     T50 recuperación al cargar y equipo reservado (salidas pendientes persistidas hasta confirmar; esquema 5)
Hecho     T52 atributos de mob (vida y daño hasta 1e30 con vida virtual; 8 atributos nuevos; bot OK)
En curso  T21 verificación humana
          (1 trabajo pesado a la vez)
Cierre    release v1.1.1 (scripts/release.sh, con aprobación del usuario)
```

### Flujo por tarea (v1.1)
1. Spec, `ARCHITECTURE.md`, este plan y `docs/tasks.md` (con criterios de aceptación) se actualizan en `main` **antes** de lanzar el constructor; las ampliaciones aprobadas en conversación también.
2. Constructor (Codex, GPT 6.1 Sol high, sin modo fast salvo que el usuario lo pida); **bugs reportados por el usuario**: la investigación de causa raíz y su revisión se lanzan con esfuerzo **extra high** (`EFFORT=xhigh`) en su worktree; sin commits (el sandbox no escribe en `.git`); lista los archivos tocados fuera de su alcance.
3. Arquitecto: revisa informe y PNG, commitea; revisor Codex independiente; correcciones en el mismo hilo del constructor hasta `APROBADO` (y segunda revisión focalizada si toca inventarios o ciclo de vida).
4. Arquitecto: integra con `--no-ff`, ajusta catálogo, `.agent/build.sh`, push, borra worktree; actualiza docs si la implementación se desvió.
