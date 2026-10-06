package dev.dasan.customdungeons.model;

import java.util.List;
import java.util.Map;
import java.util.EnumMap;

public record DungeonDef(String id, String displayName, boolean enabled, Point lobby, Point exit,
                         int minPlayers, int maxPlayers, int lobbyCountdownSeconds,
                         int lives, boolean keepInventory, int timeLimitSeconds,
                         int cooldownSeconds, boolean requirePermission, ScalingDef scaling,
                         Map<HookEvent, List<String>> hooks, RewardDef reward, List<RoomDef> rooms) {
    public DungeonDef {
        Map<HookEvent, List<String>> snapshot = new EnumMap<>(HookEvent.class);
        hooks.forEach((key, value) -> snapshot.put(key, List.copyOf(value)));
        hooks = Map.copyOf(snapshot);
        rooms = List.copyOf(rooms);
    }
}
