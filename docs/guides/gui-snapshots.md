# Capturas de los menús sin cliente de Minecraft

Desde la raíz del worktree, con Java 25:

```bash
./gradlew guiSnapshots --no-daemon
python3 scripts/render-gui.py
```

El primer comando instancia los menús reales con un jugador mock que tiene todos
los permisos, los textos españoles de `messages.yml`, las habilidades registradas
y la dungeon y los cuatro mobs de `docs/reference/ejemplos/demo/`. No arranca Paper,
no conecta a ningún servidor y no guarda definiciones. La tarea es opcional:
`./gradlew build --no-daemon` no ejecuta la exportación ni descarga recursos.

El segundo comando requiere Python 3 y Pillow. Si falta Pillow, instala un entorno
virtual **fuera del repositorio** y úsalo así:

```bash
python3 -m venv ~/.cache/customdungeons/venv
~/.cache/customdungeons/venv/bin/python -m pip install Pillow
~/.cache/customdungeons/venv/bin/python scripts/render-gui.py
```

Los JSON y PNG quedan en `build/gui-snapshots/`. Los archivos con sufijo
`-page-N` muestran páginas adicionales. Hay capturas de la dungeon vacía, la sala
sin región, las tres salas completas (automática, con llave y final), los spawners
con sus oleadas y entradas, los editores de mobs, equipo, encantamientos, pociones,
habilidades, parámetros, combos y fases, y los selectores dinámicos. También se muestran una poción de fuerza de ejemplo,
su editor, la vista de control de dungeon y el mob con prueba en vivo activa. Cada habilidad
registrada tiene una captura de sus parámetros. La exportación elimina los JSON/PNG
anteriores de esta carpeta; no guardes archivos propios ahí.

## Formato y lectura

Cada JSON incluye `menu` (clase), `title`, `color` (hexadecimal), `rows` y `slots`.
Los slots se numeran desde **0**, como en Bukkit. Se incluyen también los vacíos
(`AIR`). Cada slot contiene `slot`, `material`, `name`, `color`, `lore`, `action`
y `amount`. El color principal es el de la primera porción visible del nombre,
respetando la herencia de estilos; un texto sin color explícito usa blanco.

`action` indica si el botón tiene un callback: los handlers explícitamente vacíos
y la navegación deshabilitada se clasifican como información. La detección lee la
expresión `Button.of` en el código fuente porque el contrato `Button` no tiene un
indicador de botón informativo. Nunca se ejecutan acciones de juego para clasificarlas;
solo se abren el selector de portador y el editor de pociones mediante sus
callbacks de navegación reales.
Un botón que responde con un aviso de bloqueo sigue teniendo acción.

El PNG imita el inventario de cofre, con rejilla de 9 columnas, fondo gris y escala
×4. No hay etiquetas sobre los slots. Debajo aparece la leyenda
`slot: nombre — (acción|info) — primera línea de lore` para todos los ítems,
incluido el borde decorativo. La leyenda usa texto plano; el título conserva su
color principal. El JSON conserva todo el lore para revisar detalles adicionales.

## Texturas y caché local

El renderizador consulta el manifiesto oficial de Mojang:
<https://piston-meta.mojang.com/mc/game/version_manifest_v2.json>.
Selecciona **26.3**, descarga el client jar desde la URL oficial y comprueba su
tamaño y SHA-1. No sustituye una versión ausente por otra. Extrae únicamente PNG
de `assets/minecraft/textures/item/` y `block/` a `~/.cache/customdungeons/`.
Las ejecuciones posteriores reutilizan la caché; no necesitan red si ya está completa.

Se busca primero la textura del ítem y luego la cara frontal, superior o lateral
del bloque. Para paneles de vidrio se usa la textura del vidrio correspondiente.
El reloj y las brújulas usan un fotograma fijo de sus texturas; la manzana
encantada usa la textura de manzana dorada. Una textura animada muestra su primer fotograma. Si no hay una textura directa,
se dibuja un recuadro con las iniciales y se informa del material en consola.
Son aproximaciones 2D: no simulan modelos 3D, resource packs, brillo de
encantamientos, tooltips ni la fuente del cliente. Los selectores de registros
usan las constantes y entradas mock disponibles, no un registro completo de servidor.

Para usar otra ubicación fuera del repo (por ejemplo, en un sandbox):

```bash
python3 scripts/render-gui.py --cache-dir /tmp/customdungeons-cache
```

Se puede cambiar la carpeta de JSON con `--input`. `--version` permite generar
comparaciones explícitas con otras versiones; la aceptación de T33 usa 26.3.

**No versiones ni redistribuyas el client jar ni las texturas de Mojang.** Solo
se usan localmente. Tampoco se versionan los JSON/PNG generados: `build/` está
ignorado por Git. Para entregar la revisión, comparte la ruta local de los PNG.

Para verificar el renderizador sin red: `python3 scripts/test-render-gui.py`.
