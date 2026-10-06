# Guía de distribución de la GUI

La rejilla es un cofre de 9 × 6, numerado de 0 a 53 por filas. El contenido ocupa las columnas 1–7 y se centra respecto a la columna 4; las filas incompletas también se centran. Los grupos relacionados comparten fila o columna. Las acciones de listas van en una cabecera propia, separada del contenido. Nunca se colocan botones aislados junto a la esquina superior izquierda.

Los bordes identifican la categoría: dungeon naranja (&6), sala verde (&a), spawner/oleada azul claro (&b), mob morado (&d), premio amarillo (&e), herramientas y entradas cian (&3). Los iconos conservan su variedad. Los títulos usan el color de categoría, el valor actual blanco, la unidad explícita y las instrucciones gris: «Clic izq.: …», «Clic der.: …», «Shift + clic izq.: …». Todos los textos proceden de messages.yml y messages_en.yml.

Barra inferior fija: volver 45, anterior 48, guardar 49, siguiente 50, cerrar 53. Los controles no disponibles aparecen grises. Cabeceras de sección explican qué regulan y no cambian datos.

Ajustes usa dos paneles simétricos: jugadores y supervivencia arriba; tiempos y acceso abajo. Escalado muestra los incrementos por jugador adicional y un ejemplo calculado con el mínimo actual. La lista principal reserva una fila para crear, biblioteca y el futuro asistente (sin acción). Premios separa dinero/XP/comandos de las 27 ranuras reales: filas 2–4 completas, delimitadas por la cabecera y la barra inferior. Esta excepción al borde lateral conserva la capacidad original y nunca mezcla adornos con ítems depositados.

Los mapas siguientes son normativos; `#` es borde, `.` espacio vacío, `L` contenido paginado, `I` ranura de depósito. La leyenda de cada mapa identifica las acciones por slot. Las listas conservan el orden de datos; las filas parciales se centran sin alterar los índices de edición.

## DungeonListMenu

```text
 # |  # |  # |  # |  4 |  # |  # |  # |  #
 # |  . | 11 |  . | 13 |  . | 15 |  . |  #
 # |  L |  L |  L |  L |  L |  L |  L |  #
 # |  L |  L |  L |  L |  L |  L |  L |  #
 # |  L |  L |  L |  L |  L |  L |  L |  #
 V |  # |  # |  A |  G |  S |  # |  # |  X
```

4 Cabecera; 11 Nueva dungeon; 13 Biblioteca de mobs; 15 Asistente reservado.
V: volver; A: anterior; G: guardar; S: siguiente; X: cerrar.

## DungeonMenu (editor y vista de control)

```text
 # |  # |  # |  # |  # |  # |  # |  # |  #
 # | 10 |  . | 12 |  . | 14 |  . | 16 |  #
 # |  . |  . | 21 |  . | 23 |  . |  . |  #
 # | 28 | 29 |  . | 31 |  . | 33 | 34 |  #
 # | 37 |  . | 39 |  . | 41 |  . | 43 |  #
 V |  # |  # |  A |  G |  S |  # |  # |  X
```

10 Ajustes; 12 Escalado; 14 Ganchos; 16 Salas; 21 Premio; 23 Activación; 28 Lobby aquí; 29 Lobby desde herramienta; 31 Errores (si existen); 33 Salida aquí; 34 Salida desde herramienta; 37 Iniciar; 39 Probar; 41 Detener; 43 Reiniciar.
V: volver; A: anterior; G: guardar; S: siguiente; X: cerrar.

En la vista de control, las secciones de edición están bloqueadas; las posiciones son las mismas.

## DungeonSettingsMenu

```text
 # |  # |  # |  # |  4 |  # |  # |  # |  #
 # |  . | 11 |  . |  . |  . | 15 |  . |  #
 # | 19 |  . | 21 |  . | 23 |  . | 25 |  #
 # |  . | 29 |  . |  . |  . | 33 |  . |  #
 # | 37 | 38 | 39 |  . | 41 |  . | 43 |  #
 V |  # |  # |  A |  G |  S |  # |  # |  X
```

4 Nombre; 11 Cabecera jugadores; 15 Cabecera supervivencia; 19 Mínimo; 21 Máximo; 23 Vidas; 25 Conservar inventario; 29 Cabecera tiempos; 33 Cabecera acceso; 37 Cuenta atrás; 38 Tiempo límite; 39 Cooldown; 41 Permiso; 43 Activación.
V: volver; A: anterior; G: guardar; S: siguiente; X: cerrar.

