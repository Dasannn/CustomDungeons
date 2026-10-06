package dev.dasan.customdungeons.ability.impl.borrowed;

import dev.dasan.customdungeons.ability.*;
import java.util.*;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.*;
import org.bukkit.potion.*;

public final class DragonBreathAbility implements Ability, Listener {
    private final Map<DragonFireball, AbilityContext> flights = new WeakHashMap<>();
    private final Map<AreaEffectCloud, AbilityContext> clouds = new WeakHashMap<>();
    public String id() { return "dragon_breath"; }
    public Material icon() { return Material.DRAGON_BREATH; }
    public List<ParamSpec> params() { return List.of(
        new ParamSpec("radius", ParamType.DOUBLE, 3.0, 0.5, 16),
        new ParamSpec("durationTicks", ParamType.TICKS, 100, 1, 1200),
        new ParamSpec("damagePerTick", ParamType.DOUBLE, 1.0, 0, 100)); }
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
            // Keep native cloud particles invisible; Effects sends the visual only to nearby participants.
            c.setParticle(Particle.BLOCK, Material.AIR.createBlockData());
            c.addCustomEffect(new PotionEffect(PotionEffectType.INSTANT_DAMAGE, 1, 0), true);
            BorrowedAbilitiesA.mark(c, ctx.caster(), "__dragon_breath");
        });
        clouds.put(cloud, ctx);
        pulse(cloud, ctx, ctx.params().getInt("durationTicks"));
    }
    private void pulse(AreaEffectCloud cloud, AbilityContext ctx, int remaining) {
        if (remaining <= 0 || !cloud.isValid() || ctx.session().players().isEmpty()) {
            clouds.remove(cloud); cloud.remove(); return;
        }
        var at = cloud.getLocation();
        Effects.particles(ctx.session(), at, Particle.DRAGON_BREATH, 8, cloud.getRadius() / 2);
        for (var player : ctx.session().players()) {
            if (BorrowedAbilitiesA.allowed(ctx.caster(), player) && player.getWorld().equals(at.getWorld())
                    && Math.abs(player.getLocation().getY() - at.getY()) <= 2) {
                var delta = player.getLocation().toVector().subtract(at.toVector()).setY(0);
                if (delta.lengthSquared() <= cloud.getRadius() * cloud.getRadius())
                    Effects.damage(player, ctx.params().getDouble("damagePerTick"), ctx.caster());
            }
        }
        ctx.session().scheduler().runLater(1, () -> pulse(cloud, ctx, remaining - 1));
    }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void nativeCloud(com.destroystokyo.paper.event.entity.EnderDragonFireballHitEvent event) {
        if (Effects.marked(event.getEntity())) event.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void apply(AreaEffectCloudApplyEvent event) {
        if (clouds.containsKey(event.getEntity())) event.getAffectedEntities().clear();
    }
}
