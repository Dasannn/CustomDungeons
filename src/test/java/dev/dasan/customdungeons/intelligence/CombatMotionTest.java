package dev.dasan.customdungeons.intelligence;

import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CombatMotionTest {
    @Test void oppositeGroupSlotStartsWithALateralStepInsteadOfCrossingThePlayer() {
        var d=new CombatMotion().decide(input(4,"ZOMBIE",3,1,false,false,Math.PI,0));
        assertEquals(CombatMotion.Action.SPREAD,d.action());
        assertTrue(d.x()>0,"The first endpoint must stay on this side of the player");
        assertTrue(d.z()>.5,"Walk around the opponent before closing the assigned slot");
        assertEquals(3,Math.hypot(d.x(),d.z()),1e-6);
    }
    @Test void distantGroupMembersKeepTheirDistinctApproachInsteadOfLosingItToPressure() {
        var d=new CombatMotion().decide(input(4,"ZOMBIE",8,1,false,false,Math.PI*2/3,0));
        assertEquals(CombatMotion.Action.SPREAD,d.action());assertTrue(d.z()>.5);
        assertTrue(Math.hypot(d.x(),d.z())<8);
    }
    @Test void meleeCirclingStaysCloseAndMovesAcrossTheNativeCellBoundary() {
        var d=new CombatMotion().decide(input(3,"ZOMBIE",1,1,false,false,null,0));
        assertEquals(CombatMotion.Action.CIRCLE,d.action());
        assertTrue(Math.hypot(d.x(),d.z())<=1.25,"Ordinary melee mobs should stay close enough to press the opponent");
        assertTrue(d.z()>.5,"Circling must leave the starting grid cell rather than repeatedly planning to the same node");
    }
    CombatMotion.Input input(int level, String type, double distance, double health,
                             boolean shield, boolean aimed, Double angle, long tick) {
        return new CombatMotion.Input(level, MovementDef.DEFAULT, type, distance, 0, 0, 0,
                1, 0, health, shield, aimed, 1, angle, tick);
    }
    @Test void legacyLevelsLeaveNavigationAlone() {
        for(int level=0;level<2;level++)
            assertEquals(CombatMotion.Action.VANILLA,new CombatMotion().decide(input(level,"SKELETON",2,.1,true,true,0d,0)).action());
    }
    @Test void rangedKeepsEightToFourteenBlocksAndMeleePresses() {
        var motion=new CombatMotion();
        var back=motion.decide(input(2,"SKELETON",3,1,false,false,null,0));
        assertEquals(CombatMotion.Action.DISTANCE,back.action());assertTrue(back.x()>3);assertTrue(back.faceTarget());
        assertEquals(CombatMotion.Action.HOLD,motion.decide(input(2,"STRAY",11,1,false,false,null,10)).action());
        var close=motion.decide(input(2,"SKELETON",18,1,false,false,null,20));assertTrue(close.x()<18);
        var melee=motion.decide(input(2,"ZOMBIE",11,1,false,false,null,30));assertEquals(CombatMotion.Action.PRESS,melee.action());assertTrue(melee.x()<11);
    }
    @Test void explicitStylesOverrideEntityAndAutoRecognisesRangedTypes() {
        for(String type:Set.of("SKELETON","STRAY","BOGGED","PARCHED","PILLAGER","BLAZE","BREEZE","EVOKER","ILLUSIONER","GHAST","SHULKER","WITCH","GUARDIAN","ELDER_GUARDIAN","WITHER"))
            assertEquals(MovementDef.Style.RANGED,MovementDef.Style.AUTO.resolve(type));
        assertEquals(MovementDef.Style.MELEE,MovementDef.Style.AUTO.resolve("WITHER_SKELETON"));
        assertEquals(MovementDef.Style.MELEE,MovementDef.Style.MELEE.resolve("SKELETON"));
        assertEquals(MovementDef.Style.RANGED,MovementDef.Style.RANGED.resolve("ZOMBIE"));
    }
    @Test void shieldFlanksOutsideItsFrontAndCircleIsLevelThree() {
        var motion=new CombatMotion();
        assertEquals(CombatMotion.Action.PRESS,motion.decide(input(2,"ZOMBIE",2.5,1,true,false,null,0)).action());
        var flank=motion.decide(input(3,"ZOMBIE",2.5,1,true,false,null,10));
        assertEquals(CombatMotion.Action.FLANK,flank.action());assertTrue(flank.x()<.1);assertTrue(Math.abs(flank.z())>.9);
        assertEquals(CombatMotion.Action.CIRCLE,motion.decide(input(3,"ZOMBIE",2.5,1,false,false,null,20)).action());
    }
    @Test void retreatLastsThreeSecondsAtQuarterHealthAndOnlyEveryTwentySeconds() {
        var motion=new CombatMotion();
        assertEquals(CombatMotion.Action.RETREAT,motion.decide(input(3,"ZOMBIE",3,.25,false,false,null,0)).action());
        assertEquals(CombatMotion.Action.RETREAT,motion.decide(input(3,"ZOMBIE",4,1,false,false,null,59)).action());
        assertNotEquals(CombatMotion.Action.RETREAT,motion.decide(input(3,"ZOMBIE",4,.25,false,false,null,60)).action());
        assertNotEquals(CombatMotion.Action.RETREAT,motion.decide(input(3,"ZOMBIE",4,.25,false,false,null,399)).action());
        assertEquals(CombatMotion.Action.RETREAT,motion.decide(input(3,"ZOMBIE",4,.25,false,false,null,400)).action());
        assertNotEquals(CombatMotion.Action.RETREAT,new CombatMotion().decide(input(3,"ZOMBIE",3,.251,false,false,null,0)).action());
    }
    @Test void dodgeRequiresLevelFourAndHasFourSecondCooldown() {
        var motion=new CombatMotion();
        assertNotEquals(CombatMotion.Action.DODGE,motion.decide(input(3,"ZOMBIE",10,1,false,true,null,0)).action());
        var dodge=motion.decide(input(4,"ZOMBIE",10,1,false,true,null,10));
        assertEquals(CombatMotion.Action.DODGE,dodge.action());assertEquals(10,dodge.x(),1e-6);assertTrue(Math.abs(dodge.z())>=2);
        assertNotEquals(CombatMotion.Action.DODGE,motion.decide(input(4,"ZOMBIE",10,1,false,true,null,89)).action());
        assertEquals(CombatMotion.Action.DODGE,motion.decide(input(4,"ZOMBIE",10,1,false,true,null,90)).action());
    }
    @Test void dodgeIsLateralToTheIncomingShotEvenWhenItIsNotFromTheCurrentTarget() {
        var d=new CombatMotion().decide(input(4,"ZOMBIE",10,1,false,true,null,0).withDodgeBearing(Math.PI/2));
        assertEquals(CombatMotion.Action.DODGE,d.action());assertEquals(7,d.x(),1e-6);assertEquals(0,d.z(),1e-6);
    }
    @Test void spreadConvergesToDistinctSlotsByLocalStepsOnlyAtLevelFour() {
        for(int member=0;member<3;member++) {
            var motion=new CombatMotion();double angle=member*Math.PI*2/3,x=3,z=0;
            for(int step=0;step<16;step++) {
                var i=new CombatMotion.Input(4,MovementDef.DEFAULT,"ZOMBIE",x,z,0,0,1,0,1,false,false,1,angle,step*10);
                var d=motion.decide(i);assertEquals(CombatMotion.Action.SPREAD,d.action());x=d.x();z=d.z();
            }
            assertEquals(Math.atan2(Math.sin(angle),Math.cos(angle)),Math.atan2(z,x),1e-6);
            assertEquals(1,Math.hypot(x,z),1e-6,"Close to melee contact after reaching the assigned side");
        }
        assertNotEquals(CombatMotion.Action.SPREAD,new CombatMotion().decide(input(3,"ZOMBIE",3,1,false,false,0d,20)).action());
    }
    @Test void disabledOptionsAndDroppingLevelCancelRetreat() {
        var def=new MovementDef(MovementDef.Style.AUTO,Set.of(MovementDef.Option.values()));
        var i=input(5,"ZOMBIE",3,.1,true,true,0d,0);
        assertEquals(CombatMotion.Action.PRESS,new CombatMotion().decide(i.withMovement(def)).action());
        var motion=new CombatMotion();motion.decide(i);
        assertEquals(CombatMotion.Action.VANILLA,motion.decide(input(0,"ZOMBIE",3,1,false,false,null,10)).action());
        assertNotEquals(CombatMotion.Action.RETREAT,motion.decide(input(3,"ZOMBIE",3,1,false,false,null,20)).action());
    }
    @Test void overlappingPositionsProduceFiniteDestinations() {
        var d=new CombatMotion().decide(input(5,"SKELETON",0,.2,false,true,null,0));
        assertTrue(Double.isFinite(d.x()));assertTrue(Double.isFinite(d.z()));
    }
    @Test void shieldFlanksPreserveTheTrioDistinctAngles() {
        var destinations=new java.util.HashSet<String>();
        for(int i=0;i<3;i++) {
            var d=new CombatMotion().decide(input(4,"ZOMBIE",3,1,true,false,i*Math.PI*2/3,0));
            assertEquals(CombatMotion.Action.FLANK,d.action());assertTrue(d.x()<=0);
            destinations.add(d.x()+":"+d.z());
        }
        assertEquals(3,destinations.size());
    }
    @Test void bodySizeChangesMeleeSpacingButNeverTheRangedBand() {
        var i=input(3,"ZOMBIE",3,1,false,false,null,0).withMeleeRadius(4);
        var d=new CombatMotion().decide(i);assertEquals(4,Math.hypot(d.x(),d.z()),1e-6);
        assertEquals(4,i.withMovement(MovementDef.DEFAULT.withStyle(MovementDef.Style.MELEE)).meleeRadius());
        var ranged=new CombatMotion().decide(input(2,"SKELETON",3,1,false,false,null,0).withMeleeRadius(4));
        assertEquals(11,Math.hypot(ranged.x(),ranged.z()),1e-6);
    }
    @Test void scaledMeleeMobsCircleAndFlankAtTheirOwnContactRadius() {
        var contact=input(3,"RAVAGER",6,1,false,false,null,0).withMeleeRadius(6);
        var circle=new CombatMotion().decide(contact);
        assertEquals(CombatMotion.Action.CIRCLE,circle.action());
        assertEquals(6,Math.hypot(circle.x(),circle.z()),1e-6);
        var shield=input(3,"RAVAGER",6,1,true,false,null,0).withMeleeRadius(6);
        assertEquals(CombatMotion.Action.FLANK,new CombatMotion().decide(shield).action());
        assertEquals(CombatMotion.Action.PRESS,new CombatMotion().decide(input(3,"RAVAGER",12,1,false,false,null,0).withMeleeRadius(6)).action());
    }
}
