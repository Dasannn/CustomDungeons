package dev.dasan.customdungeons.session;

import dev.dasan.customdungeons.model.Point;
import dev.dasan.customdungeons.runtime.*;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/** Effect boundary: lifecycle and wave arithmetic can be exercised without a server. */
interface SessionServices {
    default void teleport(Player player, Point point) {}
    default void joined(DungeonSession session,Player player,Runnable ready) { ready.run(); }
    default boolean inside(DungeonSession session,Player player) {
        var at=player.getLocation();
        return DungeonSessionRuntime.containsDungeon(session.def(),at);
    }
    default boolean onExitPlate(DungeonSession session,Player player) { return false; }
    default Point destination(DungeonSession session,Player player) {
        return session.def().finishDestination()==dev.dasan.customdungeons.model.FinishDestination.PREVIOUS
                ? session.previous(player.getUniqueId()) : session.def().exit();
    }
    default void exiting(DungeonSession session,int seconds) {}
    default void reentered(DungeonSession session,Player player) {}
    default void departed(DungeonSession session,Player player) {}
    default void released(DungeonSession session) {}
    default void scoreboardRemoved(Player player) {}
    default boolean prepareStart(DungeonSession session) { return true; }
    default boolean canSpawnAt(Location at) { return true; }
    default boolean platesReady(DungeonSession session) { return false; }
    default void lobbyCountdown(DungeonSession session,int seconds,boolean cancelled) {}
    default void start(DungeonSession session) {}
    default void tick(DungeonSession session) {}
    default void recoveryTick(long tick) {}
    default boolean introTick(DungeonSession session) { return false; }
    default void introRestore(DungeonSession session,Player player) {}
    default void roomCleared(DungeonSession session) { session.openDoor(); }
    default void finish(DungeonSession session) {}
    default void leave(DungeonSession session, Player player) {}
    default void disconnected(DungeonSession session, Player player) { leave(session,player); }
    default void removed(DungeonSession session, ActiveMob mob, org.bukkit.event.Event event) {}
    default ActiveMob spawn(DungeonSession session, String template, Location at) { return null; }
    default Location spawnLocation(DungeonSession session, String spawner) { return null; }
    default TempBlocks tempBlocks() { return null; }
    default void stolen(UUID owner, ItemStack item, ActiveMob thief) {}
    default void observerFailed(RuntimeException error) {}
    default void invulnerable(Player player, boolean value) { player.setInvulnerable(value); }
}
