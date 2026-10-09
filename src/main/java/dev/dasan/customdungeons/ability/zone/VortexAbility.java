package dev.dasan.customdungeons.ability.zone;

import java.util.List;
import org.bukkit.Material;

public final class VortexAbility extends ZoneAbility {
    public VortexAbility() {super("vortex",Material.ENDER_EYE,List.of(number("radius",8,3,16),number("force",.3,.1,1),time("ticks",40,20,100),number("damage",6,0,20),number("burst-radius",3,1,8)));}
}
