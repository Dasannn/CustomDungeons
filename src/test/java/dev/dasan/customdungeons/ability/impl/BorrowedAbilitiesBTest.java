package dev.dasan.customdungeons.ability.impl;

import dev.dasan.customdungeons.ability.impl.borrowed.BlazeVolleyAbility;
import java.util.*;
import dev.dasan.customdungeons.ability.*;
import dev.dasan.customdungeons.ability.impl.borrowed.*;
import dev.dasan.customdungeons.ability.impl.generic.*;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.entity.*;
import io.papermc.paper.event.entity.EntityKnockbackEvent;
import org.bukkit.persistence.PersistentDataContainer;
import static org.mockito.Mockito.*;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;
import org.bukkit.potion.PotionEffectType;
import static org.junit.jupiter.api.Assertions.*;

class BorrowedAbilitiesBTest {
    @BeforeAll
    static void initializePublicApiRegistries() {
        PaperApiTestBootstrap.initialize();
    }
    @Test void volleyIsSymmetricPreservesSpeedAndDoesNotMutateAim() {
        var aim = new Vector(0, 0, 2);
        var rays = BlazeVolleyAbility.velocities(aim, 3, 30);
        assertEquals(3, rays.size());
        assertEquals(new Vector(0, 0, 2), aim);
        assertEquals(new Vector(0, 0, 1), rays.get(1));
        assertEquals(0, rays.stream().mapToDouble(Vector::getX).sum(), 1e-9);
        for (var ray : rays) assertEquals(1, ray.length(), 1e-9);
        assertEquals(Math.sin(Math.toRadians(15)), Math.abs(rays.getFirst().getX()), 1e-9);
    }
    @Test void singleShotHasNoSpreadAndZeroAimDoesNotLaunch() {
        assertEquals(List.of(new Vector(1, 0, 0)), BlazeVolleyAbility.velocities(new Vector(4, 0, 0), 1, 60));
        assertTrue(BlazeVolleyAbility.velocities(new Vector(), 3, 30).isEmpty());
    }

