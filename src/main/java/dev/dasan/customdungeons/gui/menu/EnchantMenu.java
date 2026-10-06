package dev.dasan.customdungeons.gui.menu;

import dev.dasan.customdungeons.gui.*;
import dev.dasan.customdungeons.model.*;
import dev.dasan.customdungeons.ability.*;
import java.util.*;
import java.util.function.*;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.*;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

public final class EnchantMenu extends MobMenuBase {
    private final MobMenu.Loadout loadout;
    private final EquipmentSlot slot;
    public EnchantMenu(Player p, MobMenu.MobDraft d, MobMenu.Loadout l, EquipmentSlot slot, Menu parent) {
        super(p, "enchants", d, parent); loadout = l; this.slot = slot;
    }
    @Override protected int contentCount() { return EquipmentMenu.enchantAvailable(loadout, slot) ? (int) Registry.ENCHANTMENT.stream().count() : 0; }
    @Override protected MobMenu.Loadout summaryLoadout() { return loadout; }
    @Override protected void render() {
        var equipment = loadout.equipment.get(slot);
        if (!EquipmentMenu.enchantAvailable(loadout, slot)) {
            set(13, GuiTheme.unavailable(message("enchants"), message("equipment-required")));
            return;
        }
        var buttons = new ArrayList<Button>();
        for (var enchant : Registry.ENCHANTMENT) {
            int level = equipment.item().getEnchantmentLevel(enchant);
            buttons.add(entry(Material.ENCHANTED_BOOK, enchant.getKey() + " = " + level,
                () -> Inputs.number(viewer, label("choice", enchant.getKey()), 0, 255, level, v -> {
                    ItemStack item = equipment.item(); item.removeEnchantment(enchant);
                    if (v >= 1) item.addUnsafeEnchantment(enchant, (int)v);
                    loadout.equipment.put(slot, new EquipmentDef(item, equipment.dropChance()));
                }), () -> { ItemStack item = equipment.item(); item.removeEnchantment(enchant); loadout.equipment.put(slot, new EquipmentDef(item, equipment.dropChance())); }));
        }
        entries(buttons);
    }
}
