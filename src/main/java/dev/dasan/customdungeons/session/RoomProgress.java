package dev.dasan.customdungeons.session;

import dev.dasan.customdungeons.session.WaveScheduler.SpawnOrder;

import dev.dasan.customdungeons.model.RoomDef;
import dev.dasan.customdungeons.model.SpawnerDef;
import dev.dasan.customdungeons.model.WaveDef;
import java.util.*;
import java.util.function.IntUnaryOperator;

/** Independent wave progression for each spawner, driven by externally supplied live counts. */
public final class RoomProgress {
    private final List<Progress> spawners = new ArrayList<>();
    private final IntUnaryOperator scaledCount;
    private final Random rng;
    private boolean started;
    private boolean cleared;

    public RoomProgress(RoomDef room, IntUnaryOperator scaledCount, Random rng) {
        this.scaledCount = Objects.requireNonNull(scaledCount);
        this.rng = Objects.requireNonNull(rng);
        for (SpawnerDef spawner : room.spawners()) spawners.add(new Progress(spawner));
    }

    public void start(long tick) {
        if (started) throw new IllegalStateException("Room already started");
        started = true;
        for (Progress progress : spawners) progress.startWave(tick);
    }

    /** Missing spawner counts mean zero. Returned orders must be included in the next live snapshot. */
    public Map<String, List<SpawnOrder>> tick(long tick, Map<String, Integer> aliveBySpawner) {
        if (!started) throw new IllegalStateException("Room not started");
        Map<String, List<SpawnOrder>> orders = new LinkedHashMap<>();
        cleared = true;
        for (Progress progress : spawners) {
            int alive = aliveBySpawner.getOrDefault(progress.spawner.id(), 0);
            List<SpawnOrder> spawned = progress.tick(tick, alive);
            if (!spawned.isEmpty()) orders.put(progress.spawner.id(), spawned);
            if (!progress.finished() || alive != 0 || !spawned.isEmpty()) cleared = false;
        }
        return Collections.unmodifiableMap(orders);
    }

    public boolean cleared() { return cleared; }

    private final class Progress {
        private final SpawnerDef spawner;
        private int index;
        private WaveScheduler scheduler;
        private Long nextStart;
        private Long lastSpawn;

        private Progress(SpawnerDef spawner) { this.spawner = spawner; }
        private boolean finished() { return index >= spawner.waves().size(); }
        private void startWave(long tick) {
            if (finished()) return;
            scheduler = new WaveScheduler(spawner.waves().get(index), scaledCount, rng);
            scheduler.startAt(tick);
            nextStart = null;
            lastSpawn = null;
        }
        private List<SpawnOrder> tick(long tick, int alive) {
            while (!finished()) {
                if (nextStart != null) {
                    if (tick < nextStart) return List.of();
                    startWave(nextStart);
                }
                List<SpawnOrder> orders = scheduler.tick(tick, alive);
                if (!orders.isEmpty()) {
                    lastSpawn = tick;
                    return orders; // incoming alive does not include these orders yet
                }
                if (!scheduler.doneSpawning() || alive != 0 || (lastSpawn != null && tick <= lastSpawn))
                    return List.of();
                WaveDef completed = spawner.waves().get(index++);
                if (finished()) return List.of();
                nextStart = tick + completed.pauseAfterTicks();
            }
            return List.of();
        }
    }
}
