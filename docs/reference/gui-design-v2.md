# Diseño de GUI v2 — CustomDungeons

Diseño definido por el arquitecto tras ver las instantáneas PNG de la GUI y el feedback del usuario. Sustituye a `gui-layout.md` como fuente de verdad. Toda tarea de GUI debe cumplirlo y validarse con `./gradlew guiSnapshots` + `scripts/render-gui.py`.

## 1. Principios
1. **Se distingue a la vista qué es información y qué es un botón.**
2. **Cada menú usa solo las filas que necesita** (3–6). Nada de cofres medio vacíos.
3. **Un único lenguaje de iconos en todo el plugin** (§3); aprendido una vez, sirve para todos los menús.
4. **Lo importante se ve sin entrar**: resúmenes en la lore (✔/✖, mobs por oleada, coordenadas).
5. **Máximo 2 clics desde un objeto hasta su contenido** (p. ej. sala → spawner muestra ya las oleadas y sus mobs).
6. **Simetría**: los grupos se centran respecto a la columna 4; columnas de sección en 1·3·5·7 (4 secciones), 2·4·6 (3) o 3·5 (2).

## 2. Estructura común
- **Fila superior** (fila 0): borde de cristal del color de la categoría; slot 4 = **ítem de resumen** del objeto que se edita (icono propio, nombre con el objeto, lore con estado ✔/✖ de cada parte); slot 8 = **❔ Ayuda** (`KNOWLEDGE_BOOK`): 3–6 líneas con qué se hace en este menú y en qué orden.
- **Cabeceras de sección**: `*_STAINED_GLASS_PANE` del color de la sección, nombre en negrita del color de la sección con ✔ verde / ✖ rojo si aplica, lore de 1–2 líneas. **No tienen acción**; jamás se usa un ítem "real" como cabecera.
- **Botones**: ítems reales según §3. Nombre: verbo + objeto ("Fijar checkpoint aquí"). Lore: estado/valor actual en blanco, línea vacía, instrucciones en gris con formato `Clic izq.: …` / `Clic der.: …` / `Shift+clic: …`.
- **Huecos vacíos**: `GRAY_STAINED_GLASS_PANE` sin nombre (nombre vacío, sin tooltip) para que el interior no parezca "agujeros".
- **Fila inferior contextual** (última fila): bordes del color de la categoría y SOLO los botones que aplican: Volver (`ARROW`, slot 0 de la fila) si hay padre; Página anterior/siguiente (`SPECTRAL_ARROW`, slots 3 y 5 de la fila) solo si hay más de una página; Guardar (`LIME_CONCRETE`, slot 4 de la fila) solo en editores con borrador; si hay cambios sin guardar, Guardar muestra "● Cambios sin guardar" y brillo (encantamiento visual); Cerrar (`BARRIER`, slot 8 de la fila).
- **Botón no disponible**: `GRAY_DYE` con el nombre del botón en gris y la lore "No disponible: <motivo>". Nunca cristal negro.
- **Colores de categoría** (borde/cabeceras): Dungeon = naranja, Sala = verde, Spawner/Oleada = cian, Premio = amarillo, Mob = morado, Habilidad/Combo/Fase = magenta, Herramientas = gris claro, Selectores = azul claro.
- Nombres: nunca códigos `&` crudos; todo texto configurable pasa por `Text.parse` (corrige el `&6` visible en la lista de dungeons).

