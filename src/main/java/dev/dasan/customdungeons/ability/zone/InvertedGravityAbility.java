package dev.dasan.customdungeons.ability.zone;

import java.util.List;
import org.bukkit.Material;

public final class InvertedGravityAbility extends ZoneAbility {
    public InvertedGravityAbility() {super("inverted_gravity",Material.FEATHER,List.of(number("radius",5,2,12),time("ticks",60,20,160),number("damage",0,0,10)));}
}
