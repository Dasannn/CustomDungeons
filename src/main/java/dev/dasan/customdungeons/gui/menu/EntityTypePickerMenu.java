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

public final class EntityTypePickerMenu extends PagedMenu<org.bukkit.entity.EntityType> {
    private final MobMenu.MobDraft draft;
    private final Menu previous;
    private String query = "";
    public EntityTypePickerMenu(Player p, MobMenu.MobDraft draft, Menu parent) {
        super(p, MobMenuBase.message("entity"), 6); this.draft = draft; previous = parent;
    }
    @Override protected List<org.bukkit.entity.EntityType> items() {
        set(4, Button.of(Material.COMPASS, MobMenuBase.message("search"), List.of(MobMenuBase.message("search-lore")),
                (p,c) -> MenuListener.instance().later(() -> Inputs.text(p, MobMenuBase.message("search"), query, 100,
                    s -> query = s.toLowerCase(Locale.ROOT)))));
        return Arrays.stream(org.bukkit.entity.EntityType.values()).filter(t -> t.isAlive() && t.isSpawnable())
                .filter(t -> t.name().toLowerCase(Locale.ROOT).contains(query)).toList();
    }
    @Override protected Button button(org.bukkit.entity.EntityType type) {
        return Button.of(MobMenuBase.egg(type.name()), MobMenuBase.label("choice", type.getKey()),
                List.of(MobMenuBase.message("click-lore")), (p,c) -> MenuListener.instance().later(() -> {
                    draft.type = type.name(); previous.open();
                }));
    }
    @Override protected Menu parent() { return previous; }
}
