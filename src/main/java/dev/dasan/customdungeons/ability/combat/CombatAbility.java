package dev.dasan.customdungeons.ability.combat;

import dev.dasan.customdungeons.ability.*;
import java.util.List;
import org.bukkit.Material;

/** Declared parameters also drive the existing editor. Intrinsic warnings cannot be bypassed. */
public abstract class CombatAbility implements Ability {
    private final String id;
    private final Material icon;
    private final List<ParamSpec> params;
    protected CombatAbility(String id,Material icon,List<ParamSpec> params) {
        this.id=id;this.icon=icon;this.params=List.copyOf(params);
    }
    public final String id() {return id;}
    public final Material icon() {return icon;}
    public final List<ParamSpec> params() {return params;}
    public int defaultTelegraphTicks() {return id.equals("blink_behind")?10:20;}
    public final void execute(AbilityContext ctx) {execute(ctx,0);}
    public final void execute(AbilityContext ctx,int warning) {CombatService.execute(id,ctx,warning);}
    protected static ParamSpec number(String key,double value,double min,double max) {return new ParamSpec(key,ParamType.DOUBLE,value,min,max);}
    protected static ParamSpec integer(String key,int value,int min,int max) {return new ParamSpec(key,ParamType.INT,value,min,max);}
    protected static ParamSpec time(String key,int value,int min,int max) {return new ParamSpec(key,ParamType.TICKS,value,min,max);}
}
