package dev.dasan.customdungeons.ability.zone;

import java.util.*;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Checks actual player warnings and damage, including native rain events, for every zone. */
class ZoneDamageFootprintTest {
    static List<String> ids(){return ZoneServiceTest.IDS;}
    private List<Location> warnings(Player viewer) {
        return mockingDetails(viewer).getInvocations().stream()
                .filter(i->i.getMethod().getName().equals("spawnParticle")&&i.getArgument(0)==Particle.ENCHANT)
                .map(i->((Location)i.getArgument(1)).clone()).toList();
    }
    private boolean marked(String id,Location at,List<Location> marks) {
        if(marks.isEmpty())return false;
        double x=at.getX(),z=at.getZ();
        if(id.equals("cracked_floor")) {
            int tx=(int)Math.floor(x/2)*2,tz=(int)Math.floor(z/2)*2;
            return marks.stream().filter(p->(int)Math.floor(p.getX()/2)*2==tx&&(int)Math.floor(p.getZ()/2)*2==tz)
                    .map(p->p.getX()+":"+p.getZ()).distinct().count()==4;
        }
        if(id.equals("charged_beam")) {
            return x>=marks.stream().mapToDouble(Location::getX).min().orElseThrow()-1e-6
                    &&x<=marks.stream().mapToDouble(Location::getX).max().orElseThrow()+1e-6
                    &&z>=marks.stream().mapToDouble(Location::getZ).min().orElseThrow()-1e-6
                    &&z<=marks.stream().mapToDouble(Location::getZ).max().orElseThrow()+1e-6;
        }
        double radius=marks.stream().mapToDouble(p->Math.hypot(p.getX(),p.getZ())).max().orElseThrow();
        if(Math.hypot(x,z)>radius+1e-6)return false;
        if(!id.equals("sweep")||Math.hypot(x,z)==0)return true;
        double angle=Math.atan2(z,x);
        return angle>=marks.stream().mapToDouble(p->Math.atan2(p.getZ(),p.getX())).min().orElseThrow()-1e-6
                &&angle<=marks.stream().mapToDouble(p->Math.atan2(p.getZ(),p.getX())).max().orElseThrow()+1e-6;
    }
    @ParameterizedTest @MethodSource("ids")
    void everyDamagedPositionIsContainedInItsPreviouslyWarnedFootprint(String id) {
        try(var f=new ZoneServiceTest.Fixture()) {
            f.positions.put(f.player,new Location(f.world,0,64,0));f.positions.put(f.ally,new Location(f.world,0,64,0));
            // Beam fixes its forward direction using a target; its origin stays at the mob.
            if(id.equals("charged_beam"))f.positions.put(f.ally,new Location(f.world,0,64,2));
            var probes=new ArrayList<Player>();
            for(double[] point:List.of(new double[]{0,0},new double[]{0,6},new double[]{6,0},new double[]{0,-6},new double[]{0,18},new double[]{1,1},new double[]{3,3})) {
                var probe=f.player(point[0],point[1]);f.players.add(probe);probes.add(probe);
                doAnswer(i->{assertTrue(marked(id,probe.getLocation(),warnings(f.player)),id+" damaged an unmarked point "+probe.getLocation());return null;})
                        .when(probe).damage(anyDouble(),any(Entity.class));
                if(id.equals("rift"))when(probe.teleport(any(Location.class))).thenAnswer(i->{assertTrue(marked(id,probe.getLocation(),warnings(f.player)));return true;});
            }
            f.cast(id,Map.of("radius",3,"burst-radius",8,"ticks",20,"count",1,"warning",20,"damage",2));
            for(int tick=0;tick<80;tick++) {
                f.step(1);
                if(id.equals("arrow_rain")&&!f.created.isEmpty()&&tick<40)for(Player probe:probes) {
                    var event=mock(EntityDamageByEntityEvent.class);when(event.getEntity()).thenReturn(probe);when(event.getDamager()).thenReturn(f.created.getFirst());
                    f.service.arrowDamage(event);
                    boolean cancelled=mockingDetails(event).getInvocations().stream().anyMatch(i->i.getMethod().getName().equals("setCancelled")&&Boolean.TRUE.equals(i.getArgument(0)));
                    if(!cancelled)assertTrue(marked(id,probe.getLocation(),warnings(f.player)),"Rain authorized unmarked damage");
                }
            }
            if(!id.equals("rift")&&!id.equals("arrow_rain"))assertTrue(probes.stream().anyMatch(p->mockingDetails(p).getInvocations().stream().anyMatch(i->i.getMethod().getName().equals("damage"))),"Expected a damaging positive control for "+id);
        }
    }
    @Test void vortexMarksTheWholeBurstRingThroughoutAttractionBeforeAnyBurstDamage() {
        try(var f=new ZoneServiceTest.Fixture()) {
            f.positions.put(f.player,new Location(f.world,0,64,6));
            f.cast("vortex",Map.of("radius",3,"burst-radius",8,"ticks",20));f.step(39);
            verify(f.player,never()).damage(anyDouble(),any(Entity.class));
            long ring=warnings(f.player).stream().filter(p->Math.abs(Math.hypot(p.getX(),p.getZ())-8)<1e-6).count();
            assertEquals(32,ring,"Eight ring marks on each of four attraction frames");
            f.step(1);verify(f.player).damage(6,f.entity);
        }
    }
}
