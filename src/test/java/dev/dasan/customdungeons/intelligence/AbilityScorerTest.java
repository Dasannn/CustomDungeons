package dev.dasan.customdungeons.intelligence;

import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static dev.dasan.customdungeons.intelligence.AbilityScorer.*;
import static org.junit.jupiter.api.Assertions.*;

class AbilityScorerTest {
    private static final Situation NORMAL = situation(3, 1, false, 1, false, false, false, 1);
    private static Situation situation(double distance, int group, boolean healing, double hp,
                                       boolean air, boolean shield, boolean ranged, double mobHp) {
        return new Situation(distance, group, healing, hp, air, shield, ranged, mobHp);
    }
    private static Option option(String key, double reach, double damage, Situation situation, Role... roles) {
        return new Option(key, new Profile(reach, damage, Set.of(roles)), situation, 0, Long.MIN_VALUE);
    }
    private static Option best(Option... options) { return choose(List.of(options), 5, 100, new Random(1)); }

    @Test void farTargetSelectsLongReachAndRejectsAnUnreachableCast() {
        var far = situation(13, 1, false, 1, false, false, true, 1);
        var close = option("grab", 4, 6, far);
        var longReach = option("beam", 16, 14, far);
        assertSame(longReach, best(close, longReach));
        assertEquals(Double.NEGATIVE_INFINITY, score(close, 100));
    }
    @Test void groupedPlayersFavorAreaOverSingleTarget() {
        var group = situation(3, 4, false, 1, false, false, false, 1);
        var area = option("blast", 8, 6, group, Role.AREA);
        assertSame(area, best(option("single", 8, 10, group), area));
    }
    @Test void healingTargetFavorsInterruptionEvenOverAnAreaAttack() {
        var healing = situation(3, 2, true, .5, false, false, false, 1);
        var silence = option("silence", 6, 0, healing, Role.INTERRUPT);
        assertSame(silence, best(option("blast", 8, 10, healing, Role.AREA), silence));
    }
    @Test void lowHealthTargetFavorsDamage() {
        var dying = situation(3, 1, false, .2, false, false, false, 1);
        var damage = option("hit", 8, 10, dying);
        assertSame(damage, best(option("utility", 8, 0, dying), damage));
    }
    @Test void airborneTargetFavorsAntiAir() {
        var air = situation(6, 1, false, 1, true, false, false, 1);
        var antiAir = option("homing", 16, 4, air, Role.ANTI_AIR);
        assertSame(antiAir, best(option("ordinary", 16, 8, air), antiAir));
    }
    @Test void shieldFavorsBypass() {
        var shield = situation(3, 1, false, 1, false, true, false, 1);
        var bypass = option("behind", 8, 6, shield, Role.ANTI_SHIELD);
        assertSame(bypass, best(option("arrow", 8, 6, shield), bypass));
    }
    @Test void rangedTargetFavorsClosingTheGap() {
        var ranged = situation(10, 1, false, 1, false, false, true, 1);
        var approach = option("charge", 12, 10, ranged, Role.APPROACH);
        assertSame(approach, best(option("ordinary", 12, 10, ranged), approach));
    }
    @ParameterizedTest @ValueSource(strings = {"HEAL", "SUMMON", "DEFENSE"})
    void woundedMobFavorsSurvival(String role) {
        var wounded = situation(3, 1, false, 1, false, false, false, .2);
        var survive = option("survive", 10, 0, wounded, Role.valueOf(role));
        assertSame(survive, best(option("hit", 10, 6, wounded), survive));
    }
    @Test void fullHealthDoesNotWasteHealingBelowTheThreshold() {
        assertNull(best(option("heal", 10, 0, NORMAL, Role.HEAL)));
    }
    @Test void recentUsageRewardsVarietyAndExpires() {
        var ordinary = option("first", 10, 6, NORMAL);
        var recent = new Option(ordinary.key(), ordinary.profile(), NORMAL, 2, 90);
        var fresh = option("second", 10, 6, NORMAL);
        assertSame(fresh, best(recent, fresh));
        assertEquals(score(ordinary, 400), score(new Option("first", ordinary.profile(), NORMAL, 0, 90), 400));
    }
    @Test void nothingReadyOrAboveThresholdMeansNoCast() {
        assertNull(choose(List.of(), 5, 0, new Random(1)));
        var spammed = option("hit", 10, 6, NORMAL);
        assertNull(best(new Option("hit", spammed.profile(), NORMAL, 8, 100)));
    }
    @ParameterizedTest @ValueSource(ints = {0, 1})
    void lowerLevelsNeverUseTheScorer(int level) {
        assertNull(choose(List.of(option("hit", 10, 6, NORMAL)), level, 100, new Random(1)));
    }
    @Test void levelTwoRandomizesOnlyBetweenTheTwoBestEligibleChoices() {
        var a = option("a", 10, 6, NORMAL);
        var b = option("b", 10, 6, NORMAL);
        var c = option("c", 10, 6, NORMAL);
        var chosen = new HashSet<String>();
        var random = new Random(5);
        for (int i = 0; i < 200; i++) chosen.add(choose(List.of(a, b, c), 2, 100, random).key());
        assertEquals(Set.of("a", "b"), chosen);
    }
    @ParameterizedTest @ValueSource(ints = {3, 4})
    void middleLevelsUsuallyChooseTheBestWithSomeVariation(int level) {
        var a = option("best", 10, 6, NORMAL);
        var b = option("second", 10, 0, NORMAL);
        int first = 0;
        var random = new Random(5);
        for (int i = 0; i < 1000; i++) if (choose(List.of(a, b), level, 100, random) == a) first++;
        assertTrue(first > 750 && first < 950, "best choices=" + first);
    }
    @Test void levelFiveAlwaysChoosesTheBestWithoutConsumingRandomness() {
        var a = option("best", 10, 6, NORMAL);
        var random = org.mockito.Mockito.mock(java.util.random.RandomGenerator.class);
        assertSame(a, choose(List.of(option("other", 10, 0, NORMAL), a), 5, 100, random));
        org.mockito.Mockito.verifyNoInteractions(random);
    }
    @Test void badGeometryNeverProducesANaNOrUsableScore() {
        for (double distance : new double[] {Double.NaN, Double.POSITIVE_INFINITY, -1})
            assertEquals(Double.NEGATIVE_INFINITY, score(option("bad", 10, 6,
                    situation(distance, 1, false, 1, false, false, false, 1)), 100));
    }
    @Test void areaWithoutPlayersDoesNotSpendACast() {
        assertNull(best(option("area", 10, 6, situation(3, 0, false, 1, false, false, false, 1), Role.AREA)));
    }
    @Test void selectionCostWithSixtyFourReadyAbilitiesIsBounded() {
        var options = new ArrayList<Option>();
        for (int i = 0; i < 64; i++) options.add(option("skill" + i, 16, 6, NORMAL, Role.AREA));
        var random = new Random(1);
        for (int i = 0; i < 2000; i++) choose(options, 5, i, random);
        long begins = System.nanoTime();
        assertTimeout(java.time.Duration.ofSeconds(5), () -> {
            for (int i = 0; i < 10_000; i++) assertNotNull(choose(options, 5, i, random));
        });
        System.out.printf(Locale.ROOT, "T59a scorer: 64 candidates, 10000 evaluations, %.3f us/evaluation%n",
                (System.nanoTime() - begins) / 10_000d / 1000);
    }
}
