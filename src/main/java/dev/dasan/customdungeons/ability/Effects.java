package dev.dasan.customdungeons.ability;

import dev.dasan.customdungeons.config.PluginConfig;
import dev.dasan.customdungeons.runtime.*;
import java.util.*;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;

/** Shared public-API effects; visuals are sent only to nearby participants. */
public final class Effects {
    public static final NamespacedKey PROJECTILE_KEY = new NamespacedKey("customdungeons", "ability_projectile");
    private static final Map<Projectile, SessionContext> PROJECTILE_SESSIONS = new WeakHashMap<>();
    private static double viewRadius = 48, density = 1;
    private Effects() {}
    static void configure(PluginConfig.PerformanceLimits limits) {
        viewRadius = Double.isFinite(limits.effectViewRadius()) ? Math.max(0, limits.effectViewRadius()) : 48;
        density = Double.isFinite(limits.particleDensity()) ? Math.clamp(limits.particleDensity(), 0, 10) : 1;
    }
    public static void particles(SessionContext session, Location at, Particle particle, int count, double spread) {
        if (count <= 0) return;
        int bounded = Math.max(1, (int) Math.round(Math.min(256, count * density)));
        for (Player p : viewers(session, at))
            p.spawnParticle(particle, at, bounded, spread, spread, spread, 0);
    }
    public static void sound(SessionContext session, Location at, Sound sound, float volume, float pitch) {
        for (Player p : viewers(session, at)) p.playSound(at, sound, volume, pitch);
    }
    private static List<Player> viewers(SessionContext session, Location at) {
        return session.players().stream().filter(p -> p.isOnline() && !p.isDead()
                && p.getWorld().equals(at.getWorld())
                && p.getLocation().distanceSquared(at) <= viewRadius * viewRadius).toList();
    }
    public static void damage(LivingEntity target, double amount, ActiveMob source) {
        if (Double.isFinite(amount) && amount > 0 && TargetSelector.eligible(source, target, Double.MAX_VALUE))
            target.damage(amount, source.entity());
    }
    public static void knockback(LivingEntity target, Location from, double strength, double up) {
        if (!target.getWorld().equals(from.getWorld()) || !Double.isFinite(strength) || !Double.isFinite(up)) return;
        Vector direction = target.getLocation().toVector().subtract(from.toVector()).setY(0);
        if (direction.lengthSquared() > 0) direction.normalize().multiply(strength);
        target.setVelocity(direction.setY(up));
    }
    public static <T extends Projectile> T launch(ActiveMob caster, Class<T> type, Vector velocity) {
        T projectile = caster.entity().launchProjectile(type, velocity);
        projectile.getPersistentDataContainer().set(PROJECTILE_KEY, PersistentDataType.BYTE, (byte) 1);
        projectile.getPersistentDataContainer().set(new NamespacedKey("customdungeons", "session"),
                PersistentDataType.STRING, caster.session().id().toString());
        if (projectile instanceof Explosive explosive) { explosive.setIsIncendiary(false); explosive.setYield(0); }
        PROJECTILE_SESSIONS.put(projectile, caster.session());
        return projectile;
    }
    public static boolean marked(Entity entity) {
        return entity != null && entity.getPersistentDataContainer().has(PROJECTILE_KEY, PersistentDataType.BYTE);
    }
    public static boolean projectileTargetAllowed(Projectile projectile, Entity target) {
        SessionContext session = PROJECTILE_SESSIONS.get(projectile);
        return session != null && target instanceof Player p && session.players().contains(p)
                && p.isOnline() && !p.isDead() && p.getGameMode() != GameMode.CREATIVE
                && p.getGameMode() != GameMode.SPECTATOR;
    }
}
