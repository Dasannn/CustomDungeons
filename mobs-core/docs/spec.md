# Spec — mobs-core

Sistema de mobs reutilizable: plantillas, atributos, habilidades, inteligencia y jefes. Lo usa CustomDungeons y lo heredará un futuro plugin de jefes y mobs del mundo, que copiará este módulo (o lo usará como dependencia) sin tocar nada de dungeons.

Estado: **diseño aprobado en conversación (2026-10-08); diseño detallado de S1 pendiente de revisión**. Los valores numéricos marcados *(ajustable)* son valores por defecto configurables, no contratos.

Rige `docs/constitution.md` del repositorio. Principios propios del módulo:
- **Independiente:** `mobs-core` no depende de ningún paquete de dungeons. El anfitrión (CustomDungeons u otro plugin) se conecta por una interfaz.
- **Optimizado y escalable:** ninguna tarea por mob, jugador ni habilidad; todo se evalúa en la tarea que aporta el anfitrión. Coste proporcional a los jugadores cerca de cada mob, no al tamaño del servidor.
- **Justo:** nada mata de un golpe; todo control tiene aviso, duración máxima y forma de escapar; toda adaptación tiene contrapartidas.
- **Compatible:** el nivel de inteligencia 0 es el valor por defecto y reproduce exactamente el comportamiento actual; los YAML existentes cargan sin cambios.
- **Configurable:** todo se ajusta en la GUI y en YAML, con rangos visibles (RF-GUI-06 de CustomDungeons).

## 1. Núcleo (S1, RF-MC)
- **RF-MC-01** Módulo Gradle `mobs-core` dentro de este repositorio, sin dependencias de dungeons (el compilador lo garantiza). Contiene: modelo y codec YAML de plantillas de mob, fases y habilidades; atributos y vida virtual (RF-MOB-07); habilidades y su motor; telegraph y efectos; creación de entidades; jefes (BossBar, fases, música); prueba en vivo; inteligencia (§2); menús de plantillas de mob y jefes.
- **RF-MC-02** **Contrato de anfitrión**: una interfaz pequeña por la que el anfitrión aporta lo que hoy da la partida (jugadores implicados, tarea compartida, bloques temporales, mundo, limpieza, quién oye sonidos y efectos). CustomDungeons la implementa con sus sesiones; la prueba en vivo y el futuro plugin aportan la suya.
- **RF-MC-03** Migración sin cambios visibles: mismos YAML, mismos permisos, mismos menús y mensajes; todos los tests actuales siguen pasando.
- **RF-MC-04** Guía de integración en el módulo (`mobs-core/docs/`): qué copiar, cómo implementar el contrato de anfitrión, registro de habilidades y reglas, y puntos de extensión para ramas por servidor.
- **RF-MC-05** Los requisitos RF-MOB-*, RF-HAB-* y RF-JEF-* de `docs/spec.md` se trasladan a este documento al construir S1.

## 2. Inteligencia (S2, RF-IA)
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

## 3. Habilidades nuevas (S3, RF-HAB2)
- **RF-HAB2-01** Agarrar y lanzar: atrapa a un jugador y lo arroja contra otro; daño a ambos al chocar.
- **RF-HAB2-02** Jaula de levitación: levita sin poder moverse y pierde vida; la rompen los compañeros dañando al mob.
- **RF-HAB2-03** Agarre que drena: roba vida; el jugador se suelta pulsando espacio N veces (`PlayerInputEvent`) o lo liberan sus compañeros.
- **RF-HAB2-04** Marca bomba: cuenta atrás sobre un jugador y daño en área a los cercanos; nunca letal desde vida llena.
- **RF-HAB2-05** Invocación táctica de esbirros (decidida por la inteligencia).
- **RF-HAB2-06** Catálogo adicional a elegir por el usuario (propuesta del 2026-10-07): vórtice, raíces, cadena de almas, gravedad invertida, suelo agrietado, barrido, pilares que caen, charcos de veneno, rayo cargado, embestida, lluvia de flechas, lanza que ancla, tótem del jefe, señuelos, ataque final interrumpible, purga, robo de mejoras, silencio, enlace de dolor, parpadeo a la espalda, grieta. Pendiente de selección; si son muchas, S3a y S3b.
- **RF-HAB2-07** Reglas comunes: aviso previo, duración máxima, forma de escape, modificaciones al jugador solo transitorias (nunca guardadas en disco), entidades y bloques temporales marcados y limpiados incluso tras una caída, presupuesto de partículas y entidades, parámetros con rango en la GUI.

## 4. Jefes (RF-JEFES)
- **RF-JEFES-01** Entrada **Jefes** en el menú principal de `/customdungeon`, junto a la biblioteca de mobs. Usa el mismo sistema de plantillas: un jefe es una plantilla con BossBar, fases, inteligencia, punto débil y habilidades, con esas secciones a la vista. Vive en `mobs-core`.
