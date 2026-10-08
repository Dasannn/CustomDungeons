package dev.dasan.customdungeons.mob;

import dev.dasan.customdungeons.ability.AbilityRegistry;
import dev.dasan.customdungeons.config.PluginConfig;
import dev.dasan.customdungeons.model.MobTemplate;
import dev.dasan.customdungeons.model.RewardDef;
import dev.dasan.customdungeons.text.Messages;
import java.util.Map;
import java.util.Set;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;

/** Mob-only view of the existing test configuration fixtures. */
public record TestMobsPlatform(PluginConfig config, Map<String, MobTemplate> templates,
                               Messages messages, AbilityRegistry abilityRegistry) implements MobsPlatform {
    public static MobsPlatform of(PluginConfig config) {
        return new TestMobsPlatform(config, Map.of(), null, new AbilityRegistry());
    }
    @Override public PluginConfig.PerformanceLimits limits() { return config.limits(); }
    @Override public Set<EntityType> armorCapable() { return config.armorCapable(); }
    @Override public int musicLengthTicks(String key) { return Math.max(1, config.musicLengthTicks().getOrDefault(key, 2400)); }
    @Override public void deliverReward(Player player, RewardDef reward) { throw new UnsupportedOperationException(); }
}
