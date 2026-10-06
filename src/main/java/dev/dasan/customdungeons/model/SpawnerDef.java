package dev.dasan.customdungeons.model;

import java.util.List;

public record SpawnerDef(String id, Point location, double radius, List<WaveDef> waves, @org.jspecify.annotations.Nullable String presetId) {
    public SpawnerDef(String id, Point location, double radius, List<WaveDef> waves) {
        this(id, location, radius, waves, null);
    }
    public SpawnerDef {
        waves = List.copyOf(waves);
    }
}
