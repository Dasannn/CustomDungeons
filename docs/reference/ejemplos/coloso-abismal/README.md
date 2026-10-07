# Coloso Abismal

Contenido para Paper **26.3 build 157 / Java 25** y CustomDungeons con T28–T46 y T48. Los `.yml` usan JSON, subconjunto válido de YAML, igual que el paquete T45. No requiere plantillas de otros ejemplos.

## Instalación en otro servidor

1. Carga `cd_dungeons` con Multiverse-Core o la creación automática del plugin. Reserva **X 1900–2036, Y 64–97, Z 600–650**, incluida la plataforma de salida y, en el Laberinto, los circuitos de botones. Comprueba que todo el volumen esté libre antes de construir. Para mover el paquete, cambia todas las coordenadas del YAML y del script; los nombres de mundo de ambos deben coincidir. Estas zonas no se cruzan con la demo (X 484–549), la Guarida (784–870) ni T45 (1200–1345).
2. Copia `mobs/*.yml` a `plugins/CustomDungeons/mobs/` y `dungeons/coloso-abismal.yml` a `plugins/CustomDungeons/dungeons/`. Comprueba que los IDs no existan y conserva los archivos y plugins ajenos.
3. Sin partidas activas, ejecuta [build.mcfunction](build.mcfunction) desde consola, **una orden por línea, sin barra**. Espera al menos dos segundos tras `forceload add`. Ningún `fill` supera 32768 bloques. La última orden retira solo las cargas de construcción del paquete. Si lo importas a un datapack, precarga los chunks antes de ejecutar la función; usa un `pack.mcmeta` compatible con tu build.
4. Ejecuta `customdungeon reload`. Abre el editor de la dungeon y confirma **Errores: 0**, sin avisos de estos IDs en el log. Los avisos de otros paquetes no invalidan este contenido. Activa el scoreboard global (`scoreboard.enabled: true`) para ver los objetivos contextuales.
5. Portal opcional: crea y selecciona con WorldEdit un marco **fuera de las áreas de dungeon** en tu mundo de llegada, luego `mvp create <nombre>`, `mvp modify <nombre> action-type command` y `mvp modify <nombre> action "console:customdungeon join %player% coloso-abismal"`. Concede `multiverse.portal.access.<nombre>` solo al grupo autorizado. Adapta selección, nombre y posición a tu servidor (sintaxis de Multiverse-Portals 5.3). También basta `/customdungeon join coloso-abismal`; no concedas permisos de administración a jugadores normales.

Portal junto al spawn: [portal.txt](portal.txt) incluye marco, selección WorldEdit, acción y permiso de Multiverse-Portals.

## Cómo se juega

Grupo de **2–4 jugadores**, tres vidas, límite de 20 minutos, sin cooldown. La muerte pierde una vida y suelta el inventario; la desconexión voluntaria usa DIE_AND_DROP al reconectar. Cantidad fija y +10 % de vida por jugador sobre el mínimo. Cada superviviente recibe 3 fragmentos de amatista y 80 XP. Lleva comida, armadura de hierro o diamante y espada; para el Coloso se recomienda diamante, arco y escudo. Los bots usaron ayudas técnicas; la dificultad para humanos aún necesita una partida de aceptación.

Suelo Y 64, pies Y 65. Lobby `(1904.5, 65, 614.5)`; salida segura en `(2033.5, 65, 614.5)`, fuera del área. La entrada está en X 1910, Y 65–68, Z 613–615. Las salas se activan al entrar, nunca solo por abrir su puerta.

| Sala | Interior libre / región YAML | Checkpoint | Oleadas |
|---|---|---|---|
| 1: Desfiladero de Obsidiana | X 1911–1933, Z 602–626, Y 65–76; región hasta X 1934, Y 64–77 | X 1913.5 | 2 custodios + 2 vigías; después 1 custodio |
| 2: Galería de las Almas | X 1935–1957, Z 602–626, Y 65–76; región hasta X 1958, Y 64–77 | X 1937.5 | 2 custodios + 2 vigías; después 1 custodio |
| 3: Trono del Coloso | X 1959–2021, Z 602–648, Y 65–96; región hasta X 2022, Y 64–97 | X 1961.5 | 1 Coloso |

Basalto oscuro, marcas cian, sculk y lámparas de almas. Tras diez segundos de lobby, la cámara recorre el complejo durante **12 segundos**; agacharse permite saltarla individualmente. Se restauran supervivencia y posición del lobby, sin TP de inicio. El scoreboard muestra introducción, objetivos, vidas y estado del jefe; depende del ajuste global.

