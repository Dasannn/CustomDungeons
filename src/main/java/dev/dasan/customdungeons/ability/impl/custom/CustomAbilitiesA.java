package dev.dasan.customdungeons.ability.impl.custom;

import dev.dasan.customdungeons.ability.*;
import java.util.List;
import org.bukkit.*;
import org.bukkit.event.Listener;

public final class CustomAbilitiesA {
    private static double viewRadius = 48, density = 1;
    private CustomAbilitiesA() {}
    public static void register(AbilityRegistry r) {
        if (Bukkit.getServer() != null) {
            var plugin = Bukkit.getPluginManager().getPlugin("CustomDungeons");
            if (plugin != null) {
                double radius = plugin.getConfig().getDouble("performance.effect-view-radius", 48);
                double amount = plugin.getConfig().getDouble("performance.particle-density", 1);
                viewRadius = Double.isFinite(radius) ? Math.max(0, radius) : 48;
                density = Double.isFinite(amount) ? Math.clamp(amount, 0, 10) : 1;
            }
        }
        for (Ability a : List.of(new HookAbility(), new AnchorAbility(), new FreezeAbility(),
                new SwapAbility(), new ChaosAbility(), new DisarmAbility())) {
            r.register(a);
            if (a instanceof Listener listener && Bukkit.getServer() != null) {
                var plugin = Bukkit.getPluginManager().getPlugin("CustomDungeons");
                if (plugin == null) throw new IllegalStateException("CustomDungeons plugin unavailable");
                Bukkit.getPluginManager().registerEvents(listener, plugin);
                if (a instanceof AnchorAbility anchor)
                    for (var player : Bukkit.getOnlinePlayers()) anchor.clear(player);
            }
        }
    }
    static void chains(AbilityContext ctx, Location at) {
        if (density <= 0) return;
        var data = Material.IRON_CHAIN.createBlockData();
        for (var player : EffectAudience.viewers(ctx.session(), at, viewRadius)) {
            player.spawnParticle(Particle.BLOCK, at.clone().add(0, 0.5, 0),
                    (int) Math.min(256, Math.max(1, Math.round(16 * density))), 0.3, 0.5, 0.3, 0, data);
        }
    }
}
