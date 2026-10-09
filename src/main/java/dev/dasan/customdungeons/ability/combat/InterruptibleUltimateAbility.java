package dev.dasan.customdungeons.ability.combat;

import dev.dasan.customdungeons.ability.*;
import java.util.List;
import org.bukkit.Material;

public final class InterruptibleUltimateAbility extends CombatAbility {
    public InterruptibleUltimateAbility() {super("interruptible_ultimate",Material.NETHER_STAR,List.of(time("charge-ticks",80,40,200),number("interrupt-percent",10,1,50),number("radius",10,3,24),number("damage",30,0,80),new ParamSpec("lethal",ParamType.BOOLEAN,false,0,1)));}
}
