package dev.dasan.customdungeons.config;

import dev.dasan.customdungeons.ability.impl.PaperApiTestBootstrap;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Every shipped definition must retain its data through repeated YAML round trips. */
class ExampleYamlCompatibilityTest {
    static { PaperApiTestBootstrap.initialize(); }

    private final Map<Map<String,Object>,org.bukkit.inventory.ItemStack> items = new java.util.HashMap<>();

    @Test void allExampleDefinitionsRoundTripWithoutChanges() throws Exception {
        var codec = new DefinitionCodec();
        Path root = Path.of("docs/reference/ejemplos");
        int count = 0;
        try (var files = Files.walk(root)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".yml")).sorted().toList()) {
                String kind = file.getParent().getFileName().toString();
                if (!kind.equals("mobs") && !kind.equals("dungeons") && !kind.equals("spawners")) continue;
                String id = file.getFileName().toString().replaceFirst("\\.yml$", "");
                var yaml = fixture(Files.readString(file));
                Object original = decode(codec, kind, id, yaml);
                var encoded = encode(codec, original);
                var roundTrip = new YamlConfiguration();
                copy(encoded).forEach(roundTrip::set);
                var parsed = fixture(roundTrip.saveToString());
                Object restored = decode(codec, kind, id, parsed);
                assertEquals(original, restored, file.toString());
                var stable = new YamlConfiguration();
                copy(encode(codec, restored)).forEach(stable::set);
                assertEquals(roundTrip.saveToString(), stable.saveToString(), file.toString());
                String output = System.getProperty("exampleYaml.output");
                if (output != null) {
                    Path target = Path.of(output).resolve(root.relativize(file));
                    Files.createDirectories(target.getParent());
                    Files.writeString(target, roundTrip.saveToString());
                }
                count++;
            }
        }
        assertTrue(count > 0, "No example definitions checked");
    }

    /** Paper's item factory is unavailable offline; preserve every item YAML field verbatim. */
    @SuppressWarnings("unchecked")
    private YamlConfiguration fixture(String source) {
        var yaml = new YamlConfiguration();
        Map<String,Object> raw = new org.yaml.snakeyaml.Yaml().load(source);
        raw.forEach((key, value) -> yaml.set(key, fixtureValue(value)));
        return yaml;
    }
    @SuppressWarnings("unchecked")
    private Object fixtureValue(Object value) {
        if (value instanceof java.util.List<?> list) return list.stream().map(this::fixtureValue).toList();
        if (value instanceof Map<?,?> map) {
            var raw=(Map<String,Object>)map;
            if ("org.bukkit.inventory.ItemStack".equals(raw.get("==")) || raw.containsKey("type")) {
                return items.computeIfAbsent(raw, key -> {
                    var item=org.mockito.Mockito.mock(org.bukkit.inventory.ItemStack.class);
                    org.mockito.Mockito.when(item.clone()).thenReturn(item);
                    org.mockito.Mockito.when(item.serialize()).thenReturn(key);
                    org.mockito.Mockito.when(item.getType()).thenReturn(org.bukkit.Material.valueOf(key.get("type").toString()));
                    org.mockito.Mockito.when(item.getAmount()).thenReturn(key.get("amount") instanceof Number n ? n.intValue() : 1);
                    return item;
                });
            }
            var result=new java.util.LinkedHashMap<String,Object>();
            raw.forEach((key, item) -> result.put(key, fixtureValue(item)));
            return result;
        }
        return value;
    }

    private static Map<String,Object> copy(Map<String,Object> map) {
        var result = new java.util.TreeMap<String,Object>();
        map.forEach((key, value) -> result.put(key, copyValue(value)));
        return result;
    }
    @SuppressWarnings("unchecked")
    private static Object copyValue(Object value) {
        if (value instanceof org.bukkit.inventory.ItemStack item) return copy(item.serialize());
        if (value instanceof Map<?,?> map) return copy((Map<String,Object>) map);
        if (value instanceof java.util.List<?> list) {
            var result = new java.util.ArrayList<Object>();
            list.forEach(item -> result.add(copyValue(item)));
            return result;
        }
        return value;
    }

    private static Object decode(DefinitionCodec codec, String kind, String id, YamlConfiguration yaml) {
        return switch (kind) {
            case "mobs" -> codec.decodeMob(id, yaml);
            case "dungeons" -> codec.decodeDungeon(id, yaml);
            default -> codec.decodeSpawnerPreset(id, yaml);
        };
    }

    private static Map<String,Object> encode(DefinitionCodec codec, Object definition) {
        return switch (definition) {
            case dev.dasan.customdungeons.model.MobTemplate mob -> codec.encode(mob);
            case dev.dasan.customdungeons.model.DungeonDef dungeon -> codec.encode(dungeon);
            case dev.dasan.customdungeons.model.SpawnerPreset preset -> codec.encode(preset);
            default -> throw new IllegalArgumentException();
        };
    }
}
