package dev.dasan.customdungeons.ability.combat;

import dev.dasan.customdungeons.ability.*;
import java.util.List;
import org.bukkit.Material;

public final class SilenceAbility extends CombatAbility {
    public SilenceAbility() {super("silence",Material.ENDER_PEARL,List.of(number("radius",6,3,12),time("ticks",80,20,200)));}
}
