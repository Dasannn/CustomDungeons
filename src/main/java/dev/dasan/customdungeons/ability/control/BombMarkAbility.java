package dev.dasan.customdungeons.ability.control;

import dev.dasan.customdungeons.ability.*;
import java.util.List;
import org.bukkit.Material;

public final class BombMarkAbility extends ControlAbility {
    public BombMarkAbility() {super("bomb_mark",Material.TNT,List.of(ticks(100,60,200),number("damage",12,0,40),number("radius",3,1,10)));}
}
