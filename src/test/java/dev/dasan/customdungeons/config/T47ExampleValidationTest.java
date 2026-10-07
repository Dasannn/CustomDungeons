package dev.dasan.customdungeons.config;

import dev.dasan.customdungeons.ability.*;
import dev.dasan.customdungeons.ability.impl.PaperApiTestBootstrap;
import dev.dasan.customdungeons.model.*;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Pattern;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Validate the shipped content itself, including waves resolved from shared presets. */
class T47ExampleValidationTest {
    @Test void everyPackPassesTheProductionValidatorWithoutWarnings() throws Exception {
        PaperApiTestBootstrap.initialize();
        var registry = new AbilityRegistry();
        Abilities.registerDefaults(registry);
        var validator = new Validator(registry);
        var codec = new DefinitionCodec();
        var loader = new ConfigLoader(s -> {}, m -> true);
        var config = loader.load(new YamlConfiguration());
        var ids = new HashSet<String>();
        registry.all().forEach(a -> ids.add(a.id()));
        for (String id : List.of("templo-placas", "laberinto-enigma", "coloso-abismal")) {
            Path pack = Path.of("docs/reference/ejemplos", id);
            var mobs = new HashMap<String, MobTemplate>();
            try (var files = Files.list(pack.resolve("mobs"))) {
                for (Path file : files.toList()) {
                    var mob = codec.decodeMob(stem(file), read(file));
                    assertEquals(List.of(), validator.validate(mob, config, ids), file.toString());
                    assertEquals(List.of(), validator.warnings(mob), file.toString());
                    mobs.put(mob.id(), mob);
                }
            }
            var presets = new HashMap<String, SpawnerPreset>();
            if (Files.isDirectory(pack.resolve("spawners"))) {
                try (var files = Files.list(pack.resolve("spawners"))) {
                    for (Path file : files.toList()) {
                        var preset = codec.decodeSpawnerPreset(stem(file), read(file));
                        assertEquals(List.of(), validator.validate(preset, mobs), file.toString());
                        presets.put(preset.id(), preset);
                    }
                }
            }
            var dungeon = codec.decodeDungeon(id, read(pack.resolve("dungeons/" + id + ".yml")));
            assertEquals(List.of(), validator.validate(dungeon, mobs, presets), id);
            assertEquals(List.of(), validator.warnings(SpawnerPresets.resolve(dungeon, presets), mobs), id);
            assertEquals(3, dungeon.rooms().size(), id);
            if (id.equals("laberinto-enigma")) {
                assertEquals(2, presets.size());
                for (String preset : presets.keySet())
                    assertEquals(3, SpawnerPresets.usage(preset, Map.of(id, dungeon)).size());
                assertEquals(RoomDef.OpeningMode.EXTERNAL_KEY, dungeon.rooms().get(1).openingMode());
            }
        }
    }
    private static String stem(Path file) { return file.getFileName().toString().replace(".yml", ""); }
    @Test void arrivalAndSpawnPointsHaveFloorAndHeadroomInTheBuildScripts() throws Exception {
        var codec = new DefinitionCodec();
        for (String id : List.of("templo-placas", "laberinto-enigma", "coloso-abismal")) {
            var pack = Path.of("docs/reference/ejemplos", id);
            var dungeon = codec.decodeDungeon(id, read(pack.resolve("dungeons/" + id + ".yml")));
            var lines = Files.readAllLines(pack.resolve("build.mcfunction"));
            var points = new ArrayList<Point>(List.of(dungeon.lobby(), dungeon.exit()));
            points.addAll(dungeon.plates());
            for (var room : dungeon.rooms()) {
                points.add(room.checkpoint());
                room.spawners().forEach(s -> points.add(s.location()));
            }
            for (var point : points) {
                int x = (int)Math.floor(point.x()), y = (int)Math.floor(point.y()), z = (int)Math.floor(point.z());
                assertNotEquals("air", blockAt(lines, x, y - 1, z), id + " floor " + point);
                assertTrue(Set.of("air", "stone_pressure_plate", "polished_blackstone_pressure_plate")
                        .contains(blockAt(lines, x, y, z)), id + " feet " + point);
                assertEquals("air", blockAt(lines, x, y + 1, z), id + " head " + point);
            }
        }
    }
    /** Simulate only fill/setblock geometry, in script order, at one queried block. */
    private static String blockAt(List<String> lines, int x, int y, int z) {
        String block = "air";
        var fill = Pattern.compile(".*run fill (-?\\d+) (-?\\d+) (-?\\d+) (-?\\d+) (-?\\d+) (-?\\d+) (\\w+)(?: (hollow))?");
        var set = Pattern.compile(".*run setblock (-?\\d+) (-?\\d+) (-?\\d+) (\\w+).*");
        for (String line : lines) {
            var m = fill.matcher(line);
            if (m.matches()) {
                int a=Integer.parseInt(m.group(1)), b=Integer.parseInt(m.group(2)), c=Integer.parseInt(m.group(3));
                int d=Integer.parseInt(m.group(4)), e=Integer.parseInt(m.group(5)), f=Integer.parseInt(m.group(6));
                if (x>=a && x<=d && y>=b && y<=e && z>=c && z<=f)
                    block=m.group(8)!=null && x>a && x<d && y>b && y<e && z>c && z<f ? "air" : m.group(7);
            } else {
                m=set.matcher(line);
                if (m.matches() && x==Integer.parseInt(m.group(1)) && y==Integer.parseInt(m.group(2)) && z==Integer.parseInt(m.group(3)))
                    block=m.group(4);
            }
        }
        return block;
    }
    private static YamlConfiguration read(Path file) throws Exception {
        var yaml = new YamlConfiguration();
        yaml.load(file.toFile());
        return yaml;
    }
}
