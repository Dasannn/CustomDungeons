package dev.dasan.customdungeons.intelligence;

import java.util.*;

/** Main-thread, encounter-only ring buffers. No player/entity references or persistent data. */
public final class EncounterMemory {
    public record Observation(long tick,String pattern,String damageType,double damage,boolean behind,boolean ranged,boolean weak,long sequence,String weapon) {}
    private static final class History {
        final ArrayDeque<Observation> events=new ArrayDeque<>(64);
        final Set<String> seen=new HashSet<>();
        String tactic="",type="";long since,sequence;
    }
    private final Map<UUID,History> players=new LinkedHashMap<>();
    public void record(UUID player,String pattern,String type,double damage,long tick) { record(player,pattern,type,damage,tick,false,false,false); }
    public void record(UUID player,String pattern,String type,double damage,long tick,boolean behind,boolean ranged,boolean weak) {record(player,pattern,type,damage,tick,behind,ranged,weak,"");}
    public void record(UUID player,String pattern,String type,double damage,long tick,boolean behind,boolean ranged,boolean weak,String weapon) {
        History h=players.get(player);
        if(h==null) { if(players.size()==16)return;players.put(player,h=new History()); }
        if(!pattern.equals("heal") && (!pattern.equals(h.tactic) || ((pattern.equals("damage")||pattern.equals("consumables"))&&!type.equals(h.type)))) { h.tactic=pattern;h.type=type;h.since=h.sequence; }
        prune(h,tick,200);
        if(h.events.size()==64)h.events.removeFirst();
        h.events.addLast(new Observation(tick,pattern,type,Math.max(0,damage),behind,ranged,weak,h.sequence++,weapon));
    }
    private static void prune(History h,long tick,int window) { while(!h.events.isEmpty()&&tick-h.events.getFirst().tick()>window)h.events.removeFirst(); }
    public int repetitions(UUID player,String pattern,long tick,int window) {
        var h=players.get(player);if(h==null||!h.tactic.equals(pattern))return 0;
        prune(h,tick,200);int count=0;for(var e:h.events)if(e.sequence()>=h.since&&tick-e.tick()<=window&&e.pattern().equals(pattern))count++;
        return count;
    }
    public List<Observation> observations(UUID id,long tick) { var h=players.get(id);if(h==null)return List.of();prune(h,tick,200);return List.copyOf(h.events); }
    public double threat(UUID player,long tick) { return observations(player,tick).stream().mapToDouble(Observation::damage).sum(); }
    public boolean seen(UUID player,String pattern) { var h=players.get(player);return h!=null&&h.seen.contains(pattern); }
    public void remember(UUID player,String pattern) { var h=players.get(player);if(h!=null&&h.seen.size()<32)h.seen.add(pattern); }
    public Set<UUID> players() { return Set.copyOf(players.keySet()); }
    public void reserve(Set<UUID> nearby) {
        int missing=(int)nearby.stream().filter(id->!players.containsKey(id)).count();
        for(var it=players.keySet().iterator();players.size()+missing>16&&it.hasNext();)if(!nearby.contains(it.next()))it.remove();
    }
    public int playerCount() { return players.size(); }
    public int eventCount() { return players.values().stream().mapToInt(h->h.events.size()).sum(); }
    public void forget(UUID player) { players.remove(player); }
    public void clear() { players.clear(); }
}
