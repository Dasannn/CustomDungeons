package dev.dasan.customdungeons.session;

import dev.dasan.customdungeons.config.PluginConfig;
import dev.dasan.customdungeons.model.*;
import java.util.function.Consumer;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;

public final class DoorService {
    private final DungeonSession session;
    private final SessionTempBlocks blocks;
    private final PluginConfig config;
    public DoorService(DungeonSession session, SessionTempBlocks blocks, PluginConfig config) {
        this.session = session; this.blocks = blocks; this.config = config;
    }
    public void closeAll() {
        for (RoomDef room : session.def().rooms()) if (room.door() != null)
            each(room.door(),b -> {
                if (supported(b)) blocks.place(b,config.doorMaterial().createBlockData(),Integer.MAX_VALUE);
            });
    }
    public CompletableFuture<Boolean> open(int index) { return open(index,() -> {}); }
    CompletableFuture<Boolean> open(int index,Runnable consumeKey) {
        Region door = session.def().rooms().get(index).door();
        var regionBlocks=new ArrayList<Block>();
        if (door != null) each(door,regionBlocks::add);
        if (!regionBlocks.stream().allMatch(this::supported)) return CompletableFuture.completedFuture(false);
        var opened=blocks.openDoor(regionBlocks,Material.AIR.createBlockData(),() ->
                session.state().state()==SessionState.RUNNING && session.roomIndex()==index
                        && regionBlocks.stream().allMatch(this::supported));
        return opened.thenApply(success -> {
            if (!success) {
                if (session.state().state()==SessionState.RUNNING)
                    Bukkit.getLogger().warning("CustomDungeons: door opening failed for " + session.def().id());
                return false;
            }
            consumeKey.run();
            session.openDoor();
            if (door != null) {
                Location at = beside(door);
                for (var player : session.players()) {
                    player.playSound(at,"minecraft:block.iron_door.open",1,1);
                    if (player.getWorld().equals(at.getWorld()) && player.getLocation().distanceSquared(at) <= Math.pow(config.limits().effectViewRadius(),2))
                        player.spawnParticle(Particle.CLOUD,at,(int)(10*config.limits().particleDensity()),0.5,1,0.5,0.01);
                }
            }
            return true;
        });
    }
    /** Entrance uses the same durable batch as room doors, without advancing roomIndex. */
    public CompletableFuture<Boolean> openEntrance() {
        var door=session.def().entranceDoor();
        if(door==null)return CompletableFuture.completedFuture(true);
        var regionBlocks=new ArrayList<Block>();each(door,regionBlocks::add);
        if(!regionBlocks.stream().allMatch(this::supported))return CompletableFuture.completedFuture(false);
        return blocks.openDoor(regionBlocks,Material.AIR.createBlockData(),()->
                session.state().state()==SessionState.RUNNING && regionBlocks.stream().allMatch(this::supported));
    }
    private boolean supported(Block block) {
        if (!(block.getState() instanceof org.bukkit.block.TileState)) return true;
        Bukkit.getLogger().warning("CustomDungeons: unsupported TileState in door at "
                + block.getWorld().getName()+":"+block.getX()+","+block.getY()+","+block.getZ());
        return false;
    }
    /** Pure selection: nearest walkable block to the door, ties nearest the checkpoint. */
    static Point keyPosition(Region room, Region door, Point checkpoint, java.util.function.Predicate<BlockPos> walkable) {
        if (door == null) return checkpoint;
        BlockPos best = null;
        double distance = Double.POSITIVE_INFINITY, checkpointDistance = Double.POSITIVE_INFINITY;
        for (int x=room.min().x(); x<=room.max().x(); x++)
            for (int y=room.min().y(); y<room.max().y(); y++)
                for (int z=room.min().z(); z<=room.max().z(); z++) {
                    double dx = Math.max(Math.max(door.min().x()-x,0),x-door.max().x());
                    double dy = Math.max(Math.max(door.min().y()-y,0),y-door.max().y());
                    double dz = Math.max(Math.max(door.min().z()-z,0),z-door.max().z());
                    double d = dx*dx+dy*dy+dz*dz;
                    double c = Math.pow(x+0.5-checkpoint.x(),2)+Math.pow(y+0.5-checkpoint.y(),2)+Math.pow(z+0.5-checkpoint.z(),2);
                    if (d>distance || d==distance && c>=checkpointDistance) continue;
                    BlockPos candidate = new BlockPos(x,y,z);
                    if (walkable.test(candidate)) { best=candidate; distance=d; checkpointDistance=c; }
                }
        return best == null ? checkpoint : new Point(room.world(),best.x()+0.5,best.y()+0.5,best.z()+0.5,0,0);
    }
    static Location keyRespawn(RoomDef room) {
        World world = java.util.Objects.requireNonNull(Bukkit.getWorld(room.region().world()));
        Point point = keyPosition(room.region(),room.door(),room.checkpoint(),p ->
                world.getBlockAt(p.x(),p.y(),p.z()).getType().isAir()
                && world.getBlockAt(p.x(),p.y()+1,p.z()).getType().isAir()
                && world.getBlockAt(p.x(),p.y()-1,p.z()).getType().isSolid());
        return new Location(world,point.x(),point.y(),point.z(),point.yaw(),point.pitch());
    }
    static Location beside(Region region) {
        return new Location(java.util.Objects.requireNonNull(Bukkit.getWorld(region.world())),region.min().x()+0.5,region.min().y()+0.5,region.min().z()-0.5);
    }
    private static void each(Region region, Consumer<Block> action) {
        World world = java.util.Objects.requireNonNull(Bukkit.getWorld(region.world()));
        for (int x=region.min().x(); x<=region.max().x(); x++)
            for (int y=region.min().y(); y<=region.max().y(); y++)
                for (int z=region.min().z(); z<=region.max().z(); z++) action.accept(world.getBlockAt(x,y,z));
    }
}
