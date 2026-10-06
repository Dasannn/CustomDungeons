package dev.dasan.customdungeons.gui.menu;

import dev.dasan.customdungeons.gui.*;
import dev.dasan.customdungeons.model.*;
import java.util.*;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.*;

/** Displays detached equipment and copies cursor, hand or dragged items without consuming originals. */
public final class EquipmentMenu extends MobMenuBase implements org.bukkit.event.Listener {
    private static final List<EquipmentSlot> SLOTS = List.of(EquipmentSlot.HAND, EquipmentSlot.OFF_HAND,
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET);
    private final MobMenu.Loadout loadout;
    private final Map<Integer, ItemStack> previews = new HashMap<>();
    private List<Integer> renderedInputs;
    private List<Integer> inputSlots() { return armorCapable() ? List.of(19,20,21,23,24,25) : List.of(19,20); }
    private EquipmentSlot equipmentSlot(int slot) { return SLOTS.get(inputSlots().indexOf(slot)); }
    private void copy(EquipmentSlot slot, ItemStack item) {
        if (item == null || item.getType().isAir() || !accepted(item)) return;
        EquipmentDef old = loadout.equipment.get(slot);
        loadout.equipment.put(slot,new EquipmentDef(item,old == null ? 0 : old.dropChance()));
    }

