# Demo: La Cripta del Guardián

Dungeon `demo`, preparada para CustomDungeons 1.0.1 en Paper 26.3. Las definiciones finales están en [ejemplos/demo](../reference/ejemplos/demo/), con subcarpetas `dungeons/` y `mobs/`; los mismos archivos se instalan en `plugins/CustomDungeons/`. No requiere cambios de Java ni configuración operativa del plugin.

## Construcción y acceso

El recinto se construyó sobre un volumen vacío comprobado mediante un cliente conectado, lejos del laboratorio T18. Todo el recorrido está a Y=65 en `cd_dungeons`, con suelo a Y=64 y eje central Z=507,5. Las salas se alinean hacia el este (+X).

| Zona | Límites de construcción (X; Z) | Interior / altura | Punto de referencia |
|---|---|---|---|
| Lobby abierto | 480–496; 499–515 | Plataforma, bancos y pilares con faroles de almas | `(488.5,65,507.5)` |
| Sala 1 | 496–512; 499–515 | 15×8×15; techo Y=73 | Checkpoint `(499.5,65,507.5)` |
| Sala 2 | 512–528; 499–515 | 15×8×15; techo Y=73 | Checkpoint `(515.5,65,507.5)` |
| Sala 3, jefe | 528–550; 496–518 | 21×10×21; techo Y=75 | Checkpoint `(531.5,65,507.5)` |

Suelos de deepslate pulida, camino central de ladrillos agrietados, paredes y techos de deepslate/blackstone, pilares cincelados, linternas y faroles de almas. Los pilares decorativos están fuera del recorrido central y los radios de spawn.

La entrada del lobby está en X=496; las puertas entre salas están en X=512 y X=528. Los tres huecos son de 3×3: Y=65–67, Z=506–508. Los huecos están vacíos cuando la dungeon está libre; al empezar, el plugin los cierra con su material configurado (hierro en este servidor) y al abrir o terminar restaura el aire original. No rellenes permanentemente estos huecos: `DoorService.open` restaura el bloque original, no lo sustituye siempre por aire.

El portal `entrada_demo` está cerca del spawn normal, en `world`:

- Volumen de activación: `(12,113,-1)` a `(12,115,1)`; centro `(12,114,0)`.
- Marco: plano X=12, Y=112–116, Z=-2–2; obsidiana y crying obsidian.
- Plataforma: X=8–16, Y=112, Z=-4–4; acceso por escalones desde el oeste (X=4–7). La plataforma solo sustituyó aire; se conserva el terreno existente.
- Salida de la dungeon: `(0.5,111,0.5)` en `world`, junto al spawn de Multiverse `(0,111,0)`.

No hay bloques de portal al Nether: cruzar el volumen vacío dentro del marco activa Multiverse-Portals.

## Partida y salas

Una partida por dungeon, de 1 a 4 jugadores, cuenta atrás de 15 segundos, tres vidas y límite de 20 minutos. Se conserva el inventario al morir. No exige permiso específico ni cooldown. La cantidad de mobs no escala; la vida aumenta un 10 % por jugador adicional al mínimo. Con dos jugadores, el jefe tiene 198 puntos de vida (180 × 1,1).

Al acabar el lobby, CustomDungeons transporta al grupo al checkpoint de la sala 1. Las salas posteriores comienzan cuando un participante entra en su región después de abrir la puerta anterior.

| Sala | Región (X; Y; Z) | Spawner / radio | Oleadas | Apertura |
|---|---|---|---|---|
| `sala-1` | 497–512; 64–73; 500–514 | `sala-1-centro`: `(505.5,65,507.5)` / 2,5 | 1: 2 zombies + 1 esqueleto simultáneos. 2: 1 zombie + 2 arañas, escalonados cada 20 ticks. Pausa de 60 ticks entre oleadas. | Automática tras eliminar la segunda oleada |
| `sala-2` | 513–528; 64–73; 500–514 | `sala-2-centro`: `(521.5,65,507.5)` / 2,5 | 2 zombies + 1 esqueleto + 1 araña simultáneos | Llave ligada a la partida; portador `demo-skeleton` |
| `sala-3` | 529–549; 64–75; 497–517 | `sala-3-centro`: `(539.5,65,507.5)` / 2 | 1: 2 zombies + 2 arañas. Pausa de 80 ticks. 2: jefe único. | Completa la dungeon; no tiene puerta final |

