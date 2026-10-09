package dev.dasan.customdungeons.ability.combat;

import dev.dasan.customdungeons.ability.*;
import java.util.List;
import org.bukkit.Material;

public final class BlinkBehindAbility extends CombatAbility {
    public BlinkBehindAbility() {super("blink_behind",Material.ENDER_EYE,List.of(number("distance",2,1,4),number("bonus",50,0,200)));}
}
