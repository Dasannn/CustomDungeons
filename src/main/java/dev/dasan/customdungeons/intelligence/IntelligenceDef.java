package dev.dasan.customdungeons.intelligence;

import dev.dasan.customdungeons.config.NumericRange;
import java.util.*;

/** Optional additive definition. Null phase fields preserve the previous effective value. */
public record IntelligenceDef(Integer explicitLevel, WeakPoint explicitWeakPoint, Integer explicitBonus,
                              Map<String,Integer> advanced, Set<String> disabled) {
    public enum WeakPoint { NONE, BACK, HEAD }
    public static final IntelligenceDef NONE=new IntelligenceDef(null,null,null,Map.of(),Set.of());
    public static final IntelligenceDef INHERIT=NONE;
    public IntelligenceDef { advanced=Map.copyOf(advanced);disabled=Set.copyOf(disabled); }
    public static IntelligenceDef level(int level) { return NONE.withLevel(level); }
    public int level() { return explicitLevel==null?0:explicitLevel; }
    public WeakPoint weakPoint() { return explicitWeakPoint==null?WeakPoint.NONE:explicitWeakPoint; }
    public int bonus() { return explicitBonus==null?25:explicitBonus; }
    public IntelligenceDef withLevel(Integer value) { return new IntelligenceDef(value,explicitWeakPoint,explicitBonus,advanced,disabled); }
    public IntelligenceDef withWeakPoint(WeakPoint value) { return new IntelligenceDef(explicitLevel,value,explicitBonus,advanced,disabled); }
    public IntelligenceDef withBonus(Integer value) { return new IntelligenceDef(explicitLevel,explicitWeakPoint,value,advanced,disabled); }
    public IntelligenceDef withAdvanced(String field,Integer value) { var map=new LinkedHashMap<>(advanced);if(value==null)map.remove(field);else map.put(field,value);return new IntelligenceDef(explicitLevel,explicitWeakPoint,explicitBonus,map,disabled); }
    public IntelligenceDef toggle(String id) { var set=new HashSet<>(disabled);if(!set.remove(id))set.add(id);return new IntelligenceDef(explicitLevel,explicitWeakPoint,explicitBonus,advanced,set); }
    public IntelligenceDef phase(IntelligenceDef phase) { return new IntelligenceDef(phase.explicitLevel==null?explicitLevel:phase.explicitLevel,phase.explicitWeakPoint==null?explicitWeakPoint:phase.explicitWeakPoint,phase.explicitBonus==null?explicitBonus:phase.explicitBonus,advanced,disabled); }
    public int defaultValue(String field) {
        return switch(field) {
            case "window" -> level()>=5?6:level()==4?8:10;
            case "repetitions" -> level()>=5?2:level()>=3?3:4;
            case "duration" -> switch(level()){case 5->15;case 4->12;case 3->10;default->8;};
            case "cooldown" -> switch(level()){case 5->10;case 4->12;case 3->15;default->20;};
            case "maximum" -> level()<2?0:level()>=5?3:level()==4?2:1;
            default -> throw new IllegalArgumentException("Unknown intelligence field: "+field);
        };
    }
    public int value(String field) { return (int)range(field,level()<2?5:Math.clamp(level(),0,5)).clamp(advanced.getOrDefault(field,defaultValue(field))); }
    public int maximum() { return Math.clamp(value("maximum"),0,defaultValue("maximum")); }
    public static NumericRange range(String field,int level) {
        return switch(field) {
            case "level" -> range(0,5);case "bonus" -> range(0,100);case "window" -> range(1,10);
            case "repetitions" -> range(2,16);case "duration" -> range(1,30);case "cooldown" -> range(1,60);
            case "maximum" -> range(0,IntelligenceDef.level(level).defaultValue("maximum"));
            default -> throw new IllegalArgumentException("Unknown intelligence field");
        };
    }
    private static NumericRange range(int min,int max) { return new NumericRange(min,max,0,NumericRange.Origin.PLUGIN); }
}
