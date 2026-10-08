package dev.dasan.customdungeons.gui.menu;

import dev.dasan.customdungeons.gui.*;
import java.util.*;
import java.util.function.*;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

/** Same command editing gestures and catalog as dungeon rewards, with the mob draft owner. */
final class BossRewardCommands extends PagedMenu<String> {
    private final Menu previous;
    private final WorldBossMenu root;
    private final Supplier<List<String>> source;
    private final Consumer<List<String>> update;
    BossRewardCommands(Player p,Menu previous,WorldBossMenu root,Supplier<List<String>> source,Consumer<List<String>> update) {
        super(p,DungeonEditor.msg("commands"),6);this.previous=previous;this.root=root;this.source=source;this.update=update;
    }
    @Override public boolean permitted(Player p){return super.permitted(p)&&WorldBossMenu.allowed(p);}
    @Override protected Material borderMaterial(){return Material.YELLOW_STAINED_GLASS_PANE;}
    @Override protected Menu parent(){return previous;}
    @Override protected List<String> items(){return source.get();}
    @Override protected Runnable onSave(){return root::save;}
    @Override protected Button button(String value){return Button.of(Material.COMMAND_BLOCK,DungeonEditor.msg("command",Placeholder.unparsed("value",value)),
            List.of(DungeonEditor.msg("command-preview",Placeholder.unparsed("value",value)),Component.empty(),DungeonEditor.msg("command-lore")),(p,c)->MenuListener.instance().later(()->{
                if(!root.writable())return;
                int index=source.get().indexOf(value);
                Inputs.text(p,DungeonEditor.msg(c.isRightClick()?"remove-command":"command"),value,1024,next->{
                    if(!root.writable())return;
                    if(c.isRightClick()){if(next.equals(value))update.accept(DungeonMenu.remove(source.get(),index));}
                    else if(!next.isBlank())update.accept(DungeonMenu.replace(source.get(),index,next));refresh();
                });
            }));}
    @Override protected void renderFooter(){set(getInventory().getSize()-7,Button.of(Material.LIME_DYE,DungeonEditor.msg("add"),List.of(DungeonEditor.msg("add-lore")),(p,c)->MenuListener.instance().later(()->{
        if(root.writable())Inputs.text(p,DungeonEditor.msg("command"),"",1024,value->{if(root.writable()&&!value.isBlank()){update.accept(DungeonMenu.append(source.get(),value));refresh();}});
    })));}
}
