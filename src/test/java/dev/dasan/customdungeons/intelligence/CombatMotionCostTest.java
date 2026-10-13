package dev.dasan.customdungeons.intelligence;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Offline microbenchmark only; spark acceptance remains an integrated-server check. */
class CombatMotionCostTest {
    @Test void reportsPureDecisionCostWithoutATimingThresholdOnSharedPi() {
        var motion=new CombatMotion();double checksum=0;
        for(int i=0;i<20_000;i++)checksum+=motion.decide(input(i)).x();
        long start=System.nanoTime();int count=100_000;
        for(int i=0;i<count;i++)checksum+=motion.decide(input(i)).x();
        long elapsed=System.nanoTime()-start;
        System.out.printf("T59b pure decisions: %d operations, %.0f ns/decision; checksum %.3f%n",count,(double)elapsed/count,checksum);
        assertTrue(Double.isFinite(checksum));
    }
    private static CombatMotion.Input input(long tick) {
        return new CombatMotion.Input(5,MovementDef.DEFAULT,"ZOMBIE",3,1,0,0,1,0,1,
                tick%3==0,tick%7==0,1,Math.PI,tick);
    }
}
