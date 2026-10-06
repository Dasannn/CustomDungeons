package dev.dasan.customdungeons.ability.impl.custom;

import dev.dasan.customdungeons.ability.*;
import java.util.*;
import org.bukkit.*;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.*;
import com.destroystokyo.paper.event.entity.EntityRemoveFromWorldEvent;

public final class MinionShieldAbility implements Ability, Listener {
    private final Map<UUID, Set<UUID>> minions = new HashMap<>();
    private boolean spawning;
    @EventHandler
    public void death(EntityDeathEvent event) { minions.remove(event.getEntity().getUniqueId()); }
    @EventHandler
    public void removed(EntityRemoveFromWorldEvent event) { minions.remove(event.getEntity().getUniqueId()); }
    public String id() { return "minion_shield"; }
    public Material icon() { return Material.SHIELD; }
    public List<ParamSpec> params() { return List.of(new ParamSpec("template", ParamType.MOB_TEMPLATE, "", 0, 0), new ParamSpec("count", ParamType.INT, 3, 1, 20)); }
    public void execute(AbilityContext ctx) {
        if (spawning) return;
        UUID owner = ctx.caster().entity().getUniqueId();
        if (!CustomAbilitiesB.alive(ctx.caster())) { minions.remove(owner); return; }
        Set<UUID> ids = minions.get(owner);
        if (ids == null) {
            ids = new HashSet<>();
            String template = ctx.params().getString("template");
            spawning = true;
            try {
                if (!template.isBlank()) for (int i = 0; i < ctx.params().getInt("count"); i++) {
                    var child = ctx.session().spawnMinion(template, ctx.caster().entity().getLocation(), ctx.caster());
                    if (child != null && CustomAbilitiesB.alive(child)) ids.add(child.entity().getUniqueId());
                }
            } finally { spawning = false; }
            minions.put(owner, ids);
            boolean previouslyInvulnerable = ctx.caster().entity().isInvulnerable();
            if (!ids.isEmpty()) ctx.caster().entity().setInvulnerable(true);
            watch(ctx, owner, previouslyInvulnerable);
        }
        retainLiving(ctx, ids);
        if (!ids.isEmpty() && ctx.cause() instanceof EntityDamageEvent event
                && event.getEntity().equals(ctx.caster().entity())) {
            event.setCancelled(true);
            Effects.particles(ctx.session(), ctx.caster().entity().getLocation().add(0, 1, 0), Particle.ENCHANT, 20, 0.8);
        }
    }
    private void retainLiving(AbilityContext ctx, Set<UUID> ids) {
        Set<UUID> alive = new HashSet<>();
        for (var mob : ctx.session().mobs()) if (CustomAbilitiesB.alive(mob)) alive.add(mob.entity().getUniqueId());
        ids.retainAll(alive);
    }
    private void watch(AbilityContext ctx, UUID owner, boolean previouslyInvulnerable) {
        if (!CustomAbilitiesB.alive(ctx.caster())) {
            minions.remove(owner); ctx.caster().entity().setInvulnerable(previouslyInvulnerable); return;
        }
        Set<UUID> ids = minions.get(owner);
        if (ids == null) return;
        retainLiving(ctx, ids);
        ctx.caster().entity().setInvulnerable(previouslyInvulnerable || !ids.isEmpty());
        if (ids.isEmpty()) { minions.remove(owner); return; }
        ctx.session().scheduler().runLater(1, () -> watch(ctx, owner, previouslyInvulnerable));
    }
}
