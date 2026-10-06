# La Guarida del Warden

Paquete de contenido para Paper 26.3 / Java 25 y CustomDungeons de esta rama (base `66d0f98`, metadatos del jar `1.0.1`). Requiere la implementación de RF-LLA-01/02: portador `*` y uso de llaves a ≤4 bloques. **Verificación parcial: el runtime de esta rama ignora la llave de la última sala y permite teletransportarse tras su puerta cerrada. Los YAML conservan las tres salas y las tres puertas con llave solicitadas; no se modificó producción para ocultar esos fallos.** El número de versión por sí solo no garantiza esas funciones en un jar antiguo.

## Instalación en otro servidor

1. Carga el mundo Bukkit `cd_dungeons` (clave `minecraft:cd_dungeons`) con Multiverse-Core. Reserva el área X=784–870, Y=64–87, Z=495–522; debe estar vacía. En otro emplazamiento, cambia **todas** las coordenadas de construcción y YAML. No ejecutes la construcción durante una partida.
2. Copia `mobs/*.yml` a `plugins/CustomDungeons/mobs/` y `dungeons/guarida-warden.yml` a `plugins/CustomDungeons/dungeons/`. Conserva definiciones ajenas; los nombres de este paquete no deben colisionar con ellas.
3. Ejecuta [build.mcfunction](build.mcfunction) desde consola, **una línea cada vez**, sin `/`. Tras la primera orden `forceload add`, espera al menos dos segundos a que carguen los chunks antes de enviar los `fill`. Cada volumen está por debajo del límite vanilla de 32768 bloques. La última orden retira las cargas forzadas de construcción; las partidas gestionan sus propios chunks.
4. Alternativamente, coloca el archivo en `data/guarida_warden/function/build.mcfunction` de un datapack con `pack.mcmeta` compatible con tu build de 26.3. **Precarga primero** con `execute in minecraft:cd_dungeons run forceload add 784 494 870 523`, espera la carga y luego ejecuta `function guarida_warden:build`. El propio archivo retira las cargas al finalizar. No dependas de que una precarga y los `fill` de la misma función se resuelvan en el mismo tick.
5. Con Multiverse-Portals y WorldEdit, ejecuta [portal.txt](portal.txt) en el orden indicado: construcción desde consola, selección/creación desde un administrador conectado en `world`, y permiso del portal desde consola. El marco queda en X=20, Y=112–116, Z=8–12, cerca del spawn `(0,111,0)` usado aquí. Si tu spawn/terreno difiere, adapta el marco, la selección **y la salida del YAML**. El interior del marco está vacío; no necesita bloques de portal al Nether.
6. Sin partidas activas, ejecuta `customdungeon reload`. Confirma la recarga en el log y abre `/customdungeon` → Dungeons creadas → `guarida-warden`: debe indicar **Errores: 0**. La acción del portal es exactamente `console:customdungeon join %player% guarida-warden`; CustomDungeons sigue validando al jugador, capacidad y estado.

Se concedió únicamente el acceso público a este portal al grupo `default`; `customdungeons.player.join` ya es `true` por defecto. Si restringes el acceso a otro grupo, sustituye esa concesión. No hace falta conceder permisos de administración a jugadores normales.

## Recorrido y contenido

Dos a cuatro jugadores, lobby de 15 segundos, tres vidas, inventario conservado, límite de 30 minutos y sin cooldown. Cantidad fija; +10 % de vida por jugador por encima de dos. Cada superviviente obtiene cinco diamantes y 100 XP.

Todo el recorrido está a Y=65, suelo Y=64 y eje Z=508,5. Lobby: `(792.5,65,508.5)`. Salida física: plataforma X=862–870; la salida configurada de la partida es `(0.5,111,0.5)` en `world`. En esta rama el grupo vuelve allí directamente al morir el último enemigo de la sala 3, sin llave final (B01).

| Sala | Interior construido | Región YAML (inclusiva) | Spawner / radio | Puerta |
|---|---|---|---|---|
| 1 | X=801–816, Z=501–516, Y=65–74: **16×10×16** | `(801,64,501)`–`(817,75,516)` | `(809.5,65,508.5)` / 2 | X=817, Y=65–67, Z=507–509 |
| 2 | X=818–833, Z=501–516, Y=65–74: **16×10×16** | `(818,64,501)`–`(834,75,516)` | `(826.5,65,508.5)` / 2 | X=834, Y=65–67, Z=507–509 |
| 3 | X=835–860, Z=496–521, Y=65–86: **26×22×26** | `(835,64,496)`–`(861,87,521)` | `(848.5,65,508.5)` / 2 | X=861, Y=65–67, Z=507–509 |

