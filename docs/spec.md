# Especificación — CustomDungeons (MVP v1.0)

> Documento sujeto a revisión del equipo. Requisitos con ID para trazabilidad en plan y tareas.

## 1. Resumen
Plugin para Paper 26.3 (Java 25) que permite a administradores crear, mediante GUI y herramientas, dungeons de varias salas en un mundo dedicado. Cada sala tiene spawners con oleadas de mobs personalizados (equipo, pociones y habilidades especiales). Al completar todas las salas, los participantes reciben un premio configurado.

## 2. Glosario
| Término | Definición |
|---|---|
| Dungeon | Conjunto ordenado de salas en el mundo de dungeons, con lobby, salida, reglas y premio. |
| Sala | Región cúbica con checkpoint, puerta y uno o más spawners. |
| Puerta | Bloques que bloquean el paso a la siguiente sala hasta limpiar la actual. |
| Spawner | Punto con radio de aparición y lista ordenada de oleadas. |
| Oleada | Conjunto de entradas (plantilla × cantidad + retardo) con un modo de aparición. |
| Plantilla de mob | Definición reutilizable de un mob: tipo, stats, equipo, pociones, habilidades. |
| Partida | Ejecución de una dungeon por un grupo de jugadores. |

## 3. Alcance
**Incluido (MVP):** todo lo descrito en las secciones 4–12.

**Excluido (fases futuras):** generación de portales con schematics (aleatoria o forzada), límite de portales por mundo, destino configurable en otra dimensión, exclusión de portales cerca de towns (AdvancedTowny), portales en el aire, instancias por grupo, sistema de party, PlaceholderAPI.
El diseño debe permitir que la fase de portales reutilice la entrada al lobby (RF-PAR-01) sin cambios estructurales.

## 4. Mundo y estructura (RF-MUN)
- **RF-MUN-01** Las dungeons residen en un mundo dedicado configurable (`dungeon-world.name`). Si `dungeon-world.auto-create: true` y no existe, se crea vacío (void).
- **RF-MUN-02** Una dungeon tiene: id, nombre visible, punto de lobby, punto de salida (mundo cualquiera), jugadores mín./máx., cuenta atrás del lobby, vidas por jugador, keepInventory, tiempo límite opcional, cooldown por jugador, premio, salas ordenadas, estado activado/desactivado.
- **RF-MUN-03** Una sala tiene: región cúbica, checkpoint, puerta (región de bloques, opcional en la última sala), ≥ 1 spawner.
- **RF-MUN-04** Un spawner tiene: ubicación, radio de aparición, oleadas ordenadas.
- **RF-MUN-05** Una oleada tiene: entradas (plantilla, cantidad, retardo desde el inicio de la oleada), modo `SIMULTANEO | SECUENCIAL | ESCALONADO(intervalo) | ALEATORIO`, pausa antes de la siguiente oleada.

## 5. Partida (RF-PAR)
- **RF-PAR-01** Entrada: `/customdungeon join <dungeon>` teletransporta al lobby. Es la única vía de entrada y será reutilizada por los portales futuros.
- **RF-PAR-02** Una sola partida activa por dungeon. Si está en curso, se informa "dungeon ocupada".
- **RF-PAR-03** El primer jugador abre el lobby con cuenta atrás configurable; entran jugadores hasta el máximo. Al terminar la cuenta atrás, si hay ≥ mínimo, empieza; si no, se cancela y se devuelve a todos.
- **RF-PAR-04** Iniciada la partida no se admiten nuevos jugadores.
- **RF-PAR-05** Estados: `LIBRE → LOBBY → EN_CURSO(sala, oleada) → COMPLETADA | FALLIDA → RESETEO → LIBRE`.
- **RF-PAR-06** Una sala se limpia cuando han muerto todos los mobs de todas las oleadas de todos sus spawners. Entonces se abre su puerta y el checkpoint pasa a la siguiente sala.
- **RF-PAR-07** Los jugadores no pueden salir de la sala actual ni saltarse puertas (incluye ender pearl, chorus y teletransportes no autorizados).
- **RF-PAR-08** Los mobs de la partida no pueden salir de su sala; si salen, se devuelven al spawner.
- **RF-PAR-09** Muerte: el jugador reaparece en el checkpoint de la sala actual y pierde una vida. Sin vidas queda eliminado y vuelve al punto de salida. Inventario conservado al morir si `keepInventory` (por defecto true).
- **RF-PAR-10** La partida falla si todos los jugadores son eliminados, abandonan (`/customdungeon leave` o desconexión) o se agota el tiempo límite.
- **RF-PAR-11** Desconexión: cuenta como abandono; al reconectar el jugador aparece en el punto de salida.
- **RF-PAR-12** Durante la partida los jugadores no pueden romper ni colocar bloques en el mundo de dungeons (salvo `customdungeons.bypass.build`).
- **RF-PAR-13** BossBar por partida: sala actual, oleada y mobs restantes. Mobs marcados como jefe muestran BossBar propia.
- **RF-PAR-14** Cooldown por jugador y dungeon tras completar (configurable; bypass por permiso).
- **RF-PAR-15** Recuperación: al arrancar el servidor, toda partida no terminada se marca como abortada, se eliminan sus mobs, se restauran bloques temporales y puertas, y los jugadores afectados aparecen en la salida al conectarse.

