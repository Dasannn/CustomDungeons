# Especificación — CustomDungeons (MVP v1.0)

> Documento sujeto a revisión del equipo. Requisitos con ID para trazabilidad en plan y tareas.
>
> **v1.2**: núcleo de mobs con frontera (§9b), inteligencia (§9c), habilidades nuevas (§9d) y jefes del mundo (§9e).

## 1. Resumen
Plugin para Paper 26.3 (Java 25) que permite a administradores crear, mediante GUI y herramientas, dungeons de varias salas en un mundo dedicado. Cada sala tiene spawners con oleadas de mobs personalizados (equipo, pociones, habilidades, combos y fases de jefe). Al completar todas las salas, los supervivientes reciben el premio configurado. Los jugadores llegan mediante portales de Multiverse-Portals que ejecutan el comando de entrada del plugin.

## 2. Glosario
| Término | Definición |
|---|---|
| Dungeon | Conjunto ordenado de salas en el mundo de dungeons, con lobby, salida, reglas y premio. |
| Sala | Región cúbica con checkpoint, puerta y uno o más spawners. |
| Puerta | Bloques que bloquean el paso a la siguiente sala hasta desbloquearla. |
| Llave | Ítem soltado por un mob designado; abre una puerta que exige llave. |
| Spawner | Punto con radio de aparición y lista ordenada de oleadas. |
| Oleada | Conjunto de entradas (plantilla × cantidad + retardo) con un modo de aparición. |
| Plantilla de mob | Definición reutilizable de un mob: tipo, stats, equipo, pociones, habilidades, combos, fases. |
| Combo | Secuencia ordenada de habilidades ejecutada como una sola acción. |
| Fase de jefe | Conjunto de cambios que se aplican a un mob al cruzar un umbral de vida. |
| Partida | Ejecución de una dungeon por un grupo de jugadores. |
| Superviviente | Jugador que sigue en la partida (con vidas) cuando se completa la última sala. |

## 3. Alcance
**Incluido (MVP):** todo lo descrito en las secciones 4–14.

**Excluido:** generación y gestión de portales (lo hace Multiverse-Portals), protección del terreno (lo hace WorldGuard), instancias por grupo, sistema de party, PlaceholderAPI, integración con MythicMobs.

## 4. Mundo y estructura (RF-MUN)
- **RF-MUN-01** Las dungeons residen en un mundo dedicado configurable (`dungeon-world.name`), normalmente gestionado por Multiverse-Core. Si `dungeon-world.auto-create: true` y no existe, el plugin lo crea vacío (void).
- **RF-MUN-02** Una dungeon tiene: id, nombre visible, punto de lobby, punto de salida, jugadores mín., límite de jugadores (RF-PAR-03), cuenta atrás del lobby, vidas por jugador (1–100, por defecto 3), conservar inventario al morir (por defecto **no**), tiempo límite opcional, cooldown por jugador, escalado de dificultad (RF-PAR-16), ganchos de comandos (RF-INT-02), premio, salas ordenadas, estado activado/desactivado.
- **RF-MUN-03** Una sala tiene: región cúbica, checkpoint, puerta (región de bloques; opcional en la última sala), modo de desbloqueo (`AUTOMATICO | LLAVE`), ≥ 1 spawner.
- **RF-MUN-04** Un spawner tiene: ubicación, radio de aparición, oleadas ordenadas.
- **RF-MUN-05** Una oleada tiene: entradas (plantilla, cantidad, retardo desde el inicio de la oleada), modo `SIMULTANEO | SECUENCIAL | ESCALONADO(intervalo) | ALEATORIO`, pausa antes de la siguiente oleada.

## 5. Partida (RF-PAR)
- **RF-PAR-01** Entrada única: `/customdungeon join [jugador] <dungeon>`. Sin `jugador` lo usa el propio jugador; con `jugador` lo ejecuta la consola o un portal de Multiverse-Portals (RF-INT-01). Valida estado, límite, cooldown y permiso; si pasa, teletransporta al lobby; si no, envía el motivo con el prefijo.
- **RF-PAR-02** Una sola partida activa por dungeon. En curso → mensaje "dungeon en curso".
- **RF-PAR-03** Límite de jugadores por dungeon: número máximo o `0` = sin límite. Con límite, al llenarse el lobby se rechazan nuevas entradas ("dungeon llena").
- **RF-PAR-04** El primer jugador abre el lobby con cuenta atrás configurable. Al terminar, si hay ≥ mínimo, empieza; si no, se cancela y se devuelve a todos a la salida.
- **RF-PAR-05** Iniciada la partida no se admiten nuevos jugadores.
- **RF-PAR-06** Estados: `LIBRE → LOBBY → EN_CURSO(sala, oleada) → COMPLETADA | FALLIDA → RESETEO → LIBRE`.
- **RF-PAR-07** Desbloqueo de puerta al terminar la última oleada de la sala:
  - `AUTOMATICO`: la puerta se abre sola (efecto visual y sonido).
  - `LLAVE`: un mob designado de la sala suelta la llave al morir; un jugador la usa con clic derecho sobre la puerta y esta se abre. La llave queda ligada a la partida: no sale del mundo de dungeons, no se guarda en contenedores y desaparece al terminar la partida.
- **RF-PAR-08** Al abrir la puerta, el checkpoint pasa a la siguiente sala y empieza su primera oleada cuando un jugador entra en ella.
- **RF-PAR-09** Los jugadores no pueden pasar a una sala con la puerta cerrada (incluye ender pearl, chorus y teletransportes no autorizados). La protección de bloques es de WorldGuard (RF-INT-03).
- **RF-PAR-10** Los mobs de la partida no pueden salir de su sala; si salen, se devuelven al spawner.
- **RF-PAR-11** Muerte: el jugador pierde una vida y reaparece en el checkpoint de la sala actual. Su inventario cae al suelo (salvo `keep-inventory: true` en la dungeon). Sin vidas, queda eliminado y vuelve al punto de salida.
- **RF-PAR-12** La partida falla si todos los jugadores son eliminados, abandonan (`/customdungeon leave` o desconexión) o se agota el tiempo límite.
- **RF-PAR-13** Desconexión: cuenta como abandono inmediato; al reconectar se aplica `disconnectMode` (RF-DESC-01). Caída o apagado: recuperación sin penalización (RF-PAR-17).
- **RF-PAR-14** BossBar por partida: sala, oleada y mobs restantes. Los jefes muestran BossBar propia.
- **RF-PAR-15** Cooldown por jugador y dungeon tras completarla (bypass por permiso).
- **RF-PAR-16** Escalado por número de jugadores: por cada jugador por encima del mínimo, `+X %` de mobs por entrada y `+Y %` de vida de los mobs (configurable por dungeon; por defecto 25 % y 15 %). Siempre limitado por el máximo de mobs vivos por partida (RF-CFG-01).
- **RF-PAR-17** Recuperación: al arrancar el servidor, toda partida no terminada se marca como abortada, se eliminan sus mobs y llaves, se restauran bloques temporales y puertas, y los jugadores afectados aparecen en la salida al conectarse.

