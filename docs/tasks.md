# Tareas — CustomDungeons MVP v1.0

Lee primero `docs/constitution.md`, `docs/spec.md`, `ARCHITECTURE.md` y `docs/plan.md` (restricciones globales y foco de revisión). Raíz de código: `src/main/java/dev/dasan/customdungeons/` (abreviado `…/`). Tests: `src/test/java/dev/dasan/customdungeons/`.

Estado: `[ ]` pendiente · `[~]` en curso · `[x]` integrada en `main`.

| Tarea | Depende de | Oleada |
|---|---|---|
| T01 Fundación y contratos | — | A |
| T02 Config, mensajes y definiciones | T01 | B |
| T03 Storage | T01 | B |
| T04 GUI base | T01 | B |
| T05 Motor de habilidades | T01 | B |
| T06 Mobs y jefes | T01 | B |
| T07 Núcleo de partida (lógica pura) | T01 | B |
| T08 Herramientas de admin | T02 | C |
| T09 Partida en runtime | T02, T03, T05, T06, T07 | C |
| T10–T13 Habilidades A–D | T05 (T13 también T06) | C |
| T14 Cierre de partida | T09 | D |
| T15 GUI de dungeons | T04, T08 | D |
| T17 GUI de mobs + probar en vivo | T04, T05, T06 | D |
| T16 Comandos | T08, T09, T14, T15, T17 | E |
| T18 Pruebas integradas + guías | todas | F |
| T19 Release | T18 | F |

---

## [ ] T01 — Fundación y contratos

**Objetivo:** proyecto que compila y carga en el servidor, con todos los contratos compartidos que permiten trabajar en paralelo.

**Archivos:**
- `settings.gradle.kts`, `build.gradle.kts`, `gradle/wrapper/*`, `gradlew`, `gradlew.bat`
- `src/main/resources/paper-plugin.yml`, `src/main/resources/config.yml` (claves completas de RF-CFG-01 con valores por defecto), `src/main/resources/messages.yml` (claves de esta tarea; el resto las añade cada tarea)
- `…/CustomDungeonsPlugin.java`, `…/CustomDungeonsLoader.java`
- `…/model/*.java` (records de abajo)
- `…/runtime/SessionContext.java`, `TempBlocks.java`, `TickScheduler.java`, `ActiveMob.java`
- `…/ability/Ability.java`, `ParamSpec.java`, `ParamType.java`, `ParamValues.java`, `AbilityContext.java`, `AbilityRegistry.java`, `Abilities.java`
- `…/text/Text.java`, `…/text/Messages.java`
- `…/config/PluginConfig.java` (record)
- `scripts/test-server.sh`
- Tests: `model/RegionTest.java`, `text/TextTest.java`, `ability/ParamValuesTest.java`

**Build:**
- Gradle 9.8.0 no está instalado: descarga `https://services.gradle.org/distributions/gradle-9.8.0-bin.zip` en `~/.local/share/gradle-dist/`, genera el wrapper con `gradle wrapper --gradle-version 9.8.0` y usa siempre `./gradlew` después.
- Toolchain Java 25. Repos: `mavenCentral()`, `https://repo.papermc.io/repository/maven-public/`, `https://jitpack.io`.
- `compileOnly("io.papermc.paper:paper-api:26.3.build.157-beta")`, `compileOnly("com.github.MilkBowl:VaultAPI:1.7.1") { isTransitive = false }`.
- `compileOnly` y `testImplementation` de HikariCP 7.1.0, sqlite-jdbc 3.53.4.0 y mysql-connector-j 26.7.0 (en runtime los carga `CustomDungeonsLoader` con `MavenLibraryResolver` usando `MavenLibraryResolver.MAVEN_CENTRAL_DEFAULT_MIRROR`).
- `testImplementation` de JUnit 5 (BOM 5.x más reciente) y de `paper-api` (para usar tipos en tests sin servidor).
- Tarea `jar` produce `build/libs/CustomDungeons-<version>.jar`. Versión `1.0.0-SNAPSHOT`.

**`paper-plugin.yml`:** `name: CustomDungeons`, `main`, `loader`, `api-version: '26.3'`, `dependencies.server` con Vault, WorldGuard, Multiverse-Core, Multiverse-Portals y LuckPerms (`load: BEFORE`, `required: false`), y **todos los permisos** de spec §12 declarados con descripción y `default` (`op` para admin, `true` para `customdungeons.player.*`), con `customdungeons.admin` y `customdungeons.player` como padres.

**Contratos (código exacto; los demás tareas dependen de estos nombres):**

```java
// model — todos records inmutables. Valores <= 0 en stats de MobTemplate = "usar vanilla".
public record BlockPos(int x, int y, int z) {}
public record Point(String world, double x, double y, double z, float yaw, float pitch) {}
public record Region(String world, BlockPos min, BlockPos max) {
    public static Region of(String world, BlockPos a, BlockPos b) { /* normaliza min/max */ }
    public boolean contains(String world, int x, int y, int z) { /* inclusivo */ }
    public long volume() { /* (dx+1)*(dy+1)*(dz+1) */ }
}
public enum UnlockMode { AUTOMATIC, KEY }
public enum SpawnMode { SIMULTANEOUS, SEQUENTIAL, STAGGERED, RANDOM }
public enum HookEvent { LOBBY_OPEN, FULL, START, COMPLETE, FAIL, FREE }
public enum Trigger { EVERY_X_SECONDS, ON_HIT, ON_DAMAGED, HEALTH_BELOW, ON_SPAWN, ON_DEATH, PLAYER_IN_RANGE }
public enum TargetMode { CURRENT_TARGET, NEAREST, RANDOM, ALL_IN_RADIUS }
public record WaveEntry(String templateId, int count, int delayTicks) {}
public record WaveDef(List<WaveEntry> entries, SpawnMode mode, int staggerIntervalTicks, int pauseAfterTicks) {}
public record SpawnerDef(String id, Point location, double radius, List<WaveDef> waves) {}
public record RoomDef(String id, Region region, Point checkpoint, @Nullable Region door,
                      UnlockMode unlock, @Nullable String keyCarrierTemplateId, List<SpawnerDef> spawners) {}
public record ScalingDef(double extraMobsPerPlayer, double extraHealthPerPlayer) {}      // 0.25, 0.15
public record RewardDef(List<ItemStack> items, double money, int xp, List<String> commands) {}
public record DungeonDef(String id, String displayName, boolean enabled, Point lobby, Point exit,
                         int minPlayers, int maxPlayers /*0 = sin límite*/, int lobbyCountdownSeconds,
                         int lives, boolean keepInventory, int timeLimitSeconds /*0 = sin límite*/,
                         int cooldownSeconds, boolean requirePermission, ScalingDef scaling,
                         Map<HookEvent, List<String>> hooks, RewardDef reward, List<RoomDef> rooms) {}
public record EquipmentDef(ItemStack item, float dropChance) {}
public record PotionDef(String effectKey, int amplifier, boolean particles) {}
public record AbilityInstance(String abilityId, Trigger trigger, double triggerValue, TargetMode target,
                              double range, int cooldownTicks, double chance, int telegraphTicks,
                              Map<String, Object> params) {}
public record ComboStep(String abilityId, Map<String, Object> params, int delayTicks) {}
public record ComboDef(String id, Trigger trigger, double triggerValue, TargetMode target, double range,
                       int cooldownTicks, List<ComboStep> steps) {}
public record PhaseDef(double healthThreshold /*0..1*/, boolean replaceAbilities,
                       List<AbilityInstance> abilities, List<ComboDef> combos,
                       Map<EquipmentSlot, EquipmentDef> equipment, List<PotionDef> potions,
                       double healPercent, List<WaveEntry> summons, @Nullable String title,
                       @Nullable String subtitle, @Nullable String soundKey, @Nullable String musicKey,
                       int invulnerableTicks) {}
public record MobTemplate(String id, String entityType /*namespaced key*/, String displayName,
                          double maxHealth, double damage, double speed, double knockbackResistance,
                          double scale, Map<EquipmentSlot, EquipmentDef> equipment, List<PotionDef> potions,
                          List<AbilityInstance> abilities, List<ComboDef> combos, boolean boss,
                          String bossBarColor, @Nullable String musicKey, List<PhaseDef> phases,
                          boolean vanillaDrops) {}
```

