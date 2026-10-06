package dev.dasan.customdungeons.ability.impl;

import dev.dasan.customdungeons.ability.*;
import dev.dasan.customdungeons.ability.impl.borrowed.*;
import java.util.*;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import dev.dasan.customdungeons.runtime.*;
import dev.dasan.customdungeons.model.MobTemplate;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.entity.*;
import org.bukkit.persistence.PersistentDataContainer;
import java.util.function.Consumer;

class BorrowedAbilitiesATest {
    @org.junit.jupiter.api.BeforeAll
    static void initializeApiRegistries() {
        PaperApiTestBootstrap.initialize();
    }

    @Test void registerAddsEight() {
        var registry = new AbilityRegistry();
        BorrowedAbilitiesA.register(registry);
        assertEquals(8, registry.all().size());
        assertEquals(Set.of("wither_skulls", "wither_shockwave", "dragon_breath", "dragon_roar",
                "sonic_boom", "darkness_pulse", "evoker_fangs", "summon_vexes"),
                registry.all().stream().map(Ability::id).collect(java.util.stream.Collectors.toSet()));
    }
    @Test void allHaveUniqueIdsAndValidParamSpecs() {
        var registry = new AbilityRegistry();
        BorrowedAbilitiesA.register(registry);
        Set<String> ids = new HashSet<>();
        for (var ability : registry.all()) {
            assertTrue(ids.add(ability.id()));
            assertNotNull(ability.icon());
            Set<String> keys = new HashSet<>();
            for (var spec : ability.params()) {
                assertTrue(keys.add(spec.key()));
                assertNotNull(spec.defaultValue());
                if (spec.defaultValue() instanceof Number number) {
                    assertTrue(Double.isFinite(number.doubleValue()));
                    assertTrue(number.doubleValue() >= spec.min() && number.doubleValue() <= spec.max());
                }
            }
        }
    }
    @Test void fangLineUsesDirectionAndNeverSpawnsAtCaster() {
        var offsets = EvokerFangsAbility.offsets("LINE", 3, new Vector(0, 7, 2));
        assertEquals(List.of(new Vector(0, 0, 1.5), new Vector(0, 0, 3), new Vector(0, 0, 4.5)), offsets);
    }
    @Test void fangCircleIsEvenlySpacedAndInvalidPatternFallsBackToLine() {
        var offsets = EvokerFangsAbility.offsets("CIRCLE", 4, new Vector());
        assertEquals(4, offsets.size());
        for (var offset : offsets) assertEquals(3, offset.length(), 1e-9);
        assertEquals(0, offsets.stream().mapToDouble(Vector::getX).sum(), 1e-9);
        assertEquals(0, offsets.stream().mapToDouble(Vector::getZ).sum(), 1e-9);
        assertEquals(EvokerFangsAbility.offsets("LINE", 2, new Vector()),
                EvokerFangsAbility.offsets("invalid", 2, new Vector()));
    }