## 6. Premios (RF-PRE)
- **RF-PRE-01** Componentes: ítems (arrastrados en la GUI, con NBT completo), dinero (Vault), XP, comandos de consola con `{player}`.
- **RF-PRE-02** Solo los **supervivientes** reciben el premio, completo, al completar la última sala. Eliminados y quienes abandonaron no reciben nada.
- **RF-PRE-03** Si el inventario está lleno, los ítems sobrantes se guardan y se entregan con `/customdungeon claim` (persistente en BD).
- **RF-PRE-04** Sin Vault, el dinero se ignora con aviso en consola.

## 7. Plantillas de mob (RF-MOB)
- **RF-MOB-01** Tipo de entidad (cualquier mob vivo), nombre visible, vida máx., daño, velocidad, resistencia al empuje, escala.
- **RF-MOB-02** Equipo (casco, pechera, pantalones, botas, mano principal, secundaria) con encantamientos y probabilidad de drop. Ranuras de armadura solo para tipos de `armor-capable-mobs` en config (por defecto: Zombie, Husk, Drowned, Zombie Villager, Skeleton, Stray, Wither Skeleton, Bogged, Parched, Piglin, Piglin Brute, Zombified Piglin; verificar en 26.3).
- **RF-MOB-03** Efectos de poción permanentes (tipo, nivel, partículas visibles sí/no).
- **RF-MOB-04** Habilidades y combos: lista de instancias, cada una con disparador, objetivo, parámetros y aviso visual.
- **RF-MOB-05** Jefe: BossBar propia y fases (RF-JEF).
- **RF-MOB-07** (T52) **Atributos ampliados, sin límite donde sea posible.** Editables en YAML y en la GUI (submenú **Atributos** del editor de mob, también para las estadísticas de las fases de jefe). Todos son opcionales: sin valor se usa el vanilla del tipo de entidad.
  - **Vida máx.: sin límite** (número finito > 0). Hasta 1024 (límite de Minecraft) es vida real. Por encima se usa **vida virtual**: la entidad tiene 1024 de vida real y el daño recibido y la curación se dividen por `vida/1024`, de modo que aguanta exactamente la vida configurada. BossBar, scoreboard, avisos y fases por porcentaje usan la vida virtual. El escalado por jugadores (RF-PAR) puede llevar la vida por encima de 1024 con el mismo mecanismo. `/kill` y el vacío siguen matando.
  - **Daño de ataque: sin límite** (finito ≥ 0). Hasta 2048 es el atributo de Minecraft; por encima el plugin fija el daño de cada golpe cuerpo a cuerpo del mob. No cambia el daño propio de las habilidades.
  - **Velocidad 0–1024** (límite de Minecraft; antes 0–1 del plugin), **resistencia al empuje 0–1** y **escala 0–16**: límites fijos de Minecraft.
  - **Nuevos, con el límite de Minecraft:** armadura 0–30, dureza de armadura 0–20, rango de detección 0–2048, empuje de ataque 0–5, fuerza de salto 0–32, gravedad −1–1, altura de paso 0–10 y resistencia al empuje de explosiones 0–1.
  - "Sin límite" significa hasta 10^30 (margen de seguridad frente a los cálculos en `float` de Minecraft, que darían NaN con valores mayores); la vida virtual nunca guarda valores no finitos.
  - La GUI muestra en cada campo "Sin límite" o "Rango: a–b · límite de Minecraft" (RF-GUI-06); la vida avisa "por encima de 1024 se usa vida virtual" y el daño "por encima de 2048 lo aplica el plugin". Los YAML antiguos cargan igual y los valores fuera de rango se recortan al cargar con aviso.
- **RF-MOB-06** Drops vanilla desactivados por defecto (configurable por plantilla). Puede marcarse como portador de llave (RF-PAR-07).

## 8. Habilidades (RF-HAB)
- **RF-HAB-01** Disparadores: `CADA_X_SEG`, `AL_GOLPEAR`, `AL_RECIBIR_DAÑO`, `VIDA_BAJO_%`, `AL_APARECER`, `AL_MORIR`, `JUGADOR_EN_RANGO`.
- **RF-HAB-02** Objetivos: objetivo actual, más cercano, aleatorio, todos en radio.
- **RF-HAB-03** Parámetros comunes: cooldown, probabilidad, rango, daño, radio, duración de aviso. Parámetros específicos por habilidad.
- **RF-HAB-04** Ninguna habilidad rompe bloques ni causa incendios. Bloques temporales solo en aire y siempre restaurados.
- **RF-HAB-05** Prestadas: Cráneos del Wither (normal/azul), Onda del Wither, Aliento del Dragón, Rugido del Dragón, Sonic Boom, Pulso de Oscuridad, Colmillos del Evoker (línea/círculo), Invocar Vexes, Ráfaga de Blaze, Bola de fuego de Ghast, Wind Charge, Salto de Breeze, Bala de Shulker, Fatiga del Elder Guardian, Rayo de Guardian (partículas), Explosión de Creeper, Pociones de Bruja, Teletransporte de Enderman, Rugido con empuje, Lanzar al aire, Telaraña, División al morir, Ceguera, Rayo (sin incendio).
- **RF-HAB-06** Genéricas: Efecto al golpear (cualquier poción), Flechas con efecto (cualquier poción).
- **RF-HAB-07** Propias: Ladrón, Desarme, Gancho, Vampirismo, Sanador, Enfurecer, Escudo de esbirros, Reflejo, Meteoritos, Terremoto, Congelar, Anclar, Caos, Intercambio, Último aliento, Doble.
- **RF-HAB-08** Ladrón: roba un ítem aleatorio de la hotbar; lo suelta al morir; si la partida termina sin recuperarlo, se devuelve al jugador (o por `claim` si está desconectado).
- **RF-HAB-09** Combos: secuencia ordenada de 2–5 habilidades con retardo entre pasos; comparte disparador, objetivo y cooldown. Si el mob muere, el combo se cancela.
- **RF-HAB-10** Añadir una habilidad nueva no requiere modificar la GUI (editor generado desde los parámetros declarados).