```java
// runtime
public interface TickScheduler { void runLater(int ticks, Runnable task); long currentTick(); }
public interface TempBlocks {
    /** Coloca solo si el bloque es aire. Restaura a aire tras ttlTicks o en restoreAll(). */
    boolean place(Block block, BlockData data, int ttlTicks);
    void restoreAll();
}
public interface SessionContext {
    UUID id();
    boolean isLiveTest();
    /** Únicos objetivos válidos de habilidades. */
    Collection<Player> players();
    @Nullable Region currentRoomRegion();
    Collection<ActiveMob> mobs();
    @Nullable ActiveMob spawnMinion(String templateId, Location at, ActiveMob owner);
    TempBlocks tempBlocks();
    TickScheduler scheduler();
    /** Ladrón: registra el ítem robado para devolverlo si no se recupera. */
    void onItemStolen(UUID owner, ItemStack item, ActiveMob thief);
}
public final class ActiveMob {
    public ActiveMob(Mob entity, MobTemplate template, SessionContext session) {...}
    public Mob entity(); public MobTemplate template(); public SessionContext session();
    public List<AbilityInstance> abilities();   // mutable: fases las cambian
    public List<ComboDef> combos();             // mutable
    public int phaseIndex(); public void phaseIndex(int i);
    public long invulnerableUntil(); public void invulnerableUntil(long tick);
    public boolean ready(String key, long now); public void cooldown(String key, long readyAt);
    public List<ItemStack> stolenItems();       // Ladrón
}
```

```java
// ability
public enum ParamType { INT, DOUBLE, BOOLEAN, TICKS, POTION_EFFECT, PARTICLE, SOUND, MOB_TEMPLATE, STRING }
public record ParamSpec(String key, ParamType type, Object defaultValue, double min, double max) {}
public final class ParamValues {
    public ParamValues(Map<String, Object> raw, List<ParamSpec> specs) {...}
    /** Devuelve el valor o el default; números fuera de [min,max] se recortan. */
    public int getInt(String key); public double getDouble(String key);
    public boolean getBoolean(String key); public String getString(String key);
}
public record AbilityContext(ActiveMob caster, List<LivingEntity> targets, ParamValues params,
                             SessionContext session, @Nullable Event cause) {}
public interface Ability {
    String id();                 // snake_case, p. ej. "wither_skulls"
    Material icon();
    List<ParamSpec> params();    // sin los comunes de AbilityInstance
    void execute(AbilityContext ctx);
}
public final class AbilityRegistry { void register(Ability a); Optional<Ability> get(String id); Collection<Ability> all(); }
public final class Abilities {
    public static void registerDefaults(AbilityRegistry r) {
        // Cada tarea de habilidades añade aquí UNA línea: XxxAbilities.register(r);
    }
}
```

```java
// text
public final class Text {
    /** Acepta &0-&f, &k-&o, &r, &#RRGGBB y MiniMessage mezclados. */
    public static Component parse(String input);
}
public final class Messages {
    public void load(YamlConfiguration yaml, String prefix);
    public Component get(String key, TagResolver... placeholders);   // clave ausente → "<key>" en rojo + warning una vez
    public void send(CommandSender to, String key, TagResolver... placeholders); // con prefijo
}
// config
public record PluginConfig(String prefix, String language, DatabaseSettings database, String dungeonWorld,
                           boolean autoCreateWorld, DungeonDefaults defaults, PerformanceLimits limits,
                           Set<EntityType> armorCapable, List<String> commandAliases, GuiSounds guiSounds,
                           Material doorMaterial, int liveTestMaxSeconds, Map<String, Integer> musicLengthTicks) {
    public record DatabaseSettings(String type, String host, int port, String database, String user,
                                   String password, int poolSize) {}
    public record DungeonDefaults(int lives, boolean keepInventory, int lobbyCountdownSeconds,
                                  int cooldownSeconds, int minPlayers, int maxPlayers, ScalingDef scaling) {}
    public record PerformanceLimits(int maxAliveMobsPerSession, double particleDensity, double effectViewRadius) {}
    public record GuiSounds(String click, String open, String save, String error) {}
}
```
`armor-capable-mobs` por defecto en `config.yml`: zombie, husk, drowned, zombie_villager, skeleton, stray, wither_skeleton, bogged, parched, piglin, piglin_brute, zombified_piglin.

**`CustomDungeonsPlugin`:** guarda servicios en campos con getters. `onEnable` crea `AbilityRegistry` y llama a `Abilities.registerDefaults`, carga `Messages`, y deja un bloque comentado `// --- registro de servicios (una línea por tarea) ---` donde cada tarea posterior añade su inicialización. `onDisable` vacío por ahora.

**`scripts/test-server.sh`:** `SERVER=~/Desktop/Proyectos/plugins/servidor/Servidor`, `JAVA=/usr/lib/jvm/temurin-25-jdk-arm64/bin/java`.
- `deploy`: `./gradlew jar` y copia a `$SERVER/plugins/CustomDungeons.jar`.
- `start`: `screen -dmS cd-test` con `$SERVER/start.sh`, y espera "Done (" en `logs/latest.log` (timeout de 180 s).
- `stop`: envía `stop` por screen y espera a que el proceso termine.
- `cmd "<x>"`: `screen -S cd-test -p 0 -X stuff "<x>\n"`.
- `log [n]`: `tail -n ${n:-80}` del log.
- Además, **actualiza el servidor** a Paper 26.3 build 157: descárgalo desde `https://fill.papermc.io/v3/projects/paper/versions/26.3/builds/157`, toma la URL de descarga del JSON, guarda `paper-26.3-157.jar` y cambia `start.sh` para usarlo. No borres el jar antiguo.

**Tests:**
```java
@Test void regionNormalizesAndContainsInclusive() {
    Region r = Region.of("w", new BlockPos(5, 10, 5), new BlockPos(0, 0, 0));
    assertEquals(new BlockPos(0,0,0), r.min()); assertEquals(new BlockPos(5,10,5), r.max());
    assertTrue(r.contains("w", 5, 10, 5)); assertTrue(r.contains("w", 0, 0, 0));
    assertFalse(r.contains("w", 6, 0, 0)); assertFalse(r.contains("other", 1, 1, 1));
    assertEquals(6L * 11 * 6, r.volume());
}
@Test void textParsesLegacyHexAndMiniMessage() {
    var plain = PlainTextComponentSerializer.plainText();
    Component c = Text.parse("&8[&6Custom&#FF8800Dungeons&8] <bold>hola</bold>");
    assertEquals("[CustomDungeons] hola", plain.serialize(c));
    assertEquals(TextColor.color(0xFF8800), /* color del span "Dungeons" */ ...);
}
@Test void paramValuesClampAndDefault() {
    var specs = List.of(new ParamSpec("radius", ParamType.DOUBLE, 3.0, 1, 10));
    assertEquals(3.0, new ParamValues(Map.of(), specs).getDouble("radius"));
    assertEquals(10.0, new ParamValues(Map.of("radius", 99), specs).getDouble("radius"));
}
```

**Aceptación:** `./gradlew build` en verde. `scripts/test-server.sh deploy && start` → el log muestra `CustomDungeons` habilitado sin errores en Paper 26.3-157. `stop` apaga limpio.

---

## [x] T02 — Config, mensajes y definiciones (RF-CFG, RF-MUN)

**Archivos:** `…/config/ConfigLoader.java`, `…/config/DefinitionStore.java`, `…/config/DefinitionCodec.java`, `…/config/Validator.java`, `…/config/ValidationError.java`, `src/main/resources/messages.yml` (claves `config.*` y `validation.*`), `src/main/resources/messages_en.yml`; tests `config/DefinitionCodecTest.java`, `config/ValidatorTest.java`, `config/ConfigLoaderTest.java`.

**Interfaces:**
- Consume: records de `model`, `PluginConfig`, `Messages`.
- Produce:
  - `PluginConfig ConfigLoader.load(YamlConfiguration)`: valores ausentes → defaults; inválidos → default + warning.
  - `DefinitionCodec`: `Map<String,Object> encode(DungeonDef)`, `DungeonDef decodeDungeon(String id, ConfigurationSection)`, y lo mismo para `MobTemplate`. Los `ItemStack` usan la serialización de Bukkit (`ConfigurationSerializable`).
  - `DefinitionStore`:
    - `void loadAll()`: síncrono al arrancar.
    - `Map<String,DungeonDef> dungeons()` y `Map<String,MobTemplate> mobs()`.
    - `CompletableFuture<Void> save(DungeonDef)` y `save(MobTemplate)`: escriben en async en `dungeons/<id>.yml` y `mobs/<id>.yml`.
    - `delete(...)` y `reload()`.
  - `List<ValidationError> Validator.validate(DungeonDef, Map<String,MobTemplate>)`, `validate(MobTemplate, PluginConfig, Set<String> abilityIds)`, `record ValidationError(String path, String messageKey, Map<String,String> args)`.

