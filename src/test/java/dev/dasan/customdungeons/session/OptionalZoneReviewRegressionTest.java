package dev.dasan.customdungeons.session;

import dev.dasan.customdungeons.ability.*;
import dev.dasan.customdungeons.ability.impl.borrowed.SummonVexesAbility;
import dev.dasan.customdungeons.ability.impl.custom.FreezeAbility;
import dev.dasan.customdungeons.ability.impl.OnHitEffectAbility;
import dev.dasan.customdungeons.mob.*;
import dev.dasan.customdungeons.model.*;
import dev.dasan.customdungeons.runtime.ActiveMob;
import java.util.*;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OptionalZoneReviewRegressionTest {
    static {dev.dasan.customdungeons.ability.impl.PaperApiTestBootstrap.initialize();}
    final OptionalZoneRuntimeTest fixture=new OptionalZoneRuntimeTest();
    ActiveMob caster(DungeonSession s) { return fixture.zone(s,"a").getFirst(); }
    @Test void realVexAbilityCannotBypassFullGlobalCapacity() {
        var s=fixture.start(fixture.definition(1,false),fixture.player(1));s.maxAlive(3);fixture.ticks(s,10);
        var platform=mock(MobsPlatform.class);when(platform.limits()).thenReturn(new dev.dasan.customdungeons.config.PluginConfig.PerformanceLimits(3,1,48));
        new AbilityEngine(new AbilityRegistry(),platform);
        var caster=caster(s);var ability=new SummonVexesAbility();
        ability.execute(new AbilityContext(caster,List.of(),new ParamValues(Map.of("count",3),ability.params()),caster.session(),null));
        assertEquals(3,s.mobs().size());verify(fixture.world,never()).spawn(any(Location.class),eq(Vex.class),any(java.util.function.Consumer.class));
    }
    @Test void freezeRevalidatesPreviouslySelectedTargetWithoutFilteringTheAudience() {
        var p=fixture.player(1);when(p.isValid()).thenReturn(true);when(p.getWorld()).thenReturn(fixture.world);
        var s=fixture.start(fixture.definition(1,false),p);fixture.ticks(s,10);var caster=caster(s);
        var targets=TargetSelector.select(caster,TargetMode.ALL_IN_RADIUS,32);assertEquals(List.of(p),targets);
        var ability=new FreezeAbility();var context=new AbilityContext(caster,targets,new ParamValues(Map.of(),ability.params()),caster.session(),null);
        fixture.remove(s,s.roomMobs());s.tick();assertEquals(1,s.roomIndex());
        when(p.getLocation()).thenReturn(new Location(fixture.world,21,64,1));
        assertTrue(TargetSelector.select(caster,TargetMode.ALL_IN_RADIUS,32).isEmpty());
        assertTrue(caster.session().audience(caster.entity().getLocation()).contains(p));
        ability.execute(context);verify(p,never()).setFreezeTicks(anyInt());
        when(p.getLocation()).thenReturn(new Location(fixture.world,1,64,1));ability.execute(context);verify(p).setFreezeTicks(200);
    }
    @Test void directTargetEffectRevalidatesTheOriginAtExecution() {
        var p=fixture.player(1);var s=fixture.start(fixture.definition(1,false),p);fixture.ticks(s,10);var caster=caster(s);
        var ability=new OnHitEffectAbility();var context=new AbilityContext(caster,List.of(p),new ParamValues(Map.of(),ability.params()),caster.session(),null);
        when(p.getLocation()).thenReturn(new Location(fixture.world,21,64,1));ability.execute(context);
        verify(p,never()).addPotionEffect(any());
        when(p.getLocation()).thenReturn(new Location(fixture.world,1,64,1));ability.execute(context);verify(p).addPotionEffect(any());
    }
    @Test void realVexAbilityUsesTheLastGlobalSlotAndReservesItDuringSpawnEvents() {
        var setup=new SessionRuntimeRegressionTest();setup.configure();
        DungeonSessionRuntime[] runtime={null};
        var services=new SessionServices() {
            public ActiveMob spawn(DungeonSession s,SpawnOrigin o,String template,Location at) { return fixture.services.spawn(s,o,template,at); }
            public Location spawnLocation(DungeonSession s,SpawnOrigin o) { return fixture.services.spawnLocation(s,o); }
            public ActiveMob spawn(DungeonSession s,SpawnOrigin o,MobTemplate template,Location at) { return runtime[0].spawn(s,o,template,at); }
        };
        var s=new DungeonSession(fixture.definition(1,false),true,services);
        s.join(fixture.player(1));s.join(fixture.player(1));s.forceStart();s.maxAlive(7);fixture.ticks(s,10);assertEquals(6,s.mobs().size());
        runtime[0]=setup.runtime(mock(SessionManager.class),s);
        var caster=caster(s);var ability=new SummonVexesAbility();
        var context=new AbilityContext(caster,List.of(),new ParamValues(Map.of("count",3),ability.params()),caster.session(),null);
        var vex=mock(Vex.class);when(vex.getUniqueId()).thenReturn(UUID.randomUUID());when(vex.isValid()).thenReturn(true);
        when(vex.getLocation()).thenReturn(new Location(fixture.world,1,65,1));
        var data=mock(org.bukkit.persistence.PersistentDataContainer.class);when(vex.getPersistentDataContainer()).thenReturn(data);
        int[] calls={0};
        doAnswer(c->{
            ((java.util.function.Consumer<Vex>)c.getArgument(4)).accept(vex);
            if(++calls[0]==1)ability.execute(context); // Paper can dispatch listeners before spawn() returns.
            return vex;
        }).when(fixture.world).spawn(any(Location.class),eq(Vex.class),eq(org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.CUSTOM),eq(false),any());
        ability.execute(context);
        assertEquals(1,calls[0]);assertEquals(7,s.mobs().size());
        assertEquals(s.origin(caster.entity().getUniqueId()),s.origin(vex.getUniqueId()));
        assertEquals(3,fixture.zone(s,"a").size());assertEquals(2,s.roomMobs().size());
        verify(data).set(MobKeys.ORIGIN,org.bukkit.persistence.PersistentDataType.STRING,"zone:a");
        verify(data).set(MobKeys.SPAWNER,org.bukkit.persistence.PersistentDataType.STRING,"same");
        verify(data).set(MobKeys.HEALTH_MULTIPLIER,org.bukkit.persistence.PersistentDataType.DOUBLE,1.15);
        fixture.remove(s,List.of(s.mob(vex.getUniqueId())));fixture.ticks(s,2);assertEquals(6,s.mobs().size());
    }

    @Test void abilityProjectileRevalidatesOriginWhenTheSelectedPlayerLeaves() {
        var p=fixture.player(1);var s=fixture.start(fixture.definition(1,false),p);fixture.ticks(s,10);var caster=caster(s);
        var projectile=mock(org.bukkit.entity.Arrow.class);var data=mock(org.bukkit.persistence.PersistentDataContainer.class);
        when(projectile.getPersistentDataContainer()).thenReturn(data);
        when(caster.entity().launchProjectile(eq(Arrow.class),any(org.bukkit.util.Vector.class),any(java.util.function.Consumer.class)))
                .thenAnswer(c->{((java.util.function.Consumer<Arrow>)c.getArgument(2)).accept(projectile);return projectile;});
        Effects.launch(caster,Arrow.class,new org.bukkit.util.Vector(1,0,0));
        assertTrue(Effects.projectileTargetAllowed(projectile,p));when(p.getLocation()).thenReturn(new Location(fixture.world,21,64,1));
        assertFalse(Effects.projectileTargetAllowed(projectile,p));
    }
    @Test void vanillaTargetAndHitRespectTheOriginWhileAudienceStillContainsThePlayer() {
        var p=fixture.player(1);var s=fixture.start(fixture.definition(1,false),p);fixture.ticks(s,10);var caster=caster(s);
        when(caster.entity().getPersistentDataContainer().get(MobKeys.SESSION,org.bukkit.persistence.PersistentDataType.STRING)).thenReturn(s.id().toString());
        var manager=mock(SessionManager.class);when(manager.byId(s.id().toString())).thenReturn(Optional.of(s));
        var listener=new SessionListener(manager);when(p.getLocation()).thenReturn(new Location(fixture.world,21,64,1));
        var target=mock(org.bukkit.event.entity.EntityTargetLivingEntityEvent.class);when(target.getEntity()).thenReturn(caster.entity());when(target.getTarget()).thenReturn(p);
        listener.target(target);verify(target).setCancelled(true);
        var hit=mock(org.bukkit.event.entity.EntityDamageByEntityEvent.class);when(hit.getDamager()).thenReturn(caster.entity());when(hit.getEntity()).thenReturn(p);
        listener.hit(hit);verify(hit).setCancelled(true);verify(manager,never()).runtime(s);
        assertTrue(caster.session().audience(p.getLocation()).contains(p));
    }

}
