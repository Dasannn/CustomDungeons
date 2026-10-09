package dev.dasan.customdungeons.ability.zone;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ZoneRulesTest {
    @Test void coneHasFixedFrontAndEscapeBehind() {
        assertTrue(ZoneRules.cone(0,4,0,1,5,120));
        assertFalse(ZoneRules.cone(0,-4,0,1,5,120));
        assertFalse(ZoneRules.cone(4,0,0,1,5,120));
        assertFalse(ZoneRules.cone(0,6,0,1,5,120));
    }
    @Test void beamIsFiniteAndUsesFullWidth() {
        assertTrue(ZoneRules.beam(.49,8,0,1,16,1));
        assertFalse(ZoneRules.beam(.51,8,0,1,16,1));
        assertFalse(ZoneRules.beam(0,-1,0,1,16,1));
        assertFalse(ZoneRules.beam(0,17,0,1,16,1));
    }
    @Test void everyTwoByTwoPatternLeavesExactlyHalfSafeAndAlternates() {
        for(int pattern=0;pattern<3;pattern++)for(int wave=0;wave<3;wave++) {
            int hits=0;
            for(int x=-8;x<8;x++)for(int z=-8;z<8;z++) {
                boolean hit=ZoneRules.cracked(x,z,pattern,wave);
                if(hit)hits++;
                assertNotEquals(hit,ZoneRules.cracked(x,z,pattern,wave+1));
                assertEquals(hit,ZoneRules.cracked(x+.25,z+.25,pattern,wave));
            }
            assertEquals(128,hits);
        }
    }
    @Test void warningTilesCoverEveryDangerousCellAtOddAndFractionalRadii() {
        for(double radius:new double[]{3,4.5,7,8.25,16})for(int pattern=0;pattern<3;pattern++) {
            var tiles=ZoneRules.tiles(radius,pattern,0);
            for(double x=-radius;x<=radius;x+=.5)for(double z=-radius;z<=radius;z+=.5)
                if(x*x+z*z<=radius*radius&&ZoneRules.cracked(x,z,pattern,0)) {
                    int cx=(int)Math.floor(x/2),cz=(int)Math.floor(z/2);
                    assertTrue(tiles.contains(new ZoneRules.Tile(cx*2,cz*2)),"Unmarked dangerous tile at radius "+radius);
                }
        }
    }
    @Test void limitsFollowLivePlatformBudgetAndNeverExceedHardCaps() {
        assertEquals(0,ZoneRules.zoneLimit(0));
        assertEquals(16,ZoneRules.zoneLimit(50));
        assertEquals(64,ZoneRules.arrowLimit(50));
        assertEquals(8,ZoneRules.arrowLimit(1));
        assertEquals(0,ZoneRules.particleBudget(0));
        assertTrue(ZoneRules.particleBudget(10)<=128);
    }
    @Test void damageCannotKillFromFullHealthEvenAtMaximumParameters() {
        assertEquals(19,ZoneRules.damage(40,20,20));
        assertEquals(40,ZoneRules.damage(40,5,20));
        assertEquals(0,ZoneRules.damage(Double.NaN,20,20));
    }
    @Test void riftRespectsMinimumAndMaximumDistanceFromWholeGroup() {
        assertTrue(ZoneRules.riftDistance(8,8,16));
        assertTrue(ZoneRules.riftDistance(16,8,16));
        assertFalse(ZoneRules.riftDistance(7.9,8,16));
        assertFalse(ZoneRules.riftDistance(17,8,16));
    }
}
