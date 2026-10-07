# Laboratorio v1.1 (T45)

Ejemplos ejecutados en Paper 26.3 build 157, Java 25, con dos jugadores offline. Son variantes de **una misma arena**: ejecutar una sola partida a la vez. El recorrido usa X=1200–1251, Y=64–85, Z=0–10 en `cd_dungeons`; la salida está fuera del área, en X=1280,5. El laboratorio del asistente usa X=1300–1345.

## Instalación

Solo `/home/dasan/Desktop/Proyectos/plugins/servidor/Servidor-agentes`, puerto 25566. Copiar `dungeons/`, `mobs/` y `spawners/` a las carpetas homónimas de `plugins/CustomDungeons/`, comprobando antes que los IDs `t45-*` no pertenecen a otra prueba. No sustituir definiciones ajenas. Los YAML están escritos como JSON, que es un subconjunto válido de YAML.

Cargar `cd_dungeons` y reservar un emplazamiento vacío. Ejecutar [build.mcfunction](build.mcfunction) una línea por consola, sin el comentario inicial ni líneas vacías. Después de cada `forceload add`, esperar al menos dos segundos. No construir durante una partida. Ejecutar `customdungeon reload` y comprobar sus resultados en el log. Las puertas se materializan al iniciar la partida y se restauran durante la limpieza.

| ID | Uso |
|---|---|
| `t45-plates` | Dos placas, entrada sin TP, activación por entrada, final NONE y placa de salida. |
| `t45-auto` | Inicio automático, TP a sala 1, final NONE; base para desconexión y ambiente. |
| `t45-immediate` | Final inmediato a EXIT. |
| `t45-delay` | Gracia de 10 s y retorno a la posición PREVIOUS de cada jugador. |
| `t45-timeout` | Límite de 5 s; fuerza salida aunque el final sea NONE. |
| `t45-key` | Portador `*` en sala 1; recogida y clic derecho a la puerta. |
| `t45-external` | Sala 1 espera llave externa tras limpiar sus mobs. |
| `t45-intro` | Cámara de 10 s, sin TP al terminar la introducción. |
| `t45-preset` | Las dos salas comparten `t45-shared`; ejemplo para cambiar y desvincular oleadas. |
| `t45-range-test` | Invoca `t45-range`: escala 7,06 válida; velocidad 4,7 recortada a 1. |
| `t45-load` | Introducción, ambiente y panel; sala 2 con un jefe y 49 husks, dos habilidades cada uno. |

Todos usan mínimo y máximo de dos jugadores. Para repetir la prueba desde consola:

```text
customdungeon join T45A t45-auto
customdungeon join T45B t45-auto
```

El inicio automático tarda cinco segundos desde que están ambos. Placas: A en `(1202.5,65,5.5)` y B en `(1204.5,65,5.5)` durante tres segundos; deben estar apoyados en el suelo. Sala 1: entrar en `(1212.5,65,5.5)`. Después de limpiar/desbloquear su puerta, sala 2: `(1233.5,65,5.5)`. Placa de salida: `(1249.5,65,5.5)`, activa al completar. `customdungeon stop <id>` fuerza salida y limpieza; confirmar `FREE` antes de probar otra variante.

Para el puzzle, habilitar temporalmente bloques de comando **solo en agentes**, con el servidor apagado, y restaurar esa opción al terminar. Con la sala 1 limpia, A cerca de X=1227,5 y B lejos:

```text
execute in minecraft:cd_dungeons run setblock 1227 65 7 command_block{Command:"customdungeon key give @p t45-external"}
execute in minecraft:cd_dungeons run setblock 1227 66 7 redstone_block
```

A recibe una llave ligada a esa partida/sala. Usarla con clic derecho sobre `(1230,65,5)` a menos de cuatro bloques. Retirar los dos bloques después.

`t45-husk`/`t45-boss` aíslan la progresión; `t45-load-mob`/`t45-load-boss` añaden habilidades de ceguera y oscuridad para el perfil. Los efectos Resistance V dados por consola a los bots facilitan la prueba técnica y no son parte de los ejemplos ni una evaluación de dificultad.

Resultados, controlador, recetas de GUI/crash y límites: [pruebas integradas, v1.1 (T45)](../../../guides/pruebas-integradas.md#v11-t45). Al cerrar, retirar las cargas forzadas de los dos laboratorios, deshacer los permisos temporales de los bots y apagar agentes. Las superficies y estos ejemplos pueden conservarse para repetir la prueba.