**Reglas del validador** (cada una con su test):
- id `[a-z0-9_-]{1,32}`;
- `minPlayers ≥ 1`; `maxPlayers == 0 || maxPlayers ≥ minPlayers`;
- `lives ≥ 1`;
- `lobby`/`exit` presentes;
- ≥ 1 sala, cada sala con región, checkpoint y ≥ 1 spawner con ≥ 1 oleada con ≥ 1 entrada;
- `count ≥ 1`;
- toda `templateId` existe (foco 4);
- `UnlockMode.KEY` exige `door != null` y un `keyCarrierTemplateId` presente en alguna oleada de esa sala;
- todas las salas salvo la última exigen `door`;
- en una plantilla: equipo de armadura solo si `entityType ∈ armorCapable`;
- `abilityId` registrado;
- fases con umbrales en (0,1) y estrictamente decrecientes;
- combos con 2–5 pasos.

**Carga tolerante (foco 4):** si `loadAll` encuentra una definición inválida, la registra con `enabled=false` y escribe un warning con la ruta del error; nunca lanza excepción.

**Tests:**
- `codecRoundTripDungeon()`: `DungeonDef` sin ítems → encode → decode, igual.
- `codecRoundTripMobWithPhasesAndCombos()`.
- Un test por regla del validador: `missingTemplateIsReported()`, `keyRoomWithoutCarrierIsReported()`, `armorOnWardenIsReported()`, `phaseThresholdsMustDecrease()`, etc.
- `invalidYamlLoadsDisabled()`.

**Aceptación:** tests en verde. Con el `dungeons/ejemplo.yml` incluido como recurso de test, `loadAll` carga una dungeon de 2 salas.

---

## [x] T03 — Storage (RF-BD, RNF-03)

**Archivos:** `…/storage/Storage.java` (interfaz), `…/storage/SqlStorage.java`, `…/storage/Migrations.java`, `…/storage/RunResult.java`, `…/storage/RunPlayerRecord.java`, `…/storage/PlayerStats.java`, `…/storage/ActiveSessionRecord.java`, `…/storage/TempBlockRecord.java`; test `storage/SqlStorageTest.java`.

**Interfaz (todas devuelven `CompletableFuture` ejecutado en un executor propio de un hilo para SQLite, o del tamaño del pool para MySQL):**
```java
public interface Storage extends AutoCloseable {
    CompletableFuture<Long> startRun(String dungeonId, Instant start, Collection<UUID> players);
    CompletableFuture<Void> finishRun(long runId, RunResult result, Instant end, List<RunPlayerRecord> players);
    CompletableFuture<Optional<Instant>> cooldownUntil(UUID player, String dungeonId);
    CompletableFuture<Void> setCooldown(UUID player, String dungeonId, Instant until);
    CompletableFuture<Void> addClaims(UUID player, List<ItemStack> items);
    CompletableFuture<List<ItemStack>> takeClaims(UUID player);           // atómico: lee y borra
    CompletableFuture<PlayerStats> stats(UUID player);
    CompletableFuture<Void> markActive(ActiveSessionRecord r);
    CompletableFuture<Void> clearActive(UUID sessionId);
    CompletableFuture<List<ActiveSessionRecord>> loadActive();
    CompletableFuture<Void> addTempBlock(TempBlockRecord r);
    CompletableFuture<Void> removeTempBlock(String world, int x, int y, int z);
    CompletableFuture<List<TempBlockRecord>> loadTempBlocks();
    CompletableFuture<Void> addPendingExit(UUID player, Point exit);
    CompletableFuture<Optional<Point>> takePendingExit(UUID player);
}
public enum RunResult { COMPLETED, FAILED, ABORTED }
public record RunPlayerRecord(UUID player, int kills, int deaths, boolean survived, boolean rewarded) {}
public record PlayerStats(int runs, int completions, int kills, int deaths) {}
public record ActiveSessionRecord(UUID sessionId, String dungeonId, Set<UUID> players, Point exit) {}
public record TempBlockRecord(String world, int x, int y, int z, String originalBlockData) {}
```
- Usa HikariCP. `SqlStorage.create(PluginConfig.DatabaseSettings, Path dataFolder)`: con `sqlite` usa `dataFolder/data.db` y `PRAGMA journal_mode=WAL`.
- Los `ItemStack` se guardan con `ItemStack.serializeItemsAsBytes`/`deserializeItemsFromBytes` en un BLOB.
- Migraciones versionadas en una tabla `schema_version`.
- Todo SQL debe ser compatible con SQLite y MySQL (sin `UPSERT` específico: usa `INSERT ... ON CONFLICT` en SQLite y `ON DUPLICATE KEY` en MySQL vía un `Dialect` interno de dos ramas).

**Tests** (SQLite en `@TempDir`, sin servidor):
- `cooldownRoundTrip()`.
- `runLifecycleUpdatesStats()`: 2 runs, uno completado → `stats.completions == 1`.
- `activeSessionsAndTempBlocksPersist()`: se cierra el storage, se reabre y los datos siguen.
- `pendingExitIsTakenOnce()`.
- `migrationsIdempotent()`: abrir dos veces no falla.
- Los claims con `ItemStack` necesitan servidor: se verifican en T18.

**Aceptación:** tests en verde. Ninguna llamada bloquea el hilo que la invoca.

---

## [x] T04 — GUI base (RF-GUI-01, 02, 04, 05)

**Archivos:** `…/gui/Menu.java`, `…/gui/Button.java`, `…/gui/PagedMenu.java`, `…/gui/MenuListener.java`, `…/gui/GuiTheme.java`, `…/gui/Inputs.java`, `…/gui/Draft.java`, `…/gui/EditLocks.java`; `messages.yml` claves `gui.common.*`; tests `gui/DraftTest.java`, `gui/EditLocksTest.java`, `gui/PagerMathTest.java`.

**Interfaces producidas:**
```java
public abstract class Menu implements InventoryHolder {
    protected Menu(Player viewer, Component title, int rows);           // rows 3..6
    protected abstract void render();                                    // usa set(slot, Button)
    protected final void set(int slot, Button b);
    public final void open(); public final void refresh();
    protected @Nullable Menu parent();                                   // para "Volver"
}
public record Button(ItemStack icon, ClickHandler onClick) {
    public interface ClickHandler { void handle(Player p, ClickType click); }
    public static Button of(Material m, Component name, List<Component> lore, ClickHandler h);
}
public abstract class PagedMenu<T> extends Menu { protected abstract List<T> items(); protected abstract Button button(T item); }
public final class GuiTheme {
    static void frame(Menu m);                                           // borde de cristal
    static void navBar(Menu m, @Nullable Runnable onSave, boolean hasPrev, boolean hasNext);
    // fila inferior: volver(45) · anterior(48) · guardar(49) · siguiente(50) · cerrar(53)
}
public final class Inputs {   // Dialog API de Paper
    static void number(Player p, Component title, double min, double max, double current, DoubleConsumer onSubmit);
    static void text(Player p, Component title, String current, int maxLength, Consumer<String> onSubmit);
    static void confirm(Player p, Component question, Runnable onYes);
}
public final class Draft<T> { Draft(T original); T get(); void set(T v); boolean dirty(); T original(); }
public final class EditLocks { boolean tryLock(String key, UUID admin); void unlock(String key, UUID admin); Optional<UUID> holder(String key); void releaseAll(UUID admin); }
```
- `MenuListener` cancela todos los clics y arrastres sobre inventarios cuyo holder es un `Menu`, y despacha al `Button`. Excepción: `Menu#allowsPlacement(int slot)` permite colocar ítems reales en las ranuras del editor de premio (T15) y de equipo (T17).
- Los sonidos salen de `PluginConfig.guiSounds`.
- Al cerrar o desconectarse se liberan los bloqueos (`EditLocks.releaseAll`).

**Tests:**
- `draftTracksDirty()`.
- `lockIsExclusiveAndReleasable()`.
- `pagerMath()`: 45 ítems con 28 por página → 2 páginas, rangos correctos.

**Aceptación:** tests en verde. En el servidor, un menú de prueba temporal (no se commitea) abre, pagina, pide un número con Dialog y se cierra sin que se puedan sacar ítems.

---

