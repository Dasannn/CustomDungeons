package dev.dasan.customdungeons.session;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ExitPolicyTest {
    @Test void immediateDelayedAndNoneDeadlines() {
        assertTrue(ExitPolicy.due("IMMEDIATE",60,0,false));
        assertFalse(ExitPolicy.due("DELAYED",60,1199,false));
        assertTrue(ExitPolicy.due("DELAYED",60,1200,false));
        assertFalse(ExitPolicy.due("NONE",60,5999,false));
        assertTrue(ExitPolicy.due("NONE",60,6000,false));
    }
    @Test void timeoutOverridesEveryMode() {
        for(String mode:new String[]{"IMMEDIATE","DELAYED","NONE"}) assertTrue(ExitPolicy.due(mode,60,0,true));
    }
}
