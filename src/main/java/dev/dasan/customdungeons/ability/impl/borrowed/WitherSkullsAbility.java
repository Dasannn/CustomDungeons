package dev.dasan.customdungeons.ability.impl.borrowed;

import dev.dasan.customdungeons.ability.*;
import java.util.*;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.*;
import org.bukkit.potion.*;

public final class WitherSkullsAbility implements Ability, Listener {
    private final Map<WitherSkull, AbilityContext> flights = new WeakHashMap<>();
    public String id() { return "wither_skulls"; }
    public Material icon() { return Material.WITHER_SKELETON_SKULL; }
    public List<ParamSpec> params() { return List.of(
        new ParamSpec("count", ParamType.INT, 1, 1, 5),
        new ParamSpec("blue", ParamType.BOOLEAN, false, 0, 0),
        new ParamSpec("witherSeconds", ParamType.DOUBLE, 5.0, 0.05, 3600),
        new ParamSpec("damage", ParamType.DOUBLE, 8.0, 0, 1000)); }
    public void execute(AbilityContext ctx) {
        for (var target : BorrowedAbilitiesA.targets(ctx, 64)) {
            var velocity = target.getEyeLocation().toVector().subtract(ctx.caster().entity().getEyeLocation().toVector());
            if (velocity.lengthSquared() == 0) continue;
            velocity.normalize().multiply(0.8);
            for (int i = 0; i < ctx.params().getInt("count"); i++) {
                var skull = Effects.launch(ctx.caster(), WitherSkull.class, velocity);
                skull.setCharged(ctx.params().getBoolean("blue"));
                BorrowedAbilitiesA.mark(skull, ctx.caster(), "__wither_skulls");
                flights.put(skull, ctx);
                ctx.session().scheduler().runLater(100, () -> { flights.remove(skull); skull.remove(); });
            }
        }
    }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void hit(ProjectileHitEvent event) {
        var ctx = flights.remove(event.getEntity());
        if (ctx == null) return;
        event.setCancelled(true);
        event.getEntity().remove();
        if (event.getHitEntity() instanceof LivingEntity target && BorrowedAbilitiesA.allowed(ctx.caster(), target)) {
            Effects.damage(target, ctx.params().getDouble("damage"), ctx.caster());
            target.addPotionEffect(new PotionEffect(PotionEffectType.WITHER,
                    (int) Math.round(ctx.params().getDouble("witherSeconds") * 20), 0));
        }
    }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void prime(ExplosionPrimeEvent event) {
        if (event.getEntity() instanceof WitherSkull && Effects.marked(event.getEntity())) event.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void explode(EntityExplodeEvent event) {
        if (event.getEntity() instanceof WitherSkull && Effects.marked(event.getEntity())) {
            event.blockList().clear(); event.setCancelled(true);
        }
    }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void nativeDamage(EntityDamageByEntityEvent event) {
        if (flights.containsKey(event.getDamager())) event.setCancelled(true);
    }
}