## [x] T05 — Motor de habilidades (RF-HAB-01..04, 09, 10)

**Archivos:** `…/ability/AbilityEngine.java`, `…/ability/TargetSelector.java`, `…/ability/Telegraph.java`, `…/ability/ComboRunner.java`, `…/ability/Effects.java`, `…/ability/impl/LightningAbility.java`, `…/ability/impl/OnHitEffectAbility.java`, `…/ability/impl/SummonMinionsAbility.java`; tests `ability/AbilityEngineTest.java`, `ability/ComboRunnerTest.java`, `ability/TargetSelectorTest.java`.

**Interfaces producidas:**
```java
public final class AbilityEngine {
    public AbilityEngine(AbilityRegistry registry, PluginConfig config);
    /** Llamado cada tick por el ticker dueño (SessionTicker o LiveTestService). */
    public void tick(Collection<ActiveMob> mobs, long tick);
    /** Llamado por listeners: ON_HIT, ON_DAMAGED, HEALTH_BELOW, ON_SPAWN, ON_DEATH. */
    public void fire(Trigger trigger, ActiveMob mob, @Nullable Event cause, long tick);
}
public final class TargetSelector { static List<LivingEntity> select(ActiveMob caster, TargetMode mode, double range); }
public final class Telegraph { static void show(SessionContext s, Location center, double radius, int ticks, Particle particle); }
public final class Effects {   // utilidades compartidas por todas las habilidades
    static void particles(SessionContext s, Location at, Particle p, int count, double spread);  // solo a jugadores en effectViewRadius
    static void sound(SessionContext s, Location at, Sound sound, float vol, float pitch);
    static void damage(LivingEntity target, double amount, ActiveMob source);                    // con atribución
    static void knockback(LivingEntity target, Location from, double strength, double up);
    static <T extends Projectile> T launch(ActiveMob caster, Class<T> type, Vector velocity);    // marca PDC customdungeons:ability_projectile
}
```
**Reglas:**
- **Objetivos (foco 5):** solo de `session.players()`. Además deben estar vivos, no en espectador ni en creativo, en el mismo mundo y a ≤ `range`.
- **Cooldowns:** clave `abilityId#índice` en `ActiveMob.ready/cooldown`.
- **Probabilidad:** `chance` en [0,1] con un `Random` inyectable para tests.
- **`EVERY_X_SECONDS`:** se ejecuta cada `triggerValue*20` ticks, desfasado por mob para no concentrar picos en el mismo tick.
- **`PLAYER_IN_RANGE`:** se comprueba cada 10 ticks, no cada tick.
- **`HEALTH_BELOW`:** una sola vez por mob al cruzar `triggerValue` (en %).
- **Telegraph:** si `telegraphTicks > 0`, primero se muestra el aviso y la ejecución se programa con `session.scheduler().runLater`. Si el caster muere antes, se cancela.
- **Proyectiles marcados:** `EntityExplodeEvent` vacía `blockList()`, `ExplosionPrimeEvent` pone `fire=false`, y `ProjectileHitEvent` contra bloques no prende fuego. Todo ello en un listener propio de esta tarea (`AbilityProtectionListener`).
- **`ComboRunner`:** ejecuta los pasos de un `ComboDef` con `delayTicks` entre ellos vía el scheduler. Lo cancela la muerte del mob. El cooldown del combo empieza al terminar el último paso.
- **3 habilidades de referencia,** que sirven de ejemplo para T10–T13:
  - `lightning` (daño configurable, `strikeLightningEffect`, sin fuego);
  - `on_hit_effect` (poción, nivel, duración, en `ON_HIT`);
  - `summon_minions` (plantilla, cantidad, radio, vía `session.spawnMinion`).
- **Registro:** `Abilities.registerDefaults` registra las 3 con una línea `CoreAbilities.register(r)`.

**Tests** (con stubs de `SessionContext`/`TickScheduler`; los tipos de Bukkit que haga falta se mockean con Mockito):
- `cooldownBlocksSecondExecution()`.
- `chanceZeroNeverFires()`.
- `healthBelowFiresOnce()`.
- `telegraphDelaysExecutionAndCancelsOnDeath()`.
- `comboRunsStepsInOrderWithDelays()`.
- `comboCancelledWhenCasterDies()`.
- `selectorExcludesNonSessionAndSpectators()` (foco 5).

Añade Mockito como `testImplementation`.

**Aceptación:** tests en verde. Dejar documentadas en Javadoc de `Ability` las convenciones para implementar una habilidad (id, parámetros, uso de `Effects`, prohibición de romper bloques).

---

## [x] T06 — Mobs y jefes (RF-MOB, RF-JEF, RF-PAR-16)

**Archivos:** `…/mob/MobFactory.java`, `…/mob/MobKeys.java`, `…/mob/BossController.java`, `…/mob/Scaling.java`; `messages.yml` claves `boss.*`; tests `mob/ScalingTest.java`, `mob/PhaseSelectionTest.java`.

**Interfaces producidas:**
```java
public final class MobKeys { static NamespacedKey SESSION, TEMPLATE, ABILITY_PROJECTILE, KEY_ITEM, TOOL; static boolean isDungeonMob(Entity e); }
public final class Scaling {
    static int count(int base, int players, int minPlayers, double extraPerPlayer);        // ceil(base*(1+extra*max(0,players-min)))
    static double healthMultiplier(int players, int minPlayers, double extraPerPlayer);   // 1+extra*max(0,players-min)
}
public final class MobFactory {
    public MobFactory(PluginConfig config);
    /** Spawnea, aplica stats/escala/equipo/pociones/drops, marca PDC. healthMultiplier >= 1. */
    public ActiveMob spawn(MobTemplate t, Location at, SessionContext session, double healthMultiplier);
}
public final class BossController {
    public BossController(MobFactory factory, Map<String, MobTemplate> templates);
    /** Llamar tras cada daño recibido por un jefe; aplica la siguiente fase si cruza umbral. */
    public void onDamaged(ActiveMob boss, long tick);
    public BossBar barFor(ActiveMob boss);            // creada perezosamente, color de plantilla
    public void startMusic(ActiveMob boss);           // musicKey de plantilla o fase, en bucle a session.players()
    public void stopMusic(ActiveMob boss);
    public void cleanup(ActiveMob boss);              // quita bossbar y música
}
```
**Reglas:**
- **Stats** por atributos: `MAX_HEALTH`, `ATTACK_DAMAGE`, `MOVEMENT_SPEED`, `KNOCKBACK_RESISTANCE` y `SCALE`. Un valor ≤ 0 en la plantilla significa no tocar el atributo.
- **Equipo:** solo si el tipo está en `armorCapable`. La mano principal y la secundaria se permiten en cualquier mob que tenga `EntityEquipment`.
- **Drops:** se anulan si `vanillaDrops=false` (lo aplica T09 en el evento de muerte, usando el PDC).
- **Mobs persistentes:** `setRemoveWhenFarAway(false)` y `setPersistent(false)`.
- **Fases:** al entrar en una fase se aplican sus cambios una sola vez y en orden:
  - `replaceAbilities` vacía las listas, si no se añaden;
  - equipo, pociones y curación del `healPercent`;
  - `summons` vía `session.spawnMinion`;
  - título y subtítulo a `session.players()` con `Text.parse`;
  - sonido y cambio de música;
  - invulnerabilidad de `invulnerableTicks`, que T09 respeta cancelando el daño mientras `tick < invulnerableUntil`.
- **Música:** `Player#playSound` con la clave de sonido (`Sound` vanilla o clave de resource pack) en la categoría `RECORDS`. Se repite cuando termina su duración, tomada de `PluginConfig.musicLengthTicks` por clave (`music-lengths` en `config.yml`, con un default de 2400 ticks). Esa repetición la dispara el ticker dueño llamando a `BossController.tickMusic(tick)`.

**Tests:**
- `scalingCountAndHealth()`: base 4, 4 jugadores, mín. 1, 0.25 → 7; multiplicador de vida 1,45.
- `phaseSelectionPicksNextCrossedThresholdOnce()`: lógica pura extraída en `static int nextPhase(List<PhaseDef>, int current, double healthFraction)`.

**Aceptación:** tests en verde. En el servidor, con un comando de depuración temporal (no se commitea), se spawnea un Zombie con armadura encantada, escala 1.5 y Fuerza; y un Warden al que **no** se le aplica la armadura, con un aviso en consola.

---

## [x] T07 — Núcleo de partida, lógica pura (RF-PAR-02..06, 16)

