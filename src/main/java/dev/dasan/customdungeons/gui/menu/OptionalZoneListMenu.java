package dev.dasan.customdungeons.gui.menu;

import dev.dasan.customdungeons.config.*;
import dev.dasan.customdungeons.gui.*;
import dev.dasan.customdungeons.model.*;
import java.util.*;
import org.bukkit.Material;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

/** Zones have stable ids and no mandatory traversal order. */
public final class OptionalZoneListMenu extends DungeonPage<OptionalZoneDef> {
    private List<ValidationError> errors=List.of();
    public OptionalZoneListMenu(DungeonMenu root,Menu parent) {super("zones",root,parent);}
    @Override protected Runnable onSave() {return null;}
    @Override protected String createKey() {return "add-zone";}
    @Override protected List<OptionalZoneDef> entries() {return root.draft.get().optionalZones();}
    @Override protected void render() {
        errors=new Validator().validate(root.draft.get(),root.services.store.mobs(),root.services.store.spawnerPresets());
        super.render();
        long missing=java.util.stream.IntStream.range(0,entries().size()).filter(i->errors.stream().anyMatch(e->e.path().startsWith("optional-zones["+i+"]"))).count();
        summary(Material.MAP,msg("zones",Placeholder.unparsed("value",Integer.toString(entries().size()))),msg("zone-list-state",Placeholder.unparsed("complete",Long.toString(entries().size()-missing)),Placeholder.unparsed("missing",Long.toString(missing))),msg("zone-independent"));
    }
    @Override protected Button entry(OptionalZoneDef zone,int index) {
        var issues=errors.stream().filter(e->e.path().startsWith("optional-zones["+index+"]")).toList();
        var lore=new ArrayList<net.kyori.adventure.text.Component>();
        if(issues.isEmpty()) {lore.add(msg("zone-complete-region",Placeholder.component("region",OptionalZoneMenu.regionPosition(zone.region()))));lore.add(msg("zone-ready-totals",Placeholder.component("totals",OptionalZoneMenu.totals(root,zone))));lore.add(msg("zone-no-own-fields"));}
        else lore.add(msg("zone-incomplete-parts",Placeholder.component("region",msg(zone.region()!=null?"zone-region-ready":"zone-region-missing")),Placeholder.component("spawners",msg(zone.spawners().isEmpty()?"zone-no-spawners":"zone-has-spawners"))));
        issues.forEach(e->lore.add(OptionalZoneMenu.error(e)));lore.add(net.kyori.adventure.text.Component.empty());lore.add(msg(issues.isEmpty()?"zone-entry-edit":"zone-entry-complete"));if(issues.isEmpty())lore.add(msg("zone-entry-preview"));lore.add(msg("zone-entry-remove"));
        return Button.of(Material.MAP,msg(issues.isEmpty()?"zone-ready":"zone-missing",Placeholder.unparsed("value",zone.id())),lore,(p,c)->{
            if(!root.writable()) return;
            if(c.isShiftClick()&&c.isRightClick()) MenuListener.instance().later(()->{
                if(root.writable()) Inputs.confirm(p,msg("remove-zone"),()->{
                    if(!root.writable())return;
                    root.change(v->v.optionalZones=v.optionalZones.stream().filter(z->!z.id().equals(zone.id())).toList());refresh();
                });
            });
            else if(c.isRightClick()) OptionalZoneMenu.preview(root,zone);
            else MenuListener.instance().later(()->{if(root.writable())new OptionalZoneMenu(root,zone.id(),this).open();});
        });
    }
    @Override protected net.kyori.adventure.text.Component title() {return msg("zones-title",Placeholder.component("name",dev.dasan.customdungeons.text.Text.parse(root.draft.get().displayName())));}
    @Override protected void renderHeader() {GuiTheme.help(this,java.util.stream.IntStream.rangeClosed(1,4).mapToObj(i->msg("help-zones-"+i)).toList());tintName(8,net.kyori.adventure.text.format.NamedTextColor.GREEN);}
    @Override protected void renderFooter() {set(getInventory().getSize()-5,action("add-zone",Material.LIME_DYE,"",(p,c)->MenuListener.instance().later(()->{if(root.writable())create();}),msg("zone-id")));tintNavigation(net.kyori.adventure.text.format.NamedTextColor.GREEN);}
    @Override protected void create() {
        if(!root.writable())return;
        Inputs.text(viewer,msg("zone-id"),"",32,id->{
            if(!root.writable())return;
            if(!DungeonMenu.validId(id)) {root.tell("invalid-id");return;}
            if(root.draft.get().rooms().stream().anyMatch(r->r.id().equals(id))||entries().stream().anyMatch(z->z.id().equals(id))) {root.tell("zone-duplicate");return;}
            root.change(v->v.optionalZones=DungeonMenu.append(v.optionalZones,new OptionalZoneDef(id,null,List.of())));
            new OptionalZoneMenu(root,id,this).open();
        });
    }
}
