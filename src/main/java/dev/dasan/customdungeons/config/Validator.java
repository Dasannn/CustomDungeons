package dev.dasan.customdungeons.config;

import dev.dasan.customdungeons.model.*;
import dev.dasan.customdungeons.ability.*;
import java.util.*;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.EquipmentSlot;

public final class Validator {
    private final AbilityRegistry registry;
    private final boolean editorPrecision;
    public Validator() {this(DefaultRegistry.INSTANCE);}
    public Validator(AbilityRegistry registry) {this(registry,true);}
    private Validator(AbilityRegistry registry,boolean editorPrecision) {this.registry=Objects.requireNonNull(registry);this.editorPrecision=editorPrecision;}
    /** Representable YAML decimals remain compatible; integer model fields are enforced by the codec. */
    Validator forLoading() {return new Validator(registry,false);}
    AbilityRegistry registry() {return registry;}
    private static final class DefaultRegistry {
        private static final AbilityRegistry INSTANCE=new AbilityRegistry();
        static {Abilities.registerDefaults(INSTANCE);}
    }
    public List<Warning> warnings(MobTemplate mob) {
        var warnings=new ArrayList<Warning>();
        double scale=mob.attributes().values().getOrDefault("scale",mob.scale());
        if(NumericRanges.SCALE.contains(scale) && scale>NumericRanges.SCALE_WARNING_THRESHOLD)
            warnings.add(new Warning(mob.attributes().values().containsKey("scale") ? "attributes.scale" : "scale","validation.scale-high",Map.of()));
        for(int i=0;i<mob.phases().size();i++) {
            Double override=mob.phases().get(i).attributes().values().get("scale");
            if(override!=null && NumericRanges.SCALE.contains(override) && override>NumericRanges.SCALE_WARNING_THRESHOLD)
                warnings.add(new Warning("phases["+i+"].attributes.scale","validation.scale-high",Map.of()));
        }
        return List.copyOf(warnings);
    }
    /** Advisory findings are separate from the blocking T01 validation contract. */
    public record Warning(String path, String messageKey, Map<String,String> args) {
        public Warning { args = Map.copyOf(args); }
    }
    public List<Warning> warnings(DungeonDef dungeon, Map<String,MobTemplate> mobs, EntityHeights heights) {
        var warnings = new ArrayList<Warning>();
        for (int r=0; r<dungeon.rooms().size(); r++) {
            RoomDef room=dungeon.rooms().get(r);
            if (r==dungeon.rooms().size()-1 && room.unlock()==UnlockMode.KEY)
                warnings.add(new Warning("rooms["+r+"].unlock","validation.final-room-key",Map.of()));
            if (room.region()==null) continue;
            // Regions include both selected blocks, consistently with Region.volume().
            double roomHeight=(long)room.region().max().y()-room.region().min().y()+1;
            for (int s=0; s<room.spawners().size(); s++) {
                var waves=room.spawners().get(s).waves();
                for (int w=0; w<waves.size(); w++) {
                    var entries=waves.get(w).entries();
                    for (int e=0; e<entries.size(); e++) {
                        MobTemplate mob=mobs.get(entries.get(e).templateId());
                        if (mob==null || mob.entityType()==null) continue;
                        double scale=mob.attributes().values().getOrDefault("scale",mob.scale());
                        if(!NumericRanges.SCALE.contains(scale)) continue;
                        EntityType type;
                        try { type=EntityType.valueOf(mob.entityType().toUpperCase(Locale.ROOT).replace("MINECRAFT:","")); }
                        catch (IllegalArgumentException unknown) { continue; }
                        String entryPath="rooms["+r+"].spawners["+s+"].waves["+w+"].entries["+e+"]";
                        for(var warning:warnings(mob)) warnings.add(new Warning(entryPath+"."+warning.path(),warning.messageKey(),warning.args()));
                        double height=heights.scaledHeight(type,mob.attributes().values().containsKey("scale")
                                ? Math.max(NumericRanges.SCALE_ATTRIBUTE_MIN,scale) : scale);
                        if (height>roomHeight) warnings.add(new Warning(
                                "rooms["+r+"].spawners["+s+"].waves["+w+"].entries["+e+"]",
                                "validation.mob-height",Map.of("mob",mob.displayName().isBlank() ? mob.id() : mob.displayName(),
                                        "height",number(height),"room-height",number(roomHeight))));
                    }
                }
            }
        }
        return List.copyOf(warnings);
    }
    /** Retains the T34 convenience API using bundled configuration defaults. */
    public List<Warning> warnings(DungeonDef dungeon, Map<String,MobTemplate> mobs) {
        return warnings(dungeon,mobs,ConfigLoader.defaultEntityHeights());
    }
    public List<ValidationError> validate(DungeonDef d, Map<String,MobTemplate> mobs) {
        return validate(d,mobs,Map.of());
    }
    public List<ValidationError> validate(DungeonDef d, Map<String,MobTemplate> mobs, Map<String,SpawnerPreset> presets) {
        return validate(d,mobs,presets,true);
    }
    /** Allows pure authoring reconciliation without repeating main-thread door inspection. */
    public List<ValidationError> validate(DungeonDef d, Map<String,MobTemplate> mobs, Map<String,SpawnerPreset> presets, boolean inspectDoors) {
        var errors = new ArrayList<ValidationError>();
        id(d.id(),"id",errors);
        if (inside(d.area(),d.exit()) || inside(d.entranceDoor(),d.exit())
                || d.rooms().stream().anyMatch(r->inside(r.region(),d.exit()) || inside(r.door(),d.exit())))
            error(errors,"exit","exit-inside");
        if(d.startMode()==StartMode.PLATES) {
            if(d.plates().isEmpty()) error(errors,"plates","plates-required");
            required(d.entranceDoor(),"entrance-door",errors);
        }
        if(!NumericRanges.dungeon("plate-countdown").contains(d.plateCountdownSeconds()))rangeError(errors,"plate-countdown-seconds","plate-countdown",NumericRanges.dungeon("plate-countdown"));
        if(!NumericRanges.dungeon("intro-seconds").contains(d.introSeconds()))rangeError(errors,"intro-seconds","intro-seconds",NumericRanges.dungeon("intro-seconds"));
        if(!NumericRanges.dungeon("exit-grace").contains(d.exitGraceSeconds()))rangeError(errors,"exit-grace-seconds","exit-grace",NumericRanges.dungeon("exit-grace"));
        var plateBlocks=new HashSet<String>();
        for(int i=0;i<d.plates().size()+d.exitPlates().size();i++) {
            boolean exit=i>=d.plates().size();
            var p=exit?d.exitPlates().get(i-d.plates().size()):d.plates().get(i);String path=(exit?"exit-plates["+(i-d.plates().size()):"plates["+i)+"]";
            if(!Double.isFinite(p.x()) || !Double.isFinite(p.y()) || !Double.isFinite(p.z()) || p.world()==null || p.world().isBlank())error(errors,path,"plate-point");
            if(!plateBlocks.add(p.world()+":"+Math.floor(p.x())+":"+Math.floor(p.y())+":"+Math.floor(p.z())))error(errors,path,"duplicate-plate");
            if(d.area()!=null)within(d.area(),p,path,errors);
        }
        if(d.entranceDoor()!=null && inspectDoors && org.bukkit.Bukkit.getServer()!=null && org.bukkit.Bukkit.isPrimaryThread()
                && containsTileState(d.entranceDoor()))error(errors,"entrance-door","door-tile-state");
        if(d.area()!=null) {
            within(d.area(),d.entranceDoor(),"entrance-door",errors);
            within(d.area(),d.lobby(),"lobby",errors);
            for(int i=0;i<d.rooms().size();i++) {
                var r=d.rooms().get(i); String path="rooms["+i+"]";
                within(d.area(),r.region(),path+".region",errors);
                within(d.area(),r.door(),path+".door",errors);
                within(d.area(),r.checkpoint(),path+".checkpoint",errors);
                for(int j=0;j<r.spawners().size();j++) within(d.area(),r.spawners().get(j).location(),path+".spawners["+j+"].location",errors);
            }
        }
        for(int i=0;i<d.spawnerPresets().size();i++) {
            String preset=d.spawnerPresets().get(i);
            if(!presets.containsKey(preset)) errors.add(new ValidationError("spawner-presets["+i+"]","validation.spawner-preset",Map.of("preset",preset)));
        }
        if (!NumericRanges.dungeon("min").contains(d.minPlayers())) rangeError(errors,"min-players","min-players",NumericRanges.dungeon("min"));
        if (!NumericRanges.dungeon("max").contains(d.maxPlayers()) || (d.maxPlayers() != 0 && d.maxPlayers() < d.minPlayers())) rangeError(errors,"max-players","max-players",NumericRanges.dungeon("max"));
        var lives=NumericRanges.dungeon("lives");
        if (!lives.contains(d.lives())) errors.add(new ValidationError("lives","validation.lives",
                Map.of("min",lives.format(lives.min()),"max",lives.format(lives.max()))));
        numeric(d.lobbyCountdownSeconds(),"lobby-countdown-seconds",NumericRanges.dungeon("countdown"),errors);
        numeric(d.timeLimitSeconds(),"time-limit-seconds",NumericRanges.dungeon("time"),errors);
        numeric(d.cooldownSeconds(),"cooldown-seconds",NumericRanges.dungeon("cooldown"),errors);
        numeric(NumericRanges.percent(d.scaling().extraMobsPerPlayer()),"scaling.extra-mobs-per-player",NumericRanges.dungeon("extra-mobs"),errors);
        numeric(NumericRanges.percent(d.scaling().extraHealthPerPlayer()),"scaling.extra-health-per-player",NumericRanges.dungeon("extra-health"),errors);
        if(d.reward()==null) required(null,"reward",errors);
        else {
            numeric(d.reward().money(),"reward.money",NumericRanges.MONEY,errors);
            numeric(d.reward().xp(),"reward.xp",NumericRanges.XP,errors);
        }
        required(d.lobby(),"lobby",errors); required(d.exit(),"exit",errors);
        nonEmpty(d.rooms(),"rooms",errors);
        for (int i=0;i<d.rooms().size();i++) {
            RoomDef room = d.rooms().get(i); String path = "rooms["+i+"]";
            if(room.ambience()!=null)for(String field:AmbienceSettings.errors(room.ambience()))
                error(errors,path+".ambience."+field,"ambience");
            required(room.region(),path+".region",errors); required(room.checkpoint(),path+".checkpoint",errors);
            if (i < d.rooms().size()-1 && room.door() == null) error(errors,path+".door","door");
            // YAML validation also runs on workers. World inspection belongs to the editor's main thread.
            if (inspectDoors && room.door() != null && org.bukkit.Bukkit.getServer() != null && org.bukkit.Bukkit.isPrimaryThread()
                    && containsTileState(room.door())) error(errors,path+".door","door-tile-state");
            nonEmpty(room.spawners(),path+".spawners",errors);
            boolean carrier = "*".equals(room.keyCarrierTemplateId());
            for (int j=0;j<room.spawners().size();j++) {
                SpawnerDef spawner = room.spawners().get(j); String sp = path+".spawners["+j+"]";
                required(spawner.location(),sp+".location",errors);
                numeric(spawner.radius(),sp+".radius",NumericRanges.SPAWNER_RADIUS,errors);
                List<WaveDef> waves;
                try { waves = SpawnerPresets.waves(spawner,presets); }
                catch (IllegalArgumentException missing) {
                    errors.add(new ValidationError(sp+".preset-id","validation.spawner-preset",Map.of("preset",spawner.presetId()))); continue;
                }
                nonEmpty(waves,sp+".waves",errors);
                for (int k=0;k<waves.size();k++) {
                    WaveDef wave = waves.get(k); String wp = sp+".waves["+k+"]";
                    nonEmpty(wave.entries(),wp+".entries",errors);
                    numeric(wave.pauseAfterTicks()/20.0,wp+".pause-after-ticks",NumericRanges.SECONDS,errors);
                    if(wave.mode()==SpawnMode.STAGGERED || wave.staggerIntervalTicks()!=0) numeric(wave.staggerIntervalTicks()/20.0,wp+".stagger-interval-ticks",NumericRanges.dungeon("interval"),errors);
                    for (int l=0;l<wave.entries().size();l++) {
                        WaveEntry entry = wave.entries().get(l); String ep = wp+".entries["+l+"]";
                        if (!NumericRanges.WAVE_COUNT.contains(entry.count())) rangeError(errors,ep+".count","count",NumericRanges.WAVE_COUNT);
                        numeric(entry.delayTicks()/20.0,ep+".delay-ticks",NumericRanges.SECONDS,errors);
                        if (!mobs.containsKey(entry.templateId())) errors.add(new ValidationError(ep+".template-id","validation.template",Map.of("template",entry.templateId())));
                        if (Objects.equals(entry.templateId(),room.keyCarrierTemplateId())) carrier = true;
                    }
                }
            }
            if (i < d.rooms().size()-1 && room.openingMode() == RoomDef.OpeningMode.KEY && (room.keyCarrierTemplateId() == null || !carrier)) error(errors,path+".key-carrier-template-id","key-carrier");
        }
        return List.copyOf(errors);
    }
    private static boolean inside(Region region,Point point) {
        return region!=null && point!=null && Double.isFinite(point.x()) && Double.isFinite(point.y()) && Double.isFinite(point.z())
                && region.contains(point.world(),(int)Math.floor(point.x()),(int)Math.floor(point.y()),(int)Math.floor(point.z()));
    }
    private void within(Region area,Region region,String path,List<ValidationError> errors) {
        if(region!=null && (!area.contains(region.world(),region.min().x(),region.min().y(),region.min().z())
                || !area.contains(region.world(),region.max().x(),region.max().y(),region.max().z()))) error(errors,path,"outside-area");
    }
    private void within(Region area,Point point,String path,List<ValidationError> errors) {
        if(point!=null && (!Double.isFinite(point.x()) || !Double.isFinite(point.y()) || !Double.isFinite(point.z())
                || (!area.world().equals(point.world()) || point.x()<area.min().x() || point.x()>=area.max().x()+1d
                || point.y()<area.min().y() || point.y()>=area.max().y()+1d || point.z()<area.min().z() || point.z()>=area.max().z()+1d))) error(errors,path,"outside-area");
    }
    public List<ValidationError> validate(SpawnerPreset preset, Map<String,MobTemplate> mobs) {
        var point = new Point("validation",0,0,0,0,0);
        var room = new RoomDef("validation",Region.of("validation",new BlockPos(0,0,0),new BlockPos(0,0,0)),point,
                null,UnlockMode.AUTOMATIC,null,List.of(new SpawnerDef("validation",point,preset.radius(),preset.waves())));
        var d = new DungeonDef(preset.id(),preset.name(),false,point,new Point("validation",2,0,0,0,0),1,0,30,3,false,0,0,false,
                new ScalingDef(0,0),Map.of(),new RewardDef(List.of(),0,0,List.of()),List.of(room));
        var errors = new ArrayList<>(validate(d,mobs));
        if (!(editorPrecision ? NumericRanges.SPAWNER_RADIUS.containsPrecise(preset.radius()) : NumericRanges.SPAWNER_RADIUS.contains(preset.radius()))) rangeError(errors,"radius","spawner-radius",NumericRanges.SPAWNER_RADIUS);
        if (preset.name().isBlank()) error(errors,"name","required");
        return List.copyOf(errors);
    }
    private static boolean containsTileState(Region region) {
        var world=org.bukkit.Bukkit.getWorld(region.world());
        if (world == null) return false;
        for (int x=region.min().x(); x<=region.max().x(); x++)
            for (int y=region.min().y(); y<=region.max().y(); y++)
                for (int z=region.min().z(); z<=region.max().z(); z++)
                    if (world.getBlockAt(x,y,z).getState() instanceof org.bukkit.block.TileState) return true;
        return false;
    }
    public List<ValidationError> validate(MobTemplate m, PluginConfig config, Set<String> abilityIds) {
        var errors = new ArrayList<ValidationError>(); id(m.id(),"id",errors);
        EntityType entity = null;
        try { entity = EntityType.valueOf(m.entityType().replace("minecraft:","").toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException ignored) {}
        if (entity == null || !entity.isAlive() || !entity.isSpawnable()) error(errors,"entity-type","entity-type");
        stat(m.maxHealth(),"max-health",NumericRanges.stat("health"),errors);
        stat(m.damage(),"damage",NumericRanges.stat("damage"),errors);
        stat(m.speed(),"speed",NumericRanges.stat("speed"),errors);
        stat(m.knockbackResistance(),"knockback-resistance",NumericRanges.stat("resistance"),errors);
        stat(m.scale(),"scale",NumericRanges.stat("scale"),errors);
        attributes(m.attributes(), "attributes", errors);
        boolean armor = entity != null && config.armorCapable().contains(entity);
        equipment(m.equipment(),armor,"equipment",errors);
        potions(m.potions(),"potions",errors);
        abilities(m.abilities(),abilityIds,"abilities",errors); combos(m.combos(),abilityIds,"combos",errors);
        double previous = 1;
        for (int i=0;i<m.phases().size();i++) {
            PhaseDef phase = m.phases().get(i); String path = "phases["+i+"]";
            double threshold = phase.healthThreshold();
            if (!Double.isFinite(threshold) || threshold <= 0 || threshold >= previous || threshold >= 1) error(errors,path+".health-threshold","phase-threshold");
            previous = threshold;
            numeric(threshold*100,path+".health-threshold",NumericRanges.mob("threshold"),errors);
            numeric(phase.healPercent(),path+".heal-percent",NumericRanges.mob("heal"),errors);
            numeric(phase.invulnerableTicks(),path+".invulnerable-ticks",NumericRanges.mob("invulnerable-ticks"),errors);
            for(int j=0;j<phase.summons().size();j++) {
                var summon=phase.summons().get(j);
                numeric(summon.count(),path+".summons["+j+"].count",NumericRanges.summonCount(config),errors);
                numeric(summon.delayTicks(),path+".summons["+j+"].delay-ticks",NumericRanges.TICKS,errors);
            }
            attributes(phase.attributes(), path+".attributes", errors);
            potions(phase.potions(),path+".potions",errors);
            equipment(phase.equipment(),armor,path+".equipment",errors);
            abilities(phase.abilities(),abilityIds,path+".abilities",errors); combos(phase.combos(),abilityIds,path+".combos",errors);
        }
        return List.copyOf(errors);
    }
    private void attributes(MobAttributes attributes, String path, List<ValidationError> errors) {
        attributes.values().forEach((key,value) -> stat(value,path+"."+key,NumericRanges.attribute(key),errors));
    }
    private void equipment(Map<EquipmentSlot,EquipmentDef> equipment,boolean armor,String path,List<ValidationError> errors) {
        for (var entry : equipment.entrySet()) {
            EquipmentSlot slot=entry.getKey();
            numeric(entry.getValue().dropChance(),path+"."+slot.name()+".drop-chance",NumericRanges.mob("drop-chance"),errors);
            if(entry.getValue().item().hasItemMeta()) for(var level:entry.getValue().item().getEnchantments().entrySet())
                numeric(level.getValue(),path+"."+slot.name()+".enchantments."+level.getKey().getKey(),NumericRanges.ENCHANTMENT_LEVEL,errors);
            if (!armor && (slot == EquipmentSlot.HEAD || slot == EquipmentSlot.CHEST || slot == EquipmentSlot.LEGS || slot == EquipmentSlot.FEET || slot == EquipmentSlot.BODY)) error(errors,path+"."+slot.name(),"armor");
            String reserved=reservedEquipment(entry.getValue().item());
            if (reserved != null) error(errors,path+"."+slot.name(),"equipment-"+reserved);
        }
    }
    /** Reject reserved markers regardless of the PDC value type. */
    public static String reservedEquipment(org.bukkit.inventory.ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        var data=item.getItemMeta().getPersistentDataContainer();
        if (data.has(dev.dasan.customdungeons.mob.MobKeys.TOOL)) return "tool";
        if (data.has(dev.dasan.customdungeons.mob.MobKeys.KEY_ITEM)) return "key";
        return null;
    }
    public static boolean validStat(double value,double min,double max) {
        return Double.isFinite(value) && (value == 0 || (value >= min && value <= max));
    }
    private static String number(double value) {
        return Double.isFinite(value) ? String.format(Locale.ROOT,"%.2f",value) : Double.toString(value);
    }
    private void stat(double value,String path,NumericRange range,List<ValidationError> errors) {
        if (!range.contains(value)) errors.add(new ValidationError(path,"validation.stat-range",
                Map.of("value",number(value),"min",range.format(range.min()),"max",range.format(range.max()))));
    }
    /** Localize field names and equipment slots while retaining nested phase positions. */
    public static net.kyori.adventure.text.Component describe(ValidationError error,dev.dasan.customdungeons.text.Messages messages) {
        var args=error.args().entrySet().stream().map(e->net.kyori.adventure.text.minimessage.tag.resolver.Placeholder.unparsed(e.getKey(),e.getValue()))
                .toArray(net.kyori.adventure.text.minimessage.tag.resolver.TagResolver[]::new);
        String path=error.path();
        net.kyori.adventure.text.Component field;
        if (path.matches("(phases\\[\\d+\\]\\.)?equipment\\.[A-Z_]+")) {
            String slot=path.substring(path.lastIndexOf('.')+1);
            field=messages.get("validation.equipment-path",net.kyori.adventure.text.minimessage.tag.resolver.Placeholder.component("slot",messages.get("validation.slot."+slot)));
            if(path.startsWith("phases[")) field=messages.get("validation.phase-path",
                    net.kyori.adventure.text.minimessage.tag.resolver.Placeholder.unparsed("index",Integer.toString(Integer.parseInt(path.substring(7,path.indexOf(']')))+1)),
                    net.kyori.adventure.text.minimessage.tag.resolver.Placeholder.component("field",field));
        } else {
            String attribute=path.substring(path.lastIndexOf('.')+1);
            if (MobAttributes.KEYS.contains(attribute) || Set.of("id","entity-type").contains(path)) {
                field=messages.get("validation.field."+attribute);
                if(path.startsWith("phases[")) field=messages.get("validation.phase-path",
                        net.kyori.adventure.text.minimessage.tag.resolver.Placeholder.unparsed("index",Integer.toString(Integer.parseInt(path.substring(7,path.indexOf(']')))+1)),
                        net.kyori.adventure.text.minimessage.tag.resolver.Placeholder.component("field",field));
            } else field=net.kyori.adventure.text.Component.text(path);
        }
        return messages.get("gui.mob.validation-path",net.kyori.adventure.text.minimessage.tag.resolver.Placeholder.component("path",field),
                net.kyori.adventure.text.minimessage.tag.resolver.Placeholder.component("error",messages.get(error.messageKey(),args)));
    }
    private void rangeError(List<ValidationError> errors,String path,String key,NumericRange range) {
        errors.add(new ValidationError(path,"validation."+key,Map.of("min",range.format(range.min()),"max",range.format(range.max()),"decimals",Integer.toString(range.decimals()))));
    }
    private void numeric(double value,String path,NumericRange range,List<ValidationError> errors) {
        numeric(value,path,range,errors,editorPrecision);
    }
    private void numeric(double value,String path,NumericRange range,List<ValidationError> errors,boolean requireIntegerPrecision) {
        if(!range.contains(value) || (requireIntegerPrecision && range.decimals()==0 && value!=Math.rint(value)))
            errors.add(new ValidationError(path,"validation.numeric-range",Map.of("value",number(value),"min",range.format(range.min()),"max",range.format(range.max()))));
    }
    private void potions(List<PotionDef> potions,String path,List<ValidationError> errors) {
        for(int i=0;i<potions.size();i++) numeric((double)potions.get(i).amplifier()+1,path+"["+i+"].level",NumericRanges.POTION_LEVEL,errors);
    }
    private void parameters(String id,Map<String,Object> params,String path,List<ValidationError> errors) {
        registry.get(id).ifPresent(a->a.params().stream().filter(NumericRanges::numeric).forEach(spec->{
            Object value=params.getOrDefault(spec.key(),spec.defaultValue());
            numeric(value instanceof Number n ? n.doubleValue() : Double.NaN,path+".params."+spec.key(),NumericRanges.parameter(spec),errors,true);
        }));
    }
    private void abilities(List<AbilityInstance> abilities,Set<String> ids,String path,List<ValidationError> errors) {
        for (int i=0;i<abilities.size();i++) {
            var a=abilities.get(i);String ap=path+"["+i+"]";
            ability(a.abilityId(),ids,ap+".ability-id",errors);
            numeric(a.triggerValue(),ap+".trigger-value",NumericRanges.common("trigger-value"),errors);
            numeric(a.range(),ap+".range",NumericRanges.common("range"),errors);
            numeric(a.cooldownTicks(),ap+".cooldown-ticks",NumericRanges.common("cooldown"),errors);
            numeric(a.chance(),ap+".chance",NumericRanges.common("chance"),errors);
            numeric(a.telegraphTicks(),ap+".telegraph-ticks",NumericRanges.common("telegraph"),errors);
            parameters(a.abilityId(),a.params(),ap,errors);
        }
    }
    private void combos(List<ComboDef> combos,Set<String> ids,String path,List<ValidationError> errors) {
        for (int i=0;i<combos.size();i++) {
            ComboDef combo = combos.get(i); String cp = path+"["+i+"]";
            numeric(combo.triggerValue(),cp+".trigger-value",NumericRanges.mob("trigger-value"),errors);
            numeric(combo.range(),cp+".range",NumericRanges.mob("range"),errors);
            numeric(combo.cooldownTicks(),cp+".cooldown-ticks",NumericRanges.TICKS,errors);
            if (combo.steps().size() < 2 || combo.steps().size() > 5) error(errors,cp+".steps","combo-size");
            for (int j=0;j<combo.steps().size();j++) {
                var step=combo.steps().get(j);String sp=cp+".steps["+j+"]";
                ability(step.abilityId(),ids,sp+".ability-id",errors);
                numeric(step.delayTicks()/20.0,sp+".delay-ticks",NumericRanges.SECONDS,errors);
                parameters(step.abilityId(),step.params(),sp,errors);
            }
        }
    }
    private void ability(String id,Set<String> ids,String path,List<ValidationError> errors) {
        if (!ids.contains(id)) errors.add(new ValidationError(path,"validation.ability",Map.of("ability",id)));
    }
    private void id(String id,String path,List<ValidationError> errors) {
        if (id == null || !id.matches("[a-z0-9_-]{1,32}")) error(errors,path,"id");
    }
    private void nonEmpty(List<?> list,String path,List<ValidationError> errors) { if (list.isEmpty()) error(errors,path,"non-empty"); }
    private void required(Object value,String path,List<ValidationError> errors) { if (value == null) error(errors,path,"required"); }
    private void error(List<ValidationError> errors,String path,String key) { errors.add(new ValidationError(path,"validation."+key,Map.of())); }
}
