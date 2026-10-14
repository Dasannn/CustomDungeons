package dev.dasan.customdungeons.ability;

import dev.dasan.customdungeons.intelligence.*;
import dev.dasan.customdungeons.mob.MobsPlatform;
import dev.dasan.customdungeons.model.*;
import dev.dasan.customdungeons.runtime.ActiveMob;
import java.util.*;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Acceptance of RF-IA-10 through the real engine, with a simulated host clock. */
class SituationalAbilitySelectionTest {
    static { dev.dasan.customdungeons.ability.impl.PaperApiTestBootstrap.initialize(); }
    private static org.bukkit.inventory.ItemStack food() {
        // The shared test registry has no vanilla food metadata; model it locally.
        var type = mock(Material.class); when(type.isEdible()).thenReturn(true);
        var item = mock(org.bukkit.inventory.ItemStack.class); when(item.getType()).thenReturn(type);
        return item;
    }
    static class Fixture extends AbilityEngineTest.Fixture {
        final ActiveMob active;
        Fixture(int level, AbilityInstance... definitions) {
            super(List.of(definitions), List.of());
            active = new ActiveMob(entity, mob.template().withIntelligence(IntelligenceDef.level(level)), session);
            when(player.getUniqueId()).thenReturn(new UUID(0, 2));
            when(player.getHealth()).thenReturn(20d);
            when(player.isOnGround()).thenReturn(true);
            when(entity.getTarget()).thenReturn(player);
            Arrays.stream(definitions).map(AbilityInstance::abilityId).distinct().forEach(this::scoredAbility);
        }
        void scoredAbility(String id) {
            registry.register(new Ability() {
                public String id() { return id; }
                public Material icon() { return Material.STONE; }
                public List<ParamSpec> params() {
                    var specs = new ArrayList<ParamSpec>();
                    if (Set.of("creeper_blast", "silence", "healer", "poison_pools").contains(id))
                        specs.add(new ParamSpec("radius", ParamType.DOUBLE, 4d, 0, 32));
                    if (id.equals("sweep")) {
                        specs.add(new ParamSpec("reach", ParamType.DOUBLE, 5d, 2, 10));
                        specs.add(new ParamSpec("angle", ParamType.DOUBLE, 120d, 60, 240));
                    }
                    if (id.equals("charged_beam")) {
                        specs.add(new ParamSpec("length", ParamType.DOUBLE, 16d, 4, 32));
                        specs.add(new ParamSpec("width", ParamType.DOUBLE, 1d, .5, 3));
                    }
                    if (id.equals("falling_pillars")) specs.add(new ParamSpec("count", ParamType.INT, 3, 1, 8));
                    specs.add(new ParamSpec("damage", ParamType.DOUBLE, id.equals("silence") || id.equals("healer") ? 0d : 6d, 0, 100));
                    return specs;
                }
                public void execute(AbilityContext ctx) { calls.add(id + "@" + clock.now); }
            });
        }
        void step(AbilityEngine engine, int tick) { clock.advance(tick); engine.tick(List.of(active), tick); }
        Player companion(long id, double x, double z) {
            var p = mock(Player.class);
            when(p.getUniqueId()).thenReturn(new UUID(0, id));
            when(p.getWorld()).thenReturn(world); when(p.getLocation()).thenReturn(new Location(world, x, 64, z));
            when(p.isOnline()).thenReturn(true); when(p.isValid()).thenReturn(true);
            when(p.getGameMode()).thenReturn(GameMode.SURVIVAL); when(p.getHealth()).thenReturn(20d);
            when(p.isOnGround()).thenReturn(true);
            return p;
        }
    }
    private static AbilityInstance periodic(String id, double range) {
        return new AbilityInstance(id, Trigger.EVERY_X_SECONDS, .05, TargetMode.NEAREST, range, 40, 1, 0, Map.of());
    }

