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
        super(p, MobMenuBase.menuTitle("entity"), 6); this.draft = draft; previous = parent;
    }
    void query(String value) { query = value.strip().toLowerCase(Locale.ROOT); }
    @Override protected Material borderMaterial() { return Material.LIGHT_BLUE_STAINED_GLASS_PANE; }
    @Override protected int preferredRows() { return GuiLayout.rowsFor(items().size(),7,0); }
    @Override protected void renderHeader() {
        set(4, GuiTheme.information(Material.BOOK, MobMenuBase.menuTitle("entity"), List.of(MenuListener.instance().messages().get("gui.mob.selector-count",
                Placeholder.unparsed("count", Integer.toString(items().size())), Placeholder.component("query", MobMenuBase.filterLabel(query))))));
        if (items().isEmpty()) set(13, GuiTheme.information(Material.GRAY_DYE, MobMenuBase.message("no-results"), List.of()));
        GuiTheme.help(this, java.util.stream.IntStream.rangeClosed(1,3).mapToObj(i -> MobMenuBase.message("help-selector-"+i)).toList());
    }
    @Override protected void renderFooter() {
        set(getInventory().getSize()-8, Button.of(Material.NAME_TAG, MobMenuBase.message("search"), List.of(MobMenuBase.message("search-lore")),
                (p,c) -> MenuListener.instance().later(() -> Inputs.text(p,MobMenuBase.message("search"),query,100,this::query))));
    }
    @Override protected List<org.bukkit.entity.EntityType> items() {
        return Arrays.stream(org.bukkit.entity.EntityType.values()).filter(EntityTypePickerMenu::isSelectable)
                .filter(t -> t.name().toLowerCase(Locale.ROOT).contains(query)).toList();
    }
    static boolean isSelectable(org.bukkit.entity.EntityType type) {
        Class<? extends org.bukkit.entity.Entity> entityClass = type.getEntityClass();
        return type.isSpawnable() && entityClass != null
                && org.bukkit.entity.Mob.class.isAssignableFrom(entityClass);
    }
    @Override protected Button button(org.bukkit.entity.EntityType type) {
        return Button.of(MobMenuBase.egg(type.name()), MobMenuBase.label("choice", type.getKey()),
                List.of(MobMenuBase.message("action-choose")), (p,c) -> MenuListener.instance().later(() -> {
                    draft.type = type.name(); previous.open();
                }));
    }
    @Override protected Menu parent() { return previous; }
}