## ScalingMenu

```text
 # |  # |  # |  # |  4 |  # |  # |  # |  #
 # |  . |  . | 12 |  . | 14 |  . |  . |  #
 # |  . |  . |  . |  . |  . |  . |  . |  #
 # |  . |  . | 30 |  . | 32 |  . |  . |  #
 # |  . |  . |  . |  . |  . |  . |  . |  #
 V |  # |  # |  A |  G |  S |  # |  # |  X
```

4 Explicación; 12 Mobs por jugador; 14 Vida por jugador; 30 Ejemplo mobs; 32 Ejemplo vida.
V: volver; A: anterior; G: guardar; S: siguiente; X: cerrar.

## HooksMenu

```text
 # |  # |  # |  # |  4 |  # |  # |  # |  #
 # |  . | 11 |  . | 13 |  . | 15 |  . |  #
 # |  . |  . |  . | 22 |  . |  . |  . |  #
 # |  . | 29 |  . | 31 |  . | 33 |  . |  #
 # |  . |  . |  . |  . |  . |  . |  . |  #
 V |  # |  # |  A |  G |  S |  # |  # |  X
```

4 Cabecera Acceso e inicio; 11 Lobby abierto; 13 Grupo lleno; 15 Inicio; 22 Cabecera Resultado y limpieza; 29 Completada; 31 Fallida; 33 Libre.

V/A/G/S/X: barra inferior fija. Cada evento muestra cuántos comandos tiene configurados.

## RewardMenu

```text
 # |  # |  # |  # |  4 |  # |  # |  # |  #
 # |  . | 11 |  . | 13 |  . | 15 |  . |  #
 I |  I |  I |  I |  I |  I |  I |  I |  I
 I |  I |  I |  I |  I |  I |  I |  I |  I
 I |  I |  I |  I |  I |  I |  I |  I |  I
 V |  # |  # |  A |  G |  S |  # |  # |  X
```

4 Etiqueta de zona; 11 Dinero; 13 XP; 15 Comandos.
V: volver; A: anterior; G: guardar; S: siguiente; X: cerrar.

I son las 27 ranuras de ítems. Las plantillas guardadas ocupan estas mismas celdas y solo permiten quitar con clic derecho; los depósitos reales se copian y devuelven.

## CommandList (ganchos y comandos de premio)

```text
 # |  # |  # |  # |  4 |  # |  # |  # |  #
 # |  L |  L |  L |  L |  L |  L |  L |  #
 # |  L |  L |  L |  L |  L |  L |  L |  #
 # |  L |  L |  L |  L |  L |  L |  L |  #
 # |  L |  L |  L |  L |  L |  L |  L |  #
 V |  # |  # |  A |  G |  S |  # |  # |  X
```

4 Añadir.
V: volver; A: anterior; G: guardar; S: siguiente; X: cerrar.

Se muestra una página completa; cada fila parcial se centra según GuiLayout.centeredRow.

## RoomListMenu

```text
 # |  # |  # |  # |  4 |  # |  # |  # |  #
 # |  . |  . |  . | 13 |  . |  . |  . |  #
 # |  L |  L |  L |  L |  L |  L |  L |  #
 # |  L |  L |  L |  L |  L |  L |  L |  #
 # |  L |  L |  L |  L |  L |  L |  L |  #
 V |  # |  # |  A |  G |  S |  # |  # |  X
```

4 Cabecera Salas y cantidad; 13 Añadir sala.

V/A/G/S/X: barra inferior fija. L muestra orden, id, región/tamaño, checkpoint, puerta, modo/portador y spawners. Filas parciales centradas.

## RoomMenu

```text
 # |  # |  2 |  # |  4 |  # |  6 |  # |  #
 # |  . | 11 |  . |  . |  . | 15 |  . |  #
 # | 19 |  . | 21 |  . | 23 |  . | 25 |  #
 # |  . | 29 |  . |  . |  . | 33 |  . |  #
 # | 37 | 38 | 39 |  . | 41 | 42 | 43 |  #
 V |  # |  # |  A |  G |  S |  # |  # |  X
```

