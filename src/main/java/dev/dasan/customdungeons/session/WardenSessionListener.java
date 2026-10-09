package dev.dasan.customdungeons.session;

import dev.dasan.customdungeons.CustomDungeonsPlugin;
import dev.dasan.customdungeons.mob.MobKeys;
import dev.dasan.customdungeons.runtime.ActiveMob;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Warden;
import org.bukkit.event.*;
import org.bukkit.event.entity.*;
import org.bukkit.persistence.PersistentDataType;

/** Dungeon Warden anger policy, excluded from the independent live-test host. */
public final class WardenSessionListener implements Listener, SessionLifecycleListener {
    private final CustomDungeonsPlugin plugin;
    private final Predicate<Entity> isLive;
    private final Set<UUID> wardensWatching = new HashSet<>();
    private boolean watchingLifecycle;

    public WardenSessionListener(CustomDungeonsPlugin plugin, Predicate<Entity> isLive) {
        this.plugin=plugin; this.isLive=isLive;
    }
    @EventHandler(priority=EventPriority.HIGHEST)
    public void target(EntityTargetLivingEntityEvent event) {
        if(event.getEntity() instanceof Warden warden && event.getTarget() instanceof Player player)
            engageWarden(warden, player);
    }
    private void engageWarden(Warden warden,Player target) {
        if(dev.dasan.customdungeons.ability.combat.CombatService.isDecoy(warden))return;
        boolean live=isLive.test(warden);
        if(live) return;
        var sessions=plugin.sessionManager();
        String id=warden.getPersistentDataContainer().get(MobKeys.SESSION,PersistentDataType.STRING);
        if(sessions!=null && id!=null) sessions.sessionOf(target.getUniqueId()).filter(session -> session.id().toString().equals(id)).ifPresent(session -> warden.setAnger(target,150));
    }
    /** One queued refresh per dungeon, driven by its existing ticker; never a Bukkit task per mob. */
    public void watchWardens(DungeonSession session) {
        if(!wardensWatching.add(session.id())) return;
        if(!watchingLifecycle) {
            plugin.sessionManager().addListener(this);
            watchingLifecycle=true;
        }
        session.scheduler().runLater(20,new Runnable() {
            @Override public void run() {
                if(!wardensWatching.contains(session.id())) return;
                var targets=session.players();
                var wardens=session.mobs().stream().map(ActiveMob::entity)
                    .filter(entity -> entity instanceof Warden && !dev.dasan.customdungeons.ability.combat.CombatService.isDecoy(entity) && entity.isValid() && !entity.isDead()).toList();
                if(targets.isEmpty() || wardens.isEmpty()) { wardensWatching.remove(session.id()); return; }
                for(var entity:wardens) for(var target:targets) ((Warden)entity).setAnger(target,150);
                session.scheduler().runLater(20,this);
            }
        });
    }
    @Override public void onFinished(DungeonSession session,
            dev.dasan.customdungeons.storage.RunResult result,Set<UUID> survivors) { wardensWatching.remove(session.id()); }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void spawnedWarden(CreatureSpawnEvent event) {
        if(!(event.getEntity() instanceof Warden warden)||dev.dasan.customdungeons.ability.combat.CombatService.isDecoy(warden)) return;
        var sessions=plugin.sessionManager();
        String id=warden.getPersistentDataContainer().get(MobKeys.SESSION,PersistentDataType.STRING);
        if(sessions!=null && id!=null) Bukkit.getOnlinePlayers().forEach(player -> sessions.sessionOf(player.getUniqueId())
            .filter(session -> session.id().toString().equals(id)).ifPresent(session -> {
                warden.setAnger(player,150); watchWardens(session);
            }));
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void wardenAnger(io.papermc.paper.event.entity.WardenAngerChangeEvent event) {
        if(dev.dasan.customdungeons.ability.combat.CombatService.isDecoy(event.getEntity()))return;
        boolean live=isLive.test(event.getEntity());
        if(live) return;
        var sessions=plugin.sessionManager();
        String id=event.getEntity().getPersistentDataContainer().get(MobKeys.SESSION,PersistentDataType.STRING);
        if(sessions!=null && id!=null && event.getTarget()!=null) sessions.sessionOf(event.getTarget().getUniqueId()).filter(session -> session.id().toString().equals(id)).ifPresent(session -> {
            if(event.getTarget() instanceof Player player && session.survivors().contains(player.getUniqueId())) event.setNewAnger(150);
        });
    }

    @EventHandler public void disable(org.bukkit.event.server.PluginDisableEvent event) {
        if(event.getPlugin()==plugin) wardensWatching.clear();
    }
}