## 9. Fases de jefe (RF-JEF)
- **RF-JEF-01** Un jefe puede tener fases con umbral de vida (p. ej. 66 %, 33 %). Cada fase se activa una sola vez.
- **RF-JEF-02** Al entrar en fase puede: reemplazar o añadir habilidades y combos, cambiar equipo y pociones, curarse un %, invocar plantillas, mostrar un título en pantalla a los jugadores de la partida, reproducir un sonido y cambiar la música.
- **RF-JEF-03** Música: clave de sonido configurable (sonidos vanilla como discos de música o sonidos de resource pack), reproducida en bucle a los jugadores de la partida mientras el jefe vive; se detiene al morir el jefe o terminar la partida.
- **RF-JEF-04** Breve invulnerabilidad configurable durante la transición de fase (efecto visual).

## 9b. Núcleo de mobs (RF-MOB-08) — v1.2
- **RF-MOB-08** **Frontera del sistema de mobs.** Los paquetes `mob`, `ability`, `boss` e `intelligence` no importan nada de dungeons (`session`, `storage`, `reward`, `command`, `listener` ni los menús de dungeon); un test de frontera lo comprueba con una **lista de permitidos** (solo paquetes del sistema de mobs, `runtime`, los modelos y la configuración de mobs, `text`, Paper/Adventure/JDK), no con una lista de prohibidos. Dependen solo de dos contratos:
  - `MobHost` (uno por encuentro: partida de dungeon, prueba en vivo o jefe del mundo; sustituye a `SessionContext`): `id()`, `players()` (únicos objetivos válidos), `audience(Location)` (quién oye sonidos y ve efectos), `area()` (zona opcional con su mundo —`MobArea`: mundo + `BoundingBox`, con `contains(Location)`—, equivalente exacta a la antigua `currentRoomRegion()`), `mobs()`, `spawnMinion(...)`, `tempBlocks()`, `scheduler()`, `onItemStolen(...)`. Se amplía solo con métodos `default`.
  - `MobsPlatform` (uno por plugin): textos (`messages.yml`, mismas claves), límites de rendimiento, acceso a plantillas, espacio de nombres PDC (`customdungeons`) y entrega de recompensas.
  - **Sin cambios visibles:** mismos YAML, claves PDC `customdungeons:*`, base de datos, permisos, menús y mensajes; todos los tests siguen pasando.
  - **Extensión para ramas por servidor** (decisión 0002): `AbilityRegistry`, `BossRegistry` e `IntelligenceRules` permiten registrar habilidades, jefes y reglas escritos en código sin tocar el núcleo.

## 9c. Inteligencia (RF-IA) — v1.2
Los valores marcados *(ajustable)* son valores por defecto configurables. El nivel 0 reproduce exactamente el comportamiento actual.
- **RF-IA-01** **Nivel de inteligencia** por plantilla y por fase, elegido al crear o editar el mob. Cada nivel incluye todo lo del anterior:

  | Nivel | Comportamiento |
  |---|---|
  | 0 · Nula (por defecto) | Como hoy: habilidades por tiempo, vida, golpe; sin memoria ni adaptación. |
  | 1 · Consciente | Memoria de amenaza; elige objetivo por amenaza; reacción lenta a una sola cosa. |
  | 2 · Táctica | Disparadores y objetivos nuevos (RF-IA-03/04); responde a una estrategia repetida con la habilidad adecuada. |
  | 3 · Estratégica | Bloqueos temporales de estrategias abusadas (RF-IA-05); protege su punto débil (RF-IA-06); invoca esbirros cuando conviene. |
  | 4 · Adaptativa | Resistencia temporal al tipo de daño dominante; hasta 2 adaptaciones activas. |
  | 5 · Legendaria | Detecta antes, hasta 3 adaptaciones activas, recuerda lo usado durante todo el encuentro. |

  Los parámetros de cada nivel (ventanas, umbrales, duraciones, máximos) son *(ajustables)* por plantilla.
- **RF-IA-02** **Memoria por encuentro** (no es IA ni aprendizaje): por jugador cercano, daño hecho, críticos, curaciones y consumibles usados, armadura y vida, dirección y distancia del ataque, tipo de daño, arma. Vive en memoria mientras el mob existe; se borra al morir o desaparecer; nunca se guarda en disco ni entre encuentros.
- **RF-IA-03** **Disparadores nuevos**: atacado por la espalda, rodeado (N jugadores en radio), ráfaga de daño (X en Y s), ataque a distancia, jugador que se cura, jugador a punto de morir, estrategia detectada.
- **RF-IA-04** **Objetivos nuevos**: más amenaza, más débil (menos vida), más tanque, menos armadura, el que se curó, el arquero, el que está detrás, el más alejado.
- **RF-IA-05** **Adaptación**, sutil (sonido y partículas propios del mob y un aviso breve en la barra de acción; sin rueda ni referencias explícitas). Detecta y responde temporalmente a: manzanas doradas y pociones (recarga del ítem o heridas graves), tótems (enfurecimiento breve, nunca muerte), élitros y cohetes (derribo y recarga), críticos (resistencia temporal), mazo (lanzamiento del atacante o amortiguación), perlas de ender (recarga), escudo (inutilizado unos segundos) y daño dominante (resistencia temporal a ese tipo, niveles 4–5).
- **RF-IA-06** **Punto débil** por plantilla: espalda, cabeza o ninguno; daño extra configurable, calculado según la posición y dirección del golpe o el impacto del proyectil. Si el grupo lo abusa (nivel ≥ 3), el mob contraataca hacia ahí con cualquiera de sus habilidades.
- **RF-IA-07** **Brechas obligatorias** (que el mob no sea injusto): ventana de reacción (necesita un patrón repetido); variar de táctica reinicia la detección; olvido tras una duración; máximo de adaptaciones activas por nivel; recarga entre adaptaciones; coste mientras está adaptado (p. ej. más daño en el punto débil); contramedidas interrumpibles con daño durante su aviso; tope total de resistencia (nunca inmune); ningún bloqueo dura todo el encuentro.
- **RF-IA-08** Rendimiento: decisiones cada pocos ticks *(ajustable)* dentro de la tarea del anfitrión, contadores acotados, sin búsqueda de rutas propia (la IA de movimiento sigue siendo la de Minecraft).

