package dev.dasan.customdungeons.session;

import dev.dasan.customdungeons.mob.MobsPlatform;
import dev.dasan.customdungeons.CustomDungeonsPlugin;
import dev.dasan.customdungeons.mob.MobKeys;
import dev.dasan.customdungeons.storage.*;
import java.util.*;
import java.time.Instant;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.world.*;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.persistence.PersistentDataType;

/** Startup-only database waits; T09 already restores blocks during normal session shutdown. */
public final class RecoveryService implements Listener {
    private final CustomDungeonsPlugin plugin;
    private final Storage storage;
    private final SessionManager manager;
    private final List<TempBlockRecord> deferred = new ArrayList<>();
    public RecoveryService(CustomDungeonsPlugin plugin, Storage storage, SessionManager manager) {
        this.plugin=plugin; this.storage=storage; this.manager=manager;
    }
    public void recoverOnEnable() {
        // Wait during onEnable, before registration returns and join commands can run.
        // Includes orphaned runs even if their active_sessions record was never written or already cleared.
        int aborted = storage.abortUnfinishedRuns(Instant.now()).join();
        plugin.getServer().getPluginManager().registerEvents(this,plugin);
        for (var block : storage.loadTempBlocks().join()) {
            if (!restore(block)) deferred.add(block);
            else storage.removeTempBlock(block.world(),block.x(),block.y(),block.z()).join();
        }
        var active=storage.loadActive().join();
        for (var session : active) {
            manager.recoverOccupants(session.dungeonId(),session.players());
            for (UUID player : session.players()) storage.addPendingExit(player,session.exit()).join();
            storage.clearActive(session.sessionId()).join();
        }
        for (World world : Bukkit.getWorlds()) for (Entity entity : world.getEntities()) removeStale(entity);
        for (Player player : Bukkit.getOnlinePlayers()) { cleanKeys(player); manager.connected(player); }
        plugin.getLogger().info("Run recovery: " + active.size() + " interrupted sessions; " + aborted + " original runs aborted; " + deferred.size() + " block records await their worlds");
    }
    private boolean restore(TempBlockRecord record) {
        World world=Bukkit.getWorld(record.world()); if (world == null) return false;
        var block=world.getBlockAt(record.x(),record.y(),record.z());
        dev.dasan.customdungeons.runtime.BlockRestoration.restore(block,record.originalBlockData(),record.placedBlockData());
        return true;
    }
    @EventHandler public void worldLoaded(WorldLoadEvent event) {
        for (var block : List.copyOf(deferred)) if (block.world().equals(event.getWorld().getName()) && restore(block)) {
            deferred.remove(block);
            storage.removeTempBlock(block.world(),block.x(),block.y(),block.z()).exceptionally(error -> {
                plugin.getLogger().warning("Recovered block journal deletion failed: " + error.getClass().getSimpleName()); return null;
            });
        }
    }
    @EventHandler public void entitiesLoaded(EntitiesLoadEvent event) {
        // Live tests own their lifecycle (T17); do not remove a current live test on chunk reload.
        for (Entity entity : event.getEntities())
            if (!entity.getPersistentDataContainer().has(MobsPlatform.key("live_test"),PersistentDataType.BYTE)) removeStale(entity);
    }
    private void removeStale(Entity entity) {
        String id=entity.getPersistentDataContainer().get(MobKeys.SESSION,PersistentDataType.STRING);
        if (id != null && manager.byId(id).filter(s -> s.state().state()!=SessionState.FREE).isEmpty()) dev.dasan.customdungeons.mob.MobHealth.terminate(entity,false);
    }
    @EventHandler(priority=EventPriority.LOWEST) public void join(PlayerJoinEvent event) { cleanKeys(event.getPlayer()); }
    private void cleanKeys(Player player) {
        var contents=player.getInventory().getContents();
        for (int i=0;i<contents.length;i++) if (KeyService.isKey(contents[i])) player.getInventory().setItem(i,null);
        if (KeyService.isKey(player.getItemOnCursor())) player.setItemOnCursor(null);
    }
}