2 Abrir lista de spawners; 4 cabecera Spawners y cantidad; 6 añadir spawner.
11 cabecera Región, con coordenadas y tamaño; 19 usar selección/dar varita; 21 dar varita.
15 cabecera Checkpoint, con coordenadas; 23 usar punto/dar herramienta; 25 fijar aquí.
29 cabecera Puerta, con coordenadas y tamaño; 37 usar selección/dar selector; 38 estado de puerta (información); 39 quitar puerta.
33 cabecera Desbloqueo, modo y portador; 41 alternar automático/llave; 42 selector de portador de T28 (último mob en morir o plantilla), con el portador actual en la lore; 43 seleccionar directamente una plantilla portadora.

Los dos paneles superiores y los dos inferiores comparten ancho y eje. Los spawners tienen su propia lista, para no competir con las cinco secciones de la sala. Las cabeceras no modifican el borrador. V/A/G/S/X son la barra fija.

## RoomMenu.CarrierPicker (selector de portador)

```text
 # |  # |  # |  # |  4 |  # |  # |  # |  #
 # |  L |  L |  L |  L |  L |  L |  L |  #
 # |  L |  L |  L |  L |  L |  L |  L |  #
 # |  L |  L |  L |  L |  L |  L |  L |  #
 # |  L |  L |  L |  L |  L |  L |  L |  #
 V |  # |  # |  A |  G |  S |  # |  # |  X
```

4 cabecera Portador de llave, sin acción. L ofrece primero «Último mob en morir», seguido de las plantillas con su huevo. Las filas parciales se centran: con dos opciones, ocupan 12 y 14. Clic izq. selecciona y vuelve a RoomMenu. Se abre desde el slot 42 de Desbloqueo; conserva el borde verde de Sala.

## RoomSpawnerList (lista de spawners de una sala)

```text
 # |  # |  # |  # |  4 |  # |  # |  # |  #
 # |  L |  L |  L |  L |  L |  L |  L |  #
 # |  L |  L |  L |  L |  L |  L |  L |  #
 # |  L |  L |  L |  L |  L |  L |  L |  #
 # |  L |  L |  L |  L |  L |  L |  L |  #
 V |  # |  # |  A |  G |  S |  # |  # |  X
```

4 añadir spawner. L muestra ubicación, radio y cantidad de oleadas; clic abre el editor, shift + clic der. elimina. Volver regresa a RoomMenu. Filas parciales centradas, 28 entradas por página.

## SpawnerMenu

```text
 # |  # |  # |  # |  4 |  # |  # |  # |  #
 # |  . | 11 |  . | 13 |  . | 15 |  . |  #
 # |  . | 20 |  . | 22 |  . | 24 |  . |  #
 # |  . |  . |  . | 31 |  . |  . |  . |  #
 # |  . | 38 |  . | 40 |  . | 42 |  . |  #
 V |  # |  # |  A |  G |  S |  # |  # |  X
```

4 Resumen; 11 Cabecera Ubicación y coordenadas; 13 Cabecera Radio; 15 Cabecera Oleadas y cantidad; 20 Ubicación; 22 Radio; 24 Editar oleadas; 31 Cabecera Herramientas y previsualización; 38 Dar colocador; 40 Mostrar marcadores; 42 Ocultar marcadores.

V/A/G/S/X: barra inferior fija.

## WaveListMenu

```text
 # |  # |  # |  # |  4 |  # |  # |  # |  #
 # |  . |  . |  . | 13 |  . |  . |  . |  #
 # |  L |  L |  L |  L |  L |  L |  L |  #
 # |  L |  L |  L |  L |  L |  L |  L |  #
 # |  L |  L |  L |  L |  L |  L |  L |  #
 V |  # |  # |  A |  G |  S |  # |  # |  X
```

4 Cabecera Oleadas y cantidad; 13 Añadir oleada.

V/A/G/S/X: barra inferior fija. Cada oleada muestra entradas, modo y pausa. Se mantienen las acciones de reordenar/eliminar.

## WaveMenu

```text
 # |  # |  # |  # |  4 |  # |  # |  # |  #
 # |  . | 11 |  . | 13 |  . | 15 |  . |  #
 # |  . | 20 |  . | 22 |  . | 24 |  . |  #
 # |  L |  L |  L |  L |  L |  L |  L |  #
 # |  L |  L |  L |  L |  L |  L |  L |  #
 V |  # |  # |  A |  G |  S |  # |  # |  X
```

4 Añadir entrada (con explicación); 11 Cabecera Modo; 13 Cabecera Intervalo; 15 Cabecera Pausa; 20 Cambiar modo; 22 Intervalo; 24 Pausa.

