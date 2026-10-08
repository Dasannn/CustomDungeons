package dev.dasan.customdungeons.boss;

import dev.dasan.customdungeons.model.MobTemplate;
import java.util.*;

/** Server branches register code bases here. Only explicitly permitted world settings can be overridden. */
public final class BossRegistry {
    public record Entry(MobTemplate template, boolean configurable) {}
    private final Map<String,Entry> code=new LinkedHashMap<>();
    public void register(MobTemplate template,boolean configurable) {
        Objects.requireNonNull(template.worldBoss(),"Code boss requires world settings");
        if(code.putIfAbsent(template.id(),new Entry(template,configurable))!=null)
            throw new IllegalArgumentException("Duplicate boss id");
    }
    public boolean definedInCode(String id) {return code.containsKey(id);}
    public boolean configurable(String id) {return !code.containsKey(id)||code.get(id).configurable();}
    public Map<String,MobTemplate> all(Map<String,MobTemplate> yaml) {
        var result=new TreeMap<String,MobTemplate>();
        yaml.forEach((id,m)->{if(m.worldBoss()!=null)result.put(id,m);});
        code.forEach((id,e)->{
            var override=yaml.get(id);
            result.put(id,e.configurable()&&override!=null&&override.worldBoss()!=null
                    ? e.template().withWorldBoss(override.worldBoss()):e.template());
        });
        return Collections.unmodifiableMap(result);
    }
}
