package dev.dasan.customdungeons.config;

import dev.dasan.customdungeons.model.*;
import java.util.*;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.EquipmentSlot;

public final class Validator {
    public List<ValidationError> validate(DungeonDef d, Map<String,MobTemplate> mobs) {
        var errors = new ArrayList<ValidationError>();
        id(d.id(),"id",errors);
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
                required(spawner.location(),sp+".location",errors); nonEmpty(spawner.waves(),sp+".waves",errors);
                for (int k=0;k<spawner.waves().size();k++) {
                    WaveDef wave = spawner.waves().get(k); String wp = sp+".waves["+k+"]";
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
        for (EquipmentSlot slot : equipment.keySet())
            if (!armor && (slot == EquipmentSlot.HEAD || slot == EquipmentSlot.CHEST || slot == EquipmentSlot.LEGS || slot == EquipmentSlot.FEET || slot == EquipmentSlot.BODY)) error(errors,path+"."+slot.name(),"armor");
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
