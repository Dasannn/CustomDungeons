package dev.dasan.customdungeons.intelligence;

import dev.dasan.customdungeons.mob.MobHost;
import dev.dasan.customdungeons.runtime.ActiveMob;
import java.util.*;
import org.bukkit.entity.Player;

/** At most one host membership scan per ten ticks; no world entity/player scans. */
final class CombatAngles {
    private record Assignment(UUID target,double angle) {}
    private record Snapshot(long bucket,Map<UUID,Assignment> angles) {}
    private final Map<MobHost,Snapshot> hosts=new WeakHashMap<>();
    Double angle(ActiveMob mob,IntelligenceDef def,long tick) {
        if(!def.movement().enabled(MovementDef.Option.SPREAD,def.level())||mob.session().mobs().size()<2)return null;
        var host=mob.session();long bucket=Math.floorDiv(tick,10);var snapshot=hosts.get(host);
        if(snapshot==null||snapshot.bucket()!=bucket) {
            var groups=new HashMap<UUID,List<UUID>>();
            for(var member:host.mobs()) {
                var brain=IntelligenceService.brain(member);var effective=brain==null?member.template().intelligence():brain.definition();
                if(!effective.movement().enabled(MovementDef.Option.SPREAD,effective.level())
                        ||!member.entity().isValid()||member.entity().isDead()
                        ||!(member.entity().getTarget() instanceof Player target)
                        ||!CombatMover.eligible(member,target,member.entity().getLocation()))continue;
                groups.computeIfAbsent(target.getUniqueId(),k->new ArrayList<>()).add(member.entity().getUniqueId());
            }
            var angles=new HashMap<UUID,Assignment>();
            for(var entry:groups.entrySet())if(entry.getValue().size()>1) {
                var group=entry.getValue();
                group.sort(UUID::compareTo);
                for(int i=0;i<group.size();i++)angles.put(group.get(i),new Assignment(entry.getKey(),i*Math.PI*2/group.size()));
            }
            snapshot=new Snapshot(bucket,angles);hosts.put(host,snapshot);
        }
        var assignment=snapshot.angles().get(mob.entity().getUniqueId());
        return assignment!=null&&mob.entity().getTarget() instanceof Player target&&assignment.target().equals(target.getUniqueId())?assignment.angle():null;
    }
    void invalidate(MobHost host) {hosts.remove(host);}
    void clear() {hosts.clear();}
}
