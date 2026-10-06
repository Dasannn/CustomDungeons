package dev.dasan.customdungeons.gui.wizard;

import dev.dasan.customdungeons.config.*;
import dev.dasan.customdungeons.model.*;
import java.util.*;

/** Step validity delegates definition rules to the existing Validator. */
public final class WizardRules {
    private WizardRules() {}
    public static List<ValidationError> errors(int step,DungeonDef d,Map<String,MobTemplate> mobs,Map<String,SpawnerPreset> presets) {
        var all=new Validator().validate(d,mobs,presets);
        return switch(step) {
            case 0 -> d.area()==null?List.of(new ValidationError("area","wizard.need-area",Map.of())):List.of();
            case 1 -> all.stream().filter(e->e.path().equals("lobby") || e.path().equals("exit")).toList();
            // An empty library is allowed: the room picker also offers local spawners.
            case 2 -> {
                var errors=new ArrayList<>(all.stream().filter(e->e.path().startsWith("spawner-presets")).toList());
                for(String id:d.spawnerPresets()) if(presets.containsKey(id)) errors.addAll(new Validator().validate(presets.get(id),mobs));
                yield List.copyOf(errors);
            }
            case 3 -> all.stream().filter(e->e.path().equals("rooms") || e.path().startsWith("rooms[")).toList();
            case 4 -> all.stream().filter(e->Set.of("min-players","max-players","lives").contains(e.path())).toList();
            case 5 -> rewardErrors(d.reward()); // Empty is a valid, intentionally skippable default.
            case 6 -> {
                var errors=new ArrayList<>(all);errors.addAll(rewardErrors(d.reward()));
                if(d.area()==null) errors.add(new ValidationError("area","wizard.need-area",Map.of()));
                yield List.copyOf(errors);
            }
            default -> throw new IllegalArgumentException("Invalid wizard step");
        };
    }
    private static List<ValidationError> rewardErrors(RewardDef reward) {
        return reward==null || !Double.isFinite(reward.money()) || reward.money()<0 || reward.xp()<0
                ?List.of(new ValidationError("reward","wizard.reward-invalid",Map.of())):List.of();
    }
}
