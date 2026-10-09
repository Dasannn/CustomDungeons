package dev.dasan.customdungeons.ability.combat;

import dev.dasan.customdungeons.ability.*;
import java.util.List;
import org.bukkit.Material;

public final class BossTotemAbility extends CombatAbility {
    public BossTotemAbility() {super("boss_totem",Material.TOTEM_OF_UNDYING,List.of(number("health",30,5,500),new ParamSpec("protect",ParamType.BOOLEAN,false,0,1),number("heal-percent",2,.5,10),number("reduction",30,10,60),time("ticks",400,100,1200)));}
}
