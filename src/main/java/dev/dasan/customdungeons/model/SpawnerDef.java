package dev.dasan.customdungeons.model;

import java.util.List;

public record SpawnerDef(String id, Point location, double radius, List<WaveDef> waves) {
    public SpawnerDef {
        waves = List.copyOf(waves);
    }
}
