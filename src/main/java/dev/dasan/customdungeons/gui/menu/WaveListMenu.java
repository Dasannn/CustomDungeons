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

public final class WaveListMenu extends DungeonPage<WaveDef> {
    private final int room,spawner;
    public WaveListMenu(DungeonMenu root,int room,int spawner,Menu parent) {super("waves",root,parent);this.room=room;this.spawner=spawner;}
    @Override protected List<WaveDef> entries() {return root.draft.get().rooms().get(room).spawners().get(spawner).waves();}
    @Override protected Button entry(WaveDef wave,int index) {
        return action("wave",Material.CLOCK,index+1,(p,c)->{
            if(c.isShiftClick()) {
                root.spawner(room,spawner,s->{var waves=new ArrayList<>(s.waves());
                    if(c.isRightClick()) waves.remove(index);else if(index>0) Collections.swap(waves,index,index-1);
                    return new SpawnerDef(s.id(),s.location(),s.radius(),waves);});refresh();
            } else MenuListener.instance().later(()->{if(root.writable()) new WaveMenu(root,room,spawner,index,this).open();});
        });
    }
    @Override protected void create() {
        root.spawner(room,spawner,s->new SpawnerDef(s.id(),s.location(),s.radius(),DungeonMenu.append(s.waves(),new WaveDef(List.of(),SpawnMode.SIMULTANEOUS,20,0))));refresh();
    }
}