## 6. Premios (RF-PRE)
- **RF-PRE-01** Componentes: ítems (arrastrados en la GUI, con NBT completo), dinero (Vault), XP, comandos de consola con `{player}`.
- **RF-PRE-02** Se entregan al completar la última sala a todo jugador que participó.
- **RF-PRE-03** Reparto por participación: quien completa recibe 100 %; un eliminado o que abandonó recibe el % configurado (por defecto 0 %). El % aplica a dinero y XP; ítems y comandos solo con 100 %.
- **RF-PRE-04** Si el inventario está lleno, los ítems sobrantes se guardan y se entregan con `/customdungeon claim` (persistente en BD).
- **RF-PRE-05** Sin Vault, el dinero se ignora con aviso en consola.

## 7. Plantillas de mob (RF-MOB)
- **RF-MOB-01** Tipo de entidad (cualquier mob vivo), nombre visible, vida máx., daño, velocidad, resistencia al empuje, escala.
- **RF-MOB-02** Equipo (casco, pechera, pantalones, botas, mano principal, secundaria) con encantamientos y probabilidad de drop. Ranuras de armadura solo para tipos de `armor-capable-mobs` en config (por defecto: Zombie, Husk, Drowned, Zombie Villager, Skeleton, Stray, Wither Skeleton, Bogged, Piglin, Piglin Brute, Zombified Piglin; verificar en 26.3).
- **RF-MOB-03** Efectos de poción permanentes (tipo, nivel, partículas visibles sí/no).
- **RF-MOB-04** Habilidades: lista de instancias con disparador, objetivo, parámetros y aviso visual.
- **RF-MOB-05** Marca de jefe: BossBar propia.
- **RF-MOB-06** Drops vanilla desactivados por defecto (configurable por plantilla).

## 8. Habilidades (RF-HAB)
- **RF-HAB-01** Disparadores: `CADA_X_SEG`, `AL_GOLPEAR`, `AL_RECIBIR_DAÑO`, `VIDA_BAJO_%`, `AL_APARECER`, `AL_MORIR`, `JUGADOR_EN_RANGO`.
- **RF-HAB-02** Objetivos: objetivo actual, más cercano, aleatorio, todos en radio.
- **RF-HAB-03** Parámetros comunes: cooldown, probabilidad, rango, daño, radio, duración de aviso. Parámetros específicos por habilidad.
- **RF-HAB-04** Ninguna habilidad rompe bloques ni causa incendios. Bloques temporales solo en aire y siempre restaurados.
- **RF-HAB-05** Habilidades prestadas: Cráneos del Wither (normal/azul), Onda del Wither, Aliento del Dragón, Rugido del Dragón, Sonic Boom, Pulso de Oscuridad, Colmillos del Evoker (línea/círculo), Invocar Vexes, Ráfaga de Blaze, Bola de fuego de Ghast, Wind Charge, Salto de Breeze, Bala de Shulker, Fatiga del Elder Guardian, Rayo de Guardian (partículas), Explosión de Creeper, Pociones de Bruja, Teletransporte de Enderman, Rugido con empuje, Lanzar al aire, Telaraña, División al morir, Ceguera, Rayo (sin incendio).
- **RF-HAB-06** Habilidades genéricas: Efecto al golpear (cualquier poción), Flechas con efecto (cualquier poción).
- **RF-HAB-07** Habilidades propias: Ladrón, Desarme, Gancho, Vampirismo, Sanador, Enfurecer, Escudo de esbirros, Reflejo, Meteoritos, Terremoto, Congelar, Anclar, Caos, Intercambio, Último aliento, Doble.
- **RF-HAB-08** Ladrón: roba un ítem aleatorio de la hotbar; lo suelta al morir; si la partida termina sin recuperarlo, se devuelve al jugador (o por `claim` si está desconectado).
- **RF-HAB-09** Añadir una habilidad nueva no requiere modificar la GUI (editor generado desde los parámetros declarados).

