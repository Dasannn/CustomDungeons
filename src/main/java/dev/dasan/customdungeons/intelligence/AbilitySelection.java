package dev.dasan.customdungeons.intelligence;

import dev.dasan.customdungeons.ability.*;
import dev.dasan.customdungeons.mob.MobHealth;
import dev.dasan.customdungeons.model.AbilityInstance;
import dev.dasan.customdungeons.model.TargetMode;
import dev.dasan.customdungeons.runtime.ActiveMob;
import java.util.*;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

/** Bukkit snapshot adapter; allocated only for tactical mobs, owned by their engine state. */
public final class AbilitySelection {
    private static final Set<String> ANTI_AIR = Set.of("shulker_bullet", "hook", "anchor_spear");
    private static final Set<String> ANTI_SHIELD = Set.of("sonic_boom", "blink_behind", "roar_knockback");
    private static final Set<String> APPROACH = Set.of("charge", "blink_behind", "hook", "ender_blink", "breeze_leap", "swap");
    private static final Set<String> SUMMON = Set.of("tactical_summon", "summon_minions", "summon_vexes", "double");
    private static final Set<String> DEFENSE = Set.of("boss_totem", "minion_shield", "reflect", "decoys");
    private static final Set<String> AREA = Set.of("sweep", "charged_beam", "dragon_roar", "wither_shockwave", "falling_pillars");
    public record Candidate(String key, AbilityInstance instance, Ability ability, ParamValues params,
                            double reach, List<LivingEntity> targets) {}
    private record Use(String id, long tick) {}
    private record Target(Player player, Location at, boolean healing, boolean airborne,
                          boolean shield, boolean ranged, double health) {}
    private record ChoiceTrace(long tick,AbilityScorer.Option option,double score) {}
    private ChoiceTrace debugChoice;
    private final ArrayDeque<Use> recent = new ArrayDeque<>(8);

    /** Count effects, never rejected selections or warnings. Duplicate instances share recent usage. */
    public void started(String id, long tick) {
        if (recent.size() == 8) recent.removeFirst();
        recent.addLast(new Use(id, tick));
    }

    public String choose(ActiveMob mob, List<Candidate> candidates, int level, long tick, Random random) {
        boolean debug=dev.dasan.customdungeons.mob.DebugView.active();
        if(!debug)debugChoice=null;
        if (candidates.isEmpty() || level < 2) return null;
        while (!recent.isEmpty() && tick - recent.getFirst().tick() >= 200) recent.removeFirst();
        Location origin = mob.entity().getLocation();
        double range = 32;
        for (var candidate : candidates) range = Math.max(range, candidate.reach());
        var brain = IntelligenceService.brain(mob);
        var area = mob.session().area();
        var targets = new ArrayList<Target>();
        // One bounded encounter scan and one memory snapshot per player, shared by all scores.
        for (var entity : TargetSelector.select(mob, TargetMode.ALL_IN_RADIUS, range)) {
            var player = (Player) entity;
            Location at = player.getLocation();
            if (area != null && !area.contains(at)) continue;
            boolean healing = false, ranged = origin.distanceSquared(at) >= 64;
            if (player.isHandRaised()) {
                var item = player.getActiveItem();
                Material type = item == null ? Material.AIR : item.getType();
                healing = type.isEdible() || type == Material.POTION;
                ranged |= type == Material.BOW || type == Material.CROSSBOW;
            }
            if (brain != null) for (var observation : brain.memory().observations(player.getUniqueId(), tick)) {
                long age = tick - observation.tick();
                if (age >= 0 && age <= 40 && (observation.pattern().equals("heal") || observation.pattern().equals("consumables"))) healing = true;
                if (age >= 0 && age <= 100 && observation.ranged()) ranged = true;
            }
            double maximum = MobHealth.maximum(player);
            targets.add(new Target(player, at, healing, player.isGliding() || !player.isOnGround(),
                    player.isBlocking(), ranged, Math.clamp(player.getHealth() / (maximum > 0 ? maximum : 20), 0, 1)));
        }
        var options = new ArrayList<AbilityScorer.Option>(candidates.size());
        double mobHealth = MobHealth.fraction(mob.entity());
        for (var candidate : candidates) {
            var primary = candidate.targets().isEmpty() ? null : targets.stream()
                    .filter(t -> t.player().equals(candidate.targets().getFirst())).findFirst().orElse(null);
            if (primary == null && !candidate.targets().isEmpty()) continue;
            var roles = roles(candidate);
            double radius = number(candidate, "radius", candidate.reach());
            String id = candidate.instance().abilityId();
            boolean self = centeredOnCaster(id);
            Location center = self || primary == null ? origin : primary.at();
            int group = 0;
            for (var target : targets) if (inArea(candidate, origin, center, primary, target.at(), radius)) group++;
            if (id.equals("falling_pillars")) group = Math.min(group, (int)number(candidate,"count",3));
            double distance = primary == null || id.equals("healer") || id.equals("boss_totem") ? 0 : origin.distance(primary.at());
            double reach = self && roles.contains(AbilityScorer.Role.AREA) ? Math.min(radius, candidate.reach()) : candidate.reach();
            var situation = new AbilityScorer.Situation(distance, group, primary != null && primary.healing(),
                    primary == null ? 1 : primary.health(), primary != null && primary.airborne(),
                    primary != null && primary.shield(), primary != null && primary.ranged(), mobHealth);
            int count = 0; long last = Long.MIN_VALUE;
            for (var use : recent) if (use.id().equals(candidate.instance().abilityId())) { count++; last = use.tick(); }
            options.add(new AbilityScorer.Option(candidate.key(),
                    new AbilityScorer.Profile(reach, number(candidate, "damage", 0), roles), situation, count, last));
        }
        var selected = AbilityScorer.choose(options, level, tick, random);
        // A single immutable diagnostic snapshot, taken before started() mutates recency.
        if(debug)debugChoice=selected==null?null:new ChoiceTrace(tick,selected,AbilityScorer.score(selected,tick));
        return selected == null ? null : selected.key();
    }

