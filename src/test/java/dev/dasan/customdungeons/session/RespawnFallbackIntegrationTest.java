package dev.dasan.customdungeons.session;

import dev.dasan.customdungeons.model.*;
import dev.dasan.customdungeons.storage.*;
import java.util.*;
import java.util.concurrent.*;
import org.bukkit.*;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RespawnFallbackIntegrationTest {
    World configure(ReturnRecoveryRegressionTest.Fixture t) {
        var secondary=mock(World.class);when(secondary.getName()).thenReturn("secondary");
        when(secondary.getSpawnLocation()).thenReturn(new Location(secondary,80,70,80));
        RespawnSafetyRegressionTest.terrain(secondary);
        when(secondary.isChunkLoaded(anyInt(),anyInt())).thenReturn(true);
        var chunk=mock(Chunk.class);when(chunk.getWorld()).thenReturn(secondary);
        when(secondary.getChunkAtAsync(anyInt(),anyInt())).thenReturn(CompletableFuture.completedFuture(chunk));
        t.bukkit.when(Bukkit::getWorlds).thenReturn(List.of(t.f.world,secondary));
        t.bukkit.when(()->Bukkit.getWorld("secondary")).thenReturn(secondary);
        return secondary;
    }
    @Test void disconnectWithoutBedPrefersDungeonExteriorExit() throws Exception {
        try(var t=new DisconnectRecoveryTest.Fixture(DisconnectMode.DIE_AND_DROP,false,"secondary")) {
            var secondary=configure(t);t.killEvents();t.manager.connected(t.player);
            var respawn=mock(PlayerRespawnEvent.class);when(respawn.getPlayer()).thenReturn(t.player);
            when(respawn.getRespawnLocation()).thenReturn(new Location(t.f.world,500,70,500));
            t.listener.respawn(respawn);
            verify(respawn).setRespawnLocation(argThat((Location at)->at.getWorld()==t.f.world && at.getX()==99));
        }
    }
    @Test void recoveryPreviousAndExitFailThenUseConfiguredWorld() throws Exception {
        try(var t=new ReturnRecoveryRegressionTest.Fixture("secondary")) {
            var secondary=configure(t);
            when(t.f.world.isChunkLoaded(anyInt(),anyInt())).thenReturn(false);
            when(t.f.world.getChunkAtAsync(anyInt(),anyInt())).thenReturn(CompletableFuture.failedFuture(new IllegalStateException("chunk")));
            t.manager.connected(t.player);
            verify(t.player).teleport(argThat((Location at)->at.getWorld()==secondary));
        }
    }
    @Test void finishingWithUnavailableExitUsesConfiguredWorld() throws Exception {
        try(var t=new ReturnRecoveryRegressionTest.Fixture("secondary")) {
            configure(t);
            var def=t.f.definition().withFinish(FinishMode.IMMEDIATE,60,FinishDestination.EXIT,List.of());
            var session=new DungeonSession(def,false,new SessionServices(){});
            // The fixture exit is in a room, so it must advance to the shared spawn fallback.
            var runtime=t.f.runtime(t.manager,session);
            assertEquals("secondary",runtime.destination(session,t.player).world());
        }
    }
    @Test void bedAndAnchorInsideAreaOutsideRoomStillUseConfiguredSpawn() throws Exception {
        for(boolean bed:List.of(true,false))try(var t=new DisconnectRecoveryTest.Fixture(DisconnectMode.DIE_AND_DROP,false,"secondary")) {
            var secondary=configure(t);
            var codec=new dev.dasan.customdungeons.config.DefinitionCodec();
            var yaml=new org.bukkit.configuration.file.YamlConfiguration();codec.encode(t.f.definition()).forEach(yaml::set);
            yaml.set("area",Map.of("world","world","min",Map.of("x",0,"y",60,"z",0),"max",Map.of("x",100,"y",90,"z",100)));
            when(t.f.definitions.dungeons()).thenReturn(Map.of("test",codec.decodeDungeon("test",yaml)));
            t.killEvents();t.manager.connected(t.player);
            var respawn=mock(PlayerRespawnEvent.class);when(respawn.getPlayer()).thenReturn(t.player);
            when(respawn.isBedSpawn()).thenReturn(bed);when(respawn.isAnchorSpawn()).thenReturn(!bed);
            when(respawn.getRespawnLocation()).thenReturn(new Location(t.f.world,80,70,80));
            t.listener.respawn(respawn);verify(respawn).setRespawnLocation(argThat((Location at)->at.getWorld()==secondary));
        }
    }
    @Test void cinematicPreviousAndExitFailThenUseConfiguredWorld() throws Exception {
        try(var t=new ReturnRecoveryRegressionTest.Fixture("secondary")) {
            var secondary=configure(t);
            var a=new CinematicRestorationTest().new Actor();
            when(a.p.teleport(any(Location.class))).thenAnswer(call->{a.at.set(call.getArgument(0));return true;});
            var saved=CinematicRecovery.capture(a.p);
            t.manager.cinematics().backup(saved).join();t.manager.cinematics().activate(a.p,saved);
            t.bukkit.when(()->Bukkit.getWorld("world")).thenReturn(null);
            var done=new java.util.concurrent.atomic.AtomicBoolean();
            t.manager.cinematics().recover(a.p,true,()->true,()->done.set(true),()->fail("Fallback must restore"));
            assertTrue(done.get());assertEquals(secondary,a.at.get().getWorld());
            verify(secondary,atLeastOnce()).getChunkAtAsync(anyInt(),anyInt());
            t.manager.cinematics().close();
        }
    }

    @Test void bedAndAnchorInsideAnotherDungeonAreaOrRoomUseConfiguredSpawn() throws Exception {
        for(boolean bed:List.of(true,false))for(boolean area:List.of(true,false))
                try(var t=new DisconnectRecoveryTest.Fixture(DisconnectMode.DIE_AND_DROP,false,"secondary")) {
            var secondary=configure(t);
            var codec=new dev.dasan.customdungeons.config.DefinitionCodec();
            var yaml=new org.bukkit.configuration.file.YamlConfiguration();
            codec.encode(t.f.definition()).forEach(yaml::set);
            var region=Map.of("world","world","min",Map.of("x",70,"y",60,"z",70),
                    "max",Map.of("x",100,"y",90,"z",100));
            if(area)yaml.set("area",region);
            else {
                var rooms=new ArrayList<Map<String,Object>>();
                for(var room:yaml.getMapList("rooms")) {
                    var copy=new LinkedHashMap<String,Object>();room.forEach((key,value)->copy.put(key.toString(),value));
                    copy.put("region",region);rooms.add(copy);
                }
                yaml.set("rooms",rooms);
            }
            when(t.f.definitions.dungeons()).thenReturn(Map.of("test",t.f.definition(),"other",codec.decodeDungeon("other",yaml)));
            // Both dungeon exits are in rooms: only the configured world's safe spawn remains.
            when(t.storage.disconnect(any())).thenReturn(CompletableFuture.completedFuture(Optional.of(
                    new DisconnectRecord(t.record.id(),t.record.player(),t.record.sessionId(),t.record.dungeonId(),
                            t.record.position(),t.f.definition().exit(),t.record.mode(),t.record.keepInventory()))));
            t.killEvents();t.manager.connected(t.player);
            var respawn=mock(PlayerRespawnEvent.class);when(respawn.getPlayer()).thenReturn(t.player);
            when(respawn.isBedSpawn()).thenReturn(bed);when(respawn.isAnchorSpawn()).thenReturn(!bed);
            when(respawn.getRespawnLocation()).thenReturn(new Location(t.f.world,80,70,80));
            t.listener.respawn(respawn);
            verify(respawn).setRespawnLocation(argThat((Location at)->at.getWorld()==secondary));
        }
    }

    @Test void unavailableAnchorPrefersExteriorExitWhenVanillaReportsNoPersonalSpawn() throws Exception {
        try(var t=new DisconnectRecoveryTest.Fixture(DisconnectMode.DIE_AND_DROP,false,"secondary")) {
            var secondary=configure(t);t.killEvents();t.manager.connected(t.player);
            var respawn=mock(PlayerRespawnEvent.class);when(respawn.getPlayer()).thenReturn(t.player);
            when(respawn.isBedSpawn()).thenReturn(false);when(respawn.isAnchorSpawn()).thenReturn(false);
            when(respawn.getRespawnLocation()).thenReturn(new Location(t.f.world,500,70,500));
            t.listener.respawn(respawn);
            verify(respawn).setRespawnLocation(argThat((Location at)->at.getWorld()==t.f.world && at.getX()==99));
        }
    }
    @Test void finishAlwaysWaitsForAsyncChunkEvenIfAlreadyLoaded() {
        try(var t=new ReturnRecoveryRegressionTest.Fixture()) {
            var session=new DungeonSession(t.f.definition(),false,new SessionServices(){});
            var runtime=t.f.runtime(t.manager,session);var pending=new CompletableFuture<Chunk>();
            when(t.f.world.getChunkAtAsync(6,0)).thenReturn(pending);
            runtime.teleport(t.player,t.target.exit());
            verify(t.f.world).getChunkAtAsync(6,0);verify(t.player,never()).teleport(any(Location.class));
            assertEquals(JoinResult.RESETTING,t.manager.join(t.player,"missing"));
            var chunk=mock(Chunk.class);when(chunk.getWorld()).thenReturn(t.f.world);when(chunk.getX()).thenReturn(6);
            pending.complete(chunk);
            var order=inOrder(chunk,t.player);order.verify(chunk).addPluginChunkTicket(t.f.plugin);
            order.verify(t.player).teleport(argThat((Location at)->at.getX()==99));
            order.verify(chunk).removePluginChunkTicket(t.f.plugin);
            assertEquals(JoinResult.DISABLED,t.manager.join(t.player,"missing"));
        }
    }
    @Test void finishSpawnFallbackPreloadsSearchAndDestinationBeforeTeleport() {
        try(var t=new ReturnRecoveryRegressionTest.Fixture("secondary")) {
            var secondary=configure(t);when(secondary.isChunkLoaded(anyInt(),anyInt())).thenReturn(false);
            var pending=new CompletableFuture<Chunk>();when(secondary.getChunkAtAsync(anyInt(),anyInt())).thenReturn(pending);
            var session=new DungeonSession(t.f.definition(),false,new SessionServices(){});
            var runtime=t.f.runtime(t.manager,session);
            // Only the configured world has safe ground. Search must wait for its chunks.
            when(t.f.world.getWorldBorder().isInside(any(Location.class))).thenReturn(false);
            runtime.teleport(t.player,runtime.destination(session,t.player));
            verify(t.player,never()).teleport(any(Location.class));
            when(secondary.isChunkLoaded(anyInt(),anyInt())).thenReturn(true);
            var chunk=mock(Chunk.class);when(chunk.getWorld()).thenReturn(secondary);pending.complete(chunk);
            verify(t.player).teleport(argThat((Location at)->at.getWorld()==secondary && at.getY()==70));
            verify(secondary,times(10)).getChunkAtAsync(anyInt(),anyInt());
        }
    }
    @Test void lateFinishChunkCannotTeleportANewConnection() {
        try(var t=new ReturnRecoveryRegressionTest.Fixture()) {
            var session=new DungeonSession(t.f.definition(),false,new SessionServices(){});
            var runtime=t.f.runtime(t.manager,session);var pending=new CompletableFuture<Chunk>();
            when(t.f.world.getChunkAtAsync(6,0)).thenReturn(pending);
            runtime.teleport(t.player,t.target.exit());t.manager.disconnected(t.player);
            var chunk=mock(Chunk.class);when(chunk.getWorld()).thenReturn(t.f.world);pending.complete(chunk);
            verify(t.player,never()).teleport(any(Location.class));verify(chunk,never()).addPluginChunkTicket(any());
        }
    }
    @Test void allSpawnsAndExitsInsideDungeonsNeverThrowAndAlwaysReplaceVanilla() {
        try(var t=new DisconnectRecoveryTest.Fixture(DisconnectMode.DIE_AND_DROP,false)) {
            var codec=new dev.dasan.customdungeons.config.DefinitionCodec();
            var yaml=new org.bukkit.configuration.file.YamlConfiguration();codec.encode(t.f.definition()).forEach(yaml::set);
            yaml.set("area",Map.of("world","world","min",Map.of("x",-1000,"y",-64,"z",-1000),
                    "max",Map.of("x",1000,"y",319,"z",1000)));
            when(t.f.definitions.dungeons()).thenReturn(Map.of("test",codec.decodeDungeon("test",yaml)));
            when(t.player.isDead()).thenReturn(true);t.manager.connected(t.player);
            var respawn=mock(PlayerRespawnEvent.class);when(respawn.getPlayer()).thenReturn(t.player);
            when(respawn.getRespawnLocation()).thenReturn(new Location(t.f.world,1,64,1));
            assertDoesNotThrow(()->t.listener.respawn(respawn));
            verify(respawn).setRespawnLocation(argThat((Location at)->at.getWorld()==t.f.world && at.getY()==70));
            verify(t.logger,atLeastOnce()).warning(anyString());
        }
    }
}
