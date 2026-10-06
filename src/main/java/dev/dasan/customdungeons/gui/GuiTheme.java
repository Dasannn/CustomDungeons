package dev.dasan.customdungeons.gui;

import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Material;
import org.jetbrains.annotations.Nullable;

public final class GuiTheme {
    private GuiTheme() {}

    public static Button information(Material icon, Component name, List<Component> lore) {
        return Button.of(icon, name, lore, (p, c) -> {});
    }
    public static void frame(Menu menu) {
        int size = menu.getInventory().getSize();
        Button border = information(menu.borderMaterial(), Component.empty(), List.of());
        Button filler = information(Material.GRAY_STAINED_GLASS_PANE, Component.empty(), List.of());
        border.icon().editMeta(meta -> meta.setHideTooltip(true));
        filler.icon().editMeta(meta -> meta.setHideTooltip(true));
        for (int slot = 0; slot < size; slot++)
            menu.set(slot, slot < 9 || slot >= size - 9 || slot % 9 == 0 || slot % 9 == 8 ? border : filler);
    }
    public static Material toggleIcon(boolean value) { return value ? Material.LIME_DYE : Material.GRAY_DYE; }
    public static Button section(Material glass, Component name, List<Component> lore) {
        return information(glass, name.decorate(TextDecoration.BOLD), lore);
    }
    public static Component grayName(Component name) {
        return Component.text(net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(name), NamedTextColor.GRAY);
    }
    public static Button unavailable(Component name, Component reason) {
        var messages = MenuListener.instance().messages();
        return information(Material.GRAY_DYE, grayName(name),
                List.of(messages.get("gui.common.unavailable", Placeholder.component("reason", reason))));
    }
    public static void help(Menu menu, List<Component> lines) {
        menu.set(8, information(Material.KNOWLEDGE_BOOK,
                MenuListener.instance().messages().get("gui.common.help"), lines));
    }
    public static void navBar(Menu menu, @Nullable Runnable onSave, boolean hasPrev, boolean hasNext) {
        int base = menu.getInventory().getSize() - 9;
        for (int slot : GuiLayout.footerSlots(menu.parent() != null, hasPrev || hasNext, onSave != null)) {
            switch (slot) {
                case 0 -> menu.set(base, nav("back", Material.ARROW, (p, click) -> {
                    MenuListener.instance().later(menu.parent()::open);
                }));
                case 3 -> menu.set(base + 3, hasPrev ? nav("previous", Material.SPECTRAL_ARROW,
                        (p, click) -> menu.previousPage()) : unavailable(
                                MenuListener.instance().messages().get("gui.common.previous"),
                                MenuListener.instance().messages().get("gui.common.first-page")));
                case 4 -> {
                    var messages = MenuListener.instance().messages();
                    var lore = new java.util.ArrayList<Component>();
                    if (menu.hasUnsavedChanges()) lore.add(messages.get("gui.common.unsaved"));
                    lore.add(Component.empty()); lore.add(messages.get("gui.common.save-lore"));
                    var save = Button.of(Material.LIME_CONCRETE, messages.get("gui.common.save"), lore, (p, click) -> {
                        onSave.run(); MenuListener.instance().play(p, MenuListener.instance().sounds().save());
                    });
                    if (menu.hasUnsavedChanges()) save.icon().editMeta(meta -> meta.setEnchantmentGlintOverride(true));
                    menu.set(base + 4, save);
                }
                case 5 -> menu.set(base + 5, hasNext ? nav("next", Material.SPECTRAL_ARROW,
                        (p, click) -> menu.nextPage()) : unavailable(
                                MenuListener.instance().messages().get("gui.common.next"),
                                MenuListener.instance().messages().get("gui.common.last-page")));
                case 8 -> menu.set(base + 8, nav("close", Material.BARRIER,
                        (p, click) -> MenuListener.instance().later(p::closeInventory)));
                default -> throw new IllegalStateException("Unexpected footer slot");
            }
        }
    }
    private static Button nav(String key, Material material, Button.ClickHandler handler) {
        var messages = MenuListener.instance().messages();
        return Button.of(material, messages.get("gui.common." + key),
                List.of(Component.empty(), messages.get("gui.common." + key + "-lore")), handler);
    }
}