## 9d. Habilidades nuevas (RF-HAB2) — v1.2
- **RF-HAB2-01** Agarrar y lanzar: atrapa a un jugador y lo arroja contra otro; daño a ambos al chocar.
- **RF-HAB2-02** Jaula de levitación: levita sin poder moverse y pierde vida; la rompen los compañeros dañando al mob.
- **RF-HAB2-03** Agarre que drena: roba vida; el jugador se suelta pulsando espacio N veces (`PlayerInputEvent`) o lo liberan sus compañeros.
- **RF-HAB2-04** Marca bomba: cuenta atrás sobre un jugador y daño en área a los cercanos; nunca letal desde vida llena.
- **RF-HAB2-05** Invocación táctica de esbirros (decidida por la inteligencia).
- **RF-HAB2-06** Catálogo adicional a elegir por el usuario (propuesta del 2026-10-07): vórtice, raíces, cadena de almas, gravedad invertida, suelo agrietado, barrido, pilares que caen, charcos de veneno, rayo cargado, embestida, lluvia de flechas, lanza que ancla, tótem del jefe, señuelos, ataque final interrumpible, purga, robo de mejoras, silencio, enlace de dolor, parpadeo a la espalda, grieta. Pendiente de selección; si son muchas, S3a y S3b.
- **RF-HAB2-07** Reglas comunes: aviso previo, duración máxima, forma de escape, modificaciones al jugador solo transitorias (nunca guardadas en disco), entidades y bloques temporales marcados y limpiados incluso tras una caída, presupuesto de partículas y entidades, parámetros con rango en la GUI.

## 9e. Jefes del mundo (RF-JEFES) — v1.2
- **RF-JEFES-01** Entrada **Jefes** en el menú principal de `/customdungeon`. Un jefe es una plantilla de mob (mismo editor, mismo YAML `mobs/<id>.yml`) con una sección aditiva `world-boss`; el menú Jefes lista esas plantillas y añade al editor el submenú **Jefe del mundo** (zona, límites, recompensas, aparecer ahora). Las plantillas de jefe siguen pudiendo usarse en oleadas de dungeon (allí se ignora `world-boss`).
- **RF-JEFES-02** **Zona de aparición:** mundo, X mín./máx. y Z mín./máx. (p. ej. −2000…2000), máximo de ejemplares vivos a la vez (por defecto 1), radio de encuentro (por defecto 48) y daño mínimo para recompensa (por defecto 5 % de la vida máxima). Al guardar se valida mín. < máx., mundo existente y rangos (RF-GUI-06).
- **RF-JEFES-03** **Aparición por comando o botón:** `/customdungeon boss spawn <jefe>` (jugador o consola) elige un punto aleatorio dentro de la zona y del borde del mundo, sobre el bloque sólido más alto. Descarta **agua** (incluidos bloques anegados y plantas acuáticas), lava, hojas y puntos sin hueco libre para la altura del mob (con su escala). Hasta 20 intentos *(ajustable)*; carga de chunks asíncrona; si no encuentra sitio o se alcanzó el máximo vivo, lo explica. Responde con las coordenadas. No hay aparición automática por temporizador.
- **RF-JEFES-04** `/customdungeon boss list` (jefes vivos y posición) y `/customdungeon boss despawn <jefe>` (retira todos sus ejemplares sin recompensa). Permiso `customdungeons.admin.boss` (incluido en `customdungeons.admin`).
- **RF-JEFES-05** **Encuentro en el mundo** (implementa `MobHost`): objetivos, BossBar, sonidos y efectos para los jugadores en supervivencia o aventura dentro del radio de encuentro. Si el jefe sale de su zona, vuelve a su punto de aparición. Si su chunk se descarga, se reinicia o se recarga el plugin, el jefe se retira limpio (sin recompensa, bloques temporales restaurados) y los restos marcados se eliminan al cargar sus chunks.
- **RF-JEFES-06** **Recompensas configurables en la GUI** (mismo `RewardDef` y menú que las dungeons: ítems, dinero, XP, comandos). Al morir el jefe por daño, cada jugador que le haya hecho al menos el daño mínimo recibe la recompensa; lo que no quepa en el inventario queda pendiente para `/customdungeon claim`. El daño se cuenta en memoria por encuentro (con vida virtual).
- **RF-JEFES-07** Jefes definidos en código (ramas por servidor) se registran en `BossRegistry` y aparecen en el menú y en los comandos junto a los creados en el juego.

## 10. GUI (RF-GUI)
- **RF-GUI-01** Menús de cofre con borde decorativo, ítems con lore explicativa (acción por clic izq./der./shift), barra inferior fija (volver, página anterior, guardar, página siguiente, cerrar), sonidos.
- **RF-GUI-02** Entrada de valores con la Dialog API de Paper (texto, números con slider); alternativa por clics (±1, ±10 con shift).
- **RF-GUI-03** Árbol: Dungeons → Dungeon (ajustes, escalado, ganchos, salas, premio, activar) → Sala (región, checkpoint, puerta, desbloqueo) → Spawner → Oleada → Entradas; Biblioteca de mobs → Mob (tipo, stats, equipo, encantamientos, pociones, habilidades, combos, fases).
- **RF-GUI-04** Edición sobre borrador; "Guardar" valida y persiste. Errores en rojo con descripción.
- **RF-GUI-05** Bloqueo: un editor por dungeon; no editable con partida en curso.
- **RF-GUI-06** Probar en vivo: desde el editor de un mob, botón que lo invoca junto al admin (sin dungeon) con sus habilidades, combos y fases activos. Se elimina con un botón, al salir del radio o tras un tiempo máximo. El admin puede alternar invulnerabilidad durante la prueba. **(T53)** El mob se comporta como en una dungeon: ataca a cualquier jugador cercano, incluido el admin, sin objetivo forzado. Sus objetivos válidos (cuerpo a cuerpo y habilidades por igual) son el admin y los jugadores en supervivencia o aventura dentro de `effect-view-radius` que no estén en una partida de dungeon; nunca otros jugadores. Sus sonidos (habilidades, fases, música) y efectos los perciben todos los jugadores cercanos al mob, no solo el admin; en las partidas, todos los jugadores de la sesión.

## 11. Herramientas (RF-HER)
- **RF-HER-01** Varita de región, herramienta de puerta, colocador de spawner, herramienta de puntos (lobby, checkpoint, salida). Marcadas con PDC; no se pueden soltar, guardar en contenedores ni duplicar.
- **RF-HER-02** Previsualización con partículas solo visibles para el admin que las usa.
- **RF-HER-03** El marcador de spawner es un `ItemDisplay` visible solo en modo edición; clic abre su editor.
- **RF-HER-04** `/customdungeon show <dungeon>` dibuja salas, puertas y spawners durante X segundos.

## 12. Comandos y permisos (RF-CMD)
Comando principal `/customdungeon`; alias configurables (por defecto ninguno).