## 3. Lenguaje de iconos (obligatorio)
| Significado | Ítem |
|---|---|
| Aplicar / fijar / usar selección / confirmar | `LIME_DYE` |
| Quitar / borrar / desactivar | `RED_DYE` |
| Dar herramienta | el ítem real de la herramienta (`tools.items.*`) |
| No disponible | `GRAY_DYE` |
| Abrir submenú de un objeto | el icono propio del objeto (sala `OAK_DOOR`, spawner `SPAWNER`, oleada `ZOMBIE_HEAD`, mob = huevo del tipo, premio `CHEST`, escalado `ANVIL`, hooks `COMMAND_BLOCK`, ajustes `COMPARATOR`) |
| Valor numérico editable | icono del concepto (vida `APPLE`, daño `IRON_SWORD`, velocidad `SUGAR`, empuje `SHIELD`, escala `SLIME_BALL`, radio `TARGET`, tiempo `CLOCK`, jugadores `PLAYER_HEAD`, vidas `TOTEM_OF_UNDYING`, dinero `GOLD_INGOT`, XP `EXPERIENCE_BOTTLE`) |
| Alternar sí/no | `LIME_DYE` (sí) / `GRAY_DYE` (no) con el estado en el nombre |
| Añadir elemento a una lista | `LIME_DYE` con "+" en el nombre ("+ Añadir sala") |
| Ver en el mundo (partículas) | `SPYGLASS` |
| Iniciar / probar | `LIME_CONCRETE` (iniciar) / `TARGET` (probar) ; detener `RED_CONCRETE` |
| Ayuda | `KNOWLEDGE_BOOK` |

## 4. Menús (mapas)
Notación: `▒` borde categoría, `·` relleno gris, `H:` cabecera (cristal), `B:` botón. Filas numeradas desde 0.

### 4.1 Principal (`/customdungeon`) — 3 filas, naranja
```
0: ▒ ▒ ▒ ▒ [Resumen: N dungeons, M plantillas] ▒ ▒ ▒ [❔]
1: ▒ · [Dungeons creadas] · [+ Nueva dungeon] · [Biblioteca de mobs] · ▒     (slots 11,13,15)
2: ▒ ▒ ▒ ▒ ▒ ▒ ▒ ▒ [Cerrar]
```
"Dungeons creadas" (`BOOKSHELF`) abre la **lista de dungeons** (6 filas, paginada): cada dungeon con icono por estado (activada `LIME_CONCRETE`, desactivada `GRAY_CONCRETE`, en curso `ORANGE_CONCRETE`, con errores `RED_CONCRETE`) y lore: nombre, salas, jugadores, estado, nº de errores. Cuando exista el asistente (T29) ocupa el hueco 13 "+ Nueva dungeon (asistente)" y "Nueva dungeon (editor)" pasa a la lista.

### 4.2 Editor de dungeon — 5 filas, naranja
Columnas 1·3·5·7:
```
fila 0:  ▒ ▒ ▒ ▒ [Resumen dungeon ✔/✖ por parte] ▒ ▒ ▒ [❔]
fila 1:  H:Estructura   H:Reglas        H:Puntos          H:Partida
fila 2:  B:Salas(N)     B:Ajustes       B:Fijar lobby aquí B:Iniciar
fila 3:  B:Premio       B:Escalado      B:Fijar salida aquí B:Probar
fila 4:  ▒ Volver · · Guardar · · · Cerrar  (fila inferior contextual)
```
Fila extra entre 3 y la inferior solo si hace falta: Hooks (`COMMAND_BLOCK`) bajo Reglas; Detener/Resetear bajo Partida; Activar/Desactivar dungeon (alternar) bajo Estructura. Si una dungeon está en curso (modo solo control), los botones de edición son `GRAY_DYE` con motivo.

### 4.3 Salas de la dungeon — lista, verde
Lista paginada centrada (filas 1–4, columnas 1–7). Cada sala: `OAK_DOOR` (✔ completa) o `IRON_DOOR` con ✖ (incompleta); nombre "Sala N"; lore: ✔/✖ región, checkpoint, puerta, desbloqueo, nº spawners y total de mobs. `+ Añadir sala` en la fila inferior (slot 4 de la fila si no hay Guardar; si lo hay, a su izquierda). Shift+clic der. borra (con confirmación); shift+clic izq. reordena.

