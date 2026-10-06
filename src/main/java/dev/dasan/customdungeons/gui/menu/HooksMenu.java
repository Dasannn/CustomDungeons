package dev.dasan.customdungeons.gui.menu;

import dev.dasan.customdungeons.gui.*;
import dev.dasan.customdungeons.model.*;
import dev.dasan.customdungeons.config.*;
import dev.dasan.customdungeons.tool.*;
import java.util.*;
import java.util.function.*;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

public final class HooksMenu extends DungeonEditor {
    public HooksMenu(DungeonMenu root) {super("hooks",root,root);}
    @Override protected void render() {
        section(4,"section-hooks-start",Material.BELL);
        section(22,"section-hooks-end",Material.COMMAND_BLOCK);
        int index=0;
        var slots=new ArrayList<>(GuiLayout.centeredRow(1,3));
        slots.addAll(GuiLayout.centeredRow(3,3));
        for(HookEvent event:HookEvent.values()) {
            add(slots.get(index++),"hook-"+event.name().toLowerCase(Locale.ROOT),switch(event) {
                case LOBBY_OPEN -> Material.RED_BED; case FULL -> Material.PLAYER_HEAD;
                case START -> Material.LIME_CONCRETE; case COMPLETE -> Material.GOLD_INGOT;
                case FAIL -> Material.RED_CONCRETE; case FREE -> Material.DARK_OAK_DOOR;
            },()->
                    new CommandList(root,this,()->root.draft.get().hooks().getOrDefault(event,List.of()),commands->root.change(v->{
                        var hooks=new EnumMap<HookEvent,List<String>>(HookEvent.class);hooks.putAll(v.hooks);hooks.put(event,commands);v.hooks=hooks;
                    })).open(),msg("command-count",Placeholder.unparsed("value",Integer.toString(root.draft.get().hooks().getOrDefault(event,List.of()).size()))));
        }
    }
}

/** Hooks and reward commands use the same add/edit/remove input flow. */
final class CommandList extends DungeonPage<String> {
    private final Supplier<List<String>> source;
    private final Consumer<List<String>> update;
    CommandList(DungeonMenu root,Menu parent,Supplier<List<String>> source,Consumer<List<String>> update) {
        super("commands",root,parent);this.source=source;this.update=update;
    }
    @Override protected List<String> entries() {return source.get();}
    @Override protected Button entry(String command,int index) {
        return action("command",Material.COMMAND_BLOCK,command,(p,c)->MenuListener.instance().later(()->{
            if(!root.writable()) return;
            Inputs.text(p,msg(c.isRightClick()?"remove-command":"command"),command,1024,s->{
                if(!root.writable()) return;
                if(c.isRightClick()) {
                    // Requiring the exact command makes removal explicit and cancellable.
                    if(s.equals(command)) update.accept(DungeonMenu.remove(source.get(),index));
                } else if(!s.isBlank()) update.accept(DungeonMenu.replace(source.get(),index,s));
                refresh();
            });
        }));
    }
    @Override protected void create() {
        Inputs.text(viewer,msg("command"),"",1024,s->{if(root.writable()&&!s.isBlank()) {update.accept(DungeonMenu.append(source.get(),s));refresh();}});
    }
}
