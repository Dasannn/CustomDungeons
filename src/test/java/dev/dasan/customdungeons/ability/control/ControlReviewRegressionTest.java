package dev.dasan.customdungeons.ability.control;

import dev.dasan.customdungeons.ability.FallProtection;
import dev.dasan.customdungeons.mob.MobDamageAppliedEvent;
import java.util.List;
import java.util.stream.Stream;
import org.bukkit.entity.Entity;
import org.bukkit.GameMode;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.*;
import org.bukkit.potion.*;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Permanent regressions for the three T57a review findings. */
class ControlReviewRegressionTest {
    static {dev.dasan.customdungeons.ability.impl.PaperApiTestBootstrap.initialize();}
    static Stream<Arguments> airborneReleases() {
        return Stream.of("grab_throw","levitation_cage").flatMap(id ->
            Stream.of("teleport","world","leave","playerDeath","mobDeath","retire","reload","escape","duration")
                .map(reason -> Arguments.of(id,reason)));
    }
    @ParameterizedTest @MethodSource("airborneReleases")
    void releaseKeepsFallGuardUntilLanding(String id,String reason) {
        try(var f=new ControlServiceTest.Fixture()) {
            f.start(id);
            switch(reason) {
                case "teleport" -> f.service.teleport(new PlayerTeleportEvent(f.player,f.player.getLocation(),f.player.getLocation().add(0,20,0),PlayerTeleportEvent.TeleportCause.PLUGIN));
                case "world" -> {
                    var event=new PlayerChangedWorldEvent(f.player,f.world);
                    f.service.world(event);FallProtection.shared().world(event);
                }
                case "leave" -> {f.players.remove(f.player);ControlService.releasePlayer(f.player);}
                case "playerDeath" -> {
                    var event=mock(PlayerDeathEvent.class);when(event.getEntity()).thenReturn(f.player);
                    f.service.playerDeath(event);FallProtection.shared().death(event);
                }
                case "mobDeath" -> {var event=mock(EntityDeathEvent.class);when(event.getEntity()).thenReturn(f.entity);f.service.death(event);}
                case "retire" -> ControlService.cleanup(f.caster);
                case "reload" -> ControlService.reset();
                case "escape" -> {
                    var hit=mock(EntityDamageByEntityEvent.class);when(hit.getDamager()).thenReturn(f.ally);
                    f.service.mobDamage(new MobDamageAppliedEvent(f.entity,hit,20,80));
                }
                case "duration" -> f.step(id.equals("grab_throw")?30:80);
            }
            assertEquals(0,f.service.activeCount());assertTrue(FallProtection.shared().hasPending(),reason);
            var fall=mock(EntityDamageEvent.class);when(fall.getEntity()).thenReturn(f.player);when(fall.getCause()).thenReturn(EntityDamageEvent.DamageCause.FALL);
            FallProtection.shared().fall(fall);verify(fall).setCancelled(true);
            when(f.player.isOnGround()).thenReturn(true);
            FallProtection.shared().move(new PlayerMoveEvent(f.player,f.player.getLocation(),f.player.getLocation()));
            assertFalse(FallProtection.shared().hasPending());
        }
    }
    @Test void teleportWithoutAnActiveControlKeepsExistingGuardAndItsDeadline() {
        try(var f=new ControlServiceTest.Fixture()) {
            FallProtection.shared().protect(f.player);
            f.service.teleport(new PlayerTeleportEvent(f.player,f.player.getLocation(),f.player.getLocation().add(0,20,0),PlayerTeleportEvent.TeleportCause.PLUGIN));
            f.step(199);FallProtection.shared().tick();assertTrue(FallProtection.shared().hasPending());
            f.step(1);FallProtection.shared().tick();assertFalse(FallProtection.shared().hasPending());
        }
    }
    static Stream<PotionEffect> replacementEffects() {
        return Stream.of(new PotionEffect(PotionEffectType.LEVITATION,40,0,false,false,true),
            new PotionEffect(PotionEffectType.LEVITATION,80,0,true,false,true),
            new PotionEffect(PotionEffectType.LEVITATION,80,0,false,true,true),
            new PotionEffect(PotionEffectType.LEVITATION,80,0,false,false,false),
            new PotionEffect(PotionEffectType.LEVITATION,80,1,false,false,true),
            new PotionEffect(PotionEffectType.LEVITATION,80,0,false,false,true,new PotionEffect(PotionEffectType.LEVITATION,200,0)));
    }
    @ParameterizedTest @MethodSource("replacementEffects")
    void cleanupPreservesEveryDifferentReplacementPotion(PotionEffect replacement) {
        try(var f=new ControlServiceTest.Fixture()) {
            f.start("levitation_cage");f.player.addPotionEffect(replacement);ControlService.cleanup(f.caster);
            assertEquals(replacement,f.player.getPotionEffect(PotionEffectType.LEVITATION));
            verify(f.player,never()).removePotionEffect(PotionEffectType.LEVITATION);
        }
    }
    private static void dispatchPotion(ControlService service,EntityPotionEffectEvent event) throws Exception {
        for(var method:ControlService.class.getMethods()) {
            var handler=method.getAnnotation(org.bukkit.event.EventHandler.class);
            if(handler!=null&&method.getParameterCount()==1&&method.getParameterTypes()[0]==EntityPotionEffectEvent.class
                    &&!(handler.ignoreCancelled()&&event.isCancelled()))method.invoke(service,event);
        }
    }
    @Test void successfulForeignReplacementRelinquishesEvenAnIdenticalPotion() throws Exception {
        try(var f=new ControlServiceTest.Fixture()) {
            f.start("levitation_cage");var applied=f.player.getPotionEffect(PotionEffectType.LEVITATION);
            var event=new EntityPotionEffectEvent(f.player,applied,applied,null,EntityPotionEffectEvent.Cause.COMMAND,EntityPotionEffectEvent.Action.CHANGED,true);
            dispatchPotion(f.service,event);ControlService.cleanup(f.caster);
            assertEquals(applied,f.player.getPotionEffect(PotionEffectType.LEVITATION));verify(f.player,never()).removePotionEffect(PotionEffectType.LEVITATION);
        }
    }
    @Test void cancelledOrNonOverridingPotionAttemptKeepsOwnership() throws Exception {
        for(boolean cancelled:List.of(true,false))try(var f=new ControlServiceTest.Fixture()) {
            f.start("levitation_cage");var applied=f.player.getPotionEffect(PotionEffectType.LEVITATION);
            var event=new EntityPotionEffectEvent(f.player,applied,applied.withDuration(1),null,EntityPotionEffectEvent.Cause.PLUGIN,EntityPotionEffectEvent.Action.CHANGED,cancelled);
            event.setCancelled(cancelled);dispatchPotion(f.service,event);ControlService.cleanup(f.caster);
            verify(f.player).removePotionEffect(PotionEffectType.LEVITATION);
        }
    }
    @Test void exactAppliedSnapshotKeepsFlagsAndHiddenEffectsDuringNaturalAging() {
        try(var f=new ControlServiceTest.Fixture()) {
            var hidden=new PotionEffect(PotionEffectType.LEVITATION,200,0,true,true,false);
            var applied=new PotionEffect(PotionEffectType.LEVITATION,80,0,true,true,false,hidden);
            doReturn(applied).when(f.player).getPotionEffect(PotionEffectType.LEVITATION);
            f.start("levitation_cage");f.step(20);
            var aged=new PotionEffect(PotionEffectType.LEVITATION,60,0,true,true,false,new PotionEffect(PotionEffectType.LEVITATION,180,0,true,true,false));
            doReturn(aged).when(f.player).getPotionEffect(PotionEffectType.LEVITATION);
            ControlService.cleanup(f.caster);verify(f.player).removePotionEffect(PotionEffectType.LEVITATION);
        }
    }
    @Test void naturallyAgedOwnedPotionIsStillRemoved() {
        try(var f=new ControlServiceTest.Fixture()) {
            f.start("levitation_cage");f.step(20);
            doReturn(new PotionEffect(PotionEffectType.LEVITATION,60,0,false,false,true)).when(f.player).getPotionEffect(PotionEffectType.LEVITATION);
            ControlService.cleanup(f.caster);verify(f.player).removePotionEffect(PotionEffectType.LEVITATION);
        }
    }
    @Test void throwHitsAnInterceptorInsteadOfOnlyItsAimedDestination() {
        try(var f=new ControlServiceTest.Fixture()) {
            var intercept=f.player(3);f.players.add(intercept);f.start("grab_throw");
            doReturn(f.player.getBoundingBox()).when(intercept).getBoundingBox();f.step(30);
            verify(f.player).damage(6,f.entity);verify(intercept).damage(6,f.entity);verify(intercept).setVelocity(any(Vector.class));
            verify(f.ally,never()).damage(anyDouble(),any(Entity.class));
        }
    }
    @Test void sweptThrowHitsFirstEligibleInterceptorAndNeverOutsiders() {
        try(var f=new ControlServiceTest.Fixture()) {
            var first=f.player(4);var second=f.player(6);var outside=f.player(2);var creative=f.player(3);
            when(creative.getGameMode()).thenReturn(GameMode.CREATIVE);
            f.players.addAll(List.of(second,first,creative));
            // The aimed player moves away; participants intercept the next motion segment.
            doReturn(new BoundingBox(20,64,0,20.6,66,.6)).when(f.ally).getBoundingBox();
            f.start("grab_throw");f.step(30);
            doReturn(new BoundingBox(10,64,0,10.6,66,.6)).when(f.player).getBoundingBox();f.step(10);
            verify(f.player).damage(6,f.entity);verify(first).damage(6,f.entity);verify(first).setVelocity(any(Vector.class));
            for(var untouched:List.of(second,outside,creative,f.ally))verify(untouched,never()).damage(anyDouble(),any(Entity.class));
            assertTrue(FallProtection.shared().hasPending());
        }
    }
}
