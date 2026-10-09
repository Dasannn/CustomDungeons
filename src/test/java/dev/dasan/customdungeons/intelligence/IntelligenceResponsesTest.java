package dev.dasan.customdungeons.intelligence;

import dev.dasan.customdungeons.ability.AbilityEngine;
import dev.dasan.customdungeons.config.PluginConfig;
import dev.dasan.customdungeons.mob.*;
import dev.dasan.customdungeons.model.MobTemplate;
import dev.dasan.customdungeons.runtime.*;
import dev.dasan.customdungeons.text.Messages;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.bukkit.*;
import org.bukkit.attribute.*;
import org.bukkit.entity.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.*;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Exercises the host tick, actual response mutation and damage listeners, not FairCombat alone. */
class IntelligenceResponsesTest {
    static {dev.dasan.customdungeons.ability.impl.PaperApiTestBootstrap.initialize();}
    private static final class Fight implements AutoCloseable {
        final Mob entity=mock(Mob.class);
        final Player player=mock(Player.class);
        final World world=mock(World.class);
        final AbilityEngine engine=mock(AbilityEngine.class);
        final AttributeInstance speed=mock(AttributeInstance.class),health=mock(AttributeInstance.class);
        final MockedStatic<Bukkit> bukkit=mockStatic(Bukkit.class);
        final AtomicInteger tick=new AtomicInteger(),itemCooldown=new AtomicInteger();
        final IntelligenceService service;
        final ActiveMob mob;
        final IntelligenceBrain brain;
        Fight(String rule) {
            when(entity.getUniqueId()).thenReturn(new UUID(0,0));when(entity.isValid()).thenReturn(true);
            when(entity.getWorld()).thenReturn(world);when(entity.getLocation()).thenReturn(new Location(world,0,100,0));
            when(entity.getAttribute(Attribute.MOVEMENT_SPEED)).thenReturn(speed);
            when(entity.getAttribute(Attribute.MAX_HEALTH)).thenReturn(health);when(health.getValue()).thenReturn(100d);
            when(player.getUniqueId()).thenReturn(new UUID(0,1));when(player.isOnline()).thenReturn(true);when(player.isValid()).thenReturn(true);
            when(player.getGameMode()).thenReturn(GameMode.SURVIVAL);when(player.getWorld()).thenReturn(world);
            when(player.getLocation()).thenReturn(new Location(world,1,125,0));when(player.getVelocity()).thenReturn(new Vector(0,0,0));
            when(player.getCooldown(any(Material.class))).thenAnswer(i->itemCooldown.get());
            doAnswer(i->{itemCooldown.set(i.getArgument(1));return null;}).when(player).setCooldown(any(Material.class),anyInt());
            var inventory=mock(org.bukkit.inventory.PlayerInventory.class);when(player.getInventory()).thenReturn(inventory);
            var sword=mock(org.bukkit.inventory.ItemStack.class);when(sword.getType()).thenReturn(Material.DIAMOND_SWORD);when(inventory.getItemInMainHand()).thenReturn(sword);
            var host=mock(MobHost.class);when(host.id()).thenReturn(UUID.randomUUID());when(host.players()).thenReturn(List.of(player));when(host.audience(any())).thenReturn(List.of(player));
            var clock=mock(TickScheduler.class);when(host.scheduler()).thenReturn(clock);when(clock.currentTick()).thenAnswer(i->(long)tick.get());
            var platform=mock(MobsPlatform.class);var limits=mock(PluginConfig.PerformanceLimits.class);when(platform.limits()).thenReturn(limits);when(limits.effectViewRadius()).thenReturn(48d);
            var messages=mock(Messages.class);when(platform.messages()).thenReturn(messages);when(messages.get(anyString())).thenReturn(net.kyori.adventure.text.Component.text("notice"));
            var template=new MobTemplate("responses","HUSK","",100,8,.2,0,1,Map.of(),List.of(),List.of(),List.of(),false,"PURPLE",null,List.of(),false).withIntelligence(IntelligenceDef.level(5));
            if(rule.equals("flight"))template=template.withIntelligence(new IntelligenceDef(5,IntelligenceDef.WeakPoint.NONE,0,Map.of("duration",1),Set.of()));
            mob=new ActiveMob(entity,template,host);
            bukkit.when(()->Bukkit.getPlayer(player.getUniqueId())).thenReturn(player);bukkit.when(Bukkit::getCurrentTick).thenAnswer(i->tick.get());
            service=new IntelligenceService(platform,IntelligenceRules.defaults());IntelligenceService.track(mob);brain=IntelligenceService.brain(mob);
            brain.memory().record(player.getUniqueId(),rule,"FIREWORK_ROCKET",0,0);brain.memory().record(player.getUniqueId(),rule,"FIREWORK_ROCKET",0,0);
        }
        void tick(int time) {tick.set(time);IntelligenceService.tick(mob,engine,time);}
        EntityDamageEvent fall() {var e=mock(EntityDamageEvent.class);when(e.getEntity()).thenReturn(player);when(e.getCause()).thenReturn(EntityDamageEvent.DamageCause.FALL);when(e.getDamage()).thenReturn(100d);when(e.getFinalDamage()).thenReturn(100d);service.fall(e);return e;}
        @Override public void close() {when(player.isOnGround()).thenReturn(true);service.close();bukkit.close();}
    }
    @ParameterizedTest @ValueSource(strings={"flight","mace","totem"})
    void strongResponsesAnnounceWaitAndAreInterruptedByRealDamageReceipt(String pattern) {
        try(var f=new Fight(pattern)) {
            f.tick(0);verify(f.player).sendActionBar(any(net.kyori.adventure.text.Component.class));
            assertEquals(1,f.brain.active().size());assertEquals(15,f.brain.active().getFirst().begins());
            f.tick(14);verify(f.player,never()).setGliding(false);verify(f.player,never()).setVelocity(any());verify(f.speed,never()).addTransientModifier(any());
            var hit=mock(EntityDamageByEntityEvent.class);when(hit.getDamager()).thenReturn(f.player);when(hit.getCause()).thenReturn(EntityDamageEvent.DamageCause.ENTITY_ATTACK);
            f.service.applied(new MobDamageAppliedEvent(f.entity,hit,6,94));f.tick(15);
            assertTrue(f.brain.active().isEmpty());verify(f.player,never()).setGliding(false);verify(f.player,never()).setVelocity(any());verify(f.speed,never()).addTransientModifier(any());
            assertEquals(0,f.itemCooldown.get());
        }
    }
    @ParameterizedTest @ValueSource(strings={"flight","mace"})
    void actualDisplacementCannotCauseLethalFallAfterCasterRemoval(String pattern) {
        try(var f=new Fight(pattern)) {
            f.tick(0);f.tick(15);
            if(pattern.equals("flight")){verify(f.player).setGliding(false);assertEquals(160,f.itemCooldown.get());}
            verify(f.player).setVelocity(any());
            IntelligenceService.cleanup(f.mob);assertEquals(0,f.itemCooldown.get());assertNull(IntelligenceService.brain(f.mob));
            verify(f.fall()).setCancelled(true); // 100 fall damage on a full-health player is suppressed.
        }
    }
    @ParameterizedTest @ValueSource(strings={"death","phase","reload","expiry"})
    void playerFallGuardSurvivesAllCasterCleanupPaths(String path) {
        try(var f=new Fight("flight")) {
            f.tick(0);f.tick(15);
            switch(path){case "death" -> f.service.death(new EntityDeathEvent(f.entity,mock(org.bukkit.damage.DamageSource.class),List.of()));case "phase" -> IntelligenceService.phase(f.mob,IntelligenceDef.level(0),16);case "reload" -> IntelligenceService.reset();default -> f.tick(36);}
            verify(f.fall()).setCancelled(true);
            when(f.player.isOnGround()).thenReturn(true);
            f.tick.set(400);f.service.move(new PlayerMoveEvent(f.player,f.player.getLocation(),f.player.getLocation()));
            f.tick.set(402);f.service.move(new PlayerMoveEvent(f.player,f.player.getLocation(),f.player.getLocation()));
            verify(f.fall(),never()).setCancelled(true); // Protection ended on landing; a later unrelated fall is vanilla.
        }
    }
    @Test void realEnrageBoostsMovementAndDamageButCannotOneShotFromFullHealth() {
        try(var f=new Fight("totem")) {
            f.tick(0);f.tick(15);verify(f.speed).addTransientModifier(any());
            var max=mock(AttributeInstance.class);when(max.getValue()).thenReturn(20d);when(f.player.getAttribute(Attribute.MAX_HEALTH)).thenReturn(max);
            var damage=new java.util.concurrent.atomic.AtomicReference<>(100d);
            var hit=mock(EntityDamageByEntityEvent.class);when(hit.getEntity()).thenReturn(f.player);when(hit.getDamager()).thenReturn(f.entity);
            when(hit.getDamage()).thenAnswer(i->damage.get());when(hit.getFinalDamage()).thenAnswer(i->damage.get());doAnswer(i->{damage.set(i.getArgument(0));return null;}).when(hit).setDamage(anyDouble());
            f.service.damage(hit);assertEquals(19d,damage.get());assertTrue(20-damage.get()>0);
            // Also verify the actual 15% boost below the non-lethal ceiling.
            damage.set(8d);f.service.damage(hit);assertEquals(9.2,damage.get(),1e-9);
            var modifier=mock(AttributeModifier.class);var key=MobsPlatform.key("intelligence_"+f.entity.getUniqueId()+"_totem");when(modifier.getKey()).thenReturn(key);when(f.speed.getModifiers()).thenReturn(List.of(modifier));
            IntelligenceService.cleanup(f.mob);verify(f.speed).removeModifier(modifier);
        }
    }
    @ParameterizedTest @ValueSource(strings={"consumables","shield","pearl","flight"})
    void reloadRestoresAllActualPlayerCooldownsIncludingHostsOutsideSessions(String pattern) {
        try(var f=new Fight(pattern)) {
            f.tick(0);f.tick(15);assertTrue(f.itemCooldown.get()>0);
            IntelligenceService.reset();assertEquals(0,f.itemCooldown.get());assertNull(IntelligenceService.brain(f.mob));
            if(pattern.equals("flight"))verify(f.fall()).setCancelled(true);
        }
    }
    @Test void disableSettlesProtectedFallBeforeDroppingListenerWithoutPersistentEffects() {
        try(var f=new Fight("flight")) {
            f.tick(0);f.tick(15);
            f.service.close();verify(f.player,never()).teleport(any(Location.class),any(PlayerTeleportEvent.TeleportCause.class));assertEquals(0,f.itemCooldown.get());
            verify(f.fall(),never()).setCancelled(true);verify(f.player,never()).addPotionEffect(any());
        }
    }
    @ParameterizedTest @ValueSource(strings={"quit","playerDeath","disable"})
    void commonCleanupRestoresShieldAndForgetsPlayerOnEveryTerminalPath(String path) {
        try(var f=new Fight("shield")) {
            f.tick(0);f.tick(15);assertEquals(80,f.itemCooldown.get());
            switch(path) {
                case "quit" -> {var event=mock(PlayerQuitEvent.class);when(event.getPlayer()).thenReturn(f.player);f.service.quit(event);}
                case "playerDeath" -> {var event=mock(PlayerDeathEvent.class);when(event.getEntity()).thenReturn(f.player);f.service.playerDeath(event);}
                default -> f.service.close();
            }
            assertEquals(0,f.itemCooldown.get());assertEquals(0,f.brain.memory().playerCount());
        }
    }
    @Test void quittingCompletesTheProtectedFallAndReleasesAllPlayerState() {
        try(var f=new Fight("flight")) {
            f.tick(0);f.tick(15);
            var quit=mock(PlayerQuitEvent.class);when(quit.getPlayer()).thenReturn(f.player);f.service.quit(quit);
            assertEquals(0,f.itemCooldown.get());assertEquals(0,f.brain.memory().playerCount());
            verify(f.player,never()).teleport(any(Location.class),any(PlayerTeleportEvent.TeleportCause.class));verify(f.fall(),never()).setCancelled(true);
        }
    }
}
