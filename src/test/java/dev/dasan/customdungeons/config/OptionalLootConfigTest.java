package dev.dasan.customdungeons.config;

import dev.dasan.customdungeons.model.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.nio.file.*;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.configuration.serialization.ConfigurationSerialization;
import org.bukkit.configuration.serialization.SerializableAs;
import org.bukkit.inventory.ItemStack;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class OptionalLootConfigTest {
    static final Point POINT=DefinitionCodecTest.POINT;
    static final Region REGION=DefinitionCodecTest.REGION;
    static final Map<String,MobTemplate> MOBS=Map.of("zombie",DefinitionCodecTest.mob());
    static OptionalZoneDef zone(String id) {
        return new OptionalZoneDef(id,REGION,DefinitionCodecTest.dungeon().rooms().getFirst().spawners());
    }
    static LootChestDef chest(String id,Point point) {
        return new LootChestDef(id,point,LootChestDef.Trigger.ROOM_CLEAR,List.of());
    }
    static DungeonDef chests(List<LootChestDef> chests) {
        var d=DefinitionCodecTest.dungeon();
        return SpawnerPresets.withRooms(d,List.of(d.rooms().getLast().withLootChests(chests)));
    }
    @Test void legacyEncodingOmitsNewEmptyLists() throws Exception {
        var codec=new DefinitionCodec();var d=DefinitionCodecTest.dungeon();
        assertFalse(codec.encode(d).containsKey("optional-zones"));
        assertTrue(((List<Map<String,Object>>)codec.encode(d).get("rooms")).stream().noneMatch(r->r.containsKey("loot-chests")));
        assertEquals(d,codec.decodeDungeon(d.id(),DefinitionCodecTest.yaml(codec.encode(d))));
    }
    @Test void zonesChestsAndProbabilitiesRoundTrip() throws Exception {
        var d=chests(List.of(chest("treasure",POINT))).withOptionalZones(List.of(zone("side")));
        var codec=new DefinitionCodec();
        assertEquals(d,codec.decodeDungeon(d.id(),DefinitionCodecTest.yaml(codec.encode(d))));
        assertEquals(d.optionalZones(),d.withDisconnectMode(DisconnectMode.RETURN_TO_EXIT).optionalZones());
        assertEquals(d.optionalZones(),d.withFinish(FinishMode.NONE,60,FinishDestination.EXIT,List.of()).optionalZones());
        assertEquals(d.rooms(),SpawnerPresets.withLibrary(d,List.of()).rooms());
    }
    @Test void chestLocationUsesBlockCoordinatesIncludingNegativeFractions() {
        assertEquals(new Point("dungeons",-1,64,2,0,0),chest("c",new Point("dungeons",-.1,64.9,2.5,90,30)).location());
    }
    @Test void immediateWarningDoesNotBlockValidationOrChangeMode() {
        var d=chests(List.of(chest("c",POINT)));
        assertTrue(new Validator().validate(d,MOBS).isEmpty());
        assertTrue(new Validator().warnings(d,MOBS).stream().anyMatch(w->w.messageKey().equals("validation.loot-immediate")));
        assertEquals(FinishMode.IMMEDIATE,d.finishMode());
    }
    @Test void zoneIdsShareRoomNamespaceAndSpawnerIdsAreLocal() {
        var d=DefinitionCodecTest.dungeon().withOptionalZones(List.of(zone("side"),zone("other")));
        assertTrue(new Validator().validate(d,MOBS).isEmpty());
        assertTrue(new Validator().validate(d.withOptionalZones(List.of(zone("first"))),MOBS).stream().anyMatch(e->e.messageKey().equals("validation.duplicate-id")));
    }
    @Test void chestDuplicatesNeighboursAndDoorsAreRejected() {
        var validator=new Validator();
        assertTrue(validator.validate(chests(List.of(chest("a",POINT),chest("b",POINT))),MOBS).stream().anyMatch(e->e.messageKey().equals("validation.loot-conflict")));
        var next=new Point(POINT.world(),POINT.x()+1,POINT.y(),POINT.z(),0,0);
        assertTrue(validator.validate(chests(List.of(chest("a",POINT),chest("b",next))),MOBS).stream().anyMatch(e->e.messageKey().equals("validation.loot-double")));
    }
    @TempDir Path directory;
    @BeforeEach void items() {
        dev.dasan.customdungeons.ability.impl.PaperApiTestBootstrap.initialize();
        ConfigurationSerialization.registerClass(LootItem.class,"T61LootItem");
    }
    @AfterEach void unregister() {ConfigurationSerialization.unregisterClass(LootItem.class);}
    static List<ValidationError> errors(DungeonDef d) {return new Validator().validate(d,MOBS,Map.of(),false);}
    static void has(DungeonDef d,String path,String key) {
        assertTrue(errors(d).stream().anyMatch(e->e.path().equals(path) && e.messageKey().equals("validation."+key)),()->errors(d).toString());
    }
    static DungeonDef withZone(OptionalZoneDef z) {return DefinitionCodecTest.dungeon().withOptionalZones(List.of(z));}
    static DungeonDef entries(List<LootEntryDef> entries) {
        return chests(List.of(new LootChestDef("c",POINT,LootChestDef.Trigger.ROOM_ENTER,entries)));
    }
    static SpawnerDef spawner(List<WaveDef> waves) {return new SpawnerDef("s",POINT,3,waves);}
    static YamlConfiguration raw(DungeonDef d) {
        var yaml=new YamlConfiguration();new DefinitionCodec().encode(d).forEach(yaml::set);return yaml;
    }
    @ParameterizedTest @ValueSource(strings={"","UPPER","../bad","a a","012345678901234567890123456789012"})
    void invalidZoneAndChestIds(String id) {
        has(withZone(zone(id)),"optional-zones[0].id","id");
        has(chests(List.of(chest(id,POINT))),"rooms[0].loot-chests[0].id","id");
    }
    @Test void duplicateRoomAndChestIdsAcrossRoomsAreRejected() {
        var d=DefinitionCodecTest.dungeon();var first=d.rooms().getFirst();
        has(SpawnerPresets.withRooms(d,List.of(first,first)),"rooms[1].id","duplicate-id");
        has(SpawnerPresets.withRooms(d,List.of(first.withLootChests(List.of(chest("same",POINT))),
                d.rooms().getLast().withLootChests(List.of(chest("same",new Point("dungeons",4,64,4,0,0)))))),
                "rooms[1].loot-chests[0].id","duplicate-id");
    }
    @Test void duplicateSpawnersAreLocalToOriginAndIdsMustBeValid() {
        var sp=zone("z").spawners().getFirst();
        has(withZone(new OptionalZoneDef("z",REGION,List.of(sp,sp))),"optional-zones[0].spawners[1].id","duplicate-id");
        has(withZone(new OptionalZoneDef("z",REGION,List.of(new SpawnerDef("BAD",POINT,3,sp.waves())))),
                "optional-zones[0].spawners[0].id","id");
    }
    @Test void zonesRequireRegionSpawnersWavesAndEntries() {
        has(withZone(new OptionalZoneDef("z",null,List.of())),"optional-zones[0].region","required");
        has(withZone(new OptionalZoneDef("z",REGION,List.of())),"optional-zones[0].spawners","non-empty");
        has(withZone(new OptionalZoneDef("z",REGION,List.of(spawner(List.of())))),"optional-zones[0].spawners[0].waves","non-empty");
        has(withZone(new OptionalZoneDef("z",REGION,List.of(spawner(List.of(new WaveDef(List.of(),SpawnMode.SIMULTANEOUS,0,0)))))),
                "optional-zones[0].spawners[0].waves[0].entries","non-empty");
    }
    @Test void zoneRegionWorldBoundsSpawnerLocationAndExit() {
        has(withZone(new OptionalZoneDef("z",new Region("other",REGION.min(),REGION.max()),zone("z").spawners())),"optional-zones[0].region","zone-region");
        has(withZone(new OptionalZoneDef("z",new Region("dungeons",REGION.max(),REGION.min()),List.of())),"optional-zones[0].region","zone-region");
        var outside=new Point("dungeons",50,64,2,0,0);
        has(withZone(new OptionalZoneDef("z",REGION,List.of(new SpawnerDef("s",outside,3,zone("z").spawners().getFirst().waves())))),
                "optional-zones[0].spawners[0].location","outside-area");
        var aroundExit=Region.of("dungeons",new BlockPos(19,60,0),new BlockPos(25,70,10));
        has(withZone(new OptionalZoneDef("z",aroundExit,List.of())),"exit","exit-inside");
        var yaml=raw(withZone(zone("z")));yaml.set("area",Map.of("world","dungeons","min",Map.of("x",0,"y",60,"z",0),"max",Map.of("x",5,"y",70,"z",5)));
        has(new DefinitionCodec().decodeDungeon("ejemplo",yaml),"optional-zones[0].region","outside-area");
    }
    @Test void zonePresetsAreEffectiveAndResolveDetachUsageRetainChests() throws Exception {
        var wave=zone("z").spawners().getFirst().waves();
        var preset=new SpawnerPreset("pack","Pack",3,wave);var presets=Map.of("pack",preset);
        var z=new OptionalZoneDef("z",REGION,List.of(new SpawnerDef("s",POINT,3,List.of(),"pack")));
        var d=chests(List.of(chest("c",POINT))).withOptionalZones(List.of(z));
        assertTrue(new Validator().validate(d,MOBS,presets,false).isEmpty());
        assertEquals(d,new DefinitionCodec().decodeDungeon(d.id(),DefinitionCodecTest.yaml(new DefinitionCodec().encode(d))));
        var resolved=SpawnerPresets.resolve(d,presets);
        assertEquals(wave,resolved.optionalZones().getFirst().spawners().getFirst().waves());
        assertNull(resolved.optionalZones().getFirst().spawners().getFirst().presetId());
        assertEquals(d.rooms(),resolved.rooms());
        assertEquals(resolved,SpawnerPresets.detach(d,"pack",presets));
        assertEquals(List.of(new SpawnerPresets.Usage(d.id(),d.displayName(),"z")),SpawnerPresets.usage("pack",Map.of(d.id(),d)));
        assertTrue(new Validator().validate(d,MOBS,Map.of(),false).stream().anyMatch(e->e.path().equals("optional-zones[0].spawners[0].preset-id")));
        assertTrue(new Validator().validate(d,MOBS,Map.of("pack",new SpawnerPreset("pack","Pack",3,List.of())),false).stream()
                .anyMatch(e->e.path().equals("optional-zones[0].spawners[0].waves")));
        assertTrue(new Validator().validate(d,Map.of(),presets,false).stream().anyMatch(e->e.messageKey().equals("validation.template")));
    }
    @Test void chestCanBeOutsideAssociatedRoomInZoneOrAreaAndAssociationSurvivesReordering() {
        var d=DefinitionCodecTest.dungeon();
        var zone=new OptionalZoneDef("z",Region.of("dungeons",new BlockPos(30,60,0),new BlockPos(40,70,10)),List.of());
        var point=new Point("dungeons",35,64,2,0,0);
        var with=SpawnerPresets.withRooms(d,List.of(d.rooms().getFirst(),d.rooms().getLast().withLootChests(List.of(chest("c",point))))).withOptionalZones(List.of(zone));
        assertTrue(errors(with).stream().noneMatch(e->e.path().contains("loot-chests")));
        var reversed=SpawnerPresets.withRooms(with,List.of(with.rooms().getLast(),with.rooms().getFirst()));
        assertEquals("last",reversed.rooms().getFirst().id());assertEquals(point,reversed.rooms().getFirst().lootChests().getFirst().location());
        has(chests(List.of(chest("c",point))),"rooms[0].loot-chests[0].location","loot-location");
        var yaml=raw(chests(List.of(chest("c",point))));
        yaml.set("area",Map.of("world","dungeons","min",Map.of("x",0,"y",60,"z",0),"max",Map.of("x",40,"y",70,"z",10)));
        assertTrue(errors(new DefinitionCodec().decodeDungeon("ejemplo",yaml)).stream().noneMatch(e->e.path().contains("loot-chests")));
    }
    @ParameterizedTest @ValueSource(doubles={Double.NaN,Double.POSITIVE_INFINITY,Double.NEGATIVE_INFINITY,1e40})
    void invalidChestCoordinate(double x) {
        has(chests(List.of(chest("c",new Point("dungeons",x,64,2,0,0)))),"rooms[0].loot-chests[0].location","loot-location");
    }
    @Test void chestWorldMissingPositionAndPhysicalConflicts() {
        has(chests(List.of(chest("c",null))),"rooms[0].loot-chests[0].location","loot-location");
        has(chests(List.of(chest("c",new Point("other",1,64,2,0,0)))),"rooms[0].loot-chests[0].location","loot-location");
        var d=DefinitionCodecTest.dungeon();
        has(SpawnerPresets.withRooms(d,List.of(d.rooms().getFirst().withLootChests(List.of(chest("c",POINT))),d.rooms().getLast())),
                "rooms[0].loot-chests[0].location","loot-conflict");
        var yaml=raw(chests(List.of(chest("c",POINT))));
        yaml.set("entrance-door",Map.of("world","dungeons","min",Map.of("x",1,"y",64,"z",2),"max",Map.of("x",1,"y",64,"z",2)));
        has(new DefinitionCodec().decodeDungeon("ejemplo",yaml),"rooms[0].loot-chests[0].location","loot-conflict");
        for(String field:List.of("plates","exit-plates")) {
            yaml=raw(chests(List.of(chest("c",POINT))));
            yaml.set(field,List.of(Map.of("world","dungeons","x",1.9,"y",64.1,"z",2.5)));
            has(new DefinitionCodec().decodeDungeon("ejemplo",yaml),"rooms[0].loot-chests[0].location","loot-conflict");
        }
    }
    @Test void onlyHorizontalChestNeighboursConflict() {
        assertTrue(errors(chests(List.of(chest("a",POINT),chest("b",new Point("dungeons",1,65,2,0,0)),
                chest("c",new Point("dungeons",2,64,3,0,0))))).isEmpty());
    }
    @Test void blockInspectorReportsRestorationAndRealDoubleChestSeparately() {
        var d=chests(List.of(chest("c",POINT)));
        var findings=new Validator().validateLootBlocks(d,p->{assertEquals(chest("c",POINT).location(),p);return new Validator.LootBlockInspector.Inspection(false,true);});
        assertEquals(Set.of("validation.loot-block","validation.loot-double"),findings.stream().map(ValidationError::messageKey).collect(java.util.stream.Collectors.toSet()));
        assertTrue(new Validator().validateLootBlocks(d,p->new Validator.LootBlockInspector.Inspection(true,false)).isEmpty());
    }
    @Test void entriesMaximumIs27AndEmptyIsValid() {
        var entry=new LootEntryDef(new LootItem(1,"ordinary"),.4);
        assertTrue(errors(entries(Collections.nCopies(27,entry))).isEmpty());
        has(entries(Collections.nCopies(28,entry)),"rooms[0].loot-chests[0].entries","numeric-range");
        assertTrue(errors(entries(List.of())).isEmpty());
    }
    @ParameterizedTest @ValueSource(ints={0,-1,65})
    void invalidStackAmount(int amount) {has(entries(List.of(new LootEntryDef(new LootItem(amount,"ordinary"),1))),"rooms[0].loot-chests[0].entries[0].item","loot-stack");}
    @Test void nullAirAndReservedStacks() {
        has(entries(List.of(new LootEntryDef(null,1))),"rooms[0].loot-chests[0].entries[0].item","loot-stack");
        var air=new LootItem(1,"air");has(entries(List.of(new LootEntryDef(air,1))),"rooms[0].loot-chests[0].entries[0].item","loot-stack");
        for(String marker:List.of("tool","key"))has(entries(List.of(new LootEntryDef(new LootItem(1,marker),1))),"rooms[0].loot-chests[0].entries[0].item","equipment-"+marker);
    }
    @ParameterizedTest @ValueSource(doubles={-.01,1.01,Double.NaN,Double.POSITIVE_INFINITY,Double.NEGATIVE_INFINITY})
    void invalidChanceBlocksSaving(double chance) {has(entries(List.of(new LootEntryDef(new LootItem(1,"ordinary"),chance))),"rooms[0].loot-chests[0].entries[0].chance","numeric-range");}
    @Test void independentChancesNeedNotSumToOneAndBothEndpointsAreValid() {
        assertTrue(errors(entries(List.of(new LootEntryDef(new LootItem(1,"ordinary"),0),new LootEntryDef(new LootItem(1,"ordinary"),1),new LootEntryDef(new LootItem(1,"ordinary"),.999999999)))).isEmpty());
    }
    @Test void stacksAreDefensivelyCopiedAndOpaqueMetadataRoundTrips() throws Exception {
        var original=new LootItem(12,"ordinary");var entry=new LootEntryDef(original,.123456789);
        original.setAmount(2);assertEquals(12,entry.item().getAmount());
        entry.item().setAmount(3);assertEquals(12,entry.item().getAmount());
        var d=entries(List.of(entry));var decoded=new DefinitionCodec().decodeDungeon(d.id(),DefinitionCodecTest.yaml(new DefinitionCodec().encode(d)));
        var restored=(LootItem)decoded.rooms().getFirst().lootChests().getFirst().entries().getFirst().item();
        assertEquals(12,restored.getAmount());assertEquals(original.payload,restored.payload);
        assertEquals(.123456789,decoded.rooms().getFirst().lootChests().getFirst().entries().getFirst().chance());
    }
    @Test void unknownTriggerAndMissingOrNonNumericChanceFailDecoding() {
        var d=entries(List.of(new LootEntryDef(new LootItem(1,"ordinary"),1)));
        for(Object chance:List.of("bad",Double.NaN,Double.POSITIVE_INFINITY)) {
            var entry=Map.of("item",new LootItem(1,"ordinary"),"chance",chance);
            var yaml=raw(d);yaml.set("rooms",List.of(Map.of("id","r","loot-chests",List.of(Map.of("id","c","entries",List.of(entry))))));
            assertThrows(IllegalArgumentException.class,()->new DefinitionCodec().decodeDungeon("d",yaml));
        }
        var yaml=raw(d);yaml.set("rooms",List.of(Map.of("id","r","loot-chests",List.of(Map.of("id","c","entries",List.of(Map.of("item",new LootItem(1,"ordinary"))))))));
        assertThrows(IllegalArgumentException.class,()->new DefinitionCodec().decodeDungeon("d",yaml));
        yaml.set("rooms",List.of(Map.of("id","r","loot-chests",List.of(Map.of("id","c","trigger","UNKNOWN")))));
        assertThrows(IllegalArgumentException.class,()->new DefinitionCodec().decodeDungeon("d",yaml));
    }
    @Test void omittedTriggerDefaultsToClear() {
        var yaml=new YamlConfiguration();yaml.set("rooms",List.of(Map.of("id","r","loot-chests",List.of(Map.of("id","c")))));
        assertEquals(LootChestDef.Trigger.ROOM_CLEAR,new DefinitionCodec().decodeDungeon("d",yaml).rooms().getFirst().lootChests().getFirst().trigger());
    }
    @Test void normalizerClampsNewNumericPathsWithWarningsWithoutMutatingInput() {
        var d=entries(List.of(new LootEntryDef(new LootItem(1,"ordinary"),2))).withOptionalZones(List.of(
                new OptionalZoneDef("z",REGION,List.of(new SpawnerDef("s",POINT,100,List.of(new WaveDef(List.of(new WaveEntry("zombie",300,-20)),SpawnMode.STAGGERED,1,-20)))))));
        var yaml=raw(d);String before=yaml.saveToString();
        var normalized=new NumericLoadNormalizer(null,new dev.dasan.customdungeons.ability.AbilityRegistry()).normalize("dungeons",yaml);
        var adjusted=new DefinitionCodec().decodeDungeon(d.id(),normalized.yaml());
        assertEquals(1,adjusted.rooms().getFirst().lootChests().getFirst().entries().getFirst().chance());
        assertEquals(64,adjusted.optionalZones().getFirst().spawners().getFirst().radius());
        assertEquals(6,normalized.warnings().size());assertEquals(before,yaml.saveToString());
        assertTrue(errors(adjusted).isEmpty());
        assertTrue(normalized.warnings().stream().anyMatch(w->w.path().equals("rooms[0].loot-chests[0].entries[0].chance")));
    }
    @ParameterizedTest @ValueSource(strings={"bad",".NaN",".inf","-.inf"})
    void loadingRejectsNonNumericAndNonFiniteNewFields(String raw) throws Exception {
        var yaml=raw(withZone(zone("z")));yaml.set("optional-zones",List.of(Map.of("id","z","spawners",List.of(Map.of("radius",raw)))));
        assertThrows(NumericLoadNormalizer.InvalidNumber.class,()->new NumericLoadNormalizer(null,null).normalize("dungeons",yaml));
        yaml.set("optional-zones",List.of());yaml.set("rooms",List.of(Map.of("loot-chests",List.of(Map.of("entries",List.of(Map.of("chance",raw)))))));
        assertThrows(NumericLoadNormalizer.InvalidNumber.class,()->new NumericLoadNormalizer(null,null).normalize("dungeons",yaml));
    }
    @Test void immutableZoneChestAndEntryLists() {
        var spawners=new ArrayList<>(zone("z").spawners());var z=new OptionalZoneDef("z",REGION,spawners);spawners.clear();assertFalse(z.spawners().isEmpty());
        var entries=new ArrayList<>(List.of(new LootEntryDef(new LootItem(1,"ordinary"),1)));
        var chest=new LootChestDef("c",POINT,LootChestDef.Trigger.ROOM_CLEAR,entries);entries.clear();assertEquals(1,chest.entries().size());
        assertThrows(UnsupportedOperationException.class,()->z.spawners().clear());
        assertThrows(UnsupportedOperationException.class,()->chest.entries().clear());
    }
    @Test void wizardAndConstructionDraftsPersistNewDataIncludingBaselineAndUndo() {
        var d=entries(List.of(new LootEntryDef(new LootItem(7,"ordinary"),.37))).withOptionalZones(List.of(zone("z")));
        var warnings=new ArrayList<String>();
        try(var store=new dev.dasan.customdungeons.gui.wizard.WizardDraftStore(directory,Runnable::run,warnings::add)) {
            store.save(new dev.dasan.customdungeons.gui.wizard.WizardDraftStore.Saved(d,3,4)).join();
        }
        try(var store=new dev.dasan.customdungeons.gui.wizard.WizardDraftStore(directory,Runnable::run,warnings::add)) {
            assertEquals(d,store.get(d.id()).orElseThrow().definition());assertTrue(warnings.isEmpty());
        }
        var state=new dev.dasan.customdungeons.tool.construction.BuildState(d);
        state.change(d.withFinish(FinishMode.DELAYED,60,FinishDestination.EXIT,List.of()));
        var admin=UUID.randomUUID();
        try(var journal=new dev.dasan.customdungeons.tool.construction.BuildJournal(directory,Runnable::run)) {journal.save(admin,state.snapshot()).join();}
        try(var journal=new dev.dasan.customdungeons.tool.construction.BuildJournal(directory,Runnable::run)) {
            var saved=journal.draft(admin,d.id()).orElseThrow();assertEquals(state.snapshot(),saved);
            assertEquals(d,saved.baseline());
            var resumed=new dev.dasan.customdungeons.tool.construction.BuildState(saved);assertTrue(resumed.undo());assertEquals(d,resumed.definition());
        }
    }
    @Test void storeSavesEnabledImmediateDefinitionAndReloadsWithoutChangingMode() throws Exception {
        var d=entries(List.of(new LootEntryDef(new LootItem(7,"ordinary"),.37))).withOptionalZones(List.of(zone("z")));
        var warnings=new ArrayList<String>();
        try(var store=new DefinitionStore(directory,new ConfigLoader(p->{},m->m==Material.IRON_BLOCK).load(new YamlConfiguration()),Set.of("test"),warnings::add,Runnable::run)) {
            store.save(DefinitionCodecTest.mob()).join();store.save(d).join();
            byte[] before=Files.readAllBytes(directory.resolve("dungeons/ejemplo.yml"));store.reload();
            assertEquals(d,store.dungeons().get(d.id()));assertTrue(store.dungeons().get(d.id()).enabled());
            assertArrayEquals(before,Files.readAllBytes(directory.resolve("dungeons/ejemplo.yml")));
            assertTrue(warnings.stream().anyMatch(w->w.contains("validation.loot-immediate")),warnings::toString);
        }
    }
    @Test void loadClampsChanceAndZoneFieldsWithoutRewritingOrDisablingAndKeepsWarnings() throws Exception {
        var d=entries(List.of(new LootEntryDef(new LootItem(7,"ordinary"),.37))).withOptionalZones(List.of(zone("z")));
        var warnings=new ArrayList<String>();
        try(var store=new DefinitionStore(directory,new ConfigLoader(p->{},m->m==Material.IRON_BLOCK).load(new YamlConfiguration()),Set.of("test"),warnings::add,Runnable::run)) {
            store.save(DefinitionCodecTest.mob()).join();store.save(d).join();
            var file=directory.resolve("dungeons/ejemplo.yml");String yaml=Files.readString(file).replace("chance: 0.37","chance: 2.0").replace("radius: 3.0","radius: 100.0");
            Files.writeString(file,yaml);byte[] before=Files.readAllBytes(file);store.reload();
            assertTrue(store.dungeons().get(d.id()).enabled(),warnings::toString);
            assertEquals(1,store.dungeons().get(d.id()).rooms().getFirst().lootChests().getFirst().entries().getFirst().chance());
            assertEquals(64,store.dungeons().get(d.id()).optionalZones().getFirst().spawners().getFirst().radius());
            assertTrue(store.loadWarnings("dungeons",d.id()).stream().anyMatch(w->w.path().equals("rooms[0].loot-chests[0].entries[0].chance")));
            assertTrue(warnings.stream().anyMatch(w->w.contains("optional-zones[0].spawners[0].radius")),warnings::toString);
            assertArrayEquals(before,Files.readAllBytes(file));
        }
    }
    @Test void structuralLoadErrorDisablesDungeonWhileKeepingZonesAndChests() throws Exception {
        var d=chests(List.of(chest("c",POINT))).withOptionalZones(List.of(zone("z")));
        try(var store=new DefinitionStore(directory,new ConfigLoader(p->{},m->m==Material.IRON_BLOCK).load(new YamlConfiguration()),Set.of("test"),w->{},Runnable::run)) {
            store.save(DefinitionCodecTest.mob()).join();store.save(d).join();
            var file=directory.resolve("dungeons/ejemplo.yml");Files.writeString(file,Files.readString(file).replace("id: z\n","id: last\n"));store.reload();
            var loaded=store.dungeons().get(d.id());assertFalse(loaded.enabled());assertEquals(d.rooms(),loaded.rooms());assertEquals("last",loaded.optionalZones().getFirst().id());
        }
    }
    @ParameterizedTest @ValueSource(doubles={0,65,Double.NaN,Double.POSITIVE_INFINITY})
    void invalidZoneRadiusBlocksSaving(double radius) {
        var z=zone("z");has(withZone(new OptionalZoneDef(z.id(),z.region(),List.of(new SpawnerDef("s",POINT,radius,z.spawners().getFirst().waves())))),
                "optional-zones[0].spawners[0].radius","numeric-range");
    }
    @Test void zoneSpawnerRequiresFiniteLocationInSameWorldAndReferencesExistingTemplate() {
        var waves=zone("z").spawners().getFirst().waves();
        has(withZone(new OptionalZoneDef("z",REGION,List.of(new SpawnerDef("s",null,3,waves)))),"optional-zones[0].spawners[0].location","required");
        has(withZone(new OptionalZoneDef("z",REGION,List.of(new SpawnerDef("s",new Point("other",1,64,2,0,0),3,waves)))),"optional-zones[0].spawners[0].location","outside-area");
        has(withZone(new OptionalZoneDef("z",REGION,List.of(new SpawnerDef("s",new Point("dungeons",Double.NaN,64,2,0,0),3,waves)))),"optional-zones[0].spawners[0].location","point");
        var missing=List.of(new WaveDef(List.of(new WaveEntry("missing",1,0)),SpawnMode.SIMULTANEOUS,0,0));
        has(withZone(new OptionalZoneDef("z",REGION,List.of(spawner(missing)))),"optional-zones[0].spawners[0].waves[0].entries[0].template-id","template");
    }
    @Test void newCatalogMessagesHaveMatchingSpanishAndEnglishKeysAndHistory() throws Exception {
        var spanish=new YamlConfiguration();var english=new YamlConfiguration();
        try(var es=getClass().getResourceAsStream("/messages.yml");var en=getClass().getResourceAsStream("/messages_en.yml")) {
            spanish.load(new java.io.InputStreamReader(es,java.nio.charset.StandardCharsets.UTF_8));english.load(new java.io.InputStreamReader(en,java.nio.charset.StandardCharsets.UTF_8));
        }
        assertEquals(33,spanish.getInt("version"));assertEquals(33,english.getInt("version"));
        for(String key:List.of("command.reset-loot-resolved","loot.recovery-resolved")) {
            assertNotNull(spanish.getString(key));assertNotNull(english.getString(key));
        }
        assertNotNull(getClass().getResource("/defaults-history/messages-v31.yml"));assertNotNull(getClass().getResource("/defaults-history/messages_en-v31.yml"));
        assertNotNull(spanish.getString("loot.persistence-failed"));assertNotNull(english.getString("loot.persistence-failed"));
        assertNotNull(getClass().getResource("/defaults-history/messages-v30.yml"));assertNotNull(getClass().getResource("/defaults-history/messages_en-v30.yml"));
        for(String key:List.of("region","loot-block-unprepared","point","duplicate-id","zone-region","loot-immediate","loot-location","loot-conflict","loot-double","loot-stack","loot-trigger","loot-block")) {
            assertNotNull(spanish.getString("validation."+key));assertNotNull(english.getString("validation."+key));
        }
        assertNotNull(getClass().getResource("/defaults-history/messages-v29.yml"));assertNotNull(getClass().getResource("/defaults-history/messages_en-v29.yml"));
    }
    @Test void invertedGlobalAreaBlocksValidationAndPublicationWithAndWithoutZone() {
        for(boolean zones:List.of(false,true)) {
            var d=zones?withZone(zone("z")):DefinitionCodecTest.dungeon();
            var yaml=raw(d);yaml.set("area",Map.of("world","dungeons","min",Map.of("x",10,"y",70,"z",10),"max",Map.of("x",0,"y",60,"z",0)));
            var invalid=new DefinitionCodec().decodeDungeon(d.id(),yaml);
            has(invalid,"area","region");
            assertFalse(dev.dasan.customdungeons.gui.wizard.WizardRules.evaluate(invalid,MOBS,Map.of(),false).errors(6).isEmpty());
            var config=new ConfigLoader(p->{},m->m==Material.IRON_BLOCK).load(new YamlConfiguration());
            try(var store=new DefinitionStore(directory,config,Set.of("test"),w->{},Runnable::run)) {
                store.save(DefinitionCodecTest.mob()).join();
                assertThrows(java.util.concurrent.CompletionException.class,()->store.save(invalid).join());
                assertFalse(store.dungeons().containsKey(invalid.id()));
                store.save(DefinitionCodecTest.mob()).join(); // Drain the failed write before closing the store.
            }
        }
    }
    /** Supplies Bukkit serialization at the unit-test boundary without a running Paper server. */
    @SerializableAs("T61LootItem")
    public static final class LootItem extends ItemStack {
        private int amount;private final String marker;private final Map<String,Object> payload;
        LootItem(int amount,String marker) {this(amount,marker,Map.of("custom-name","Treasure", "nbt",Map.of("enchants",List.of("sharpness:7"),"customdungeons:test","opaque-data")));}
        private LootItem(int amount,String marker,Map<String,Object> payload) {super();this.amount=amount;this.marker=marker;this.payload=Map.copyOf(payload);}
        public static LootItem deserialize(Map<String,Object> map) {return new LootItem(((Number)map.get("amount")).intValue(),(String)map.get("marker"),(Map<String,Object>)map.get("payload"));}
        @Override public Map<String,Object> serialize() {return Map.of("amount",amount,"marker",marker,"payload",payload);}
        @Override public ItemStack clone() {return new LootItem(amount,marker,payload);}
        @Override public Material getType() {return marker.equals("air")?Material.AIR:Material.DIAMOND;}
        @Override public int getMaxStackSize() {return 64;}
        @Override public int getAmount() {return amount;}
        @Override public void setAmount(int amount) {this.amount=amount;}
        @Override public boolean hasItemMeta() {return true;}
        @Override public org.bukkit.inventory.meta.ItemMeta getItemMeta() {
            var meta=mock(org.bukkit.inventory.meta.ItemMeta.class);var pdc=mock(org.bukkit.persistence.PersistentDataContainer.class);
            when(meta.getPersistentDataContainer()).thenReturn(pdc);
            if(marker.equals("tool"))when(pdc.has(dev.dasan.customdungeons.mob.MobKeys.TOOL)).thenReturn(true);
            if(marker.equals("key"))when(pdc.has(dev.dasan.customdungeons.mob.MobKeys.KEY_ITEM)).thenReturn(true);
            return meta;
        }
        @Override public boolean equals(Object other) {return other instanceof LootItem item && amount==item.amount && marker.equals(item.marker) && payload.equals(item.payload);}
        @Override public int hashCode() {return Objects.hash(amount,marker,payload);}
    }

}