| Comando | Permiso |
|---|---|
| `/customdungeon` (menú) | `customdungeons.admin.edit` |
| `tool <tipo>` | `customdungeons.admin.tools` |
| `test <dungeon>` (sin premio; saltar oleada, invulnerable) | `customdungeons.admin.test` |
| `start / stop / reset <dungeon>` | `customdungeons.admin.control` |
| `show <dungeon>` | `customdungeons.admin.edit` |
| `reload` | `customdungeons.admin.reload` |
| `debug` | `customdungeons.admin.debug` |
| `boss spawn <jefe>` / `boss list` / `boss despawn <jefe>` (v1.2, RF-JEFES) | `customdungeons.admin.boss` |
| `join <dungeon>` | `customdungeons.player.join` (+ `customdungeons.join.<id>` si la dungeon lo exige) |
| `join <jugador> <dungeon>` | consola o `customdungeons.admin.join.others` |
| `leave` / `stats` / `claim` | `customdungeons.player.*` |
| bypass | `customdungeons.bypass.cooldown`, `customdungeons.bypass.limit` |

`customdungeons.admin` agrupa todos los nodos de admin; `customdungeons.player` es `true` por defecto.

## 13. Integraciones (RF-INT)
- **RF-INT-01** Multiverse-Portals (5.3.0+): cada portal de entrada se configura con acción de comando que ejecuta `customdungeon join {player} <dungeon>`. El plugin decide si el jugador entra. La vuelta al mundo original la gestiona Multiverse-Portals. Verificar en pruebas si la acción se ejecuta como consola o como jugador y documentarlo en `docs/guides/`.
- **RF-INT-02** Ganchos de comandos por dungeon, ejecutados por consola con placeholders `{dungeon}`, `{players}`, `{max}`: `on-lobby-open`, `on-full`, `on-start`, `on-complete`, `on-fail`, `on-free`. Sirven para avisar o modificar portales de Multiverse-Portals u otros plugins.
- **RF-INT-03** WorldGuard protege el terreno de las dungeons (romper, colocar, explosiones, etc.). Los bloques que coloca el plugin (puertas, bloques temporales) se gestionan por API y no dependen de flags de WorldGuard. Guía de flags recomendados en `docs/guides/`.
- **RF-INT-04** Vault (opcional): premios en dinero. LuckPerms: solo vía nodos de permiso.

## 14. Configuración y mensajes (RF-CFG)
- **RF-CFG-01** `config.yml`: prefijo de chat (por defecto `&8[&6CustomDungeons&8] `), base de datos (`sqlite`|`mysql`, host, puerto, nombre, usuario, contraseña, pool), mundo de dungeons, valores por defecto de dungeon, límites de rendimiento (máx. mobs vivos por partida, densidad y radio de partículas), `armor-capable-mobs`, alias, sonidos de GUI, idioma.
- **RF-CFG-02** Colores en texto configurable: códigos `&` estilo Essentials, incluido hex `&#RRGGBB`. MiniMessage también aceptado.
- **RF-CFG-03** `messages.yml` con todos los textos (es, en). Ningún texto visible al jugador embebido en código.
- **RF-CFG-04** Definiciones en `dungeons/<id>.yml` y `mobs/<id>.yml`, editables a mano; `reload` las recarga si no hay partidas en curso.

## 15. Persistencia (RF-BD)
Tablas: partidas (id, dungeon, inicio, fin, resultado), jugadores de partida (kills, muertes, resultado, premio), cooldowns, premios pendientes (`claim`), estadísticas por jugador. Todo acceso asíncrono.

## 15b. Actualizador firmado (RF-UPD) — v1.0.2
- **RF-UPD-01** `/customdungeon update check`: consulta en segundo plano `https://api.github.com/repos/Dasannn/CustomDungeons/releases/latest` y compara su versión semántica con la del plugin; informa si está al día o qué versión hay (con enlace a las notas).
- **RF-UPD-02** `/customdungeon update`: muestra la versión disponible y pide `/customdungeon update confirm` en ≤ 60 s.
- **RF-UPD-03** `/customdungeon update confirm`: descarga en segundo plano el asset `CustomDungeons-<v>.jar` y su firma `CustomDungeons-<v>.jar.sig`; verifica la firma **Ed25519** (pura, sobre los bytes del jar) con la clave pública incrustada en el plugin; verifica que el `paper-plugin.yml` del jar tiene `name: CustomDungeons` y `version: <v>`; solo entonces lo escribe en la carpeta de actualización de Bukkit (`Bukkit.getUpdateFolderFile()`, se aplica en el siguiente reinicio). Cualquier fallo → nada se instala y se explica el motivo.
- **RF-UPD-04** Permiso `customdungeons.admin.update` (consola permitida). Nunca instala ni reinicia automáticamente. No permite bajar a versiones anteriores.
- **RF-UPD-05** `config.yml`: `updater.enabled` (true), `updater.check-on-startup` (true: aviso en consola si hay versión nueva), `updater.repository` (`Dasannn/CustomDungeons`).
- **RF-UPD-06** La clave privada vive solo en la máquina del mantenedor (`~/.config/customdungeons/release-signing.key`); la pública está en `docs/reference/release-signing.pub` e incrustada en el código. Cada release se firma con `scripts/sign-release.sh`.

## 15c. Asistente de creación y llaves (v1.1.0)
- **RF-ASI-01** `/customdungeon create <id>` y botón "Nueva dungeon (asistente)" abren un asistente por pasos: 1) Área total (varita; contorno + tamaño), 2) Lobby y salida ("Fijar aquí"), 3) Salas una a una (región dentro del área, checkpoint, puerta opcional en la última, desbloqueo automático o con llave), 4) Spawners por sala (herramienta + radio + oleadas, con plantillas rápidas de oleada), 5) Reglas (valores por defecto, saltable), 6) Premio, 7) Revisión final con errores del Validator, "Probar" y "Activar".
- **RF-ASI-02** Progreso visible: panel lateral (scoreboard) con los pasos y su estado, BossBar con el paso actual e instrucciones en chat. "Siguiente" solo se habilita si el paso es válido; "Atrás" permite volver.
- **RF-ASI-03** Partículas persistentes mientras el asistente está abierto: área, salas, puertas y spawners ya definidos (visibles solo para el admin), hasta salir del asistente.
- **RF-ASI-04** Borrador persistente: se puede salir y reanudar ("Continuar asistente"). El editor completo sigue disponible.
- **RF-ASI-05** `DungeonDef` gana un campo opcional `area` (Region); el Validator exige que salas, puertas, puntos y spawners estén dentro si existe.
- **RF-SPW-01** Plantillas de spawner: `SpawnerPreset(id, nombre, radio por defecto, oleadas)` guardadas en `spawners/<id>.yml`; Biblioteca de spawners en el menú principal (crear, editar, borrar con confirmación si está en uso).
- **RF-SPW-02** Un spawner de sala puede referenciar una plantilla (`presetId`) y usar sus oleadas; en el editor de sala se ve el origen y se ofrece: "Editar plantilla (afecta a todas las salas que la usan: N)" o "Hacer propio de esta sala" (copia local de las oleadas, se desvincula).
- **RF-SPW-03** `DungeonDef` gana una lista opcional `spawnerPresets` (plantillas elegidas para esa dungeon) que el asistente y el editor de sala ofrecen primero al colocar spawners.
- **RF-SPW-04** El Validator resuelve plantillas referenciadas (error si falta) y valida sus oleadas como las locales.
- **RF-ASI-01b** Orden del asistente: 1) Área, 2) Lobby y salida, 3) Spawners de la dungeon (crear o elegir plantillas), 4) Salas (colocar spawners de esas plantillas con la herramienta o en la posición), 5) Reglas, 6) Premio, 7) Revisión.
- **RF-LLA-01** La llave no se puede colocar como bloque nunca. Se usa con clic derecho (a bloque o al aire) estando a ≤ 4 bloques de la puerta de la sala actual; lejos, mensaje "acércate a la puerta".
- **RF-LLA-02** Portador de llave: opción "el último mob en morir de la sala" (por defecto en salas nuevas; valor `*` en keyCarrierTemplateId) o una plantilla concreta.

