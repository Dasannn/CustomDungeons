package dev.dasan.customdungeons.intelligence;

import dev.dasan.customdungeons.mob.*;
import dev.dasan.customdungeons.runtime.ActiveMob;
import dev.dasan.customdungeons.model.*;
import java.util.*;
import org.bukkit.entity.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class IntelligenceServiceTest {
    static {dev.dasan.customdungeons.ability.impl.PaperApiTestBootstrap.initialize();}
    @Test void zeroCreatesNoMemoryAndPhaseKeepsSameMemoryAcrossZero() {
        var platform=mock(MobsPlatform.class);var entity=mock(Mob.class);when(entity.getUniqueId()).thenReturn(UUID.randomUUID());
        var host=mock(MobHost.class);
        var template=new MobTemplate("a","HUSK","",20,1,0,0,1,Map.of(),List.of(),List.of(),List.of(),true,"PURPLE",null,List.of(),false);
        var mob=new ActiveMob(entity,template,host);
        try(var service=new IntelligenceService(platform,IntelligenceRules.defaults())) {
            IntelligenceService.track(mob);assertNull(IntelligenceService.brain(mob));verifyNoInteractions(host);
            IntelligenceService.phase(mob,IntelligenceDef.level(3),0);var memory=IntelligenceService.brain(mob).memory();UUID p=UUID.randomUUID();memory.record(p,"damage","x",1,0);
            IntelligenceService.phase(mob,IntelligenceDef.level(0),1);assertSame(memory,IntelligenceService.brain(mob).memory());assertEquals(1,memory.playerCount());
            IntelligenceService.phase(mob,IntelligenceDef.level(5),2);assertSame(memory,IntelligenceService.brain(mob).memory());
            IntelligenceService.cleanup(mob);assertNull(IntelligenceService.brain(mob));assertEquals(0,memory.playerCount());
        }
    }
    @Test void dyingOrRemovingMobClearsMemory() {
        var entity=mock(Mob.class);when(entity.getUniqueId()).thenReturn(UUID.randomUUID());
        var template=new MobTemplate("a","HUSK","",20,1,0,0,1,Map.of(),List.of(),List.of(),List.of(),false,"PURPLE",null,List.of(),false).withIntelligence(IntelligenceDef.level(1));
        var mob=new ActiveMob(entity,template,mock(MobHost.class));
        try(var service=new IntelligenceService(mock(MobsPlatform.class),IntelligenceRules.defaults())) {
            IntelligenceService.track(mob);var memory=IntelligenceService.brain(mob).memory();memory.record(UUID.randomUUID(),"damage","x",1,0);
            IntelligenceService.tick(mob,mock(dev.dasan.customdungeons.ability.AbilityEngine.class),1);
            assertNull(IntelligenceService.brain(mob));assertEquals(0,memory.playerCount());
        }
    }
    @Test void shieldDetectionRequiresActualBlockedDamage() {
        var event=mock(org.bukkit.event.entity.EntityDamageByEntityEvent.class);
        assertFalse(IntelligenceService.shieldBlocked(event));
        when(event.isApplicable(org.bukkit.event.entity.EntityDamageEvent.DamageModifier.BLOCKING)).thenReturn(true);
        assertFalse(IntelligenceService.shieldBlocked(event));
        when(event.getDamage(org.bukkit.event.entity.EntityDamageEvent.DamageModifier.BLOCKING)).thenReturn(-4d);
        assertTrue(IntelligenceService.shieldBlocked(event));
    }
    @Test void damageReceiptsAndAllowedAirInteractionsFeedMemory() {
        var world=mock(org.bukkit.World.class);var entity=mock(Mob.class);var player=mock(Player.class);
        when(entity.getUniqueId()).thenReturn(new UUID(0,0));when(entity.isValid()).thenReturn(true);when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(entity.getWorld()).thenReturn(world);when(player.getWorld()).thenReturn(world);
        when(entity.getLocation()).thenReturn(new org.bukkit.Location(world,0,100,0));when(player.getLocation()).thenReturn(new org.bukkit.Location(world,1,100,0));
        when(player.isOnline()).thenReturn(true);when(player.isValid()).thenReturn(true);when(player.getGameMode()).thenReturn(org.bukkit.GameMode.SURVIVAL);
        var inventory=mock(org.bukkit.inventory.PlayerInventory.class);when(player.getInventory()).thenReturn(inventory);
        var sword=mock(org.bukkit.inventory.ItemStack.class);when(sword.getType()).thenReturn(org.bukkit.Material.DIAMOND_SWORD);when(inventory.getItemInMainHand()).thenReturn(sword);
        var host=mock(MobHost.class);when(host.players()).thenReturn(List.of(player));
        var clock=mock(dev.dasan.customdungeons.runtime.TickScheduler.class);when(host.scheduler()).thenReturn(clock);
        var template=new MobTemplate("a","HUSK","",20,1,0,0,1,Map.of(),List.of(),List.of(),List.of(),false,"PURPLE",null,List.of(),false).withIntelligence(IntelligenceDef.level(5));
        var mob=new ActiveMob(entity,template,host);
        var hit=mock(org.bukkit.event.entity.EntityDamageByEntityEvent.class);when(hit.getDamager()).thenReturn(player);when(hit.isCritical()).thenReturn(true);when(hit.getCause()).thenReturn(org.bukkit.event.entity.EntityDamageEvent.DamageCause.ENTITY_ATTACK);
        try(var bukkit=mockStatic(org.bukkit.Bukkit.class);var service=new IntelligenceService(mock(MobsPlatform.class),IntelligenceRules.defaults())) {
            bukkit.when(()->org.bukkit.Bukkit.getPlayer(player.getUniqueId())).thenReturn(player);
            IntelligenceService.track(mob);service.applied(new MobDamageAppliedEvent(entity,hit,7,13));
            service.applied(new MobDamageAppliedEvent(entity,hit,7,6));
            assertEquals("critical",IntelligenceService.brain(mob).evaluate(0).getFirst().rule().id());
            IntelligenceService.brain(mob).clear();IntelligenceService.tick(mob,mock(dev.dasan.customdungeons.ability.AbilityEngine.class),0);
            service.glide(new org.bukkit.event.entity.EntityToggleGlideEvent(player,true));
            when(player.isGliding()).thenReturn(true);
            var rocket=mock(org.bukkit.inventory.ItemStack.class);when(rocket.getType()).thenReturn(org.bukkit.Material.FIREWORK_ROCKET);
            var click=mock(org.bukkit.event.player.PlayerInteractEvent.class);when(click.getPlayer()).thenReturn(player);when(click.getItem()).thenReturn(rocket);when(click.getAction()).thenReturn(org.bukkit.event.block.Action.RIGHT_CLICK_AIR);
            when(click.isCancelled()).thenReturn(true);when(click.useItemInHand()).thenReturn(org.bukkit.event.Event.Result.ALLOW);
            try {
                boolean ignored=IntelligenceService.class.getMethod("interact",org.bukkit.event.player.PlayerInteractEvent.class).getAnnotation(org.bukkit.event.EventHandler.class).ignoreCancelled();
                var listener=new org.bukkit.plugin.RegisteredListener(service,(target,event)->((IntelligenceService)target).interact((org.bukkit.event.player.PlayerInteractEvent)event),org.bukkit.event.EventPriority.MONITOR,mock(org.bukkit.plugin.Plugin.class),ignored);
                listener.callEvent(click);
                assertEquals(2,IntelligenceService.brain(mob).memory().repetitions(player.getUniqueId(),"flight",0,200));
                when(click.useItemInHand()).thenReturn(org.bukkit.event.Event.Result.DENY);listener.callEvent(click);
                assertEquals(2,IntelligenceService.brain(mob).memory().repetitions(player.getUniqueId(),"flight",0,200));
            } catch(Exception failure){throw new AssertionError(failure);}
        }
    }
}
