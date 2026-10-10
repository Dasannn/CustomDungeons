package dev.dasan.customdungeons.ability.control;

import dev.dasan.customdungeons.ability.*;
import dev.dasan.customdungeons.ability.combat.*;
import dev.dasan.customdungeons.intelligence.IntelligenceDef;
import dev.dasan.customdungeons.model.*;
import dev.dasan.customdungeons.runtime.ActiveMob;
import java.util.*;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.*;
import org.bukkit.potion.PotionEffectType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Gameplay regression coverage for RF-HAB2-13. */
class ControlGameplayRegressionTest {
    static { dev.dasan.customdungeons.ability.impl.PaperApiTestBootstrap.initialize(); }

    static AbilityInstance defaults(String id) {
        // MobMenu.defaults and the live warden-colosal.yml share these common defaults.
        return new AbilityInstance(id, Trigger.EVERY_X_SECONDS, 5, TargetMode.NEAREST,
                16, 100, 1, 20, Map.of());
    }

    static AbilityEngine engine(ControlServiceTest.Fixture f) {
        when(f.entity.getUniqueId()).thenReturn(new UUID(0, 0));
        return new AbilityEngine(f.registry, null, new Random(1));
    }

    static void run(ControlServiceTest.Fixture f, AbilityEngine engine, ActiveMob mob, int ticks) {
        engine.tick(List.of(mob), f.tick.get());
        for (int i = 0; i < ticks; i++) {
            f.step(1);
            engine.tick(List.of(mob), f.tick.get());
        }
    }

    static void openTerrain(ControlServiceTest.Fixture f) {
        when(f.platform.limits()).thenReturn(new dev.dasan.customdungeons.config.PluginConfig.PerformanceLimits(50, 1, 48));
        when(f.world.isChunkLoaded(anyInt(), anyInt())).thenReturn(true);
        when(f.world.getMinHeight()).thenReturn(-64);
        when(f.world.getMaxHeight()).thenReturn(320);
        var border = mock(WorldBorder.class);
        when(border.getCenter()).thenReturn(new Location(f.world, 0, 64, 0));
        when(border.getSize()).thenReturn(1000.0);
        when(f.world.getWorldBorder()).thenReturn(border);
        var block = mock(Block.class);
        when(block.getType()).thenReturn(Material.AIR);
        when(block.isPassable()).thenReturn(true);
        when(block.getBlockData()).thenReturn(mock(BlockData.class));
        when(f.world.getBlockAt(anyInt(), anyInt(), anyInt())).thenReturn(block);
        when(f.entity.getWidth()).thenReturn(.6);
        when(f.entity.getHeight()).thenReturn(1.8);
        when(f.entity.hasAI()).thenReturn(true);
        when(f.entity.teleport(any(Location.class))).thenReturn(true);
    }

    @ParameterizedTest
    @ValueSource(strings = {"levitation_cage", "grab_throw"})
    void chargeBeforeControlWithGuiDefaultsMustNotEraseEveryCast(String id) {
        try (var f = new ControlServiceTest.Fixture(); var combat = new CombatService(f.platform)) {
            openTerrain(f);
            CombatAbilities.register(f.registry);
            f.caster.abilities().add(defaults("charge"));
            f.caster.abilities().add(defaults(id));
            var engine = engine(f);
            run(f, engine, f.caster, 2000);
            verify(f.entity, atLeast(10)).setAI(false); // The real charge repeatedly starts.
            long executions = id.equals("levitation_cage") ? calls(f.player, "addPotionEffect") : calls(f.entity, "addPassenger");
            assertTrue(executions > 0, "Expected at least one " + id + " execution in 100 seconds, actual: " + executions);
        }
    }

    @Test
    void simultaneousCageAndGrabMustBothGetAnOpportunityOverTime() {
        try (var f = new ControlServiceTest.Fixture()) {
            f.caster.abilities().add(defaults("levitation_cage"));
            f.caster.abilities().add(defaults("grab_throw"));
            var engine = engine(f);
            run(f, engine, f.caster, 2000);
            verify(f.player, atLeast(5)).addPotionEffect(any());
            assertTrue(calls(f.entity, "addPassenger") > 0, "Cage applied repeatedly, grab never mounted the player");
        }
    }

