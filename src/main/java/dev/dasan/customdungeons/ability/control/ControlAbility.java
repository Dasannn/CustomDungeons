package dev.dasan.customdungeons.ability.control;

import dev.dasan.customdungeons.ability.*;
import java.util.List;
import org.bukkit.Material;

/** Intrinsic warning is also used by direct calls/combos. The engine supplies its configured warning. */
public abstract class ControlAbility implements Ability {
    private final String id;private final Material icon;private final List<ParamSpec> params;
    protected ControlAbility(String id,Material icon,List<ParamSpec> params){this.id=id;this.icon=icon;this.params=List.copyOf(params);}
    public final String id(){return id;}public final Material icon(){return icon;}public final List<ParamSpec> params(){return params;}
    public final void execute(AbilityContext ctx){execute(ctx,20);}
    public final void execute(AbilityContext ctx,int warning){ControlService.execute(id,ctx,Math.max(20,warning));}
    protected static ParamSpec number(String k,double v,double min,double max){return new ParamSpec(k,ParamType.DOUBLE,v,min,max);}
    protected static ParamSpec integer(String k,int v,int min,int max){return new ParamSpec(k,ParamType.INT,v,min,max);}
    protected static ParamSpec ticks(int v,int min,int max){return new ParamSpec("ticks",ParamType.TICKS,v,min,max);}
}
