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
        return Button.of(egg(mob.entityType()),msg("template-name",Placeholder.unparsed("id",mob.id()),Placeholder.unparsed("name",mob.displayName())),
                List.of(msg("template-lore")),(p,c)->{if(root.writable()){selected.accept(mob.id());MenuListener.instance().later(destination::open);}});
    }
    @Override protected void render() {
        super.render();
        set(4,Button.of(Material.BOOK,msg("template"),List.of(msg("template-lore")),(p,c)->{}));
    }
    @Override protected void create() {tell("pick-template");}
}
