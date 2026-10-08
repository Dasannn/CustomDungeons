package dev.dasan.customdungeons.mob;

import dev.dasan.customdungeons.ability.AbilityRegistry;
import dev.dasan.customdungeons.config.PluginConfig.PerformanceLimits;
import dev.dasan.customdungeons.model.MobTemplate;
import dev.dasan.customdungeons.model.RewardDef;
import dev.dasan.customdungeons.text.Messages;
import java.util.Map;
import java.util.Set;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;

/** Plugin services exposed to mobs, without dungeon ownership or persistence details. */
public interface MobsPlatform {
    String PDC_NAMESPACE = "customdungeons";

    static NamespacedKey key(String value) { return new NamespacedKey(PDC_NAMESPACE, value); }

    Messages messages();
    PerformanceLimits limits();
    Map<String, MobTemplate> templates();
    AbilityRegistry abilityRegistry();
    Set<EntityType> armorCapable();
    int musicLengthTicks(String key);
    void deliverReward(Player player, RewardDef reward);
}
