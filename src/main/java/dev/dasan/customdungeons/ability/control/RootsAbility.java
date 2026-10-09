package dev.dasan.customdungeons.ability.control;

import dev.dasan.customdungeons.ability.*;
import java.util.List;
import org.bukkit.Material;

public final class RootsAbility extends ControlAbility {
    public RootsAbility() {super("roots",Material.MANGROVE_ROOTS,List.of(ticks(80,20,200),number("health",10,1,100)));}
}
