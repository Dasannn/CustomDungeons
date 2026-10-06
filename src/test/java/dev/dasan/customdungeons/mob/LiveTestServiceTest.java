package dev.dasan.customdungeons.mob;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LiveTestServiceTest {
    @org.junit.jupiter.api.io.TempDir java.nio.file.Path directory;

    @Test void oneContextOnlyTargetsItsAdminAndCleanupRestoresPreviousInvulnerability() {
        var f = fixture(true);
        assertTrue(f.test.isLiveTest());
        assertEquals(List.of(f.admin), f.test.players());
        f.services.tests.put(f.admin.getUniqueId(), f.test);
        f.test.scheduler().runLater(1, () -> fail("Ended test ran queued work"));
        f.test.close(); f.test.close();
        assertTrue(f.test.players().isEmpty()); assertTrue(f.test.mobs().isEmpty());
        assertTrue(f.services.tests.isEmpty());
        org.mockito.Mockito.verify(f.admin, org.mockito.Mockito.times(1)).setInvulnerable(true);
        ((LiveTestService.Clock)f.test.scheduler()).advance();
        f.services.journal.close();
    }

    @Test void temporaryBlocksWaitForJournalExpireAndRestoreOnStop() {
        var f = fixture(false);
        var block = org.mockito.Mockito.mock(org.bukkit.block.Block.class);
        var air = org.mockito.Mockito.mock(org.bukkit.block.data.BlockData.class);
        var web = org.mockito.Mockito.mock(org.bukkit.block.data.BlockData.class);
        org.mockito.Mockito.when(air.clone()).thenReturn(air);
        org.mockito.Mockito.when(air.getAsString()).thenReturn("minecraft:air");
        org.mockito.Mockito.when(block.getWorld()).thenReturn(f.world);
        org.mockito.Mockito.when(block.getBlockData()).thenReturn(air);
        org.mockito.Mockito.when(block.isEmpty()).thenReturn(true);
        assertTrue(f.test.tempBlocks().place(block, web, 2));
        org.mockito.Mockito.verify(block, org.mockito.Mockito.never()).setBlockData(web,false);
        assertFalse(f.test.tempBlocks().place(block, web, 2));
        f.services.journal.close();
        var clock=(LiveTestService.Clock)f.test.scheduler();
        clock.advance();
        org.mockito.Mockito.verify(block).setBlockData(web,false);
        clock.advance(); org.mockito.Mockito.verify(block,org.mockito.Mockito.never()).setBlockData(air,false);
        clock.advance(); org.mockito.Mockito.verify(block).setBlockData(air,false);
        assertTrue(f.services.reserved.isEmpty());
        assertTrue(f.test.tempBlocks().place(block,web,100)); f.services.journal.close(); clock.advance();
        f.test.close(); f.services.journal.close();
        org.mockito.Mockito.verify(block,org.mockito.Mockito.times(2)).setBlockData(air,false);
        assertTrue(f.services.reserved.isEmpty());
        assertEquals("", assertDoesNotThrow(() -> java.nio.file.Files.readString(directory.resolve("blocks"))));
    }

    @Test void closingBeforePlacementPreservesOtherPlayersChangesAndReleasesReservation() {
        var f=fixture(false);
        var block=org.mockito.Mockito.mock(org.bukkit.block.Block.class);
        var air=org.mockito.Mockito.mock(org.bukkit.block.data.BlockData.class);
        var web=org.mockito.Mockito.mock(org.bukkit.block.data.BlockData.class);
        org.mockito.Mockito.when(air.clone()).thenReturn(air);
        org.mockito.Mockito.when(air.getAsString()).thenReturn("minecraft:air");
        org.mockito.Mockito.when(block.getWorld()).thenReturn(f.world);
        org.mockito.Mockito.when(block.getBlockData()).thenReturn(air);
        org.mockito.Mockito.when(block.isEmpty()).thenReturn(true);
        assertTrue(f.test.tempBlocks().place(block,web,100));
        f.test.close(); f.services.journal.close();
        org.mockito.Mockito.verify(block,org.mockito.Mockito.never()).setBlockData(org.mockito.Mockito.any(),org.mockito.Mockito.anyBoolean());
        assertTrue(f.services.reserved.isEmpty());
    }

    @Test void nativeCombatAndPotionsCannotAffectBystanders() {
        var f=fixture(false);
        f.services.tests.put(f.admin.getUniqueId(),f.test);
        var source=org.mockito.Mockito.mock(org.bukkit.entity.Mob.class);
        var sourceData=org.mockito.Mockito.mock(org.bukkit.persistence.PersistentDataContainer.class);
        org.mockito.Mockito.when(source.getPersistentDataContainer()).thenReturn(sourceData);
        org.mockito.Mockito.when(sourceData.get(MobKeys.SESSION,org.bukkit.persistence.PersistentDataType.STRING)).thenReturn(f.test.id().toString());
        var outsider=org.mockito.Mockito.mock(org.bukkit.entity.Player.class);
        org.mockito.Mockito.when(outsider.getPersistentDataContainer()).thenReturn(org.mockito.Mockito.mock(org.bukkit.persistence.PersistentDataContainer.class));
        var damage=org.mockito.Mockito.mock(org.bukkit.event.entity.EntityDamageByEntityEvent.class);
        org.mockito.Mockito.when(damage.getEntity()).thenReturn(outsider);
        org.mockito.Mockito.when(damage.getDamager()).thenReturn(source);
        f.services.damage(damage);
        org.mockito.Mockito.verify(damage).setCancelled(true);
        var target=org.mockito.Mockito.mock(org.bukkit.event.entity.EntityTargetLivingEntityEvent.class);
        org.mockito.Mockito.when(target.getEntity()).thenReturn(source); org.mockito.Mockito.when(target.getTarget()).thenReturn(outsider);
        f.services.target(target); org.mockito.Mockito.verify(target).setCancelled(true);
        var potion=org.mockito.Mockito.mock(org.bukkit.entity.ThrownPotion.class);
        org.mockito.Mockito.when(potion.getPersistentDataContainer()).thenReturn(org.mockito.Mockito.mock(org.bukkit.persistence.PersistentDataContainer.class));
        org.mockito.Mockito.when(potion.getShooter()).thenReturn(source);
        var splash=org.mockito.Mockito.mock(org.bukkit.event.entity.PotionSplashEvent.class);
        org.mockito.Mockito.when(splash.getPotion()).thenReturn(potion);
        org.mockito.Mockito.when(splash.getAffectedEntities()).thenReturn(List.of(f.admin,outsider));
        f.services.splash(splash);
        org.mockito.Mockito.verify(splash).setIntensity(outsider,0);
        org.mockito.Mockito.verify(splash,org.mockito.Mockito.never()).setIntensity(org.mockito.Mockito.eq(f.admin),org.mockito.Mockito.anyDouble());
        f.test.close(); f.services.journal.close();
    }

    @Test void vanillaVexBelongsToEvokerCannotHurtOutsidersAndIsRemovedOnClose() {
        var f=fixture(false);
        f.services.tests.put(f.admin.getUniqueId(),f.test);
        var evoker=entity(f,org.bukkit.entity.Evoker.class,org.bukkit.entity.EntityType.EVOKER);
        f.services.executing=f.test;
        f.services.spawned(new org.bukkit.event.entity.CreatureSpawnEvent(evoker,org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.CUSTOM));
        f.services.executing=null;
        var vex=entity(f,org.bukkit.entity.Vex.class,org.bukkit.entity.EntityType.VEX);
        org.mockito.Mockito.when(vex.getOwner()).thenReturn(evoker);
        f.services.spawned(new org.bukkit.event.entity.CreatureSpawnEvent(vex,org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.SPELL));
        assertEquals(2,f.test.mobs().size());
        assertSame(f.test,f.services.owner(vex));
        var damage=org.mockito.Mockito.mock(org.bukkit.event.entity.EntityDamageByEntityEvent.class);
        var outsider=entity(f,org.bukkit.entity.Player.class,org.bukkit.entity.EntityType.PLAYER);
        org.mockito.Mockito.when(damage.getEntity()).thenReturn(outsider);
        org.mockito.Mockito.when(damage.getDamager()).thenReturn(vex);
        f.services.damage(damage);
        org.mockito.Mockito.verify(damage).setCancelled(true);
        f.test.close();
        org.mockito.Mockito.verify(vex).remove();
    }

    @Test void nearbySpellSummonsAreTrackedButUnrelatedNaturalSpawnsAreNot() {
        var f=fixture(false);
        f.services.tests.put(f.admin.getUniqueId(),f.test);
        var source=entity(f,org.bukkit.entity.Evoker.class,org.bukkit.entity.EntityType.EVOKER);
        f.services.executing=f.test;
        f.services.spawned(new org.bukkit.event.entity.CreatureSpawnEvent(source,org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.CUSTOM));
        f.services.executing=null;
        var summon=entity(f,org.bukkit.entity.Vex.class,org.bukkit.entity.EntityType.VEX);
        f.services.spawned(new org.bukkit.event.entity.CreatureSpawnEvent(summon,org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.SPELL));
        var natural=entity(f,org.bukkit.entity.Mob.class,org.bukkit.entity.EntityType.ZOMBIE);
        f.services.spawned(new org.bukkit.event.entity.CreatureSpawnEvent(natural,org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.NATURAL));
        assertEquals(2,f.test.mobs().size());
        assertNull(f.services.owner(natural));
        f.test.close();
        org.mockito.Mockito.verify(summon).remove();
        org.mockito.Mockito.verify(natural,org.mockito.Mockito.never()).remove();
    }

    @Test void genericSpawnEventsUseSummonReasonAndOnlyAttributeNearbyEntitiesInSameWorld() {
        var f=fixture(false);
        f.services.tests.put(f.admin.getUniqueId(),f.test);
        var source=entity(f,org.bukkit.entity.Mob.class,org.bukkit.entity.EntityType.ZOMBIE);
        f.services.executing=f.test;
        f.services.spawned(new org.bukkit.event.entity.EntitySpawnEvent(source));
        f.services.executing=null;
        var nearby=entity(f,org.bukkit.entity.Mob.class,org.bukkit.entity.EntityType.ZOMBIE);
        org.mockito.Mockito.when(nearby.getEntitySpawnReason()).thenReturn(org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.REINFORCEMENTS);
        f.services.spawned(new org.bukkit.event.entity.EntitySpawnEvent(nearby));
        assertSame(f.test,f.services.owner(nearby));
        var distant=entity(f,org.bukkit.entity.Mob.class,org.bukkit.entity.EntityType.ZOMBIE);
        org.mockito.Mockito.when(distant.getEntitySpawnReason()).thenReturn(org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.SPELL);
        org.mockito.Mockito.when(distant.getLocation()).thenReturn(new org.bukkit.Location(f.world,100,64,0));
        f.services.spawned(new org.bukkit.event.entity.EntitySpawnEvent(distant));
        assertNull(f.services.owner(distant));
        org.mockito.Mockito.when(distant.getLocation()).thenReturn(new org.bukkit.Location(f.otherWorld,1,64,0));
        f.services.spawned(new org.bukkit.event.entity.EntitySpawnEvent(distant));
        assertNull(f.services.owner(distant));
        f.test.close();
        org.mockito.Mockito.verify(nearby).remove();
        org.mockito.Mockito.verify(distant,org.mockito.Mockito.never()).remove();
    }

    @Test void nativeSpawnsAndTransformationsCannotExceedFiftyMobs() {
        var f=fixture(false);
        f.services.tests.put(f.admin.getUniqueId(),f.test);
        f.services.executing=f.test;
        org.bukkit.entity.Mob source=null;
        for(int i=0;i<50;i++) {
            source=entity(f,org.bukkit.entity.Mob.class,org.bukkit.entity.EntityType.ZOMBIE);
            var event=new org.bukkit.event.entity.CreatureSpawnEvent(source,org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.CUSTOM);
            f.services.spawned(event);
            assertFalse(event.isCancelled());
            // Repeated notifications must not count an existing entity twice.
            f.services.spawned(event);
        }
        var extra=entity(f,org.bukkit.entity.Mob.class,org.bukkit.entity.EntityType.ZOMBIE);
        var overflow=new org.bukkit.event.entity.CreatureSpawnEvent(extra,org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.CUSTOM);
        f.services.spawned(overflow);
        assertTrue(overflow.isCancelled());
        org.mockito.Mockito.verify(extra).remove();
        // Projectiles still belong to cleanup and do not consume a mob slot.
        var projectile=entity(f,org.bukkit.entity.Projectile.class,org.bukkit.entity.EntityType.ARROW);
        var shot=new org.bukkit.event.entity.EntitySpawnEvent(projectile);
        f.services.spawned(shot);
        assertFalse(shot.isCancelled());
        f.services.executing=null;
        var transformed=entity(f,org.bukkit.entity.Mob.class,org.bukkit.entity.EntityType.DROWNED);
        var transform=new org.bukkit.event.entity.EntityTransformEvent(source,List.of(transformed),org.bukkit.event.entity.EntityTransformEvent.TransformReason.DROWNED);
        f.services.transform(transform);
        assertEquals(50,f.test.mobs().size());
        org.mockito.Mockito.verify(transformed).remove();
        f.test.close();
        org.mockito.Mockito.verify(projectile).remove();
    }

    private <T extends org.bukkit.entity.Entity> T entity(Fixture f,Class<T> type,org.bukkit.entity.EntityType kind) {
        T entity=org.mockito.Mockito.mock(type);
        org.mockito.Mockito.when(entity.getUniqueId()).thenReturn(java.util.UUID.randomUUID());
        org.mockito.Mockito.when(entity.getPersistentDataContainer()).thenReturn(org.mockito.Mockito.mock(org.bukkit.persistence.PersistentDataContainer.class));
        org.mockito.Mockito.when(entity.getType()).thenReturn(kind);
        org.mockito.Mockito.when(entity.getLocation()).thenReturn(new org.bukkit.Location(f.world,1,64,0));
        org.mockito.Mockito.when(entity.isValid()).thenReturn(true);
        return entity;
    }

    private record Fixture(LiveTestService test, LiveTestService.Manager services, org.bukkit.entity.Player admin, org.bukkit.World world, org.bukkit.World otherWorld) {}
    private Fixture fixture(boolean invulnerable) {
        var plugin=org.mockito.Mockito.mock(dev.dasan.customdungeons.CustomDungeonsPlugin.class);
        org.mockito.Mockito.when(plugin.abilityRegistry()).thenReturn(new dev.dasan.customdungeons.ability.AbilityRegistry());
        var player=org.mockito.Mockito.mock(org.bukkit.entity.Player.class);
        var world=org.mockito.Mockito.mock(org.bukkit.World.class);
        org.mockito.Mockito.when(world.getUID()).thenReturn(java.util.UUID.randomUUID());
        org.mockito.Mockito.when(player.getUniqueId()).thenReturn(java.util.UUID.randomUUID());
        org.mockito.Mockito.when(player.getLocation()).thenReturn(new org.bukkit.Location(world,0,64,0));
        org.mockito.Mockito.when(player.isInvulnerable()).thenReturn(invulnerable);
        var store=org.mockito.Mockito.mock(dev.dasan.customdungeons.config.DefinitionStore.class);
        org.mockito.Mockito.when(store.mobs()).thenReturn(java.util.Map.of());
        var config=new dev.dasan.customdungeons.config.PluginConfig("","es",null,"world",false,null,
                new dev.dasan.customdungeons.config.PluginConfig.PerformanceLimits(50,1,48),java.util.Set.of(),List.of(),null,null,300,java.util.Map.of());
        var services=new LiveTestService.Manager(plugin,config,store,new MobFactory(config),directory.resolve("blocks"));
        return new Fixture(new LiveTestService(services,player),services,player,world,org.mockito.Mockito.mock(org.bukkit.World.class));
    }

    @Test void spawnIsFourBlocksAwayEvenWhenLookingStraightDown() {
        var at=new org.bukkit.Location(null,10,64,20,90,90);
        var spawned=LiveTestService.spawnLocation(at);
        assertEquals(6,spawned.getX(),1e-9); assertEquals(20,spawned.getZ(),1e-9);
        assertEquals(64,spawned.getY()); assertEquals(10,at.getX());
    }
    @Test void radiusAndTimeoutAreInclusiveAndWorldChangeStops() {
        assertFalse(LiveTestService.shouldStop(true, true, 48 * 48, 5999, 300));
        assertTrue(LiveTestService.shouldStop(true, true, 48 * 48 + .01, 1, 300));
        assertTrue(LiveTestService.shouldStop(true, true, 0, 6000, 300));
        assertTrue(LiveTestService.shouldStop(true, false, 0, 1, 300));
        assertTrue(LiveTestService.shouldStop(false, true, 0, 1, 300));
    }
    @Test void queuedWorkRunsOnceInOrderAndNestedWorkWaitsForNextTick() {
        var clock = new LiveTestService.Clock();
        var calls = new ArrayList<Integer>();
        clock.runLater(2, () -> calls.add(2));
        clock.runLater(1, () -> { calls.add(1); clock.runLater(0, () -> calls.add(3)); });
        clock.advance(); assertEquals(List.of(1), calls);
        clock.advance(); assertEquals(List.of(1, 2, 3), calls);
        clock.advance(); assertEquals(3, calls.size());
        clock.runLater(1, () -> calls.add(4)); clock.clear(); clock.advance();
        assertEquals(3, calls.size());
    }
}
