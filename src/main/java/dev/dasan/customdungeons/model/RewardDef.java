package dev.dasan.customdungeons.model;

import java.util.List;
import org.bukkit.inventory.ItemStack;

public record RewardDef(List<ItemStack> items, double money, int xp, List<String> commands) {
    public RewardDef {
        items = items.stream().map(ItemStack::clone).toList();
        commands = List.copyOf(commands);
    }
    @Override public List<ItemStack> items() { return items.stream().map(ItemStack::clone).toList(); }
}
