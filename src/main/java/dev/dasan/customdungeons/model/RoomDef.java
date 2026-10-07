package dev.dasan.customdungeons.model;

import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

public record RoomDef(String id, Region region, Point checkpoint, @Nullable Region door,
                      UnlockMode unlock, @Nullable String keyCarrierTemplateId, List<SpawnerDef> spawners,
                      OpeningMode openingMode, @Nullable RoomAmbience ambience) {
    /** Additive room setting: the original T01 unlock enum and constructor remain available. */
    public enum OpeningMode {
        AUTOMATIC, KEY, EXTERNAL_KEY;
        public OpeningMode next() { return values()[(ordinal()+1)%values().length]; }
    }
    public RoomDef(String id, Region region, Point checkpoint, @Nullable Region door,
                   UnlockMode unlock, @Nullable String keyCarrierTemplateId, List<SpawnerDef> spawners) {
        this(id,region,checkpoint,door,unlock,keyCarrierTemplateId,spawners,
                unlock==UnlockMode.KEY ? OpeningMode.KEY : OpeningMode.AUTOMATIC);
    }
    public RoomDef(String id, Region region, Point checkpoint, @Nullable Region door,
                   UnlockMode unlock, @Nullable String keyCarrierTemplateId, List<SpawnerDef> spawners, OpeningMode openingMode) {
        this(id,region,checkpoint,door,unlock,keyCarrierTemplateId,spawners,openingMode,null);
    }
    public RoomDef withAmbience(@Nullable RoomAmbience value) {
        return new RoomDef(id,region,checkpoint,door,unlock,keyCarrierTemplateId,spawners,openingMode,value);
    }
    public RoomDef {
        Objects.requireNonNull(openingMode);
        // Legacy consumers still see a locked door for both key modes.
        unlock = openingMode==OpeningMode.AUTOMATIC ? UnlockMode.AUTOMATIC : UnlockMode.KEY;
        spawners = List.copyOf(spawners);
    }
}