Deepslate, sculk, pilares y luces de almas; entrada abierta en X=800. Los huecos de puertas quedan en aire al construir. Todas las salas usan `unlock: KEY` y `key-carrier-template-id: "*"`: comportamiento solicitado: llave al terminar la sala, en la última posición de muerte. Se confirmó en las salas 1 y 2; B01 impide hacerlo en la sala 3. Su token incluye la partida y sala; una llave vanilla no sustituye la del plugin. Equípala y usa clic derecho a ≤4 bloques de la puerta; se consume al abrirla.

| Sala | Oleada 1, simultánea | Oleada 2, escalonada salvo jefe | Total |
|---|---|---|---:|
| 1 | 2 `gw-vigia` + 2 `gw-arquero` | 2 `gw-vigia` + 1 `gw-arquero`, intervalo 20 ticks | 7 |
| 2 | 2 `gw-centinela` + 2 `gw-acechador` | 2 `gw-centinela` + 1 `gw-acechador`, intervalo 20 ticks | 7 |
| 3 | 2 `gw-centinela` + 2 `gw-acechador` | 1 `warden-colosal` + 2 `gw-acechador`, simultáneos | 7 |

Pausa de 60 ticks entre oleadas. Todos los spawners y sus radios caben en la región y tienen suelo y altura libres.

- `gw-vigia`: zombie, 32 HP, daño 4, hierro completo y espada; ralentización al golpear.
- `gw-arquero`: esqueleto, 28 HP, daño 3, hierro completo y arco; flechas con ralentización.
- `gw-centinela`: husk, 64 HP, daño 7, diamante completo y espada; Fuerza I, vampirismo y rugido con empuje.
- `gw-acechador`: stray, 56 HP, daño 5, hierro completo y arco; Velocidad I, flechas con debilidad y pulso de oscuridad.
- `warden-colosal`: WARDEN, **1024 HP**, daño 18, velocidad 0,25, resistencia al empuje 1 y **escala 5**. BossBar azul y música inicial `music_disc.5`. `sonic_boom` y `darkness_pulse`; combo `roar_knockback → sonic_boom → earthquake` cada 16 s, retardos 0/30/30 ticks. Al **66 % (792 HP configurados)** añade rugido, título y música `13`; al **33 % (396 HP configurados)** añade terremoto, título y música `blocks`. Cada transición tiene 20 ticks de invulnerabilidad. En el servidor de agentes, `spigot.yml` limita MAX_HEALTH a 1024: la vida efectiva fue 1024 y los umbrales efectivos 675,84/337,92. Ver C01; todos los valores YAML pasan el Validator y el editor.

La configuración `entity-heights.warden: 2.9` da **2,9×5 = 14,5 bloques**; los 22 bloques interiores dejan 7,5 de margen vertical. HP ≤2048, daño ≤1000, velocidad/resistencia ≤1, escala ≤10; parámetros específicos dentro de los ParamSpec de [habilidades](../../habilidades.md). Las fases usan fracciones 0,66/0,33, no 66/33.

## Verificación en el servidor de agentes

Prueba del 6 de octubre de 2026, solo `Servidor-agentes`, **25566**, mediante `scripts/test-server.sh` con `CD_TARGET=agents`. Se compiló y desplegó esta rama sin editar Java ni otros plugins. Antes del arranque: 5735 MB disponibles (`free -m`), superior al mínimo de 2500 MB. Dos clientes mineflayer 4.39.0 / protocolo 1.20.4 mediante ViaBackwards: `WardenQA`, `WardenQB`; física del cliente desactivada y movimientos de 0,15 bloques/50 ms. Se inspeccionaron dos volúmenes de 11016 y 24864 bloques antes de construir: ambos contenían únicamente aire.

Los bots usaron supervivencia, equipo de netherite, Resistencia V, saturación y visión nocturna. Fuerza III durante salas normales; retirada al aparecer el jefe. Los ataques son paquetes reales de cliente. Se usaron teletransportes dentro de la sala para posicionar los bots, además de desplazamientos reales por las puertas abiertas. No se usó `kill`, `skipwave`, modo test, invulnerabilidad del plugin ni se apagaron sus mecánicas. La prueba certifica progresión técnica; no el equilibrio para humanos.

Partida normal del **6 de octubre de 2026, 12:44:31–12:50:12 UTC-5**; SQLite run **1**, `COMPLETED`. Ambos supervivientes, cero muertes: WardenQA 12 bajas, WardenQB 9. Las 21 bajas fueron por ataques de jugadores. No hubo excepciones ni mensajes ERROR en el log del ciclo.

