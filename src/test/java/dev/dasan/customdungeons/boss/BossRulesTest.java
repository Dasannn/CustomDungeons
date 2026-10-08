package dev.dasan.customdungeons.boss;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class BossRulesTest {
    @Test void terrainRejectsLiquidsLeavesAndBlockedHeadroom() {
        assertTrue(BossSpawner.safeColumn(true,false,false,4,3, true));
        assertFalse(BossSpawner.safeColumn(true,true,false,4,3,true)); // water, waterlogged, aquatic plants or lava
        assertFalse(BossSpawner.safeColumn(true,false,true,4,3,true));
        assertFalse(BossSpawner.safeColumn(false,false,false,4,3,true));
        assertFalse(BossSpawner.safeColumn(true,false,false,2,3,true));
        assertFalse(BossSpawner.safeColumn(true,false,false,4,3,false));
    }
    @Test void reservationsCountTowardMaximumAndReleaseExactlyOnce() {
        var slots=new BossSlots();
        assertTrue(slots.reserve("boss",1));
        assertFalse(slots.reserve("boss",1));
        slots.release("boss");
        assertTrue(slots.reserve("boss",1));
    }
    @Test void minimumCountsRealVirtualDamageCapsOverkillAndIgnoresAdministrativeDeaths() {
        var damage=new DamageLedger(5000,5);
        UUID a=UUID.randomUUID(), b=UUID.randomUUID();
        damage.add(a,249,5000);damage.add(b,250,4751);
        assertEquals(java.util.Set.of(b),damage.eligible(true));
        assertTrue(damage.eligible(false).isEmpty());
        damage.add(a,1,4501);assertEquals(java.util.Set.of(a,b),damage.eligible(true));
        damage.add(a,Double.MAX_VALUE,1);assertEquals(251,damage.damage(a));
        damage.add(a,Double.NaN,10);assertEquals(251,damage.damage(a));
    }
    @Test void leashUsesWorldAndExclusiveUpperZoneBounds() {
        assertTrue(WorldEncounter.inZone("world",-2,3,"world",-2,4,-5,4));
        assertFalse(WorldEncounter.inZone("other",0,0,"world",-2,4,-5,4));
        assertFalse(WorldEncounter.inZone("world",4,0,"world",-2,4,-5,4));
        assertFalse(WorldEncounter.inZone("world",0,-6,"world",-2,4,-5,4));
    }
}