Dos salas de cinco enemigos preceden al trono. `ca-coloso` es un **HUSK**, 640 HP, daño 6, velocidad 0,16, resistencia al empuje 1 y **escala 8** (por debajo del umbral de aviso >10). Según `entity-heights.husk: 1.95`, mide **15,6 bloques**; la sala tiene **32 bloques libres**, Y 65–96, con techo Y 97. Comprueba ese valor si tu servidor personaliza entity-heights. No lleva armadura; deja espacio para rodearlo y evita encerrarlo en la puerta pequeña.

Tres etapas: inicial; fase II al **66 %** añade terremoto, título y música `13`; fase III al **33 %** añade oscuridad suave de dos segundos, título y música `blocks`. Transiciones de 20 ticks de invulnerabilidad. Combo `roar_knockback → earthquake`, separación de 30 ticks, cada 16 s; rugido independiente cada 12 s. Los ataques llevan avisos y daño moderado: apártate de la zona marcada.

Al vencer, `NONE` no teletransporta automáticamente. Sal por la entrada o el arco oriental y abandona el área (X>2026), o usa `/customdungeon leave` antes del final. La dungeon queda ocupada mientras permanezcas dentro, con salida forzada a los 300 s. `finish-destination: PREVIOUS` se aplica a ese retorno de seguridad; usa `EXIT` si la ubicación previa es inválida. La desconexión voluntaria durante el combate abandona al instante y, al reconectar, causa muerte y drop siguiendo las reglas del plugin; no se penaliza una caída del servidor ni una salida durante la cinemática.

## Prueba con bots (T47)

**7 de octubre de 2026**, solo `Servidor-agentes`, **25566**, `CD_TARGET=agents` y `scripts/test-server.sh`; Paper 26.3 build 157. Dos bots principales `T47A`/`T47B`, Mineflayer 4.39.0, protocolo 1.20.4 mediante ViaBackwards. 

SQLite run **46**, **COMPLETED**, `2026-10-07T06:53:35.741785688Z`–`2026-10-07T06:58:14.184141211Z`. Bajas de los dos supervivientes: **8 + 3**, cero muertes; ambos `survived=1`, `rewarded=1`. Supervivencia, espada/armadura de netherite, Resistencia V, saturación y visión nocturna para el combate. Física cliente desactivada; posicionamiento asistido por TP dentro de salas y movimientos reales al cruzar puertas. No se usaron kill de mobs, skipwave, modo test ni cambios de habilidades. **Verifica funcionamiento, no equilibrio para humanos ni percepción visual/audio.**

| Comprobación | Resultado |
|---|---|
| Cinemática 12 s | **OK**: A espectador 06:53:35.840–06:53:47.834 UTC; B 06:53:35.846–06:53:47.836. Restauración a supervivencia y posición del lobby. Comprobación final sin resistencia tras corregir el suelo: salud 20/20 antes y después. |
| Salas / escala | **OK**: dos salas de 5 mobs y jefe; 11 bajas reales. Consulta de atributo del jefe: Scale **8.0**; 32 bloques libres frente a 15,6 de entity-heights. |
| Fases | **OK**: títulos II y III para ambos a 06:56:38.034/035 y 06:57:25.483 UTC; cambios de música con `stop_sound` de 5/13/blocks y BossBar actualizada. |
| Combo | **OK de ejecución**: rugidos y BlockDisplay de terremoto observados; el perfil contiene `ComboRunner.start/step` y las acciones diferidas `lambda$step$0/1`. El muestreo no certifica el retardo exacto de 30 ticks. |
| Scoreboard | **OK de paquetes**: objetivo lateral y actualizaciones de vida/fase/objetivo a A/B; sin captura raster del cliente. |
| NONE / PREVIOUS | **OK**: sin TP al completar; nuevo join rechazado mientras se vacía. Al agotar 300 s, A volvió a `(2033.5,65,614.5)` y B a `(2033.5,65,616.5)`, sus posiciones previas exactas. |
| DIE_AND_DROP | **PARCIAL**: A abandona al desconectar, B sigue; al reconectar A muere, pierde inventario y reaparece fuera. El drop físico falla en este servidor también con muerte vanilla: incidente T47-I01, sin causa aislada. Segundo login no repite la muerte. |
| Premio | **OK**: ambos supervivientes del recorrido completo recibieron 3 fragmentos y 80 XP. |

Los volúmenes se inspeccionaron antes de construir y contenían únicamente aire: Templo 39382, Laberinto 47530 y Coloso 237558 bloques. Se corrigió durante la autoría el suelo del lobby (Y 64); después se comprobó en vivo el espacio de pies/cabeza de los tres vestíbulos y el lobby/cámara del Coloso sin resistencia. El combate completo usó las mismas salas y YAML; no se repitió tras ese cambio del vestíbulo. El análisis estático de los scripts confirma rutas continuas hasta checkpoints, spawners, placas y salida con las puertas abiertas.