V/A/G/S/X: barra inferior fija. L muestra plantilla, cantidad y retardo. 14 entradas por página; el intervalo solo afecta al modo escalonado.

## WaveEntryMenu

```text
 # |  # |  # |  # |  # |  # |  # |  # |  #
 # |  . | 11 |  . | 13 |  . | 15 |  . |  #
 # |  . | 20 |  . | 22 |  . | 24 |  . |  #
 # |  . |  . |  . |  . |  . |  . |  . |  #
 # |  . |  . |  . |  . |  . |  . |  . |  #
 V |  # |  # |  A |  G |  S |  # |  # |  X
```

11 Cabecera Plantilla y valor; 13 Cabecera Cantidad; 15 Cabecera Retardo; 20 Seleccionar plantilla; 22 Cantidad de mobs; 24 Retardo en segundos.

V/A/G/S/X: barra inferior fija.

## TemplatePickerMenu

```text
 # |  # |  # |  # |  4 |  # |  # |  # |  #
 # |  L |  L |  L |  L |  L |  L |  L |  #
 # |  L |  L |  L |  L |  L |  L |  L |  #
 # |  L |  L |  L |  L |  L |  L |  L |  #
 # |  L |  L |  L |  L |  L |  L |  L |  #
 V |  # |  # |  A |  G |  S |  # |  # |  X
```

4 Etiqueta de selección de plantilla.
V: volver; A: anterior; G: guardar; S: siguiente; X: cerrar.

Se muestra una página completa; cada fila parcial se centra según GuiLayout.centeredRow.

## MobLibraryMenu

```text
 # |  # |  # |  # |  4 |  # |  # |  # |  #
 # |  L |  L |  L |  L |  L |  L |  L |  #
 # |  L |  L |  L |  L |  L |  L |  L |  #
 # |  L |  L |  L |  L |  L |  L |  L |  #
 # |  L |  L |  L |  L |  L |  L |  L |  #
 V |  # |  # |  A |  G |  S |  # |  # |  X
```

4 Crear plantilla.
V: volver; A: anterior; G: guardar; S: siguiente; X: cerrar.

Se muestra una página completa; cada fila parcial se centra según GuiLayout.centeredRow.

## MobMenu

```text
 # |  # |  # |  # |  4 |  # |  # |  # |  #
 # | 10 | 11 | 12 | 13 | 14 | 15 | 16 |  #
 # |  . | 20 | 21 | 22 | 23 | 24 |  . |  #
 # |  . | 29 |  . | 31 |  . | 33 |  . |  #
 # |  . |  . |  . | 40 |  . |  . |  . | 44
 V |  # |  # |  A |  G |  S |  # |  # |  X
```

4 Cabecera y explicación de grupos; 10 Tipo; 11 Nombre; 12 Stats; 13 Equipo; 14 Pociones; 15 Habilidades; 16 Combos; 20 Jefe; 21 Fases; 22 Color BossBar; 23 Música; 24 Drops; 29 Probar; 31 Cabecera Prueba / detener si está activa; 33 Invulnerabilidad; 40 Errores (si existen); 44 Errores de guardado (si existen).

V/A/G/S/X: barra inferior fija. Identidad/combate, reglas de jefe y prueba ocupan filas propias; el valor actual también figura en la lore.

## EntityTypePickerMenu

```text
 # |  # |  # |  # |  4 |  # |  # |  # |  #
 # |  L |  L |  L |  L |  L |  L |  L |  #
 # |  L |  L |  L |  L |  L |  L |  L |  #
 # |  L |  L |  L |  L |  L |  L |  L |  #
 # |  L |  L |  L |  L |  L |  L |  L |  #
 V |  # |  # |  A |  G |  S |  # |  # |  X
```

4 Buscar.
V: volver; A: anterior; G: guardar; S: siguiente; X: cerrar.

Se muestra una página completa; cada fila parcial se centra según GuiLayout.centeredRow.

## StatsMenu

```text
 # |  # |  # |  # |  # |  # |  # |  # |  #
 # |  . | 11 | 12 | 13 | 14 | 15 |  . |  #
 # |  . |  . |  . |  . |  . |  . |  . |  #
 # |  . |  . |  . |  . |  . |  . |  . |  #
 # |  . |  . |  . |  . |  . |  . |  . | 44
 V |  # |  # |  A |  G |  S |  # |  # |  X
```

