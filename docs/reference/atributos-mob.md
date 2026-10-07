# Atributos de mob (T52)

El editor de mob abre **Atributos**; cada fase de jefe tiene el mismo acceso.
Clic izquierdo escribe el valor. Shift + clic derecho quita el override: en el
mob vuelve a vanilla; en una fase deja el atributo vigente sin cambios. Guardar
comparte el borrador del editor de mob. Los rangos y los avisos proceden de
`NumericRanges`, también usado al validar y normalizar las definiciones. Vida y
daño admiten notación científica y números finitos hasta 10³⁰; los campos
acotados conservan su precisión indicada en el diálogo.

El bloque opcional `attributes` admite estos campos tanto en la plantilla como
en cada entrada de `phases`. Omitir un campo no modifica el atributo. Un cero
explícito sí se aplica, salvo la vida, que debe ser positiva. Los campos antiguos
`max-health`, `damage`, `speed`, `knockback-resistance` y `scale` siguen funcionando;
su cero sigue significando vanilla. Cuando ambos formatos declaran un campo,
`attributes` tiene prioridad. El codec no añade ese bloque a YAML antiguos.

```yaml
entity-type: ZOMBIE
attributes:
  max-health: 5000
  damage: 3000
  speed: 0.3
  knockback-resistance: 0
  scale: 1
  armor: 30
  armor-toughness: 20
  follow-range: 48
  attack-knockback: 1
  jump-strength: 0.42
  gravity: 0.08
  step-height: 0.6
  explosion-knockback-resistance: 1
boss: true
phases:
  - health-threshold: 0.5
    attributes:
      damage: 4000
      armor: 20
    heal-percent: 10
```

| Campo | Valores |
|---|---|
| `max-health` | Finito > 0, hasta 10³⁰ |
| `damage` | Finito ≥ 0, hasta 10³⁰ |
| `speed` | 0–1024 |
| `knockback-resistance` | 0–1 |
| `scale` | 0–16; Minecraft aplica un mínimo efectivo de 0,0625 |
| `armor` | 0–30 |
| `armor-toughness` | 0–20 |
| `follow-range` | 0–2048 |
| `attack-knockback` | 0–5 |
| `jump-strength` | 0–32 |
| `gravity` | −1–1 |
| `step-height` | 0–10 |
| `explosion-knockback-resistance` | 0–1 |

«Sin límite» significa **hasta 10³⁰**, con margen para los cálculos en `float`
de Minecraft. El lore muestra «Sin límite (hasta 10³⁰)». Vida y daño comparten
el mismo máximo en Validator y entradas numéricas, que admiten notación científica.
La carga recorta valores superiores con aviso, también en los campos antiguos
y los atributos de las fases, sin reescribir el YAML.

Sobre 1024 se usa vida virtual: el PDC conserva el máximo y la vida restante
como `double` y es la única fuente de verdad. La vida física, limitada a 1024,
es un espejo proporcional. El daño final del evento se resta íntegro después
de las defensas, absorción e inmunidad de Paper. En los golpes que no agotarían
el espejo físico no se modifica el evento. La absorción se consume
exclusivamente por vanilla sobre sus unidades originales.
La curación del evento y la de las habilidades suman unidades virtuales.
La defensa numérica del listener acota los ataques procedentes de PDC antiguos
o externos antes de recalcular las defensas de Paper. Nunca entrega a
`setDamage` valores no finitos ni mayores que el rango de `float`. Si el cálculo
del evento no es finito o desborda `float`, considera el golpe letal cuando el
daño de entrada es al menos la vida virtual restante; lo normaliza como daño
físico finito con la misma fuente. En otro caso cancela el golpe, sin modificar
la vida ni ejecutar los efectos nativos. La predicción de fases usa esa misma
regla. La escritura de vida virtual rechaza `NaN`, también tras curaciones;
las sumas que desbordan se recortan al máximo finito del mob.

