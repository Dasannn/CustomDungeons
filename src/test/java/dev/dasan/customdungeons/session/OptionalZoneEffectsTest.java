package dev.dasan.customdungeons.session;

import dev.dasan.customdungeons.mob.*;
import dev.dasan.customdungeons.model.*;
import dev.dasan.customdungeons.runtime.ActiveMob;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason;
import org.bukkit.persistence.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OptionalZoneEffectsTest {
    static {dev.dasan.customdungeons.ability.impl.PaperApiTestBootstrap.initialize();}
    final OptionalZoneRuntimeTest fixture=new OptionalZoneRuntimeTest();
    final SpawnOrigin origin=new SpawnOrigin(SpawnOrigin.Kind.ZONE,"a","same");

    @Test void factoryAndAuxiliariesAndVanillaProjectilesReceiveFullOriginBeforeSpawnEvents() {
        var s=fixture.start(fixture.definition(1,false),fixture.player(100));
        var host=s.host(origin);var entity=mock(Zombie.class);var data=mock(PersistentDataContainer.class);
        var strings=new HashMap<NamespacedKey,String>();when(entity.getPersistentDataContainer()).thenReturn(data);
        doAnswer(c->{strings.put(c.getArgument(0),c.getArgument(2));return null;}).when(data).set(any(),eq(PersistentDataType.STRING),anyString());
        when(data.get(any(),eq(PersistentDataType.STRING))).thenAnswer(c->strings.get(c.getArgument(0)));
        when(data.has(any(),eq(PersistentDataType.STRING))).thenAnswer(c->strings.containsKey(c.getArgument(0)));
        doAnswer(c->{((Consumer<Zombie>)c.getArgument(4)).accept(entity);
            assertEquals("zone:a",strings.get(MobKeys.ORIGIN));assertEquals("same",strings.get(MobKeys.SPAWNER));return entity;
        }).when(fixture.world).spawn(any(Location.class),eq(Zombie.class),eq(SpawnReason.CUSTOM),eq(false),any());
        var template=new MobTemplate("z","ZOMBIE","",0,0,0,0,0,Map.of(),List.of(),List.of(),List.of(),false,"RED",null,List.of(),false);
        var active=new MobFactory(mock(MobsPlatform.class)).spawn(template,new Location(fixture.world,1,64,1),host,1);
        var auxiliary=mock(Entity.class);var auxData=mock(PersistentDataContainer.class);when(auxiliary.getPersistentDataContainer()).thenReturn(auxData);
        OwnedEntities.mark(auxiliary,active);
        var projectile=mock(Projectile.class);var projectileData=mock(PersistentDataContainer.class);when(projectile.getPersistentDataContainer()).thenReturn(projectileData);
        assertTrue(OwnedEntities.markProjectile(projectile,entity));
        for(var target:List.of(auxData,projectileData)) {
            verify(target).set(MobKeys.SESSION,PersistentDataType.STRING,s.id().toString());
            verify(target).set(MobKeys.ORIGIN,PersistentDataType.STRING,"zone:a");verify(target).set(MobKeys.SPAWNER,PersistentDataType.STRING,"same");
        }
    }
    @Test void runtimeUsesInitialHealthScalingAndStableAreaForLateZoneSpawns() {
        var setup=new SessionRuntimeRegressionTest();setup.configure();
        var mob=new MobTemplate("zombie","ZOMBIE","",20,1,.2,0,1,Map.of(),List.of(),List.of(),List.of(),false,"RED",null,List.of(),false);
        when(setup.definitions.mobs()).thenReturn(Map.of("zombie",mob));
        try(var factories=mockConstruction(MobFactory.class)) {
            var runtime=new DungeonSessionRuntime(setup.plugin,mock(SessionManager.class),setup.definitions,setup.config,setup.storage);
            var s=new DungeonSession(fixture.definition(1,false),false,runtime);runtime.attach(s);
            // Preparation is tested separately; freeze a real initial group without Bukkit teleports.
            SessionRuntimeRegressionTest.field(s,"initialPlayers",3);
            var host=s.host(origin);var entity=mock(Mob.class);when(entity.getUniqueId()).thenReturn(UUID.randomUUID());
            var active=new ActiveMob(entity,mob,host);
            when(factories.constructed().getFirst().spawn(eq(mob),any(),same(host),eq(1.3))).thenReturn(active);
            assertSame(active,runtime.spawn(s,origin,"zombie",new Location(fixture.world,1,64,1)));
            verify(factories.constructed().getFirst()).spawn(eq(mob),any(),same(host),eq(1.3));
        } catch(Exception e) {throw new AssertionError(e);}
    }
    @Test void spawnRadiusAndLeashUseOriginEvenAfterMandatoryRoomAdvances() throws Exception {
        var setup=new SessionRuntimeRegressionTest();setup.configure();
        var def=fixture.definition(1,false);
        // A huge radius must always fall back inside the zone.
        var spawner=new SpawnerDef("same",fixture.point(1),1000,fixture.spawners(1).getFirst().waves());
        def=def.withOptionalZones(List.of(new OptionalZoneDef("a",fixture.region(0),List.of(spawner))));
        var s=fixture.start(def,fixture.player(100));
        var runtime=setup.runtime(mock(SessionManager.class),s);
        SessionRuntimeRegressionTest.field(s,"roomIndex",1);
        try(var bukkit=mockStatic(Bukkit.class)) {
            bukkit.when(()->Bukkit.getWorld("world")).thenReturn(fixture.world);
            for(int i=0;i<100;i++)assertTrue(DungeonSessionRuntime.contains(fixture.region(0),runtime.spawnLocation(s,origin)));
        }
        var active=fixture.services.spawn(s,origin,"zombie",new Location(fixture.world,21,64,1));s.track(active,origin);
        var clock=mock(dev.dasan.customdungeons.runtime.TickScheduler.class);when(clock.currentTick()).thenReturn(20L);
        SessionRuntimeRegressionTest.field(s,"scheduler",clock);
        var manager=mock(SessionManager.class);when(manager.blockJournal()).thenReturn(new SessionTempBlocks.Journal());var chunk=mock(Chunk.class);
        when(fixture.world.isChunkLoaded(anyInt(),anyInt())).thenReturn(true);
        when(fixture.world.getChunkAtAsync(anyInt(),anyInt())).thenReturn(CompletableFuture.completedFuture(chunk));
        // tick() runs the real leash, with the other presentation services scoped to mocks.
        runtime=setup.runtime(manager,s);
        SessionRuntimeRegressionTest.field(runtime,"sidebar",mock(SessionSidebar.class));
        SessionRuntimeRegressionTest.field(runtime,"ambience",mock(SessionAmbience.class));
        try(var bukkit=mockStatic(Bukkit.class)) {
            bukkit.when(()->Bukkit.getWorld("world")).thenReturn(fixture.world);
            runtime.tick(s);verify(active.entity(),never()).teleport(any(Location.class));
            when(clock.currentTick()).thenReturn(40L);runtime.tick(s);
            var destination=org.mockito.ArgumentCaptor.forClass(Location.class);verify(active.entity()).teleport(destination.capture());
            assertTrue(DungeonSessionRuntime.contains(fixture.region(0),destination.getValue()));
        }
    }
    @Test void zoneChunksArePreparedAsynchronouslyAndTicketsAreReleasedAndLateLoadsIgnored() {
        var manager=mock(SessionManager.class);when(manager.blockJournal()).thenReturn(new SessionTempBlocks.Journal());var chunk=mock(Chunk.class);when(chunk.getWorld()).thenReturn(fixture.world);
        when(fixture.world.getUID()).thenReturn(UUID.randomUUID());
        var load=new CompletableFuture<Chunk>();when(fixture.world.getChunkAtAsync(anyInt(),anyInt())).thenReturn(load);
        var remote=Region.of("world",new BlockPos(64,60,0),new BlockPos(79,70,15));
        var def=fixture.definition(1,false).withOptionalZones(List.of(new OptionalZoneDef("remote",remote,List.of())));
        try(var bukkit=mockStatic(Bukkit.class)) {
            bukkit.when(()->Bukkit.getWorld("world")).thenReturn(fixture.world);
            var chunks=new SessionChunks(manager);assertFalse(chunks.prepare(def));verify(fixture.world).getChunkAtAsync(4,0);
            verify(fixture.world,never()).getChunkAt(anyInt(),anyInt());
            when(fixture.world.isChunkLoaded(anyInt(),anyInt())).thenReturn(true);load.complete(chunk);
            assertTrue(chunks.prepare(def));verify(manager,atLeastOnce()).retainChunk(chunk);
            chunks.close();verify(manager,atLeastOnce()).releaseChunk(chunk);
            var pending=new CompletableFuture<Chunk>();when(fixture.world.getChunkAtAsync(anyInt(),anyInt())).thenReturn(pending);
            var late=new SessionChunks(manager);late.prepare(def);late.close();clearInvocations(manager);
            pending.complete(chunk);late.tick();verifyNoInteractions(manager);
        }
    }
    @Test void poisonWarningAndDamageStayInsideZoneAfterAdvancingAndFinishCleansTheEffect() throws Exception {
        var p=fixture.player(9);var ally=fixture.player(11);
        for(var player:List.of(p,ally)) {
            when(player.getWorld()).thenReturn(fixture.world);when(player.isValid()).thenReturn(true);
            when(player.getHealth()).thenReturn(20d);when(player.getHeight()).thenReturn(1.8);
        }
        when(fixture.world.isChunkLoaded(anyInt(),anyInt())).thenReturn(true);
        var s=fixture.start(fixture.definition(1,false),p,ally);fixture.ticks(s,10);
        var caster=fixture.zone(s,"a").getFirst();when(caster.entity().getWorld()).thenReturn(fixture.world);
        var platform=mock(MobsPlatform.class);when(platform.limits()).thenReturn(new dev.dasan.customdungeons.config.PluginConfig.PerformanceLimits(50,1,48));
        when(platform.messages()).thenReturn(SidebarDataTest.messages());
        new dev.dasan.customdungeons.ability.AbilityEngine(new dev.dasan.customdungeons.ability.AbilityRegistry(),platform);
        var service=new dev.dasan.customdungeons.ability.zone.ZoneService(platform);
        try(var bukkit=mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getCurrentTick).thenAnswer(c->(int)s.scheduler().currentTick());
            var ability=new dev.dasan.customdungeons.ability.zone.PoisonPoolsAbility();
            ability.execute(new dev.dasan.customdungeons.ability.AbilityContext(caster,List.of(p,ally),
                    new dev.dasan.customdungeons.ability.ParamValues(Map.of("count",1,"radius",5),ability.params()),caster.session(),null));
            assertEquals(1,service.activeCount());fixture.remove(s,s.roomMobs());s.tick();assertEquals(1,s.roomIndex());
            fixture.ticks(s,25);verify(p).damage(1,caster.entity());verify(ally,never()).damage(anyDouble(),any(Entity.class));
            var marks=new ArrayList<Location>();
            for(var call:mockingDetails(fixture.world).getInvocations())if(call.getMethod().getName().equals("spawnParticle")
                    && call.getArgument(0)==Particle.ENCHANT && call.getArgument(1) instanceof List<?>)
                marks.add(new Location(fixture.world,call.getArgument(3),call.getArgument(4),call.getArgument(5)));
            assertFalse(marks.isEmpty());assertTrue(marks.stream().allMatch(caster.session().area()::contains));
            var setup=new SessionRuntimeRegressionTest();setup.configure();var runtime=setup.runtime(mock(SessionManager.class),s);
            SessionRuntimeRegressionTest.field(runtime,"temp",mock(SessionTempBlocks.class));
            runtime.keys=mock(KeyService.class);runtime.finish(s);assertEquals(0,service.activeCount());
            for(var mob:s.mobs())verify(mob.entity()).remove();
            clearInvocations(p);fixture.ticks(s,30);verify(p,never()).damage(anyDouble(),any(Entity.class));
        } finally {service.close();}
    }
    @org.junit.jupiter.params.ParameterizedTest @org.junit.jupiter.params.provider.EnumSource(value=SessionState.class,names={"COMPLETED","FAILED","FREE"})
    void entitiesLoadedAfterFinishAreRemovedBySessionMarkEvenWithoutTheirOriginalChunk(SessionState state) {
        var manager=mock(SessionManager.class);var s=mock(DungeonSession.class);var machine=new SessionStateMachine();
        machine.openLobby();machine.start();if(state==SessionState.COMPLETED)machine.complete();else machine.fail();
        if(state==SessionState.FREE) {machine.beginReset();machine.finishReset();}
        when(s.state()).thenReturn(machine);when(manager.byId("previous")).thenReturn(Optional.of(s));
        var recovery=new RecoveryService(mock(dev.dasan.customdungeons.CustomDungeonsPlugin.class),mock(dev.dasan.customdungeons.storage.Storage.class),manager);
        var entity=mock(Entity.class);var data=mock(PersistentDataContainer.class);when(entity.getPersistentDataContainer()).thenReturn(data);
        when(data.get(MobKeys.SESSION,PersistentDataType.STRING)).thenReturn("previous");
        when(data.get(MobKeys.ORIGIN,PersistentDataType.STRING)).thenReturn("zone:a");
        var event=mock(org.bukkit.event.world.EntitiesLoadEvent.class);when(event.getEntities()).thenReturn(List.of(entity));
        recovery.entitiesLoaded(event);verify(entity).remove();
    }

}
