# Habilidades registradas

Referencia del código de esta rama para preparar T18. `Abilities.registerDefaults` llama a CoreAbilities, CustomAbilitiesB, BorrowedAbilitiesA, BorrowedAbilitiesC, CustomAbilitiesA, BorrowedAbilitiesB y GenericAbilities: **43 ids únicos**. `on_hit_effect` se registra una sola vez en CoreAbilities.

Los nombres proceden de `src/main/resources/messages.yml`, clave `ability.<id>.name`; se omite el código de color `&6`. Las tres habilidades de CoreAbilities no tienen esa clave en este snapshot: se indica su ausencia sin inventar una traducción.

Los disparadores son recomendaciones de uso, no valores predeterminados impuestos por el registro. Se muestra también el enum usado en YAML. `EVERY_X_SECONDS` requiere `triggerValue` en segundos; `HEALTH_BELOW`, un porcentaje. Para habilidades que necesitan el evento de daño o muerte, usar el disparador indicado y aviso `telegraphTicks: 0`, para conservar la causa y actuar durante el evento.

La última columna enumera **cada ParamSpec** como `clave (tipo): default / min / max`. Los límites son inclusivos. `TICKS`: 20 ticks ≈ 1 segundo; daño y curación: puntos de vida (2 = un corazón); distancias: bloques; amplificador: 0 = nivel I. Los límites `0 / 0` de texto, booleano, poción o plantilla son los metadatos literales del código, no un rango numérico aplicable. `""` exige elegir una plantilla válida para invocarla.

