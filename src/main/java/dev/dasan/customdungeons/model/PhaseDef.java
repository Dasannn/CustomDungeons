package dev.dasan.customdungeons.model;

import java.util.List;
import java.util.Map;
import org.bukkit.inventory.EquipmentSlot;
import org.jspecify.annotations.Nullable;

public record PhaseDef(double healthThreshold, boolean replaceAbilities,
                       List<AbilityInstance> abilities, List<ComboDef> combos,
                       Map<EquipmentSlot, EquipmentDef> equipment, List<PotionDef> potions,
                       double healPercent, List<WaveEntry> summons, @Nullable String title,
                       @Nullable String subtitle, @Nullable String soundKey, @Nullable String musicKey,
                       int invulnerableTicks) {
    public PhaseDef {
        abilities = List.copyOf(abilities);
        combos = List.copyOf(combos);
        equipment = Map.copyOf(equipment);
        potions = List.copyOf(potions);
        summons = List.copyOf(summons);
    }
}
