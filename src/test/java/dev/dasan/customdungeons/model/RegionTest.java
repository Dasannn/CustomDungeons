package dev.dasan.customdungeons.model;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class RegionTest {
    @Test void regionNormalizesAndContainsInclusive() {
        Region r = Region.of("w", new BlockPos(5, 10, 5), new BlockPos(0, 0, 0));
        assertEquals(new BlockPos(0,0,0), r.min());
        assertEquals(new BlockPos(5,10,5), r.max());
        assertTrue(r.contains("w", 5,10,5));
        assertTrue(r.contains("w", 0,0,0));
        assertFalse(r.contains("w", 6,0,0));
        assertFalse(r.contains("other", 1,1,1));
        assertEquals(6L * 11 * 6, r.volume());
    }
    @Test void definitionsSnapshotCollections() {
        var entries = new java.util.ArrayList<WaveEntry>();
        entries.add(new WaveEntry("mob", 1, 0));
        WaveDef wave = new WaveDef(entries, SpawnMode.SIMULTANEOUS,0,0);
        entries.clear();
        assertEquals(1, wave.entries().size());
        assertThrows(UnsupportedOperationException.class, () -> wave.entries().clear());
    }
    @Test void volumeUsesLongArithmetic() {
        assertEquals(4_294_967_296L, Region.of("w", new BlockPos(Integer.MIN_VALUE,0,0), new BlockPos(Integer.MAX_VALUE,0,0)).volume());
    }
}
