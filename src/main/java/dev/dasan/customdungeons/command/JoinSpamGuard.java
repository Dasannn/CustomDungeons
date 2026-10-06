package dev.dasan.customdungeons.command;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.LongSupplier;

/** Main-thread rejection throttle. The injected clock returns monotonic milliseconds. */
public final class JoinSpamGuard {
    private final LongSupplier clock;
    private final Map<UUID, Long> lastMessage = new HashMap<>();
    public JoinSpamGuard() { this(() -> System.nanoTime() / 1_000_000); }
    public JoinSpamGuard(LongSupplier clock) { this.clock = Objects.requireNonNull(clock); }
    public boolean allow(UUID player) {
        long now = clock.getAsLong();
        // Also bounds entries for offline targets whose join was rejected.
        lastMessage.entrySet().removeIf(entry -> now - entry.getValue() >= 3000);
        if (lastMessage.containsKey(player)) return false;
        lastMessage.put(player, now);
        return true;
    }
    public void forget(UUID player) { lastMessage.remove(player); }
}
