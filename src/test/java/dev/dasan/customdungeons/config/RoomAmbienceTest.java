package dev.dasan.customdungeons.config;

import dev.dasan.customdungeons.model.*;
import java.util.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RoomAmbienceTest {
    @Test void sparseOverridesInheritAndResetWithoutSavingDefaults() {
        var defaults=AmbienceSettings.load(new YamlConfiguration(),path->{});
        var override=new RoomAmbience(Map.of("entry-title","Custom","effects",List.of(),"door-shake",false));
        var merged=defaults.resolve(override,true);
        assertEquals("Custom",merged.text("entry-title"));
        assertEquals("minecraft:block.iron_door.open",merged.text("door-sound"));
        assertEquals(List.of(),merged.effects());
        assertFalse(merged.flag("door-shake"));
        assertEquals(3,override.values().size());
        assertTrue(override.with("entry-title",null).values().size()==2);
        assertEquals("{room}",defaults.resolve(null,false).text("entry-title"));
        assertEquals(List.of(new PotionDef("minecraft:darkness",0,false)),defaults.resolve(null,true).effects());
    }
    @Test void ancientYamlAndSparseRoomRoundTrip() throws Exception {
        var codec=new DefinitionCodec();var old=DefinitionCodecTest.dungeon();
        assertNull(codec.decodeDungeon(old.id(),DefinitionCodecTest.yaml(codec.encode(old))).rooms().getFirst().ambience());
        var room=old.rooms().getFirst().withAmbience(new RoomAmbience(Map.of("music","custom:loop","density",3,"effects",List.of(new PotionDef("minecraft:speed",1,true)))));
        var modified=SpawnerPresets.withRooms(old,List.of(room,old.rooms().getLast()));
        var encoded=codec.encode(modified);
        assertEquals(modified,codec.decodeDungeon(old.id(),DefinitionCodecTest.yaml(encoded)));
        var fields=(Map<?,?>)((Map<?,?>)((List<?>)encoded.get("rooms")).getFirst()).get("ambience");
        assertEquals(Set.of("music","density","effects"),fields.keySet());
    }
    @Test void malformedAndOutOfRangeSettingsAreRejectedAndDefaultsFallBack() {
        assertThrows(IllegalArgumentException.class,()->new RoomAmbience(Map.of("density",Double.NaN)));
        assertThrows(IllegalArgumentException.class,()->new RoomAmbience(Map.of("door-shake","yes")));
        var invalid=new RoomAmbience(Map.of("density",1000));
        assertFalse(AmbienceSettings.errors(invalid).isEmpty());
        var yaml=new YamlConfiguration();yaml.set("ambience.defaults.density",1000);
        var warnings=new ArrayList<String>();var defaults=AmbienceSettings.load(yaml,warnings::add);
        assertEquals(4,defaults.resolve(null,false).number("density"));
        assertEquals(List.of("ambience.defaults.density"),warnings);
    }
    @Test void emptyParticleTokensCannotReachRuntimeAndBrokenDefaultsFallBack() {
        for(String particle:List.of(","," , ","CLOUD,","DUST"))
            assertFalse(AmbienceSettings.errors(new RoomAmbience(Map.of("particle",particle))).isEmpty(),particle);
        var yaml=new YamlConfiguration();yaml.set("ambience.defaults.particle",",");
        var warnings=new ArrayList<String>();var defaults=AmbienceSettings.load(yaml,warnings::add);
        assertEquals("",defaults.resolve(null,false).text("particle"));
        assertEquals(List.of("ambience.defaults.particle"),warnings);
    }
    @Test void bossDefaultDoesNotLeakIntoOrdinaryRoomsAndSpawnerResolutionPreservesOverrides() {
        var room=DefinitionCodecTest.dungeon().rooms().getFirst().withAmbience(new RoomAmbience(Map.of("entry-title","Test")));
        assertEquals(room.ambience(),SpawnerPresets.resolve(SpawnerPresets.withRooms(DefinitionCodecTest.dungeon(),List.of(room)),Map.of()).rooms().getFirst().ambience());
    }
}
