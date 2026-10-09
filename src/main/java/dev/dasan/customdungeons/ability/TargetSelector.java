package dev.dasan.customdungeons.ability;

import dev.dasan.customdungeons.model.TargetMode;
import dev.dasan.customdungeons.runtime.ActiveMob;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

/** Selects only living participants; never queries nearby world entities. */
public final class TargetSelector {
    private TargetSelector() {}
    public static List<LivingEntity> select(ActiveMob caster, TargetMode mode, double range) {
        return select(caster, mode, range, ThreadLocalRandom.current());
    }
    static List<LivingEntity> select(ActiveMob caster, TargetMode mode, double range, Random random) {
        if (!Double.isFinite(range) || range < 0) return List.of();
        Location origin = caster.entity().getLocation();
        List<LivingEntity> eligible = new ArrayList<>();
        for (Player player : caster.session().players()) {
            if (eligible(caster, player, range)) eligible.add(player);
        }
        if (eligible.isEmpty()) return List.of();
        return switch (mode) {
            case ALL_IN_RADIUS -> List.copyOf(eligible);
            case CURRENT_TARGET -> eligible.contains(caster.entity().getTarget())
                    ? List.of(caster.entity().getTarget()) : List.of();
            case NEAREST -> List.of(Collections.min(eligible,
                    Comparator.comparingDouble(e -> origin.distanceSquared(e.getLocation()))));
            case MOST_THREAT, WEAKEST, TANKIEST, LEAST_ARMOR, LAST_HEALED, ARCHER, BEHIND, FARTHEST -> intelligent(caster,mode,eligible,origin);
            case RANDOM -> List.of(eligible.get(random.nextInt(eligible.size())));
        };
    }
    /** Control strategies operate on the same eligibility/range set as the configured selector. */
    public static List<LivingEntity> selectAbility(ActiveMob caster,String ability,TargetMode mode,double range) {
        return selectAbility(caster,ability,mode,range,ThreadLocalRandom.current());
    }
    static List<LivingEntity> selectAbility(ActiveMob caster,String ability,TargetMode mode,double range,Random random) {
        var selected=select(caster,mode,range,random);
        if(ability.equals("blink_behind")) {
            var brain=dev.dasan.customdungeons.intelligence.IntelligenceService.brain(caster);
            int level=brain==null?caster.template().intelligence().level():brain.definition().level();
            if(level>=3) {
                var ranged=select(caster,TargetMode.ARCHER,range,random);
                if(!ranged.isEmpty())return ranged;
            }
            return selected;
        }
        if(Set.of("vortex","inverted_gravity","cracked_floor","sweep","falling_pillars","poison_pools","charged_beam","arrow_rain","rift").contains(ability)) {
            var brain=dev.dasan.customdungeons.intelligence.IntelligenceService.brain(caster);
            int level=brain==null?caster.template().intelligence().level():brain.definition().level();
            if(level<3)return selected;
            var players=caster.session().players().stream().filter(p->eligible(caster,p,range)).toList();
            return zoneTargets(caster,players).stream().map(p->(LivingEntity)p).toList();
        }
        if(!Set.of("grab_throw","drain_grab","levitation_cage","roots","anchor_spear","bomb_mark","soul_chain").contains(ability))return selected;
        var brain=dev.dasan.customdungeons.intelligence.IntelligenceService.brain(caster);
        int level=brain==null?caster.template().intelligence().level():brain.definition().level();
        var candidates=caster.session().players().stream().filter(p->eligible(caster,p,range)).map(p->(LivingEntity)p).toList();
        if(candidates.isEmpty())return List.of();
        if(level<3) {
            if(!ability.equals("soul_chain")||selected.isEmpty()||selected.size()>1)return selected;
            var first=selected.getFirst();var second=candidates.stream().filter(p->!p.equals(first)).min(Comparator.comparingDouble(p->p.getLocation().distanceSquared(first.getLocation())));
            return second.map(p->List.of(first,p)).orElse(selected);
        }
        if(ability.equals("soul_chain")) {
            LivingEntity a=candidates.getFirst(),b=null;double far=-1;
            for(int i=0;i<candidates.size();i++)for(int j=i+1;j<candidates.size();j++) {
                double distance=candidates.get(i).getLocation().distanceSquared(candidates.get(j).getLocation());
                if(distance>far){far=distance;a=candidates.get(i);b=candidates.get(j);}
            }
            return b==null?List.of(a):List.of(a,b);
        }
        if(ability.equals("bomb_mark"))return List.of(Collections.max(candidates,Comparator.comparingLong(p->candidates.stream().filter(q->q!=p&&q.getLocation().distanceSquared(p.getLocation())<=9).count())));
        if(Set.of("grab_throw","drain_grab","roots","anchor_spear").contains(ability))return List.of(Collections.max(candidates,Comparator.comparingDouble(p->candidates.stream().filter(q->q!=p).mapToDouble(q->q.getLocation().distanceSquared(p.getLocation())).min().orElse(Double.MAX_VALUE))));
        return selected;
    }
    public static boolean tacticalSummonAllowed(ActiveMob caster) {
        var brain=dev.dasan.customdungeons.intelligence.IntelligenceService.brain(caster);
        int level=brain==null?caster.template().intelligence().level():brain.definition().level();
        int attackers=(int)caster.session().players().stream().filter(p->eligible(caster,p,32)).count();
        int allies=(int)caster.session().mobs().stream().filter(m->m.entity().isValid()&&!m.entity().isDead()).count();
        return dev.dasan.customdungeons.intelligence.IntelligenceRules.tacticalSummon(level,
            dev.dasan.customdungeons.mob.MobHealth.fraction(caster.entity()),attackers,Math.max(1,allies));
    }
    public static Location tacticalSummonAnchor(ActiveMob caster) {
        var brain=dev.dasan.customdungeons.intelligence.IntelligenceService.brain(caster);
        int level=brain==null?caster.template().intelligence().level():brain.definition().level();
        Location origin=caster.entity().getLocation();if(level<2)return origin;
        var back=select(caster,TargetMode.BEHIND,32);if(!back.isEmpty())return back.getFirst().getLocation();
        var ranged=select(caster,TargetMode.ARCHER,32);if(ranged.isEmpty())return origin;
        return origin.clone().add(ranged.getFirst().getLocation().toVector().subtract(origin.toVector()).multiply(.5));
    }
    /** Area targeting keeps the configured selector below level 3. Above it, cluster first. */
    public static List<Player> zoneTargets(ActiveMob caster,List<Player> selected) {
        var brain=dev.dasan.customdungeons.intelligence.IntelligenceService.brain(caster);
        int level=brain==null?caster.template().intelligence().level():brain.definition().level();
        if(level<3)return List.copyOf(selected);
        return selected.stream().sorted(Comparator.comparingLong((Player p)->selected.stream()
                .filter(q->q.getLocation().distanceSquared(p.getLocation())<=16).count()).reversed()).toList();
    }
    /** Ten ticks of horizontal velocity is the fixed half-second prediction. */
    public static Location zonePosition(ActiveMob caster,Player target) {
        var brain=dev.dasan.customdungeons.intelligence.IntelligenceService.brain(caster);
        int level=brain==null?caster.template().intelligence().level():brain.definition().level();
        Location at=target.getLocation().clone();
        if(level>=3) {
            var velocity=target.getVelocity().clone().setY(0).multiply(10);
            if(Double.isFinite(velocity.getX())&&Double.isFinite(velocity.getZ()))at.add(velocity);
        }
        return at;
    }
    private static List<LivingEntity> intelligent(ActiveMob caster,TargetMode mode,List<LivingEntity> players,Location origin) {
        var brain=dev.dasan.customdungeons.intelligence.IntelligenceService.brain(caster);
        if(brain==null||brain.definition().level()<(mode==TargetMode.MOST_THREAT?1:2))return List.of();
        long tick=caster.session().scheduler().currentTick();
        var candidates=players.stream().filter(e->switch(mode) {
            case LAST_HEALED -> brain.memory().observations(e.getUniqueId(),tick).stream().anyMatch(o->o.pattern().equals("heal")||o.pattern().equals("consumables"));
            case ARCHER -> brain.memory().observations(e.getUniqueId(),tick).stream().anyMatch(dev.dasan.customdungeons.intelligence.EncounterMemory.Observation::ranged);
            case BEHIND -> dev.dasan.customdungeons.intelligence.IntelligenceService.behind(caster.entity(),(Player)e);
            default -> true;
        }).toList();
        return candidates.stream().max(Comparator.comparingDouble(e->switch(mode) {
            case MOST_THREAT -> brain.memory().threat(e.getUniqueId(),tick);
            case WEAKEST -> -e.getHealth();case TANKIEST -> attribute(e,org.bukkit.attribute.Attribute.ARMOR)+e.getHealth();
            case LEAST_ARMOR -> -attribute(e,org.bukkit.attribute.Attribute.ARMOR);
            case FARTHEST -> origin.distanceSquared(e.getLocation());
            case LAST_HEALED -> brain.memory().observations(e.getUniqueId(),tick).stream().filter(o->o.pattern().equals("heal")||o.pattern().equals("consumables")).mapToLong(dev.dasan.customdungeons.intelligence.EncounterMemory.Observation::tick).max().orElse(0);
            default -> -origin.distanceSquared(e.getLocation());
        })).map(List::of).orElse(List.of());
    }
    private static double attribute(LivingEntity e,org.bukkit.attribute.Attribute type) {var a=e.getAttribute(type);return a==null?0:a.getValue();}
    static boolean eligible(ActiveMob caster, LivingEntity target, double range) {
        return target instanceof Player p && caster.session().players().contains(p)
                && p.isOnline() && p.isValid() && !p.isDead()
                && p.getGameMode() != GameMode.SPECTATOR && p.getGameMode() != GameMode.CREATIVE
                && p.getWorld().equals(caster.entity().getWorld())
                && caster.entity().getLocation().distanceSquared(p.getLocation()) <= range * range;
    }
}
