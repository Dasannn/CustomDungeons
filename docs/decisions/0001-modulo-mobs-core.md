# 0001 — Sistema de mobs en un módulo independiente (`mobs-core`)

Fecha: 2026-10-08. Estado: aceptada por el usuario.

## Contexto
El sistema de mobs (plantillas, atributos, habilidades, jefes) se reutilizará en un futuro plugin de jefes y mobs del mundo. Hoy 15 de los ~70 archivos de `mob` y `ability` dependen de `runtime` y de las sesiones de dungeon, así que copiarlo arrastraría código de dungeons. Además se va a añadir una capa de inteligencia y habilidades nuevas que, sin frontera, aumentaría ese acoplamiento.

## Opciones
- **A) Módulo Gradle `mobs-core` en este repositorio** — elegida.
- B) Paquete aislado en el mismo módulo con guía de copia: más barato, pero nada impide volver a acoplarlo.
- C) Librería en repositorio aparte publicada como jar: más limpia a largo plazo, pero dos repositorios y versiones que coordinar ahora.

## Decisión
`mobs-core` es un subproyecto Gradle sin dependencias de dungeons; el compilador impide el acoplamiento. CustomDungeons lo consume por un contrato de anfitrión (jugadores implicados, tarea compartida, bloques temporales, limpieza, destinatarios de sonidos y efectos). La spec del módulo vive en `mobs-core/docs/spec.md` para que viaje con él. El jar de CustomDungeons sigue siendo uno solo (el módulo se empaqueta dentro).

## Consecuencias
- S1 es una migración sin cambios visibles (mismos YAML, permisos, menús y mensajes) y debe mantener verdes todos los tests.
- La inteligencia (S2), las habilidades nuevas (S3) y el menú de Jefes nacen en `mobs-core`.
- El futuro plugin copia `mobs-core/` (o lo usa como dependencia) e implementa su propio anfitrión.
