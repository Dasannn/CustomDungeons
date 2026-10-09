package dev.dasan.customdungeons.ability.control;

import dev.dasan.customdungeons.ability.*;
import java.util.List;
import org.bukkit.Material;

public final class DrainGrabAbility extends ControlAbility {
    public DrainGrabAbility() {super("drain_grab",Material.FERMENTED_SPIDER_EYE,List.of(
            ticks(120,20,300),number("damage",2,0,10),number("healing",100,0,200),integer("jumps",8,3,20),number("release-damage",20,1,200)));}
}