    private static double number(Candidate candidate, String key, double fallback) {
        for (var spec : candidate.ability().params()) if (spec.key().equals(key)
                && (spec.type() == ParamType.DOUBLE || spec.type() == ParamType.INT || spec.type() == ParamType.TICKS))
            return candidate.params().getDouble(key);
        return fallback;
    }
    private static Set<AbilityScorer.Role> roles(Candidate candidate) {
        var roles = EnumSet.noneOf(AbilityScorer.Role.class);
        String id = candidate.instance().abilityId();
        boolean healing = id.equals("healer") || id.equals("drain_grab") || id.equals("vampirism")
                || id.equals("boss_totem") && !candidate.params().getBoolean("protect");
        if (healing) roles.add(AbilityScorer.Role.HEAL);
        // Known effects plus declared area parameters preserve usefulness for registered abilities.
        if (!id.equals("healer") && !SUMMON.contains(id) && !DEFENSE.contains(id) && (candidate.instance().target() == TargetMode.ALL_IN_RADIUS
                || number(candidate, "radius", 0) > 0 || AREA.contains(id)))
            roles.add(AbilityScorer.Role.AREA);
        if (id.equals("silence")) roles.add(AbilityScorer.Role.INTERRUPT);
        if (ANTI_AIR.contains(id)) roles.add(AbilityScorer.Role.ANTI_AIR);
        if (ANTI_SHIELD.contains(id)) roles.add(AbilityScorer.Role.ANTI_SHIELD);
        if (APPROACH.contains(id)) roles.add(AbilityScorer.Role.APPROACH);
        if (SUMMON.contains(id)) roles.add(AbilityScorer.Role.SUMMON);
        if (DEFENSE.contains(id)) roles.add(AbilityScorer.Role.DEFENSE);
        return roles;
    }
    private static boolean inArea(Candidate candidate, Location origin, Location center, Target primary, Location at, double radius) {
        String id = candidate.instance().abilityId();
        if (id.equals("charged_beam") || id.equals("sweep")) {
            if (Math.abs(at.getY() - origin.getY()) > 4) return false;
            var direction = id.equals("charged_beam") && primary != null
                    ? primary.at().toVector().subtract(origin.toVector()).setY(0) : origin.getDirection().setY(0);
            if (direction.lengthSquared() == 0) direction.setZ(1); else direction.normalize();
            double x = at.getX() - origin.getX(), z = at.getZ() - origin.getZ();
            return id.equals("charged_beam")
                    ? dev.dasan.customdungeons.ability.zone.ZoneRules.beam(x,z,direction.getX(),direction.getZ(),candidate.reach(),number(candidate,"width",1))
                    : dev.dasan.customdungeons.ability.zone.ZoneRules.cone(x,z,direction.getX(),direction.getZ(),candidate.reach(),number(candidate,"angle",120));
        }
        return at.distanceSquared(center) <= radius * radius;
    }
    private static boolean centeredOnCaster(String id) {
        return switch (id) {
            case "vortex", "sweep", "cracked_floor", "charged_beam", "falling_pillars", "creeper_blast", "wither_shockwave",
                    "dragon_roar", "roar_knockback", "darkness_pulse", "earthquake", "interruptible_ultimate",
                    "purge", "silence", "healer", "tactical_summon", "summon_minions", "summon_vexes" -> true;
            default -> false;
        };
    }
}
