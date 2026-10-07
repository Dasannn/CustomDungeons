package dev.dasan.customdungeons.config;

import dev.dasan.customdungeons.CustomDungeonsPlugin;
import dev.dasan.customdungeons.ability.Ability;
import dev.dasan.customdungeons.model.*;
import dev.dasan.customdungeons.text.Messages;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.ServicePriority;

/** Immutable cache snapshots, serialized async mutations and atomic replacement of YAML files. */
public final class DefinitionStore implements AutoCloseable {
    private final Path root;
    private final PluginConfig config;
    private final Set<String> abilityIds;
    private final Consumer<String> warning;
    private Consumer<Validator.Warning> adjustmentWarning;
    private final Executor executor;
    private final Object dataLock = new Object();
    private final Object queueLock = new Object();
    private final DefinitionCodec codec = new DefinitionCodec();
    private final Validator validator = new Validator();
    private volatile Snapshot snapshot = new Snapshot(Map.of(),Map.of(),Map.of());
    private boolean closed;
    private Runnable onReload = () -> {};
    private volatile boolean reloading;
    private CompletableFuture<Snapshot> loading = CompletableFuture.completedFuture(null);
    private CompletableFuture<Void> reloadResult = CompletableFuture.completedFuture(null);
    private CompletableFuture<Void> writes = CompletableFuture.completedFuture(null);
    private record Snapshot(Map<String,DungeonDef> dungeons,Map<String,MobTemplate> mobs,Map<String,SpawnerPreset> spawnerPresets) {
        Snapshot { dungeons = Map.copyOf(dungeons); mobs = Map.copyOf(mobs); spawnerPresets = Map.copyOf(spawnerPresets); }
    }
    public DefinitionStore(Path root,PluginConfig config,Set<String> abilityIds,Logger logger,Executor executor) {
        this(root,config,abilityIds,logger::warning,executor);
    }
    public DefinitionStore(Path root,PluginConfig config,Set<String> abilityIds,Consumer<String> warning,Executor executor) {
        this.root = root.toAbsolutePath().normalize(); this.config = config;
        this.abilityIds = Set.copyOf(abilityIds); this.warning = warning; this.executor = executor;
        this.adjustmentWarning = finding -> warning.accept(finding.path()+" ("+finding.messageKey()+") "+finding.args());
    }
    /** Single authorized service-registration line in the plugin. Future tasks retrieve Bukkit services. */
    public static void register(CustomDungeonsPlugin plugin) {
        Messages messages = plugin.messages();
        var plain = PlainTextComponentSerializer.plainText();
        var configYaml = new YamlConfiguration();
        plugin.getConfig().getValues(false).forEach(configYaml::set);
        var configWarnings = new ArrayList<String>();
        var loader = new ConfigLoader(configWarnings::add);
        PluginConfig config = loader.load(configYaml);
        EntityHeights heights = loader.loadEntityHeights(configYaml);
        String resource = config.language().equals("en") ? "messages_en.yml" : "messages.yml";
        if (!Files.exists(plugin.getDataFolder().toPath().resolve(resource))) plugin.saveResource(resource,false);
        var messageWarnings = new ArrayList<String>();
        messages.load(loadMessages(plugin.getDataFolder().toPath(),config.language(),messageWarnings::add),config.prefix());
        configWarnings.forEach(path->plugin.getLogger().warning(plain.serialize(messages.get("config.invalid-value",Placeholder.unparsed("path",path)))));
        messageWarnings.forEach(path->plugin.getLogger().warning(plain.serialize(messages.get("config.invalid-value",Placeholder.unparsed("path",path)))));
        var store = new DefinitionStore(plugin.getDataFolder().toPath(),config,
                plugin.abilityRegistry().all().stream().map(Ability::id).collect(Collectors.toSet()),
                path -> {
                    if (plugin.isEnabled()) plugin.getServer().getScheduler().runTask(plugin, () ->
                            plugin.getLogger().warning(plain.serialize(messages.get("config.invalid-definition",Placeholder.unparsed("path",path)))));
                },
                ForkJoinPool.commonPool());
        store.adjustmentWarning = finding -> {
            if (plugin.isEnabled()) plugin.getServer().getScheduler().runTask(plugin, () -> {
                var args = finding.args().entrySet().stream().map(e -> Placeholder.unparsed(e.getKey(),e.getValue()))
                        .toArray(net.kyori.adventure.text.minimessage.tag.resolver.TagResolver[]::new);
                plugin.getLogger().warning(plain.serialize(messages.get("config.adjusted-definition",
                        Placeholder.unparsed("path",finding.path()),
                        Placeholder.component("warning",messages.get(finding.messageKey(),args)))));
            });
        };
        // Loading old ItemStacks can initialize Paper's legacy conversion tables for seconds.
        // Keep that work off the server thread, including the first load on enable.
        store.reloadAsync(task -> plugin.getServer().getScheduler().runTask(plugin, task))
                .whenComplete((unused,error) -> {
                    if (!plugin.isEnabled()) return;
                    if (error != null) plugin.getLogger().warning(plain.serialize(messages.get("config.load-failed")));
                    else plugin.getLogger().info(plain.serialize(messages.get("config.loaded",
                            Placeholder.unparsed("dungeons",Integer.toString(store.dungeons().size())),
                            Placeholder.unparsed("mobs",Integer.toString(store.mobs().size())))));
                });
        plugin.getServer().getPluginManager().registerEvents(new org.bukkit.event.Listener() {
            @org.bukkit.event.EventHandler
            public void onDisable(org.bukkit.event.server.PluginDisableEvent event) {
                if (event.getPlugin() == plugin) {
                    try { store.close(); }
                    catch (CompletionException error) { plugin.getLogger().warning(plain.serialize(messages.get("config.save-failed"))); }
                }
            }
        },plugin);
        plugin.getServer().getServicesManager().register(PluginConfig.class,config,plugin,ServicePriority.Normal);
        plugin.getServer().getServicesManager().register(EntityHeights.class,heights,plugin,ServicePriority.Normal);
        plugin.getServer().getServicesManager().register(DefinitionStore.class,store,plugin,ServicePriority.Normal);
    }
    static YamlConfiguration loadMessages(Path directory,String language,Consumer<String> warning) {
        String resource = "en".equals(language) ? "messages_en.yml" : "messages.yml";
        var defaults = new YamlConfiguration();
        try (var stream = Objects.requireNonNull(DefinitionStore.class.getResourceAsStream("/"+resource))) {
            defaults.load(new InputStreamReader(stream,StandardCharsets.UTF_8));
        } catch (Exception error) { throw new IllegalStateException("Cannot load bundled messages",error); }
        var yaml = new YamlConfiguration(); Path file = directory.resolve(resource);
        if (Files.exists(file)) {
            try { yaml.load(file.toFile()); }
            catch (Exception error) { warning.accept(file+":<yaml>"); yaml = new YamlConfiguration(); }
        }
        for (String key : defaults.getKeys(true)) {
            if (defaults.isString(key) && !yaml.isString(key)) yaml.set(key,defaults.getString(key));
        }
        return yaml;
    }
    public Map<String,DungeonDef> dungeons() { return snapshot.dungeons(); }
    public Map<String,MobTemplate> mobs() { return snapshot.mobs(); }
    public Map<String,SpawnerPreset> spawnerPresets() { return snapshot.spawnerPresets(); }
    public void reload() { loadAll(); }
    public boolean isReloading() { return reloading; }
    /** Main-thread notification after successful publication, including the initial load. */
    public void onReload(Runnable listener) {
        synchronized (queueLock) { onReload = Objects.requireNonNull(listener); }
    }
    /** Read after earlier writes; publish all definition maps together using the caller's main-thread executor. */
    public CompletableFuture<Void> reloadAsync(Executor applyExecutor) {
        Objects.requireNonNull(applyExecutor);
        synchronized (queueLock) {
            if (closed || reloading) return CompletableFuture.failedFuture(new IllegalStateException("DefinitionStore unavailable"));
            reloading = true;
            var result = new CompletableFuture<Void>();
            reloadResult = result;
            try {
                loading = writes.handle((unused,error) -> null).thenApplyAsync(unused -> readSnapshot(), executor);
                loading.whenComplete((loaded,error) -> {
                    try { applyExecutor.execute(() -> finishReload(result, loaded, error)); }
                    catch (RuntimeException failure) { finishReload(result, null, failure); }
                });
            } catch (RuntimeException failure) { finishReload(result, null, failure); }
            return result;
        }
    }
    private void finishReload(CompletableFuture<Void> result, Snapshot loaded, Throwable error) {
        synchronized (queueLock) {
            if (closed) error = new IllegalStateException("DefinitionStore closed");
            if (error == null) { snapshot = loaded; onReload.run(); }
            reloading = false;
            if (error == null) result.complete(null); else result.completeExceptionally(error);
        }
    }
    /** Called during disable, where synchronous draining is permitted. The supplied executor is not owned. */
    @Override public void close() {
        CompletableFuture<Void> pending;
        CompletableFuture<Snapshot> reading;
        synchronized (queueLock) {
            closed = true; pending = writes; reading = loading;
            reloadResult.completeExceptionally(new IllegalStateException("DefinitionStore closed"));
        }
        // Never wait for a scheduled main-thread callback while disabling on that thread.
        try { pending.join(); } finally { reading.handle((unused,error) -> null).join(); }
    }
    public void loadAll() {
        synchronized (queueLock) {
            if (closed || reloading) throw new IllegalStateException("DefinitionStore unavailable");
            synchronized (dataLock) { snapshot = readSnapshot(); onReload.run(); }
        }
    }
    private Snapshot readSnapshot() {
        Map<String,MobTemplate> mobs = new LinkedHashMap<>();
        Map<String,DungeonDef> dungeons = new LinkedHashMap<>();
        for (Path file : files("mobs")) {
            String id = id(file);
            try {
                MobTemplate mob = codec.decodeMob(id,read(file));
                if (Double.isFinite(mob.maxHealth()) && mob.maxHealth()>1024) {
                    adjustmentWarning.accept(new Validator.Warning(file+":max-health","validation.health-clamped",
                            Map.of("value",Double.toString(mob.maxHealth()),"max","1024")));
                    mob = new MobTemplate(mob.id(),mob.entityType(),mob.displayName(),1024,mob.damage(),mob.speed(),
                            mob.knockbackResistance(),mob.scale(),mob.equipment(),mob.potions(),mob.abilities(),mob.combos(),
                            mob.boss(),mob.bossBarColor(),mob.musicKey(),mob.phases(),mob.vanillaDrops());
                }
                var errors = validator.validate(mob,config,abilityIds);
                if (errors.isEmpty()) mobs.put(id,mob); else report(file,errors);
            } catch (IOException | SecurityException e) { throw new CompletionException(e); }
            catch (Exception e) { warnParse(file,e); }
        }
        Map<String,SpawnerPreset> presets = new LinkedHashMap<>();
        for (Path file : files("spawners")) {
            String id = id(file);
            try {
                var preset = codec.decodeSpawnerPreset(id,read(file));
                var errors = validator.validate(preset,mobs);
                if (errors.isEmpty()) presets.put(id,preset); else report(file,errors);
            } catch (IOException | SecurityException e) { throw new CompletionException(e); }
            catch (Exception e) { warnParse(file,e); }
        }
        for (Path file : files("dungeons")) {
            String id = id(file);
            try {
                DungeonDef dungeon = codec.decodeDungeon(id,read(file));
                var normalized = automaticFinalRoom(dungeon);
                if (normalized != dungeon) adjustmentWarning.accept(new Validator.Warning(
                        file+":rooms["+(dungeon.rooms().size()-1)+"].unlock","validation.final-room-key",Map.of()));
                dungeon = normalized;
                var errors = validator.validate(dungeon,mobs,presets);
                if (!errors.isEmpty()) { report(file,errors); dungeon = disabled(dungeon); }
                dungeons.put(id,dungeon);
            } catch (IOException | SecurityException e) { throw new CompletionException(e); }
            catch (Exception e) {
                warnParse(file,e);
                dungeons.put(id,new DungeonDef(id,"",false,null,null,1,0,30,3,false,0,0,false,
                        config.defaults().scaling(),Map.of(),new RewardDef(List.of(),0,0,List.of()),List.of()));
            }
        }
        return new Snapshot(dungeons,mobs,presets);
    }
    public CompletableFuture<Void> save(DungeonDef dungeon) {
        checkId(dungeon.id());
        // ItemStacks are encoded into YAML on the caller thread; only plain UTF-8 bytes reach the executor.
        var normalized = automaticFinalRoom(dungeon);
        String yaml = yaml(codec.encode(normalized));
        return mutate(()->{
            var errors = validator.validate(normalized,snapshot.mobs(),snapshot.spawnerPresets()); requireValid(errors);
            write("dungeons",normalized.id(),yaml);
            var dungeons = new HashMap<>(snapshot.dungeons()); dungeons.put(normalized.id(),normalized);
            snapshot = new Snapshot(dungeons,snapshot.mobs(),snapshot.spawnerPresets());
        });
    }
    public CompletableFuture<Void> save(MobTemplate mob) {
        checkId(mob.id());
        String yaml = yaml(codec.encode(mob));
        return mutate(()->{
            requireValid(validator.validate(mob,config,abilityIds)); write("mobs",mob.id(),yaml);
            var mobs = new HashMap<>(snapshot.mobs()); mobs.put(mob.id(),mob);
            snapshot = new Snapshot(revalidate(snapshot.dungeons(),mobs,snapshot.spawnerPresets()),mobs,snapshot.spawnerPresets());
        });
    }
    public CompletableFuture<Void> save(SpawnerPreset preset) {
        return savePreset(preset,null,false);
    }
    public CompletableFuture<Void> save(SpawnerPreset preset,SpawnerPreset expected) {
        return savePreset(preset,expected,true);
    }
    private CompletableFuture<Void> savePreset(SpawnerPreset preset,SpawnerPreset expected,boolean compare) {
        checkId(preset.id()); String yaml = yaml(codec.encode(preset));
        return mutate(() -> {
            if(compare && !Objects.equals(expected,snapshot.spawnerPresets().get(preset.id()))) throw new IllegalStateException("Preset changed");
            requireValid(validator.validate(preset,snapshot.mobs()));
            write("spawners",preset.id(),yaml);
            var presets = new HashMap<>(snapshot.spawnerPresets()); presets.put(preset.id(),preset);
            snapshot = new Snapshot(revalidate(snapshot.dungeons(),snapshot.mobs(),presets),snapshot.mobs(),presets);
        });
    }
    /** Persist every dependent room before deleting the origin. An interrupted delete remains resolvable. */
    public CompletableFuture<Void> deleteSpawnerPreset(String id) {
        checkId(id);
        // Serialize Bukkit reward items on the caller thread, just like save(DungeonDef).
        var before = snapshot;
        var dungeons = new HashMap<>(before.dungeons());
        var yaml = new LinkedHashMap<String,String>();
        for (var dungeon : before.dungeons().values()) {
            var local = SpawnerPresets.detach(dungeon,id,before.spawnerPresets());
            if (!local.equals(dungeon)) {
                yaml.put(dungeon.id(),yaml(codec.encode(local))); dungeons.put(dungeon.id(),local);
            }
        }
        return mutate(() -> {
            if (!snapshot.dungeons().equals(before.dungeons()) || !snapshot.spawnerPresets().equals(before.spawnerPresets()))
                throw new IllegalStateException("Definitions changed during preset deletion");
            for (var entry : yaml.entrySet()) write("dungeons",entry.getKey(),entry.getValue());
            Files.deleteIfExists(target("spawners",id));
            var presets = new HashMap<>(snapshot.spawnerPresets()); presets.remove(id);
            snapshot = new Snapshot(dungeons,snapshot.mobs(),presets);
        });
    }
    public CompletableFuture<Void> delete(DungeonDef dungeon) { return deleteDungeon(dungeon.id()); }
    public CompletableFuture<Void> delete(MobTemplate mob) { return deleteMob(mob.id()); }
    public CompletableFuture<Void> deleteDungeon(String id) {
        checkId(id); return mutate(()->{
            Files.deleteIfExists(target("dungeons",id));
            var dungeons = new HashMap<>(snapshot.dungeons()); dungeons.remove(id);
            snapshot = new Snapshot(dungeons,snapshot.mobs(),snapshot.spawnerPresets());
        });
    }
    public CompletableFuture<Void> deleteMob(String id) {
        checkId(id); return mutate(()->{
            Files.deleteIfExists(target("mobs",id));
            var mobs = new HashMap<>(snapshot.mobs()); mobs.remove(id);
            snapshot = new Snapshot(revalidate(snapshot.dungeons(),mobs,snapshot.spawnerPresets()),mobs,snapshot.spawnerPresets());
        });
    }
    private Map<String,DungeonDef> revalidate(Map<String,DungeonDef> definitions,Map<String,MobTemplate> mobs,Map<String,SpawnerPreset> presets) {
        var out = new HashMap<String,DungeonDef>();
        definitions.forEach((id,dungeon)->{
            var errors = validator.validate(dungeon,mobs,presets);
            if (!errors.isEmpty()) report(root.resolve("dungeons/"+id+".yml"),errors);
            out.put(id,errors.isEmpty()?dungeon:disabled(dungeon));
        }); return out;
    }
    private CompletableFuture<Void> mutate(IoAction action) {
        synchronized (queueLock) {
            if (closed || reloading) return CompletableFuture.failedFuture(new IllegalStateException("DefinitionStore unavailable"));
            writes = writes.handle((unused,error)->null).thenRunAsync(()->{
                synchronized (dataLock) {
                    try { action.run(); } catch (IOException e) { throw new CompletionException(e); }
                }
            },executor);
            return writes;
        }
    }
    @FunctionalInterface private interface IoAction { void run() throws IOException; }
    private List<Path> files(String kind) {
        try {
            Path dir = directory(kind);
            List<Path> candidates;
            try (var stream = Files.list(dir)) {
                candidates = stream.filter(p -> p.getFileName().toString().endsWith(".yml"))
                        .sorted().toList();
            }
            var files = new ArrayList<Path>();
            for (Path file : candidates) {
                // isRegularFile silently returns false on I/O failure; a failed stat must abort reload.
                if (Files.readAttributes(file,java.nio.file.attribute.BasicFileAttributes.class,
                        LinkOption.NOFOLLOW_LINKS).isRegularFile()) files.add(file);
            }
            return files;
        } catch (IOException | java.io.UncheckedIOException | SecurityException e) {
            throw new CompletionException(e);
        }
    }
    private Path directory(String kind) throws IOException {
        Files.createDirectories(root);
        if (Files.isSymbolicLink(root)) throw new IOException("Symlink data directory");
        Path dir = root.resolve(kind);
        if (Files.isSymbolicLink(dir)) throw new IOException("Symlink definition directory");
        Files.createDirectories(dir); return dir;
    }
    private Path target(String kind,String id) throws IOException {
        checkId(id); Path file = directory(kind).resolve(id+".yml");
        if (Files.isSymbolicLink(file)) throw new IOException("Symlink definition file"); return file;
    }
    private void write(String kind,String id,String yaml) throws IOException {
        Path target = target(kind,id); Path tmp = Files.createTempFile(target.getParent(),".definition-",".tmp");
        try {
            Files.writeString(tmp,yaml);
            try { Files.move(tmp,target,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException e) { Files.move(tmp,target,StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(tmp); }
    }
    private YamlConfiguration read(Path file) throws Exception {
        var yaml = new YamlConfiguration(); yaml.load(file.toFile()); return yaml;
    }
    private static String yaml(Map<String,Object> values) {
        var yaml = new YamlConfiguration(); values.forEach(yaml::set); return yaml.saveToString();
    }
    private void report(Path file,List<ValidationError> errors) {
        errors.forEach(error->warning.accept(file+":"+error.path()+" ("+error.messageKey()+")"));
    }
    private void warnParse(Path file,Exception exception) {
        // YAML parser exceptions can include source snippets, including secrets. Only codec paths are safe.
        warning.accept(file+":"+DefinitionCodec.errorPath(exception));
    }
    private static void requireValid(List<ValidationError> errors) {
        if (!errors.isEmpty()) throw new IllegalArgumentException(errors.stream().map(e->e.path()+": "+e.messageKey()).collect(Collectors.joining(", ")));
    }
    private static void checkId(String id) {
        if (id == null || !id.matches("[a-z0-9_-]{1,32}")) throw new IllegalArgumentException("validation.id");
    }
    private static String id(Path file) { String name = file.getFileName().toString(); return name.substring(0,name.length()-4); }
    /** The final wave completes the dungeon immediately, so its room never waits for a key. */
    private static DungeonDef automaticFinalRoom(DungeonDef dungeon) {
        if (dungeon.rooms().isEmpty() || dungeon.rooms().getLast().unlock()!=UnlockMode.KEY) return dungeon;
        var rooms = new ArrayList<>(dungeon.rooms());
        var last = rooms.getLast();
        rooms.set(rooms.size()-1,new RoomDef(last.id(),last.region(),last.checkpoint(),last.door(),
                UnlockMode.AUTOMATIC,last.keyCarrierTemplateId(),last.spawners(),RoomDef.OpeningMode.AUTOMATIC,last.ambience()));
        return SpawnerPresets.withRooms(dungeon,rooms);
    }
    private static DungeonDef disabled(DungeonDef d) {
        return new DungeonDef(d.id(),d.displayName(),false,d.lobby(),d.exit(),d.minPlayers(),d.maxPlayers(),d.lobbyCountdownSeconds(),d.lives(),d.keepInventory(),
                d.timeLimitSeconds(),d.cooldownSeconds(),d.requirePermission(),d.scaling(),d.hooks(),d.reward(),d.rooms(),d.spawnerPresets(),d.area(),d.startMode(),d.plates(),d.plateCountdownSeconds(),d.entranceDoor(),
                d.teleportOnStart(),d.introCinematic(),d.introSeconds(),d.finishMode(),d.exitGraceSeconds(),d.finishDestination(),d.exitPlates());
    }
}
