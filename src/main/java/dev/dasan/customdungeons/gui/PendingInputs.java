package dev.dasan.customdungeons.gui;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import org.bukkit.scheduler.BukkitTask;

/** Main-thread dialog lifecycle, independent of inventories and the scheduler. */
final class PendingInputs {
    private static final class Entry {
        final UUID token;
        BukkitTask timeout;
        boolean opening = true;
        Entry(UUID token) { this.token = token; }
    }
    private final Map<UUID, Entry> entries = new HashMap<>();
    private final Consumer<UUID> releaseLocks;

    PendingInputs(Consumer<UUID> releaseLocks) { this.releaseLocks = releaseLocks; }
    void begin(UUID player, UUID token) {
        release(player);
        entries.put(player, new Entry(token));
    }
    boolean matches(UUID player, UUID token) {
        Entry entry = entries.get(player);
        return entry != null && entry.token.equals(token);
    }
    boolean active(UUID player) { return entries.containsKey(player); }
    void timeout(UUID player, UUID token, BukkitTask task) {
        if (matches(player, token)) { entries.get(player).timeout = task; }
        else { task.cancel(); }
    }
    void shown(UUID player, UUID token) {
        if (matches(player, token)) { entries.get(player).opening = false; }
    }
    boolean opening(UUID player) {
        Entry entry = entries.get(player);
        return entry != null && entry.opening;
    }
    /** Returns true only for the inventory close caused by showing this Dialog. */
    boolean inventoryClosed(UUID player) {
        if (opening(player)) { return true; }
        abandon(player);
        return false;
    }
    boolean finish(UUID player, UUID token) {
        if (!matches(player, token)) { return false; }
        release(player);
        return true;
    }
    void release(UUID player) {
        Entry entry = entries.remove(player);
        if (entry != null && entry.timeout != null) { entry.timeout.cancel(); }
    }
    void abandon(UUID player) {
        if (entries.containsKey(player)) {
            release(player);
            releaseLocks.accept(player);
        }
    }
}
