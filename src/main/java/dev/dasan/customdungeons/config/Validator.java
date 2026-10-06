package dev.dasan.customdungeons.config;

import dev.dasan.customdungeons.model.*;
import java.util.*;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.EquipmentSlot;

public final class Validator {
    /** Advisory findings are separate from the blocking T01 validation contract. */
    public record Warning(String path, String messageKey, Map<String,String> args) {
        public Warning { args = Map.copyOf(args); }
    }
    public List<Warning> warnings(DungeonDef dungeon, Map<String,MobTemplate> mobs, EntityHeights heights) {
        var warnings = new ArrayList<Warning>();
        for (int r=0; r<dungeon.rooms().size(); r++) {
            RoomDef room=dungeon.rooms().get(r);
            if (room.region()==null) continue;
            // Regions include both selected blocks, consistently with Region.volume().
            double roomHeight=(long)room.region().max().y()-room.region().min().y()+1;
            for (int s=0; s<room.spawners().size(); s++) {
                var waves=room.spawners().get(s).waves();
                for (int w=0; w<waves.size(); w++) {
                    var entries=waves.get(w).entries();
                    for (int e=0; e<entries.size(); e++) {
                        MobTemplate mob=mobs.get(entries.get(e).templateId());
                        if (mob==null || mob.entityType()==null || !validStat(mob.scale(),0,10)) continue;
                        EntityType type;
                        try { type=EntityType.valueOf(mob.entityType().toUpperCase(Locale.ROOT).replace("MINECRAFT:","")); }
                        catch (IllegalArgumentException unknown) { continue; }
                        double height=heights.height(type)*(mob.scale()==0 ? 1 : mob.scale());
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
        var errors = new ArrayList<ValidationError>();
        id(d.id(),"id",errors);
        for(int i=0;i<d.spawnerPresets().size();i++) {
            String preset=d.spawnerPresets().get(i);
            if(!presets.containsKey(preset)) errors.add(new ValidationError("spawner-presets["+i+"]","validation.spawner-preset",Map.of("preset",preset)));
        }
        if (d.minPlayers() < 1) error(errors,"min-players","min-players");
        if (d.maxPlayers() != 0 && d.maxPlayers() < d.minPlayers()) error(errors,"max-players","max-players");
        if (d.lives() < 1) error(errors,"lives","lives");
        required(d.lobby(),"lobby",errors); required(d.exit(),"exit",errors);
        nonEmpty(d.rooms(),"rooms",errors);
        for (int i=0;i<d.rooms().size();i++) {
            RoomDef room = d.rooms().get(i); String path = "rooms["+i+"]";
            required(room.region(),path+".region",errors); required(room.checkpoint(),path+".checkpoint",errors);
            if ((i < d.rooms().size()-1 || room.unlock() == UnlockMode.KEY) && room.door() == null) error(errors,path+".door","door");
            // YAML validation also runs on workers. World inspection belongs to the editor's main thread.
            if (room.door() != null && org.bukkit.Bukkit.getServer() != null && org.bukkit.Bukkit.isPrimaryThread()
                    && containsTileState(room.door())) error(errors,path+".door","door-tile-state");
            nonEmpty(room.spawners(),path+".spawners",errors);
            boolean carrier = "*".equals(room.keyCarrierTemplateId());
            for (int j=0;j<room.spawners().size();j++) {
                SpawnerDef spawner = room.spawners().get(j); String sp = path+".spawners["+j+"]";
                required(spawner.location(),sp+".location",errors);
                List<WaveDef> waves;
                try { waves = SpawnerPresets.waves(spawner,presets); }
                catch (IllegalArgumentException missing) {
                    errors.add(new ValidationError(sp+".preset-id","validation.spawner-preset",Map.of("preset",spawner.presetId()))); continue;
                }
                nonEmpty(waves,sp+".waves",errors);
                for (int k=0;k<waves.size();k++) {
                    WaveDef wave = waves.get(k); String wp = sp+".waves["+k+"]";
                    nonEmpty(wave.entries(),wp+".entries",errors);
                    for (int l=0;l<wave.entries().size();l++) {
                        WaveEntry entry = wave.entries().get(l); String ep = wp+".entries["+l+"]";
                        if (entry.count() < 1) error(errors,ep+".count","count");
                        if (!mobs.containsKey(entry.templateId())) errors.add(new ValidationError(ep+".template-id","validation.template",Map.of("template",entry.templateId())));
                        if (Objects.equals(entry.templateId(),room.keyCarrierTemplateId())) carrier = true;
                    }
                }
            }
            if (room.unlock() == UnlockMode.KEY && (room.keyCarrierTemplateId() == null || !carrier)) error(errors,path+".key-carrier-template-id","key-carrier");
        }
        return List.copyOf(errors);
    }
    public List<ValidationError> validate(SpawnerPreset preset, Map<String,MobTemplate> mobs) {
        var point = new Point("validation",0,0,0,0,0);
        var room = new RoomDef("validation",Region.of("validation",new BlockPos(0,0,0),new BlockPos(0,0,0)),point,
                null,UnlockMode.AUTOMATIC,null,List.of(new SpawnerDef("validation",point,preset.radius(),preset.waves())));
        var d = new DungeonDef(preset.id(),preset.name(),false,point,point,1,0,30,3,false,0,0,false,
                new ScalingDef(0,0),Map.of(),new RewardDef(List.of(),0,0,List.of()),List.of(room));
        var errors = new ArrayList<>(validate(d,mobs));
        if (!Double.isFinite(preset.radius()) || preset.radius()<1 || preset.radius()>64
                || Math.abs(preset.radius()*10-Math.rint(preset.radius()*10))>1e-8) error(errors,"radius","spawner-radius");
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
        stat(m.maxHealth(),"max-health",1,2048,errors);
        stat(m.damage(),"damage",0,1000,errors);
        stat(m.speed(),"speed",0,1,errors);
        stat(m.knockbackResistance(),"knockback-resistance",0,1,errors);
        stat(m.scale(),"scale",0,10,errors);
        boolean armor = entity != null && config.armorCapable().contains(entity);
        equipment(m.equipment(),armor,"equipment",errors);
        abilities(m.abilities(),abilityIds,"abilities",errors); combos(m.combos(),abilityIds,"combos",errors);
        double previous = 1;
        for (int i=0;i<m.phases().size();i++) {
            PhaseDef phase = m.phases().get(i); String path = "phases["+i+"]";
            double threshold = phase.healthThreshold();
            if (!Double.isFinite(threshold) || threshold <= 0 || threshold >= previous || threshold >= 1) error(errors,path+".health-threshold","phase-threshold");
            previous = threshold;
            equipment(phase.equipment(),armor,path+".equipment",errors);
            abilities(phase.abilities(),abilityIds,path+".abilities",errors); combos(phase.combos(),abilityIds,path+".combos",errors);
        }
        return List.copyOf(errors);
    }
    private void equipment(Map<EquipmentSlot,EquipmentDef> equipment,boolean armor,String path,List<ValidationError> errors) {
        for (var entry : equipment.entrySet()) {
            EquipmentSlot slot=entry.getKey();
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
    private void stat(double value,String path,double min,double max,List<ValidationError> errors) {
        if (!validStat(value,min,max)) errors.add(new ValidationError(path,"validation.stat-range",
                Map.of("value",number(value),"min",number(min),"max",number(max))));
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
        } else if (Set.of("max-health","damage","speed","knockback-resistance","scale","id","entity-type").contains(path))
            field=messages.get("validation.field."+path);
        else field=net.kyori.adventure.text.Component.text(path);
        return messages.get("gui.mob.validation-path",net.kyori.adventure.text.minimessage.tag.resolver.Placeholder.component("path",field),
                net.kyori.adventure.text.minimessage.tag.resolver.Placeholder.component("error",messages.get(error.messageKey(),args)));
    }
    private void abilities(List<AbilityInstance> abilities,Set<String> ids,String path,List<ValidationError> errors) {
        for (int i=0;i<abilities.size();i++) ability(abilities.get(i).abilityId(),ids,path+"["+i+"].ability-id",errors);
    }
    private void combos(List<ComboDef> combos,Set<String> ids,String path,List<ValidationError> errors) {
        for (int i=0;i<combos.size();i++) {
            ComboDef combo = combos.get(i); String cp = path+"["+i+"]";
            if (combo.steps().size() < 2 || combo.steps().size() > 5) error(errors,cp+".steps","combo-size");
            for (int j=0;j<combo.steps().size();j++) ability(combo.steps().get(j).abilityId(),ids,cp+".steps["+j+"].ability-id",errors);
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
