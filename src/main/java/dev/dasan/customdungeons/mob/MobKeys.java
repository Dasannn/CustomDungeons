package dev.dasan.customdungeons.mob;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.persistence.PersistentDataType;

public final class MobKeys {
    public static final NamespacedKey SESSION = key("session");
    public static final NamespacedKey TEMPLATE = key("template");
    public static final NamespacedKey ABILITY_PROJECTILE = key("ability_projectile");
    public static final NamespacedKey KEY_ITEM = key("key_item");
    public static final NamespacedKey VIRTUAL_MAX_HEALTH = key("virtual_max_health");
    public static final NamespacedKey VIRTUAL_HEALTH = key("virtual_health");
    public static final NamespacedKey PHYSICAL_HEALTH_SNAPSHOT = key("physical_health_snapshot");
    public static final NamespacedKey VIRTUAL_ATTACK_DAMAGE = key("virtual_attack_damage");
    public static final NamespacedKey HEALTH_MULTIPLIER = key("health_multiplier");
    public static final NamespacedKey TOOL = key("tool");

    private MobKeys() {}
    private static NamespacedKey key(String value) { return MobsPlatform.key(value); }

    public static boolean isDungeonMob(Entity entity) {
        var data = entity.getPersistentDataContainer();
        return entity instanceof org.bukkit.entity.Mob
                && data.has(SESSION, PersistentDataType.STRING)
                && data.has(TEMPLATE, PersistentDataType.STRING);
    }
}