**Archivos:** `…/session/SessionState.java`, `…/session/SessionStateMachine.java`, `…/session/JoinResult.java`, `…/session/JoinRules.java`, `…/session/WaveScheduler.java`, `…/session/RoomProgress.java`, `…/session/SpawnOrder.java`; tests de cada una.

**Interfaces producidas:**
```java
public enum SessionState { FREE, LOBBY, RUNNING, COMPLETED, FAILED, RESETTING }
public final class SessionStateMachine {
    public SessionState state();
    public void openLobby(); public void start(); public void complete(); public void fail();
    public void beginReset(); public void finishReset();   // transición inválida → IllegalStateException
}
public enum JoinResult { OK, ALREADY_IN, DISABLED, RUNNING, RESETTING, FULL, COOLDOWN, NO_PERMISSION }
public final class JoinRules {
    static JoinResult check(SessionState state, boolean enabled, boolean alreadyIn, int players, int maxPlayers,
                            boolean bypassLimit, @Nullable Instant cooldownUntil, Instant now,
                            boolean bypassCooldown, boolean hasPermission);
    // Orden: ALREADY_IN, DISABLED, NO_PERMISSION, RUNNING/COMPLETED/FAILED→RUNNING, RESETTING, COOLDOWN, FULL, OK
}
public record SpawnOrder(String templateId) {}
public final class WaveScheduler {
    public WaveScheduler(WaveDef wave, IntUnaryOperator scaledCount, Random rng);
    /** startTick = tick en que empieza la oleada; alive = mobs vivos de esta oleada. */
    public List<SpawnOrder> tick(long tick, int alive);
    public boolean doneSpawning();
}
public final class RoomProgress {
    public RoomProgress(RoomDef room, IntUnaryOperator scaledCount, Random rng);
    public void start(long tick);
    /** Devuelve órdenes de spawn por spawner. aliveBySpawner: vivos actuales por spawner id. */
    public Map<String, List<SpawnOrder>> tick(long tick, Map<String, Integer> aliveBySpawner);
    public boolean cleared();   // todas las oleadas de todos los spawners terminadas y 0 vivos
}
```
**Semántica de `WaveScheduler`:**
- **`SIMULTANEOUS`:** todas las unidades de cada entrada salen en `start + delayTicks` de esa entrada.
- **`SEQUENTIAL`:** la entrada *i* sale entera cuando `alive == 0` tras la entrada *i−1*, más su `delayTicks`.
- **`STAGGERED`:** una unidad cada `staggerIntervalTicks`, entradas en orden. Cada entrada empieza tras su `delayTicks`.
- **`RANDOM`:** se barajan todas las unidades de todas las entradas con `rng`; se ignoran los `delayTicks`; una unidad cada `staggerIntervalTicks` desde el inicio.

**Semántica de `RoomProgress`:** cada spawner avanza sus oleadas por separado. La siguiente oleada empieza `pauseAfterTicks` después de que la anterior terminó de spawnear **y** sus vivos llegaron a 0.

**Límite de mobs vivos:** no se aplica aquí. T09 retiene las órdenes en cola si se alcanza `maxAliveMobsPerSession`.

**Tests:**
- Máquina de estados: todas las transiciones válidas, más `invalidTransitionThrows()`.
- `JoinRules`: un test por resultado, más `zeroMaxMeansUnlimited()` y `bypassLimitIgnoresFull()`.
- `WaveScheduler`: un test por modo con ticks explícitos.
- `RoomProgress`:
  - `roomClearsOnlyWhenAllSpawnersDoneAndNoneAlive()`.
  - `pauseBetweenWaves()`.
  - `deadByAnyCauseCounts()` (foco 1): el contador `alive` baja aunque la muerte no la cause un jugador, y la sala se limpia.

**Aceptación:** tests en verde, sin dependencias de servidor salvo tipos de `model`.

---

## [x] T08 — Herramientas de admin (RF-HER)

**Archivos:** `…/tool/ToolType.java`, `…/tool/ToolService.java`, `…/tool/Selection.java`, `…/tool/ToolListener.java`, `…/tool/PreviewRenderer.java`, `…/tool/SpawnerMarkers.java`; `messages.yml` claves `tool.*`; test `tool/SelectionTest.java`.

**Interfaces producidas:**
```java
public enum ToolType { REGION, DOOR, SPAWNER, POINT }
public final class ToolService {
    void give(Player p, ToolType t, @Nullable String dungeonId);   // ítem con PDC customdungeons:tool = tipo + dungeon
    Optional<Selection> selection(UUID admin);                     // última región o puerta seleccionada
    Optional<Location> lastPoint(UUID admin);
    void clear(UUID admin);
}
public record Selection(String world, @Nullable BlockPos a, @Nullable BlockPos b) { boolean complete(); Region toRegion(); }
public final class PreviewRenderer { void showRegion(Player p, Region r, Color c); void showDungeon(Player p, DungeonDef d, int seconds); }
public final class SpawnerMarkers { void show(DungeonDef d); void hide(String dungeonId); @Nullable String spawnerAt(Entity clicked); }
```
**Reglas:**
- Clic izquierdo y derecho marcan `a` y `b`.
- Herramienta de puntos: clic derecho guarda la ubicación con yaw y pitch.
- Los ítems de herramienta:
  - no se pueden soltar (`PlayerDropItemEvent`);
  - no se pueden mover a otros inventarios;
  - no dejan drop al morir: se eliminan.
- **Ticker de previsualización:** es la única excepción aceptada a la regla de tickers. Uno global que solo corre mientras algún admin sostiene una herramienta, cada 10 ticks, con partículas por `Player#spawnParticle`, visibles solo para ese admin. Se detiene cuando ningún admin sostiene una.
- **Marcadores de spawner:** `ItemDisplay` con PDC, creados solo mientras se edita o se ejecuta `show`, y eliminados después.

**Tests:** `selectionCompleteAndToRegionNormalizes()`.

**Aceptación:** en el servidor, seleccionar una región muestra su contorno solo al admin. Tirar la herramienta no hace nada. El ticker se detiene al guardar la herramienta (comprobado con spark o con un log de depuración).

---

## [x] T09 — Partida en runtime (RF-PAR-01..14, 16; RF-HAB-08 parte de sesión)

**Archivos:** `…/session/SessionManager.java`, `…/session/DungeonSession.java` (implementa `SessionContext`), `…/session/SessionTicker.java`, `…/session/SessionTempBlocks.java` (implementa `TempBlocks`, persiste vía `Storage`), `…/session/DoorService.java`, `…/session/KeyService.java`, `…/session/SessionListener.java`, `…/session/SessionBossBar.java`; `messages.yml` claves `session.*`; tests `session/DungeonSessionFlowTest.java` (con stubs), `session/KeyRulesTest.java`.

**Interfaces:**
- Consume: T02 `DefinitionStore`, T03 `Storage`, T05 `AbilityEngine`, T06 `MobFactory`/`BossController`/`Scaling`, T07 completo.
- Produce:
```java
public final class SessionManager {
    JoinResult join(Player p, String dungeonId);         // idempotente (foco 3); teleporta al lobby si OK
    void leave(Player p);                                // cuenta como abandono
    Optional<DungeonSession> sessionOf(UUID player);
    Optional<DungeonSession> session(String dungeonId);
    void startTest(Player admin, String dungeonId);     // sin premio; admin solo
    void forceStart(String dungeonId); void stop(String dungeonId); void reset(String dungeonId);
    void shutdown();                                     // onDisable: limpia todo
    void addListener(SessionLifecycleListener l);        // T14 se engancha aquí
}
public interface SessionLifecycleListener {
    default void onStateChange(DungeonSession s, SessionState from, SessionState to) {}
    default void onLobbyFull(DungeonSession s) {}
    default void onFinished(DungeonSession s, RunResult result, Set<UUID> survivors) {}
}
public final class DungeonSession implements SessionContext {
    DungeonDef def(); SessionStateMachine state(); boolean testMode();
    int livesLeft(UUID p); Set<UUID> survivors(); int roomIndex();
    void skipWave();                                     // solo testMode / debug
    void setInvulnerable(UUID admin, boolean v);         // solo testMode / debug
}
```
**Reglas** (cada una referencia su requisito):
- **Lobby:**
  - `join` OK → teleporta al lobby. Si es el primero, abre la cuenta atrás (`LOBBY_OPEN`).
  - Al llegar a `maxPlayers` → `onLobbyFull`.
  - Al acabar la cuenta atrás: con ≥ mínimo, `start` y teleporta al checkpoint de la sala 0; si no, cancela y devuelve a todos a la salida.
