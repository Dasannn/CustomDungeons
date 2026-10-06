package dev.dasan.customdungeons.gui.wizard;
import dev.dasan.customdungeons.model.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class WizardRulesTest {
    static DungeonDef empty(RewardDef reward) {return new DungeonDef("d","d",false,null,null,1,0,30,3,false,0,0,false,
            new ScalingDef(.25,.15),Map.of(),reward,List.of());}
    @Test void rulesAndRewardsCanKeepDefaultsAndLocalSpawnersDoNotRequireAPreset() {
        var d=empty(new RewardDef(List.of(),0,0,List.of()));
        for(int step:List.of(2,4,5)) assertTrue(WizardRules.errors(step,d,Map.of(),Map.of()).isEmpty());
        for(int step:List.of(0,1,3,6)) assertFalse(WizardRules.errors(step,d,Map.of(),Map.of()).isEmpty());
    }
    @Test void invalidRewardBlocksRewardAndReviewSteps() {
        for(var reward:Arrays.asList(null,new RewardDef(List.of(),Double.NaN,0,List.of()),new RewardDef(List.of(),-1,-1,List.of()))) {
            assertFalse(WizardRules.errors(5,empty(reward),Map.of(),Map.of()).isEmpty());
            assertTrue(WizardRules.errors(6,empty(reward),Map.of(),Map.of()).stream().anyMatch(e->e.path().equals("reward")));
        }
    }
    @Test void validationSnapshotContainsEveryStepAndCannotBeChangedByCallers() {
        var d=empty(new RewardDef(List.of(),Double.NaN,0,List.of()));
        var snapshot=WizardRules.evaluate(d,Map.of(),Map.of(),false);
        for(int step=0;step<7;step++) assertEquals(WizardRules.errors(step,d,Map.of(),Map.of()),snapshot.errors(step));
        assertTrue(snapshot.errors(0).stream().anyMatch(e->e.path().equals("area")));
        assertTrue(snapshot.errors(1).stream().anyMatch(e->e.path().equals("lobby")));
        assertTrue(snapshot.errors(3).stream().anyMatch(e->e.path().equals("rooms")));
        assertTrue(snapshot.errors(5).stream().anyMatch(e->e.path().equals("reward")));
        assertTrue(snapshot.errors(6).stream().anyMatch(e->e.path().equals("reward")));
        assertSame(snapshot.errors(6),snapshot.errors(6));
        assertTrue(snapshot.warnings().isEmpty());
        assertThrows(UnsupportedOperationException.class,()->snapshot.errors(6).clear());
        assertThrows(UnsupportedOperationException.class,()->snapshot.steps().clear());
        assertThrows(IndexOutOfBoundsException.class,()->snapshot.errors(7));
    }
}