- **RF-INI-01** Modo de inicio por dungeon (Ajustes → Inicio): **Automático** (por defecto; con el mínimo de jugadores en el lobby corre la cuenta atrás `lobbyCountdownSeconds`) o **Placas de presión**: el admin coloca placas de piedra con una herramienta; cada placa es una plaza de jugador y el mínimo de jugadores = nº de placas (el campo "Jugadores mínimos" queda bloqueado y lo indica). Al colocar una placa se muestra "Placa N de M · mínimo M jugadores". Cuando todas las placas están pisadas por jugadores de la sesión corre una cuenta atrás corta (`plateCountdownSeconds`, por defecto 3) que se cancela si alguien se baja.
- **RF-INI-02** **Puerta de entrada** (`entranceDoor`, Region opcional, herramienta de puerta): se abre al empezar la partida y se restaura al terminar, como las demás puertas. Obligatoria en modo Placas; opcional en Automático.
- **RF-INI-03** **Inicio sin teletransporte** (`teleportOnStart`, por defecto falso en dungeons nuevas; las existentes conservan el comportamiento actual con `true`): al empezar no se teletransporta a nadie; se abre la puerta de entrada con título y sonido.
- **RF-INI-04** **Activación por entrada**: las oleadas de cada sala empiezan cuando el primer jugador de la sesión entra en la región de esa sala (no al abrir su puerta ni al empezar la partida). El tiempo límite de la partida corre desde el inicio.
- **RF-INI-05** **Salida al terminar**: `finishMode` admite **IMMEDIATE** (TP inmediato, predeterminado), **DELAYED** (TP tras `exitGraceSeconds`, 10–300 s, por defecto 60; BossBar y barra de acción «Saliendo en 0:45») y **NONE** (sin TP hasta que abandonen el área y todas las regiones; a los 300 s se fuerza el TP). Los jugadores salen de la sesión al completar o fallar; la dungeon permanece bloqueada para join, portal e inicio mientras quede algún jugador de la partida anterior dentro de su área o regiones («La dungeon se está vaciando, espera un momento», es/en). `finishDestination` admite **EXIT** (punto de salida, predeterminado; el Validator exige que quede fuera del área y de todas las regiones de salas, puertas y entrada, o bloquea guardar y activar) y **PREVIOUS** (posición y mundo guardados al unirse, persistentes ante caídas; si el mundo no existe, la posición no es segura o está dentro del área o regiones de la dungeon, se usa EXIT). En DELAYED, si todos salen físicamente antes del plazo (placas, TP propio, muerte o desconexión), la dungeon se libera al detectar que está vacía sin esperar ni volver a teletransportarlos. La recuperación limita cada consulta a BD y carga de chunk a 10 s: ante error o timeout registra un aviso y usa EXIT; si EXIT no está disponible, usa el spawn del mundo principal, sin mantener indefinidamente el estado de retorno. Agotar el tiempo límite falla la partida y fuerza siempre el TP al destino elegido, ignorando `finishMode`. Compatibilidad YAML: `teleportOnFinish`/`teleport-on-finish` verdadero → IMMEDIATE, falso → NONE; sin campo → IMMEDIATE. `exitPlates` es una lista opcional de placas de **POLISHED_BLACKSTONE_PRESSURE_PLATE**, dentro del área si existe: solo tras completar, quien pisa una sale inmediatamente al destino, de forma individual. Conviven con cualquier modo; los demás conservan la cuenta atrás de DELAYED o el bloqueo y la red de seguridad de NONE. La herramienta entrega placas de inicio (STONE_PRESSURE_PLATE) y salida, con marcadores distintos. El menú «Inicio y final» permite elegir modo, tiempo y destino en los slots 25/34/43.
- **RF-INI-06** **Cinemática de inicio** (`introCinematic`, por defecto desactivada): al pasar a EN_CURSO, se guarda con confirmación el estado previo antes de activar espectador temporal. La cámara recorre una órbita aérea alrededor del área (o la caja de salas, puertas, entrada y lobby), pasa por los centros de salas en orden y termina en la puerta de entrada o sala 0. Cada sala visitada recibe al menos 10 ticks de cámara; se reserva también un tramo final de al menos 10 ticks. Si no caben todas en la duración, se recorre una muestra uniforme que incluye siempre la primera y la última sala. Durante la cámara no se pueden abrir contenedores ni interactuar con bloques, entidades, inventarios o ítems. Teletransportes interpolados cada tick con yaw/pitch al objetivo, dentro de alturas y borde del mundo; solo API pública, sin NMS. Duración `introSeconds` de 5–20 s, calculada tras la preparación persistente; agacharse la salta solo para ese jugador. Título opcional por catálogo (`cinematic.title`, vacío para desactivar). Cada jugador recupera modo de juego, posición exacta previa (incluidos yaw/pitch), invulnerabilidad, vuelo permitido y vuelo activo antes de aplicar `teleportOnStart`. Las oleadas y los efectos de sala esperan a que termine el último recorrido; después se conserva la activación por entrada de T38. **Decisión del arquitecto:** el tiempo límite y la duración jugada comienzan al terminar la cinemática del grupo (incluidos saltos), nunca durante la preparación ni el recorrido. El scoreboard muestra el estado `intro` y su objetivo contextual.
  - Desconectarse mientras el grupo está en la cinemática (incluso después de saltarla individualmente) libera la plaza **sin penalización T44**; se restaura el estado temporal y se conserva el retorno sin muerte. Fin, salto, abandono, parada y apagado restauran; una recarga del plugin restaura por su cierre/reactivación. `/customdungeon reload` mantiene el rechazo de T16 mientras exista una partida activa.
  - Recuperación tras caída: respaldo atómico y forzado antes de mutar al jugador, marcador de generación en PDC vanilla; se restaura el estado temporal antes del retorno de recuperación de partida RF-PAR-17/T38. Los bytes previos se retienen tras restaurar hasta que un login real posterior cargue el marcador `restored` junto con el estado vanilla persistido; reactivar con jugadores online no confirma nada. La confirmación retira también las generaciones anteriores ya restauradas y superadas, conservando cualquier generación posterior aún sin confirmar. Los atributos se restauran inmediatamente, antes de resolver mundos o cargar chunks. Si falla la posición previa, se intenta la salida de la dungeon y después el spawn del mundo principal; si también fallan, la posición queda pendiente y se conserva el registro, sin expulsiones repetidas. Una restauración cancelada conserva al jugador bajo seguimiento: hasta 20 intentos, uno por tick dentro del ticker existente; después se fuerza el modo con API pública y se registra el error. Esta última mutación invalida únicamente la cancelación del cambio de modo de restauración del propio jugador. En abandono o cierre, se aplica inmediatamente este último recurso, sin depender de otro tick de la sesión. Si otro plugin sigue vetando el modo original, se intenta supervivencia temporal segura con los atributos guardados. **Nunca se marca restaurado sin verificar el estado final:** modo, invulnerabilidad y vuelo deben coincidir, y la posición debe estar en el mismo mundo a una distancia menor de 0,5 bloques del destino de restauración, con yaw y pitch a una diferencia máxima de 1° (yaw circular, incluyendo ángulos equivalentes separados por 360°); el booleano del teletransporte no basta. Un veto o redirección persistente mantiene reintentos cada 20 ticks mientras el jugador esté conectado, con un único aviso por generación. Cada intento recorre de nuevo los destinos original → salida → spawn principal (tras terminar la sesión, parte del destino final capturado para evitar volver al lobby), capturando el fallo de cada destino antes de probar el siguiente. Antes de cualquier teletransporte de la cadena de recuperación se precarga el chunk con `getChunkAtAsync`, incluso si ya figura cargado. Un registro a nivel de plugin conserva estos pendientes tras liberar la sesión y los atiende mediante los tickers existentes; al desconectar o apagar, el respaldo no verificado permanece pendiente para el siguiente login y nunca se borra por confirmación. Si vanilla conserva una generación activa aunque su journal figure restaurado, se vuelve a marcar pendiente antes de recuperarla. Todo callback asíncrono comprueba conexión, generación y contexto vigente antes de actuar: los retornos de introducción dejan de ser válidos al terminar la sesión y los reintentos posteriores usan su destino final de salida. **Limitación aceptada por el arquitecto:** un listener posterior que cancela TODOS los cambios de modo, incluido SURVIVAL, puede impedir salir de espectador; es imposible forzarlo con API pública. En ese caso el plugin nunca confirma la restauración, sigue reintentando cada 20 ticks mientras el jugador esté conectado y conserva el respaldo pendiente para el siguiente login; nunca lo borra sin verificación. Un respaldo ausente bloquea una nueva entrada y se registra como pérdida de restauración exacta; se aplica estado de seguridad (supervivencia, vulnerable y sin vuelo), conservando el marcador para intervención administrativa.
