package dev.dasan.customdungeons.ability.control;

import dev.dasan.customdungeons.ability.*;
import java.util.List;
import org.bukkit.Material;

public final class TacticalSummonAbility extends ControlAbility {
    public TacticalSummonAbility() {super("tactical_summon",Material.ZOMBIE_SPAWN_EGG,List.of(
            new ParamSpec("template",ParamType.MOB_TEMPLATE,"",0,0),integer("count",2,1,10),integer("max-alive",4,1,20),number("radius",3,0,32)));}
}