    @Test void registerAddsNineBorrowedAndOneGenericWithoutDuplicatingCore() {
        var registry = new AbilityRegistry();
        BorrowedAbilitiesB.register(registry);
        assertEquals(9, registry.all().size());
        GenericAbilities.register(registry);
        assertEquals(Set.of("blaze_volley", "ghast_fireball", "wind_charge", "breeze_leap", "shulker_bullet",
                "elder_curse", "guardian_beam", "creeper_blast", "witch_potions", "arrow_effect"),
                registry.all().stream().map(Ability::id).collect(java.util.stream.Collectors.toSet()));
        var defaults = new AbilityRegistry();
        Abilities.registerDefaults(defaults);
        assertTrue(defaults.all().stream().map(Ability::id).collect(java.util.stream.Collectors.toSet()).containsAll(registry.all().stream().map(Ability::id).toList()));
    }
    @Test void allHaveUniqueIdsAndValidParamSpecs() {
        var registry = new AbilityRegistry();
        BorrowedAbilitiesB.register(registry);
        GenericAbilities.register(registry);
        Set<String> ids = new HashSet<>();
        for (var ability : registry.all()) {
            assertTrue(ids.add(ability.id()));
            assertNotNull(ability.icon());
            Set<String> keys = new HashSet<>();
            for (var spec : ability.params()) {
                assertTrue(keys.add(spec.key()));
                assertNotNull(spec.defaultValue());
                if (spec.defaultValue() instanceof Number n) {
                    assertTrue(Double.isFinite(n.doubleValue()));
                    assertTrue(n.doubleValue() >= spec.min() && n.doubleValue() <= spec.max());
                }
            }
        }
    }
    private <T extends Projectile> T projectile(BorrowedAbilitiesATest.Fixture f, Class<T> type) {
        var projectile = mock(type);
        when(projectile.getPersistentDataContainer()).thenReturn(mock(PersistentDataContainer.class));
        when(projectile.getLocation()).thenReturn(new Location(f.world, 2, 64, 0));
        doAnswer(c->{((java.util.function.Consumer<org.bukkit.entity.Projectile>)c.getArgument(2)).accept(projectile);return projectile;}).when(f.entity).launchProjectile(eq(type), any(Vector.class), any());
        return projectile;
    }
    private ProjectileHitEvent hit(Projectile projectile, Entity target) {
        var event = mock(ProjectileHitEvent.class);
        when(event.getEntity()).thenReturn(projectile);
        when(event.getHitEntity()).thenReturn(target);
        new BorrowedAbilitiesB().hit(event);
        return event;
    }
    @Test void instantEffectsHaveOneTickRegardlessOfConfiguredSeconds() {
        var customInstant = mock(PotionEffectType.class);
        when(customInstant.isInstant()).thenReturn(true);
        for (var type : List.of(PotionEffectType.INSTANT_HEALTH, PotionEffectType.INSTANT_DAMAGE,
                PotionEffectType.SATURATION, customInstant)) {
            for (double seconds : new double[] {0.05, 2.5, 3600})
                assertEquals(1, BorrowedAbilitiesB.durationTicks(type, seconds));
        }
    }
    @Test void potionBuilderAndArrowUseSingleTickForInstantEffects() {
        for (var type : List.of(PotionEffectType.INSTANT_HEALTH, PotionEffectType.INSTANT_DAMAGE,
                PotionEffectType.SATURATION)) {
            var key = NamespacedKey.minecraft("test_instant");
            try (var ignored = PaperApiTestBootstrap.withEffect(key, type)) {
                var f = new BorrowedAbilitiesATest.Fixture();
                var arrow = projectile(f, Arrow.class);
                var ability = new ArrowEffectAbility();
                var ctx = f.context(ability, Map.of("effect", key.toString(), "seconds", 3600, "amplifier", 2));
                var potion = BorrowedAbilitiesB.potion(ctx);
                assertEquals(1, potion.getDuration());
                assertEquals(type, potion.getType());
                ability.execute(ctx);
                verify(arrow).addCustomEffect(argThat(effect -> effect.getDuration() == 1
                        && effect.getType() == type && effect.getAmplifier() == 2), eq(true));
                // The custom potion impact and the native arrow consume the same built effect.
                assertEquals(1, BorrowedAbilitiesB.potion(f.context(new WitchPotionsAbility(),
                        Map.of("effect", key.toString(), "seconds", 3600))).getDuration());
            }
            assertNotSame(type, Registry.EFFECT.get(key), "temporary effect must be restored");
        }
    }
    @Test void thrownPotionsAreAlsoMarkedForResetWithoutRunningTheTimeout() {
        var f = new BorrowedAbilitiesATest.Fixture();
        var shot = projectile(f, ThrownPotion.class);
        BorrowedAbilitiesB.launch(f.context(new WitchPotionsAbility(), Map.of()), ThrownPotion.class,
                new Vector(1, 0, 0), (context, at, hit) -> {});
        verify(shot.getPersistentDataContainer()).set(dev.dasan.customdungeons.mob.MobKeys.SESSION,
                org.bukkit.persistence.PersistentDataType.STRING, f.session.id().toString());
        verify(shot.getPersistentDataContainer()).set(dev.dasan.customdungeons.mob.MobKeys.ABILITY_PROJECTILE,
                org.bukkit.persistence.PersistentDataType.BYTE, (byte) 1);
        verify(shot).setPersistent(false);
        // Marking is synchronous, before any queued timeout can run.
        assertEquals(1, f.pending.size());
        verify(shot, never()).remove();
    }
    @Test void nativeParticipantGuardStillWorksAfterImpactRoutingIsReleased() {
        var f = new BorrowedAbilitiesATest.Fixture();
        var shot = projectile(f, Arrow.class);
        var ability = new ArrowEffectAbility();
        ability.execute(f.context(ability, Map.of()));
        hit(shot, f.player);
        assertTrue(Effects.projectileTargetAllowed(shot, f.player));
        assertFalse(Effects.projectileTargetAllowed(shot, f.outsider));
        when(f.session.players()).thenReturn(List.of());
        assertFalse(Effects.projectileTargetAllowed(shot, f.player));
    }
    @Test void timedEffectsKeepSecondsToTicksConversion() {
        var type = mock(PotionEffectType.class);
        assertEquals(1, BorrowedAbilitiesB.durationTicks(type, 0.05));
        assertEquals(50, BorrowedAbilitiesB.durationTicks(type, 2.5));
        assertEquals(51, BorrowedAbilitiesB.durationTicks(type, 2.56));
        assertEquals(72000, BorrowedAbilitiesB.durationTicks(type, 3600));
    }
    @Test void everyProjectileIsSessionMarkedAndNonPersistent() {
        for (var ability : List.of(new BlazeVolleyAbility(), new GhastFireballAbility(),
                new WindChargeAbility(), new ShulkerBulletAbility(), new ArrowEffectAbility())) {
            var f = new BorrowedAbilitiesATest.Fixture();
            Class<? extends Projectile> type = switch (ability.id()) {
                case "blaze_volley" -> SmallFireball.class;
                case "ghast_fireball" -> Fireball.class;
                case "wind_charge" -> org.bukkit.entity.WindCharge.class;
                case "shulker_bullet" -> org.bukkit.entity.ShulkerBullet.class;
                default -> Arrow.class;
            };
            var shot = projectile(f, type);
            ability.execute(f.context(ability, Map.of("count", 1)));
            verify(shot.getPersistentDataContainer()).set(dev.dasan.customdungeons.mob.MobKeys.SESSION,
                    org.bukkit.persistence.PersistentDataType.STRING, f.session.id().toString());
            verify(shot.getPersistentDataContainer()).set(dev.dasan.customdungeons.mob.MobKeys.ABILITY_PROJECTILE,
                    org.bukkit.persistence.PersistentDataType.BYTE, (byte) 1);
            verify(shot).setPersistent(false);
        }
    }
    @Test void volleyRespectsCountRejectsOutsidersAndExpires() {
        var f = new BorrowedAbilitiesATest.Fixture();
        var ball = projectile(f, SmallFireball.class);
        var ability = new BlazeVolleyAbility();
        ability.execute(f.context(ability, Map.of("count", 3)));
        verify(f.entity, times(3)).launchProjectile(eq(SmallFireball.class), any(Vector.class), any());
        verify(ball, times(6)).setIsIncendiary(false);
        verify(ball, times(3)).setYield(0);
        verify(hit(ball, f.outsider)).setCancelled(true);
        verify(f.outsider, never()).damage(anyDouble(), any(Entity.class));
        f.pending.forEach(Runnable::run);
        verify(ball, times(4)).remove();
    }
    @Test void ghastImpactAppliesConfiguredRadialDamageOnlyToParticipants() {
        var f = new BorrowedAbilitiesATest.Fixture();
        var ball = projectile(f, Fireball.class);
        var ability = new GhastFireballAbility();
        ability.execute(f.context(ability, Map.of("damage", 13.0, "yield", 3.0)));
        hit(ball, null); // block impacts may still damage nearby participants
        verify(f.player).damage(13, f.entity);
        verify(f.outsider, never()).damage(anyDouble(), any(Entity.class));
        verify(f.world, never()).createExplosion(any(Location.class), anyFloat(), anyBoolean(), anyBoolean(), any(Entity.class));
    }
    @Test void windPushIsConfiguredAndCannotPushOutsiders() {
        var f = new BorrowedAbilitiesATest.Fixture();
        var ball = projectile(f, org.bukkit.entity.WindCharge.class);
        var ability = new WindChargeAbility();
        ability.execute(f.context(ability, Map.of("power", 2.0)));
        hit(ball, f.player);
        verify(f.player).setVelocity(new Vector(0, 1, 0));
        verify(f.outsider, never()).setVelocity(any());
    }
    @Test void leapUsesFirstEligibleTargetAndConfiguredHeight() {
        var f = new BorrowedAbilitiesATest.Fixture();
        var ability = new BreezeLeapAbility();
        ability.execute(f.context(ability, Map.of("height", 1.2)));
        verify(f.entity).setVelocity(new Vector(0.8, 1.2, 0));
        clearInvocations(f.entity);
        when(f.player.getGameMode()).thenReturn(GameMode.SPECTATOR);
        ability.execute(f.context(ability, Map.of()));
        verify(f.entity, never()).setVelocity(any());
    }
    @Test void bulletTargetsParticipantAndAppliesConfiguredLevitation() {
        var f = new BorrowedAbilitiesATest.Fixture();
        var bullet = projectile(f, org.bukkit.entity.ShulkerBullet.class);
        var ability = new ShulkerBulletAbility();
        ability.execute(f.context(ability, Map.of("levitationSeconds", 2.5)));
        verify(bullet).setTarget(f.player);
        hit(bullet, f.player);
        verify(f.player).damage(4, f.entity);
        verify(f.player).addPotionEffect(argThat(effect -> effect.getDuration() == 50 && effect.getAmplifier() == 0));
    }
    @Test void beamChargesBeforeDamageAndChecksFinalLineOfSight() {
        var f = new BorrowedAbilitiesATest.Fixture();
        when(f.entity.hasLineOfSight(f.player)).thenReturn(true);
        var ability = new GuardianBeamAbility();
        ability.execute(f.context(ability, Map.of("chargeTicks", 2, "damage", 9.0)));
        verify(f.player, never()).damage(anyDouble(), any(Entity.class));
        f.pending.removeFirst().run();
        verify(f.player, never()).damage(anyDouble(), any(Entity.class));
        f.pending.removeFirst().run();
        verify(f.player).damage(9, f.entity);
        clearInvocations(f.player);
        when(f.entity.hasLineOfSight(f.player)).thenReturn(false);
        ability.execute(f.context(ability, Map.of("chargeTicks", 1)));
        f.pending.removeFirst().run();
        verify(f.player, never()).damage(anyDouble(), any(Entity.class));
    }
    @Test void beamCancelsOnDeathAndParticipantDeparture() {
        var f = new BorrowedAbilitiesATest.Fixture();
        var ability = new GuardianBeamAbility();
        ability.execute(f.context(ability, Map.of("chargeTicks", 1)));
        when(f.entity.isDead()).thenReturn(true);
        f.pending.removeFirst().run();
        verify(f.player, never()).damage(anyDouble(), any(Entity.class));
        when(f.entity.isDead()).thenReturn(false);
        ability.execute(f.context(ability, Map.of("chargeTicks", 1)));
        when(f.session.players()).thenReturn(List.of());
        f.pending.removeFirst().run();
        verify(f.player, never()).damage(anyDouble(), any(Entity.class));
    }
    @Test void creeperTelegraphsThenExplodesWithoutFireOrBlocksAndManualDamage() {
        var f = new BorrowedAbilitiesATest.Fixture();
        var ability = new CreeperBlastAbility();
        when(f.entity.getPersistentDataContainer()).thenReturn(mock(PersistentDataContainer.class));
        ability.execute(f.context(ability, Map.of("fuseTicks", 1, "radius", 4.0, "damage", 11.0)));
        verify(f.world, never()).createExplosion(any(Location.class), anyFloat(), anyBoolean(), anyBoolean(), any(Entity.class));
        when(f.world.createExplosion(any(Location.class), anyFloat(), eq(false), eq(false), eq(f.entity)))
                .thenAnswer(inv -> {
                    var listener = new BorrowedAbilitiesB();
                    var nativeDamage = mock(EntityDamageByEntityEvent.class);
                    when(nativeDamage.getDamager()).thenReturn(f.entity);
                    when(nativeDamage.getCause()).thenReturn(EntityDamageEvent.DamageCause.ENTITY_EXPLOSION);
                    when(nativeDamage.getEntity()).thenReturn(f.outsider);
                    listener.damage(nativeDamage);
                    verify(nativeDamage).setCancelled(true);
                    var push = mock(EntityKnockbackEvent.class);
                    when(push.getCause()).thenReturn(EntityKnockbackEvent.Cause.EXPLOSION);
                    listener.knockback(push);
                    verify(push).setCancelled(true);
                    return true;
                });
        f.pending.removeFirst().run();
        verify(f.world).createExplosion(new Location(f.world, 0, 64, 0), 2f, false, false, f.entity);
        verify(f.player).damage(11, f.entity);
        verify(f.outsider, never()).damage(anyDouble(), any(Entity.class));
    }
    @Test void creeperFuseCancelsOnCasterDeath() {
        var f = new BorrowedAbilitiesATest.Fixture();
        var ability = new CreeperBlastAbility();
        ability.execute(f.context(ability, Map.of("fuseTicks", 1)));
        when(f.entity.isDead()).thenReturn(true);
        f.pending.removeFirst().run();
        verify(f.world, never()).createExplosion(any(Location.class), anyFloat(), anyBoolean(), anyBoolean(), any(Entity.class));
    }
    @Test void projectilesCancelNativeDamageExplosionsAndPotionSplash() {
        var projectile = mock(ThrownPotion.class);
        var data = mock(PersistentDataContainer.class);
        when(projectile.getPersistentDataContainer()).thenReturn(data);
        when(data.has(new NamespacedKey("customdungeons", "abilities_b"), org.bukkit.persistence.PersistentDataType.BYTE)).thenReturn(true);
        var listener = new BorrowedAbilitiesB();
        var splash = mock(PotionSplashEvent.class);
        when(splash.getPotion()).thenReturn(projectile);
        listener.splash(splash);
        verify(splash).setCancelled(true);
        var prime = mock(ExplosionPrimeEvent.class);
        when(prime.getEntity()).thenReturn(projectile);
        listener.prime(prime);
        verify(prime).setCancelled(true);
        var damage = mock(EntityDamageByEntityEvent.class);
        when(damage.getDamager()).thenReturn(projectile);
        listener.damage(damage);
        verify(damage).setCancelled(true);
        var explosion = mock(EntityExplodeEvent.class);
        var blocks = new ArrayList<org.bukkit.block.Block>();
        blocks.add(mock(org.bukkit.block.Block.class));
        when(explosion.getEntity()).thenReturn(projectile);
        when(explosion.blockList()).thenReturn(blocks);
        listener.explode(explosion);
        assertTrue(blocks.isEmpty());
    }

