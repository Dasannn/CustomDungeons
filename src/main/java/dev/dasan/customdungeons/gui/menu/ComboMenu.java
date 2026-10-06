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

public final class ComboMenu extends MobMenuBase {
    private final MobMenu.Loadout loadout;
    private final int index;
    public ComboMenu(Player p, MobMenu.MobDraft d, MobMenu.Loadout l, int index, Menu parent) {
        super(p,"combos",d,parent); loadout=l; this.index=index;
    }
    public static boolean validSteps(List<ComboStep> steps) { return steps.size() >= 2 && steps.size() <= 5; }
    public static Menu list(Player p, MobMenu.MobDraft d, MobMenu.Loadout l, Menu parent) {
        return new MobMenuBase(p,"combos",d,parent) {
            @Override protected void render() {
                action(4,"add","",() -> new AbilityPickerMenu(p,this,a -> {
                    var step = new ComboStep(a.id(),defaults(a).params(),0);
                    l.combos.add(new ComboDef("combo_"+UUID.randomUUID().toString().substring(0,8),Trigger.EVERY_X_SECONDS,5,TargetMode.NEAREST,16,100,List.of(step,step)));
                }).open());
                var buttons = new ArrayList<Button>();
                for (int i=0; i<l.combos.size();i++) { final int n=i;
                    buttons.add(entry(Material.IRON_CHAIN,l.combos.get(i).id(),() -> new ComboMenu(p,d,l,n,this).open(),() -> l.combos.remove(n)));
                }
                entries(buttons);
            }
        };
    }
    private ComboDef current() { return loadout.combos.get(index); }
    private void update(String id, Trigger trigger, double tv, TargetMode target, double range, int cooldown, List<ComboStep> steps) {
        loadout.combos.set(index,new ComboDef(id,trigger,tv,target,range,cooldown,steps));
    }
    private void steps(Consumer<List<ComboStep>> edit) {
        var c=current(); var steps=new ArrayList<>(c.steps()); edit.accept(steps);
        if (validSteps(steps)) update(c.id(),c.trigger(),c.triggerValue(),c.target(),c.range(),c.cooldownTicks(),steps);
    }
    @Override protected void render() {
        ComboDef c=current();
        text(10,"combo-id",c.id(),v -> update(v,c.trigger(),c.triggerValue(),c.target(),c.range(),c.cooldownTicks(),c.steps()));
        select(11,"trigger",c.trigger().name(),Arrays.stream(Trigger.values()).map(Enum::name).toList(),v -> update(c.id(),Trigger.valueOf(v),c.triggerValue(),c.target(),c.range(),c.cooldownTicks(),c.steps()));
        number(12,"trigger-value",c.triggerValue(),0,3600,v -> update(c.id(),c.trigger(),v,c.target(),c.range(),c.cooldownTicks(),c.steps()));
        select(13,"target",c.target().name(),Arrays.stream(TargetMode.values()).map(Enum::name).toList(),v -> update(c.id(),c.trigger(),c.triggerValue(),TargetMode.valueOf(v),c.range(),c.cooldownTicks(),c.steps()));
        number(14,"range",c.range(),0,256,v -> update(c.id(),c.trigger(),c.triggerValue(),c.target(),v,c.cooldownTicks(),c.steps()));
        number(15,"cooldown",c.cooldownTicks(),0,72000,v -> update(c.id(),c.trigger(),c.triggerValue(),c.target(),c.range(),(int)v,c.steps()));
        if (c.steps().size()<5) action(16,"add-step","",() -> new AbilityPickerMenu(viewer,this,a -> steps(s -> s.add(new ComboStep(a.id(),defaults(a).params(),0)))).open());
        for (int i=0;i<c.steps().size();i++) {
            final int n=i; ComboStep step=c.steps().get(i);
            set(19+i,Button.of(registry().get(step.abilityId()).map(Ability::icon).orElse(Material.BARRIER),label("step",(i+1)+": "+step.abilityId()),
                List.of(message("step-lore")),(p,click) -> MenuListener.instance().later(() -> {
                    if (click.isShiftClick() && click.isRightClick()) { steps(s -> s.remove(n)); refresh(); }
                    else if (click.isShiftClick()) { if(n>0) steps(s -> Collections.swap(s,n,n-1)); refresh(); }
                    else if (click.isRightClick()) new AbilityPickerMenu(p,this,a -> steps(s -> s.set(n,new ComboStep(a.id(),defaults(a).params(),step.delayTicks())))).open();
                    else new ParamEditorMenu(p,data,new AbilityInstance(step.abilityId(),c.trigger(),c.triggerValue(),c.target(),c.range(),c.cooldownTicks(),1,0,step.params()),this,
                        a -> steps(s -> s.set(n,new ComboStep(a.abilityId(),a.params(),s.get(n).delayTicks()))),false).open();
                })));
            number(28+i,"delay",step.delayTicks(),0,72000,v -> steps(s -> s.set(n,new ComboStep(step.abilityId(),step.params(),(int)v))));
        }
    }
}