| Comprobación | Resultado y evidencia |
|---|---|
| Portal / lobby | Ambos cruzaron el volumen real del portal (12:44:16 y 12:44:18), llegaron al lobby y esperaron la cuenta atrás normal. No se forzó `start`. |
| Validator / editor | Recargas correctas; lista `guarida-warden` verde, **Errores: 0**. Abiertos editor, salas, biblioteca y editor de `warden-colosal`, sin guardar borradores. Los YAML nuevos no generaron errores de validación. |
| Sala 1: llave / puerta | **OK**. Dos oleadas, siete bajas; llave con PDC `partida:sala-1` tras el último esqueleto, en `(816.70,65,508.41)`. No había llave mientras quedaban mobs. Recogida real, intento a 5,5 bloques sin consumir, uso a 1,5 bloques, nueve bloques restaurados a aire y llave consumida. |
| Sala 2: llave / puerta | **OK**. Dos oleadas, siete bajas; llave `partida:sala-2` tras el último stray, en `(833.70,65,501.38)`. Mismas comprobaciones de distancia, apertura de nueve bloques y consumo. |
| Sala 3: llave / puerta | **FALLO B01**. Dos oleadas mixtas, siete bajas y jefe derrotado. Se completó/resetearon puertas y se teletransportó al grupo sin crear ni usar la llave final. No se contabiliza la restauración durante reset como apertura con llave. |
| Puertas cerradas: movimiento | **OK en las tres**: intentos con paquetes de movimiento se frenaron en X≈816,55 / 833,55 / 860,55, sin atravesar los bloques. |
| Puertas cerradas: TP | Salas 1 y 2: teletransportes a la sala futura bloqueados, con bots sin op y con op respectivamente. Sala 3: **FALLO B02**, TP detrás de la puerta permitido. |
| Correa de mobs | Un zombie, un husk y el Warden desplazados por consola fuera de su región volvieron dentro al comprobarlos 1,3 s después. Esto verifica recuperación, no ausencia de desplazamiento transitorio. |
| Warden / escala | Atributo Scale consultado: **5,0**. Durante el combate natural las posiciones observadas permanecieron dentro de su sala; última posición `(858.75,65,506.57)`. No cruzó físicamente la puerta. |
| Fases / BossBar | Ambos recibieron la BossBar azul. Fase 66 % a **12:49:15,852**, BossBar 0,65625 (672/1024); fase 33 % a **12:49:44,404**, BossBar 0,328125 (336/1024). Un título por fase y jugador, sin excepciones. |
| Música / combo | Configuración `5 → 13 → blocks`; paquetes `stop_sound` de 5/13 al cambiar fase y de blocks al morir, en ambos bots. Combo de tres pasos aceptado y combate mayor de 16 s sin excepciones; no se instrumentaron por separado sus retardos. La captura no registró los paquetes numéricos `sound_effect`: escucha humana pendiente. |
| Premio | Ambos recibieron cinco diamantes; DB `survived=1`, `rewarded=1`. |

### Spark / MSPT

