# Constitución — CustomDungeons

Principios no negociables. Ante un conflicto, este documento manda sobre todos los demás.

## 1. Portabilidad entre versiones
- Solo API pública de Paper (`paper-api`). **Prohibido NMS/CraftBukkit** salvo excepción aprobada, aislada en una única clase con alternativa por API.
- Actualizar de versión de Minecraft debe requerir, en el caso normal, solo cambiar la versión de la dependencia.
- Datos dependientes de versión (p. ej. mobs que admiten armadura) viven en configuración, no en código.

## 2. Rendimiento
- Una sola tarea programada por partida activa. Nunca tareas por mob ni por habilidad.
- Ninguna operación de base de datos o de disco en el hilo principal durante el juego. Excepción: la carga inicial en `onEnable`, `/customdungeon reload` y el guardado final en `onDisable`, que pueden ser síncronos.
- Partículas y efectos solo a jugadores cercanos, con límites configurables.
- Objetivo: ≤ 2 ms de MSPT por partida activa con ~50 mobs con habilidades.

## 3. Integridad del mundo y de los jugadores
- Ninguna habilidad rompe bloques. Los bloques temporales (puertas, telarañas) siempre se restauran, incluso tras un reinicio o crash.
- Ninguna habilidad destruye ítems del jugador (p. ej. *Ladrón* siempre devuelve lo robado). La pérdida de inventario al morir es una regla de juego explícita y configurable, no un efecto colateral.
- La protección del terreno es responsabilidad de WorldGuard; el plugin no la duplica.
- Toda partida interrumpida se limpia al arrancar: mobs marcados eliminados, puertas restauradas, jugadores devueltos.

## 4. Configurable antes que codificado
- Comportamiento ajustable en `config.yml`, `messages.yml` o la GUI. Textos nunca embebidos en código.
- Toda acción de administración protegida por un nodo de permiso.

## 5. Calidad visual
- GUIs coherentes, descriptivas y estéticas; avisos visuales antes de ataques (telegraph) para que el combate sea justo.

## 6. Integración antes que reinvención
- Si un plugin del servidor ya resuelve algo (WorldGuard: protección; Multiverse-Portals: portales; LuckPerms: permisos; Vault: economía), nos integramos con él en lugar de reimplementarlo.
- Integraciones por comandos configurables o API pública, siempre opcionales: el plugin arranca sin ellas.

## 7. Simplicidad
- Sin abstracciones especulativas. Se construye lo que la spec pide; lo futuro se diseña para no estorbar, no se implementa por adelantado.
- Sin dependencias nuevas si unas líneas de código o la API de Paper lo resuelven.

## 8. Modularidad y ramas
- El sistema de mobs vive en el módulo `mobs-core`, que **no depende de nada de dungeons**; CustomDungeons lo usa por una interfaz de anfitrión. Lo nuevo sobre mobs nace en `mobs-core`.
- `main` es el plugin genérico. El código propio de un servidor concreto vive en ramas `server/<nombre>` que reciben `main` periódicamente; para que cambien poco, el comportamiento se hace configurable y extensible por registro (habilidades, reglas de inteligencia), nunca fijado para un servidor.

## 9. Combate justo
- Ninguna habilidad ni contramedida mata de un golpe desde vida llena. Todo control tiene aviso, duración máxima y forma de escapar.
- Toda adaptación de un mob es temporal, tiene contrapartidas y un tope (nunca inmune).
- Las modificaciones temporales al jugador (atributos, movimiento, recargas) son transitorias: nunca se guardan en disco.

## 10. Proceso
- Flujo SDD: constitución → spec → arquitectura → plan → tareas. No se implementa sin plan aprobado.
- Trabajo en worktrees; cada cambio pasa por revisión antes de integrarse en `main`.
- Toda lógica no trivial deja al menos un test que falla si la lógica se rompe.
