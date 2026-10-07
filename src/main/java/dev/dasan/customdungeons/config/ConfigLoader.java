package dev.dasan.customdungeons.config;

import dev.dasan.customdungeons.model.ScalingDef;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.logging.Logger;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;

/** Strict types and bounds; warnings contain paths only, never configuration values. */
public final class ConfigLoader {
    private final Consumer<String> warning;
    private final Predicate<Material> doorMaterialAllowed;
    private static final YamlConfiguration DEFAULTS = defaults();
    private static final EntityHeights DEFAULT_ENTITY_HEIGHTS = parseHeights(
            DEFAULTS.getConfigurationSection("entity-heights"),Map.of(),path -> {});
    public ConfigLoader() { this(Logger.getLogger("CustomDungeons")::warning); }
    public ConfigLoader(Consumer<String> warning) { this(warning, material->material.isBlock() && !material.isAir()); }
    /** Injectable API boundary keeps config parsing testable without live Paper registries. */
    public ConfigLoader(Consumer<String> warning, Predicate<Material> doorMaterialAllowed) {
        this.warning = Objects.requireNonNull(warning); this.doorMaterialAllowed = Objects.requireNonNull(doorMaterialAllowed);
    }

    public PluginConfig load(YamlConfiguration y) {
        int min = integer(y,"dungeon-defaults.min-players",1,Integer.MAX_VALUE);
        int max = integer(y,"dungeon-defaults.max-players",0,Integer.MAX_VALUE);
        if (max != 0 && max < min) { warn("dungeon-defaults.max-players"); max = 0; }
        String material = text(y,"door-material",false);
        Material door = Material.matchMaterial(material);
        if (door == null || !doorMaterialAllowed.test(door)) { warn("door-material"); door = Material.IRON_BLOCK; }
        var armor = new HashSet<EntityType>();
        List<String> armorNames = strings(y,"armor-capable-mobs");
        boolean badArmor = false;
        for (String name : armorNames) {
            try {
                EntityType type = EntityType.valueOf(name.replace("minecraft:","").toUpperCase(Locale.ROOT));
                if (!type.isAlive() || !type.isSpawnable()) { badArmor = true; break; }
                armor.add(type);
            } catch (IllegalArgumentException e) { badArmor = true; break; }
        }
        if (badArmor) {
            warn("armor-capable-mobs"); armor.clear();
            DEFAULTS.getStringList("armor-capable-mobs").forEach(n->armor.add(EntityType.valueOf(n.toUpperCase(Locale.ROOT))));
        }
        List<String> aliases = strings(y,"command-aliases");
        if (aliases.stream().anyMatch(a->!a.matches("[a-z0-9_-]+"))) { warn("command-aliases"); aliases = List.of(); }
        Map<String,Integer> music = new HashMap<>();
        Object rawMusic = y.get("music-lengths");
        if (rawMusic != null && !(rawMusic instanceof List<?>)) warn("music-lengths");
        if (rawMusic instanceof List<?> list) {
            var entries = y.getMapList("music-lengths");
            if (entries.size() != list.size()) warn("music-lengths");
            for (int i=0;i<entries.size();i++) {
                var entry = entries.get(i);
                Object key = entry.get("key"), value = entry.get("ticks");
                if (key instanceof String s && s.matches("[a-z0-9_.-]+:[a-z0-9_./-]+") && value instanceof Number n
                        && Double.isFinite(n.doubleValue()) && n.doubleValue() == n.intValue() && n.intValue() > 0) music.put(s,n.intValue());
                else warn("music-lengths["+i+"]");
            }
        }
        return new PluginConfig(text(y,"prefix",true),choice(y,"language",Set.of("es","en")),
                new PluginConfig.DatabaseSettings(choice(y,"database.type",Set.of("sqlite","mysql")),
                        text(y,"database.host",false),integer(y,"database.port",1,65535),text(y,"database.database",false),
                        text(y,"database.user",true),text(y,"database.password",true),integer(y,"database.pool-size",1,1024)),
                text(y,"dungeon-world.name",false),bool(y,"dungeon-world.auto-create"),
                new PluginConfig.DungeonDefaults(integer(y,"dungeon-defaults.lives",DungeonLimits.MIN_LIVES,DungeonLimits.MAX_LIVES),bool(y,"dungeon-defaults.keep-inventory"),
                        integer(y,"dungeon-defaults.lobby-countdown-seconds",0,Integer.MAX_VALUE),integer(y,"dungeon-defaults.cooldown-seconds",0,Integer.MAX_VALUE),min,max,
                        new ScalingDef(number(y,"dungeon-defaults.scaling.extra-mobs-per-player",0,Double.MAX_VALUE),number(y,"dungeon-defaults.scaling.extra-health-per-player",0,Double.MAX_VALUE))),
                new PluginConfig.PerformanceLimits(integer(y,"performance.max-alive-mobs-per-session",1,Integer.MAX_VALUE),
                        number(y,"performance.particle-density",0,1),number(y,"performance.effect-view-radius",1,Double.MAX_VALUE)),
                armor,aliases,new PluginConfig.GuiSounds(sound(y,"gui-sounds.click"),sound(y,"gui-sounds.open"),sound(y,"gui-sounds.save"),sound(y,"gui-sounds.error")),
                door,integer(y,"live-test.max-seconds",1,Integer.MAX_VALUE),music);
    }
    public EntityHeights loadEntityHeights(YamlConfiguration yaml) {
        if (!yaml.contains("entity-heights")) return DEFAULT_ENTITY_HEIGHTS;
        var section = yaml.getConfigurationSection("entity-heights");
        if (section == null) { warn("entity-heights"); return DEFAULT_ENTITY_HEIGHTS; }
        return parseHeights(section,DEFAULT_ENTITY_HEIGHTS.values(),warning);
    }
    public static EntityHeights defaultEntityHeights() { return DEFAULT_ENTITY_HEIGHTS; }
    private static EntityHeights parseHeights(ConfigurationSection section,Map<EntityType,Double> fallback,Consumer<String> warning) {
        var heights = new EnumMap<EntityType,Double>(EntityType.class);
        heights.putAll(fallback);
        if (section == null) return new EntityHeights(heights);
        for (String key : section.getKeys(false)) {
            String path = "entity-heights."+key;
            EntityType type;
            try { type = EntityType.valueOf(key.toUpperCase(Locale.ROOT).replace("MINECRAFT:","")); }
            catch (IllegalArgumentException unknown) { warning.accept(path); continue; }
            Object value = section.get(key);
            if (!type.isAlive() || !type.isSpawnable() || !(value instanceof Number number)
                    || !Double.isFinite(number.doubleValue()) || number.doubleValue() <= 0) {
                warning.accept(path); continue;
            }
            heights.put(type,number.doubleValue());
        }
        return new EntityHeights(heights);
    }
    private static YamlConfiguration defaults() {
        try (var in = ConfigLoader.class.getResourceAsStream("/config.yml")) {
            Objects.requireNonNull(in,"config.yml");
            var y = new YamlConfiguration(); y.load(new InputStreamReader(in,StandardCharsets.UTF_8)); return y;
        } catch (Exception e) { throw new IllegalStateException("Cannot read bundled config.yml",e); }
    }
    private void warn(String path) { warning.accept(path); }
    private String text(YamlConfiguration y,String path,boolean empty) {
        Object value = y.get(path); String fallback = DEFAULTS.getString(path);
        if (value == null) return fallback;
        if (value instanceof String s && (empty || !s.isBlank())) return s;
        warn(path); return fallback;
    }
    private String choice(YamlConfiguration y,String path,Set<String> choices) {
        String s = text(y,path,false); if (choices.contains(s)) return s;
        warn(path); return DEFAULTS.getString(path);
    }
    private String sound(YamlConfiguration y,String path) {
        String s = text(y,path,false); if (s.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")) return s;
        warn(path); return DEFAULTS.getString(path);
    }
    private boolean bool(YamlConfiguration y,String path) {
        Object value = y.get(path); if (value == null) return DEFAULTS.getBoolean(path);
        if (value instanceof Boolean b) return b; warn(path); return DEFAULTS.getBoolean(path);
    }
    private double number(YamlConfiguration y,String path,double min,double max) {
        Object value = y.get(path); if (value == null) return DEFAULTS.getDouble(path);
        if (value instanceof Number n && Double.isFinite(n.doubleValue()) && n.doubleValue() >= min && n.doubleValue() <= max) return n.doubleValue();
        warn(path); return DEFAULTS.getDouble(path);
    }
    private int integer(YamlConfiguration y,String path,int min,int max) {
        Object value = y.get(path); if (value == null) return DEFAULTS.getInt(path);
        if (value instanceof Number n && Double.isFinite(n.doubleValue()) && n.doubleValue() == Math.rint(n.doubleValue())
                && n.doubleValue() >= min && n.doubleValue() <= max) return n.intValue();
        warn(path); return DEFAULTS.getInt(path);
    }
    private List<String> strings(YamlConfiguration y,String path) {
        Object value = y.get(path); if (value == null) return DEFAULTS.getStringList(path);
        if (value instanceof List<?> list && list.stream().allMatch(String.class::isInstance)) return list.stream().map(String.class::cast).toList();
        warn(path); return DEFAULTS.getStringList(path);
    }
}
