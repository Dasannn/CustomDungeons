package dev.dasan.customdungeons.gui.menu;

import dev.dasan.customdungeons.session.SessionState;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GuiUsabilityTest {
    @Test void fractionalSecondsRoundToTicks() {
        assertEquals(2,WaveMenu.secondsToTicks(.1));
        assertEquals(25,WaveMenu.secondsToTicks(1.25));
        assertEquals(72000,WaveMenu.secondsToTicks(3600));
    }
    @Test void statsPreserveVanillaButRejectBelowMinimumOverrides() {
        assertEquals(0,StatsMenu.validateStat("health",0));
        assertEquals(.1,StatsMenu.validateStat("scale",.1));
        assertThrows(IllegalArgumentException.class,()->StatsMenu.validateStat("health",.5));
        assertThrows(IllegalArgumentException.class,()->StatsMenu.validateStat("scale",.05));
    }
    @Test void controlsRecheckPermissionValidityAndSavedState() {
        assertEquals("control-no-permission",reason("test",false,true,true,false,SessionState.FREE,false));
        assertEquals("control-disabled",reason("test",true,false,true,false,SessionState.FREE,false));
        assertEquals("control-invalid",reason("test",true,true,false,false,SessionState.FREE,false));
        assertEquals("control-save-first",reason("test",true,true,true,true,SessionState.FREE,false));
        assertEquals("control-busy",reason("test",true,true,true,false,SessionState.FREE,true));
        assertEquals("control-no-lobby",reason("start",true,true,true,false,SessionState.RUNNING,false));
        assertNull(reason("start",true,true,true,false,SessionState.LOBBY,false));
        assertNull(reason("test",true,true,true,false,SessionState.FREE,false));
        assertNull(reason("stop",true,true,true,false,SessionState.RUNNING,false));
    }
    private String reason(String key,boolean permission,boolean enabled,boolean valid,boolean dirty,SessionState state,boolean busy) {
        return DungeonMenu.controlReason(key,permission,enabled,valid,dirty,state,busy);
    }
}
