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

public final class WaveEntryMenu extends DungeonEditor {
    private final int room,spawner,wave,entry;
    public WaveEntryMenu(DungeonMenu root,int room,int spawner,int wave,int entry,Menu parent) {super("entry",root,parent);this.room=room;this.spawner=spawner;this.wave=wave;this.entry=entry;}
    private WaveEntry value() {return root.draft.get().rooms().get(room).spawners().get(spawner).waves().get(wave).entries().get(entry);}
    private void update(UnaryOperator<WaveEntry> change) {root.wave(room,spawner,wave,v->new WaveDef(DungeonMenu.replace(v.entries(),entry,change.apply(v.entries().get(entry))),v.mode(),v.staggerIntervalTicks(),v.pauseAfterTicks()));}
    @Override protected void render() {
        add(11,"template",Material.ZOMBIE_SPAWN_EGG,()->new TemplatePickerMenu(root,this,id->update(v->new WaveEntry(id,v.count(),v.delayTicks()))).open());
        integer(13,"count",value().count(),1,200,n->update(v->new WaveEntry(v.templateId(),(int)n,v.delayTicks())));
        decimal(15,"delay",value().delayTicks()/20.0,0,3600,1,n->update(v->new WaveEntry(v.templateId(),v.count(),WaveMenu.secondsToTicks(n))));
    }
}