Perfil [tKz24HfN08](https://spark.lucko.me/tKz24HfN08), motor async, intervalo **4000 µs**, **1200 ticks**, desde **17:48:15,895 hasta 17:49:16,313 UTC** (60,418 s). Dos jugadores y una partida; Warden vivo durante toda la ventana, con sus escoltas al principio y combate directo después. La captura alcanza la transición al 66 %; **no cubre la fase al 33 %**.

| Métrica | Resultado |
|---|---:|
| MSPT total medio de Paper, último minuto | **3,992 ms** |
| MSPT total p95 | **10,416 ms** |
| MSPT total máximo | 100,555 ms |
| TPS del último minuto | 20,000 |
| CustomDungeons, tiempo muestreado en Server thread | **224 ms** |
| CustomDungeons estimado por tick / partida | **224 / 1200 = 0,187 ms** |

Se decodificó `SamplerData` con los [schemas oficiales de spark](https://github.com/lucko/spark/tree/master/spark-common/src/main/proto/spark). Se sumó solo el primer frame `dev.dasan.customdungeons.*` de cada rama de `Server thread`, sin duplicar hijos: ticker 184 ms; listeners y seguimiento de teletransportes 40 ms. No se multiplicó un porcentaje del tiempo de espera del hilo por el MSPT. **Cumple ≤2 ms para la carga y ventana capturadas**, como estimación de muestreo; no garantiza el coste de todas las fases ni de otros grupos/dungeons. Los valores totales de Paper incluyen IA vanilla y los demás plugins.

### Bugs del plugin: reproducción exacta, sin correcciones

**B01 — Última sala KEY completada sin llave (bloquea el requisito de la sala 3).**

1. Instala este paquete sin cambiar `rooms[2].unlock: KEY`, portador `*` ni puerta en X=861.
2. Dos jugadores cruzan el portal; eliminan las dos oleadas de salas 1 y 2 y usan sus llaves.
3. En sala 3 eliminan la primera oleada y los dos stray de la segunda; el último mob es `warden-colosal`.
4. Antes de la última baja, comprueba `execute in minecraft:cd_dungeons if block 861 65 508 iron_block run say GUARIDA FINAL CLOSED`.
5. Mata al Warden por ataques: **sin clic derecho ni llave**, aparecen COMPLETE/FREE y ambos jugadores regresan a world con premio. No aparece un ítem con PDC de `sala-3`.

Ocurrió a 12:50:12–13. `DungeonSession.tick`, línea 131, llama `finish(true)` para la última sala antes de `services.roomCleared`; `openDoor`, línea 162, también rechaza avanzar desde la última sala. El Validator acepta la puerta KEY final, pero el runtime no espera esa llave. No se añadió una cuarta sala ni se cambió el modo de desbloqueo para eludirlo.

**B02 — Teletransporte detrás de la puerta final cerrada.**

1. Entra en sala 3 mientras está en combate; confirma que X=861, Y=65–67, Z=507–509 sigue siendo hierro.
2. Desde consola ejecuta `execute in minecraft:cd_dungeons run minecraft:tp WardenQA 863.5 65 508.5`.
3. El bot pasa de X≈837 a **X=863,5**, al otro lado, y la puerta sigue cerrada. Repetir el movimiento físico contra la puerta desde X=859,5 sí se frena en X≈860,55.

El mismo procedimiento hacia las salas 2 y 3 antes de desbloquearlas sí fue rechazado. `SessionListener.blocked` comprueba solo las regiones de salas futuras; tras la última puerta no hay región futura. El caso documentado es un TP de consola, no una prueba de perlas o chorus en esa salida. No se modificó la protección del plugin.

### Observaciones del entorno

- **C01 — Tope de vida efectiva:** `spigot.yml`, `settings.attribute.maxHealth.max: 1024.0`, limita el atributo, aunque `max-health: 1200` está dentro del rango 0/1–2048 del Validator. Reproducción: invoca esta plantilla en la segunda oleada y consulta `execute in minecraft:cd_dungeons run data get entity @e[type=warden,x=835,y=64,z=495,dx=26,dy=24,dz=28,limit=1] Health`: **1024.0f**. `attribute ... minecraft:max_health get` consulta el mismo límite efectivo. Las fases se calcularon sobre ese máximo real. No se cambió `spigot.yml`; en otro servidor, documenta su límite y el escalado antes de equilibrar vida y umbrales.
- **EssentialsAntiBuild:** sin op, bloqueó la recogida de la llave y mostró denegaciones de uso de la espada. Los mobs sí murieron por ataques y la llave válida quedó en su posición de muerte. Al dar op solo a los bots, la misma llave se recogió inmediatamente y continuó la misma partida. No se cambió Essentials ni se atribuye esta restricción a CustomDungeons. Retirados los op al terminar.
- **Plantilla ajena `warden.yml`:** el servidor ya contiene una plantilla con velocidad fuera de rango y herramientas reservadas en HAND/OFF_HAND. Sus warnings se repiten en reload; no se tocó. No corresponden a los cinco YAML de este paquete.
- **Multiverse-Portals:** aviso transitorio de LOCATION vacía al crear el objeto; tras configurar acción, `mvp info` confirmó coordenadas correctas y ambos cruces funcionaron. No fue un fallo persistente del portal.

### Limpieza y alcance final

Servidor apagado mediante `scripts/test-server.sh stop` a **12:53:47–48 UTC-5**; puerto 25566 cerrado. Bots desconectados y controlador 18819 cerrado, sin procesos de bot pendientes. Retirados op, efectos, equipo y permisos individuales temporales; se conserva el permiso público del portal. No se tocaron `servidor/Servidor`, sus plugins ni otros worktrees; no se borró ningún plugin del servidor de agentes y no hubo commits.

SQLite: `active_sessions=0`; los 27 bloques de las tres puertas están marcados `restored=1`, **cero pendientes**. Los nueve bloques de cada hueco se comprobaron en aire con condiciones de consola. Escaneo final de la cámara: aire y decoración esperada, sin entidades ajenas a jugadores ni bloques temporales. Cero cargas forzadas en cd_dungeons y world. Los YAML, la construcción y el portal quedan instalados en el servidor de agentes para reproducir los fallos.

Evidencia bruta temporal en `.agent/`: `warden-results.jsonl`, `warden-bots.jsonl`, `warden-events-final.json`, `warden-spark-metrics.json`, logs de consola y comandos. El controlador mineflayer de esta tarea está en `servidor/bots/warden/control.cjs` y solo apunta a 25566; el controlador antiguo `bots/control.cjs` apunta a 25565 y **no debe usarse** para reproducir esta tarea.

