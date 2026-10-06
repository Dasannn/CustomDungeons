package dev.dasan.customdungeons.session;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class JoinRulesTest {
    private final Instant now = Instant.parse("2026-10-05T00:00:00Z");
    private JoinResult check(SessionState state, boolean enabled, boolean in, int players, int max,
                             boolean limitBypass, Instant cooldown, boolean cooldownBypass, boolean permission) {
        return JoinRules.check(state, enabled, in, players, max, limitBypass, cooldown, now, cooldownBypass, permission);
    }
    @Test void alreadyInComesFirst() {
        assertEquals(JoinResult.ALREADY_IN, check(SessionState.RESETTING, false, true, 2, 1, false, now.plusSeconds(1), false, false));
    }
    @Test void disabled() {
        assertEquals(JoinResult.DISABLED, check(SessionState.RUNNING, false, false, 2, 1, false, now.plusSeconds(1), false, false));
    }
    @Test void noPermission() {
        assertEquals(JoinResult.NO_PERMISSION, check(SessionState.RUNNING, true, false, 2, 1, false, now.plusSeconds(1), false, false));
    }
    @Test void runningAndTerminalStatesReject() {
        for (var state : new SessionState[]{SessionState.RUNNING, SessionState.COMPLETED, SessionState.FAILED})
            assertEquals(state==SessionState.RUNNING?JoinResult.RUNNING:JoinResult.RESETTING, check(state, true, false, 2, 1, false, now.plusSeconds(1), false, true));
    }
    @Test void resetting() {
        assertEquals(JoinResult.RESETTING, check(SessionState.RESETTING, true, false, 2, 1, false, now.plusSeconds(1), false, true));
    }
    @Test void cooldownBeforeFull() {
        assertEquals(JoinResult.COOLDOWN, check(SessionState.LOBBY, true, false, 2, 1, false, now.plusSeconds(1), false, true));
    }
    @Test void full() {
        assertEquals(JoinResult.FULL, check(SessionState.LOBBY, true, false, 2, 2, false, null, false, true));
    }
    @Test void ok() {
        for (var state : new SessionState[]{SessionState.FREE, SessionState.LOBBY})
            assertEquals(JoinResult.OK, check(state, true, false, 1, 2, false, null, false, true));
    }
    @Test void zeroMaxMeansUnlimited() {
        assertEquals(JoinResult.OK, check(SessionState.LOBBY, true, false, 100, 0, false, null, false, true));
    }
    @Test void bypassLimitIgnoresFull() {
        assertEquals(JoinResult.OK, check(SessionState.LOBBY, true, false, 2, 1, true, null, false, true));
    }
    @Test void bypassCooldownAndExpiry() {
        assertEquals(JoinResult.OK, check(SessionState.FREE, true, false, 0, 1, false, now.plusSeconds(1), true, true));
        assertEquals(JoinResult.OK, check(SessionState.FREE, true, false, 0, 1, false, now, false, true));
        assertEquals(JoinResult.OK, check(SessionState.FREE, true, false, 0, 1, false, now.minusSeconds(1), false, true));
    }
}