La llave aparece al completar la sala 2 en el lugar donde murió el esqueleto. Recoge el gancho de cuerda brillante, sostenlo y haz clic derecho en un bloque de la puerta de X=528. Una llave ordinaria de `/give` no sirve: el plugin comprueba los datos de la partida. La llave desaparece al usarse o cerrar la sesión.

Cada superviviente recibe **3 diamantes y 50 puntos de experiencia**, además de cualquier XP vanilla del combate. El premio no contiene dinero ni comandos adicionales.

## Biblioteca de mobs

| Plantilla | Estadísticas base | Equipo / habilidades |
|---|---|---|
| `demo-zombie` | Zombie, 24 HP, daño 3, velocidad 0,23 | Armadura completa de hierro con Protección I e Irrompibilidad I; espada de hierro; drop de equipo 0 |
| `demo-skeleton` | Esqueleto, 20 HP, daño 2, velocidad 0,22 | Casco de cota, arco Poder I; `arrow_effect` cada 6 s: Lentitud I durante 3 s, rango 16, aviso 15 ticks |
| `demo-spider` | Araña, 18 HP, daño 2, velocidad 0,24 | `cobweb` cada 9 s, rango 10, aviso 15 ticks; telaraña temporal de 60 ticks |
| `demo-boss` | Wither Skeleton, 180 HP, daño 4, velocidad 0,23, escala 1,5, resistencia al empuje 0,5 | Espada de piedra, casco de hierro, BossBar púrpura y música |

Las plantillas desactivan los drops vanilla; las telarañas y puertas las restaura CustomDungeons. La resistencia al empuje de los mobs normales es 0,1.

El jefe tiene:

- Música inicial `minecraft:music_disc.cat`.
- `wither_skulls` cada 10 s: un cráneo, daño 4, Wither de 2 s, rango 22, aviso 25 ticks.
- `enrage` una vez al bajar al 33 %: Fuerza I y Velocidad I. **El disparador `HEALTH_BELOW` usa 33,0; las fases usan fracciones 0,66 y 0,33.**
- Combo `demo-cadena` cada 14 s: `hook` (potencia 0,8; elevación 0,1) → `anchor` (30 ticks) → `meteors` (2 meteoritos, radio 2, daño 4, aviso 40 ticks). Retardos de los pasos: 0, 20 y 20 ticks. Objetivo cercano dentro de 20 bloques.
- Fase al 66 %: título «Fase II: Sombras», música `music_disc.13`, sonido Wither, 20 ticks de invulnerabilidad; añade `darkness_pulse` cada 12 s, oscuridad de 2 s.
- Fase al 33 %: título «Fase III: Furia», música `music_disc.blocks`, sonido Wither Skeleton y 20 ticks de invulnerabilidad. Conserva las habilidades anteriores.

Para una visita en supervivencia, lleva espada, armadura, comida y evita los avisos de meteoritos. La demo enseña configuración y progresión; el equilibrio para un jugador requiere valoración humana adicional.

## Portal: sintaxis verificada

