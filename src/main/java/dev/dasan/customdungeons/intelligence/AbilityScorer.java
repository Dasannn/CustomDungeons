package dev.dasan.customdungeons.intelligence;

import java.util.List;
import java.util.Set;
import java.util.random.RandomGenerator;

/** Pure situational choice. The engine supplies only casts that can start. */
public final class AbilityScorer {
    public enum Role { AREA, INTERRUPT, ANTI_AIR, ANTI_SHIELD, APPROACH, HEAL, SUMMON, DEFENSE }
    public record Profile(double reach, double damage, Set<Role> roles) {
        public Profile { roles = Set.copyOf(roles); }
    }
    public record Situation(double distance, int playersInArea, boolean healing, double targetHealth,
                            boolean airborne, boolean shield, boolean ranged, double mobHealth) {}
    public record Option(String key, Profile profile, Situation situation, int recentUses, long lastUsed) {}
    public static final double THRESHOLD = 12;
    private AbilityScorer() {}

    public static double score(Option option, long tick) {
        var p = option.profile(); var s = option.situation(); var roles = p.roles();
        if (!Double.isFinite(s.distance()) || s.distance() < 0 || !Double.isFinite(p.reach())
                || p.reach() < 0 || s.distance() > p.reach()) return Double.NEGATIVE_INFINITY;
        if (roles.contains(Role.AREA) && s.playersInArea() == 0) return Double.NEGATIVE_INFINITY;
        double score = 30 + 8 * s.distance() / Math.max(1, p.reach()) + Math.min(6, p.damage() * .5);
        if (roles.contains(Role.AREA)) score += 18 * Math.clamp(s.playersInArea() - 1, 0, 4);
        if (s.healing() && roles.contains(Role.INTERRUPT)) score += 50;
        if (s.targetHealth() <= .25 && p.damage() > 0) score += 20;
        if (s.airborne() && roles.contains(Role.ANTI_AIR)) score += 35;
        if (s.shield() && roles.contains(Role.ANTI_SHIELD)) score += 40;
        if (s.ranged() && roles.contains(Role.APPROACH)) score += 30;
        if (roles.contains(Role.HEAL)) {
            score += 55 * (1 - Math.clamp(s.mobHealth(), 0, 1));
            if (s.mobHealth() >= .9 && p.damage() == 0) score -= 50;
        }
        if (s.mobHealth() <= .35) {
            if (roles.contains(Role.DEFENSE)) score += 35;
            if (roles.contains(Role.SUMMON)) score += 25;
        }
        if (roles.contains(Role.SUMMON)) score += 8 * Math.clamp(s.playersInArea() - 1, 0, 4);
        // A bounded ten-second window lets useful skills recover and discourages duplicate instances.
        if (option.lastUsed() != Long.MIN_VALUE && tick - option.lastUsed() < 200)
            score -= 26 * (1 - Math.clamp((tick - option.lastUsed()) / 200d, 0, 1));
        score -= 5 * Math.max(0, option.recentUses());
        return score;
    }

    /** Linear scan, stable ties; random choice never admits a cast below the threshold. */
    public static Option choose(List<Option> options, int level, long tick, RandomGenerator random) {
        if (level < 2) return null;
        Option first = null, second = null;
        double best = THRESHOLD, next = THRESHOLD;
        for (var option : options) {
            double score = score(option, tick);
            if (score > best) { second = first; next = best; first = option; best = score; }
            else if (score > next) { second = option; next = score; }
        }
        if (second == null || level >= 5) return first;
        return (level == 2 ? random.nextInt(2) == 1 : random.nextDouble() < .15) ? second : first;
    }
}
