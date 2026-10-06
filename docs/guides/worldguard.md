# Protección del mundo de dungeons con WorldGuard

Propuesta para T18 punto 6, WorldGuard 7.0.19. Estos comandos quedan documentados para la integración posterior; no se aplicaron en la preparación. La protección del terreno corresponde a WorldGuard (RF-INT-03).

## Flags para todo cd_dungeons

Usa `__global__` en el mundo dedicado; `-w` evita modificar el mundo donde esté el administrador. Para una región seleccionada, sustituye `__global__` por su nombre en cada comando.

```text
/rg flag -w cd_dungeons __global__ block-break deny
/rg flag -w cd_dungeons __global__ block-place deny
/rg flag -w cd_dungeons __global__ tnt deny
/rg flag -w cd_dungeons __global__ creeper-explosion deny
/rg flag -w cd_dungeons __global__ enderpearl deny
/rg flag -w cd_dungeons __global__ chorus-fruit-teleport deny
/rg flag -w cd_dungeons __global__ mob-spawning deny
```

Se recomienda denegar perlas para impedir saltos de sala. Si el diseño las necesita, cambia únicamente esa flag:

```text
/rg flag -w cd_dungeons __global__ enderpearl allow
```

El control de puertas cerradas de CustomDungeons sigue siendo necesario. Ver la [referencia oficial de flags](https://worldguard.enginehub.org/en/latest/regions/flags/). No deniegues `mob-damage`, ni actives `invincible`, ni bloquees `item-drop`/`item-pickup`: afectarían combate, vidas, inventarios y llaves. No establezcas `build deny` como sustituto general, pues abarca interacciones adicionales.

## Permitir mobs de plugins

`MobFactory` invoca con **`SpawnReason.CUSTOM`**. Esta razón solo evita la prohibición de spawn de WorldGuard si `mobs.block-plugin-spawning` está en `false`. La [configuración oficial](https://worldguard.enginehub.org/en/latest/config/#mobs) documenta `true` como valor predeterminado; el servidor inspeccionado también lo tiene en `true`.

Antes de las pruebas integradas, configura únicamente `plugins/WorldGuard/worlds/cd_dungeons/config.yml`, preservando sus otras claves:

```yaml
mobs:
  block-plugin-spawning: false
```

Recarga WorldGuard con `/wg reload`. Este cambio exceptúa spawns CUSTOM y COMMAND en ese mundo, no solo los de CustomDungeons; `mob-spawning deny` sigue bloqueando el spawn natural. El [listener de WorldGuard 7.0.19](https://github.com/EngineHub/WorldGuard/blob/7.0.19/worldguard-bukkit/src/main/java/com/sk89q/worldguard/bukkit/listener/WorldGuardEntityListener.java) comprueba esta excepción antes de consultar la flag. No se ha cambiado la configuración de WorldGuard en esta preparación.

## Comprobaciones pendientes

Prueba con un jugador sin bypass: no rompe ni coloca bloques, no usa perlas/chorus si están denegadas, combate y recoge llaves normalmente. Confirma mobs CUSTOM y puertas/telarañas temporales con restauración; las modificaciones directas por API no dependen de estas flags. Comprueba regiones superpuestas y prioridades si usas regiones por dungeon.

`creeper-explosion deny` puede suprimir también daño a entidades según `regions.explosion-flags-block-entity-damage`; verifica el daño de habilidades durante T18. Las flags documentadas no certifican aún el comportamiento de combate.
