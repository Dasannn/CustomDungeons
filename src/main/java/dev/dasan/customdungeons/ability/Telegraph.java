package dev.dasan.customdungeons.ability;

import dev.dasan.customdungeons.runtime.SessionContext;
import org.bukkit.Location;
import org.bukkit.Particle;

/** A bounded ring refreshed through the owning scheduler; no Bukkit task is created. */
public final class Telegraph {
    private Telegraph() {}
    public static void show(SessionContext session, Location center, double radius, int ticks, Particle particle) {
        if (ticks <= 0 || !Double.isFinite(radius) || radius < 0) return;
        draw(session, center.clone(), Math.min(radius, 64), ticks, particle);
    }
    private static void draw(SessionContext session, Location center, double radius, int remaining, Particle particle) {
        for (int i = 0; i < 24; i++) {
            double angle = i * Math.PI * 2 / 24;
            Effects.particles(session, center.clone().add(radius * Math.cos(angle), 0.1, radius * Math.sin(angle)),
                    particle, 1, 0);
        }
        if (remaining > 5) session.scheduler().runLater(5, () -> draw(session, center, radius, remaining - 5, particle));
    }
}
