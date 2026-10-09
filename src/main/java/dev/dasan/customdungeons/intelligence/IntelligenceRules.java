package dev.dasan.customdungeons.intelligence;

import java.util.*;

/** Registrable declarative detectors and responses; all execution passes through the brain. */
public final class IntelligenceRules {
    public enum Response { ITEM_COOLDOWN, ENRAGE, GROUND, CRITICAL_RESISTANCE, PUSH, SHIELD, DAMAGE_RESISTANCE }
    public record Rule(String id,String pattern,int minimumLevel,boolean strong,Response response) {
        public Rule { Objects.requireNonNull(id);if(!id.matches("[a-z0-9_-]{1,48}"))throw new IllegalArgumentException("Invalid rule id");Objects.requireNonNull(pattern);Objects.requireNonNull(response);strong=strong||response==Response.GROUND||response==Response.PUSH||response==Response.ENRAGE;if(minimumLevel<3||minimumLevel>5)throw new IllegalArgumentException("Adaptation minimum must be 3..5"); }
    }
    private final Map<String,Rule> rules=new LinkedHashMap<>();
    public void register(Rule rule) { if(rules.size()>=32||rules.putIfAbsent(rule.id(),rule)!=null)throw new IllegalArgumentException("Duplicate/full intelligence registry"); }
    public List<Rule> all() { return List.copyOf(rules.values()); }
    /** RF-HAB2-05: deterministic disadvantage decision, no navigation or world queries. */
    public static boolean tacticalSummon(int level,double health,int attackers,int allies) {
        return level<2||health<=.5||attackers>allies;
    }
    public static IntelligenceRules defaults() {
        var r=new IntelligenceRules();
        r.register(new Rule("consumables","consumables",3,false,Response.ITEM_COOLDOWN));
        r.register(new Rule("totem","totem",3,true,Response.ENRAGE));
        r.register(new Rule("flight","flight",3,true,Response.GROUND));
        r.register(new Rule("critical","critical",4,false,Response.CRITICAL_RESISTANCE));
        r.register(new Rule("mace","mace",3,true,Response.PUSH));
        r.register(new Rule("pearl","pearl",3,false,Response.ITEM_COOLDOWN));
        r.register(new Rule("shield","shield",3,false,Response.SHIELD));
        r.register(new Rule("damage","damage",4,false,Response.DAMAGE_RESISTANCE));return r;
    }
}
