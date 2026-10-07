package dev.dasan.customdungeons.ability.impl.custom;

import dev.dasan.customdungeons.ability.*;
import java.util.List;
import org.bukkit.*;
import org.bukkit.entity.Fireball;
import org.bukkit.util.Vector;

public final class MeteorsAbility implements Ability, org.bukkit.event.Listener {
    private final java.util.Map<java.util.UUID, java.util.Set<Fireball>> visuals = new java.util.HashMap<>();
    @org.bukkit.event.EventHandler(ignoreCancelled=true)
    public void death(org.bukkit.event.entity.EntityDeathEvent event) { cleanup(event.getEntity().getUniqueId()); }
    @org.bukkit.event.EventHandler
    public void removed(com.destroystokyo.paper.event.entity.EntityRemoveFromWorldEvent event) { cleanup(event.getEntity().getUniqueId()); }
    @org.bukkit.event.EventHandler(priority = org.bukkit.event.EventPriority.HIGHEST)
    public void impactDamage(org.bukkit.event.entity.EntityDamageByEntityEvent event) {
        if (visuals.values().stream().anyMatch(entities -> entities.contains(event.getDamager())))
            event.setCancelled(true); // Only the configured radial impact may deal damage.
    }
    @org.bukkit.event.EventHandler(priority = org.bukkit.event.EventPriority.HIGHEST)
    public void hit(org.bukkit.event.entity.ProjectileHitEvent event) {
        if (visuals.values().stream().anyMatch(entities -> entities.contains(event.getEntity())))
            event.setCancelled(true);
    }
    private void cleanup(java.util.UUID owner) {
        var entities = visuals.remove(owner);
        if (entities != null) java.util.List.copyOf(entities).forEach(org.bukkit.entity.Entity::remove);
    }
    public String id() { return "meteors"; }
    public Material icon() { return Material.FIRE_CHARGE; }
    public List<ParamSpec> params() { return List.of(new ParamSpec("count", ParamType.INT, 3, 1, 10),
        new ParamSpec("radius", ParamType.DOUBLE, 3.0, 0.5, 16), new ParamSpec("damage", ParamType.DOUBLE, 8.0, 0, 1000),
        new ParamSpec("telegraphTicks", ParamType.TICKS, 40, 1, 1200)); }
    public void execute(AbilityContext ctx) {
        var targets = ctx.targets().stream().filter(t -> CustomAbilitiesB.participant(ctx, t)).toList();
        if (targets.isEmpty()) return;
        for (int i = 0; i < ctx.params().getInt("count"); i++) {
            Location center = targets.get(i % targets.size()).getLocation().clone();
            Telegraph.show(ctx.caster(), center, ctx.params().getDouble("radius"), ctx.params().getInt("telegraphTicks"),
                    Particle.FLAME, () -> fall(ctx, center), () -> {});
        }
    }
    private void fall(AbilityContext ctx, Location center) {
        Fireball ball = Effects.launch(ctx.caster(), Fireball.class, new Vector(0, -1, 0));
        visuals.computeIfAbsent(ctx.caster().entity().getUniqueId(), key -> new java.util.HashSet<>()).add(ball);
        ball.setPersistent(false);
        ball.teleport(center.clone().add(0, 20, 0));
        ball.setDirection(new Vector(0, -1, 0));
        ball.setAcceleration(new Vector(0, 0, 0));
        ball.setVelocity(new Vector(0, 0, 0));
        ball.setIsIncendiary(false); ball.setYield(0);
        // Manual flight and impact keep damage and terrain independent of vanilla explosions.
        descend(ctx, center, ball, 0);
    }
    private void remove(AbilityContext ctx, Fireball ball) {
        ball.remove();
        var owner = ctx.caster().entity().getUniqueId();
        var entities = visuals.get(owner);
        if (entities != null) {
            entities.remove(ball);
            if (entities.isEmpty()) visuals.remove(owner);
        }
    }
    private void descend(AbilityContext ctx, Location center, Fireball ball, int ticks) {
        if (!CustomAbilitiesB.alive(ctx.caster())) { remove(ctx, ball); return; }
        if (ticks == 20) {
            remove(ctx, ball);
            CustomAbilitiesB.blast(ctx, center, ctx.params().getDouble("radius"), ctx.params().getDouble("damage"), 0);
            return;
        }
        if (ball.isValid()) ball.teleport(center.clone().add(0, 20 - ticks, 0));
        ctx.session().scheduler().runLater(1, () -> descend(ctx, center, ball, ticks + 1));
    }
}