`./gradlew test --tests '*T47ExampleValidationTest'`: **2 tests, 0 fallos**; codec y Validator reales, cero errores/avisos en mobs, presets y dungeons con oleadas resueltas; suelo y altura libre de los puntos de llegada/spawn. Recarga sin errores ni avisos de estos IDs. No hubo excepciones de CustomDungeons. Los avisos de paquetes históricos preexistentes no se corrigieron en T47.

### MSPT

[Perfil spark mvnGk3c5CW](https://spark.lucko.me/mvnGk3c5CW), async, intervalo **4000 µs**, **2400 ticks**, 06:55:48.584–06:57:48.793 UTC. Cubre combate del jefe y ambas transiciones, no el final de 300 s ni la desconexión.

| Métrica | Resultado |
|---|---:|
| MSPT total medio de Paper, último minuto al cerrar | 3.120 ms |
| MSPT total p95 / máximo | 6.454 / 108.470 ms |
| TPS, último minuto | 20,000 |
| Tiempo muestreado atribuido directamente a CustomDungeons | 344 ms |
| Estimación del plugin por tick / una partida | **344 / 2400 = 0.143 ms** |

Se decodificó `SamplerData` con los [schemas oficiales de spark](https://github.com/lucko/spark/tree/master/spark-common/src/main/proto/spark); se sumó solo el primer frame `dev.dasan.customdungeons.*` de cada rama de Server thread, sin duplicar descendientes. El MSPT de Paper es del último minuto de la captura; la atribución del plugin usa todos sus ticks. Los tres perfiles cumplen ≤2 ms **como estimación para estas ventanas**, sin medir el coste incremental contra una base vacía ni garantizar otras cargas. La IA vanilla y los demás plugins están en el MSPT total. En el Coloso coincidió también la verificación Gradle con parte de la captura.

### T47-I01 — inventario sin drop recuperable (alta, integración; origen no aislado)

En agentes, el bot A llevaba un diamante, `keep-inventory: false`, y B seguía activo en sala 1. A desconectó y reconectó: muerte e inventario vacío correctos, pero B no vio entidades item. Segunda reproducción a **07:08:32 UTC**; cinco segundos después, `execute ... unless entity @e[type=minecraft:item,...]` emitió **T47_DROP_MISSING**. La partida solo se detuvo después de comprobarlo.

Control fuera de sesión, en `(2033.5,65,614.5)`: bot sin respawn automático, `minecraft:give T47A diamond 1`, `minecraft:kill T47A`; a 07:10:45 UTC el servidor emitió **T47_BASELINE_DROP_MISSING**. `keep_inventory=false`, `entity_drops=true` y `mob_drops=true`. Esto impide atribuir el fallo a CustomDungeons; no se desactivó ni eliminó ningún plugin para aislarlo.

Reproducción del caso de dungeon: unir A/B, esperar lobby y cámara, entrar en sala 1, alejar B 10 bloques, dar el diamante a A, cerrar su cliente y reconectar. Antes de detener la partida, consultar desde consola `execute in minecraft:cd_dungeons unless entity @e[type=minecraft:item,x=1900,y=64,z=600,dx=126,dy=33,dz=50] run say T47_DROP_MISSING`. Comprobar también el control vanilla descrito arriba.

Archivo de referencia de la función que debe entregar drops: [DisconnectService.java](../../../../src/main/java/dev/dasan/customdungeons/session/DisconnectService.java), métodos `apply`, `death` y `drop` (especialmente líneas 201–242). Es un punto de investigación, **no una atribución demostrada**. Evidencia local: `.agent/drop-repro.log`, `.agent/death-baseline.log`, `.agent/evidence.jsonl` y log del servidor. Ninguna corrección de producción.

Cierre: `scripts/test-server.sh stop` devolvió **«Servidor detenido.»**; guardado de chunks y fin de I/O completados, sin bots/controlador vivos, puertos 25566/18848 cerrados, permisos temporales retirados, `enable-command-block=false` restaurado. Temperatura máxima observada **74,35 °C** (no se alcanzó el umbral de 80 °C); arranques con más de 2500 MB disponibles y <75 °C. Se conservaron mundo, ejemplos y plugins ajenos. Evidencia bruta local ignorada en `.agent/`; resumen común en [pruebas integradas — T47](../../../guides/pruebas-integradas.md#t47--contenido-de-ejemplo).
