package dev.dasan.customdungeons.session;

import java.util.*;
import java.util.concurrent.*;
import org.bukkit.*;
import org.bukkit.block.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RespawnSafetyRegressionTest {
    static void terrain(World world) {
        var solid=mock(Material.class);when(solid.isSolid()).thenReturn(true);
        var empty=mock(Material.class);var blocks=new HashMap<String,Block>();
        when(world.getMinHeight()).thenReturn(-64);when(world.getMaxHeight()).thenReturn(320);
        var border=mock(WorldBorder.class);when(border.isInside(any(Location.class))).thenReturn(true);
        when(world.getWorldBorder()).thenReturn(border);
        when(world.getHighestBlockYAt(anyInt(),anyInt())).thenReturn(69);
        when(world.getBlockAt(any(Location.class))).thenAnswer(call->{
            Location at=call.getArgument(0);return world.getBlockAt(at.getBlockX(),at.getBlockY(),at.getBlockZ());
        });
        when(world.getBlockAt(anyInt(),anyInt(),anyInt())).thenAnswer(call->{
            int x=call.getArgument(0),y=call.getArgument(1),z=call.getArgument(2);
            String key=x+":"+y+":"+z;if(blocks.containsKey(key))return blocks.get(key);
            var block=mock(Block.class);blocks.put(key,block);
            when(block.getType()).thenReturn(y==69?solid:y<69?empty:Material.AIR);
            when(block.isPassable()).thenReturn(y!=69);
            when(block.getRelative(BlockFace.UP)).thenAnswer(unused->world.getBlockAt(x,y+1,z));
            when(block.getRelative(BlockFace.DOWN)).thenAnswer(unused->world.getBlockAt(x,y-1,z));
            return block;
        });
    }
    @Test void unsafeSpawnColumnFindsNearbySolidGround() {
        try(var bukkit=mockStatic(Bukkit.class)) {
            World world=mock(World.class);when(world.getName()).thenReturn("world");terrain(world);
            when(world.getSpawnLocation()).thenReturn(new Location(world,0,70,0));
            when(world.isChunkLoaded(anyInt(),anyInt())).thenReturn(true);
            var lava=mock(Block.class);when(lava.getType()).thenReturn(Material.LAVA);
            when(lava.getRelative(any(BlockFace.class))).thenReturn(lava);
            when(world.getBlockAt(eq(0),anyInt(),eq(0))).thenReturn(lava);
            bukkit.when(Bukkit::getWorlds).thenReturn(List.of(world));bukkit.when(()->Bukkit.getWorld("world")).thenReturn(world);
            var target=new RespawnDestinations("","dungeons",at->false,key->{}).spawn();
            assertTrue(target.x()!=0 || target.z()!=0,"Must leave the unsafe lava column");
            assertTrue(DungeonSessionRuntime.safePrevious(target));
        }
    }
    @Test void spiralVisitsEveryColumnOnceAndStaysInsideRadius() {
        var columns=RespawnDestinations.spiral(-31,48,16);
        assertEquals(new RespawnDestinations.Column(-31,48),columns.getFirst());
        assertEquals(33*33,columns.size());assertEquals(columns.size(),new HashSet<>(columns).size());
        int previous=0;
        for(var column:columns) {
            int radius=Math.max(Math.abs(column.x()+31),Math.abs(column.z()-48));
            assertTrue(radius>=previous && radius<=16);previous=radius;
        }
    }
    @Test void unloadedTerrainIsInspectedOnlyAfterAsyncPreloadAndOnMainThread() {
        try(var bukkit=mockStatic(Bukkit.class)) {
            World world=mock(World.class);when(world.getName()).thenReturn("world");terrain(world);
            when(world.getSpawnLocation()).thenReturn(new Location(world,-1,70,-1));
            var loaded=new java.util.concurrent.atomic.AtomicBoolean();
            when(world.isChunkLoaded(anyInt(),anyInt())).thenAnswer(call->loaded.get());
            var future=new CompletableFuture<Chunk>();when(world.getChunkAtAsync(anyInt(),anyInt())).thenReturn(future);
            var chunk=mock(Chunk.class);var main=new ArrayList<Runnable>();var retained=new ArrayList<Chunk>();var released=new ArrayList<Chunk>();
            bukkit.when(Bukkit::getWorlds).thenReturn(List.of(world));bukkit.when(()->Bukkit.getWorld("world")).thenReturn(world);
            var helper=new RespawnDestinations("","dungeons",at->false,key->{});
            var result=helper.spawnAsync(main::add,retained::add,released::add);
            verify(world,never()).getHighestBlockYAt(anyInt(),anyInt());verify(world,never()).getBlockAt(any(Location.class));
            assertFalse(result.isDone());loaded.set(true);future.complete(chunk);
            assertFalse(result.isDone());assertEquals(1,main.size());main.removeFirst().run();
            assertEquals("world",result.join().world());assertFalse(retained.isEmpty());assertEquals(retained,released);
            verify(world,times(9)).getChunkAtAsync(anyInt(),anyInt());
        }
    }
    @Test void noSafeTerrainAdjustsFirstWorldSpawnToHighestBlockAndWarns() {
        try(var bukkit=mockStatic(Bukkit.class)) {
            World world=mock(World.class);when(world.getName()).thenReturn("world");terrain(world);
            when(world.getSpawnLocation()).thenReturn(new Location(world,0,12,0,40,10));
            when(world.isChunkLoaded(anyInt(),anyInt())).thenReturn(true);
            bukkit.when(Bukkit::getWorlds).thenReturn(List.of(world));bukkit.when(()->Bukkit.getWorld("world")).thenReturn(world);
            var warnings=new ArrayList<String>();
            var result=new RespawnDestinations("","dungeons",at->true,warnings::add).spawn();
            assertEquals(70,result.y());assertEquals(40,result.yaw());assertEquals(List.of("respawn.unsafe-spawn"),warnings);
        }
    }
    @Test void unsafeMaterialsBlockedHeadVoidAndBorderAreRejected() {
        try(var bukkit=mockStatic(Bukkit.class)) {
            World world=mock(World.class);when(world.getName()).thenReturn("world");terrain(world);
            when(world.isChunkLoaded(anyInt(),anyInt())).thenReturn(true);
            bukkit.when(()->Bukkit.getWorld("world")).thenReturn(world);
            var point=new dev.dasan.customdungeons.model.Point("world",0,70,0,0,0);
            assertTrue(DungeonSessionRuntime.safePrevious(point));
            var feet=world.getBlockAt(0,70,0);var head=feet.getRelative(BlockFace.UP);var floor=feet.getRelative(BlockFace.DOWN);
            Material solid=floor.getType();
            when(world.getBlockAt(any(Location.class))).thenReturn(feet);
            for(Material type:List.of(Material.LAVA,Material.FIRE,Material.SOUL_FIRE,Material.WATER)) {
                when(feet.getType()).thenReturn(type);assertFalse(DungeonSessionRuntime.safePrevious(point));
            }
            when(feet.getType()).thenReturn(Material.AIR);when(head.isPassable()).thenReturn(false);
            assertFalse(DungeonSessionRuntime.safePrevious(point));when(head.isPassable()).thenReturn(true);
            when(floor.getType()).thenReturn(mock(Material.class));assertFalse(DungeonSessionRuntime.safePrevious(point));
            when(floor.getType()).thenReturn(solid);when(world.getWorldBorder().isInside(any(Location.class))).thenReturn(false);
            assertFalse(DungeonSessionRuntime.safePrevious(point));
        }
    }
}
