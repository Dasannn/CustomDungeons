package dev.dasan.customdungeons.gui.menu;

import dev.dasan.customdungeons.gui.*;
import dev.dasan.customdungeons.intelligence.*;
import java.util.*;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.*;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;

/** Shared template draft; phase fields override only level, weak point and bonus. */
public final class IntelligenceMenu extends MobMenuBase {
    private final MobMenu.PhaseDraft phase;
    private final String view;
    public IntelligenceMenu(Player p,MobMenu.MobDraft data,MobMenu.PhaseDraft phase,Menu parent) {this(p,data,phase,parent,"");}
    public IntelligenceMenu(Player p,MobMenu.MobDraft data,MobMenu.PhaseDraft phase,Menu parent,String view) {super(p,titleKey(view),data,parent);this.phase=phase;this.view=view;}
    private static String titleKey(String view) {return "intelligence"+(view.isEmpty()?"":"-"+view);}
    static Component m(String key,TagResolver... args) {return MenuListener.instance().messages().get("gui.intelligence."+key,args);}
    private static TagResolver arg(String key,Object value) {return Placeholder.unparsed(key,value.toString());}
    public static Component marker(IntelligenceDef d) {return m("list-marker",arg("level",d.level()),Placeholder.component("name",m("level-"+d.level()+"-name")));}
    static List<Component> markerLore(dev.dasan.customdungeons.model.MobTemplate mob) {return mob!=null&&mob.intelligence().level()>0?List.of(marker(mob.intelligence())):List.of();}
    private IntelligenceDef own() {return phase==null?data.intelligence:phase.intelligence;}
    public IntelligenceDef effective() {
        var d=data.intelligence;
        if(phase!=null)for(var p:data.phases){d=d.phase(p.intelligence);if(p==phase)break;}
        return d;
    }
    private boolean writable() {return permitted(viewer)&&!codeReadonly()&&(data.worldBoss==null||WorldBossMenu.allowed(viewer))&&!MenuListener.instance().rejectReload(viewer);}
    private void change(Consumer<IntelligenceDef> setter,IntelligenceDef value) {if(writable()){setter.accept(value);refresh();}}
    private void own(IntelligenceDef value) {change(d->{if(phase==null)data.intelligence=d;else phase.intelligence=d;},value);}
    @Override protected int preferredRows() {return view.isEmpty()?6:5;}
    @Override protected Material borderMaterial() {return phase==null?Material.PURPLE_STAINED_GLASS_PANE:Material.MAGENTA_STAINED_GLASS_PANE;}
    @Override protected void renderHeader() {
        var d=effective();var lore=new ArrayList<Component>();
        lore.add(marker(d));lore.add(m("weak-state",Placeholder.component("point",point(d.weakPoint())),arg("bonus",d.bonus())));lore.add(m("template-context",arg("id",data.id)));
        if(phase!=null)lore.add(m("phase-context",arg("phase",data.phases.indexOf(phase)+1),arg("threshold",formatValue(phase.threshold*100))));
        if(view.equals("advanced")) {
            lore.add(m("advanced-summary",arg("repetitions",d.value("repetitions")),arg("window",d.value("window")),arg("duration",d.value("duration"))));
            lore.add(m("advanced-summary-2",arg("cooldown",d.value("cooldown")),arg("maximum",d.maximum())));
        } else if(view.equals("adaptations"))lore.addAll(adaptationSummary());
        
        lore.addAll(data.validationErrors);
        set(4,GuiTheme.information(egg(data.type),MenuListener.instance().messages().get("gui.mob.summary",Placeholder.component("name",dev.dasan.customdungeons.text.Text.parse(data.name)),Placeholder.component("menu",m(view.isEmpty()?"title":view+"-title"))),lore));
        GuiTheme.help(this,java.util.stream.IntStream.rangeClosed(1,view.equals("advanced")?3:4).mapToObj(i->m(view.equals("advanced")?"advanced-help-"+i:view.equals("adaptations")?"adapt-help-"+i:"help-"+i)).toList());
    }
    @Override protected void render() {switch(view){case "advanced"->advanced();case "adaptations"->adaptations();default->main();}}
    private static Component point(IntelligenceDef.WeakPoint p) {return m("weak-value-"+p.name().toLowerCase(Locale.ROOT));}
    private void main() {
        var d=effective();set(13,GuiTheme.section(m("section-level"),List.of(m("level-range"))));
        int[] slots={19,20,21,23,24,25};
        for(int level=0;level<=5;level++) {
            final int n=level;var values=IntelligenceDef.level(n);boolean selected=phase==null?d.level()==n:Objects.equals(own().explicitLevel(),n);
            var lore=new ArrayList<Component>();
            int descriptions=n==0?1:n==5?3:2;for(int i=1;i<=descriptions;i++)lore.add(m("level-"+n+"-description-"+i));
            if(n>=1)lore.add(m("cumulative"));
            if(n>=2) {lore.add(m("level-detection",arg("repetitions",values.value("repetitions")),arg("window",values.value("window"))));lore.add(m("level-maximum",arg("maximum",values.maximum())));lore.add(m("level-times",arg("duration",values.value("duration")),arg("cooldown",values.value("cooldown"))));}
            else {lore.add(m("no-detection"));lore.add(m("no-maximum-"+n));lore.add(m("no-times"));}
            if(n==2)lore.add(m("level-2-counters"));lore.add(m("level-range"));lore.add(Component.empty());lore.add(m("choose-level"));if(phase!=null)lore.add(m("explicit-phase-level"));
            Component name=m("level-"+n,arg("level",n));if(selected)name=m("selected",Placeholder.component("name",name));
            var b=Button.of(Material.SCULK_SENSOR,name,lore,(p,c)->{if(c==ClickType.LEFT)own(own().withLevel(n));});b.icon().editMeta(meta->meta.setEnchantmentGlintOverride(selected));set(slots[n],b);
        }
        var weakLore=new ArrayList<>(List.of(m("weak-state",Placeholder.component("point",point(d.weakPoint())),arg("bonus",d.bonus())),m("weak-options"),m("weak-defense"),Component.empty(),m("weak-next"),m("weak-previous")));
        if(phase!=null){if(own().explicitWeakPoint()==null)weakLore.addFirst(m("weak-inherited"));weakLore.add(m("reset-weak"));}
        set(29,Button.of(Material.TARGET,m("weak-name",Placeholder.component("point",point(d.weakPoint()))),weakLore,(p,c)->{
            if(c==ClickType.SHIFT_RIGHT&&phase!=null)own(own().withWeakPoint(null));
            else if(c==ClickType.LEFT||c==ClickType.RIGHT) {var points=List.of(IntelligenceDef.WeakPoint.BACK,IntelligenceDef.WeakPoint.HEAD,IntelligenceDef.WeakPoint.NONE);own(own().withWeakPoint(points.get(Math.floorMod(points.indexOf(d.weakPoint())+(c==ClickType.LEFT?1:-1),3))));}
        }));
        var bonusLore=new ArrayList<>(List.of(m("bonus-current",arg("value",d.bonus())),m("bonus-default"),m("bonus-none"),rangeDescription(IntelligenceDef.range("bonus",d.level()),m("unit-percent")),m("integer-precision"),Component.empty(),m("number-edit"),m("number-clicks"),m(phase==null?"bonus-reset":"bonus-reset-phase")));
        set(31,Button.of(Material.IRON_SWORD,m("bonus-name",arg("value",d.bonus())),bonusLore,(p,c)->{
            if(!writable())return;if(c==ClickType.SHIFT_RIGHT){own(own().withBonus(null));return;}
            var range=IntelligenceDef.range("bonus",d.level());java.util.function.DoubleConsumer submit=v->{if(writable())own(own().withBonus((int)v));};
            MenuListener.instance().later(()->{if(c==ClickType.LEFT)NumericInputs.edit(p,m("bonus-name",arg("value",d.bonus())),range,d.bonus(),submit);else if(c==ClickType.RIGHT)NumericInputs.clicks(p,m("bonus-name",arg("value",d.bonus())),range,d.bonus(),submit);});
        }));
        if(phase==null)set(33,GuiTheme.section(m("cost-name"),List.of(m("cost-weak"),m("cost-none"))));
        else {boolean inherited=own().explicitLevel()==null;var b=Button.of(inherited?Material.LIME_DYE:Material.GRAY_DYE,m("inherit-name"),List.of(m("inherit-effective",arg("level",d.level())),m("inherit-rule"),m("inherit-only-level"),Component.empty(),m("inherit-click")),(p,c)->{if(c==ClickType.LEFT)own(own().withLevel(null));});b.icon().editMeta(meta->meta.setEnchantmentGlintOverride(inherited));set(33,b);}
        access(39,Material.COMPARATOR,"advanced",List.of(m("advanced-summary",arg("repetitions",d.value("repetitions")),arg("window",d.value("window")),arg("duration",d.value("duration"))),m("advanced-summary-2",arg("cooldown",d.value("cooldown")),arg("maximum",d.maximum())),m("advanced-shared"),Component.empty(),m("advanced-click")));
        access(41,Material.LIME_DYE,"adaptations",adaptationSummary());
    }
    private void access(int slot,Material material,String mode,List<Component> lore) {set(slot,Button.of(material,m(mode+"-access"),lore,(p,c)->{if(c==ClickType.LEFT)MenuListener.instance().later(()->new IntelligenceMenu(p,data,phase,this,mode).open());}));}
    private List<Component> adaptationSummary() {
        var rules=IntelligenceRules.defaults().all();var d=effective();long enabled=rules.stream().filter(r->!data.intelligence.disabled().contains(r.id())).count();long eligible=rules.stream().filter(r->r.minimumLevel()<=d.level()).count();long usable=rules.stream().filter(r->r.minimumLevel()<=d.level()&&!data.intelligence.disabled().contains(r.id())).count();
        return List.of(m("adaptations-summary",arg("enabled",enabled),arg("total",rules.size()),arg("usable",usable),arg("eligible",eligible)),m("adaptations-shared"),Component.empty(),m("adaptations-click"));
    }
    private void advanced() {
        set(11,GuiTheme.section(m("section-detection"),List.of(m("section-detection-lore"))));set(13,GuiTheme.section(m("section-limits"),List.of(m("section-limits-lore"))));set(15,GuiTheme.section(m("section-times"),List.of(m("section-times-lore"))));
        set(31,GuiTheme.section(m("openings-name"),List.of(m("openings-1"),m("openings-2"),m("openings-3"))));
        String[] fields={"window","repetitions","maximum","duration","cooldown"};int[] slots={20,29,22,24,33};Material[] icons={Material.CLOCK,Material.REPEATER,Material.ZOMBIE_HEAD,Material.CLOCK,Material.CLOCK};
        var d=effective();for(int i=0;i<fields.length;i++) {String key=fields[i];Component unit=List.of("window","duration","cooldown").contains(key)?m("unit-seconds"):Component.empty();int value=data.intelligence.advanced().getOrDefault(key,d.defaultValue(key));
            Component name=m("field-"+key,arg("value",value),Placeholder.component("unit",unit));
            if(d.level()<2){set(slots[i],GuiTheme.unavailable(m("field-"+key,arg("value","—"),arg("unit","")),m("no-advanced")));continue;}
            var lore=new ArrayList<Component>();boolean custom=data.intelligence.advanced().containsKey(key);
            lore.add(m(custom?"custom-value":"level-value",arg("value",value),Placeholder.component("unit",unit)));
            if(custom)lore.add(m("level-reference",arg("value",d.defaultValue(key)),Placeholder.component("unit",unit)));
            var range=IntelligenceDef.range(key,d.level());lore.add(rangeDescription(range,unit));lore.add(m("integer-precision"));
            if(key.equals("repetitions"))lore.add(m("repetitions-minimum"));if(key.equals("maximum"))lore.add(m("maximum-zero"));if(key.equals("window"))lore.add(m("window-bound"));
            boolean invalid=!range.contains(value);
            if(invalid)lore.add(m("number-error",Placeholder.component("field",name),arg("min",range.format(range.min())),arg("max",range.format(range.max())),Placeholder.component("unit",unit)));
            lore.add(Component.empty());lore.add(m("number-edit"));lore.add(m("number-clicks"));lore.add(m("reset-level"));
            set(slots[i],Button.of(invalid?Material.RED_DYE:icons[i],name,lore,(p,c)->{
                if(!writable())return;if(c==ClickType.SHIFT_RIGHT){change(v->data.intelligence=v,data.intelligence.withAdvanced(key,null));return;}
                java.util.function.DoubleConsumer submit=v->{if(writable())change(x->data.intelligence=x,data.intelligence.withAdvanced(key,(int)v));};
                MenuListener.instance().later(()->{if(c==ClickType.LEFT)NumericInputs.edit(p,name,range,value,submit);else if(c==ClickType.RIGHT)NumericInputs.clicks(p,name,range,value,submit);});
            }));
        }
    }
    private static Component rangeDescription(dev.dasan.customdungeons.config.NumericRange range,Component unit) {
        return m("range",arg("min",range.format(range.min())),arg("max",range.format(range.max())),Placeholder.component("unit",unit));
    }
    private static String catalogId(String id) {return switch(id){case "critical"->"criticals";case "damage"->"dominant-damage";case "pearl"->"pearls";default->id;};}
    private void adaptations() {
        set(13,GuiTheme.section(m("adapt-list-heading"),List.of(m("adapt-limit",arg("level",effective().level()),arg("maximum",effective().maximum())))));int[] slots={19,21,23,25,28,30,32,34};int i=0;
        for(var rule:IntelligenceRules.defaults().all()) {
            boolean enabled=!data.intelligence.disabled().contains(rule.id());var d=effective();var lore=new ArrayList<Component>();lore.add(m("adapt-state",Placeholder.component("state",m(enabled?"enabled":"disabled"))));lore.add(m("adapt-minimum",arg("level",rule.minimumLevel())));lore.add(m("adapt-"+catalogId(rule.id())+"-effect"));
            if(enabled&&d.level()<rule.minimumLevel())lore.add(m("adapt-unused",arg("minimum",rule.minimumLevel()),arg("level",d.level())));
            else lore.add(m(enabled?"adapt-eligible":d.level()>=rule.minimumLevel()?"adapt-off":"adapt-kept"));
            if(rule.strong())lore.add(m("strong-warning"));if(rule.id().equals("damage"))lore.add(m("damage-caps"));lore.add(m("adapt-tradeoff"));lore.add(Component.empty());lore.add(m("adapt-toggle"));
            set(slots[i++],Button.of(enabled?Material.LIME_DYE:Material.GRAY_DYE,m("adapt-name",Placeholder.component("name",m("adapt-"+catalogId(rule.id()))),Placeholder.component("state",m(enabled?"enabled":"disabled"))),lore,(p,c)->{if(c==ClickType.LEFT)change(v->data.intelligence=v,data.intelligence.toggle(rule.id()));}));
        }
    }
    @Override protected void renderFooter() {
        int slot=getInventory().getSize()-5;var lore=new ArrayList<Component>();if(hasUnsavedChanges())lore.add(MenuListener.instance().messages().get("gui.common.unsaved"));lore.add(Component.empty());lore.add(m("save-template"));
        var b=Button.of(Material.LIME_CONCRETE,MenuListener.instance().messages().get("gui.common.save"),lore,(p,c)->{if(c==ClickType.LEFT&&writable())save();});b.icon().editMeta(meta->meta.setEnchantmentGlintOverride(hasUnsavedChanges()));set(slot,b);
    }
}
