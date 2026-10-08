# 0001 — Frontera del sistema de mobs

Fecha: 2026-10-08. Estado: **revisada el mismo día** (sustituye a la versión inicial «módulo Gradle `mobs-core`»).

## Contexto
El sistema de mobs (plantillas, atributos, habilidades, jefes) crece con inteligencia, habilidades nuevas y jefes del mundo. Hoy los archivos de `mob` y `ability` dependen de 4 clases de `runtime` (`ActiveMob`, `SessionContext`, `TempBlocks`, `TickScheduler`); `SessionContext` ya es una interfaz pequeña. Se planteó extraerlo a un módulo para un futuro plugin aparte de jefes del mundo. El usuario aclaró después que **ese plugin aparte se descarta**: los jefes y mobs del mundo vivirán en CustomDungeons, y lo que cambiará en las ramas por servidor (decisión 0002) serán jefes programados en código.

## Opciones
- A) Módulo Gradle `mobs-core`: frontera garantizada por el compilador, pero diff enorme (mover todo, multimódulo, espacio de nombres PDC) sin beneficio visible ahora que no hay otro plugin.
- **B) Paquetes con frontera vigilada por test** — elegida.
- C) Sin frontera: el acoplamiento crecería con S2/S3 y los jefes del mundo.

## Decisión
`mob`, `ability`, `boss` e `intelligence` siguen en el plugin, sin importar nada de dungeons (RF-MOB-08). Dependen de dos contratos: `MobHost` (por encuentro; sustituye a `SessionContext`) y `MobsPlatform` (por plugin). Un test de frontera falla ante un import prohibido. Puntos de extensión por registro (`AbilityRegistry`, `BossRegistry`, `IntelligenceRules`) para las ramas por servidor.

## Consecuencias
- T54 es una refactorización pequeña y sin cambios visibles (mismos YAML, claves PDC, BD, permisos, menús y mensajes).
- Si algún día se retoma un plugin aparte, se copian esos paquetes y se implementan los dos contratos.
