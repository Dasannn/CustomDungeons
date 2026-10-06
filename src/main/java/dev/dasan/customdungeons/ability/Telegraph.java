package dev.dasan.customdungeons.ability;

import dev.dasan.customdungeons.runtime.SessionContext;
import dev.dasan.customdungeons.runtime.ActiveMob;
import org.bukkit.Location;
import org.bukkit.Particle;

/** A bounded ring refreshed through the owning scheduler; no Bukkit task is created. */
public final class Telegraph {
    private Telegraph() {}
    public static void show(ActiveMob caster, Location center, double radius, int ticks, Particle particle,
                            Runnable complete, Runnable cancel) {
        if (ticks <= 0 || !Double.isFinite(radius) || radius < 0) { cancel.run(); return; }
        draw(caster, center.clone(), Math.min(radius, 64), ticks, particle, complete, cancel);
    }
    private static void draw(ActiveMob caster, Location center, double radius, int remaining, Particle particle,
                             Runnable complete, Runnable cancel) {
        if (!AbilityEngine.alive(caster)) { cancel.run(); return; }
        if (remaining <= 0) { complete.run(); return; }
        SessionContext session = caster.session();
        for (int i = 0; i < 24; i++) {
            double angle = i * Math.PI * 2 / 24;
            Effects.particles(session, center.clone().add(radius * Math.cos(angle), 0.1, radius * Math.sin(angle)),
                    particle, 1, 0);
        }
        int delay = Math.min(5, remaining);
        session.scheduler().runLater(delay,
                () -> draw(caster, center, radius, remaining - delay, particle, complete, cancel));
    }
}