    static boolean enchantAvailable(MobMenu.Loadout loadout, EquipmentSlot slot) {
        var value = loadout.equipment.get(slot);
        return value != null && !value.item().getType().isAir();
    }
    @Override protected MobMenu.Loadout summaryLoadout() { return loadout; }
    private boolean armorCapable() {
        return config().armorCapable().stream().anyMatch(t -> t.name().equalsIgnoreCase(data.type.replace("minecraft:", "")));
    }
    @Override protected Set<Integer> reservedInputSlots() { return Set.copyOf(inputSlots()); }
    @Override public boolean allowsPlacement(int slot) {
        return inputSlots().contains(slot);
    }
    @Override protected boolean allowsNativePlacement(int slot) { return false; }
    @Override protected void beforeInventoryReplaced() { acceptPlacedItems(); }
    /** Recover only real deposits; the displayed draft copies never leave the GUI. */
    void acceptPlacedItems() { acceptPlacedItems(false); }
    private void acceptPlacedItems(boolean deathClose) {
        var inputs=inputSlots();
        // The draft may now support fewer slots than the inventory which owns these deposits.
        for(int input : renderedInputs==null ? inputs : renderedInputs) {
            ItemStack item=getInventory().getItem(input);
            if(item==null || item.getType().isAir() || item.equals(previews.get(input))) continue;
            getInventory().setItem(input,null);
            int index=inputs.indexOf(input);
            if(index>=0 && viewer.hasPermission("customdungeons.admin.edit") && accepted(item)) {
                EquipmentSlot slot=SLOTS.get(index);
                EquipmentDef previous=loadout.equipment.get(slot);
                loadout.equipment.put(slot,new EquipmentDef(item,previous==null ? 0 : previous.dropChance()));
            }
            returnDepositedItem(item,deathClose);
        }
    }
    private boolean accepted(ItemStack item) {
        String reserved=dev.dasan.customdungeons.config.Validator.reservedEquipment(item);
        if (reserved == null) return true;
        MenuListener.instance().messages().send(viewer,"gui.mob.equipment-"+reserved+"-rejected");
        return false;
    }
    /** Keep preview copies inside the GUI: clicks copy inputs, never give the preview to the player. */
    @org.bukkit.event.EventHandler(priority=org.bukkit.event.EventPriority.HIGHEST)
    public void placed(org.bukkit.event.inventory.InventoryClickEvent event) {
        if(event.getView().getTopInventory()!=getInventory() || !event.getWhoClicked().equals(viewer)
                || !allowsPlacement(event.getRawSlot())) return;
        event.setCancelled(true);
        if(!viewer.hasPermission("customdungeons.admin.edit") || MenuListener.instance().rejectReload(viewer)) return;
        if (!(event.isLeftClick() || event.isRightClick()) || event.getClick() == org.bukkit.event.inventory.ClickType.DOUBLE_CLICK) return;
        EquipmentSlot slot = equipmentSlot(event.getRawSlot());
        if(event.isShiftClick()) loadout.equipment.remove(slot);
        else if(event.isLeftClick() || event.isRightClick()) {
            ItemStack cursor = event.getCursor();
            copy(slot,cursor == null || cursor.getType().isAir() ? viewer.getInventory().getItemInMainHand() : cursor);
        }
        MenuListener.instance().later(() -> { if(viewer.getOpenInventory().getTopInventory()==getInventory()) refresh(); });
    }
    @org.bukkit.event.EventHandler(priority=org.bukkit.event.EventPriority.HIGHEST)
    public void dragged(org.bukkit.event.inventory.InventoryDragEvent event) {
        if(event.getView().getTopInventory()!=getInventory() || !event.getWhoClicked().equals(viewer)
                || event.getRawSlots().stream().noneMatch(this::allowsPlacement)) return;
        event.setCancelled(true);
        if(!viewer.hasPermission("customdungeons.admin.edit") || MenuListener.instance().rejectReload(viewer)) return;
        if(event.getRawSlots().stream().anyMatch(slot -> slot < 0 || (slot < getInventory().getSize() && !allowsPlacement(slot)))) return;
        for(var entry:event.getNewItems().entrySet()) if(allowsPlacement(entry.getKey())) copy(equipmentSlot(entry.getKey()),entry.getValue());
        MenuListener.instance().later(() -> { if(viewer.getOpenInventory().getTopInventory()==getInventory()) refresh(); });
    }
    @org.bukkit.event.EventHandler
    public void closed(org.bukkit.event.inventory.InventoryCloseEvent event) {
        if(event.getInventory()!=getInventory()) return;
        acceptPlacedItems(event.getReason()==org.bukkit.event.inventory.InventoryCloseEvent.Reason.DEATH);
        releaseInventoryListener(event.getInventory());
        // A later reopening may capture the old inventory again; it must contain no draft copies.
        previews.forEach((slot,preview) -> {
            if(preview.equals(event.getInventory().getItem(slot))) event.getInventory().setItem(slot,null);
        });
        previews.clear();
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
        renderedInputs=inputSlots();
        bindInventoryListener(this,plugin());
        boolean armor = armorCapable();
        previews.clear();
        for (int i=0; i<SLOTS.size(); i++) {
            EquipmentSlot slot = SLOTS.get(i);
            int column = List.of(1,2,3,5,6,7).get(i);
            set(9+column,GuiTheme.section(displayValue("equipment-slot",slot),List.of(message("equipment-inputs-lore"))));
            if (!armor && i>=2) {
                set(18+column,GuiTheme.unavailable(displayValue("equipment-slot",slot),message("no-armor-reason")));
                set(27+column,GuiTheme.unavailable(message("drop-unavailable"),message("no-armor-reason")));
                set(36+column,GuiTheme.unavailable(message("enchants"),message("no-armor-reason")));
                continue;
            }
            clear(18+column);
            EquipmentDef value=loadout.equipment.get(slot);
            if (enchantAvailable(loadout,slot)) {
                ItemStack preview=value.item();
                getInventory().setItem(18+column,preview);
                previews.put(18+column,preview.clone());
                number(27+column,"drop-chance",value.dropChance(),0,1,
                        v -> loadout.equipment.put(slot,new EquipmentDef(value.item(),(float)v)));
                set(36+column,Button.of(Material.ENCHANTED_BOOK,label("enchant-slot",displayValue("equipment-slot",slot)),
                        List.of(message("action-open")),(p,c) -> MenuListener.instance().later(() -> new EnchantMenu(p,data,loadout,slot,this).open())));
            } else {
                set(27+column,GuiTheme.unavailable(message("drop-unavailable"),message("equipment-required")));
                set(36+column,GuiTheme.unavailable(label("enchant-slot",displayValue("equipment-slot",slot)),message("equipment-required")));
            }
        }
        if (!armor && loadout.equipment.keySet().stream().anyMatch(slot -> slot != EquipmentSlot.HAND && slot != EquipmentSlot.OFF_HAND))
            action(46,Material.RED_DYE,"remove-armor","",() -> loadout.equipment.keySet().removeIf(slot -> slot != EquipmentSlot.HAND && slot != EquipmentSlot.OFF_HAND));
    }
}
