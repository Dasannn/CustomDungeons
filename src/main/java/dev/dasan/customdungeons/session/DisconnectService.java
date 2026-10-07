package dev.dasan.customdungeons.session;

import dev.dasan.customdungeons.model.*;
import dev.dasan.customdungeons.storage.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.persistence.PersistentDataType;

/** Main-thread effects backed by a separate asynchronous voluntary-quit journal. */
final class DisconnectService {
    private static final NamespacedKey APPLIED=new NamespacedKey("customdungeons","disconnect_applied");
    private static final NamespacedKey RESPAWN=new NamespacedKey("customdungeons","disconnect_respawn");
    private final Storage storage;
    private final SessionManager manager;
    private final DisconnectPersistence journal;
    private final Map<UUID,CompletableFuture<Void>> writes=new HashMap<>();
    private final Map<UUID,DisconnectRecord> dying=new HashMap<>();

    DisconnectService(Storage storage,SessionManager manager) {
        this.storage=storage;this.manager=manager;
        journal=storage instanceof DisconnectPersistence persistence?persistence:null;
    }
    void record(DungeonSession session,Player player) {
        if(journal==null)return;
        var at=player.getLocation();
        var position=at==null || at.getWorld()==null?session.def().lobby():point(at);
        var record=new DisconnectRecord(UUID.randomUUID(),player.getUniqueId(),session.id(),session.def().id(),
                position,session.def().exit(),session.def().disconnectMode(),session.def().keepInventory());
        try {
            CompletableFuture<Void> saved=journal.saveDisconnect(record);
            writes.put(player.getUniqueId(),saved);manager.observe(saved);
            saved.whenComplete((unused,error)->manager.main(()->writes.remove(player.getUniqueId(),saved)));
        } catch(RuntimeException failure) {manager.disconnectFailed("journal write",failure);}
    }
    /** Returns to the ordinary crash/exit recovery only when no voluntary-quit record exists. */
    void reconnect(Player player,BooleanSupplier current,Runnable ordinary,Runnable done) {
        if(journal==null){ordinary.run();return;}
        UUID uuid=player.getUniqueId();
        var write=writes.remove(uuid);
        try {
            var read=(write==null?CompletableFuture.<Void>completedFuture(null):write)
                    .thenCompose(unused->journal.disconnect(uuid));
            manager.observe(bounded(read).handle((record,error)->{
                manager.main(()->{
                    if(!current.getAsBoolean())return;
                    if(error!=null){manager.disconnectFailed("journal read",error);ordinary.run();return;}
                    if(record.isEmpty()){ordinary.run();return;}
                    DisconnectRecord value=record.orElseThrow();
                    if(value.id().toString().equals(player.getPersistentDataContainer().get(APPLIED,PersistentDataType.STRING))) {
                        cleanup(value,done);return;
                    }
                    if(value.mode()==DisconnectMode.DIE_AND_DROP && player.isDead()) {
                        player.getPersistentDataContainer().set(RESPAWN,PersistentDataType.BYTE,(byte)1);
                        player.getPersistentDataContainer().set(APPLIED,PersistentDataType.STRING,value.id().toString());
                        cleanup(value,done);return;
                    }
                    prepare(player,value,current,done,value.mode()==DisconnectMode.DIE_AND_DROP?0:1);
                });return null;
            }));
        } catch(RuntimeException error) {manager.disconnectFailed("journal read",error);ordinary.run();}
    }
    private <T> CompletableFuture<T> bounded(CompletableFuture<T> operation) {
        return operation.thenApply(value->value).orTimeout(manager.recoveryTimeoutMillis(),TimeUnit.MILLISECONDS);
    }
    /** Stored position -> stored exit -> an outside world spawn, with bounded async chunk loading. */
    private void prepare(Player player,DisconnectRecord record,BooleanSupplier current,Runnable done,int stage) {
        if(!current.getAsBoolean())return;
        try {
            Point destination=stage==0?record.position():stage==1?record.exit():outsideSpawn(player);
            if(destination==null || !Double.isFinite(destination.x()) || !Double.isFinite(destination.y())
                    || !Double.isFinite(destination.z()))throw new IllegalStateException("Missing disconnect position");
            World world=Bukkit.getWorld(destination.world());
            if(world==null || destination.y()<world.getMinHeight() || destination.y()>=world.getMaxHeight())
                throw new IllegalStateException("Missing world or invalid height");
            Location at=location(destination);
            if(stage>0 && manager.insideDungeon(at))throw new IllegalStateException("Exit inside dungeon");
            int x=at.getBlockX()>>4,z=at.getBlockZ()>>4;
            if(world.isChunkLoaded(x,z)){apply(player,record,current,done,at,stage);return;}
            manager.observe(bounded(world.getChunkAtAsync(x,z)).handle((chunk,error)->{
                manager.main(()->{
                    if(!current.getAsBoolean())return;
                    if(error!=null){retry(player,record,current,done,stage,error);return;}
                    boolean retained=false;
                    try {manager.retainChunk(chunk);retained=true;apply(player,record,current,done,at,stage);}
                    catch(RuntimeException failure){retry(player,record,current,done,stage,failure);}
                    finally{if(retained)manager.releaseChunk(chunk);}
                });return null;
            }));
        } catch(RuntimeException error){retry(player,record,current,done,stage,error);}
    }
    private void retry(Player player,DisconnectRecord record,BooleanSupplier current,Runnable done,int stage,Throwable error) {
        manager.disconnectFailed("position stage "+stage,error);
        if(stage<2)prepare(player,record,current,done,stage+1);
        else done.run(); // Keep the durable record if every destination/cancellation fails.
    }
    private void apply(Player player,DisconnectRecord record,BooleanSupplier current,Runnable done,Location at,int stage) {
        if(!current.getAsBoolean())return;
        if(!manager.recoveryTeleport(player,at)) {retry(player,record,current,done,stage,new IllegalStateException("Teleport cancelled"));return;}
        if(record.mode()==DisconnectMode.DIE_AND_DROP) {
            // A persisted vanilla death screen must not cause another death on the next login.
            player.getPersistentDataContainer().set(RESPAWN,PersistentDataType.BYTE,(byte)1);
            if(!player.isDead()) {
                dying.put(player.getUniqueId(),record);
                try {player.setHealth(0);} finally {dying.remove(player.getUniqueId());}
            }
            if(!player.isDead()) {manager.disconnectFailed("death cancelled",new IllegalStateException("Player still alive"));done.run();return;}
        }
        player.getPersistentDataContainer().set(APPLIED,PersistentDataType.STRING,record.id().toString());
        cleanup(record,done);
    }
    private void cleanup(DisconnectRecord record,Runnable done) {
        CompletableFuture<Void> operation=storage instanceof ExitPersistence returns
                ?returns.clearReturnTarget(record.player(),record.sessionId()):CompletableFuture.completedFuture(null);
        // Neither T38's PREVIOUS target nor a legacy pending exit may override a future bed respawn.
        operation=operation.thenCompose(unused->storage.takePendingExit(record.player()))
                .thenCompose(unused->journal.clearDisconnect(record.player(),record.id()));
        manager.observe(bounded(operation).whenComplete((unused,error)->manager.main(done)));
    }
    boolean death(PlayerDeathEvent event) {
        DisconnectRecord record=dying.get(event.getEntity().getUniqueId());
        if(record==null)return false;
        // Vanilla can omit inventory drops when the world's gamerule keeps inventory.
        // Override that rule with the dungeon snapshot before allowing vanilla to clear the inventory.
        if(!record.keepInventory() && event.getKeepInventory()) {
            event.getDrops().clear();
            for(var item:event.getEntity().getInventory().getContents())
                if(item!=null && !item.getType().isAir() && !KeyService.isKey(item))event.getDrops().add(item.clone());
        }
        event.setKeepInventory(record.keepInventory());event.setKeepLevel(record.keepInventory());
        if(record.keepInventory()) {event.getDrops().clear();event.setDroppedExp(0);}
        else event.getDrops().removeIf(KeyService::isKey);
        return true;
    }
    void drop(ItemSpawnEvent event) {
        var item=event.getEntity();
        for(var entry:dying.entrySet()) {
            Player player=Bukkit.getPlayer(entry.getKey());
            if(player==null || !Objects.equals(item.getWorld(),player.getWorld())
                    || item.getLocation().distanceSquared(player.getLocation())>16)continue;
            DisconnectRecord record=entry.getValue();
            item.getPersistentDataContainer().set(dev.dasan.customdungeons.mob.MobKeys.SESSION,
                    PersistentDataType.STRING,record.sessionId().toString());
            // A finished session already ran cleanup: dispose its late drops on the next main turn.
            if(manager.byId(record.sessionId().toString()).filter(s->s.state().state()==SessionState.RUNNING
                    || s.state().state()==SessionState.LOBBY).isEmpty()) manager.main(item::remove);
            break;
        }
    }
    boolean respawn(PlayerRespawnEvent event) {
        var pdc=event.getPlayer().getPersistentDataContainer();
        if(!pdc.has(RESPAWN,PersistentDataType.BYTE))return false;
        Location at=event.getRespawnLocation();
        if(manager.insideDungeon(at)) {
            Point outside=outsideSpawn(event.getPlayer());
            if(outside==null)throw new IllegalStateException("No respawn outside dungeon is configured");
            event.setRespawnLocation(location(outside));
        }
        pdc.remove(RESPAWN);return true;
    }
    private Point outsideSpawn(Player player) {
        // Prefer the death world's spawn, preserving the normal bed/world semantics where possible.
        var worlds=new ArrayList<World>();if(player.getWorld()!=null)worlds.add(player.getWorld());
        for(World world:Bukkit.getWorlds())if(!worlds.contains(world))worlds.add(world);
        for(World world:worlds) {
            var at=world.getSpawnLocation();if(at!=null && !manager.insideDungeon(at))return point(at);
        }
        return manager.outsideExit();
    }
    private static Point point(Location at) {return new Point(at.getWorld().getName(),at.getX(),at.getY(),at.getZ(),at.getYaw(),at.getPitch());}
    private static Location location(Point p) {return DungeonSessionRuntime.location(p);}
}
