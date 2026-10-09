package dev.dasan.customdungeons.ability.combat;

import dev.dasan.customdungeons.ability.*;
import java.util.List;
import org.bukkit.Material;

public final class PurgeAbility extends CombatAbility {
    public PurgeAbility() {super("purge",Material.MILK_BUCKET,List.of(number("radius",8,3,16)));}
}