- **RF-LLA-03** **Llave por comando**: `/customdungeon key give <jugador> [dungeon]` (permiso `customdungeons.admin.key`, usable desde consola y bloques de comando) entrega la misma llave; abre la puerta cerrada de la sala actual del jugador a ≤ 4 bloques, solo durante una partida activa de esa dungeon (si se indica). Nuevo modo de apertura de sala **Llave externa (puzzle)**: la puerta no se abre al limpiar la sala y ningún mob suelta llave; solo la abre una llave entregada por comando.
- **RF-CON-01** **Modo construcción**: `/customdungeon build <dungeonId>` y botón en el editor. Guarda el inventario del admin (persistente ante caídas) y da una barra de herramientas: 1 varita de área, 2 varita de sala, 3 puerta de sala (sin Shift) / puerta de entrada (con Shift; selección independiente), 4 colocador de spawner, 5 placas de presión (clic: inicio STONE_PRESSURE_PLATE; Shift+clic: salida POLISHED_BLACKSTONE_PRESSURE_PLATE; clic derecho sobre una registrada: quitar, independientemente de Shift), 6 puntos (checkpoint, lobby, salida; shift cambia), 7 seleccionar sala activa, 8 deshacer, 9 abrir el menú de la dungeon. Partículas de todo lo configurado SIEMPRE activas mientras dura el modo; barra de acción con herramienta y contexto ("Sala 2 · placas 2/3", con nº de placas de inicio/mínimo y nº de salida). Deshacer revierte el punto y el bloque de la placa; solo se colocan placas nuevas sobre aire, y no se sobrescriben bloques cambiados después ni se cargan chunks de forma síncrona para deshacer. Salir (comando, botón o desconexión) restaura el inventario. Funciona también con una dungeon nueva aún no guardada (la versión base del borrador es la del editor). Si la entrada falla por cualquier motivo, el admin recibe un mensaje, no se le retira nada y su inventario queda intacto (nunca un fallo silencioso).
- **RF-CON-02** Los cambios del modo construcción van a un **borrador persistente** por dungeon y admin; al volver a `/customdungeon build <id>` se reanuda mostrando el estado (partículas incluidas). Guardar valida con el Validator como el editor y vuelve a comprobar conflictos justo antes de publicar. El borrador de una dungeon cuya publicación se eliminó no la recrea: se rechaza con aviso de conflicto.

