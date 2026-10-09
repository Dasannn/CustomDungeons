package dev.dasan.customdungeons.ability.control;

import dev.dasan.customdungeons.ability.*;
import java.util.List;
import org.bukkit.Material;

public final class AnchorSpearAbility extends ControlAbility {
    public AnchorSpearAbility() {super("anchor_spear",Material.TRIDENT,List.of(number("damage",4,0,20),ticks(160,40,400),number("leash",4,2,10),integer("hits",5,1,30)));}
}
