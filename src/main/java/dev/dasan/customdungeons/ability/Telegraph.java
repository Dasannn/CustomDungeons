package dev.dasan.customdungeons.ability;

import dev.dasan.customdungeons.mob.MobHost;
import dev.dasan.customdungeons.runtime.ActiveMob;
import org.bukkit.Location;
import org.bukkit.Particle;
import java.util.ArrayList;
import java.util.List;

/** A bounded ring refreshed through the owning scheduler; no Bukkit task is created. */
public final class Telegraph {
    private Telegraph() {}
    public static void show(ActiveMob caster, Location center, double radius, int ticks, Particle particle,
                            Runnable complete, Runnable cancel) {
        if (ticks <= 0 || !Double.isFinite(radius) || radius < 0) { cancel.run(); return; }
        radius=Math.min(radius,64);var points=new ArrayList<Location>(24);
        for(int i=0;i<24;i++) {
            double angle=i*Math.PI*2/24;
            points.add(center.clone().add(radius*Math.cos(angle),.1,radius*Math.sin(angle)));
        }
        draw(caster, List.copyOf(points), ticks, particle, complete, cancel);
    }
    private static void draw(ActiveMob caster, List<Location> points, int remaining, Particle particle,
                             Runnable complete, Runnable cancel) {
        if (!AbilityEngine.alive(caster)) { cancel.run(); return; }
        if (remaining <= 0) { complete.run(); return; }
        MobHost session = caster.session();
        Effects.pointsShared(session,points,particle,1,0);
        int delay = Math.min(5, remaining);
        session.scheduler().runLater(delay,
                () -> draw(caster, points, remaining - delay, particle, complete, cancel));
    }
}
