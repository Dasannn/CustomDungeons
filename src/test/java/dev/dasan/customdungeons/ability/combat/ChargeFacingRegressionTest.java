package dev.dasan.customdungeons.ability.combat;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ChargeFacingRegressionTest {
    static { dev.dasan.customdungeons.ability.impl.PaperApiTestBootstrap.initialize(); }
    @Test void chargeMustFaceItsWarnedTravelDirection() {
        try (var f = new CombatServiceTest.Fixture()) {
            // Vanilla target/facing and the ability-selected player can differ in group combat.
            f.mobPosition.setYaw(180);
            var origin = f.mobPosition.clone();
            f.cast("charge");
            f.step(21);
            var travel = f.mobPosition.toVector().subtract(origin.toVector()).setY(0);
            assertTrue(travel.length() > .5, "a real charge started");
            var facing = f.mobPosition.getDirection().setY(0).normalize();
            assertTrue(facing.dot(travel.normalize()) > .9,
                    "charge keeps the stale origin yaw and can travel backwards");
        }
    }
}
