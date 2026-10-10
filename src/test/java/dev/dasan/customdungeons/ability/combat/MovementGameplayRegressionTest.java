package dev.dasan.customdungeons.ability.combat;

import dev.dasan.customdungeons.ability.*;
import dev.dasan.customdungeons.ability.control.*;
import dev.dasan.customdungeons.intelligence.*;
import dev.dasan.customdungeons.mob.MobDamageAppliedEvent;
import dev.dasan.customdungeons.model.*;
import dev.dasan.customdungeons.runtime.ActiveMob;
import java.util.*;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.*;
import org.bukkit.util.BoundingBox;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Gameplay regression coverage for RF-HAB2-13. */
class MovementGameplayRegressionTest {
    static { dev.dasan.customdungeons.ability.impl.PaperApiTestBootstrap.initialize(); }
    private static AbilityInstance periodic(String id) {
        return new AbilityInstance(id, Trigger.EVERY_X_SECONDS, 5, TargetMode.NEAREST,
                16, 100, 1, 20, Map.of());
    }

    @Test void chargeMustNotStarveControlsWithTheSameGuiDefaultCadence() {
        try (var f = new CombatServiceTest.Fixture()) {
            when(f.entity.getUniqueId()).thenReturn(new UUID(0, 0));
            var cage = spy(new LevitationCageAbility());
            var grab = spy(new GrabThrowAbility());
            doAnswer(i->{AbilityCasts.started(i.getArgument(0));return null;}).when(cage).execute(any(AbilityContext.class),anyInt());
            doAnswer(i->{AbilityCasts.started(i.getArgument(0));return null;}).when(grab).execute(any(AbilityContext.class),anyInt());
            f.registry.register(cage);
            f.registry.register(grab);
            f.caster.abilities().addAll(List.of(periodic("charge"), periodic("levitation_cage"), periodic("grab_throw")));
            var engine = new AbilityEngine(f.registry, f.platform, new Random(1));
            for (int tick = 0; tick < 400; tick++) {
                // A moving survival player remains within both the engine range and grab reach.
                f.positions.put(f.player, f.mobPosition.clone().add(0, 0, 2));
                f.positions.put(f.ally, f.mobPosition.clone().add(1, 0, 2));
                engine.tick(List.of(f.caster), tick);
                if (CombatService.paused(f.caster) && tick < 30) {
                    assertTrue(f.caster.ready("levitation_cage#1", 1), "Pending cage must retain its cooldown");
                    assertTrue(f.caster.ready("grab_throw#2", 1), "Pending grab must retain its cooldown");
                }
                f.step(1);
            }
            assertAll(
                () -> verify(cage, atLeastOnce()).execute(any(AbilityContext.class), anyInt()),
                () -> verify(grab, atLeastOnce()).execute(any(AbilityContext.class), anyInt()));
        }
    }

    @Test void aWalkableBottomSlabMustNotBeClassifiedAsAChargeWall() {
        try (var f = new CombatServiceTest.Fixture()) {
            f.mobPosition.setY(63.5);
            f.positions.put(f.player, new Location(f.world, 0, 63.5, 2));
            f.positions.put(f.ally, new Location(f.world, 1, 63.5, 2));
            for (int x = -1; x <= 1; x++) for (int z = -1; z <= 14; z++) {
                var slab = f.world.getBlockAt(x, 63, z);
                when(slab.getType()).thenReturn(Material.STONE_SLAB);
                when(slab.isSolid()).thenReturn(true);
                when(slab.isPassable()).thenReturn(false);
                // The collision shape ends at the feet: no overlap with the mob's body.
                when(slab.getBoundingBox()).thenReturn(new BoundingBox(x, 63, z, x + 1, 63.5, z + 1));
            }
            f.cast("charge");
            f.step(45); // Clear charge would have finished at tick 39; false stun lasts until 60.
            assertAll(
                () -> assertTrue(f.mobPosition.getZ() > 1, "Charge never advances on an unobstructed slab floor"),
                () -> assertFalse(CombatService.paused(f.caster), "Floor incorrectly produces a wall stun"));
        }
    }

    @Test void chargeMustFaceItsTravelDirectionInsteadOfTeleportingBackward() {
        try (var f = new CombatServiceTest.Fixture()) {
            f.mobPosition.setYaw(180); // Looking south's opposite, while target lies at positive Z.
            var start = f.mobPosition.clone();
            f.cast("charge");
            f.step(24);
            var travel = f.mobPosition.toVector().subtract(start.toVector()).normalize();
            assertTrue(f.mobPosition.getZ() > start.getZ(), "Charge advances toward the target");
            assertTrue(f.mobPosition.getDirection().dot(travel) > .9,
                    "The original yaw is preserved, so the mob moves backward (dot = "
                            + f.mobPosition.getDirection().dot(travel) + ")");
        }
    }

