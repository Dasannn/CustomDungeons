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

## v1.1 (T45)

### Alcance y entorno

Ejecución del 6–7 de octubre de 2026, hora de Bogotá (UTC−5), sobre `test/t45-integration`, HEAD `e672656`. SHA-256 del jar desplegado: `cee214810ee54449060da83267e4a82ca674f78e334f19060fbbe9e42b2056ce`. Los registros de los bots usan UTC; los horarios citados abajo son los de Paper, salvo indicación contraria. Se verificaron los doce escenarios solicitados de T45, con las limitaciones indicadas al final. Los resultados históricos de T18, sus bugs y sus rutas de servidor no describen esta ejecución.

Se utilizó exclusivamente `/home/dasan/Desktop/Proyectos/plugins/servidor/Servidor-agentes`, puerto **25566**, Paper **26.3 build 157**, Java **25** y los comandos de `scripts/test-server.sh` con `CD_TARGET=agents`. No se ejecutó `CD_TARGET=user`, ni se modificó `servidor/Servidor`. Mineflayer 4.39.0 se conectó offline mediante ViaVersion/ViaBackwards. A/B utilizaron protocolo 1.20.4; un tercer bot, M, utilizó 26.1 para el diálogo nativo de confirmación de «Hacer propio».

`timeout 360 ./gradlew build --no-daemon` terminó correctamente en 2 min 35 s: **1178 tests, cero fallos y cero omitidos**. El despliegue copió el jar de esta rama; su etiqueta de versión interna sigue siendo `1.0.1`, por lo que esa etiqueta no identifica el conjunto de cambios v1.1. No se cambió código de producción ni se hicieron commits.

Antes del primer arranque: **6342 MB disponibles y 62,25 °C**. Cada rearranque comprobó `MemAvailable >= 2500 MB` y temperatura `<75 °C`. Un supervisor muestreó la temperatura cada cinco segundos y estaba preparado para apagar a `>80 °C`, esperar a `<70 °C` y permitir continuar. El rango térmico y las comprobaciones finales se recogen en «Cierre».

### Preparación reproducible

Usar una terminal persistente para el ciclo de vida del servidor. El script de consola y `screen` deben compartir el contexto de procesos; llamadas aisladas del sandbox pueden no ver la sesión. Reservar un emplazamiento vacío antes de construir. Los nuevos ejemplos están en [t45/README.md](../reference/ejemplos/t45/README.md): **11 dungeons, cinco mobs y una plantilla compartida**, más la receta de bloques. Todas las variantes T45 comparten una arena: ejecutar **una partida a la vez**.

```bash
export CD_TARGET=agents
free -m
cat /sys/class/thermal/thermal_zone0/temp
timeout 360 ./gradlew build --no-daemon
timeout 190 scripts/test-server.sh deploy
# Copiar los ejemplos a plugins/CustomDungeons/{dungeons,mobs,spawners}
# exclusivamente en Servidor-agentes, sin sobrescribir IDs ajenos.
timeout 190 scripts/test-server.sh start
timeout 20 scripts/test-server.sh cmd 'customdungeon reload'
```

Ejecutar las líneas de [build.mcfunction](../reference/ejemplos/t45/build.mcfunction) mediante `cmd`, omitiendo comentarios y líneas vacías; esperar dos segundos tras cada `forceload add`. Lobby `(1203.5,65,5.5)`, placas de inicio X=1202/1204, entrada X=1209, sala 1 X=1210–1230, sala 2 X=1231–1250, placa de salida X=1249. La salida `(1280.5,65,5.5)` queda fuera del área X=1200–1251. El asistente usa el segundo laboratorio X=1300–1345.

Bots offline **T45A** y **T45B**; dar OP temporal para edición y permitir la recogida de objetos en este servidor con Essentials. Dar Resistance V/saturación durante los escenarios técnicos; para ambiente dar además Speed III. Estos efectos facilitan el ensayo y no prueban el equilibrio de combate. Deshacer OP y efectos al terminar. Para cada escenario:

```text
customdungeon join T45A <id>
customdungeon join T45B <id>
execute in minecraft:cd_dungeons run minecraft:tp T45A <x> 65 5.5
execute in minecraft:cd_dungeons run minecraft:tp T45B <x> 65 5.5
customdungeon stop <id>
```

Confirmar estado `FREE` antes de la siguiente variante. Para aislar progresión se eliminaron mobs de las salas T45 con `minecraft:kill` limitado a husks de sus cuboides; en la Guarida se usaron ataques reales de ambos bots.

#### Control de bots y evidencia

El controlador ejecutado está en `servidor/bots/t45/control.cjs`, iniciado con `timeout 7200 node ...`, y escucha **solo en 127.0.0.1:18846**. No utilizar el antiguo `bots/control.cjs` de T18, que apunta a 25565. El supervisor de consola de esta ejecución escuchó en 127.0.0.1:18845. Ninguno debe permanecer escuchando después del ensayo.

API JSON del controlador: `connect`, `snapshot`, `chat`, `held`, `equip`, `activate`, `hit`, `click`, `sneak`, `blocks`, `attack`, `quit`, `shutdown`. Ejemplos:

```python
import json, urllib.request
def bot(action, name='T45A', **kw):
    request = urllib.request.Request('http://127.0.0.1:18846',
        data=json.dumps(dict(action=action, name=name, **kw)).encode(),
        headers={'Content-Type': 'application/json'})
    return json.loads(urllib.request.urlopen(request, timeout=30).read())
bot('connect'); bot('connect', 'T45B')
bot('chat', text='/customdungeon build t45-auto')
bot('click', slot=41, button=0, mode=0)
bot('snapshot')
bot('activate', pos=[1230,65,5])
bot('sneak', value=True)
# Al terminar: bot('shutdown')
```

Para regenerar ese controlador basta mapear las acciones a las APIs de Mineflayer: `clickWindow(slot,button,mode)`, `activateBlock(blockAt(new Vec3(...pos)))`, `setQuickBarSlot(slot)`, `equip(item,'hand')`, `setControlState('sneak',value)` y `attack(entity)`. `hit` envía `block_dig` status 0 seguido de 1 al mismo bloque. Esperar 650–800 ms tras clics/interacciones. Crear cada bot con:

```javascript
const bot = require('mineflayer').createBot({host:'127.0.0.1', port:25566,
  username:'T45A', auth:'offline', version:'1.20.4', physicsEnabled:false});
bot.physicsEnabled = false;
const write = bot._client.write.bind(bot._client);
bot._client.write = (name, data) => {
  if (['look','position','position_look'].includes(name) &&
      Object.values(data).some(v => typeof v === 'number' && !Number.isFinite(v))) return;
  if (['look','position','position_look','flying'].includes(name) && 'onGround' in data)
    data.onGround = true;
  write(name, data);
};
```

Moverlos por consola sobre un suelo sólido. El `onGround=true` es necesario con física desactivada para reproducir **placas**; la primera tentativa sin este ajuste no se contó. Registrar `messagestr`, `windowOpen`, `death`, `end`, y paquetes `boss_bar`, `scoreboard_*`, `set_title_*`, `sound_effect`, `stop_sound`, `entity_effect`, `remove_entity_effect` y `game_state_change`. Los snapshots incluyen posición, modo, salud, ventana, inventario/NBT y entidades. **`inventory.items()` no incluye toda la armadura/offhand:** para afirmar inventario exacto comparar `bot.inventory.slots` completo (46 slots), además del slot seleccionado. En Paper 26.3 el NBT reparte los objetos entre `Inventory` y `equipment`; consultar ambos, no únicamente `Inventory`.

Artefactos locales, deliberadamente fuera del producto y sin versionar, en `.agent/`: `t45-evidence.jsonl` (peticiones, respuestas y aserciones), `t45-bots.jsonl`, `modern-bot.jsonl`, `temperature.jsonl`, `supervisor.log`; scripts/logs `wizard`, `wizard-cleanup`, `build`, `build-exact`, `stage0-valid`, `stage1`, `stage2`, `stage3`, `shutdown-valid`, `modern-own`, `preset-valid`, `chest`, `emptying`, `warden`, `canonical-warden`, `safety` y `profile`. `spark-profile.pb`/`spark-metrics.json` conservan la captura y su análisis. Los logs de Paper conservan los arranques anteriores al crash/reinicio en `Servidor-agentes/logs/`; no limitar una auditoría a `latest.log`.

### Resultado por punto

«OK» significa que se observó el comportamiento indicado en este ensayo real, no que toda combinación de configuraciones de la tarea esté certificada. La evidencia de sonido/título/GUI es de paquetes y contenido de ventana, no de percepción humana.

