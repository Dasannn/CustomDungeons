package dev.dasan.customdungeons.model;

import java.util.List;
import java.util.Map;
import java.util.EnumMap;

public record DungeonDef(String id, String displayName, boolean enabled, Point lobby, Point exit,
                         int minPlayers, int maxPlayers, int lobbyCountdownSeconds,
                         int lives, boolean keepInventory, int timeLimitSeconds,
                         int cooldownSeconds, boolean requirePermission, ScalingDef scaling,
                         Map<HookEvent, List<String>> hooks, RewardDef reward, List<RoomDef> rooms, List<String> spawnerPresets, Region area,
                         StartMode startMode, List<Point> plates, int plateCountdownSeconds, Region entranceDoor,
                         boolean teleportOnStart, boolean introCinematic, int introSeconds,
                         FinishMode finishMode, int exitGraceSeconds, FinishDestination finishDestination, List<Point> exitPlates) {
    /** Source compatibility with initial T38 and older callers. */
    public DungeonDef(String id, String displayName, boolean enabled, Point lobby, Point exit,
                      int minPlayers, int maxPlayers, int lobbyCountdownSeconds, int lives, boolean keepInventory,
                      int timeLimitSeconds, int cooldownSeconds, boolean requirePermission, ScalingDef scaling,
                      Map<HookEvent,List<String>> hooks, RewardDef reward, List<RoomDef> rooms, List<String> spawnerPresets,
                      Region area, StartMode startMode, List<Point> plates, int plateCountdownSeconds, Region entranceDoor,
                      boolean teleportOnStart, boolean teleportOnFinish, boolean introCinematic, int introSeconds) {
        this(id,displayName,enabled,lobby,exit,minPlayers,maxPlayers,lobbyCountdownSeconds,lives,keepInventory,
                timeLimitSeconds,cooldownSeconds,requirePermission,scaling,hooks,reward,rooms,spawnerPresets,area,
                startMode,plates,plateCountdownSeconds,entranceDoor,teleportOnStart,introCinematic,introSeconds,
                teleportOnFinish?FinishMode.IMMEDIATE:FinishMode.NONE,60,FinishDestination.EXIT,List.of());
    }
    public boolean teleportOnFinish() { return finishMode != FinishMode.NONE; }
    public DungeonDef withFinish(FinishMode mode,int grace,FinishDestination destination,List<Point> points) {
        return new DungeonDef(id,displayName,enabled,lobby,exit,minPlayers,maxPlayers,lobbyCountdownSeconds,lives,
                keepInventory,timeLimitSeconds,cooldownSeconds,requirePermission,scaling,hooks,reward,rooms,
                spawnerPresets,area,startMode,plates,plateCountdownSeconds,entranceDoor,teleportOnStart,
                introCinematic,introSeconds,mode,grace,destination,points);
    }
    /** Existing source constructors retain legacy start teleport behavior. */
    public DungeonDef(String id, String displayName, boolean enabled, Point lobby, Point exit,
                      int minPlayers, int maxPlayers, int lobbyCountdownSeconds, int lives, boolean keepInventory,
                      int timeLimitSeconds, int cooldownSeconds, boolean requirePermission, ScalingDef scaling,
                      Map<HookEvent, List<String>> hooks, RewardDef reward, List<RoomDef> rooms,
                      List<String> spawnerPresets, Region area) {
        this(id, displayName, enabled, lobby, exit, minPlayers, maxPlayers, lobbyCountdownSeconds, lives,
                keepInventory, timeLimitSeconds, cooldownSeconds, requirePermission, scaling, hooks, reward, rooms,
                spawnerPresets, area, StartMode.AUTO, List.of(), 3, null, true, true, false, 10);
    }
    public DungeonDef withStart(StartMode mode, List<Point> points, int countdown, Region door,
                                boolean startTp, boolean finishTp, boolean cinematic, int seconds) {
        return new DungeonDef(id,displayName,enabled,lobby,exit,minPlayers,maxPlayers,lobbyCountdownSeconds,lives,
                keepInventory,timeLimitSeconds,cooldownSeconds,requirePermission,scaling,hooks,reward,rooms,
                spawnerPresets,area,mode,points,countdown,door,startTp,finishTp,cinematic,seconds);
    }
    /** T36 constructor retained for source compatibility; legacy definitions have no area. */
    public DungeonDef(String id, String displayName, boolean enabled, Point lobby, Point exit,
                      int minPlayers, int maxPlayers, int lobbyCountdownSeconds, int lives, boolean keepInventory,
                      int timeLimitSeconds, int cooldownSeconds, boolean requirePermission, ScalingDef scaling,
                      Map<HookEvent, List<String>> hooks, RewardDef reward, List<RoomDef> rooms, List<String> spawnerPresets) {
        this(id, displayName, enabled, lobby, exit, minPlayers, maxPlayers, lobbyCountdownSeconds, lives,
                keepInventory, timeLimitSeconds, cooldownSeconds, requirePermission, scaling, hooks, reward, rooms, spawnerPresets, null);
    }
    public DungeonDef(String id, String displayName, boolean enabled, Point lobby, Point exit,
                      int minPlayers, int maxPlayers, int lobbyCountdownSeconds, int lives, boolean keepInventory,
                      int timeLimitSeconds, int cooldownSeconds, boolean requirePermission, ScalingDef scaling,
                      Map<HookEvent, List<String>> hooks, RewardDef reward, List<RoomDef> rooms) {
        this(id, displayName, enabled, lobby, exit, minPlayers, maxPlayers, lobbyCountdownSeconds, lives,
                keepInventory, timeLimitSeconds, cooldownSeconds, requirePermission, scaling, hooks, reward, rooms, List.of());
    }
    public DungeonDef {
        Map<HookEvent, List<String>> snapshot = new EnumMap<>(HookEvent.class);
        hooks.forEach((key, value) -> snapshot.put(key, List.copyOf(value)));
        hooks = Map.copyOf(snapshot);
        rooms = List.copyOf(rooms);
        spawnerPresets = List.copyOf(spawnerPresets);
        plates = List.copyOf(plates);
        exitPlates = List.copyOf(exitPlates);
        java.util.Objects.requireNonNull(finishMode);
        java.util.Objects.requireNonNull(finishDestination);
        java.util.Objects.requireNonNull(startMode);
        if (startMode == StartMode.PLATES) minPlayers = plates.size();
    }
}
