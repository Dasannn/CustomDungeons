package dev.dasan.customdungeons.gui.menu;

import dev.dasan.customdungeons.gui.*;
import dev.dasan.customdungeons.model.*;
import dev.dasan.customdungeons.ability.*;
import java.util.*;
import java.util.function.*;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.*;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

public final class StatsMenu extends MobMenuBase {
    public StatsMenu(Player p, MobMenu.MobDraft d, Menu parent) { super(p, "stats", d, parent); }
    @Override protected void render() {
        number(10, "health", data.health, 0, 100000, v -> data.health = v);
        number(11, "damage", data.damage, 0, 10000, v -> data.damage = v);
        number(12, "speed", data.speed, 0, 10, v -> data.speed = v);
        number(13, "resistance", data.resistance, 0, 1, v -> data.resistance = v);
        number(14, "scale", data.scale, 0, 16, v -> data.scale = v);
    }
}
