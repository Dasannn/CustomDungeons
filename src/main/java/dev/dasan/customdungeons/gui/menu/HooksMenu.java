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
        int slot=10;
        for(HookEvent event:HookEvent.values()) {
            add(slot++,"hook-"+event.name().toLowerCase(Locale.ROOT),Material.COMMAND_BLOCK,()->
                    new CommandList(root,this,()->root.draft.get().hooks().getOrDefault(event,List.of()),commands->root.change(v->{
                        var hooks=new EnumMap<HookEvent,List<String>>(HookEvent.class);hooks.putAll(v.hooks);hooks.put(event,commands);v.hooks=hooks;
                    })).open());
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
        return action("command",Material.PAPER,command,(p,c)->MenuListener.instance().later(()->{
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