| Punto | Resultado | Evidencia observada |
|---|---|---|
| 1. Asistente T29 | **OK** | `t45-wizard` se creó por los siete pasos con clics reales: área, puntos, biblioteca, sala/spawner, reglas, recompensa y revisión. Revisión sin errores → Activar persistió `enabled: true`. A 23:54:28 se retiró la BossBar y volvió `t45prior`; los overlays del asistente cesaron al salir. Ver también comprobación de limpieza abajo. |
| 2. Construcción T40 | **OK** | Nueve herramientas; área/sala/puerta, Shift+puerta de entrada, ambas placas, spawner y puntos usados con interacciones reales y Deshacer. Placas volvieron a AIR. Salida normal y SIGKILL/reconexión restauraron inventario y slot 4; comparación completa, incluida armadura/offhand, y segunda reconexión sin duplicados documentadas abajo. |
| 3. Plantillas T36 | **OK** | Ambas salas enlazadas: cambiar count 1→2 produjo dos mobs en cada sala. «Hacer propio» real + guardar copió waves y retiró `preset-id` solo de sala 1. Edición posterior count 3: sala propia invocó uno y enlazada tres (`preset-valid.log`, seis aserciones correctas). |
| 4. Inicio/final T38 | **OK** | Todas las placas → cuenta atrás; retirar B la canceló. Entrada cerrada antes y AIR después; posición de A permaneció X=1202,5. Cero mobs antes de entrar, uno al entrar en sala 1 y jefe al entrar en sala 2. IMMEDIATE→EXIT, DELAYED 10 s→PREVIOUS individual, NONE→placas, timeout 5 s→EXIT. NONE sin pisar salida expulsó a los 300 s de seguridad (00:27:45). Reingreso durante vaciado rechazado explícitamente a 00:08:41. |
| 5. Llaves T28/T39 | **OK** | Portador `*` dio llave con PDC partida/sala, recogida y usada para abrir. Sala EXTERNAL_KEY quedó cerrada al limpiar. Bloque de comando real con `customdungeon key give @p t45-external` recibió pulso de redstone; A obtuvo la llave y abrió (23:53:38–43). |
| 6. Desconexión T44 | **OK** | Quit en RUNNING → muerte al volver, siete diamantes soltados con `Paper.Origin` en posición guardada, inventario vacío y respawn fuera. Kick administrativo mantuvo nueve esmeraldas y no causó muerte. Stop real con ambos en RUNNING → B conservó tres diamantes y 20 HP; sin muerte de reconexión. |
| 7. Scoreboard T42 | **OK** | Ventanas de lobby/partida/jefe/final e intro; objetivos «Entra», «Recoge/Usa la llave», «Resuelve el puzzle», «Sal por la placa» y gracia decreciente. Al abandonar se restauró el objetivo previo `t45prior`/`T45_PREV`, score 7. |
| 8. Ambiente T43 | **OK** | Títulos de entrada r1/r2 y paquetes de sonido/música. Night Vision apareció dentro y desapareció al salir; Speed III preexistente mantuvo amplificador 2. `stop_sound` para cat/13 al limpiar/salir. Menú Ambiente abrió y mostró sus valores. |
| 9. Cinemática T41 | **OK** | Ambos SPECTATOR durante intro; fin natural y agacharse devolvieron SURVIVAL y lobby original `(1203.5,65,5.5)`. Desconexión de B conservó tres diamantes/20 HP y salió sin penalización. Cofre cercano accesible en control espectador, inaccesible durante intro. |
| 10. Rangos T46 | **OK** | YAML original escala 7,06 / velocidad 4,7 se mantuvo intacto. Aviso de recorte de speed a 1; plantilla disponible, mob invocado y atributos reales scale=7,06, speed=1 (23:53:54–55). Escala 7,06 ya está dentro del rango válido. |
| 11. Guarida del Warden | **OK** | Seis YAML exactos del repositorio. START 2 00:28:19 → COMPLETE 2/FREE 00:32:19; Warden muerto por B a 00:32:02. Run 38 `COMPLETED`: A/B 15/6 kills, cero muertes, survived/rewarded=1 y cinco diamantes cada uno. Ataques reales, sin `test`, `skip` ni kill por consola. |
| 12. Rendimiento | **OK para esta carga** | Captura 60 s / 1200 ticks, intro + ambiente + scoreboard + jefe y 49 mobs. MSPT medio 5,486 ms / p95 11,818 ms; atribución directa de CustomDungeons **0,573 ms/tick**, objetivo ≤2 ms. |

### Recetas y comprobaciones importantes

#### Asistente, construcción y menús

`/customdungeon create t45-wizard` partió de un ID inexistente, sin precargar su YAML. En Área: slot 29 entrega varita; clic izquierdo en `(1300,64,0)` y derecho en `(1330,85,10)`; reabrir y slot 31 aplica selección, 53 avanza. En Puntos: situarse en `(1303.5,65,5.5)` y slot 29 fija lobby; `(1340.5,65,5.5)` y slot 33 fija salida. Biblioteca permitió avanzar sin añadir otra plantilla.

En Salas, slot 30 crea una; varita slot 19, selección `(1310,64,1)`–`(1325,80,9)`, volver y aplicar región slot 28. Situarse en `(1312.5,65,5.5)` y slot 21 fija checkpoint. Slot 39 añade spawner desde la biblioteca validada; regresar con 45. Avanzar Reglas y Recompensa con 53; Revisión → Activar slot 33. No se sustituyó ese flujo por un YAML. Buscar los botones por nombre en la ventana si cambia su orden.

Se repitió la salida explícita del asistente con captura de partículas al 100%: **1536 paquetes** del cuboide durante tres segundos activo y **cero** en tres segundos después de salir (ventana de asentamiento de 0,5 s). Se retiró antes la varita para excluir su previsualización normal. A **00:21:59,836** se eliminó la BossBar `0713fc01-dbef-4d5b-b051-e0670b3deaad`; un milisegundo después volvió `t45prior`. Escape solamente cierra la ventana y permite seguir usando herramientas; la salida explícita fue el botón **49**, según el flujo del asistente. Evidencia: `wizard-cleanup.log` y `wizard-particle-counts` en JSONL.

En `/customdungeon build t45-auto`, hotbar 0/1/2 selecciona área/sala/puerta; Shift con 2 selecciona entrada. Hotbar 4 coloca inicio, Shift+4 salida; 5 alterna checkpoint/lobby/exit con Shift; 3 coloca spawner tras seleccionar plantilla; 7 deshace; 8 abre menú. Cada modificación se deshizo. Desde raíz slot 41 se abrió **Inicio y final**; raíz → Salas 28 → Sala 1 → Ambiente 43 cargó los valores del YAML. Se validó carga/estado de esos menús, sin reclamar edición exhaustiva de cada diálogo de sus campos.

Para inventario, preparar siete diamantes, nueve esmeraldas, espada nombrada `T45 exact` con Damage=7, armadura con daños 3/9/2/4 y escudo en offhand con Damage=5. Comparar **los 46 slots de `bot.inventory.slots`, incluido el NBT completo**, y el slot seleccionado. Consultar también `minecraft:data get entity T45A Inventory` y `minecraft:data get entity T45A equipment`: Paper 26.3 guarda el equipo en un compuesto separado. Entrar/salir; repetir con `save-all flush` antes y dentro de construcción. Identificar exactamente el único proceso cuyo ejecutable sea Java y cuyo cwd sea `Servidor-agentes`; enviar SIGKILL a ese PID numérico. Reiniciar con las comprobaciones térmicas/RAM, reconectar y volver a comparar. Desconectar/reconectar una segunda vez para detectar duplicación. No usar un `pkill -f` amplio.

La primera medición de construcción comparó inventario de almacenamiento/hotbar y slot seleccionado; se amplió a los 46 slots completos para incluir los cuatro de armadura y offhand. Los tres arrays (salida normal, reconexión tras SIGKILL y segunda reconexión) coincidieron exactamente con el inicial, incluidos daños/NBT. Las aserciones `FULL 46 slots armor+offhand` están en `t45-evidence.jsonl`; `build-full-slots.json` conserva las cuatro capturas y `build-exact.log` la secuencia. No basta la etiqueta inicial «including armor/offhand» del SNBT Inventory del arnés: esa etiqueta se corrigió al comprobar la separación de NBT de Paper. Una aserción SNBT tras el crash capturó por error la respuesta de una consulta paralela de `equipment`, en lugar de `Inventory`; se excluye esa aserción del arnés. La evidencia de aceptación son los cuatro arrays de 46 slots y sus tres comparaciones correctas, que no dependen de leer «la última respuesta» del log.

#### Plantillas y diálogo nativo

En `t45-preset`, comenzar con ambos spawners referenciando `t45-shared`. Cambiar `spawners/t45-shared.yml` count 1→2, recargar, iniciar y entrar/limpiar cada sala: ambas cuentan dos mobs. Para desvincular, usar construcción → Salas → Sala 1 → Spawners → primer spawner → **Hacer propio** (43), confirmar y **Guardar** (49). Salir de construcción. Comprobar que sala 1 tiene `waves` propias y no `preset-id`, y que sala 2 conserva su referencia. Cambiar plantilla a count 3 y recargar: resultados 1/3. Restaurar plantilla y dungeon al terminar.

