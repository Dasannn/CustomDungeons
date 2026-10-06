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

public final class WaveMenu extends DungeonEditor {
    private final int room,spawner,wave;
    private int page;
    public WaveMenu(DungeonMenu root,int room,int spawner,int wave,Menu parent) {super("wave",root,parent);this.room=room;this.spawner=spawner;this.wave=wave;}
    private WaveDef value() {return root.draft.get().rooms().get(room).spawners().get(spawner).waves().get(wave);}
    private List<WaveEntry> entries() {return value().entries();}
    @Override protected void render() {
        var v=value();page=Math.clamp(page,0,PagedMenu.pageCount(entries().size(),2)-1);
        summary(Material.ZOMBIE_HEAD,msg("wave-label",Placeholder.unparsed("value",Integer.toString(wave+1))),waveLine(v,wave));
        section(11,"section-mode",Material.CYAN_STAINED_GLASS_PANE,msg("mode-"+v.mode().name().toLowerCase(Locale.ROOT)));
        section(13,"section-pause",Material.CYAN_STAINED_GLASS_PANE);
        section(15,"section-mobs",Material.CYAN_STAINED_GLASS_PANE);
        set(20,action("mode",modeIcon(v.mode()),"",(p,c)->{
            root.wave(room,spawner,wave,w->new WaveDef(w.entries(),SpawnMode.values()[(w.mode().ordinal()+1)%SpawnMode.values().length],w.staggerIntervalTicks(),w.pauseAfterTicks()));refresh();
        },msg("mode-"+v.mode().name().toLowerCase(Locale.ROOT))));
        if(v.mode()==SpawnMode.STAGGERED) decimal(29,"interval",v.staggerIntervalTicks()/20.0,n->root.wave(room,spawner,wave,w->new WaveDef(w.entries(),w.mode(),secondsToTicks(n),w.pauseAfterTicks())));
        decimal(22,"pause",v.pauseAfterTicks()/20.0,n->root.wave(room,spawner,wave,w->new WaveDef(w.entries(),w.mode(),w.staggerIntervalTicks(),secondsToTicks(n))));
        for(int e=page*2;e<Math.min(page*2+2,entries().size());e++) set(24+(e%2)*9,entry(entries().get(e),e));
        if(entries().isEmpty()) add(24,"add-entry",Material.LIME_DYE,this::create);
        else if(entries().size()==1) add(33,"add-entry",Material.LIME_DYE,this::create);
    }
    static Material modeIcon(SpawnMode mode) {
        return switch(mode) {case SIMULTANEOUS -> Material.TNT;case SEQUENTIAL -> Material.REPEATER;
            case STAGGERED -> Material.CLOCK;case RANDOM -> Material.PRISMARINE_CRYSTALS;};
    }
    @Override protected void renderFooter() {if(entries().size()>=2) add(getInventory().getSize()-9+2,"add-entry",Material.LIME_DYE,this::create);}
    @Override protected boolean hasPreviousPage() {return page>0;}
    @Override protected boolean hasNextPage() {return (page+1)*2<entries().size();}
    @Override protected void previousPage() {if(hasPreviousPage()) {page--;refresh();}}
    @Override protected void nextPage() {if(hasNextPage()) {page++;refresh();}}
    static int secondsToTicks(double seconds) {return (int)Math.round(seconds*20);}
    protected Button entry(WaveEntry entry,int index) {
        var mob=root.services.store.mobs().get(entry.templateId());
        return Button.of(mob==null?Material.EGG:TemplatePickerMenu.egg(mob.entityType()),
                msg("entry-label",Placeholder.unparsed("count",Integer.toString(entry.count())),
                        Placeholder.component("name",mob==null?Component.text(entry.templateId()):dev.dasan.customdungeons.text.Text.parse(mob.displayName())),
                        Placeholder.unparsed("delay",Inputs.formatNumber(entry.delayTicks()/20.0,1))),
                List.of(msg("entry-summary",Placeholder.unparsed("template",entry.templateId()),Placeholder.unparsed("count",Integer.toString(entry.count())),
                        Placeholder.component("unit",msg(entry.count()==1?"unit-mob":"unit-mobs")),
                        Placeholder.unparsed("delay",Inputs.formatNumber(entry.delayTicks()/20.0,1))),Component.empty(),msg("entry-lore")),(p,c)->{
            if(!root.writable()) return;
            if(c.isShiftClick()&&c.isRightClick()) {root.wave(room,spawner,wave,v->new WaveDef(DungeonMenu.remove(v.entries(),index),v.mode(),v.staggerIntervalTicks(),v.pauseAfterTicks()));refresh();}
            else MenuListener.instance().later(()->{if(root.writable()) new WaveEntryMenu(root,room,spawner,wave,index,this).open();});
        });
    }
    protected void create() {
        new TemplatePickerMenu(root,this,id->root.wave(room,spawner,wave,v->new WaveDef(DungeonMenu.append(v.entries(),new WaveEntry(id,1,0)),v.mode(),v.staggerIntervalTicks(),v.pauseAfterTicks()))).open();
    }
}
