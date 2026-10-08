package dev.dasan.customdungeons.ability;

import dev.dasan.customdungeons.mob.MobHost;
import java.util.Collection;
import java.util.List;
import org.bukkit.Location;
import org.bukkit.entity.Player;

/** Radius filtering for the host's presentation audience. This never grants target eligibility. */
public final class EffectAudience {
    private EffectAudience() {}

    public static Collection<Player> viewers(MobHost host, Location at, double radius) {
        return nearby(host.audience(at), at, radius);
    }

    public static List<Player> nearby(Collection<Player> players, Location at, double radius) {
        double bounded = Double.isFinite(radius) ? Math.max(0, radius) : 48;
        return players.stream().filter(player -> {
            if (!player.isOnline() || player.isDead()) return false;
            Location location = player.getLocation();
            return location != null && at.getWorld() != null && at.getWorld().equals(location.getWorld())
                    && location.distanceSquared(at) <= bounded * bounded;
        }).toList();
    }
}
