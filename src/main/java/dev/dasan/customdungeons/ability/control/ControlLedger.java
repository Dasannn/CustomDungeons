package dev.dasan.customdungeons.ability.control;

import java.util.*;

/** Main-thread ownership. Pair acquisition is all-or-nothing; time is supplied by the caller. */
public final class ControlLedger<T> {
    private final Map<UUID,T> active=new HashMap<>();
    private final Map<UUID,Long> immune=new HashMap<>();
    private record Guard(UUID player,long expires) {}
    private final PriorityQueue<Guard> deadlines=new PriorityQueue<>(Comparator.comparingLong(Guard::expires));
    public boolean acquire(List<UUID> players,T value,long now) {
        while(!deadlines.isEmpty()&&now>=deadlines.peek().expires()) {
            var guard=deadlines.remove();immune.remove(guard.player(),guard.expires());
        }
        if(players.isEmpty()||players.stream().anyMatch(p->!available(p,now)))return false;
        for(UUID p:players){immune.remove(p);active.put(p,value);}return true;
    }
    public T get(UUID player) {return active.get(player);}
    /** Read-only eligibility shared by acquisition and throw impacts; never consumes immunity. */
    public boolean available(UUID player,long now) {
        return !active.containsKey(player)&&now>=immune.getOrDefault(player,Long.MIN_VALUE);
    }
    public boolean isEmpty() {return active.isEmpty();}
    public Collection<T> values() {return Set.copyOf(active.values());}
    public void release(T value,long now) {
        var ids=active.entrySet().stream().filter(e->e.getValue()==value).map(Map.Entry::getKey).toList();
        for(UUID p:ids){active.remove(p);immune.put(p,now+60);deadlines.add(new Guard(p,now+60));}
    }
    public void expire(UUID player,long now) {if(now>=immune.getOrDefault(player,Long.MAX_VALUE))immune.remove(player);}
    int immunitySize() {return immune.size();}
    public void clear() {active.clear();immune.clear();deadlines.clear();}
}
