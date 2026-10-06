package dev.dasan.customdungeons.ability.impl.borrowed;

import dev.dasan.customdungeons.ability.*;
import java.util.*;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.*;

public final class DragonBreathAbility implements Ability, Listener {
    private final Map<DragonFireball, AbilityContext> flights = new WeakHashMap<>();
    public String id() { return "dragon_breath"; }
    public Material icon() { return Material.DRAGON_BREATH; }
    public List<ParamSpec> params() { return List.of(
        new ParamSpec("radius", ParamType.DOUBLE, 3.0, 0.5, 16),
        new ParamSpec("durationTicks", ParamType.TICKS, 100, 1, 1200),
        new ParamSpec("damagePerHit", ParamType.DOUBLE, 1.0, 0, 100)); }
    public void execute(AbilityContext ctx) {
        for (var target : BorrowedAbilitiesA.targets(ctx, 64)) {
            var velocity = target.getEyeLocation().toVector().subtract(ctx.caster().entity().getEyeLocation().toVector());
            if (velocity.lengthSquared() == 0) continue;
            var ball = Effects.launch(ctx.caster(), DragonFireball.class, velocity.normalize().multiply(0.8));
            BorrowedAbilitiesA.mark(ball, ctx.caster(), "__dragon_breath");
            flights.put(ball, ctx);
            ctx.session().scheduler().runLater(100, () -> { flights.remove(ball); ball.remove(); });
        }
    }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void hit(ProjectileHitEvent event) {
        var ctx = flights.remove(event.getEntity());
        if (ctx == null) return;
        event.setCancelled(true);
        var at = event.getEntity().getLocation();
        event.getEntity().remove();
        if (event.getHitEntity() != null && !BorrowedAbilitiesA.allowed(ctx.caster(), event.getHitEntity())) return;
        if (!BorrowedAbilitiesA.inRoom(ctx.caster(), at)) return;
        var cloud = at.getWorld().spawn(at, AreaEffectCloud.class, c -> {
            c.setSource(ctx.caster().entity());
            c.setRadius((float) ctx.params().getDouble("radius"));
            c.setDuration(ctx.params().getInt("durationTicks"));
            c.setWaitTime(0);
            c.setRadiusPerTick(0);
            c.setRadiusOnUse(0);
            c.setDurationOnUse(0);
            // Visual-only cloud: no native potion damage, including to non-participants.
            c.setBasePotionType(null);
            c.clearCustomEffects();
            c.setParticle(Particle.DRAGON_BREATH, 1.0f);
            BorrowedAbilitiesA.mark(c, ctx.caster(), "__dragon_breath");
        });
        pulse(cloud, ctx, new PulsePlan(ctx.params().getInt("durationTicks"), cloud.getReapplicationDelay()), 0);
    }
    /** Hits at age 0, then every max(10, reapplicationDelay) ticks, strictly before expiration.
     * The 10-tick minimum respects vanilla damage immunity. The final callback removes the cloud
     * at durationTicks even when its lifetime is not a multiple of the hit interval.
     */
    public record PulsePlan(int durationTicks, int reapplicationDelay) {
        public int interval() { return Math.max(10, reapplicationDelay); }
        public boolean hitsAt(int elapsed) {
            return elapsed >= 0 && elapsed < durationTicks && elapsed % interval() == 0;
        }
        public int nextDelay(int elapsed) {
            return elapsed >= durationTicks ? 0 : Math.min(interval(), durationTicks - elapsed);
        }
    }
    private void pulse(AreaEffectCloud cloud, AbilityContext ctx, PulsePlan plan, int elapsed) {
        if (elapsed >= plan.durationTicks() || !cloud.isValid() || ctx.session().players().isEmpty()) {
            cloud.remove(); return;
        }
        var at = cloud.getLocation();
        if (plan.hitsAt(elapsed)) for (var player : ctx.session().players()) {
            if (BorrowedAbilitiesA.allowed(ctx.caster(), player) && player.getWorld().equals(at.getWorld())
                    && Math.abs(player.getLocation().getY() - at.getY()) <= 2) {
                var delta = player.getLocation().toVector().subtract(at.toVector()).setY(0);
                if (delta.lengthSquared() <= cloud.getRadius() * cloud.getRadius())
                    Effects.damage(player, ctx.params().getDouble("damagePerHit"), ctx.caster());
            }
        }
        int delay = plan.nextDelay(elapsed);
        ctx.session().scheduler().runLater(delay, () -> pulse(cloud, ctx, plan, elapsed + delay));
    }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void nativeCloud(com.destroystokyo.paper.event.entity.EnderDragonFireballHitEvent event) {
        if (Effects.marked(event.getEntity())) event.setCancelled(true);
    }
}
