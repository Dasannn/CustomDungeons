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
    private final EditOrigin origin;
    private final int spawner,wave,entry;
    public WaveEntryMenu(DungeonMenu root,int room,int spawner,int wave,int entry,Menu parent) {this(root,EditOrigin.room(root.draft.get(),room),spawner,wave,entry,parent);}
    public WaveEntryMenu(DungeonMenu root,EditOrigin origin,int spawner,int wave,int entry,Menu parent) {super("entry",originTitle("entry",origin),3,root,parent);this.origin=origin;this.spawner=spawner;this.wave=wave;this.entry=entry;}
    private WaveEntry value() {return origin.spawners(root.draft.get()).get(spawner).waves().get(wave).entries().get(entry);}
    private void update(UnaryOperator<WaveEntry> change) {root.wave(origin,spawner,wave,v->new WaveDef(DungeonMenu.replace(v.entries(),entry,change.apply(v.entries().get(entry))),v.mode(),v.staggerIntervalTicks(),v.pauseAfterTicks()));}
    @Override protected Component title() {return origin.kind()==EditOrigin.Kind.ZONE?msg("zone-editor-title",Placeholder.component("editor",msg(category)),Placeholder.unparsed("id",origin.id())):super.title();}
    @Override protected void render() {
        var e=value();var mob=root.services.store.mobs().get(e.templateId());
        summary(mob==null?Material.EGG:TemplatePickerMenu.egg(mob.entityType()),msg("entry-label",Placeholder.unparsed("count",Integer.toString(e.count())),
                Placeholder.component("name",mob==null?Component.text(e.templateId()):dev.dasan.customdungeons.text.Text.parse(mob.displayName())),
                Placeholder.unparsed("delay",Inputs.formatNumber(e.delayTicks()/20.0,1))));
        add(11,"template",mob==null?Material.EGG:TemplatePickerMenu.egg(mob.entityType()),()->new TemplatePickerMenu(root,this,id->update(v->new WaveEntry(id,v.count(),v.delayTicks()))).open(),java.util.stream.Stream.concat(java.util.stream.Stream.of(msg("value",Placeholder.unparsed("value",e.templateId()))),IntelligenceMenu.markerLore(mob).stream()).toArray(Component[]::new));
        if(mob!=null&&mob.intelligence().level()>0)getInventory().getItem(4).editMeta(meta->{var lore=new ArrayList<>(meta.lore());lore.add(IntelligenceMenu.marker(mob.intelligence()));meta.lore(lore);});
        integer(13,"count",e.count(),n->update(v->new WaveEntry(v.templateId(),n,v.delayTicks())));
        decimal(15,"delay",e.delayTicks()/20.0,n->update(v->new WaveEntry(v.templateId(),v.count(),WaveMenu.secondsToTicks(n))));
    }
}
