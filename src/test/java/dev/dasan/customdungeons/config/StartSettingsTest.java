package dev.dasan.customdungeons.config;

import dev.dasan.customdungeons.model.*;
import java.util.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class StartSettingsTest {
    final DefinitionCodec codec=new DefinitionCodec();
    DungeonDef base() {return codec.decodeDungeon("demo",new YamlConfiguration());}
    @Test void legacyYamlRetainsTeleportAndNewDefaultsRoundTrip() throws Exception {
        assertTrue(base().teleportOnStart()); assertTrue(base().teleportOnFinish());
        var point=new Point("world",1.5,64,1.5,0,0);
        var d=base().withStart(StartMode.PLATES,List.of(point),3,
                Region.of("world",new BlockPos(0,64,0),new BlockPos(0,65,0)),false,false,true,20);
        var y=new YamlConfiguration(); codec.encode(d).forEach(y::set);
        var loaded=new YamlConfiguration(); loaded.loadFromString(y.saveToString());
        assertEquals(d,codec.decodeDungeon("demo",loaded)); assertEquals(1,d.minPlayers());
        assertEquals(d,SpawnerPresets.resolve(d,Map.of()));
    }
    @Test void finishCompatibilityAndAllNewFieldsRoundTrip() {
        for(boolean teleport:List.of(false,true)) {
            var y=new YamlConfiguration();y.set("teleport-on-finish",teleport);
            assertEquals(teleport?FinishMode.IMMEDIATE:FinishMode.NONE,codec.decodeDungeon("demo",y).finishMode());
        }
        assertEquals(FinishMode.IMMEDIATE,base().finishMode());assertEquals(60,base().exitGraceSeconds());
        for(var mode:FinishMode.values()) {
            var d=base().withFinish(mode,45,FinishDestination.PREVIOUS,List.of(new Point("world",1,64,1,0,0)));
            var y=new YamlConfiguration();codec.encode(d).forEach(y::set);
            assertFalse(y.contains("teleport-on-finish"));assertEquals(d,codec.decodeDungeon("demo",y));
            assertEquals(d,SpawnerPresets.resolve(d,Map.of()));
        }
    }
    @Test void validatesExitPlatesAndGraceBounds() {
        var y=new YamlConfiguration();y.set("area.world","world");
        for(String corner:List.of("min","max"))for(String axis:List.of("x","y","z"))y.set("area."+corner+"."+axis,0);
        var d=codec.decodeDungeon("demo",y).withFinish(FinishMode.NONE,9,FinishDestination.EXIT,List.of(new Point("world",5,64,5,0,0)));
        var errors=new Validator().validate(d,Map.of());
        assertTrue(errors.stream().anyMatch(e->e.path().equals("exit-plates[0]") && e.messageKey().equals("validation.outside-area")));
        assertTrue(errors.stream().anyMatch(e->e.messageKey().equals("validation.exit-grace")));
    }
    @Test void platesRequireDoorAndAtLeastOnePlate() {
        var d=base().withStart(StartMode.PLATES,List.of(),3,null,false,true,false,10);
        var errors=new Validator().validate(d,Map.of());
        assertTrue(errors.stream().anyMatch(e->e.messageKey().equals("validation.plates-required")));
        assertTrue(errors.stream().anyMatch(e->e.path().equals("entrance-door")));
    }
    @Test void rejectsPlatesOutsideAreaDuplicatesAndInvalidDurations() {
        var y=new YamlConfiguration(); y.set("area.world","world");
        for(String corner:List.of("min","max")) {
            y.set("area."+corner+".x",0);y.set("area."+corner+".y",64);y.set("area."+corner+".z",0);
        }
        var p=new Point("world",2,64,0,0,0);
        var d=codec.decodeDungeon("demo",y).withStart(StartMode.PLATES,List.of(p,p),0,null,false,true,false,21);
        var errors=new Validator().validate(d,Map.of());
        for(String key:List.of("outside-area","duplicate-plate","plate-countdown","intro-seconds"))
            assertTrue(errors.stream().anyMatch(e->e.messageKey().equals("validation."+key)),key);
    }
    @Test void exitDestinationCanBeOutsideAreaToReleaseEvacuationLock() {
        var y=new YamlConfiguration();y.set("area.world","world");
        for(String corner:List.of("min","max"))for(String axis:List.of("x","y","z"))y.set("area."+corner+"."+axis,0);
        y.set("exit.world","world");y.set("exit.x",100);y.set("exit.y",64);y.set("exit.z",100);
        assertTrue(new Validator().validate(codec.decodeDungeon("demo",y),Map.of()).stream().noneMatch(e->e.path().equals("exit")));
    }
    @Test void exitInsideAreaRoomDoorOrEntranceIsAlwaysAnError() {
        var exit=new Point("world",1.9,64.5,1.9,0,0);
        var region=Region.of("world",new BlockPos(1,64,1),new BlockPos(1,64,1));
        for(String kind:List.of("area","room","door","entrance")) {
            var room=new RoomDef("room",kind.equals("room")?region:null,null,kind.equals("door")?region:null,UnlockMode.AUTOMATIC,null,List.of());
            var d=new DungeonDef("demo","demo",true,exit,exit,1,0,3,3,false,0,0,false,
                new ScalingDef(0,0),Map.of(),new RewardDef(List.of(),0,0,List.of()),List.of(room),List.of(),kind.equals("area")?region:null)
                .withStart(StartMode.AUTO,List.of(),3,kind.equals("entrance")?region:null,false,true,false,10);
            assertTrue(new Validator().validate(d,Map.of(),Map.of(),false).stream()
                .anyMatch(e->e.path().equals("exit") && e.messageKey().equals("validation.exit-inside")),kind);
        }
    }
    @Test void versionTwelveCatalogsMigrateToThirteenAndRetainCustomTexts() {
        for(String stem:List.of("messages","messages_en")) {
            var old=YamlConfiguration.loadConfiguration(java.nio.file.Path.of("src/main/resources/defaults-history/"+stem+"-v12.yml").toFile());
            var current=YamlConfiguration.loadConfiguration(java.nio.file.Path.of("src/main/resources/"+stem+".yml").toFile());
            old.set("gui.dungeon.name","custom");
            var history=YamlConfiguration.loadConfiguration(java.nio.file.Path.of("src/main/resources/defaults-history/"+stem+"-v12.yml").toFile());
            var migrated=ConfigMigration.merge(old,current,List.of(history),true);
            assertTrue(migrated.added()>0);assertEquals(13,old.getInt("version"));
            assertEquals("custom",old.getString("gui.dungeon.name"));assertTrue(old.isString("gui.dungeon.start-settings"));
        }
    }

}
