package dev.dasan.customdungeons.config;

import dev.dasan.customdungeons.model.*;
import dev.dasan.customdungeons.gui.wizard.WizardRules;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.junit.jupiter.api.Assertions.*;

class OptionalCheckpointValidationTest {
    final DefinitionCodec codec=new DefinitionCodec();
    final Validator validator=new Validator();
    final Map<String,MobTemplate> mobs=Map.of("zombie",DefinitionCodecTest.mob());
    DungeonDef definition(int lives,boolean teleport,boolean firstPoint) throws Exception {
        var encoded=new LinkedHashMap<>(codec.encode(DefinitionCodecTest.dungeon()));encoded.put("lives",lives);encoded.put("teleport-on-start",teleport);
        var rooms=new ArrayList<Map<String,Object>>();
        for(var value:(List<?>)encoded.get("rooms")) {
            var room=new LinkedHashMap<>((Map<String,Object>)value);
            if(!rooms.isEmpty() || !firstPoint)room.remove("checkpoint");rooms.add(room);
        }
        encoded.put("rooms",rooms);return codec.decodeDungeon("ejemplo",DefinitionCodecTest.yaml(encoded));
    }
    @ParameterizedTest @CsvSource({"1,false,false,0","1,true,false,1","1,true,true,0","2,false,false,2","2,true,true,1"})
    void validationExplainsEachRequiredCheckpoint(int lives,boolean teleport,boolean firstPoint,int expected) throws Exception {
        var errors=validator.validate(definition(lives,teleport,firstPoint),mobs);
        assertEquals(expected,errors.size(),errors::toString);
        for(var error:errors) {
            assertTrue(error.path().matches("rooms\\[[01]\\]\\.checkpoint"));
            assertEquals(lives==1?"validation.checkpoint-start":"validation.checkpoint-lives",error.messageKey());
        }
    }
    @Test void wizardRoomStepAndReviewAcceptMissingOptionalPointsAndRevalidateRules() throws Exception {
        var optional=definition(1,false,false);
        var allowed=WizardRules.evaluate(optional,mobs,Map.of(),false);
        assertTrue(allowed.errors(3).isEmpty());
        assertTrue(allowed.errors(6).stream().noneMatch(e->e.path().endsWith(".checkpoint")));
        var raised=WizardRules.evaluate(definition(2,false,false),mobs,Map.of(),false);
        assertEquals(2,raised.errors(3).stream().filter(e->e.path().endsWith(".checkpoint")).count());
        assertEquals(2,raised.errors(6).stream().filter(e->e.path().endsWith(".checkpoint")).count());
    }
    @Test void raisingLivesReportsMissingPointAndPreservesExistingPoints() throws Exception {
        var single=definition(1,true,true);assertTrue(validator.validate(single,mobs).isEmpty());
        var encoded=new LinkedHashMap<>(codec.encode(single));encoded.put("lives",2);
        var raised=codec.decodeDungeon(single.id(),DefinitionCodecTest.yaml(encoded));var errors=validator.validate(raised,mobs);
        assertEquals(List.of(new ValidationError("rooms[1].checkpoint","validation.checkpoint-lives",Map.of())),errors);
        assertEquals(single.rooms().getFirst().checkpoint(),raised.rooms().getFirst().checkpoint());
    }
    @Test void catalogUpgradeFrom35KeepsCustomTextsAndAddsBothCheckpointReasons() throws Exception {
        for(String stem:List.of("messages","messages_en")) {
            var old=new org.bukkit.configuration.file.YamlConfiguration();var current=new org.bukkit.configuration.file.YamlConfiguration();
            try(var history=getClass().getResourceAsStream("/defaults-history/"+stem+"-v35.yml");var defaults=getClass().getResourceAsStream("/"+stem+".yml")) {
                old.load(new java.io.InputStreamReader(history,java.nio.charset.StandardCharsets.UTF_8));
                current.load(new java.io.InputStreamReader(defaults,java.nio.charset.StandardCharsets.UTF_8));
            }
            var previous=new org.bukkit.configuration.file.YamlConfiguration();previous.loadFromString(old.saveToString());
            old.set("gui.dungeon.section-checkpoint-lore","Custom checkpoint lore");
            ConfigMigration.merge(old,current,List.of(previous),true);
            assertEquals(36,old.getInt("version"));assertEquals("Custom checkpoint lore",old.getString("gui.dungeon.section-checkpoint-lore"));
            for(String key:List.of("validation.checkpoint-lives","validation.checkpoint-start","gui.dungeon.checkpoint-optional","gui.dungeon.checkpoint-review"))
                assertEquals(current.getString(key),old.getString(key));
        }
    }
    @Test void optionalAndLegacyYamlRoundTripWithoutInventingPoints() throws Exception {
        for(var def:List.of(definition(1,false,false),definition(1,true,true),DefinitionCodecTest.dungeon())) {
            var encoded=codec.encode(def);var copy=codec.decodeDungeon(def.id(),DefinitionCodecTest.yaml(encoded));
            assertEquals(def,copy);assertEquals(encoded,codec.encode(copy));
        }
        assertNull(definition(1,false,false).rooms().getFirst().checkpoint());
    }
}
