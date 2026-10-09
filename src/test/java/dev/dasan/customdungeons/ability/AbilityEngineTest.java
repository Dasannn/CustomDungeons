package dev.dasan.customdungeons.ability;

import dev.dasan.customdungeons.mob.MobHost;
import dev.dasan.customdungeons.model.*;
import dev.dasan.customdungeons.runtime.*;
import java.util.*;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import dev.dasan.customdungeons.config.PluginConfig;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SuppressWarnings("deprecation")
class AbilityEngineTest {
    static { dev.dasan.customdungeons.ability.impl.PaperApiTestBootstrap.initialize(); }
    @AfterEach void resetEffects() {
        Effects.configure(() -> new PluginConfig.PerformanceLimits(50, 1, 48));
    }
    static class Clock implements TickScheduler {
        long now; final NavigableMap<Long, List<Runnable>> tasks = new TreeMap<>();
        public long currentTick() { return now; }
        public void runLater(int ticks, Runnable r) { tasks.computeIfAbsent(now + ticks, k -> new ArrayList<>()).add(r); }
        void advance(long tick) {
            while (!tasks.isEmpty() && tasks.firstKey() <= tick) {
                now = tasks.firstKey(); var due = tasks.pollFirstEntry().getValue(); due.forEach(Runnable::run);
            }
            now = tick;
        }
    }
    static class Fixture {
        final World world = mock(World.class);
        final Mob entity = mock(Mob.class);
        final Player player = mock(Player.class);
        final MobHost session = mock(MobHost.class);
        final Clock clock = new Clock();
        final AbilityRegistry registry = new AbilityRegistry();
        final List<String> calls = new ArrayList<>();
        final ActiveMob mob;
        Fixture(List<AbilityInstance> abilities, List<ComboDef> combos) {
            when(entity.getUniqueId()).thenReturn(new UUID(0, 0));
            when(entity.getPersistentDataContainer()).thenReturn(mock(org.bukkit.persistence.PersistentDataContainer.class));
            when(entity.getWorld()).thenReturn(world);
            when(entity.getLocation()).thenReturn(new Location(world, 0, 64, 0));
            when(entity.isValid()).thenReturn(true);
            when(entity.getHealth()).thenReturn(40.0);
            var max=mock(org.bukkit.attribute.AttributeInstance.class);
            when(max.getValue()).thenReturn(100d);when(entity.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH)).thenReturn(max);
            when(player.getWorld()).thenReturn(world);
            when(player.getLocation()).thenReturn(new Location(world, 2, 64, 0));
            when(player.isValid()).thenReturn(true);
            when(player.isOnline()).thenReturn(true);
            when(player.getGameMode()).thenReturn(GameMode.SURVIVAL);
            when(session.id()).thenReturn(new UUID(0, 1));
            when(session.players()).thenReturn(List.of(player));
            when(session.audience(any(Location.class))).thenAnswer(call -> session.players());
            when(session.scheduler()).thenReturn(clock);
            mob = new ActiveMob(entity, new MobTemplate("test", "minecraft:zombie", "", 100, 0, 0, 0, 0,
                Map.of(), List.of(), abilities, combos, false, "RED", null, List.of(), false), session);
            register("test"); register("second");
        }
        void register(String id) {
            registry.register(new Ability() {
                public String id() { return id; }
                public Material icon() { return Material.STONE; }
                public List<ParamSpec> params() { return List.of(); }
                public void execute(AbilityContext ctx) { calls.add(id + "@" + clock.now); }
            });
        }
        AbilityEngine engine() { return new AbilityEngine(registry, null, new Random(1)); }
        void fire(AbilityEngine e, Trigger t, long tick) { clock.advance(tick); e.fire(t, mob, null, tick); }
    }
    static AbilityInstance instance(Trigger t, double value, int cooldown, double chance, int warning) {
        return new AbilityInstance("test", t, value, TargetMode.ALL_IN_RADIUS, 10, cooldown, chance, warning, Map.of());
    }
    @Test void healthAboveThresholdWaitsAndIncomingDamageCrossesThreshold() {
        var f = new Fixture(List.of(instance(Trigger.HEALTH_BELOW, 50, 0, 1, 0)), List.of());
        var e = f.engine(); when(f.entity.getHealth()).thenReturn(60.0);
        f.fire(e, Trigger.HEALTH_BELOW, 0); assertTrue(f.calls.isEmpty());
        var damage = mock(org.bukkit.event.entity.EntityDamageEvent.class);
        when(damage.getEntity()).thenReturn(f.entity); when(damage.getFinalDamage()).thenReturn(15.0);
        when(damage.getCause()).thenReturn(org.bukkit.event.entity.EntityDamageEvent.DamageCause.CUSTOM);
        e.fire(Trigger.HEALTH_BELOW, f.mob, damage, 1); assertEquals(1, f.calls.size());
    }
    @Test void separateInstancesHaveSeparateCooldowns() {
        var f = new Fixture(List.of(instance(Trigger.ON_HIT, 0, 20, 1, 0),
            instance(Trigger.ON_HIT, 0, 20, 1, 0)), List.of());
        f.fire(f.engine(), Trigger.ON_HIT, 0); assertEquals(2, f.calls.size());
    }
    @Test void periodicChecksAreStaggeredAndNotRepeatedAtSameTick() {
        var first = new Fixture(List.of(instance(Trigger.EVERY_X_SECONDS, 1, 0, 1, 0)), List.of());
        var second = new Fixture(List.of(instance(Trigger.EVERY_X_SECONDS, 1, 0, 1, 0)), List.of());
        when(second.entity.getUniqueId()).thenReturn(new UUID(0, 1));
        var e = first.engine();
        e.tick(List.of(first.mob, second.mob), 0); e.tick(List.of(first.mob, second.mob), 0);
        assertEquals(1, first.calls.size());
        first.clock.advance(1); e.tick(List.of(first.mob, second.mob), 1);
        assertEquals(2, first.calls.size());
    }
    @Test void coreRegistryHasThreeUniqueAbilitiesWithBoundedDefaults() {
        var r = new AbilityRegistry(); dev.dasan.customdungeons.ability.impl.CoreAbilities.register(r);
        assertEquals(Set.of("lightning", "on_hit_effect", "summon_minions"),
            r.all().stream().map(Ability::id).collect(java.util.stream.Collectors.toSet()));
        for (var a : r.all()) for (var spec : a.params()) {
            if (spec.defaultValue() instanceof Number n)
                assertTrue(n.doubleValue() >= spec.min() && n.doubleValue() <= spec.max());
        }
    }
    @Test void cooldownBlocksSecondExecution() {
        var f = new Fixture(List.of(instance(Trigger.ON_HIT, 0, 20, 1, 0)), List.of()); var e = f.engine();
        f.fire(e, Trigger.ON_HIT, 0); f.fire(e, Trigger.ON_HIT, 1); f.fire(e, Trigger.ON_HIT, 20);
        assertEquals(List.of("test@0", "test@20"), f.calls);
    }
    @Test void chanceZeroNeverFires() {
        var f = new Fixture(List.of(instance(Trigger.ON_HIT, 0, 0, 0, 0)), List.of());
        f.fire(f.engine(), Trigger.ON_HIT, 0); assertTrue(f.calls.isEmpty());
    }
    @Test void healthBelowFiresOnce() {
        var f = new Fixture(List.of(instance(Trigger.HEALTH_BELOW, 50, 0, 1, 0)), List.of()); var e = f.engine();
        f.fire(e, Trigger.HEALTH_BELOW, 0); f.fire(e, Trigger.HEALTH_BELOW, 10);
        assertEquals(1, f.calls.size());
    }
    @Test void telegraphDelaysExecutionAndCancelsOnDeath() {
        var f = new Fixture(List.of(instance(Trigger.ON_HIT, 0, 0, 1, 5)), List.of()); var e = f.engine();
        f.fire(e, Trigger.ON_HIT, 0); assertTrue(f.calls.isEmpty());
        f.clock.advance(5); assertEquals(List.of("test@5"), f.calls);
        f.fire(e, Trigger.ON_HIT, 6); when(f.entity.isDead()).thenReturn(true);
        f.clock.advance(11); assertEquals(1, f.calls.size());
    }
    @Test void halfDensityKeepsEveryTelegraphPointVisible() {
        Effects.configure(() -> new PluginConfig.PerformanceLimits(50, 0.5, 48));
        var f = new Fixture(List.of(instance(Trigger.ON_HIT, 0, 0, 1, 20)), List.of());
        f.fire(f.engine(), Trigger.ON_HIT, 0);
        verify(f.player, times(24)).spawnParticle(eq(Particle.CRIT), any(Location.class),
                eq(1), eq(0.0), eq(0.0), eq(0.0), eq(0.0));
    }
    @Test void telegraphStopsRefreshesAndPendingExecutionWhenCasterDies() {
        assertTelegraphCancelled(true);
    }
    @Test void telegraphStopsRefreshesAndPendingExecutionWhenCasterBecomesInvalid() {
        assertTelegraphCancelled(false);
    }
    private void assertTelegraphCancelled(boolean dead) {
        var f = new Fixture(List.of(instance(Trigger.ON_HIT, 0, 0, 1, 20)), List.of());
        var engine = f.engine();
        f.fire(engine, Trigger.ON_HIT, 0);
        verify(f.player, times(24)).spawnParticle(eq(Particle.CRIT), any(Location.class),
                eq(1), eq(0.0), eq(0.0), eq(0.0), eq(0.0));
        clearInvocations(f.player);
        if (dead) when(f.entity.isDead()).thenReturn(true);
        else when(f.entity.isValid()).thenReturn(false);
        f.clock.advance(5);
        verify(f.player, never()).spawnParticle(any(Particle.class), any(Location.class),
                anyInt(), anyDouble(), anyDouble(), anyDouble(), anyDouble());
        assertTrue(f.clock.tasks.isEmpty(), "Cancellation must stop queued refreshes and execution");
        when(f.entity.isDead()).thenReturn(false);
        when(f.entity.isValid()).thenReturn(true);
        f.clock.advance(20);
        assertTrue(f.calls.isEmpty(), "A cancelled warning cannot execute if the caster becomes valid again");
        f.fire(engine, Trigger.ON_HIT, 21);
        f.clock.advance(41);
        assertEquals(List.of("test@41"), f.calls, "Cancellation must clear the pending ability state");
    }
    @Test void liveCasterCompletesTelegraphAtExactDeadlineBetweenRefreshes() {
        var f = new Fixture(List.of(instance(Trigger.ON_HIT, 0, 0, 1, 12)), List.of());
        f.fire(f.engine(), Trigger.ON_HIT, 0);
        f.clock.advance(11);
        assertTrue(f.calls.isEmpty());
        verify(f.player, times(72)).spawnParticle(eq(Particle.CRIT), any(Location.class),
                eq(1), eq(0.0), eq(0.0), eq(0.0), eq(0.0));
        f.clock.advance(12);
        assertEquals(List.of("test@12"), f.calls);
        assertTrue(f.clock.tasks.isEmpty());
    }
    @Test void pendingTelegraphCannotStackAndTargetsAreRevalidated() {
        var f = new Fixture(List.of(instance(Trigger.ON_HIT, 0, 0, 1, 5)), List.of()); var e = f.engine();
        f.fire(e, Trigger.ON_HIT, 0); f.fire(e, Trigger.ON_HIT, 1);
        when(f.player.getGameMode()).thenReturn(GameMode.SPECTATOR); f.clock.advance(5);
        assertTrue(f.calls.isEmpty());
    }
    @Test void everySecondsAndRangeUseTheirCadence() {
        var f = new Fixture(List.of(instance(Trigger.EVERY_X_SECONDS, 1, 0, 1, 0),
            instance(Trigger.PLAYER_IN_RANGE, 0, 0, 1, 0)), List.of()); var e = f.engine();
        for(int i=0;i<40;i++) { f.clock.advance(i); e.tick(List.of(f.mob), i); }
        assertEquals(6, f.calls.size());
    }
    @Test void spawnExecutesOnceWithoutTargets() {
        var f = new Fixture(List.of(instance(Trigger.ON_SPAWN, 0, 0, 1, 0)), List.of()); var e = f.engine();
        when(f.session.players()).thenReturn(List.of());
        f.fire(e, Trigger.ON_SPAWN, 0); f.fire(e, Trigger.ON_SPAWN, 1);
        assertEquals(List.of("test@0"), f.calls);
    }
    @Test void deathDoesNotRepeatAndExecutesOnDeadCaster() {
        var f = new Fixture(List.of(instance(Trigger.ON_DEATH, 0, 0, 1, 0)), List.of()); var e = f.engine();
        when(f.entity.isDead()).thenReturn(true); f.fire(e, Trigger.ON_DEATH, 0); f.fire(e, Trigger.ON_DEATH, 1);
        assertEquals(1, f.calls.size());
    }
    @Test void damageBurstUsesConfiguredIntelligenceWindow() {
        var fixture=new Fixture(List.of(instance(Trigger.DAMAGE_BURST,25,0,1,0)),List.of());
        try(var service=new dev.dasan.customdungeons.intelligence.IntelligenceService(mock(dev.dasan.customdungeons.mob.MobsPlatform.class),dev.dasan.customdungeons.intelligence.IntelligenceRules.defaults())) {
            dev.dasan.customdungeons.intelligence.IntelligenceService.phase(fixture.mob,dev.dasan.customdungeons.intelligence.IntelligenceDef.level(2),0);
            var brain=dev.dasan.customdungeons.intelligence.IntelligenceService.brain(fixture.mob);
            brain.memory().record(UUID.randomUUID(),"damage","x",30,0);
            var engine=fixture.engine();fixture.fire(engine,Trigger.DAMAGE_BURST,150);assertEquals(1,fixture.calls.size());
            brain.changeLevel(brain.definition().withAdvanced("window",1),160);
            fixture.fire(engine,Trigger.DAMAGE_BURST,160);assertEquals(1,fixture.calls.size());
        }
    }
}
