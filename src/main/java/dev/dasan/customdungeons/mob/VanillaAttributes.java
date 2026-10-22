package dev.dasan.customdungeons.mob;

import java.util.*;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.EntityType;

/** Base attribute values of an unspawned entity, read from the running server (never hardcoded). */
public final class VanillaAttributes {
    private VanillaAttributes() {}
    private static final Map<EntityType,Map<String,Double>> cache=new EnumMap<>(EntityType.class);

    public static Attribute attribute(String key) {
        return switch(key) {
            case "max-health" -> Attribute.MAX_HEALTH;case "damage" -> Attribute.ATTACK_DAMAGE;
            case "speed" -> Attribute.MOVEMENT_SPEED;case "knockback-resistance" -> Attribute.KNOCKBACK_RESISTANCE;
            case "scale" -> Attribute.SCALE;case "armor" -> Attribute.ARMOR;case "armor-toughness" -> Attribute.ARMOR_TOUGHNESS;
            case "follow-range" -> Attribute.FOLLOW_RANGE;case "attack-knockback" -> Attribute.ATTACK_KNOCKBACK;
            case "jump-strength" -> Attribute.JUMP_STRENGTH;case "gravity" -> Attribute.GRAVITY;
            case "step-height" -> Attribute.STEP_HEIGHT;case "explosion-knockback-resistance" -> Attribute.EXPLOSION_KNOCKBACK_RESISTANCE;
            default -> throw new IllegalArgumentException("Unknown mob attribute");
        };
    }

    static void clearCache() {cache.clear();}

    /** Empty when the type lacks the attribute (e.g. no attack damage) or cannot be created. */
    public static OptionalDouble of(String entityType,String key,World world) {
        if(entityType==null||world==null)return OptionalDouble.empty();
        EntityType type;
        try {type=EntityType.valueOf(entityType.toUpperCase(Locale.ROOT).replace("MINECRAFT:",""));}
        catch(IllegalArgumentException unknown){return OptionalDouble.empty();}
        // computeIfAbsent keeps no entry when the server could not create the entity.
        var values=cache.computeIfAbsent(type,t->read(t,world));
        Double value=values==null?null:values.get(key);
        return value==null?OptionalDouble.empty():OptionalDouble.of(value);
    }

    private static Map<String,Double> read(EntityType type,World world) {
        var entityClass=type.getEntityClass();
        if(entityClass==null||!type.isAlive())return null;
        // Public API creates an unspawned entity; no events, mobs or chunk tickets are introduced.
        var entity=world.createEntity(new Location(world,0,world.getMinHeight()+1,0),entityClass);
        if(!(entity instanceof org.bukkit.attribute.Attributable attributable))return null;
        var values=new HashMap<String,Double>();
        for(String key:dev.dasan.customdungeons.model.MobAttributes.KEYS) {
            var instance=attributable.getAttribute(attribute(key));
            if(instance!=null)values.put(key,instance.getBaseValue());
        }
        return Map.copyOf(values);
    }
}
