package dev.dasan.customdungeons.ability.impl.custom;

import dev.dasan.customdungeons.ability.*;
import dev.dasan.customdungeons.ability.impl.borrowed.BorrowedAbilitiesC;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public final class ChaosAbility implements Ability {
    public String id() { return "chaos"; }
    public Material icon() { return Material.ENDER_EYE; }
    public List<ParamSpec> params() { return List.of(); }
    /** Fisher-Yates on a copy; every input slot appears exactly once in the result. */
    public static int[] shuffleHotbar(int[] slots, Random r) {
        int[] result = slots.clone();
        for (int i = result.length - 1; i > 0; i--) {
            int j = r.nextInt(i + 1);
            int old = result[i];
            result[i] = result[j];
            result[j] = old;
        }
        return result;
    }
    public void execute(AbilityContext ctx) {
        for (var target : BorrowedAbilitiesC.targets(ctx)) {
            var inventory = ((Player) target).getInventory();
            ItemStack[] hotbar = new ItemStack[9];
            for (int i = 0; i < 9; i++) hotbar[i] = inventory.getItem(i);
            int[] order = shuffleHotbar(new int[]{0, 1, 2, 3, 4, 5, 6, 7, 8}, ThreadLocalRandom.current());
            for (int i = 0; i < 9; i++) inventory.setItem(i, hotbar[order[i]]);
        }
    }
}
