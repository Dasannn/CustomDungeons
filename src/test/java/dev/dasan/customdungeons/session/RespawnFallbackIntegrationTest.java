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
        when(secondary.isChunkLoaded(anyInt(),anyInt())).thenReturn(true);
        var chunk=mock(Chunk.class);when(chunk.getWorld()).thenReturn(secondary);
        when(secondary.getChunkAtAsync(anyInt(),anyInt())).thenReturn(CompletableFuture.completedFuture(chunk));
        t.bukkit.when(Bukkit::getWorlds).thenReturn(List.of(t.f.world,secondary));
        t.bukkit.when(()->Bukkit.getWorld("secondary")).thenReturn(secondary);
        return secondary;
    }
    @Test void disconnectWithoutBedUsesConfiguredWorld() throws Exception {
        try(var t=new DisconnectRecoveryTest.Fixture(DisconnectMode.DIE_AND_DROP,false,"secondary")) {
            var secondary=configure(t);t.killEvents();t.manager.connected(t.player);
            var respawn=mock(PlayerRespawnEvent.class);when(respawn.getPlayer()).thenReturn(t.player);
            when(respawn.getRespawnLocation()).thenReturn(new Location(t.f.world,500,70,500));
            t.listener.respawn(respawn);
            verify(respawn).setRespawnLocation(argThat((Location at)->at.getWorld()==secondary));
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
            verify(secondary).getChunkAtAsync(anyInt(),anyInt());
            t.manager.cinematics().close();
        }
    }
}
