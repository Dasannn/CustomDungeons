# Atributos de mob (T52)

El editor de mob abre **Atributos**; cada fase de jefe tiene el mismo acceso.
Clic izquierdo escribe el valor. Shift + clic derecho quita el override: en el
mob vuelve a vanilla; en una fase deja el atributo vigente sin cambios. Guardar
comparte el borrador del editor de mob. Los rangos y los avisos proceden de
`NumericRanges`, también usado al validar y normalizar las definiciones. Vida y
daño admiten notación científica y cualquier número finito representable como
`double`; los campos acotados conservan su precisión indicada en el diálogo.

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
| `max-health` | Finito > 0, sin límite superior de edición |
| `damage` | Finito ≥ 0, sin límite superior de edición |
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

Sobre 1024 se usa vida virtual: la entidad tiene un máximo físico de 1024 y el
daño final recibido y la curación se convierten proporcionalmente. Un registro
PDC de la vida restante evita acumular errores del `float` de Minecraft. El mismo
helper sirve a BossBar, scoreboard, disparadores por vida y fases. Las curaciones
del plugin también usan unidades virtuales. `/kill` y el vacío evitan el escalado.
Para valores positivos menores que 1, Minecraft exige un máximo de atributo de
1: se usa la misma conversión para conservar la vida configurada. El escalado
por jugadores se aplica también al máximo de una fase; cambiarlo conserva el
porcentaje de vida previo. Un producto que desborda `double` se satura en el mayor
número finito representable.

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
