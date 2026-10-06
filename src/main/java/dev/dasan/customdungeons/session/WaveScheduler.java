package dev.dasan.customdungeons.session;

import dev.dasan.customdungeons.model.SpawnMode;
import dev.dasan.customdungeons.model.WaveDef;
import dev.dasan.customdungeons.model.WaveEntry;
import java.util.*;
import java.util.function.IntUnaryOperator;

/**
 * Pure, tick-driven spawning. Live counts belong to this wave; spawn queue capacity is handled by T09.
 * In RANDOM mode, all scaled units from all entries are shuffled together using the supplied rng.
 * Entry delayTicks are ignored: the first unit is due at the wave start tick, and each subsequent
 * unit is due staggerIntervalTicks after the previous one.
 */
public final class WaveScheduler {
    private record Entry(String templateId, int count, int delayTicks) {}
    private record Unit(String templateId, int delayTicks) {}

    private final SpawnMode mode;
    private final int interval;
    private final List<Entry> entries = new ArrayList<>();
    private final List<Unit> units = new ArrayList<>();
    private final Set<Integer> emittedEntries = new HashSet<>();
    private Long startTick;
    private Long nextDue;
    private Long lastEmission;
    private boolean waitingForClear;
    private int cursor;

    public WaveScheduler(WaveDef wave, IntUnaryOperator scaledCount, Random rng) {
        Objects.requireNonNull(scaledCount);
        Objects.requireNonNull(rng);
        mode = Objects.requireNonNull(wave.mode());
        interval = wave.staggerIntervalTicks();
        for (WaveEntry entry : wave.entries()) {
            int count = scaledCount.applyAsInt(entry.count());
            if (count < 0) throw new IllegalArgumentException("Negative scaled count");
            if (count == 0) continue;
            entries.add(new Entry(entry.templateId(), count, entry.delayTicks()));
            if (mode == SpawnMode.STAGGERED || mode == SpawnMode.RANDOM) {
                for (int i = 0; i < count; i++) {
                    // Only ordered entries pay their delay, once at the first unit.
                    units.add(new Unit(entry.templateId(), mode == SpawnMode.STAGGERED && i == 0 ? entry.delayTicks() : 0));
                }
            }
        }
        if (mode == SpawnMode.RANDOM) Collections.shuffle(units, rng);
    }

    /** RoomProgress supplies the actual start even when its first tick is observed later. */
    void startAt(long tick) {
        if (startTick != null) throw new IllegalStateException("Wave already started");
        startTick = tick;
        if (!entries.isEmpty()) {
            int delay = mode == SpawnMode.STAGGERED || mode == SpawnMode.RANDOM
                    ? units.getFirst().delayTicks() : entries.getFirst().delayTicks();
            nextDue = tick + delay;
        }
    }

    /** The first call anchors a standalone wave. Repeated calls cannot re-emit orders. */
    public List<SpawnOrder> tick(long tick, int alive) {
        if (startTick == null) startAt(tick);
        if (doneSpawning()) return List.of();
        return switch (mode) {
            case SIMULTANEOUS -> simultaneous(tick);
            case SEQUENTIAL -> sequential(tick, alive);
            case STAGGERED, RANDOM -> staggered(tick);
        };
    }

    private List<SpawnOrder> simultaneous(long tick) {
        List<SpawnOrder> orders = new ArrayList<>();
        for (int i = 0; i < entries.size(); i++) {
            Entry entry = entries.get(i);
            if (!emittedEntries.contains(i) && tick >= startTick + entry.delayTicks()) {
                addEntry(orders, entry);
                emittedEntries.add(i);
            }
        }
        return List.copyOf(orders);
    }

    private List<SpawnOrder> sequential(long tick, int alive) {
        if (waitingForClear) {
            // The incoming count on the emission tick predates the orders just returned.
            if (alive != 0 || tick <= lastEmission) return List.of();
            nextDue = tick + entries.get(cursor).delayTicks();
            waitingForClear = false;
        }
        if (tick < nextDue || alive != 0) return List.of();
        List<SpawnOrder> orders = new ArrayList<>();
        addEntry(orders, entries.get(cursor++));
        lastEmission = tick;
        waitingForClear = cursor < entries.size();
        return List.copyOf(orders);
    }

    private List<SpawnOrder> staggered(long tick) {
        List<SpawnOrder> orders = new ArrayList<>();
        while (cursor < units.size() && tick >= nextDue) {
            orders.add(new SpawnOrder(units.get(cursor++).templateId()));
            if (cursor < units.size()) nextDue += (long) interval + units.get(cursor).delayTicks();
        }
        return List.copyOf(orders);
    }

    private static void addEntry(List<SpawnOrder> orders, Entry entry) {
        for (int i = 0; i < entry.count(); i++) orders.add(new SpawnOrder(entry.templateId()));
    }

    public boolean doneSpawning() {
        return switch (mode) {
            case SIMULTANEOUS -> emittedEntries.size() == entries.size();
            case SEQUENTIAL -> cursor == entries.size();
            case STAGGERED, RANDOM -> cursor == units.size();
        };
    }
}
