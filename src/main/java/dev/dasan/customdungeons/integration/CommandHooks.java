package dev.dasan.customdungeons.integration;

import dev.dasan.customdungeons.model.HookEvent;
import dev.dasan.customdungeons.session.*;
import java.util.*;
import java.util.function.Consumer;

/** Runs configured console commands at lifecycle boundaries, without a ticker. */
public final class CommandHooks implements SessionLifecycleListener {
    private final Consumer<String> dispatch;
    public CommandHooks(Consumer<String> dispatch) { this.dispatch = dispatch; }
    public static String render(String template, String dungeonId, int players, int max) {
        return template.replace("{dungeon}",dungeonId).replace("{players}",Integer.toString(players))
                .replace("{max}",max == 0 ? "∞" : Integer.toString(max));
    }
    @Override public void onStateChange(DungeonSession s, SessionState from, SessionState to) {
        HookEvent event = switch (to) {
            case LOBBY -> from == SessionState.FREE ? HookEvent.LOBBY_OPEN : null;
            case RUNNING -> HookEvent.START;
            case COMPLETED -> HookEvent.COMPLETE;
            case FAILED -> HookEvent.FAIL;
            case FREE -> HookEvent.FREE;
            default -> null;
        };
        if (event != null) fire(s,event,s.survivors().size());
    }
    @Override public void onLobbyFull(DungeonSession s) { fire(s,HookEvent.FULL,s.survivors().size()); }
    private void fire(DungeonSession s, HookEvent event, int players) {
        for (String command : s.def().hooks().getOrDefault(event,List.of()))
            dispatch.accept(render(command,s.def().id(),players,s.def().maxPlayers()));
    }
}
