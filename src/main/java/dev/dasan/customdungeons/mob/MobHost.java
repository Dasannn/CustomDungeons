package dev.dasan.customdungeons.mob;

import dev.dasan.customdungeons.runtime.ActiveMob;
import dev.dasan.customdungeons.runtime.TempBlocks;
import dev.dasan.customdungeons.runtime.TickScheduler;
import java.util.Collection;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.Nullable;

/** One encounter. Extend this contract only with default methods. */
public interface MobHost {
    UUID id();
    /** Only eligible ability targets. */
    Collection<Player> players();
    /** Sound, title and music recipients at the emission point; independent of targets. */
    Collection<Player> audience(Location at);
    /** Optional block area in the encounter's world; upper bounds are exclusive. */
    @Nullable MobArea area();
    Collection<ActiveMob> mobs();
    @Nullable ActiveMob spawnMinion(String templateId, Location at, ActiveMob owner);
    TempBlocks tempBlocks();
    TickScheduler scheduler();
    /** Registers stolen items for return when they are not recovered. */
    void onItemStolen(UUID owner, ItemStack item, ActiveMob thief);
}
