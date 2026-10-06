package dev.dasan.customdungeons.tool;

import dev.dasan.customdungeons.CustomDungeonsPlugin;
import dev.dasan.customdungeons.gui.Menu;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;

/** Inventory boundaries remain closed during both durable entry and asynchronous crash recovery. */
public final class BuildModeListener implements Listener {
    private final CustomDungeonsPlugin plugin;
    private final BuildModeService mode;
    public BuildModeListener(CustomDungeonsPlugin plugin,BuildModeService mode) {this.plugin=plugin;this.mode=mode;}
    private boolean protectedPlayer(Player player) {return mode.protects(player.getUniqueId());}
    @EventHandler(priority=EventPriority.HIGHEST)
    public void interact(PlayerInteractEvent event) {
        if(!protectedPlayer(event.getPlayer())&&!BuildTools.isTool(event.getItem())) return;
        event.setCancelled(true);event.setUseItemInHand(Event.Result.DENY);event.setUseInteractedBlock(Event.Result.DENY);
        if(event.getHand()==org.bukkit.inventory.EquipmentSlot.HAND&&event.getAction()!=Action.PHYSICAL) mode.interact(event);
    }
    @EventHandler(priority=EventPriority.HIGHEST)
    public void click(InventoryClickEvent event) {
        if(!(event.getWhoClicked() instanceof Player player)) return;
        boolean hotbar=event.getHotbarButton()>=0&&BuildTools.isTool(player.getInventory().getItem(event.getHotbarButton()));
        if(protectedPlayer(player)||BuildTools.isTool(event.getCurrentItem())||BuildTools.isTool(event.getCursor())||hotbar
                ||event.getClick()==ClickType.SWAP_OFFHAND&&BuildTools.isTool(player.getInventory().getItemInOffHand())) event.setCancelled(true);
    }
    @EventHandler(priority=EventPriority.HIGHEST)
    public void drag(InventoryDragEvent event) {
        if(event.getWhoClicked() instanceof Player player&&(protectedPlayer(player)||BuildTools.isTool(event.getOldCursor()))) event.setCancelled(true);
    }
    @EventHandler(priority=EventPriority.HIGHEST)
    public void drop(PlayerDropItemEvent event) {
        if(protectedPlayer(event.getPlayer())||BuildTools.isTool(event.getItemDrop().getItemStack())) event.setCancelled(true);
    }
    @EventHandler(priority=EventPriority.HIGHEST)
    public void swap(PlayerSwapHandItemsEvent event) {
        if(protectedPlayer(event.getPlayer())||BuildTools.isTool(event.getMainHandItem())||BuildTools.isTool(event.getOffHandItem())) event.setCancelled(true);
    }
    @EventHandler(priority=EventPriority.HIGHEST)
    public void open(InventoryOpenEvent event) {
        if(event.getPlayer() instanceof Player player&&protectedPlayer(player)&&!(event.getInventory().getHolder() instanceof Menu)) event.setCancelled(true);
    }
    @EventHandler(priority=EventPriority.HIGHEST)
    public void pickup(EntityPickupItemEvent event) {
        if(BuildTools.isTool(event.getItem().getItemStack())||event.getEntity() instanceof Player player&&protectedPlayer(player)) event.setCancelled(true);
    }
    @EventHandler(priority=EventPriority.HIGHEST)
    public void hopper(InventoryPickupItemEvent event) {if(BuildTools.isTool(event.getItem().getItemStack()))event.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST)
    public void entityUse(PlayerInteractEntityEvent event) {if(protectedPlayer(event.getPlayer()))event.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST)
    public void armorStand(PlayerArmorStandManipulateEvent event) {
        if(protectedPlayer(event.getPlayer())||BuildTools.isTool(event.getPlayerItem())||BuildTools.isTool(event.getArmorStandItem()))event.setCancelled(true);
    }
    @EventHandler(priority=EventPriority.HIGHEST)
    public void damage(EntityDamageByEntityEvent event) {
        if(event.getDamager() instanceof Player player&&protectedPlayer(player))event.setCancelled(true);
    }
    @EventHandler(priority=EventPriority.HIGHEST)
    public void breakBlock(BlockBreakEvent event) {if(protectedPlayer(event.getPlayer()))event.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST)
    public void placeBlock(BlockPlaceEvent event) {if(protectedPlayer(event.getPlayer()))event.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST)
    public void consume(PlayerItemConsumeEvent event) {if(protectedPlayer(event.getPlayer()))event.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST)
    public void death(PlayerDeathEvent event) {
        if(!protectedPlayer(event.getEntity()))return;
        event.setKeepInventory(true);event.getDrops().clear();event.getItemsToKeep().clear();mode.exit(event.getEntity());
    }
    @EventHandler public void quit(PlayerQuitEvent event) {mode.disconnected(event.getPlayer());}
    @EventHandler public void join(PlayerJoinEvent event) {mode.recover(event.getPlayer());}
    @EventHandler public void held(PlayerItemHeldEvent event) {
        plugin.getServer().getScheduler().runTask(plugin,()->{var menu=mode.menu(event.getPlayer().getUniqueId());if(menu!=null)menu.actionbar();});
    }
}
