# Templo de las Placas

Contenido para Paper **26.3 build 157 / Java 25** y CustomDungeons con T28–T46 y T48. Los `.yml` usan JSON, subconjunto válido de YAML, igual que el paquete T45. No requiere plantillas de otros ejemplos.

## Instalación en otro servidor

1. Carga `cd_dungeons` con Multiverse-Core o la creación automática del plugin. Reserva **X 1500–1596, Y 64–77, Z 600–628**, incluida la plataforma de salida y, en el Laberinto, los circuitos de botones. Comprueba que todo el volumen esté libre antes de construir. Para mover el paquete, cambia todas las coordenadas del YAML y del script; los nombres de mundo de ambos deben coincidir. Estas zonas no se cruzan con la demo (X 484–549), la Guarida (784–870) ni T45 (1200–1345).
2. Copia `mobs/*.yml` a `plugins/CustomDungeons/mobs/` y `dungeons/templo-placas.yml` a `plugins/CustomDungeons/dungeons/`. Comprueba que los IDs no existan y conserva los archivos y plugins ajenos.
3. Sin partidas activas, ejecuta [build.mcfunction](build.mcfunction) desde consola, **una orden por línea, sin barra**. Espera al menos dos segundos tras `forceload add`. Ningún `fill` supera 32768 bloques. La última orden retira solo las cargas de construcción del paquete. Si lo importas a un datapack, precarga los chunks antes de ejecutar la función; usa un `pack.mcmeta` compatible con tu build.
4. Ejecuta `customdungeon reload`. Abre el editor de la dungeon y confirma **Errores: 0**, sin avisos de estos IDs en el log. Los avisos de otros paquetes no invalidan este contenido. Activa el scoreboard global (`scoreboard.enabled: true`) para ver los objetivos contextuales.
5. Portal opcional: crea y selecciona con WorldEdit un marco **fuera de las áreas de dungeon** en tu mundo de llegada, luego `mvp create <nombre>`, `mvp modify <nombre> action-type command` y `mvp modify <nombre> action "console:customdungeon join %player% templo-placas"`. Concede `multiverse.portal.access.<nombre>` solo al grupo autorizado. Adapta selección, nombre y posición a tu servidor (sintaxis de Multiverse-Portals 5.3). También basta `/customdungeon join templo-placas`; no concedas permisos de administración a jugadores normales.

## Cómo se juega

Grupo de **3–4 jugadores**, tres vidas, límite de 20 minutos, sin cooldown. Se conserva el inventario al morir. Cantidad fija y +10 % de vida por jugador sobre el mínimo. Cada superviviente recibe 3 fragmentos de amatista y 40 XP. Lleva comida, armadura de hierro o diamante y espada; para el Coloso se recomienda diamante, arco y escudo. Los bots usaron ayudas técnicas; la dificultad para humanos aún necesita una partida de aceptación.

Suelo Y 64, pies Y 65. Lobby `(1504.5, 65, 614.5)`; salida segura en `(1593.5, 65, 614.5)`, fuera del área. La entrada está en X 1510, Y 65–68, Z 613–615. Las salas se activan al entrar, nunca solo por abrir su puerta.

| Sala | Interior libre / región YAML | Checkpoint | Oleadas |
|---|---|---|---|
| 1: Atrio de las Tres Huellas | X 1511–1533, Z 602–626, Y 65–76; región hasta X 1534, Y 64–77 | X 1513.5 | 2 custodios + 2 vigías; después 1 custodio |
| 2: Galería del Polvo Dorado | X 1535–1557, Z 602–626, Y 65–76; región hasta X 1558, Y 64–77 | X 1537.5 | 2 custodios + 2 vigías; después 1 custodio |
| 3: Santuario del Sol | X 1559–1581, Z 602–626, Y 65–76; región hasta X 1582, Y 64–77 | X 1561.5 | 2 custodios + 2 vigías; después 1 custodio |

Templo de arenisca, columnas cinceladas, alfombra de oro y luz cálida. Tres placas en `(1504,65,610)`, `(1504,65,614)` y `(1504,65,618)` requieren **tres participantes distintos** durante tres segundos. Bajarse cancela la cuenta atrás; dos jugadores no bastan. La entrada se abre sin teletransporte. Hay cinco enemigos por sala, sonidos de piedra y polvo representado por `ASH`/`CLOUD`/`SMOKE` (sin parámetros adicionales de partícula).

Tras despejar el Santuario, `DELAYED` deja **45 segundos** antes de llevar a cada ocupante a `EXIT`. La placa negra `(1582,65,614)` permite salir individualmente antes; no funciona durante el combate. Quien sale antes no vuelve a ser teletransportado al vencer el plazo. Las puertas y la entrada se restauran al vaciarse la dungeon.

## Prueba con bots (T47)

**7 de octubre de 2026**, solo `Servidor-agentes`, **25566**, `CD_TARGET=agents` y `scripts/test-server.sh`; Paper 26.3 build 157. Dos bots principales `T47A`/`T47B`, Mineflayer 4.39.0, protocolo 1.20.4 mediante ViaBackwards. Un tercer bot auxiliar, T47C, ocupó únicamente la tercera placa y salió al arrancar; no combatió ni recibió premio. El mínimo de tres placas se conserva en el paquete.

