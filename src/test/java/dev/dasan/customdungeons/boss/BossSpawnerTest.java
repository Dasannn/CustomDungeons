package dev.dasan.customdungeons.boss;

import dev.dasan.customdungeons.ability.impl.PaperApiTestBootstrap;
import dev.dasan.customdungeons.config.EntityHeights;
import dev.dasan.customdungeons.model.*;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Zombie;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BossSpawnerTest {
    World world;Block ground,air;Plugin plugin;BukkitScheduler scheduler;
    @BeforeEach void setup() {
        PaperApiTestBootstrap.initialize();world=mock(World.class);ground=mock(Block.class);air=mock(Block.class);plugin=mock(Plugin.class);scheduler=mock(BukkitScheduler.class);
        when(plugin.isEnabled()).thenReturn(true);when(world.getUID()).thenReturn(UUID.randomUUID());
        var server=mock(Server.class);when(plugin.getServer()).thenReturn(server);when(server.getScheduler()).thenReturn(scheduler);
        when(world.getMinHeight()).thenReturn(-64);when(world.getMaxHeight()).thenReturn(320);when(world.isChunkLoaded(anyInt(),anyInt())).thenReturn(true);
        var border=mock(WorldBorder.class);when(world.getWorldBorder()).thenReturn(border);when(border.getCenter()).thenReturn(new Location(world,0,0,0));when(border.getSize()).thenReturn(1000d);
        when(world.getHighestBlockYAt(anyInt(),anyInt(),eq(HeightMap.WORLD_SURFACE))).thenReturn(63);
        when(ground.isSolid()).thenReturn(true);when(ground.getType()).thenReturn(Material.STONE);when(ground.getBlockData()).thenReturn(mock(BlockData.class));
        when(air.getType()).thenReturn(Material.AIR);when(air.isPassable()).thenReturn(true);when(air.getBlockData()).thenReturn(mock(BlockData.class));
        when(world.getBlockAt(anyInt(),anyInt(),anyInt())).thenAnswer(c->(int)c.getArgument(1)==63?ground:air);
    }
    @ParameterizedTest @EnumSource(value=Material.class,names={"WATER","LAVA","OAK_LEAVES","KELP","KELP_PLANT","SEAGRASS","TALL_SEAGRASS","BUBBLE_COLUMN"})
    void rejectsUnsafeSurface(Material type){when(ground.getType()).thenReturn(type);assertTrue(BossSpawner.validate(world,0,0,.8,2).isEmpty());}
    @Test void rejectsWaterloggedSolidAndWaterloggedHeadroom(){
        var data=mock(Waterlogged.class);when(data.isWaterlogged()).thenReturn(true);when(ground.getBlockData()).thenReturn(data);
        assertTrue(BossSpawner.validate(world,0,0,.8,2).isEmpty());when(ground.getBlockData()).thenReturn(mock(BlockData.class));when(air.getBlockData()).thenReturn(data);
        assertTrue(BossSpawner.validate(world,0,0,.8,2).isEmpty());
    }
    @Test void clearanceIncludesScaledHeightAndAdjacentChunks(){
        assertTrue(BossSpawner.validate(world,0,0,.8,2).isPresent());
        var obstacle=mock(Block.class);when(obstacle.getType()).thenReturn(Material.STONE);when(obstacle.getBlockData()).thenReturn(mock(BlockData.class));
        when(world.getBlockAt(anyInt(),eq(66),anyInt())).thenReturn(obstacle);
        assertTrue(BossSpawner.validate(world,0,0,.8,2).isPresent());assertTrue(BossSpawner.validate(world,0,0,.8,4).isEmpty());
        when(world.isChunkLoaded(-1,0)).thenReturn(false);assertTrue(BossSpawner.validate(world,0,0,4,2).isEmpty());
    }
    @Test void rejectsBodyAcrossWorldBorderAndBuildHeight(){assertTrue(BossSpawner.validate(world,499,0,2,2).isEmpty());assertTrue(BossSpawner.validate(world,0,0,.8,300).isEmpty());}
    @Test void exactlyTwentyAsyncAttemptsAndNoSynchronousChunkLoads(){
        var chunk=mock(Chunk.class);when(world.getChunkAtAsync(anyInt(),anyInt(),eq(true))).thenReturn(CompletableFuture.completedFuture(chunk));
        var dimensions=mock(Zombie.class);when(dimensions.getWidth()).thenReturn(.6);when(dimensions.getHeight()).thenReturn(2d);doReturn(dimensions).when(world).createEntity(any(Location.class),any());
        when(ground.getType()).thenReturn(Material.WATER);var work=new ArrayDeque<Runnable>();
        var template=new MobTemplate("boss","ZOMBIE","",100,1,0,0,1,Map.of(),List.of(),List.of(),List.of(),true,"PURPLE",null,List.of(),false).withWorldBoss(new WorldBossDef("world",-10,10,-10,10,1,48,5,new RewardDef(List.of(),0,0,List.of())));
        try(var api=mockStatic(Bukkit.class)) {
            api.when(()->Bukkit.getWorld("world")).thenReturn(world);api.when(()->Bukkit.getWorld(world.getUID())).thenReturn(world);api.when(Bukkit::getScheduler).thenReturn(scheduler);
            doAnswer(c->{work.add(c.getArgument(1));return null;}).when(scheduler).runTask(eq(plugin),any(Runnable.class));
            var result=new BossSpawner(plugin,new EntityHeights(Map.of(EntityType.ZOMBIE,2d)),20,new Random(3)).find(template,()->true);
            assertFalse(result.isDone());while(!work.isEmpty())work.remove().run();assertTrue(result.join().isEmpty());
            verify(world,times(20)).getChunkAtAsync(anyInt(),anyInt(),eq(true));verify(world,never()).getChunkAt(anyInt(),anyInt());
        }
    }
    @Test void pendingChunkCallbackIsScheduledOnMainAndDiscardedIfInvalidatedBeforeExecution() throws Exception {
        var chunk=mock(Chunk.class);var pending=new CompletableFuture<Chunk>();
        when(world.getChunkAtAsync(anyInt(),anyInt(),eq(true))).thenReturn(pending);
        var dimensions=mock(Zombie.class);when(dimensions.getWidth()).thenReturn(.6);when(dimensions.getHeight()).thenReturn(2d);doReturn(dimensions).when(world).createEntity(any(Location.class),any());
        var tasks=new java.util.concurrent.ConcurrentLinkedQueue<Runnable>();
        doAnswer(c->{tasks.add(c.getArgument(1));return null;}).when(scheduler).runTask(eq(plugin),any(Runnable.class));
        var current=new java.util.concurrent.atomic.AtomicBoolean(true);
        var template=new MobTemplate("boss","ZOMBIE","",100,1,0,0,1,Map.of(),List.of(),List.of(),List.of(),true,"PURPLE",null,List.of(),false).withWorldBoss(new WorldBossDef("world",-10,10,-10,10,1,48,5,new RewardDef(List.of(),0,0,List.of())));
        try(var api=mockStatic(Bukkit.class)) {
            api.when(()->Bukkit.getWorld("world")).thenReturn(world);api.when(()->Bukkit.getWorld(world.getUID())).thenReturn(world);
            var result=new BossSpawner(plugin,new EntityHeights(Map.of()),20,new Random(3)).find(template,current::get);
            var worker=new Thread(()->pending.complete(chunk));worker.start();worker.join();
            assertEquals(1,tasks.size());assertFalse(result.isDone());
            current.set(false);tasks.remove().run();assertFalse(result.isDone());
            verify(world,never()).getHighestBlockYAt(anyInt(),anyInt(),any(HeightMap.class));
            verify(chunk,never()).addPluginChunkTicket(any());
        }
    }
}
