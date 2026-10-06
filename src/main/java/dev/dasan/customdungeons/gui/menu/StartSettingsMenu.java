package dev.dasan.customdungeons.gui.menu;

import dev.dasan.customdungeons.gui.*;
import dev.dasan.customdungeons.model.*;
import dev.dasan.customdungeons.tool.*;
import java.util.*;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.*;

/** Approved T38 six-row layout. Cinematic fields are persisted for T41 only. */
public final class StartSettingsMenu extends DungeonEditor {
    public StartSettingsMenu(DungeonMenu root) {super("start-settings",root,root);}
    static Component mode(DungeonDef d) {
        return msg(d.startMode()==StartMode.PLATES?"start-mode-plates":"start-mode-auto");
    }
    static Component modeSummary(DungeonDef d) {
        return msg(d.startMode()==StartMode.PLATES?"start-mode-summary-plates":"start-mode-summary-auto",
                Placeholder.unparsed("count",Integer.toString(d.plates().size())));
    }
    static Component overview(DungeonDef d) {
        return msg("start-overview",Placeholder.component("mode",modeSummary(d)),
                Placeholder.unparsed("count",Integer.toString(d.plates().size())),
                Placeholder.component("tp",msg(d.teleportOnStart()?"start-with-tp":"start-without-tp")),
                Placeholder.component("intro",msg(d.introCinematic()?"start-with-intro":"start-without-intro")));
    }
    @Override protected void render() {
        var d=root.draft.get();
        summary(Material.LEVER,msg("start-summary",Placeholder.component("name",dev.dasan.customdungeons.text.Text.parse(d.displayName()))),
                msg("start-summary-mode",Placeholder.component("mode",modeSummary(d)),Placeholder.unparsed("count",Integer.toString(d.plates().size())),
                        Placeholder.component("door",msg(d.entranceDoor()!=null?"start-door-ready":"start-door-status-missing"))),
                msg("start-summary-begin",Placeholder.component("tp",msg(d.teleportOnStart()?"start-with-tp":"start-without-tp")),
                        Placeholder.component("intro",msg(d.introCinematic()?"start-intro-seconds":"start-without-intro",Placeholder.unparsed("value",Integer.toString(d.introSeconds()))))),
                msg("finish-summary",Placeholder.component("mode",msg("finish-mode-"+d.finishMode().name().toLowerCase(Locale.ROOT))),Placeholder.component("destination",msg("finish-destination-"+d.finishDestination().name().toLowerCase(Locale.ROOT)))));
        section(10,"start-section-mode",Material.WHITE_STAINED_GLASS_PANE);
        if(d.entranceDoor()==null && d.startMode()==StartMode.AUTO)section(12,"start-section-door",Material.WHITE_STAINED_GLASS_PANE,msg("start-door-optional"));
        else sectionState(12,"start-section-door",d.entranceDoor()!=null);
        section(14,"start-section-begin",Material.WHITE_STAINED_GLASS_PANE);
        section(16,"start-section-finish",Material.WHITE_STAINED_GLASS_PANE);
        set(19,Button.of(d.startMode()==StartMode.PLATES?Material.STONE_PRESSURE_PLATE:Material.CLOCK,
                msg("start-mode",Placeholder.component("value",mode(d))),
                List.of(msg(d.startMode()==StartMode.PLATES?"start-plates-explanation":"start-auto-explanation"),
                        msg(d.startMode()==StartMode.PLATES?"start-plates-explanation-2":"start-auto-explanation-2"),Component.empty(),
                        msg(d.startMode()==StartMode.PLATES?"start-switch-auto":"start-switch-plates")),(p,c)->{
                    if(!root.writable()) return;
                    root.change(v->{v.startMode=v.startMode==StartMode.AUTO?StartMode.PLATES:StartMode.AUTO;if(v.startMode==StartMode.AUTO)v.min=Math.max(1,v.min);});refresh();
                }));
        set(21,Button.of(Material.AMETHYST_SHARD,msg("start-give-door"),List.of(Component.empty(),msg("start-give-tool-lore")),
                (p,c)->{if(root.writable())root.services.tools.give(p,ToolType.DOOR,d.id());}));
        set(28,Button.of(Material.STONE_PRESSURE_PLATE,msg("give-plate"),List.of(
                msg("start-plates-count",Placeholder.unparsed("value",Integer.toString(d.plates().size())),Placeholder.unparsed("exits",Integer.toString(d.exitPlates().size()))),Component.empty(),msg("give-plate-lore"),msg("give-plate-lore-remove")),
                (p,c)->{if(root.writable()){root.services.tools.give(p,ToolType.PLATE,d.id());root.services.tools.give(p,ToolType.EXIT_PLATE,d.id());}}));
        set(30,Button.of(d.entranceDoor()==null?Material.GRAY_DYE:Material.LIME_DYE,msg("start-door-select"),
                List.of(doorLore(d),Component.empty(),msg("start-door-select-lore")),(p,c)->{
                    if(!root.writable())return;
                    if(c.isRightClick())root.change(v->v.entranceDoor=null);
                    else {
                        var selection=root.services.tools.selection(p.getUniqueId()).orElse(null);
                        if(selection==null||!selection.complete()){tell("no-selection");return;}
                        root.change(v->v.entranceDoor=selection.toRegion());
                    }
                    refresh();
                }));
        set(39,Button.of(Material.SPYGLASS,msg("start-door-preview"),List.of(Component.empty(),msg("start-door-preview-lore")),(p,c)->{
            if(!root.writable())return;
            if(d.entranceDoor()==null){tell("start-door-missing");return;}
            var previews=root.services.plugin.getServer().getServicesManager().load(PreviewRenderer.class);
            if(previews!=null)previews.showRegion(p,d.entranceDoor(),Color.ORANGE,15);
        }));
        startToggle(23,"start-tp",d.teleportOnStart(),GuiTheme.toggleIcon(d.teleportOnStart()),()->root.change(v->v.startTp=!v.startTp));
        set(25,Button.of(switch(d.finishMode()){case IMMEDIATE->Material.ENDER_PEARL;case DELAYED->Material.CLOCK;case NONE->Material.BARRIER;},
                msg("finish-mode",Placeholder.component("value",msg("finish-mode-"+d.finishMode().name().toLowerCase(Locale.ROOT)))),
                List.of(msg("finish-mode-lore"),msg("finish-mode-safety")),(p,c)->{
                    if(root.writable()){root.change(v->v.finishMode=FinishMode.values()[(v.finishMode.ordinal()+1)%FinishMode.values().length]);refresh();}
                }));
        if(d.finishMode()==FinishMode.DELAYED)integer(34,"exit-grace",d.exitGraceSeconds(),10,300,n->root.change(v->v.exitGrace=n),msg("exit-grace-explanation"));
        else set(34,Button.of(Material.GRAY_DYE,msg("exit-grace",Placeholder.unparsed("value",Integer.toString(d.exitGraceSeconds()))),List.of(msg("exit-grace-disabled")),(p,c)->{}));
        set(43,Button.of(d.finishDestination()==FinishDestination.EXIT?Material.COMPASS:Material.RECOVERY_COMPASS,
                msg("finish-destination",Placeholder.component("value",msg("finish-destination-"+d.finishDestination().name().toLowerCase(Locale.ROOT)))),
                List.of(msg("finish-destination-lore")),(p,c)->{
                    if(root.writable()){root.change(v->v.finishDestination=v.finishDestination==FinishDestination.EXIT?FinishDestination.PREVIOUS:FinishDestination.EXIT);refresh();}
                }));
        startToggle(32,"intro-cinematic",d.introCinematic(),Material.ENDER_EYE,()->root.change(v->v.cinematic=!v.cinematic));
        integer(37,"plate-countdown",d.plateCountdownSeconds(),1,Integer.MAX_VALUE,n->root.change(v->v.plateCountdown=n),msg("plate-countdown-explanation"));
        integer(41,"intro-seconds",d.introSeconds(),5,20,n->root.change(v->v.introSeconds=n),msg("intro-seconds-explanation"));
    }
    private void startToggle(int slot,String key,boolean value,Material icon,Runnable toggle) {
        var lore=new ArrayList<Component>();
        lore.add(msg(key.equals("start-tp")?(value?"start-tp-yes-explanation":"start-tp-explanation"):key+"-explanation"));
        if(key.equals("intro-cinematic"))lore.add(msg("intro-cinematic-explanation-2"));
        lore.add(Component.empty());lore.add(msg(key+"-lore"));
        set(slot,Button.of(icon,msg(key,Placeholder.component("value",msg(value?"start-yes":"start-no"))),lore,
                (p,c)->{if(root.writable()){toggle.run();refresh();}}));
    }
    private Component doorLore(DungeonDef d) {
        var door=d.entranceDoor();
        if(door==null)return msg(d.startMode()==StartMode.PLATES?"start-door-required":"start-door-optional");
        return msg("start-door-region",Placeholder.unparsed("pos1",door.min().x()+","+door.min().y()+","+door.min().z()),
                Placeholder.unparsed("pos2",door.max().x()+","+door.max().y()+","+door.max().z()));
    }
    @Override protected void renderFooter() {
        set(45,Button.of(Material.ARROW,MenuListener.instance().messages().get("gui.common.back"),
                List.of(Component.empty(),msg("start-back-lore")),(p,c)->MenuListener.instance().later(root::open)));
        var lore=new ArrayList<Component>();
        if(root.dirty())lore.add(MenuListener.instance().messages().get("gui.common.unsaved"));
        lore.add(Component.empty());lore.add(msg("start-save-lore"));
        set(49,Button.of(Material.LIME_CONCRETE,MenuListener.instance().messages().get("gui.common.save"),lore,
                (p,c)->{root.saveDraft();MenuListener.instance().play(p,MenuListener.instance().sounds().save());}));
    }

}
