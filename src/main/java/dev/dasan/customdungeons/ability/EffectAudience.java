package dev.dasan.customdungeons.ability;

import dev.dasan.customdungeons.runtime.SessionContext;
import java.util.Collection;
import java.util.List;
import org.bukkit.Location;
import org.bukkit.entity.Player;

/** Presentation audience only; this policy never grants eligibility for ability targets.
 * Live tests use the configured effect-view-radius (48 blocks by default) around the
 * emission point. Dungeon sounds/music reach all participants; particles remain local.
 */
public final class EffectAudience {
    private EffectAudience() {}

    public static Collection<Player> listeners(SessionContext session, Location at, double radius) {
        if (!session.isLiveTest()) return session.players();
        return at.getWorld() == null ? List.of() : nearby(at.getWorld().getPlayers(), at, radius);
    }

    public static Collection<Player> viewers(SessionContext session, Location at, double radius) {
        if (session.isLiveTest() && at.getWorld() == null) return List.of();
        return nearby(session.isLiveTest() ? at.getWorld().getPlayers() : session.players(), at, radius);
    }

    private static List<Player> nearby(Collection<Player> players, Location at, double radius) {
        double bounded = Double.isFinite(radius) ? Math.max(0, radius) : 48;
        return players.stream().filter(player -> {
            if (!player.isOnline() || player.isDead()) return false;
            Location location = player.getLocation();
            return location != null && at.getWorld() != null && at.getWorld().equals(location.getWorld())
                    && location.distanceSquared(at) <= bounded * bounded;
        }).toList();
    }
}