- **Ticker:** un `SessionTicker` por sesión (`runTaskTimer`, 1 tick). Por cada tick:
  - `RoomProgress.tick` (las órdenes de spawn se retienen si `alive ≥ maxAliveMobsPerSession`);
  - `AbilityEngine.tick`;
  - `BossController.tickMusic`;
  - expiración de bloques temporales y tareas de `runLater`;
  - correa de mobs cada 20 ticks (RF-PAR-10);
  - tiempo límite;
  - BossBar solo si cambió algo.
- **La sala empieza cuando un jugador de la partida entra en su región** (RF-PAR-08). La sala 0 empieza con el `start`.
- **Muerte de mob por cualquier causa** (foco 1): `EntityDeathEvent` y `EntityRemoveFromWorldEvent` (Paper) con PDC de la sesión → se descuenta de los vivos de su spawner. Drops vanilla anulados si la plantilla lo pide. Se dispara `ON_DEATH`.
- **Puertas (RF-PAR-07):**
  - Al empezar la partida, `DoorService` rellena cada puerta con el material de `config.yml` (`door-material`, por defecto `minecraft:iron_bars`) en los bloques de aire de la región, registrados como temporales persistentes.
  - `AUTOMATIC`: al limpiar la sala, restaura el aire con partículas y sonido.
  - `KEY`: al limpiar la sala, `KeyService` suelta la llave donde murió el último portador (o junto a la puerta si no hubo portador vivo).
- **Llave (foco 2):**
  - Es un ítem con PDC (`KEY_ITEM` = sessionId + roomId), invulnerable, sin despawn (`setUnlimitedLifetime(true)`) y con brillo.
  - **Recolocación:** si el ítem desaparece (`EntityRemoveFromWorldEvent` sin que lo recoja un jugador) o cae por debajo de `minY` del mundo, reaparece junto a la puerta.
  - **Prohibido:** meterla en contenedores, tirarla fuera del mundo de dungeons o sacarla de él. Si su portador muere, cae con el inventario. Si abandona, reaparece junto a la puerta.
  - **Uso:** clic derecho sobre un bloque de la puerta con la llave correcta → abre la puerta y consume la llave.
- **Contención (RF-PAR-09):**
  - `PlayerMoveEvent` solo cuando cambia el bloque y el jugador está en `Set<UUID>` de sesiones: si entra en una sala con índice mayor que la puerta abierta, se le devuelve al checkpoint.
  - `PlayerTeleportEvent` (ender pearl, chorus) se cancela si el destino está en una sala bloqueada.
- **Muerte de jugador (RF-PAR-11):**
  - Se resta una vida y se respeta `keepInventory` de la dungeon (`setKeepInventory`/`setKeepLevel`).
  - Respawn en el checkpoint (`PlayerRespawnEvent#setRespawnLocation`).
  - Sin vidas: eliminado, va a la salida tras el respawn y deja de ser objetivo.
- **Derrota (RF-PAR-12, 13):** abandono (`leave` o `PlayerQuitEvent`) → `addPendingExit` en `Storage`. Al reconectar (`PlayerJoinEvent`) se toma `takePendingExit` y se le teleporta. Sin jugadores activos, o con el tiempo agotado → `fail`.
- **Ladrón (RF-HAB-08):** `onItemStolen` guarda el ítem en `ActiveMob.stolenItems`. Al morir el mob, suelta esos ítems. Al terminar la partida, los no recuperados se devuelven al dueño: al inventario si está online y tiene sitio; si no, `Storage.addClaims`.
- **Fin:** completada (última sala limpia) o fallida → `onFinished(result, survivors)` → `RESETTING`:
  - eliminar mobs con el PDC de la sesión;
  - eliminar llaves;
  - `tempBlocks.restoreAll`;
  - quitar BossBars y música;
  - liberar chunk tickets;
  - `FREE`.
- **Chunk tickets:** se añaden al empezar la partida en los chunks de todas las salas y se quitan al resetear.
- **Mundo de dungeons (RF-MUN-01):** al habilitar, si `dungeonWorld` no existe y `autoCreateWorld`, se crea vacío (`WorldCreator` con generador void propio de 1 clase, `ChunkGenerator` sin bloques).
- **Cooldowns en memoria:** `JoinRules` es síncrono, así que al conectarse un jugador (y al habilitar el plugin, para los online) se cargan sus cooldowns con `Storage.cooldownUntil` en una caché `Map<UUID, Map<String, Instant>>`. T14 la actualiza al fijar un cooldown. Mientras no ha cargado, se trata como sin cooldown.
- **Modo test:** sin premio y sin cooldown. `skipWave` mata la oleada actual. Invulnerabilidad opcional para el admin.

**Tests:**
- `DungeonSessionFlowTest`, con stubs de mundo, factory y storage y ticks manuales: lobby → start → sala 0 limpia → puerta abierta → última sala → `onFinished(COMPLETED, {p1})`.
- `deathWithoutLivesEliminates()`.
- `allLeaveFails()`.
- `joinIsIdempotent()`.
- `KeyRulesTest`: decisión de recolocar la llave (`static boolean shouldRespawnKey(Cause cause, double y, int minY)`).

**Aceptación:** en el servidor, con una dungeon de 2 salas creada a mano en YAML, `join` → lobby → combate → puerta → final, sin errores en el log. Una muerte resta vida y el inventario cae. `/kill @e[type=zombie]` limpia la sala (foco 1).

---

## [x] T10 — Habilidades A: Wither, Dragón, Warden, Evoker (RF-HAB-05)

**Archivos:** `…/ability/impl/borrowed/WitherSkullsAbility.java`, `WitherShockwaveAbility.java`, `DragonBreathAbility.java`, `DragonRoarAbility.java`, `SonicBoomAbility.java`, `DarknessPulseAbility.java`, `EvokerFangsAbility.java`, `SummonVexesAbility.java`, `BorrowedAbilitiesA.java` (con `register`); `messages.yml` claves `ability.<id>.name/lore`; test `ability/impl/BorrowedAbilitiesATest.java`.
**Una línea** en `Abilities.registerDefaults`: `BorrowedAbilitiesA.register(r);`.

| id | Parámetros específicos | Implementación (API Paper) |
|---|---|---|
| `wither_skulls` | count 1–5, blue (bool), witherSeconds, damage | `WitherSkull` vía `Effects.launch`, `setCharged(blue)`; al impactar aplica `WITHER` |
| `wither_shockwave` | radius, damage, knockback | partículas `EXPLOSION_EMITTER` + `Effects.damage/knockback` a objetivos en radio |
| `dragon_breath` | radius, durationTicks, damagePerTick | `DragonFireball`; al impactar, `AreaEffectCloud` con `INSTANT_DAMAGE` dueño = caster |
| `dragon_roar` | radius, knockback, up | sonido `ENTITY_ENDER_DRAGON_GROWL` + knockback radial |
| `sonic_boom` | damage, range | partícula `SONIC_BOOM` en línea + `DamageSource` tipo `SONIC_BOOM` |
| `darkness_pulse` | radius, seconds | `DARKNESS` a objetivos + sonido `ENTITY_WARDEN_HEARTBEAT` |
| `evoker_fangs` | pattern (LINE/CIRCLE), count, damage | `EvokerFangs` con `setOwner(caster)` |
| `summon_vexes` | count, lifetimeSeconds | `Vex` vía `session.spawnMinion` con plantilla interna `__vex` o spawn directo marcado con PDC |

**Tests:** `allHaveUniqueIdsAndValidParamSpecs()` (default dentro de [min,max]) y `registerAddsEight()`.
**Aceptación:** en el servidor, cada habilidad asignada a un mob de prueba se ve, hace daño solo a jugadores de la partida y no rompe ni quema bloques.

## [x] T11 — Habilidades B: proyectiles, control y genéricas (RF-HAB-05, 06)

**Archivos:** `…/ability/impl/borrowed/BlazeVolleyAbility.java`, `GhastFireballAbility.java`, `WindChargeAbility.java`, `BreezeLeapAbility.java`, `ShulkerBulletAbility.java`, `ElderCurseAbility.java`, `GuardianBeamAbility.java`, `CreeperBlastAbility.java`, `WitchPotionsAbility.java`, `BorrowedAbilitiesB.java`; `…/ability/impl/generic/ArrowEffectAbility.java` (`on_hit_effect` ya existe de T05), `GenericAbilities.java`; test equivalente.
**Líneas** en `Abilities.registerDefaults`: `BorrowedAbilitiesB.register(r); GenericAbilities.register(r);`.

