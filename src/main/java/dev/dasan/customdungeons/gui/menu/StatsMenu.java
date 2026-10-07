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
    private final MobMenu.PhaseDraft phase;
    static final int[] POSITIONS = {19,20,21,22,23,24,25,28,29,30,32,33,34};
    public StatsMenu(Player p, MobMenu.MobDraft d, Menu parent) { this(p,d,null,parent); }
    public StatsMenu(Player p, MobMenu.MobDraft d, MobMenu.PhaseDraft phase, Menu parent) {
        super(p,"stats",d,parent); this.phase=phase;
    }
    @Override protected int preferredRows() { return 5; }
    @Override protected Material borderMaterial() {
        return phase==null ? Material.PURPLE_STAINED_GLASS_PANE : Material.MAGENTA_STAINED_GLASS_PANE;
    }
    @Override protected void render() {
        section(13,"section-stats",Material.WHITE_STAINED_GLASS_PANE);
        for (int i=0;i<MobAttributes.KEYS.size();i++) attribute(POSITIONS[i],MobAttributes.KEYS.get(i));
    }
    private Map<String,Double> overrides() { return phase==null ? data.attributes : phase.attributes; }
    private Double configured(String key) {
        if (overrides().containsKey(key)) return overrides().get(key);
        if (phase!=null) return null;
        double old=switch(key) {
            case "max-health" -> data.health; case "damage" -> data.damage; case "speed" -> data.speed;
            case "knockback-resistance" -> data.resistance; case "scale" -> data.scale; default -> 0;
        };
        return old==0 ? null : old;
    }
    private void reset(String key) {
        overrides().remove(key);
        if (phase==null) switch(key) {
            case "max-health" -> data.health=0; case "damage" -> data.damage=0; case "speed" -> data.speed=0;
            case "knockback-resistance" -> data.resistance=0; case "scale" -> data.scale=0; default -> {}
        }
    }
    private void attribute(int slot,String key) {
        var range=NumericRanges.attribute(key);
        String labelKey=switch(key) {case "max-health" -> "health";case "knockback-resistance" -> "resistance";default -> key;};
        Material icon=switch(key) {
            case "max-health" -> Material.APPLE;case "damage" -> Material.IRON_SWORD;case "speed" -> Material.SUGAR;
            case "knockback-resistance" -> Material.SHIELD;case "scale" -> Material.SLIME_BALL;
            case "armor" -> Material.IRON_CHESTPLATE;case "armor-toughness" -> Material.DIAMOND_CHESTPLATE;
            case "follow-range" -> Material.COMPASS;case "attack-knockback" -> Material.PISTON;
            case "jump-strength" -> Material.RABBIT_FOOT;case "gravity" -> Material.FEATHER;
            case "step-height" -> Material.QUARTZ_STAIRS;case "explosion-knockback-resistance" -> Material.TNT;
            default -> throw new IllegalArgumentException("Unknown attribute");
        };
        Double value=configured(key);
        Component shown=value==null ? message(phase==null ? "attribute-vanilla" : "attribute-inherit") : Component.text(formatValue(value));
        var lore=new ArrayList<Component>();lore.add(message(labelKey+"-lore"));
        if(key.equals("scale") && value!=null && value>NumericRanges.SCALE_WARNING_THRESHOLD)
            lore.add(MenuListener.instance().messages().get("validation.scale-high"));
        lore.add(Component.empty());lore.add(message("attribute-edit"));lore.add(message("attribute-reset"));
        Button button=Button.of(value!=null && !range.contains(value) ? Material.RED_DYE : icon,label(labelKey,shown),lore,(p,c)->MenuListener.instance().later(()->{
            if(c.isShiftClick() && c.isRightClick()) {reset(key);refresh();return;}
            NumericInputs.edit(viewer,message(labelKey),range,value==null?Math.max(1,range.min()):value,
                    n->{overrides().put(key,n);refresh();});
        }));
        set(slot,NumericInputs.decorate(button,range));
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
}
