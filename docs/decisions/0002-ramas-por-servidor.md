# 0002 — Ramas por servidor

Fecha: 2026-10-08. Estado: aceptada por el usuario (se aplicará más adelante).

## Contexto
El plugin se terminará en `main` y más adelante habrá código único para un servidor concreto.

## Decisión
- `main` es el plugin genérico y la única rama que se publica como release pública.
- Cada servidor tiene una rama larga `server/<nombre>` creada desde `main`, que recibe `main` periódicamente (merge, no rebase, para conservar su historia).
- Para minimizar conflictos, lo nuevo se diseña configurable y extensible por registro (habilidades, reglas de inteligencia, anfitriones), sin fijar en código nada propio de un servidor. Lo específico de un servidor no se sube a `main` salvo que se generalice.
- Las releases de una rama de servidor llevan sufijo de build (`1.2.0+<nombre>.N`) y su propia clave o canal de actualización si se distribuyen.

## Consecuencias
Cada tarea nueva revisa que no introduzca valores propios de un servidor en `main`.