    @Test void invalidPotionKeysDoNotLaunchPotionsOrArrows() {
        var f = new BorrowedAbilitiesATest.Fixture();
        for (var ability : List.of(new WitchPotionsAbility(), new ArrowEffectAbility()))
            ability.execute(f.context(ability, Map.of("effect", "invalid key!")));
        verify(f.entity, never()).launchProjectile(any(), any(Vector.class), any());
    }
    @Test void projectilesFromDeadCastersCannotApplyEffects() {
        var f = new BorrowedAbilitiesATest.Fixture();
        var ball = projectile(f, Fireball.class);
        var ability = new GhastFireballAbility();
        ability.execute(f.context(ability, Map.of()));
        when(f.entity.isDead()).thenReturn(true);
        hit(ball, f.player);
        verify(f.player, never()).damage(anyDouble(), any(Entity.class));
    }
    @Test void impactRechecksParticipantMembership() {
        var f = new BorrowedAbilitiesATest.Fixture();
        var ball = projectile(f, Fireball.class);
        var ability = new GhastFireballAbility();
        ability.execute(f.context(ability, Map.of()));
        when(f.session.players()).thenReturn(List.of());
        hit(ball, f.player);
        verify(f.player, never()).damage(anyDouble(), any(Entity.class));
    }

    @Test void tippedArrowKeepsNativeImpactForParticipantsAndExpiresWithoutPickup() {
        var f = new BorrowedAbilitiesATest.Fixture();
        var arrow = projectile(f, Arrow.class);
        var ability = new ArrowEffectAbility();
        ability.execute(f.context(ability, Map.of("seconds", 2.5, "amplifier", 3)));
        verify(arrow).setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
        verify(arrow).addCustomEffect(argThat(effect -> effect.getDuration() == 50 && effect.getAmplifier() == 3), eq(true));
        var event = hit(arrow, f.player);
        verify(event, never()).setCancelled(true);
        verify(arrow, never()).remove();
        f.pending.removeFirst().run();
        verify(arrow).remove();
    }
    @Test void elderCurseUsesConfiguredDurationAndAmplifierOnlyOnParticipants() {
        var f = new BorrowedAbilitiesATest.Fixture();
        var ability = new ElderCurseAbility();
        ability.execute(f.context(ability, Map.of("seconds", 2.5, "amplifier", 4)));
        verify(f.player).addPotionEffect(argThat(effect -> effect.getDuration() == 50 && effect.getAmplifier() == 4
                && effect.getType() == PotionEffectType.MINING_FATIGUE));
        verify(f.outsider, never()).addPotionEffect(any());
    }
}
