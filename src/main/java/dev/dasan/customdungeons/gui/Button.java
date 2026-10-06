package dev.dasan.customdungeons.gui;

import java.util.List;
import java.util.Objects;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

public record Button(ItemStack icon, ClickHandler onClick) {
    public Button { Objects.requireNonNull(icon); Objects.requireNonNull(onClick); }
    /** Inventory opens/closes must use MenuListener.later, as required by InventoryClickEvent. */
    public interface ClickHandler { void handle(Player p, ClickType click); }

    public static Button of(Material material, Component name, List<Component> lore, ClickHandler handler) {
        ItemStack icon = new ItemStack(material);
        icon.editMeta(meta -> {
            meta.displayName(name.decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE));
            meta.lore(lore.stream().map(line -> line.decorationIfAbsent(
                    TextDecoration.ITALIC, TextDecoration.State.FALSE)).toList());
        });
        return new Button(icon, handler);
    }
}