| id | Parámetros | Implementación |
|---|---|---|
| `blaze_volley` | count, spreadDeg | `SmallFireball` sin fuego (`setIsIncendiary(false)`) |
| `ghast_fireball` | yield (solo daño), damage | `Fireball` `setIsIncendiary(false)`, explosión sin bloques |
| `wind_charge` | power | `WindCharge` |
| `breeze_leap` | height | velocity hacia el objetivo + partículas `GUST` |
| `shulker_bullet` | levitationSeconds | `ShulkerBullet` con `setTarget` |
| `elder_curse` | seconds, amplifier | `MINING_FATIGUE` + `ElderGuardian` curse particle (`ELDER_GUARDIAN`) |
| `guardian_beam` | chargeTicks, damage | línea de partículas actualizada vía scheduler + daño al terminar si sigue en línea de visión |
| `creeper_blast` | radius, damage, fuseTicks | telegraph + `createExplosion(loc, power, false, false, caster)` |
| `witch_potions` | effect, amplifier, seconds | `ThrownPotion` con `PotionContents` |
| `arrow_effect` | effect, amplifier, seconds | dispara `Arrow` con `addCustomEffect` |

**Tests y aceptación:** como en T10.

## [x] T12 — Habilidades C: utilidades prestadas + propias de control (RF-HAB-05, 07)

**Archivos:** `…/ability/impl/borrowed/EndermanBlinkAbility.java`, `RoarKnockbackAbility.java`, `LaunchUpAbility.java`, `CobwebAbility.java`, `SplitOnDeathAbility.java`, `BlindnessAbility.java`, `BorrowedAbilitiesC.java`; `…/ability/impl/custom/HookAbility.java`, `AnchorAbility.java`, `FreezeAbility.java`, `SwapAbility.java`, `ChaosAbility.java`, `DisarmAbility.java`, `CustomAbilitiesA.java`; test.
**Líneas:** `BorrowedAbilitiesC.register(r); CustomAbilitiesA.register(r);`.

- **`ender_blink`:** teletransporte detrás del objetivo a un bloque seguro (dos de aire sobre uno sólido) y partículas `PORTAL`.
- **`roar_knockback`** y **`launch_up`:** fuerzas a los objetivos.
- **`cobweb`:** `TempBlocks.place(COBWEB)` en los pies del objetivo, solo si hay aire; `ttl` configurable.
- **`split_on_death`:** en `ON_DEATH`, invoca N copias con escala y vida reducidas vía `spawnMinion`; recursión máxima 1.
- **`blindness`:** aplica el efecto de poción.
- **`hook`:** tira del objetivo hacia el caster.
- **`anchor`:** `SLOWNESS` 255 + `JUMP_BOOST` −128 durante N ticks y partículas de cadena.
- **`freeze`:** `setFreezeTicks`.
- **`swap`:** intercambia ubicaciones.
- **`chaos`:** baraja los slots 0–8 de la hotbar.
- **`disarm`:** suelta el ítem de la mano principal a 3–5 bloques, con `setPickupDelay`. El ítem sigue siendo del jugador y no se destruye.

**Tests:** ids únicos y specs válidos, más una función pura `static int[] shuffleHotbar(int[] slots, Random r)` de `chaos` que sea una permutación.

## [x] T13 — Habilidades D: propias de combate y soporte (RF-HAB-07, 08) — depende también de T06

**Archivos:** `…/ability/impl/custom/ThiefAbility.java`, `VampirismAbility.java`, `HealerAbility.java`, `EnrageAbility.java`, `MinionShieldAbility.java`, `ReflectAbility.java`, `MeteorsAbility.java`, `EarthquakeAbility.java`, `LastBreathAbility.java`, `DoubleAbility.java`, `CustomAbilitiesB.java`; test.
**Línea:** `CustomAbilitiesB.register(r);`.

- **`thief`:**
  - En `ON_HIT`, toma un ítem aleatorio no vacío de la hotbar del objetivo y llama a `session.onItemStolen`.
  - Aplica `SPEED` al ladrón y lo hace huir: objetivo `null` y dirección contraria durante N ticks.
  - En `isLiveTest()` el ítem se devuelve al terminar la prueba.
  - **Nunca destruye el ítem.**
- **`vampirism`:** cura un % del daño infligido (`ON_HIT`).
- **`healer`:** cura a los `mobs()` de la sesión en un radio, con una línea de partículas.
- **`enrage`:** pensada para `HEALTH_BELOW`. `STRENGTH` y `SPEED` y un aura de partículas `ANGRY_VILLAGER` mientras viva.
- **`minion_shield`:**
  - Invoca N esbirros.
  - El caster es invulnerable mientras alguno viva. Los UUID de los esbirros se guardan en un `Map<UUID, Set<UUID>>` interno de la habilidad, indexado por el UUID del caster y limpiado al morir este. No se modifica `ActiveMob`.
  - El daño se cancela en `ON_DAMAGED` y se muestra un efecto de escudo.
- **`reflect`:** en `ON_DAMAGED` por proyectil, cancela el daño y relanza un proyectil igual hacia el tirador.
- **`meteors`:**
  - Marca N zonas con un círculo de partículas durante `telegraphTicks`.
  - Después cae `Fireball` no incendiaria desde Y+20 sobre cada zona.
  - Daño en radio.
- **`earthquake`:**
  - Onda de `BlockDisplay` (copias visuales del suelo) que suben y bajan durante 20 ticks y se eliminan después.
  - Daño y empuje vertical a los objetivos en radio.
  - **Sin tocar bloques reales.**
- **`last_breath`:** en `ON_DEATH`, explosión sin bloques **o** invocación de una plantilla (parámetro `mode`).
- **`double`:** invoca una copia de la propia plantilla con la vida y la escala multiplicadas por un factor < 1, sin la habilidad `double` para evitar cadenas.

**Tests:** ids únicos y specs válidos, más `static int pickStealSlot(ItemStack[] hotbar, Random r)`, que devuelve −1 si la hotbar está vacía.

---

## [ ] T14 — Cierre de partida: premios, cooldowns, hooks, recuperación (RF-PRE, RF-INT-02, RF-PAR-15, 17)

**Archivos:** `…/reward/RewardService.java`, `…/integration/VaultHook.java`, `…/integration/CommandHooks.java`, `…/session/RecoveryService.java`, `…/session/RunRecorder.java`; `messages.yml` claves `reward.*`, `claim.*`; tests `reward/RewardServiceTest.java`, `integration/CommandHooksTest.java`.

**Interfaces producidas:**
```java
public final class RewardService implements SessionLifecycleListener {
    // onFinished(COMPLETED) y no testMode: a cada superviviente → ítems (sobrantes a Storage.addClaims), dinero (VaultHook), XP, comandos {player}
    CompletableFuture<Integer> claim(Player p);    // entrega pendientes, devuelve nº de ítems
}
public final class VaultHook { static Optional<VaultHook> tryCreate(); void deposit(OfflinePlayer p, double amount); }
public final class CommandHooks implements SessionLifecycleListener {
    static String render(String template, String dungeonId, int players, int max);   // {dungeon} {players} {max}; max 0 → "∞"
    // ejecuta por consola los comandos del HookEvent correspondiente a cada transición
}
public final class RecoveryService { void recoverOnEnable(); }   // RF-PAR-17
public final class RunRecorder implements SessionLifecycleListener { /* startRun/finishRun/setCooldown(solo supervivientes, no testMode)/markActive/clearActive */ }
```
**Mapeo de hooks:**

| Transición | Hook |
|---|---|
| FREE → LOBBY | `LOBBY_OPEN` |
| `onLobbyFull` | `FULL` |
| → RUNNING | `START` |
| → COMPLETED | `COMPLETE` |
| → FAILED | `FAIL` |
| → FREE | `FREE` |

**Recuperación al arrancar:** en `onEnable`, antes de aceptar `join`:
- `loadTempBlocks` → restaurar cada bloque a su `originalBlockData`;
- `loadActive` → por cada sesión, `addPendingExit` de sus jugadores, `finishRun(ABORTED)` y `clearActive`;
- eliminar entidades con el PDC de sesión cuando se carguen sus chunks (`EntitiesLoadEvent`).

**Tests:**
- `onlySurvivorsAreRewarded()`, con stubs.
- `testModeGivesNothing()`.
- `hookRenderPlaceholders()`.
- `unlimitedRendersInfinity()`.