El cliente 1.20.4 puede usar ventanas, pero no representa el nuevo diálogo nativo. El bot M con protocolo 26.1 recibió `show_dialog`. Para confirmar realmente en Paper 26.3 se extrajo la UUID dinámica de `additions.id` de la acción «Sí» y se envió `custom_click_action` con ID `paper:dialog_click_callback`. El serializer de minecraft-data 26.1 usa otro formato; no reutilizarlo sin adaptar. El paquete válido de esta sesión fue:

```text
0x44 | VarInt(longitud UTF8 id) | UTF8('paper:dialog_click_callback')
     | VarInt(longitud TAG) | TAG compuesto anónimo
TAG: 0x0a + 0x0b + unsigned-short(2) + 'id' + int(4)
     + cuatro int de la UUID recién recibida + 0x00
```

No lleva un booleano de presencia delante del TAG. El bot de GUI moderno suprimió todos sus paquetes de movimiento y se situó por consola, evitando `Invalid move packet` en este cliente. Paper confirmó «Dungeon guardada correctamente» a **00:06:41**. No se simuló «Hacer propio» editando YAML. Este detalle de protocolo es específico de los clientes/versiones ensayados.

#### Placas, final y llaves

En `t45-plates`, ambos en lobby; A/B sobre X=1202,5/1204,5. Mover B fuera antes de tres segundos: cuenta atrás cancelada y puerta X=1209 sigue cerrada. Repetir apoyados en las placas: START, puerta AIR y ningún TP. Entrar en sala 1 invoca su oleada; limpiar y entrar en sala 2 invoca su jefe. NONE mantiene jugadores hasta pisar salida X=1249. No confundir el comando administrativo `stop` con completar. Se repitió NONE sin pisar ninguna placa: continuó dentro hasta 290,1 s del muestreo y en 300,1 s estaba en EXIT; `FREE` a 00:27:45. Evidencia: `safety.log`/`safety-sample`.

En `t45-immediate`, completar lleva a EXIT. En `t45-delay`, antes de unirse situar A/B en posiciones previas distintas de overworld (X=1,5/2,5); completar, comprobar que permanecen durante la gracia y que después vuelven cada uno a su posición. En `t45-timeout`, esperar cinco segundos de partida: expulsa a EXIT aunque finishMode=NONE. Para «se está vaciando», completar NONE, sacar solo A por placa, intentar que A se una mientras B sigue dentro y comprobar el rechazo; sacar B y confirmar FREE.

`t45-key` usa portador `*`; recoger el tripwire_hook del último mob y comprobar PDC `customdungeons:key_item=<session>:r1`. Equiparlo y clicar la puerta `(1230,65,5)` desde X=1227,5. En `t45-external`, primero comprobar que matar todos los mobs no abre la puerta ni genera llave natural. Habilitar temporalmente `enable-command-block=true` en **agentes**, apagado; conservar/restaurar el archivo original. Construir un bloque de comando real y darle pulso:

```text
execute in minecraft:cd_dungeons run setblock 1227 65 7 command_block{Command:"customdungeon key give @p t45-external"}
execute in minecraft:cd_dungeons run setblock 1227 66 7 redstone_block
```

A debe estar cerca y B lejos para que `@p` seleccione A desde el bloque. Verificar llave/PDC y apertura con interacción real. Retirar ambos bloques. Una ejecución de `key give` directamente desde consola no demuestra el contexto de selector de un bloque de comando.

#### Desconexión, panel, ambiente y cinemática

En `t45-auto` con ambos en RUNNING, dar siete diamantes a A y situarlo en `(1214.5,65,5.5)`. Quit/reconectar: evento `death`, inventario sin diamantes, entidad item count 7, origen `(1214.5,66.32,5.5)`; esa altura es el lanzamiento del drop. El objeto puede desplazarse después: comparar `Paper.Origin`, no su posición varios segundos más tarde. Respawn `(8,-63,8)` en cd_dungeons, fuera de todas las áreas.

Repetir con `kick T45A T45_admin_kick`: nueve esmeraldas conservadas, sin muerte. Para shutdown usar `scripts/test-server.sh stop` con **los dos dentro de RUNNING**, no durante lobby o un retorno pendiente. El ensayo válido registró START 2 a 00:01:38, stop a 00:01:40 y reconnect después del arranque de 00:02:48; B tenía tres diamantes/20 HP antes y después. La expectativa de once esmeraldas de A en una aserción del arnés era inválida: A las había perdido antes de esa partida por una muerte vanilla en el exterior. Esa aserción se excluye; no prueba un fallo de T44.

Antes de unirse, crear objetivo `t45prior`, título `T45_PREV`, score 7 y sidebar. Capturar paquetes de objetivos/display/scores durante lobby, sala, jefe y final; confirmar restauración después. Objetivos de llaves/puzzle se observaron en sus variantes. La configuración por defecto muestra objetivos contextuales cuando no hay mobs; no exigir que sustituya el objetivo de combate mientras quedan enemigos.

Dar Speed III antes de `t45-auto`; dentro de sala 1 aparecen títulos/sonido y Night Vision. Volver al lobby X=1206,5: Night Vision retirada, Speed con amplificador 2 permanece. Capturar `stop_sound` al limpiar/terminar. Para intro `t45-intro`, iniciar, comprobar SPECTATOR, agacharse y comparar modo/posición de origen. Repetir sin saltar para fin natural. Quit de B durante intro: reconecta sin muerte ni pérdida.

Para demostrar bloqueo de cofres, un control positivo en SPECTATOR fuera de intro abrió un cofre cercano. Dentro de una intro temporal de 20 s, colocar otro cofre **junto a la cámara** (no lejos en el lobby), intentar abrirlo y comprobar `currentWindow == null`. El control positivo pasó y el intento durante intro se rechazó. Retirar el cofre temporal y restaurar los diez segundos de duración.

#### Rangos y regresión

`t45-range.yml` contiene `scale: 7.06` y `speed: 4.7`. Recargar, revisar aviso de speed y ejecutar `t45-range-test`; consultar atributos del husk generado: scale=7,06 y movement_speed=1. El archivo original conserva ambos valores. La plantilla histórica `warden.yml` instalada también contiene herramientas de administrador en HAND/OFF_HAND: se excluye por `validation.equipment-tool`, un problema independiente del recorte numérico. El ejemplo T45 aísla esa causa y demuestra que números fuera de rango no desactivan una plantilla válida.

La Guarida usa los YAML de [guarida-warden](../reference/ejemplos/guarida-warden). **Copiar exactamente la dungeon y sus cinco mobs del repositorio antes de este escenario**, respaldando las definiciones instaladas para restaurarlas al cerrar. La primera ejecución usó una Guarida instalada con oleadas anteriores distintas (run 36, 25 kills); se repitió con las seis copias byte a byte del ejemplo. Unir los dos bots, esperar el inicio automático de 15 s. Atacar con espadas de netherita, Strength III y Resistance V; aproximarlos por consola a los mobs y emitir ataques reales. Sala 1: X=801–817; sala 2: 818–834; sala 3: 835–861; Z=508,5. Esperar entre oleadas y no dar una sala por limpia en su pausa de cinco segundos. La llave puede recogerla **cualquiera** de los dos: buscarla en ambos inventarios antes de equipar y abrir puertas X=817/834. La primera selección fija de A falló porque B ya había recogido la llave; se continuó con B sin conceder otra llave. La última sala legacy configurada como KEY avisa y se trata como Automático según T37.

### Rendimiento

