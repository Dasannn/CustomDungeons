package dev.dasan.customdungeons.ability.control;

import dev.dasan.customdungeons.ability.*;
import java.util.List;
import org.bukkit.Material;

public final class LevitationCageAbility extends ControlAbility {
    public LevitationCageAbility() {super("levitation_cage",Material.SHULKER_SHELL,List.of(
            integer("level",1,1,5),number("damage",1,0,10),ticks(80,20,300),number("release-damage",20,1,200)));}
}
