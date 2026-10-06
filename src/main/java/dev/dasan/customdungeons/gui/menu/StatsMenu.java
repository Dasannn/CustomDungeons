package dev.dasan.customdungeons.gui.menu;

import dev.dasan.customdungeons.gui.*;
import dev.dasan.customdungeons.model.*;
import dev.dasan.customdungeons.ability.*;
import java.util.*;
import java.util.function.*;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.*;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

public final class StatsMenu extends MobMenuBase {
    public StatsMenu(Player p, MobMenu.MobDraft d, Menu parent) { super(p, "stats", d, parent); }
    @Override protected int preferredRows() { return 4; }
    @Override protected void render() {
        section(13,"section-stats",Material.WHITE_STAINED_GLASS_PANE);
        stat(19,"health",Material.APPLE,data.health,2048,1,v->data.health=v);
        stat(20,"damage",Material.IRON_SWORD,data.damage,1000,1,v->data.damage=v);
        stat(21,"speed",Material.SUGAR,data.speed,1,2,v->data.speed=v);
        stat(23,"resistance",Material.SHIELD,data.resistance,1,2,v->data.resistance=v);
        stat(24,"scale",Material.SLIME_BALL,data.scale,10,2,v->data.scale=v);
        action(25,Material.ANVIL,"clamp-stats","",()->clampStats(data));
    }
    static double minimum(String key) {
        return switch(key) { case "health" -> 1; default -> 0; };
    }
    static double maximum(String key) {
        return switch(key) { case "health" -> 2048; case "damage" -> 1000; case "scale" -> 10; default -> 1; };
    }
    static double validateStat(String key,double value) {
        if(!dev.dasan.customdungeons.config.Validator.validStat(value,minimum(key),maximum(key)))
            throw new IllegalArgumentException("Out of range stat");
        return value;
    }
    static double clampStat(String key,double value) {
        if(value == 0) return 0;
        if(Double.isNaN(value)) return 0;
        return Math.clamp(value,minimum(key),maximum(key));
    }
    static void clampStats(MobMenu.MobDraft draft) {
        draft.health=clampStat("health",draft.health);
        draft.damage=clampStat("damage",draft.damage);
        draft.speed=clampStat("speed",draft.speed);
        draft.resistance=clampStat("resistance",draft.resistance);
        draft.scale=clampStat("scale",draft.scale);
    }
    private void stat(int slot,String key,Material icon,double value,double max,int decimals,DoubleConsumer submit) {
        String formatted=Double.isFinite(value) ? formatValue(new java.math.BigDecimal(Inputs.formatNumber(value,decimals))) : Double.toString(value);
        Runnable edit=()->Inputs.decimal(viewer,label(key,formatted),0,max,
                clampStat(key,value),decimals,n->{
                    try {submit.accept(validateStat(key,n));}
                    catch(IllegalArgumentException invalid) {MenuListener.instance().messages().send(viewer,"gui.mob.invalid-stat");}
                });
        if(dev.dasan.customdungeons.config.Validator.validStat(value,minimum(key),max))
            action(slot,icon,key,formatted,edit);
        else {
            var messages=MenuListener.instance().messages();
            set(slot,Button.of(Material.RED_DYE,messages.get("gui.mob.stat-invalid",
                    Placeholder.component("name",messages.get("validation.field."+switch(key) { case "health" -> "max-health"; case "resistance" -> "knockback-resistance"; default -> key; })),
                    Placeholder.unparsed("value",formatted)),List.of(messages.get("gui.mob.stat-range",
                    Placeholder.unparsed("min",Inputs.formatNumber(minimum(key),decimals)),
                    Placeholder.unparsed("max",Inputs.formatNumber(max,decimals))),message(key+"-lore"),message("click-lore")),
                    (p,c)->MenuListener.instance().later(edit)));
        }
    }
}
