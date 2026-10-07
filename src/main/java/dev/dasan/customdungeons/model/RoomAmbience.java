package dev.dasan.customdungeons.model;

import java.util.*;

/** Sparse room overrides. An absent key inherits config; an empty string/list disables it. */
public record RoomAmbience(Map<String,Object> values) {
    public static final Set<String> TEXT = Set.of("entry-title","entry-subtitle","entry-sound","music",
            "particle","door-sound","door-rumble","door-particle","door-title","clear-sound","clear-title");
    public static final Set<String> NUMBERS = Set.of("density","door-density","title-seconds","shake-ticks","effect-ticks");
    public RoomAmbience {
        var copy=new LinkedHashMap<String,Object>();
        values.forEach((key,value)->{
            if(TEXT.contains(key) && value instanceof String s && s.length()<=256) copy.put(key,s);
            else if(NUMBERS.contains(key) && value instanceof Number n && Double.isFinite(n.doubleValue())
                    && n.doubleValue()==n.intValue()) copy.put(key,n.intValue());
            else if(key.equals("door-shake") && value instanceof Boolean) copy.put(key,value);
            else if((key.equals("effects") || key.equals("boss-effects")) && value instanceof List<?> list
                    && list.size()<=16 && list.stream().allMatch(PotionDef.class::isInstance)) copy.put(key,List.copyOf(list));
            else throw new IllegalArgumentException("Invalid ambience field: "+key);
        });
        values=Collections.unmodifiableMap(copy);
    }
    public RoomAmbience with(String key,Object value) {
        var copy=new LinkedHashMap<>(values);
        if(value==null)copy.remove(key);else copy.put(key,value);
        return new RoomAmbience(copy);
    }
}
