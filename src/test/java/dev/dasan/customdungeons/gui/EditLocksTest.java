package dev.dasan.customdungeons.gui;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EditLocksTest {
    @Test void lockIsExclusiveAndReleasable() {
        EditLocks locks = new EditLocks();
        UUID first = UUID.randomUUID(), second = UUID.randomUUID();
        assertTrue(locks.tryLock("dungeon", first));
        assertTrue(locks.tryLock("dungeon", first));
        assertFalse(locks.tryLock("dungeon", second));
        locks.unlock("dungeon", second);
        assertEquals(first, locks.holder("dungeon").orElseThrow());
        locks.unlock("dungeon", first);
        assertTrue(locks.holder("dungeon").isEmpty());
        assertTrue(locks.tryLock("dungeon", second));
    }
    @Test void releaseAllOnlyReleasesOwnersLocks() {
        EditLocks locks = new EditLocks();
        UUID first = UUID.randomUUID(), second = UUID.randomUUID();
        locks.tryLock("a", first);
        locks.tryLock("b", first);
        locks.tryLock("c", second);
        locks.releaseAll(first);
        assertTrue(locks.holder("a").isEmpty());
        assertTrue(locks.holder("b").isEmpty());
        assertEquals(second, locks.holder("c").orElseThrow());
    }
}
