package dev.dasan.customdungeons.mob;

import dev.dasan.customdungeons.model.EquipmentDef;
import dev.dasan.customdungeons.model.MobTemplate;
import dev.dasan.customdungeons.model.PotionDef;
import dev.dasan.customdungeons.runtime.ActiveMob;
import dev.dasan.customdungeons.mob.MobHost;
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
    private final MobsPlatform platform;

    public MobFactory(MobsPlatform platform) { this.platform = Objects.requireNonNull(platform); }

    dev.dasan.customdungeons.config.PluginConfig.PerformanceLimits performanceLimits() { return platform.limits(); }

    int musicLengthTicks(String key) {
        return platform.musicLengthTicks(key);
    }

    public ActiveMob spawn(MobTemplate template, Location at, MobHost session, double healthMultiplier) {
        return spawn(template,at,session,healthMultiplier,mob->{});
    }
    public ActiveMob spawn(MobTemplate template, Location at, MobHost session, double healthMultiplier, java.util.function.Consumer<Mob> initialize) {
        if (!dev.dasan.customdungeons.config.NumericRanges.SCALE.contains(template.scale())) {
            throw new IllegalArgumentException("scale must be finite and within 0..16");
        }
        var legacy=Map.of("health",template.maxHealth(),"damage",template.damage(),"speed",template.speed(),
                "resistance",template.knockbackResistance());
        legacy.forEach((key,value)->{
            if(!dev.dasan.customdungeons.config.NumericRanges.stat(key).contains(value))
                throw new IllegalArgumentException("Invalid mob stat");
        });
        template.attributes().values().forEach((key,value)->{
            if(!dev.dasan.customdungeons.config.NumericRanges.attribute(key).contains(value))
                throw new IllegalArgumentException("Invalid mob attribute");
        });
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
                    initialize.accept(mob);
                    mob.setRemoveWhenFarAway(false);
                    mob.setPersistent(false);
                    mob.customName(Text.parse(template.displayName()));
                    mob.setCustomNameVisible(!template.displayName().isBlank());
                    mob.getPersistentDataContainer().set(MobKeys.HEALTH_MULTIPLIER,PersistentDataType.DOUBLE,healthMultiplier);
                    var max=mob.getAttribute(Attribute.MAX_HEALTH);
                    if(max!=null) MobHealth.configure(mob,MobHealth.scaledMaximum(
                            template.attributes().values().getOrDefault("max-health",template.maxHealth()>0?template.maxHealth():max.getValue()),
                            healthMultiplier),false);
                    if(template.damage()>0) attackDamage(mob,template.damage());
                    if(template.speed()>0) setAttribute(mob,Attribute.MOVEMENT_SPEED,template.speed());
                    if(template.knockbackResistance()>0) setAttribute(mob,Attribute.KNOCKBACK_RESISTANCE,template.knockbackResistance());
                    if(template.scale()>0) setAttribute(mob,Attribute.SCALE,template.scale());
                    // Health was already scaled once above; apply the remaining optional overrides.
                    applyAttributes(mob,new dev.dasan.customdungeons.model.MobAttributes(template.attributes().values().entrySet().stream()
                            .filter(e->!e.getKey().equals("max-health")).collect(java.util.stream.Collectors.toMap(Map.Entry::getKey,Map.Entry::getValue))));
                    applyEquipment(mob, template.equipment());
                    applyPotions(mob, template.potions());
                });
        var active=new ActiveMob(entity, template, session);
        dev.dasan.customdungeons.intelligence.IntelligenceService.track(active);
        return active;
    }

    private static void setAttribute(Mob entity, Attribute attribute, double value) {
        var instance = entity.getAttribute(attribute);
        if (instance != null) instance.setBaseValue(value);
    }

    static void attackDamage(Mob entity,double damage) {
        setAttribute(entity,Attribute.ATTACK_DAMAGE,Math.min(2048,damage));
        var data=entity.getPersistentDataContainer();
        if(damage>2048) data.set(MobKeys.VIRTUAL_ATTACK_DAMAGE,PersistentDataType.DOUBLE,damage);
        else data.remove(MobKeys.VIRTUAL_ATTACK_DAMAGE);
    }
    void applyAttributes(Mob entity,dev.dasan.customdungeons.model.MobAttributes attributes) {
        attributes.values().forEach((key,value)->{
            if(!dev.dasan.customdungeons.config.NumericRanges.attribute(key).contains(value))
                throw new IllegalArgumentException("Invalid mob attribute");
            if(key.equals("max-health")) {
                Double multiplier=entity.getPersistentDataContainer().get(MobKeys.HEALTH_MULTIPLIER,PersistentDataType.DOUBLE);
                MobHealth.configure(entity,MobHealth.scaledMaximum(value,multiplier==null?1:multiplier),true);
            } else if(key.equals("damage")) attackDamage(entity,value);
            else setAttribute(entity,switch(key) {
                case "speed" -> Attribute.MOVEMENT_SPEED;case "knockback-resistance" -> Attribute.KNOCKBACK_RESISTANCE;
                case "scale" -> Attribute.SCALE;case "armor" -> Attribute.ARMOR;case "armor-toughness" -> Attribute.ARMOR_TOUGHNESS;
                case "follow-range" -> Attribute.FOLLOW_RANGE;case "attack-knockback" -> Attribute.ATTACK_KNOCKBACK;
                case "jump-strength" -> Attribute.JUMP_STRENGTH;case "gravity" -> Attribute.GRAVITY;
                case "step-height" -> Attribute.STEP_HEIGHT;case "explosion-knockback-resistance" -> Attribute.EXPLOSION_KNOCKBACK_RESISTANCE;
                default -> throw new IllegalArgumentException("Unknown mob attribute");
            },value);
        });
    }
    void applyEquipment(Mob entity, Map<EquipmentSlot, EquipmentDef> equipment) {
        var target = entity.getEquipment();
        if (target == null) return;
        boolean warned = false;
        for (var entry : equipment.entrySet()) {
            EquipmentSlot slot = entry.getKey();
            if (slot != EquipmentSlot.HAND && slot != EquipmentSlot.OFF_HAND
                    && !platform.armorCapable().contains(entity.getType())) {
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
