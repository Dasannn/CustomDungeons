package dev.dasan.customdungeons.gui;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class EditLocks {
    private final Map<String, UUID> locks = new HashMap<>();

    public synchronized boolean tryLock(String key, UUID admin) {
        Objects.requireNonNull(key);
        Objects.requireNonNull(admin);
        UUID owner = locks.putIfAbsent(key, admin);
        return owner == null || owner.equals(admin);
    }
    public synchronized void unlock(String key, UUID admin) { locks.remove(key, admin); }
    public synchronized Optional<UUID> holder(String key) { return Optional.ofNullable(locks.get(key)); }
    public synchronized void releaseAll(UUID admin) { locks.values().removeIf(admin::equals); }
}
