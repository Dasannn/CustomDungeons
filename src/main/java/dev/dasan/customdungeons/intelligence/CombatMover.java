package dev.dasan.customdungeons.intelligence;

import com.destroystokyo.paper.entity.Pathfinder;
import dev.dasan.customdungeons.ability.MobMovement;
import dev.dasan.customdungeons.mob.*;
import dev.dasan.customdungeons.runtime.ActiveMob;
import java.util.*;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/** One instance per intelligent mob, evaluated by the existing staggered host tick. */
public final class CombatMover {
    private final CombatMotion motion=new CombatMotion();
    private Pathfinder.PathResult ownedPath;
    private boolean spreadPath;
    private UUID pathTarget;
    private Location pathOrigin;
    private long pathUntil;
    boolean hasOwnedPath() {return ownedPath!=null;}
    public void suspend(ActiveMob mob) {motion.cancelRetreat();release(mob);}

    /** Keep an accepted group route through native pursuit updates; never search here. */
    void maintain(ActiveMob mob,IntelligenceDef definition,long tick) {
        if(!spreadPath||ownedPath==null||tick>=pathUntil)return;
        if(!definition.movement().enabled(MovementDef.Option.SPREAD,definition.level())) {release(mob);return;}
        var entity=mob.entity();
        if(!entity.hasAI()||MobMovement.paused(mob)||MobMovement.retreating(mob))return;
        var at=entity.getLocation();
        if(!(entity.getTarget() instanceof Player target)||!Objects.equals(target.getUniqueId(),pathTarget)
                ||!eligible(mob,target,at)||!at.getWorld().equals(pathOrigin.getWorld())
                ||at.distanceSquared(pathOrigin)>16) {release(mob);return;}
        var path=entity.getPathfinder();
        if(path==null) {release(mob);return;}
        var current=path.getCurrentPath();
        if(current==ownedPath||sameRoute(current,ownedPath))return;
        var bounds=entity.getBoundingBox();double width=Math.max(bounds.getWidthX(),bounds.getWidthZ()),height=bounds.getHeight();
        if(!Double.isFinite(width)||width<=0||!Double.isFinite(height)||height<=0
                ||!safePath(at,ownedPath,width,height,mob.session().area())||!path.moveTo(ownedPath,1))release(mob);
    }

