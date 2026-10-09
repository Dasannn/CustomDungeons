package dev.dasan.customdungeons.ability.combat;

import dev.dasan.customdungeons.ability.*;
import java.util.List;
import org.bukkit.Material;

public final class ChargeAbility extends CombatAbility {
    public ChargeAbility() {super("charge",Material.IRON_SWORD,List.of(number("length",12,4,24),number("damage",10,0,30),time("stun-ticks",40,0,100)));}
}