[Perfil de spark ouxbuuYfkj](https://spark.lucko.me/ouxbuuYfkj), **00:09:29,926–00:10:30,181**, 60,255 s de reloj / **1200 ticks**, muestreo de ejecución cada **4 ms**. Se canceló el perfil automático antes de `spark profiler start --timeout 60 --interval 4`. El recorrido incluyó countdown, introducción de diez segundos, sala 1 y entrada a sala 2; los seis snapshots de los segundos 25,75–50,79 registraron **50 mobs**: jefe y 49 husks, con ceguera/oscuridad, ambiente y scoreboard activos. El coste es de la ventana completa, no solo de su tramo estable de jefe.

| Métrica | Resultado |
|---|---:|
| MSPT total medio, último minuto al cerrar | **5,485507 ms** |
| MSPT total p95, último minuto al cerrar | **11,818467 ms** |
| MSPT mediana / máximo del minuto | 4,397362 / 87,457369 ms |
| TPS, últimos 5 s / 10 s / 1 min | 20 / 20 / 20 |
| Frames directos de CustomDungeons en Server thread | **688 ms muestreados** |
| Coste directo del plugin por tick | **688 / 1200 = 0,573333 ms/tick** |

Atribución por [schemas oficiales de spark](https://github.com/lucko/spark/tree/master/spark-common/src/main/proto/spark): decodificar `SamplerData`, recorrer `Server thread.children_refs` y sumar solo el primer frame cuyo `class_name` empiece por `dev.dasan.customdungeons.` en cada rama, sin volver a sumar sus descendientes. `SessionTicker.run` aportó 604 ms; resto de llamadas, 84 ms. Usar ticks del perfil como denominador; no multiplicar un porcentaje del hilo, que incluye espera, por MSPT. Descarga y formatos: [datos raw de spark](https://spark.lucko.me/docs/misc/Raw-spark-data).

El objetivo ≤2 ms se cumple **como estimación de muestreo para esta carga y una partida**. La IA vanilla ejecutada fuera de los frames del plugin está en el MSPT total. No es una garantía de todas las habilidades o varias partidas simultáneas. El informe auxiliar [BzPyYbas41](https://spark.lucko.me/BzPyYbas41) no sustituye la captura; su conexión de actualización produjo después un aviso WebSocket de spark, fuera de CustomDungeons.

### Bugs, observaciones y límites

**No hay un bug nuevo confirmado de producción en los escenarios verificados.** No se aplicó ningún arreglo de producción. Los B01–B04 de la sección histórica T18 no se vuelven a declarar como vigentes ni resueltos sin su reproducción específica.

| Observación | Severidad / clasificación | Reproducción y archivo relacionado |
|---|---|---|
| Respawn vanilla del mundo vacío situado sin suelo | Media, configuración del laboratorio; no se ha demostrado violación de T44 | Quit en RUNNING y reconnect sin cama válida devuelve fuera del área, a spawn `(8,-63,8)` de cd_dungeons. Caída/muerte exterior posterior es vanilla. Configurar spawn exterior seguro antes de probar con humanos. Selección de destino: `session/DisconnectService.java`, `outsideSpawn`; configuración spawn del mundo. |
| `warden.yml` histórico no disponible | Configuración inválida preexistente | Recargar el YAML instalado: herramientas reservadas en `equipment.HAND/OFF_HAND` generan `validation.equipment-tool`. No confundirlo con speed 4,7; el ejemplo `t45-range` sí carga. YAML instalado y `config/Validator.java`. |
| Confirmación de diálogo incompatible con serializer 26.1 | Limitación del cliente de pruebas | Enviar el formato optionalNBT de minecraft-data 26.1 a Paper 26.3 provoca desconexión por decoder; enviar el TAG con longitud como arriba permitió confirmar y guardar. Archivo del arnés `bots/t45/modern.cjs`; sin indicio de defecto del plugin. |

No verificado: renderizado/audio por un cliente humano; edición exhaustiva de todos los campos de diálogos nativos; scoreboard coexistiendo con plugins que lo reasignan periódicamente; variantes de reconexión con cama/ancla, keepInventory y cancelación de muerte por otro plugin; aislamiento de llaves antiguas/ajenas bajo todas las combinaciones; restauración de cinemática al deshabilitar bruscamente el plugin y vuelos/yaw/pitch de todos los modos. Los ensayos de esta sección no certifican todas las combinaciones del TODO v1.1 ni reejecutan las 43 habilidades de T18. Los subescenarios solicitados se distinguen de estos límites en la tabla.

### Cierre

El servidor terminó con apagado limpio a **00:33:28**; `scripts/test-server.sh stop` devolvió **«Servidor detenido.»**. Controlador y supervisor terminaron con código 0. Puertos **25566, 18845, 18846 y 18847 cerrados**; sin procesos Paper/controladores T45. Se retiraron OP de A/B/M, efectos e inventarios de los dos bots, el objetivo `t45prior` y solamente las cargas forzadas de los dos laboratorios T45. Los bloques de comando y el cofre temporal ya estaban retirados. Las superficies de los laboratorios y el historial de runs/logs permanecen para reproducción.

`server.properties` quedó **idéntico byte a byte** al original (incluido `enable-command-block`). Las seis definiciones previas de la Guarida quedaron idénticas a sus respaldos. Se retiraron solo **21 archivos propios de la prueba**: 17 definiciones de los ejemplos, la dungeon creada por el asistente, su borrador de limpieza y dos borradores de construcción; se conservaron copias locales en `.agent/server-definitions-after/`. No se borró ningún jar ni otro plugin: mismos **16 jars** y mismos hashes de los **15 ajenos a CustomDungeons**. El único jar distinto es el desplegado desde esta rama.

Estado persistente tras apagar: `active_sessions=0`, `disconnects=0`, `session_returns=0`, **cero bloques con `restored=0`**. `pending_exits` conserva dos registros de otros UUID, ninguno de A/B/M; no se editaron esos registros. Se conservaron tres respaldos de construcción con estado **RESTORED** y checksum válido, sin inventario activo pendiente, para mantener el journal y la evidencia. La restauración sin duplicados se probó antes de retirar los objetos de prueba.

Temperatura registrada **56,20–73,80 °C**, sin superar 80 °C ni necesitar enfriamiento. Después de apagar: **6589 MB disponibles / 59,50 °C**. Los archivos versionables introducidos son esta sección y `docs/reference/ejemplos/t45/`; `git diff --check` no presenta errores. Ningún cambio de producción, ningún commit.

## T47 — contenido de ejemplo

7-oct-2026, Paper 26.3 build 157, rama `docs/t47-examples`, servidor **agentes / 25566**. Dos bots principales por dungeon, Mineflayer 4.39.0 / 1.20.4 mediante ViaBackwards; el Templo necesitó además un auxiliar solo para ocupar la tercera placa. Todos los recorridos completos terminaron en `COMPLETED`, con ambos principales supervivientes y premiados. Ataques reales, resistencia elevada y TP de posicionamiento: aceptación técnica; equilibrio humano y render/escucha pendientes.

| Dungeon / paquete | Prueba específica y resultado | Run | MSPT Paper medio / p95 | Plugin estimado |
|---|---|---:|---:|---:|
| [Templo de las Placas](../reference/ejemplos/templo-placas/README.md) | 3 placas, cancelación al bajar, entrada sin TP; 3 salas; placa de salida antes/después y DELAYED 45 s a EXIT: **OK**. | 44 | 3,095 / 7,046 ms | 0,250 ms/tick |
| [Laberinto del Enigma](../reference/ejemplos/laberinto-enigma/README.md) | 2 presets compartidos en las 3 salas, ambiente/Velocidad I; botones reales fuera de orden y 1→2→3, comando del bloque→llave→puerta: **OK** tras permitir interacción en AntiBuild. | 45 | 2,410 / 4,957 ms | 0,133 ms/tick |
| [Coloso Abismal](../reference/ejemplos/coloso-abismal/README.md) | Cámara 12 s, Scale 8, fases 66/33 %, ComboRunner ejecutado, scoreboard; NONE y retorno PREVIOUS a 300 s: **OK**. DIE_AND_DROP: muerte/salida/inventario **OK**, drop físico **no recuperable en este entorno** (T47-I01). | 46 | 3,120 / 6,454 ms | 0,143 ms/tick |

Spark: [Templo](https://spark.lucko.me/FJDiXmlApg) 1200 ticks / 300 ms del plugin; [Laberinto](https://spark.lucko.me/EhgoEwyUOm) 1200 / 160; [Coloso](https://spark.lucko.me/mvnGk3c5CW) 2400 / 344, incluyendo ambas fases. Async, 4 ms de muestreo. Atribución por primer frame CustomDungeons de cada rama, sin sumar sus hijos; cumple ≤2 ms para estas ventanas. MSPT medio/p95 de Paper corresponden al último minuto al cerrar cada perfil, incluyen otros plugins y no son el coste del plugin. Máximo del Coloso 108,470 ms; una verificación Gradle coincidió con el perfil. Metodología y límites en los README.

Validator: **cero errores y avisos** de los nuevos IDs, también con presets resueltos. `T47ExampleValidationTest`: **2 tests / 0 fallos** (definiciones y suelo/espacio libre). Antes de construir: solo aire en los volúmenes 39382 / 47530 / 237558 bloques; zonas X 1500–1596, 1700–1796 y 1900–2036, separadas de demo, Guarida y T45. Se corrigió el suelo de los vestíbulos en el contenido y se revalidaron bloques, rutas y cámara/lobby sin resistencia; salas y YAML de combate iguales a los probados. Sin cambios Java de producción.

**T47-I01, alta, integración, origen no aislado:** tras reconectar A en sala 1 del Coloso con un diamante y `keep-inventory=false`, murió y perdió inventario, pero no quedaron entidades item aunque B seguía jugando. Repetido a 07:08:32 UTC, consulta de consola negativa cinco segundos después. El control vanilla fuera de sesión (`give` + `kill`, cliente sin respawn automático) también dio resultado negativo a 07:10:45 UTC; reglas `keep_inventory=false`, `entity_drops=true`, `mob_drops=true`. **No atribuido a CustomDungeons** y no corregido. Archivo de referencia de la función: `src/main/java/dev/dasan/customdungeons/session/DisconnectService.java`, `apply/death/drop`, especialmente 201–242; reproducción completa en el README del Coloso. No se eliminaron ni desactivaron plugins para aislar el origen. Escenarios adicionales detenidos intencionalmente tras comprobar desconexión/lobby; sus runs fallidos no son fallos del combate completo.

Cierre **07:18:50 UTC**: servidor apagado limpiamente con `test-server.sh stop`, chunks e I/O guardados, sin bots/controlador/monitor térmico vivos, puertos 25566/18848 cerrados. Permisos temporales de interacción retirados y `enable-command-block=false` restaurado. Arranques con >2500 MB disponibles y <75 °C, máximo observado 74,35 °C. Se preservaron todas las definiciones/plugins ajenos y no se tocó el servidor del usuario. Evidencia local en `.agent/evidence.jsonl`, `bots.jsonl`, perfiles `.pb`/`*-metrics.json`, `temperature.jsonl`, logs de cada escenario y cierre; no se versiona.

## T49 — drops al morir

**7 de octubre de 2026, 07:30–07:47 UTC.** Investigación de T47-I01 exclusivamente en `/home/dasan/Desktop/Proyectos/plugins/servidor/Servidor-agentes`, Paper **26.3 build 157**, puerto **25566**, mediante `CD_TARGET=agents scripts/test-server.sh`. Bots Mineflayer 4.39.0, protocolo 1.20.4 mediante ViaBackwards, supervivencia y **sin respawn automático**. No se desplegó otro CustomDungeons ni se modificó Java. El servidor del usuario no se inspeccionó ni se modificó.

**Causa confirmada de la pérdida en DIE_AND_DROP: incompatibilidad con EssentialsX AntiBuild y sus permisos de drops.** Con `protect.disable.use: true`, `essentials.build=false` y sin permiso `essentials.build.drop.<material>`, AntiBuild cancela el drop que Paper produce para los ítems reescritos por la penalización. El cliente recibe `You are not permitted to drop LAPIS_LAZULI.`, el inventario del servidor queda vacío y no se genera una entidad item, aunque el compañero sigue vivo en la sala. Desactivar **solo** AntiBuild permite el drop; restaurarlo y conceder **solo** el permiso del material también lo permite, sin conceder construcción.

**La ausencia vanilla de T47 no queda explicada por ese mismo veto.** En T49, los controles vanilla con un bot nuevo y con el bot que acababa de sufrir la penalización sí generaron drops. Se reprodujo un falso negativo de consola al descargarse el chunk después de morir el único jugador cercano: el diamante volvió a aparecer **con el mismo UUID** al recargar el chunk, sin otra muerte ni otro `give`. Esto demuestra persistencia, no eliminación. No hay evidencia suficiente para asignar esa causa exacta al control histórico de T47, donde había otro bot; tampoco se demuestra un limpiador global. La pérdida en la penalización y una consulta vanilla negativa deben diagnosticarse por separado.

### Evidencia y comparación

| Condición | Evidencia de consola/cliente | Resultado |
|---|---|---|
| 16 plugins iniciales, fuera de sesión, `give diamond` + `minecraft:kill`, en `(2033.5,65,614.5)` | 07:34:38 UTC: muerte; 07:34:40: entidad `minecraft:diamond`, `Age: 25`, inventario `[]`. | El drop vanilla se genera. |
| Mismo diamante, único bot muerto, chunk sin carga forzada | 07:34:59: consulta negativa. Tras regresar y después cargar temporalmente el chunk, reaparece UUID `[I; 671978471, -339063840, -1979079141, -1171992717]`; el mismo ítem permanece sobre el suelo. | Falso negativo por descarga del chunk. |
| Fuera de sesión, chunk cargado temporalmente, daño de consola | 07:36:23: `minecraft:damage T49Drops 100 minecraft:fall`, salud `0.0f`, inventario `[]`, entidad `minecraft:iron_ingot`; seguía presente a 07:36:42. | Daño mortal con drop. |
| Fuera de sesión, caída física de Y=100 a Y=65, sin efectos ni invulnerabilidad | 07:36:59: `T49Drops fell from a high place`; 07:37:08: entidad `minecraft:gold_ingot`, `Age: 196`, salud `0.0f`, inventario `[]`. | Muerte ambiental real con drop. |
| DIE_AND_DROP, 16 plugins, sin permiso de drop | 07:39:53: aviso AntiBuild para lapislázuli; compañero con salud `20.0f`, sin entidad item en la sala, inventario del muerto `[]` a 07:40:05. | Pérdida reproducida. |
| Mismo escenario, **solo jar AntiBuild retirado**, 15 plugins | 07:43:58–07:44:04: lapislázuli en el suelo con `customdungeons:disconnect_drop` y `customdungeons:session`; compañero vivo, inventario del muerto `[]`. | El veto desaparece. |
| AntiBuild restaurado, 16 plugins, permiso temporal `essentials.build.drop.lapis_lazuli=true`, contexto `world=cd_dungeons` | `essentials.build` sigue en `false`; 07:46:41–07:47:04: lapislázuli con ambas marcas PDC, inventario `[]`, compañero vivo. | Mitigación mínima validada. |

La penalización se comparó en la primera sala del Coloso: A en `(1913.5,65,614.5)`, B en `(1924.5,65,624.5)`, después del lobby y la cinemática; B recibió resistencia temporal para mantener la partida activa durante la consulta. Se consultó antes de detener la partida, evitando confundir su limpieza final con el veto de AntiBuild. Se usó inventario NBT del servidor: el inventario que muestra un cliente en la pantalla de muerte puede estar desactualizado.

### Ajustes y plugins revisados

| Elemento | Estado observado e implicación |
|---|---|
| Gamerules de `minecraft:cd_dungeons` y `minecraft:overworld` | `keep_inventory=false`, `entity_drops=true`, `mob_drops=true`; en dungeons, `immediate_respawn=false`. Son los nombres aceptados por esta 26.3; `player_drops` fue rechazado. No se cambió ninguna regla. |
| Spigot / Paper por mundo | `world-settings.default.item-despawn-rate: 6000`, `merge-radius.item: 0.5`; `entities.spawning.alt-item-despawn-rate.enabled: false`. Los `paper-world.yml` de overworld/dungeons solo contienen `_version: 31`, sin overrides. No explican el veto inmediato. Referencias: [Spigot](https://docs.papermc.io/paper/reference/spigot-configuration/), [Paper por mundo](https://docs.papermc.io/paper/reference/world-configuration/). |
| Essentials / AntiBuild / Protect | `protect.disable.build: true`, `protect.disable.use: true`, `warn-on-build-disallow: true`. `essentials.keepinv` era `false` en el bot vanilla; políticas de maldiciones en `keep` e ítems de prueba sin encantamientos. `drop-items-if-full: false` controla entregas con inventario lleno, no este caso. El veto aislado es el de AntiBuild. |
| CustomCrafting, WolfyUtilities, SocialBlueprint | No se encontró una opción de limpieza general de drops en sus configs. Inspección de jars: CustomCrafting/WolfyUtilities no referencian `PlayerDeathEvent`/`ItemSpawnEvent`; SocialBlueprint tiene un listener de muerte de duelo y otro que limpia drops únicamente de sus entidades ambientales gestionadas. Permanecieron activos en las comparaciones que sí produjeron ítems. |
| WorldGuard / Multiverse | No se encontró una regla de borrado de ítems en las regiones/configs revisadas. Permanecieron activos. Se usó `minecraft:tp`: el alias `tp` pertenece a Essentials y en una prueba inicial conservó overworld pese al contexto de `execute in`; se verificaron `Dimension` y `Pos` antes de las reproducciones válidas. |
| DamageControl, Towny, CoreProtect, DiscordTowny, FraMCit | **Ya estaban desactivados al iniciar**, con sus cinco jars en `plugins-disabled/`. Sus carpetas no prueban que estén activos. DamageControl no tiene una opción de limpieza de inventarios en la config revisada; las cuatro opciones Towny `keep_inventory_on_death_in_*` estaban en `false`. No se activaron y no pueden explicar esta reproducción. No se extrapola su descarte a otro servidor donde estén activos. |

### Punto de compatibilidad en CustomDungeons

Los listeners están registrados globalmente, pero la muerte ordinaria fuera de sesión no aplica las reglas de dungeon: [SessionListener.java:64–79](../../src/main/java/dev/dasan/customdungeons/session/SessionListener.java#L64-L79) exige pertenencia a una sesión, y [DisconnectService.java:199–201](../../src/main/java/dev/dasan/customdungeons/session/DisconnectService.java#L199-L201) retorna sin modificar el evento cuando no hay penalización en curso. BuildModeListener solo preserva inventario de jugadores protegidos por construcción; ToolListener solo filtra herramientas marcadas. Ninguno convierte un diamante vanilla del bot nuevo en un drop de penalización.

**Bug de compatibilidad reproducido, sin corregir:** [DisconnectService.java:215–219](../../src/main/java/dev/dasan/customdungeons/session/DisconnectService.java#L215-L219) clona y reemplaza cada elemento de `PlayerDeathEvent.getDrops()` para marcar la penalización. El bytecode de Paper 26.3 build 157 muestra que esa lista transforma los elementos reemplazados en `Entity.DefaultDrop` sin su consumidor nativo y usa la ruta de drop de jugador como alternativa. En esta ruta interviene `EssentialsAntiBuildListener.onPlayerDropItem`: cancela cuando falta `essentials.build` y el permiso por material, con `protect.disable.use` activo. El inventario acaba vacío pese al veto. El [listener oficial de EssentialsX](https://raw.githubusercontent.com/EssentialsX/Essentials/2.x/EssentialsAntiBuild/src/main/java/com/earth2me/essentials/antibuild/EssentialsAntiBuildListener.java) y el bytecode del jar instalado coinciden en ese control. Es un punto de revisión del manejo de drops modificados/cancelados, no una demostración de que CustomDungeons borre inventarios vanilla fuera de sesión.

### Cómo reproducir sin confundir ausencia y eliminación

1. Arrancar únicamente agentes con RAM libre ≥2500 MB y Pi <75 °C. Si supera 80 °C, detener y esperar <70 °C. Conectar un bot superviviente con `respawn:false`; usar comandos `minecraft:` para `give`, `kill`, `tp` y `gamemode`.
2. Para vanilla, usar suelo seguro alejado del borde, por ejemplo `(2028.5,65,620.5)`. Consultar `data get entity <bot> Dimension`, `Pos` e `Inventory`. Leer las tres gamerules anteriores en **el mundo real de la muerte**, dar un ítem y ejecutar `minecraft:kill <bot>`. Para daño real, dar otro ítem, teletransportar a Y=100 y habilitar física: debe caer hasta Y=65 y registrar muerte por caída.
3. Mantener otro jugador vivo cerca o, solo para diagnosticar, consultar primero `forceload query` y cargar temporalmente el chunk. Aquí no había chunks forzados; se añadió `[126,38]` con `forceload add 2028 614` y luego se retiró. Consultar inmediatamente y a los 5–20 s:

   ```mcfunction
   execute in minecraft:cd_dungeons positioned 2028.5 65 620.5 as @e[type=minecraft:item,distance=..24] run data get entity @s
   execute in minecraft:cd_dungeons positioned 2028.5 65 620.5 unless entity @e[type=minecraft:item,distance=..24] run say DROP_MISSING
   ```

   Registrar UUID, `Item`, `Pos` y `Age`. Si desaparece de los selectores, recargar el chunk y buscar el UUID antes de afirmar que fue eliminado. Ampliar también el volumen: la caja publicada de la dungeon termina en X=2026 y no incluye la plataforma exterior. El control histórico vanilla utilizó otra caja, X=2030 con `dx=6`, Y=64 con `dy=10`, Z=610 con `dz=8`; no sustituye una búsqueda amplia ni una comprobación de carga/suelo.
4. Para el fallo confirmado, unir A/B al Coloso, esperar 10 s de lobby y 12 s de cámara, situarlos en las coordenadas de sala anteriores, dar lapislázuli a A, desconectarlo y reconectarlo. Conservar B vivo. Deben aparecer muerte, aviso AntiBuild, inventario NBT vacío y ausencia de item dentro de la sala cargada. Repetir con solo AntiBuild desactivado **durante un reinicio completo**, restaurándolo siempre; luego repetir con AntiBuild activo y permiso específico de drop. No usar `/reload` para esta comparación.

### Recomendación para servidores con estos plugins

Si se conserva AntiBuild y la regla de dungeon es perder inventario, permitir a los participantes soltar sus ítems **en los mundos de dungeons**, mediante `essentials.build.drop.<material>` o `essentials.build.drop.*`. El ensayo validó el nodo del lapislázuli manteniendo `essentials.build=false`. Ejemplo para un grupo de jugadores existente, a aplicar por su administrador:

```text
lp group <grupo-jugadores> permission set essentials.build.drop.* true world=cd_dungeons
```

Revisar también `essentials.build.pickup.*` si deben recuperar el botín: AntiBuild controla recogida además de drop. No hace falta conceder `essentials.build` ni OP para resolver el veto de drops. Mantener `keep_inventory=false`, `entity_drops=true`, `mob_drops=true` en los mundos pertinentes y revisar que `essentials.keepinv` no imponga una política distinta. Como alternativa global, el administrador puede desactivar `protect.disable.use`, pero eso permite otros usos de ítems y es un cambio más amplio. No se aplicó ninguna recomendación permanentemente ni en el servidor del usuario; **el mismo riesgo existe allí si AntiBuild y esos permisos tienen el mismo estado**.

### Restauración y cierre

Se retiró únicamente `EssentialsXAntiBuild-2.22.1-dev+24-49a2f10.jar`, a `plugins/.t49-disabled/`, y se restauró antes del último arranque. **Lista final idéntica a la inicial:** 16 plugins activos — CustomDungeons, CustomCrafting, Essentials, EssentialsAntiBuild, EssentialsProtect, LuckPerms, Multiverse-Core, Multiverse-Portals, NBTAPI, SocialBlueprint, Vault, ViaBackwards, ViaVersion, WolfyUtilities, WorldEdit y WorldGuard — y los cinco jars previamente desactivados siguen en `plugins-disabled/`. Nombres y SHA-256 de los **21 jars** coinciden; `.t49-disabled/` queda vacía. Ningún plugin fue borrado.

Permiso temporal del lapislázuli retirado explícitamente; comprobación LuckPerms posterior: sin nodo directo ni heredado. Sin chunks forzados al terminar. Las **43 configs respaldadas** coinciden byte a byte con sus originales; solo hubo que restaurar la reescritura automática de `server.properties` al apagar. No se cambiaron gamerules, definiciones ni configuraciones de plugins.

Apagado limpio a **07:47:07 UTC**, con `test-server.sh stop`, guardado de chunks y fin de I/O; `list` mostró **0 jugadores** antes del apagado. Verificación final a **07:47:43 UTC**: puertos **25566/25576/18849/18850/18851 cerrados**, bots/controladores/monitor terminados. Arranques válidos con 3500–3539 MB libres y 57,3–62,8 °C; máximo de lecturas observado **73,8 °C**, sin alcanzar 80 °C. Evidencia local ignorada y protegida en `.agent/`: `commands.jsonl`, `bots.jsonl`, `peer.jsonl`, logs de las tres condiciones, bytecode de referencia, `plugins-initial.json`, `configs-initial.json`, `temperature.jsonl` y `final-state.json`. Sin commits.

## T51 — entrada en modo construcción y revisión integrada

**7-oct-2026, rama `fix/t51-build-mode-entry`, Java 25 / Paper 26.3, agentes / 25566.** Revisión autorizada con un bot Mineflayer 4.39.0 / protocolo 26.1, como el cliente nativo de T45. Ejecución completa **11:30:21–11:34:12 UTC**: **16/16 escenarios pasaron**, incluyendo recarga y reinicio limpio. La suite apagó su servidor y retiró el OP temporal; el usuario pidió un arranque final para dejarlo encendido con este build. No se toca el servidor del usuario / 25565.

### Diagnóstico y evidencia local

La traza histórica a **05:07:28** contiene `BuildState$Saved.<init>` → `BuildMenu.prepare` → `BuildModeService.enter` → botón 47, con `NullPointerException` en `Objects.requireNonNull`. No identifica el ID ni si faltaba `definition` o `baseline`. La ausencia de versión vigente en una dungeon nueva reproduce la línea base nula; se corrige usando la definición del editor. La reproducción histórica aislada sobre v1.1.0 y la revisión posterior se detallan abajo. **No se ha aislado la causa específica del intento publicado histórico comunicado por el usuario.** La publicada funciona en los tests reales sin servidor y en todas las entradas en vivo de esta revisión; no se infiere de ello un estado histórico que no fue conservado.

`DungeonMenuFlowTest` carga `docs/reference/ejemplos/coloso-abismal` con `DefinitionCodec`, `DefinitionStore`, validación y `BuildJournal` reales en directorios temporales. Recorre lista → editor → botón 47 → servicio real, comprueba las nueve entregas y restaura los 41 slots Bukkit originales. Bukkit, renderizado y serialización NBT de inventario se simulan en esos tests; la prueba Paper/Mineflayer de abajo comprueba los ítems reales.

| Hipótesis | Evidencia comprobable sin servidor |
|---|---|
| Clave distinta del ID del editor | La carga real usa `coloso-abismal`; coincide con `DungeonDef.id()`, el editor y la línea base. |
| Vacío durante carga asíncrona | Se conserva el snapshot publicado hasta aplicar el resultado; con contenido igual se puede entrar. Una publicación distinta informa conflicto y no modifica inventario. |
| Borrador del asistente con definición nula | `WizardDraftStore.save(Saved(null,...))` falla antes de insertar; la publicada permanece accesible. |
| `source.draft.get()` nulo | La lista abre un editor editable con definición publicada no nula; el `Saved` original acepta ambos argumentos. |
| Editor `controlOnly` | La vista ocupada tiene `draft == null`, pero no muestra botón 47, no es editable y `prepare` devuelve sin retirar ítems. |

Estas pruebas descartan las hipótesis **en las rutas ensayadas**. Los nuevos fallos registran dungeon, UUID del admin, fase (`prepare`/`backup`/`activate`) y traza; `Saved` identifica el campo nulo. Se preservan borradores de construcción cuando el editor del asistente vuelve con su definición inicial. Un fallo previo a entregar herramientas avisa con `build.entry-failed` y mantiene el inventario; una entrega parcial devuelve los originales desde la copia duradera. Las regresiones cubren también conflictos, publicación, reanudación sin editor y callbacks de generaciones antiguas.

### Revisión posterior y reproducción histórica sin servidor

Tras el informe del revisor, se vuelve a probar el código Java **anterior al fix T51**: commit `c421b9732e3b9560e25e2e863572edd4579000ac`, versión Gradle **1.1.0**, padre de `005ac11`. El árbol `src/main/java` coincide exactamente con el de `main` (`8a6df7ad49057fe977f6dc2c7314ceac12f6aaba`); las novedades posteriores de `main` son de documentación. Se extraen sus fuentes mediante `git archive` a `.agent/t51-history/` y se compilan en un source set independiente; su classpath de ejecución antepone esas clases y excluye las clases actuales de producción. No se cambia de rama ni se modifica otro worktree. Se usa el fixture actual de Bukkit y el codec, almacén, journal y servicio reales de esa versión. El test temporal comprueba la excepción histórica; el flujo que revela el bug se conserva como regresión `newDungeonCreatedFromListEntersBuildWithoutSaving` contra el código corregido.

| Caso en v1.1.0 anterior al fix | Resultado sin servidor |
|---|---|
| Lista → botón 49 «crear» → confirmar ID → editor nuevo → botón 47, sin guardar | **Reproduce el NPE exacto**. La definición del editor existe; `latestDefinition` y la línea base son nulas. La excepción sale del callback programado, antes del respaldo o de retirar ítems. |
| Cargar el pack real `coloso-abismal` → lista → editor publicado → botón 47 | **No reproduce el NPE**. Clave e ID coinciden, ambos argumentos de `Saved` existen; entra, entrega nueve herramientas y restaura los 41 slots originales. |

Traza de la prueba histórica (los callbacks del scheduler se ejecutan desde la cola del fixture):

```text
java.lang.NullPointerException
  at java.util.Objects.requireNonNull(Objects.java:220)
  at BuildState$Saved.<init>(BuildState.java:10)
  at BuildMenu.lambda$prepare$0(BuildMenu.java:44)
  at BuildMenu.prepare(BuildMenu.java:43)
  at BuildModeService.enter(BuildModeService.java:66)
  at DungeonMenu.lambda$renderFooter$1(DungeonMenu.java:210)
```

**Dos probes históricos pasaron**: uno exige esa excepción y el otro exige éxito publicado. Evidencia local ignorada: `.agent/t51-history/history.log`, init script, fuentes extraídas y test temporal; no entran en la suite del producto. El retraso histórico de 11 segundos tras abrir `/customdungeon` es compatible con crear una dungeon y pulsar el botón; la traza no identifica el ID y no permite atribuir con certeza ese intento. Sigue sin reproducirse el NPE comunicado sobre una publicación existente y no se inventa una segunda causa.

Las regresiones del revisor fallaron antes de corregir (**5 ejecutadas, 4 fallos** incluyendo el test puro de eliminación); después pasa la suite enfocada de construcción, estado y journal. Cambios:

- Al terminar la escritura del journal, el callback principal comprueba otra vez conflictos **inmediatamente antes de `DefinitionStore.save`**. Publicación reemplazada/eliminada durante la espera: no escribe, informa `gui.dungeon.conflict`, termina el estado de guardado y libera el lock si el admin salió. Se prueban publicación concurrente de una dungeon nueva y reemplazo/eliminación de una publicada.
- `BuildState.Saved` añade `baselineExists`, conserva el constructor previo y lo persiste en `BuildJournal` como `baseline-exists`. Una dungeon nueva sin versión vigente se marca `false`; una versión existente/eliminada se distingue incluso tras editar, deshacer, publicar y reabrir el journal. `conflicts(null)` rechaza la eliminada con aviso al entrar/guardar. Los contratos T01 no cambian. **Compatibilidad conservadora:** un journal antiguo sin el campo se considera basado en una versión existente. Si falta ahora esa versión, se bloquea, pues el formato anterior no permite distinguir eliminación de creación nunca publicada; se conserva el archivo y sus datos.
- La limpieza de bots retira su OP **independientemente de `owned`**. Si Paper está apagado tras un segundo arranque fallido, retira únicamente ese nombre temporal de `ops.json` mediante reemplazo atómico bajo el lock compartido, conservando los demás operadores. No edita offline si detecta Paper vivo y comunica el fallo. Tests con directorios temporales prueban deop con servidor ajeno al runner, limpieza offline tras fallo y ausencia de identidad; **9/9 tests Node**, frente a dos fallos previos al arreglo. Se conservan los filtros de Mojang añadidos por el arquitecto.

**Verificación final de esta revisión:** `./gradlew build --no-daemon --max-workers=2` (JVM 768 MB, dos procesadores) terminó **BUILD SUCCESSFUL en 3 min**: **1240 tests, 0 fallos, 0 errores, 0 omitidos**. Self-check: **9/9 Node** y serializers reales disponibles sin conexión; probes históricos: **2/2**. `guiSnapshots` pasó y `scripts/render-gui.py --cache-dir /tmp/customdungeons-cache` generó **380 PNG**, incluidos `build/gui-snapshots/t40-build-menu.png`, `t40-build-rooms.png`, `t40-build-bar.png` y `t40-dungeon-editor.png`. `git diff --check` limpio. Logs locales ignorados: `.agent/t51-review-build.log`, `t51-review-selfcheck.log`, `t51-review-snapshots.log`, `t51-review-render.log`; el rojo/verde de los tests queda en `t51-review-red.log`, `t51-review-green.log`, `t51-cleanup-red.log`, `t51-cleanup-green.log`.

**Esta revisión se verifica sin desplegar, reiniciar ni conectar bots al servidor ocupado.** Los 16/16 escenarios y el jar documentados a continuación pertenecen a la ejecución anterior, no al código posterior a esta revisión. El comando para repetirlos cuando el servidor quede libre y apagado sigue siendo `CD_TARGET=agents scripts/test-t51-bots.sh --run`.

### Revisión independiente, ronda 2

Revisión de `f2b026b` contra `main`, con árbol inicialmente limpio. Se comprueban el segundo control de conflictos tras el journal, la procedencia de la base y la retirada de OP independiente de `owned`. Los históricos de mensajes v17 coinciden byte a byte con los catálogos anteriores; la migración a v18 conserva personalizaciones y añade el aviso de entrada fallida.

Se añaden dos regresiones: un journal antiguo sin `baseline-exists` reanuda sus cambios, contexto e historial cuando la publicación sigue vigente, sin reescribir el archivo; una restauración que lanza durante la entrada mantiene la generación activa y el respaldo, no duplica la entrada y permite restaurar los originales al reintentar la salida. `./gradlew build --no-daemon --max-workers=2` (Java 25, JVM 768 MB, dos procesadores) termina **BUILD SUCCESSFUL en 2 min 36 s: 1242 tests, cero fallos, errores u omitidos**. `CD_TARGET=agents scripts/test-t51-bots.sh --self-check`: **9/9 Node**, sin conexión.

**Hallazgo pendiente, severidad media:** `BuildMenu.prepare` registra un editor normal desde el journal cuando no hay versión vigente, antes de comprobar si el borrador procede de una publicación eliminada. Aunque devuelve conflicto, conserva el editor y su lock; la dungeon vuelve a aparecer en la lista y ese editor permite llamar a `DefinitionStore.save` para recrearla. La comprobación de procedencia debe preceder al registro del editor recuperado, sin dejar editor ni lock tras rechazarlo. No se cambia producción durante esta revisión.

Reproducción aislada, ignorada: `.agent/t51-reviewer/DeletedDraftProbe.java`, ejecutada con `./gradlew -I .agent/t51-reviewer/probe.gradle t51ReviewerProbe`. **Un test falla con cuatro comprobaciones**: lock retenido, dungeon reaparecida, editor editable y guardado invocado. Informe generado en `build/reports/tests/t51ReviewerProbe/index.html`; esta prueba temporal no forma parte del build normal. Las regresiones existentes solo comprobaban el retorno nulo y el aviso, sin comprobar esos efectos posteriores.

Se contrasta la evidencia previa: **16 escenarios, 19 entradas y usos de herramientas, 19 restauraciones**, cero líneas ERROR/Exception en los tres logs guardados. El puerto 25566 continúa abierto, el jar desplegado conserva el SHA-256 `e41d39c57865b2dfe76b5703494b7473c1284801eee51afee13ffdc3bf7c6a26`, el `latest.log` consultado tampoco contiene errores/excepciones y no hay OP temporales T51. No se despliega, reinicia, detiene ni ejecuta la suite de bots sobre el servidor reservado. Esta evidencia en vivo sigue correspondiendo al jar anterior a `f2b026b`.

### Incidencias del guion y explicación del mensaje

| Intento | Resultado / corrección |
|---|---|
| Dos intentos del arquitecto; segundo en `.agent/t51-bots/20261007T111602Z-1186115/` | El primero se interrumpió por el timeout de claves públicas de Mojang con `online-mode=false`; se conservan sus filtros en `checkLog` y el `rg` del cierre. El segundo entregó nueve herramientas al entrar por botón en la publicada, pero el guion leyó una ventana antes de recibir sus ítems. |
| `.agent/t51-bots/20261007T112013Z-26/` | Tres escenarios publicados pasaron. Se corrigió la espera de «Salas»: el `window` completo del intento anterior apareció 36 ms después de `Missing create-room control`. `roomMenuReady` exige resumen y botón de creación cargados y tiene regresión Node. El siguiente fallo era otra navegación del guion: salir del editor nuevo para buscarlo otra vez activa correctamente la confirmación de descarte. Ahora se pulsa directamente su botón 47. |
| `.agent/t51-bots/20261007T112329Z-26/` | Once escenarios pasaron. Un pillager mató al bot durante el siguiente uso de herramientas; solo se había fijado creativo en la primera conexión. Ahora cada conexión fija y verifica creativo y una muerte aborta con diagnóstico de fixture. No se eliminan mobs ni se cambian gamerules. |
| `.agent/t51-bots/20261007T112904Z-26/` | **16/16 pasaron**. Los eventos `connected` confirman reconexiones en supervivencia antes de restablecer creativo. No hubo muerte ni fallo de preparación/entrega/GUI del plugin. |

**«Se reanudó tu borrador» no prueba que hubiera un borrador previo.** `build.entered` es un texto incondicional heredado de T40, enviado también al crear el primer journal antes de entregar herramientas. En el intento del arquitecto, `savedDefinitionPreserved:false` significaba que no existía un borrador del bot antes de entrar, no una pérdida. El guion ahora registra `priorDraftFound:false` y `savedDefinitionPreserved:null` para primera entrada, y `true/true` al verificar una reanudación. No se cambia ese texto del catálogo en esta iteración.

### Matriz ejecutada y resultados

Bot **`T51B6ac62d4a`**, UUID offline propio, OP temporal. Fixtures nuevas: `t51_t51b6ac62d4a_e3` (editor) y `t51_t51b6ac62d4a_w5` (asistente); también se crearon las variantes e4/w6. La publicada `coloso-abismal` quedó **idéntica byte a byte** según SHA-256; todos los cambios se guardan en borradores privados del bot, sin publicar ni modificar bloques.

Inventario de control: siete diamantes con `custom_data`, espada de hierro con daño 9 y datos propios, casco con daño 3 y escudo en segunda mano con daño 2, slot seleccionado 4. Cada comparación incluye **46 slots de protocolo**, cantidades, NBT, componentes añadidos/eliminados, armadura, segunda mano y vacíos; los componentes se comparan como mapas, sin depender del orden del paquete. La entrada exige exactamente nueve ítems, índices PDC 0–8 en la barra. Cada uso exige selector/creación de sala, cambio real de punto, reversión por deshacer y apertura del editor; se deja una edición persistida y se comprueba que las reentradas la conservan.

| Grupo | Entrada / salida | Resultado |
|---|---|---|
| Publicada | Botón→comando; comando→botón; botón→desconexión | 3/3 OK |
| Nueva desde editor | Botón→botón; comando→desconexión | 2/2 OK |
| Nueva desde asistente | Botón→comando; comando→botón | 2/2 OK |
| Borrador previo de editor/asistente/publicada | Comando→desconexión; botón→botón; comando→comando | 3/3 OK |
| `/customdungeon reload` con construcción activa | Restauración al recargar y reentrada de publicada/asistente por botón/comando | 2/2 OK |
| Reinicio con construcción activa | Recuperación al reconectar; publicada por ambas rutas, borradores de editor/asistente; tres tipos de salida | 4/4 OK |

La evidencia contiene **19 entradas con nueve herramientas y 19 usos de varias herramientas**, **16 restauraciones al salir**, **dos restauraciones al recargar** y **una comprobación de inventario tras reiniciar**. `state.json` enumera los 16 escenarios aprobados; `after.jsonl` termina con `suite-pass {scenarios:16}`. El reinicio es limpio: se prueba construcción activa → apagado → carga de player-data → reentrada. La caída abrupta se simula en tests Java; no se provoca una caída real.

Logs Paper de la ejecución completa: **cero líneas ERROR/Exception**, incluso sin aplicar los filtros de Mojang, en `before-server.log`, `after-server.log` y `shutdown-server.log`. Líneas relevantes (hora local del servidor, UTC−5):

```text
[06:30:17] Done (62.875s)! For help, type "help"
[06:31:42] T51B6ac62d4a issued server command: /customdungeon reload
[06:31:56] T51B6ac62d4a issued server command: /customdungeon reload
[06:33:39] Done (60.482s)! For help, type "help"
[06:34:12] T51B6ac62d4a lost connection: Disconnected
[06:34:12] System chat: Stopping the server
```

Evidencia ignorada en `.agent/t51-bots/20261007T112904Z-26/`: JSONL por fase, inventarios, `state.json`, controladores, `lifecycle.log` y tres logs Paper. Jar probado: `CustomDungeons-1.1.0.jar`, SHA-256 `e41d39c57865b2dfe76b5703494b7473c1284801eee51afee13ffdc3bf7c6a26`, igual al desplegado. El OP del bot se retira; sus datos y borradores nuevos se conservan para reproducción. No se borra contenido ajeno.

### Reproducción y límites

```bash
CD_TARGET=agents scripts/test-t51-bots.sh --self-check
CD_TARGET=agents scripts/test-t51-bots.sh --plan
CD_TARGET=agents scripts/test-t51-bots.sh --run
```

El self-check actual ejecuta **nueve tests Node**, dependencias existentes en `servidor/bots/node_modules` y serializers reales de clic/uso/agacharse; no conecta. El callback nativo usa el paquete 0x44 de T45; los clics cancelados omiten predicciones de stacks cuyo formato cambió a hashes. No se instalan dependencias nuevas.

`--run` requiere el servidor de agentes libre y apagado y la publicada sin borrador del asistente que la oculte. Rechaza `paper-26.3` o puerto 25566 abierto, usa exclusivamente `CD_TARGET=agents scripts/test-server.sh`, despliega, arranca, reinicia una vez y apaga al terminar. Coordina dos ejecutores T51 y espera dos minutos/reintenta hasta diez veces ante bloqueo de otra operación. Si entra otro jugador, cancela el bot y deja el servidor encendido. Fuera del sandbox, el arranque habitual es `CD_TARGET=agents scripts/test-server.sh start`, tras comprobar `pgrep -f paper-26.3`. Dentro de este sandbox, el proceso de un arranque aislado no sobrevivió al cierre de la orden; se delegó el mismo script, con la comprobación de procesos también en el host, a la unidad transitoria de usuario `customdungeons-agents-t51.service` (tipo `forking`, sin instalación ni habilitación permanente). La unidad se creó por el bus de usuario `/run/user/1000/bus`; el runner completo de bots no necesita ese cambio porque conserva su proceso hasta apagar. No lanzar otra suite mientras haya jugadores.

Verificación de la ejecución de bots anterior: `./gradlew build --no-daemon --max-workers=2` (JVM 768 MB, dos procesadores) terminó **BUILD SUCCESSFUL en 9 s**, seis tareas `UP-TO-DATE`; no hubo cambios Java desde la suite verde de **1228 tests, 0 fallos, 0 errores, 0 omitidos**. Self-check actual: **6 tests Node / 0 fallos**. `git diff --check` limpio. El intento aislado a 11:39:15 UTC anunció «listo», pero su proceso terminó con el sandbox y el puerto estaba cerrado; no se considera un arranque persistente. Arranque final autorizado desde la unidad de usuario: **11:48:31 UTC**, `Done (44.240s)!`; a **11:48:49 UTC**, **7 dungeons / 19 plantillas** cargadas. Unidad **active**, PID principal del host **1208460**, puerto **25566 abierto comprobado desde órdenes independientes**, mismo SHA-256 del jar probado. **Cero OP temporales T51**. El servidor se deja encendido para el usuario, como excepción explícitamente autorizada al cierre habitual de la suite. Logs locales: `.agent/t51-build.log`, `t51-bot-self-check.log`, `t51-final-start.log`, `t51-host-start-job.log`, `t51-final-state.log` y `server-console.log`; no se versionan. Las capturas actuales de los menús siguen en `build/gui-snapshots/t40-build-menu.png`, `t40-build-rooms.png`, `t40-build-bar.png` y `t40-dungeon-editor.png` (380 PNG generados). No hubo cambios Java/GUI adicionales al corregir los fallos del guion.

**Límites:** el fallo publicado histórico no se ha reproducido ni asignado a otra causa; el diagnóstico nuevo permite identificar un nuevo intento si reaparece. La prueba usa creativo y movimiento por consola, no certifica combate ni todas las interacciones humanas con otros plugins. El aviso incondicional de reanudación de T40 queda explicado arriba. Esta revisión no altera los contratos T01.
