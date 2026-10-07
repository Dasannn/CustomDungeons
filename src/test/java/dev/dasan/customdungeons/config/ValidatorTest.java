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
        @Override public boolean hasItemMeta() { return false; }
        @Override public org.bukkit.inventory.ItemStack clone() { return new TestItem(); }
    }
    final Validator validator = new Validator();
    List<ValidationError> dungeon(String key,Object value) {
        var y = new YamlConfiguration(); new DefinitionCodec().encode(DefinitionCodecTest.dungeon()).forEach(y::set); y.set(key,value);
        return validator.validate(new DefinitionCodec().decodeDungeon("ejemplo",y),Map.of("zombie",DefinitionCodecTest.mob()));
    }
    void has(List<ValidationError> errors,String key) { assertTrue(errors.stream().anyMatch(e->e.messageKey().equals("validation."+key)),errors::toString); }
    @Test void tileStatesInDoorAreRejectedWithGuiMessage() {
        var world=org.mockito.Mockito.mock(org.bukkit.World.class);
        var block=org.mockito.Mockito.mock(org.bukkit.block.Block.class);
        var tile=org.mockito.Mockito.mock(org.bukkit.block.TileState.class);
        org.mockito.Mockito.when(world.getBlockAt(org.mockito.ArgumentMatchers.anyInt(),org.mockito.ArgumentMatchers.anyInt(),org.mockito.ArgumentMatchers.anyInt())).thenReturn(block);
        org.mockito.Mockito.when(block.getState()).thenReturn(tile);
        var server=org.mockito.Mockito.mock(org.bukkit.Server.class);
        try (var bukkit=org.mockito.Mockito.mockStatic(org.bukkit.Bukkit.class)) {
            bukkit.when(org.bukkit.Bukkit::getServer).thenReturn(server);
            bukkit.when(org.bukkit.Bukkit::isPrimaryThread).thenReturn(true);
            bukkit.when(()->org.bukkit.Bukkit.getWorld("dungeons")).thenReturn(world);
            has(validator.validate(DefinitionCodecTest.dungeon(),Map.of("zombie",DefinitionCodecTest.mob())),"door-tile-state");
        }
    }
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
    @Test void livesHaveTheSameOneToHundredBoundaryAsTheEditor() {
        assertTrue(dungeon("lives",1).isEmpty());assertTrue(dungeon("lives",100).isEmpty());
        has(dungeon("lives",101),"lives");has(dungeon("lives",100000),"lives");
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
    @Test void wildcardCarrierDoesNotHideInvalidWaveTemplates() {
        var errors=room(Map.of("unlock","KEY","key-carrier-template-id","*","spawners",List.of(Map.of("waves",
                List.of(Map.of("entries",List.of(Map.of("template-id","missing","count",1))))))));
        has(errors,"template");
        assertTrue(errors.stream().noneMatch(e->e.messageKey().equals("validation.key-carrier")));
    }
    @Test void keyRoomWithoutCarrierIsReported() { has(room(Map.of("unlock","KEY","key-carrier-template-id","missing")),"key-carrier"); }
    @Test void keyRoomRequiresDoor() {
        // A non-final KEY room still requires a door.
        var errors = dungeon("rooms",List.of(Map.of("unlock","KEY"),Map.of("unlock","AUTOMATIC")));
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
    @Test void reservedEquipmentIsRejectedInBothHandsAndPhases() {
        var config=new ConfigLoader(path->{},material->material == org.bukkit.Material.IRON_BLOCK).load(new YamlConfiguration());
        for (var key : List.of(dev.dasan.customdungeons.mob.MobKeys.TOOL,dev.dasan.customdungeons.mob.MobKeys.KEY_ITEM)) {
            var item=org.mockito.Mockito.mock(org.bukkit.inventory.ItemStack.class);
            var meta=org.mockito.Mockito.mock(org.bukkit.inventory.meta.ItemMeta.class);
            var pdc=org.mockito.Mockito.mock(org.bukkit.persistence.PersistentDataContainer.class);
            org.mockito.Mockito.when(item.clone()).thenReturn(item);
            org.mockito.Mockito.when(item.hasItemMeta()).thenReturn(true);
            org.mockito.Mockito.when(item.getItemMeta()).thenReturn(meta);
            org.mockito.Mockito.when(meta.getPersistentDataContainer()).thenReturn(pdc);
            org.mockito.Mockito.when(pdc.has(key)).thenReturn(true);
            var equipment=Map.of(EquipmentSlot.HAND,new EquipmentDef(item,0),EquipmentSlot.OFF_HAND,new EquipmentDef(item,0));
            var phase=new PhaseDef(.5,false,List.of(),List.of(),equipment,List.of(),0,List.of(),null,null,null,null,0);
            var mob=new MobTemplate("warden","WARDEN","",0,0,0,0,0,equipment,List.of(),List.of(),List.of(),false,"RED",null,List.of(phase),false);
            var errors=validator.validate(mob,config,Set.of());
            assertEquals(4,errors.size());
            assertTrue(errors.stream().allMatch(e->e.messageKey().equals(key.equals(dev.dasan.customdungeons.mob.MobKeys.TOOL)
                    ? "validation.equipment-tool" : "validation.equipment-key")));
            assertTrue(errors.stream().anyMatch(e->e.path().equals("phases[0].equipment.OFF_HAND")));
        }
    }

    @Test void scaleAcceptsZeroSmallDecimalsAndFullZeroToTenRange() {
        for (double scale : new double[]{0,.01,.05,1,7.0625,10})
            assertTrue(statMob("scale",scale).isEmpty(),"scale="+scale);
        has(statMob("scale",10.01),"stat-range");
    }

    @Test void legacyAndNonFiniteStatsAreRejectedWithRanges() {
        for (var entry : Map.of("max-health",2049d,"damage",1001d,"speed",4.7265625,
                "knockback-resistance",1.1,"scale",10.01).entrySet()) {
            var errors=statMob(entry.getKey(),entry.getValue());
            var error=errors.stream().filter(e->e.path().equals(entry.getKey())).findFirst().orElseThrow();
            assertEquals("validation.stat-range",error.messageKey());
            assertTrue(error.args().containsKey("min"));
            assertTrue(error.args().containsKey("max"));
        }
        for (String stat : List.of("max-health","damage","speed","knockback-resistance","scale")) {
            has(statMob(stat,-1),"stat-range");
            has(statMob(stat,Double.NaN),"stat-range");
            has(statMob(stat,Double.POSITIVE_INFINITY),"stat-range");
            assertTrue(statMob(stat,0).isEmpty(),stat);
        }
        has(statMob("max-health",.5),"stat-range");
        assertTrue(statMob("scale",.05).isEmpty());
        for (var entry : Map.of("max-health",1024d,"damage",1000d,"speed",1d,
                "knockback-resistance",1d,"scale",10d).entrySet())
            assertTrue(statMob(entry.getKey(),entry.getValue()).isEmpty(),entry.getKey());
    }

    @Test void healthOverrideCannotExceedPaperLimit() {
        assertTrue(statMob("max-health",1024).isEmpty());
        for (double health : new double[]{1024.1,2048,4096}) {
            var error=statMob("max-health",health).stream().filter(e->e.path().equals("max-health")).findFirst().orElseThrow();
            assertEquals("1024.00",error.args().get("max"));
        }
    }
    @Test void finalKeyRoomWarnsWithoutRequiringDoorOrCarrier() throws Exception {
        var yaml=DefinitionCodecTest.yaml(new DefinitionCodec().encode(DefinitionCodecTest.dungeon()));
        var rooms=new ArrayList<>(yaml.getMapList("rooms"));
        var last=new HashMap<String,Object>();rooms.getLast().forEach((k,v)->last.put(k.toString(),v));last.put("unlock","KEY");last.put("key-carrier-template-id","missing");
        rooms.set(rooms.size()-1,last);yaml.set("rooms",rooms);
        var dungeon=new DefinitionCodec().decodeDungeon("ejemplo",yaml);
        assertTrue(validator.validate(dungeon,Map.of("zombie",DefinitionCodecTest.mob())).isEmpty());
        var warning=validator.warnings(dungeon,Map.of("zombie",DefinitionCodecTest.mob())).stream()
                .filter(w->w.messageKey().equals("validation.final-room-key")).findFirst().orElseThrow();
        assertEquals("rooms[1].unlock",warning.path());
        // The warning must also exist before the region has been configured.
        last.remove("region");yaml.set("rooms",rooms);
        assertTrue(validator.warnings(new DefinitionCodec().decodeDungeon("ejemplo",yaml),Map.of()).stream()
                .anyMatch(w->w.messageKey().equals("validation.final-room-key")));
    }

    @Test void tallWaveTemplatesWarnWithoutInvalidatingDungeon() {
        var dungeon=DefinitionCodecTest.dungeon();
        var tall=heightMob("minecraft:warden",4);
        assertTrue(validator.validate(dungeon,Map.of("zombie",tall)).isEmpty());
        var warnings=validator.warnings(dungeon,Map.of("zombie",tall));
        assertEquals(2,warnings.size());
        var warning=warnings.getFirst();
        assertEquals("validation.mob-height",warning.messageKey());
        assertEquals("rooms[0].spawners[0].waves[0].entries[0]",warning.path());
        assertEquals("&aColoso",warning.args().get("mob"));
        assertEquals("11.60",warning.args().get("height"));
        assertEquals("11.00",warning.args().get("room-height"));
    }
    @Test void heightWarningsRespectVanillaZeroAndInclusiveRegionHeight() {
        var dungeon=DefinitionCodecTest.dungeon();
        for(double scale:new double[]{0,1,3})
            assertTrue(validator.warnings(dungeon,Map.of("zombie",heightMob("WARDEN",scale))).isEmpty());
        // Inclusive region is 11 blocks tall; an exactly 11-block Ghast fits.
        assertTrue(validator.warnings(dungeon,Map.of("zombie",heightMob("GHAST",2.75))).isEmpty());
        assertFalse(validator.warnings(dungeon,Map.of("zombie",heightMob("GHAST",3))).isEmpty());
    }
    @Test void heightWarningsUseConfiguredHeightsAndSkipUnconfiguredTypes() {
        var dungeon=DefinitionCodecTest.dungeon();
        var mob=heightMob("WARDEN",10);
        assertTrue(validator.warnings(dungeon,Map.of("zombie",mob),new EntityHeights(Map.of(org.bukkit.entity.EntityType.WARDEN,.5))).isEmpty());
        var warnings=validator.warnings(dungeon,Map.of("zombie",mob),new EntityHeights(Map.of(org.bukkit.entity.EntityType.WARDEN,3d)));
        assertEquals(2,warnings.size()); assertEquals("30.00",warnings.getFirst().args().get("height"));
        assertTrue(validator.warnings(dungeon,Map.of("zombie",mob),new EntityHeights(Map.of())).isEmpty());
        assertFalse(validator.warnings(dungeon,Map.of("zombie",heightMob("SLIME",10)),new EntityHeights(Map.of(org.bukkit.entity.EntityType.SLIME,2d))).isEmpty());
    }
    @Test void zeroScaleUsesVanillaHeightRatherThanZeroForShortRooms() {
        var y=new YamlConfiguration(); new DefinitionCodec().encode(DefinitionCodecTest.dungeon()).forEach(y::set);
        var rooms=new ArrayList<>(y.getMapList("rooms"));
        var room=new HashMap<Object,Object>(rooms.getFirst());
        room.put("region",Map.of("world","dungeons","min",Map.of("x",0,"y",60,"z",0),"max",Map.of("x",10,"y",61,"z",10)));
        rooms.set(0,room); y.set("rooms",rooms);
        var warnings=validator.warnings(new DefinitionCodec().decodeDungeon("ejemplo",y),Map.of("zombie",heightMob("WARDEN",0)));
        assertEquals(1,warnings.size());
        assertEquals("2.90",warnings.getFirst().args().get("height"));
        assertEquals("2.00",warnings.getFirst().args().get("room-height"));
    }
    @Test void unknownUnusedMissingAndInvalidTemplatesDoNotProduceHeightWarnings() {
        var dungeon=DefinitionCodecTest.dungeon();
        for(String type:List.of("not_a_type","UNKNOWN","SLIME"))
            assertTrue(validator.warnings(dungeon,Map.of("zombie",heightMob(type,10))).isEmpty());
        for(double scale:new double[]{-1,Double.NaN,Double.POSITIVE_INFINITY})
            assertTrue(validator.warnings(dungeon,Map.of("zombie",heightMob("WARDEN",scale))).isEmpty());
        assertTrue(validator.warnings(dungeon,Map.of("unused",heightMob("WARDEN",10))).isEmpty());
        var y=new YamlConfiguration(); new DefinitionCodec().encode(dungeon).forEach(y::set);
        var rooms=new ArrayList<>(y.getMapList("rooms"));
        var room=new HashMap<Object,Object>(rooms.getFirst()); room.remove("region"); rooms.set(0,room); y.set("rooms",rooms);
        assertEquals(1,validator.warnings(new DefinitionCodec().decodeDungeon("ejemplo",y),Map.of("zombie",heightMob("WARDEN",10))).size());
    }
    private MobTemplate heightMob(String type,double scale) {
        return new MobTemplate("zombie",type,"&aColoso",0,0,0,0,scale,Map.of(),List.of(),List.of(),List.of(),false,"RED",null,List.of(),false);
    }

    List<ValidationError> statMob(String key,double value) {
        var stats=new java.util.HashMap<String,Double>(Map.of("max-health",0d,"damage",0d,"speed",0d,"knockback-resistance",0d,"scale",0d));
        stats.put(key,value);
        var template=new MobTemplate("warden","WARDEN","",stats.get("max-health"),stats.get("damage"),stats.get("speed"),
                stats.get("knockback-resistance"),stats.get("scale"),Map.of(),List.of(),List.of(),List.of(),false,"RED",null,List.of(),false);
        return validator.validate(template,new ConfigLoader(path->{},material->material == org.bukkit.Material.IRON_BLOCK).load(new YamlConfiguration()),Set.of());
    }
    List<ValidationError> mob(String key,Object value) {
        var y = new YamlConfiguration(); new DefinitionCodec().encode(DefinitionCodecTest.mob()).forEach(y::set); y.set(key,value);
        return validator.validate(new DefinitionCodec().decodeMob("zombie",y),new ConfigLoader(path->{},material->material == org.bukkit.Material.IRON_BLOCK).load(new YamlConfiguration()),Set.of("test"));
    }
    List<ValidationError> room(Map<String,Object> room) { return dungeon("rooms",List.of(room,Map.of("id","last"))); }
    List<ValidationError> entry(WaveEntry entry) {
        return room(Map.of("spawners",List.of(Map.of("waves",List.of(Map.of("entries",List.of(Map.of("template-id",entry.templateId(),"count",entry.count()))))))));
    }
    @Test void lifeDefaultsAndValidationUseTheDeclaredPluginLimits() {
        var yaml=new YamlConfiguration();var warnings=new ArrayList<String>();
        var loader=new ConfigLoader(warnings::add,m->m==org.bukkit.Material.IRON_BLOCK);
        yaml.set("dungeon-defaults.lives",DungeonLimits.MAX_LIVES);
        assertEquals(100,loader.load(yaml).defaults().lives());
        yaml.set("dungeon-defaults.lives",DungeonLimits.MAX_LIVES+1);
        assertEquals(3,loader.load(yaml).defaults().lives());assertTrue(warnings.contains("dungeon-defaults.lives"));
        var error=dungeon("lives",101).stream().filter(e->e.path().equals("lives")).findFirst().orElseThrow();
        assertEquals(Map.of("min","1","max","100"),error.args());
    }

}
