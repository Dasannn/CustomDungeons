package dev.dasan.customdungeons.gui.menu;

import dev.dasan.customdungeons.gui.*;
import dev.dasan.customdungeons.model.*;
import java.util.*;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

/** World bosses from YAML and code share this paged library. */
public final class BossListMenu extends PagedMenu<MobTemplate> {
    private final Menu previous;
    private boolean creating;
    public BossListMenu(Player p,Menu previous){super(p,WorldBossMenu.m("list-title"),6);this.previous=previous;}
    @Override public boolean permitted(Player p){return super.permitted(p)&&WorldBossMenu.allowed(p);}
    @Override protected Menu parent(){return previous;}
    @Override protected List<MobTemplate> items(){return WorldBossMenu.templates().values().stream().sorted(Comparator.comparing(MobTemplate::id)).toList();}
    @Override protected void renderHeader(){
        int code=(int)items().stream().filter(m->MobMenuBase.plugin().bossRegistry().definedInCode(m.id())).count();
        var heading=WorldBossMenu.m("list-summary",Placeholder.unparsed("page",Integer.toString(page()+1)),Placeholder.unparsed("pages",Integer.toString(pageCount(items().size(),pageSize()))));
        var count=WorldBossMenu.m("list-count",Placeholder.unparsed("count",Integer.toString(items().size())),Placeholder.unparsed("code",Integer.toString(code)),Placeholder.unparsed("alive",Integer.toString(WorldBossMenu.totalAlive())));
        var showing=WorldBossMenu.m("list-showing",Placeholder.unparsed("first",Integer.toString(items().isEmpty()?0:page()*pageSize()+1)),Placeholder.unparsed("last",Integer.toString(Math.min(items().size(),(page()+1)*pageSize()))),Placeholder.unparsed("count",Integer.toString(items().size())));
        set(4,GuiTheme.information(Material.BOOK,heading,List.of(count,showing)));
        if(code>0)set(6,GuiTheme.section(WorldBossMenu.m("code-indicator",Placeholder.unparsed("code",Integer.toString(code))),List.of(WorldBossMenu.m("code-readonly"),WorldBossMenu.m("code-configurable"))));
        if(items().isEmpty())set(22,GuiTheme.information(Material.GRAY_DYE,WorldBossMenu.m("list-empty"),List.of()));
        GuiTheme.help(this,java.util.stream.IntStream.rangeClosed(1,4).mapToObj(i->WorldBossMenu.m("help-list-"+i)).toList());
    }
    @Override protected Button button(MobTemplate m){
        var b=m.worldBoss();boolean code=MobMenuBase.plugin().bossRegistry().definedInCode(m.id());
        int alive=WorldBossMenu.alive(m.id());
        var lore=new ArrayList<Component>();lore.addAll(IntelligenceMenu.markerLore(m));
        if(alive>0)lore.addAll(WorldBossMenu.positionLore(m.id(),b));else lore.add(WorldBossMenu.m("alive-line",WorldBossMenu.args(b,alive)));
        lore.add(WorldBossMenu.m("entity-line",Placeholder.unparsed("type",m.entityType())));lore.add(WorldBossMenu.zone(b));
        lore.add(WorldBossMenu.m(code?"origin-code":"origin-yaml"));if(code){lore.add(WorldBossMenu.m("code-readonly"));lore.add(WorldBossMenu.m("code-configurable"));}
        boolean orphan=WorldBossMenu.orphaned(m.id());
        if(orphan)lore.add(WorldBossMenu.m("orphan-lore"));
        if(alive==0)lore.add(WorldBossMenu.m("no-position"));
        lore.add(Component.empty());lore.add(WorldBossMenu.m(code?"click-edit-code":"click-edit"));lore.add(WorldBossMenu.m("click-spawn"));lore.add(WorldBossMenu.m("click-despawn"));
        if(alive==0)lore.add(WorldBossMenu.m("list-despawn-unavailable",WorldBossMenu.args(b,alive)));
        if(alive>=b.maxAlive())lore.add(WorldBossMenu.m("list-spawn-unavailable",WorldBossMenu.args(b,alive)));
        Component name=WorldBossMenu.m("entry-name",Placeholder.component("name",dev.dasan.customdungeons.text.Text.parse(m.displayName())),Placeholder.unparsed("id",m.id()));
        if(orphan)name=name.append(Component.space()).append(WorldBossMenu.m("orphan-marker"));
        if(code)name=name.append(Component.space()).append(WorldBossMenu.m("code-marker"));
        return Button.of(MobMenuBase.egg(m.entityType()),name,lore,(p,c)->{
            if(!permitted(p)||MenuListener.instance().rejectReload(p))return;
            MenuListener.instance().later(()->{
                if(!permitted(p))return;
                if(c==ClickType.SHIFT_RIGHT)WorldBossMenu.despawn(p,m.id(),this);
                else if(WorldBossMenu.orphaned(m.id()))MenuListener.instance().messages().send(p,"gui.world-boss.orphan-lore");
                else if(c==ClickType.RIGHT)WorldBossMenu.spawn(p,m.id(),this);
                else if(c==ClickType.LEFT)new MobMenu(p,m,this).open();
            });
        });
    }
    @Override protected void renderFooter(){
        if(hasNextPage())getInventory().getItem(50).editMeta(meta->{
            var lore=new ArrayList<>(meta.lore());
            lore.addFirst(WorldBossMenu.m("list-next",Placeholder.unparsed("page",Integer.toString(page()+2)),Placeholder.unparsed("pages",Integer.toString(pageCount(items().size(),pageSize()))),Placeholder.unparsed("remaining",Integer.toString(items().size()-(page()+1)*pageSize()))));
            meta.lore(lore);
        });
        set(49,Button.of(Material.LIME_DYE,WorldBossMenu.m("new"),List.of(WorldBossMenu.m("new-additive"),Component.empty(),WorldBossMenu.m("new-existing"),WorldBossMenu.m("new-template")),(p,c)->{
            if(c!=ClickType.LEFT&&c!=ClickType.RIGHT)return;
            MenuListener.instance().later(()->{if(!permitted(p)||MenuListener.instance().rejectReload(p))return;
                if(c==ClickType.LEFT)new BossChoiceMenu(p,WorldBossMenu.m("picker"),MobMenuBase.store().mobs().values().stream().filter(m->m.worldBoss()==null&&!MobMenuBase.plugin().bossRegistry().definedInCode(m.id())).map(MobTemplate::id).sorted().toList(),this,id->{
                    var draft=new MobMenu.MobDraft(MobMenuBase.store().mobs().get(id));draft.worldBoss=WorldBossDef.defaults(p.getWorld().getName());draft.boss=true;new MobMenu(p,draft,this).open();
                }).open();
                else Inputs.text(p,MobMenuBase.message("id"),"",32,id->{
                    if(!permitted(p)||MenuListener.instance().rejectReload(p))return;
                    if(!id.matches("[a-z0-9_-]{1,32}")){MenuListener.instance().messages().send(p,"gui.mob.invalid-id");return;}
                    if(MobMenuBase.store().mobs().containsKey(id)||WorldBossMenu.templates().containsKey(id)){MenuListener.instance().messages().send(p,"gui.mob.duplicate-id",Placeholder.unparsed("id",id));return;}
                    var m=new MobTemplate(id,"ZOMBIE",id,0,0,0,0,0,Map.of(),List.of(),List.of(),List.of(),false,"PURPLE",null,List.of(),false);
                    if(creating)return;creating=true;
                    var plugin=MobMenuBase.plugin();
                    MobMenuBase.store().save(m).whenComplete((unused,error)->{
                        if(!plugin.isEnabled())return;
                        MenuListener.instance().later(()->{
                            creating=false;if(!p.isOnline()||!permitted(p))return;
                            if(error!=null){MenuListener.instance().messages().send(p,"gui.world-boss.save-failed");return;}
                            var draft=new MobMenu.MobDraft(m);draft.boss=true;draft.worldBoss=WorldBossDef.defaults(p.getWorld().getName());new MobMenu(p,draft,this).open();
                        });
                    });
                });
            });
        }));
    }
}
