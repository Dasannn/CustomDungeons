package dev.dasan.customdungeons.gui.menu;

import dev.dasan.customdungeons.gui.*;
import dev.dasan.customdungeons.model.*;
import dev.dasan.customdungeons.ability.*;
import java.util.*;
import java.util.function.*;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.*;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

public final class PhaseListMenu extends MobMenuBase {
    public PhaseListMenu(Player p,MobMenu.MobDraft d,Menu parent) { super(p,"phases",d,parent); }
    @Override protected void renderFooter() {
        addButton();
    }
    private void addButton() {
        action(getInventory().getSize()-7,Material.LIME_DYE,"add-phase","",() -> {
            double threshold=data.phases.isEmpty() ? .66 : data.phases.getLast().threshold / 2;
            data.phases.add(new MobMenu.PhaseDraft(new PhaseDef(threshold,false,List.of(),List.of(),Map.of(),List.of(),0,List.of(),null,null,null,null,20)));
        });
    }
    @Override protected Component entryHeading() { return message("phase-list-heading"); }
    @Override protected int entryFirstRow() { return 4; }
    @Override protected int preferredRows() { return 6; }
    @Override protected void render() {
        section(13,"section-boss",Material.WHITE_STAINED_GLASS_PANE);
        bool(19,"boss",data.boss,v -> data.boss=v);
        select(21,"bar-color",data.color,Arrays.stream(net.kyori.adventure.bossbar.BossBar.Color.values()).map(Enum::name).toList(),v -> data.color=v);
        sound(23,"music",data.music,v -> data.music=v);
        bool(25,"vanilla-drops",data.drops,v -> data.drops=v);
        var buttons=new ArrayList<Button>();
        for(int i=0;i<data.phases.size();i++) {
            final int n=i; var phase=data.phases.get(i);
            buttons.add(entry(Material.NETHER_STAR,MenuListener.instance().messages().get("gui.mob.phase",Placeholder.unparsed("number",Integer.toString(i+1)),Placeholder.unparsed("threshold",formatValue(phase.threshold*100))),() -> new PhaseMenu(viewer,data,phase,this).open(),() -> data.phases.remove(n)));
        }
        entries(buttons);
    }
}
