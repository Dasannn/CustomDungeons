package dev.dasan.customdungeons.ability;

import dev.dasan.customdungeons.mob.MobsPlatform;
import dev.dasan.customdungeons.mob.MobHost;
import dev.dasan.customdungeons.config.PluginConfig;
import dev.dasan.customdungeons.runtime.*;
import java.util.*;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;

/** Shared public-API effects; visuals are sent only to nearby participants. */
public final class Effects {
    public static final NamespacedKey PROJECTILE_KEY = MobsPlatform.key("ability_projectile");
    private static final Map<Projectile, MobHost> PROJECTILE_SESSIONS = new WeakHashMap<>();
    // Keep the source, never a configuration snapshot: /reload replaces the YAML object.
    private static java.util.function.Supplier<PluginConfig.PerformanceLimits> limits =
            () -> new PluginConfig.PerformanceLimits(50, 1, 48);
    private Effects() {}
    static void configure(java.util.function.Supplier<PluginConfig.PerformanceLimits> source) {
        limits = Objects.requireNonNull(source);
    }
    public static int maxAliveMobs() { return limits.get().maxAliveMobsPerSession(); }
    public static double viewRadius() {
        double radius = limits.get().effectViewRadius();
        return Double.isFinite(radius) ? Math.max(0, radius) : 48;
    }
    public static double particleDensity() {
        double density = limits.get().particleDensity();
        return Double.isFinite(density) ? Math.clamp(density, 0, 10) : 1;
    }
    public static void particles(MobHost session, Location at, Particle particle, int count, double spread) {
        if (count <= 0) return;
        int bounded = Math.max(1, (int) Math.round(Math.min(256, count * particleDensity())));
        for (Player p : EffectAudience.viewers(session, at, viewRadius()))
            p.spawnParticle(particle, at, bounded, spread, spread, spread, 0);
    }
    public static void sound(MobHost session, Location at, Sound sound, float volume, float pitch) {
        for (Player p : session.audience(at)) p.playSound(at, sound, volume, pitch);
    }
    public static void damage(LivingEntity target, double amount, ActiveMob source) {
        if (Double.isFinite(amount) && amount > 0 && TargetSelector.eligible(source, target, Double.MAX_VALUE))
            dev.dasan.customdungeons.mob.MobCombatListener.abilityDamage(target,amount,source.entity());
    }
    public static void knockback(LivingEntity target, Location from, double strength, double up) {
        if (!target.getWorld().equals(from.getWorld()) || !Double.isFinite(strength) || !Double.isFinite(up)) return;
        Vector direction = target.getLocation().toVector().subtract(from.toVector()).setY(0);
        if (direction.lengthSquared() > 0) direction.normalize().multiply(strength);
        target.setVelocity(direction.setY(up));
    }
    public static <T extends Projectile> T launch(ActiveMob caster, Class<T> type, Vector velocity) {
        T projectile = caster.entity().launchProjectile(type, velocity, created -> {
            dev.dasan.customdungeons.mob.OwnedEntities.mark(created,caster);
            created.getPersistentDataContainer().set(PROJECTILE_KEY, PersistentDataType.BYTE, (byte) 1);
            if (created instanceof Explosive explosive) { explosive.setIsIncendiary(false); explosive.setYield(0); }
        });
        PROJECTILE_SESSIONS.put(projectile, caster.session());
        return projectile;
    }
    public static boolean marked(Entity entity) {
        return entity != null && entity.getPersistentDataContainer().has(PROJECTILE_KEY, PersistentDataType.BYTE);
    }
    /** Terrain protection also covers vanilla shots with explicit mob-host ownership.
     * Keep marked() ability-specific: those handlers replace native impacts and damage. */
    public static boolean protectedProjectile(Entity entity) {
        return marked(entity) || entity instanceof Projectile
                && entity.getPersistentDataContainer().has(dev.dasan.customdungeons.mob.MobKeys.SESSION, PersistentDataType.STRING);
    }
    public static boolean projectileTargetAllowed(Projectile projectile, Entity target) {
        MobHost session = PROJECTILE_SESSIONS.get(projectile);
        return session != null && target instanceof Player p && session.players().contains(p)
                && p.isOnline() && !p.isDead() && p.getGameMode() != GameMode.CREATIVE
                && p.getGameMode() != GameMode.SPECTATOR;
    }
}
