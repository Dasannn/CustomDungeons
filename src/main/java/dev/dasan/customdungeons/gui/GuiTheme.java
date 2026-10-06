package dev.dasan.customdungeons.gui;

import java.util.List;
import org.bukkit.Material;
import org.jetbrains.annotations.Nullable;

public final class GuiTheme {
    private GuiTheme() {}

    public static void frame(Menu menu) {
        int size = menu.getInventory().getSize();
        Button glass = Button.of(Material.GRAY_STAINED_GLASS_PANE,
                MenuListener.instance().messages().get("gui.common.border"), List.of(), (p, click) -> {});
        for (int slot = 0; slot < size; slot++) {
            if (slot < 9 || slot >= size - 9 || slot % 9 == 0 || slot % 9 == 8) { menu.set(slot, glass); }
        }
    }
    /** Same relative positions for 3..6 rows; in a six-row menu: 45,48,49,50,53. */
    public static void navBar(Menu menu, @Nullable Runnable onSave, boolean hasPrev, boolean hasNext) {
        int base = menu.getInventory().getSize() - 9;
        menu.set(base, nav("back", Material.ARROW, menu.parent() != null, (p, click) -> {
            Menu parent = menu.parent();
            if (parent != null) { MenuListener.instance().later(parent::open); }
        }));
        menu.set(base + 3, nav("previous", Material.ARROW, hasPrev, (p, click) -> menu.previousPage()));
        menu.set(base + 4, nav("save", Material.LIME_DYE, onSave != null, (p, click) -> {
            if (onSave != null) {
                onSave.run();
                MenuListener.instance().play(p, MenuListener.instance().sounds().save());
            }
        }));
        menu.set(base + 5, nav("next", Material.ARROW, hasNext, (p, click) -> menu.nextPage()));
        menu.set(base + 8, nav("close", Material.BARRIER, true,
                (p, click) -> MenuListener.instance().later(p::closeInventory)));
    }
    private static Button nav(String key, Material material, boolean enabled, Button.ClickHandler handler) {
        var messages = MenuListener.instance().messages();
        return Button.of(enabled ? material : Material.GRAY_DYE, messages.get("gui.common." + key),
                List.of(messages.get("gui.common." + key + "-lore"),
                        messages.get(enabled ? "gui.common.click-lore" : "gui.common.unavailable")),
                enabled ? handler : (p, click) -> {});
    }
}
