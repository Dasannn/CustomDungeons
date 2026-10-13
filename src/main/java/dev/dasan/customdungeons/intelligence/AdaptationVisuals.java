package dev.dasan.customdungeons.intelligence;

import dev.dasan.customdungeons.ability.EffectAudience;
import dev.dasan.customdungeons.mob.MobsPlatform;
import dev.dasan.customdungeons.runtime.ActiveMob;
import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Mob;

/** One transient name lease per intelligent mob; no PDC, disk writes or scheduled work. */
final class AdaptationVisuals {
    private Component original;
    private boolean visible, colored;

    void activate(ActiveMob active, MobsPlatform platform) {
        var mob=active.entity();
        if(!colored) {
            original=mob.customName();visible=mob.isCustomNameVisible();colored=true;
            var name=original==null?mob.name():original;
            mob.customName(platform.messages().get("intelligence.learning.name",Placeholder.component("name",withoutColors(name))));
            mob.setCustomNameVisible(true);
        }
        var at=mob.getLocation().clone().add(0,mob.getHeight()+.35,0);
        var limits=platform.limits();
        double density=Double.isFinite(limits.particleDensity())?Math.clamp(limits.particleDensity(),0,1):1;
        var points=density==0?List.<Location>of():eyePoints(at,Math.max(4,(int)Math.round(12*density)));
        for(var player:EffectAudience.viewers(active.session(),at,limits.effectViewRadius())) {
            player.playSound(at,"minecraft:block.respawn_anchor.charge",SoundCategory.HOSTILE,.6f,.5f);
            for(var point:points)player.spawnParticle(Particle.END_ROD,point,1,0,0,0,0);
        }
    }
    private static Component withoutColors(Component name) {
        return name.color(null).children(name.children().stream().map(AdaptationVisuals::withoutColors).toList());
    }
    /** A vertical eye facing along the mob's yaw, with a pupil and at most 12 outline points. */
    static List<Location> eyePoints(Location center,int count) {
        int bounded=Math.clamp(count,4,12);
        double yaw=Math.toRadians(center.getYaw()),x=Math.cos(yaw),z=Math.sin(yaw);
        var points=new ArrayList<Location>(bounded+1);
        for(int i=0;i<bounded;i++) {
            double angle=i*2*Math.PI/bounded,side=.45*Math.cos(angle),height=.2*Math.sin(angle);
            points.add(center.clone().add(x*side,height,z*side));
        }
        points.add(center.clone());return List.copyOf(points);
    }
    boolean active() {return colored;}
    void restore(Mob mob) {
        if(!colored)return;
        mob.customName(original);mob.setCustomNameVisible(visible);
        original=null;colored=false;
    }
}
