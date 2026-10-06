# Pruebas integradas del MVP (T18)

Ejecución del 6 de octubre de 2026, rama `feat/t18-integration`. Este informe distingue las comprobaciones ejecutadas de los pendientes; T18 no se marca completada mientras queden fallos o criterios sin verificar. No se modificó código de producción ni se crearon commits.

## Entorno y límites de la prueba

- Paper 26.3-157-main@4728a90, API 26.3.build.157-beta, Temurin Java 25.0.4.1, Linux aarch64 (Raspberry Pi).
- Multiverse-Core 5.8.1, Multiverse-Portals 5.3.0, ViaVersion/ViaBackwards 5.12.0, LuckPerms 5.5.71, WorldGuard 7.0.19, Essentials Economy mediante Vault. Spark integrado en Paper.
- Cuatro clientes offline: `T18A`, `T18B`, `T18C`, `T18D`. Node 22.22.2 y mineflayer 4.39.0, instalados fuera del repo en `/home/dasan/Desktop/Proyectos/plugins/servidor/bots/`.
- Los intentos con 1.21.11 y 26.1 entraron y fueron expulsados con `multiplayer.disconnect.invalid_player_movement`. **1.20.4** se mantuvo conectado mediante ViaBackwards. Se desactivó la simulación física del bot (`physicsEnabled: false`); los recorridos envían incrementos de posición de 0,15 bloques cada 50 ms. No son clientes humanos: se verifican estado, paquetes y eventos, no percepción visual o auditiva.
- WorldGuard se conserva con su configuración existente. No se añadieron flags para ocultar errores de integridad de habilidades. No se instaló una sonda ni se retiró ningún plugin preexistente.
- Las salas usan coordenadas de laboratorio, en el mundo vacío `cd_dungeons`. El portal está en `world`. La salida del ejemplo está en `cd_dungeons` en `(100,65,5)`.

