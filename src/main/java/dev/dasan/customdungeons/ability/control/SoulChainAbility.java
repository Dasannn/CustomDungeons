package dev.dasan.customdungeons.ability.control;

import dev.dasan.customdungeons.ability.*;
import java.util.List;
import org.bukkit.Material;

public final class SoulChainAbility extends ControlAbility {
    public SoulChainAbility() {super("soul_chain",Material.IRON_CHAIN,List.of(ticks(160,40,400),number("distance",6,2,16),number("damage",2,0,10)));}
}