    private static Object diagnosticField(Object owner,String name) throws ReflectiveOperationException {
        var field=owner.getClass().getDeclaredField(name);field.setAccessible(true);return field.get(owner);
    }
    @Test void debugChoiceKeepsTheActualScoreBeforeStartedChangesRecentUsage() throws ReflectiveOperationException {
        var f=new Fixture(5,periodic("sonic_boom",16));
        when(f.player.getLocation()).thenReturn(new Location(f.world,13,64,0));
        try(var debug=mockStatic(dev.dasan.customdungeons.mob.DebugView.class)) {
            debug.when(dev.dasan.customdungeons.mob.DebugView::active).thenReturn(true);
            var engine=f.engine();f.step(engine,0);
            var state=((Map<?,?>)diagnosticField(engine,"states")).get(f.active);
            var selection=diagnosticField(state,"selection");var trace=diagnosticField(selection,"debugChoice");
            assertNotNull(trace);
            var option=(AbilityScorer.Option)diagnosticField(trace,"option");
            assertEquals("sonic_boom#0",option.key());assertEquals(13,option.situation().distance());
            assertEquals(0,option.recentUses());assertEquals(39.5,(double)diagnosticField(trace,"score"),1e-6);
            assertEquals(0L,diagnosticField(trace,"tick"));
            f.step(engine,1);assertSame(trace,diagnosticField(selection,"debugChoice"));
            debug.when(dev.dasan.customdungeons.mob.DebugView::active).thenReturn(false);
            f.step(engine,40);assertNull(diagnosticField(selection,"debugChoice"));
        }
    }
    @Test void noDebugAudienceAllocatesNoChoiceSnapshot() throws ReflectiveOperationException {
        var f=new Fixture(5,periodic("sonic_boom",16));
        try(var debug=mockStatic(dev.dasan.customdungeons.mob.DebugView.class)) {
            var engine=f.engine();f.step(engine,0);
            var state=((Map<?,?>)diagnosticField(engine,"states")).get(f.active);
            assertNull(diagnosticField(diagnosticField(state,"selection"),"debugChoice"));
            assertEquals(1,f.calls.size());
        }
    }
    @Test void shieldBypassingRoarIsUsefulAgainstShieldButDoesNotDisplaceDamageOtherwise() {
        for(boolean shield:List.of(false,true)) {
            var f=new Fixture(5,periodic("plain",20));
            f.active.abilities().addLast(periodic("roar_knockback",20));
            f.registry.register(new Ability() {
                public String id(){return "roar_knockback";}
                public Material icon(){return Material.GOAT_HORN;}
                public List<ParamSpec> params(){return List.of(new ParamSpec("knockback",ParamType.DOUBLE,1.5,0,5),new ParamSpec("up",ParamType.DOUBLE,.4,0,3));}
                public void execute(AbilityContext ctx){f.calls.add(id()+"@"+f.clock.now);}
            });
            when(f.player.isBlocking()).thenReturn(shield);
            f.step(f.engine(),0);
            assertEquals(List.of((shield?"roar_knockback":"plain")+"@0"),f.calls);
        }
    }
    @Test void farTargetUsesOnlyAReadyLongRangeAbility() {
        var f = new Fixture(5, periodic("grab_throw", 4), periodic("sonic_boom", 16));
        when(f.player.getLocation()).thenReturn(new Location(f.world, 13, 64, 0));
        f.step(f.engine(), 0);
        assertEquals(List.of("sonic_boom@0"), f.calls);
        assertTrue(f.active.ready("grab_throw#0", 0));
    }
    @Test void declaredReachCapsTheSelectorAndDoesNotSpendTheBlockedCooldown() {
        var f = new Fixture(5, periodic("sonic_boom", 16));
        f.active.abilities().addFirst(periodic("short", 16));
        var shortAbility = mock(Ability.class);
        when(shortAbility.id()).thenReturn("short");
        when(shortAbility.params()).thenReturn(List.of(new ParamSpec("reach", ParamType.DOUBLE, 4d, 1, 8)));
        f.registry.register(shortAbility);
        when(f.player.getLocation()).thenReturn(new Location(f.world, 13, 64, 0));
        f.step(f.engine(), 0);
        assertEquals(List.of("sonic_boom@0"), f.calls);
        assertTrue(f.active.ready("short#0", 0));
        verify(shortAbility, never()).execute(any());
    }
    @Test void cooldownAndZeroProbabilityExcludeOtherwiseHighScoringSkills() {
        var impossible = new AbilityInstance("silence", Trigger.EVERY_X_SECONDS, .05, TargetMode.NEAREST, 16, 40, 0, 0, Map.of());
        var f = new Fixture(5, impossible, periodic("sonic_boom", 16), periodic("single", 16));
        when(f.player.isBlocking()).thenReturn(true);
        f.active.cooldown("sonic_boom#1", 100);
        f.step(f.engine(), 0);
        assertEquals(List.of("single@0"), f.calls);
        assertFalse(f.active.ready("sonic_boom#1", 0));
    }
    @Test void cancelledWarningDoesNotConsumeCooldownOrRecentUsage() {
        var warning = new AbilityInstance("single", Trigger.EVERY_X_SECONDS, .05, TargetMode.NEAREST, 4, 40, 1, 5, Map.of());
        var f = new Fixture(5, warning); var engine = f.engine();
        f.step(engine, 0);
        assertTrue(AbilityCasts.warning(f.active)); assertTrue(f.active.ready("single#0", 0));
        when(f.player.getLocation()).thenReturn(new Location(f.world, 13, 64, 0));
        f.step(engine, 5);
        assertTrue(f.calls.isEmpty()); assertTrue(f.active.ready("single#0", 5));
        when(f.player.getLocation()).thenReturn(new Location(f.world, 2, 64, 0));
        f.step(engine, 10); f.step(engine, 15);
        assertEquals(List.of("single@15"), f.calls);
        assertFalse(f.active.ready("single#0", 15));
    }
    @Test void occupiedControlTargetIsNotScoredAndItsPendingCastRemainsReady() {
        var f = new Fixture(5, periodic("grab_throw", 4), periodic("single", 16));
        try (var controls = mockStatic(dev.dasan.customdungeons.ability.control.ControlService.class)) {
            controls.when(() -> dev.dasan.customdungeons.ability.control.ControlService.targetAvailable("grab_throw", f.player)).thenReturn(false);
            f.step(f.engine(), 0);
            assertEquals(List.of("single@0"), f.calls);
            assertTrue(f.active.ready("grab_throw#0", 0));
        }
    }
    @Test void duplicateInstancesShareRecentUsageAndGiveAnotherSkillATurn() {
        var f = new Fixture(5, periodic("single", 16), periodic("single", 16), periodic("other", 16));
        var engine = f.engine(); f.step(engine, 0); f.step(engine, 10);
        assertEquals(List.of("single@0", "other@10"), f.calls);
        assertTrue(f.active.ready("single#1", 10));
    }
    @ParameterizedTest @ValueSource(strings = {"creeper_blast", "falling_pillars"})
    void aGroupUsesTheAreaAbilityEvenWhenItIsLaterInTheList(String id) {
        var f = new Fixture(5, periodic("single", 16), periodic(id, 16));
        var group = List.of(f.player, f.companion(3, 2, 1), f.companion(4, 3, 1));
        when(f.session.players()).thenReturn(group);
        f.step(f.engine(), 0);
        assertEquals(List.of(id + "@0"), f.calls);
    }
    @Test void playersOutsideTheBeamOrBehindTheSweepDoNotBoostTheirAreaScore() {
        for (String id : List.of("charged_beam", "sweep")) {
            var f = new Fixture(5, periodic(id, 16), periodic("creeper_blast", 16));
            var group = List.of(f.player, f.companion(3, 0, -3), f.companion(4, 1, -3),
                    f.companion(5, 8, 4), f.companion(6, 9, 4));
            when(f.session.players()).thenReturn(group);
            f.step(f.engine(), 0);
            assertEquals(List.of("creeper_blast@0"), f.calls, id);
        }
    }
    @Test void aSelfCenteredAreaCannotReachAPlayerOutsideItsActualRadius() {
        var f = new Fixture(5, periodic("creeper_blast", 16), periodic("single", 16));
        when(f.player.getLocation()).thenReturn(new Location(f.world, 13, 64, 0));
        f.step(f.engine(), 0);
        assertEquals(List.of("single@0"), f.calls);
        assertTrue(f.active.ready("creeper_blast#0", 0));
    }
    @Test void playersOutsideTheHostAreaDoNotInflateAnAreaScore() {
        var f = new Fixture(5, periodic("poison_pools", 16), periodic("sonic_boom", 16));
        when(f.world.getName()).thenReturn("test");
        when(f.session.area()).thenReturn(new dev.dasan.customdungeons.mob.MobArea("test",
                new org.bukkit.util.BoundingBox(-4, 60, -4, 4, 70, 4)));
        when(f.player.getLocation()).thenReturn(new Location(f.world, 3, 64, 0));
        when(f.player.isBlocking()).thenReturn(true);
        var group = List.of(f.player, f.companion(3, 5, 0), f.companion(4, 5, 1), f.companion(5, 5, -1));
        when(f.session.players()).thenReturn(group);
        f.step(f.engine(), 0);
        assertEquals(List.of("sonic_boom@0"), f.calls);
    }
    @Test void currentlyEatingTargetUsesSilenceWithoutWaitingForAHealingEvent() {
        var f = new Fixture(5, periodic("single", 16), periodic("silence", 16));
        when(f.player.isHandRaised()).thenReturn(true);
        var consumed = food(); when(f.player.getActiveItem()).thenReturn(consumed);
        f.step(f.engine(), 0);
        assertEquals(List.of("silence@0"), f.calls);
    }
    @Test void recentHealingMemoryAlsoFavorsSilenceAndThenExpires() {
        var f = new Fixture(5, periodic("single", 16), periodic("silence", 16));
        try (var bukkit = mockStatic(Bukkit.class);
             var service = new IntelligenceService(mock(MobsPlatform.class), IntelligenceRules.defaults())) {
            IntelligenceService.track(f.active);
            IntelligenceService.brain(f.active).memory().record(f.player.getUniqueId(), "heal", "", 0, 0);
            var engine = f.engine(); f.step(engine, 0);
            assertEquals(List.of("silence@0"), f.calls);
            f.step(engine, 240);
            assertEquals(List.of("silence@0", "single@240"), f.calls);
        }
    }
    @Test void anOldPeriodicPendingCastCannotPreemptAConfiguredEvent() {
        var warning = new AbilityInstance("warning", Trigger.ON_SPAWN, 0, TargetMode.NEAREST, 16, 0, 1, 20, Map.of());
        var event = new AbilityInstance("event", Trigger.ON_HIT, 0, TargetMode.NEAREST, 16, 100, 1, 0, Map.of());
        var f = new Fixture(5, warning, periodic("single", 16), event);
        var engine = f.engine();
        engine.fire(Trigger.ON_SPAWN, f.active, null, 0);
        f.step(engine, 1);
        f.clock.advance(2); engine.fire(Trigger.ON_HIT, f.active, null, 2);
        f.step(engine, 20);
        assertEquals(List.of("warning@20", "event@20"), f.calls);
        f.step(engine, 30);
        assertEquals(List.of("warning@20", "event@20", "single@30"), f.calls);
    }
    @Test void aHealthTriggerDetectedInThisEvaluationPreemptsPeriodics() {
        var health = new AbilityInstance("heal_now", Trigger.HEALTH_BELOW, 50, TargetMode.NEAREST, 16, 100, 1, 0, Map.of());
        var f = new Fixture(5, periodic("single", 16), health);
        f.step(f.engine(), 0);
        assertEquals(List.of("heal_now@0"), f.calls);
    }
    @Test void tacticalTicksAreStaggeredAndRepeatedEvaluationsNeverBurst() {
        var f = new Fixture(5, periodic("single", 16), periodic("other", 16));
        when(f.entity.getUniqueId()).thenReturn(new UUID(0, 7));
        var engine = f.engine();
        for (int tick = 0; tick < 20; tick++) { f.step(engine, tick); f.step(engine, tick); }
        assertEquals(List.of("single@7", "other@17"), f.calls);
    }
    @Test void aPhaseChangingTheLevelUsesTheEffectiveBrainDefinition() {
        var f = new Fixture(0, periodic("single", 16), periodic("silence", 16));
        when(f.player.isHandRaised()).thenReturn(true);
        var consumed = food(); when(f.player.getActiveItem()).thenReturn(consumed);
        try (var bukkit = mockStatic(Bukkit.class);
             var service = new IntelligenceService(mock(MobsPlatform.class), IntelligenceRules.defaults())) {
            IntelligenceService.phase(f.active, IntelligenceDef.level(5), 0);
            var engine = f.engine(); f.step(engine, 0);
            assertEquals(List.of("silence@0"), f.calls);
            IntelligenceService.phase(f.active, IntelligenceDef.level(0), 1);
            f.step(engine, 1);
            assertEquals(List.of("silence@0", "single@1"), f.calls);
        }
    }
    @Test void periodicCandidatesAcrossBothTriggersCompeteInTheSameEvaluation() {
        var range = new AbilityInstance("silence", Trigger.PLAYER_IN_RANGE, 0, TargetMode.NEAREST, 16, 40, 1, 0, Map.of());
        var f = new Fixture(5, periodic("single", 16), range);
        when(f.player.isBlocking()).thenReturn(true);
        // Both are neutral here; recent history must eventually give the second trigger a turn.
        var engine = f.engine();
        for (int tick = 0; tick < 200; tick++) f.step(engine, tick);
        assertTrue(f.calls.stream().anyMatch(s -> s.startsWith("silence@")), f.calls.toString());
    }
    @ParameterizedTest @ValueSource(ints = {0, 1})
    void legacyLevelsKeepTheExactListOrderAndCadence(int level) {
        var definitions = new AbilityInstance[] {periodic("single", 16), periodic("silence", 16)};
        var baseline = new Fixture(0, definitions);
        var f = new Fixture(level, definitions);
        when(f.player.isHandRaised()).thenReturn(true);
        var engine = f.engine(); var original = baseline.engine();
        try (var bukkit = mockStatic(Bukkit.class);
             var service = new IntelligenceService(mock(MobsPlatform.class), IntelligenceRules.defaults())) {
            IntelligenceService.track(f.active);
            for (int tick = 0; tick < 2400; tick++) { baseline.step(original, tick); f.step(engine, tick); }
            assertEquals(baseline.calls, f.calls);
            verify(f.player, never()).getActiveItem();
        }
    }
    @Test void levelFiveUsesAtLeastFourConfiguredAbilitiesInTwoMinutesOfVariedStates() {
        var f = new Fixture(5, periodic("single", 16), periodic("sonic_boom", 16),
                periodic("creeper_blast", 16), periodic("silence", 16), periodic("healer", 16));
        var engine = f.engine(); var used = new HashSet<String>();
        var group = List.of(f.player, f.companion(3, 2, 1), f.companion(4, 3, 1));
        var consumed = food(); when(f.player.getActiveItem()).thenReturn(consumed);
        for (int tick = 0; tick < 2400; tick++) {
            if (tick % 120 == 0) {
                int phase = (tick / 120) % 5;
                when(f.player.getLocation()).thenReturn(new Location(f.world, phase == 0 ? 13 : 2, 64, 0));
                when(f.player.isBlocking()).thenReturn(phase == 1);
                when(f.entity.getHealth()).thenReturn(phase == 4 ? 20d : 100d);
                when(f.player.isHandRaised()).thenReturn(phase == 3);
                when(f.session.players()).thenReturn(phase == 2 ? group : List.of(f.player));
            }
            f.step(engine, tick);
        }
        for (var call : f.calls) used.add(call.split("@")[0]);
        assertTrue(used.size() >= 4, "Used=" + used + " calls=" + f.calls);
        for (var expected : Map.of(2, "creeper_blast", 3, "silence", 4, "healer").entrySet())
            assertTrue(f.calls.stream().anyMatch(s -> s.startsWith(expected.getValue() + "@")
                    && (Integer.parseInt(s.split("@")[1]) / 120) % 5 == expected.getKey()), "Situation=" + expected);
        assertTrue(f.calls.stream().collect(java.util.stream.Collectors.groupingBy(s -> s.split("@")[1], java.util.stream.Collectors.counting()))
                .values().stream().allMatch(n -> n == 1), "At most one new ability per evaluation");
        assertTrue(f.calls.stream().allMatch(s -> Integer.parseInt(s.split("@")[1]) % 10 == 0));
        System.out.printf("T59a simulated 120s: %d distinct skills %s, %d casts%n", used.size(), new TreeSet<>(used), f.calls.size());
    }
}
