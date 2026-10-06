package dev.dasan.customdungeons.mob;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ScalingTest {
    @Test void scalingCountAndHealth() {
        assertEquals(7, Scaling.count(4, 4, 1, 0.25));
        assertEquals(1.45, Scaling.healthMultiplier(4, 1, 0.15), 1e-12);
    }
    @Test void belowMinimumDoesNotReduceDifficulty() {
        assertEquals(4, Scaling.count(4, 0, 2, 0.25));
        assertEquals(1, Scaling.healthMultiplier(0, 2, 0.15));
    }
    @Test void roundsUpAndHandlesZeroScalingAndEmptyEntries() {
        assertEquals(2, Scaling.count(1, 2, 1, 0.25));
        assertEquals(4, Scaling.count(4, 9, 1, 0));
        assertEquals(0, Scaling.count(0, 4, 1, 0.25));
    }
}
