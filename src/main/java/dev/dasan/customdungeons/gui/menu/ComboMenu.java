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
            @Override protected int contentCount() { return l.combos.size(); }
            @Override protected MobMenu.Loadout summaryLoadout() { return l; }
            @Override protected void renderFooter() {
                action(getInventory().getSize()-7,Material.LIME_DYE,"add","",() -> new AbilityPickerMenu(p,this,first ->
                    new AbilityPickerMenu(p,this,second -> {
                        var steps=List.of(new ComboStep(first.id(),defaults(first).params(),0),
                                new ComboStep(second.id(),defaults(second).params(),0));
                        l.combos.add(new ComboDef("combo_"+UUID.randomUUID().toString().substring(0,8),
                                Trigger.EVERY_X_SECONDS,5,TargetMode.NEAREST,16,100,steps));
                    }).open(),false).open());
            }
            @Override protected void render() {
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
    private void steps(Consumer<Editor> edit) {
        var c=current(); var editor=new Editor(c.steps()); edit.accept(editor);
        update(c.id(),c.trigger(),c.triggerValue(),c.target(),c.range(),c.cooldownTicks(),editor.steps());
    }
    /** Detached editor model; shared records remain unchanged. */
    public static final class Editor {
        private final List<ComboStep> steps;
        public Editor(List<ComboStep> steps) {
            if (!validSteps(steps)) throw new IllegalArgumentException("Expected 2 to 5 steps");
            this.steps=new ArrayList<>(steps);
        }
        public List<ComboStep> steps() { return List.copyOf(steps); }
        public boolean add(ComboStep step) { if(steps.size()==5) return false; steps.add(step); return true; }
        public boolean remove(int index) { if(steps.size()==2) return false; steps.remove(index); return true; }
        public void replace(int index,ComboStep step) { steps.set(index,step); }
        public void move(int from,int to) { Objects.checkIndex(from,steps.size()); Objects.checkIndex(to,steps.size()); var step=steps.remove(from); steps.add(to,step); }
    }
    public static int delayTicks(double seconds) {
        if (!Double.isFinite(seconds) || seconds<0 || seconds>3600) throw new IllegalArgumentException("Invalid delay");
        return (int)Math.round(seconds*10)*2;
    }
    public static OptionalInt parseDelay(String input) {
        try { return OptionalInt.of(delayTicks(Double.parseDouble(input.strip().replace(',','.')))); }
        catch(IllegalArgumentException error) { return OptionalInt.empty(); }
    }
    public static String delaySeconds(int ticks) { return String.format(Locale.ROOT,"%.1f",ticks/20.0); }
    @Override protected void render() {
        ComboDef c=current();
        section(13,"section-combo",Material.WHITE_STAINED_GLASS_PANE);
        text(19,"combo-id",c.id(),v -> update(v,c.trigger(),c.triggerValue(),c.target(),c.range(),c.cooldownTicks(),c.steps()));
        select(20,"trigger",c.trigger().name(),Arrays.stream(Trigger.values()).map(Enum::name).toList(),v -> update(c.id(),Trigger.valueOf(v),c.triggerValue(),c.target(),c.range(),c.cooldownTicks(),c.steps()));
        number(21,"trigger-value",c.triggerValue(),0,3600,v -> update(c.id(),c.trigger(),v,c.target(),c.range(),c.cooldownTicks(),c.steps()));
        select(22,"target",c.target().name(),Arrays.stream(TargetMode.values()).map(Enum::name).toList(),v -> update(c.id(),c.trigger(),c.triggerValue(),TargetMode.valueOf(v),c.range(),c.cooldownTicks(),c.steps()));
        number(23,"range",c.range(),0,256,v -> update(c.id(),c.trigger(),c.triggerValue(),c.target(),v,c.cooldownTicks(),c.steps()));
        number(24,"cooldown",c.cooldownTicks(),0,72000,v -> update(c.id(),c.trigger(),c.triggerValue(),c.target(),c.range(),(int)v,c.steps()));
        if (c.steps().size()<5) action(25,Material.LIME_DYE,"add-step","",() -> new AbilityPickerMenu(viewer,this,a -> steps(s -> s.add(new ComboStep(a.id(),defaults(a).params(),0)))).open());
        else section(25,"combo-full",Material.GRAY_DYE);
        for (int i=0;i<c.steps().size();i++) {
            final int n=i; ComboStep step=c.steps().get(i);
            set(GuiLayout.centeredRow(3,c.steps().size()).get(i),Button.of(registry().get(step.abilityId()).map(MobMenuBase::abilityIcon).orElse(Material.BARRIER),MenuListener.instance().messages().get("gui.mob.combo-step",Placeholder.unparsed("number",Integer.toString(i+1)),Placeholder.component("ability",abilityName(step.abilityId()))),
                List.of(message("step-lore")),(p,click) -> MenuListener.instance().later(() -> {
                    if (click.isShiftClick() && click.isRightClick()) { steps(s -> s.remove(n)); refresh(); }
                    else if (click.isShiftClick()) { if(n>0) steps(s -> s.move(n,n-1)); refresh(); }
                    else if (click.isRightClick()) new AbilityPickerMenu(p,this,a -> steps(s -> s.replace(n,new ComboStep(a.id(),defaults(a).params(),step.delayTicks())))).open();
                    else new ParamEditorMenu(p,data,new AbilityInstance(step.abilityId(),c.trigger(),c.triggerValue(),c.target(),c.range(),c.cooldownTicks(),1,0,step.params()),this,
                        a -> steps(s -> s.replace(n,new ComboStep(a.abilityId(),a.params(),s.steps().get(n).delayTicks()))),false).open();
                })));
            set(GuiLayout.centeredRow(4,c.steps().size()).get(i), Button.of(Material.CLOCK, label("step-delay", delaySeconds(step.delayTicks())),
                    List.of(message("step-delay-lore"), message("step-delay-clicks")), (p, click) -> MenuListener.instance().later(() -> {
                if (click.isRightClick()) { if (n + 1 < c.steps().size()) { steps(s -> s.move(n, n + 1)); refresh(); } return; }
                Inputs.text(viewer,message("step-delay"),delaySeconds(step.delayTicks()),16,input -> {
                    var delay=parseDelay(input);
                    if(delay.isEmpty()) { MenuListener.instance().messages().send(viewer,"gui.mob.invalid-delay"); return; }
                    steps(s -> { var latest=s.steps().get(n); s.replace(n,new ComboStep(latest.abilityId(),latest.params(),delay.getAsInt())); });
                });
            })));
        }
    }
}
