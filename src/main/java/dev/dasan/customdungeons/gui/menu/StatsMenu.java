package dev.dasan.customdungeons.gui.menu;

import dev.dasan.customdungeons.gui.*;
import dev.dasan.customdungeons.config.NumericRanges;
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
        stat(19,"health",Material.APPLE,data.health,v->data.health=v);
        stat(20,"damage",Material.IRON_SWORD,data.damage,v->data.damage=v);
        stat(21,"speed",Material.SUGAR,data.speed,v->data.speed=v);
        stat(23,"resistance",Material.SHIELD,data.resistance,v->data.resistance=v);
        stat(24,"scale",Material.SLIME_BALL,data.scale,v->data.scale=v);
        action(25,Material.ANVIL,"clamp-stats","",()->clampStats(data));
    }
    static double minimum(String key) { return NumericRanges.stat(key).min(); }
    static double maximum(String key) { return NumericRanges.stat(key).max(); }
    static double validateStat(String key,double value) {
        if(!NumericRanges.stat(key).contains(value)) throw new IllegalArgumentException("Out of range stat");
        return value;
    }
    static double clampStat(String key,double value) { return NumericRanges.stat(key).clamp(value); }
    static void clampStats(MobMenu.MobDraft draft) {
        draft.health=clampStat("health",draft.health);
        draft.damage=clampStat("damage",draft.damage);
        draft.speed=clampStat("speed",draft.speed);
        draft.resistance=clampStat("resistance",draft.resistance);
        draft.scale=clampStat("scale",draft.scale);
    }
    private void stat(int slot,String key,Material icon,double value,DoubleConsumer submit) {
        var range=NumericRanges.stat(key);
        String formatted=Double.isFinite(value) ? formatValue(new java.math.BigDecimal(Inputs.formatNumber(value,range.decimals()))) : Double.toString(value);
        Runnable edit=()->NumericInputs.edit(viewer,label(key,formatted),range,value,n->{
            try {submit.accept(validateStat(key,n));}
            catch(IllegalArgumentException invalid) {MenuListener.instance().messages().send(viewer,"gui.mob.invalid-stat");}
        });
        Button button;
        if(range.contains(value)) button=actionButton(icon,key,formatted,"write",edit);
        else {
            var messages=MenuListener.instance().messages();
            button=Button.of(Material.RED_DYE,messages.get("gui.mob.stat-invalid",
                    Placeholder.component("name",messages.get("validation.field."+switch(key) { case "health" -> "max-health"; case "resistance" -> "knockback-resistance"; default -> key; })),
                    Placeholder.unparsed("value",formatted)),List.of(message(key+"-lore"),message("click-lore")),
                    (p,c)->MenuListener.instance().later(edit));
        }
        if(key.equals("scale") && range.contains(value) && value>NumericRanges.SCALE_WARNING_THRESHOLD)
            button.icon().editMeta(meta->{var lore=new ArrayList<>(meta.lore());
                lore.add(MenuListener.instance().messages().get("validation.scale-high"));meta.lore(lore);});
        set(slot,NumericInputs.decorate(button,range));
    }
}
