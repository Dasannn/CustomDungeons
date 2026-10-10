package dev.dasan.customdungeons.ability;
import dev.dasan.customdungeons.model.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PhaseActivationScheduleTest {
    @Test void abilitiesIntroducedByALatePhaseKeepTheirIndividualInitialOffsets() {
        var definitions=List.of(AbilityEngineTest.instance(Trigger.EVERY_X_SECONDS,5,100,1,0),
            new AbilityInstance("second",Trigger.EVERY_X_SECONDS,5,TargetMode.NEAREST,10,100,1,0,Map.of()));
        var fresh=new AbilityEngineTest.Fixture(definitions,List.of());
        var phased=new AbilityEngineTest.Fixture(List.of(),List.of());
        var freshEngine=fresh.engine();var phaseEngine=phased.engine();
        phaseEngine.tick(List.of(phased.mob),0);
        phased.clock.advance(1000); // The phase is introduced at host tick 1000.
        phased.mob.abilities().addAll(definitions);
        for(int t=1000;t<1100;t++) {
            fresh.clock.advance(t);freshEngine.tick(List.of(fresh.mob),t);
            phased.clock.advance(t);phaseEngine.tick(List.of(phased.mob),t);
        }
        assertEquals(2,fresh.calls.size());assertEquals(2,phased.calls.size());
        int expected=Integer.parseInt(fresh.calls.get(1).split("@")[1])-Integer.parseInt(fresh.calls.get(0).split("@")[1]);
        int actual=Integer.parseInt(phased.calls.get(1).split("@")[1])-Integer.parseInt(phased.calls.get(0).split("@")[1]);
        assertEquals(fresh.calls,phased.calls,"A phase must use its activation tick as the schedule origin");
        assertEquals(expected,actual,"A late phase collapsed individual offsets; fresh="+fresh.calls+" phase="+phased.calls);
    }
}
