package dev.dasan.customdungeons.intelligence;

import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import static org.junit.jupiter.api.Assertions.*;

class IntelligenceBreachesTest {
    static List<IntelligenceRules.Rule> rules(){return IntelligenceRules.defaults().all();}
    @ParameterizedTest @MethodSource("rules") void detectionWindowVariationExpirationAndCooldown(IntelligenceRules.Rule rule) {
        for(int level=rule.minimumLevel();level<=5;level++) {
            UUID id=UUID.randomUUID();var m=new EncounterMemory();var def=IntelligenceDef.level(level);var b=new IntelligenceBrain(def,m,IntelligenceRules.defaults());int n=def.value("repetitions");
            for(int i=0;i<n-1;i++)m.record(id,rule.pattern(),"x",1,i);
            assertTrue(b.evaluate(n).isEmpty());
            m.record(id,"different","y",1,n+1);m.record(id,rule.pattern(),"x",1,n+2);assertTrue(b.evaluate(n+2).isEmpty());
            for(int i=1;i<n;i++)m.record(id,rule.pattern(),"x",1,n+2+i);
            long start=n*2+2;assertEquals(1,b.evaluate(start).size());
            var a=b.active().getFirst();assertTrue(a.begins()>a.announced());
            assertEquals(start+def.value("duration")*20+(rule.strong()?15:1),a.expires());
            b.evaluate(a.expires());assertTrue(b.active().isEmpty());
            assertEquals(1,b.drainRemoved().size());
            long future=Math.max(a.expires(),start+def.value("cooldown")*20L)+1;for(int i=0;i<n;i++)m.record(id,rule.pattern(),"x",1,future+i);
            assertEquals(1,b.evaluate(future+n).size());
            b.clear();assertTrue(b.active().isEmpty());assertEquals(0,m.playerCount());
        }
    }
    @Test void registeredRulesCannotBypassSingleUseMaximumCooldownOrCost() {
        var rules=new IntelligenceRules();rules.register(new IntelligenceRules.Rule("server","server",3,true,IntelligenceRules.Response.PUSH));
        assertThrows(IllegalArgumentException.class,()->rules.register(new IntelligenceRules.Rule("unsafe","x",1,false,IntelligenceRules.Response.PUSH)));
        UUID id=UUID.randomUUID();var m=new EncounterMemory();var b=new IntelligenceBrain(IntelligenceDef.level(5).withAdvanced("maximum",1).withAdvanced("cooldown",60),m,rules);
        m.record(id,"server","x",1,0);assertTrue(b.evaluate(0).isEmpty());m.record(id,"server","x",1,1);assertEquals(1,b.evaluate(1).size());
        assertEquals(1.1,b.damageMultiplier(false,"x",false),1e-9);b.damageDuringWarning(5,100);assertTrue(b.active().isEmpty());
        m.record(id,"server","x",1,50);m.record(id,"server","x",1,51);assertTrue(b.evaluate(52).isEmpty());
        b.forget(id);assertEquals(0,m.playerCount());
    }
    @Test void weakBonusAddsCostAndResistanceIsNeverMoreThanFortyPercent() {
        var m=new EncounterMemory();UUID id=UUID.randomUUID();var b=new IntelligenceBrain(IntelligenceDef.level(5).withAdvanced("cooldown",1),m,IntelligenceRules.defaults());
        for(String pattern:List.of("critical","damage")) {long t=pattern.equals("critical")?0:25;m.record(id,pattern,"x",1,t);m.record(id,pattern,"x",1,t+1);b.evaluate(t+1);}
        b.clock(40);assertEquals(.66,b.damageMultiplier(false,"x",true),1e-9);
        b.changeLevel(b.definition().withWeakPoint(IntelligenceDef.WeakPoint.BACK).withBonus(100),41);
        assertEquals(.6*2.15,b.damageMultiplier(true,"x",true),1e-9);
        b.changeLevel(IntelligenceDef.level(3),42);assertTrue(b.active().isEmpty());assertTrue(m.playerCount()>0);
    }
    @Test void maximumIsCappedAndSeenTacticsStillNeedRepetition() {
        assertEquals(1,IntelligenceDef.level(3).withAdvanced("maximum",3).maximum());
        var m=new EncounterMemory();UUID id=UUID.randomUUID();m.record(id,"critical","x",1,0);m.remember(id,"critical");
        var b=new IntelligenceBrain(IntelligenceDef.level(5),m,IntelligenceRules.defaults());assertTrue(b.evaluate(0).isEmpty());
        m.record(id,"critical","x",1,150);assertTrue(b.evaluate(150).isEmpty());
        m.record(id,"critical","x",1,151);assertEquals(1,b.evaluate(151).size());
        b.clear();assertFalse(m.seen(id,"critical"));
    }
    @Test void changingDamageTypeOrConsumableResetsDetectionButHealingDoesNot() {
        var m=new EncounterMemory();UUID id=UUID.randomUUID();m.record(id,"damage","cut",1,0);m.record(id,"damage","fire",1,1);
        assertEquals(1,m.repetitions(id,"damage",2,200));
        m.record(id,"consumables","apple",0,3);m.record(id,"heal","",0,4);m.record(id,"consumables","apple",0,5);
        assertEquals(2,m.repetitions(id,"consumables",6,200));m.record(id,"consumables","potion",0,7);assertEquals(1,m.repetitions(id,"consumables",8,200));
    }
    @ParameterizedTest @MethodSource("rules") void noResponseCanDealLethalDamageFromFullHealth(IntelligenceRules.Rule rule) {
        for(double health:List.of(1d,20d,100d))for(double hit:List.of(0d,10d,1000d,1e30)) {
            double response=rule.response()==IntelligenceRules.Response.ENRAGE?hit*1.15:hit;
            assertTrue(FairCombat.nonLethalBase(response,response,health)<health);
        }
        assertEquals(0,FairCombat.nonLethalBase(Double.NaN,10,20));
    }
    @Test void simultaneousLimitAndOldestFirstRemoval() {
        var d=IntelligenceDef.level(5).withAdvanced("cooldown",1).withAdvanced("duration",30);var m=new EncounterMemory();var b=new IntelligenceBrain(d,m,IntelligenceRules.defaults());
        int i=0;for(String pattern:List.of("consumables","critical","damage")){UUID p=new UUID(0,++i);long t=i*25;m.record(p,pattern,"x",1,t);m.record(p,pattern,"x",1,t+1);assertEquals(1,b.evaluate(t+1).size());}
        assertEquals(3,b.active().size());var old=b.active();
        b.changeLevel(d.withAdvanced("maximum",1),100);assertEquals(List.of(old.get(0),old.get(1)),b.drainRemoved());assertEquals(List.of(old.get(2)),b.active());
    }
    @Test void variationInTheSameTickStillResetsDetection() {
        var m=new EncounterMemory();UUID p=UUID.randomUUID();m.record(p,"critical","x",1,0);m.record(p,"normal","x",1,0);m.record(p,"critical","x",1,0);assertEquals(1,m.repetitions(p,"critical",0,200));
    }
    @Test void leavingRangeWithdrawsEffectsButRetainsEncounterRecallAndCannotAdaptToAbsentPlayer() {
        var m=new EncounterMemory();UUID p=UUID.randomUUID();m.record(p,"critical","x",1,0);m.record(p,"critical","x",1,1);
        var b=new IntelligenceBrain(IntelligenceDef.level(5),m,IntelligenceRules.defaults());assertEquals(1,b.evaluate(1).size());
        b.withdraw(p);assertTrue(m.seen(p,"critical"));assertEquals(1,m.playerCount());
        m.record(p,"critical","x",1,210);m.record(p,"critical","x",1,211);assertTrue(b.evaluate(212,Set.of()).isEmpty());assertEquals(1,b.evaluate(213,Set.of(p)).size());
    }
    @Test void phaseDowngradeRemovesIneligibleAndExcessAdaptationsTogetherInAgeOrder() {
        var definition=IntelligenceDef.level(5).withAdvanced("cooldown",1).withAdvanced("duration",30);
        var memory=new EncounterMemory();var brain=new IntelligenceBrain(definition,memory,IntelligenceRules.defaults());
        int index=0;for(String pattern:List.of("consumables","critical","flight")) {
            UUID player=new UUID(0,++index);long tick=index*25;memory.record(player,pattern,"x",1,tick);memory.record(player,pattern,"x",1,tick+1);brain.evaluate(tick+1);
        }
        var before=brain.active();brain.changeLevel(IntelligenceDef.level(3),100);
        assertEquals(List.of(before.get(0),before.get(1)),brain.drainRemoved());
        assertEquals(List.of(before.get(2)),brain.active());assertEquals(3,memory.playerCount());
    }
}
