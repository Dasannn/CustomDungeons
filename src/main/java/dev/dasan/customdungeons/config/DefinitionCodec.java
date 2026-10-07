package dev.dasan.customdungeons.config;

import dev.dasan.customdungeons.model.*;
import java.util.*;
import java.util.function.Function;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.configuration.serialization.ConfigurationSerialization;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/** YAML uses kebab-case record fields; ids come from filenames. ItemStacks remain Bukkit serializables. */
public final class DefinitionCodec {
    public Map<String,Object> encode(DungeonDef value) { return writeDungeonDef(value); }
    public Map<String,Object> encode(MobTemplate value) { return writeMobTemplate(value); }
    public DungeonDef decodeDungeon(String id, ConfigurationSection y) { return readDungeonDef(id, y); }
    public MobTemplate decodeMob(String id, ConfigurationSection y) { return readMobTemplate(id, y); }

    public Map<String,Object> encode(SpawnerPreset value) {
        var out = new LinkedHashMap<String,Object>();
        out.put("name",value.name()); out.put("radius",value.radius());
        out.put("waves",value.waves().stream().map(DefinitionCodec::writeWaveDef).toList()); return out;
    }
    public SpawnerPreset decodeSpawnerPreset(String id, ConfigurationSection y) {
        return new SpawnerPreset(id,string(y,"name",""),number(y,"radius",3),list(y,"waves",DefinitionCodec::readWaveDef));
    }
    private static Map<String,Object> writeSpawnerDef(SpawnerDef value) {
        var out = new LinkedHashMap<String,Object>();
        out.put("id", value.id());
        if (value.presetId() != null) out.put("preset-id",value.presetId());
        out.put("location", (value.location() == null ? null : writePoint(value.location())));
        out.put("radius", value.radius());
        out.put("waves", value.waves().stream().map(DefinitionCodec::writeWaveDef).toList());
        return out;
    }
    private static SpawnerDef readSpawnerDef(ConfigurationSection y) {
        return new SpawnerDef(
                string(y, "id", ""),
                (y.get("location") == null ? null : readPoint(section(y.get("location"), "location"))),
                number(y, "radius", 0),
                list(y, "waves", DefinitionCodec::readWaveDef), y.get("preset-id") == null ? null : string(y,"preset-id",""));
    }
    private static Map<String,Object> writeComboStep(ComboStep value) {
        var out = new LinkedHashMap<String,Object>();
        out.put("ability-id", value.abilityId());
        out.put("params", value.params());
        out.put("delay-ticks", value.delayTicks());
        return out;
    }
    private static ComboStep readComboStep(ConfigurationSection y) {
        return new ComboStep(
                string(y, "ability-id", ""),
                rawMap(y.get("params")),
                integer(y, "delay-ticks", 0));
    }
    private static Map<String,Object> writePoint(Point value) {
        var out = new LinkedHashMap<String,Object>();
        out.put("world", value.world());
        out.put("x", value.x());
        out.put("y", value.y());
        out.put("z", value.z());
        out.put("yaw", value.yaw());
        out.put("pitch", value.pitch());
        return out;
    }
    private static Point readPoint(ConfigurationSection y) {
        return new Point(
                string(y, "world", ""),
                number(y, "x", 0),
                number(y, "y", 0),
                number(y, "z", 0),
                (float) number(y, "yaw", 0),
                (float) number(y, "pitch", 0));
    }
    private static Map<String,Object> writeComboDef(ComboDef value) {
        var out = new LinkedHashMap<String,Object>();
        out.put("id", value.id());
        out.put("trigger", value.trigger().name());
        out.put("trigger-value", value.triggerValue());
        out.put("target", value.target().name());
        out.put("range", value.range());
        out.put("cooldown-ticks", value.cooldownTicks());
        out.put("steps", value.steps().stream().map(DefinitionCodec::writeComboStep).toList());
        return out;
    }
    private static ComboDef readComboDef(ConfigurationSection y) {
        return new ComboDef(
                string(y, "id", ""),
                enumValue(y, "trigger", Trigger.class, Trigger.ON_SPAWN),
                number(y, "trigger-value", 0),
                enumValue(y, "target", TargetMode.class, TargetMode.CURRENT_TARGET),
                number(y, "range", 0),
                integer(y, "cooldown-ticks", 0),
                list(y, "steps", DefinitionCodec::readComboStep));
    }
    private static Map<String,Object> writePotionDef(PotionDef value) {
        var out = new LinkedHashMap<String,Object>();
        out.put("effect-key", value.effectKey());
        out.put("amplifier", value.amplifier());
        out.put("particles", value.particles());
        return out;
    }
    private static PotionDef readPotionDef(ConfigurationSection y) {
        return new PotionDef(
                string(y, "effect-key", ""),
                integer(y, "amplifier", 0),
                bool(y, "particles", true));
    }
    private static Map<String,Object> writeScalingDef(ScalingDef value) {
        var out = new LinkedHashMap<String,Object>();
        out.put("extra-mobs-per-player", value.extraMobsPerPlayer());
        out.put("extra-health-per-player", value.extraHealthPerPlayer());
        return out;
    }
    private static ScalingDef readScalingDef(ConfigurationSection y) {
        return new ScalingDef(
                number(y, "extra-mobs-per-player", 0),
                number(y, "extra-health-per-player", 0));
    }
    private static Map<String,Object> writeWaveDef(WaveDef value) {
        var out = new LinkedHashMap<String,Object>();
        out.put("entries", value.entries().stream().map(DefinitionCodec::writeWaveEntry).toList());
        out.put("mode", value.mode().name());
        out.put("stagger-interval-ticks", value.staggerIntervalTicks());
        out.put("pause-after-ticks", value.pauseAfterTicks());
        return out;
    }
    private static WaveDef readWaveDef(ConfigurationSection y) {
        return new WaveDef(
                list(y, "entries", DefinitionCodec::readWaveEntry),
                enumValue(y, "mode", SpawnMode.class, SpawnMode.SIMULTANEOUS),
                integer(y, "stagger-interval-ticks", 0),
                integer(y, "pause-after-ticks", 0));
    }
    private static Map<String,Object> writeRoomDef(RoomDef value) {
        var out = new LinkedHashMap<String,Object>();
        out.put("id", value.id());
        out.put("region", (value.region() == null ? null : writeRegion(value.region())));
        out.put("checkpoint", (value.checkpoint() == null ? null : writePoint(value.checkpoint())));
        out.put("door", (value.door() == null ? null : writeRegion(value.door())));
        out.put("unlock", value.unlock().name());
        // Legacy modes remain represented by unlock; only the additive puzzle mode needs a new key.
        if (value.openingMode()==RoomDef.OpeningMode.EXTERNAL_KEY) out.put("opening-mode", value.openingMode().name());
        out.put("key-carrier-template-id", value.keyCarrierTemplateId());
        if(value.ambience()!=null && !value.ambience().values().isEmpty()) {
            var ambience=new LinkedHashMap<>(value.ambience().values());
            for(String key:List.of("effects","boss-effects"))if(ambience.containsKey(key))
                ambience.put(key,((List<?>)ambience.get(key)).stream().map(p->writePotionDef((PotionDef)p)).toList());
            out.put("ambience",ambience);
        }
        out.put("spawners", value.spawners().stream().map(DefinitionCodec::writeSpawnerDef).toList());
        return out;
    }
    private static RoomDef readRoomDef(ConfigurationSection y) {
        var unlock = enumValue(y, "unlock", UnlockMode.class, UnlockMode.AUTOMATIC);
        var opening = enumValue(y, "opening-mode", RoomDef.OpeningMode.class,
                unlock == UnlockMode.KEY ? RoomDef.OpeningMode.KEY : RoomDef.OpeningMode.AUTOMATIC);
        return new RoomDef(
                string(y, "id", ""),
                (y.get("region") == null ? null : readRegion(section(y.get("region"), "region"))),
                (y.get("checkpoint") == null ? null : readPoint(section(y.get("checkpoint"), "checkpoint"))),
                (y.get("door") == null ? null : readRegion(section(y.get("door"), "door"))),
                enumValue(y, "unlock", UnlockMode.class, UnlockMode.AUTOMATIC),
                string(y, "key-carrier-template-id", opening == RoomDef.OpeningMode.KEY ? "*" : null),
                list(y, "spawners", DefinitionCodec::readSpawnerDef), opening, readAmbience(y));
    }
    private static RoomAmbience readAmbience(ConfigurationSection y) {
        if(y.get("ambience")==null)return null;
        var section=section(y.get("ambience"),"ambience");
        var fields=new LinkedHashMap<>(section.getValues(false));
        for(String key:List.of("effects","boss-effects"))if(fields.containsKey(key))fields.put(key,AmbienceSettings.readEffects(section,key));
        return fields.isEmpty()?null:new RoomAmbience(fields);
    }
    private static Map<String,Object> writeBlockPos(BlockPos value) {
        var out = new LinkedHashMap<String,Object>();
        out.put("x", value.x());
        out.put("y", value.y());
        out.put("z", value.z());
        return out;
    }
    private static BlockPos readBlockPos(ConfigurationSection y) {
        if (y.get("x") == null || y.get("y") == null || y.get("z") == null) return null;
        return new BlockPos(
                integer(y, "x", 0),
                integer(y, "y", 0),
                integer(y, "z", 0));
    }
    private static Map<String,Object> writeWaveEntry(WaveEntry value) {
        var out = new LinkedHashMap<String,Object>();
        out.put("template-id", value.templateId());
        out.put("count", value.count());
        out.put("delay-ticks", value.delayTicks());
        return out;
    }
    private static WaveEntry readWaveEntry(ConfigurationSection y) {
        return new WaveEntry(
                string(y, "template-id", ""),
                integer(y, "count", 0),
                integer(y, "delay-ticks", 0));
    }
    private static Map<String,Object> writePhaseDef(PhaseDef value) {
        var out = new LinkedHashMap<String,Object>();
        out.put("health-threshold", value.healthThreshold());
        out.put("replace-abilities", value.replaceAbilities());
        out.put("abilities", value.abilities().stream().map(DefinitionCodec::writeAbilityInstance).toList());
        out.put("combos", value.combos().stream().map(DefinitionCodec::writeComboDef).toList());
        out.put("equipment", writeEquipment(value.equipment()));
        out.put("potions", value.potions().stream().map(DefinitionCodec::writePotionDef).toList());
        out.put("heal-percent", value.healPercent());
        out.put("summons", value.summons().stream().map(DefinitionCodec::writeWaveEntry).toList());
        out.put("title", value.title());
        out.put("subtitle", value.subtitle());
        out.put("sound-key", value.soundKey());
        out.put("music-key", value.musicKey());
        out.put("invulnerable-ticks", value.invulnerableTicks());
        if (!value.attributes().values().isEmpty()) out.put("attributes", value.attributes().values());
        return out;
    }
    private static PhaseDef readPhaseDef(ConfigurationSection y) {
        return new PhaseDef(
                number(y, "health-threshold", 0),
                bool(y, "replace-abilities", false),
                list(y, "abilities", DefinitionCodec::readAbilityInstance),
                list(y, "combos", DefinitionCodec::readComboDef),
                equipment(y.get("equipment")),
                list(y, "potions", DefinitionCodec::readPotionDef),
                number(y, "heal-percent", 0),
                list(y, "summons", DefinitionCodec::readWaveEntry),
                string(y, "title", null),
                string(y, "subtitle", null),
                string(y, "sound-key", null),
                string(y, "music-key", null),
                integer(y, "invulnerable-ticks", 0), readAttributes(y));
    }
    private static Map<String,Object> writeRewardDef(RewardDef value) {
        var out = new LinkedHashMap<String,Object>();
        out.put("items", value.items());
        out.put("money", value.money());
        out.put("xp", value.xp());
        out.put("commands", value.commands());
        return out;
    }
    private static RewardDef readRewardDef(ConfigurationSection y) {
        return new RewardDef(
                items(y, "items"),
                number(y, "money", 0),
                integer(y, "xp", 0),
                strings(y, "commands"));
    }
    private static Map<String,Object> writeDungeonDef(DungeonDef value) {
        var out = new LinkedHashMap<String,Object>();
        out.put("display-name", value.displayName());
        out.put("enabled", value.enabled());
        out.put("lobby", (value.lobby() == null ? null : writePoint(value.lobby())));
        out.put("exit", (value.exit() == null ? null : writePoint(value.exit())));
        out.put("min-players", value.minPlayers());
        out.put("max-players", value.maxPlayers());
        out.put("lobby-countdown-seconds", value.lobbyCountdownSeconds());
        out.put("lives", value.lives());
        out.put("keep-inventory", value.keepInventory());
        out.put("disconnect-mode",value.disconnectMode().name());
        out.put("time-limit-seconds", value.timeLimitSeconds());
        out.put("cooldown-seconds", value.cooldownSeconds());
        out.put("require-permission", value.requirePermission());
        out.put("scaling", (value.scaling() == null ? null : writeScalingDef(value.scaling())));
        out.put("hooks", writeHooks(value.hooks()));
        out.put("reward", (value.reward() == null ? null : writeRewardDef(value.reward())));
        out.put("spawner-presets",value.spawnerPresets());
        out.put("area",value.area()==null?null:writeRegion(value.area()));
        out.put("start-mode",value.startMode().name());
        out.put("plates",value.plates().stream().map(DefinitionCodec::writePoint).toList());
        out.put("plate-countdown-seconds",value.plateCountdownSeconds());
        out.put("entrance-door",value.entranceDoor()==null?null:writeRegion(value.entranceDoor()));
        out.put("teleport-on-start",value.teleportOnStart()); out.put("finish-mode",value.finishMode().name());
        out.put("exit-grace-seconds",value.exitGraceSeconds());
        out.put("finish-destination",value.finishDestination().name());
        out.put("exit-plates",value.exitPlates().stream().map(DefinitionCodec::writePoint).toList());
        out.put("intro-cinematic",value.introCinematic()); out.put("intro-seconds",value.introSeconds());
        out.put("rooms", value.rooms().stream().map(DefinitionCodec::writeRoomDef).toList());
        return out;
    }
    private static DungeonDef readDungeonDef(String id, ConfigurationSection y) {
        return new DungeonDef(
                id,
                string(y, "display-name", ""),
                bool(y, "enabled", true),
                (y.get("lobby") == null ? null : readPoint(section(y.get("lobby"), "lobby"))),
                (y.get("exit") == null ? null : readPoint(section(y.get("exit"), "exit"))),
                integer(y, "min-players", 1),
                integer(y, "max-players", 0),
                integer(y, "lobby-countdown-seconds", 30),
                integer(y, "lives", 3),
                bool(y, "keep-inventory", false),
                integer(y, "time-limit-seconds", 0),
                integer(y, "cooldown-seconds", 0),
                bool(y, "require-permission", false),
                (y.get("scaling") == null ? new ScalingDef(.25, .15) : readScalingDef(section(y.get("scaling"), "scaling"))),
                hooks(y.get("hooks")),
                (y.get("reward") == null ? new RewardDef(List.of(), 0, 0, List.of()) : readRewardDef(section(y.get("reward"), "reward"))),
                list(y, "rooms", DefinitionCodec::readRoomDef), strings(y,"spawner-presets"),
                y.get("area")==null?null:readRegion(section(y.get("area"),"area")),
                enumValue(y,"start-mode",StartMode.class,StartMode.AUTO),list(y,"plates",DefinitionCodec::readPoint),
                integer(y,"plate-countdown-seconds",3),
                y.get("entrance-door")==null?null:readRegion(section(y.get("entrance-door"),"entrance-door")),
                bool(y,"teleport-on-start",true),
                bool(y,"intro-cinematic",false),integer(y,"intro-seconds",10),
                enumValue(y,"finish-mode",FinishMode.class,bool(y,"teleport-on-finish",bool(y,"teleportOnFinish",true))?FinishMode.IMMEDIATE:FinishMode.NONE),
                integer(y,"exit-grace-seconds",60),enumValue(y,"finish-destination",FinishDestination.class,FinishDestination.EXIT),
                list(y,"exit-plates",DefinitionCodec::readPoint),
                enumValue(y,"disconnect-mode",DisconnectMode.class,DisconnectMode.DIE_AND_DROP));
    }
    private static Map<String,Object> writeRegion(Region value) {
        var out = new LinkedHashMap<String,Object>();
        out.put("world", value.world());
        out.put("min", (value.min() == null ? null : writeBlockPos(value.min())));
        out.put("max", (value.max() == null ? null : writeBlockPos(value.max())));
        return out;
    }
    private static Region readRegion(ConfigurationSection y) {
        BlockPos min = y.get("min") == null ? null : readBlockPos(section(y.get("min"), "min"));
        BlockPos max = y.get("max") == null ? null : readBlockPos(section(y.get("max"), "max"));
        if (min == null || max == null) return null;
        return new Region(string(y, "world", ""), min, max);
    }
    private static Map<String,Object> writeMobTemplate(MobTemplate value) {
        var out = new LinkedHashMap<String,Object>();
        out.put("entity-type", value.entityType());
        out.put("display-name", value.displayName());
        out.put("max-health", value.maxHealth());
        out.put("damage", value.damage());
        out.put("speed", value.speed());
        out.put("knockback-resistance", value.knockbackResistance());
        out.put("scale", value.scale());
        out.put("equipment", writeEquipment(value.equipment()));
        out.put("potions", value.potions().stream().map(DefinitionCodec::writePotionDef).toList());
        out.put("abilities", value.abilities().stream().map(DefinitionCodec::writeAbilityInstance).toList());
        out.put("combos", value.combos().stream().map(DefinitionCodec::writeComboDef).toList());
        out.put("boss", value.boss());
        out.put("boss-bar-color", value.bossBarColor());
        out.put("music-key", value.musicKey());
        out.put("phases", value.phases().stream().map(DefinitionCodec::writePhaseDef).toList());
        out.put("vanilla-drops", value.vanillaDrops());
        if (!value.attributes().values().isEmpty()) out.put("attributes", value.attributes().values());
        return out;
    }
    private static MobTemplate readMobTemplate(String id, ConfigurationSection y) {
        return new MobTemplate(
                id,
                string(y, "entity-type", "minecraft:zombie"),
                string(y, "display-name", ""),
                number(y, "max-health", 0),
                number(y, "damage", 0),
                number(y, "speed", 0),
                number(y, "knockback-resistance", 0),
                number(y, "scale", 0),
                equipment(y.get("equipment")),
                list(y, "potions", DefinitionCodec::readPotionDef),
                list(y, "abilities", DefinitionCodec::readAbilityInstance),
                list(y, "combos", DefinitionCodec::readComboDef),
                bool(y, "boss", false),
                string(y, "boss-bar-color", "RED"),
                string(y, "music-key", null),
                list(y, "phases", DefinitionCodec::readPhaseDef),
                bool(y, "vanilla-drops", false), readAttributes(y));
    }
    private static MobAttributes readAttributes(ConfigurationSection y) {
        if (y.get("attributes") == null) return MobAttributes.EMPTY;
        var section = section(y.get("attributes"), "attributes");
        var values = new LinkedHashMap<String,Double>();
        for (String key : section.getKeys(false)) {
            if (!MobAttributes.KEYS.contains(key)) throw new IllegalArgumentException("Unknown mob attribute");
            if (section.get(key) != null) values.put(key, number(section, key, 0));
        }
        return new MobAttributes(values);
    }
    private static Map<String,Object> writeAbilityInstance(AbilityInstance value) {
        var out = new LinkedHashMap<String,Object>();
        out.put("ability-id", value.abilityId());
        out.put("trigger", value.trigger().name());
        out.put("trigger-value", value.triggerValue());
        out.put("target", value.target().name());
        out.put("range", value.range());
        out.put("cooldown-ticks", value.cooldownTicks());
        out.put("chance", value.chance());
        out.put("telegraph-ticks", value.telegraphTicks());
        out.put("params", value.params());
        return out;
    }
    private static AbilityInstance readAbilityInstance(ConfigurationSection y) {
        return new AbilityInstance(
                string(y, "ability-id", ""),
                enumValue(y, "trigger", Trigger.class, Trigger.ON_SPAWN),
                number(y, "trigger-value", 0),
                enumValue(y, "target", TargetMode.class, TargetMode.CURRENT_TARGET),
                number(y, "range", 0),
                integer(y, "cooldown-ticks", 0),
                number(y, "chance", 1),
                integer(y, "telegraph-ticks", 0),
                rawMap(y.get("params")));
    }
    private static Map<String,Object> writeEquipmentDef(EquipmentDef value) {
        var out = new LinkedHashMap<String,Object>();
        out.put("item", value.item());
        out.put("drop-chance", value.dropChance());
        return out;
    }
    private static EquipmentDef readEquipmentDef(ConfigurationSection y) {
        return new EquipmentDef(
                item(y.get("item")),
                (float) number(y, "drop-chance", 0));
    }

