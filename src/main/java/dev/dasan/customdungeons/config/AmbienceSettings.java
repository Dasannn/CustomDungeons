package dev.dasan.customdungeons.config;

import dev.dasan.customdungeons.model.*;
import java.util.*;
import java.util.function.Consumer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;

/** Compiled at session/editor creation, never loads config or disk in the ticker. */
public final class AmbienceSettings {
    private static final Set<String> SOUND_KEYS=Set.of("entry-sound","music","door-sound","door-rumble","clear-sound");
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
                var normalized=NumericLoadNormalizer.normalizeAmbienceDefault(key,section.get(key));
                normalized.warnings().forEach(w->warning.accept(w.path()));
                var normalizedSection=normalized.yaml();
                Object value=normalizedSection.get(key);
                if(key.equals("effects") || key.equals("boss-effects"))value=readEffects(normalizedSection,key);
                var candidate=loadCompatible(new RoomAmbience(Map.of(key,Objects.requireNonNull(value))),
                        path->warning.accept("ambience.defaults."+path));
                value=candidate.values().get(key);
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
            if(RoomAmbience.NUMBERS.contains(key) && !NumericRanges.ambience(key).contains((Integer)value))errors.add(key);
            if(key.equals("effects") || key.equals("boss-effects")) {
                var types=new HashSet<String>();
                for(Object item:(List<?>)value) {
                    var effect=(PotionDef)item;
                    if(!NumericRanges.ambience("amplifier").contains(effect.amplifier()) || !knownEffect(effect.effectKey())
                            || !types.add(effect.effectKey()))errors.add(key);
                }
            }
            if(SOUND_KEYS.contains(key) && !((String)value).isBlank() && !knownSound((String)value))errors.add(key);
            if(key.equals("particle") || key.equals("door-particle")) {
                String keys=(String)value;
                if(!keys.isBlank() && Arrays.stream(keys.split(",",-1)).anyMatch(p->!knownParticle(p)))errors.add(key);
            }
        });
        return List.copyOf(errors);
    }
    private static NamespacedKey registryKey(String key) {
        return key.matches("[a-z0-9_.-]+:[a-z0-9_/.-]+")?NamespacedKey.fromString(key):null;
    }
    private static boolean knownEffect(String key) {
        var id=registryKey(key);return id!=null && Registry.EFFECT.get(id)!=null;
    }
    private static boolean knownSound(String key) {
        var id=registryKey(key);return id!=null && Registry.SOUNDS.get(id)!=null;
    }
    private static boolean knownParticle(String key) {
        try {return org.bukkit.Particle.valueOf(key).getDataType()==Void.class;}
        catch(IllegalArgumentException invalid){return false;}
    }
    /** Loading compatibility: omit unknown registry entries, warn, and never rewrite the file. */
    public static RoomAmbience loadCompatible(RoomAmbience value,Consumer<String> warning) {
        var fields=new LinkedHashMap<>(value.values());
        value.values().forEach((key,raw)->{
            if(key.equals("effects") || key.equals("boss-effects")) {
                var valid=new ArrayList<PotionDef>();int index=0;
                for(Object item:(List<?>)raw) {
                    var effect=(PotionDef)item;
                    if(knownEffect(effect.effectKey()))valid.add(effect);
                    else warning.accept(key+"["+index+"].effect-key");
                    index++;
                }
                fields.put(key,List.copyOf(valid));
            } else if(SOUND_KEYS.contains(key) && !((String)raw).isBlank() && !knownSound((String)raw)) {
                warning.accept(key);fields.put(key,"");
            } else if((key.equals("particle") || key.equals("door-particle")) && !((String)raw).isBlank()) {
                var particles=Arrays.asList(((String)raw).split(",",-1));
                var valid=particles.stream().filter(AmbienceSettings::knownParticle).toList();
                if(valid.size()!=particles.size()){warning.accept(key);fields.put(key,String.join(",",valid));}
            }
        });
        return new RoomAmbience(fields);
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
