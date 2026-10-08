package dev.dasan.customdungeons.model;

import java.util.List;
import java.util.Map;
import org.bukkit.inventory.EquipmentSlot;
import org.jspecify.annotations.Nullable;

public record MobTemplate(String id, String entityType, String displayName,
                          double maxHealth, double damage, double speed, double knockbackResistance,
                          double scale, Map<EquipmentSlot, EquipmentDef> equipment, List<PotionDef> potions,
                          List<AbilityInstance> abilities, List<ComboDef> combos, boolean boss,
                          String bossBarColor, @Nullable String musicKey, List<PhaseDef> phases,
                          boolean vanillaDrops, MobAttributes attributes, @Nullable WorldBossDef worldBoss) {
    public MobTemplate(String id, String entityType, String displayName, double maxHealth, double damage,
                       double speed, double knockbackResistance, double scale,
                       Map<EquipmentSlot, EquipmentDef> equipment, List<PotionDef> potions,
                       List<AbilityInstance> abilities, List<ComboDef> combos, boolean boss,
                       String bossBarColor, @Nullable String musicKey, List<PhaseDef> phases,
                       boolean vanillaDrops, MobAttributes attributes) {
        this(id,entityType,displayName,maxHealth,damage,speed,knockbackResistance,scale,equipment,potions,
                abilities,combos,boss,bossBarColor,musicKey,phases,vanillaDrops,attributes,null);
    }
    public MobTemplate withWorldBoss(@Nullable WorldBossDef value) {
        return new MobTemplate(id,entityType,displayName,maxHealth,damage,speed,knockbackResistance,scale,equipment,potions,
                abilities,combos,boss,bossBarColor,musicKey,phases,vanillaDrops,attributes,value);
    }
    public MobTemplate(String id, String entityType, String displayName, double maxHealth, double damage,
                       double speed, double knockbackResistance, double scale,
                       Map<EquipmentSlot, EquipmentDef> equipment, List<PotionDef> potions,
                       List<AbilityInstance> abilities, List<ComboDef> combos, boolean boss,
                       String bossBarColor, @Nullable String musicKey, List<PhaseDef> phases, boolean vanillaDrops) {
        this(id, entityType, displayName, maxHealth, damage, speed, knockbackResistance, scale,
                equipment, potions, abilities, combos, boss, bossBarColor, musicKey, phases, vanillaDrops, MobAttributes.EMPTY);
    }
    public MobTemplate {
        equipment = Map.copyOf(equipment);
        potions = List.copyOf(potions);
        abilities = List.copyOf(abilities);
        combos = List.copyOf(combos);
        phases = List.copyOf(phases);
    }
}