    public void evaluate(ActiveMob mob,IntelligenceDef definition,long tick,Double groupAngle) {
        // Do not even read terrain, target, or pathfinder for old levels and paused actors.
        if(definition.level()<2) {suspend(mob);return;}
        if(!mob.entity().hasAI()||MobMovement.paused(mob)||MobMovement.retreating(mob))return;
        var entity=mob.entity();var at=entity.getLocation();
        if(!(entity.getTarget() instanceof Player target)||!eligible(mob,target,at)) {release(mob);return;}
        var to=target.getLocation();var direction=to.getDirection();
        Double incoming=definition.movement().enabled(MovementDef.Option.DODGE,definition.level())?incomingBearing(mob,target,at):null;
        var decision=motion.decide(new CombatMotion.Input(definition.level(),definition.movement(),mob.template().entityType(),
                at.getX(),at.getZ(),to.getX(),to.getZ(),direction.getX(),direction.getZ(),MobHealth.fraction(entity),
                target.isBlocking(),incoming!=null,(entity.getUniqueId().hashCode()&1)==0?1:-1,groupAngle,tick,
                (entity.getWidth()+target.getWidth())/2+.15,incoming));
        if(decision.action()==CombatMotion.Action.HOLD) {
            release(mob);var path=entity.getPathfinder();if(path!=null)path.stopPathfinding();
            entity.lookAt(target);MobMovement.combatFacing(mob,target,11);return;
        }
        var bounds=entity.getBoundingBox();double width=Math.max(bounds.getWidthX(),bounds.getWidthZ()),height=bounds.getHeight();
        if(!Double.isFinite(width)||width<=0||!Double.isFinite(height)||height<=0) {release(mob);return;}
        var path=entity.getPathfinder();if(path==null) {release(mob);return;}
        // Alternate local endpoints, never custom routes. Paper does all path searching.
        double dx=decision.x()-at.getX(),dz=decision.z()-at.getZ();
        double[][] alternatives={{dx,dz},{dx*.75-dz*.25,dz*.75+dx*.25},{dx*.75+dz*.25,dz*.75-dx*.25}};
        for(var delta:alternatives) {
            var end=destination(at,delta[0],delta[1],width,height,mob.session().area());
            if(end.isEmpty())continue;
            var found=path.findPath(end.get());
            if(found==null||!found.canReachFinalPoint()||!safePath(at,found,width,height,mob.session().area()))continue;
            if(!path.moveTo(found,1))continue;
            ownedPath=found;
            spreadPath=decision.action()==CombatMotion.Action.SPREAD;
            pathTarget=target.getUniqueId();pathOrigin=at.clone();pathUntil=tick+10;
            // A newly found path can start under the mob itself. Look at the
            // opponent now; MobMovement follows actual travel on subsequent steps.
            entity.lookAt(target);
            if(decision.faceTarget())MobMovement.combatFacing(mob,target,11);
            else MobMovement.clearCombatFacing(mob);
            return;
        }
        release(mob);
    }
    static boolean eligible(ActiveMob mob,Player target,Location origin) {
        return mob.session().players().contains(target)&&target.isOnline()&&target.isValid()&&!target.isDead()
                &&target.getGameMode()!=org.bukkit.GameMode.CREATIVE&&target.getGameMode()!=org.bukkit.GameMode.SPECTATOR
                &&target.getWorld().equals(origin.getWorld())&&origin.distanceSquared(target.getLocation())<=32*32;
    }
    static boolean aimedAt(Player target,Location eye) {
        var item=target.hasActiveItem()?target.getActiveItem():null;
        if(item==null||(item.getType()!=Material.BOW&&item.getType()!=Material.CROSSBOW)) {
            var inventory=target.getInventory();
            if(inventory==null||!loadedCrossbow(inventory.getItemInMainHand())&&!loadedCrossbow(inventory.getItemInOffHand()))return false;
        }
        var from=target.getEyeLocation();var direction=from.getDirection();var toward=eye.toVector().subtract(from.toVector());
        return toward.lengthSquared()>1e-6&&direction.dot(toward.normalize())>=.94;
    }
    private static boolean loadedCrossbow(org.bukkit.inventory.ItemStack item) {
        return item!=null&&item.getType()==Material.CROSSBOW
                &&item.getItemMeta() instanceof org.bukkit.inventory.meta.CrossbowMeta meta&&meta.hasChargedProjectiles();
    }
    private static Double incomingBearing(ActiveMob mob,Player target,Location at) {
        var eye=mob.entity().getEyeLocation();
        if(aimedAt(target,eye))return bearing(at,target);
        // Host participants only. No equipment or audience scans below level four.
        for(var player:mob.session().players())if(!player.equals(target)&&eligible(mob,player,at)&&aimedAt(player,eye))return bearing(at,player);
        return null;
    }
    private static double bearing(Location at,Player player) {
        var from=player.getLocation();return Math.atan2(at.getZ()-from.getZ(),at.getX()-from.getX());
    }
    private static Optional<Location> destination(Location origin,double dx,double dz,double width,double height,MobArea area) {
        var world=origin.getWorld();int x=(int)Math.floor(origin.getX()+dx),z=(int)Math.floor(origin.getZ()+dz);
        // Nine floor candidates maximum, indoors, including raised steps and slopes.
        for(int offset=0;offset<=4;offset++)for(int sign:offset==0?new int[]{1}:new int[]{-1,1}) {
            var at=SafeTerrain.validateAt(world,x,origin.getBlockY()+sign*offset,z,width,height);
            if(at.isPresent()&&SafeTerrain.fitsArea(at.get(),width,height,area))return at;
        }
        return Optional.empty();
    }
    /** Inspect Paper's route, including intervening footprints; never invent a route around hazards. */
    static boolean safePath(Location origin,Pathfinder.PathResult route,double width,double height,MobArea area) {
        var points=route.getPoints();if(points==null||points.isEmpty()||points.size()>64)return false;
        var from=origin;double floor=origin.getY();int samples=0;
        var shapes=new HashMap<org.bukkit.block.Block,List<org.bukkit.util.BoundingBox>>();
        for(var point:points) {
            if(point==null||!origin.getWorld().equals(point.getWorld())||!Double.isFinite(point.getX())||!Double.isFinite(point.getY())||!Double.isFinite(point.getZ()))return false;
            double length=Math.hypot(point.getX()-from.getX(),point.getZ()-from.getZ());
            int steps=Math.max(1,(int)Math.ceil(length*2));if(samples+steps>128)return false;samples+=steps;
            for(int step=1;step<=steps;step++) {
                double t=(double)step/steps;
                var at=new Location(origin.getWorld(),from.getX()+(point.getX()-from.getX())*t,
                        from.getY()+(point.getY()-from.getY())*t,from.getZ()+(point.getZ()-from.getZ())*t);
                var support=SafeTerrain.supportFloor(at,at.getY(),width,area,shapes);
                if(support.isEmpty()||Math.abs(support.getAsDouble()-floor)>1.25)return false;
                floor=support.getAsDouble();at.setY(floor);
                if(!SafeTerrain.fitsArea(at,width,height,area)||!SafeTerrain.clearBody(at,width,height))return false;
            }
            from=point;
        }
        return true;
    }
    /** Stop our path only while we still own it, never a newer ability/native route. */
    public void release(ActiveMob mob) {
        MobMovement.clearCombatFacing(mob);
        if(ownedPath==null)return;
        // Retain ownership through a pause so a phase downgrade can release it
        // on the first eligible host tick, without writing navigation while paused.
        if(mob.entity().isValid()) {
            if(MobMovement.paused(mob))return;
            var path=mob.entity().getPathfinder();
            if(path!=null&&(path.getCurrentPath()==ownedPath||sameRoute(path.getCurrentPath(),ownedPath)))path.stopPathfinding();
        }
        ownedPath=null;spreadPath=false;pathTarget=null;pathOrigin=null;
    }
    private static boolean sameRoute(Pathfinder.PathResult a,Pathfinder.PathResult b) {
        // Paper may return fresh wrappers for the same native route.
        return a!=null&&a.getPoints().equals(b.getPoints());
    }
}
