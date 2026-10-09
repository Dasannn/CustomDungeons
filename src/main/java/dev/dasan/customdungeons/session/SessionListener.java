package dev.dasan.customdungeons.session;

import dev.dasan.customdungeons.mob.MobKeys;
import dev.dasan.customdungeons.model.Trigger;
import dev.dasan.customdungeons.runtime.ActiveMob;
import java.util.*;
import org.bukkit.Location;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import com.destroystokyo.paper.event.entity.EntityRemoveFromWorldEvent;

/** Events route to the owning session; no work for unrelated players/entities. */
public final class SessionListener implements Listener {
    private final SessionManager manager;
    private final Map<UUID,dev.dasan.customdungeons.model.Point> respawns=new HashMap<>();
    public SessionListener(SessionManager manager) { this.manager=manager; }
    private boolean camera(Player player) {
        // Activation writes this marker before the spectator mutation, and confirmation releases it.
        return CinematicRecovery.pending(player);
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=false)
    public void restoreGameMode(PlayerGameModeChangeEvent event) {
        var recovery=manager.cinematics();
        // Only the scoped last-resort restoration may override a third-party veto.
        if(recovery!=null && recovery.forcingMode(event.getPlayer(),event.getNewGameMode()))event.setCancelled(false);
    }
    private Optional<DungeonSession> owner(Entity entity) {
        if(dev.dasan.customdungeons.ability.combat.CombatService.isDecoySource(entity))return Optional.empty();
        String id=entity.getPersistentDataContainer().get(MobKeys.SESSION,PersistentDataType.STRING);
        if (id == null) return Optional.empty();
        return managerSession(id);
    }
    private Optional<DungeonSession> managerSession(String id) { return manager.byId(id); }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void move(PlayerMoveEvent event) {
        Location to=event.getTo(), from=event.getFrom();
        if (to == null || from.getBlockX()==to.getBlockX() && from.getBlockY()==to.getBlockY() && from.getBlockZ()==to.getBlockZ() && Objects.equals(from.getWorld(),to.getWorld())) return;
        manager.sessionOf(event.getPlayer().getUniqueId()).ifPresent(session -> {
            if(session.introActive() && manager.runtime(session).cinematic.contains(event.getPlayer().getUniqueId()) && !manager.authorized(event.getPlayer()))event.setTo(from);
            else if (blocked(session,to) && !manager.authorized(event.getPlayer()))event.setTo(DungeonSessionRuntime.location(session.checkpoint()));
        });
    }
    private boolean blocked(DungeonSession session,Location at) {
        if (session.state().state()!=SessionState.RUNNING) return false;
        for (int i=session.roomIndex()+1;i<session.def().rooms().size();i++)
            if (DungeonSessionRuntime.contains(session.def().rooms().get(i).region(),at)) return true;
        return false;
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void teleport(PlayerTeleportEvent event) {
        if (manager.authorized(event.getPlayer()) || event.getTo()==null) return;
        manager.sessionOf(event.getPlayer().getUniqueId()).ifPresent(session -> {
            if (session.introActive() && manager.runtime(session).cinematic.contains(event.getPlayer().getUniqueId()) || blocked(session,event.getTo()) || !Objects.equals(event.getTo().getWorld(),event.getFrom().getWorld()) && carriesKey(event.getPlayer())) event.setCancelled(true);
        });
    }
    private static boolean carriesKey(Player player) {
        for (ItemStack item : player.getInventory().getContents()) if (KeyService.isKey(item)) return true;
        return KeyService.isKey(player.getItemOnCursor());
    }
    @EventHandler public void death(PlayerDeathEvent event) {
        if(manager.disconnectDeath(event))return;
        manager.sessionOf(event.getEntity().getUniqueId()).ifPresent(session -> {
            var runtime=manager.runtime(session);
            runtime.ambience.remove(event.getEntity());
            event.setKeepInventory(session.def().keepInventory()); event.setKeepLevel(session.def().keepInventory());
            if (session.def().keepInventory()) { event.getDrops().clear(); event.setDroppedExp(0); }
            else {
                event.getDrops().removeIf(runtime.keys::belongsToSession);
                runtime.keys.died(event.getEntity());
            }
            var exit=session.returnPoint(event.getEntity());
            session.playerDied(event.getEntity().getUniqueId());
            boolean eliminated=session.livesLeft(event.getEntity().getUniqueId())==0;
            if (eliminated) event.getDrops().removeIf(KeyService::isKey);
            respawns.put(event.getEntity().getUniqueId(),exit);
        });
    }
    dev.dasan.customdungeons.model.Point pendingRespawn(UUID player) {
        var exit=respawns.get(player);
        if (exit==null) return null;
        return manager.sessionOf(player)
            .filter(s -> s.livesLeft(player)>0 && (s.state().state()==SessionState.RUNNING || s.state().state()==SessionState.LOBBY))
            .map(DungeonSession::checkpoint).orElse(exit);
    }
    @EventHandler(priority=EventPriority.HIGHEST) public void respawn(PlayerRespawnEvent event) {
        if(manager.disconnectRespawn(event)) {respawns.remove(event.getPlayer().getUniqueId());return;}
        var point=pendingRespawn(event.getPlayer().getUniqueId());
        respawns.remove(event.getPlayer().getUniqueId());
        if (point!=null) event.setRespawnLocation(DungeonSessionRuntime.location(point));
    }
    @EventHandler public void drop(ItemSpawnEvent event) { manager.disconnectDrop(event); }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void ambienceMove(PlayerMoveEvent event) {
        if(event.getTo()!=null)manager.sessionOf(event.getPlayer().getUniqueId()).ifPresent(s->manager.runtime(s).ambience.moved(s,event.getPlayer(),event.getTo()));
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void ambienceTeleport(PlayerTeleportEvent event) {
        if(event.getTo()!=null)manager.sessionOf(event.getPlayer().getUniqueId()).ifPresent(s->manager.runtime(s).ambience.moved(s,event.getPlayer(),event.getTo()));
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void ambiencePotion(EntityPotionEffectEvent event) {
        if(event.getEntity() instanceof Player p)manager.sessionOf(p.getUniqueId()).ifPresent(s->manager.runtime(s).ambience.changed(p,event));
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void spectate(com.destroystokyo.paper.event.player.PlayerStartSpectatingEntityEvent event) {
        manager.sessionOf(event.getPlayer().getUniqueId()).ifPresent(s->{
            if(s.introActive() && manager.runtime(s).cinematic.contains(event.getPlayer().getUniqueId()))event.setCancelled(true);
        });
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void sneak(PlayerToggleSneakEvent event) {
        if(event.isSneaking())manager.sessionOf(event.getPlayer().getUniqueId()).ifPresent(s-> {
            if(s.introActive())manager.runtime(s).cinematic.skip(event.getPlayer().getUniqueId());
        });
    }
    @EventHandler public void changedWorld(PlayerChangedWorldEvent event) { manager.worldChanged(event.getPlayer()); }
    @EventHandler public void quit(PlayerQuitEvent event) { respawns.remove(event.getPlayer().getUniqueId()); manager.disconnected(event.getPlayer(),event.getReason()); }
    @EventHandler public void join(PlayerJoinEvent event) { manager.joined(event.getPlayer()); }
    @EventHandler(priority=EventPriority.HIGHEST)
    public void mobDeath(EntityDeathEvent event) {
        owner(event.getEntity()).ifPresent(session -> {
            ActiveMob mob=session.mob(event.getEntity().getUniqueId());
            if (mob==null) return;
            if (!mob.template().vanillaDrops()) { event.getDrops().clear(); event.setDroppedExp(0); }
            session.mobRemoved(event.getEntity().getUniqueId(),event);
        });
    }
    @EventHandler public void removed(EntityRemoveFromWorldEvent event) {
        owner(event.getEntity()).ifPresent(session -> session.mobRemoved(event.getEntity().getUniqueId(),event));
        if (event.getEntity() instanceof Item item) for (DungeonSession session : manager.activeSessions()) {
            var runtime=manager.runtime(session); runtime.keys.removed(item); runtime.removedDrop(item);
        }
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void protectTestAdmin(EntityDamageEvent event) {
        if(event.getEntity() instanceof Player player) manager.sessionOf(player.getUniqueId()).ifPresent(session -> {
            if(session.introActive() || session.isTestInvulnerable(player.getUniqueId())) {
                if(event instanceof EntityDamageByEntityEvent) event.setDamage(0);
                else event.setCancelled(true);
            }
        });
    }
    @EventHandler(priority=EventPriority.HIGH,ignoreCancelled=true)
    public void damaged(EntityDamageEvent event) {
        owner(event.getEntity()).ifPresent(session -> {
            var mob=session.mob(event.getEntity().getUniqueId()); if (mob==null) return;
            if (session.scheduler().currentTick()<mob.invulnerableUntil()) { event.setCancelled(true); return; }
            manager.runtime(session).abilities.fire(Trigger.ON_DAMAGED,mob,event,session.scheduler().currentTick());
        });
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void postDamage(EntityDamageEvent event) {
        owner(event.getEntity()).ifPresent(session -> {
            var mob=session.mob(event.getEntity().getUniqueId());
            if (mob!=null && mob.template().boss()) session.scheduler().runLater(1,() -> manager.runtime(session).bosses.onDamaged(mob,session.scheduler().currentTick()));
        });
    }
    @EventHandler(priority=EventPriority.HIGH,ignoreCancelled=true)
    public void hit(EntityDamageByEntityEvent event) {
        Entity attacker=event.getDamager();
        if (attacker instanceof Projectile projectile && projectile.getShooter() instanceof Entity shooter) attacker=shooter;
        Entity source=attacker;
        if(source instanceof Player player && camera(player)){event.setCancelled(true);return;}
        owner(source).ifPresent(session -> {
            if (event.getEntity() instanceof Player target && !session.survivors().contains(target.getUniqueId())) { event.setCancelled(true); return; }
            var mob=session.mob(source.getUniqueId()); if (mob==null) return;
            // Zero before dispatch too: the HIGHEST protection handler runs after this HIGH handler.
            if(event.getEntity() instanceof Player target && session.isTestInvulnerable(target.getUniqueId())) event.setDamage(0);
            manager.runtime(session).abilities.fire(Trigger.ON_HIT,mob,event,session.scheduler().currentTick());
        });
    }
    @EventHandler(ignoreCancelled=true) public void target(EntityTargetLivingEntityEvent event) {
        owner(event.getEntity()).ifPresent(session -> {
            if (event.getTarget()!=null && (!(event.getTarget() instanceof Player player) || !session.survivors().contains(player.getUniqueId()))) event.setCancelled(true);
        });
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void pickup(EntityPickupItemEvent event) {
        if(event.getEntity() instanceof Player player && camera(player)){event.setCancelled(true);return;}
        Item item=event.getItem();
        if (KeyService.isKey(item.getItemStack())) {
            var session=event.getEntity() instanceof Player player ? manager.sessionOf(player.getUniqueId()).orElse(null) : null;
            if (session==null || !manager.runtime(session).keys.matches(item.getItemStack())) { event.setCancelled(true); return; }
        }
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void merge(ItemMergeEvent event) {
        if(DisconnectService.penaltyDrop(event.getEntity()) || DisconnectService.penaltyDrop(event.getTarget())) {
            event.setCancelled(true);return;
        }
        for (DungeonSession session : manager.activeSessions()) {
            var runtime=manager.runtime(session);
            if (runtime.tracksDrop(event.getEntity().getUniqueId()) || runtime.tracksDrop(event.getTarget().getUniqueId())) {
                event.setCancelled(true); return;
            }
        }
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void pickedUp(EntityPickupItemEvent event) {
        // A partially collected stack remains in the world; stolen items use their remaining amount.
        for (DungeonSession session : manager.activeSessions()) {
            manager.runtime(session).pickedUp(event.getItem(),event.getRemaining());
            if (event.getEntity() instanceof Player player && event.getRemaining()==0) manager.runtime(session).keys.pickedUp(event.getItem(),player);
        }
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void containerPickup(InventoryPickupItemEvent event) { if (KeyService.isKey(event.getItem().getItemStack())) event.setCancelled(true); }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void containerPickedUp(InventoryPickupItemEvent event) {
        for (DungeonSession session : manager.activeSessions()) manager.runtime(session).containerPickup(event.getItem());
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void click(InventoryClickEvent event) {
        if(event.getWhoClicked() instanceof Player player && camera(player)){event.setCancelled(true);return;}
        boolean involved=KeyService.isKey(event.getCurrentItem()) || KeyService.isKey(event.getCursor());
        if (event.getWhoClicked() instanceof Player player && event.getHotbarButton()>=0)
            involved |= KeyService.isKey(player.getInventory().getItem(event.getHotbarButton()));
        if (event.getClick()==ClickType.SWAP_OFFHAND && event.getWhoClicked() instanceof Player player)
            involved |= KeyService.isKey(player.getInventory().getItemInOffHand());
        if (involved && (event.getView().getTopInventory().getType()!=InventoryType.CRAFTING || event.getRawSlot()<event.getView().getTopInventory().getSize() || event.getSlotType()==InventoryType.SlotType.OUTSIDE)) event.setCancelled(true);
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void drag(InventoryDragEvent event) {
        if(event.getWhoClicked() instanceof Player player && camera(player)){event.setCancelled(true);return;}
        if (KeyService.isKey(event.getOldCursor()) && (event.getView().getTopInventory().getType()!=InventoryType.CRAFTING || event.getRawSlots().stream().anyMatch(s -> s<event.getView().getTopInventory().getSize()))) event.setCancelled(true);
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=false)
    public void interact(PlayerInteractEvent event) {
        if(camera(event.getPlayer())) {
            event.setUseInteractedBlock(Event.Result.DENY);event.setUseItemInHand(Event.Result.DENY);event.setCancelled(true);return;
        }
        if (event.getAction()==org.bukkit.event.block.Action.PHYSICAL && event.getClickedBlock()!=null
            && event.getClickedBlock().getType()==org.bukkit.Material.POLISHED_BLACKSTONE_PRESSURE_PLATE) {
            manager.exitPlate(event.getPlayer());
            return;
        }
        if (!KeyService.isKey(event.getItem())) return;
        // Deny vanilla use even for stale/foreign keys and interactions already denied by WorldGuard.
        event.setUseInteractedBlock(Event.Result.DENY);
        event.setUseItemInHand(Event.Result.DENY);
        if (event.getAction()!=org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK && event.getAction()!=org.bukkit.event.block.Action.RIGHT_CLICK_AIR) return;
        manager.sessionOf(event.getPlayer().getUniqueId()).ifPresent(session ->
            manager.runtime(session).keys.use(event.getPlayer(),event.getClickedBlock(),event.getItem()));
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=false)
    public void place(org.bukkit.event.block.BlockPlaceEvent event) {
        if (camera(event.getPlayer()) || KeyService.isKey(event.getItemInHand())) event.setCancelled(true);
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=false)
    public void open(InventoryOpenEvent event) {
        if(event.getPlayer() instanceof Player player && camera(player))event.setCancelled(true);
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=false)
    public void interactEntity(PlayerInteractEntityEvent event) {if(camera(event.getPlayer()))event.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=false)
    public void interactAtEntity(PlayerInteractAtEntityEvent event) {if(camera(event.getPlayer()))event.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=false)
    public void breakBlock(org.bukkit.event.block.BlockBreakEvent event) {if(camera(event.getPlayer()))event.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=false)
    public void cameraDrop(PlayerDropItemEvent event) {if(camera(event.getPlayer()))event.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=false)
    public void swap(PlayerSwapHandItemsEvent event) {if(camera(event.getPlayer()))event.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=false)
    public void bucketEmpty(PlayerBucketEmptyEvent event) {if(camera(event.getPlayer()))event.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=false)
    public void bucketFill(PlayerBucketFillEvent event) {if(camera(event.getPlayer()))event.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=false)
    public void consume(PlayerItemConsumeEvent event) {if(camera(event.getPlayer()))event.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=false)
    public void shoot(EntityShootBowEvent event) {if(event.getEntity() instanceof Player player && camera(player))event.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=false)
    public void manipulate(PlayerArmorStandManipulateEvent event) {if(camera(event.getPlayer()))event.setCancelled(true);}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void dropped(PlayerDropItemEvent event) {
        manager.sessionOf(event.getPlayer().getUniqueId()).ifPresent(session -> manager.runtime(session).keys.playerDropped(event.getItemDrop(),event.getPlayer()));
    }
    @EventHandler public void itemSpawn(ItemSpawnEvent event) {
        if (!KeyService.isKey(event.getEntity().getItemStack())) return;
        for (DungeonSession session : manager.activeSessions()) {
            var keys=manager.runtime(session).keys;
            if (keys.matches(event.getEntity().getItemStack())) {
                keys.spawned(event.getEntity()); return;
            }
        }
        // A key whose session has ended must never survive in the world.
        event.setCancelled(true);
    }
}