La compatibilidad de mineflayer se consultó en su [repositorio oficial](https://github.com/PrismarineJS/mineflayer). La versión que funcionó aquí es la indicada arriba, no una afirmación sobre soporte nativo de 26.3.

## Preparación reproducible

Desde este worktree:

```bash
mkdir -p .agent
mkdir -p /home/dasan/Desktop/Proyectos/plugins/servidor/bots
timeout 180 npm install --cache /tmp/t18-npm-cache \
  --prefix /home/dasan/Desktop/Proyectos/plugins/servidor/bots mineflayer@4.39.0
# Con el servidor detenido; despliega exclusivamente CustomDungeons de esta rama.
timeout 180 scripts/test-server.sh deploy
timeout 180 ./gradlew test
```

La caché habitual `/home/dasan/.npm` es de solo lectura en este sandbox; usar `/tmp` evita `EROFS`. No se debe instalar `node_modules` ni crear un `package.json` en el repositorio.

Mantén **todos** los comandos de lifecycle y consola en una misma terminal persistente. Una llamada de terminal separada puede tener otro namespace de procesos: `clean_orphans` no ve el Java del primero y cierra su `screen`. El primer intento sufrió ese problema antes de las pruebas válidas; no cuenta como prueba del criterio de crash. No ejecutes `test-server.sh cmd` desde otro namespace mientras el servidor está activo.

```bash
timeout 190 scripts/test-server.sh start
# ... todos los comandos siguientes en esta misma terminal ...
timeout 190 scripts/test-server.sh stop
```

Si aparece `Otra operación del servidor está en curso.`, espera 120 segundos y reintenta hasta diez veces. No elimines el lock. En los ciclos válidos de esta ejecución no hubo contención.

Los ejemplos están en [ejemplos/dungeons](../reference/ejemplos/dungeons/) y [ejemplos/mobs](../reference/ejemplos/mobs/). Copia sus YAML a `Servidor/plugins/CustomDungeons/{dungeons,mobs}/` sin sustituir definiciones ajenas. Sus IDs empiezan por `t18-`. Los mundos referenciados deben estar cargados; no hace falta cambiar el `config.yml` existente para cargar estas definiciones.

Antes de construir superficies, reserva/carga los chunks (un `/fill` sobre un chunk descargado devuelve `That position is not loaded`):

```text
execute in minecraft:cd_dungeons run forceload add 0 0 110 15
execute in minecraft:cd_dungeons run forceload add 200 0 260 15
execute in minecraft:overworld run forceload add 298 298 310 308
execute in minecraft:cd_dungeons run fill 0 64 0 60 64 10 stone
execute in minecraft:cd_dungeons run fill 95 64 0 105 64 10 stone
execute in minecraft:cd_dungeons run fill 0 65 -1 60 68 -1 glass
execute in minecraft:cd_dungeons run fill 0 65 11 60 68 11 glass
execute in minecraft:cd_dungeons run fill -1 65 0 -1 68 10 glass
execute in minecraft:overworld run fill 298 69 298 310 69 308 stone
customdungeon reload
```

`execute in ... run minecraft:tp` y `minecraft:kill` son deliberadamente namespaced: Essentials intercepta `tp` y `kill` sin namespace y no interpreta los selectores de mobs. Además, un `minecraft:tp <bot> <coords>` directo desde consola teletransporta al mundo de la consola: para continuar dentro de la dungeon usa siempre `execute in minecraft:cd_dungeons run minecraft:tp ...`.

## Control de bots

El controlador de esta ejecución se conserva fuera del repo en `servidor/bots/control.cjs`; los escenarios y el helper están en `servidor/bots/t18/`. Los logs brutos locales quedan en `.agent/` (ignorados por Git). El controlador escucha **solo** en `127.0.0.1:18818`; no usa credenciales ni RCON. Su API acepta JSON: `connect`, `chat`, `snapshot`, `walk`, `use`, `equip`, `activate`, `click`, `close`, `quit`. Cada respuesta incluye posición, salud, experiencia, inventario y entidades; guarda también chats, ventanas, sonidos y BossBars.

En la terminal persistente:

```bash
timeout 7200 node /home/dasan/Desktop/Proyectos/plugins/servidor/bots/control.cjs \
  > .agent/bots.jsonl 2>&1 &
```

Cliente HTTP mínimo (guardar fuera del repo como `servidor/bots/request.py`):

```python
import json, sys, urllib.request
request = urllib.request.Request('http://127.0.0.1:18818',
    data=sys.argv[1].encode(), headers={'Content-Type':'application/json'})
print(urllib.request.urlopen(request, timeout=20).read().decode())
```

Ejemplos:

```bash
timeout 25 python3 /home/dasan/Desktop/Proyectos/plugins/servidor/bots/request.py \
  '{"action":"connect","name":"T18A","version":"1.20.4"}'
timeout 25 python3 /home/dasan/Desktop/Proyectos/plugins/servidor/bots/request.py \
  '{"action":"chat","name":"T18A","text":"/customdungeon"}'
timeout 25 python3 /home/dasan/Desktop/Proyectos/plugins/servidor/bots/request.py \
  '{"action":"walk","name":"T18A","dx":0.15,"steps":25}'
```

Concede permisos por consola. `T18A` necesita operador para WorldEdit, portal y GUI; los otros no son operadores para que el límite y los rechazos no tengan bypass:

```text
op T18A
lp user T18B permission set customdungeons.player.join true
lp user T18C permission set customdungeons.player.join true
lp user T18D permission set customdungeons.player.join true
lp user T18A permission set multiverse.portal.access.t18portal true
lp user T18B permission set multiverse.portal.access.t18portal true
lp user T18C permission set multiverse.portal.access.t18portal true
lp user T18D permission set multiverse.portal.access.t18portal true
```

## Resultados por criterio

| Criterio | Resultado | Evidencia / alcance |
|---|---|---|
| 1. Dungeon de tres salas, GUI, jefe | **FALLO parcial** | YAML cargado y aceptado por Validator; GUI abre el borrador correcto; textos del editor rotos (B02) y biblioteca de mobs inaccesible (B03). Creación completa por GUI no verificada. Dos fases verificadas a 75/120 y 30/120 de vida, con títulos y cambios de música cat → 13 → blocks; stop_sound al cerrar. Combo hook → anchor → meteors cargado y despachado, sin excepción; no se midió el retardo exacto entre pasos. |
| 2. Portal, lleno/en curso y antispam | **OK** | A cruzó al lobby. D cruzó con lobby lleno (02:39:37) y en curso (02:39:47): recibió el motivo y permaneció en world. Ocho join en <3 s generaron una sola denegación. |
| 3. Grupo, supervivientes y premios | **OK** | Run SQLite 4, COMPLETED. A/B: 2 diamantes + 25 XP + 3 esmeraldas + $10; C: 0 en todos, una muerte, survived=0 y rewarded=0. |
| 4. Puertas y correa | **OK en escenarios ejecutados** | TP a sala futura devuelve/rechaza entrada; movimiento por encima de la puerta acaba en sala 1; perla por encima no teletransporta al destino. Mob desplazado a x=25 vuelve al spawner en <=2 s. |
| 5. Recuperación de crash | **OK** | Runs 12 y 13 ABORTED tras dos SIGKILL; puertas restauradas (88 y 44 registros), tablas activas/journal vacías, sin mobs/ítems y llave retirada de inventario; A/B en salida. |
| 6. 43 habilidades | **FALLO** | 43 plantillas despachadas en test, 43 comparaciones de 7.137 bloques OK. dragon_breath lanza excepción (B01). Ladrón devuelve los 7 diamantes. Probar en vivo queda NO VERIFICADO por B03. |
| 7. Escalado 1 vs 4 | **OK** | 30 vs 50 vivos (cap=50); atributo MAX_HEALTH base 40 vs 58. Verificación adicional 02:41:45–52. |
| 8. RNF-02 | **OK para esta carga** | MSPT total medio 4,556 ms / p95 6,556 ms; coste muestreado del plugin 1,520 ms/tick, por debajo de 2 ms. 50 mobs durante los seis muestreos, 1200 ticks. |
| 9. Armadura en 26.3 | **OK por datos de servidor** | Los 12 tipos devolvieron HEAD/CHEST/LEGS/FEET de diamante en equipment (02:39:28–33). Renderizado humano no verificado. |

Resultados válidos de premios, servidor 02:29:08–12 (UTC-5):

```text
[Server] T18 COMPLETE t18-integracion 2
Balance of T18A: $10
Balance of T18B: $10
Balance of T18C: $0
```

Snapshots de los clientes: A/B `experience.points=25`, inventarios con 2 DIAMOND y 3 EMERALD; C `experience.points=0`, inventario vacío. Todos terminaron en `(100,65,5)`. C fue eliminado con una muerte antes del cierre; no hubo premios en los ciclos fallidos ni en las pruebas test. La DB confirma `run_players` de run 4: A/B `(survived=1,rewarded=1)`, C `(survived=0,rewarded=0,deaths=1)`.

Las 43 comparaciones de bloques dieron `T18 BLOCKS_UNCHANGED <id>` entre 02:29:42 y el final del barrido. El único ERROR de CustomDungeons en ese intervalo fue el de DragonBreathAbility, a las 02:31:43. Ausencia de excepciones no equivale a validar todos los daños, probabilidades, parámetros, objetivos externos y cancelaciones de combos.


## Reproducción de entrada y partida

Comandos del bot T18A, teletransportado primero a `(303,70,302)` en `world`:

```text
//pos1 305,70,300
//pos2 305,73,304
/mvp create t18portal
/mvp modify t18portal action-type command
/mvp modify t18portal action "console:customdungeon join %player% t18-integracion"
/mvp modify t18portal action-success-message @disabled
/mvp info t18portal
```

T18A avanza en X con el controlador. El log de MV-Portals confirma el cruce y el bot acaba en el lobby. El placeholder es `%player%` y la acción se ejecuta como consola. MV-Portals emite un warning transitorio de ubicación vacía al crear el objeto; el portal queda configurado y se comprueba por `mvp info` y cruce real.

Añade B y C por consola; envía ocho entradas de D separadas por 0,1 s. Con `max-players: 3`, D debe permanecer fuera y recibir una sola denegación en esa ventana. Tras iniciar, una denegación nueva pasados tres segundos debe decir «en curso».

```text
customdungeon join T18B t18-integracion
customdungeon join T18C t18-integracion
customdungeon join T18D t18-integracion
customdungeon start t18-integracion
customdungeon join T18D t18-integracion
```

Para aislar la lógica de partida del daño/latencia del bot, aplica Resistance V antes de la partida y mata expresamente a C:

```text
minecraft:effect give T18A resistance 600 4 true
minecraft:effect give T18B resistance 600 4 true
minecraft:effect give T18C resistance 600 4 true
minecraft:kill T18C
```

El escenario externo `complete.py` vacía inventarios y XP, crea una partida nueva, elimina C, intenta un teletransporte prematuro y una perla, fuerza un mob fuera de su sala, elimina las oleadas por consola, recoge la llave con A y hace clic derecho real con el bot sobre `(39,65,5)`. Avanza a `(43,65,5)`, mata el jefe y consulta inventarios/XP/balances. Esta prueba completa el flujo con jugadores conectados; las muertes de mobs son inducidas por consola, no una evaluación de equilibrio de combate.

```text
execute in minecraft:cd_dungeons run minecraft:tp T18A 23 65 5
execute in minecraft:cd_dungeons run minecraft:tp @e[type=zombie,x=0,y=64,z=0,dx=19,dy=10,dz=10,limit=1] 25 65 5
# Tras dos segundos, debe haberse recolocado en sala 1.
execute in minecraft:cd_dungeons run data get entity @e[type=zombie,x=0,y=64,z=0,dx=19,dy=10,dz=10,limit=1] Pos
execute in minecraft:cd_dungeons run minecraft:kill @e[type=zombie,x=0,y=64,z=0,dx=19,dy=10,dz=10]
execute in minecraft:cd_dungeons run minecraft:kill @e[type=skeleton,x=0,y=64,z=0,dx=19,dy=10,dz=10]
execute in minecraft:cd_dungeons run minecraft:tp T18A 23 65 5
execute in minecraft:cd_dungeons run minecraft:kill @e[type=zombie,x=20,y=64,z=0,dx=19,dy=10,dz=10]
execute in minecraft:cd_dungeons run minecraft:kill @e[type=skeleton,x=20,y=64,z=0,dx=19,dy=10,dz=10]
```

Consulta el `item` de la llave y mueve A a su posición para recogerlo. Equipa `tripwire_hook`, acerca A a `(37,65,5)` y usa `activate` sobre `[39,65,5]`. No añadas una llave normal con `/give`: necesita el PDC de la partida.

## Habilidades e integridad

La exploración del registro encuentra 43 IDs únicos. El escenario `abilities.py` genera una plantilla aislada por ID (`t18-a-<id>`) y reutiliza una dungeon de una sala. Parámetros por defecto; los de plantilla se fijan a `t18-skeleton`. No se presupone que un disparador incompatible pruebe la habilidad: `thief` y `vampirism` usan `ON_HIT`; `reflect` y `minion_shield`, `ON_DAMAGED`; `split_on_death` y `last_breath`, `ON_DEATH`; las restantes, `ON_SPAWN`.

Cada ejecución tiene marcadores `T18 ABILITY <id> BEGIN/END`, observación de seis segundos, snapshots y cierre. Para los disparadores de golpe/daño se induce el evento con `minecraft:damage`; para muerte, con `minecraft:kill`. Reflejo requiere adicionalmente un proyectil cuyo dueño sea el bot; una mera llamada al disparador sin proyectil no lo certifica.

El ejemplo `t18-habilidades.yml` es un **catálogo** editable: para reproducir pruebas aisladas conserva una única entrada de `abilities`, cambia el disparador según lo anterior y ejecuta `/customdungeon test t18-habilidades`. No es una plantilla equilibrada para producción. `more-fixtures.py` conserva la generación de las plantillas aisladas utilizadas en esta ejecución.

Comparación exacta de 7.137 bloques (61 × 9 × 13), incluyendo aire, suelo y paredes:

```text
execute in minecraft:cd_dungeons run minecraft:clone 0 64 -1 60 72 11 200 64 -1 replace
# Ejecutar habilidad, esperar y detener la prueba.
customdungeon stop t18-habilidades
execute in minecraft:cd_dungeons if blocks 0 64 -1 60 72 11 200 64 -1 all run say T18 BLOCKS_UNCHANGED <id>
```

La comparación tras limpiar detecta bloques rotos, quemados y temporales sin restaurar. No comprueba transitorios de duración menor que el muestreo ni demuestra por sí sola que cada efecto tenga el daño correcto. Los proyectiles, invocaciones, pociones y cambios de inventario se contrastan con los snapshots y logs.

## Escalado y carga

`t18-escalado` tiene una entrada base de 30 mobs y escalado 25 % de cantidad / 15 % de vida por jugador adicional. Ejecuta con un bot y con cuatro, fuerza inicio, cuenta únicamente los zombies de la región `(0..19,64..74,0..10)` y consulta `Health`. El límite del servidor es 50. Detén cada partida antes de la siguiente.

`t18-carga` tiene 50 zombies, dos habilidades por plantilla (`darkness_pulse`, `freeze`). Usa un único bot en supervivencia con Resistance V. Mantén la misma partida durante toda la medición; no abras GUI ni debug durante el perfil. Evita quemado solar en el mundo de pruebas durante esa ventana.

```text
customdungeon join T18A t18-carga
customdungeon start t18-carga
spark profiler --timeout 60
spark tps
customdungeon stop t18-carga
```

Se documentará la sintaxis realmente aceptada y la ventana medida; el profiler de fondo de Paper no debe confundirse con una captura nueva de 60 s. La [documentación de spark](https://spark.lucko.me/docs/Command-Usage) describe `profiler start --timeout 60` y las métricas de salud. El coste de CustomDungeons debe atribuirse al código del plugin, no al MSPT total de Paper y los demás plugins.

### Medición obtenida

Perfil [atjoAbYAMT](https://spark.lucko.me/atjoAbYAMT), captura async desde **07:40:18.449 hasta 07:41:18.739 UTC** (60,290 s), **1200 ticks**, intervalo de muestreo 4000 µs. Una partida activa, un bot participante y otros tres bots online fuera de ella. La ventana registra 107 entidades totales y 811 chunks; estos incluyen el resto del servidor, no solo la dungeon.

| Métrica | Resultado |
|---|---:|
| MSPT total medio, último minuto | 4,556361 ms |
| MSPT total p95, último minuto | 6,555991 ms |
| Mediana | 4,405644 ms |
| Máximo | 32,305176 ms |
| TPS | 20,0000 |
| Mobs vivos en muestras a 0/10/20/30/40/50/60 s | 50 en todas |
| CustomDungeons: tiempo muestreado acumulado | 1824 ms |
| CustomDungeons: coste estimado por tick y partida | **1,520 ms** |

Atribución: se decodificó `SamplerData` con los [schemas oficiales](https://github.com/lucko/spark/tree/master/spark-common/src/main/proto/spark). Se recorrió el árbol de `Server thread`, sumando solo el primer frame `dev.dasan.customdungeons.*` de cada rama para evitar contar sus hijos dos veces: `SessionTicker...run` 1820 ms y `EvokerFangsAbility.damage` 4 ms (listener registrado aunque esa habilidad no forma parte de la carga). **1824 / 1200 = 1,520 ms/tick**. El tiempo de `Server thread` (59972 ms) incluye espera entre ticks: no se multiplicó su porcentaje por el MSPT.

El objetivo RNF-02 de <=2 ms por partida activa se cumple **en esta captura, como estimación de muestreo**. No es una medición instrumental exacta ni una garantía para cualquier combinación de habilidades o múltiples partidas. El coste de IA vanilla de los mobs que Paper ejecuta fuera de los frames del plugin forma parte del MSPT total, no de esa atribución directa.

Se usó `spark profiler cancel` para retirar el profiler automático de fondo y después **`spark profiler start --timeout 60`**, la sintaxis explícita documentada. El alias corto pedido se deja en la receta conceptual, pero la captura válida es la de `start`. `spark tps` a 02:41:24 mostró p95 6,6 ms para el último minuto. El mensaje posterior `Average: 50ms` provino del *tickmonitor* (intervalo entre ticks), **no** del MSPT; se apagó el monitor tras comprobarlo. El health report adicional es [gzys4YwHFK](https://spark.lucko.me/gzys4YwHFK).

## Armadura

`t18-armaduras` invoca una plantilla de cada uno de los 12 tipos configurados, con casco, pechera, pantalones y botas de diamante. En modo test, consulta `equipment` de cada tipo por consola:

```text
execute in minecraft:cd_dungeons run data get entity @e[type=parched,x=0,y=64,z=0,dx=19,dy=10,dz=10,limit=1] equipment
```

Se comprueba el equipo del servidor y, cuando se capture, el paquete de equipo recibido por el bot. No hay inspección humana de renderizado.

## Crash y limpieza

Antes de enviar SIGKILL verifica que el PID es el Java de **esta** sesión `cd-test`, que no hay jugadores ajenos y que no hay otro agente usando el servidor. No uses un `pkill java`. Haz `save-all flush` para que la prueba tenga entidades/bloques efectivamente persistidos y espera a que terminen las escrituras asíncronas de CustomDungeons.

```text
customdungeon join T18A t18-integracion
customdungeon join T18B t18-integracion
customdungeon start t18-integracion
execute in minecraft:cd_dungeons if block 19 65 5 iron_block run say T18 CRASH_CLOSED
save-all flush
```

En la misma terminal: identifica el PID con `pgrep -af '[p]aper-26.3'`, envía `kill -9 <PID_VERIFICADO>`, espera su salida y arranca con el script. Reconecta los bots, consulta posición/salida, puertas y entidades. Consulta SQLite en modo solo lectura para comprobar `runs.result = ABORTED`, `active_sessions` y `temp_blocks` vacías. No deduzcas ABORTED solo de que la dungeon acepte otra entrada.

### Primera recuperación observada

Se mató exclusivamente el PID 531 de Java de `cd-test` a **07:42:54 UTC**, tras `save-all flush`. Antes: run 12 sin resultado, una sesión activa con A/B y **88** registros de bloques de puerta. El arranque terminó a 07:43:54; a 02:43:50 del servidor registró:

```text
Run recovery: 1 interrupted sessions; 1 original runs aborted; 0 block records await their worlds
```

SQLite después: run 12 `ABORTED`, `active_sessions=0`, `temp_blocks=0`. A y B reconectaron; aunque el mensaje de login conserva la ubicación persistida `(3,65,5)`, los snapshots posteriores confirman el teletransporte asíncrono a `(100,65,5)`. A las 02:44:30–32 los comandos dieron `T18 CRASH_DOOR_RESTORED`, `T18 CRASH_DOOR2_RESTORED` y `No entity was found` para zombie e ítem dentro de las tres salas.

Un segundo ciclo comenzó desde la misma dungeon recuperada y jugable. Se avanzó a la sala de llave, se probó su eliminación y recolocación (B04), y se llevó el ítem por consola a `(35.5,65,5.5)` para recogerlo sin modificar el plugin. Se verificó `tripwire_hook` con PDC en la hotbar de A y se hizo `save-all flush`; antes del segundo SIGKILL: run 13 pendiente, una sesión activa y **44** registros de bloques (segunda puerta). Este movimiento del ítem fue solo preparación del escenario de crash; no se presenta como corrección de B04.

### Segunda recuperación observada

SIGKILL exclusivo al PID 12540 de `cd-test` a **07:47:24 UTC**. El arranque a 02:48:20 volvió a informar una sesión interrumpida y un run original abortado. Run 13 = ABORTED; `active_sessions=0`, `temp_blocks=0`. Tras reconectar, A volvió a `(100,65,5)` con DIAMOND y ENDER_PEARL conservados, **sin TRIPWIRE_HOOK**; B permaneció en la salida. A 02:49:18–20 ambas puertas dieron los marcadores de AIR y no hubo zombie ni ítem dentro de la dungeon. Los dos escenarios separan la persistencia de mobs/puertas y la persistencia de llave/inventario.

## Focos de revisión de plan.md

| Foco | Resultado |
|---|---|
| Mob eliminado sin muerte por jugador | OK para `minecraft:kill`: avanzó la sala y completó la dungeon. Caída/lava/descarga no ejercitadas individualmente. |
| Llave perdida | **FALLO B04** al eliminar el ítem; fallback fuera de región y junto al muro. Caída/lava quedan pendientes después de corregir ese fallback. |
| Spam de portal | OK: cruce real aceptado/rechazado e intento de ocho join sin spam de mensajes; no se aplicaron hooks que alteren permisos del portal. |
| Plantilla inexistente | OK: copia temporal `t18-rota` referencia `t18-inexistente`, reload advierte `validation.template` y desactiva la dungeon; join rechaza entrada; el servidor continúa. Se retiró solo esa copia. |
| Objetivos externos | **NO VERIFICADO exhaustivamente**: tres bots no participantes estaban online durante carga y otras pruebas, pero no se cubrieron cada habilidad/radio, espectador y dos partidas vecinas con aserciones dedicadas. |

Se ejecutaron **354 tests unitarios**, 0 fallos, 0 errores, 0 omitidos, además de las comprobaciones de servidor. Los resultados unitarios no reemplazan los criterios manuales pendientes.

## Bugs y pendientes

Los hallazgos confirmados se enumeran más abajo; el cierre incorpora los pendientes de aceptación.

### Hallazgos confirmados durante el barrido

**B01 — ALTA: Aliento del Dragón falla al impactar en Paper 26.3.** Archivo `src/main/java/dev/dasan/customdungeons/ability/impl/borrowed/DragonBreathAbility.java:48`. Reproducción: plantilla husk con `dragon_breath`, `ON_SPAWN`, `NEAREST`, rango 40, probabilidad 1, sin telegraph, parámetros por defecto; bot en supervivencia a dos bloques; `/customdungeon test t18-habilidades`; esperar el impacto. Log del servidor:

```text
[02:31:43] [Server thread/ERROR]: Could not pass event ProjectileHitEvent to CustomDungeons v1.0.0-SNAPSHOT
java.lang.IllegalArgumentException: missing required data class java.lang.Float
at org.bukkit.craftbukkit.entity.CraftAreaEffectCloud.setParticle(CraftAreaEffectCloud.java:122)
at ...DragonBreathAbility.lambda$hit$0(DragonBreathAbility.java:48)
```

`c.setParticle(Particle.DRAGON_BREATH)` no proporciona los datos exigidos por 26.3. El proyectil sí se lanza, pero la nube prevista no llega a funcionar. Los bloques se conservaron. No se aplicó una corrección ni se presenta la habilidad como OK.

**B02 — MEDIA: todos los textos del editor de dungeons faltan.** Archivo `src/main/resources/messages.yml:330` (el inglés depende del fallback español para estas claves). El bloque `dungeon:` quedó bajo `livetest:`, mientras las clases buscan `gui.dungeon.*`. Reproducción: cargar los ejemplos, OP al bot, `/customdungeon`, clic en la entrada `t18-integracion` (slot 12 con las tres definiciones originales ordenadas). Abre `<gui.dungeon.list>` y luego `<gui.dungeon.title>`; botones sin descripción útil. Logs del 02:24:20–21: `Missing message key: gui.dungeon.list`, `gui.dungeon.dungeon`, `gui.dungeon.title`, `gui.dungeon.rooms`, etc. No es un `messages.yml` antiguo: las claves tampoco existen en esas rutas en el recurso de esta rama, y el fallback de T16 busca los mismos paths.

**B03 — ALTA: biblioteca de mobs y probar en vivo sin ruta de acceso.** Archivos `src/main/java/dev/dasan/customdungeons/command/CustomDungeonCommand.java`, `gui/menu/DungeonListMenu.java`, `gui/menu/MobLibraryMenu.java`. Reproducción: `/customdungeon` → lista → dungeon: solo ajustes, escalado, hooks, salas, premio y activar. No hay botón ni comando para la biblioteca. Comprobación estática complementaria: `rg -n 'new MobLibraryMenu' src/main/java` no encuentra ningún llamador. El servicio de probar en vivo existe y `MobMenu` tiene su botón, pero no es alcanzable por la entrada pública del plugin. Se dejan pendientes el editor de mobs y la ejecución real en vivo; no se introduce una ruta artificial mediante una sonda.

## Anexo: controlador utilizado

Guardar este código como `servidor/bots/control.cjs` (fuera del repo). El proceso se inicia con el `timeout` indicado arriba. Las rutas de escenarios externos son relativas al worktree al ejecutarlos; sus resultados quedan en `.agent/`.

```javascript
const mf=require('mineflayer'),http=require('http');
const bots={},events=[];const delay=ms=>new Promise(r=>setTimeout(r,ms));
function log(name,type,data){const e={time:new Date().toISOString(),name,type,data};events.push(e);console.log(JSON.stringify(e));}
http.createServer(async(req,res)=>{let raw='';req.on('data',c=>raw+=c);req.on('end',async()=>{try{let a=JSON.parse(raw||'{}'),b=bots[a.name];
if(a.action==='connect'){b=mf.createBot({host:'127.0.0.1',port:25565,username:a.name,auth:'offline',physicsEnabled:false,version:a.version||'26.1'});bots[a.name]=b;b.physicsEnabled=false;const wr=b._client.write.bind(b._client);b._client.write=(n,p)=>{if(['look','position','position_look'].includes(n)&&Object.values(p).some(v=>typeof v==='number'&&!Number.isFinite(v))){log(a.name,'INVALID_OUT',{n,p});return;}wr(n,p);};b.on("spawn",()=>{b.physicsEnabled=false;});
b._client.on('packet',(p,m)=>{if(['sound_effect','named_sound_effect','stop_sound','set_title_text','boss_bar'].includes(m.name))log(a.name,m.name,p);});for(const e of ['spawn','death','end','kicked','error']) b.on(e,x=>log(a.name,e,typeof x==='object'?JSON.stringify(x):String(x||'')));b.on('messagestr',m=>log(a.name,'chat',m));b.on('windowOpen',w=>log(a.name,'window',{title:w.title,items:w.slots.filter(Boolean).map(i=>({slot:i.slot,name:i.name,nbt:i.nbt}))}));await delay(5000);
}else if(a.action==='walk'){for(let i=0;i<(a.steps||20);i++){b.entity.position.x+=a.dx||0;b.entity.position.z+=a.dz||0;await delay(50);}}else if(a.action==='equip'){await b.equip(b.inventory.items().find(i=>i.name===a.item),'hand');}else if(a.action==='chat'){b.chat(a.text);await delay(1000);}else if(a.action==='move'){await b.look(a.yaw||0,a.pitch||0,true);b.setControlState(a.control||'forward',true);await delay(a.ms||1000);b.clearControlStates();}else if(a.action==='click'){await b.clickWindow(a.slot,0,0);await delay(500);}else if(a.action==='close'){b.closeWindow(b.currentWindow);}else if(a.action==='activate'){const v=require('vec3').Vec3;await b.activateBlock(b.blockAt(new v(...a.pos)));}else if(a.action==='use'){if(a.item)await b.equip(b.inventory.items().find(i=>i.name===a.item),'hand');await b.look(a.yaw||0,a.pitch||0,true);b.activateItem();await delay(1000);}else if(a.action==='attack'){let e=Object.values(b.entities).find(e=>e.id===a.id);if(e)b.attack(e);await delay(500);}else if(a.action==='quit'){b.quit();}else if(a.action==='events'){}
const snap=b&&b.entity?{pos:b.entity.position,health:b.health,game:b.game,experience:b.experience,items:b.inventory.items().map(i=>({name:i.name,count:i.count,slot:i.slot,nbt:i.nbt})),entities:Object.values(b.entities).map(e=>({id:e.id,name:e.name,type:e.type,pos:e.position})),window:b.currentWindow?.title}:null;
res.end(JSON.stringify({ok:true,snap,events:events.slice(-100)}));}catch(e){res.statusCode=500;res.end(JSON.stringify({error:String(e)}));}});}).listen(18818,'127.0.0.1');
```

### B04 — ALTA: recuperación de llave fuera de la sala y dentro del muro

Archivos `src/main/java/dev/dasan/customdungeons/session/KeyService.java` (`fallback`, `accessible`, `tick`) y `DoorService.java` (`beside`). Reproducción con el ejemplo original de tres salas y sus paredes de cristal en z=-1:

1. Completar sala 1, entrar a sala 2 y matar sus zombies/skeletons; la llave original aparece en la sala y se puede recoger (probado en la partida completada).
2. Sin recogerla, `execute in minecraft:cd_dungeons run minecraft:kill @e[type=item,x=20,y=64,z=0,dx=19,dy=10,dz=10]`.
3. Consultar el ítem y esperar dos segundos. Se recoloca en `(39.5,65.5,-0.5)`, fuera de todas las regiones (min z=0), sobre el muro z=-1; Paper lo empuja fuera. Los snapshots muestran IDs 63 y 66 sucesivos, con z=-1.1536/-1.1775, y el log a 02:44:44 muestra z=-1.1246.
4. Teletransportar A a `(38.5,65,0.5)` por el comando dentro del mundo de dungeons y esperar: sigue sin `tripwire_hook` en inventario.

`tick()` comprueba accesibilidad cada 20 ticks, encuentra siempre el fallback fuera de región y vuelve a crear la llave. Con esta pared el punto recuperado es inaccesible desde la sala. La llave no se pierde definitivamente del storage de sesión, pero la recuperación no ofrece un punto recogible estable. Corregir el cálculo de un punto seguro dentro de una región accesible requiere una tarea del arquitecto; no se cambió producción. El caso de caída al vacío/lava no se certifica como OK por compartir este fallback.


## Matriz de habilidades del barrido

La columna de ejecución significa que se despachó la habilidad en el contexto test con el evento necesario y se observó la ventana indicada. La integridad se comprueba después del cierre. No certifica aún probar en vivo, ni todas las variantes de parámetros (p. ej. cráneo azul/patrones/modos de Último aliento), daño exacto de cada proyectil, ni aislamiento de todos los objetivos ajenos.

| Habilidad | Ejecución test | Bloques tras limpieza |
|---|---|---|
| `anchor` | OK sin excepción | OK |
| `arrow_effect` | OK sin excepción | OK |
| `blaze_volley` | OK sin excepción | OK |
| `blindness` | OK sin excepción | OK |
| `breeze_leap` | OK sin excepción | OK |
| `chaos` | OK sin excepción | OK |
| `cobweb` | OK sin excepción | OK |
| `creeper_blast` | OK sin excepción | OK |
| `darkness_pulse` | OK sin excepción | OK |
| `disarm` | OK sin excepción | OK |
| `double` | OK sin excepción | OK |
| `dragon_breath` | FALLO: excepción al impactar | OK |
| `dragon_roar` | OK sin excepción | OK |
| `earthquake` | OK sin excepción | OK |
| `elder_curse` | OK sin excepción | OK |
| `ender_blink` | OK sin excepción | OK |
| `enrage` | OK sin excepción | OK |
| `evoker_fangs` | OK sin excepción | OK |
| `freeze` | OK sin excepción | OK |
| `ghast_fireball` | OK sin excepción | OK |
| `guardian_beam` | OK sin excepción | OK |
| `healer` | OK sin excepción | OK |
| `hook` | OK sin excepción | OK |
| `last_breath` | OK sin excepción | OK |
| `launch_up` | OK sin excepción | OK |
| `lightning` | OK sin excepción | OK |
| `meteors` | OK sin excepción | OK |
| `minion_shield` | OK sin excepción | OK |
| `on_hit_effect` | OK sin excepción | OK |
| `reflect` | OK, evento adicional con flecha; daño cancelado | OK |
| `roar_knockback` | OK sin excepción | OK |
| `shulker_bullet` | OK sin excepción | OK |
| `sonic_boom` | OK sin excepción | OK |
| `split_on_death` | OK sin excepción | OK |
| `summon_minions` | OK sin excepción | OK |
| `summon_vexes` | OK sin excepción | OK |
| `swap` | OK sin excepción | OK |
| `thief` | OK sin excepción | OK |
| `vampirism` | OK sin excepción | OK |
| `wind_charge` | OK sin excepción | OK |
| `witch_potions` | OK sin excepción | OK |
| `wither_shockwave` | OK sin excepción | OK |
| `wither_skulls` | OK sin excepción | OK |

Ladrón: 7 DIAMOND → 0 en hotbar → 7 devueltos al detener (también repetido sin Resistance). Vampirismo: caster 100 → 50 por daño controlado → 51 después de infligir 4, parámetro percent=25. Reflejo: flecha con Owner UUID de A, salud del caster sigue en 100; no se probaron variantes de tridente/poción.


## Pendientes para aceptación completa

- Creación completa por GUI, textos y biblioteca accesible después de B02/B03; la petición permitió preparar definiciones con YAML, pero esa sustitución no demuestra la experiencia de edición.
- Las 43 habilidades en **probar en vivo**: no alcanzable por una ruta pública (B03). `test` sí se ejercitó para todas, con el fallo B01.
- Variantes de parámetros (cráneo azul, patrones de fangs, modos de Last Breath), números exactos de daño/curación en todas las habilidades, duración de combos y cancelación por muerte, renderizado/audio humano, aislamiento exhaustivo de objetivos externos.
- Inventario lleno y `/claim`, devolución de robo offline, reconexión normal sin crash, bajas por lava/vacío/descarga y cooldown. Son requisitos del MVP que no se presentan como certificados por estos nueve escenarios; los tests unitarios cubren parte de ellos.
- Recuperación de llave perdida debe repetirse con vacío/lava y paredes tras B04.

T18 no se marca completada. El arquitecto puede convertir **B01–B04** en tareas T18.x; no se añadieron tareas ni se modificó producción.

## Anexo: regenerar y ejecutar el barrido sin archivos locales previos

Los `.yml` de los ejemplos usan sintaxis JSON, un subconjunto válido de YAML que `YamlConfiguration` cargó en esta ejecución. Por eso el siguiente generador usa únicamente la biblioteca estándar de Python. Guarda el script fuera del repo y ejecútalo desde el worktree, en la misma terminal persistente del servidor y del controlador. No ejecutes partidas simultáneas en esa misma arena. Esta receta crea IDs nuevos `t18-sweep-*`; retíralos al terminar.

```python
import json, pathlib, subprocess, time, urllib.request
from copy import deepcopy
server = pathlib.Path('/home/dasan/Desktop/Proyectos/plugins/servidor/Servidor')
examples = pathlib.Path('docs/reference/ejemplos')
catalog = json.loads((examples/'mobs/t18-habilidades.yml').read_text())
arena = json.loads((examples/'dungeons/t18-habilidades.yml').read_text())
def command(text, delay=.5):
    for attempt in range(11):
        p = subprocess.run(['timeout','15','scripts/test-server.sh','cmd',text],
                           capture_output=True, text=True)
        if p.returncode == 0:
            time.sleep(delay)
            return
        if 'Otra operación' not in p.stderr or attempt == 10:
            raise RuntimeError(p.stderr)
        time.sleep(120)
def client(action, **values):
    payload = json.dumps(dict(action=action, name='T18A', **values)).encode()
    return json.loads(urllib.request.urlopen(urllib.request.Request(
        'http://127.0.0.1:18818', data=payload,
        headers={'Content-Type':'application/json'}), timeout=20).read())
for instance in catalog['abilities']:
    ability = deepcopy(instance)
    id = ability['ability-id']
    trigger = {'thief':'ON_HIT', 'vampirism':'ON_HIT',
               'reflect':'ON_DAMAGED', 'minion_shield':'ON_DAMAGED',
               'split_on_death':'ON_DEATH', 'last_breath':'ON_DEATH'}.get(id,'ON_SPAWN')
    ability.update(trigger=trigger, chance=1, **{'cooldown-ticks':0,'telegraph-ticks':0})
    if id == 'last_breath': ability['params']['mode'] = 'SUMMON'
    template = deepcopy(catalog)
    template['abilities'] = [ability]
    template['display-name'] = 'T18 sweep '+id
    name = 't18-sweep-'+id
    path = server/'plugins/CustomDungeons/mobs'/f'{name}.yml'
    if path.exists(): raise RuntimeError('No sobrescribir: '+str(path))
    path.write_text(json.dumps(template))
    dungeon = deepcopy(arena)
    dungeon['rooms'][0]['spawners'][0]['waves'][0]['entries'] = [
        {'template-id':name,'count':1,'delay-ticks':0}]
    destination = server/'plugins/CustomDungeons/dungeons/t18-sweep.yml'
    destination.write_text(json.dumps(dungeon))  # ID reservado a esta receta
    command('customdungeon reload')
    command('minecraft:effect give T18A resistance 600 4 true')
    command('minecraft:clear T18A')
    command('minecraft:give T18A diamond 7')
    command(f'say T18 ABILITY {id} BEGIN')
    client('chat', text='/customdungeon test t18-sweep')
    time.sleep(3)
    caster = '@e[type=husk,x=0,y=64,z=0,dx=19,dy=10,dz=10,limit=1]'
    prefix = 'execute in minecraft:cd_dungeons run '
    if trigger == 'ON_HIT':
        command(prefix+f'minecraft:damage T18A 1 minecraft:mob_attack by {caster}')
    elif id == 'minion_shield':
        command(prefix+f'minecraft:damage {caster} 1 minecraft:generic')
    elif trigger == 'ON_DEATH':
        command(prefix+f'minecraft:kill {caster}')
    # Reflejo: añadir aquí la flecha con Owner de A descrita más abajo.
    time.sleep(3)
    print(id, 'antes del cierre', client('snapshot')['snap'])
    command('customdungeon stop t18-sweep')
    print(id, 'después del cierre', client('snapshot')['snap'])
    command(prefix+f'if blocks 0 64 -1 60 72 11 200 64 -1 all run say T18 BLOCKS_UNCHANGED {id}')
    command(f'say T18 ABILITY {id} END')
```

Prepara el clon de referencia antes de ejecutar ese barrido, como se indica en la sección de integridad. El generador no copia `t18-skeleton`: instala primero los ejemplos base. Para Reflejo, el bot offline `T18A` tiene UUID `125fbff8-acce-3096-9640-101f373e7f7b`; convierte sus 16 bytes a cuatro enteros con `struct.unpack('>iiii', uuid.UUID(...).bytes)` y pásalos como `Owner:[I;...]`:

```text
execute in minecraft:cd_dungeons run minecraft:summon arrow 3.0 66.0 5.0 {Motion:[1.0d,0.0d,0.0d],Owner:[I;308264952,-1395773290,-1774186465,926842747],damage:1.0d}
```

Usa coordenadas decimales: con `3 66 5`, el comando centra X/Z en el bloque y la flecha puede pasar al lado del caster. La primera tentativa de Reflejo tuvo ese problema y no se contó como prueba del efecto; se repitió con decimales a 02:49:25: caster permaneció en 100 HP tras el impacto, sin excepción. El disparador sin proyectil solo verifica que su guard no lanza error.

Para repetir las fases, instala `t18-jefe.yml`, usa `/customdungeon test t18-jefe` y aplica dos golpes de 45 separados por dos segundos con `minecraft:damage` al husk de la sala. Los paquetes de título/música del bot son evidencia observable aun sin clientes humanos. Para repetir el premio, vuelve a los YAML de integración y al escenario de tres jugadores; `test` no entrega premios.

## Cierre e higiene

El servidor se apagó con `scripts/test-server.sh stop` a 02:50:45–46 y devolvió **`Servidor detenido.`**. En la misma terminal no quedó un proceso Paper y se terminó el controlador Node. Se retiraron únicamente las seis definiciones de dungeon y 58 plantillas temporales instaladas por esta ejecución, se eliminó `t18portal`, se deshicieron los dos permisos concedidos a cada bot y el OP de A, y se retiraron los chunks forzados del laboratorio. Se reactivó `advance_time` tras la ventana sin quemado solar. Las superficies de laboratorio permanecen para reproducción; no se vaciaron mundos ni bases de datos de plugins.

Tras el cierre: `active_sessions=0`, `temp_blocks=0`, ningún YAML `t18-*` de esta prueba permanece instalado, lista de MV-Portals vacía (como al inicio), hashes de los jars iguales al snapshot posterior al despliegue de CustomDungeons. No se instalaron/retiraron otros plugins. Se conservan los runs y logs de evidencia de la prueba; no se borró historial del servidor.

Entregables del worktree: esta guía y **6 YAML de dungeons + 17 YAML de mobs** en `docs/reference/ejemplos/`. Los ejemplos tienen referencias completas a plantillas y IDs de habilidades registrados. `git diff --check` no presentó errores; el estado solo incluye esta guía y esa carpeta nuevas. No hay commits ni cambios de producción.
