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
    @Override protected void render() {
        stat(11,"health",Material.APPLE,data.health,2048,1,v->data.health=v);
        stat(12,"damage",Material.DIAMOND_SWORD,data.damage,1000,1,v->data.damage=v);
        stat(13,"speed",Material.SUGAR,data.speed,1,2,v->data.speed=v);
        stat(14,"resistance",Material.SHIELD,data.resistance,1,2,v->data.resistance=v);
        stat(15,"scale",Material.SLIME_BLOCK,data.scale,4,2,v->data.scale=v);
    }
    static double validateStat(String key,double value) {
        if(value!=0 && ((key.equals("health") && value<1) || (key.equals("scale") && value<0.1)))
            throw new IllegalArgumentException("Below minimum stat");
        return value;
    }
    private void stat(int slot,String key,Material icon,double value,double max,int decimals,DoubleConsumer submit) {
        action(slot,icon,key,Inputs.formatNumber(value,decimals),()->Inputs.decimal(viewer,label(key,Inputs.formatNumber(value,decimals)),0,max,
                Math.clamp(value,0,max),decimals,n->{
                    try {submit.accept(validateStat(key,n));}
                    catch(IllegalArgumentException invalid) {MenuListener.instance().messages().send(viewer,"gui.mob.invalid-stat");}
                }));
    }
}
