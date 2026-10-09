package dev.dasan.customdungeons.ability.control;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ControlRulesTest {
    @Test void oneControlAndExactlySixtyTicksOfImmunity() {
        var ledger=new ControlLedger<String>();UUID p=UUID.randomUUID();
        assertTrue(ledger.acquire(List.of(p),"grab",10));
        assertFalse(ledger.acquire(List.of(p),"roots",11));
        ledger.release("grab",20);
        assertFalse(ledger.acquire(List.of(p),"roots",79));
        assertTrue(ledger.acquire(List.of(p),"roots",80));
    }
    @Test void pairAcquisitionIsAtomicAndReleaseProtectsBoth() {
        var ledger=new ControlLedger<String>();UUID a=UUID.randomUUID(),b=UUID.randomUUID();
        ledger.acquire(List.of(b),"roots",0);
        assertFalse(ledger.acquire(List.of(a,b),"chain",1));assertNull(ledger.get(a));
        ledger.release("roots",2);assertTrue(ledger.acquire(List.of(a,b),"chain",62));
        ledger.release("chain",63);assertFalse(ledger.acquire(List.of(a),"grab",122));
        assertTrue(ledger.acquire(List.of(a),"grab",123));
    }
    @Test void expiredImmunityIsRetiredEvenWhenTheOldHostsDiscardTheirQueues() {
        var ledger=new ControlLedger<Object>();
        for(int i=0;i<100;i++) {Object value=new Object();assertTrue(ledger.acquire(List.of(new UUID(0,i)),value,i*61L));ledger.release(value,i*61L);}
        assertEquals(1,ledger.immunitySize());
    }
    @Test void jumpCountsOnlyRisingEdgesAndDamageIsBounded() {
        var escape=new ControlRules.Escape(3,20);
        assertFalse(escape.jump(true));assertFalse(escape.jump(true));
        escape.jump(false);assertFalse(escape.jump(true));escape.jump(false);assertTrue(escape.jump(true));
        var damage=new ControlRules.Escape(8,20);
        assertFalse(damage.damage(19));assertFalse(damage.damage(-10));assertTrue(damage.damage(1));
    }
    @Test void bombNeverExceedsHealthMinusOneForFullHealthOrSameTickGuard() {
        assertEquals(19,ControlRules.bombDamage(40,20,true));
        assertEquals(6,ControlRules.bombDamage(40,7,true));
        assertEquals(40,ControlRules.bombDamage(40,7,false));
        assertEquals(0,ControlRules.bombDamage(40,1,true));
    }
    @Test void tacticalSummonRespectsBothLimitsAndNeedsDisadvantageAtLevelTwo() {
        assertTrue(ControlRules.summon(1,1,1,1));assertFalse(ControlRules.summon(2,.8,1,1));
        assertTrue(ControlRules.summon(2,.4,1,1));assertTrue(ControlRules.summon(2,1,2,1));
        assertEquals(2,ControlRules.summonCount(10,2,4,48,50));
        assertEquals(0,ControlRules.summonCount(10,4,4,1,50));
    }
}