### 4.4 Sala — 6 filas, verde (maqueta aprobada por el usuario)
```
0: ▒ ▒ ▒ ▒ [Resumen sala] ▒ ▒ ▒ [❔]
1: ▒ H:Región  · H:Checkpoint · H:Puerta    · H:Desbloqueo ▒
2: ▒ B:Dar varita · B:Fijar aquí · B:Dar herr. puerta · B:Auto/Llave ▒
3: ▒ B:Usar selección · B:Usar punto · B:Usar selección · B:Portador ▒
4: ▒ · B:Spawners(N) · B:+Añadir spawner aquí · B:Ver sala ▒   (slots 38,40,42)
5: fila inferior contextual
```
Quitar puerta: `RED_DYE` en la columna de Puerta solo si hay puerta (sustituye a "Usar selección" con clic der., o en fila 4 col 5 si cabe); Portador solo activo con Llave (si no, `GRAY_DYE` "Solo con llave").

### 4.5 Spawners de la sala — lista, cian
Cada spawner: `SPAWNER`, nombre "Spawner N", lore con posición, radio y **todas sus oleadas con mobs**: "Oleada 1 (simultánea): 3× Zombi, 2× Esqueleto". `+ Añadir spawner aquí` en la fila inferior.

### 4.6 Spawner — 5 filas, cian
```
0: ▒ ▒ ▒ ▒ [Resumen spawner] ▒ ▒ ▒ [❔]
1: ▒ H:Ubicación · H:Radio · H:Oleadas ▒      (columnas 2,4,6)
2: ▒ B:Fijar aquí · B:Radio N bloques · B:Oleada 1 ▒
3: ▒ B:Dar colocador · B:Ver en el mundo · B:Oleada 2 … ▒
4: fila inferior
```
La columna Oleadas lista hasta 3 oleadas (`ZOMBIE_HEAD`, lore = mobs y modo) + "+ Añadir oleada"; si hay más, "Ver todas las oleadas (N)" abre la lista.

### 4.7 Oleada — 5 filas, cian
Fila 1: cabeceras Modo · Pausa · Mobs. Fila 2–3: Modo (icono por modo: simultáneo `TNT`, secuencial `REPEATER`, escalonado `CLOCK`, aleatorio `PRISMARINE_CRYSTALS`) con el intervalo debajo si es escalonado; Pausa tras la oleada (`CLOCK`); a la derecha las **entradas** como huevos del tipo de mob con "3× Zombi blindado (retardo 2,0 s)" + "+ Añadir mob". Clic en entrada: editar cantidad/retardo/plantilla en un menú de 3 filas.

### 4.8 Biblioteca de mobs — lista, morado
Cada plantilla: huevo del tipo, nombre, lore: tipo, vida, escala, nº habilidades, jefe sí/no, ✖ si inválida (con 1ª causa). `+ Crear plantilla` (guarda la plantilla al confirmar el id y abre su editor).

### 4.9 Mob — 5 filas, morado
Columnas 1·3·5·7: H:Identidad (tipo, nombre visible) · H:Estadísticas (abre stats; lore con resumen) · H:Equipo (equipo, encantamientos) · H:Combate (pociones, habilidades, combos, fases/jefe). Fila 4: `TARGET` "Probar en vivo" centrado y `RED_CONCRETE` "Detener prueba" si hay una activa.

### 4.10 Resto
Ajustes, Escalado, Premio, Hooks, Estadísticas, Equipo, Pociones, Encantamientos, Habilidades, Combos, Fases, Editor de parámetros, Selectores (tipo, partícula, sonido, poción, plantilla, disparador, objetivo, color de barra): aplica §1–§3 con cabeceras por sección, tamaño mínimo, iconos del lenguaje y fila inferior contextual. Selectores muy largos (sonidos) con filtro por texto (botón `NAME_TAG` "Buscar…").

## 5. Validación
- Cada menú tiene instantánea PNG en estado típico, vacío y con error.
- El arquitecto revisa las PNG antes de integrar; una tarea de GUI no se integra sin esa revisión.
