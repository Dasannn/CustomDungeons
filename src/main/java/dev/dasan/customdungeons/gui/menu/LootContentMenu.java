package dev.dasan.customdungeons.gui.menu;

import dev.dasan.customdungeons.gui.*;
import dev.dasan.customdungeons.model.*;
import dev.dasan.customdungeons.config.*;
import java.util.*;
import org.bukkit.Material;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.ItemStack;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

/** Templates are registered only after the physical original is back in player storage. */
public final class LootContentMenu extends LootEditor implements Listener {
    public LootContentMenu(DungeonMenu root,String room,String chest,Menu parent) {super("content",root,room,chest,parent);}
    @Override protected int preferredRows() {return 6;}
    static boolean itemSlot(int slot) {return slot>=18&&slot<=44;}
    @Override protected Set<Integer> reservedInputSlots() {return Set.copyOf(java.util.stream.IntStream.rangeClosed(18,44).boxed().toList());}
    @Override public boolean allowsPlacement(int slot) {return itemSlot(slot)&&slot-18>=value().entries().size()&&root.canEdit(false);}
    @Override protected boolean allowsNativePlacement(int slot) {return false;}
    @Override protected void renderHeader() {GuiTheme.help(this,java.util.stream.IntStream.rangeClosed(1,5).mapToObj(i->m("help-content-"+i)).toList());tintName(8,net.kyori.adventure.text.format.NamedTextColor.YELLOW);}
    @Override protected void render() {
        bindInventoryListener(this,root.services.plugin);
        var entries=value().entries();
        summary(Material.CHEST,m("content-title",arg("id",chestId)),entries.isEmpty()?m("empty-summary"):m("content-summary",arg("count",entries.size()),arg("room",roomNumber(root,roomId)),arg("id",roomId)),m("shared"));
        info(13,Material.WHITE_STAINED_GLASS_PANE,"templates",m("one-roll"),range());
        for(int slot=18;slot<=44;slot++) {
            int index=slot-18;
            if(index>=entries.size()) {clear(slot);continue;}
            var e=entries.get(index);var icon=e.item();
            icon.editMeta(meta->{meta.displayName(name(e.item()).color(net.kyori.adventure.text.format.NamedTextColor.WHITE));meta.lore(List.of(m("stack-chance",arg("amount",e.item().getAmount()),Placeholder.component("name",name(e.item())),arg("chance",percent(e.chance()))),range(),m("whole-stack"),Component.empty(),m("chance-lore"),m("entry-remove")));});
            set(slot,new Button(icon,(p,c)->MenuListener.instance().later(()->{
                if(!root.writable()||index>=value().entries().size()||!value().entries().get(index).equals(e))return;
                if(c.isRightClick()) {update(old->new LootChestDef(old.id(),old.location(),old.trigger(),DungeonMenu.remove(old.entries(),index)));refresh();}
                else if(c.isLeftClick())new LootChanceInputMenu(root,roomId,chestId,index,this).open();
            })));
        }
    }
    private boolean accepted(ItemStack item) {return item!=null&&!item.getType().isAir()&&item.getAmount()>0&&item.getAmount()<=item.getMaxStackSize()&&Validator.reservedEquipment(item)==null;}
    private void copy(Collection<ItemStack> items) {
        if(!root.writable())return;
        var entries=new ArrayList<>(value().entries());
        for(var item:items) {
            if(entries.size()>=NumericRanges.LOOT_ENTRIES.max())break;
            if(accepted(item))entries.add(new LootEntryDef(item,NumericRanges.LOOT_CHANCE.max()));
            else MenuListener.instance().messages().send(viewer,"gui.loot.invalid-stack");
        }
        update(old->new LootChestDef(old.id(),old.location(),old.trigger(),entries));
    }
    private int cursorOrigin=-1;
    private int returnSlot() {
        var inventory=viewer.getInventory();
        if(cursorOrigin>=0&&cursorOrigin<36&&empty(inventory.getItem(cursorOrigin)))return cursorOrigin;
        for(int slot=0;slot<36;slot++)if(empty(inventory.getItem(slot)))return slot;
        return -1;
    }
    private static boolean empty(ItemStack item) {return item==null||item.getType().isAir();}
    @EventHandler(priority=EventPriority.HIGHEST)
    public void placed(InventoryClickEvent event) {
        if(event.getView().getTopInventory()!=getInventory()||!event.getWhoClicked().equals(viewer))return;
        if(event.getClickedInventory()==viewer.getInventory()) {
            if(event.isShiftClick()) {
                event.setCancelled(true);
                if(!MenuListener.instance().rejectReload(viewer)&&root.writable())copy(event.getCurrentItem()==null?List.of():List.of(event.getCurrentItem()));
                laterRefresh();
            } else if(empty(event.getCursor()))cursorOrigin=event.getSlot();
            return;
        }
        if(!itemSlot(event.getRawSlot()))return;
        event.setCancelled(true);
        if(!allowsPlacement(event.getRawSlot())||MenuListener.instance().rejectReload(viewer)||event.isShiftClick()
                ||!(event.getClick()==ClickType.LEFT||event.getClick()==ClickType.RIGHT)||!root.writable())return;
        var cursor=event.getCursor();if(!accepted(cursor))return;
        int slot=returnSlot();if(slot<0)return;
        viewer.getInventory().setItem(slot,cursor.clone());event.setCursor(null);cursorOrigin=-1;
        copy(List.of(cursor));laterRefresh();
    }
    @EventHandler(priority=EventPriority.HIGHEST)
    public void dragged(InventoryDragEvent event) {
        if(event.getView().getTopInventory()!=getInventory()||!event.getWhoClicked().equals(viewer)
                ||event.getRawSlots().stream().noneMatch(LootContentMenu::itemSlot))return;
        event.setCancelled(true);
        if(MenuListener.instance().rejectReload(viewer)||event.getRawSlots().stream().anyMatch(s->!allowsPlacement(s)))return;
        var original=event.getOldCursor().clone();
        var templates=event.getNewItems().entrySet().stream().sorted(Map.Entry.comparingByKey()).map(e->e.getValue().clone()).toList();
        // A cancelled drag restores its old cursor after dispatch. Register only after that restoration.
        MenuListener.instance().later(()->{
            if(viewer.getOpenInventory().getTopInventory()!=getInventory()||!root.writable()
                    ||!Objects.equals(viewer.getItemOnCursor(),original)||!accepted(original)
                    ||templates.stream().noneMatch(this::accepted)||value().entries().size()>=NumericRanges.LOOT_ENTRIES.max())return;
            int slot=returnSlot();if(slot<0)return;
            viewer.getInventory().setItem(slot,original.clone());viewer.setItemOnCursor(null);cursorOrigin=-1;
            copy(templates);refresh();
        });
    }
    private void laterRefresh() {MenuListener.instance().later(()->{if(viewer.getOpenInventory().getTopInventory()==getInventory())refresh();});}
    @EventHandler public void closed(InventoryCloseEvent event) {if(event.getInventory()==getInventory())releaseInventoryListener(event.getInventory());}
}

