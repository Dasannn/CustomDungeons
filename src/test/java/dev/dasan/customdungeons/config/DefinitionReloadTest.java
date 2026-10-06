package dev.dasan.customdungeons.config;

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
