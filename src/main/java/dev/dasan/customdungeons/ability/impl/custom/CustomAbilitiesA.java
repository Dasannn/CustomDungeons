package dev.dasan.customdungeons.ability.impl.custom;

import dev.dasan.customdungeons.ability.*;
import java.util.List;
import org.bukkit.*;
import org.bukkit.event.Listener;

public final class CustomAbilitiesA {
    private CustomAbilitiesA() {}
    public static void register(AbilityRegistry r) {
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
        double density = Effects.particleDensity();
        if (density <= 0) return;
        var data = Material.IRON_CHAIN.createBlockData();
        for (var player : EffectAudience.viewers(ctx.session(), at, Effects.viewRadius())) {
            player.spawnParticle(Particle.BLOCK, at.clone().add(0, 0.5, 0),
                    (int) Math.min(256, Math.max(1, Math.round(16 * density))), 0.3, 0.5, 0.3, 0, data);
        }
    }
}
