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
    @Override protected boolean showEntryHeading() { return false; }
    @Override protected int contentCount() { return (common ? 7 : 0) + registry().get(value.abilityId()).map(a -> a.params().size()).orElse(0); }
    @Override protected void renderHeader() {
        super.renderHeader();
        set(4, GuiTheme.information(registry().get(value.abilityId()).map(MobMenuBase::abilityIcon).orElse(Material.RED_DYE),
                registry().get(value.abilityId()).isPresent() ? abilityName(value.abilityId()) : label("ability-missing", value.abilityId()),
                List.of(message("parameters-lore"))));
    }
    @Override protected void render() {
        var buttons = new ArrayList<Button>();
        if (common) {
            buttons.add(parameter("trigger", value.trigger(), () -> choose(viewer, "trigger", Arrays.stream(Trigger.values()).map(Enum::name).toList(), this, v -> field("trigger",v))));
            buttons.add(parameter("target", value.target(), () -> choose(viewer, "target", Arrays.stream(TargetMode.values()).map(Enum::name).toList(), this, v -> field("target",v))));
            for (String key : List.of("trigger-value", "range", "cooldown", "chance", "telegraph")) {
                double current = switch(key) { case "trigger-value" -> value.triggerValue(); case "range" -> value.range(); case "cooldown" -> value.cooldownTicks(); case "chance" -> value.chance(); default -> value.telegraphTicks(); };
                buttons.add(parameter(key, current, () -> NumericInputs.edit(viewer, message(key), NumericRanges.common(key), current, v -> field(key,v))));
            }
        }
        registry().get(value.abilityId()).ifPresent(a -> a.params().forEach(spec -> {
            Object current = value.params().getOrDefault(spec.key(), spec.defaultValue());
            var button=Button.of(switch(spec.type()) { case POTION_EFFECT -> Material.POTION; case SOUND -> Material.MUSIC_DISC_CAT; case PARTICLE -> Material.FIREWORK_ROCKET; case MOB_TEMPLATE -> Material.SPAWNER; case BOOLEAN -> GuiTheme.toggleIcon(Boolean.parseBoolean(current.toString())); case TICKS -> Material.CLOCK; default -> Material.COMPARATOR; }, label("parameter", parameterName(spec) + " = " + (current instanceof Number n ? formatValue(n) : current instanceof Boolean flag ? net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(displayValue(spec.key(),flag)) : current)),
                List.of(message("parameter-lore"),message("action-"+switch(spec.type()) { case BOOLEAN -> "toggle"; case POTION_EFFECT, SOUND, PARTICLE, MOB_TEMPLATE -> "choose"; default -> "write"; })), (p,c) -> MenuListener.instance().later(() -> edit(spec,current)));
            buttons.add(NumericRanges.numeric(spec) ? NumericInputs.decorate(button,NumericRanges.parameter(spec)) : button);
        }));
        entries(buttons);
        if (registry().get(value.abilityId()).isEmpty()) {
            set(13, GuiTheme.unavailable(label("ability-missing", value.abilityId()), message("ability-missing-lore")));
        } else if (common) {
            section(13,"section-ability-parameters",Material.WHITE_STAINED_GLASS_PANE);
        } else section(13,"section-specific",Material.WHITE_STAINED_GLASS_PANE);
    }
    private Button parameter(String key, Object v, Runnable edit) {
        var button=Button.of(icon(key), label(key,v), List.of(message(key+"-lore"),message(key.equals("trigger") || key.equals("target") ? "action-choose" : "action-write")), (p,c) -> MenuListener.instance().later(edit));
        return v instanceof Number ? NumericInputs.decorate(button,NumericRanges.common(key)) : button;
    }
    private String parameterName(ParamSpec spec) {
        if(registry().get(value.abilityId()).orElse(null) instanceof dev.dasan.customdungeons.ability.zone.ZoneAbility)
            return net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                    .serialize(MenuListener.instance().messages().get("zone.parameters."+spec.key()));
        return spec.key();
    }
    private void edit(ParamSpec spec, Object current) {
        switch(spec.type()) {
            case INT, TICKS, DOUBLE -> NumericInputs.edit(viewer, label("parameter",parameterName(spec)), NumericRanges.parameter(spec), ((Number)current).doubleValue(),
                v -> { if (spec.type() == ParamType.DOUBLE) param(spec.key(),v); else param(spec.key(),(int)v); });
            case BOOLEAN -> { param(spec.key(), !Boolean.parseBoolean(current.toString())); refresh(); }
            case POTION_EFFECT -> choose(viewer,"potions",potionKeys(),this,v -> param(spec.key(),v));
            case PARTICLE -> choose(viewer,"particle",Arrays.stream(Particle.values()).map(Enum::name).toList(),this,v -> param(spec.key(),v));
            case SOUND -> choose(viewer,"sound",soundKeys(),this,v -> param(spec.key(),v));
            case MOB_TEMPLATE -> choose(viewer,"template",store().mobs().keySet().stream().sorted().toList(),this,v -> param(spec.key(),v));
            case STRING -> Inputs.text(viewer,label("parameter",parameterName(spec)),current.toString(),256,v -> param(spec.key(),v));
        }
    }
}
