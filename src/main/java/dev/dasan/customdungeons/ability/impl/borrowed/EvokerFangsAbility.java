package dev.dasan.customdungeons.ability.impl.borrowed;

import dev.dasan.customdungeons.ability.*;
import dev.dasan.customdungeons.mob.MobArea;
import java.util.*;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.*;
import org.bukkit.util.Vector;

public final class EvokerFangsAbility implements Ability, Listener {
    private final Map<EvokerFangs, AbilityContext> fangs = new WeakHashMap<>();
    public String id() { return "evoker_fangs"; }
    public Material icon() { return Material.EVOKER_SPAWN_EGG; }
    public List<ParamSpec> params() { return List.of(
        new ParamSpec("pattern", ParamType.STRING, "LINE", 0, 0),
        new ParamSpec("count", ParamType.INT, 8, 1, 32),
        new ParamSpec("damage", ParamType.DOUBLE, 6.0, 0, 1000)); }
    /** Pure geometry; invalid patterns use LINE and vertical aim cannot distort spacing. */
    public static List<Vector> offsets(String pattern, int count, Vector aim) {
        Vector direction = aim.clone().setY(0);
        if (direction.lengthSquared() == 0) direction.setZ(1);
        direction.normalize();
        List<Vector> result = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            double angle = 2 * Math.PI * i / count;
            result.add("CIRCLE".equalsIgnoreCase(pattern)
                    ? new Vector(3 * Math.cos(angle), 0, 3 * Math.sin(angle))
                    : direction.clone().multiply(1.5 * (i + 1)));
        }
        return List.copyOf(result);
    }
    /** Pure world positions, filtered before any supporting blocks are inspected. */
    public static List<Vector> positions(String pattern, int count, Vector origin, Vector aim,
                                         String world, MobArea room) {
        return offsets(pattern, count, aim).stream().map(offset -> origin.clone().add(offset))
                .filter(at -> room == null || room.contains(world, (int) Math.floor(at.getX()),
                        (int) Math.floor(at.getY()), (int) Math.floor(at.getZ())))
                .toList();
    }
    public void execute(AbilityContext ctx) {
        var origin = ctx.caster().entity().getLocation();
        var targets = BorrowedAbilitiesA.targets(ctx, 64);
        Vector aim = targets.isEmpty() ? origin.getDirection()
                : targets.getFirst().getLocation().toVector().subtract(origin.toVector());
        int index = 0;
        var world = origin.getWorld();
        for (var position : positions(ctx.params().getString("pattern"), ctx.params().getInt("count"),
                origin.toVector(), aim, world.getName(), ctx.session().area())) {
            var at = new Location(world, position.getX(), position.getY(), position.getZ());
            if (!world.isChunkLoaded(at.getBlockX() >> 4, at.getBlockZ() >> 4)) continue;
            // Find a local supporting surface without modifying terrain.
            boolean grounded = false;
            for (int dy = 2; dy >= -3; dy--) {
                var block = at.clone().add(0, dy, 0).getBlock();
                if (block.isSolid() && block.getRelative(0, 1, 0).isPassable()) {
                    at.setY(block.getY() + 1); grounded = true; break;
                }
            }
            if (!grounded || !BorrowedAbilitiesA.inRoom(ctx.caster(), at)) continue;
            int delay = "CIRCLE".equalsIgnoreCase(ctx.params().getString("pattern")) ? 0 : index++ * 2;
            var fang = at.getWorld().spawn(at, EvokerFangs.class, f -> {
                f.setOwner(ctx.caster().entity()); f.setAttackDelay(delay);
                BorrowedAbilitiesA.mark(f, ctx.caster(), "__evoker_fangs");
            });
            fangs.put(fang, ctx);
            ctx.session().scheduler().runLater(delay + 40, () -> { fangs.remove(fang); fang.remove(); });
        }
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void damage(EntityDamageByEntityEvent event) {
        var ctx = fangs.get(event.getDamager());
        if (ctx == null) return;
        if (!BorrowedAbilitiesA.allowed(ctx.caster(), event.getEntity()) || ctx.params().getDouble("damage") <= 0)
            event.setCancelled(true);
        else event.setDamage(ctx.params().getDouble("damage"));
    }
}
