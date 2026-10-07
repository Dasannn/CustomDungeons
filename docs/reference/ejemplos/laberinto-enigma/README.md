# Laberinto del Enigma

Contenido para Paper **26.3 build 157 / Java 25** y CustomDungeons con T28–T46 y T48. Los `.yml` usan JSON, subconjunto válido de YAML, igual que el paquete T45. No requiere plantillas de otros ejemplos.

## Instalación en otro servidor

1. Carga `cd_dungeons` con Multiverse-Core o la creación automática del plugin. Reserva **X 1700–1796, Y 64–77, Z 594–628**, incluida la plataforma de salida y, en el Laberinto, los circuitos de botones. Comprueba que todo el volumen esté libre antes de construir. Para mover el paquete, cambia todas las coordenadas del YAML y del script; los nombres de mundo de ambos deben coincidir. Estas zonas no se cruzan con la demo (X 484–549), la Guarida (784–870) ni T45 (1200–1345).
2. Copia `mobs/*.yml` a `plugins/CustomDungeons/mobs/` y `dungeons/laberinto-enigma.yml` a `plugins/CustomDungeons/dungeons/`. Copia además `spawners/*.yml` a `plugins/CustomDungeons/spawners/`. Comprueba que los IDs no existan y conserva los archivos y plugins ajenos.
3. Sin partidas activas, ejecuta [build.mcfunction](build.mcfunction) desde consola, **una orden por línea, sin barra**. Espera al menos dos segundos tras `forceload add`. Ningún `fill` supera 32768 bloques. La última orden retira solo las cargas de construcción del paquete. Si lo importas a un datapack, precarga los chunks antes de ejecutar la función; usa un `pack.mcmeta` compatible con tu build.
4. Ejecuta `customdungeon reload`. Abre el editor de la dungeon y confirma **Errores: 0**, sin avisos de estos IDs en el log. Los avisos de otros paquetes no invalidan este contenido. Activa el scoreboard global (`scoreboard.enabled: true`) para ver los objetivos contextuales.
5. Portal opcional: crea y selecciona con WorldEdit un marco **fuera de las áreas de dungeon** en tu mundo de llegada, luego `mvp create <nombre>`, `mvp modify <nombre> action-type command` y `mvp modify <nombre> action "console:customdungeon join %player% laberinto-enigma"`. Concede `multiverse.portal.access.<nombre>` solo al grupo autorizado. Adapta selección, nombre y posición a tu servidor (sintaxis de Multiverse-Portals 5.3). También basta `/customdungeon join laberinto-enigma`; no concedas permisos de administración a jugadores normales.

Para el puzzle, habilita `enable-command-block=true` en `server.properties` con el servidor apagado y reinicia. La opción debe estar habilitada mientras este contenido esté disponible. El objetivo interno `le47` no usa sidebar: se crea durante la construcción; si ya existe, verifica que pertenezca a este paquete antes de continuar. El estado se reinicia al comenzar cada partida. Protege terreno y circuitos con WorldGuard; el plugin no protege bloques. No des permisos de bloques de comando a jugadores.

