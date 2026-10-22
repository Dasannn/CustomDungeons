package dev.dasan.customdungeons.gui.menu;

import dev.dasan.customdungeons.gui.*;
import dev.dasan.customdungeons.model.*;
import dev.dasan.customdungeons.config.*;
import java.util.*;
import java.util.function.UnaryOperator;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.tag.resolver.*;
import org.bukkit.Material;

/** Stable room/chest identities, shared presentation and guarded draft mutations. */
abstract class LootEditor extends DungeonEditor {
    final String roomId,chestId;
    LootEditor(String kind,DungeonMenu root,String room,String chest,Menu parent) {
        super("loot",m(kind+"-title",arg("id",chest==null?room:chest),arg("room",roomNumber(root,room))),root,parent);
        roomId=room;chestId=chest;
    }
    static TagResolver arg(String key,Object value) {return Placeholder.unparsed(key,String.valueOf(value));}
    static Component m(String key,TagResolver... args) {return MenuListener.instance().messages().get("gui.loot."+key,args);}
    static int roomNumber(DungeonMenu root,String id) {return new dev.dasan.customdungeons.tool.EditOrigin(dev.dasan.customdungeons.tool.EditOrigin.Kind.ROOM,id).index(root.draft.get())+1;}
    RoomDef room() {return root.draft.get().rooms().get(roomNumber(root,roomId)-1);}
    LootChestDef value() {return room().lootChests().stream().filter(c->c.id().equals(chestId)).findFirst().orElseThrow();}
    void update(UnaryOperator<LootChestDef> action) {root.loot(roomId,chestId,action);}
    Component context() {return m("context",arg("room",roomNumber(root,roomId)),arg("id",roomId));}
    static Component trigger(LootChestDef.Trigger trigger) {return m(trigger==LootChestDef.Trigger.ROOM_ENTER?"enter":"clear");}
    static Component position(Point p) {return p==null?m("missing"):m("position",arg("world",p.world()),arg("x",(int)p.x()),arg("y",(int)p.y()),arg("z",(int)p.z()));}
    static Component range() {var r=NumericRanges.LOOT_CHANCE;return m("range",arg("min",Inputs.formatNumber(r.min()*100,0)),arg("max",Inputs.formatNumber(r.max()*100,0)));}
    static Component name(org.bukkit.inventory.ItemStack item) {var meta=item.getItemMeta();return meta!=null&&meta.displayName()!=null?meta.displayName():Component.translatable(item.getType().translationKey());}
    static String percent(double chance) {return java.math.BigDecimal.valueOf(chance).movePointRight(2).stripTrailingZeros().toPlainString();}
    static Component contents(List<LootEntryDef> entries) {
        if(entries.isEmpty())return m("empty");
        return Component.join(net.kyori.adventure.text.JoinConfiguration.separator(Component.text(" · ")),entries.stream().map(e->m("entry-summary",Placeholder.component("name",name(e.item())),arg("amount",e.item().getAmount()),arg("chance",percent(e.chance())))).toList());
    }
    void info(int slot,Material icon,String key,Component... lore) {set(slot,GuiTheme.information(icon,m(key),List.of(lore)));}
    void button(int slot,Material icon,String key,Runnable action,Component... lore) {set(slot,Button.of(icon,m(key),List.of(lore),(p,c)->MenuListener.instance().later(()->{if(root.writable())action.run();})));}
    @Override protected Material borderMaterial() {return Material.YELLOW_STAINED_GLASS_PANE;}
    @Override protected void renderHeader() {GuiTheme.help(this,java.util.stream.IntStream.rangeClosed(1,5).mapToObj(i->m("help-chest-"+i)).toList());tintName(8,NamedTextColor.YELLOW);}
    @Override protected void renderFooter() {tintNavigation(NamedTextColor.YELLOW);}
}
