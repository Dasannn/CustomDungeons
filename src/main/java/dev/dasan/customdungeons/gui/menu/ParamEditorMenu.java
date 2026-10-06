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

/** ParamSpec drives the editor; new registered abilities require no GUI changes. */
public final class ParamEditorMenu extends MobMenuBase {
    private AbilityInstance value;
    private final Consumer<AbilityInstance> changed;
    private final boolean common;
    public ParamEditorMenu(Player p, MobMenu.MobDraft d, AbilityInstance value, Menu parent, Consumer<AbilityInstance> changed) {
        this(p, d, value, parent, changed, true);
    }
    public ParamEditorMenu(Player p, MobMenu.MobDraft d, AbilityInstance value, Menu parent, Consumer<AbilityInstance> changed, boolean common) {
        super(p, "parameters", d, parent); this.value = value; this.changed = changed; this.common = common;
    }
    private void replace(Trigger trigger, double tv, TargetMode target, double range, int cooldown, double chance, int telegraph, Map<String,Object> params) {
        value = new AbilityInstance(value.abilityId(), trigger, tv, target, range, cooldown, chance, telegraph, params); changed.accept(value);
    }
    private void field(String key, Object v) {
        var a = value;
        replace(key.equals("trigger") ? Trigger.valueOf(v.toString()) : a.trigger(),
                key.equals("trigger-value") ? ((Number)v).doubleValue() : a.triggerValue(),
                key.equals("target") ? TargetMode.valueOf(v.toString()) : a.target(),
                key.equals("range") ? ((Number)v).doubleValue() : a.range(),
                key.equals("cooldown") ? ((Number)v).intValue() : a.cooldownTicks(),
                key.equals("chance") ? ((Number)v).doubleValue() : a.chance(),
                key.equals("telegraph") ? ((Number)v).intValue() : a.telegraphTicks(), a.params());
    }
    private void param(String key, Object v) {
        var params = new LinkedHashMap<>(value.params()); params.put(key, v);
        replace(value.trigger(), value.triggerValue(), value.target(), value.range(), value.cooldownTicks(), value.chance(), value.telegraphTicks(), params);
    }
    @Override protected void render() {
        var buttons = new ArrayList<Button>();
        if (common) {
            buttons.add(parameter("trigger", value.trigger(), () -> choose(viewer, "trigger", Arrays.stream(Trigger.values()).map(Enum::name).toList(), this, v -> field("trigger",v))));
            buttons.add(parameter("target", value.target(), () -> choose(viewer, "target", Arrays.stream(TargetMode.values()).map(Enum::name).toList(), this, v -> field("target",v))));
            for (String key : List.of("trigger-value", "range", "cooldown", "chance", "telegraph")) {
                double current = switch(key) { case "trigger-value" -> value.triggerValue(); case "range" -> value.range(); case "cooldown" -> value.cooldownTicks(); case "chance" -> value.chance(); default -> value.telegraphTicks(); };
                buttons.add(parameter(key, current, () -> Inputs.number(viewer, message(key), 0, key.equals("chance") ? 1 : 72000, current, v -> field(key,v))));
            }
        }
        registry().get(value.abilityId()).ifPresent(a -> a.params().forEach(spec -> {
            Object current = value.params().getOrDefault(spec.key(), spec.defaultValue());
            buttons.add(Button.of(Material.PAPER, label("parameter", spec.key() + " = " + current),
                List.of(message("parameter-lore")), (p,c) -> MenuListener.instance().later(() -> edit(spec,current))));
        }));
        entries(buttons);
    }
    private Button parameter(String key, Object v, Runnable edit) {
        return Button.of(Material.PAPER, label(key,v), List.of(message(key+"-lore")), (p,c) -> MenuListener.instance().later(edit));
    }
    private void edit(ParamSpec spec, Object current) {
        switch(spec.type()) {
            case INT, TICKS, DOUBLE -> Inputs.number(viewer, label("parameter",spec.key()), spec.min(),spec.max(), ((Number)current).doubleValue(),
                v -> { if (spec.type() == ParamType.DOUBLE) param(spec.key(),v); else param(spec.key(),(int)v); });
            case BOOLEAN -> { param(spec.key(), !Boolean.parseBoolean(current.toString())); refresh(); }
            case POTION_EFFECT -> choose(viewer,"potions",potionKeys(),this,v -> param(spec.key(),v));
            case PARTICLE -> choose(viewer,"particle",Arrays.stream(Particle.values()).map(Enum::name).toList(),this,v -> param(spec.key(),v));
            case SOUND -> choose(viewer,"sound",soundKeys(),this,v -> param(spec.key(),v));
            case MOB_TEMPLATE -> choose(viewer,"template",store().mobs().keySet().stream().sorted().toList(),this,v -> param(spec.key(),v));
            case STRING -> Inputs.text(viewer,label("parameter",spec.key()),current.toString(),256,v -> param(spec.key(),v));
        }
    }
}
