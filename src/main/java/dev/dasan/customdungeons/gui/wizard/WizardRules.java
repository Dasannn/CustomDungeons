package dev.dasan.customdungeons.gui.wizard;

import dev.dasan.customdungeons.config.*;
import dev.dasan.customdungeons.model.*;
import java.util.*;

/** Step validity delegates definition rules to the existing Validator. */
public final class WizardRules {
    private WizardRules() {}
    public record Result(List<List<ValidationError>> steps,List<Validator.Warning> warnings) {
        public Result {steps=steps.stream().map(List::copyOf).toList();warnings=List.copyOf(warnings);}
        public List<ValidationError> errors(int step) {return steps.get(step);}
    }
    public static Result evaluate(DungeonDef d,Map<String,MobTemplate> mobs,Map<String,SpawnerPreset> presets,boolean inspectDoors) {
        var validator=new Validator();
        var all=validator.validate(d,mobs,presets,inspectDoors);
        var steps=new ArrayList<List<ValidationError>>();
        steps.add(d.area()==null?List.of(new ValidationError("area","wizard.need-area",Map.of())):List.of());
        steps.add(all.stream().filter(e->e.path().equals("lobby") || e.path().equals("exit")).toList());
        // An empty library is allowed: the room picker also offers local spawners.
        var presetErrors=new ArrayList<>(all.stream().filter(e->e.path().startsWith("spawner-presets")).toList());
        for(String id:d.spawnerPresets()) if(presets.containsKey(id)) presetErrors.addAll(validator.validate(presets.get(id),mobs));
        steps.add(presetErrors);
        steps.add(all.stream().filter(e->e.path().equals("rooms") || e.path().startsWith("rooms[")).toList());
        steps.add(all.stream().filter(e->Set.of("min-players","max-players","lives").contains(e.path())).toList());
        steps.add(rewardErrors(d.reward())); // Empty is a valid, intentionally skippable default.
        var review=new ArrayList<>(all);review.addAll(steps.get(5));review.addAll(steps.getFirst());
        steps.add(review);
        return new Result(steps,review.isEmpty()?validator.warnings(SpawnerPresets.resolve(d,presets),mobs):List.of());
    }
    public static List<ValidationError> errors(int step,DungeonDef d,Map<String,MobTemplate> mobs,Map<String,SpawnerPreset> presets) {
        if(step<0 || step>=7) throw new IllegalArgumentException("Invalid wizard step");
        return evaluate(d,mobs,presets,true).errors(step);
    }
    private static List<ValidationError> rewardErrors(RewardDef reward) {
        return reward==null || !Double.isFinite(reward.money()) || reward.money()<0 || reward.xp()<0
                ?List.of(new ValidationError("reward","wizard.reward-invalid",Map.of())):List.of();
    }
}
