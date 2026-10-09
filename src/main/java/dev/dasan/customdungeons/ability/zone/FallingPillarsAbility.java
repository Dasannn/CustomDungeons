package dev.dasan.customdungeons.ability.zone;

import java.util.List;
import org.bukkit.Material;

public final class FallingPillarsAbility extends ZoneAbility {
    public FallingPillarsAbility() {super("falling_pillars",Material.STONE_BRICKS,List.of(integer("count",3,1,8),time("warning",30,20,80),number("damage",10,0,30),time("obstacle-ticks",120,0,400)));}
}