    @Test
    void isolatedGrabTargetMustBeChosenAmongPlayersWithinGrabReach() {
        try (var f = new ControlServiceTest.Fixture()) {
            var far = f.player(10);
            f.players.add(far);
            var smart = new ActiveMob(f.entity,
                    f.caster.template().withIntelligence(IntelligenceDef.level(5)), f.host);
            smart.abilities().add(defaults("grab_throw"));
            var engine = engine(f);
            assertEquals(List.of(far), TargetSelector.selectAbility(smart, "grab_throw", TargetMode.NEAREST, 16));
            run(f,engine,smart,80);
            assertFalse(smart.ready("grab_throw#0", 80), "Cooldown has been consumed despite no grab");
            assertEquals(1, f.service.activeCount(), "Two eligible participants remain within the 4-block reach");
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"levitation_cage", "drain_grab", "roots", "soul_chain"})
    void escapingConfiguredRangeDuringWarningMustPreventAcquisition(String id) {
        try (var f = new ControlServiceTest.Fixture()) {
            f.caster.abilities().add(new AbilityInstance(id, Trigger.ON_SPAWN, 0, TargetMode.NEAREST,
                    4, 100, 1, 20, Map.of()));
            var engine = engine(f);
            engine.fire(Trigger.ON_SPAWN, f.caster, null, 0);
            f.step(10);
            when(f.player.getLocation()).thenReturn(new Location(f.world, 30, 64, 0));
            f.step(10);
            assertEquals(0, f.service.activeCount(), "The warned target escaped a 4-block cast range");
        }
    }

    @Test
    void tacticalSummonWithConfiguredTemplateMustWorkWithoutAPlayerInCastRange() {
        try (var f = new ControlServiceTest.Fixture()) {
            f.players.clear(); // RF-HAB2-05: intelligence 0-1 summons when its trigger fires.
            f.caster.abilities().add(new AbilityInstance("tactical_summon", Trigger.EVERY_X_SECONDS,
                    5, TargetMode.NEAREST, 16, 100, 1, 20, Map.of("template", "ally", "radius", 0)));
            var engine = engine(f);
            run(f,engine,f.caster,100);
            assertEquals(2, calls(f.host, "spawnMinion"), "Configured summon has no dependence on player targets at level 0");
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"already_mounted", "mount_rejected", "potion_rejected", "existing_levitation"})
    void failedAcquisitionMustNotGrantImmunityToAPlayerWhoWasNeverControlled(String reason) {
        try (var f = new ControlServiceTest.Fixture()) {
            String first = reason.contains("levitation") || reason.contains("potion") ? "levitation_cage" : "grab_throw";
            switch (reason) {
                case "already_mounted" -> when(f.player.isInsideVehicle()).thenReturn(true);
                case "mount_rejected" -> when(f.entity.addPassenger(any())).thenReturn(false);
                case "potion_rejected" -> doReturn(false).when(f.player).addPotionEffect(any());
                case "existing_levitation" -> when(f.player.hasPotionEffect(PotionEffectType.LEVITATION)).thenReturn(true);
                default -> throw new AssertionError(reason);
            }
            f.cast(first);
            f.step(20);
            assertEquals(0, f.service.activeCount(), "Initial cast never applied a control");
            when(f.player.isInsideVehicle()).thenReturn(false);
            when(f.entity.addPassenger(any())).thenReturn(true);
            var next = first.equals("grab_throw") ? "drain_grab" : "grab_throw";
            f.cast(next);
            f.step(20);
            assertEquals(1, f.service.activeCount(), "A rejected cast must not impose release immunity");
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"cancelled_teleport", "exception"})
    void drainMustReleaseIfItsVehicleCannotFollowTheCaster(String failure) {
        try (var f = new ControlServiceTest.Fixture()) {
            f.start("drain_grab");
            var vehicle = f.created.getFirst();
            if (failure.equals("cancelled_teleport")) {
                doReturn(false).when(vehicle).teleport(any(Location.class),
                        any(io.papermc.paper.entity.TeleportFlag.class));
                f.step(1);
            } else {
                doThrow(new IllegalStateException("Injected API failure; no live occurrence established"))
                        .when(vehicle).teleport(any(Location.class), any(io.papermc.paper.entity.TeleportFlag.class));
                assertThrows(IllegalStateException.class, () -> f.step(1));
                f.step(400); // The advertised 120-tick maximum has long expired.
            }
            assertEquals(0, f.service.activeCount(), "Control must not outlive failed vehicle movement");
        }
    }

