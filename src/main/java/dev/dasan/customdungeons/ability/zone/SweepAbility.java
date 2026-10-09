package dev.dasan.customdungeons.ability.zone;

import java.util.List;
import org.bukkit.Material;

public final class SweepAbility extends ZoneAbility {
    public SweepAbility() {super("sweep",Material.IRON_SWORD,List.of(number("angle",120,60,240),number("reach",5,2,10),number("damage",10,0,30)));}
    @Override public int defaultTelegraphTicks() {return 16;}
}