Paper escribe la vida física después de despachar el evento. Un único job
puntual compartido reconcilia todos los espejos pendientes en el siguiente turno
del hilo principal; no hay tareas repetitivas nuevas ni tareas por entidad.
Si el daño final mataría físicamente pero aún queda vida virtual, el listener
registra primero la resta original y limita únicamente BASE para que la resta
nativa en `float` deje al menos el suelo positivo. Habitualmente conserva los
modificadores de armadura, resistencia, absorción e inmunidad sin recalcularlos.
Si su suma con valores extremos no permite representar ese daño acotado, deja
el daño físico en cero: conserva ABSORPTION y anula los demás modificadores
físicos, sin cambiar la resta virtual original. Nunca permite un daño final
negativo, porque Paper lo sumaría a la absorción del mob. Solo este caso
límite altera `lastHurt` y la contabilidad nativa derivada de BASE; los golpes
ordinarios mantienen la semántica de Paper. No se entra en `die` ni en
`dropCustomDeathLoot`, y no se cancela `EntityDeathEvent`: así se evitan también
los cambios de durabilidad que Paper realiza antes del evento de muerte.
Cuando la vida virtual llega a cero, el golpe mata con su fuente original sin
consumir un tótem; `/kill` y el vacío la ponen a cero directamente.
`MobHealth.terminate` pone antes a cero la vida virtual en las terminaciones del
plugin: `skipwave` provoca la muerte normal; stop, recuperación y limpieza
retiran la entidad sin emitir una muerte ni drops.

El espejo tiene un suelo positivo representable en `float` mientras quede vida
virtual. Mientras falte vida virtual, el espejo queda por debajo del máximo
físico incluso después de redondearlo a `float` (como máximo, el `float` anterior
al máximo); así vanilla sigue emitiendo eventos de regeneración. Por encima
de 1 HP virtual, también supera 1 HP físico, para que
`PoisonMobEffect` siga emitiendo daño. El veneno resta hasta 1 HP virtual y no
puede aumentarla si ya era menor. BossBar, scoreboard, disparadores y fases usan
el mismo helper autoritativo, incluso entre el evento y la escritura física.
Un `setHealth` externo no sustituye la vida virtual; las curaciones externas
que emiten `EntityRegainHealthEvent` sí se contabilizan.

Para valores positivos menores que 1, Minecraft exige un máximo de atributo de
1: se usa el mismo espejo para conservar la vida configurada. El escalado por
jugadores se aplica también al máximo de una fase; cambiarlo conserva el
porcentaje de vida previo. Un producto que desborda `double` se satura en el mayor
número finito representable.

Las regresiones reproducen las operaciones del bytecode de Paper 26.3 build 157:
`LivingEntity.hurtServer` compara `lastHurt` antes de emitir eventos y lo obtiene
de BASE/BLOCKING/FREEZING/HARD_HAT; `actuallyHurt` consume ABSORPTION y resta
`(float) getFinalDamage()` de la vida física; `Mob.dropCustomDeathLoot` modifica
el equipo antes de `callEntityDeathEvent`; `RegenerationMobEffect` exige
vida física menor que el máximo; `PoisonMobEffect.applyEffectTick`
solo llama a `hurtServer` si la vida física supera 1. Los casos cubren golpes
iguales y mayores durante i-frames, el residuo de 5000,0001 − 5000, absorción
4 − 2 y veneno de 4 a 1 HP virtual; también la prevención de efectos previos
de muerte, el orden de terminación de `skipwave` y la regeneración con
100.000.000 − 1 HP. Las dimensiones de probar en vivo y los avisos de altura
usan `attributes.scale`, con el campo `scale` antiguo como respaldo para YAML
anteriores (su 0 conserva la escala vanilla; el 0 explícito en
`attributes.scale` usa el mínimo efectivo de Minecraft, 0,0625).

Sobre 2048 el atributo físico de ataque queda en 2048 y el listener fija el daño
base del golpe cuerpo a cuerpo. Siguen aplicándose las defensas del objetivo.
Proyectiles y daño de habilidades conservan sus propios valores. Solo se aplican
atributos que el tipo de entidad admite por la API pública de Paper.

La carga recorta valores finitos fuera de rango en memoria y publica avisos,
sin reescribir el archivo. Valores no finitos o de tipo inválido se rechazan.
La prueba reproducible con cliente bot se ejecuta, con el servidor de agentes
libre y detenido, mediante `scripts/test-t52-bots.sh --run`. El guion usa
exclusivamente el puerto 25566, despliega y arranca con `test-server.sh`, crea
fixtures únicos, verifica 4999 + 1 de vida, un golpe de 3000 y `/kill`, retira sus
definiciones y apaga su servidor. Evidencia temporal en `.agent/t52-bots/`.