    @Test
    void tacticalSummonDoesNotDependOnAnInitiallyNearbyPlayerAfterWarning() {
        try (var f = new ControlServiceTest.Fixture()) {
            f.caster.abilities().add(new AbilityInstance("tactical_summon",Trigger.ON_SPAWN,0,
                    TargetMode.NEAREST,4,100,1,20,Map.of("template","ally","radius",0)));
            var engine=engine(f);engine.fire(Trigger.ON_SPAWN,f.caster,null,0);
            when(f.player.getLocation()).thenReturn(new Location(f.world,30,64,0));
            f.step(20);
            assertEquals(2,calls(f.host,"spawnMinion"));
        }
    }

    @Test
    void spearImpactFailureRollsBackItsReservationAndAuxiliaries() {
        try (var f = new ControlServiceTest.Fixture()) {
            f.cast("anchor_spear");f.step(20);
            doThrow(new IllegalStateException("Injected helper creation failure")).when(f.world)
                    .spawn(any(Location.class),any(Class.class),any(java.util.function.Consumer.class));
            var hit=mock(org.bukkit.event.entity.ProjectileHitEvent.class);
            when(hit.getEntity()).thenReturn((Trident)f.created.getFirst());
            when(hit.getHitEntity()).thenReturn(f.player);
            assertThrows(IllegalStateException.class,()->f.service.hit(hit));
            assertEquals(0,f.service.activeCount());assertEquals(0,f.service.auxiliaryCount());
        }
    }

    @Test
    void cancellationOfAGroupWarningCannotLetItsOtherTargetsApplyAnInvalidCast() {
        try (var f = new ControlServiceTest.Fixture()) {
            f.caster.abilities().add(new AbilityInstance("levitation_cage",Trigger.ON_SPAWN,0,
                    TargetMode.ALL_IN_RADIUS,4,100,1,20,Map.of()));
            var engine=engine(f);engine.fire(Trigger.ON_SPAWN,f.caster,null,0);
            when(f.player.getLocation()).thenReturn(new Location(f.world,30,64,0));f.step(20);
            assertEquals(0,f.service.activeCount());
            assertTrue(f.caster.ready("levitation_cage#0",20));
        }
    }

