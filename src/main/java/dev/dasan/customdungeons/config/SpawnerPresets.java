package dev.dasan.customdungeons.config;

import dev.dasan.customdungeons.model.*;
import java.util.*;

/** Pure transformations, shared by validation, session snapshots and draft editing. */
public final class SpawnerPresets {
    private SpawnerPresets() {}
    public static List<WaveDef> waves(SpawnerDef spawner, Map<String,SpawnerPreset> presets) {
        if (spawner.presetId() == null) return spawner.waves();
        var preset = presets.get(spawner.presetId());
        if (preset == null) throw new IllegalArgumentException("validation.spawner-preset");
        return preset.waves();
    }
    public static SpawnerDef makeLocal(SpawnerDef spawner, Map<String,SpawnerPreset> presets) {
        return new SpawnerDef(spawner.id(),spawner.location(),spawner.radius(),waves(spawner,presets));
    }
    public static DungeonDef resolve(DungeonDef dungeon, Map<String,SpawnerPreset> presets) {
        return withRooms(dungeon,dungeon.rooms().stream().map(r -> room(r,r.spawners().stream()
                .map(s -> makeLocal(s,presets)).toList())).toList());
    }
    public static DungeonDef detach(DungeonDef dungeon, String id, Map<String,SpawnerPreset> presets) {
        return withLibrary(withRooms(dungeon,dungeon.rooms().stream().map(r -> room(r,r.spawners().stream()
                .map(s -> Objects.equals(id,s.presetId()) ? makeLocal(s,presets) : s).toList())).toList()),
                dungeon.spawnerPresets().stream().filter(p -> !p.equals(id)).toList());
    }
    public record Usage(String dungeonId, String dungeonName, String roomId) {}
    /** One usage per room, even if it contains multiple placements of this preset. */
    public static List<Usage> usage(String id, Map<String,DungeonDef> dungeons) {
        return dungeons.values().stream().sorted(Comparator.comparing(DungeonDef::id))
                .flatMap(d -> d.rooms().stream().filter(r -> r.spawners().stream().anyMatch(s -> Objects.equals(id,s.presetId())))
                        .map(r -> new Usage(d.id(),d.displayName(),r.id()))).toList();
    }
    private static RoomDef room(RoomDef r,List<SpawnerDef> spawners) {
        return new RoomDef(r.id(),r.region(),r.checkpoint(),r.door(),r.unlock(),r.keyCarrierTemplateId(),spawners,r.openingMode(),r.ambience());
    }
    public static DungeonDef withRooms(DungeonDef d,List<RoomDef> rooms) { return copy(d,rooms,d.spawnerPresets()); }
    public static DungeonDef withLibrary(DungeonDef d,List<String> ids) { return copy(d,d.rooms(),ids); }
    private static DungeonDef copy(DungeonDef d,List<RoomDef> rooms,List<String> ids) {
        return new DungeonDef(d.id(),d.displayName(),d.enabled(),d.lobby(),d.exit(),d.minPlayers(),d.maxPlayers(),
                d.lobbyCountdownSeconds(),d.lives(),d.keepInventory(),d.timeLimitSeconds(),d.cooldownSeconds(),
                d.requirePermission(),d.scaling(),d.hooks(),d.reward(),rooms,ids,d.area(),d.startMode(),d.plates(),d.plateCountdownSeconds(),d.entranceDoor(),
                d.teleportOnStart(),d.introCinematic(),d.introSeconds(),d.finishMode(),d.exitGraceSeconds(),d.finishDestination(),d.exitPlates());
    }
}
