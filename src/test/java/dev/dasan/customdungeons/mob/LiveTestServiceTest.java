package dev.dasan.customdungeons.mob;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LiveTestServiceTest {
    static { dev.dasan.customdungeons.ability.impl.PaperApiTestBootstrap.initialize(); }
    @org.junit.jupiter.api.io.TempDir java.nio.file.Path directory;

    @Test void invalidLegacyTemplateReportsEveryErrorBeforeStarting() throws Exception {
        var f=fixture(false);
        var messages=org.mockito.Mockito.mock(dev.dasan.customdungeons.text.Messages.class);
        org.mockito.Mockito.when(f.services.platform.messages()).thenReturn(messages);
        org.mockito.Mockito.when(f.admin.hasPermission("customdungeons.admin.test")).thenReturn(true);
        var field=LiveTestService.class.getDeclaredField("manager"); field.setAccessible(true);
        Object previous=field.get(null); field.set(null,f.services);
        try {
            var item=org.mockito.Mockito.mock(org.bukkit.inventory.ItemStack.class);
            var meta=org.mockito.Mockito.mock(org.bukkit.inventory.meta.ItemMeta.class);
            var pdc=org.mockito.Mockito.mock(org.bukkit.persistence.PersistentDataContainer.class);
            org.mockito.Mockito.when(item.clone()).thenReturn(item);
            org.mockito.Mockito.when(item.hasItemMeta()).thenReturn(true);
            org.mockito.Mockito.when(item.getItemMeta()).thenReturn(meta);
            org.mockito.Mockito.when(meta.getPersistentDataContainer()).thenReturn(pdc);
            org.mockito.Mockito.when(pdc.has(MobKeys.TOOL)).thenReturn(true);
            var equipment=java.util.Map.of(org.bukkit.inventory.EquipmentSlot.HAND,new dev.dasan.customdungeons.model.EquipmentDef(item,0),
                    org.bukkit.inventory.EquipmentSlot.OFF_HAND,new dev.dasan.customdungeons.model.EquipmentDef(item,0));
            var template=new dev.dasan.customdungeons.model.MobTemplate("warden","WARDEN","",0,0,1024.01,0,16.01,equipment,
                    List.of(),List.of(),List.of(),false,"RED",null,List.of(),false);
            org.mockito.Mockito.when(messages.get(org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.any(net.kyori.adventure.text.minimessage.tag.resolver.TagResolver[].class)))
                    .thenReturn(net.kyori.adventure.text.Component.empty());
            assertFalse(LiveTestService.start(f.admin,template));
            assertTrue(f.services.tests.isEmpty());
            org.mockito.Mockito.verify(messages).send(f.admin,"livetest.invalid");
            org.mockito.Mockito.verify(messages,org.mockito.Mockito.times(4)).send(org.mockito.ArgumentMatchers.eq(f.admin),
                    org.mockito.ArgumentMatchers.eq("livetest.validation-error"),org.mockito.ArgumentMatchers.any(net.kyori.adventure.text.minimessage.tag.resolver.TagResolver.class));
            org.mockito.Mockito.verify(f.admin,org.mockito.Mockito.never()).setInvulnerable(org.mockito.ArgumentMatchers.anyBoolean());
        } finally { field.set(null,previous); f.test.close(); f.services.journal.close(); }
    }

    @Test void lackOfSpaceWarnsButStillStartsAndExplicitStopCleansUp() throws Exception {
        dev.dasan.customdungeons.ability.impl.PaperApiTestBootstrap.initialize();
        var f=fixture(false);
        var messages=org.mockito.Mockito.mock(dev.dasan.customdungeons.text.Messages.class);
        org.mockito.Mockito.when(f.services.platform.messages()).thenReturn(messages);
        org.mockito.Mockito.when(f.admin.hasPermission("customdungeons.admin.test")).thenReturn(true);
        var principal=entity(f,org.bukkit.entity.Zombie.class,org.bukkit.entity.EntityType.ZOMBIE);
        var unspawned=org.mockito.Mockito.mock(org.bukkit.entity.Zombie.class);
        org.mockito.Mockito.when(unspawned.getWidth()).thenReturn(.6);
        org.mockito.Mockito.when(unspawned.getHeight()).thenReturn(1.95);
        org.mockito.Mockito.when(f.world.createEntity(org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.eq(org.bukkit.entity.Zombie.class))).thenReturn(unspawned);
        org.mockito.Mockito.when(f.world.getMinHeight()).thenReturn(0);
        org.mockito.Mockito.when(f.world.getMaxHeight()).thenReturn(256);
        // No loaded supporting/clear blocks: this used to reject the live test.
        org.mockito.Mockito.when(f.world.isChunkLoaded(org.mockito.ArgumentMatchers.anyInt(),org.mockito.ArgumentMatchers.anyInt())).thenReturn(false);
        var spawnedAt=new java.util.concurrent.atomic.AtomicReference<org.bukkit.Location>();
        org.mockito.Mockito.doAnswer(call -> {
            spawnedAt.set(call.getArgument(0));
            java.util.function.Consumer<org.bukkit.entity.Zombie> configure=call.getArgument(4);
            configure.accept(principal); return principal;
        }).when(f.world).spawn(org.mockito.ArgumentMatchers.any(org.bukkit.Location.class),org.mockito.ArgumentMatchers.eq(org.bukkit.entity.Zombie.class),
                org.mockito.ArgumentMatchers.eq(org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.CUSTOM),org.mockito.ArgumentMatchers.eq(false),org.mockito.ArgumentMatchers.any());
        var scheduler=org.mockito.Mockito.mock(org.bukkit.scheduler.BukkitScheduler.class);
        var ticker=org.mockito.Mockito.mock(org.bukkit.scheduler.BukkitTask.class);
        org.mockito.Mockito.when(scheduler.runTaskTimer(org.mockito.ArgumentMatchers.eq(f.services.plugin),org.mockito.ArgumentMatchers.any(Runnable.class),org.mockito.ArgumentMatchers.eq(1L),org.mockito.ArgumentMatchers.eq(1L))).thenReturn(ticker);
        var field=LiveTestService.class.getDeclaredField("manager"); field.setAccessible(true);
        Object previous=field.get(null); field.set(null,f.services);
        try(var bukkit=org.mockito.Mockito.mockStatic(org.bukkit.Bukkit.class)) {
            bukkit.when(org.bukkit.Bukkit::getScheduler).thenReturn(scheduler);
            var template=new dev.dasan.customdungeons.model.MobTemplate("zombie","ZOMBIE","",0,0,0,0,10,
                    java.util.Map.of(),List.of(),List.of(),List.of(),false,"RED",null,List.of(),false);
            assertTrue(LiveTestService.start(f.admin,template));
            assertTrue(LiveTestService.active(f.admin));
            assertEquals(f.admin.getLocation(),spawnedAt.get());
            org.mockito.Mockito.verify(messages).send(f.admin,"livetest.space-warning");
            org.mockito.Mockito.verify(messages).send(f.admin,"livetest.started");
            org.mockito.Mockito.verify(principal,org.mockito.Mockito.never()).setTarget(org.mockito.ArgumentMatchers.any());
            LiveTestService.stop(f.admin);
            assertFalse(LiveTestService.active(f.admin));
            org.mockito.Mockito.verify(principal).remove();
            org.mockito.Mockito.verify(ticker).cancel();
        } finally { LiveTestService.stop(f.admin); field.set(null,previous); f.test.close(); f.services.journal.close(); }
    }

    @Test void oneContextIncludesEligibleAdminAndCleanupRestoresPreviousInvulnerability() {
        var f = fixture(true);
        assertNull(f.test.area());
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
        org.mockito.Mockito.when(web.clone()).thenReturn(web);
        org.mockito.Mockito.when(web.getAsString()).thenReturn("minecraft:cobweb");
        org.mockito.Mockito.when(block.getWorld()).thenReturn(f.world);
        var state=new java.util.concurrent.atomic.AtomicReference<>(air);
        org.mockito.Mockito.when(block.getBlockData()).thenAnswer(i->state.get());
        org.mockito.Mockito.doAnswer(i->{state.set(i.getArgument(0));return null;}).when(block).setBlockData(org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.eq(false));
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
        org.mockito.Mockito.when(web.clone()).thenReturn(web);
        org.mockito.Mockito.when(web.getAsString()).thenReturn("minecraft:cobweb");
        org.mockito.Mockito.when(block.getWorld()).thenReturn(f.world);
        org.mockito.Mockito.when(block.getBlockData()).thenReturn(air);
        org.mockito.Mockito.when(block.isEmpty()).thenReturn(true);
        assertTrue(f.test.tempBlocks().place(block,web,100));
        f.test.close(); f.services.journal.close();
        org.mockito.Mockito.verify(block,org.mockito.Mockito.never()).setBlockData(org.mockito.Mockito.any(),org.mockito.Mockito.anyBoolean());
        assertTrue(f.services.reserved.isEmpty());
    }

    @org.junit.jupiter.params.ParameterizedTest @org.junit.jupiter.params.provider.ValueSource(booleans={true,false})
    void liveCleanupNeverOverwritesAnotherActorsBlock(boolean expires) {
        var f=fixture(false);var block=org.mockito.Mockito.mock(org.bukkit.block.Block.class);
        org.bukkit.block.data.BlockData air=org.mockito.Mockito.mock(org.bukkit.block.data.BlockData.class),pillar=org.mockito.Mockito.mock(org.bukkit.block.data.BlockData.class),foreign=org.mockito.Mockito.mock(org.bukkit.block.data.BlockData.class);
        org.mockito.Mockito.when(air.clone()).thenReturn(air);org.mockito.Mockito.when(pillar.clone()).thenReturn(pillar);
        org.mockito.Mockito.when(air.getAsString()).thenReturn("minecraft:air");org.mockito.Mockito.when(pillar.getAsString()).thenReturn("minecraft:stone_bricks");org.mockito.Mockito.when(foreign.getAsString()).thenReturn("minecraft:diamond_block");
        var state=new java.util.concurrent.atomic.AtomicReference<>(air);
        org.mockito.Mockito.when(block.getWorld()).thenReturn(f.world);org.mockito.Mockito.when(block.isEmpty()).thenReturn(true);org.mockito.Mockito.when(block.getBlockData()).thenAnswer(i->state.get());
        org.mockito.Mockito.doAnswer(i->{state.set(i.getArgument(0));return null;}).when(block).setBlockData(org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.eq(false));
        assertTrue(f.test.tempBlocks().place(block,pillar,1));f.services.journal.close();var clock=(LiveTestService.Clock)f.test.scheduler();clock.advance();assertSame(pillar,state.get());
        state.set(foreign);if(expires)clock.advance();else f.test.close();
        assertSame(foreign,state.get());f.test.close();f.services.journal.close();assertTrue(f.services.reserved.isEmpty());
    }

    @Test void nativeCombatAndAbilityPotionsCanAffectNearbyParticipants() {
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
        participate(f,outsider);
        f.services.damage(damage);
        org.mockito.Mockito.verify(damage,org.mockito.Mockito.never()).setCancelled(true);
        var target=org.mockito.Mockito.mock(org.bukkit.event.entity.EntityTargetLivingEntityEvent.class);
        org.mockito.Mockito.when(target.getEntity()).thenReturn(source); org.mockito.Mockito.when(target.getTarget()).thenReturn(outsider);
        f.services.target(target); org.mockito.Mockito.verify(target,org.mockito.Mockito.never()).setCancelled(true);
        var potion=org.mockito.Mockito.mock(org.bukkit.entity.ThrownPotion.class);
        org.mockito.Mockito.when(potion.getPersistentDataContainer()).thenReturn(org.mockito.Mockito.mock(org.bukkit.persistence.PersistentDataContainer.class));
        org.mockito.Mockito.when(potion.getShooter()).thenReturn(source);
        var splash=org.mockito.Mockito.mock(org.bukkit.event.entity.PotionSplashEvent.class);
        org.mockito.Mockito.when(splash.getPotion()).thenReturn(potion);
        org.mockito.Mockito.when(splash.getAffectedEntities()).thenReturn(List.of(f.admin,outsider));
        f.services.splash(splash);
        org.mockito.Mockito.verify(splash,org.mockito.Mockito.never()).setIntensity(org.mockito.Mockito.eq(outsider),org.mockito.Mockito.anyDouble());
        org.mockito.Mockito.verify(splash,org.mockito.Mockito.never()).setIntensity(org.mockito.Mockito.eq(f.admin),org.mockito.Mockito.anyDouble());
        f.test.close(); f.services.journal.close();
    }

    @Test void vanillaVexBelongsToEvokerCanAttackPlayersAndIsRemovedOnClose() {
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
        participate(f,outsider);
        f.services.damage(damage);
        org.mockito.Mockito.verify(damage,org.mockito.Mockito.never()).setCancelled(true);
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

    @Test void protectedAdminStillReceivesOnHitEffectsAndRepeatedComboStepsFromMeleeAndProjectile() {
        for(boolean projectile:List.of(false,true)) {
            var f=fixture(false);
            f.services.tests.put(f.admin.getUniqueId(),f.test);
            org.mockito.Mockito.when(f.admin.getPersistentDataContainer()).thenReturn(org.mockito.Mockito.mock(org.bukkit.persistence.PersistentDataContainer.class));
            org.mockito.Mockito.when(f.admin.isOnline()).thenReturn(true);
            org.mockito.Mockito.when(f.admin.isValid()).thenReturn(true);
            org.mockito.Mockito.when(f.admin.getGameMode()).thenReturn(org.bukkit.GameMode.SURVIVAL);
            org.mockito.Mockito.when(f.admin.getWorld()).thenReturn(f.world);
            f.test.setInvulnerable(true);
            var calls=new java.util.ArrayList<dev.dasan.customdungeons.ability.AbilityContext>();
            var ability=new dev.dasan.customdungeons.ability.Ability() {
                public String id() { return "test_hit"; }
                public org.bukkit.Material icon() { return org.bukkit.Material.BLAZE_POWDER; }
                public List<dev.dasan.customdungeons.ability.ParamSpec> params() { return List.of(); }
                public void execute(dev.dasan.customdungeons.ability.AbilityContext ctx) {
                    assertFalse(((org.bukkit.event.entity.EntityDamageEvent)ctx.cause()).isCancelled());
                    assertEquals(0,((org.bukkit.event.entity.EntityDamageEvent)ctx.cause()).getDamage());
                    calls.add(ctx); ctx.targets().forEach(target -> target.setFreezeTicks(200));
                }
            };
            f.services.platform.abilityRegistry().register(ability);
            var source=entity(f,org.bukkit.entity.Mob.class,org.bukkit.entity.EntityType.ZOMBIE);
            org.mockito.Mockito.when(source.getWorld()).thenReturn(f.world);
            f.services.executing=f.test; f.services.spawned(new org.bukkit.event.entity.EntitySpawnEvent(source)); f.services.executing=null;
            var mob=f.test.mobs().iterator().next();
            mob.abilities().add(new dev.dasan.customdungeons.model.AbilityInstance(ability.id(),dev.dasan.customdungeons.model.Trigger.ON_HIT,0,
                dev.dasan.customdungeons.model.TargetMode.NEAREST,16,0,1,0,java.util.Map.of()));
            mob.combos().add(new dev.dasan.customdungeons.model.ComboDef("hit_combo",dev.dasan.customdungeons.model.Trigger.ON_HIT,0,
                dev.dasan.customdungeons.model.TargetMode.NEAREST,16,0,List.of(
                    new dev.dasan.customdungeons.model.ComboStep(ability.id(),java.util.Map.of(),0),
                    new dev.dasan.customdungeons.model.ComboStep(ability.id(),java.util.Map.of(),2))));
            org.bukkit.entity.Entity damager=source;
            if(projectile) {
                var shot=entity(f,org.bukkit.entity.Projectile.class,org.bukkit.entity.EntityType.ARROW);
                org.mockito.Mockito.when(shot.getShooter()).thenReturn(source); damager=shot;
            }
            var damage=org.mockito.Mockito.mock(org.bukkit.event.entity.EntityDamageByEntityEvent.class);
            org.mockito.Mockito.when(damage.getEntity()).thenReturn(f.admin);
            org.mockito.Mockito.when(damage.getDamager()).thenReturn(damager);
            var cancelled=new java.util.concurrent.atomic.AtomicBoolean();
            org.mockito.Mockito.doAnswer(call -> { cancelled.set(call.getArgument(0)); return null; }).when(damage).setCancelled(org.mockito.ArgumentMatchers.anyBoolean());
            org.mockito.Mockito.when(damage.isCancelled()).thenAnswer(call -> cancelled.get());
            var amount=new java.util.concurrent.atomic.AtomicReference<Double>(6.0);
            org.mockito.Mockito.when(damage.getDamage()).thenAnswer(call -> amount.get());
            org.mockito.Mockito.doAnswer(call -> { amount.set(call.getArgument(0)); return null; }).when(damage).setDamage(org.mockito.ArgumentMatchers.anyDouble());
            try {
                f.services.damage(damage);
                assertEquals(2,calls.size()); assertEquals(0,damage.getDamage()); assertFalse(damage.isCancelled());
                ((LiveTestService.Clock)f.test.scheduler()).advance(); ((LiveTestService.Clock)f.test.scheduler()).advance();
                assertEquals(3,calls.size());
                for(var ctx:calls) { assertEquals(List.of(f.admin),ctx.targets()); assertSame(damage,ctx.cause()); }
                org.mockito.Mockito.verify(f.admin,org.mockito.Mockito.times(3)).setFreezeTicks(200);
                assertEquals(0,damage.getDamage()); assertFalse(damage.isCancelled());
            } finally {
                org.mockito.Mockito.when(f.admin.isOnline()).thenReturn(false); f.test.close(); f.services.journal.close();
            }
        }
    }

    @Test void explicitInvulnerabilityCancelsEveryDamageCauseIncludingCreativeAttacks() {
        var f=fixture(false); f.services.tests.put(f.admin.getUniqueId(),f.test);
        f.test.setInvulnerable(true);
        var event=org.mockito.Mockito.mock(org.bukkit.event.entity.EntityDamageEvent.class);
        org.mockito.Mockito.when(event.getEntity()).thenReturn(f.admin);
        f.services.damage(event); org.mockito.Mockito.verify(event).setCancelled(true);
        f.test.setInvulnerable(false);
        var unprotected=org.mockito.Mockito.mock(org.bukkit.event.entity.EntityDamageEvent.class);
        org.mockito.Mockito.when(unprotected.getEntity()).thenReturn(f.admin);
        org.mockito.Mockito.when(f.admin.getPersistentDataContainer()).thenReturn(org.mockito.Mockito.mock(org.bukkit.persistence.PersistentDataContainer.class));
        f.services.damage(unprotected); org.mockito.Mockito.verify(unprotected,org.mockito.Mockito.never()).setCancelled(true);
        f.test.close();
    }
    @Test void missingPrincipalEndsTestAndRemovesMinionsEvenIfAdminIsOnline() {
        var f=fixture(false); f.services.tests.put(f.admin.getUniqueId(),f.test);
        org.mockito.Mockito.when(f.admin.isOnline()).thenReturn(true);
        org.mockito.Mockito.when(f.admin.hasPermission("customdungeons.admin.test")).thenReturn(true);
        org.mockito.Mockito.when(f.admin.getWorld()).thenReturn(f.world);
        org.mockito.Mockito.when(f.services.platform.messages()).thenReturn(org.mockito.Mockito.mock(dev.dasan.customdungeons.text.Messages.class));
        var principal=entity(f,org.bukkit.entity.Mob.class,org.bukkit.entity.EntityType.WARDEN);
        f.test.principal=principal;
        var minion=entity(f,org.bukkit.entity.Mob.class,org.bukkit.entity.EntityType.ZOMBIE);
        f.services.executing=f.test; f.services.spawned(new org.bukkit.event.entity.EntitySpawnEvent(minion)); f.services.executing=null;
        org.mockito.Mockito.when(principal.isValid()).thenReturn(false);
        f.test.tick(); assertTrue(f.services.tests.isEmpty());
        org.mockito.Mockito.verify(minion).remove(); f.services.journal.close();
    }
    @Test void removalEventAndDisconnectCloseImmediately() {
        var f=fixture(false); f.services.tests.put(f.admin.getUniqueId(),f.test);
        var principal=entity(f,org.bukkit.entity.Mob.class,org.bukkit.entity.EntityType.WARDEN);
        f.test.principal=principal; f.services.executing=f.test;
        f.services.spawned(new org.bukkit.event.entity.EntitySpawnEvent(principal)); f.services.executing=null;
        f.services.removed(new com.destroystokyo.paper.event.entity.EntityRemoveFromWorldEvent(principal,f.world));
        assertTrue(f.services.tests.isEmpty()); f.services.journal.close();
        var second=fixture(false); second.services.tests.put(second.admin.getUniqueId(),second.test);
        second.services.quit(new org.bukkit.event.player.PlayerQuitEvent(second.admin,net.kyori.adventure.text.Component.empty(),org.bukkit.event.player.PlayerQuitEvent.QuitReason.DISCONNECTED));
        assertTrue(second.services.tests.isEmpty()); second.services.journal.close();
    }

    @Test void liveWardenAngerIsVanillaWhileDungeonBehaviorIsPreserved() {
        var f=fixture(false); f.services.tests.put(f.admin.getUniqueId(),f.test);
        var bridge=new dev.dasan.customdungeons.session.WardenSessionListener((dev.dasan.customdungeons.CustomDungeonsPlugin)f.services.plugin,e -> f.services.owner(e)!=null);
        var warden=entity(f,org.bukkit.entity.Warden.class,org.bukkit.entity.EntityType.WARDEN);
        f.services.executing=f.test; f.services.spawned(new org.bukkit.event.entity.EntitySpawnEvent(warden)); f.services.executing=null;
        var event=org.mockito.Mockito.mock(io.papermc.paper.event.entity.WardenAngerChangeEvent.class);
        org.mockito.Mockito.when(event.getEntity()).thenReturn(warden); org.mockito.Mockito.when(event.getTarget()).thenReturn(f.admin);
        bridge.wardenAnger(event); org.mockito.Mockito.verify(event,org.mockito.Mockito.never()).setNewAnger(org.mockito.ArgumentMatchers.anyInt());
        var other=entity(f,org.bukkit.entity.Player.class,org.bukkit.entity.EntityType.PLAYER);
        var unrelated=org.mockito.Mockito.mock(io.papermc.paper.event.entity.WardenAngerChangeEvent.class);
        org.mockito.Mockito.when(unrelated.getEntity()).thenReturn(warden); org.mockito.Mockito.when(unrelated.getTarget()).thenReturn(other);
        bridge.wardenAnger(unrelated); org.mockito.Mockito.verify(unrelated,org.mockito.Mockito.never()).setNewAnger(org.mockito.Mockito.anyInt());
        f.test.close(); f.services.journal.close();
        var manager=org.mockito.Mockito.mock(dev.dasan.customdungeons.session.SessionManager.class);
        var session=org.mockito.Mockito.mock(dev.dasan.customdungeons.session.DungeonSession.class);
        org.mockito.Mockito.when(((dev.dasan.customdungeons.CustomDungeonsPlugin)f.services.plugin).sessionManager()).thenReturn(manager);
        var id=java.util.UUID.randomUUID(); org.mockito.Mockito.when(session.id()).thenReturn(id);
        var adminId=f.admin.getUniqueId();
        org.mockito.Mockito.when(session.survivors()).thenReturn(java.util.Set.of(adminId));
        org.mockito.Mockito.when(manager.sessionOf(f.admin.getUniqueId())).thenReturn(java.util.Optional.of(session));
        org.mockito.Mockito.when(warden.getPersistentDataContainer().get(MobKeys.SESSION,org.bukkit.persistence.PersistentDataType.STRING)).thenReturn(id.toString());
        var inSession=org.mockito.Mockito.mock(io.papermc.paper.event.entity.WardenAngerChangeEvent.class);
        org.mockito.Mockito.when(inSession.getEntity()).thenReturn(warden); org.mockito.Mockito.when(inSession.getTarget()).thenReturn(f.admin);
        bridge.wardenAnger(inSession); org.mockito.Mockito.verify(inSession).setNewAnger(150);
    }
    @Test void liveWardenSpawnAndTicksNeverForceTargetOrAnger() {
        dev.dasan.customdungeons.ability.impl.PaperApiTestBootstrap.initialize();
        var f=fixture(false);
        var warden=entity(f,org.bukkit.entity.Warden.class,org.bukkit.entity.EntityType.WARDEN);
        org.mockito.Mockito.when(f.world.spawn(org.mockito.ArgumentMatchers.any(org.bukkit.Location.class),
                org.mockito.ArgumentMatchers.eq(org.bukkit.entity.Warden.class),
                org.mockito.ArgumentMatchers.eq(org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.CUSTOM),
                org.mockito.ArgumentMatchers.eq(false),org.mockito.ArgumentMatchers.any())).thenReturn(warden);
        var template=new dev.dasan.customdungeons.model.MobTemplate("warden","WARDEN","",0,0,0,0,0,
                java.util.Map.of(),List.of(),List.of(),List.of(),false,"RED",null,List.of(),false);
        org.mockito.Mockito.when(f.services.platform.templates()).thenReturn(java.util.Map.of("warden",template));
        f.test.close();
        var live=new LiveTestService(f.services,f.admin);
        f.services.tests.put(f.admin.getUniqueId(),live);
        org.mockito.Mockito.when(f.admin.isOnline()).thenReturn(true);
        org.mockito.Mockito.when(f.admin.hasPermission("customdungeons.admin.test")).thenReturn(true);
        org.mockito.Mockito.when(f.admin.getWorld()).thenReturn(f.world);
        try {
            assertNotNull(live.spawnMinion("warden",f.admin.getLocation(),null));
            live.principal=warden;
            for(int i=0;i<40;i++) live.tick();
            var target=org.mockito.Mockito.mock(org.bukkit.event.entity.EntityTargetLivingEntityEvent.class);
            org.mockito.Mockito.when(target.getEntity()).thenReturn(warden);
            org.mockito.Mockito.when(target.getTarget()).thenReturn(f.admin);
            f.services.target(target);
            org.mockito.Mockito.verify(warden,org.mockito.Mockito.never()).setTarget(org.mockito.ArgumentMatchers.any());
            org.mockito.Mockito.verify(warden,org.mockito.Mockito.never()).setAnger(org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.anyInt());
        } finally {
            org.mockito.Mockito.when(f.admin.isOnline()).thenReturn(false);
            live.close(); f.services.journal.close();
        }
    }

    @Test void dungeonWardensUseOneRefreshInExistingTickerAndStopOnFinish() {
        var f=fixture(false);
        var bridge=new dev.dasan.customdungeons.session.WardenSessionListener((dev.dasan.customdungeons.CustomDungeonsPlugin)f.services.plugin,e -> f.services.owner(e)!=null);
        var manager=org.mockito.Mockito.mock(dev.dasan.customdungeons.session.SessionManager.class);
        org.mockito.Mockito.when(((dev.dasan.customdungeons.CustomDungeonsPlugin)f.services.plugin).sessionManager()).thenReturn(manager);
        var session=org.mockito.Mockito.mock(dev.dasan.customdungeons.session.DungeonSession.class);
        org.mockito.Mockito.when(session.id()).thenReturn(java.util.UUID.randomUUID());
        org.mockito.Mockito.when(session.players()).thenReturn(List.of(f.admin));
        var clock=new LiveTestService.Clock(); org.mockito.Mockito.when(session.scheduler()).thenReturn(clock);
        var warden=entity(f,org.bukkit.entity.Warden.class,org.bukkit.entity.EntityType.WARDEN);
        var template=new dev.dasan.customdungeons.model.MobTemplate("warden","WARDEN","",20,1,.2,0,1,
            java.util.Map.of(),List.of(),List.of(),List.of(),false,"RED",null,List.of(),false);
        org.mockito.Mockito.when(session.mobs()).thenReturn(List.of(new dev.dasan.customdungeons.runtime.ActiveMob(warden,template,session)));
        bridge.watchWardens(session); bridge.watchWardens(session);
        for(int i=0;i<40;i++) clock.advance();
        org.mockito.Mockito.verify(warden,org.mockito.Mockito.times(2)).setAnger(f.admin,150);
        bridge.onFinished(session,dev.dasan.customdungeons.storage.RunResult.FAILED,java.util.Set.of());
        for(int i=0;i<40;i++) clock.advance();
        org.mockito.Mockito.verify(warden,org.mockito.Mockito.times(2)).setAnger(f.admin,150);
        assertTrue(((java.util.Set<?>)wardenWatchers(bridge)).isEmpty()); f.test.close(); f.services.journal.close();
    }
    @Test void deadPrincipalStopsWithoutWaitingForRemovalEvent() {
        var f=fixture(false); f.services.tests.put(f.admin.getUniqueId(),f.test);
        var principal=entity(f,org.bukkit.entity.Mob.class,org.bukkit.entity.EntityType.WARDEN);
        f.test.principal=principal; org.mockito.Mockito.when(principal.isDead()).thenReturn(true);
        f.test.tick(); assertTrue(f.services.tests.isEmpty()); f.services.journal.close();
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

    private void participate(Fixture f,org.bukkit.entity.Player player) {
        org.mockito.Mockito.when(f.admin.isOnline()).thenReturn(true);
        org.mockito.Mockito.when(f.admin.isValid()).thenReturn(true);
        org.mockito.Mockito.when(f.admin.getWorld()).thenReturn(f.world);
        org.mockito.Mockito.when(f.admin.getGameMode()).thenReturn(org.bukkit.GameMode.SURVIVAL);
        org.mockito.Mockito.when(f.admin.hasPermission("customdungeons.admin.test")).thenReturn(true);
        org.mockito.Mockito.when(f.services.platform.messages()).thenReturn(org.mockito.Mockito.mock(dev.dasan.customdungeons.text.Messages.class));
        org.mockito.Mockito.when(player.isOnline()).thenReturn(true);
        org.mockito.Mockito.when(player.isValid()).thenReturn(true);
        org.mockito.Mockito.when(player.getGameMode()).thenReturn(org.bukkit.GameMode.SURVIVAL);
        org.mockito.Mockito.when(player.getWorld()).thenReturn(f.world);
        org.mockito.Mockito.when(player.getLocation()).thenReturn(new org.bukkit.Location(f.world,1,64,0));
        org.mockito.Mockito.when(f.world.getPlayers()).thenReturn(List.of(f.admin,player));
        f.test.principal=entity(f,org.bukkit.entity.Mob.class,org.bukkit.entity.EntityType.HUSK);
        f.test.tick();
    }

    private record Fixture(LiveTestService test, LiveTestService.Manager services, org.bukkit.entity.Player admin, org.bukkit.World world, org.bukkit.World otherWorld) {}
    private Fixture fixture(boolean invulnerable) {
        var plugin=org.mockito.Mockito.mock(dev.dasan.customdungeons.CustomDungeonsPlugin.class);
        org.mockito.Mockito.when(plugin.abilityRegistry()).thenReturn(new dev.dasan.customdungeons.ability.AbilityRegistry());
        org.mockito.Mockito.when(plugin.messages()).thenReturn(org.mockito.Mockito.mock(dev.dasan.customdungeons.text.Messages.class));
        var player=org.mockito.Mockito.mock(org.bukkit.entity.Player.class);
        var world=org.mockito.Mockito.mock(org.bukkit.World.class);
        org.mockito.Mockito.when(world.getUID()).thenReturn(java.util.UUID.randomUUID());
        org.mockito.Mockito.when(player.getUniqueId()).thenReturn(java.util.UUID.randomUUID());
        org.mockito.Mockito.when(player.getLocation()).thenReturn(new org.bukkit.Location(world,0,64,0));
        org.mockito.Mockito.when(player.isInvulnerable()).thenReturn(invulnerable);
        org.mockito.Mockito.when(player.isOnline()).thenReturn(true);
        org.mockito.Mockito.when(player.isValid()).thenReturn(true);
        org.mockito.Mockito.when(player.getGameMode()).thenReturn(org.bukkit.GameMode.SURVIVAL);
        org.mockito.Mockito.when(player.getWorld()).thenReturn(world);
        var config=new dev.dasan.customdungeons.config.PluginConfig("","es",null,"world",false,null,
                new dev.dasan.customdungeons.config.PluginConfig.PerformanceLimits(50,1,48),java.util.Set.of(),List.of(),null,null,300,java.util.Map.of());
        org.mockito.Mockito.when(plugin.templates()).thenReturn(java.util.Map.of());
        org.mockito.Mockito.when(plugin.limits()).thenReturn(config.limits());
        var services=new LiveTestService.Manager(plugin,plugin,config.liveTestMaxSeconds(),dev.dasan.customdungeons.session.LiveTestIntegration.validation(plugin,config),candidate -> plugin.sessionManager()==null || plugin.sessionManager().sessionOf(candidate.getUniqueId()).isEmpty(),directory.resolve("blocks"));
        return new Fixture(new LiveTestService(services,player),services,player,world,org.mockito.Mockito.mock(org.bukkit.World.class));
    }

    @Test void fallbackPreservesAdminPositionAndOrientationEvenLookingDown() {
        var at=new org.bukkit.Location(null,10,64,20,90,90);
        var spawned=LiveTestService.spawnLocation(at);
        assertEquals(10,spawned.getX(),1e-9); assertEquals(20,spawned.getZ(),1e-9);
        assertEquals(64,spawned.getY()); assertEquals(10,at.getX());
        assertEquals(at,spawned); assertNotSame(at,spawned);
    }
    @Test void targetedBlockUsesItsTopAtUpToThirtyTwoBlocksAndMissFallsBack() {
        var admin=org.mockito.Mockito.mock(org.bukkit.entity.Player.class);
        var world=org.mockito.Mockito.mock(org.bukkit.World.class);
        var at=new org.bukkit.Location(world,1.25,64.5,2.75,90,45);
        org.mockito.Mockito.when(admin.getLocation()).thenReturn(at);
        assertEquals(at,LiveTestService.spawnLocation(admin));
        var block=org.mockito.Mockito.mock(org.bukkit.block.Block.class);
        org.mockito.Mockito.when(block.getWorld()).thenReturn(world);
        org.mockito.Mockito.when(block.getX()).thenReturn(20);
        org.mockito.Mockito.when(block.getY()).thenReturn(65);
        org.mockito.Mockito.when(block.getZ()).thenReturn(10);
        org.mockito.Mockito.when(block.getBoundingBox()).thenReturn(new org.bukkit.util.BoundingBox(20,65,10,21,66,11));
        // Even a side-face hit spawns above the top, rather than inside the wall.
        org.mockito.Mockito.when(admin.rayTraceBlocks(32)).thenReturn(new org.bukkit.util.RayTraceResult(
                new org.bukkit.util.Vector(20,65.5,10.5),block,org.bukkit.block.BlockFace.WEST));
        var spawn=LiveTestService.spawnLocation(admin);
        assertEquals(new org.bukkit.Location(world,20.5,66,10.5,90,45),spawn);
        org.mockito.Mockito.verify(admin,org.mockito.Mockito.times(2)).rayTraceBlocks(32);
        assertEquals(new org.bukkit.Location(world,1.25,64.5,2.75,90,45),at);
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
    private static Object wardenWatchers(Object bridge) {
        try { var field=bridge.getClass().getDeclaredField("wardensWatching"); field.setAccessible(true); return field.get(bridge); }
        catch(ReflectiveOperationException error) { throw new AssertionError(error); }
    }

}