11 Vida; 12 Daño; 13 Velocidad; 14 Resistencia; 15 Escala; 44 Errores (si existen).
V: volver; A: anterior; G: guardar; S: siguiente; X: cerrar.

## EquipmentMenu (con armadura)

```text
 # |  # |  # |  # |  4 |  # |  # |  # |  #
 # | 10 | 11 | 12 |  . | 14 | 15 | 16 |  #
 # | 19 | 20 | 21 |  . | 23 | 24 | 25 |  #
 # |  I |  I |  I |  . |  I |  I |  I |  #
 # |  . |  . |  . |  . |  . |  . |  . | 44
 V |  # |  # |  A |  G |  S |  # |  # |  X
```

4 Etiqueta de depósitos; 10 Mano principal; 11 Mano secundaria; 12 Casco; 14 Pechera; 15 Pantalones; 16 Botas; 19 Drop mano principal; 20 Drop mano secundaria; 21 Drop casco; 23 Drop pechera; 24 Drop pantalones; 25 Drop botas; 44 Errores (si existen).
V: volver; A: anterior; G: guardar; S: siguiente; X: cerrar.

Probabilidad de drop solo para equipo configurado. Cada I está alineada bajo el icono que corresponde.

## EquipmentMenu (sin armadura)

```text
 # |  # |  # |  # |  4 |  # |  # |  # |  #
 # |  . |  . | 12 |  . | 14 |  . |  . |  #
 # |  . |  . | 21 |  . | 23 |  . |  . |  #
 # |  . |  . |  I |  . |  I |  . |  . |  #
 # |  . |  . |  . | 40 |  . |  . |  . | 44
 V |  # |  # |  A |  G |  S |  # |  # |  X
```

4 Etiqueta de depósitos; 12 Mano principal; 14 Mano secundaria; 21 Drop mano principal; 23 Drop mano secundaria; 40 Aviso de armadura incompatible; 44 Errores (si existen).
V: volver; A: anterior; G: guardar; S: siguiente; X: cerrar.

## EnchantMenu

```text
 # |  # |  # |  # |  # |  # |  # |  # |  #
 # |  L |  L |  L |  L |  L |  L |  L |  #
 # |  L |  L |  L |  L |  L |  L |  L |  #
 # |  L |  L |  L |  L |  L |  L |  L |  #
 # |  L |  L |  L |  L |  L |  L |  L | 44
 V |  # |  # |  A |  G |  S |  # |  # |  X
```

Sin acción en la cabecera.
V: volver; A: anterior; G: guardar; S: siguiente; X: cerrar.

Se muestra una página completa; cada fila parcial se centra según GuiLayout.centeredRow.

44: errores de validación, solo cuando existen.

## PotionMenu

```text
 # |  # |  # |  # |  4 |  # |  # |  # |  #
 # |  L |  L |  L |  L |  L |  L |  L |  #
 # |  L |  L |  L |  L |  L |  L |  L |  #
 # |  L |  L |  L |  L |  L |  L |  L |  #
 # |  L |  L |  L |  L |  L |  L |  L | 44
 V |  # |  # |  A |  G |  S |  # |  # |  X
```

4 Añadir.
V: volver; A: anterior; G: guardar; S: siguiente; X: cerrar.

Se muestra una página completa; cada fila parcial se centra según GuiLayout.centeredRow.

44: errores de validación, solo cuando existen.

## PotionMenu: editor de efecto

```text
 # |  # |  # |  # |  # |  # |  # |  # |  #
 # |  . | 11 |  . | 13 |  . | 15 |  . |  #
 # |  . |  . |  . |  . |  . |  . |  . |  #
 # |  . |  . |  . |  . |  . |  . |  . |  #
 # |  . |  . |  . |  . |  . |  . |  . | 44
 V |  # |  # |  A |  G |  S |  # |  # |  X
```

11 Tipo de poción; 13 Nivel; 15 Partículas; 44 Errores (si existen).
V: volver; A: anterior; G: guardar; S: siguiente; X: cerrar.

## AbilityListMenu

```text
 # |  # |  # |  # |  4 |  # |  # |  # |  #
 # |  L |  L |  L |  L |  L |  L |  L |  #
 # |  L |  L |  L |  L |  L |  L |  L |  #
 # |  L |  L |  L |  L |  L |  L |  L |  #
 # |  L |  L |  L |  L |  L |  L |  L | 44
 V |  # |  # |  A |  G |  S |  # |  # |  X
```

