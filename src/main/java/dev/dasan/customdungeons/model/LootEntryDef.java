package dev.dasan.customdungeons.model;

import org.bukkit.inventory.ItemStack;

/** Immutable stack template; probabilities belong to the entry, never to item metadata. */
public record LootEntryDef(ItemStack item, double chance) {
    public LootEntryDef { item = item == null ? null : item.clone(); }
    @Override public ItemStack item() { return item == null ? null : item.clone(); }
}