Configuración probada con Multiverse-Portals 5.3.0 y contrastada con su [documentación oficial de acciones](https://mvplugins.org/portals/how-to/configure-portal-actions/):

```text
//pos1 12,113,-1
//pos2 12,115,1
/mvp create entrada_demo
/mvp modify entrada_demo action-type command
/mvp modify entrada_demo action "console:customdungeon join %player% demo"
/mvp modify entrada_demo action-success-message @disabled
/mvp info entrada_demo
```

Selecciona las esquinas estando en `world`. Estos comandos reproducen la creación si el portal no existe; no los repitas para reemplazarlo durante una partida. `mvp info` mostró tipo `command`, volumen correcto y el comando expandido con el nombre del jugador. La acción no empieza con `/`, utiliza `%player%` y se ejecuta desde consola.

El propietario del portal se dejó en `TakeOnnMee` (`mvp modify entrada_demo owner TakeOnnMee`). Se concedió `multiverse.portal.access.entrada_demo` al grupo `default`. CustomDungeons sigue validando `customdungeons.player.join`, límite, estado y cooldown del destinatario. El permiso de entrada del plugin ya está habilitado por defecto; se concedió explícitamente al bot DemoB durante la prueba.

## Recorrido para TakeOnnMee en la GUI

Haz la inspección cuando `demo` esté libre. Como operador:

1. Ejecuta `/customdungeon`; abre la entrada **demo** de la lista.
2. En **Ajustes**, revisa lobby, salida, jugadores, cuenta atrás, tres vidas y conservar inventario. En **Escalado**, compara cantidad fija con vida +10 %.
3. En **Salas**, abre `sala-1`: región, checkpoint, puerta automática; entra en su spawner y compara sus dos oleadas y el modo escalonado de la segunda.
4. Abre `sala-2`: puerta con llave y plantilla portadora `demo-skeleton`. En su oleada comprueba que existe exactamente un esqueleto.
5. Abre `sala-3`: región mayor y ausencia de puerta; revisa los esbirros y la segunda oleada con `demo-boss`.
6. En **Premio**, examina los tres diamantes y 50 XP. Los hooks START/COMPLETE/FREE dejan marcadores en consola para estudiar el ciclo.
7. Vuelve a la lista y abre **Biblioteca de mobs** (libro). Examina las cuatro plantillas `demo-*`: equipo y encantamientos del zombie, flechas del esqueleto, duración de telaraña y, en el jefe, escala/BossBar, habilidades, combo y dos fases.
8. Usa `/customdungeon show demo` para ver regiones/spawners y visita el lobby con `/minecraft:execute in minecraft:cd_dungeons run minecraft:tp @s 488.5 65 507.5`. Este último comando es una visita administrativa fuera de partida.
9. Para jugar con premio, vuelve a `world` y cruza el marco, o usa `/customdungeon join demo`. `/customdungeon test demo` inicia una prueba sin premio; `/customdungeon leave` abandona tu partida.

No guardes un borrador si solo estás estudiándolo. La GUI bloquea la edición mientras hay una partida activa.

## Verificación y observaciones

Prueba del 6 de octubre de 2026, **06:17:13–06:19:45 UTC-5**, con clientes mineflayer 1.20.4 a través de ViaBackwards, nombres **DemoA** y **DemoB**. Se usó el controlador existente como base; scripts y evidencias locales están en `.agent/`, ignorados por Git. No se modificó Java, no hubo commits y no se reinició, detuvo ni redeplegó el servidor.

| Comprobación | Resultado |
|---|---|
| Dos cruces reales del portal | Ambos llegaron al lobby; posiciones próximas a `(488.5,65,507.5)` |
| Cuenta atrás normal | Se esperaron los 15 s, sin forzar `start` |
| Sala 1 | Dos oleadas derrotadas mediante ataques de bots; nueve bloques de la puerta restaurados a aire |
| Sala 2 | Cuatro mobs derrotados; DemoA recogió la llave en la posición de muerte del esqueleto y hizo clic sobre `(528,65,507)`; puerta abierta |
| Sala 3 | Cuatro esbirros y jefe derrotados mediante ataques de bots; no se usó `/kill` ni `skipwave` |
| Jefe | Escala 1,5 consultada en el servidor; BossBar; títulos de fases recibidos por ambos clientes a las 06:19:15 y 06:19:38 |
| Música | Paquetes `cat → 13 → blocks`; `stop_sound` de `blocks` en ambos clientes al terminar |
| Premio | Ambos inventarios: 3 diamantes; ambos clientes: `experience.points=50`, nivel 4 |
| Persistencia | Run SQLite **23**, `COMPLETED`; ambos `survived=1`, `rewarded=1`, una muerte cada uno; 11 y 4 mobs eliminados, respectivamente |
| GUI | Abiertos lista, editor de `demo`, salas, primera sala, biblioteca de mobs y editor de `demo-boss` sin guardar cambios |

Los bots usaron supervivencia con armadura/espada de diamante, saturación y física desactivada, moviéndose en incrementos de hasta 0,15 bloques. En la sala 3 se aplicó **Resistencia V exclusivamente a los bots** para observar fases y ataques sin agotamiento de vidas. Esto verifica progresión y premio; no certifica el equilibrio del jefe para jugadores humanos. Los ataques, la recogida y el clic de llave fueron acciones reales de cliente, no bajas inducidas por consola. Se observó también un proyectil Wither durante el combate.

Una partida anterior de TakeOnnMee terminó en `COMPLETED` (run 21). Se esperó a que terminara para probar con bots; no se canceló ni se teletransportó al dueño.

### Ajustes y mensajes observados

- **Construcción corregida:** la primera prueba con bots alcanzó el final de la sala 1 pero los huecos estaban rellenos originalmente con blackstone. El plugin restaura ese bloque al abrir, por lo que permanecía una barrera física. Se hizo salir únicamente a los bots, se vaciaron los dos huecos y se repitió el recorrido completo. Reproducción: rellenar una puerta antes de iniciar → completar sala → `DoorService.open` restaura el relleno. Mantener aire en los huecos resuelve el problema; no se cambió Java.
- **Multiverse-Portals:** `/mvp create entrada_demo` produjo el aviso transitorio `Failed Parsing Location … got: `` / invalid LOCATION`. El archivo persistido y `/mvp info` contienen después la ubicación correcta; ambos cruces reales funcionaron. Reproducción: seleccionar con WorldEdit y crear el portal antes de definir la acción. No fue un fallo persistente del portal.
- En la espera de la visita del dueño hubo timeout `keepAliveError` de los clientes de prueba; se reconectaron antes del recorrido válido. No se atribuye ese timeout a CustomDungeons.
- La carga inicial rechazó un selector `SELF` escrito por error en la plantilla. El enum real acepta `CURRENT_TARGET`, `NEAREST`, `RANDOM` y `ALL_IN_RADIUS`. La versión final utiliza `NEAREST`; `enrage` aplica las pociones al propio mob independientemente del selector.

No se detectaron excepciones de CustomDungeons en la partida completa. La música y los títulos se verifican por paquetes; la percepción visual/auditiva humana y el equilibrio quedan fuera de esa evidencia.


### Prueba adicional de habilidades y limpieza

Se abrió **Probar en vivo** de `demo-boss` desde la biblioteca, con DemoA en el lobby, Resistencia V y sin atacar durante unos 42 segundos. Se capturaron entidades `wither_skull` y `fireball` (meteorito en `(488.5,85,507.5)`); el jefe no tiene una habilidad de meteoritos independiente, por lo que esa aparición confirma que el combo llegó a su último paso. No se midieron con precisión los retardos ni el daño con un cliente humano. La prueba se cerró mediante **Detener prueba**, sin guardar cambios.

Estado final comprobado:

- `demo` en **FREE**; `active_sessions=0` y `temp_blocks=0` en SQLite.
- Sin entidades ajenas a jugadores en el volumen de la demo; cero telarañas y cero bloques desconocidos en la inspección de X=480–550, Y=64–75, Z=496–518.
- Ambos huecos de puerta restaurados a aire.
- Bots desconectados; DemoA dejó de ser operador y se retiró el permiso explícito temporal de DemoB. Se limpiaron sus efectos de prueba.
- Retiradas las cargas forzadas usadas para construir e inspeccionar; las partidas cargarán sus propios chunks mediante el plugin.
- Portal persistido, propietario TakeOnnMee, permiso de acceso del grupo `default` conservado. No se borraron definiciones, entidades ni construcciones ajenas.
