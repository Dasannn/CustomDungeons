package dev.dasan.customdungeons.ability;

import dev.dasan.customdungeons.model.TargetMode;
import dev.dasan.customdungeons.runtime.ActiveMob;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

/** Selects only living participants; never queries nearby world entities. */
public final class TargetSelector {
    private TargetSelector() {}
    public static List<LivingEntity> select(ActiveMob caster, TargetMode mode, double range) {
        return select(caster, mode, range, ThreadLocalRandom.current());
    }
    static List<LivingEntity> select(ActiveMob caster, TargetMode mode, double range, Random random) {
        if (!Double.isFinite(range) || range < 0) return List.of();
        Location origin = caster.entity().getLocation();
        List<LivingEntity> eligible = new ArrayList<>();
        for (Player player : caster.session().players()) {
            if (eligible(caster, player, range)) eligible.add(player);
        }
        if (eligible.isEmpty()) return List.of();
        return switch (mode) {
            case ALL_IN_RADIUS -> List.copyOf(eligible);
            case CURRENT_TARGET -> eligible.contains(caster.entity().getTarget())
                    ? List.of(caster.entity().getTarget()) : List.of();
            case NEAREST -> List.of(Collections.min(eligible,
                    Comparator.comparingDouble(e -> origin.distanceSquared(e.getLocation()))));
            case MOST_THREAT, WEAKEST, TANKIEST, LEAST_ARMOR, LAST_HEALED, ARCHER, BEHIND, FARTHEST -> intelligent(caster,mode,eligible,origin);
            case RANDOM -> List.of(eligible.get(random.nextInt(eligible.size())));
        };
    }
    private static List<LivingEntity> intelligent(ActiveMob caster,TargetMode mode,List<LivingEntity> players,Location origin) {
        var brain=dev.dasan.customdungeons.intelligence.IntelligenceService.brain(caster);
        if(brain==null||brain.definition().level()<(mode==TargetMode.MOST_THREAT?1:2))return List.of();
        long tick=caster.session().scheduler().currentTick();
        var candidates=players.stream().filter(e->switch(mode) {
            case LAST_HEALED -> brain.memory().observations(e.getUniqueId(),tick).stream().anyMatch(o->o.pattern().equals("heal")||o.pattern().equals("consumables"));
            case ARCHER -> brain.memory().observations(e.getUniqueId(),tick).stream().anyMatch(dev.dasan.customdungeons.intelligence.EncounterMemory.Observation::ranged);
            case BEHIND -> dev.dasan.customdungeons.intelligence.IntelligenceService.behind(caster.entity(),(Player)e);
            default -> true;
        }).toList();
        return candidates.stream().max(Comparator.comparingDouble(e->switch(mode) {
            case MOST_THREAT -> brain.memory().threat(e.getUniqueId(),tick);
            case WEAKEST -> -e.getHealth();case TANKIEST -> attribute(e,org.bukkit.attribute.Attribute.ARMOR)+e.getHealth();
            case LEAST_ARMOR -> -attribute(e,org.bukkit.attribute.Attribute.ARMOR);
            case FARTHEST -> origin.distanceSquared(e.getLocation());
            case LAST_HEALED -> brain.memory().observations(e.getUniqueId(),tick).stream().filter(o->o.pattern().equals("heal")||o.pattern().equals("consumables")).mapToLong(dev.dasan.customdungeons.intelligence.EncounterMemory.Observation::tick).max().orElse(0);
            default -> -origin.distanceSquared(e.getLocation());
        })).map(List::of).orElse(List.of());
    }
    private static double attribute(LivingEntity e,org.bukkit.attribute.Attribute type) {var a=e.getAttribute(type);return a==null?0:a.getValue();}
    static boolean eligible(ActiveMob caster, LivingEntity target, double range) {
        return target instanceof Player p && caster.session().players().contains(p)
                && p.isOnline() && p.isValid() && !p.isDead()
                && p.getGameMode() != GameMode.SPECTATOR && p.getGameMode() != GameMode.CREATIVE
                && p.getWorld().equals(caster.entity().getWorld())
                && caster.entity().getLocation().distanceSquared(p.getLocation()) <= range * range;
    }
}
