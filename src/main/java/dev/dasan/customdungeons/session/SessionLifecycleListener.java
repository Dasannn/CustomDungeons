package dev.dasan.customdungeons.session;
import dev.dasan.customdungeons.storage.RunResult;
import java.util.Set;
import java.util.UUID;
public interface SessionLifecycleListener {
    default void onStateChange(DungeonSession s, SessionState from, SessionState to) {}
    default void onLobbyFull(DungeonSession s) {}
    default void onFinished(DungeonSession s, RunResult result, Set<UUID> survivors) {}
}