| Id | Nombre en messages.yml | Disparador recomendado | Parámetros: default / min / max |
|---|---|---|---|
| `lightning` | **Clave ausente** (`ability.lightning.name`) | CADA_X_SEG (`EVERY_X_SECONDS`) | `damage` (DOUBLE): `6.0` / `0` / `1000` |
| `on_hit_effect` | **Clave ausente** (`ability.on_hit_effect.name`) | AL_GOLPEAR (`ON_HIT`) | `effect` (POTION_EFFECT): `"minecraft:poison"` / `0` / `0`<br>`amplifier` (INT): `0` / `0` / `255`<br>`seconds` (DOUBLE): `5.0` / `0.05` / `3600` |
| `summon_minions` | **Clave ausente** (`ability.summon_minions.name`) | CADA_X_SEG (`EVERY_X_SECONDS`) | `template` (MOB_TEMPLATE): `""` / `0` / `0`<br>`count` (INT): `2` / `1` / `50`<br>`radius` (DOUBLE): `3.0` / `0` / `32` |
| `thief` | Ladrón | AL_GOLPEAR (`ON_HIT`) | `fleeTicks` (TICKS): `60` / `1` / `1200`<br>`speedAmplifier` (INT): `1` / `0` / `10` |
| `vampirism` | Vampirismo | AL_GOLPEAR (`ON_HIT`) | `percent` (DOUBLE): `25.0` / `0` / `100` |
| `healer` | Sanador | CADA_X_SEG (`EVERY_X_SECONDS`) | `radius` (DOUBLE): `8.0` / `0` / `32`<br>`heal` (DOUBLE): `4.0` / `0` / `1000` |
| `enrage` | Enfurecer | VIDA_BAJO_% (`HEALTH_BELOW`) | `strengthAmplifier` (INT): `1` / `0` / `10`<br>`speedAmplifier` (INT): `1` / `0` / `10` |
| `minion_shield` | Escudo de esbirros | AL_RECIBIR_DAÑO (`ON_DAMAGED`) | `template` (MOB_TEMPLATE): `""` / `0` / `0`<br>`count` (INT): `3` / `1` / `20` |
| `reflect` | Reflejo | AL_RECIBIR_DAÑO (`ON_DAMAGED`) | `speed` (DOUBLE): `1.5` / `0.1` / `4` |
| `meteors` | Meteoritos | CADA_X_SEG (`EVERY_X_SECONDS`) | `count` (INT): `3` / `1` / `10`<br>`radius` (DOUBLE): `3.0` / `0.5` / `16`<br>`damage` (DOUBLE): `8.0` / `0` / `1000`<br>`telegraphTicks` (TICKS): `40` / `1` / `1200` |
| `earthquake` | Terremoto | CADA_X_SEG (`EVERY_X_SECONDS`) | `radius` (DOUBLE): `6.0` / `1` / `16`<br>`damage` (DOUBLE): `6.0` / `0` / `1000`<br>`up` (DOUBLE): `0.7` / `0` / `3` |
| `last_breath` | Último aliento | AL_MORIR (`ON_DEATH`) | `mode` (STRING): `"EXPLOSION"` / `0` / `0`<br>`template` (MOB_TEMPLATE): `""` / `0` / `0`<br>`radius` (DOUBLE): `5.0` / `0` / `16`<br>`damage` (DOUBLE): `10.0` / `0` / `1000` |
| `double` | Doble | VIDA_BAJO_% (`HEALTH_BELOW`) | `factor` (DOUBLE): `0.5` / `0.05` / `0.99` |
| `wither_skulls` | Cráneos del Wither | CADA_X_SEG (`EVERY_X_SECONDS`) | `count` (INT): `1` / `1` / `5`<br>`blue` (BOOLEAN): `false` / `0` / `0`<br>`witherSeconds` (DOUBLE): `5.0` / `0.05` / `3600`<br>`damage` (DOUBLE): `8.0` / `0` / `1000` |
| `wither_shockwave` | Onda del Wither | CADA_X_SEG (`EVERY_X_SECONDS`) | `radius` (DOUBLE): `5.0` / `0.5` / `32`<br>`damage` (DOUBLE): `6.0` / `0` / `1000`<br>`knockback` (DOUBLE): `1.2` / `0` / `5` |
| `dragon_breath` | Aliento del Dragón | CADA_X_SEG (`EVERY_X_SECONDS`) | `radius` (DOUBLE): `3.0` / `0.5` / `16`<br>`durationTicks` (TICKS): `100` / `1` / `1200`<br>`damagePerHit` (DOUBLE): `1.0` / `0` / `100` |
| `dragon_roar` | Rugido del Dragón | CADA_X_SEG (`EVERY_X_SECONDS`) | `radius` (DOUBLE): `8.0` / `0.5` / `32`<br>`knockback` (DOUBLE): `1.5` / `0` / `5`<br>`up` (DOUBLE): `0.5` / `0` / `3` |
| `sonic_boom` | Sonic Boom | CADA_X_SEG (`EVERY_X_SECONDS`) | `damage` (DOUBLE): `10.0` / `0` / `1000`<br>`range` (DOUBLE): `16.0` / `0.5` / `64` |
| `darkness_pulse` | Pulso de Oscuridad | CADA_X_SEG (`EVERY_X_SECONDS`) | `radius` (DOUBLE): `8.0` / `0.5` / `32`<br>`seconds` (DOUBLE): `5.0` / `0.05` / `3600` |
| `evoker_fangs` | Colmillos del Evoker | CADA_X_SEG (`EVERY_X_SECONDS`) | `pattern` (STRING): `"LINE"` / `0` / `0`<br>`count` (INT): `8` / `1` / `32`<br>`damage` (DOUBLE): `6.0` / `0` / `1000` |
| `summon_vexes` | Invocar Vexes | CADA_X_SEG (`EVERY_X_SECONDS`) | `count` (INT): `3` / `1` / `16`<br>`lifetimeSeconds` (DOUBLE): `30.0` / `0.05` / `300` |
| `ender_blink` | Teletransporte de Enderman | JUGADOR_EN_RANGO (`PLAYER_IN_RANGE`) | `distance` (DOUBLE): `2.0` / `1` / `4` |
| `roar_knockback` | Rugido con empuje | CADA_X_SEG (`EVERY_X_SECONDS`) | `knockback` (DOUBLE): `1.5` / `0` / `5`<br>`up` (DOUBLE): `0.4` / `0` / `3` |
| `launch_up` | Lanzar al aire | CADA_X_SEG (`EVERY_X_SECONDS`) | `power` (DOUBLE): `1.2` / `0` / `3` |
| `cobweb` | Telaraña | CADA_X_SEG (`EVERY_X_SECONDS`) | `ttl` (TICKS): `100` / `1` / `1200` |
| `split_on_death` | División al morir | AL_MORIR (`ON_DEATH`) | `count` (INT): `2` / `1` / `8`<br>`scaleFactor` (DOUBLE): `0.5` / `0.1` / `0.9`<br>`healthFactor` (DOUBLE): `0.5` / `0.1` / `0.9` |
| `blindness` | Ceguera | CADA_X_SEG (`EVERY_X_SECONDS`) | `seconds` (DOUBLE): `5.0` / `0.05` / `60` |
| `hook` | Gancho | CADA_X_SEG (`EVERY_X_SECONDS`) | `power` (DOUBLE): `1.2` / `0` / `3`<br>`up` (DOUBLE): `0.2` / `0` / `2` |
| `anchor` | Anclar | CADA_X_SEG (`EVERY_X_SECONDS`) | `ticks` (TICKS): `60` / `1` / `1200` |
| `freeze` | Congelar | CADA_X_SEG (`EVERY_X_SECONDS`) | `ticks` (TICKS): `200` / `1` / `1200` |
| `swap` | Intercambio | CADA_X_SEG (`EVERY_X_SECONDS`) | Sin parámetros específicos. |
| `chaos` | Caos | CADA_X_SEG (`EVERY_X_SECONDS`) | Sin parámetros específicos. |
| `disarm` | Desarme | AL_GOLPEAR (`ON_HIT`) | `distance` (DOUBLE): `4.0` / `3` / `5`<br>`pickupDelay` (TICKS): `40` / `0` / `200` |
| `blaze_volley` | Ráfaga de Blaze | CADA_X_SEG (`EVERY_X_SECONDS`) | `count` (INT): `3` / `1` / `16`<br>`spreadDeg` (DOUBLE): `20.0` / `0` / `180` |
| `ghast_fireball` | Bola de fuego de Ghast | CADA_X_SEG (`EVERY_X_SECONDS`) | `yield` (DOUBLE): `2.0` / `0.5` / `16`<br>`damage` (DOUBLE): `6.0` / `0` / `1000` |
| `wind_charge` | Carga de viento | CADA_X_SEG (`EVERY_X_SECONDS`) | `power` (DOUBLE): `1.5` / `0` / `5` |
| `breeze_leap` | Salto de Breeze | JUGADOR_EN_RANGO (`PLAYER_IN_RANGE`) | `height` (DOUBLE): `0.8` / `0` / `3` |
| `shulker_bullet` | Bala de Shulker | CADA_X_SEG (`EVERY_X_SECONDS`) | `levitationSeconds` (DOUBLE): `5.0` / `0.05` / `3600` |
| `elder_curse` | Fatiga del Elder Guardian | CADA_X_SEG (`EVERY_X_SECONDS`) | `seconds` (DOUBLE): `10.0` / `0.05` / `3600`<br>`amplifier` (INT): `2` / `0` / `255` |
| `guardian_beam` | Rayo de Guardian | CADA_X_SEG (`EVERY_X_SECONDS`) | `chargeTicks` (TICKS): `40` / `1` / `1200`<br>`damage` (DOUBLE): `6.0` / `0` / `1000` |
| `creeper_blast` | Explosión de Creeper | CADA_X_SEG (`EVERY_X_SECONDS`) | `radius` (DOUBLE): `4.0` / `0.5` / `16`<br>`damage` (DOUBLE): `10.0` / `0` / `1000`<br>`fuseTicks` (TICKS): `30` / `1` / `1200` |
| `witch_potions` | Pociones de Bruja | CADA_X_SEG (`EVERY_X_SECONDS`) | `effect` (POTION_EFFECT): `"minecraft:poison"` / `0` / `0`<br>`amplifier` (INT): `0` / `0` / `255`<br>`seconds` (DOUBLE): `5.0` / `0.05` / `3600` |
| `arrow_effect` | Flechas con efecto | CADA_X_SEG (`EVERY_X_SECONDS`) | `effect` (POTION_EFFECT): `"minecraft:poison"` / `0` / `0`<br>`amplifier` (INT): `0` / `0` / `255`<br>`seconds` (DOUBLE): `5.0` / `0.05` / `3600` |