Si tienes EssentialsX AntiBuild, permite al grupo de jugadores `essentials.build.interact.stone_button` y `essentials.build.interact.tripwire_hook` en el contexto `world=cd_dungeons`. Son permisos de interacción, sin administración ni construcción general; consulta los [permisos oficiales de EssentialsX](https://www.essentialsx.net/permissions). Pulsa los botones **con la mano vacía** si AntiBuild bloquea también el uso del arma sostenida. WorldGuard debe permitir el uso de los botones en la región del puzzle, manteniendo la protección contra romper/colocar bloques.

Portal junto al spawn: [portal.txt](portal.txt) incluye marco, selección WorldEdit, acción y permiso de Multiverse-Portals.

## Cómo se juega

Grupo de **2–4 jugadores**, tres vidas, límite de 20 minutos, sin cooldown. Se conserva el inventario al morir. Cantidad fija y +10 % de vida por jugador sobre el mínimo. Cada superviviente recibe 3 fragmentos de amatista y 40 XP. Lleva comida, armadura de hierro o diamante y espada; para el Coloso se recomienda diamante, arco y escudo. Los bots usaron ayudas técnicas; la dificultad para humanos aún necesita una partida de aceptación.

Suelo Y 64, pies Y 65. Lobby `(1704.5, 65, 614.5)`; salida segura en `(1793.5, 65, 614.5)`, fuera del área. La entrada está en X 1710, Y 65–68, Z 613–615. Las salas se activan al entrar, nunca solo por abrir su puerta.

| Sala | Interior libre / región YAML | Checkpoint | Oleadas |
|---|---|---|---|
| 1: Senderos de Musgo | X 1711–1733, Z 602–626, Y 65–76; región hasta X 1734, Y 64–77 | X 1713.5 | 2 custodios + 2 ecos, usando dos plantillas |
| 2: Cámara de las Tres Runas | X 1735–1757, Z 602–626, Y 65–76; región hasta X 1758, Y 64–77 | X 1737.5 | 2 custodios + 2 ecos, usando dos plantillas |
| 3: Jardín del Eco | X 1759–1781, Z 602–626, Y 65–76; región hasta X 1782, Y 64–77 | X 1761.5 | 2 custodios + 2 ecos, usando dos plantillas |

Muros de musgo con pasillos alternos y luz de cobre. Las salas 1 y 3 tienen rutas en zigzag: rodea los extremos libres de los tabiques. Las dos plantillas `le-custodios` y `le-ecos` se usan **en las tres salas** (dos mobs cada una, escalonados cada 20 ticks); editar una cambia futuras partidas en todas ellas. La sala 1 aplica Velocidad I solo dentro de ella, música `cat` y esporas; la sala 2 usa `chirp`/`ENCHANT`; la sala 3, `ward`/`GLOW`.

La sala 2 usa `opening-mode: EXTERNAL_KEY` con `unlock: AUTOMATIC` para compatibilidad del codec; la apertura efectiva es externa. Limpiar sus mobs **no abre** la puerta y ningún mob entrega llave. Pulsa las runas en el orden mostrado en el título: **oro → cobre → amatista**. Botones en `(1740,66,602)`, `(1744,66,602)`, `(1748,66,602)`; un error reinicia a cero. Espera a que se libere cada botón antes de repetirlo. La cadena final ejecuta exactamente `customdungeon key give @p laberinto-enigma` desde un bloque de comando: acércate a esa runa, con tu compañero detrás, para que seas el jugador más cercano. El plugin exige que el receptor esté en la partida indicada. El mecanismo permite repetir la secuencia si otro participante recibió la llave.

Equipa la llave y usa clic derecho a ≤4 bloques de la puerta `(1758,65–68,613–615)`. Se consume al abrir; una llave vanilla no sirve. La última sala finaliza automáticamente y retorna a `EXIT`.

## Prueba con bots (T47)

**7 de octubre de 2026**, solo `Servidor-agentes`, **25566**, `CD_TARGET=agents` y `scripts/test-server.sh`; Paper 26.3 build 157. Dos bots principales `T47A`/`T47B`, Mineflayer 4.39.0, protocolo 1.20.4 mediante ViaBackwards. 

SQLite run **45**, **COMPLETED**, `2026-10-07T06:48:27.232080140Z`–`2026-10-07T06:53:03.285724135Z`. Bajas de los dos supervivientes: **6 + 6**, cero muertes; ambos `survived=1`, `rewarded=1`. Supervivencia, espada/armadura de netherite, Resistencia V, saturación y visión nocturna para el combate. Física cliente desactivada; posicionamiento asistido por TP dentro de salas y movimientos reales al cruzar puertas. No se usaron kill de mobs, skipwave, modo test ni cambios de habilidades. **Verifica funcionamiento, no equilibrio para humanos ni percepción visual/audio.**

| Comprobación | Resultado |
|---|---|
| Plantillas compartidas | **OK**: `le-custodios` y `le-ecos` resueltos en las tres salas; 4 enemigos por sala, 12 bajas reales. |
| Llave externa | **OK**: limpiar sala 2 dejó su puerta cerrada y no creó llave de mob. |
| Puzzle físico | **OK**: clic real en cobre fuera de orden → estado 0; oro → 1, cobre → 2, amatista → llave ligada a la sala. El bloque final ejecutó `customdungeon key give @p laberinto-enigma`; no se entregó por consola. |
| Uso / progresión | **OK**: llave equipada, clic derecho cerca de la puerta, apertura y consumo; ambos cruzaron y completaron sala 3. |
| Ambiente | **OK**: paquetes de música y partículas por sala; Velocidad I (`effectId=0`, amplifier 0) aplicada a A/B en sala 1 y retirada al salir. |
| Premio / final | **OK**: ambos supervivientes recibieron 3 fragmentos y 40 XP, retorno inmediato a EXIT. |

Los volúmenes se inspeccionaron antes de construir y contenían únicamente aire: Templo 39382, Laberinto 47530 y Coloso 237558 bloques. Se corrigió durante la autoría el suelo del lobby (Y 64); después se comprobó en vivo el espacio de pies/cabeza de los tres vestíbulos y el lobby/cámara del Coloso sin resistencia. El combate completo usó las mismas salas y YAML; no se repitió tras ese cambio del vestíbulo. El análisis estático de los scripts confirma rutas continuas hasta checkpoints, spawners, placas y salida con las puertas abiertas.

`./gradlew test --tests '*T47ExampleValidationTest'`: **2 tests, 0 fallos**; codec y Validator reales, cero errores/avisos en mobs, presets y dungeons con oleadas resueltas; suelo y altura libre de los puntos de llegada/spawn. Recarga sin errores ni avisos de estos IDs. No hubo excepciones de CustomDungeons. Los avisos de paquetes históricos preexistentes no se corrigieron en T47.

La primera serie de clics fue rechazada por EssentialsX AntiBuild. Se reanudó la misma partida con la mano vacía y permisos **temporales** de interacción de botón/llave solo para A y `world=cd_dungeons`; se retiraron al cerrar. No se modificó WorldGuard ni se concedió OP. La configuración de bloques de comando del servidor de agentes se restauró a `false`: hay que habilitarla de nuevo para repetir este puzzle.

### MSPT

[Perfil spark EhgoEwyUOm](https://spark.lucko.me/EhgoEwyUOm), async, intervalo **4000 µs**, **1200 ticks**, 06:48:33.332–06:49:33.536 UTC. Cubre combate en salas 1/2, no la resolución posterior del puzzle.

| Métrica | Resultado |
|---|---:|
| MSPT total medio de Paper, último minuto al cerrar | 2.410 ms |
| MSPT total p95 / máximo | 4.957 / 14.916 ms |
| TPS, último minuto | 20,000 |
| Tiempo muestreado atribuido directamente a CustomDungeons | 160 ms |
| Estimación del plugin por tick / una partida | **160 / 1200 = 0.133 ms** |

Se decodificó `SamplerData` con los [schemas oficiales de spark](https://github.com/lucko/spark/tree/master/spark-common/src/main/proto/spark); se sumó solo el primer frame `dev.dasan.customdungeons.*` de cada rama de Server thread, sin duplicar descendientes. El MSPT de Paper es del último minuto de la captura; la atribución del plugin usa todos sus ticks. Los tres perfiles cumplen ≤2 ms **como estimación para estas ventanas**, sin medir el coste incremental contra una base vacía ni garantizar otras cargas. La IA vanilla y los demás plugins están en el MSPT total. En el Coloso coincidió también la verificación Gradle con parte de la captura.

### Bugs y límites

Ningún bug nuevo de producción atribuible a esta dungeon. No se modificó Java de producción. Escucha humana, render del cliente y ajuste de dificultad con jugadores reales pendientes. Los retardos finos de efectos no se certifican con este controlador.

Cierre: `scripts/test-server.sh stop` devolvió **«Servidor detenido.»**; guardado de chunks y fin de I/O completados, sin bots/controlador vivos, puertos 25566/18848 cerrados, permisos temporales retirados, `enable-command-block=false` restaurado. Temperatura máxima observada **74,35 °C** (no se alcanzó el umbral de 80 °C); arranques con más de 2500 MB disponibles y <75 °C. Se conservaron mundo, ejemplos y plugins ajenos. Evidencia bruta local ignorada en `.agent/`; resumen común en [pruebas integradas — T47](../../../guides/pruebas-integradas.md#t47--contenido-de-ejemplo).
