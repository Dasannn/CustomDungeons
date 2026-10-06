package dev.dasan.customdungeons.session;

import dev.dasan.customdungeons.session.WaveScheduler.SpawnOrder;

import dev.dasan.customdungeons.model.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RoomProgressTest {
    private WaveDef wave(int pause) {
        return new WaveDef(List.of(new WaveEntry("mob", 1, 0)), SpawnMode.SIMULTANEOUS, 0, pause);
    }
    private SpawnerDef spawner(String id, WaveDef... waves) {
        return new SpawnerDef(id, new Point("w", 0, 0, 0, 0, 0), 1, List.of(waves));
    }
    private RoomProgress room(SpawnerDef... spawners) {
        var point = new Point("w", 0, 0, 0, 0, 0);
        return new RoomProgress(new RoomDef("room", Region.of("w", new BlockPos(0,0,0), new BlockPos(1,1,1)),
                point, null, UnlockMode.AUTOMATIC, null, List.of(spawners)), n -> n, new Random(1));
    }
    @Test void roomClearsOnlyWhenAllSpawnersDoneAndNoneAlive() {
        var room = room(spawner("a", wave(0)), spawner("b", wave(0), wave(0)));
        assertFalse(room.cleared());
        room.start(10);
        assertEquals(Set.of("a", "b"), room.tick(10, Map.of()).keySet());
        assertFalse(room.cleared()); // returned orders are not in the incoming alive snapshot yet
        assertTrue(room.tick(11, Map.of("a", 1, "b", 1)).isEmpty());
        assertFalse(room.cleared());
        assertEquals(List.of(new SpawnOrder("mob")), room.tick(12, Map.of("b", 0)).get("b"));
        assertFalse(room.cleared());
        room.tick(13, Map.of("b", 1));
        assertFalse(room.cleared());
        room.tick(14, Map.of());
        assertTrue(room.cleared());
    }
    @Test void pauseBetweenWaves() {
        var room = room(spawner("a", wave(5), wave(0)));
        room.start(20);
        room.tick(20, Map.of());
        room.tick(21, Map.of("a", 1));
        assertTrue(room.tick(24, Map.of()).isEmpty());
        assertTrue(room.tick(28, Map.of()).isEmpty());
        assertEquals(1, room.tick(29, Map.of()).get("a").size());
    }
    @Test void deadByAnyCauseCounts() {
        var room = room(spawner("a", wave(0)));
        room.start(0);
        room.tick(0, Map.of());
        room.tick(1, Map.of("a", 1));
        room.tick(2, Map.of("a", 0)); // lava, despawn, /kill: no player attribution required
        assertTrue(room.cleared());
    }
    @Test void spawnersAdvanceIndependently() {
        var room = room(spawner("a", wave(0), wave(0)), spawner("b", wave(0)));
        room.start(0);
        room.tick(0, Map.of());
        assertEquals(Set.of("a"), room.tick(1, Map.of("b", 1)).keySet());
        assertFalse(room.cleared());
    }
    @Test void explicitStartAnchorsDelayedFirstWave() {
        var delayed = new WaveDef(List.of(new WaveEntry("mob", 1, 5)), SpawnMode.SIMULTANEOUS, 0, 0);
        var room = room(spawner("a", delayed));
        room.start(10);
        assertEquals(1, room.tick(15, Map.of()).get("a").size());
    }
    @Test void emptyWavesAndZeroScalingFinish() {
        var room = room(spawner("a"));
        room.start(0);
        room.tick(0, Map.of());
        assertTrue(room.cleared());
    }
    @Test void cannotTickBeforeStartOrStartTwice() {
        var room = room(spawner("a", wave(0)));
        assertThrows(IllegalStateException.class, () -> room.tick(0, Map.of()));
        room.start(0);
        assertThrows(IllegalStateException.class, () -> room.start(1));
    }
}
