package dev.dasan.customdungeons.config;

import dev.dasan.customdungeons.ability.ParamSpec;
import dev.dasan.customdungeons.ability.ParamType;
import java.util.Map;
import java.math.BigDecimal;
import static dev.dasan.customdungeons.config.NumericRange.Origin.*;

/** One definition per model field. ParamSpec remains the authoritative ability contract. */
public final class NumericRanges {
    private NumericRanges() {}
    // Verified against Paper 26.3 build 157. See ARCHITECTURE, numeric ranges.
    public static final double SCALE_ATTRIBUTE_MIN=0.0625;
    public static final double SCALE_WARNING_THRESHOLD=10;
    /** RF-MOB-07: nominally unlimited editing, with headroom for Paper's float arithmetic. */
    public static final double UNBOUNDED_MAX=1e30;
    public static final NumericRange HEALTH=new NumericRange(Double.MIN_VALUE,UNBOUNDED_MAX,8,PLUGIN,true);
    public static final NumericRange SCALE=new NumericRange(0,16,4,MINECRAFT,true);
    public static final NumericRange SPAWNER_RADIUS=new NumericRange(1,64,1,PLUGIN);
    public static final NumericRange SECONDS=new NumericRange(0,3600,1,PLUGIN);
    public static final NumericRange TICKS=new NumericRange(0,72000,0,PLUGIN);
    public static final NumericRange POTION_LEVEL=new NumericRange(1,256,0,MINECRAFT);
    public static final NumericRange ENCHANTMENT_LEVEL=new NumericRange(0,255,0,PLUGIN);
    public static final NumericRange MONEY=new NumericRange(0,1000000000,2,PLUGIN);
    public static final NumericRange XP=new NumericRange(0,1000000,0,PLUGIN);
    public static final NumericRange WAVE_COUNT=new NumericRange(1,200,0,PLUGIN);
    private static NumericRange plugin(double min,double max,int decimals) {return new NumericRange(min,max,decimals,PLUGIN);}
    public static final NumericRange POTION_AMPLIFIER=new NumericRange(POTION_LEVEL.min()-1,POTION_LEVEL.max()-1,
            POTION_LEVEL.decimals(),POTION_LEVEL.origin());
    private static final NumericRange AMBIENCE_DENSITY=plugin(0,32,0);
    private static final Map<String,NumericRange> AMBIENCE=Map.of(
            "density",AMBIENCE_DENSITY,"door-density",AMBIENCE_DENSITY,"title-seconds",plugin(1,10,0),
            "shake-ticks",plugin(0,40,0),"effect-ticks",plugin(20,60,0),"amplifier",POTION_AMPLIFIER);
    private static final Map<String,NumericRange> STATS=Map.of(
            "health",HEALTH,"damage",new NumericRange(0,UNBOUNDED_MAX,8,PLUGIN,true),
            "speed",new NumericRange(0,1024,2,MINECRAFT,true),
            "resistance",new NumericRange(0,1,2,MINECRAFT,true),"scale",SCALE);
    private static final Map<String,NumericRange> ATTRIBUTES=Map.ofEntries(
            Map.entry("max-health",new NumericRange(Double.MIN_VALUE,UNBOUNDED_MAX,8,PLUGIN)),
            Map.entry("damage",new NumericRange(0,UNBOUNDED_MAX,8,PLUGIN)),
            Map.entry("speed",new NumericRange(0,1024,2,MINECRAFT)),
            Map.entry("knockback-resistance",new NumericRange(0,1,2,MINECRAFT)),
            Map.entry("scale",new NumericRange(0,16,4,MINECRAFT)),
            Map.entry("armor",new NumericRange(0,30,2,MINECRAFT)),
            Map.entry("armor-toughness",new NumericRange(0,20,2,MINECRAFT)),
            Map.entry("follow-range",new NumericRange(0,2048,2,MINECRAFT)),
            Map.entry("attack-knockback",new NumericRange(0,5,2,MINECRAFT)),
            Map.entry("jump-strength",new NumericRange(0,32,2,MINECRAFT)),
            Map.entry("gravity",new NumericRange(-1,1,4,MINECRAFT)),
            Map.entry("step-height",new NumericRange(0,10,2,MINECRAFT)),
            Map.entry("explosion-knockback-resistance",new NumericRange(0,1,2,MINECRAFT)));
    public static NumericRange attribute(String key) {return required(ATTRIBUTES,key);}
    private static final Map<String,NumericRange> DUNGEON=Map.ofEntries(
            Map.entry("min",plugin(1,100,0)),Map.entry("max",plugin(0,300,0)),Map.entry("lives",plugin(1,100,0)),
            Map.entry("countdown",plugin(5,600,0)),Map.entry("time",plugin(0,7200,0)),Map.entry("cooldown",plugin(0,604800,0)),
            Map.entry("extra-mobs",plugin(0,500,0)),Map.entry("extra-health",plugin(0,500,0)),
            Map.entry("exit-grace",plugin(10,300,0)),Map.entry("plate-countdown",plugin(1,Integer.MAX_VALUE,0)),
            Map.entry("intro-seconds",plugin(5,20,0)),Map.entry("radius",SPAWNER_RADIUS),Map.entry("count",WAVE_COUNT),
            Map.entry("interval",plugin(.1,3600,1)),Map.entry("pause",SECONDS),Map.entry("delay",SECONDS));
    private static final Map<String,NumericRange> MOB=Map.of(
            "drop-chance",plugin(0,1,2),"potion-level",POTION_LEVEL,"trigger-value",plugin(0,3600,2),
            "range",plugin(0,256,2),"cooldown",TICKS,"threshold",plugin(.01,99.99,2),
            "heal",plugin(0,100,2),"invulnerable-ticks",plugin(0,1200,0),"delay",TICKS);
    private static final Map<String,NumericRange> COMMON=Map.of(
            "trigger-value",plugin(0,72000,2),"range",plugin(0,72000,2),"cooldown",TICKS,
            "chance",plugin(0,1,2),"telegraph",TICKS);
    public static NumericRange ambience(String key) {return required(AMBIENCE,key);}
    public static NumericRange stat(String key) {return required(STATS,key);}
    public static NumericRange dungeon(String key) {return required(DUNGEON,key);}
    public static NumericRange mob(String key) {return required(MOB,key);}
    public static NumericRange common(String key) {return required(COMMON,key);}
    public static NumericRange summonCount(PluginConfig config) {return plugin(1,config.limits().maxAliveMobsPerSession(),0);}
    public static boolean numeric(ParamSpec spec) {return spec.type()==ParamType.DOUBLE || spec.type()==ParamType.INT || spec.type()==ParamType.TICKS;}
    /** No second table of ability bounds: adapt the exact registered ParamSpec. */
    public static NumericRange parameter(ParamSpec spec) {
        if(!numeric(spec)) throw new IllegalArgumentException("Non-numeric parameter");
        var origin=spec.key().endsWith("Amplifier") || spec.key().equals("amplifier")
                ? (spec.min()==0 && spec.max()==255 ? MINECRAFT : PLUGIN) : PLUGIN;
        return new NumericRange(spec.min(),spec.max(),spec.type()==ParamType.DOUBLE ? 8 : 0,origin);
    }
    /** Percent editors store decimal coefficients; avoid binary noise on conversion back. */
    public static double percent(double fraction) {
        return Double.isFinite(fraction) ? BigDecimal.valueOf(fraction).movePointRight(2).doubleValue() : fraction;
    }
    public static double effectiveScale(double scale) {return scale==0 ? 1 : Math.max(SCALE_ATTRIBUTE_MIN,scale);}
    private static NumericRange required(Map<String,NumericRange> ranges,String key) {
        var range=ranges.get(key);if(range==null)throw new IllegalArgumentException("Unknown numeric field: "+key);return range;
    }
}