final class LootChanceInputMenu extends LootEditor {
    private final int index;
    private final LootEntryDef original;
    private double chance;
    LootChanceInputMenu(DungeonMenu root,String room,String chest,int index,Menu parent) {
        super("chance",root,room,chest,parent);this.index=index;original=value().entries().get(index);chance=original.chance();
    }
    @Override protected Component title() {return m("chance-title",Placeholder.component("name",name(original.item())));}
    @Override protected int preferredRows() {return 3;}
    @Override protected Runnable onSave() {return null;}
    @Override protected void renderHeader() {GuiTheme.help(this,java.util.stream.IntStream.rangeClosed(1,4).mapToObj(i->m("help-chance-"+i)).toList());tintName(8,net.kyori.adventure.text.format.NamedTextColor.YELLOW);}
    static double adjust(double chance,boolean increase,boolean shift) {
        double value=java.math.BigDecimal.valueOf(chance).add(java.math.BigDecimal.valueOf((increase?1:-1)*(shift?.1:.01))).doubleValue();
        return NumericRanges.LOOT_CHANCE.clamp(value);
    }
    private Component provisional() {return m("provisional",arg("chance",percent(chance)));}
    @Override protected void render() {
        var item=original.item();
        summary(item.getType(),m("chance-summary",arg("amount",item.getAmount()),Placeholder.component("name",name(item))),m("current-chance",arg("chance",percent(original.chance()))),range());
        for(boolean increase:new boolean[]{false,true}) {
            String key=increase?"increase":"reduce";
            set(increase?16:10,Button.of(increase?Material.LIME_DYE:Material.RED_DYE,m(key),List.of(provisional(),range(),Component.empty(),m(key+"-lore"),m(key+"-shift")),(p,c)->{if(root.writable()){chance=adjust(chance,increase,c.isShiftClick());refresh();}}));
        }
        var display=GuiTheme.information(item.getType(),m("chance",arg("chance",percent(chance))),List.of(m("stack",arg("amount",item.getAmount()),Placeholder.component("name",name(item))),range(),m("extremes")));display.icon().setAmount(item.getAmount());set(13,display);
    }
    @Override protected void renderFooter() {
        super.renderFooter();button(22,Material.LIME_DYE,"confirm",()->{
            if(index>=value().entries().size()||!value().entries().get(index).equals(original)) {root.tell("conflict");return;}
            update(old->new LootChestDef(old.id(),old.location(),old.trigger(),DungeonMenu.replace(old.entries(),index,new LootEntryDef(original.item(),chance))));parent().open();
        },provisional(),range(),Component.empty(),m("confirm-lore"));
    }
}
