package dev.dasan.customdungeons.ability.combat;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CombatRulesTest {
    @Test void reflectedDamageHasOneTotalBudgetAndRejectsInvalidReceipts() {
        var link=new CombatRules.DamageBudget(8);
        assertEquals(3,link.take(10,30));
        assertEquals(5,link.take(100,30));
        assertEquals(0,link.take(100,30));
        assertEquals(0,new CombatRules.DamageBudget(8).take(Double.NaN,30));
    }
    @Test void interruptionUsesMaximumHealthAndOnlyPositiveFiniteDamage() {
        var charge=new CombatRules.Interruption(5000,10);
        assertFalse(charge.add(Double.NaN));assertFalse(charge.add(-10));
        assertFalse(charge.add(499));assertTrue(charge.add(1));
        assertEquals(500,charge.required());assertEquals(500,charge.progress());
    }
    @Test void fullHealthIsProtectedUnlessExplicitlyLethal() {
        assertEquals(19,CombatRules.damage(80,20,20,false));
        assertEquals(80,CombatRules.damage(80,20,20,true));
        assertEquals(30,CombatRules.damage(30,10,20,false));
    }
    @Test void fixedChargeCorridorLeavesASideEscape() {
        assertTrue(CombatRules.corridor(0,4,0,1,12,1));
        assertFalse(CombatRules.corridor(2,4,0,1,12,1));
        assertFalse(CombatRules.corridor(0,-1,0,1,12,1));
        assertFalse(CombatRules.corridor(0,13,0,1,12,1));
    }
    @Test void stolenInfiniteEffectsStillExpireAtTheConfiguredCap() {
        assertEquals(600,CombatRules.buffTicks(-1,600));assertEquals(80,CombatRules.buffTicks(80,600));
    }
    @Test void totemMeleeUsesTheHitboxAndPlayerReach() {
        assertTrue(CombatRules.totemInReach(3.4,1.6,0,3));
        assertTrue(CombatRules.totemInReach(0,2,0,3));
        assertFalse(CombatRules.totemInReach(30,1.6,0,3));
        assertFalse(CombatRules.totemInReach(0,6,0,3));
        assertFalse(CombatRules.totemInReach(0,0,0,Double.NaN));
    }
}
