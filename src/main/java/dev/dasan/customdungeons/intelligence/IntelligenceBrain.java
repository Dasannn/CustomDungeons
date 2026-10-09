package dev.dasan.customdungeons.intelligence;

import java.util.*;

/** Pure central enforcement of RF-IA-07, including for server-registered rules. */
public final class IntelligenceBrain {
    public record Adaptation(IntelligenceRules.Rule rule,UUID player,String damageType,long announced,long begins,long expires) {}
    private IntelligenceDef definition;
    private final EncounterMemory memory;
    private final IntelligenceRules rules;
    private final List<Adaptation> active=new ArrayList<>();
    private final List<Adaptation> removed=new ArrayList<>();
    private long nextAdaptation;
    public IntelligenceBrain(IntelligenceDef definition,EncounterMemory memory,IntelligenceRules rules) { this.definition=definition;this.memory=memory;this.rules=rules; }
    public EncounterMemory memory() { return memory; }
    public IntelligenceDef definition() { return definition; }
    public List<Adaptation> active() { return List.copyOf(active); }
    public List<Adaptation> drainRemoved() { var result=List.copyOf(removed);removed.clear();return result; }
    public List<Adaptation> evaluate(long tick) {return evaluate(tick,memory.players());}
    public List<Adaptation> evaluate(long tick,Set<UUID> eligible) {
        clock(tick);
        if(definition.level()<3||tick<nextAdaptation||active.size()>=definition.maximum())return List.of();
        for(var rule:rules.all()) {
            if(definition.level()<rule.minimumLevel()||definition.disabled().contains(rule.id())||active.stream().anyMatch(a->a.rule().id().equals(rule.id())))continue;
            for(var player:eligible) {
                int required=Math.max(2,definition.value("repetitions")-(definition.level()==5&&memory.seen(player,rule.pattern())?1:0));
                if(memory.repetitions(player,rule.pattern(),tick,definition.value("window")*20)<required)continue;
                var observations=memory.observations(player,tick).stream().filter(o->o.pattern().equals(rule.pattern())).toList();String type=observations.isEmpty()?"":observations.getLast().damageType();
                long starts=tick+(rule.strong()?15:1);
                var a=new Adaptation(rule,player,type,tick,starts,starts+definition.value("duration")*20L);
                warningDamage=0;active.add(a);memory.remember(player,rule.pattern());
                nextAdaptation=tick+definition.value("cooldown")*20L;return List.of(a);
            }
        }
        return List.of();
    }
    public void damageDuringWarning(double damage,double maxHealth) {
        if(damage<=0)return;
        warningDamage+=damage;
        if(warningDamage>=Math.max(1,maxHealth*.05)) { for(var it=active.iterator();it.hasNext();) {var a=it.next();if(a.rule().strong()&&lastTick<a.begins()){removed.add(a);it.remove();}}warningDamage=0; }
    }
    private double warningDamage;
    private long lastTick;
    public void clock(long tick) { lastTick=tick;for(var it=active.iterator();it.hasNext();){var a=it.next();if(tick>=a.expires()){removed.add(a);it.remove();}}if(active.stream().noneMatch(a->a.rule().strong()&&tick<a.begins()))warningDamage=0; }
    public void changeLevel(IntelligenceDef value,long tick) {
        definition=value;
        int excess=Math.max(0,(int)active.stream().filter(a->value.level()>=a.rule().minimumLevel()).count()-value.maximum());
        for(var it=active.iterator();it.hasNext();) {
            var adaptation=it.next();boolean eligible=value.level()>=adaptation.rule().minimumLevel();
            if(!eligible||excess>0) {if(eligible)excess--;removed.add(adaptation);it.remove();}
        }
        clock(tick);
    }
    public double damageMultiplier(boolean weak,String type,boolean critical) {
        double resistance=0;
        for(var a:active)if(lastTick>=a.begins()) {
            if(a.rule().response()==IntelligenceRules.Response.CRITICAL_RESISTANCE&&critical)resistance+=.3;
            if(a.rule().response()==IntelligenceRules.Response.DAMAGE_RESISTANCE&&a.damageType().equals(type))resistance+=definition.level()==5?.4:.3;
        }
        double bonus=weak?definition.bonus()/100d:0;
        if(!active.isEmpty())bonus+=definition.weakPoint()==IntelligenceDef.WeakPoint.NONE?.1:weak?.15:0;
        return (1-Math.min(.4,resistance))*(1+bonus);
    }
    public void forget(UUID player) {memory.forget(player);withdraw(player);}
    public void withdraw(UUID player) {for(var it=active.iterator();it.hasNext();){var a=it.next();if(a.player().equals(player)){removed.add(a);it.remove();}} }
    public void clear() { removed.addAll(active);active.clear();memory.clear(); }
}
