package dev.dasan.customdungeons.model;

import java.util.List;
import org.jspecify.annotations.Nullable;

public record RoomDef(String id, Region region, Point checkpoint, @Nullable Region door,
                      UnlockMode unlock, @Nullable String keyCarrierTemplateId, List<SpawnerDef> spawners) {
    public RoomDef {
        spawners = List.copyOf(spawners);
    }
}
