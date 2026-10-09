package dev.dasan.customdungeons.ability.zone;

import dev.dasan.customdungeons.ability.*;
import java.util.List;
import org.bukkit.Material;

/** Intrinsic warning cannot be bypassed by zero telegraph or direct combo execution. */
public abstract class ZoneAbility implements Ability {
    private final String id;
    private final Material icon;
    private final List<ParamSpec> params;
    protected ZoneAbility(String id,Material icon,List<ParamSpec> params) {this.id=id;this.icon=icon;this.params=List.copyOf(params);}
    public final String id() {return id;}
    public final Material icon() {return icon;}
    public final List<ParamSpec> params() {return params;}
    /** Initial common GUI warning; each ability still enforces its intrinsic warning. */
    public int defaultTelegraphTicks() {return 20;}
    public final void execute(AbilityContext ctx) {execute(ctx,0);}
    public final void execute(AbilityContext ctx,int warning) {ZoneService.execute(id,ctx,warning);}
    protected static ParamSpec number(String k,double v,double min,double max) {return new ParamSpec(k,ParamType.DOUBLE,v,min,max);}
    protected static ParamSpec integer(String k,int v,int min,int max) {return new ParamSpec(k,ParamType.INT,v,min,max);}
    protected static ParamSpec time(String k,int v,int min,int max) {return new ParamSpec(k,ParamType.TICKS,v,min,max);}
}