4 Añadir.
V: volver; A: anterior; G: guardar; S: siguiente; X: cerrar.

Se muestra una página completa; cada fila parcial se centra según GuiLayout.centeredRow.

44: errores de validación, solo cuando existen.

## AbilityPickerMenu

```text
 # |  # |  # |  # |  # |  # |  # |  # |  #
 # |  L |  L |  L |  L |  L |  L |  L |  #
 # |  L |  L |  L |  L |  L |  L |  L |  #
 # |  L |  L |  L |  L |  L |  L |  L |  #
 # |  L |  L |  L |  L |  L |  L |  L |  #
 V |  # |  # |  A |  G |  S |  # |  # |  X
```

Sin acción en la cabecera.
V: volver; A: anterior; G: guardar; S: siguiente; X: cerrar.

Se muestra una página completa; cada fila parcial se centra según GuiLayout.centeredRow.

## ParamEditorMenu

```text
 # |  # |  # |  # |  # |  # |  # |  # |  #
 # |  L |  L |  L |  L |  L |  L |  L |  #
 # |  L |  L |  L |  L |  L |  L |  L |  #
 # |  L |  L |  L |  L |  L |  L |  L |  #
 # |  L |  L |  L |  L |  L |  L |  L | 44
 V |  # |  # |  A |  G |  S |  # |  # |  X
```

Sin acción en la cabecera.
V: volver; A: anterior; G: guardar; S: siguiente; X: cerrar.

Se muestra una página completa; cada fila parcial se centra según GuiLayout.centeredRow.

44: errores de validación, solo cuando existen.

## ComboMenu: lista

```text
 # |  # |  # |  # |  4 |  # |  # |  # |  #
 # |  L |  L |  L |  L |  L |  L |  L |  #
 # |  L |  L |  L |  L |  L |  L |  L |  #
 # |  L |  L |  L |  L |  L |  L |  L |  #
 # |  L |  L |  L |  L |  L |  L |  L | 44
 V |  # |  # |  A |  G |  S |  # |  # |  X
```

4 Añadir.
V: volver; A: anterior; G: guardar; S: siguiente; X: cerrar.

Se muestra una página completa; cada fila parcial se centra según GuiLayout.centeredRow.

44: errores de validación, solo cuando existen.

## ComboMenu: editor (ejemplo con 5 pasos)

```text
 # |  # |  # |  # |  4 |  # |  # |  # |  #
 # | 10 | 11 | 12 | 13 | 14 | 15 | 16 |  #
 # |  . | 20 | 21 | 22 | 23 | 24 |  . |  #
 # |  . | 29 | 30 | 31 | 32 | 33 |  . |  #
 # |  . | 38 | 39 | 40 | 41 | 42 |  . | 44
 V |  # |  # |  A |  G |  S |  # |  # |  X
```

4 Cabecera Secuencia; 10 Id; 11 Disparador; 12 Valor disparador; 13 Objetivo; 14 Rango; 15 Cooldown; 16 Añadir / aviso del máximo; 20 Paso 1; 21 Paso 2; 22 Paso 3; 23 Paso 4; 24 Paso 5; 29 Retardo 1; 30 Retardo 2; 31 Retardo 3; 32 Retardo 4; 33 Retardo 5; 38 Bajar paso 1; 39 Bajar paso 2; 40 Bajar paso 3; 41 Bajar paso 4; 42 Último paso (inactivo); 44 Errores (si existen).

V/A/G/S/X: barra inferior fija. Para 2–4 pasos se centran las columnas. Cada paso conserva alineados su retardo y control de orden. El último control es gris; con cinco pasos, añadir también es gris.

## PhaseListMenu

```text
 # |  # |  # |  # |  4 |  # |  # |  # |  #
 # |  L |  L |  L |  L |  L |  L |  L |  #
 # |  L |  L |  L |  L |  L |  L |  L |  #
 # |  L |  L |  L |  L |  L |  L |  L |  #
 # |  L |  L |  L |  L |  L |  L |  L | 44
 V |  # |  # |  A |  G |  S |  # |  # |  X
```

4 Añadir.
V: volver; A: anterior; G: guardar; S: siguiente; X: cerrar.

Se muestra una página completa; cada fila parcial se centra según GuiLayout.centeredRow.

44: errores de validación, solo cuando existen.

## PhaseMenu

