package dev.dasan.customdungeons.tool.construction;

import dev.dasan.customdungeons.config.DefinitionCodec;
import dev.dasan.customdungeons.model.*;
import dev.dasan.customdungeons.tool.EditOrigin;
import java.util.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ZoneBuildStateTest {
    static DungeonDef definition() {
        var yaml=new YamlConfiguration();
        yaml.set("rooms",List.of(Map.of("id","first"),Map.of("id","second")));
        yaml.set("optional-zones",List.of(Map.of("id","side")));
        return new DefinitionCodec().decodeDungeon("test",yaml);
    }
    @Test void selectingZoneKeepsMandatoryRoomAndResumesTypedContext() {
        var state=new BuildState(definition());state.room(1);state.origin(EditOrigin.zone("side"));
        var resumed=new BuildState(state.snapshot());
        assertEquals(1,resumed.room());assertEquals(EditOrigin.zone("side"),resumed.origin());
        resumed.cyclePoint();assertEquals(EditOrigin.zone("side"),resumed.origin());
        resumed.room(0);assertEquals(EditOrigin.room(definition(),0),resumed.origin());
    }
    @Test void deletedZoneFallsBackToSelectedRoomAndUndoRestoresDefinition() {
        var original=definition();var state=new BuildState(original);state.room(1);state.origin(EditOrigin.zone("side"));
        var yaml=new DefinitionCodec().encode(original);yaml.remove("optional-zones");
        var source=new YamlConfiguration();source.addDefaults(yaml);source.options().copyDefaults(true);
        state.change(new DefinitionCodec().decodeDungeon("test",source));
        assertEquals(EditOrigin.room(original,1),state.origin());
        assertTrue(state.undo());assertEquals(original,state.definition());assertEquals(1,state.room());
    }
    @Test void legacySavedConstructorStillSelectsMandatoryRoom() {
        var original=definition();var state=new BuildState(new BuildState.Saved(original,original,1,0,List.of()));
        assertEquals(EditOrigin.room(original,1),state.origin());
    }
    @Test void originIdentityResolvesWithinItsOwnSpawnerScope() {
        var d=definition();assertTrue(EditOrigin.zone("side").exists(d));
        assertFalse(new EditOrigin(EditOrigin.Kind.ROOM,"side").exists(d));
        assertEquals(0,EditOrigin.zone("side").index(d));assertTrue(EditOrigin.zone("side").spawners(d).isEmpty());
    }
}
