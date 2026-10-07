package dev.dasan.customdungeons.session;

import dev.dasan.customdungeons.model.*;
import dev.dasan.customdungeons.storage.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.entity.Item;
import org.bukkit.event.player.PlayerQuitEvent.QuitReason;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.persistence.PersistentDataType;

/** Main-thread effects backed by a separate asynchronous voluntary-quit journal. */
final class DisconnectService {
    private static final NamespacedKey APPLIED=new NamespacedKey("customdungeons","disconnect_applied");
    private static final NamespacedKey DROP=new NamespacedKey("customdungeons","disconnect_drop");
    private static final NamespacedKey RESPAWN=new NamespacedKey("customdungeons","disconnect_respawn");
    private final Storage storage;
    private final SessionManager manager;
    private final DisconnectPersistence journal;
    private final Map<UUID,CompletableFuture<Void>> writes=new HashMap<>();
    private final Map<UUID,DisconnectRecord> dying=new HashMap<>();
    private final Map<UUID,Point> respawnPoints=new HashMap<>();
    private final Map<UUID,Chunk> respawnChunks=new HashMap<>();

    DisconnectService(Storage storage,SessionManager manager) {
        this.storage=storage;this.manager=manager;
        journal=storage instanceof DisconnectPersistence persistence?persistence:null;
    }
    void record(DungeonSession session,Player player,QuitReason reason) {
        if(journal==null)return;
        var at=player.getLocation();
        var position=at==null || at.getWorld()==null?session.def().lobby():point(at);
        var record=new DisconnectRecord(UUID.randomUUID(),player.getUniqueId(),session.id(),session.def().id(),
                position,session.def().exit(),modeFor(session.def().disconnectMode(),reason),session.def().keepInventory());
        try {
            CompletableFuture<Void> saved=journal.saveDisconnect(record);
            writes.put(player.getUniqueId(),saved);manager.observe(saved);
            saved.whenComplete((unused,error)->manager.main(()->writes.remove(player.getUniqueId(),saved)));
        } catch(RuntimeException failure) {manager.disconnectFailed("journal write",failure);}
    }
    static DisconnectMode modeFor(DisconnectMode configured,QuitReason reason) {
        return switch(reason) {
            case DISCONNECTED,TIMED_OUT -> configured;
            case KICKED,ERRONEOUS_STATE -> DisconnectMode.RETURN_TO_EXIT;
        };
    }
    String appliedGeneration(Player player) {
        var pdc=player.getPersistentDataContainer();
        return pdc==null?null:pdc.get(APPLIED,PersistentDataType.STRING);
    }
    /** confirmedGeneration is captured at the real join, before any asynchronous work. */
    void reconnect(Player player,String confirmedGeneration,BooleanSupplier current,Runnable ordinary,Runnable done) {
        if(journal==null){ordinary.run();return;}
        UUID uuid=player.getUniqueId();
        var write=writes.remove(uuid);
        try {
            var read=(write==null?CompletableFuture.<Void>completedFuture(null):write)
                    .thenCompose(unused->journal.disconnect(uuid));
            manager.observe(bounded(read).handle((record,error)->{
                manager.main(()->{
                    if(!current.getAsBoolean())return;
                    if(error!=null){manager.disconnectFailed("journal read",error);return;}
                    if(record.isEmpty()){ordinary.run();return;}
                    DisconnectRecord value=record.orElseThrow();
                    if(value.id().toString().equals(confirmedGeneration)) {
                        if(player.getPersistentDataContainer().has(RESPAWN,PersistentDataType.BYTE))
                            prepareRespawn(player,value,current,()->cleanup(value,current,done));
                        else cleanup(value,current,done);
                        return;
                    }
                    // An in-memory marker prevents another application, but proves no vanilla save.
                    if(value.id().toString().equals(appliedGeneration(player))) {
                        if(player.getPersistentDataContainer().has(RESPAWN,PersistentDataType.BYTE))
                            prepareRespawn(player,value,current,()->{});
                        return;
                    }
                    if(value.mode()==DisconnectMode.DIE_AND_DROP && player.isDead()) {
                        player.getPersistentDataContainer().set(RESPAWN,PersistentDataType.BYTE,(byte)1);
                        player.getPersistentDataContainer().set(APPLIED,PersistentDataType.STRING,value.id().toString());
                        prepareRespawn(player,value,current,()->{});
                        return; // Wait for a later real join to confirm the death and inventory.
                    }
                    if(value.mode()==DisconnectMode.DIE_AND_DROP)
                        prepareRespawn(player,value,current,()->prepare(player,value,current,done,0));
                    else prepare(player,value,current,done,1);
                });return null;
            }));
        } catch(RuntimeException error) {manager.disconnectFailed("journal read",error);}
    }
    private <T> CompletableFuture<T> bounded(CompletableFuture<T> operation) {
        return operation.thenApply(value->value).orTimeout(manager.recoveryTimeoutMillis(),TimeUnit.MILLISECONDS);
    }
    /** Stored position -> stored exit -> an outside world spawn, with bounded async chunk loading. */
    private void prepare(Player player,DisconnectRecord record,BooleanSupplier current,Runnable done,int stage) {
        if(!current.getAsBoolean())return;
        try {
            Point destination=stage==0?record.position():stage==1?record.exit():manager.outsideSpawn();
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
        // If every destination fails, retain both the durable record and the entry guard.
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
            if(!player.isDead()) {
                player.getPersistentDataContainer().remove(RESPAWN);
                disconnected(player.getUniqueId());
                manager.disconnectFailed("death cancelled",new IllegalStateException("Player still alive"));return;
            }
        }
        player.getPersistentDataContainer().set(APPLIED,PersistentDataType.STRING,record.id().toString());
        // Vanilla persists death, inventory and PDC together. Only a later real join proves it did.
    }
    private boolean exterior(Point point) {
        return RespawnDestinations.valid(point) && Bukkit.getWorld(point.world())!=null
                && !manager.insideDungeon(location(point));
    }
    private void prepareRespawn(Player player,DisconnectRecord record,BooleanSupplier current,Runnable ready) {
        Point exit=exterior(record.exit())?record.exit():manager.outsideExit();
        if(!exterior(exit)){prepareSpawn(player,current,ready);return;}
        try {
            manager.observe(bounded(Bukkit.getWorld(exit.world()).getChunkAtAsync(
                    ((int)Math.floor(exit.x()))>>4,((int)Math.floor(exit.z()))>>4)).whenComplete((chunk,error)->manager.main(()->{
                if(!current.getAsBoolean())return;
                if(error!=null){prepareSpawn(player,current,ready);return;}
                rememberRespawn(player,exit,chunk);ready.run();
            })));
        } catch(RuntimeException error){prepareSpawn(player,current,ready);}
    }
    private void prepareSpawn(Player player,BooleanSupplier current,Runnable ready) {
        manager.observe(manager.outsideSpawnAsync().whenComplete((point,error)->manager.main(()->{
            if(!current.getAsBoolean())return;
            if(error!=null){manager.disconnectFailed("respawn preparation",error);ready.run();return;}
            try {
                manager.observe(bounded(Bukkit.getWorld(point.world()).getChunkAtAsync(
                        ((int)Math.floor(point.x()))>>4,((int)Math.floor(point.z()))>>4)).whenComplete((chunk,failure)->manager.main(()->{
                    if(!current.getAsBoolean())return;
                    if(failure!=null)manager.disconnectFailed("respawn preparation",failure);
                    else rememberRespawn(player,point,chunk);
                    ready.run();
                })));
            } catch(RuntimeException failure){manager.disconnectFailed("respawn preparation",failure);ready.run();}
        })));
    }
    private void rememberRespawn(Player player,Point point,Chunk chunk) {
        disconnected(player.getUniqueId());manager.retainChunk(chunk);
        respawnChunks.put(player.getUniqueId(),chunk);respawnPoints.put(player.getUniqueId(),point);
    }
    void disconnected(UUID uuid) {
        respawnPoints.remove(uuid);Chunk chunk=respawnChunks.remove(uuid);if(chunk!=null)manager.releaseChunk(chunk);
    }
    void close() {for(UUID uuid:List.copyOf(respawnChunks.keySet()))disconnected(uuid);}
    private void cleanup(DisconnectRecord record,BooleanSupplier current,Runnable done) {
        CompletableFuture<Void> operation=storage instanceof ExitPersistence returns
                ?returns.clearReturnTarget(record.player(),record.sessionId()):CompletableFuture.completedFuture(null);
        // Neither T38's PREVIOUS target nor a legacy pending exit may override a future bed respawn.
        operation=operation.thenCompose(unused->storage.takePendingExit(record.player()))
                .thenCompose(unused->journal.clearDisconnect(record.player(),record.id()));
        manager.observe(bounded(operation).whenComplete((unused,error)->manager.main(()->{
            if(!current.getAsBoolean())return;
            if(error!=null){manager.disconnectFailed("journal acknowledgement",error);return;}
            done.run();
        })));
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
        else {
            event.getDrops().removeIf(KeyService::isKey);
            // Bridge actual death drops to their entities; nearby foreign spawns are not ours.
            // Clone first so an event stack alias cannot mark the player's retained inventory.
            event.getDrops().replaceAll(original->{
                var drop=original.clone();
                drop.editPersistentDataContainer(pdc->pdc.set(DROP,PersistentDataType.STRING,record.id().toString()));
                return drop;
            });
        }
        return true;
    }
    static boolean penaltyDrop(Item item) {
        var pdc=item.getPersistentDataContainer();
        return pdc!=null && pdc.has(DROP,PersistentDataType.STRING);
    }
    void drop(ItemSpawnEvent event) {
        var item=event.getEntity();var stack=item.getItemStack();
        String generation=stack.getPersistentDataContainer().get(DROP,PersistentDataType.STRING);
        if(generation==null)return;
        for(var record:dying.values()) {
            if(!record.id().toString().equals(generation))continue;
            // Ownership belongs to the entity, never to the item after collection.
            stack.editPersistentDataContainer(pdc->pdc.remove(DROP));item.setItemStack(stack);
            item.getPersistentDataContainer().set(DROP,PersistentDataType.STRING,generation);
            item.getPersistentDataContainer().set(dev.dasan.customdungeons.mob.MobKeys.SESSION,
                    PersistentDataType.STRING,record.sessionId().toString());
            if(manager.byId(record.sessionId().toString()).filter(s->s.state().state()==SessionState.RUNNING
                    || s.state().state()==SessionState.LOBBY).isEmpty()) manager.main(item::remove);
            break;
        }
    }
    boolean respawn(PlayerRespawnEvent event) {
        var pdc=event.getPlayer().getPersistentDataContainer();
        if(!pdc.has(RESPAWN,PersistentDataType.BYTE))return false;
        Location at=event.getRespawnLocation();
        Point vanilla=at.getWorld()==null?null:point(at);
        Point outside=RespawnDestinations.personalSpawn(event.isBedSpawn(),event.isAnchorSpawn(),vanilla,
                p->manager.insideDungeon(location(p)),()->{
                    Point prepared=respawnPoints.get(event.getPlayer().getUniqueId());
                    if(exterior(prepared))return prepared;
                    Point exit=manager.outsideExit();return exterior(exit)?exit:manager.outsideSpawn();
                });
        // A player event supplies a world even during unusual world-unload/plugin races.
        event.setRespawnLocation(outside==null?at:location(outside));
        disconnected(event.getPlayer().getUniqueId());
        pdc.remove(RESPAWN);return true;
    }
    private static Point point(Location at) {return new Point(at.getWorld().getName(),at.getX(),at.getY(),at.getZ(),at.getYaw(),at.getPitch());}
    private static Location location(Point p) {return DungeonSessionRuntime.location(p);}
}