```text
 # |  # |  # |  # |  4 |  # |  # |  # |  #
 # |  . | 11 |  . | 13 |  . | 15 |  . |  #
 # | 19 |  . | 21 |  . | 23 |  . | 25 |  #
 # |  . | 29 |  . | 31 |  . | 33 |  . |  #
 # |  . | 38 |  . | 40 |  . | 42 |  . | 44
 V |  # |  # |  A |  G |  S |  # |  # |  X
```

4 Cabecera y explicación de filas; 11 Umbral; 13 Reemplazar habilidades; 15 Curación; 19 Habilidades; 21 Combos; 23 Equipo; 25 Pociones; 29 Título; 31 Subtítulo; 33 Invulnerabilidad; 38 Sonido; 40 Música; 42 Invocaciones; 44 Errores (si existen).

V/A/G/S/X: barra inferior fija.

## PhaseMenu: lista de invocaciones

```text
 # |  # |  # |  # |  4 |  # |  # |  # |  #
 # |  L |  L |  L |  L |  L |  L |  L |  #
 # |  L |  L |  L |  L |  L |  L |  L |  #
 # |  L |  L |  L |  L |  L |  L |  L |  #
 # |  L |  L |  L |  L |  L |  L |  L | 44
 V |  # |  # |  A |  G |  S |  # |  # |  X
```

4 Añadir.
V: volver; A: anterior; G: guardar; S: siguiente; X: cerrar.

Se muestra una página completa; cada fila parcial se centra según GuiLayout.centeredRow.

44: errores de validación, solo cuando existen.

## PhaseMenu: editor de invocación

```text
 # |  # |  # |  # |  # |  # |  # |  # |  #
 # |  . | 11 |  . | 13 |  . | 15 |  . |  #
 # |  . |  . |  . |  . |  . |  . |  . |  #
 # |  . |  . |  . |  . |  . |  . |  . |  #
 # |  . |  . |  . |  . |  . |  . |  . | 44
 V |  # |  # |  A |  G |  S |  # |  # |  X
```

11 Plantilla; 13 Cantidad; 15 Retardo; 44 Errores (si existen).
V: volver; A: anterior; G: guardar; S: siguiente; X: cerrar.

## MobMenuBase.choose: selectores de disparador, objetivo, sonido, poción, partícula y plantilla

```text
 # |  # |  # |  # |  4 |  # |  # |  # |  #
 # |  L |  L |  L |  L |  L |  L |  L |  #
 # |  L |  L |  L |  L |  L |  L |  L |  #
 # |  L |  L |  L |  L |  L |  L |  L |  #
 # |  L |  L |  L |  L |  L |  L |  L |  #
 V |  # |  # |  A |  G |  S |  # |  # |  X
```

4 Buscar.
V: volver; A: anterior; G: guardar; S: siguiente; X: cerrar.

Se muestra una página completa; cada fila parcial se centra según GuiLayout.centeredRow.

## Inputs.numberWithClicks: entrada numérica

```text
 # |  # |  # |  # |  # |  # |  # |  # |  #
 # | 10 |  . |  . | 13 |  . |  . | 16 |  #
 # |  . |  . |  . |  . |  . |  . |  . |  #
 # |  . |  . |  . |  . |  . |  . |  . |  #
 # |  . |  . |  . |  . |  . |  . |  . |  #
 V |  # |  # |  A |  G |  S |  # |  # |  X
```

10 Reducir; 13 Valor actual; 16 Aumentar.
V: volver; A: anterior; G: guardar; S: siguiente; X: cerrar.

Shift cambia el paso de 1 a 10. Guardar acepta; Volver restaura el menú de origen. Las entradas de texto, confirmación y slider son diálogos de Paper y no usan rejilla.

## Capacidad y compatibilidad

La lista principal, Salas y Oleadas muestran 21 entradas por página; el editor de Oleada muestra 14; la lista de spawners de Sala y las demás listas, 28. Reservar filas para controles cambia la paginación, sin modificar el orden ni la selección de los datos. Las alertas de validación son excepciones al borde decorativo y conservan el rojo para distinguirlas.

## Migración de mensajes

T31 publica los catálogos ES/EN como versión 4. Los snapshots `defaults-history/messages-v3.yml` y `messages_en-v3.yml` son copias exactas de los recursos anteriores a T31. La migración actualiza textos por defecto reconocidos, añade las claves nuevas y conserva personalizaciones. No cambia los contratos compartidos ni la implementación de la migración.
