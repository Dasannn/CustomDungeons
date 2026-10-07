package dev.dasan.customdungeons.config;

import dev.dasan.customdungeons.model.*;
import java.util.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import org.bukkit.*;
import org.bukkit.potion.PotionEffectType;
import dev.dasan.customdungeons.ability.impl.PaperApiTestBootstrap;
import static org.junit.jupiter.api.Assertions.*;

class RoomAmbienceTest {
    @BeforeAll static void registry(){PaperApiTestBootstrap.initialize();}
    @TempDir Path directory;
    static final NamespacedKey MISSING_EFFECT=NamespacedKey.minecraft("not_a_real_effect");
    static final NamespacedKey MISSING_SOUND=NamespacedKey.minecraft("not_a_real_sound");
    @Test void unknownPotionSoundAndParticleAreRejectedWhenSaving() {
        try(var effect=PaperApiTestBootstrap.withoutEntry(PotionEffectType.class,MISSING_EFFECT);
            var sound=PaperApiTestBootstrap.withoutEntry(Sound.class,MISSING_SOUND)) {
            var invalid=new RoomAmbience(Map.of("effects",List.of(new PotionDef(MISSING_EFFECT.toString(),0,false)),
                    "entry-sound",MISSING_SOUND.toString(),"particle","NOT_A_PARTICLE"));
            assertEquals(Set.of("effects","entry-sound","particle"),new HashSet<>(AmbienceSettings.errors(invalid)));
            for(String key:List.of("entry-sound","music","door-sound","door-rumble","clear-sound"))
                assertEquals(List.of(key),AmbienceSettings.errors(new RoomAmbience(Map.of(key,MISSING_SOUND.toString()))));
            var dungeon=DefinitionCodecTest.dungeon();
            var edited=SpawnerPresets.withRooms(dungeon,List.of(dungeon.rooms().getFirst().withAmbience(invalid),dungeon.rooms().getLast()));
            var store=new DefinitionStore(directory,new ConfigLoader(p->{},m->true).load(new YamlConfiguration()),Set.of("test"),p->{},Runnable::run);
            try {store.save(DefinitionCodecTest.mob()).join();assertThrows(java.util.concurrent.CompletionException.class,()->store.save(edited).join());}
            finally {assertThrows(java.util.concurrent.CompletionException.class,store::close);}
        }
    }
    @Test void loadingDefaultsOmitsUnknownEntriesButKeepsValidEffectsAndParticles() {
        try(var effect=PaperApiTestBootstrap.withoutEntry(PotionEffectType.class,MISSING_EFFECT);
            var sound=PaperApiTestBootstrap.withoutEntry(Sound.class,MISSING_SOUND)) {
            var yaml=new YamlConfiguration();
            yaml.set("ambience.defaults.effects",List.of(Map.of("effect-key","minecraft:speed"),Map.of("effect-key",MISSING_EFFECT.toString())));
            yaml.set("ambience.defaults.entry-sound",MISSING_SOUND.toString());yaml.set("ambience.defaults.particle","ASH,NOT_A_PARTICLE");
            var warnings=new ArrayList<String>();var loaded=AmbienceSettings.load(yaml,warnings::add).resolve(null,false);
            assertEquals(List.of(new PotionDef("minecraft:speed",0,false)),loaded.effects());
            assertEquals("",loaded.text("entry-sound"));assertEquals("ASH",loaded.text("particle"));assertEquals(3,warnings.size());
        }
    }
    @Test void loadingUnknownRoomEntriesWarnsWithoutDisablingOrRewritingDungeon() throws Exception {
        try(var effect=PaperApiTestBootstrap.withoutEntry(PotionEffectType.class,MISSING_EFFECT);
            var sound=PaperApiTestBootstrap.withoutEntry(Sound.class,MISSING_SOUND)) {
            var warnings=new ArrayList<String>();
            var store=new DefinitionStore(directory,new ConfigLoader(p->{},m->true).load(new YamlConfiguration()),Set.of("test"),warnings::add,Runnable::run);
            try {
                store.save(DefinitionCodecTest.mob()).join();store.save(DefinitionCodecTest.dungeon()).join();
                var file=directory.resolve("dungeons/ejemplo.yml");var yaml=YamlConfiguration.loadConfiguration(file.toFile());
                var rooms=new ArrayList<>(yaml.getMapList("rooms"));var room=new LinkedHashMap<String,Object>();rooms.getFirst().forEach((k,v)->room.put(k.toString(),v));
                room.put("ambience",Map.of("effects",List.of(Map.of("effect-key","minecraft:speed"),Map.of("effect-key",MISSING_EFFECT.toString())),
                        "entry-sound",MISSING_SOUND.toString(),"particle","ASH,NOT_A_PARTICLE"));rooms.set(0,room);yaml.set("rooms",rooms);yaml.save(file.toFile());
                var original=Files.readString(file);store.reload();var loaded=store.dungeons().get("ejemplo");
                assertTrue(loaded.enabled());var ambience=loaded.rooms().getFirst().ambience();
                assertEquals(List.of(new PotionDef("minecraft:speed",0,false)),ambience.values().get("effects"));
                assertEquals("",ambience.values().get("entry-sound"));assertEquals("ASH",ambience.values().get("particle"));
                assertEquals(3,warnings.size());assertTrue(warnings.stream().allMatch(w->w.contains("rooms[0].ambience.") && w.contains("validation.ambience-ignored")));
                assertEquals(original,Files.readString(file));
            } finally {store.close();}
        }
    }
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
    @Test void malformedSettingsAreRejectedAndNumericDefaultsClamp() {
        assertThrows(IllegalArgumentException.class,()->new RoomAmbience(Map.of("density",Double.NaN)));
        assertThrows(IllegalArgumentException.class,()->new RoomAmbience(Map.of("door-shake","yes")));
        var invalid=new RoomAmbience(Map.of("density",1000));
        assertFalse(AmbienceSettings.errors(invalid).isEmpty());
        var yaml=new YamlConfiguration();yaml.set("ambience.defaults.density",1000);
        var warnings=new ArrayList<String>();var defaults=AmbienceSettings.load(yaml,warnings::add);
        assertEquals(32,defaults.resolve(null,false).number("density"));
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
    @Test void numericDefaultsClampBeforeDecodingWithoutMutatingConfiguration() {
        var yaml=new YamlConfiguration();
        yaml.set("ambience.defaults.density",new java.math.BigInteger("999999999999999999999"));
        yaml.set("ambience.defaults.effect-ticks",1);yaml.set("ambience.defaults.title-seconds",100);
        yaml.set("ambience.defaults.effects",List.of(Map.of("effect-key","minecraft:speed","amplifier",999)));
        String original=yaml.saveToString();var warnings=new ArrayList<String>();
        var loaded=AmbienceSettings.load(yaml,warnings::add).resolve(null,false);
        assertEquals(32,loaded.number("density"));assertEquals(20,loaded.number("effect-ticks"));assertEquals(10,loaded.number("title-seconds"));
        assertEquals(List.of(new PotionDef("minecraft:speed",255,false)),loaded.effects());
        assertEquals(4,warnings.size());assertEquals(original,yaml.saveToString());
    }
    @Test void bossDefaultDoesNotLeakIntoOrdinaryRoomsAndSpawnerResolutionPreservesOverrides() {
        var room=DefinitionCodecTest.dungeon().rooms().getFirst().withAmbience(new RoomAmbience(Map.of("entry-title","Test")));
        assertEquals(room.ambience(),SpawnerPresets.resolve(SpawnerPresets.withRooms(DefinitionCodecTest.dungeon(),List.of(room)),Map.of()).rooms().getFirst().ambience());
    }
}
