package dev.dasan.customdungeons.gui.menu;

import dev.dasan.customdungeons.gui.*;
import dev.dasan.customdungeons.config.*;
import dev.dasan.customdungeons.model.*;
import dev.dasan.customdungeons.boss.WorldBossService;
import java.util.*;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.*;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;

/** The world section edits the root mob draft and never publishes implicitly. */
public final class WorldBossMenu extends MobMenuBase {
    public WorldBossMenu(Player p,MobMenu.MobDraft data,Menu parent) {super(p,"world-boss",data,parent);}
    @Override protected Component title(){return m("editor-title");}
    @Override public boolean permitted(Player p){return super.permitted(p)&&allowed(p);}
    static boolean allowed(Player p) {
        if(p.hasPermission("customdungeons.admin.boss"))return true;
        MenuListener.instance().messages().send(p,"gui.world-boss.no-permission");return false;
    }
    static Component m(String key,TagResolver... args){return MenuListener.instance().messages().get("gui.world-boss."+key,args);}
    static String number(double value,int decimals,boolean fixed) {
        var n=java.math.BigDecimal.valueOf(value).setScale(decimals,java.math.RoundingMode.HALF_UP);
        String text=(fixed?n:n.stripTrailingZeros()).toPlainString().replace("-","−");
        return "en".equals(config().language())?text:text.replace('.',',');
    }
    static Component rangeDescription(Component description) {
        description=description.replaceText(b->b.matchLiteral("-").replacement("−"));
        return "en".equals(config().language())?description:description.replaceText(b->b.match("(?<=\\d)\\.(?=\\d)").replacement(","));
    }
    static WorldBossService service(){return Bukkit.getServicesManager().load(WorldBossService.class);}
    static Map<String,MobTemplate> templates(){var s=service();return s==null?Map.of():s.listed();}
    static boolean orphaned(String id){var s=service();return s!=null&&s.orphaned(id);}
    static int alive(String id){var s=service();return s==null?0:s.alive(id);}
    static int totalAlive(){return templates().keySet().stream().mapToInt(WorldBossMenu::alive).sum();}
    static TagResolver[] args(WorldBossDef b,int alive){return new TagResolver[]{
        Placeholder.unparsed("world",b.world()),Placeholder.unparsed("xmin",number(b.xMin(),0,false)),Placeholder.unparsed("xmax",number(b.xMax(),0,false)),
        Placeholder.unparsed("zmin",number(b.zMin(),0,false)),Placeholder.unparsed("zmax",number(b.zMax(),0,false)),Placeholder.unparsed("alive",Integer.toString(alive)),
        Placeholder.unparsed("max",Integer.toString(b.maxAlive())),Placeholder.unparsed("radius",Integer.toString(b.radius())),Placeholder.unparsed("damage",number(b.minimumDamage(),1,false))};}
    static Component zone(WorldBossDef b){return m("zone-line",args(b,0));}
    static Component rewards(WorldBossDef b){var r=b.reward();return m("rewards-line",Placeholder.unparsed("items",Integer.toString(r.items().size())),
            Placeholder.unparsed("money",number(r.money(),2,true)),Placeholder.unparsed("xp",Integer.toString(r.xp())),Placeholder.unparsed("commands",Integer.toString(r.commands().size())));}
    boolean writable(){return permitted(viewer)&&!data.saving&&!MenuListener.instance().rejectReload(viewer)
            &&(plugin().bossRegistry()==null||plugin().bossRegistry().configurable(data.id));}
    private List<ValidationError> errors(){return new Validator(registry()).validateWorldBoss(data.worldBoss,Bukkit.getWorlds().stream().map(World::getName).collect(java.util.stream.Collectors.toSet()));}
    @Override protected void renderHeader(){
        var b=data.worldBoss;var invalid=errors();var state=invalid.isEmpty()?m("validation-ok"):m("validation-error");
        set(4,GuiTheme.information(egg(data.type),m("summary",Placeholder.component("name",dev.dasan.customdungeons.text.Text.parse(data.name))),
                List.of(m("alive-line",args(b,alive(data.id))),zone(b),m("encounter-line",args(b,0)),rewards(b),state)));
        var lore=new ArrayList<Component>();
        if(invalid.isEmpty()){lore.add(m("validation-ok-lore"));lore.add(m("validation-ok-ranges"));}
        else {invalid.forEach(e->lore.add(MenuListener.instance().messages().get(e.messageKey(),e.args().entrySet().stream().map(a->Placeholder.unparsed(a.getKey(),a.getValue())).toArray(TagResolver[]::new))));lore.add(m("validation-save-blocked"));if(b.xMin()>=b.xMax())lore.add(m("validation-fix-x"));}
        set(6,GuiTheme.information(invalid.isEmpty()?Material.LIME_STAINED_GLASS_PANE:Material.RED_STAINED_GLASS_PANE,state,lore));
        GuiTheme.help(this,java.util.stream.IntStream.rangeClosed(1,5).mapToObj(i->m("help-editor-"+i)).toList());
    }
    @Override protected void render(){
        var b=data.worldBoss;
        for(int i=0;i<4;i++) {
            String key=List.of("x","z","encounter","actions").get(i);boolean bad=key.equals("x")?b.xMin()>=b.xMax():key.equals("z")&&b.zMin()>=b.zMax();
            set(10+i*2,bad?GuiTheme.section(false,m("field-error",Placeholder.component("field",m("section-"+key))),List.of(m("section-"+key+"-lore"))):GuiTheme.section(m("section-"+key),List.of(m("section-"+key+"-lore"))));
        }
        number(19,"x-min",Material.PAPER,b.xMin());number(28,"x-max",Material.PAPER,b.xMax());
        number(21,"z-min",Material.MAP,b.zMin());number(30,"z-max",Material.MAP,b.zMax());
        number(23,"max-alive",Material.ZOMBIE_HEAD,b.maxAlive());number(32,"radius",Material.TARGET,b.radius());number(41,"minimum-damage",Material.IRON_SWORD,b.minimumDamage());
        set(37,Button.of(Material.GRASS_BLOCK,m("world",args(b,0)),List.of(m("world-current",args(b,0)),Component.empty(),m("world-lore")),(p,c)->{
            if(c==ClickType.LEFT)MenuListener.instance().later(()->{if(writable())new BossChoiceMenu(p,m("world-picker"),Bukkit.getWorlds().stream().map(World::getName).toList(),v->Material.GRASS_BLOCK,this,v->{if(writable()){var old=data.worldBoss;data.worldBoss=new WorldBossDef(v,old.xMin(),old.xMax(),old.zMin(),old.zMax(),old.maxAlive(),old.radius(),old.minimumDamage(),old.reward());}}).open();});
        }));
        var positions=positionLore(data.id,b);
        positions.add(m("state-refresh"));set(39,GuiTheme.information(alive(data.id)>0?Material.YELLOW_STAINED_GLASS_PANE:Material.LIME_STAINED_GLASS_PANE,m("state",args(b,alive(data.id))),positions));
        set(25,Button.of(Material.CHEST,m("rewards"),List.of(rewards(b),m("rewards-rule",args(b,0)),m("pending-items"),Component.empty(),m("rewards-lore")),(p,c)->{
            if(c==ClickType.LEFT)MenuListener.instance().later(()->{if(writable())new RewardMenu(p,data,this).open();});
        }));
        String blocked=!errors().isEmpty()?"spawn-invalid":hasUnsavedChanges()?"spawn-save-first":alive(data.id)>=b.maxAlive()?"spawn-limit":null;
        if(blocked!=null)set(34,GuiTheme.unavailable(m("spawn"),m(blocked,args(b,alive(data.id)))));
        else set(34,Button.of(Material.LIME_CONCRETE,m("spawn"),List.of(m("alive-line",args(b,alive(data.id))),m("spawn-saved"),Component.empty(),m("spawn-lore"),m("manual-only")),(p,c)->{
            if(c==ClickType.LEFT)MenuListener.instance().later(()->{if(permitted(p)&&!hasUnsavedChanges()&&errors().isEmpty())spawn(p,data.id,this);});
        }));
        if(alive(data.id)==0) {
            var disabled=GuiTheme.unavailable(m("despawn"),m("despawn-none"));disabled.icon().editMeta(meta->{var l=new ArrayList<>(meta.lore());l.add(m("despawn-no-rewards"));meta.lore(l);});set(43,disabled);
        } else set(43,Button.of(Material.RED_CONCRETE,m("despawn"),List.of(m("despawn-lore",args(b,alive(data.id))),m("despawn-no-rewards")),(p,c)->{
            if(c==ClickType.LEFT)MenuListener.instance().later(()->despawn(p,data.id,this));
        }));
        if(plugin().bossRegistry()!=null&&!plugin().bossRegistry().configurable(data.id))for(int slot:new int[]{19,28,21,30,23,32,41,37,25}) {
            var item=getInventory().getItem(slot);set(slot,GuiTheme.unavailable(item.getItemMeta().displayName(),m("code-config-locked")));
        }
    }
    private void number(int slot,String key,Material material,double value){
        var range=NumericRanges.worldBoss(key);var name=m(key,Placeholder.unparsed("value",number(value,range.decimals(),false)));
        boolean bad=key.startsWith("x-")&&data.worldBoss.xMin()>=data.worldBoss.xMax()||key.startsWith("z-")&&data.worldBoss.zMin()>=data.worldBoss.zMax();
        var lore=new ArrayList<>(NumericInputs.lore(range));lore.set(0,rangeDescription(lore.getFirst()));
        if(bad){String axis=key.substring(0,1);lore.add(m("validation-"+axis,Placeholder.unparsed("min",Integer.toString(axis.equals("x")?data.worldBoss.xMin():data.worldBoss.zMin())),Placeholder.unparsed("max",Integer.toString(axis.equals("x")?data.worldBoss.xMax():data.worldBoss.zMax()))));name=m("field-error",Placeholder.component("field",name));}
        lore.add(MenuListener.instance().messages().get("gui.mob.current-value",Placeholder.unparsed("value",number(value,range.decimals(),false))));
        lore.add(m(List.of("x-min","x-max","z-min","z-max").contains(key)?"coordinate-rule":key+"-lore"));
        if(key.equals("minimum-damage")){double health=data.attributes.getOrDefault("max-health",data.health);if(health>0)lore.add(m("damage-example",Placeholder.unparsed("health",formatValue(health)),Placeholder.unparsed("required",formatValue(health*data.worldBoss.minimumDamage()/100))));}
        lore.add(Component.empty());lore.add(m(range.decimals()==0?"numeric-clicks":"numeric-clicks-decimal"));lore.add(m("numeric-fallback"));
        Component label=name;set(slot,Button.of(material,name,lore,(p,c)->{
            if(!c.isLeftClick()&&!c.isRightClick())return;
            MenuListener.instance().later(()->{if(!writable())return;
                java.util.function.DoubleConsumer accept=n->{if(writable()){changeNumber(key,n);refresh();}};
                if(c.isRightClick())NumericInputs.clicks(p,label,range,value,accept);else NumericInputs.edit(p,label,range,value,accept);
            });
        }));
    }
    private void changeNumber(String key,double n){var b=data.worldBoss;data.worldBoss=new WorldBossDef(b.world(),key.equals("x-min")?(int)n:b.xMin(),key.equals("x-max")?(int)n:b.xMax(),key.equals("z-min")?(int)n:b.zMin(),key.equals("z-max")?(int)n:b.zMax(),key.equals("max-alive")?(int)n:b.maxAlive(),key.equals("radius")?(int)n:b.radius(),key.equals("minimum-damage")?n:b.minimumDamage(),b.reward());}
    static List<Component> positionLore(String id,WorldBossDef b){
        var lore=new ArrayList<Component>();var s=service();var positions=s==null?List.<Location>of():s.positions(id);
        if(positions.isEmpty())lore.add(m("no-position"));else for(var p:positions)lore.add(m("alive-at",Placeholder.unparsed("alive",Integer.toString(alive(id))),Placeholder.unparsed("max",Integer.toString(b.maxAlive())),Placeholder.unparsed("world",p.getWorld().getName()),Placeholder.unparsed("x",Integer.toString(p.getBlockX())),Placeholder.unparsed("y",Integer.toString(p.getBlockY())),Placeholder.unparsed("z",Integer.toString(p.getBlockZ()))));return lore;
    }
    static void spawn(Player p,String id,Menu menu){if(allowed(p)&&!MenuListener.instance().rejectReload(p)&&service()!=null)service().spawn(p,id).thenAccept(success->{if(success&&MobMenuBase.plugin().isEnabled()&&allowed(p)&&p.isOnline()&&p.getOpenInventory().getTopInventory()==menu.getInventory())menu.refresh();});}
    static void despawn(Player p,String id,Menu menu){if(!allowed(p)||MenuListener.instance().rejectReload(p))return;Inputs.confirm(p,m("despawn-confirm",Placeholder.unparsed("boss",id)),()->{if(allowed(p)&&!MenuListener.instance().rejectReload(p)&&service()!=null){service().despawn(p,id);menu.refresh();}});}
    @Override protected void renderFooter(){
        var lore=new ArrayList<Component>();if(hasUnsavedChanges())lore.add(MenuListener.instance().messages().get("gui.common.unsaved"));
        lore.add(Component.empty());lore.add(m("save-context"));
        var button=Button.of(Material.LIME_CONCRETE,MenuListener.instance().messages().get("gui.common.save"),lore,(p,c)->{if(c==ClickType.LEFT&&writable())save();});
        if(hasUnsavedChanges())button.icon().editMeta(meta->meta.setEnchantmentGlintOverride(true));set(49,button);
    }
}

final class BossChoiceMenu extends PagedMenu<String> {
    private final List<String> choices;private final Menu previous;private final java.util.function.Consumer<String> accept;
    private final java.util.function.Function<String,Material> icon;
    BossChoiceMenu(Player p,Component title,List<String> choices,java.util.function.Function<String,Material> icon,Menu previous,java.util.function.Consumer<String> accept){super(p,title,6);this.choices=choices;this.icon=icon;this.previous=previous;this.accept=accept;}
    @Override public boolean permitted(Player p){return super.permitted(p)&&WorldBossMenu.allowed(p);}
    @Override protected Material borderMaterial(){return Material.LIGHT_BLUE_STAINED_GLASS_PANE;}
    @Override protected List<String> items(){return choices;}
    @Override protected Menu parent(){return previous;}
    @Override protected Button button(String value){return Button.of(icon.apply(value),Component.text(value),List.of(MobMenuBase.message("action-choose")),(p,c)->{if(c==ClickType.LEFT)MenuListener.instance().later(()->{if(permitted(p)){accept.accept(value);if(p.getOpenInventory().getTopInventory()==getInventory())previous.open();}});});}
}
