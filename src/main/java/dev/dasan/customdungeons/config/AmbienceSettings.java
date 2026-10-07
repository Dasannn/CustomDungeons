package dev.dasan.customdungeons.config;

import dev.dasan.customdungeons.model.*;
import java.util.*;
import java.util.function.Consumer;
import org.bukkit.configuration.ConfigurationSection;

/** Compiled at session/editor creation, never loads config or disk in the ticker. */
public final class AmbienceSettings {
    // TODO T46: replace these shared bounds with NumericRange when it is integrated in main.
    public record Bounds(int min,int max) { public boolean contains(int n){return n>=min && n<=max;} }
    public static final Map<String,Bounds> RANGES=Map.of("density",new Bounds(0,32),"door-density",new Bounds(0,32),
            "title-seconds",new Bounds(1,10),"shake-ticks",new Bounds(0,40),"effect-ticks",new Bounds(20,60),"amplifier",new Bounds(0,255));
    private final RoomAmbience defaults;
    private AmbienceSettings(RoomAmbience defaults){this.defaults=defaults;}
    public static RoomAmbience epicDefaults() {
        var map=new LinkedHashMap<String,Object>();
        map.put("entry-title","{room}");map.put("entry-subtitle","");
        map.put("entry-sound","minecraft:entity.warden.emerge");map.put("music","");
        map.put("effects",List.of());map.put("boss-effects",List.of(new PotionDef("minecraft:darkness",0,false)));
        map.put("particle","");map.put("density",4);map.put("door-density",24);
        map.put("door-sound","minecraft:block.iron_door.open");map.put("door-rumble","minecraft:entity.ravager.step");
        map.put("door-particle","CLOUD,SMOKE");map.put("door-title","");map.put("door-shake",false);
        map.put("clear-sound","minecraft:ui.toast.challenge_complete");map.put("clear-title","@ambience.cleared-title");
        map.put("title-seconds",3);map.put("shake-ticks",30);map.put("effect-ticks",30);
        return new RoomAmbience(map);
    }
    public static AmbienceSettings load(ConfigurationSection config,Consumer<String> warning) {
        var defaults=epicDefaults();var section=config==null?null:config.getConfigurationSection("ambience.defaults");
        if(section!=null) for(String key:section.getKeys(false)) {
            try {
                Object value=section.get(key);
                if(key.equals("effects") || key.equals("boss-effects"))value=readEffects(section,key);
                var candidate=new RoomAmbience(Map.of(key,Objects.requireNonNull(value)));
                if(!errors(candidate).isEmpty())throw new IllegalArgumentException();
                defaults=defaults.with(key,value);
            } catch(IllegalArgumentException | NullPointerException invalid){warning.accept("ambience.defaults."+key);}
        }
        return new AmbienceSettings(defaults);
    }
    public static List<PotionDef> readEffects(ConfigurationSection section,String key) {
        Object raw=section.get(key);
        if(!(raw instanceof List<?> list))throw new IllegalArgumentException("Invalid ambience effects");
        var effects=new ArrayList<PotionDef>();
        for(Object item:list) {
            if(!(item instanceof Map<?,?> map) || !(map.get("effect-key") instanceof String type))throw new IllegalArgumentException("Invalid ambience effect");
            Object amp=map.containsKey("amplifier")?map.get("amplifier"):0;
            Object particles=map.containsKey("particles")?map.get("particles"):false;
            if(!(amp instanceof Number n) || !Double.isFinite(n.doubleValue()) || n.doubleValue()!=n.intValue()
                    || !(particles instanceof Boolean visible))throw new IllegalArgumentException("Invalid ambience effect");
            effects.add(new PotionDef(type,n.intValue(),visible));
        }
        return List.copyOf(effects);
    }
    public static List<String> errors(RoomAmbience settings) {
        var errors=new ArrayList<String>();
        settings.values().forEach((key,value)->{
            if(RoomAmbience.NUMBERS.contains(key) && !RANGES.get(key).contains((Integer)value))errors.add(key);
            if(key.equals("effects") || key.equals("boss-effects")) {
                var types=new HashSet<String>();
                for(Object item:(List<?>)value) {
                    var effect=(PotionDef)item;
                    if(!RANGES.get("amplifier").contains(effect.amplifier()) || !effect.effectKey().matches("[a-z0-9_.-]+:[a-z0-9_/.-]+")
                            || !types.add(effect.effectKey()))errors.add(key);
                }
            }
            if(Set.of("entry-sound","music","door-sound","door-rumble","clear-sound").contains(key)
                    && !((String)value).isBlank() && !((String)value).matches("[a-z0-9_.-]+:[a-z0-9_/.-]+"))errors.add(key);
            if(key.equals("particle") || key.equals("door-particle")) {
                String keys=(String)value;
                try {
                    if(!keys.isBlank())for(String particle:keys.split(",",-1)) {
                        if(particle.isBlank()){errors.add(key);continue;}
                        var type=org.bukkit.Particle.valueOf(particle);
                        if(type.getDataType()!=Void.class)errors.add(key);
                    }
                } catch(IllegalArgumentException unknown){errors.add(key);}
            }
        });
        return List.copyOf(errors);
    }
    public Resolved resolve(RoomAmbience override,boolean bossRoom) {
        var map=new LinkedHashMap<>(defaults.values());
        if(override!=null)map.putAll(override.values());
        if(bossRoom && (override==null || !override.values().containsKey("effects")))map.put("effects",map.get("boss-effects"));
        return new Resolved(new RoomAmbience(map));
    }
    public record Resolved(RoomAmbience settings) {
        public String text(String key){return (String)settings.values().get(key);}
        public int number(String key){return (Integer)settings.values().get(key);}
        public boolean flag(String key){return (Boolean)settings.values().get(key);}
        @SuppressWarnings("unchecked") public List<PotionDef> effects(){return (List<PotionDef>)settings.values().get("effects");}
    }
}