    @Test
    void aRejectedTargetCannotEraseTheReceiptOfAnotherAppliedControl() {
        try (var f = new ControlServiceTest.Fixture()) {
            doReturn(false).when(f.player).addPotionEffect(any());
            f.caster.abilities().add(new AbilityInstance("levitation_cage",Trigger.ON_SPAWN,0,
                    TargetMode.ALL_IN_RADIUS,4,100,1,20,Map.of()));
            var engine=engine(f);engine.fire(Trigger.ON_SPAWN,f.caster,null,0);f.step(20);
            assertEquals(1,f.service.activeCount());
            assertFalse(f.caster.ready("levitation_cage#0",20),"An applied control consumes its receipt");
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"rejected", "exception"})
    void initialDrainMovementFailureRollsBackWithoutImmunity(String failure) {
        try (var f = new ControlServiceTest.Fixture()) {
            var vehicle=mock(ArmorStand.class);
            when(vehicle.getUniqueId()).thenReturn(UUID.randomUUID());
            var vehicleData=ControlServiceTest.pdc();
            when(vehicle.getPersistentDataContainer()).thenReturn(vehicleData);
            when(vehicle.isValid()).thenReturn(true);
            when(vehicle.addPassenger(any())).thenReturn(true);
            doAnswer(invocation -> {
                if (failure.equals("exception")) throw new IllegalStateException("Injected teleport failure");
                return false;
            }).when(vehicle).teleport(any(Location.class),any(io.papermc.paper.entity.TeleportFlag.class));
            doAnswer(invocation -> {
                ((java.util.function.Consumer<ArmorStand>)invocation.getArgument(2)).accept(vehicle);
                f.created.add(vehicle);return vehicle;
            }).when(f.world).spawn(any(Location.class),eq(ArmorStand.class),any(java.util.function.Consumer.class));
            f.cast("drain_grab");
            if (failure.equals("exception")) assertThrows(IllegalStateException.class,()->f.step(20));
            else f.step(20);
            assertEquals(0,f.service.activeCount());assertEquals(0,f.service.auxiliaryCount());
            verify(vehicle).removePassenger(f.player);verify(vehicle).remove();
            f.cast("grab_throw");f.step(20);
            assertEquals(1,f.service.activeCount(),"Initial failure must not impose immunity");
        }
    }


    @Test
    void aThrowingBoardingAttemptIsRolledBackWithoutReleaseImmunity() {
        try (var f = new ControlServiceTest.Fixture()) {
            doThrow(new IllegalStateException("Injected mount failure")).when(f.entity).addPassenger(f.player);
            f.cast("grab_throw");assertThrows(IllegalStateException.class,()->f.step(20));
            assertEquals(0,f.service.activeCount());verify(f.entity).removePassenger(f.player);
            doReturn(true).when(f.entity).addPassenger(f.player);
            f.cast("grab_throw");f.step(20);assertEquals(1,f.service.activeCount());
        }
    }

    @Test
    void aPotionApiThrowingAfterMutationWithdrawsTheEffectItInstalled() {
        try (var f = new ControlServiceTest.Fixture()) {
            doAnswer(invocation->{
                org.bukkit.potion.PotionEffect installed=invocation.getArgument(0);
                when(f.player.getPotionEffect(PotionEffectType.LEVITATION)).thenReturn(installed);
                throw new IllegalStateException("Injected post-mutation failure");
            }).when(f.player).addPotionEffect(any());
            f.cast("levitation_cage");assertThrows(IllegalStateException.class,()->f.step(20));
            assertEquals(0,f.service.activeCount());verify(f.player).removePotionEffect(PotionEffectType.LEVITATION);
        }
    }

    @Test
    void cleanupContinuesRemovingAuxiliariesAfterAnUnmountException() {
        try (var f = new ControlServiceTest.Fixture()) {
            f.start("drain_grab");var vehicle=f.created.getFirst();
            doThrow(new IllegalStateException("Injected unmount failure")).when(vehicle).removePassenger(f.player);
            doReturn(false).when(vehicle).teleport(any(Location.class),any(io.papermc.paper.entity.TeleportFlag.class));
            assertThrows(IllegalStateException.class,()->f.step(1));
            assertEquals(0,f.service.activeCount());assertEquals(0,f.service.auxiliaryCount());
            verify(vehicle).remove();
        }
    }

    @Test
    void independentCageAndGrabActuallyExecuteWhenNotCompeting() {
        for (String id : List.of("levitation_cage", "grab_throw")) {
            try (var f = new ControlServiceTest.Fixture()) {
                f.caster.abilities().add(defaults(id));
                var engine = engine(f);
                run(f,engine,f.caster,80);
                assertEquals(1, f.service.activeCount());
                if (id.equals("levitation_cage")) assertNotNull(f.player.getPotionEffect(PotionEffectType.LEVITATION));
                else verify(f.entity).addPassenger(f.player);
            }
        }
    }

    static long calls(Object mock, String name) {
        return mockingDetails(mock).getInvocations().stream().filter(i -> i.getMethod().getName().equals(name)).count();
    }
}
