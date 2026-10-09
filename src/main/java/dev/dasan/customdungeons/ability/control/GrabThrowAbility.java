package dev.dasan.customdungeons.ability.control;

import dev.dasan.customdungeons.ability.*;
import java.util.List;
import org.bukkit.Material;

public final class GrabThrowAbility extends ControlAbility {
    public GrabThrowAbility() {super("grab_throw",Material.LEAD,List.of(
            number("reach",4,1,8),ticks(30,10,100),number("force",1.5,.5,3),number("damage",6,0,20),number("release-damage",20,1,200)));}
}
