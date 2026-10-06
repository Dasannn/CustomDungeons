package dev.dasan.customdungeons.model;

import java.util.List;

/** Reusable waves; placement retains its own location and radius. */
public record SpawnerPreset(String id, String name, double radius, List<WaveDef> waves) {
    public SpawnerPreset { waves = List.copyOf(waves); }
}
