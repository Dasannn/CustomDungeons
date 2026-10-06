package dev.dasan.customdungeons.mob;

import dev.dasan.customdungeons.config.PluginConfig;
import dev.dasan.customdungeons.model.EquipmentDef;
import dev.dasan.customdungeons.model.MobTemplate;
import dev.dasan.customdungeons.model.PotionDef;
import dev.dasan.customdungeons.runtime.ActiveMob;
import dev.dasan.customdungeons.runtime.SessionContext;
import dev.dasan.customdungeons.text.Text;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Mob;
import org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;

/** Main-thread entity creation and the shared equipment/potion application for phases. */
public final class MobFactory {
    private final PluginConfig config;

    public MobFactory(PluginConfig config) { this.config = Objects.requireNonNull(config); }

    PluginConfig.PerformanceLimits performanceLimits() { return config.limits(); }

    int musicLengthTicks(String key) {
        return Math.max(1, config.musicLengthTicks().getOrDefault(key, 2400));
    }

    public ActiveMob spawn(MobTemplate template, Location at, SessionContext session, double healthMultiplier) {
        if (!Double.isFinite(healthMultiplier) || healthMultiplier < 1) {
            throw new IllegalArgumentException("healthMultiplier must be finite and >= 1");
        }
        String typeName = template.entityType().toUpperCase(Locale.ROOT);
        if (typeName.startsWith("MINECRAFT:")) typeName = typeName.substring(10);
        EntityType type = EntityType.valueOf(typeName);
        Class<? extends org.bukkit.entity.Entity> entityClass = type.getEntityClass();
        if (!type.isAlive() || !type.isSpawnable() || entityClass == null || !Mob.class.isAssignableFrom(entityClass)) {
            throw new IllegalArgumentException("Template is not a spawnable Mob: " + template.id());
        }
        Mob entity = Objects.requireNonNull(at.getWorld(), "Spawn world").spawn(at,
                entityClass.asSubclass(Mob.class), SpawnReason.CUSTOM, false, mob -> {
                    mob.getPersistentDataContainer().set(MobKeys.SESSION, PersistentDataType.STRING, session.id().toString());
                    mob.getPersistentDataContainer().set(MobKeys.TEMPLATE, PersistentDataType.STRING, template.id());
                    mob.setRemoveWhenFarAway(false);
                    mob.setPersistent(false);
                    mob.customName(Text.parse(template.displayName()));
                    mob.setCustomNameVisible(!template.displayName().isBlank());
                    setAttribute(mob, Attribute.MAX_HEALTH, template.maxHealth() * healthMultiplier);
                    setAttribute(mob, Attribute.ATTACK_DAMAGE, template.damage());
                    setAttribute(mob, Attribute.MOVEMENT_SPEED, template.speed());
                    setAttribute(mob, Attribute.KNOCKBACK_RESISTANCE, template.knockbackResistance());
                    setAttribute(mob, Attribute.SCALE, template.scale());
                    var max = mob.getAttribute(Attribute.MAX_HEALTH);
                    if (max != null) mob.setHealth(max.getValue());
                    applyEquipment(mob, template.equipment());
                    applyPotions(mob, template.potions());
                });
        return new ActiveMob(entity, template, session);
    }

    private static void setAttribute(Mob entity, Attribute attribute, double value) {
        var instance = entity.getAttribute(attribute);
        if (value > 0 && instance != null) instance.setBaseValue(value);
    }

    void applyEquipment(Mob entity, Map<EquipmentSlot, EquipmentDef> equipment) {
        var target = entity.getEquipment();
        if (target == null) return;
        boolean warned = false;
        for (var entry : equipment.entrySet()) {
            EquipmentSlot slot = entry.getKey();
            if (slot != EquipmentSlot.HAND && slot != EquipmentSlot.OFF_HAND
                    && !config.armorCapable().contains(entity.getType())) {
                if (!warned) {
                    Bukkit.getLogger().warning("[CustomDungeons] Armor ignored for non armor-capable mob: " + entity.getType());
                    warned = true;
                }
                continue;
            }
            target.setItem(slot, entry.getValue().item());
            target.setDropChance(slot, entry.getValue().dropChance());
        }
    }

    /** Remove first so Bukkit cannot retain a stronger effect of the same type. */
    static <T> void replacePotion(T type, Consumer<T> remove, Runnable add) {
        remove.accept(type);
        add.run();
    }

    void applyPotions(Mob entity, List<PotionDef> potions) {
        for (PotionDef potion : potions) {
            NamespacedKey key = NamespacedKey.fromString(potion.effectKey().toLowerCase(Locale.ROOT));
            var type = key == null ? null : Registry.POTION_EFFECT_TYPE.get(key);
            if (type == null) throw new IllegalArgumentException("Unknown potion effect: " + potion.effectKey());
            replacePotion(type, entity::removePotionEffect, () -> entity.addPotionEffect(
                    new PotionEffect(type, PotionEffect.INFINITE_DURATION,
                            potion.amplifier(), false, potion.particles(), potion.particles())));
        }
    }
}
