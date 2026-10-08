package dev.dasan.customdungeons.ability.impl.custom;

import dev.dasan.customdungeons.ability.*;
import java.util.*;
import org.bukkit.*;
import org.bukkit.entity.BlockDisplay;

public final class EarthquakeAbility implements Ability, org.bukkit.event.Listener {
    private final java.util.Map<java.util.UUID, java.util.Set<BlockDisplay>> visuals = new java.util.HashMap<>();
    @org.bukkit.event.EventHandler
    public void death(org.bukkit.event.entity.EntityDeathEvent event) { cleanup(event.getEntity().getUniqueId()); }
    @org.bukkit.event.EventHandler
    public void removed(com.destroystokyo.paper.event.entity.EntityRemoveFromWorldEvent event) { cleanup(event.getEntity().getUniqueId()); }
    private void cleanup(java.util.UUID owner) {
        var entities = visuals.remove(owner);
        if (entities != null) java.util.List.copyOf(entities).forEach(org.bukkit.entity.Entity::remove);
    }
    public String id() { return "earthquake"; }
    public Material icon() { return Material.DIRT; }
    public List<ParamSpec> params() { return List.of(new ParamSpec("radius", ParamType.DOUBLE, 6.0, 1, 16),
        new ParamSpec("damage", ParamType.DOUBLE, 6.0, 0, 1000), new ParamSpec("up", ParamType.DOUBLE, 0.7, 0, 3)); }
    public void execute(AbilityContext ctx) {
        Location origin = ctx.caster().entity().getLocation();
        double radius = ctx.params().getDouble("radius");
        var plugin = Bukkit.getPluginManager().getPlugin("CustomDungeons");
        if (plugin == null) return;
        double viewRadius = Effects.viewRadius();
        List<BlockDisplay> displays = new ArrayList<>();
        List<Location> bases = new ArrayList<>();
        for (int i = 0; i < 24; i++) {
            double angle = i * Math.PI * 2 / 24;
            Location at = origin.clone().add(Math.cos(angle) * radius, -1, Math.sin(angle) * radius);
            if (!origin.getWorld().isChunkLoaded(at.getBlockX() >> 4, at.getBlockZ() >> 4)) continue;
            var region = ctx.session().area();
            if (region != null && !region.contains(at)) continue;
            var block = at.getBlock();
            if (block.getType().isAir()) continue;
            Location base = block.getLocation();
            BlockDisplay display = origin.getWorld().spawn(base, BlockDisplay.class, d -> {
                dev.dasan.customdungeons.mob.OwnedEntities.mark(d,ctx.caster());
                d.setBlock(block.getBlockData()); d.setPersistent(false); d.setVisibleByDefault(false);
            });
            displays.add(display); bases.add(base);
            for (var player : ctx.session().players()) if (CustomAbilitiesB.participant(ctx, player)
                    && player.getLocation().distanceSquared(base) <= viewRadius * viewRadius) player.showEntity(plugin, display);
        }
        visuals.computeIfAbsent(ctx.caster().entity().getUniqueId(), key -> new HashSet<>()).addAll(displays);
        animate(ctx, displays, bases, 0);
        CustomAbilitiesB.blast(ctx, origin, radius, ctx.params().getDouble("damage"), ctx.params().getDouble("up"));
    }
    private void animate(AbilityContext ctx, List<BlockDisplay> displays, List<Location> bases, int tick) {
        if (tick >= 20 || !CustomAbilitiesB.alive(ctx.caster())) {
            displays.forEach(BlockDisplay::remove);
            var owner = ctx.caster().entity().getUniqueId();
            var entities = visuals.get(owner);
            if (entities != null) {
                entities.removeAll(displays);
                if (entities.isEmpty()) visuals.remove(owner);
            }
            return;
        }
        for (int i = 0; i < displays.size(); i++) {
            double height = Math.max(0, Math.sin(Math.PI * (tick - i / 3.0) / 20));
            displays.get(i).teleport(bases.get(i).clone().add(0, height, 0));
        }
        ctx.session().scheduler().runLater(1, () -> animate(ctx, displays, bases, tick + 1));
    }
}
