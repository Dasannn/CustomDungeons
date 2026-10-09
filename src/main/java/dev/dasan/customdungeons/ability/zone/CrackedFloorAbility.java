package dev.dasan.customdungeons.ability.zone;

import java.util.List;
import org.bukkit.Material;

public final class CrackedFloorAbility extends ZoneAbility {
    public CrackedFloorAbility() {super("cracked_floor",Material.CRACKED_STONE_BRICKS,List.of(number("radius",8,3,16),time("warning",40,20,100),number("damage",8,0,30),integer("waves",1,1,3)));}
}
