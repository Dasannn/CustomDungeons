package dev.dasan.customdungeons.model;

import java.util.List;

public record OptionalZoneDef(String id, Region region, List<SpawnerDef> spawners) {
    public OptionalZoneDef { spawners = List.copyOf(spawners); }
}
