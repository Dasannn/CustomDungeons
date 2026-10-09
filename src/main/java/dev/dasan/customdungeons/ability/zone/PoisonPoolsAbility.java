package dev.dasan.customdungeons.ability.zone;

import java.util.List;
import org.bukkit.Material;

public final class PoisonPoolsAbility extends ZoneAbility {
    public PoisonPoolsAbility() {super("poison_pools",Material.SLIME_BALL,List.of(integer("count",2,1,6),number("radius",2,1,5),time("ticks",200,60,600),number("damage",1,0,10)));}
}
