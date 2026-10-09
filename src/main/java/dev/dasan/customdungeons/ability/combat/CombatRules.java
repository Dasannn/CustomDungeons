package dev.dasan.customdungeons.ability.combat;

/** Pure bounded rules; input damage is the host's final authoritative receipt. */
public final class CombatRules {
    private CombatRules() {}
    public static final class DamageBudget {
        private double remaining;
        public DamageBudget(double maximum) {remaining=maximum;}
        public double remaining() {return remaining;}
        public double take(double damage,double percent) {
            if(!Double.isFinite(damage)||damage<=0)return 0;
            double amount=Math.min(remaining,damage*(percent/100));
            remaining-=amount;return amount;
        }
    }
    public static final class Interruption {
        private final double required;
        private double progress;
        public Interruption(double maximum,double percent) {required=maximum*(percent/100);}
        public double required() {return required;}
        public double progress() {return progress;}
        public boolean add(double damage) {
            if(Double.isFinite(damage)&&damage>0)progress=Math.min(required,progress+damage);
            return progress>=required;
        }
    }
    public static double damage(double amount,double health,double maximum,boolean lethal) {
        return !lethal&&health>=maximum?Math.min(amount,Math.max(0,health-1)):amount;
    }
    public static boolean corridor(double x,double z,double dx,double dz,double length,double halfWidth) {
        double along=x*dx+z*dz;
        return along>=0&&along<=length&&Math.abs(x*dz-z*dx)<=halfWidth;
    }
    /** Distance from the player's eye to the totem's 1 x 2 interaction box. */
    public static boolean totemInReach(double x,double y,double z,double reach) {
        if(!Double.isFinite(reach)||reach<0)return false;
        x=Math.max(0,Math.abs(x)-.5);y=Math.max(0,Math.max(-y,y-2));z=Math.max(0,Math.abs(z)-.5);
        return x*x+y*y+z*z<=reach*reach;
    }
    public static int buffTicks(int remaining,int cap) {return remaining<0?cap:Math.min(remaining,cap);}
}
