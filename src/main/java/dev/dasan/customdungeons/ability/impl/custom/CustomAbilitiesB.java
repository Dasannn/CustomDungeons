package dev.dasan.customdungeons.ability.impl.custom;

import dev.dasan.customdungeons.ability.*;
import dev.dasan.customdungeons.runtime.*;
import java.util.List;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.*;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

public final class CustomAbilitiesB {
    private CustomAbilitiesB() {}
    public static void register(AbilityRegistry r) {
        r.register(new ThiefAbility()); r.register(new VampirismAbility());
        r.register(new HealerAbility()); r.register(new EnrageAbility());
        registerLifecycle(r, new MinionShieldAbility()); r.register(new ReflectAbility());
        registerLifecycle(r, new MeteorsAbility()); registerLifecycle(r, new EarthquakeAbility());
        r.register(new LastBreathAbility()); r.register(new DoubleAbility());
    }
    private static void registerLifecycle(AbilityRegistry registry, Ability ability) {
        registry.register(ability);
        // Ability-owned transient state also needs cleanup if a session discards its queue.
        if (Bukkit.getServer() != null && ability instanceof org.bukkit.event.Listener listener) {
            var plugin = Bukkit.getPluginManager().getPlugin("CustomDungeons");
            if (plugin != null) Bukkit.getPluginManager().registerEvents(listener, plugin);
        }
    }
    static boolean alive(ActiveMob mob) { return mob.entity().isValid() && !mob.entity().isDead(); }
    static boolean participant(AbilityContext ctx, LivingEntity target) {
        return target instanceof Player p && ctx.session().players().contains(p) && p.isOnline()
                && p.isValid() && !p.isDead() && p.getGameMode() != GameMode.CREATIVE
                && p.getGameMode() != GameMode.SPECTATOR && p.getWorld().equals(ctx.caster().entity().getWorld());
    }
    static Player hitPlayer(AbilityContext ctx) {
        if (!(ctx.cause() instanceof EntityDamageByEntityEvent e) || e.isCancelled()
                || !ownedDamage(ctx, e.getDamager()) || !(e.getEntity() instanceof Player p)
                || !participant(ctx, p)) return null;
        return p;
    }
    private static boolean ownedDamage(AbilityContext ctx, Entity damager) {
        return ctx.caster().entity().equals(damager) || damager instanceof Projectile projectile
                && ctx.caster().entity().equals(projectile.getShooter());
    }
    static double healedHealth(double health, double maximum, double amount) {
        return Math.min(maximum, health + (Double.isFinite(amount) ? Math.max(0, amount) : 0));
    }
    static void heal(LivingEntity entity, double amount) {
        var max = entity.getAttribute(Attribute.MAX_HEALTH);
        if (max != null && !entity.isDead()) entity.setHealth(healedHealth(entity.getHealth(), max.getValue(), amount));
    }
    static void line(SessionContext session, Location from, Location to, Particle particle) {
        if (!from.getWorld().equals(to.getWorld())) return;
        var step = to.toVector().subtract(from.toVector());
        int count = Math.max(1, Math.min(32, (int) Math.ceil(step.length() * 2)));
        step.multiply(1.0 / count);
        for (int i = 0; i <= count; i++) Effects.particles(session, from.clone().add(step.clone().multiply(i)), particle, 1, 0);
    }
    static void blast(AbilityContext ctx, Location center, double radius, double damage, double up) {
        Effects.particles(ctx.session(), center, Particle.EXPLOSION_EMITTER, 1, 0);
        Effects.sound(ctx.session(), center, Sound.ENTITY_GENERIC_EXPLODE, 1, 1);
        for (Player p : List.copyOf(ctx.session().players())) {
            if (participant(ctx, p) && p.getLocation().distanceSquared(center) <= radius * radius) {
                Effects.damage(p, damage, ctx.caster());
                if (up > 0) Effects.knockback(p, center, 0, up);
            }
        }
    }
}