## 9. GUI (RF-GUI)
- **RF-GUI-01** Menús de cofre con borde decorativo, ítems con lore explicativa (acción por clic izq./der./shift), barra inferior fija (volver, página anterior, guardar, página siguiente, cerrar), sonidos.
- **RF-GUI-02** Entrada de valores con la Dialog API de Paper (texto, números con slider); alternativa por clics (±1, ±10 con shift).
- **RF-GUI-03** Árbol: Dungeons → Dungeon (ajustes, salas, premio, activar) → Sala → Spawner → Oleada → Entradas; Biblioteca de mobs → Mob (tipo, stats, equipo, encantamientos, pociones, habilidades).
- **RF-GUI-04** Edición sobre borrador; "Guardar" valida y persiste. Errores en rojo con descripción.
- **RF-GUI-05** Bloqueo: un editor por dungeon; no editable con partida en curso.

## 10. Herramientas (RF-HER)
- **RF-HER-01** Varita de región, herramienta de puerta, colocador de spawner, herramienta de puntos (lobby, checkpoint, salida). Marcadas con PDC; no se pueden soltar, guardar en contenedores ni duplicar.
- **RF-HER-02** Previsualización con partículas solo visibles para el admin que las usa.
- **RF-HER-03** El marcador de spawner es un `ItemDisplay` visible; clic abre su editor.
- **RF-HER-04** `/customdungeon show <dungeon>` dibuja salas, puertas y spawners durante X segundos.

## 11. Comandos y permisos (RF-CMD)
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
| `leave` / `stats` / `claim` | `customdungeons.player.*` |
| bypass | `customdungeons.bypass.cooldown`, `.bypass.maxplayers`, `.bypass.build` |

`customdungeons.admin` agrupa todos los nodos de admin; `customdungeons.player` es `true` por defecto.

## 12. Configuración y mensajes (RF-CFG)
- **RF-CFG-01** `config.yml`: prefijo de chat (por defecto `&8[&6CustomDungeons&8] `), base de datos (`sqlite`|`mysql`, host, puerto, nombre, usuario, contraseña, pool), mundo de dungeons, valores por defecto de dungeon, límites de rendimiento (máx. mobs vivos por partida, densidad y radio de partículas), `armor-capable-mobs`, alias, sonidos de GUI, idioma.
- **RF-CFG-02** Colores en texto configurable: códigos `&` estilo Essentials, incluido hex `&#RRGGBB`. MiniMessage también aceptado.
- **RF-CFG-03** `messages.yml` con todos los textos (es, en). Ningún texto visible al jugador embebido en código.
- **RF-CFG-04** Definiciones en `dungeons/<id>.yml` y `mobs/<id>.yml`, editables a mano; `reload` las recarga si no hay partidas en curso.

## 13. Persistencia (RF-BD)
Tablas: partidas (id, dungeon, inicio, fin, resultado), jugadores de partida (kills, muertes, resultado, premio), cooldowns, premios pendientes (`claim`), estadísticas por jugador. Todo acceso asíncrono.

## 14. Requisitos no funcionales (RNF)
- **RNF-01** Paper 26.3, Java 25, solo API pública (ver constitución §1).
- **RNF-02** ≤ 2 ms MSPT por partida activa con ~50 mobs con habilidades (medido con spark).
- **RNF-03** Sin E/S en el hilo principal.
- **RNF-04** El plugin arranca y funciona sin Vault ni LuckPerms (permisos vía Bukkit).

## 15. Criterios de aceptación del MVP
1. Un admin crea desde la GUI una dungeon de 3 salas con spawners, oleadas mixtas y un jefe con 3 habilidades, sin editar archivos.
2. Un grupo de 2+ jugadores completa la dungeon y recibe el premio; un eliminado recibe el % configurado.
3. No es posible saltarse una puerta ni sacar mobs de su sala.
4. Matar el servidor a mitad de partida y arrancarlo deja la dungeon limpia y jugable.
5. Todas las habilidades de §8 funcionan en `test` sin romper bloques ni perder ítems.
6. Prueba de carga cumple RNF-02.
