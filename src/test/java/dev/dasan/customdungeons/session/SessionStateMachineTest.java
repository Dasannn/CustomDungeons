package dev.dasan.customdungeons.session;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SessionStateMachineTest {
    @Test void allValidTransitions() {
        var machine = new SessionStateMachine();
        assertEquals(SessionState.FREE, machine.state());
        machine.openLobby();
        assertEquals(SessionState.LOBBY, machine.state());
        machine.start();
        assertEquals(SessionState.RUNNING, machine.state());
        machine.complete();
        assertEquals(SessionState.COMPLETED, machine.state());
        machine.beginReset();
        assertEquals(SessionState.RESETTING, machine.state());
        machine.finishReset();
        assertEquals(SessionState.FREE, machine.state());
        machine.openLobby();
        machine.fail();
        assertEquals(SessionState.FAILED, machine.state());
        machine.beginReset();
        machine.finishReset();
        machine.openLobby();
        machine.start();
        machine.fail();
        assertEquals(SessionState.FAILED, machine.state());
    }

    @Test void invalidTransitionThrows() {
        for (SessionState state : SessionState.values()) {
            for (int operation = 0; operation < 6; operation++) {
                var machine = at(state);
                boolean valid = switch (operation) {
                    case 0 -> state == SessionState.FREE;
                    case 1 -> state == SessionState.LOBBY;
                    case 2 -> state == SessionState.RUNNING;
                    case 3 -> state == SessionState.LOBBY || state == SessionState.RUNNING;
                    case 4 -> state == SessionState.COMPLETED || state == SessionState.FAILED;
                    default -> state == SessionState.RESETTING;
                };
                Runnable action = switch (operation) {
                    case 0 -> machine::openLobby;
                    case 1 -> machine::start;
                    case 2 -> machine::complete;
                    case 3 -> machine::fail;
                    case 4 -> machine::beginReset;
                    default -> machine::finishReset;
                };
                if (!valid) {
                    assertThrows(IllegalStateException.class, action::run, state + "/" + operation);
                    assertEquals(state, machine.state());
                }
            }
        }
    }

    private SessionStateMachine at(SessionState state) {
        var machine = new SessionStateMachine();
        if (state == SessionState.FREE) return machine;
        machine.openLobby();
        if (state == SessionState.LOBBY) return machine;
        machine.start();
        if (state == SessionState.RUNNING) return machine;
        if (state == SessionState.FAILED) machine.fail();
        else machine.complete();
        if (state == SessionState.RESETTING) machine.beginReset();
        return machine;
    }
}
