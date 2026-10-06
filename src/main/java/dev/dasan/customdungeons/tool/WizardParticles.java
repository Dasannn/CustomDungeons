package dev.dasan.customdungeons.tool;

import dev.dasan.customdungeons.model.*;
import java.util.*;
import org.bukkit.Color;

/** Pure geometry, evaluated on a worker. No world/block queries and at most 256 samples. */
public final class WizardParticles {
    private WizardParticles() {}
    public record Dot(String world,double x,double y,double z,Color color) {}
    private record Outline(Region region,Color color) {}
    public static List<Dot> prepare(DungeonDef d) {
        var outlines=new ArrayList<Outline>();var points=new ArrayList<Dot>();
        if(d.area()!=null) outlines.add(new Outline(d.area(),Color.LIME));
        for(var room:d.rooms()) {
            if(room.region()!=null) outlines.add(new Outline(room.region(),Color.GREEN));
            if(room.door()!=null) outlines.add(new Outline(room.door(),Color.ORANGE));
            for(var spawner:room.spawners()) if(spawner.location()!=null) {
                var p=spawner.location();points.add(new Dot(p.world(),p.x(),p.y()+.5,p.z(),Color.AQUA));
            }
        }
        // Reserve samples for placements so large outlines cannot consume the whole budget.
        var result=new ArrayList<Dot>(256);
        int placements=Math.min(64,points.size());
        for(int i=0;i<placements;i++) result.add(points.get((int)((long)i*points.size()/placements)));
        int remaining=256-result.size();
        int outlineCount=Math.min(remaining,outlines.size());
        for(int i=0;i<outlineCount;i++) {
            var outline=outlines.get((int)((long)i*outlines.size()/outlineCount));var region=outline.region();
            int samples=remaining/outlineCount+(i<remaining%outlineCount?1:0);
            double[] min={region.min().x(),region.min().y(),region.min().z()};
            double[] max={region.max().x()+1d,region.max().y()+1d,region.max().z()+1d};
            // Uniform positions along the twelve edges; independent of cuboid volume.
            for(int s=0;s<samples;s++) {
                double position=s*12d/samples;int edge=(int)position,axis=edge/4,other=(axis+1)%3,third=(axis+2)%3;
                double[] p=min.clone();p[axis]+=(max[axis]-min[axis])*(position-edge);
                p[other]=(edge%4&1)==0?min[other]:max[other];p[third]=(edge%4&2)==0?min[third]:max[third];
                result.add(new Dot(region.world(),p[0],p[1],p[2],outline.color()));
            }
        }
        return List.copyOf(result);
    }
}
