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
            case RANDOM -> List.of(eligible.get(random.nextInt(eligible.size())));
        };
    }
    static boolean eligible(ActiveMob caster, LivingEntity target, double range) {
        return target instanceof Player p && caster.session().players().contains(p)
                && p.isOnline() && p.isValid() && !p.isDead()
                && p.getGameMode() != GameMode.SPECTATOR && p.getGameMode() != GameMode.CREATIVE
                && p.getWorld().equals(caster.entity().getWorld())
                && caster.entity().getLocation().distanceSquared(p.getLocation()) <= range * range;
    }
}
