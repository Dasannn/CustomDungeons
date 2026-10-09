package dev.dasan.customdungeons.ability.control;

/** Pure fair-combat and escape rules, shared by all eight abilities. */
public final class ControlRules {
    private ControlRules() {}
    public static final class Escape {
        private final int required;private final double threshold;
        private int jumps;private double damage;private boolean pressed;
        public Escape(int jumps,double damage) {required=jumps;threshold=damage;}
        public void held(boolean down) {pressed=down;}
        public int jumps() {return jumps;}
        public boolean jump(boolean down) {if(down&&!pressed)jumps++;pressed=down;return jumps>=required;}
        public boolean damage(double amount) {if(Double.isFinite(amount)&&amount>0)damage+=amount;return damage>=threshold;}
    }
    public static double bombDamage(double amount,double health,boolean guarded) {
        return guarded?Math.min(amount,Math.max(0,health-1)):amount;
    }
    public static boolean summon(int level,double health,int attackers,int allies) {return dev.dasan.customdungeons.intelligence.IntelligenceRules.tacticalSummon(level,health,attackers,allies);}
    /** Swept player volume against one target, returning first contact distance or infinity. */
    public static double impactDistance(org.bukkit.util.BoundingBox from,org.bukkit.util.BoundingBox to,org.bukkit.util.BoundingBox target) {
        var expanded=target.clone().expand(Math.max(from.getWidthX(),to.getWidthX())/2,
            Math.max(from.getHeight(),to.getHeight())/2,Math.max(from.getWidthZ(),to.getWidthZ())/2);
        var origin=from.getCenter();if(expanded.contains(origin))return 0;
        var travel=to.getCenter().subtract(origin);double length=travel.length();
        if(length==0)return Double.POSITIVE_INFINITY;
        var hit=expanded.rayTrace(origin,travel,length);
        return hit==null?Double.POSITIVE_INFINITY:hit.getHitPosition().distance(origin);
    }
    public static int summonCount(int count,int alive,int maximum,int hostAlive,int hostMaximum) {
        return Math.max(0,Math.min(count,Math.min(maximum-alive,hostMaximum-hostAlive)));
    }
}
