package dev.dasan.customdungeons.gui.menu;

import dev.dasan.customdungeons.ability.zone.SweepAbility;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ZoneDefaultsTest {
    @Test void creatingSweepInGuiKeepsTheExactPointEightSecondWarning() {
        assertEquals(16,MobMenuBase.defaults(new SweepAbility()).telegraphTicks());
    }
}