    private static Map<String,Object> writeEquipment(Map<EquipmentSlot,EquipmentDef> value) {
        var out = new LinkedHashMap<String,Object>(); value.forEach((k,v)->out.put(k.name(),writeEquipmentDef(v))); return out;
    }
    private static Map<String,Object> writeHooks(Map<HookEvent,List<String>> value) {
        var out = new LinkedHashMap<String,Object>(); value.forEach((k,v)->out.put(k.name(),v)); return out;
    }
    private static Map<EquipmentSlot,EquipmentDef> equipment(Object value) {
        var out = new EnumMap<EquipmentSlot,EquipmentDef>(EquipmentSlot.class);
        rawMap(value).forEach((k,v)->out.put(EquipmentSlot.valueOf(k.toUpperCase(Locale.ROOT)),readEquipmentDef(section(v,"equipment."+k)))); return out;
    }
    private static Map<HookEvent,List<String>> hooks(Object value) {
        var out = new EnumMap<HookEvent,List<String>>(HookEvent.class);
        rawMap(value).forEach((k,v)->out.put(HookEvent.valueOf(k.toUpperCase(Locale.ROOT)),stringList(v,"hooks."+k))); return out;
    }
    private static ConfigurationSection section(Object value, String path) {
        if (value instanceof ConfigurationSection s) return s;
        var y = new YamlConfiguration(); return y.createSection(path, rawMap(value));
    }
    private static Map<String,Object> rawMap(Object value) {
        if (value == null) return Map.of();
        if (value instanceof ConfigurationSection s) return s.getValues(false);
        if (value instanceof Map<?,?> m) {
            var out = new LinkedHashMap<String,Object>(); m.forEach((k,v)->out.put(k.toString(),v)); return out;
        }
        throw new IllegalArgumentException("Expected YAML mapping");
    }
    private static <T> List<T> list(ConfigurationSection y,String key,Function<ConfigurationSection,T> decode) {
        Object value = y.get(key); if (value == null) return List.of();
        if (!(value instanceof List<?> values)) throw invalid(y,key);
        var out = new ArrayList<T>();
        for (int i=0;i<values.size();i++) out.add(decode.apply(section(values.get(i),y.getCurrentPath()+"."+key+"["+i+"]")));
        return out;
    }
    private static List<String> strings(ConfigurationSection y,String key) { return stringList(y.get(key),key); }
    private static List<String> stringList(Object value,String path) {
        if (value == null) return List.of();
        if (!(value instanceof List<?> values) || values.stream().anyMatch(v->!(v instanceof String))) throw new IllegalArgumentException(path+": expected string list");
        return values.stream().map(String.class::cast).toList();
    }
    private static List<ItemStack> items(ConfigurationSection y,String key) {
        Object value = y.get(key); if (value == null) return List.of();
        if (!(value instanceof List<?> values)) throw invalid(y,key);
        return values.stream().map(DefinitionCodec::item).toList();
    }
    private static ItemStack item(Object value) {
        if (value instanceof ItemStack item) return item;
        Object decoded = ConfigurationSerialization.deserializeObject(rawMap(value));
        if (decoded instanceof ItemStack item) return item;
        throw new IllegalArgumentException("Expected Bukkit ItemStack");
    }
    private static String string(ConfigurationSection y,String key,String fallback) {
        Object value = y.get(key); if (value == null) return fallback;
        if (value instanceof String s) return s; throw invalid(y,key);
    }
    private static boolean bool(ConfigurationSection y,String key,boolean fallback) {
        Object value = y.get(key); if (value == null) return fallback;
        if (value instanceof Boolean b) return b; throw invalid(y,key);
    }
    private static double number(ConfigurationSection y,String key,double fallback) {
        Object value = y.get(key); if (value == null) return fallback;
        if (value instanceof Number n && Double.isFinite(n.doubleValue())) return n.doubleValue(); throw invalid(y,key);
    }
    private static int integer(ConfigurationSection y,String key,int fallback) {
        double n = number(y,key,fallback);
        if (n != Math.rint(n) || n < Integer.MIN_VALUE || n > Integer.MAX_VALUE) throw invalid(y,key); return (int)n;
    }
    private static <E extends Enum<E>> E enumValue(ConfigurationSection y,String key,Class<E> type,E fallback) {
        String s = string(y,key,null); if (s == null) return fallback;
        try { return Enum.valueOf(type,s.toUpperCase(Locale.ROOT)); } catch (IllegalArgumentException e) { throw invalid(y,key); }
    }
    private static final class DecodeFailure extends IllegalArgumentException {
        private final String path;
        DecodeFailure(String path) { super(path + ": invalid value"); this.path = path; }
    }
    static String errorPath(Exception error) { return error instanceof DecodeFailure failure ? failure.path : "<yaml>"; }
    private static IllegalArgumentException invalid(ConfigurationSection y,String key) {
        return new DecodeFailure(y.getCurrentPath()+"."+key);
    }
}
