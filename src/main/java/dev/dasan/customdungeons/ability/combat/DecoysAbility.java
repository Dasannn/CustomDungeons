package dev.dasan.customdungeons.ability.combat;

import dev.dasan.customdungeons.ability.*;
import java.util.List;
import org.bukkit.Material;

public final class DecoysAbility extends CombatAbility {
    public DecoysAbility() {super("decoys",Material.CARVED_PUMPKIN,List.of(integer("count",2,1,6),number("health",10,1,100),number("damage-percent",25,0,100),time("ticks",300,100,1200)));}
}
