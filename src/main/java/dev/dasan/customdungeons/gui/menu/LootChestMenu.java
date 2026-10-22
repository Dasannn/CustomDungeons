package dev.dasan.customdungeons.gui.menu;

import dev.dasan.customdungeons.gui.*;
import dev.dasan.customdungeons.model.*;
import dev.dasan.customdungeons.config.*;
import java.util.*;
import org.bukkit.Material;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

public final class LootChestMenu extends LootEditor {
    public LootChestMenu(DungeonMenu root,String room,String chest,Menu parent) {super("chest",root,room,chest,parent);}
    @Override protected int preferredRows() {return 5;}
    @Override protected void render() {
        var c=value();
        summary(Material.CHEST,m("chest-title",arg("id",c.id())),context(),m(c.location()==null?(c.entries().isEmpty()?"missing-summary":"missing-content-summary"):"placed-summary",arg("count",c.entries().size())),m("shared"));
        set(11,GuiTheme.section(c.location()!=null,m(c.location()==null?"position-missing":"position-ready"),List.of(c.location()==null?m("place-first"):position(c.location()))));
        info(13,Material.WHITE_STAINED_GLASS_PANE,"trigger-header",m("once"));
        info(15,Material.WHITE_STAINED_GLASS_PANE,"content-header",m(c.entries().isEmpty()?"empty-valid":"content-count",arg("count",c.entries().size())));
        button(20,Material.CHEST,"give",()->{if(viewer.hasPermission("customdungeons.admin.tools"))root.services.tools.giveLoot(viewer,root.draft.get().id(),roomId,chestId);},m("tool-context",arg("room",roomId),arg("id",chestId)),Component.empty(),m("give-lore"),m("tool-use"));
        set(22,Button.of(Material.COMPARATOR,m("trigger",Placeholder.component("value",trigger(c.trigger()))),List.of(m(c.trigger()==LootChestDef.Trigger.ROOM_ENTER?"enter-detail":"clear-detail"),Component.empty(),m("trigger-lore")),(p,click)->MenuListener.instance().later(()->{if(root.writable())new LootTriggerMenu(root,roomId,chestId,this).open();})));
        set(24,Button.of(Material.CHEST,m("content",arg("count",c.entries().size())),List.of(contents(c.entries()),Component.empty(),m(c.entries().isEmpty()?"add-content-lore":"content-lore")),(p,click)->MenuListener.instance().later(()->{if(root.writable())new LootContentMenu(root,roomId,chestId,this).open();})));
        if(c.location()==null) {
            set(29,GuiTheme.unavailable(m("preview"),m("no-position")));set(33,GuiTheme.unavailable(m("remove"),m("no-position")));
        } else {
            button(29,Material.SPYGLASS,"preview",()->preview(root,value()),position(c.location()),Component.empty(),m("preview-time"));
            button(33,Material.RED_DYE,"remove",()->Inputs.confirm(viewer,m("remove-confirm"),()->{if(root.writable())root.editLootPlacement(roomId,chestId,null,false,this::refresh);}),m("remove-detail"),Component.empty(),m("remove-lore"));
        }
        info(31,Material.PAPER,"shared",m("independent"),m("sum"));
    }
    @Override protected void renderFooter() {
        super.renderFooter();if(value().location()==null) {
            int room=roomNumber(root,roomId)-1,index=room().lootChests().indexOf(value());
            var error=new ValidationError("rooms["+room+"].loot-chests["+index+"].location","validation.loot-position",Map.of());
            set(40,GuiTheme.information(Material.RED_DYE,m("missing"),List.of(Validator.describe(error,MenuListener.instance().messages()))));
        }
    }
    static void preview(DungeonMenu root,LootChestDef chest) {
        if(!root.writable()||chest.location()==null)return;
        var renderer=root.services.plugin.getServer().getServicesManager().load(dev.dasan.customdungeons.tool.PreviewRenderer.class);
        if(renderer!=null) {var p=chest.location();renderer.showRegion(root.viewerPlayer(),Region.of(p.world(),new BlockPos((int)p.x(),(int)p.y(),(int)p.z()),new BlockPos((int)p.x(),(int)p.y(),(int)p.z())),org.bukkit.Color.YELLOW,15);}
    }
}

final class LootTriggerMenu extends LootEditor {
    LootTriggerMenu(DungeonMenu root,String room,String chest,Menu parent) {super("trigger",root,room,chest,parent);}
    @Override protected int preferredRows() {return 3;}
    @Override protected Material borderMaterial() {return Material.LIGHT_BLUE_STAINED_GLASS_PANE;}
    @Override protected Runnable onSave() {return null;}
    @Override protected void renderHeader() {GuiTheme.help(this,java.util.stream.IntStream.rangeClosed(1,3).mapToObj(i->m("help-trigger-"+i)).toList());tintName(8,net.kyori.adventure.text.format.NamedTextColor.AQUA);}
    @Override protected void renderFooter() {tintNavigation(net.kyori.adventure.text.format.NamedTextColor.AQUA);}
    @Override protected void render() {
        summary(Material.COMPARATOR,m("trigger-summary").color(net.kyori.adventure.text.format.NamedTextColor.AQUA),m("current-trigger",Placeholder.component("value",trigger(value().trigger()))),context());
        for(var t:LootChestDef.Trigger.values()) {
            String key=t==LootChestDef.Trigger.ROOM_CLEAR?"clear":"enter";
            var lore=new ArrayList<Component>();lore.add(m(key+"-option",arg("selected",value().trigger()==t?" · ":""),Placeholder.component("state",value().trigger()==t?m("selected"):Component.empty())));lore.add(m(key+"-when"));if(t==LootChestDef.Trigger.ROOM_ENTER)lore.add(m("future"));lore.add(Component.empty());lore.add(m(key+"-apply"));
            set(t==LootChestDef.Trigger.ROOM_CLEAR?12:14,Button.of(Material.LIME_DYE,trigger(t).color(net.kyori.adventure.text.format.NamedTextColor.AQUA),lore,(p,c)->MenuListener.instance().later(()->{if(root.writable()){update(old->new LootChestDef(old.id(),old.location(),t,old.entries()));parent().open();}})));
        }
    }
}
