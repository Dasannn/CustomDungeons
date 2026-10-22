package dev.dasan.customdungeons.session;

import dev.dasan.customdungeons.model.Point;
import java.util.*;
import java.util.function.Predicate;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.Inventory;

/** Every inventory route rechecks the same authorization; vanilla handles shared stack transfers. */
public final class LootChestListener implements Listener {
    private final LootChestService loot;
    private final Predicate<Point> registered;
    LootChestListener(SessionManager manager) {this(manager.loot(),manager::lootRegistered);}
    LootChestListener(LootChestService loot,Predicate<Point> registered) {this.loot=loot;this.registered=registered;}
    @EventHandler public void chunkLoaded(org.bukkit.event.world.ChunkLoadEvent event) {loot.chunksAvailable(event.getWorld().getName());}
    @EventHandler public void worldLoaded(org.bukkit.event.world.WorldLoadEvent event) {loot.chunksAvailable(event.getWorld().getName());}
    private boolean protectedAt(Point p) {return p!=null && (loot.contains(p) || registered.test(p));}
    private Point point(Block block) {return block==null?null:LootChestService.point(block.getLocation());}
    private boolean protectedBlock(Block block) {return protectedAt(point(block));}
    private Point point(Inventory inventory) {
        var owned=loot.inventoryPoint(inventory);if(owned!=null)return owned;
        var at=inventory.getLocation();
        if(at!=null && protectedAt(LootChestService.point(at)))return LootChestService.point(at);
        if(inventory.getHolder() instanceof org.bukkit.block.DoubleChest doubleChest) {
            for(var side:new org.bukkit.inventory.InventoryHolder[]{doubleChest.getLeftSide(),doubleChest.getRightSide()})
                if(side instanceof org.bukkit.block.Chest chest && protectedBlock(chest.getBlock()))return point(chest.getBlock());
        }
        return null;
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void open(InventoryOpenEvent event) {
        var p=point(event.getInventory());
        if(p!=null && (!(event.getPlayer() instanceof Player player) || !loot.allowed(p,player,false)))event.setCancelled(true);
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void click(InventoryClickEvent event) {
        var p=point(event.getView().getTopInventory());
        if(p!=null && (event instanceof InventoryCreativeEvent || event.getClick()==ClickType.MIDDLE || event.getAction()==InventoryAction.CLONE_STACK
                || !(event.getWhoClicked() instanceof Player player) || !loot.allowed(p,player,true)))event.setCancelled(true);
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void drag(InventoryDragEvent event) {
        var p=point(event.getView().getTopInventory());
        if(p!=null && (!(event.getWhoClicked() instanceof Player player) || !loot.allowed(p,player,true)))event.setCancelled(true);
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void transfer(InventoryMoveItemEvent event) {
        if(point(event.getSource())!=null || point(event.getDestination())!=null)event.setCancelled(true);
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void pickup(InventoryPickupItemEvent event) {if(point(event.getInventory())!=null)event.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void interact(PlayerInteractEvent event) {
        var p=point(event.getClickedBlock());
        if(protectedAt(p) && !loot.allowed(p,event.getPlayer(),false))event.setUseInteractedBlock(Event.Result.DENY);
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void breakBlock(BlockBreakEvent event) {if(protectedBlock(event.getBlock()))event.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void place(BlockPlaceEvent event) {
        if(protectedBlock(event.getBlockPlaced())) {event.setCancelled(true);return;}
        var type=event.getBlockPlaced().getType();
        if(type==Material.CHEST || type==Material.TRAPPED_CHEST)for(var face:List.of(org.bukkit.block.BlockFace.NORTH,org.bukkit.block.BlockFace.SOUTH,org.bukkit.block.BlockFace.EAST,org.bukkit.block.BlockFace.WEST))
            if(protectedBlock(event.getBlockPlaced().getRelative(face)))event.setCancelled(true);
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void explode(EntityExplodeEvent event) {event.blockList().removeIf(this::protectedBlock);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void explode(BlockExplodeEvent event) {if(protectedBlock(event.getBlock()))event.setCancelled(true);else event.blockList().removeIf(this::protectedBlock);}
    private boolean piston(List<Block> blocks,org.bukkit.block.BlockFace direction) {
        return blocks.stream().anyMatch(b->protectedBlock(b) || protectedBlock(b.getRelative(direction)) || protectedBlock(b.getRelative(direction.getOppositeFace())));
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void extend(BlockPistonExtendEvent event) {if(piston(event.getBlocks(),event.getDirection()))event.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void retract(BlockPistonRetractEvent event) {if(piston(event.getBlocks(),event.getDirection()))event.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void change(EntityChangeBlockEvent event) {if(protectedBlock(event.getBlock()))event.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void pick(io.papermc.paper.event.player.PlayerPickBlockEvent event) {if(protectedBlock(event.getBlock()))event.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void destroy(com.destroystokyo.paper.event.block.BlockDestroyEvent event) {if(protectedBlock(event.getBlock()))event.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void dispense(BlockDispenseLootEvent event) {if(protectedBlock(event.getBlock()))event.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void physics(BlockPhysicsEvent event) {if(protectedBlock(event.getBlock()))event.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void burn(BlockBurnEvent event) {if(protectedBlock(event.getBlock()))event.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void flow(BlockFromToEvent event) {if(protectedBlock(event.getToBlock()))event.setCancelled(true);}
    @EventHandler(priority=EventPriority.MONITOR)
    public void death(PlayerDeathEvent event) {loot.revoke(event.getEntity().getUniqueId());}
    @EventHandler(priority=EventPriority.MONITOR)
    public void quit(PlayerQuitEvent event) {loot.revoke(event.getPlayer().getUniqueId());}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void mode(PlayerGameModeChangeEvent event) {
        if(event.getNewGameMode()==org.bukkit.GameMode.SPECTATOR)loot.revoke(event.getPlayer().getUniqueId());
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void move(PlayerMoveEvent event) {departing(event.getPlayer(),event.getTo());}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void teleport(PlayerTeleportEvent event) {departing(event.getPlayer(),event.getTo());}
    private void departing(Player player,org.bukkit.Location to) {
        if(to!=null)loot.departing(player,to);
    }
}
