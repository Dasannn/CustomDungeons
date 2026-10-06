package dev.dasan.customdungeons.config;

import dev.dasan.customdungeons.model.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.logging.Logger;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class DefinitionCodecTest {
    @TempDir Path directory;
    static final Point POINT = new Point("dungeons", 1, 64, 2, 90, 0);
    static final Region REGION = Region.of("dungeons", new BlockPos(0,60,0), new BlockPos(10,70,10));
    static DungeonDef dungeon() {
        var wave = new WaveDef(List.of(new WaveEntry("zombie", 2, 0)), SpawnMode.STAGGERED, 20, 40);
        var spawner = new SpawnerDef("spawn", POINT, 3, List.of(wave));
        return new DungeonDef("ejemplo", "&6Ejemplo", true, POINT, POINT, 1, 4, 30, 3, false, 600, 60,
                true, new ScalingDef(.25,.15), Map.of(HookEvent.START,List.of("say {dungeon}")),
                new RewardDef(List.of(), 10, 5, List.of("say {player}")), List.of(
                new RoomDef("first", REGION, POINT, REGION, UnlockMode.KEY,"zombie",List.of(spawner)),
                new RoomDef("last", REGION, POINT, null, UnlockMode.AUTOMATIC,null,List.of(spawner))));
    }
    static MobTemplate mob() {
        var ability = new AbilityInstance("test", Trigger.ON_HIT, 2, TargetMode.NEAREST, 10, 20, .5, 10, Map.of("damage", 3));
        var combo = new ComboDef("combo",Trigger.EVERY_X_SECONDS,3,TargetMode.CURRENT_TARGET,10,40,
                List.of(new ComboStep("test",Map.of("damage", 2), 0),new ComboStep("test",Map.of(),20)));
        var phase = new PhaseDef(.66,true,List.of(ability),List.of(combo),Map.of(),List.of(new PotionDef("minecraft:speed",1,false)),
                .1,List.of(new WaveEntry("zombie",1,0)),"title","subtitle","minecraft:test","custom:music",20);
        return new MobTemplate("zombie","minecraft:zombie","&aZombie",40,6,.3,.2,1.2,Map.of(),
                List.of(new PotionDef("minecraft:strength",0,true)),List.of(ability),List.of(combo),true,"RED","custom:music",List.of(phase),false);
    }
    static YamlConfiguration yaml(Map<String,Object> map) throws Exception {
        var y = new YamlConfiguration(); map.forEach(y::set);
        var decoded = new YamlConfiguration(); decoded.loadFromString(y.saveToString()); return decoded;
    }
    @Test void codecRoundTripDungeon() throws Exception {
        var codec = new DefinitionCodec(); assertEquals(dungeon(),codec.decodeDungeon("ejemplo",yaml(codec.encode(dungeon()))));
    }
    @Test void codecRoundTripMobWithPhasesAndCombos() throws Exception {
        var codec = new DefinitionCodec(); assertEquals(mob(),codec.decodeMob("zombie",yaml(codec.encode(mob()))));
    }
    @Test void invalidYamlLoadsDisabled() throws Exception {
        Files.createDirectories(directory.resolve("dungeons"));
        Files.writeString(directory.resolve("dungeons/broken.yml"),"rooms: [\n");
        var store = store(); assertDoesNotThrow(store::loadAll);
        assertFalse(store.dungeons().get("broken").enabled());
    }
    @Test void missingTemplateLoadsDisabled() throws Exception {
        Files.createDirectories(directory.resolve("dungeons"));
        yaml(new DefinitionCodec().encode(dungeon())).save(directory.resolve("dungeons/ejemplo.yml").toFile());
        var store = store(); store.loadAll(); assertFalse(store.dungeons().get("ejemplo").enabled());
    }
    @Test void exampleResourceLoadsTwoRooms() throws Exception {
        Files.createDirectories(directory.resolve("dungeons")); Files.createDirectories(directory.resolve("mobs"));
        try(var in = getClass().getResourceAsStream("/dungeons/ejemplo.yml")) {
            assertNotNull(in); Files.copy(in,directory.resolve("dungeons/ejemplo.yml"));
        }
        yaml(new DefinitionCodec().encode(mob())).save(directory.resolve("mobs/zombie.yml").toFile());
        var store = store(); store.loadAll();
        assertTrue(store.dungeons().get("ejemplo").enabled()); assertEquals(2,store.dungeons().get("ejemplo").rooms().size());
    }
    @Test void asyncSaveDeleteAndReload() {
        var store = store(); store.save(mob()).join(); store.save(dungeon()).join(); store.reload();
        assertEquals(dungeon(),store.dungeons().get("ejemplo"));
        store.deleteDungeon("ejemplo").join(); store.deleteMob("zombie").join(); store.reload();
        assertTrue(store.dungeons().isEmpty()); assertTrue(store.mobs().isEmpty());
    }
    @Test void invalidSaveDoesNotWriteAndUnsafeIdsAreRejected() {
        var store = store(); assertThrows(Exception.class,()->store.save(dungeon()).join());
        assertFalse(Files.exists(directory.resolve("dungeons/ejemplo.yml")));
        assertThrows(IllegalArgumentException.class,()->store.deleteDungeon("../escape"));
    }
    @Test void malformedEnumWarningDoesNotLeakValue() throws Exception {
        Files.createDirectories(directory.resolve("mobs"));
        Files.writeString(directory.resolve("mobs/bad.yml"),"equipment:\n  PRIVATE_VALUE:\n    drop-chance: 0\n");
        var warnings = new ArrayList<String>();
        var store = new DefinitionStore(directory,new ConfigLoader(path->{},material->material == org.bukkit.Material.IRON_BLOCK).load(new YamlConfiguration()),Set.of("test"),warnings::add,Runnable::run);
        store.loadAll(); assertTrue(store.mobs().isEmpty()); assertFalse(warnings.isEmpty());
        assertTrue(warnings.stream().noneMatch(w->w.contains("PRIVATE_VALUE")));
    }
    @Test void asyncWriteOnlyRunsOnExecutorAndPublishesAfterSuccess() {
        var tasks = new ArrayDeque<Runnable>();
        var store = new DefinitionStore(directory,new ConfigLoader(path->{},material->material == org.bukkit.Material.IRON_BLOCK).load(new YamlConfiguration()),Set.of("test"),Logger.getLogger("test"),tasks::add);
        var saved = store.save(mob()); assertFalse(saved.isDone()); assertTrue(store.mobs().isEmpty());
        assertFalse(Files.exists(directory.resolve("mobs/zombie.yml")));
        tasks.remove().run(); saved.join(); assertEquals(mob(),store.mobs().get("zombie"));
        assertTrue(Files.exists(directory.resolve("mobs/zombie.yml")));
    }
    @Test void deletingMobDisablesReferencingDungeon() {
        var store = store(); store.save(mob()).join(); store.save(dungeon()).join(); store.deleteMob("zombie").join();
        assertFalse(store.dungeons().get("ejemplo").enabled()); store.reload(); assertFalse(store.dungeons().get("ejemplo").enabled());
    }
    @Test void symlinkDirectoryCannotWriteOutsideDataFolder() throws Exception {
        Path outside = directory.resolve("outside"); Files.createDirectories(outside); Files.createSymbolicLink(directory.resolve("mobs"),outside);
        assertThrows(Exception.class,()->store().save(mob()).join()); assertFalse(Files.exists(outside.resolve("zombie.yml")));
    }
    @Test void closeFlushesPendingWritesAndRejectsNewSaves() throws Exception {
        try (var executor = java.util.concurrent.Executors.newSingleThreadExecutor()) {
            var store = new DefinitionStore(directory,new ConfigLoader(path->{},material->material == org.bukkit.Material.IRON_BLOCK).load(new YamlConfiguration()),Set.of("test"),Logger.getLogger("test"),executor);
            var saved = store.save(mob()); store.close(); assertTrue(saved.isDone()); saved.join();
            assertTrue(Files.exists(directory.resolve("mobs/zombie.yml")));
            assertThrows(Exception.class,()->store.save(mob()).join());
        }
    }
    @Test void enqueueDoesNotWaitForInFlightDiskMutation() throws Exception {
        var entered = new java.util.concurrent.CountDownLatch(1);
        var release = new java.util.concurrent.CountDownLatch(1);
        try (var executor = java.util.concurrent.Executors.newSingleThreadExecutor()) {
            var store = new DefinitionStore(directory,new ConfigLoader(path->{},material->material == org.bukkit.Material.IRON_BLOCK).load(new YamlConfiguration()),Set.of("test"),path->{
                entered.countDown();
                try { release.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new RuntimeException(e); }
            },executor);
            store.save(mob()).join(); store.save(dungeon()).join();
            var deleted = store.deleteMob("zombie");
            java.util.concurrent.CompletableFuture<java.util.concurrent.CompletableFuture<Void>> enqueued = null;
            try {
                assertTrue(entered.await(5,java.util.concurrent.TimeUnit.SECONDS));
                enqueued = java.util.concurrent.CompletableFuture.supplyAsync(()->store.save(mob()));
                // The caller must enqueue immediately even while the worker holds its mutation lock.
                enqueued.get(2,java.util.concurrent.TimeUnit.SECONDS);
            } finally { release.countDown(); }
            deleted.join(); if (enqueued != null) enqueued.join().join(); store.close();
        }
    }
    private DefinitionStore store() {
        return new DefinitionStore(directory, new ConfigLoader(path->{},material->material == org.bukkit.Material.IRON_BLOCK).load(new YamlConfiguration()),Set.of("test"),Logger.getLogger("test"),Runnable::run);
    }
}
