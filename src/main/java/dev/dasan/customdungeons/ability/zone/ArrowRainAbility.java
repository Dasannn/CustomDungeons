package dev.dasan.customdungeons.ability.zone;

import java.util.List;
import org.bukkit.Material;

public final class ArrowRainAbility extends ZoneAbility {
    public ArrowRainAbility() {super("arrow_rain",Material.ARROW,List.of(number("radius",4,2,10),time("ticks",60,20,160),integer("rate",6,2,20),number("damage",3,0,10)));}
}
