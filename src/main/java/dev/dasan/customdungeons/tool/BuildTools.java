package dev.dasan.customdungeons.tool;

import dev.dasan.customdungeons.gui.Button;
import dev.dasan.customdungeons.text.Messages;
import java.util.*;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

/** Nine fixed slots. Existing inert T25 materials are reused; every tool has its own build PDC. */
public final class BuildTools {
    public static final NamespacedKey KEY=new NamespacedKey("customdungeons","build-tool");
    private BuildTools() {}
    public static Material material(int slot) {
        return switch(slot) {
            case 0 -> Material.MAP;case 1 -> ToolMaterials.defaultFor(ToolType.REGION);
            case 2 -> ToolMaterials.defaultFor(ToolType.DOOR);case 3 -> ToolMaterials.defaultFor(ToolType.SPAWNER);
            case 4 -> ToolMaterials.defaultFor(ToolType.PLATE);case 5 -> ToolMaterials.defaultFor(ToolType.POINT);
            case 6 -> Material.COMPASS;case 7 -> Material.RECOVERY_COMPASS;case 8 -> Material.BOOK;
            default -> throw new IllegalArgumentException("Invalid build tool");
        };
    }
    public static Component name(Messages messages,int slot,int room) {
        return messages.get("build.tool-"+(slot+1)+".name",Placeholder.unparsed("room",Integer.toString(room+1)));
    }
    public static List<Component> lore(Messages messages,int slot) {
        var lore=new ArrayList<Component>();lore.add(messages.get("build.tool-"+(slot+1)+".lore"));
        if(slot==4) lore.add(messages.get("build.plate-types"));
        if(slot==6) lore.add(messages.get("build.room-previous-create"));
        if(slot==1||slot==2) lore.add(messages.get("build.selection-help"));
        return List.copyOf(lore);
    }
    public static ItemStack item(Messages messages,int slot,int room) {
        var item=Button.of(material(slot),name(messages,slot,room),lore(messages,slot),(p,c)->{}).icon();
        item.editMeta(meta->{meta.setMaxStackSize(1);meta.getPersistentDataContainer().set(KEY,PersistentDataType.INTEGER,slot);});
        return item;
    }
    public static int slot(ItemStack item) {
        if(item==null || !item.hasItemMeta()) return -1;
        var value=item.getItemMeta().getPersistentDataContainer().get(KEY,PersistentDataType.INTEGER);
        return value==null || value<0 || value>8 ? -1:value;
    }
    public static boolean isTool(ItemStack item) {return item!=null&&item.hasItemMeta()&&item.getItemMeta().getPersistentDataContainer().has(KEY);}
}
