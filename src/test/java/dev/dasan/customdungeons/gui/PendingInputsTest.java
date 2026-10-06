package dev.dasan.customdungeons.gui;

import java.util.UUID;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PendingInputsTest {
    private final UUID player = UUID.randomUUID();
    private final UUID token = UUID.randomUUID();
    private final EditLocks locks = new EditLocks();
    private final PendingInputs pending = new PendingInputs(locks::releaseAll);
    private final Timeout task = new Timeout();

    private void dialog() {
        assertTrue(locks.tryLock("original", player));
        pending.begin(player, token);
        pending.timeout(player, token, task);
        pending.shown(player, token);
    }
    @Test void openingAnotherMenuThenClosingCannotResumeAbandonedEditor() {
        dialog();
        pending.abandon(player); // Opening another Menu invalidates the old dialog.
        assertFalse(pending.finish(player, token)); // Late response or timeout cannot reopen it.
        assertTrue(task.isCancelled());
        assertTrue(locks.holder("original").isEmpty());
        assertFalse(pending.inventoryClosed(player)); // Closing the new menu is harmless.
        assertFalse(pending.matches(player, token));
    }
    @Test void closingWithoutDialogTransitionAbandonsInputAndUnlocksEditor() {
        dialog();
        assertFalse(pending.opening(player));
        assertFalse(pending.inventoryClosed(player));
        assertFalse(pending.finish(player, token));
        assertTrue(task.isCancelled());
        assertTrue(locks.holder("original").isEmpty());
    }
    @Test void initialInventoryClosePreservesDialogAndLocks() {
        locks.tryLock("original", player);
        pending.begin(player, token);
        pending.timeout(player, token, task);
        assertTrue(pending.opening(player));
        assertTrue(pending.inventoryClosed(player));
        assertFalse(task.isCancelled());
        pending.shown(player, token);
        assertFalse(pending.opening(player));
        assertTrue(pending.active(player)); // A deferred previous close must retain the Dialog locks.
        assertTrue(pending.matches(player, token));
        assertEquals(player, locks.holder("original").orElseThrow());
    }
    @Test void acceptingOrCancellingCancelsTimeoutExactlyOnce() {
        dialog();
        assertTrue(pending.finish(player, token));
        assertTrue(task.isCancelled());
        assertFalse(pending.finish(player, token));
        pending.release(player);
        assertEquals(1, task.cancellations);
    }
    @Test void disconnectOrDisableCancelsTimeoutAndRejectsLateResponse() {
        dialog();
        pending.release(player);
        assertTrue(task.isCancelled());
        assertFalse(pending.finish(player, token));
    }
    @Test void replacementCancelsOldTimeoutAndOldCallbacksCannotConsumeNewInput() {
        dialog();
        UUID replacement = UUID.randomUUID();
        Timeout next = new Timeout();
        pending.begin(player, replacement);
        pending.timeout(player, replacement, next);
        assertTrue(task.isCancelled());
        assertFalse(pending.finish(player, token));
        assertFalse(next.isCancelled());
        assertTrue(pending.finish(player, replacement));
        assertTrue(next.isCancelled());
    }
    @Test void taskRegisteredAfterReleaseIsCancelledInsteadOfRetained() {
        pending.begin(player, token);
        pending.release(player);
        pending.timeout(player, token, task);
        assertTrue(task.isCancelled());
    }
    @Test void staleTokenDoesNotCancelCurrentTimerOrReleaseOtherPlayersLocks() {
        dialog();
        UUID other = UUID.randomUUID();
        locks.tryLock("other", other);
        assertFalse(pending.finish(player, UUID.randomUUID()));
        assertFalse(task.isCancelled());
        pending.abandon(player);
        assertEquals(other, locks.holder("other").orElseThrow());
    }
    private static final class Timeout implements BukkitTask {
        int cancellations;
        @Override public int getTaskId() { return 1; }
        @Override public Plugin getOwner() { return null; }
        @Override public boolean isSync() { return true; }
        @Override public boolean isCancelled() { return cancellations > 0; }
        @Override public void cancel() { cancellations++; }
    }
}