**Aceptación:** en el servidor:
- completar la dungeon da el premio solo a los supervivientes;
- con el inventario lleno, `claim` entrega el resto;
- matar el proceso del servidor (`kill -9`) a mitad de partida y arrancarlo de nuevo deja puertas restauradas, sin mobs, y los jugadores aparecen en la salida (criterio de aceptación 5).

---

## [x] T15 — GUI de dungeons (RF-GUI-03 rama dungeon, RF-PRE-01, RF-INT-02)

**Archivos:** `…/gui/menu/DungeonListMenu.java`, `DungeonMenu.java`, `DungeonSettingsMenu.java`, `ScalingMenu.java`, `HooksMenu.java`, `RewardMenu.java`, `RoomListMenu.java`, `RoomMenu.java`, `SpawnerMenu.java`, `WaveListMenu.java`, `WaveMenu.java`, `WaveEntryMenu.java`, `TemplatePickerMenu.java`; `messages.yml` claves `gui.dungeon.*`.

**Reglas:**
- Todo sobre un `Draft<DungeonDef>` que se crea al abrir `DungeonMenu`, con `EditLocks` por dungeon. No se puede editar con partida en curso.
- "Guardar":
  - con `Validator`: errores → ítem rojo con la lista de errores;
  - sin errores → `DefinitionStore.save`.
- Región, puerta, checkpoint, lobby y salida se asignan con un botón "usar selección actual" que lee `ToolService.selection`/`lastPoint`, y otro "dar herramienta".
- El editor de premios permite arrastrar ítems reales a 27 ranuras (`allowsPlacement`). Dinero, XP y comandos se editan con `Inputs`.
- Hooks: lista de comandos por `HookEvent`, añadir y quitar con `Inputs.text`.
- `TemplatePickerMenu` lista `DefinitionStore.mobs()` con su icono (huevo del tipo).
- **Lore explicativa en cada botón,** indicando qué hace cada clic.

**Aceptación:** en el servidor, crear una dungeon nueva de 2 salas (una con llave) solo con la GUI y las herramientas, guardarla y verla en `dungeons/<id>.yml`. Guardar con una sala sin región muestra el error en rojo.

## [x] T17 — GUI de mobs + probar en vivo (RF-GUI-03 rama mobs, RF-GUI-06)

**Archivos:** `…/gui/menu/MobLibraryMenu.java`, `MobMenu.java`, `EntityTypePickerMenu.java`, `StatsMenu.java`, `EquipmentMenu.java`, `EnchantMenu.java`, `PotionMenu.java`, `AbilityListMenu.java`, `AbilityPickerMenu.java`, `ParamEditorMenu.java`, `ComboMenu.java`, `PhaseListMenu.java`, `PhaseMenu.java`; `…/mob/LiveTestService.java` (implementa `SessionContext` con `isLiveTest()=true`); `messages.yml` claves `gui.mob.*`, `livetest.*`.

**Reglas:**
- **`EntityTypePickerMenu`:** lista los `EntityType` vivos y spawneables con su huevo; la búsqueda se hace con `Inputs.text`.
- **`EquipmentMenu`:** las ranuras de armadura solo se muestran si el tipo está en `armorCapable`. En caso contrario, un ítem gris explica por qué.
- **`ParamEditorMenu`:** se genera a partir de `Ability.params()` más los campos comunes de `AbilityInstance`, con un botón por parámetro según su `ParamType`:
  - números → `Inputs.number`;
  - booleanos → alternar;
  - poción, partícula, sonido y plantilla → menús selectores.
- **`ComboMenu`:** de 2 a 5 pasos, cada uno con su habilidad, parámetros y retardo.
- **`PhaseMenu`:** campos de `PhaseDef`. Música y sonido se eligen con un selector de `Sound` o con una clave escrita a mano.
- **Probar en vivo (`LiveTestService`):**
  - invoca el mob a 4 bloques del admin, con un único ticker que llama a `AbilityEngine.tick` y a `BossController`;
  - `players()` = solo el admin;
  - los esbirros se rastrean;
  - termina con un botón, si el admin se aleja más de 48 bloques o tras `live-test.max-seconds` de `config.yml` (300 s por defecto);
  - al terminar elimina todo y restaura los bloques temporales;
  - el admin puede alternar invulnerabilidad;
  - máximo una prueba por admin.

**Aceptación:** en el servidor:
- crear desde la GUI un Warden con `wither_skulls`, un combo `hook → anchor → meteors` y 2 fases con música;
- probarlo en vivo: las fases cambian a 66 % y 33 %, la música suena y se para;
- al cerrar la prueba no queda nada en el mundo.

## [x] T16 — Comandos (RF-CMD, RF-PAR-01, RF-INT-01)

**Archivos:** `…/command/CustomDungeonCommand.java` (árbol Brigadier registrado en `LifecycleEvents.COMMANDS`), `…/command/JoinSpamGuard.java`; `messages.yml` claves `command.*`, `join.*`; test `command/JoinSpamGuardTest.java`.

**Reglas:**
- El árbol completo de spec §12, con sugerencias de ids de dungeon y nombres de jugador.
- Alias desde `PluginConfig.commandAliases`.
- **`join [jugador] <dungeon>`:**
  - Sin `jugador` lo ejecuta un jugador.
  - Con `jugador` lo pueden ejecutar la consola o quien tenga `customdungeons.admin.join.others`.
  - Si el emisor es el **propio** jugador nombrado y no tiene ese permiso, se acepta igualmente, porque MV-Portals puede ejecutarlo como jugador.
  - Se traduce cada `JoinResult` a su mensaje.
  - **Spam (foco 3):** `JoinSpamGuard` silencia los mensajes de rechazo repetidos al mismo jugador durante 3 s, y `join` es idempotente.
- **`test`, `start`, `stop`, `reset`, `show`, `tool`, `reload`, `debug`, `leave`, `stats`, `claim`:** delegan en los servicios existentes. `reload` se rechaza si hay partidas en curso.
- **`debug`** alterna el modo debug del admin: logs de ticks de sesión en consola y comandos `skipwave` e `invulnerable` dentro de la partida del admin.

**Tests:** `spamGuardSilencesWithinWindow()` y `spamGuardAllowsAfterWindow()`, con un reloj inyectable.

**Aceptación:** todos los comandos responden con el prefijo configurado. Un jugador sin permisos solo ve `join`, `leave`, `stats` y `claim` en el autocompletado.

---

## [ ] T18 — Pruebas integradas en servidor + guías (spec §17)

**Archivos:** `docs/guides/servidor-de-pruebas.md`, `docs/guides/multiverse-portals.md`, `docs/guides/worldguard.md`, `docs/reference/habilidades.md` (tabla de todas las habilidades y sus parámetros, generada a mano desde el código).

**Pasos:**
1. Instalar Multiverse-Core y Multiverse-Portals 5.3.0 (Modrinth) en el servidor de pruebas. Crear el mundo de dungeons con Multiverse.
2. Configurar un portal con acción de comando hacia `customdungeon join {player} <id>`. **Documentar** si MV-Portals lo ejecuta como consola o como jugador y la sintaxis exacta que usa para `{player}`.
3. Recorrer los criterios de aceptación 1–7 de spec §17, con 2 clientes offline-mode (`online-mode=false`). Si no hay clientes humanos disponibles, simularlos con un bot (`mineflayer`, compatible con 26.3; si no lo es, se marcan como pendientes de prueba manual).
4. **Prueba de carga (criterio 8):** dungeon de prueba con 50 mobs con 2 habilidades cada uno, `spark profiler --timeout 60`. Se documenta el MSPT por partida y se debe cumplir RNF-02.
5. Verificar la lista `armor-capable-mobs` contra la 26.3: spawnear cada tipo con armadura y comprobar que se renderiza.
6. **Guía de WorldGuard:** flags recomendadas en la región del mundo de dungeons (`block-break deny`, `block-place deny`, `tnt deny`, `creeper-explosion deny`, `enderpearl` según diseño, `chorus-fruit-teleport deny`, `mob-spawning deny` excepto plugins) y aviso de que el plugin spawnea con `SpawnReason.CUSTOM`.
- Los bugs encontrados se convierten en tareas `T18.x` en este archivo.

## [ ] T19 — Release v1.0.0

- Versión `1.0.0` en `build.gradle.kts`, `README.md` con instalación, requisitos e integraciones, y enlaces a las guías.
- `./gradlew build`, más el tag `v1.0.0`, más un release en GitHub con el jar adjunto.
