package dev.dasan.customdungeons.gui.menu;

import dev.dasan.customdungeons.gui.*;
import dev.dasan.customdungeons.model.*;
import java.util.*;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.*;

/** Copies equipment from the hand or real input slots, then returns the input items. */
public final class EquipmentMenu extends MobMenuBase implements org.bukkit.event.Listener {
    private static final List<EquipmentSlot> SLOTS = List.of(EquipmentSlot.HAND, EquipmentSlot.OFF_HAND,
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET);
    private final MobMenu.Loadout loadout;
    private boolean listening;

    private boolean armorCapable() {
        return config().armorCapable().stream().anyMatch(t -> t.name().equalsIgnoreCase(data.type.replace("minecraft:", "")));
    }
    @Override public boolean allowsPlacement(int slot) {
        return slot >= 28 && slot < 28 + (armorCapable() ? 6 : 2);
    }
    /** Input slots never contain presentation icons or draft copies. Preserve the real item's NBT. */
    void acceptPlacedItems() {
        for(int i=0;i<SLOTS.size();i++) {
            int input=28+i;
            ItemStack item=getInventory().getItem(input);
            if(item==null || item.getType().isAir()) continue;
            getInventory().setItem(input,null);
            EquipmentSlot slot=SLOTS.get(i);
            if(allowsPlacement(input) && viewer.hasPermission("customdungeons.admin.edit")) {
                EquipmentDef previous=loadout.equipment.get(slot);
                loadout.equipment.put(slot,new EquipmentDef(item,previous==null ? 0 : previous.dropChance()));
            }
            for(ItemStack leftover:viewer.getInventory().addItem(item).values())
                viewer.getWorld().dropItemNaturally(viewer.getLocation(),leftover);
        }
    }
    private void afterPlacement() {
        MenuListener.instance().later(() -> {
            acceptPlacedItems();
            if(viewer.getOpenInventory().getTopInventory()==getInventory()) refresh();
        });
    }
    @org.bukkit.event.EventHandler(priority=org.bukkit.event.EventPriority.MONITOR,ignoreCancelled=true)
    public void placed(org.bukkit.event.inventory.InventoryClickEvent event) {
        if(event.getView().getTopInventory()==getInventory() && event.getWhoClicked().equals(viewer)
                && allowsPlacement(event.getRawSlot())) afterPlacement();
    }
    @org.bukkit.event.EventHandler(priority=org.bukkit.event.EventPriority.MONITOR,ignoreCancelled=true)
    public void dragged(org.bukkit.event.inventory.InventoryDragEvent event) {
        if(event.getView().getTopInventory()==getInventory() && event.getWhoClicked().equals(viewer)
                && event.getRawSlots().stream().anyMatch(this::allowsPlacement)) afterPlacement();
    }
    @org.bukkit.event.EventHandler
    public void closed(org.bukkit.event.inventory.InventoryCloseEvent event) {
        if(event.getInventory()!=getInventory()) return;
        acceptPlacedItems();
        org.bukkit.event.HandlerList.unregisterAll(this);
        listening=false;
    }

    public EquipmentMenu(Player p, MobMenu.MobDraft d, MobMenu.Loadout l, Menu parent) {
        super(p, "equipment", d, parent); loadout = l;
    }
    @Override protected Runnable onSave() {
        Runnable save=super.onSave();
        return () -> { acceptPlacedItems(); save.run(); };
    }
    static Material slotIcon(EquipmentSlot slot) {
        return switch(slot) {
            case HEAD -> Material.DIAMOND_HELMET;
            case CHEST -> Material.DIAMOND_CHESTPLATE;
            case LEGS -> Material.DIAMOND_LEGGINGS;
            case FEET -> Material.DIAMOND_BOOTS;
            case HAND -> Material.DIAMOND_SWORD;
            case OFF_HAND -> Material.SHIELD;
            default -> Material.ARMOR_STAND;
        };
    }
    @Override protected void render() {
        if(!listening) {
            Bukkit.getPluginManager().registerEvents(this,plugin());
            listening=true;
        }
        boolean armor = armorCapable();
        var slots = SLOTS.subList(0,armor ? 6 : 2);
        for (int i=0; i<slots.size(); i++) {
            EquipmentSlot slot = slots.get(i); EquipmentDef value = loadout.equipment.get(slot);
            Button base = Button.of(value == null ? slotIcon(slot) : value.item().getType(), label("equipment-slot", slot),
                    List.of(message("equipment-slot-lore")), (p,c) -> MenuListener.instance().later(() -> {
                        acceptPlacedItems();
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
            acceptPlacedItems();
            loadout.equipment.keySet().removeIf(s -> s != EquipmentSlot.HAND && s != EquipmentSlot.OFF_HAND); refresh();
        }));
    }
}
