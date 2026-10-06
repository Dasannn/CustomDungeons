package dev.dasan.customdungeons.model;

import java.util.List;

public record WaveDef(List<WaveEntry> entries, SpawnMode mode, int staggerIntervalTicks, int pauseAfterTicks) {
    public WaveDef {
        entries = List.copyOf(entries);
    }
}
