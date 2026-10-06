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

public final class AbilityPickerMenu extends PagedMenu<Ability> {
    private final Menu previous;
    private final Consumer<Ability> selected;
    private final boolean returnToParent;
    private String query = "";
    public AbilityPickerMenu(Player p, Menu parent, Consumer<Ability> selected) {
        this(p,parent,selected,true);
    }
    public AbilityPickerMenu(Player p, Menu parent, Consumer<Ability> selected, boolean returnToParent) {
        super(p, MobMenuBase.menuTitle("abilities"), 6); previous = parent; this.selected = selected; this.returnToParent=returnToParent;
    }
    @Override protected Material borderMaterial() { return Material.LIGHT_BLUE_STAINED_GLASS_PANE; }
    @Override protected int preferredRows() { return GuiLayout.rowsFor(items().size(),7,0); }
    @Override protected void renderHeader() {
        set(4, GuiTheme.information(Material.BLAZE_POWDER, MobMenuBase.menuTitle("abilities"), List.of(MenuListener.instance().messages().get("gui.mob.selector-count",
                Placeholder.unparsed("count", Integer.toString(items().size())), Placeholder.unparsed("query", query)))));
        if (items().isEmpty()) set(13, GuiTheme.information(Material.GRAY_DYE, MobMenuBase.message("no-results"), List.of()));
        GuiTheme.help(this, java.util.stream.IntStream.rangeClosed(1,3).mapToObj(i -> MobMenuBase.message("help-selector-"+i)).toList());
    }
    void query(String value) { query = value.strip().toLowerCase(Locale.ROOT); }
    @Override protected void renderFooter() {
        set(getInventory().getSize()-8, Button.of(Material.NAME_TAG, MobMenuBase.message("search"), List.of(MobMenuBase.message("search-lore")),
                (p,c) -> MenuListener.instance().later(() -> Inputs.text(p,MobMenuBase.message("search"),query,100,this::query))));
    }
    @Override protected List<Ability> items() {
        return MobMenuBase.registry().all().stream().filter(a -> a.id().toLowerCase(Locale.ROOT).contains(query)
                || net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(MobMenuBase.abilityName(a.id())).toLowerCase(Locale.ROOT).contains(query))
                .sorted(Comparator.comparing(Ability::id)).toList();
    }
    @Override protected Button button(Ability a) {
        return Button.of(MobMenuBase.abilityIcon(a), MobMenuBase.abilityName(a.id()), List.of(MenuListener.instance().messages().get("ability."+a.id()+".lore"), MobMenuBase.message("ability-pick-lore")), (p,c) ->
            MenuListener.instance().later(() -> { selected.accept(a); if(returnToParent) previous.open(); }));
    }
    @Override protected Menu parent() { return previous; }
}
