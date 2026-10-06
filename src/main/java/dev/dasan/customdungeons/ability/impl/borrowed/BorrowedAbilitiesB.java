package dev.dasan.customdungeons.ability.impl.borrowed;

import dev.dasan.customdungeons.ability.*;
import dev.dasan.customdungeons.model.TargetMode;
import java.util.*;
import java.util.function.BiConsumer;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.*;
import io.papermc.paper.event.entity.EntityKnockbackEvent;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.*;
import org.bukkit.util.Vector;

/** Impact routing for B and generic arrows: native secondary effects never escape the session. */
public final class BorrowedAbilitiesB implements Listener {
    private record Flight(AbilityContext context, BiConsumer<Location, Entity> impact) {}
    private static final Map<Projectile, Flight> flights = new WeakHashMap<>();
    private static final Set<Projectile> spawned = Collections.newSetFromMap(new WeakHashMap<>());
    private static final NamespacedKey GROUP = new NamespacedKey("customdungeons", "abilities_b");
    // Only set during the synchronous public-API creeper explosion. Its damage is applied separately.
    private static Entity exploding;
    public static void register(AbilityRegistry registry) {
        for (Ability ability : List.of(new BlazeVolleyAbility(), new GhastFireballAbility(),
                new WindChargeAbility(), new BreezeLeapAbility(), new ShulkerBulletAbility(),
                new ElderCurseAbility(), new GuardianBeamAbility(), new CreeperBlastAbility(),
                new WitchPotionsAbility())) registry.register(ability);
        if (Bukkit.getServer() != null) {
            var plugin = Bukkit.getPluginManager().getPlugin("CustomDungeons");
            if (plugin == null) throw new IllegalStateException("CustomDungeons plugin unavailable");
            Bukkit.getPluginManager().registerEvents(new BorrowedAbilitiesB(), plugin);
        }
    }
    public static List<LivingEntity> targets(AbilityContext ctx) {
        var allowed = TargetSelector.select(ctx.caster(), TargetMode.ALL_IN_RADIUS, Double.MAX_VALUE);
        return ctx.targets().stream().filter(allowed::contains).distinct().toList();
    }
    public static boolean allowed(AbilityContext ctx, Entity entity) {
        return entity instanceof LivingEntity && TargetSelector.select(ctx.caster(),
                TargetMode.ALL_IN_RADIUS, Double.MAX_VALUE).contains(entity);
    }
    public static boolean alive(AbilityContext ctx) {
        return ctx.caster().entity().isValid() && !ctx.caster().entity().isDead() && !ctx.session().players().isEmpty();
    }
    public static Vector aim(AbilityContext ctx, LivingEntity target) {
        var delta = target.getEyeLocation().toVector().subtract(ctx.caster().entity().getEyeLocation().toVector());
        return delta.lengthSquared() == 0 ? new Vector() : delta.normalize();
    }
    public static <T extends Projectile> T launch(AbilityContext ctx, Class<T> type, Vector velocity,
                                                BiConsumer<Location, Entity> impact) {
        T projectile = Effects.launch(ctx.caster(), type, velocity);
        projectile.getPersistentDataContainer().set(GROUP, PersistentDataType.BYTE, (byte) 1);
        spawned.add(projectile);
        flights.put(projectile, new Flight(ctx, impact));
        ctx.session().scheduler().runLater(100, () -> { flights.remove(projectile); spawned.remove(projectile); projectile.remove(); });
        return projectile;
    }
    public static void radial(AbilityContext ctx, Location at, double radius, double damage, double push) {
        var room = ctx.session().currentRoomRegion();
        if (room != null && !room.contains(at.getWorld().getName(), at.getBlockX(), at.getBlockY(), at.getBlockZ())) return;
        for (var target : ctx.session().players()) {
            if (allowed(ctx, target) && target.getWorld().equals(at.getWorld())
                    && target.getLocation().distanceSquared(at) <= radius * radius) {
                Effects.damage(target, damage, ctx.caster());
                if (push > 0) Effects.knockback(target, at, push, Math.min(push, 1));
            }
        }
    }
    public static List<ParamSpec> potionParams() {
        return List.of(new ParamSpec("effect", ParamType.POTION_EFFECT, "minecraft:poison", 0, 0),
                new ParamSpec("amplifier", ParamType.INT, 0, 0, 255),
                new ParamSpec("seconds", ParamType.DOUBLE, 5.0, 0.05, 3600));
    }
    public static PotionEffect potion(AbilityContext ctx) {
        var key = NamespacedKey.fromString(ctx.params().getString("effect"));
        var type = key == null ? null : Registry.EFFECT.get(key);
        return type == null ? null : new PotionEffect(type,
                (int) Math.round(ctx.params().getDouble("seconds") * 20), ctx.params().getInt("amplifier"));
    }
    public static void explosion(AbilityContext ctx, Location at, float power) {
        Entity previous = exploding;
        exploding = ctx.caster().entity();
        try { at.getWorld().createExplosion(at, power, false, false, exploding); }
        finally { exploding = previous; }
    }
    private static boolean marked(Entity entity) {
        return entity instanceof Projectile && entity.getPersistentDataContainer().has(GROUP, PersistentDataType.BYTE);
    }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void hit(ProjectileHitEvent event) {
        var flight = flights.remove(event.getEntity());
        if (flight == null) return;
        // Eligible tipped arrows retain vanilla damage and potion handling, without pickup.
        if (event.getEntity() instanceof Arrow && alive(flight.context())
                && allowed(flight.context(), event.getHitEntity())) return;
        event.setCancelled(true);
        var at = event.getEntity().getLocation().clone();
        spawned.remove(event.getEntity());
        event.getEntity().remove();
        if (!alive(flight.context())) return;
        if (event.getHitEntity() != null && !allowed(flight.context(), event.getHitEntity())) return;
        flight.impact().accept(at, event.getHitEntity());
    }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void damage(EntityDamageByEntityEvent event) {
        boolean nativeProjectile = marked(event.getDamager()) && (!(event.getDamager() instanceof Arrow arrow)
                || !Effects.projectileTargetAllowed(arrow, event.getEntity()));
        if (nativeProjectile || (event.getDamager() == exploding
                && event.getCause() == EntityDamageEvent.DamageCause.ENTITY_EXPLOSION)) event.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void knockback(EntityKnockbackEvent event) {
        if (exploding != null && event.getCause() == EntityKnockbackEvent.Cause.EXPLOSION)
            event.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void prime(ExplosionPrimeEvent event) {
        if (marked(event.getEntity())) { event.setFire(false); event.setCancelled(true); }
    }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void explode(EntityExplodeEvent event) {
        if (marked(event.getEntity()) || event.getEntity() == exploding) event.blockList().clear();
    }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void splash(PotionSplashEvent event) {
        if (marked(event.getPotion())) event.setCancelled(true);
    }
    @EventHandler public void disable(PluginDisableEvent event) {
        if (!event.getPlugin().getName().equals("CustomDungeons")) return;
        for (var projectile : List.copyOf(spawned)) projectile.remove();
        spawned.clear(); flights.clear();
    }
}
