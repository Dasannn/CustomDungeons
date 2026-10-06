package dev.dasan.customdungeons.session;

import java.time.Instant;
import org.jspecify.annotations.Nullable;

public final class JoinRules {
    private JoinRules() {}

    public static JoinResult check(SessionState state, boolean enabled, boolean alreadyIn,
                                   int players, int maxPlayers, boolean bypassLimit,
                                   @Nullable Instant cooldownUntil, Instant now,
                                   boolean bypassCooldown, boolean hasPermission) {
        if (alreadyIn) return JoinResult.ALREADY_IN;
        if (!enabled) return JoinResult.DISABLED;
        if (!hasPermission) return JoinResult.NO_PERMISSION;
        if (state == SessionState.RUNNING || state == SessionState.COMPLETED || state == SessionState.FAILED)
            return JoinResult.RUNNING;
        if (state == SessionState.RESETTING) return JoinResult.RESETTING;
        if (!bypassCooldown && cooldownUntil != null && cooldownUntil.isAfter(now)) return JoinResult.COOLDOWN;
        if (!bypassLimit && maxPlayers > 0 && players >= maxPlayers) return JoinResult.FULL;
        return JoinResult.OK;
    }
}
