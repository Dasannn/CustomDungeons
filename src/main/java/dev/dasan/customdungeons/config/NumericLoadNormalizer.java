package dev.dasan.customdungeons.config;

import dev.dasan.customdungeons.ability.AbilityRegistry;
import dev.dasan.customdungeons.model.RoomAmbience;
import java.math.BigDecimal;
import java.util.*;
import java.util.function.Consumer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

/** Compatibility at the read boundary only. Bounds always come from NumericRanges/registered ParamSpecs. */
final class NumericLoadNormalizer {
    record Result(YamlConfiguration yaml,List<Validator.Warning> warnings) {}
    private final PluginConfig config;
    private final AbilityRegistry registry;
    private final List<Validator.Warning> warnings=new ArrayList<>();
    NumericLoadNormalizer(PluginConfig config,AbilityRegistry registry) {this.config=config;this.registry=registry;}
    Result normalize(String kind,YamlConfiguration source) {
        warnings.clear();
        var fields=new Fields(copyMap(source.getValues(false)),"");
        switch(kind) {
            case "mobs" -> mob(fields);
            case "dungeons" -> dungeon(fields);
            case "spawners" -> spawner(fields);
            case "ambience-defaults" -> fields.child("ambience",a->a.child("defaults",this::ambience));
            default -> throw new IllegalArgumentException("Unknown definition kind");
        }
        var yaml=new YamlConfiguration();fields.values.forEach(yaml::set);
        return new Result(yaml,List.copyOf(warnings));
    }
    private void dungeon(Fields f) {
        for(var field:Map.ofEntries(Map.entry("min-players","min"),Map.entry("max-players","max"),
                Map.entry("lives","lives"),Map.entry("lobby-countdown-seconds","countdown"),
                Map.entry("time-limit-seconds","time"),Map.entry("cooldown-seconds","cooldown"),
                Map.entry("plate-countdown-seconds","plate-countdown"),Map.entry("exit-grace-seconds","exit-grace"),
                Map.entry("intro-seconds","intro-seconds")).entrySet())
            number(f,field.getKey(),NumericRanges.dungeon(field.getValue()),1,0,true);
        f.child("scaling",s->{
            number(s,"extra-mobs-per-player",NumericRanges.dungeon("extra-mobs"),100,0,false);
            number(s,"extra-health-per-player",NumericRanges.dungeon("extra-health"),100,0,false);
        });
        f.child("reward",r->{number(r,"money",NumericRanges.MONEY);number(r,"xp",NumericRanges.XP,1,0,true);});
        f.list("rooms",r->{r.list("spawners",this::spawner);r.child("ambience",this::ambience);});
    }
    /** A single config default uses no mob limits or ability registry; invalid types keep per-field fallback. */
    static Result normalizeAmbienceDefault(String key,Object value) {
        var source=new YamlConfiguration();source.set("ambience.defaults."+key,value);
        var normalized=new NumericLoadNormalizer(null,null).normalize("ambience-defaults",source);
        var fields=(Map<?,?>)((Map<?,?>)normalized.yaml().get("ambience")).get("defaults");
        var single=new YamlConfiguration();single.set(key,fields.get(key));
        return new Result(single,normalized.warnings());
    }
    private void ambience(Fields f) {
        for(String key:RoomAmbience.NUMBERS)number(f,key,NumericRanges.ambience(key),1,0,true);
        for(String key:List.of("effects","boss-effects"))
            f.list(key,e->number(e,"amplifier",NumericRanges.ambience("amplifier"),1,0,true));
    }
    private void spawner(Fields f) {
        number(f,"radius",NumericRanges.SPAWNER_RADIUS);
        f.list("waves",w->{
            // T01's inactive interval has a zero default; nonzero values respect the range in every mode.
            if("STAGGERED".equalsIgnoreCase(Objects.toString(w.values.get("mode")))
                    || !(w.values.get("stagger-interval-ticks") instanceof Number n && n.doubleValue()==0))
                number(w,"stagger-interval-ticks",NumericRanges.dungeon("interval"),.05,0,true);
            number(w,"pause-after-ticks",NumericRanges.SECONDS,.05,0,true);
            w.list("entries",e->{number(e,"count",NumericRanges.WAVE_COUNT,1,0,true);number(e,"delay-ticks",NumericRanges.SECONDS,.05,0,true);});
        });
    }
    private void mob(Fields f) {
        for(var field:Map.of("max-health","health","damage","damage","speed","speed","knockback-resistance","resistance","scale","scale").entrySet())
            number(f,field.getKey(),NumericRanges.stat(field.getValue()));
        attributes(f);
        intelligence(f,0);
        f.child("world-boss",b->{
            NumericRanges.WORLD_BOSS.forEach((key,range)->number(b,key,range,1,0,range.decimals()==0));
            b.child("reward",r->{number(r,"money",NumericRanges.MONEY);number(r,"xp",NumericRanges.XP,1,0,true);});
        });
        loadout(f);
        f.list("phases",p->{
            number(p,"health-threshold",NumericRanges.mob("threshold"),100,0,false);
            number(p,"heal-percent",NumericRanges.mob("heal"));
            number(p,"invulnerable-ticks",NumericRanges.mob("invulnerable-ticks"),1,0,true);
            p.list("summons",s->{number(s,"count",NumericRanges.summonCount(config),1,0,true);number(s,"delay-ticks",NumericRanges.TICKS,1,0,true);});
            attributes(p);
            intelligence(p,0);
            loadout(p);
        });
    }
    private void intelligence(Fields f,int inherited) {
        f.child("intelligence",i->{
            number(i,"level",dev.dasan.customdungeons.intelligence.IntelligenceDef.range("level",0),1,0,true);
            number(i,"weak-point-bonus",dev.dasan.customdungeons.intelligence.IntelligenceDef.range("bonus",0),1,0,true);
            int level=i.values.get("level") instanceof Number n?n.intValue():inherited;
            i.child("advanced",a->{for(String field:List.of("window","repetitions","duration","cooldown","maximum"))number(a,field,dev.dasan.customdungeons.intelligence.IntelligenceDef.range(field,level<2?5:level),1,0,true);});
        });
    }
    private void attributes(Fields f) {
        f.child("attributes", a -> {
            for (String key : dev.dasan.customdungeons.model.MobAttributes.KEYS)
                number(a, key, NumericRanges.attribute(key));
        });
    }
    private void loadout(Fields f) {
        f.child("equipment",equipment->equipment.values.keySet().forEach(slot->{
            // Leave unknown keys to the codec, whose parse warning never exposes user supplied enum text.
            try {org.bukkit.inventory.EquipmentSlot.valueOf(slot.toUpperCase(Locale.ROOT));}
            catch(IllegalArgumentException unknown) {return;}
            equipment.child(slot,e->{
                number(e,"drop-chance",NumericRanges.mob("drop-chance"));
                if(e.values.get("item") instanceof ItemStack item && item.hasItemMeta()) {
                    // Clone before editing: the YAML object and caller's items must remain untouched.
                    ItemStack adjusted=item.clone();
                    for(var enchant:item.getEnchantments().entrySet()) {
                        var levels=new Fields(new LinkedHashMap<>(Map.of("level",enchant.getValue())),e.path+".enchantments");
                        number(levels,"level",NumericRanges.ENCHANTMENT_LEVEL,1,0,true,
                                e.path+".enchantments."+enchant.getKey().getKey());
                        int level=((Number)levels.values.get("level")).intValue();
                        if(level!=enchant.getValue()) {adjusted.removeEnchantment(enchant.getKey());if(level>0) adjusted.addUnsafeEnchantment(enchant.getKey(),level);}
                    }
                    e.values.put("item",adjusted);
                }
            });
        }));
        f.list("potions",p->number(p,"amplifier",NumericRanges.POTION_LEVEL,1,1,true));
        f.list("abilities",a->{
            for(var field:Map.of("trigger-value","trigger-value","range","range","cooldown-ticks","cooldown", "chance","chance","telegraph-ticks","telegraph").entrySet())
                number(a,field.getKey(),NumericRanges.common(field.getValue()),1,0,field.getKey().endsWith("ticks"));
            parameters(a);
        });
        f.list("combos",c->{
            number(c,"trigger-value",NumericRanges.mob("trigger-value"));number(c,"range",NumericRanges.mob("range"));
            number(c,"cooldown-ticks",NumericRanges.TICKS,1,0,true);
            c.list("steps",s->{number(s,"delay-ticks",NumericRanges.SECONDS,.05,0,true);parameters(s);});
        });
    }
    private void parameters(Fields f) {
        registry.get(Objects.toString(f.values.get("ability-id"),"")).ifPresent(ability->f.child("params",p->{
            for(var spec:ability.params()) if(NumericRanges.numeric(spec))
                number(p,spec.key(),NumericRanges.parameter(spec),1,0,spec.type()!=dev.dasan.customdungeons.ability.ParamType.DOUBLE);
        }));
    }
    private void number(Fields f,String key,NumericRange range) {number(f,key,range,1,0,false);}
    private void number(Fields f,String key,NumericRange range,double factor,int offset,boolean integer) {
        number(f,key,range,factor,offset,integer,f.fieldPath(key));
    }
    private void number(Fields f,String key,NumericRange range,double factor,int offset,boolean integer,String path) {
        Object raw=f.values.get(key);if(raw==null)return;
        if(!(raw instanceof Number)) throw new InvalidNumber(path);
        BigDecimal original;
        try {original=new BigDecimal(raw.toString());} catch(NumberFormatException invalid) {throw new InvalidNumber(path);}
        BigDecimal multiplier=BigDecimal.valueOf(factor);
        BigDecimal displayed=original.multiply(multiplier).add(BigDecimal.valueOf(offset));
        BigDecimal min=BigDecimal.valueOf(range.min()),max=BigDecimal.valueOf(range.max());
        if(range.vanillaZero() && displayed.signum()==0)return;
        BigDecimal clamped=displayed.max(min).min(max);
        if(displayed.compareTo(clamped)==0)return;
        BigDecimal stored=clamped.subtract(BigDecimal.valueOf(offset)).divide(multiplier);
        if(integer) f.values.put(key,stored.intValueExact());
        else f.values.put(key,stored.doubleValue());
        String message=path.equals("max-health") && original.compareTo(max)>0?"validation.health-clamped":"validation.numeric-clamped";
        warnings.add(new Validator.Warning(path,message,Map.of("value",original.stripTrailingZeros().toPlainString(),
                "adjusted",stored.stripTrailingZeros().toPlainString(),"min",range.format(range.min()),"max",range.format(range.max()))));
    }
    static final class InvalidNumber extends IllegalArgumentException {
        final String path;InvalidNumber(String path) {super("Invalid numeric definition");this.path=path;}
    }
    private record Fields(Map<String,Object> values,String path) {
        String fieldPath(String key) {return path.isEmpty()?key:path+"."+key;}
        @SuppressWarnings("unchecked") void child(String key,Consumer<Fields> visit) {
            if(values.get(key) instanceof Map<?,?> map)visit.accept(new Fields((Map<String,Object>)map,fieldPath(key)));
        }
        @SuppressWarnings("unchecked") void list(String key,Consumer<Fields> visit) {
            if(values.get(key) instanceof List<?> list) for(int i=0;i<list.size();i++)
                if(list.get(i) instanceof Map<?,?> map)visit.accept(new Fields((Map<String,Object>)map,fieldPath(key)+"["+i+"]"));
        }
    }
    private static Object copy(Object value) {
        if(value instanceof ConfigurationSection section)return copyMap(section.getValues(false));
        if(value instanceof Map<?,?> map)return copyMap(map);
        if(value instanceof List<?> list)return new ArrayList<>(list.stream().map(NumericLoadNormalizer::copy).toList());
        return value;
    }
    private static Map<String,Object> copyMap(Map<?,?> map) {var out=new LinkedHashMap<String,Object>();map.forEach((k,v)->out.put(k.toString(),copy(v)));return out;}
}
