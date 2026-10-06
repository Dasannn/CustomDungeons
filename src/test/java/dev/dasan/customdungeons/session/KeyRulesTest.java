package dev.dasan.customdungeons.session;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class KeyRulesTest {
    @Test void missingOrBelowWorldRespawns() {
        assertTrue(KeyService.shouldRespawnKey(KeyService.Cause.REMOVED,64,-64));
        assertTrue(KeyService.shouldRespawnKey(KeyService.Cause.TICK,-65,-64));
        assertFalse(KeyService.shouldRespawnKey(KeyService.Cause.TICK,-64,-64));
    }
    @Test void pickupAndConsumptionNeverDuplicate() {
        assertFalse(KeyService.shouldRespawnKey(KeyService.Cause.PICKED_UP,-100,-64));
        assertFalse(KeyService.shouldRespawnKey(KeyService.Cause.CONSUMED,-100,-64));
        assertFalse(KeyService.shouldRespawnKey(KeyService.Cause.RESET,-100,-64));
    }
}
