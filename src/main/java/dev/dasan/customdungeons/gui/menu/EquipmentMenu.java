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

/** Copies held items into the draft; no real item can become stranded in the GUI. */
public final class EquipmentMenu extends MobMenuBase {
    private final MobMenu.Loadout loadout;
    public EquipmentMenu(Player p, MobMenu.MobDraft d, MobMenu.Loadout l, Menu parent) {
        super(p, "equipment", d, parent); loadout = l;
    }
    @Override protected void render() {
        boolean armor = config().armorCapable().stream().anyMatch(t -> t.name().equalsIgnoreCase(data.type.replace("minecraft:", "")));
        var slots = armor ? List.of(EquipmentSlot.HAND, EquipmentSlot.OFF_HAND, EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET)
                : List.of(EquipmentSlot.HAND, EquipmentSlot.OFF_HAND);
        for (int i=0; i<slots.size(); i++) {
            EquipmentSlot slot = slots.get(i); EquipmentDef value = loadout.equipment.get(slot);
            Button base = Button.of(value == null ? Material.ARMOR_STAND : value.item().getType(), label("equipment-slot", slot),
                    List.of(message("equipment-slot-lore")), (p,c) -> MenuListener.instance().later(() -> {
                        if (c.isShiftClick()) { loadout.equipment.remove(slot); refresh(); }
                        else if (c.isRightClick()) new EnchantMenu(p, data, loadout, slot, this).open();
                        else {
                            ItemStack held = p.getInventory().getItemInMainHand();
                            if (!held.getType().isAir()) loadout.equipment.put(slot, new EquipmentDef(held, value == null ? 0 : value.dropChance()));
                            refresh();
                        }
                    }));
            if (value != null) {
                var presentation = base.icon().getItemMeta();
                ItemStack icon = value.item(); icon.editMeta(m -> { m.displayName(presentation.displayName()); m.lore(presentation.lore()); });
                base = new Button(icon, base.onClick());
            }
            set(10+i, base);
            if (value != null) number(19+i, "drop-chance", value.dropChance(), 0, 1,
                    v -> loadout.equipment.put(slot, new EquipmentDef(value.item(), (float)v)));
        }
        if (!armor) set(16, Button.of(Material.GRAY_DYE, message("no-armor"), List.of(message("no-armor-lore")), (p,c) -> {
            loadout.equipment.keySet().removeIf(s -> s != EquipmentSlot.HAND && s != EquipmentSlot.OFF_HAND); refresh();
        }));
    }
}
