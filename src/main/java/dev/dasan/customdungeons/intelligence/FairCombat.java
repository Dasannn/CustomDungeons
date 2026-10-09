package dev.dasan.customdungeons.intelligence;

/** Pure response damage guard; defenses are calculated by Paper before this final cap. */
public final class FairCombat {
    private FairCombat() {}
    public static double nonLethalBase(double base,double finalDamage,double maximumHealth) {
        if(!Double.isFinite(base)||!Double.isFinite(finalDamage)||!Double.isFinite(maximumHealth)||maximumHealth<=0)return 0;
        if(finalDamage<maximumHealth)return Math.max(0,base);
        return Math.max(0,base)*Math.max(0,maximumHealth-1)/Math.max(1,finalDamage);
    }
}
