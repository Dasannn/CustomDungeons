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
        section(4,"section-phase",Material.NETHER_STAR);
        number(11,"threshold",phase.threshold*100,.01,99.99,v -> phase.threshold=v/100);
        bool(13,"replace",phase.replace,v -> phase.replace=v);
        action(19,"abilities",phase.abilities.size(),() -> new AbilityListMenu(viewer,data,phase,this).open());
        action(21,"combos",phase.combos.size(),() -> ComboMenu.list(viewer,data,phase,this).open());
        action(23,"equipment",phase.equipment.size(),() -> new EquipmentMenu(viewer,data,phase,this).open());
        action(25,"potions",phase.potions.size(),() -> new PotionMenu(viewer,data,phase,this).open());
        number(15,"heal",phase.heal,0,100,v -> phase.heal=v);
        text(29,"title",phase.title,v -> phase.title=v.isBlank()?null:v);
        text(31,"subtitle",phase.subtitle,v -> phase.subtitle=v.isBlank()?null:v);
        sound(38,"sound",phase.sound,v -> phase.sound=v);
        sound(40,"music",phase.music,v -> phase.music=v);
        number(33,"invulnerable-ticks",phase.invulnerable,0,1200,v -> phase.invulnerable=(int)v);
        action(42,"summons",phase.summons.size(),() -> summons().open());
    }
    private Menu summons() {
        return new MobMenuBase(viewer,"summons",data,this) {
            @Override protected void render() {
                action(4,"add","",() -> choose(viewer,"template",store().mobs().keySet().stream().sorted().toList(),this,
                    id -> phase.summons.add(new WaveEntry(id,1,0))));
                var buttons=new ArrayList<Button>();
                for(int i=0;i<phase.summons.size();i++) {
                    final int n=i; WaveEntry entry=phase.summons.get(i);
                    buttons.add(entry(Material.EGG,entry.templateId()+" x"+entry.count(),() -> new MobMenuBase(viewer,"summons",data,this) {
                        @Override protected void render() {
                            WaveEntry current=phase.summons.get(n);
                            select(11,"template",current.templateId(),store().mobs().keySet().stream().sorted().toList(),v -> phase.summons.set(n,new WaveEntry(v,current.count(),current.delayTicks())));
                            number(13,"count",current.count(),1,config().limits().maxAliveMobsPerSession(),v -> phase.summons.set(n,new WaveEntry(current.templateId(),(int)v,current.delayTicks())));
                            number(15,"delay",current.delayTicks(),0,72000,v -> phase.summons.set(n,new WaveEntry(current.templateId(),current.count(),(int)v)));
                        }
                    }.open(),() -> phase.summons.remove(n)));
                }
                entries(buttons);
            }
        };
    }
}
