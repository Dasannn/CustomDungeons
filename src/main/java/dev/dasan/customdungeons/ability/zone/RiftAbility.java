package dev.dasan.customdungeons.ability.zone;

import java.util.List;
import org.bukkit.Material;

public final class RiftAbility extends ZoneAbility {
    public RiftAbility() {super("rift",Material.ENDER_PEARL,List.of(number("min-distance",8,4,32),number("max-distance",16,4,32)));}
}
