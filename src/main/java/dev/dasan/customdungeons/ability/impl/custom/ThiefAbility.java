package dev.dasan.customdungeons.ability.impl.custom;

import dev.dasan.customdungeons.ability.*;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.*;
import org.bukkit.util.Vector;

public final class ThiefAbility implements Ability {
    public String id() { return "thief"; }
    public Material icon() { return Material.CHEST; }
    public List<ParamSpec> params() { return List.of(
        new ParamSpec("fleeTicks", ParamType.TICKS, 60, 1, 1200),
        new ParamSpec("speedAmplifier", ParamType.INT, 1, 0, 10)); }
    public static int pickStealSlot(ItemStack[] hotbar, Random r) {
        int[] choices = new int[Math.min(9, hotbar.length)];
        int count = 0;
        for (int i = 0; i < choices.length; i++) {
            ItemStack item = hotbar[i];
            if (item != null && item.getType() != Material.AIR && item.getType() != Material.CAVE_AIR && item.getType() != Material.VOID_AIR && item.getAmount() > 0) choices[count++] = i;
        }
        return count == 0 ? -1 : choices[r.nextInt(count)];
    }
    public void execute(AbilityContext ctx) {
        var player = CustomAbilitiesB.hitPlayer(ctx);
        if (player == null) return;
        var inventory = player.getInventory();
        int slot = pickStealSlot(inventory.getContents(), ThreadLocalRandom.current());
        if (slot < 0) return;
        ItemStack stolen = inventory.getItem(slot).clone();
        // Register before removing: a failed handoff cannot lose the player's item.
        // The session also owns restoration when the encounter ends, and offline claims.
        ctx.session().onItemStolen(player.getUniqueId(), stolen, ctx.caster());
        inventory.setItem(slot, null);
        int ticks = ctx.params().getInt("fleeTicks");
        ctx.caster().entity().addPotionEffect(new PotionEffect(PotionEffectType.SPEED, ticks,
                ctx.params().getInt("speedAmplifier")));
        Vector away = ctx.caster().entity().getLocation().toVector().subtract(player.getLocation().toVector()).setY(0);
        if (away.lengthSquared() == 0) away = new Vector(1, 0, 0);
        flee(ctx, away.normalize().multiply(0.4), ticks);
    }
    private void flee(AbilityContext ctx, Vector away, int remaining) {
        if (!CustomAbilitiesB.alive(ctx.caster()) || remaining <= 0) return;
        ctx.caster().entity().setTarget(null);
        ctx.caster().entity().setVelocity(away.clone().setY(ctx.caster().entity().getVelocity().getY()));
        int delay = Math.min(2, remaining);
        ctx.session().scheduler().runLater(delay, () -> flee(ctx, away, remaining - delay));
    }
}
