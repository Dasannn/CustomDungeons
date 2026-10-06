package dev.dasan.customdungeons.session;

import dev.dasan.customdungeons.model.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WaveSchedulerTest {
    private WaveScheduler scheduler(SpawnMode mode, int interval, WaveEntry... entries) {
        return new WaveScheduler(new WaveDef(List.of(entries), mode, interval, 0), n -> n, new Random(7));
    }
    @Test void simultaneousUsesEachEntryDelayAndScaling() {
        var wave = new WaveScheduler(new WaveDef(List.of(new WaveEntry("a", 2, 0), new WaveEntry("b", 1, 5)),
                SpawnMode.SIMULTANEOUS, 0, 0), n -> n * 2, new Random(7));
        assertEquals(Collections.nCopies(4, new SpawnOrder("a")), wave.tick(100, 0));
        assertFalse(wave.doneSpawning());
        assertTrue(wave.tick(104, 4).isEmpty());
        assertEquals(Collections.nCopies(2, new SpawnOrder("b")), wave.tick(105, 4));
        assertTrue(wave.doneSpawning());
        assertTrue(wave.tick(106, 6).isEmpty());
    }
    @Test void sequentialWaitsForNoAliveThenDelay() {
        var wave = scheduler(SpawnMode.SEQUENTIAL, 0, new WaveEntry("a", 2, 2), new WaveEntry("b", 1, 3));
        assertTrue(wave.tick(10, 0).isEmpty());
        assertEquals(Collections.nCopies(2, new SpawnOrder("a")), wave.tick(12, 0));
        assertTrue(wave.tick(13, 2).isEmpty());
        assertTrue(wave.tick(20, 0).isEmpty());
        assertTrue(wave.tick(22, 0).isEmpty());
        assertEquals(List.of(new SpawnOrder("b")), wave.tick(23, 0));
        assertTrue(wave.doneSpawning());
    }
    @Test void sequentialZeroDelayDoesNotSkipAliveSnapshot() {
        var wave = scheduler(SpawnMode.SEQUENTIAL, 0, new WaveEntry("a", 1, 0), new WaveEntry("b", 1, 0));
        assertEquals(List.of(new SpawnOrder("a")), wave.tick(0, 0));
        assertTrue(wave.tick(0, 0).isEmpty());
        assertTrue(wave.tick(1, 1).isEmpty());
        assertEquals(List.of(new SpawnOrder("b")), wave.tick(2, 0));
    }
    @Test void zeroScaledEntriesProduceNothing() {
        for (var mode : SpawnMode.values()) {
            var wave = new WaveScheduler(new WaveDef(List.of(new WaveEntry("a", 3, 10)), mode, 2, 0), n -> 0, new Random(7));
            assertTrue(wave.tick(0, 0).isEmpty());
            assertTrue(wave.doneSpawning());
        }
    }
    @Test void emptyWaveIsDone() {
        var wave = scheduler(SpawnMode.SIMULTANEOUS, 0);
        assertTrue(wave.doneSpawning());
        assertTrue(wave.tick(10, 0).isEmpty());
    }
    @Test void staggeredUsesIntervalAndEntryDelays() {
        var wave = scheduler(SpawnMode.STAGGERED, 4, new WaveEntry("a", 2, 2), new WaveEntry("b", 2, 3));
        assertTrue(wave.tick(10, 0).isEmpty());
        assertEquals(List.of(new SpawnOrder("a")), wave.tick(12, 0));
        assertTrue(wave.tick(15, 1).isEmpty());
        assertEquals(List.of(new SpawnOrder("a")), wave.tick(16, 1));
        assertTrue(wave.tick(22, 2).isEmpty());
        assertEquals(List.of(new SpawnOrder("b")), wave.tick(23, 2));
        assertEquals(List.of(new SpawnOrder("b")), wave.tick(27, 3));
        assertTrue(wave.doneSpawning());
    }
    @Test void randomShufflesUnitsWithInjectedRandom() {
        var expected = new ArrayList<>(List.of("a", "a", "a", "b", "b", "c"));
        Collections.shuffle(expected, new Random(7));
        var wave = scheduler(SpawnMode.RANDOM, 4, new WaveEntry("a", 3, 0),
                new WaveEntry("b", 2, 0), new WaveEntry("c", 1, 0));
        for (int i = 0; i < expected.size(); i++) {
            assertEquals(List.of(new SpawnOrder(expected.get(i))), wave.tick(10 + i * 4, i));
            if (i < expected.size() - 1) assertTrue(wave.tick(11 + i * 4, i + 1).isEmpty());
        }
        assertTrue(wave.doneSpawning());
    }
    @Test void randomUsesOnlyStaggerInterval() {
        var wave = scheduler(SpawnMode.RANDOM, 3, new WaveEntry("a", 2, 2));
        assertEquals(List.of(new SpawnOrder("a")), wave.tick(10, 0));
        assertTrue(wave.tick(12, 1).isEmpty());
        assertEquals(List.of(new SpawnOrder("a")), wave.tick(13, 1));
        assertTrue(wave.doneSpawning());
    }
    @Test void randomIgnoresEntryDelays() {
        var expected = new ArrayList<>(List.of("a", "a", "a", "b", "b", "c"));
        Collections.shuffle(expected, new Random(7));
        var wave = scheduler(SpawnMode.RANDOM, 4, new WaveEntry("a", 3, 100),
                new WaveEntry("b", 2, 7), new WaveEntry("c", 1, 50));
        for (int i = 0; i < expected.size(); i++) {
            assertEquals(List.of(new SpawnOrder(expected.get(i))), wave.tick(100 + i * 4, i));
            assertTrue(wave.tick(100 + i * 4, i + 1).isEmpty());
            if (i < expected.size() - 1) {
                assertFalse(wave.doneSpawning());
                assertTrue(wave.tick(103 + i * 4, i + 1).isEmpty());
            }
        }
        assertTrue(wave.doneSpawning());
        assertTrue(wave.tick(1000, expected.size()).isEmpty());
    }
    @Test void staggeredCatchesUpOnSkippedTicks() {
        var wave = scheduler(SpawnMode.STAGGERED, 2, new WaveEntry("a", 3, 0));
        assertEquals(List.of(new SpawnOrder("a")), wave.tick(10, 0));
        assertEquals(Collections.nCopies(2, new SpawnOrder("a")), wave.tick(20, 1));
        assertTrue(wave.doneSpawning());
    }
    @Test void zeroIntervalEmitsAllDueUnits() {
        for (var mode : List.of(SpawnMode.STAGGERED, SpawnMode.RANDOM)) {
            var wave = scheduler(mode, 0, new WaveEntry("a", 3, 0));
            assertEquals(Collections.nCopies(3, new SpawnOrder("a")), wave.tick(0, 0));
            assertTrue(wave.doneSpawning());
        }
    }
    @Test void spawnOrderRetainsTemplateId() {
        assertEquals("mob", new SpawnOrder("mob").templateId());
    }

}
