package dev.dasan.customdungeons.intelligence;

import static dev.dasan.customdungeons.intelligence.MovementDef.Option.*;

/** Pure decisions and bounded per-mob clocks. Navigation and terrain belong to Paper. */
public final class CombatMotion {
    public enum Action { VANILLA, HOLD, PRESS, DISTANCE, CIRCLE, FLANK, RETREAT, DODGE, SPREAD }
    public record Input(int level, MovementDef movement, String entityType,
                        double x, double z, double targetX, double targetZ,
                        double facingX, double facingZ, double health,
                        boolean shield, boolean aimed, int side, Double groupAngle, long tick, double meleeRadius, Double dodgeBearing) {
        public Input(int level,MovementDef movement,String entityType,double x,double z,double targetX,double targetZ,
                     double facingX,double facingZ,double health,boolean shield,boolean aimed,int side,Double groupAngle,long tick) {
            this(level,movement,entityType,x,z,targetX,targetZ,facingX,facingZ,health,shield,aimed,side,groupAngle,tick,1,null);
        }
        public Input(int level,MovementDef movement,String entityType,double x,double z,double targetX,double targetZ,
                     double facingX,double facingZ,double health,boolean shield,boolean aimed,int side,Double groupAngle,long tick,double meleeRadius) {
            this(level,movement,entityType,x,z,targetX,targetZ,facingX,facingZ,health,shield,aimed,side,groupAngle,tick,meleeRadius,null);
        }
        public Input withMovement(MovementDef value) {
            return new Input(level,value,entityType,x,z,targetX,targetZ,facingX,facingZ,health,shield,aimed,side,groupAngle,tick,meleeRadius,dodgeBearing);
        }
        public Input withMeleeRadius(double value) {return new Input(level,movement,entityType,x,z,targetX,targetZ,facingX,facingZ,health,shield,aimed,side,groupAngle,tick,value,dodgeBearing);}
        public Input withDodgeBearing(double value) {return new Input(level,movement,entityType,x,z,targetX,targetZ,facingX,facingZ,health,shield,aimed,side,groupAngle,tick,meleeRadius,value);}
    }
    public record Decision(Action action, double x, double z, boolean faceTarget) {}
    private long retreatUntil, retreatReady, dodgeReady;
    public void cancelRetreat() {retreatUntil=0;}

    public Decision decide(Input i) {
        if(i.level()<2) { retreatUntil=0; return at(Action.VANILLA,i.x(),i.z(),false); }
        double dx=i.x()-i.targetX(),dz=i.z()-i.targetZ(),distance=Math.hypot(dx,dz);
        double ux=distance>1e-6?dx/distance:1,uz=distance>1e-6?dz/distance:0;
        int side=i.side()<0?-1:1;
        boolean ranged=i.movement().style().resolve(i.entityType())==MovementDef.Style.RANGED;
        double radius=ranged?11:Math.max(1,i.meleeRadius());
        if(!i.movement().enabled(RETREAT,i.level()))retreatUntil=0;
        else if(i.health()<=.25&&i.tick()>=retreatReady&&i.tick()>=retreatUntil) {
            retreatUntil=i.tick()+60;retreatReady=i.tick()+400;
        }
        if(i.tick()<retreatUntil)
            return at(Action.RETREAT,i.x()+ux*4,i.z()+uz*4,true);
        if(i.movement().enabled(DODGE,i.level())&&i.aimed()&&i.tick()>=dodgeReady) {
            dodgeReady=i.tick()+80;
            double bearing=i.dodgeBearing()==null?Math.atan2(uz,ux):i.dodgeBearing();
            return at(Action.DODGE,i.x()-Math.sin(bearing)*side*3,i.z()+Math.cos(bearing)*side*3,true);
        }
        if(ranged&&(distance<8||distance>14))
            return at(Action.DISTANCE,i.targetX()+ux*radius,i.targetZ()+uz*radius,distance<8);
        if(!ranged&&distance>4)
            return at(Action.PRESS,i.x()-ux*Math.min(4,distance-radius),i.z()-uz*Math.min(4,distance-radius),false);
        // The shield's front is its look vector. Aim just beyond the side, not through it.
        if(i.movement().enabled(SHIELD,i.level())&&i.shield()) {
            double front=Math.atan2(i.facingZ(),i.facingX());
            double angle=front+side*1.7;
            if(i.movement().enabled(SPREAD,i.level())&&i.groupAngle()!=null) {
                double offset=Math.atan2(Math.sin(i.groupAngle()-front),Math.cos(i.groupAngle()-front));
                angle=Math.abs(offset)>=Math.PI/2?i.groupAngle():front+(offset==0?side:Math.signum(offset))*1.7;
            }
            return circle(Action.FLANK,i,angle,radius);
        }
        if(i.movement().enabled(SPREAD,i.level())&&i.groupAngle()!=null)
            return circle(Action.SPREAD,i,i.groupAngle(),radius);
        if(i.movement().enabled(CIRCLE,i.level()))
            // Enough lateral travel to reach a new native node even for narrow mobs.
            return circle(Action.CIRCLE,i,Math.atan2(uz,ux)+side*Math.min(1,.7/radius),radius);
        return at(ranged?Action.HOLD:Action.PRESS,ranged?i.x():i.targetX()+ux*radius,
                ranged?i.z():i.targetZ()+uz*radius,ranged);
    }
    private static Decision circle(Action action,Input i,double angle,double radius) {
        return at(action,i.targetX()+Math.cos(angle)*radius,i.targetZ()+Math.sin(angle)*radius,false);
    }
    private static Decision at(Action action,double x,double z,boolean faceTarget) {
        return new Decision(action,x,z,faceTarget);
    }
}
