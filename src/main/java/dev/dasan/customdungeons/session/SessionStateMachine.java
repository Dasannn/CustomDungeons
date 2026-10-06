package dev.dasan.customdungeons.session;

/** Pure lifecycle; cancellation of a lobby follows the FAILED/reset path. */
public final class SessionStateMachine {
    private SessionState state = SessionState.FREE;

    public SessionState state() { return state; }
    public void openLobby() { transition(SessionState.FREE, SessionState.LOBBY); }
    public void start() { transition(SessionState.LOBBY, SessionState.RUNNING); }
    public void complete() { transition(SessionState.RUNNING, SessionState.COMPLETED); }
    public void fail() {
        require(SessionState.LOBBY, SessionState.RUNNING);
        state = SessionState.FAILED;
    }
    public void beginReset() {
        require(SessionState.COMPLETED, SessionState.FAILED);
        state = SessionState.RESETTING;
    }
    public void finishReset() { transition(SessionState.RESETTING, SessionState.FREE); }

    private void transition(SessionState from, SessionState to) {
        require(from);
        state = to;
    }
    private void require(SessionState... allowed) {
        for (SessionState candidate : allowed) if (state == candidate) return;
        throw new IllegalStateException("Invalid transition from " + state);
    }
}
