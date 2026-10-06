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

public final class WaveMenu extends DungeonPage<WaveEntry> {
    private final int room,spawner,wave;
    public WaveMenu(DungeonMenu root,int room,int spawner,int wave,Menu parent) {super("wave",root,parent);this.room=room;this.spawner=spawner;this.wave=wave;}
    private WaveDef value() {return root.draft.get().rooms().get(room).spawners().get(spawner).waves().get(wave);}
    @Override protected List<WaveEntry> entries() {return value().entries();}
    @Override protected void render() {
        super.render();
        set(0,Button.of(Material.COMPARATOR,msg("mode"),List.of(msg("mode-lore"),msg("mode-"+value().mode().name().toLowerCase(Locale.ROOT))),
                (p,c)->{if(root.writable()) {root.wave(room,spawner,wave,v->new WaveDef(v.entries(),SpawnMode.values()[(v.mode().ordinal()+1)%SpawnMode.values().length],v.staggerIntervalTicks(),v.pauseAfterTicks()));refresh();}}));
        decimal(1,"interval",value().staggerIntervalTicks()/20.0,0.1,3600,1,n->root.wave(room,spawner,wave,v->new WaveDef(v.entries(),v.mode(),secondsToTicks(n),v.pauseAfterTicks())));
        decimal(2,"pause",value().pauseAfterTicks()/20.0,0,3600,1,n->root.wave(room,spawner,wave,v->new WaveDef(v.entries(),v.mode(),v.staggerIntervalTicks(),secondsToTicks(n))));
    }
    static int secondsToTicks(double seconds) {return (int)Math.round(seconds*20);}
    @Override protected Button entry(WaveEntry entry,int index) {
        return action("entry",Material.ZOMBIE_SPAWN_EGG,entry.templateId(),(p,c)->{
            if(c.isShiftClick()&&c.isRightClick()) {root.wave(room,spawner,wave,v->new WaveDef(DungeonMenu.remove(v.entries(),index),v.mode(),v.staggerIntervalTicks(),v.pauseAfterTicks()));refresh();}
            else MenuListener.instance().later(()->{if(root.writable()) new WaveEntryMenu(root,room,spawner,wave,index,this).open();});
        });
    }
    @Override protected void create() {
        new TemplatePickerMenu(root,this,id->root.wave(room,spawner,wave,v->new WaveDef(DungeonMenu.append(v.entries(),new WaveEntry(id,1,0)),v.mode(),v.staggerIntervalTicks(),v.pauseAfterTicks()))).open();
    }
}