    @Test void oldWeakHitsOutsideTheDetectionWindowMustNotOverrideTheCurrentThreatTarget() {
        try (var f = new CombatServiceTest.Fixture(); var intelligence = new IntelligenceService(f.platform, IntelligenceRules.defaults())) {
            when(f.entity.getUniqueId()).thenReturn(new UUID(0, 0));
            f.bukkit.when(() -> Bukkit.getPlayer(f.player.getUniqueId())).thenReturn(f.player);
            f.bukkit.when(() -> Bukkit.getPlayer(f.ally.getUniqueId())).thenReturn(f.ally);
            var definition = IntelligenceDef.level(5).withWeakPoint(IntelligenceDef.WeakPoint.BACK);
            var boss = new ActiveMob(f.entity, f.caster.template().withIntelligence(definition), f.host);
            IntelligenceService.track(boss);
            var memory = IntelligenceService.brain(boss).memory();
            memory.record(f.player.getUniqueId(), "damage", "ENTITY_ATTACK", 1, 0, true, false, true);
            memory.record(f.player.getUniqueId(), "damage", "ENTITY_ATTACK", 1, 1, true, false, true);
            memory.record(f.ally.getUniqueId(), "damage", "ENTITY_ATTACK", 50, 150);
            f.tick.set(150); // Both weak hits exceed the Extreme 120-tick detection window.
            var engine = mock(AbilityEngine.class);
            IntelligenceService.tick(boss, engine, 150);
            verify(f.entity).setTarget(f.ally);
            assertAll(
                () -> verify(f.entity, never()).setTarget(f.player),
                () -> verify(engine, never()).fire(eq(Trigger.ATTACKED_FROM_BEHIND), eq(boss), isNull(), eq(150L)));
        }
    }

    @Test void repeatedFrontalHeadWeakHitsMustNotTriggerAttackedFromBehind() {
        try (var f = new CombatServiceTest.Fixture(); var intelligence = new IntelligenceService(f.platform, IntelligenceRules.defaults())) {
            when(f.entity.getUniqueId()).thenReturn(new UUID(0, 0));
            f.bukkit.when(() -> Bukkit.getPlayer(f.player.getUniqueId())).thenReturn(f.player);
            f.bukkit.when(() -> Bukkit.getPlayer(f.ally.getUniqueId())).thenReturn(f.ally);
            var boss = new ActiveMob(f.entity, f.caster.template().withIntelligence(
                    IntelligenceDef.level(5).withWeakPoint(IntelligenceDef.WeakPoint.HEAD)), f.host);
            IntelligenceService.track(boss);
            when(f.entity.getEyeLocation()).thenReturn(new Location(f.world, 0, 65.6, 0));
            var arrow = mock(Arrow.class);
            var bow = mock(ItemStack.class);
            when(bow.getType()).thenReturn(Material.BOW);
            when(arrow.getWeapon()).thenReturn(bow);
            when(arrow.getShooter()).thenReturn(f.player);
            when(arrow.getLocation()).thenReturn(new Location(f.world, 0, 65.6, .3));
            when(arrow.getVelocity()).thenReturn(new org.bukkit.util.Vector(0, 0, -1));
            var hit = mock(EntityDamageByEntityEvent.class);
            when(hit.getDamager()).thenReturn(arrow);
            when(hit.getCause()).thenReturn(org.bukkit.event.entity.EntityDamageEvent.DamageCause.PROJECTILE);
            intelligence.applied(new MobDamageAppliedEvent(f.entity, hit, 1, 99));
            f.tick.set(1);
            intelligence.applied(new MobDamageAppliedEvent(f.entity, hit, 1, 98));
            var events = IntelligenceService.brain(boss).memory().observations(f.player.getUniqueId(), 1);
            assertEquals(2, events.size());
            assertTrue(events.stream().allMatch(e -> e.weak() && !e.behind()));
            f.tick.set(10);
            var engine = mock(AbilityEngine.class);
            IntelligenceService.tick(boss, engine, 10);
            verify(engine, never()).fire(eq(Trigger.ATTACKED_FROM_BEHIND), eq(boss), isNull(), eq(10L));
        }
    }

    @Test void thiefRetreatMustNotFightTheExtremeThreatSelectorAndChargeFreeze() {
        try (var f = new CombatServiceTest.Fixture(); var intelligence = new IntelligenceService(f.platform, IntelligenceRules.defaults())) {
            when(f.entity.getUniqueId()).thenReturn(new UUID(0, 0));
            f.bukkit.when(() -> Bukkit.getPlayer(f.player.getUniqueId())).thenReturn(f.player);
            f.bukkit.when(() -> Bukkit.getPlayer(f.ally.getUniqueId())).thenReturn(f.ally);
            when(f.entity.getVelocity()).thenReturn(new org.bukkit.util.Vector());
            var inventory = mock(PlayerInventory.class);
            var item = mock(ItemStack.class);
            when(item.getType()).thenReturn(Material.DIAMOND_SWORD);
            when(item.getAmount()).thenReturn(1);
            when(item.clone()).thenReturn(item);
            when(inventory.getContents()).thenReturn(new ItemStack[]{item});
            when(inventory.getItem(0)).thenReturn(item);
            when(f.player.getInventory()).thenReturn(inventory);
            var boss = new ActiveMob(f.entity, f.caster.template().withIntelligence(IntelligenceDef.level(5)), f.host);
            IntelligenceService.track(boss);
            IntelligenceService.brain(boss).memory().record(f.player.getUniqueId(), "damage", "ENTITY_ATTACK", 1, 0);
            var thief = new dev.dasan.customdungeons.ability.impl.custom.ThiefAbility();
            var hit = mock(EntityDamageByEntityEvent.class);
            when(hit.getEntity()).thenReturn(f.player);
            when(hit.getDamager()).thenReturn(f.entity);
            thief.execute(new AbilityContext(boss, List.of(f.player), new ParamValues(Map.of(), thief.params()), f.host, hit));
            clearInvocations(f.entity);
            // Intelligence installs the combat target while the retreat clears it every two ticks.
            IntelligenceService.tick(boss, mock(AbilityEngine.class), 0);
            f.step(2);
            assertAll(
                () -> verify(f.entity, never()).setTarget(f.player),
                () -> {
                    f.cast("charge");
                    clearInvocations(f.entity);
                    f.step(2);
                    verify(f.entity, never()).setVelocity(argThat(v -> v.lengthSquared() > .01));
                });
        }
    }
}
