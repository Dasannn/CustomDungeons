package dev.dasan.customdungeons.ability;

import dev.dasan.customdungeons.model.ComboDef;
import dev.dasan.customdungeons.runtime.ActiveMob;
import java.util.*;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.Event;
import org.jspecify.annotations.Nullable;

/** Sequential steps share cast receipts; each delay is paid once, including paused time. */
public final class ComboRunner {
    @FunctionalInterface interface Diagnostic {void rejected(ActiveMob mob,String ability,AbilityEngine.DiscardReason reason);}
    private final AbilityRegistry registry;
    private final Diagnostic diagnostic;
    private final Map<ActiveMob, Set<String>> running = new WeakHashMap<>();
    public ComboRunner(AbilityRegistry registry) {this(registry,(mob,id,reason)->{});}
    ComboRunner(AbilityRegistry registry,Diagnostic diagnostic) {
        this.registry=Objects.requireNonNull(registry);this.diagnostic=diagnostic;
    }
    private void reject(ActiveMob mob,String ability,AbilityEngine.DiscardReason reason) {
        if(dev.dasan.customdungeons.mob.DebugView.active())diagnostic.rejected(mob,ability,reason);
    }
    String runningCombo(ActiveMob mob) {
        var keys=running.get(mob);if(keys==null||keys.isEmpty())return null;
        var key=keys.iterator().next();return key.substring(0,key.lastIndexOf('#'));
    }
    public boolean running(ActiveMob mob, String key) {return running.getOrDefault(mob, Set.of()).contains(key);}
    public void start(ComboDef combo, ActiveMob mob, List<LivingEntity> targets, @Nullable Event cause, String key) {
        start(combo,mob,targets,cause,key,()->{});
    }
    void start(ComboDef combo, ActiveMob mob, List<LivingEntity> targets, @Nullable Event cause, String key,Runnable firstStarted) {
        if (running(mob,key)) return;
        if (combo.steps().size()<2||combo.steps().size()>5) throw new IllegalArgumentException("Combo needs 2-5 steps");
        running.computeIfAbsent(mob,m->new HashSet<>()).add(key);
        schedule(combo,mob,List.copyOf(targets),cause,key,0,firstStarted);
    }
    private void finish(ActiveMob mob,String key) {
        var keys=running.get(mob);
        if(keys!=null){keys.remove(key);if(keys.isEmpty())running.remove(mob);}
    }
    private void schedule(ComboDef combo,ActiveMob mob,List<LivingEntity> targets,Event cause,String key,int index,Runnable firstStarted) {
        var step=combo.steps().get(index);
        class CastStep implements Runnable {
            boolean dispatching,acknowledged,advanced;
            Ability compiled;
            ParamValues parameters;
            double range;
            void advance() {
                if(advanced)return;advanced=true;
                if(index+1<combo.steps().size())schedule(combo,mob,targets,cause,key,index+1,firstStarted);
                else {mob.cooldown(key,mob.session().scheduler().currentTick()+Math.max(0,combo.cooldownTicks()));finish(mob,key);}
            }
            public void run() {
                if(!running(mob,key))return;
                boolean death=combo.trigger()==dev.dasan.customdungeons.model.Trigger.ON_DEATH;
                if(!death&&!AbilityEngine.alive(mob)){finish(mob,key);return;}
                if(!death&&MobMovement.paused(mob)){
                    if(dev.dasan.customdungeons.mob.DebugView.active())reject(mob,step.abilityId(),AbilityEngine.blockedReason(mob));
                    mob.session().scheduler().runLater(1,this);return;
                }
                Ability ability=registry.get(step.abilityId()).orElse(null);
                if(ability==null){reject(mob,step.abilityId(),AbilityEngine.DiscardReason.UNKNOWN_ABILITY);finish(mob,key);return;}
                if(compiled!=ability) {
                    var specs=ability.params();parameters=new ParamValues(step.params(),specs);range=combo.range();
                    for(var spec:specs)if(spec.key().equals("reach")||spec.key().equals("range")||spec.key().equals("length"))range=Math.min(range,parameters.getDouble(spec.key()));
                    compiled=ability;
                }
                var params=parameters;
                double castRange=range;
                boolean optional=death||combo.trigger()==dev.dasan.customdungeons.model.Trigger.ON_SPAWN||step.abilityId().equals("tactical_summon");
                var selected=step.abilityId().equals("tactical_summon")?List.<LivingEntity>of():targets.stream().filter(t->TargetSelector.eligible(mob,t,castRange)).toList();
                if(selected.isEmpty()&&!optional){reject(mob,step.abilityId(),AbilityEngine.DiscardReason.OUT_OF_RANGE);finish(mob,key);return;}
                var context=new AbilityContext(mob,selected,params,mob.session(),cause);
                AbilityCasts.watch(context,castRange,()->(death||AbilityEngine.alive(mob))&&selected.stream().allMatch(t->TargetSelector.eligible(mob,t,castRange)),
                    ()->{if(index==0)firstStarted.run();acknowledged=true;if(!dispatching)advance();},
                    retry->{
                        if(dev.dasan.customdungeons.mob.DebugView.active())reject(mob,step.abilityId(),AbilityEngine.cancellationReason(mob,ability,step.abilityId(),selected,castRange,retry));
                        if(retry&&!death)mob.session().scheduler().runLater(1,this);else finish(mob,key);
                    });
                if(!death&&!AbilityCasts.canBegin(context))return;
                try {
                    dispatching=true;
                    if(ability instanceof dev.dasan.customdungeons.ability.control.ControlAbility
                            ||ability instanceof dev.dasan.customdungeons.ability.combat.CombatAbility
                            ||ability instanceof dev.dasan.customdungeons.ability.zone.ZoneAbility)ability.execute(context);
                    else {AbilityCasts.started(context);ability.execute(context);}
                } catch(RuntimeException failure){AbilityCasts.cancel(context,false);finish(mob,key);throw failure;}
                finally {dispatching=false;}
                if(acknowledged)advance();
            }
        }
        var action=new CastStep();
        if(step.delayTicks()>0)mob.session().scheduler().runLater(step.delayTicks(),action);else action.run();
    }
}
