package dev.dasan.customdungeons.boss;

import java.util.HashMap;
import java.util.Map;

/** Main-thread reservations include both searches in flight and living encounters. */
final class BossSlots {
    private final Map<String,Integer> slots=new HashMap<>();
    boolean reserve(String id,int maximum) {
        if(count(id)>=maximum)return false;
        slots.merge(id,1,Integer::sum);return true;
    }
    int count(String id) {return slots.getOrDefault(id,0);}
    void release(String id) {slots.computeIfPresent(id,(k,n)->n<=1?null:n-1);}
}
