# Entrada a dungeons con Multiverse-Portals

Preparación de T18 para Multiverse-Core 5.8.1 y Multiverse-Portals 5.3.0. La entrada real y los hooks se probarán cuando terminen T09, T14, T15 y T16; esta preparación no crea un portal ni certifica una partida.

## Crear y configurar la entrada

Como administrador, selecciona las dos esquinas del volumen de entrada con `//wand` de WorldEdit (ya instalado). Sin WorldEdit, usa `/mvp wand`. Sustituye `cripta` por el id de una dungeon habilitada con lobby y salida válidos:

```text
/mvp create entrada_cripta
/mvp modify entrada_cripta action-type command
/mvp modify entrada_cripta action "console:customdungeon join %player% cripta"
/mvp modify entrada_cripta action-success-message @disabled
/mvp info entrada_cripta
```

Haz la configuración antes de abrir el acceso. Con LuckPerms, concede al grupo que corresponda:

```text
/lp group default permission set multiverse.portal.access.entrada_cripta true
/lp group default permission set customdungeons.player.join true
```

Si la dungeon exige permiso específico, añade `customdungeons.join.cripta` a ese grupo. Conserva la comprobación de acceso del portal y los controles de entrada de CustomDungeons.

Según la [documentación de acciones](https://mvplugins.org/portals/how-to/configure-portal-actions/), el tipo es `command`; la acción no lleva `/` inicial. Sin prefijo se ejecuta como jugador; `console:` selecciona consola y `op:` selecciona ejecución como operador. Para esta integración se usa consola. El nombre del jugador se sustituye con **`%player%`** (también existe `%world%`), sin necesitar PlaceholderAPI. `{jugador}` expresa un argumento conceptual; `{player}` no es el placeholder de una acción de comando.

## Mensajes y hooks opcionales

Los mensajes de Multiverse-Portals sí usan `{player}` y `{portal}`, y aceptan `@disabled`. Ver [mensajes de portales](https://mvplugins.org/portals/how-to/configure-portal-messages/). `action-success-message` responde al éxito de ejecutar la acción: no demuestra que CustomDungeons haya admitido al jugador. Dejarlo deshabilitado evita duplicar o contradecir el motivo de rechazo de `join`.

RF-INT-02 llama **on-full** al gancho cuando se llena el lobby y **on-free** al de vuelta a LIBRE. Los comandos se ejecutarán como consola, sin `/`, con `{dungeon}`, `{players}` y `{max}`. T14 está pendiente: aún no se certifica su ejecución.

El `DefinitionCodec` de esta rama guarda los eventos bajo `hooks` con nombres del enum, **`FULL` y `FREE`**; no acepta literalmente `on-full` ni `on-free`. Ejemplo para el archivo de definición `plugins/CustomDungeons/dungeons/cripta.yml`, una vez esté disponible T14:

```yaml
hooks:
  FULL:
    - 'mvp modify entrada_cripta action-success-message "&cDungeon {dungeon} llena ({players}/{max})."'
  FREE:
    - 'mvp modify entrada_cripta action-success-message @disabled'
```

Esto cambia un mensaje; no bloquea el portal ni reemplaza la validación de `join`. Un mensaje específico de acceso denegado se configura por separado:

```text
/mvp modify entrada_cripta no-permission-message "&cEntrada cerrada, {player}."
```

Ese mensaje solo aparece si el acceso al portal se deniega por permisos. Para usarlo con FULL/FREE habría que sincronizar también esos permisos mediante hooks de consola, y comprobar que no quedan cerrados tras reset o recuperación. No se modifica ningún permiso ni portal durante esta preparación.
