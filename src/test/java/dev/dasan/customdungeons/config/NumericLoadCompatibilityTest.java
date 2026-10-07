package dev.dasan.customdungeons.config;

import dev.dasan.customdungeons.model.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CompletionException;
import java.util.stream.Stream;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import static org.junit.jupiter.api.Assertions.*;

/** Load and save are intentionally different boundaries, tested through the real filesystem store. */
class NumericLoadCompatibilityTest {
    @TempDir Path directory;
    final DefinitionCodec codec=new DefinitionCodec();
    final List<String> console=new ArrayList<>();
    DefinitionStore store;
    @BeforeEach void setup() throws Exception {
        store=new DefinitionStore(directory,new ConfigLoader(p->{},m->m==Material.IRON_BLOCK).load(new YamlConfiguration()),
                Set.of("test","lightning"),console::add,Runnable::run);
        var mob=DefinitionCodecTest.yaml(codec.encode(DefinitionCodecTest.mob()));
        for(String path:List.of("abilities[0].ability-id","combos[0].steps[0].ability-id","combos[0].steps[1].ability-id",
                "phases[0].abilities[0].ability-id","phases[0].combos[0].steps[0].ability-id","phases[0].combos[0].steps[1].ability-id"))
            set(mob,path,"lightning");
        store.save(codec.decodeMob("zombie",mob)).join();store.save(DefinitionCodecTest.dungeon()).join();
    }
    record Field(String kind,String path,Number value,Number expected) {}
    static Stream<Field> fields() {return Stream.of(
        new Field("dungeons","cooldown-seconds",604801,604800),
        new Field("dungeons","min-players",-1,1),
        new Field("dungeons","max-players",301,300),
        new Field("dungeons","lives",101,100),
        new Field("dungeons","lives",0,1),
        new Field("dungeons","lobby-countdown-seconds",-1,5),
        new Field("dungeons","time-limit-seconds",7201,7200),
        new Field("dungeons","plate-countdown-seconds",-1,1),
        new Field("dungeons","exit-grace-seconds",301,300),
        new Field("dungeons","scaling.extra-health-per-player",-1,0),
        new Field("dungeons","intro-seconds",100,20),
        new Field("dungeons","scaling.extra-mobs-per-player",6,5),
        new Field("dungeons","rooms[0].spawners[0].radius",99,64),
        new Field("dungeons","rooms[0].spawners[0].waves[0].pause-after-ticks",72001,72000),
        new Field("dungeons","rooms[0].spawners[0].waves[0].stagger-interval-ticks",-1,2),
        new Field("dungeons","rooms[0].spawners[0].waves[0].entries[0].count",201,200),
        new Field("dungeons","rooms[0].spawners[0].waves[0].entries[0].delay-ticks",-1,0),
        new Field("dungeons","reward.money",-1,0),
        new Field("dungeons","reward.xp",1000001,1000000),
        new Field("mobs","speed",4.7,1),
        new Field("mobs","scale",17,16),
        new Field("mobs","damage",1001,1000),
        new Field("mobs","knockback-resistance",-1,0),
        new Field("mobs","max-health",2048,1024),
        new Field("mobs","potions[0].amplifier",256,255),
        new Field("mobs","abilities[0].chance",2,1),
        new Field("mobs","abilities[0].trigger-value",72001,72000),
        new Field("mobs","abilities[0].range",72001,72000),
        new Field("mobs","abilities[0].telegraph-ticks",72001,72000),
        new Field("mobs","abilities[0].params.damage",1001,1000),
        new Field("mobs","abilities[0].cooldown-ticks",72001,72000),
        new Field("mobs","combos[0].range",300,256),
        new Field("mobs","combos[0].trigger-value",3601,3600),
        new Field("mobs","combos[0].cooldown-ticks",72001,72000),
        new Field("mobs","combos[0].steps[0].params.damage",1001,1000),
        new Field("mobs","phases[0].abilities[0].chance",-1,0),
        new Field("mobs","phases[0].abilities[0].params.damage",1001,1000),
        new Field("mobs","phases[0].combos[0].steps[0].params.damage",1001,1000),
        new Field("mobs","combos[0].steps[0].delay-ticks",72001,72000),
        new Field("mobs","phases[0].health-threshold",2,.9999),
        new Field("mobs","phases[0].heal-percent",101,100),
        new Field("mobs","phases[0].invulnerable-ticks",1201,1200),
        new Field("mobs","phases[0].summons[0].count",100000,50),
        new Field("mobs","phases[0].summons[0].delay-ticks",72001,72000),
        new Field("mobs","phases[0].potions[0].amplifier",-1,0)
    );}
    @ParameterizedTest @MethodSource("fields") void loadClampsWarnsAndPreservesYaml(Field field) throws Exception {
        Path file=file(field.kind());var yaml=read(file);set(yaml,field.path(),field.value());
        yaml.save(file.toFile());byte[] original=Files.readAllBytes(file);console.clear();
        store.reloadAsync(Runnable::run).join();
        assertNotNull(store.mobs().get("zombie"),console::toString);
        assertTrue(store.dungeons().get("ejemplo").enabled(),console::toString);
        var loaded=readModel(field.kind());assertEquals(field.expected().doubleValue(),((Number)get(loaded,field.path())).doubleValue(),1e-9);
        assertEquals(1,console.stream().filter(w->w.contains(file+":"+field.path()+" ")).count(),console::toString);
        assertArrayEquals(original,Files.readAllBytes(file));
        if(field.path().equals("speed") || field.path().equals("cooldown-seconds")) console.forEach(System.out::println);
        assertEquals(1,store.loadWarnings(field.kind(),field.kind().equals("mobs")?"zombie":"ejemplo").stream().filter(w->w.path().equals(field.path())).count());
    }
    @ParameterizedTest @MethodSource("fields") void saveRejectsAndPreservesYaml(Field field) throws Exception {
        Path file=file(field.kind());byte[] original=Files.readAllBytes(file);var yaml=read(file);set(yaml,field.path(),field.value());
        assertThrows(CompletionException.class,()->{
            if(field.kind().equals("mobs")) store.save(codec.decodeMob("zombie",yaml)).join();
            else store.save(codec.decodeDungeon("ejemplo",yaml)).join();
        });
        assertArrayEquals(original,Files.readAllBytes(file));
    }
    @Test void everyRegisteredParameterLoadsClampedInEveryLoadoutAndRejectsSaving() throws Exception {
        var registry=new dev.dasan.customdungeons.ability.AbilityRegistry();dev.dasan.customdungeons.ability.Abilities.registerDefaults(registry);
        var allIds=new HashSet<String>();registry.all().forEach(a->allIds.add(a.id()));
        var allStore=new DefinitionStore(directory,new ConfigLoader(p->{},m->m==Material.IRON_BLOCK).load(new YamlConfiguration()),
                allIds,console::add,Runnable::run,new Validator(registry));
        Path file=file("mobs");byte[] baseline=Files.readAllBytes(file);int checked=0;
        for(var ability:registry.all()) for(var spec:ability.params()) if(NumericRanges.numeric(spec)) {
            for(String context:List.of("abilities[0]","combos[0].steps[0]","phases[0].abilities[0]","phases[0].combos[0].steps[0]")) {
                Files.write(file,baseline);var yaml=read(file);
                set(yaml,context+".ability-id",ability.id());set(yaml,context+".params",new LinkedHashMap<>(Map.of(spec.key(),spec.max()+1)));
                yaml.save(file.toFile());byte[] original=Files.readAllBytes(file);console.clear();
                allStore.reload();assertNotNull(allStore.mobs().get("zombie"),ability.id()+":"+spec.key()+console);
                var loaded=DefinitionCodecTest.yaml(codec.encode(allStore.mobs().get("zombie")));
                assertEquals(spec.max(),((Number)get(loaded,context+".params."+spec.key())).doubleValue());
                assertArrayEquals(original,Files.readAllBytes(file));
                assertEquals(1,allStore.loadWarnings("mobs","zombie").stream().filter(w->w.path().equals(context+".params."+spec.key())).count());
                assertThrows(CompletionException.class,()->allStore.save(codec.decodeMob("zombie",yaml)).join());
                assertArrayEquals(original,Files.readAllBytes(file));checked++;
            }
        }
        assertTrue(checked>0);System.out.println("Load/save compatibility for registered parameter contexts: "+checked);
    }
    @ParameterizedTest @org.junit.jupiter.params.provider.ValueSource(strings={"radius","waves[0].pause-after-ticks","waves[0].stagger-interval-ticks","waves[0].entries[0].count","waves[0].entries[0].delay-ticks"})
    void presetsClampWithoutBreakingReferencingDungeonAndSaveIsStrict(String path) throws Exception {
        var spawner=DefinitionCodecTest.dungeon().rooms().getFirst().spawners().getFirst();
        var preset=new SpawnerPreset("preset","Preset",3,spawner.waves());store.save(preset).join();
        Path file=directory.resolve("spawners/preset.yml");var yaml=read(file);set(yaml,path,100000);yaml.save(file.toFile());
        byte[] original=Files.readAllBytes(file);
        assertThrows(CompletionException.class,()->store.save(codec.decodeSpawnerPreset("preset",yaml)).join());
        var dungeon=read(file("dungeons"));set(dungeon,"spawner-presets",List.of("preset"));
        set(dungeon,"rooms[0].spawners[0].preset-id","preset");dungeon.save(file("dungeons").toFile());console.clear();
        store.reload();assertNotNull(store.spawnerPresets().get("preset"),console::toString);
        assertTrue(store.dungeons().get("ejemplo").enabled(),console::toString);
        assertArrayEquals(original,Files.readAllBytes(file));assertEquals(1,store.loadWarnings("spawners","preset").size());
        assertEquals(1,console.size(),console::toString);
    }
    @Test void warningsAreAtomicAndSurviveFailedSaveButClearAfterSuccessfulSaveOrReload() throws Exception {
        var file=file("mobs");var yaml=read(file);yaml.set("speed",4.7);yaml.save(file.toFile());store.reload();
        assertFalse(store.loadWarnings("mobs","zombie").isEmpty());
        assertThrows(CompletionException.class,()->store.save(codec.decodeMob("zombie",yaml)).join());
        store.save(store.dungeons().get("ejemplo")).join();assertFalse(store.loadWarnings("mobs","zombie").isEmpty());
        store.save(store.mobs().get("zombie")).join();assertTrue(store.loadWarnings("mobs","zombie").isEmpty());
        store.reload();assertTrue(store.loadWarnings("mobs","zombie").isEmpty());
    }
    @ParameterizedTest @org.junit.jupiter.params.provider.ValueSource(strings={"speed","phases[0].heal-percent","abilities[0].params.damage"})
    void nonNumericOrImpossibleMobValuesRemainErrorsWithoutLeakingInput(String path) throws Exception {
        for(Object impossible:List.of("PRIVATE_VALUE",Double.NaN,Double.POSITIVE_INFINITY)) {
            var file=file("mobs");var yaml=read(file);set(yaml,path,impossible);yaml.save(file.toFile());
            byte[] original=Files.readAllBytes(file);console.clear();store.reload();
            assertFalse(store.mobs().containsKey("zombie"));assertFalse(store.dungeons().get("ejemplo").enabled());
            assertArrayEquals(original,Files.readAllBytes(file));assertTrue(console.stream().noneMatch(w->w.contains("PRIVATE_VALUE")));
        }
    }
    @ParameterizedTest @org.junit.jupiter.params.provider.ValueSource(strings={"SIMULTANEOUS","SEQUENTIAL"})
    void unusedWaveIntervalsAlsoClampAndRejectSavingOutsideTheRange(String mode) throws Exception {
        var file=file("dungeons");var yaml=read(file);
        String wave="rooms[0].spawners[0].waves[0]";
        set(yaml,wave+".mode",mode);set(yaml,wave+".stagger-interval-ticks",72001);
        assertThrows(CompletionException.class,()->store.save(codec.decodeDungeon("ejemplo",yaml)).join());
        yaml.save(file.toFile());byte[] original=Files.readAllBytes(file);store.reload();
        assertTrue(store.dungeons().get("ejemplo").enabled());
        assertEquals(72000,store.dungeons().get("ejemplo").rooms().getFirst().spawners().getFirst().waves().getFirst().staggerIntervalTicks());
        assertArrayEquals(original,Files.readAllBytes(file));
    }
    @Test void loadKeepsRepresentableDecimalsEvenWhenEditorPrecisionIsStricter() throws Exception {
        var file=file("dungeons");var yaml=read(file);yaml.set("scaling.extra-mobs-per-player",.255);yaml.save(file.toFile());
        byte[] original=Files.readAllBytes(file);console.clear();store.reload();
        assertTrue(store.dungeons().get("ejemplo").enabled(),console::toString);
        assertEquals(.255,store.dungeons().get("ejemplo").scaling().extraMobsPerPlayer());assertArrayEquals(original,Files.readAllBytes(file));
        assertThrows(CompletionException.class,()->store.save(codec.decodeDungeon("ejemplo",yaml)).join());
        var preset=new SpawnerPreset("preset","Preset",3,DefinitionCodecTest.dungeon().rooms().getFirst().spawners().getFirst().waves());
        store.save(preset).join();file=directory.resolve("spawners/preset.yml");var presetYaml=read(file);presetYaml.set("radius",1.25);
        presetYaml.save(file.toFile());original=Files.readAllBytes(file);console.clear();store.reload();
        assertNotNull(store.spawnerPresets().get("preset"),console::toString);assertEquals(1.25,store.spawnerPresets().get("preset").radius());
        assertArrayEquals(original,Files.readAllBytes(file));assertTrue(console.isEmpty());
        assertThrows(CompletionException.class,()->store.save(codec.decodeSpawnerPreset("preset",presetYaml)).join());
    }
    @Test void reloadPublishesWarningsAndDefinitionsTogether() throws Exception {
        var worker=new ArrayDeque<Runnable>();var apply=new ArrayDeque<Runnable>();
        var async=new DefinitionStore(directory,new ConfigLoader(p->{},m->m==Material.IRON_BLOCK).load(new YamlConfiguration()),
                Set.of("test","lightning"),console::add,worker::add);async.loadAll();
        var file=file("mobs");var yaml=read(file);yaml.set("speed",4.7);yaml.save(file.toFile());
        var result=async.reloadAsync(apply::add);worker.remove().run();
        assertEquals(.3,async.mobs().get("zombie").speed());assertTrue(async.loadWarnings("mobs","zombie").isEmpty());
        apply.remove().run();result.join();
        assertEquals(1,async.mobs().get("zombie").speed());assertEquals(1,async.loadWarnings("mobs","zombie").size());
    }
    @ParameterizedTest @org.junit.jupiter.params.provider.ValueSource(strings={"cooldown-seconds","rooms[0].spawners[0].radius","rooms[0].spawners[0].waves[0].entries[0].count","reward.money"})
    void nonNumericDungeonFamiliesRemainErrorsWithoutRewritingYaml(String path) throws Exception {
        var file=file("dungeons");var yaml=read(file);set(yaml,path,"PRIVATE_VALUE");yaml.save(file.toFile());
        byte[] original=Files.readAllBytes(file);console.clear();store.reload();
        assertFalse(store.dungeons().get("ejemplo").enabled());assertArrayEquals(original,Files.readAllBytes(file));
        assertEquals(1,console.size());assertTrue(console.stream().noneMatch(w->w.contains("PRIVATE_VALUE")));
    }
    @Test void largeIntegersClampBeforeCodecRepresentabilityCheck() throws Exception {
        var file=file("dungeons");var yaml=read(file);yaml.set("cooldown-seconds",new java.math.BigInteger("999999999999999999999999999999999"));
        yaml.save(file.toFile());byte[] original=Files.readAllBytes(file);store.reload();
        assertTrue(store.dungeons().get("ejemplo").enabled(),console::toString);
        assertEquals(604800,store.dungeons().get("ejemplo").cooldownSeconds());assertArrayEquals(original,Files.readAllBytes(file));
    }
    @Test void equipmentAndEnchantmentsLoadClampAndSaveRejectInMobAndPhase() throws Exception {
        dev.dasan.customdungeons.ability.impl.PaperApiTestBootstrap.initialize();
        var access=org.mockito.Mockito.mock(io.papermc.paper.registry.RegistryAccess.class);
        org.mockito.Mockito.when(access.getRegistry(io.papermc.paper.registry.RegistryKey.ENCHANTMENT)).thenReturn(org.bukkit.Registry.ENCHANTMENT);
        try(var api=org.mockito.Mockito.mockStatic(io.papermc.paper.registry.RegistryAccess.class)) {
            api.when(io.papermc.paper.registry.RegistryAccess::registryAccess).thenReturn(access);
            Class.forName("org.bukkit.enchantments.Enchantment");
        }
        enchant=org.bukkit.Registry.ENCHANTMENT.get(org.bukkit.NamespacedKey.minecraft("sharpness"));
        org.mockito.Mockito.when(enchant.getKey()).thenReturn(org.bukkit.NamespacedKey.minecraft("sharpness"));
        org.bukkit.configuration.serialization.ConfigurationSerialization.registerClass(RangeItem.class,"NumericCompatibilityItem");
        try {
            for(String context:List.of("equipment","phases[0].equipment")) {
                for(boolean levels:List.of(false,true)) {
                    var file=file("mobs");var yaml=read(file);
                    set(yaml,context,new LinkedHashMap<>(Map.of("HAND",new LinkedHashMap<>(Map.of("item",new RangeItem(levels?256:1),"drop-chance",levels?.5:2)))));
                    yaml.save(file.toFile());byte[] original=Files.readAllBytes(file);console.clear();
                    assertThrows(CompletionException.class,()->store.save(codec.decodeMob("zombie",yaml)).join());
                    store.reload();var loaded=store.mobs().get("zombie");assertNotNull(loaded,console::toString);
                    var equipment=context.equals("equipment")?loaded.equipment():loaded.phases().getFirst().equipment();
                    var item=equipment.get(org.bukkit.inventory.EquipmentSlot.HAND);
                    assertEquals(levels?255:1,item.item().getEnchantments().get(enchant));assertEquals(levels?.5:1,item.dropChance());
                    String path=context+".HAND."+(levels?"enchantments.minecraft:sharpness":"drop-chance");
                    assertTrue(store.loadWarnings("mobs","zombie").stream().anyMatch(w->w.path().equals(path)),console::toString);
                    assertEquals(1,console.size());assertArrayEquals(original,Files.readAllBytes(file));
                    // Reset before exercising the other family; the disk deliberately still holds the invalid value.
                    store.save(loaded).join();
                }
            }
        } finally {org.bukkit.configuration.serialization.ConfigurationSerialization.unregisterClass(RangeItem.class);}
    }
    static org.bukkit.enchantments.Enchantment enchant;
    @org.bukkit.configuration.serialization.SerializableAs("NumericCompatibilityItem")
    public static final class RangeItem extends org.bukkit.inventory.ItemStack {
        private int level;
        RangeItem(int level) {super();this.level=level;}
        public static RangeItem deserialize(Map<String,Object> values) {return new RangeItem(((Number)values.get("level")).intValue());}
        @Override public Map<String,Object> serialize() {return Map.of("level",level);}
        @Override public boolean hasItemMeta() {return true;}
        @Override public org.bukkit.inventory.meta.ItemMeta getItemMeta() {return org.mockito.Mockito.mock(org.bukkit.inventory.meta.ItemMeta.class,org.mockito.Mockito.RETURNS_DEEP_STUBS);}
        @Override public Map<org.bukkit.enchantments.Enchantment,Integer> getEnchantments() {return level>0?Map.of(enchant,level):Map.of();}
        @Override public int removeEnchantment(org.bukkit.enchantments.Enchantment key) {int old=level;level=0;return old;}
        @Override public void addUnsafeEnchantment(org.bukkit.enchantments.Enchantment key,int value) {level=value;}
        @Override public org.bukkit.inventory.ItemStack clone() {return new RangeItem(level);}
    }
    Path file(String kind) {return directory.resolve(kind+"/"+(kind.equals("mobs")?"zombie":"ejemplo")+".yml");}
    static YamlConfiguration read(Path file) throws Exception {var yaml=new YamlConfiguration();yaml.load(file.toFile());return yaml;}
    YamlConfiguration readModel(String kind) throws Exception {return DefinitionCodecTest.yaml(kind.equals("mobs")?codec.encode(store.mobs().get("zombie")):codec.encode(store.dungeons().get("ejemplo")));}
    static Object get(YamlConfiguration yaml,String path) {return navigate(yaml.getValues(false),path,null,false);}
    static void set(YamlConfiguration yaml,String path,Object value) {
        var map=mutable(yaml.getValues(false));navigate(map,path,value,true);map.forEach(yaml::set);
    }
    @SuppressWarnings("unchecked") static Object navigate(Object root,String path,Object value,boolean set) {
        String[] parts=path.replace("[",".").replace("]","").split("\\.");Object node=root;
        for(int i=0;i<parts.length;i++) {
            String p=parts[i];boolean last=i==parts.length-1;
            if(node instanceof org.bukkit.configuration.ConfigurationSection section) node=section.getValues(false);
            if(node instanceof Map<?,?> map) {if(last&&set) ((Map<String,Object>)map).put(p,value);node=map.get(p);}
            else node=((List<?>)node).get(Integer.parseInt(p));
        }
        return node;
    }
    static Object copy(Object value) {
        if(value instanceof org.bukkit.configuration.ConfigurationSection s) return mutable(s.getValues(false));
        if(value instanceof Map<?,?> m) return mutable(m);
        if(value instanceof List<?> l) return new ArrayList<>(l.stream().map(NumericLoadCompatibilityTest::copy).toList());
        return value;
    }
    static Map<String,Object> mutable(Map<?,?> map) {var out=new LinkedHashMap<String,Object>();map.forEach((k,v)->out.put(k.toString(),copy(v)));return out;}
}
