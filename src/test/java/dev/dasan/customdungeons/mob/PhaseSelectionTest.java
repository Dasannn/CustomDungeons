package dev.dasan.customdungeons.mob;

import dev.dasan.customdungeons.model.PhaseDef;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PhaseSelectionTest {
    private PhaseDef phase(double threshold) {
        return new PhaseDef(threshold, false, List.of(), List.of(), Map.of(), List.of(),
                0, List.of(), null, null, null, null, 0);
    }
    @Test void phaseSelectionPicksNextCrossedThresholdOnce() {
        var phases = List.of(phase(0.66), phase(0.33));
        assertEquals(-1, BossController.nextPhase(phases, -1, 1));
        assertEquals(0, BossController.nextPhase(phases, -1, 0.66));
        assertEquals(-1, BossController.nextPhase(phases, 0, 0.5));
        assertEquals(1, BossController.nextPhase(phases, 0, 0.33));
        assertEquals(-1, BossController.nextPhase(phases, 1, 0.1));
    }
    @Test void largeHitStillSelectsPhasesInDefinitionOrder() {
        var phases = List.of(phase(0.66), phase(0.33));
        assertEquals(0, BossController.nextPhase(phases, -1, 0.1));
        assertEquals(1, BossController.nextPhase(phases, 0, 0.1));
    }
    @Test void healingDoesNotRepeatEarlierPhases() {
        var phases = List.of(phase(0.66), phase(0.33));
        assertEquals(-1, BossController.nextPhase(phases, 0, 0.9));
        assertEquals(-1, BossController.nextPhase(phases, 1, 0.2));
    }
    @Test void noPhasesMeansNoTransition() {
        assertEquals(-1, BossController.nextPhase(List.of(), -1, 0));
    }
}
