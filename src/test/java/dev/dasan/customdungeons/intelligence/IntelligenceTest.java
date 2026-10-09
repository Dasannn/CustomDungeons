package dev.dasan.customdungeons.intelligence;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class IntelligenceTest {
    private final UUID player=UUID.randomUUID();
    @Test void defaultsAndPhaseInheritance() {
        var base=IntelligenceDef.level(3);
        assertEquals(10,base.value("window"));assertEquals(3,base.value("repetitions"));
        assertEquals(1,base.maximum());assertEquals(0,IntelligenceDef.NONE.level());
        assertEquals(4,base.phase(IntelligenceDef.level(4)).phase(IntelligenceDef.INHERIT).level());
    }
    @Test void boundedMemoryAndVariation() {
        var memory=new EncounterMemory();
        memory.record(player,"critical","ENTITY_ATTACK",3,0);
        memory.record(player,"critical","ENTITY_ATTACK",3,1);
        assertEquals(2,memory.repetitions(player,"critical",2,200));
        memory.record(player,"normal","ENTITY_ATTACK",3,3);
        assertEquals(0,memory.repetitions(player,"critical",4,200));
        memory.record(player,"critical","ENTITY_ATTACK",3,5);
        assertEquals(1,memory.repetitions(player,"critical",6,200));
        assertEquals(0,memory.repetitions(player,"critical",300,200));
        for(int i=0;i<100;i++)for(int j=0;j<100;j++)memory.record(new UUID(0,i),"normal","x",1,j);
        assertTrue(memory.playerCount()<=16);assertTrue(memory.eventCount()<=16*64);
        memory.clear();assertEquals(0,memory.playerCount());
    }
    @Test void everyRuleUsesCentralBreaches() {
        for(var rule:IntelligenceRules.defaults().all()) {
            var memory=new EncounterMemory();var def=IntelligenceDef.level(5);
            var brain=new IntelligenceBrain(def,memory,IntelligenceRules.defaults());
            memory.record(player,rule.pattern(),"ENTITY_ATTACK",2,0);
            assertTrue(brain.evaluate(0).isEmpty(),rule.id());
            memory.record(player,rule.pattern(),"ENTITY_ATTACK",2,1);
            var starts=brain.evaluate(1);assertEquals(1,starts.size(),rule.id());
            assertTrue(brain.evaluate(2).isEmpty());
            brain.damageDuringWarning(100,100);brain.evaluate(16);
            if(rule.strong())assertTrue(brain.active().isEmpty(),rule.id());
            brain.evaluate(700);assertTrue(brain.active().isEmpty(),rule.id());
        }
    }
    @Test void additiveCostResistanceCapAndPhaseReduction() {
        var m=new EncounterMemory();var brain=new IntelligenceBrain(IntelligenceDef.level(5).withWeakPoint(IntelligenceDef.WeakPoint.BACK),m,IntelligenceRules.defaults());
        m.record(player,"critical","ENTITY_ATTACK",1,0);m.record(player,"critical","ENTITY_ATTACK",1,1);brain.evaluate(1);brain.evaluate(16);
        assertEquals(1.4,brain.damageMultiplier(true,"ENTITY_ATTACK",false),1e-9);
        assertTrue(brain.damageMultiplier(false,"ENTITY_ATTACK",true)>=.6);
        brain.changeLevel(IntelligenceDef.level(1),20);assertTrue(brain.active().isEmpty());assertSame(m,brain.memory());
        brain.clear();assertEquals(0,m.playerCount());
    }
}
