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

public final class MobLibraryMenu extends PagedMenu<MobTemplate> {
    private final Menu previous;
    private boolean creating;
    public MobLibraryMenu(Player viewer) { this(viewer, null); }
    public MobLibraryMenu(Player viewer, Menu parent) {
        super(viewer, MobMenuBase.message("library"), 6);
        previous = parent;
    }
    @Override protected Menu parent() { return previous; }
    @Override protected void renderFooter() {
        set(getInventory().getSize()-5, Button.of(Material.LIME_DYE, MobMenuBase.message("create"), List.of(MobMenuBase.message("create-lore")),
                (p,c) -> MenuListener.instance().later(() -> Inputs.text(p, MobMenuBase.message("id"), "", 32, id -> {
                    if(creating || !p.isOnline() || !p.hasPermission("customdungeons.admin.edit") || MenuListener.instance().rejectReload(p)) return;
                    if (!id.matches("[a-z0-9_-]{1,32}")) {
                        MenuListener.instance().messages().send(p, "gui.mob.invalid-id"); return;
                    }
                    if (MobMenuBase.store().mobs().containsKey(id)) {
                        MenuListener.instance().messages().send(p,"gui.mob.duplicate-id",Placeholder.unparsed("id",id)); return;
                    }
                    var template=new MobTemplate(id,"ZOMBIE",id,0,0,0,0,0,Map.of(),List.of(),
                            List.of(),List.of(),false,"PURPLE",null,List.of(),false);
                    creating=true;
                    var ownerPlugin=MobMenuBase.plugin();
                    try {
                        MobMenuBase.store().save(template).whenComplete((unused,failure)->{
                            if(!ownerPlugin.isEnabled()) return;
                            MenuListener.instance().later(()->{
                                creating=false;
                                if(!p.isOnline()) return;
                                if(failure!=null) {MenuListener.instance().messages().send(p,"gui.mob.save-failed");return;}
                                new MobMenu(p,template,this).open();
                            });
                        });
                    } catch(RuntimeException failure) {
                        creating=false;MenuListener.instance().messages().send(p,"gui.mob.save-failed");
                    }
                }))));
    }
    @Override protected void renderHeader() {
        set(4,GuiTheme.information(Material.BOOK,MobMenuBase.message("library"),
                List.of(MenuListener.instance().messages().get("gui.mob.library-count",Placeholder.unparsed("value",Integer.toString(items().size()))))));
        GuiTheme.help(this,java.util.stream.IntStream.rangeClosed(1,3).mapToObj(i->MobMenuBase.message("help-library-"+i)).toList());
    }
    @Override protected int preferredRows() {return GuiLayout.rowsFor(items().size(),7,0);}
    @Override protected List<MobTemplate> items() {
        return MobMenuBase.store().mobs().values().stream().sorted(Comparator.comparing(MobTemplate::id)).toList();
    }
    @Override protected Button button(MobTemplate m) {
        var draft = new MobMenu.MobDraft(m);
        var lore = new ArrayList<>(MobMenuBase.summaryLore(draft,draft));
        lore.addAll(IntelligenceMenu.markerLore(m));
        lore.add(Component.empty()); lore.add(MobMenuBase.message("library-entry-lore"));
        return Button.of(MobMenuBase.egg(m.entityType()), MenuListener.instance().messages().get("gui.mob.library-name",
                Placeholder.component("name", dev.dasan.customdungeons.text.Text.parse(m.displayName())), Placeholder.unparsed("id", m.id())),
                lore, (p,c) -> MenuListener.instance().later(() -> {
                    if (c.isShiftClick() && c.isRightClick()) {
                        if((m.worldBoss()!=null||MobMenuBase.plugin().bossRegistry().definedInCode(m.id())||WorldBossMenu.alive(m.id())>0)&&!WorldBossMenu.allowed(p))return;
                        if(MenuListener.instance().rejectReload(p))return;
                        if(WorldBossMenu.alive(m.id())>0)MenuListener.instance().messages().send(p,"gui.world-boss.delete-living-warning",
                                Placeholder.unparsed("boss",m.id()),Placeholder.unparsed("alive",Integer.toString(WorldBossMenu.alive(m.id()))));
                        Inputs.confirm(p, MobMenuBase.message("delete-confirm"),
                        () -> {
                            var current=MobMenuBase.store().mobs().get(m.id());
                            if((m.worldBoss()!=null||current!=null&&current.worldBoss()!=null||MobMenuBase.plugin().bossRegistry().definedInCode(m.id())||WorldBossMenu.alive(m.id())>0)&&!WorldBossMenu.allowed(p))return;
                            if(!permitted(p)||MenuListener.instance().rejectReload(p))return;
                            var ownerPlugin=MobMenuBase.plugin();
                            MobMenuBase.store().deleteMob(m.id()).whenComplete((v,e) -> {
                                if(!ownerPlugin.isEnabled()) return;
                                Bukkit.getScheduler().runTask(ownerPlugin, () -> {
                                    if(p.isOnline()) { MenuListener.instance().messages().send(p, e == null ? "gui.mob.deleted" : "gui.mob.save-failed"); refresh(); }
                                });
                            });
                        });
                    } else new MobMenu(p, m, this).open();
                }));
    }
}
