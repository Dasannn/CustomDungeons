package dev.dasan.customdungeons.ability.combat;

import dev.dasan.customdungeons.ability.*;
import java.util.List;
import org.bukkit.Material;

public final class PainLinkAbility extends CombatAbility {
    public PainLinkAbility() {super("pain_link",Material.IRON_CHAIN,List.of(time("ticks",160,60,300),number("percent",30,10,100),number("cap",8,2,40),number("distance",12,6,24)));}
}