## Parámetros comunes y detalles de uso

`AbilityInstance` define además `trigger`, `triggerValue`, `target`, `range`, `cooldownTicks`, `chance`, `telegraphTicks` y `params`. No son ParamSpec de cada habilidad y no tienen defaults/min/max declarados en `Abilities.registerDefaults`. `chance` usa 0–1; cooldown y aviso usan ticks. El rango del selector es independiente de parámetros específicos como `sonic_boom.range` o los radios de área.

- `evoker_fangs.pattern`: `LINE` o `CIRCLE`.
- `last_breath.mode`: `EXPLOSION` o `SUMMON`; en el segundo caso indicar `template`.
- `witch_potions` y `arrow_effect` comparten `BorrowedAbilitiesB.potionParams()`; `on_hit_effect` declara los mismos tres parámetros directamente.
- `minion_shield` necesita `ON_DAMAGED` para cancelar el daño mientras vivan los esbirros; una activación al aparecer por sí sola no cancela impactos posteriores.
- `reflect` actúa ante proyectiles durante el evento de daño; `thief` y `vampirism` requieren un impacto atribuido al mob sobre un participante. `split_on_death` y `last_breath` requieren el evento de muerte.

Fuentes locales: [registro](../../src/main/java/dev/dasan/customdungeons/ability/Abilities.java), [implementaciones](../../src/main/java/dev/dasan/customdungeons/ability/impl/), [ParamSpec](../../src/main/java/dev/dasan/customdungeons/ability/ParamSpec.java), [AbilityInstance](../../src/main/java/dev/dasan/customdungeons/model/AbilityInstance.java) y [mensajes](../../src/main/resources/messages.yml). Esta referencia no certifica las pruebas integradas de las habilidades.
