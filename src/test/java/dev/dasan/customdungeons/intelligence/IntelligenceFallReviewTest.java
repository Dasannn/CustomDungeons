package dev.dasan.customdungeons.intelligence;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import dev.dasan.customdungeons.mob.MobsPlatform;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Permanent regressions from the second review: no landing teleport or retained Player. */
class IntelligenceFallReviewTest {
    static {dev.dasan.customdungeons.ability.impl.PaperApiTestBootstrap.initialize();}
    private static final class Fixture implements AutoCloseable {
        final Player player=mock(Player.class);
        final World world=mock(World.class);
        final AtomicInteger tick=new AtomicInteger();
        final org.mockito.MockedStatic<Bukkit> bukkit=mockStatic(Bukkit.class);
        final IntelligenceService service=new IntelligenceService(mock(MobsPlatform.class),IntelligenceRules.defaults());
        Fixture() throws Exception {
            when(player.getUniqueId()).thenReturn(new UUID(0,1));
            when(player.getLocation()).thenReturn(new Location(world,0,120,0));
            when(world.getSpawnLocation()).thenReturn(new Location(world,0,64,0));
            bukkit.when(Bukkit::getCurrentTick).thenAnswer(i->tick.get());
            bukkit.when(()->Bukkit.getPlayer(player.getUniqueId())).thenReturn(player);
            var method=IntelligenceService.class.getDeclaredMethod("protectFall",Player.class);
            method.setAccessible(true);method.invoke(service,player);
        }
        int guards() throws Exception {
            var field=IntelligenceService.class.getDeclaredField("falls");field.setAccessible(true);
            return ((Map<?,?>)field.get(service)).size();
        }
        @Override public void close() {when(player.isOnGround()).thenReturn(true);service.close();bukkit.close();}
    }
    @Test void disableMustNotLeaveAnUnsettledFallWhenTeleportIsRejected() throws Exception {
        try(var f=new Fixture()) {
            // Player#teleport returns false, as with a cancelled PlayerTeleportEvent.
            f.service.close();
            assertEquals(0,f.guards());verify(f.player).setFallDistance(0);verify(f.player,never()).teleport(any(Location.class),any(PlayerTeleportEvent.TeleportCause.class));
        }
    }
    @Test void quitMustReleasePlayerReferenceWhenTeleportIsRejected() throws Exception {
        try(var f=new Fixture()) {
            var quit=mock(PlayerQuitEvent.class);when(quit.getPlayer()).thenReturn(f.player);
            f.service.quit(quit);
            assertEquals(0,f.guards());verify(f.player).setFallDistance(0);verify(f.player,never()).teleport(any(Location.class),any(PlayerTeleportEvent.TeleportCause.class));
        }
    }
    @Test void expiredGuardRetriesFailedLandingForever() throws Exception {
        try(var f=new Fixture()) {
            f.tick.set(601);f.service.move(new PlayerMoveEvent(f.player,f.player.getLocation(),f.player.getLocation()));
            f.tick.set(100000);f.service.move(new PlayerMoveEvent(f.player,f.player.getLocation(),f.player.getLocation()));
            assertEquals(0,f.guards(),"hard deadline must retire the guard even when teleport would fail");
        }
    }
    @Test void hardDeadlineExpiresWithoutMovementOrAnyLivingHost() throws Exception {
        try(var f=new Fixture()) {
            f.tick.set(199);IntelligenceService.tickProtectedFalls();assertEquals(1,f.guards());
            f.tick.set(200);IntelligenceService.tickProtectedFalls();assertEquals(0,f.guards());
            verify(f.player).setFallDistance(0);verify(f.player,never()).teleport(any(Location.class),any(PlayerTeleportEvent.TeleportCause.class));
        }
    }
    @Test void expiredFallDamageIsNotCancelledEvenBeforeNextTickerSweep() throws Exception {
        try(var f=new Fixture()) {
            f.tick.set(200);var damage=mock(EntityDamageEvent.class);
            when(damage.getEntity()).thenReturn(f.player);when(damage.getCause()).thenReturn(EntityDamageEvent.DamageCause.FALL);
            f.service.fall(damage);verify(damage,never()).setCancelled(true);assertEquals(0,f.guards());verify(f.player).setFallDistance(0);
        }
    }
    @Test void worldChangeAndGroundContactRetireProtectionImmediately() throws Exception {
        try(var f=new Fixture()) {
            f.service.worldChanged(new PlayerChangedWorldEvent(f.player,f.world));assertEquals(0,f.guards());verify(f.player).setFallDistance(0);
        }
        try(var f=new Fixture()) {
            when(f.player.isOnGround()).thenReturn(true);
            f.service.move(new PlayerMoveEvent(f.player,f.player.getLocation(),f.player.getLocation()));assertEquals(0,f.guards());verify(f.player).setFallDistance(0);
        }
    }
    @Test void deathRetiresGuardAndRepeatedCloseIsSafeOnGround() throws Exception {
        try(var f=new Fixture()) {
            var death=mock(PlayerDeathEvent.class);when(death.getEntity()).thenReturn(f.player);
            f.service.playerDeath(death);assertEquals(0,f.guards());
            f.service.close();f.service.close();assertEquals(0,f.guards());
        }
    }
}
