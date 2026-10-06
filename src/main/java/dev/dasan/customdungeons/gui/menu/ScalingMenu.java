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

public final class ScalingMenu extends DungeonEditor {
    public ScalingMenu(DungeonMenu root) {super("scaling",root,root);}
    @Override protected void render() {
        var s=root.draft.get().scaling();
        set(4,Button.of(Material.ANVIL,msg("scaling"),List.of(msg("scaling-help")),(p,c)->{}));
        example(30,"scaling-example-mobs",s.extraMobsPerPlayer());
        example(32,"scaling-example-health",s.extraHealthPerPlayer());
        integer(12,"extra-mobs",(int)Math.round(s.extraMobsPerPlayer()*100),0,500,n->root.change(v->v.scaling=new ScalingDef(n/100.0,v.scaling.extraHealthPerPlayer())));
        integer(14,"extra-health",(int)Math.round(s.extraHealthPerPlayer()*100),0,500,n->root.change(v->v.scaling=new ScalingDef(v.scaling.extraMobsPerPlayer(),n/100.0)));
    }
    private void example(int slot,String key,double increment) {
        int minimum=root.draft.get().minPlayers();
        set(slot,Button.of(Material.PAPER,msg("scaling-preview"),List.of(msg(key,Placeholder.unparsed("minimum",Integer.toString(minimum)),
                Placeholder.unparsed("percent",Inputs.formatNumber(GuiLayout.scalingExample(increment,minimum),0))),
                msg("scaling-example-lore")),(p,c)->{}));
    }
}
