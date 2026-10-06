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

    static boolean enchantAvailable(MobMenu.Loadout loadout, EquipmentSlot slot) {
        var value = loadout.equipment.get(slot);
        return value != null && !value.item().getType().isAir();
    }
    @Override protected MobMenu.Loadout summaryLoadout() { return loadout; }
    private boolean armorCapable() {
        return config().armorCapable().stream().anyMatch(t -> t.name().equalsIgnoreCase(data.type.replace("minecraft:", "")));
    }
    @Override public boolean allowsPlacement(int slot) {
        return GuiLayout.centeredRow(4,armorCapable() ? 6 : 2).contains(slot);
    }
    /** Input slots never contain presentation icons or draft copies. Preserve the real item's NBT. */
    void acceptPlacedItems() {
        var inputs=GuiLayout.centeredRow(4,armorCapable() ? 6 : 2);
        for(int input : inputs) {
            ItemStack item=getInventory().getItem(input);
            if(item==null || item.getType().isAir()) continue;
            getInventory().setItem(input,null);
            int index=inputs.indexOf(input);
            if(index>=0 && viewer.hasPermission("customdungeons.admin.edit") && accepted(item)) {
                EquipmentSlot slot=SLOTS.get(index);
                EquipmentDef previous=loadout.equipment.get(slot);
                loadout.equipment.put(slot,new EquipmentDef(item,previous==null ? 0 : previous.dropChance()));
            }
            for(ItemStack leftover:viewer.getInventory().addItem(item).values())
                viewer.getWorld().dropItemNaturally(viewer.getLocation(),leftover);
        }
    }
    private boolean accepted(ItemStack item) {
        String reserved=dev.dasan.customdungeons.config.Validator.reservedEquipment(item);
        if (reserved == null) return true;
        MenuListener.instance().messages().send(viewer,"gui.mob.equipment-"+reserved+"-rejected");
        return false;
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
    @Override protected void renderHeader() {
        super.renderHeader();
        GuiTheme.help(this, List.of(message("equipment-slot-lore"),message("equipment-inputs-lore"),message("help-editor-3")));
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
        section(13,"section-equipment",Material.WHITE_STAINED_GLASS_PANE);
        boolean armor = armorCapable();
        var slots = SLOTS.subList(0,armor ? 6 : 2);
        var columns=GuiLayout.centeredRow(2,slots.size());
        set(4,GuiTheme.information(Material.CHEST,message("equipment-inputs"),List.of(message("equipment-inputs-lore"))));
        for (int input : GuiLayout.centeredRow(4, slots.size())) clear(input);
        for (int i=0; i<slots.size(); i++) {
            EquipmentSlot slot = slots.get(i); EquipmentDef value = loadout.equipment.get(slot);
            Button base = Button.of(value == null ? slotIcon(slot) : value.item().getType(), label("equipment-slot", slot),
                    List.of(message("equipment-slot-lore")), (p,c) -> MenuListener.instance().later(() -> {
                        acceptPlacedItems();
                        if (c.isShiftClick()) { loadout.equipment.remove(slot); refresh(); }
                        else if (c.isRightClick()) {
                            if (enchantAvailable(loadout, slot)) new EnchantMenu(p, data, loadout, slot, this).open();
                            else MenuListener.instance().messages().send(p, "gui.mob.enchant-unavailable");
                        }
                        else {
                            ItemStack held = p.getInventory().getItemInMainHand();
                            if (!held.getType().isAir() && accepted(held)) loadout.equipment.put(slot, new EquipmentDef(held, value == null ? 0 : value.dropChance()));
                            refresh();
                        }
                    }));
            if (value != null) {
                var presentation = base.icon().getItemMeta();
                ItemStack icon = value.item(); icon.editMeta(m -> { m.displayName(presentation.displayName()); m.lore(presentation.lore()); });
                base = new Button(icon, base.onClick());
            }
            set(columns.get(i)-9, value == null ? GuiTheme.unavailable(label("enchant-slot", displayValue("equipment-slot", slot)), message("equipment-required"))
                    : Button.of(Material.ENCHANTED_BOOK, label("enchant-slot", displayValue("equipment-slot", slot)), List.of(message("enchant-slot-lore")),
                            (p,c) -> MenuListener.instance().later(() -> { acceptPlacedItems(); new EnchantMenu(p,data,loadout,slot,this).open(); })));
            set(columns.get(i), base);
            if (value != null) number(columns.get(i)+9, "drop-chance", value.dropChance(), 0, 1,
                    v -> loadout.equipment.put(slot, new EquipmentDef(value.item(), (float)v)));
        }
        if (!armor) {
            set(34, GuiTheme.unavailable(message("no-armor"),message("no-armor-reason")));
            if (loadout.equipment.keySet().stream().anyMatch(slot -> slot != EquipmentSlot.HAND && slot != EquipmentSlot.OFF_HAND))
                action(25,Material.RED_DYE,"remove-armor","",() -> {
                    acceptPlacedItems();
                    loadout.equipment.keySet().removeIf(slot -> slot != EquipmentSlot.HAND && slot != EquipmentSlot.OFF_HAND);
                });
        }

    }
}
