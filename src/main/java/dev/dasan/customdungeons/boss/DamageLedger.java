package dev.dasan.customdungeons.boss;

import java.util.*;

/** Original (virtual) HP units, after defenses, with each hit capped at remaining HP. */
final class DamageLedger {
    private final double maximum,minimum;
    private final Map<UUID,Double> damage=new HashMap<>();
    DamageLedger(double maximum,double minimumPercent) {this.maximum=maximum;minimum=maximum*(minimumPercent/100);}
    void add(UUID player,double amount,double remaining) {
        if(player==null || !Double.isFinite(amount) || amount<=0 || !Double.isFinite(remaining))return;
        double accepted=Math.min(amount,Math.max(0,remaining));
        damage.merge(player,accepted,(a,b)->Math.min(1e30,a+b));
    }
    double damage(UUID player) {return damage.getOrDefault(player,0d);}
    double percent(UUID player) {return maximum>0?damage(player)/maximum*100:0;}
    Set<UUID> eligible(boolean diedByDamage) {
        if(!diedByDamage)return Set.of();
        var result=new HashSet<UUID>();damage.forEach((id,n)->{if(n>=minimum)result.add(id);});return Set.copyOf(result);
    }
}
