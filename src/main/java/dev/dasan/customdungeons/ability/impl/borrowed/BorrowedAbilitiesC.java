package dev.dasan.customdungeons.ability.impl.borrowed;

import dev.dasan.customdungeons.ability.*;
import dev.dasan.customdungeons.model.TargetMode;
import java.util.List;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;

/** Utilities local to T12; destinations never load chunks or cross room boundaries. */
public final class BorrowedAbilitiesC {
    private BorrowedAbilitiesC() {}
    public static void register(AbilityRegistry r) {
        for (Ability a : List.of(new EndermanBlinkAbility(), new RoarKnockbackAbility(),
                new LaunchUpAbility(), new CobwebAbility(), new SplitOnDeathAbility(), new BlindnessAbility()))
            r.register(a);
    }
    public static List<LivingEntity> targets(AbilityContext ctx) {
        var allowed = TargetSelector.select(ctx.caster(), TargetMode.ALL_IN_RADIUS, Double.MAX_VALUE);
        return ctx.targets().stream().filter(allowed::contains).distinct().toList();
    }
    public static boolean inRoom(AbilityContext ctx, Location at) {
        var room = ctx.session().area();
        return at.getWorld() != null && (room == null || room.contains(at));
    }
    public static boolean safe(AbilityContext ctx, Location at) {
        if (!inRoom(ctx, at) || !inRoom(ctx, at.clone().add(0, 1, 0))
                || !at.getWorld().isChunkLoaded(at.getBlockX() >> 4, at.getBlockZ() >> 4)
                || at.getBlockY() <= at.getWorld().getMinHeight()
                || at.getBlockY() + 1 >= at.getWorld().getMaxHeight()
                || !at.getWorld().getWorldBorder().isInside(at)) return false;
        var feet = at.getBlock();
        var floor = feet.getRelative(0, -1, 0);
        return feet.getType().isAir() && feet.getRelative(0, 1, 0).getType().isAir()
                && floor.isSolid() && !List.of(org.bukkit.Material.MAGMA_BLOCK,
                    org.bukkit.Material.CACTUS, org.bukkit.Material.CAMPFIRE,
                    org.bukkit.Material.SOUL_CAMPFIRE).contains(floor.getType());
    }
}
