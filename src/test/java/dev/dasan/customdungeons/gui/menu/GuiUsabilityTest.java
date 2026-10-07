package dev.dasan.customdungeons.gui.menu;

import dev.dasan.customdungeons.session.SessionState;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GuiUsabilityTest {
    @Test void healthEditorHasNoAuthoringLimit() {
        assertEquals(1e30,StatsMenu.maximum("health"));
        assertEquals(2048,StatsMenu.clampStat("health",2048));
        assertEquals(1024,StatsMenu.validateStat("health",1024));
        assertEquals(1024.1,StatsMenu.validateStat("health",1024.1));
    }
    @Test void clampingRepairsLegacyStatsAndPreservesVanillaDefaults() {
        assertEquals(7.0625,StatsMenu.clampStat("scale",7.0625));
        assertEquals(16,StatsMenu.clampStat("scale",32));
        assertEquals(4.7265625,StatsMenu.clampStat("speed",4.7265625));
        assertEquals(.05,StatsMenu.clampStat("scale",.05));
        assertEquals(.5,StatsMenu.clampStat("health",.5));
        assertEquals(0,StatsMenu.clampStat("damage",-1));
        for (String key : java.util.List.of("health","damage","speed","resistance","scale")) {
            assertEquals(0,StatsMenu.clampStat(key,0));
            assertDoesNotThrow(()->StatsMenu.validateStat(key,StatsMenu.clampStat(key,Double.NaN)));
            assertDoesNotThrow(()->StatsMenu.validateStat(key,StatsMenu.clampStat(key,Double.POSITIVE_INFINITY)));
        }
    }
    @Test void allStatsRejectOutOfRangeAndNonFiniteValues() {
        for (String key : java.util.List.of("health","damage","speed","resistance","scale")) {
            assertThrows(IllegalArgumentException.class,()->StatsMenu.validateStat(key,-1));
            assertThrows(IllegalArgumentException.class,()->StatsMenu.validateStat(key,Double.NaN));
            assertThrows(IllegalArgumentException.class,()->StatsMenu.validateStat(key,Double.POSITIVE_INFINITY));
        }
        assertThrows(IllegalArgumentException.class,()->StatsMenu.validateStat("scale",16.01));
        assertThrows(IllegalArgumentException.class,()->StatsMenu.validateStat("speed",1024.01));
    }
    @Test void scaleSixteenIsAnAcceptedOverride() {
        assertEquals(16,StatsMenu.maximum("scale"));
        assertEquals(16,StatsMenu.validateStat("scale",16));
    }
    @Test void fractionalSecondsRoundToTicks() {
        assertEquals(2,WaveMenu.secondsToTicks(.1));
        assertEquals(25,WaveMenu.secondsToTicks(1.25));
        assertEquals(72000,WaveMenu.secondsToTicks(3600));
    }
    @Test void statsPreserveVanillaButRejectBelowMinimumOverrides() {
        assertEquals(0,StatsMenu.validateStat("health",0));
        assertEquals(.1,StatsMenu.validateStat("scale",.1));
        assertEquals(.5,StatsMenu.validateStat("health",.5));
        assertEquals(.05,StatsMenu.validateStat("scale",.05));
        assertEquals(10,StatsMenu.validateStat("scale",10));
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
