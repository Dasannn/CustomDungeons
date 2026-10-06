package dev.dasan.customdungeons.config;

import dev.dasan.customdungeons.model.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class SpawnerPresetTest {
    @TempDir Path directory;
    final DefinitionCodec codec = new DefinitionCodec();
    final WaveDef wave = new WaveDef(List.of(new WaveEntry("zombie", 4, 10)), SpawnMode.STAGGERED, 20, 60);
    SpawnerPreset preset() { return new SpawnerPreset("horde", "Horda", 3.4, List.of(wave)); }
    DungeonDef linked() {
        var d = DefinitionCodecTest.dungeon();
        var room = d.rooms().getLast();
        return SpawnerPresets.withRooms(d, List.of(new RoomDef(room.id(), room.region(), room.checkpoint(), null,
                UnlockMode.AUTOMATIC, null, List.of(new SpawnerDef("spawn", room.checkpoint(), 2.1, List.of(), "horde")))));
    }
    @Test void presetCodecRoundTrip() throws Exception {
        assertEquals(preset(), codec.decodeSpawnerPreset("horde", DefinitionCodecTest.yaml(codec.encode(preset()))));
    }
    @Test void linkedDungeonCodecAndOldYaml() throws Exception {
        var d = SpawnerPresets.withLibrary(linked(), List.of("horde"));
        assertEquals(d, codec.decodeDungeon(d.id(), DefinitionCodecTest.yaml(codec.encode(d))));
        assertTrue(codec.decodeDungeon("old", DefinitionCodecTest.yaml(Map.of())).spawnerPresets().isEmpty());
        assertNull(DefinitionCodecTest.dungeon().rooms().getFirst().spawners().getFirst().presetId());
    }
    @Test void resolvesAtStartAndPreservesRoomPositionAndRadius() {
        var resolved = SpawnerPresets.resolve(linked(), Map.of("horde", preset()));
        var original = linked().rooms().getFirst().spawners().getFirst();
        var placed = resolved.rooms().getFirst().spawners().getFirst();
        assertEquals(List.of(wave), placed.waves());
        assertEquals(original.location(), placed.location()); assertEquals(2.1, placed.radius());
        assertTrue(original.waves().isEmpty());
    }
    @Test void missingPresetIsBlockingAndNeverFallsBackToLocalWaves() {
        assertTrue(new Validator().validate(linked(), Map.of("zombie", DefinitionCodecTest.mob()), Map.of()).stream()
                .anyMatch(e -> e.messageKey().equals("validation.spawner-preset")));
        assertThrows(IllegalArgumentException.class, () -> SpawnerPresets.resolve(linked(), Map.of()));
    }
    @Test void validatesPresetWavesAndKeyCarrier() {
        var invalid = new SpawnerPreset("horde", "Horda", 3, List.of(new WaveDef(List.of(new WaveEntry("missing",0,0)),SpawnMode.SIMULTANEOUS,20,0)));
        assertTrue(new Validator().validate(linked(), Map.of(), Map.of("horde", invalid)).stream().anyMatch(e -> e.messageKey().equals("validation.template")));
        assertTrue(new Validator().validate(linked(), Map.of("zombie",DefinitionCodecTest.mob()), Map.of("horde",preset())).isEmpty());
    }
    @Test void makeLocalCopiesAndUnlinks() {
        var source = linked().rooms().getFirst().spawners().getFirst();
        var local = SpawnerPresets.makeLocal(source, Map.of("horde",preset()));
        assertNull(local.presetId()); assertEquals(List.of(wave),local.waves()); assertEquals(2.1,local.radius());
        assertEquals("horde",source.presetId()); assertThrows(UnsupportedOperationException.class, () -> local.waves().clear());
    }
    @Test void deleteInUsePersistsCopiesAndRemovesLibraryReference() throws Exception {
        var store = new DefinitionStore(directory,new ConfigLoader(s -> {}, m -> true).load(new org.bukkit.configuration.file.YamlConfiguration()),Set.of("test"), s -> {},Runnable::run);
        store.save(DefinitionCodecTest.mob()).join(); store.save(preset()).join();
        var d = SpawnerPresets.withLibrary(linked(),List.of("horde")); store.save(d).join();
        assertEquals(1,SpawnerPresets.usage("horde",store.dungeons()).size());
        store.deleteSpawnerPreset("horde").join(); store.reload();
        assertFalse(Files.exists(directory.resolve("spawners/horde.yml")));
        var saved = store.dungeons().get(d.id()); assertTrue(saved.enabled()); assertTrue(saved.spawnerPresets().isEmpty());
        var local = saved.rooms().getFirst().spawners().getFirst(); assertNull(local.presetId()); assertEquals(List.of(wave),local.waves());
        store.close();
    }
    @Test void usageCountsRoomsRatherThanPlacements() {
        var d=linked();var r=d.rooms().getFirst();
        var duplicate=SpawnerPresets.withRooms(d,List.of(new RoomDef(r.id(),r.region(),r.checkpoint(),null,UnlockMode.AUTOMATIC,null,
                List.of(r.spawners().getFirst(),r.spawners().getFirst()))));
        assertEquals(1,SpawnerPresets.usage("horde",Map.of(d.id(),duplicate)).size());
    }
    @Test void presetChangesDoNotAlterExistingSessionSnapshot() {
        var d=SpawnerPresets.resolve(linked(),Map.of("horde",preset()));
        var newer=new SpawnerPreset("horde","Nueva",4,List.of(new WaveDef(List.of(new WaveEntry("zombie",8,0)),SpawnMode.SEQUENTIAL,20,0)));
        assertEquals(4,d.rooms().getFirst().spawners().getFirst().waves().getFirst().entries().getFirst().count());
        assertEquals(8,SpawnerPresets.resolve(linked(),Map.of("horde",newer)).rooms().getFirst().spawners().getFirst().waves().getFirst().entries().getFirst().count());
    }
    @Test void stalePresetSaveDoesNotOverwriteOrResurrect() {
        var store=new DefinitionStore(directory,new ConfigLoader(s -> {},m -> true).load(new org.bukkit.configuration.file.YamlConfiguration()),Set.of("test"),s -> {},Runnable::run);
        store.save(DefinitionCodecTest.mob()).join();store.save(preset(),null).join();
        assertThrows(java.util.concurrent.CompletionException.class,()->store.save(preset(),null).join());
        store.deleteSpawnerPreset("horde").join();
        assertThrows(java.util.concurrent.CompletionException.class,()->store.save(preset(),preset()).join());
        assertTrue(store.spawnerPresets().isEmpty());assertThrows(java.util.concurrent.CompletionException.class,store::close);
    }
    @Test void asyncReloadPublishesPresetsAndDungeonsTogetherAndDisablesMissingOrigins() throws Exception {
        var worker=new DefinitionReloadTest.Queue();var main=new DefinitionReloadTest.Queue();
        var store=new DefinitionStore(directory,new ConfigLoader(s -> {},m -> true).load(new org.bukkit.configuration.file.YamlConfiguration()),Set.of("test"),s -> {},worker);
        Files.createDirectories(directory.resolve("mobs"));Files.createDirectories(directory.resolve("spawners"));Files.createDirectories(directory.resolve("dungeons"));
        Files.writeString(directory.resolve("mobs/zombie.yml"),DefinitionCodecTest.yaml(codec.encode(DefinitionCodecTest.mob())).saveToString());
        Files.writeString(directory.resolve("spawners/horde.yml"),DefinitionCodecTest.yaml(codec.encode(preset())).saveToString());
        Files.writeString(directory.resolve("dungeons/ejemplo.yml"),DefinitionCodecTest.yaml(codec.encode(linked())).saveToString());
        var future=store.reloadAsync(main);worker.run();
        assertTrue(store.dungeons().isEmpty());assertTrue(store.spawnerPresets().isEmpty());main.run();future.join();
        assertEquals(preset(),store.spawnerPresets().get("horde"));assertTrue(store.dungeons().get("ejemplo").enabled());
        Files.delete(directory.resolve("spawners/horde.yml"));future=store.reloadAsync(main);worker.run();main.run();future.join();
        assertFalse(store.dungeons().get("ejemplo").enabled());store.close();
    }
    @Test void malformedPreferredListAndRadiusAreRejected() throws Exception {
        assertThrows(IllegalArgumentException.class,()->codec.decodeDungeon("bad",DefinitionCodecTest.yaml(Map.of("spawner-presets",List.of(3)))));
        for(double radius:new double[]{Double.NaN,0,65,3.45}) assertTrue(new Validator().validate(new SpawnerPreset("horde","Horda",radius,List.of(wave)),Map.of("zombie",DefinitionCodecTest.mob())).stream()
                .anyMatch(e->e.messageKey().equals("validation.spawner-radius")));
    }

}
