package dev.dasan.customdungeons.config;
import dev.dasan.customdungeons.model.*;
import java.util.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class WizardAreaTest {
    @Test void optionalAreaRoundTripsAndRejectsAllGeometryOutsideIncludingOtherWorlds() {
        var codec=new DefinitionCodec(); var y=new YamlConfiguration();
        codec.encode(DefinitionCodecTest.dungeon()).forEach(y::set);
        y.set("area",Map.of("world","elsewhere","min",Map.of("x",0,"y",0,"z",0),"max",Map.of("x",1,"y",1,"z",1)));
        var definition=codec.decodeDungeon("area",y);
        assertNotNull(codec.encode(definition).get("area"));
        var paths=new Validator().validate(definition,Map.of("zombie",DefinitionCodecTest.mob())).stream()
                .filter(e->e.messageKey().equals("validation.outside-area")).map(ValidationError::path).toList();
        assertTrue(paths.containsAll(List.of("lobby","rooms[0].region","rooms[0].checkpoint","rooms[0].door","rooms[0].spawners[0].location")),paths::toString);
    }
    @Test void legacyDungeonStillPassesWithoutAnArea() {
        assertTrue(new Validator().validate(DefinitionCodecTest.dungeon(),Map.of("zombie",DefinitionCodecTest.mob())).isEmpty());
    }
    @Test void areaRoundTripCopiesAndBlockBoundaryCoordinatesArePreserved() throws Exception {
        var codec=new DefinitionCodec();var y=new YamlConfiguration();codec.encode(DefinitionCodecTest.dungeon()).forEach(y::set);
        y.set("area",Map.of("world","dungeons","min",Map.of("x",0,"y",60,"z",0),"max",Map.of("x",10,"y",70,"z",10)));
        var d=codec.decodeDungeon("ejemplo",y);
        assertEquals(d,codec.decodeDungeon("ejemplo",DefinitionCodecTest.yaml(codec.encode(d))));
        assertEquals(d.area(),SpawnerPresets.resolve(d,Map.of()).area());
        assertEquals(d.area(),SpawnerPresets.withLibrary(d,List.of()).area());
        assertEquals(d.area(),SpawnerPresets.detach(d,"unused",Map.of()).area());
        y=DefinitionCodecTest.yaml(codec.encode(d));
        for(double x:new double[]{10.9999,11,-.001,1e100}) {
            y.set("lobby.x",x);var errors=new Validator().validate(codec.decodeDungeon("ejemplo",y),Map.of("zombie",DefinitionCodecTest.mob()));
            assertEquals(x!=10.9999,errors.stream().anyMatch(e->e.path().equals("lobby")&&e.messageKey().equals("validation.outside-area")),Double.toString(x));
        }
        y.set("lobby.x",Double.POSITIVE_INFINITY);var invalid=y;
        assertThrows(IllegalArgumentException.class,()->codec.decodeDungeon("ejemplo",invalid));
    }

}
