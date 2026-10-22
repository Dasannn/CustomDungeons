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
- **Cabeceras de sección**: las neutras usan `WHITE_STAINED_GLASS_PANE` con brillo de encantamiento visual; las que muestran estado usan `LIME_STAINED_GLASS_PANE` para ✔ y `RED_STAINED_GLASS_PANE` para ✖. Nombre en negrita del color de la sección con ✔ verde / ✖ rojo si aplica, lore de 1–2 líneas. **No tienen acción**; jamás se usa un ítem "real" como cabecera. El borde conserva el color de categoría y debe distinguirse de las cabeceras.
- **Botones**: ítems reales según §3. Nombre: verbo + objeto ("Fijar checkpoint aquí"). Lore: estado/valor actual en blanco, línea vacía, instrucciones en gris con formato `Clic izq.: …` / `Clic der.: …` / `Shift+clic: …`.
- **Huecos vacíos**: `GRAY_STAINED_GLASS_PANE` sin nombre (nombre vacío, sin tooltip) para que el interior no parezca "agujeros".
- **Fila inferior contextual** (última fila): bordes del color de la categoría y SOLO los botones que aplican: Volver (`ARROW`, slot 0 de la fila) si hay padre; Página anterior/siguiente (`SPECTRAL_ARROW`, slots 3 y 5 de la fila) solo si hay más de una página; Guardar (`LIME_CONCRETE`, slot 4 de la fila) solo en editores con borrador; si hay cambios sin guardar, Guardar muestra "● Cambios sin guardar" y brillo (encantamiento visual); Cerrar (`BARRIER`, slot 8 de la fila).
- **Botón no disponible**: `GRAY_DYE` con el nombre del botón en gris y la lore "No disponible: <motivo>". Nunca cristal negro.
- **Colores de categoría** (borde): Dungeon = naranja, Sala = verde, Spawner/Oleada = cian, Premio = amarillo, Mob = morado, Habilidad/Combo/Fase = magenta, Herramientas = gris claro, Selectores = azul claro.
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
4: ▒ B:Spawners(N) · B:+Añadir spawner aquí · B:Ver sala · B:Ambiente ▒   (slots 37,39,41,43; columnas 1·3·5·7, T43)
5: fila inferior contextual
```
Quitar puerta: `RED_DYE` en la columna de Puerta solo si hay puerta (sustituye a "Usar selección" con clic der., o en fila 4 col 5 si cabe); Portador solo activo con Llave (si no, `GRAY_DYE` "Solo con llave").

### 4.5 Spawners de la sala — lista, cian
Cada spawner: `SPAWNER`, nombre "Spawner N", lore con posición, radio y **todas sus oleadas con mobs**: "Oleada 1 (simultánea): 3× Zombi, 2× Esqueleto". `+ Añadir spawner aquí` en la fila inferior.

### 4.6 Spawner — 6 filas, cian
```
0: ▒ ▒ ▒ ▒ [Resumen spawner] ▒ ▒ ▒ [❔]
1: ▒ H:Ubicación · H:Radio · H:Oleadas ▒      (columnas 2,4,6)
2: ▒ B:Fijar aquí · B:Radio N bloques · B:Oleada 1 ▒
3: ▒ B:Dar colocador · B:Ver en el mundo · B:Oleada 2 … ▒
4: ▒ · · · · · B:Oleada 3 / Ver todas las oleadas (N) · ▒
5: fila inferior contextual: Volver · + Añadir oleada · Guardar · Cerrar
```
La columna Oleadas lista hasta 3 oleadas (`ZOMBIE_HEAD`, lore = mobs y modo). Si hay más de 3, la tercera casilla se sustituye por "Ver todas las oleadas (N)" (`ZOMBIE_HEAD`). "+ Añadir oleada" siempre va en la fila inferior junto a navegación y Guardar; la fila 4 bajo Ubicación/Radio queda con relleno gris salvo que haga falta un control.

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

### 4.11 Ambiente de sala (T43, maqueta aprobada 6-oct) — 6 filas, verde
Columnas 1·3·5·7: **Al entrar** (título NAME_TAG, subtítulo PAPER, sonido de entrada NOTE_BLOCK) · **Mientras estás dentro** (música JUKEBOX, efectos POTION, partículas CAMPFIRE con densidad) · **Al abrir la puerta** (sonido IRON_DOOR, partículas CAMPFIRE, temblor sí/no) · **Al limpiar la sala** (sonido GOAT_HORN, título NAME_TAG, Restablecer RED_DYE con shift). Clic der. en cada opción = valor por defecto de `config.yml`; la sala solo guarda lo que cambia.

### 4.12 Zonas opcionales (T64, maquetas aprobadas 22-oct)
Sistema verde de Sala; icono `MAP` para zona. Slots desde 0. En modo construcción, el pie del editor de dungeon queda 51 Spawners de la dungeon · 52 Salir de construcción y restaurar inventario · 53 Cerrar solo el menú.

#### t64-01-dungeon — 6 filas, dungeon

**Propósito:** Incorporar Zonas opcionales al editor manteniendo las cuatro columnas de sección.

```text
0: ▒ ▒ ▒ ▒ [4 Resumen] ▒ ▒ ▒ [8 ❔]
1: ▒ [10 H:Estructura] · [12 H:Reglas] · [14 H:Puntos] · [16 H:Partida] ▒
2: ▒ [19 B:Salas (3)] · [21 B:Ajustes] · [23 B:Fijar lobby aquí] · [25 I:Iniciar] ▒
3: ▒ [28 B:Zonas opcionales (2)] · [30 B:Escalado] · [32 B:Fijar salida aquí] · [34 I:Probar] ▒
4: ▒ [37 B:Premio] · [39 B:Hooks] · [41 B:Inicio y final] · · ▒
5: [45 B:Volver] ▒ [47 B:Modo construcción] ▒ [49 B:Guardar] ▒ [51 B:Spawners de la dungeon (2)] ▒ [53 B:Cerrar]
```

Se preservan Reglas, Puntos y Partida del editor vigente; Estructura pasa a Salas (19), Zonas (28), Premio (37). El acceso a plantillas de spawner pasa de 19 a 51; Construcción conserva 47.

Detener se omite porque no hay partida activa. Iniciar y Probar muestran motivo de bloqueo, sin callback.

#### t64-02-zonas — 3 filas, sala

**Propósito:** Crear, editar, previsualizar y eliminar zonas; distinguir una completa y otra incompleta.

```text
0: ▒ ▒ ▒ ▒ [4 Resumen] ▒ ▒ ▒ [8 ❔]
1: ▒ · · [12 B:✔ cripta_lateral] · [14 B:✖ galeria_oculta] · · ▒
2: [18 B:Volver] ▒ ▒ ▒ [22 B:+ Añadir zona] ▒ ▒ ▒ [26 B:Cerrar]
```

Verde de Sala por compartir región y spawners. MAP identifica una zona sin sugerir una puerta. No hay reordenación: las zonas no tienen índice de recorrido.

Lista de una sola página: sin flechas de paginación ni Guardar. Los cambios se conservan en el borrador de la dungeon.

#### t64-03-zona — 5 filas, sala

**Propósito:** Editar exclusivamente región y spawners de una zona, con previsualización.

```text
0: ▒ ▒ ▒ ▒ [4 Resumen] ▒ ▒ ▒ [8 ❔]
1: ▒ · · [12 H:✔ Región] · [14 H:✔ Spawners] · · ▒
2: ▒ · · [21 B:Dar varita de región] · [23 B:Spawners (2)] · · ▒
3: ▒ · · [30 B:Usar selección: región] · [32 B:+ Añadir spawner aquí] · · ▒
4: [36 B:Volver] ▒ [38 B:Ver zona] ▒ [40 B:Guardar] ▒ ▒ ▒ [44 B:Cerrar]
```

Dos secciones centradas en columnas 3·5. Previsualizar ocupa 38 en la fila inferior contextual.

Toda selección lleva tipo Zona e id cripta_lateral; partículas y barra de acción muestran ese origen. No cambia la sala obligatoria.

#### t64-04-asistente-salas — 6 filas, sala

**Propósito:** Ofrecer zonas y cofres en el paso Salas y permitir continuar sin zonas ni cofres.

```text
0: ▒ ▒ ▒ ▒ [4 Resumen] ▒ ▒ ▒ [8 ❔]
1: ▒ [10 B:✔ 1 · Área] [11 B:✔ 2 · Lobby y salida] [12 B:✔ 3 · Spawners] [13 I:▶ 4 · Salas] [14 I:5 · Reglas] [15 I:6 · Premio] [16 I:7 · Revisión] ▒
2: ▒ · [20 B:✔ Sala 1] · [22 B:✔ Sala 2] · [24 B:✔ Sala 3] · ▒
3: ▒ · [29 B:+ Añadir sala] · [31 B:Zonas opcionales (0)] · [33 B:Ver salas y zonas] · ▒
4: ▒ · · · · · · · ▒
5: [45 B:Volver] ▒ ▒ ▒ [49 B:Salir y continuar luego] ▒ [51 B:Continuar sin zonas] ▒ [53 B:Cerrar]
```

Se conserva el indicador de siete pasos en 10–16. Continuar ocupa 51 y Cerrar 53 para respetar GUI v2; no se presenta Guardar como publicación del borrador del asistente.

Al añadir zonas, el botón 51 pasa a Siguiente y exige validar también esas zonas; nunca elimina zonas para saltar errores. Sin cofres sigue siendo válido.

#### t64-05-asistente-zona-incompleta — 6 filas, sala

**Propósito:** Mostrar que añadir una zona incompleta bloquea el paso y la revisión, con ruta y motivo.

```text
0: ▒ ▒ ▒ ▒ [4 Resumen] ▒ ▒ ▒ [8 ❔]
1: ▒ [10 B:✔ 1 · Área] [11 B:✔ 2 · Lobby y salida] [12 B:✔ 3 · Spawners] [13 I:▶ 4 · Salas] [14 I:5 · Reglas] [15 I:6 · Premio] [16 I:7 · Revisión] ▒
2: ▒ · [20 B:✔ Sala 1] · [22 B:✔ Sala 2] · [24 B:✔ Sala 3] · ▒
3: ▒ · [29 B:+ Añadir sala] · [31 B:Zonas opcionales (2)] · [33 B:Ver salas y zonas] · ▒
4: ▒ · · · [40 I:✖ Zona incompleta] · · · ▒
5: [45 B:Volver] ▒ ▒ ▒ [49 B:Salir y continuar luego] ▒ [51 I:Siguiente] ▒ [53 B:Cerrar]
```

Los motivos y rutas se toman del Validator, sin tratar las zonas como salas obligatorias. Corregir o eliminar la zona permite continuar.

#### t64-06-contexto-construccion — 6 filas, selector

**Propósito:** Elegir un contexto tipado desde la posición 7 de la barra de construcción.

```text
0: ▒ ▒ ▒ ▒ [4 Resumen] ▒ ▒ ▒ [8 ❔]
1: ▒ · · [12 H:Salas] · [14 H:Zonas opcionales] · · ▒
2: ▒ · · [21 B:Seleccionar Sala 1 · vestibulo] · [23 B:Seleccionar Zona · cripta_lateral] · · ▒
3: ▒ · · [30 B:Seleccionar Sala 2 · osario] · [32 B:Seleccionar Zona · galeria_oculta] · · ▒
4: ▒ · · [39 B:Seleccionar Sala 3 · santuario] · · · · ▒
5: [45 B:Volver] ▒ [47 B:+ Añadir sala] ▒ ▒ ▒ [51 B:+ Añadir zona] ▒ [53 B:Cerrar]
```

Tres salas y dos zonas, con columnas 3·5; admite más páginas como las listas existentes. Conserva crear sala y permite crear zona desde el selector. No añade herramientas a la barra.

Barra 1–9: área; región del contexto sala/zona; puerta; spawner del contexto; placas; puntos; selector sala/zona; deshacer; menú de dungeon.

En contexto Zona, puerta de sala y checkpoint están no disponibles con motivo; puerta de entrada con Shift, lobby y salida siguen siendo de dungeon. No se crean campos nuevos de zona.

Ejemplo de barra de acción: «Varita de región · Zona cripta_lateral». Para colocar cofres: 9 → Salas → sala → Cofres de botín → cofre → Dar colocador. La herramienta específica usa el respaldo de inventario del modo; no obtiene una décima posición fija.

#### t64-07-spawners-zona — 3 filas, spawner

**Propósito:** Reutilizar presets y editores de oleadas con el origen de zona visible.

```text
0: ▒ ▒ ▒ ▒ [4 Resumen] ▒ ▒ ▒ [8 ❔]
1: ▒ · · [12 B:Editar guardia_oeste] · [14 B:Editar guardia_este] · · ▒
2: [18 B:Volver] ▒ ▒ ▒ [22 B:+ Añadir spawner aquí] ▒ ▒ ▒ [26 B:Cerrar]
```

El spawner enlazado reutiliza «Editar plantilla (afecta a todos sus usos)» y «Hacer propio»; no se duplica el editor de presets/oleadas. Los cambios se aplican al tipo e id de origen.
