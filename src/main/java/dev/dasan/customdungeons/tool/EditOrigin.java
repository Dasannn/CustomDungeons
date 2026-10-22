package dev.dasan.customdungeons.tool;

import dev.dasan.customdungeons.model.*;
import java.util.*;

/** Stable authoring identity. Selecting a zone never changes the mandatory room index. */
public record EditOrigin(Kind kind,String id) {
    public enum Kind { ROOM, ZONE }
    public EditOrigin {Objects.requireNonNull(kind);Objects.requireNonNull(id);}
    public static EditOrigin room(DungeonDef d,int index) {return new EditOrigin(Kind.ROOM,d.rooms().get(index).id());}
    public static EditOrigin zone(String id) {return new EditOrigin(Kind.ZONE,id);}
    public boolean exists(DungeonDef d) {return kind==Kind.ROOM?d.rooms().stream().anyMatch(r->r.id().equals(id)):d.optionalZones().stream().anyMatch(z->z.id().equals(id));}
    public Region region(DungeonDef d) {return kind==Kind.ROOM?d.rooms().get(index(d)).region():d.optionalZones().get(index(d)).region();}
    public List<SpawnerDef> spawners(DungeonDef d) {return kind==Kind.ROOM?d.rooms().get(index(d)).spawners():d.optionalZones().get(index(d)).spawners();}
    public int index(DungeonDef d) {
        var ids=kind==Kind.ROOM?d.rooms().stream().map(RoomDef::id).toList():d.optionalZones().stream().map(OptionalZoneDef::id).toList();
        int index=ids.indexOf(id);if(index<0) throw new IllegalStateException("Missing edit origin: "+this);return index;
    }
}
