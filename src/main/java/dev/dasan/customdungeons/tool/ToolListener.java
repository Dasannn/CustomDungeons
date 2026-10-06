package dev.dasan.customdungeons.tool;

import dev.dasan.customdungeons.CustomDungeonsPlugin;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.event.world.EntitiesUnloadEvent;
import org.bukkit.inventory.ItemStack;

/** Bukkit event routing and inventory boundaries for PDC tools. */
public final class ToolListener implements Listener {
    private final CustomDungeonsPlugin plugin;
    private final ToolService tools;
    private final PreviewRenderer previews;
    private final SpawnerMarkers markers;

    public ToolListener(CustomDungeonsPlugin plugin, ToolService tools, PreviewRenderer previews, SpawnerMarkers markers) {
        this.plugin = plugin;
        this.tools = tools;
        this.previews = previews;
        this.markers = markers;
    }
    private void refreshAfterEvent() {
        plugin.getServer().getScheduler().runTask(plugin, previews::refresh);
    }
    // Do not ignore cancelled interactions: protected regions and right-click-air may cancel vanilla use.
    @EventHandler(priority = EventPriority.HIGHEST)
    public void interact(PlayerInteractEvent event) {
        if (event.getAction() == Action.PHYSICAL) return;
        Player player = event.getPlayer();
        ItemStack held = player.getInventory().getItemInMainHand();
        if (!ToolService.isTool(held) && !ToolService.isTool(event.getItem())) return;
        event.setCancelled(true);
        event.setUseItemInHand(Event.Result.DENY);
        event.setUseInteractedBlock(Event.Result.DENY);
        if (event.getHand() != org.bukkit.inventory.EquipmentSlot.HAND) return;
        ToolType type = ToolService.type(held);
        if (type == null) return;
        if (!tools.allowed(player)) return;
        Action action = event.getAction();
        if ((type == ToolType.REGION || type == ToolType.DOOR) && event.getClickedBlock() != null) {
            if (action == Action.LEFT_CLICK_BLOCK || action == Action.RIGHT_CLICK_BLOCK)
                tools.select(player, event.getClickedBlock().getLocation(), action == Action.LEFT_CLICK_BLOCK);
        } else if (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK) {
            if (type == ToolType.POINT) tools.point(player, player.getLocation());
            else if (type == ToolType.SPAWNER && event.getClickedBlock() != null) {
                var location = event.getClickedBlock().getRelative(event.getBlockFace()).getLocation().add(0.5, 0, 0.5);
                location.setYaw(player.getLocation().getYaw());
                location.setPitch(player.getLocation().getPitch());
                tools.point(player, location);
            }
        }
    }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void drop(PlayerDropItemEvent event) {
        if (ToolService.isTool(event.getItemDrop().getItemStack())) event.setCancelled(true);
        refreshAfterEvent();
    }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void click(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        boolean current = ToolService.isTool(event.getCurrentItem());
        boolean cursor = ToolService.isTool(event.getCursor());
        boolean hotbar = event.getHotbarButton() >= 0
                && ToolService.isTool(player.getInventory().getItem(event.getHotbarButton()));
        boolean offhand = event.getClick() == ClickType.SWAP_OFFHAND
                && ToolService.isTool(player.getInventory().getItemInOffHand());
        if (current || cursor || hotbar || offhand) {
            boolean ownStorage = event.getClickedInventory() == player.getInventory()
                    && (event.getSlot() < 36 || event.getSlot() == 40);
            // Shift, creative cloning, throwing and crafting/equipment slots never move tools.
            boolean bundle = event.getCurrentItem() != null && event.getCurrentItem().getItemMeta() instanceof org.bukkit.inventory.meta.BundleMeta
                    || event.getCursor() != null && event.getCursor().getItemMeta() instanceof org.bukkit.inventory.meta.BundleMeta;
            if (!ownStorage || bundle || event.isShiftClick() || event.getClick() == ClickType.MIDDLE
                    || event.getClick() == ClickType.DROP || event.getClick() == ClickType.CONTROL_DROP
                    || event.getAction() == InventoryAction.CLONE_STACK
                    || event.getAction() == InventoryAction.COLLECT_TO_CURSOR)
                event.setCancelled(true);
        }
        refreshAfterEvent();
    }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void creative(InventoryCreativeEvent event) {
        if (ToolService.isTool(event.getCursor()) || ToolService.isTool(event.getCurrentItem())) event.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void drag(InventoryDragEvent event) {
        if (ToolService.isTool(event.getOldCursor())) {
            int top = event.getView().getTopInventory().getSize();
            if (event.getRawSlots().stream().anyMatch(slot -> slot < top)) event.setCancelled(true);
        }
        refreshAfterEvent();
    }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void pickup(InventoryPickupItemEvent event) {
        if (ToolService.isTool(event.getItem().getItemStack())) event.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void entityPickup(EntityPickupItemEvent event) {
        if (!ToolService.isTool(event.getItem().getItemStack())) return;
        if (!(event.getEntity() instanceof Player player) || !player.hasPermission("customdungeons.admin.tools")) {
            event.setCancelled(true);
            return;
        }
        ToolType type = ToolService.type(event.getItem().getItemStack());
        for (ItemStack item : player.getInventory().getContents()) {
            if (type != null && ToolService.type(item) == type) { event.setCancelled(true); return; }
        }
        refreshAfterEvent();
    }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void death(PlayerDeathEvent event) {
        event.getDrops().removeIf(ToolService::isTool);
        event.getItemsToKeep().removeIf(ToolService::isTool);
        var player = event.getEntity();
        for (int i = 0; i < player.getInventory().getSize(); i++)
            if (ToolService.isTool(player.getInventory().getItem(i))) player.getInventory().setItem(i, null);
        if (ToolService.isTool(player.getItemOnCursor())) player.setItemOnCursor(null);
        tools.clear(player.getUniqueId());
        refreshAfterEvent();
    }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void marker(PlayerInteractEntityEvent event) {
        if (markers.spawnerAt(event.getRightClicked()) == null) return;
        event.setCancelled(true);
        if (event.getHand() == org.bukkit.inventory.EquipmentSlot.HAND) markers.click(event.getPlayer(), event.getRightClicked());
    }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void armorStand(PlayerArmorStandManipulateEvent event) {
        if (ToolService.isTool(event.getPlayerItem()) || ToolService.isTool(event.getArmorStandItem())) event.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void entityUse(PlayerInteractEntityEvent event) {
        ItemStack item = event.getHand() == org.bukkit.inventory.EquipmentSlot.HAND
                ? event.getPlayer().getInventory().getItemInMainHand() : event.getPlayer().getInventory().getItemInOffHand();
        if (ToolService.isTool(item)) event.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void damage(EntityDamageByEntityEvent event) {
        if (markers.spawnerAt(event.getEntity()) != null) event.setCancelled(true);
        if (event.getDamager() instanceof Player player && ToolService.isTool(player.getInventory().getItemInMainHand()))
            event.setCancelled(true);
    }
    @EventHandler public void held(PlayerItemHeldEvent event) { refreshAfterEvent(); }
    @EventHandler public void swap(PlayerSwapHandItemsEvent event) { refreshAfterEvent(); }
    @EventHandler public void closeInventory(InventoryCloseEvent event) { refreshAfterEvent(); }
    @EventHandler public void join(PlayerJoinEvent event) { markers.refreshVisibility(event.getPlayer()); refreshAfterEvent(); }
    @EventHandler public void respawn(PlayerRespawnEvent event) { refreshAfterEvent(); }
    @EventHandler public void world(PlayerChangedWorldEvent event) { tools.clear(event.getPlayer().getUniqueId()); refreshAfterEvent(); }
    @EventHandler public void quit(PlayerQuitEvent event) { tools.clear(event.getPlayer().getUniqueId()); refreshAfterEvent(); }
    @EventHandler public void chunkUnload(ChunkUnloadEvent event) { markers.unload(event.getChunk()); }
    @EventHandler public void entitiesUnload(EntitiesUnloadEvent event) { markers.unload(event.getChunk()); }
    @EventHandler public void disable(PluginDisableEvent event) { if (event.getPlugin() == plugin) tools.close(); }
}
