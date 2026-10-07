package dev.dasan.customdungeons.config;

import dev.dasan.customdungeons.CustomDungeonsPlugin;
import dev.dasan.customdungeons.session.SessionManager;
import dev.dasan.customdungeons.storage.Storage;
import dev.dasan.customdungeons.storage.PendingExitPersistence;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.mockito.Mockito.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Set;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class DefinitionReloadTest {
    @TempDir Path directory;
    static final class Queue implements Executor {
        final ArrayDeque<Runnable> tasks = new ArrayDeque<>();
        public void execute(Runnable task) { tasks.add(task); }
        void run() { tasks.remove().run(); }
    }
    DefinitionStore store(Executor worker) {
        return new DefinitionStore(directory, new ConfigLoader(p -> {}, m -> m == Material.IRON_BLOCK)
                .load(new YamlConfiguration()), Set.of("test"), p -> {}, worker);
    }
    @Test void legacyHealthLoadsClampedWithWarningWithoutChangingYaml() throws Exception {
        var warnings=new java.util.ArrayList<String>();
        var store=new DefinitionStore(directory,new ConfigLoader(p->{},m->m==Material.IRON_BLOCK).load(new YamlConfiguration()),
                Set.of("test"),warnings::add,Runnable::run);
        store.save(DefinitionCodecTest.mob()).join();store.save(DefinitionCodecTest.dungeon()).join();
        var file=directory.resolve("mobs/zombie.yml");var yaml=new YamlConfiguration();yaml.load(file.toFile());
        yaml.set("max-health",2048);yaml.save(file.toFile());var original=Files.readString(file);
        store.reloadAsync(Runnable::run).join();
        var mob=store.mobs().get("zombie");assertNotNull(mob);assertEquals(1024,mob.maxHealth());
        assertEquals(DefinitionCodecTest.mob().abilities(),mob.abilities());
        assertEquals(DefinitionCodecTest.mob().phases(),mob.phases());
        assertTrue(store.dungeons().get("ejemplo").enabled());
        assertTrue(warnings.stream().anyMatch(w->w.contains("max-health")&&w.contains("validation.health-clamped")),warnings::toString);
        assertEquals(original,Files.readString(file));store.close();
    }
    @Test void legacyFinalKeyRoomLoadsAsAutomaticAndSaveNormalizesIt() throws Exception {
        var warnings=new java.util.ArrayList<String>();
        var store=new DefinitionStore(directory,new ConfigLoader(p->{},m->m==Material.IRON_BLOCK).load(new YamlConfiguration()),
                Set.of("test"),warnings::add,Runnable::run);
        store.save(DefinitionCodecTest.mob()).join();store.save(DefinitionCodecTest.dungeon()).join();
        var file=directory.resolve("dungeons/ejemplo.yml");var yaml=new YamlConfiguration();yaml.load(file.toFile());
        var rooms=new java.util.ArrayList<>(yaml.getMapList("rooms"));
        var last=new java.util.HashMap<String,Object>();rooms.getLast().forEach((k,v)->last.put(k.toString(),v));last.put("unlock","KEY");last.put("key-carrier-template-id","missing");
        rooms.set(rooms.size()-1,last);yaml.set("rooms",rooms);yaml.save(file.toFile());var original=Files.readString(file);
        var raw=new DefinitionCodec().decodeDungeon("ejemplo",yaml);
        store.reloadAsync(Runnable::run).join();
        var loaded=store.dungeons().get("ejemplo");assertTrue(loaded.enabled());
        assertEquals(dev.dasan.customdungeons.model.UnlockMode.AUTOMATIC,loaded.rooms().getLast().unlock());
        assertEquals(raw.rooms().getFirst(),loaded.rooms().getFirst());
        assertTrue(warnings.stream().anyMatch(w->w.contains("rooms[1].unlock")&&w.contains("validation.final-room-key")),warnings::toString);
        assertEquals(original,Files.readString(file));
        store.save(raw).join();assertEquals(loaded,store.dungeons().get("ejemplo"));
        yaml.load(file.toFile());assertEquals("AUTOMATIC",yaml.getMapList("rooms").getLast().get("unlock"));store.close();
    }
    @Test void keepsOldSnapshotUntilMainThreadAppliesAndRejectsConcurrentChanges() throws Exception {
        var worker = new Queue(); var main = new Queue(); var store = store(worker);
        Files.createDirectories(directory.resolve("mobs"));
        Files.writeString(directory.resolve("mobs/old.yml"), "entity-type: zombie\n");
        store.loadAll();
        Files.delete(directory.resolve("mobs/old.yml"));
        Files.writeString(directory.resolve("mobs/zombie.yml"), "entity-type: zombie\n");
        var reload = store.reloadAsync(main);
        assertTrue(store.isReloading());
        assertEquals(Set.of("old"), store.mobs().keySet());
        assertThrows(CompletionException.class, () -> store.reloadAsync(main).join());
        assertThrows(CompletionException.class, () -> store.deleteMob("zombie").join());
        worker.run();
        assertEquals(Set.of("old"), store.mobs().keySet());
        assertFalse(reload.isDone());
        main.run(); reload.join();
        assertFalse(store.isReloading());
        assertEquals(Set.of("zombie"), store.mobs().keySet());
        store.close();
    }
    @Test void drainsEarlierWritesBeforeReading() {
        var worker = new Queue(); var main = new Queue(); var store = store(worker);
        var save = store.save(DefinitionCodecTest.mob());
        var reload = store.reloadAsync(main);
        assertEquals(1, worker.tasks.size());
        worker.run(); save.join();
        worker.run(); main.run(); reload.join();
        assertEquals(DefinitionCodecTest.mob(), store.mobs().get("zombie"));
        store.close();
    }
    @Test void disablingDoesNotWaitForMainThreadOrPublishLateResults() throws Exception {
        var worker = new Queue(); var main = new Queue(); var store = store(worker);
        Files.createDirectories(directory.resolve("mobs"));
        Files.writeString(directory.resolve("mobs/zombie.yml"), "entity-type: zombie\n");
        var reload = store.reloadAsync(main); worker.run();
        store.close(); // Must not join the callback queued on this very thread.
        main.run();
        assertTrue(store.mobs().isEmpty());
        assertThrows(CompletionException.class, reload::join);
        assertThrows(CompletionException.class, () -> store.reloadAsync(main).join());
    }
    @ParameterizedTest
    @ValueSource(strings = {"mobs","dungeons"})
    void directoryIoFailureRetainsBothCaches(String kind) throws Exception {
        var store = store(Runnable::run);
        store.save(DefinitionCodecTest.mob()).join(); store.save(DefinitionCodecTest.dungeon()).join();
        var mobs = store.mobs(); var dungeons = store.dungeons();
        Files.move(directory.resolve(kind), directory.resolve(kind+"-backup"));
        Files.writeString(directory.resolve(kind), "not a directory");
        var reload = store.reloadAsync(Runnable::run);
        assertThrows(CompletionException.class, reload::join);
        assertSame(mobs, store.mobs()); assertSame(dungeons, store.dungeons());
        assertFalse(store.isReloading()); store.close();
    }
    @Test void unreadableDefinitionRetainsBothCachesInsteadOfDisablingDungeon() throws Exception {
        var store = store(Runnable::run);
        store.save(DefinitionCodecTest.mob()).join(); store.save(DefinitionCodecTest.dungeon()).join();
        var mobs = store.mobs(); var dungeons = store.dungeons();
        Path file = directory.resolve("dungeons/ejemplo.yml");
        var permissions = Files.getPosixFilePermissions(file);
        try {
            Files.setPosixFilePermissions(file, Set.of());
            var reload = store.reloadAsync(Runnable::run);
            assertThrows(CompletionException.class, reload::join);
            assertSame(mobs, store.mobs()); assertSame(dungeons, store.dungeons());
            assertFalse(store.isReloading());
        } finally { Files.setPosixFilePermissions(file, permissions); store.close(); }
    }
    @Test void publishedDefinitionsRefreshPersistedCooldownsForOnlinePlayers() throws Exception {
        var worker = new Queue(); var main = new Queue(); var store = store(worker);
        var plugin = mock(CustomDungeonsPlugin.class, RETURNS_DEEP_STUBS);
        when(plugin.getDataFolder()).thenReturn(directory.toFile());
        var storage = mock(Storage.class,withSettings().extraInterfaces(PendingExitPersistence.class));
        var config = mock(PluginConfig.class);
        var player = mock(Player.class);
        var uuid = UUID.randomUUID();
        var until = Instant.now().plusSeconds(300);
        when(player.getUniqueId()).thenReturn(uuid);
        when(player.isOnline()).thenReturn(true);
        when(plugin.isEnabled()).thenReturn(true);
        when(((PendingExitPersistence)storage).pendingExit(uuid)).thenReturn(CompletableFuture.completedFuture(Optional.empty()));
        when(storage.cooldownUntil(uuid,"ejemplo")).thenReturn(CompletableFuture.completedFuture(Optional.of(until)));
        var scheduler = plugin.getServer().getScheduler();
        doAnswer(call -> { main.execute(call.getArgument(1)); return null; })
                .when(scheduler).runTask(eq(plugin),any(Runnable.class));
        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getOnlinePlayers).thenReturn(List.of(player));
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
            var manager = new SessionManager(plugin,store,config,storage);
            while (!main.tasks.isEmpty()) main.run();
            verify(storage,never()).cooldownUntil(any(),anyString());
            Files.createDirectories(directory.resolve("dungeons"));
            DefinitionCodecTest.yaml(new DefinitionCodec().encode(DefinitionCodecTest.dungeon())).save(directory.resolve("dungeons/ejemplo.yml").toFile());
            var reload = store.reloadAsync(main); worker.run(); main.run(); reload.join();
            while (!main.tasks.isEmpty()) main.run();
            verify(storage).cooldownUntil(uuid,"ejemplo");
            var field = manager.getClass().getDeclaredField("cooldowns"); field.setAccessible(true);
            var cached = (Map<?,?>)field.get(manager);
            assertEquals(until,((Map<?,?>)cached.get(uuid)).get("ejemplo"));
            var extended = until.plusSeconds(300);
            when(storage.cooldownUntil(uuid,"ejemplo")).thenReturn(CompletableFuture.completedFuture(Optional.of(extended)));
            var next = store.reloadAsync(main); worker.run(); main.run(); next.join();
            while (!main.tasks.isEmpty()) main.run();
            verify(storage,times(2)).cooldownUntil(uuid,"ejemplo");
            assertEquals(extended,((Map<?,?>)cached.get(uuid)).get("ejemplo"));
            // A delayed database result must never shorten a newer in-memory reward cooldown.
            var awarded = extended.plusSeconds(300); manager.cacheCooldown(uuid,"ejemplo",awarded);
            when(storage.cooldownUntil(uuid,"ejemplo")).thenReturn(CompletableFuture.completedFuture(Optional.of(until)));
            var third = store.reloadAsync(main); worker.run(); main.run(); third.join();
            while (!main.tasks.isEmpty()) main.run();
            assertEquals(awarded,((Map<?,?>)cached.get(uuid)).get("ejemplo"));
            verify(storage,times(3)).cooldownUntil(uuid,"ejemplo");
            verify((PendingExitPersistence)storage,times(1)).pendingExit(uuid);
            verify(storage,never()).takePendingExit(any());
        }
        store.close();
    }
    @Test void rejectedWorkerDoesNotLeaveStoreBusy() {
        var store = store(task -> { throw new java.util.concurrent.RejectedExecutionException(); });
        var reload = store.reloadAsync(Runnable::run);
        assertThrows(CompletionException.class, reload::join);
        assertFalse(store.isReloading());
        store.close();
    }
    @Test void schedulingFailureRetainsSnapshotAndAllowsRetry() {
        var worker = new Queue(); var store = store(worker);
        var reload = store.reloadAsync(task -> { throw new IllegalStateException("scheduler stopped"); });
        worker.run();
        assertThrows(CompletionException.class, reload::join);
        assertFalse(store.isReloading());
        var main = new Queue(); var retry = store.reloadAsync(main);
        worker.run(); main.run(); retry.join(); store.close();
    }
}