- **RF-SCB-01** Scoreboard lateral para jugadores en sesión (lobby, partida, final), plantillas predeterminadas de ≤ 10 filas contando título y pie, configurable en `config.yml` (`scoreboard.enabled`, `title`, `footer`, `lines` por estado) con colores `&`/`&#rrggbb`/MiniMessage y placeholders ({dungeon}, {room}, {rooms}, {wave}, {waves}, {mobs_left}, {kills}, {kills_total}, {time_left}, {lives}, {alive}, {players}, {countdown}, {plates}, {boss_phase}, {boss_health}, {objective}, {max_players}, {min_players}, {plates_total}, {boss_phases}, {time_total}, {finish_countdown}). Las líneas cuyo dato no aplica se ocultan. Línea **Objetivo** contextual (pisa las placas, limpia la sala, recoge/usa la llave, resuelve el puzzle, derrota al jefe, sal por la placa de salida / saliendo en… según el final de T38). Tiempo en rojo con < 1 min; hasta diez corazones llenos/vacíos; con más vidas iniciales se muestra «❤ ×N» (vidas restantes). Las plantillas se validan suponiendo que todas sus condiciones pueden coincidir, con un máximo de 15 entradas (incluido pie, título aparte). Como salvaguarda, un exceso en runtime se recorta a 15 con un único aviso por plantilla; los errores del panel nunca abortan join ni la partida. Refresco ≤ 1/s: primero comprobar intervalo, después comparar datos simples; crear componentes solo si cambian (incluida recarga de mensajes). El cambio de mundo mantiene el panel si continúa la sesión; si ya salió, restaura el scoreboard previo, también al abandonar, desconectar o finalizar.
- **RF-AMB-01** Ambiente: valores por defecto en `config.yml` y personalización por sala (sección "Ambiente" del editor de sala). Al abrir una puerta: sonido, partículas a lo largo de la puerta, título opcional y temblor opcional. Al entrar en una sala: título/subtítulo con su nombre, sonido de entrada, música de sala (se detiene al salir o completarla), efectos de poción para los jugadores mientras estén dentro (sin pisar efectos propios; al salir solo se quitan los aplicados por la dungeon) y partículas ambientales acotadas. Al limpiar una sala: sonido y título de victoria.

- **RF-DESC-01** Desconexión voluntaria durante una partida en curso (`disconnectMode`, Ajustes → Vidas e inventario): el jugador sale de la sesión al instante (abandono; libera su plaza; el grupo continúa) y se guarda de forma persistente su posición en la dungeon. Modo **Morir y soltar ítems** (por defecto): al reconectar muere en esa posición (sus ítems caen allí si `keepInventory` es falso; si la partida ya terminó caen igualmente y se pierden con la limpieza) y se sustituye siempre su destino vanilla siguiendo esta cadena: **cama/ancla válida fuera del área y las regiones de cualquier dungeon → salida exterior de su dungeon (o una salida exterior disponible) → spawn seguro de `respawn-world` → spawn del primer mundo cargado**. `respawn-world` vacío o inexistente usa el primer mundo cargado; un nombre inexistente genera aviso. Un spawn seguro tiene suelo sólido y dos bloques transitables libres (pies y cabeza), sin lava, fuego, agua ni otros bloques dañinos; está entre las alturas válidas, dentro del borde del mundo y fuera del mundo dedicado de dungeons y de las áreas/regiones de cualquier dungeon. Se precargan los chunks con `getChunkAtAsync` antes de inspeccionar el terreno: columna del spawn (posición original y superficie `getHighestBlockYAt + 1`) y espiral acotada de radio 16. Si no existe candidato seguro, se conserva como último recurso `World#getSpawnLocation` del primer mundo cargado ajustado al bloque más alto (altura limitada al mundo), con aviso; esta configuración imposible no produce `null` ni lanza desde el listener, aunque ese último recurso no puede garantizar seguridad ni estar fuera de las dungeons. Modo **Volver a la salida**: comportamiento anterior (TP a la salida al reconectar, sin morir). Una caída o apagado del servidor NO se trata como desconexión voluntaria: aplica la recuperación tras caída (§ crash) sin penalización. Causas (`PlayerQuitEvent.QuitReason`): `DISCONNECTED` y `TIMED_OUT` se penalizan (evita escapar cortando la conexión); `KICKED` (kick administrativo) y `ERRONEOUS_STATE` aplican "Volver a la salida" sin penalización. La penalización solo se considera aplicada cuando la muerte y el inventario resultante están persistidos; hasta entonces el registro pendiente se conserva y el jugador no puede entrar en partidas ni en el modo construcción. También se aplica durante el lobby, salvo la cinemática de inicio RF-INI-06: ninguna desconexión durante ella provoca muerte ni drop. `DIE_AND_DROP` es el modo por defecto tanto en dungeons nuevas como en YAML sin `disconnect-mode`; `RETURN_TO_EXIT` conserva el retorno sin muerte. La confirmación usa la generación cargada del PDC vanilla al comienzo de un login real posterior, antes de cualquier consulta o carga asíncrona; reactivar el plugin con jugadores online no confirma persistencia.

- **RF-GUI-06** Toda entrada numérica de la GUI (stats de mob, escala, daño, velocidad, empuje, golpes/cooldowns, tiempos, cantidades, radios, porcentajes, parámetros de habilidad…) muestra en su lore el **rango permitido** y su origen: "Rango: 0–16 · límite de Minecraft" (atributo vanilla) o "Rango: 1–300 · límite del plugin". Los rangos salen de una única fuente por campo (la misma que usa el Validator), nunca duplicados en textos. Escala de mob: 0–16 (límite del atributo `scale` de Minecraft; 0 = vanilla); por encima de 10 se avisa "necesita salas muy altas; la IA puede fallar". **Compatibilidad**: al cargar YAML, un valor numérico fuera de rango se recorta al límite con aviso en consola y en el editor, sin reescribir el archivo ni desactivar la dungeon o el mob (como la vida > 1024 de T37); solo un valor no numérico o imposible es error. Al guardar desde la GUI, un valor fuera de rango es error. La misma regla aplica a entradas no válidas de equipo al cargar (p. ej. ítems reservados del plugin como herramientas o llaves en una ranura): se ignora esa ranura con aviso y la plantilla sigue activa.

## 16. Requisitos no funcionales (RNF)
- **RNF-01** Paper 26.3, Java 25, solo API pública (ver constitución §1).
- **RNF-02** ≤ 2 ms MSPT por partida activa con ~50 mobs con habilidades (medido con spark).
- **RNF-03** Sin E/S en el hilo principal.
- **RNF-04** El plugin arranca y funciona sin Vault, LuckPerms, WorldGuard ni Multiverse.

## 17. Criterios de aceptación del MVP
1. Un admin crea desde la GUI una dungeon de 3 salas (una con llave) con spawners, oleadas mixtas y un jefe con 2 fases, música y un combo, sin editar archivos.
2. Un jugador entra por un portal de Multiverse-Portals; con la dungeon llena o en curso, el portal le muestra el motivo y no entra.
3. Un grupo de 2+ jugadores completa la dungeon; solo los supervivientes reciben el premio.
4. No es posible pasar una puerta cerrada ni sacar mobs de su sala.
5. Matar el servidor a mitad de partida y arrancarlo deja la dungeon limpia y jugable.
6. Todas las habilidades de §8 funcionan en `test` y en "probar en vivo" sin romper bloques.
7. Con escalado activo, 4 jugadores enfrentan más mobs y con más vida que 1, sin superar el máximo de mobs vivos.
8. Prueba de carga cumple RNF-02.
