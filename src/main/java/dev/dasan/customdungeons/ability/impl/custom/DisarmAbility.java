package dev.dasan.customdungeons.ability.impl.custom;

import dev.dasan.customdungeons.ability.*;
import dev.dasan.customdungeons.ability.impl.borrowed.BorrowedAbilitiesC;
import java.util.List;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

public final class DisarmAbility implements Ability {
    public String id() { return "disarm"; }
    public Material icon() { return Material.IRON_SWORD; }
    public List<ParamSpec> params() { return List.of(
            new ParamSpec("distance", ParamType.DOUBLE, 4.0, 3, 5),
            new ParamSpec("pickupDelay", ParamType.TICKS, 40, 0, 200)); }
    public void execute(AbilityContext ctx) {
        for (var target : BorrowedAbilitiesC.targets(ctx)) {
            var player = (Player) target;
            var inventory = player.getInventory();
            var held = inventory.getItemInMainHand();
            if (held.getType().isAir()) continue;
            var from = player.getLocation();
            var away = from.toVector().subtract(ctx.caster().entity().getLocation().toVector()).setY(0);
            if (away.lengthSquared() < 1e-8) away = new Vector(0, 0, 1);
            away.normalize().multiply(ctx.params().getDouble("distance"));
            // Place on safe ground rather than throwing into lava, walls or the void.
            for (int turn = 0; turn < 8; turn++) {
                var at = from.clone().add(away.clone().rotateAroundY(turn * Math.PI / 4));
                if (!BorrowedAbilitiesC.safe(ctx, at)) continue;
                var dropped = at.getWorld().dropItem(at, held.clone(), item -> {
                    item.setOwner(player.getUniqueId());
                    item.setCanMobPickup(false);
                    item.setPickupDelay(ctx.params().getInt("pickupDelay"));
                    item.setInvulnerable(true);
                    item.setUnlimitedLifetime(true);
                    item.setVelocity(new Vector());
                });
                if (dropped.isValid()) inventory.setItemInMainHand(null);
                break;
            }
        }
    }
}