    static class Fixture {
        final World world = mock(World.class);
        final Mob entity = mock(Mob.class);
        final Player player = mock(Player.class), outsider = mock(Player.class);
        final SessionContext session = mock(SessionContext.class);
        final List<Runnable> pending = new ArrayList<>();
        final ActiveMob caster;
        Fixture() {
            when(entity.getWorld()).thenReturn(world);
            when(entity.getLocation()).thenReturn(new Location(world, 0, 64, 0));
            when(entity.getEyeLocation()).thenReturn(new Location(world, 0, 65, 0));
            when(entity.isValid()).thenReturn(true);
            when(player.getWorld()).thenReturn(world);
            when(player.getLocation()).thenReturn(new Location(world, 2, 64, 0));
            when(player.getEyeLocation()).thenReturn(new Location(world, 2, 65, 0));
            when(player.isValid()).thenReturn(true);
            when(player.isOnline()).thenReturn(true);
            when(player.getGameMode()).thenReturn(GameMode.SURVIVAL);
            when(session.id()).thenReturn(UUID.randomUUID());
            when(session.players()).thenReturn(List.of(player));
            when(session.mobs()).thenReturn(List.of());
            when(session.scheduler()).thenReturn(new TickScheduler() {
                public void runLater(int ticks, Runnable task) { pending.add(task); }
                public long currentTick() { return 0; }
            });
            caster = new ActiveMob(entity, new MobTemplate("test", "minecraft:zombie", "", 0, 0, 0, 0, 0,
                    Map.of(), List.of(), List.of(), List.of(), false, "RED", null, List.of(), false), session);
        }
        AbilityContext context(Ability ability, Map<String, Object> params) {
            return new AbilityContext(caster, List.of(player, outsider), new ParamValues(params, ability.params()), session, null);
        }
        <T extends Entity> T spawned(Class<T> type) {
            T spawned = mock(type);
            when(spawned.getPersistentDataContainer()).thenReturn(mock(PersistentDataContainer.class));
            when(spawned.isValid()).thenReturn(true);
            when(spawned.getUniqueId()).thenReturn(UUID.randomUUID());
            when(spawned.getLocation()).thenReturn(new Location(world, 2, 64, 0));
            when(world.spawn(any(Location.class), eq(type), org.mockito.ArgumentMatchers.<Consumer<T>>any()))
                    .thenAnswer(inv -> { Consumer<T> consumer = inv.getArgument(2); consumer.accept(spawned); return spawned; });
            return spawned;
        }
    }
    @Test void shockwaveRejectsOutsidersAndSpectators() {
        var f = new Fixture();
        var wave = new WitherShockwaveAbility();
        wave.execute(f.context(wave, Map.of()));
        verify(f.player).damage(6.0, f.entity);
        verify(f.player).setVelocity(new Vector(1.2, 0.3, 0));
        verify(f.outsider, never()).damage(anyDouble(), any(Entity.class));
        verify(f.outsider, never()).setVelocity(any());
        clearInvocations(f.player);
        when(f.player.getGameMode()).thenReturn(GameMode.SPECTATOR);
        wave.execute(f.context(wave, Map.of()));
        verify(f.player, never()).setVelocity(any());
    }
    @Test void skullsRespectCountChargeTerrainSafetyAndExpiry() {
        var f = new Fixture();
        var skull = mock(WitherSkull.class);
        when(skull.getPersistentDataContainer()).thenReturn(mock(PersistentDataContainer.class));
        when(f.entity.launchProjectile(eq(WitherSkull.class), any(Vector.class))).thenReturn(skull);
        var ability = new WitherSkullsAbility();
        ability.execute(f.context(ability, Map.of("count", 3, "blue", true)));
        verify(f.entity, times(3)).launchProjectile(eq(WitherSkull.class), any(Vector.class));
        verify(skull, times(3)).setCharged(true);
        verify(skull, times(3)).setYield(0);
        verify(skull, times(3)).setIsIncendiary(false);
        var hit = mock(ProjectileHitEvent.class);
        when(hit.getEntity()).thenReturn(skull);
        when(hit.getHitEntity()).thenReturn(f.outsider);
        ability.hit(hit);
        verify(hit).setCancelled(true);
        verify(f.outsider, never()).damage(anyDouble(), any(Entity.class));
        f.pending.forEach(Runnable::run);
        verify(skull, times(4)).remove();
    }
    @Test void fangsUseCasterAndConfiguredDamageAndBlockOutsiders() {
        var f = new Fixture();
        var ground = mock(org.bukkit.block.Block.class);
        var air = mock(org.bukkit.block.Block.class);
        when(f.world.isChunkLoaded(anyInt(), anyInt())).thenReturn(true);
        when(f.world.getBlockAt(any(Location.class))).thenReturn(ground);
        when(ground.isSolid()).thenReturn(true);
        when(ground.getRelative(0, 1, 0)).thenReturn(air);
        when(air.isPassable()).thenReturn(true);
        when(ground.getY()).thenReturn(63);
        var fang = f.spawned(EvokerFangs.class);
        var ability = new EvokerFangsAbility();
        ability.execute(f.context(ability, Map.of("count", 1, "damage", 9.0)));
        verify(fang).setOwner(f.entity);
        var event = mock(EntityDamageByEntityEvent.class);
        when(event.getDamager()).thenReturn(fang);
        when(event.getEntity()).thenReturn(f.player);
        ability.damage(event);
        verify(event).setDamage(9.0);
        when(event.getEntity()).thenReturn(f.outsider);
        ability.damage(event);
        verify(event).setCancelled(true);
    }
    @Test void fangsOutsideRoomNeverReadBlocks() {
        var f = new Fixture();
        when(f.world.getName()).thenReturn("dungeon");
        when(f.world.isChunkLoaded(anyInt(), anyInt())).thenReturn(true);
        when(f.session.currentRoomRegion()).thenReturn(dev.dasan.customdungeons.model.Region.of("dungeon",
                new dev.dasan.customdungeons.model.BlockPos(0, 60, 0),
                new dev.dasan.customdungeons.model.BlockPos(0, 70, 0)));
        when(f.world.getBlockAt(any(Location.class))).thenThrow(new AssertionError("Unexpected block read"));
        var ability = new EvokerFangsAbility();
        ability.execute(f.context(ability, Map.of("count", 1)));
        verify(f.world, never()).getBlockAt(any(Location.class));
    }
    @Test void fangsInUnloadedChunksNeverReadBlocks() {
        var f = new Fixture();
        when(f.entity.getLocation()).thenReturn(new Location(f.world, -16, 64, -16));
        when(f.world.getBlockAt(any(Location.class))).thenThrow(new AssertionError("Unexpected block read"));
        var ability = new EvokerFangsAbility();
        ability.execute(f.context(ability, Map.of("count", 1)));
        verify(f.world).isChunkLoaded(-1, -1);
        verify(f.world, never()).getBlockAt(any(Location.class));
    }
    @Test void breathCloudIsVisualAndHitsOnlyAtReapplicationIntervals() {
        var f = new Fixture();
        var ball = mock(DragonFireball.class);
        when(ball.getPersistentDataContainer()).thenReturn(mock(PersistentDataContainer.class));
        when(ball.getLocation()).thenReturn(new Location(f.world, 2, 64, 0));
        when(f.entity.launchProjectile(eq(DragonFireball.class), any(Vector.class))).thenReturn(ball);
        var cloud = f.spawned(AreaEffectCloud.class);
        when(cloud.getRadius()).thenReturn(3f);
        when(cloud.getReapplicationDelay()).thenReturn(17);
        var queue = new TreeMap<Long, List<Runnable>>();
        long[] tick = {0};
        when(f.session.scheduler()).thenReturn(new TickScheduler() {
            public long currentTick() { return tick[0]; }
            public void runLater(int delay, Runnable task) {
                queue.computeIfAbsent(tick[0] + delay, ignored -> new ArrayList<>()).add(task);
            }
        });
        var ability = new DragonBreathAbility();
        ability.execute(f.context(ability, Map.of("durationTicks", 35, "damagePerHit", 4.0)));
        var event = mock(ProjectileHitEvent.class);
        when(event.getEntity()).thenReturn(ball);
        ability.hit(event);
        verify(cloud).setParticle(Particle.DRAGON_BREATH);
        verify(cloud).setBasePotionType(null);
        verify(cloud).clearCustomEffects();
        verify(cloud, never()).addCustomEffect(any(), anyBoolean());
        verify(f.player).damage(4.0, f.entity);
        clearInvocations(f.player);
        for (int t = 1; t <= 35; t++) {
            tick[0] = t;
            var tasks = queue.remove(tick[0]);
            if (tasks != null) tasks.forEach(Runnable::run);
            verify(f.player, times(t == 17 || t == 34 ? 1 : 0)).damage(4.0, f.entity);
            clearInvocations(f.player);
        }
        verify(cloud).remove();
        verify(f.outsider, never()).damage(anyDouble(), any(Entity.class));
    }
    @Test void vexesTargetParticipantsBlockOutsidersAndExpire() {
        var f = new Fixture();
        var vex = f.spawned(Vex.class);
        var ability = new SummonVexesAbility();
        ability.execute(f.context(ability, Map.of("count", 1, "lifetimeSeconds", 0.05)));
        verify(vex).setOwner(f.entity);
        verify(vex).setTarget(f.player);
        verify(vex).setLimitedLifetimeTicks(1);
        var event = mock(EntityDamageByEntityEvent.class);
        when(event.getDamager()).thenReturn(vex);
        when(event.getEntity()).thenReturn(f.outsider);
        ability.damage(event);
        verify(event).setCancelled(true);
        var target = mock(EntityTargetLivingEntityEvent.class);
        when(target.getEntity()).thenReturn(vex);
        when(target.getTarget()).thenReturn(f.outsider);
        ability.target(target);
        verify(target).setCancelled(true);
        f.pending.removeFirst().run();
        verify(vex).remove();
    }
    @Test void nativeExplosionsAndDragonCloudsAreCancelled() {
        var skull = mock(WitherSkull.class);
        var data = mock(PersistentDataContainer.class);
        when(skull.getPersistentDataContainer()).thenReturn(data);
        when(data.has(Effects.PROJECTILE_KEY, org.bukkit.persistence.PersistentDataType.BYTE)).thenReturn(true);
        var prime = mock(ExplosionPrimeEvent.class);
        when(prime.getEntity()).thenReturn(skull);
        new WitherSkullsAbility().prime(prime);
        verify(prime).setCancelled(true);
        var explosion = mock(EntityExplodeEvent.class);
        var blocks = new ArrayList<org.bukkit.block.Block>();
        blocks.add(mock(org.bukkit.block.Block.class));
        when(explosion.getEntity()).thenReturn(skull);
        when(explosion.blockList()).thenReturn(blocks);
        new WitherSkullsAbility().explode(explosion);
        verify(explosion).setCancelled(true);
        assertTrue(blocks.isEmpty());
        var ball = mock(DragonFireball.class);
        when(ball.getPersistentDataContainer()).thenReturn(data);
        var event = mock(com.destroystokyo.paper.event.entity.EnderDragonFireballHitEvent.class);
        when(event.getEntity()).thenReturn(ball);
        new DragonBreathAbility().nativeCloud(event);
        verify(event).setCancelled(true);
    }
}
