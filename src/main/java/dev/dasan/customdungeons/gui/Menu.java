package dev.dasan.customdungeons.gui;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.Nullable;

public abstract class Menu implements InventoryHolder {
    protected final Player viewer;
    private Inventory inventory;
    private final Component title;
    private final Map<Integer, Button> buttons = new HashMap<>();

    protected Menu(Player viewer, Component title, int rows) {
        if (rows < 3 || rows > 6) { throw new IllegalArgumentException("rows must be 3..6"); }
        this.viewer = Objects.requireNonNull(viewer);
        this.title = title;
        inventory = Bukkit.createInventory(this, rows * 9, title);
    }
    protected abstract void render();
    protected org.bukkit.Material borderMaterial() { return org.bukkit.Material.PURPLE_STAINED_GLASS_PANE; }
    protected final void set(int slot, Button button) {
        if (slot < 0 || slot >= inventory.getSize()) { throw new IndexOutOfBoundsException(slot); }
        buttons.put(slot, Objects.requireNonNull(button));
        inventory.setItem(slot, button.icon().clone());
    }
    /** Remove both the icon and its handler before making a slot available for real items. */
    protected final void clear(int slot) {
        if (slot < 0 || slot >= inventory.getSize()) { throw new IndexOutOfBoundsException(slot); }
        buttons.remove(slot);
        inventory.setItem(slot, null);
    }
    public final void open() {
        if (!viewer.hasPermission("customdungeons.admin.edit")) {
            MenuListener.instance().messages().send(viewer, "gui.common.no-permission");
            return;
        }
        if (MenuListener.instance().rejectReload(viewer)) return;
        refresh();
        viewer.openInventory(inventory);
        MenuListener.instance().play(viewer, MenuListener.instance().sounds().open());
    }
    /** Placement editors must snapshot their real items into their draft before rebuilding. */
    public final void refresh() {
        int rows = preferredRows();
        boolean resized = inventory.getSize() != rows * 9;
        boolean viewing = resized && viewer.getOpenInventory().getTopInventory() == inventory;
        if (resized) inventory = Bukkit.createInventory(this, rows * 9, title);
        buttons.clear();
        inventory.clear();
        GuiTheme.frame(this);
        set(4, GuiTheme.information(borderMaterial(), title, java.util.List.of()));
        render();
        renderHeader();
        GuiTheme.navBar(this, onSave(), hasPreviousPage(), hasNextPage());
        renderFooter();
        if (viewing) MenuListener.instance().later(() -> viewer.openInventory(inventory));
    }
    protected int preferredRows() { return inventory.getSize() / 9; }
    protected void renderHeader() {
        var messages = MenuListener.instance().messages();
        GuiTheme.help(this, java.util.List.of(messages.get("gui.common.help-browse"),
                messages.get("gui.common.help-edit"), messages.get("gui.common.help-save")));
    }
    protected void renderFooter() {}
    protected boolean hasUnsavedChanges() { return false; }
    protected @Nullable Menu parent() { return null; }
    protected @Nullable Runnable onSave() { return null; }
    protected boolean hasPreviousPage() { return false; }
    protected boolean hasNextPage() { return false; }
    protected void previousPage() {}
    protected void nextPage() {}

    /** Layout reservations are independent of permission, edit locks and stored template items. */
    protected Set<Integer> reservedInputSlots() { return Set.of(); }
    /** Only top-inventory slots explicitly designated by concrete editors accept real items. */
    public boolean allowsPlacement(int slot) { return false; }
    @Override public final Inventory getInventory() { return inventory; }
    final @Nullable Button buttonAt(int slot) { return buttons.get(slot); }
}
