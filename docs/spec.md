# Especificación — CustomDungeons (MVP v1.0)

> Documento sujeto a revisión del equipo. Requisitos con ID para trazabilidad en plan y tareas.

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
- **RF-MUN-02** Una dungeon tiene: id, nombre visible, punto de lobby, punto de salida, jugadores mín., límite de jugadores (RF-PAR-03), cuenta atrás del lobby, vidas por jugador (por defecto 3), conservar inventario al morir (por defecto **no**), tiempo límite opcional, cooldown por jugador, escalado de dificultad (RF-PAR-16), ganchos de comandos (RF-INT-02), premio, salas ordenadas, estado activado/desactivado.
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
- **RF-PAR-13** Desconexión: cuenta como abandono; al reconectar el jugador aparece en el punto de salida.
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

## 10. GUI (RF-GUI)
- **RF-GUI-01** Menús de cofre con borde decorativo, ítems con lore explicativa (acción por clic izq./der./shift), barra inferior fija (volver, página anterior, guardar, página siguiente, cerrar), sonidos.
- **RF-GUI-02** Entrada de valores con la Dialog API de Paper (texto, números con slider); alternativa por clics (±1, ±10 con shift).
- **RF-GUI-03** Árbol: Dungeons → Dungeon (ajustes, escalado, ganchos, salas, premio, activar) → Sala (región, checkpoint, puerta, desbloqueo) → Spawner → Oleada → Entradas; Biblioteca de mobs → Mob (tipo, stats, equipo, encantamientos, pociones, habilidades, combos, fases).
- **RF-GUI-04** Edición sobre borrador; "Guardar" valida y persiste. Errores en rojo con descripción.
- **RF-GUI-05** Bloqueo: un editor por dungeon; no editable con partida en curso.
- **RF-GUI-06** Probar en vivo: desde el editor de un mob, botón que lo invoca junto al admin (sin dungeon) con sus habilidades, combos y fases activos. Se elimina con un botón, al salir del radio o tras un tiempo máximo. El admin puede alternar invulnerabilidad durante la prueba.

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
