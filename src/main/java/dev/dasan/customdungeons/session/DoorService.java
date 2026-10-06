package dev.dasan.customdungeons.session;

import dev.dasan.customdungeons.config.PluginConfig;
import dev.dasan.customdungeons.model.*;
import java.util.function.Consumer;
import org.bukkit.*;
import org.bukkit.block.Block;

public final class DoorService {
    private final DungeonSession session;
    private final SessionTempBlocks blocks;
    private final PluginConfig config;
    public DoorService(DungeonSession session, SessionTempBlocks blocks, PluginConfig config) {
        this.session = session; this.blocks = blocks; this.config = config;
    }
    public void closeAll() {
        for (RoomDef room : session.def().rooms()) if (room.door() != null)
            each(room.door(),b -> blocks.place(b,config.doorMaterial().createBlockData(),Integer.MAX_VALUE));
    }
    public void open(int index) {
        Region door = session.def().rooms().get(index).door();
        if (door != null) {
            each(door,blocks::restore);
            Location at = beside(door);
            for (var player : session.players()) {
                player.playSound(at,"minecraft:block.iron_door.open",1,1);
                if (player.getWorld().equals(at.getWorld()) && player.getLocation().distanceSquared(at) <= Math.pow(config.limits().effectViewRadius(),2))
                    player.spawnParticle(Particle.CLOUD,at,(int)(10*config.limits().particleDensity()),0.5,1,0.5,0.01);
            }
        }
        session.openDoor();
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
