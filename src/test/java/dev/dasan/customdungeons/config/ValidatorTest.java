package dev.dasan.customdungeons.config;
import dev.dasan.customdungeons.model.*;
import java.util.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.EquipmentSlot;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ValidatorTest {
    static final class TestItem extends org.bukkit.inventory.ItemStack {
        TestItem() { super(); }
        @Override public org.bukkit.inventory.ItemStack clone() { return new TestItem(); }
    }
    final Validator validator = new Validator();
    List<ValidationError> dungeon(String key,Object value) {
        var y = new YamlConfiguration(); new DefinitionCodec().encode(DefinitionCodecTest.dungeon()).forEach(y::set); y.set(key,value);
        return validator.validate(new DefinitionCodec().decodeDungeon("ejemplo",y),Map.of("zombie",DefinitionCodecTest.mob()));
    }
    void has(List<ValidationError> errors,String key) { assertTrue(errors.stream().anyMatch(e->e.messageKey().equals("validation."+key)),errors::toString); }
    @Test void validDefinitionsPass() {
        assertTrue(validator.validate(DefinitionCodecTest.dungeon(),Map.of("zombie",DefinitionCodecTest.mob())).isEmpty());
        assertTrue(validator.validate(DefinitionCodecTest.mob(),new ConfigLoader(path->{},material->material == org.bukkit.Material.IRON_BLOCK).load(new YamlConfiguration()),Set.of("test")).isEmpty());
    }
    @Test void invalidIdIsReported() {
        var y = new YamlConfiguration(); new DefinitionCodec().encode(DefinitionCodecTest.dungeon()).forEach(y::set);
        has(validator.validate(new DefinitionCodec().decodeDungeon("../bad",y),Map.of()),"id");
    }
    @Test void minimumPlayersMustBePositive() { has(dungeon("min-players",0),"min-players"); }
    @Test void maximumPlayersMustCoverMinimum() {
        has(dungeon("max-players",-1),"max-players");
        has(dungeon("min-players",5),"max-players"); // The fixture's maximum is 4.
        assertTrue(dungeon("max-players",0).isEmpty());
    }
    @Test void livesMustBePositive() { has(dungeon("lives",0),"lives"); }
    @Test void lobbyAndExitAreRequired() { has(dungeon("lobby",null),"required"); has(dungeon("exit",null),"required"); }
    @Test void atLeastOneRoomIsRequired() { has(dungeon("rooms",List.of()),"non-empty"); }
    @Test void regionCheckpointSpawnerWaveAndEntryAreRequired() {
        has(room(Map.of("id","r")),"required"); has(room(Map.of("id","r")),"non-empty");
        var missingPoints = room(Map.of("id","r"));
        assertTrue(missingPoints.stream().anyMatch(e->e.path().equals("rooms[0].region") && e.messageKey().equals("validation.required")));
        assertTrue(missingPoints.stream().anyMatch(e->e.path().equals("rooms[0].checkpoint") && e.messageKey().equals("validation.required")));
        has(room(Map.of("spawners",List.of(Map.of("waves",List.of())))),"non-empty");
        has(room(Map.of("spawners",List.of(Map.of("waves",List.of(Map.of("entries",List.of())))))) ,"non-empty");
    }
    @Test void countMustBePositive() { has(entry(new WaveEntry("zombie",0,0)),"count"); }
    @Test void missingTemplateIsReported() { has(entry(new WaveEntry("missing",1,0)),"template"); }
    @Test void keyRoomWithoutCarrierIsReported() { has(room(Map.of("unlock","KEY","key-carrier-template-id","missing")),"key-carrier"); }
    @Test void keyRoomRequiresDoor() {
        // A single room is also the final room: only KEY makes its door mandatory.
        var errors = dungeon("rooms",List.of(Map.of("unlock","KEY")));
        assertTrue(errors.stream().anyMatch(e->e.path().equals("rooms[0].door") && e.messageKey().equals("validation.door")));
    }
    @Test void nonFinalRoomRequiresDoor() { has(room(Map.of("unlock","AUTOMATIC")),"door"); }
    @Test void armorOnWardenIsReported() {
        // Armor slots are validated even on an incomplete hand-written definition, without a Bukkit server.
        var m = DefinitionCodecTest.mob();
        var w = new MobTemplate(m.id(),"minecraft:warden",m.displayName(),0,0,0,0,0,
                Map.of(EquipmentSlot.HEAD,new EquipmentDef(new TestItem(),0)),List.of(),List.of(),List.of(),false,"RED",null,List.of(),false);
        has(validator.validate(w,new ConfigLoader(path->{},material->material == org.bukkit.Material.IRON_BLOCK).load(new YamlConfiguration()),Set.of()),"armor");
    }
    @Test void abilityMustBeRegisteredIncludingComboAndPhase() { has(mob("abilities",List.of(Map.of("ability-id","missing"))),"ability"); }
    @Test void comboAndPhaseAbilitiesMustBeRegistered() {
        has(mob("combos",List.of(Map.of("id","c","steps",List.of(Map.of("ability-id","missing"),Map.of("ability-id","test"))))),"ability");
        has(mob("phases",List.of(Map.of("health-threshold",.5,"abilities",List.of(Map.of("ability-id","missing"))))),"ability");
        has(mob("phases",List.of(Map.of("health-threshold",.5,"combos",List.of(Map.of("id","c","steps",List.of()))))),"combo-size");
    }
    @Test void phaseThresholdsMustDecrease() {
        has(mob("phases",List.of(Map.of("health-threshold",.3),Map.of("health-threshold",.6))),"phase-threshold");
        has(mob("phases",List.of(Map.of("health-threshold",1))),"phase-threshold");
        has(mob("phases",List.of(Map.of("health-threshold",0))),"phase-threshold");
    }
    @Test void combosRequireTwoToFiveSteps() {
        has(mob("combos",List.of(Map.of("id","c","steps",List.of()))),"combo-size");
        has(mob("combos",List.of(Map.of("id","c","steps",Collections.nCopies(6,Map.of("ability-id","test"))))),"combo-size");
    }
    List<ValidationError> mob(String key,Object value) {
        var y = new YamlConfiguration(); new DefinitionCodec().encode(DefinitionCodecTest.mob()).forEach(y::set); y.set(key,value);
        return validator.validate(new DefinitionCodec().decodeMob("zombie",y),new ConfigLoader(path->{},material->material == org.bukkit.Material.IRON_BLOCK).load(new YamlConfiguration()),Set.of("test"));
    }
    List<ValidationError> room(Map<String,Object> room) { return dungeon("rooms",List.of(room,Map.of("id","last"))); }
    List<ValidationError> entry(WaveEntry entry) {
        return room(Map.of("spawners",List.of(Map.of("waves",List.of(Map.of("entries",List.of(Map.of("template-id",entry.templateId(),"count",entry.count()))))))));
    }
}