SQLite run **44**, **COMPLETED**, `2026-10-07T06:44:26.890102676Z`–`2026-10-07T06:47:15.845356056Z`. Bajas de los dos supervivientes: **11 + 4**, cero muertes; ambos `survived=1`, `rewarded=1`. Supervivencia, espada/armadura de netherite, Resistencia V, saturación y visión nocturna para el combate. Física cliente desactivada; posicionamiento asistido por TP dentro de salas y movimientos reales al cruzar puertas. No se usaron kill de mobs, skipwave, modo test ni cambios de habilidades. **Verifica funcionamiento, no equilibrio para humanos ni percepción visual/audio.**

| Comprobación | Resultado |
|---|---|
| Tres placas / entrada | **OK**: dos participantes no arrancan; bajarse cancela; tres placas ocupadas abren la entrada tras 3 s, sin TP. A/B permanecieron en el lobby hasta entrar en el Atrio. |
| Progresión | **OK**: tres salas, dos oleadas por sala, 15 bajas reales; puertas abiertas y cruce por paquetes de movimiento. |
| Placa de salida | **OK**: A la pisó con enemigos vivos en sala 3 y permaneció dentro; tras completar, lo llevó a EXIT. |
| DELAYED 45 s / EXIT | **OK**: B permaneció tras completar y volvió a EXIT al vencer la gracia; A salió antes, se desplazó fuera y no fue teletransportado de nuevo. |
| Ambiente / premio | **OK**: títulos, paquetes de sonido de piedra y partículas ASH; ambos supervivientes recibieron 3 fragmentos y 40 XP. |

Los volúmenes se inspeccionaron antes de construir y contenían únicamente aire: Templo 39382, Laberinto 47530 y Coloso 237558 bloques. Se corrigió durante la autoría el suelo del lobby (Y 64); después se comprobó en vivo el espacio de pies/cabeza de los tres vestíbulos y el lobby/cámara del Coloso sin resistencia. El combate completo usó las mismas salas y YAML; no se repitió tras ese cambio del vestíbulo. El análisis estático de los scripts confirma rutas continuas hasta checkpoints, spawners, placas y salida con las puertas abiertas.

`./gradlew test --tests '*T47ExampleValidationTest'`: **2 tests, 0 fallos**; codec y Validator reales, cero errores/avisos en mobs, presets y dungeons con oleadas resueltas; suelo y altura libre de los puntos de llegada/spawn. Recarga sin errores ni avisos de estos IDs. No hubo excepciones de CustomDungeons. Los avisos de paquetes históricos preexistentes no se corrigieron en T47.

### MSPT

[Perfil spark FJDiXmlApg](https://spark.lucko.me/FJDiXmlApg), async, intervalo **4000 µs**, **1200 ticks**, 06:44:33.935–06:45:34.265 UTC. Cubre combate en las salas 1/2; no cubre el final de 45 s.

| Métrica | Resultado |
|---|---:|
| MSPT total medio de Paper, último minuto al cerrar | 3.095 ms |
| MSPT total p95 / máximo | 7.046 / 49.476 ms |
| TPS, último minuto | 20,000 |
| Tiempo muestreado atribuido directamente a CustomDungeons | 300 ms |
| Estimación del plugin por tick / una partida | **300 / 1200 = 0.250 ms** |

Se decodificó `SamplerData` con los [schemas oficiales de spark](https://github.com/lucko/spark/tree/master/spark-common/src/main/proto/spark); se sumó solo el primer frame `dev.dasan.customdungeons.*` de cada rama de Server thread, sin duplicar descendientes. El MSPT de Paper es del último minuto de la captura; la atribución del plugin usa todos sus ticks. Los tres perfiles cumplen ≤2 ms **como estimación para estas ventanas**, sin medir el coste incremental contra una base vacía ni garantizar otras cargas. La IA vanilla y los demás plugins están en el MSPT total. En el Coloso coincidió también la verificación Gradle con parte de la captura.

### Bugs y límites

Ningún bug nuevo de producción atribuible a esta dungeon. No se modificó Java de producción. Escucha humana, render del cliente y ajuste de dificultad con jugadores reales pendientes. Los retardos finos de efectos no se certifican con este controlador.

Cierre: `scripts/test-server.sh stop` devolvió **«Servidor detenido.»**; guardado de chunks y fin de I/O completados, sin bots/controlador vivos, puertos 25566/18848 cerrados, permisos temporales retirados, `enable-command-block=false` restaurado. Temperatura máxima observada **74,35 °C** (no se alcanzó el umbral de 80 °C); arranques con más de 2500 MB disponibles y <75 °C. Se conservaron mundo, ejemplos y plugins ajenos. Evidencia bruta local ignorada en `.agent/`; resumen común en [pruebas integradas — T47](../../../guides/pruebas-integradas.md#t47--contenido-de-ejemplo).
