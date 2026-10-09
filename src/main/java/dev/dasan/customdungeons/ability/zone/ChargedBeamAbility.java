package dev.dasan.customdungeons.ability.zone;

import java.util.List;
import org.bukkit.Material;

public final class ChargedBeamAbility extends ZoneAbility {
    public ChargedBeamAbility() {super("charged_beam",Material.END_ROD,List.of(time("warning",30,10,80),number("length",16,4,32),number("width",1,.5,3),number("damage",14,0,40)));}
}
