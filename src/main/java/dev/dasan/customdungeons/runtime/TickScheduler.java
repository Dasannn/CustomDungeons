package dev.dasan.customdungeons.runtime;
/** Queued work executed by the session's single ticker; never creates Bukkit tasks. */
public interface TickScheduler {
    void runLater(int ticks, Runnable task);
    long currentTick();
}
