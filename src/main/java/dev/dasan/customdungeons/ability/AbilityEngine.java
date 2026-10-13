package dev.dasan.customdungeons.ability;

import dev.dasan.customdungeons.mob.MobsPlatform;
import dev.dasan.customdungeons.model.AbilityInstance;
import dev.dasan.customdungeons.model.ComboDef;
import dev.dasan.customdungeons.model.Trigger;
import dev.dasan.customdungeons.runtime.ActiveMob;
import java.util.*;
import org.bukkit.Particle;
import org.bukkit.event.Event;
import org.bukkit.event.entity.EntityDamageEvent;
import org.jspecify.annotations.Nullable;

/** Main-thread engine driven exclusively by its owning session ticker. */
public final class AbilityEngine {
    private final AbilityRegistry registry;
    private final Random random;
    private final ComboRunner combos;
    private final MobsPlatform platform;
    public enum DiscardReason {
        COOLDOWN("cooldown"), OUT_OF_RANGE("out-of-range"), CONTROL_OCCUPIED("control-occupied"),
        PAUSED("paused"), WARNING("warning"), STUNNED("stunned"), CONTROLLING("controlling"),
        EFFECT_OCCUPIED("effect-occupied"), SITUATION("situation"), CHANCE("chance"), UNKNOWN_ABILITY("unknown-ability"), CANCELLED("cancelled");
        private final String key;
        DiscardReason(String key) {this.key=key;}
        public String key() {return key;}
    }
    public record Discarded(String abilityId,DiscardReason reason) {}
    // Weak keys allow ended sessions to be collected without a global cleanup task.
    private final Map<ActiveMob, State> states = new WeakHashMap<>();
    private static final class State {
        final long appearedAt;
        final Set<String> once = new HashSet<>();
        final Map<String, Pending> pending = new LinkedHashMap<>();
        String casting; long evaluation = Long.MIN_VALUE;
        Discarded lastDiscarded;
        final Map<String, Long> cadence = new HashMap<>();
        final Map<String, Schedule> schedules = new HashMap<>();
        final Map<AbilityInstance, Long> activatedAt = new IdentityHashMap<>();
        final Map<AbilityInstance, Prepared> prepared = new IdentityHashMap<>();
        final List<KeySlot> abilityKeys=new ArrayList<>(),comboKeys=new ArrayList<>();
        final Set<String> attempted=new HashSet<>();
        long attemptTick=Long.MIN_VALUE;
        State(long appearedAt) {this.appearedAt=appearedAt;}
    }
    private record Pending(AbilityInstance instance, ComboDef combo, Event cause,int index) {}
    private record KeySlot(Object definition,String key) {}
    private static String key(List<KeySlot> slots,Object definition,int index) {
        while(slots.size()<=index)slots.add(null);
        var known=slots.get(index);if(known!=null&&known.definition()==definition)return known.key();
        String id=definition instanceof AbilityInstance ability?ability.abilityId():"combo:"+((ComboDef)definition).id();
        var created=new KeySlot(definition,id+"#"+index);slots.set(index,created);return created.key();
    }
    private record Schedule(AbilityInstance instance,long initial,int warning) {}
    private record Prepared(Ability ability,ParamValues params,double range,int warning) {}
    private Prepared prepare(State state,AbilityInstance instance) {
        var known=state.prepared.get(instance);if(known!=null)return known;
        var ability=registry.get(instance.abilityId()).orElse(null);if(ability==null)return null;
        var specs=ability.params();var params=new ParamValues(instance.params(),specs);double range=instance.range();
        for(var spec:specs)if(spec.key().equals("reach")||spec.key().equals("range")||spec.key().equals("length"))range=Math.min(range,params.getDouble(spec.key()));
        var result=new Prepared(ability,params,range,AbilityCasts.warningTicks(ability,params,instance.telegraphTicks()));
        state.prepared.put(instance,result);return result;
    }
    public AbilityEngine(AbilityRegistry registry, MobsPlatform platform) {
        this(registry, platform, new Random());
    }
    public AbilityEngine(AbilityRegistry registry, MobsPlatform platform, Random random) {
        this.registry = Objects.requireNonNull(registry);
        this.random = Objects.requireNonNull(random);
        this.platform = platform;
        combos = new ComboRunner(registry,(mob,id,reason)-> {
            var state=states.get(mob);if(state!=null)discard(state,id,reason);
        });
        if (platform != null) Effects.configure(platform::limits);
    }
    public Discarded lastDiscarded(ActiveMob mob) {
        var state=states.get(mob);return state==null?null:state.lastDiscarded;
    }
    public String castingAbility(ActiveMob mob) {
        var state=states.get(mob);var pending=state==null?null:state.pending.get(state.casting);
        return pending==null?combos.runningCombo(mob):pendingId(pending);
    }
    private static String pendingId(Pending pending) {
        return pending.instance()==null?"combo:"+pending.combo().id():pending.instance().abilityId();
    }
    private static void discard(State state,String id,DiscardReason reason) {
        if(!dev.dasan.customdungeons.mob.DebugView.active())return;
        // A single receipt, allocated only when the diagnosis changes; never a history.
        var last=state.lastDiscarded;
        if(last==null||!last.abilityId().equals(id)||last.reason()!=reason)state.lastDiscarded=new Discarded(id,reason);
    }
    static DiscardReason blockedReason(ActiveMob mob) {
        var pause=dev.dasan.customdungeons.ability.combat.CombatService.pauseInfo(mob);
        if(pause!=null)return pause.stunned()?DiscardReason.STUNNED:DiscardReason.PAUSED;
        if(mob.entity() instanceof org.bukkit.entity.Ravager r&&r.getStunnedTicks()>0)return DiscardReason.STUNNED;
        if(dev.dasan.customdungeons.ability.control.ControlService.controlling(mob))return DiscardReason.CONTROLLING;
        return AbilityCasts.warning(mob)?DiscardReason.WARNING:DiscardReason.PAUSED;
    }
    private State state(ActiveMob mob,long tick) {
        var state=states.computeIfAbsent(mob,k->new State(tick));
        for(var instance:mob.abilities())state.activatedAt.putIfAbsent(instance,tick);
        return state;
    }
    public void tick(Collection<ActiveMob> mobs, long tick) {
        for (ActiveMob mob : List.copyOf(mobs)) {
            if (!alive(mob)) { states.remove(mob); continue; }
            MobMovement.track(mob);
            dev.dasan.customdungeons.mob.MobFactory.refreshSpeed(mob);
            retry(mob, state(mob,tick), tick);
            dev.dasan.customdungeons.intelligence.IntelligenceService.tick(mob,this,tick);
            if(mob.abilities().isEmpty() && mob.combos().isEmpty())continue;
            fire(Trigger.EVERY_X_SECONDS, mob, null, tick);
            fire(Trigger.PLAYER_IN_RANGE, mob, null, tick);
            fire(Trigger.HEALTH_BELOW, mob, null, tick);
        }
        if(platform!=null && dev.dasan.customdungeons.mob.DebugView.active())
            dev.dasan.customdungeons.mob.DebugView.tick(mobs,this,platform.messages(),tick);
    }
    public void fire(Trigger trigger, ActiveMob mob, @Nullable Event cause, long tick) {
        if (trigger != Trigger.ON_DEATH && !alive(mob)) return;
        MobMovement.track(mob);
        if (trigger.ordinal() > Trigger.PLAYER_IN_RANGE.ordinal()) {
            var brain=dev.dasan.customdungeons.intelligence.IntelligenceService.brain(mob);
            if(brain==null||brain.definition().level()<(trigger==Trigger.SURROUNDED?1:2))return;
        }
        State state = state(mob,tick);
        if(trigger==Trigger.ON_DEATH) {
            // The final event cannot wait for the host's next tick after its owner is removed.
            AbilityCasts.cancelWarnings(mob);state.pending.clear();state.casting=null;state.evaluation=Long.MIN_VALUE;
        }
        // These loops only select; execution follows after both loops, so no snapshot is needed.
        var abilities = mob.abilities();
        for (int i = 0; i < abilities.size(); i++) {
            AbilityInstance instance = abilities.get(i);
            if(instance.trigger()!=trigger)continue;
            String key = key(state.abilityKeys,instance,i);
            if (state.pending.containsKey(key)) continue;
            if (!periodic(trigger) && !mob.ready(key, tick)) {discard(state,instance.abilityId(),DiscardReason.COOLDOWN);continue;}
            if (periodic(trigger) && !periodicDue(mob,state,key,instance,tick,i)) {
                if(dev.dasan.customdungeons.mob.DebugView.active() && !mob.ready(key,tick))discard(state,instance.abilityId(),DiscardReason.COOLDOWN);
                continue;
            }
            if (registry.get(instance.abilityId()).isEmpty()) {discard(state,instance.abilityId(),DiscardReason.UNKNOWN_ABILITY);continue;}
            if (!matches(trigger, instance.triggerValue(), mob, cause, tick, key, state, instance.range())) continue;
            if (!(random.nextDouble() < Math.clamp(instance.chance(), 0, 1))) {
                // A rejected trigger spends its period; an accepted but blocked cast stays pending.
                if(periodic(trigger)) state.cadence.put(key,tick+period(trigger,instance.triggerValue())+state.schedules.get(key).warning());
                discard(state,instance.abilityId(),DiscardReason.CHANCE);
                continue;
            }
            state.pending.put(key, new Pending(instance, null, cause,i));
        }
        var definitions = mob.combos();
        for (int i = 0; i < definitions.size(); i++) {
            ComboDef combo = definitions.get(i);
            if(combo.trigger()!=trigger)continue;
            String key = key(state.comboKeys,combo,i);
            if (state.pending.containsKey(key) || combos.running(mob, key)) continue;
            if (!mob.ready(key, tick)) {discard(state,"combo:"+combo.id(),DiscardReason.COOLDOWN);continue;}
            if (!matches(trigger, combo.triggerValue(), mob, cause, tick, key, state, combo.range())) continue;
            state.pending.put(key, new Pending(null, combo, cause,i));
        }
        if (trigger == Trigger.ON_DEATH) {
            int attempts=state.pending.size();
            while (attempts-- > 0 && !state.pending.isEmpty()) {state.evaluation=Long.MIN_VALUE;state.casting=null;retry(mob,state,tick);}
            state.pending.clear();
        } else retry(mob, state, tick);
    }
    private void retry(ActiveMob mob, State state, long tick) {
        if(state.pending.isEmpty())return;
        boolean death=deathPending(state);
        if (!death && (state.casting != null || state.evaluation==tick || MobMovement.paused(mob))) {
            if(dev.dasan.customdungeons.mob.DebugView.active() && (state.casting!=null || MobMovement.paused(mob)))
                for(var entry:state.pending.entrySet())if(!entry.getKey().equals(state.casting)) {
                discard(state,pendingId(entry.getValue()),blockedReason(mob));break;
            }
            return;
        }
        if(state.attemptTick!=tick){state.attempted.clear();state.attemptTick=tick;}
        for (var entry : List.copyOf(state.pending.entrySet())) {
            String key = entry.getKey();
            // Several triggers share this evaluation. A blocked selection waits for the next tick.
            if(!death && !state.attempted.add(key))continue;
            var pending = entry.getValue();
            if (pending.combo() != null) {
                var combo = pending.combo();
                if (!mob.combos().contains(combo)) {state.pending.remove(key);continue;}
                var targets = TargetSelector.select(mob, combo.target(), combo.range(), random);
                if (targets.isEmpty() && combo.trigger() != Trigger.ON_SPAWN && combo.trigger() != Trigger.ON_DEATH) {
                    discard(state,pendingId(pending),DiscardReason.OUT_OF_RANGE);continue;
                }
                state.evaluation = tick;state.pending.remove(key);
                combos.start(combo, mob, targets, pending.cause(), key,
                        ()->{if(oneShot(combo.trigger()))state.once.add(key);});
                return;
            }
            var instance = pending.instance();
            // A phase replacing an instance invalidates its old pending event.
            int index = pending.index();
            if (index >= mob.abilities().size() || mob.abilities().get(index) != instance) {
                state.pending.remove(key);continue;
            }
            var prepared=prepare(state,instance);
            if(prepared==null){state.pending.remove(key);continue;}
            var ability=prepared.ability();var params=prepared.params();
            double range=prepared.range(),castRange=range;
            var targets = instance.abilityId().equals("tactical_summon") ? List.<org.bukkit.entity.LivingEntity>of()
                    : TargetSelector.selectAbility(mob, instance.abilityId(), instance.target(), range, random);
            boolean optional = instance.abilityId().equals("tactical_summon") || instance.trigger() == Trigger.ON_SPAWN || instance.trigger() == Trigger.ON_DEATH;
            if (targets.isEmpty() && !optional) {
                boolean occupied=dev.dasan.customdungeons.mob.DebugView.active()
                        && ability instanceof dev.dasan.customdungeons.ability.control.ControlAbility
                        && !TargetSelector.select(mob,instance.target()==dev.dasan.customdungeons.model.TargetMode.RANDOM
                                ?dev.dasan.customdungeons.model.TargetMode.ALL_IN_RADIUS:instance.target(),range,random).isEmpty();
                discard(state,instance.abilityId(),occupied?DiscardReason.CONTROL_OCCUPIED:DiscardReason.OUT_OF_RANGE);continue;
            }
            if (!death && ability instanceof dev.dasan.customdungeons.ability.control.ControlAbility
                    && !dev.dasan.customdungeons.ability.control.ControlService.canStart(instance.abilityId(), mob, targets)) {
                discard(state,instance.abilityId(),instance.abilityId().equals("tactical_summon")?DiscardReason.SITUATION:DiscardReason.CONTROL_OCCUPIED);continue;
            }
            if (!death && ability instanceof dev.dasan.customdungeons.ability.combat.CombatAbility
                    && !dev.dasan.customdungeons.ability.combat.CombatService.canStart(instance.abilityId(), mob)) {
                discard(state,instance.abilityId(),DiscardReason.EFFECT_OCCUPIED);continue;
            }
            state.evaluation = tick;state.casting = key;
            if(death)state.pending.remove(key); // Death is a single opportunity, even when an effect cannot start.
            var context = new AbilityContext(mob, targets, params, mob.session(), pending.cause());
            AbilityCasts.watch(context,castRange,
                    () -> (instance.trigger() == Trigger.ON_DEATH || alive(mob))
                        && (targets.isEmpty() ? optional : targets.stream().allMatch(t -> TargetSelector.eligible(mob, t, castRange))),
                    () -> {
                        if (oneShot(instance.trigger())) state.once.add(key);
                        long startedAt=mob.session().scheduler().currentTick();
                        mob.cooldown(key, startedAt + Math.max(0, instance.cooldownTicks()));
                        if(periodic(instance.trigger())) state.cadence.put(key,startedAt+Math.max(period(instance.trigger(),instance.triggerValue()),instance.cooldownTicks()));
                        state.pending.remove(key);state.casting = null;
                    },
                    retry -> {
                        if(dev.dasan.customdungeons.mob.DebugView.active()) {
                            var reason=cancellationReason(mob,ability,instance.abilityId(),targets,castRange,retry);
                            discard(state,instance.abilityId(),reason);
                        }
                        state.casting = null;
                        state.pending.remove(key);
                        if (retry && !death) state.pending.put(key, pending); // Put rejected controls behind older pending casts.
                    });
            Runnable execute = () -> {
                if(instance.trigger()==Trigger.ON_DEATH) {
                    if(!AbilityCasts.valid(context)){AbilityCasts.cancel(context,false);return;}
                } else if(!AbilityCasts.canBegin(context))return;
                try {
                    if (ability instanceof dev.dasan.customdungeons.ability.control.ControlAbility control) control.execute(context, instance.telegraphTicks());
                    else if (ability instanceof dev.dasan.customdungeons.ability.zone.ZoneAbility zone) zone.execute(context, instance.telegraphTicks());
                    else if (ability instanceof dev.dasan.customdungeons.ability.combat.CombatAbility combat) combat.execute(context, instance.telegraphTicks());
                    else {AbilityCasts.started(context);ability.execute(context);}
                } catch (RuntimeException failure) {AbilityCasts.cancel(context, false);throw failure;}
            };
            if (instance.telegraphTicks() > 0 && instance.trigger() != Trigger.ON_DEATH
                    && !(ability instanceof dev.dasan.customdungeons.ability.control.ControlAbility)
                    && !(ability instanceof dev.dasan.customdungeons.ability.zone.ZoneAbility)
                    && !(ability instanceof dev.dasan.customdungeons.ability.combat.CombatAbility)) {
                Telegraph.show(mob, mob.entity().getLocation(), range, instance.telegraphTicks(), Particle.CRIT,
                        execute, () -> AbilityCasts.cancel(context, false));
            } else execute.run();
            return;
        }
    }
    static boolean alive(ActiveMob mob) { return mob.entity().isValid() && !mob.entity().isDead(); }
    static DiscardReason cancellationReason(ActiveMob mob,Ability ability,String id,
            List<org.bukkit.entity.LivingEntity> targets,double range,boolean retry) {
        if(targets.stream().anyMatch(t->!TargetSelector.eligible(mob,t,range)))return DiscardReason.OUT_OF_RANGE;
        if(!retry)return DiscardReason.CANCELLED;
        if(ability instanceof dev.dasan.customdungeons.ability.control.ControlAbility
                && !dev.dasan.customdungeons.ability.control.ControlService.canStart(id,mob,targets))
            return id.equals("tactical_summon")?DiscardReason.SITUATION:DiscardReason.CONTROL_OCCUPIED;
        return blockedReason(mob);
    }
    private static boolean deathPending(State state) {
        for(var pending:state.pending.values())if(pending.instance()!=null && pending.instance().trigger()==Trigger.ON_DEATH || pending.combo()!=null && pending.combo().trigger()==Trigger.ON_DEATH)return true;
        return false;
    }
    private static boolean oneShot(Trigger t) {
        return t == Trigger.HEALTH_BELOW || t == Trigger.ON_SPAWN || t == Trigger.ON_DEATH;
    }
    @SuppressWarnings("deprecation")
    private boolean matches(Trigger trigger, double value, ActiveMob mob, Event cause,
                            long tick, String key, State state,double range) {
        if (oneShot(trigger) && state.once.contains(key)) return false;
        if(trigger==Trigger.SURROUNDED)return TargetSelector.select(mob,dev.dasan.customdungeons.model.TargetMode.ALL_IN_RADIUS,range).size()>=Math.max(2,value);
        if(trigger==Trigger.PLAYER_NEAR_DEATH)return mob.session().players().stream().anyMatch(p->TargetSelector.eligible(mob,p,range)&&p.getHealth()<=Math.max(1,value==0?4:value));
        if(trigger==Trigger.DAMAGE_BURST) {
            var brain=dev.dasan.customdungeons.intelligence.IntelligenceService.brain(mob);if(brain==null)return false;
            int window=brain.definition().value("window")*20;
            double damage=brain.memory().players().stream().flatMap(p->brain.memory().observations(p,tick).stream()).filter(o->tick-o.tick()<=window).mapToDouble(dev.dasan.customdungeons.intelligence.EncounterMemory.Observation::damage).sum();
            return damage>=Math.max(1,value==0?dev.dasan.customdungeons.mob.MobHealth.maximum(mob.entity())*.1:value);
        }
        if (trigger == Trigger.HEALTH_BELOW) {
            double fraction=dev.dasan.customdungeons.mob.MobHealth.fraction(mob.entity());
            if (cause instanceof EntityDamageEvent damage && !damage.isCancelled()
                    && damage.getEntity().equals(mob.entity()))
                fraction=dev.dasan.customdungeons.mob.MobHealth.fractionAfterDamage(mob.entity(),
                        damage);
            return fraction*100<=value;
        }
        if (periodic(trigger) && key.startsWith("combo:")) {
            long period=period(trigger,value);
            long next=state.cadence.computeIfAbsent(key,k->state.appearedAt+Math.floorMod(mob.entity().getUniqueId().hashCode()+key.hashCode(),period));
            if(tick<next)return false;
            state.cadence.put(key,tick+period);
        }
        return true;
    }
    private static boolean periodic(Trigger trigger) {return trigger==Trigger.EVERY_X_SECONDS||trigger==Trigger.PLAYER_IN_RANGE;}
    private static long period(Trigger trigger,double value) {return trigger==Trigger.PLAYER_IN_RANGE?10:Math.max(1,Math.round(value*20));}
    /** Only the ability's identity participates; phases do not reshuffle existing offsets. */
    static long initialOffset(AbilityInstance instance,int occurrence,long period) {
        return Math.floorMod(31L*instance.abilityId().hashCode()+instance.params().hashCode()+31L*occurrence,period);
    }
    private boolean periodicDue(ActiveMob mob,State state,String key,AbilityInstance instance,long tick,int index) {
        var schedule=state.schedules.get(key);
        if(schedule==null||schedule.instance()!=instance) {
            long period=period(instance.trigger(),instance.triggerValue());
            int occurrence=0;
            for(int i=0;i<index;i++)if(mob.abilities().get(i).abilityId().equals(instance.abilityId()))occurrence++;
            long initial=state.activatedAt.get(instance)+Math.floorMod(initialOffset(instance,occurrence,period)+mob.entity().getUniqueId().hashCode(),period);
            var prepared=prepare(state,instance);
            int warning=prepared==null?Math.max(0,instance.telegraphTicks()):prepared.warning();
            schedule=new Schedule(instance,initial,warning);state.schedules.put(key,schedule);
            state.cadence.put(key,initial+warning);
        }
        Long eligible=state.cadence.get(key);
        if(eligible==null){eligible=schedule.initial()+schedule.warning();state.cadence.put(key,eligible);}
        // Prepare the next warning before its start deadline, including the cooldown interval.
        return tick>=eligible-schedule.warning();
    }
}
