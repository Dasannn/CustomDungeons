package dev.dasan.customdungeons.gui;

import dev.dasan.customdungeons.CustomDungeonsPlugin;
import dev.dasan.customdungeons.config.PluginConfig;
import dev.dasan.customdungeons.text.Messages;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.server.PluginDisableEvent;

/** Main-thread event routing and the shared GUI services used by concrete editors. */
public final class MenuListener implements Listener {
    private static MenuListener instance;
    private final CustomDungeonsPlugin plugin;
    private final Messages messages;
    private final PluginConfig.GuiSounds sounds;
    private final EditLocks editLocks;

    public MenuListener(CustomDungeonsPlugin plugin, Messages messages,
                        PluginConfig.GuiSounds sounds, EditLocks editLocks) {
        this.plugin = Objects.requireNonNull(plugin);
        this.messages = Objects.requireNonNull(messages);
        this.sounds = Objects.requireNonNull(sounds);
        this.editLocks = Objects.requireNonNull(editLocks);
    }
    /** T04 can load its defaults before T02 exists, without modifying the T01 contracts. */
    public static void register(CustomDungeonsPlugin plugin) {
        String catalog="en".equals(plugin.getConfig().getString("language"))?"messages_en.yml":"messages.yml";
        YamlConfiguration yaml;
        try (var reader = new InputStreamReader(Objects.requireNonNull(plugin.getResource(catalog)),
                StandardCharsets.UTF_8)) {
            yaml = YamlConfiguration.loadConfiguration(reader);
        } catch (java.io.IOException exception) {
            throw new IllegalStateException("Cannot load GUI messages", exception);
        }
        var configured = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), catalog));
        for (String key : configured.getKeys(true)) {
            if (configured.isString(key)) { yaml.set(key, configured.getString(key)); }
        }
        Messages messages = new Messages(plugin.getLogger());
        messages.load(yaml, plugin.getConfig().getString("prefix", ""));
        var section = plugin.getConfig();
        var sounds = new PluginConfig.GuiSounds(section.getString("gui-sounds.click", ""),
                section.getString("gui-sounds.open", ""), section.getString("gui-sounds.save", ""),
                section.getString("gui-sounds.error", ""));
        instance = new MenuListener(plugin, messages, sounds, new EditLocks());
        plugin.getServer().getPluginManager().registerEvents(instance, plugin);
    }
    public static MenuListener instance() {
        return Objects.requireNonNull(instance, "GUI services have not been registered");
    }
    public Messages messages() { return messages; }
    /** Shared guard for menus, inventory events and delayed dialog callbacks. */
    public boolean rejectReload(Player player) {
        var store = plugin.getServer().getServicesManager().load(dev.dasan.customdungeons.config.DefinitionStore.class);
        if (store == null || !store.isReloading()) return false;
        plugin.messages().send(player, "command.reloading");
        return true;
    }
    public PluginConfig.GuiSounds sounds() { return sounds; }
    public EditLocks editLocks() { return editLocks; }
    /** Defer inventory opens/closes from button handlers until after the click event. */
    public void later(Runnable action) { plugin.getServer().getScheduler().runTask(plugin, action); }
    public void play(Player player, String sound) {
        if (sound != null && !sound.isBlank()) { player.playSound(player.getLocation(), sound, 1f, 1f); }
    }
    private boolean writable(Menu menu, int slot) {
        return slot >= 0 && slot < menu.getInventory().getSize()
                && menu.allowsPlacement(slot) && menu.buttonAt(slot) == null;
    }
    private boolean hasWritableSlots(Menu menu) {
        for (int slot = 0; slot < menu.getInventory().getSize(); slot++) {
            if (writable(menu, slot)) { return true; }
        }
        return false;
    }
    private boolean ordinaryAction(InventoryAction action) {
        return switch (action) {
            case PICKUP_ALL, PICKUP_HALF, PICKUP_ONE, PICKUP_SOME,
                    PLACE_ALL, PLACE_ONE, PLACE_SOME, SWAP_WITH_CURSOR, NOTHING -> true;
            default -> false;
        };
    }
    @EventHandler(ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof Menu menu)) { return; }
        event.setCancelled(true);
        if (event.getView().getTopInventory() != menu.getInventory()) return;
        if (!(event.getWhoClicked() instanceof Player player) || !menu.viewer.equals(player)
                || !menu.permitted(player)) { return; }
        if (rejectReload(player)) return;
        int rawSlot = event.getRawSlot();
        boolean top = rawSlot >= 0 && rawSlot < menu.getInventory().getSize();
        if (ordinaryAction(event.getAction()) && (event.isLeftClick() || event.isRightClick())
                && !event.isShiftClick()) {
            if ((top && writable(menu, rawSlot) && menu.allowsNativePlacement(rawSlot))
                    || (!top && rawSlot >= menu.getInventory().getSize() && hasWritableSlots(menu))) {
                event.setCancelled(false);
                return;
            }
        }
        if (top) {
            Button button = menu.buttonAt(rawSlot);
            // Number-key swaps, creative clone, drops and double-click collection never run a button.
            if (button != null && (event.isLeftClick() || event.isRightClick())
                    && event.getClick() != org.bukkit.event.inventory.ClickType.DOUBLE_CLICK) {
                play(player, sounds.click());
                button.onClick().handle(player, event.getClick());
            }
        }
    }
    @EventHandler(ignoreCancelled = true)
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof Menu menu)) { return; }
        event.setCancelled(true);
        if (event.getView().getTopInventory() != menu.getInventory()) return;
        if (!(event.getWhoClicked() instanceof Player player) || !menu.viewer.equals(player)
                || !menu.permitted(player) || !hasWritableSlots(menu)) { return; }
        if (rejectReload(player)) return;
        if (event.getRawSlots().stream().allMatch(slot -> slot >= menu.getInventory().getSize()
                || (writable(menu, slot) && menu.allowsNativePlacement(slot)))) { event.setCancelled(false); }
    }
    @EventHandler
    public void onOpen(InventoryOpenEvent event) {
        if (event.isCancelled() || !(event.getInventory().getHolder() instanceof Menu)) { return; }
        Inputs.abandon(event.getPlayer().getUniqueId());
    }
    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof Menu menu)) { return; }
        Player player = (Player) event.getPlayer();
        if (!plugin.isEnabled()) {
            Inputs.release(player.getUniqueId());
            editLocks.releaseAll(player.getUniqueId());
            return;
        }
        // Only the synchronous close used to show a Dialog belongs to that input flow.
        if (event.getInventory() == menu.getInventory() && Inputs.inventoryClosed(player.getUniqueId())) { return; }
        // Inventory switches close the previous view too. Release only on leaving the editor flow.
        later(() -> {
            if (!(player.getOpenInventory().getTopInventory().getHolder() instanceof Menu)
                    && !Inputs.pending(player.getUniqueId())) { editLocks.releaseAll(player.getUniqueId()); }
        });
    }
    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Inputs.release(event.getPlayer().getUniqueId());
        editLocks.releaseAll(event.getPlayer().getUniqueId());
    }
    @EventHandler
    public void onDisable(PluginDisableEvent event) {
        if (event.getPlugin() != plugin) { return; }
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            Inputs.release(player.getUniqueId());
            editLocks.releaseAll(player.getUniqueId());
            if (player.getOpenInventory().getTopInventory().getHolder() instanceof Menu) { player.closeInventory(); }
        }
        instance = null;
    }
}
