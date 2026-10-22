package dev.dasan.customdungeons.mob;

import dev.dasan.customdungeons.runtime.ActiveMob;
import dev.dasan.customdungeons.model.MobTemplate;
import dev.dasan.customdungeons.runtime.TempBlocks;
import dev.dasan.customdungeons.runtime.TickScheduler;
import java.util.Collection;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.Nullable;

/** One encounter. Extend this contract only with default methods. */
public interface MobHost {
    UUID id();
    /** Optional provenance, written before an entity is exposed to spawn events. */
    default void markOrigin(org.bukkit.entity.Entity entity) {}
    /** Only eligible ability targets. */
    Collection<Player> players();
    /** Additional eligibility checked again when an effect uses a previously selected target. */
    default boolean excludesTarget(LivingEntity target) { return false; }
    /** Sound, title and music recipients at the emission point; independent of targets. */
    Collection<Player> audience(Location at);
    /** Optional block area in the encounter's world; upper bounds are exclusive. */
    @Nullable MobArea area();
    /** Local combat/progress view, never a global capacity count. */
    Collection<ActiveMob> mobs();
    /** Reserves one creature globally until close, or rejects it. Auxiliary creatures retain the lease. */
    default @Nullable MobCapacity.Reservation reserveMobSlot() { return null; }
    @Nullable ActiveMob spawnMinion(String templateId, Location at, ActiveMob owner);
    /** Creates and tracks an inline template under the same capacity/lifecycle rules; null means rejected. */
    default @Nullable ActiveMob spawnMinionTemplate(MobTemplate template, Location at, ActiveMob owner) { return null; }
    TempBlocks tempBlocks();
    TickScheduler scheduler();
    /** Registers stolen items for return when they are not recovered. */
    void onItemStolen(UUID owner, ItemStack item, ActiveMob thief);
}
