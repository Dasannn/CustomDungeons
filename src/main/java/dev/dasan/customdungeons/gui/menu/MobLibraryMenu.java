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
    public MobLibraryMenu(Player viewer) { super(viewer, MobMenuBase.message("library"), 6); }
    @Override protected List<MobTemplate> items() {
        set(4, Button.of(Material.LIME_DYE, MobMenuBase.message("create"), List.of(MobMenuBase.message("create-lore")),
                (p,c) -> MenuListener.instance().later(() -> Inputs.text(p, MobMenuBase.message("id"), "", 32, id -> {
                    if (!id.matches("[a-z0-9_-]{1,32}") || MobMenuBase.store().mobs().containsKey(id)) {
                        MenuListener.instance().messages().send(p, "gui.mob.invalid-id"); return;
                    }
                    new MobMenu(p, new MobTemplate(id, "ZOMBIE", id, 0, 0, 0, 0, 0, Map.of(), List.of(),
                            List.of(), List.of(), false, "PURPLE", null, List.of(), false), this).open();
                }))));
        return MobMenuBase.store().mobs().values().stream().sorted(Comparator.comparing(MobTemplate::id)).toList();
    }
    @Override protected Button button(MobTemplate m) {
        return Button.of(MobMenuBase.egg(m.entityType()), MobMenuBase.label("entry", m.id()),
                List.of(MobMenuBase.message("library-entry-lore")), (p,c) -> MenuListener.instance().later(() -> {
                    if (c.isShiftClick() && c.isRightClick()) Inputs.confirm(p, MobMenuBase.message("delete-confirm"),
                        () -> {
                            var ownerPlugin=MobMenuBase.plugin();
                            MobMenuBase.store().deleteMob(m.id()).whenComplete((v,e) -> {
                                if(!ownerPlugin.isEnabled()) return;
                                Bukkit.getScheduler().runTask(ownerPlugin, () -> {
                                    if(p.isOnline()) { MenuListener.instance().messages().send(p, e == null ? "gui.mob.deleted" : "gui.mob.save-failed"); refresh(); }
                                });
                            });
                        });
                    else new MobMenu(p, m, this).open();
                }));
    }
}
