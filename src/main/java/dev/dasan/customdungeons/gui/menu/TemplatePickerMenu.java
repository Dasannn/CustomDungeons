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

public final class TemplatePickerMenu extends DungeonPage<MobTemplate> {
    private final Consumer<String> selected;
    private final Menu destination;
    public TemplatePickerMenu(DungeonMenu root,Menu parent,Consumer<String> selected) {super("template",root,parent);this.selected=selected;destination=parent;}
    @Override protected List<MobTemplate> entries() {return root.services.store.mobs().values().stream().sorted(Comparator.comparing(MobTemplate::id)).toList();}
    static Material egg(String type) {
        Material material=Material.matchMaterial(type.replace("minecraft:","").toUpperCase(Locale.ROOT)+"_SPAWN_EGG");
        return material==null?Material.EGG:material;
    }
    @Override protected Button entry(MobTemplate mob,int index) {
        return Button.of(egg(mob.entityType()),msg("template-name",Placeholder.unparsed("id",mob.id()),Placeholder.component("name",dev.dasan.customdungeons.text.Text.parse(mob.displayName()))),
                java.util.stream.Stream.concat(IntelligenceMenu.markerLore(mob).stream(),java.util.stream.Stream.of(msg("template-lore"))).toList(),(p,c)->{if(root.writable()){selected.accept(mob.id());MenuListener.instance().later(destination::open);}});
    }
    @Override protected void render() {
        super.render();
        set(4,GuiTheme.information(Material.BOOK,msg("template"),List.of(msg("template-summary-lore"),msg("list-summary",Placeholder.unparsed("value",Integer.toString(entries().size()))))));
    }
    @Override protected boolean canCreate() {return false;}
    @Override protected Runnable onSave() {return null;}
    @Override protected void create() {tell("pick-template");}
}
