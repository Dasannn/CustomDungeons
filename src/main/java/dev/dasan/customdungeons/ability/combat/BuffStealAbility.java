package dev.dasan.customdungeons.ability.combat;

import dev.dasan.customdungeons.ability.*;
import java.util.List;
import org.bukkit.Material;

public final class BuffStealAbility extends CombatAbility {
    public BuffStealAbility() {super("buff_steal",Material.POTION,List.of(integer("count",1,1,3),time("max-ticks",600,100,1200)));}
}
