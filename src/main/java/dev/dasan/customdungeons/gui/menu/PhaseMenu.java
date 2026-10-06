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

public final class PhaseMenu extends MobMenuBase {
    private final MobMenu.PhaseDraft phase;
    public PhaseMenu(Player p,MobMenu.MobDraft d,MobMenu.PhaseDraft phase,Menu parent) { super(p,"phase",d,parent); this.phase=phase; }
    @Override protected void render() {
        section(10,"section-transition",Material.WHITE_STAINED_GLASS_PANE);
        section(12,"section-combat",Material.WHITE_STAINED_GLASS_PANE);
        section(14,"section-effects",Material.WHITE_STAINED_GLASS_PANE);
        section(16,"section-audio",Material.WHITE_STAINED_GLASS_PANE);
        number(19,"threshold",phase.threshold*100,.01,99.99,v -> phase.threshold=v/100);
        number(28,"heal",phase.heal,0,100,v -> phase.heal=v);
        number(37,"invulnerable-ticks",phase.invulnerable,0,1200,v -> phase.invulnerable=(int)v);
        action(21,"abilities",phase.abilities.size(),() -> new AbilityListMenu(viewer,data,phase,this).open());
        action(30,"combos",phase.combos.size(),() -> ComboMenu.list(viewer,data,phase,this).open());
        action(39,"equipment",phase.equipment.size(),() -> new EquipmentMenu(viewer,data,phase,this).open());
        action(41,"potions",phase.potions.size(),() -> new PotionMenu(viewer,data,phase,this).open());
        bool(40,"replace",phase.replace,v -> phase.replace=v);
        text(23,"title",phase.title,v -> phase.title=v.isBlank()?null:v);
        text(32,"subtitle",phase.subtitle,v -> phase.subtitle=v.isBlank()?null:v);
        sound(25,"sound",phase.sound,v -> phase.sound=v);
        sound(34,"music",phase.music,v -> phase.music=v);
        action(43,"summons",phase.summons.size(),() -> summons().open());
    }
    @Override protected MobMenu.Loadout summaryLoadout() { return phase; }

    private Menu summons() {
        return new MobMenuBase(viewer,"summons",data,this) {
            @Override protected int contentCount() { return phase.summons.size(); }
            @Override protected MobMenu.Loadout summaryLoadout() { return phase; }
            @Override protected void renderFooter() {
                action(getInventory().getSize()-7,"add","",() -> choose(viewer,"template",store().mobs().keySet().stream().sorted().toList(),this,
                    id -> phase.summons.add(new WaveEntry(id,1,0))));
            }
            @Override protected void render() {
                var buttons=new ArrayList<Button>();
                for(int i=0;i<phase.summons.size();i++) {
                    final int n=i; WaveEntry entry=phase.summons.get(i);
                    buttons.add(entry(Material.EGG,entry.templateId()+" x"+entry.count(),() -> new MobMenuBase(viewer,"summon-editor",data,this) {
                        @Override protected void render() {
                            section(11,"section-template",Material.WHITE_STAINED_GLASS_PANE);
                            section(13,"section-count",Material.WHITE_STAINED_GLASS_PANE);
                            section(15,"section-delay",Material.WHITE_STAINED_GLASS_PANE);
                            WaveEntry current=phase.summons.get(n);
                            select(20,"template",current.templateId(),store().mobs().keySet().stream().sorted().toList(),v -> phase.summons.set(n,new WaveEntry(v,current.count(),current.delayTicks())));
                            number(22,"count",current.count(),1,config().limits().maxAliveMobsPerSession(),v -> phase.summons.set(n,new WaveEntry(current.templateId(),(int)v,current.delayTicks())));
                            number(24,"delay",current.delayTicks(),0,72000,v -> phase.summons.set(n,new WaveEntry(current.templateId(),current.count(),(int)v)));
                        }
                    }.open(),() -> phase.summons.remove(n)));
                }
                entries(buttons);
            }
        };
    }
}
