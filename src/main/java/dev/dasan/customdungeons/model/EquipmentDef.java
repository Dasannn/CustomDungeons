package dev.dasan.customdungeons.model;

import org.bukkit.inventory.ItemStack;

public record EquipmentDef(ItemStack item, float dropChance) {
    public EquipmentDef { item = item.clone(); }
    @Override public ItemStack item() { return item.clone(); }
}
