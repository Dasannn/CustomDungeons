package dev.dasan.customdungeons.ability;

import dev.dasan.customdungeons.ability.combat.CombatService;
import dev.dasan.customdungeons.ability.control.ControlService;
import dev.dasan.customdungeons.runtime.ActiveMob;
import java.util.Map;
import java.util.WeakHashMap;
import org.bukkit.entity.Mob;

/** Shared main-thread arbitration for ability and intelligence movement writes. */
public final class MobMovement {
    private MobMovement() {}
    private static final Map<Mob, java.lang.ref.WeakReference<ActiveMob>> tracked=new WeakHashMap<>();
    private record Jump(org.bukkit.Location from,org.bukkit.Location to,int tick) {}
    private static final Map<Mob,Jump> jumps=new WeakHashMap<>();
    private record Impulse(org.bukkit.Location source,org.bukkit.util.Vector vector,long tick) {}
    private static final Map<Mob,Impulse> impulses=new WeakHashMap<>();
    private static final Map<Mob, Long> retreats = new WeakHashMap<>();
    private record Facing(java.lang.ref.WeakReference<org.bukkit.entity.LivingEntity> target,long until) {}
    private static final Map<Mob,Facing> combatFacing=new WeakHashMap<>();
    public static void combatFacing(ActiveMob mob,org.bukkit.entity.LivingEntity target,int ticks) {
        track(mob);combatFacing.put(mob.entity(),new Facing(new java.lang.ref.WeakReference<>(target),mob.session().scheduler().currentTick()+ticks));
    }
    public static void clearCombatFacing(ActiveMob mob) {combatFacing.remove(mob.entity());}
    /** Read-only evidence for diagnostics; this does not suppress movement evaluations. */
    public static boolean combatFacing(ActiveMob mob) {
        var facing=combatFacing.get(mob.entity());var target=facing==null?null:facing.target().get();
        return target!=null&&target.equals(mob.entity().getTarget())&&!paused(mob)
                &&mob.session().scheduler().currentTick()<facing.until();
    }
    public static boolean paused(ActiveMob mob) {
        return AbilityCasts.warning(mob) || blocked(mob);
    }
    public static boolean blocked(ActiveMob mob) {
        return CombatService.paused(mob) || ControlService.controlling(mob)
                || mob.entity() instanceof org.bukkit.entity.Ravager ravager
                    && (ravager.getStunnedTicks()>0 || ravager.getRoarTicks()>0);
    }
    public static boolean blocked(AbilityContext context) {
        var mob=context.caster();
        return CombatService.pausedByOtherCast(context) || ControlService.controllingOtherCast(context)
                || mob.entity() instanceof org.bukkit.entity.Ravager ravager
                    && (ravager.getStunnedTicks()>0 || ravager.getRoarTicks()>0);
    }
    static float movementYaw(org.bukkit.Location from,org.bukkit.Location to) {
        double x=to.getX()-from.getX(),z=to.getZ()-from.getZ();
        return x*x+z*z<1e-6?to.getYaw():movementYaw(x,z);
    }
    private static float movementYaw(double x,double z) {
        double yaw=Math.toDegrees(Math.atan2(-x,z));return (float)(yaw<0?yaw+360:yaw==0?0:yaw);
    }
    private static boolean samePosition(org.bukkit.Location a,org.bukkit.Location b) {
        // Same positional tolerance as Vector.equals, without allocating vectors.
        return Math.abs(a.getX()-b.getX())<1e-6&&Math.abs(a.getY()-b.getY())<1e-6&&Math.abs(a.getZ()-b.getZ())<1e-6;
    }
    public static void track(ActiveMob mob) {
        var ref=tracked.get(mob.entity());if(ref==null||ref.get()!=mob)tracked.put(mob.entity(),new java.lang.ref.WeakReference<>(mob));
    }
    public static void teleported(Mob mob,org.bukkit.Location from,org.bukkit.Location to) {
        var ref=tracked.get(mob);if((ref==null||ref.get()==null)&&!CombatService.isDecoy(mob))return;
        var prior=jumps.get(mob);int tick=org.bukkit.Bukkit.getCurrentTick();
        if(prior!=null&&prior.tick()==tick&&prior.to().getWorld().equals(from.getWorld())&&samePosition(prior.to(),from))from=prior.from();
        jumps.put(mob,new Jump(from.clone(),to.clone(),tick));
    }
    public static void impulse(Mob mob,org.bukkit.entity.Entity source,org.bukkit.util.Vector vector) {
        var ref=tracked.get(mob);var active=ref==null?null:ref.get();
        if(active==null)return;
        var horizontal=vector.clone().setY(0);
        if(source==null||horizontal.lengthSquared()<1e-4){impulses.remove(mob);return;}
        impulses.put(mob,new Impulse(source.getLocation().clone(),horizontal,org.bukkit.Bukkit.getCurrentTick()));
    }
    private static boolean recoil(Mob mob,org.bukkit.Location to,double x,double z) {
        var impulse=impulses.get(mob);if(impulse==null)return false;
        long age=org.bukkit.Bukkit.getCurrentTick()-impulse.tick();
        if(age<0||age>10){impulses.remove(mob);return false;}
        if(!to.getWorld().equals(impulse.source().getWorld()))return false;
        double awayX=to.getX()-x-impulse.source().getX(),awayZ=to.getZ()-z-impulse.source().getZ();
        double length=Math.sqrt(x*x+z*z),awaySquared=awayX*awayX+awayZ*awayZ,force=impulse.vector().length();
        if(length<1e-6||awaySquared<1e-6||length>force*1.5+.01)return false;
        return (x*impulse.vector().getX()+z*impulse.vector().getZ())/(length*force)>=.8
                &&(x*awayX+z*awayZ)/(length*Math.sqrt(awaySquared))>=.5;
    }
    /** Rotation follows actual movement, without changing targets, paths or velocity. */
    public static boolean face(Mob mob,org.bukkit.Location from,org.bukkit.Location to) {
        var ref=tracked.get(mob);var active=ref==null?null:ref.get();
        if(active!=null)dev.dasan.customdungeons.mob.MobFactory.refreshSpeed(active);
        if(active==null&&!CombatService.isDecoy(mob)||!mob.hasAI())return false;
        double x=to.getX()-from.getX(),z=to.getZ()-from.getZ();
        var jump=jumps.remove(mob);
        if(jump!=null&&org.bukkit.Bukkit.getCurrentTick()-jump.tick()>=0&&org.bukkit.Bukkit.getCurrentTick()-jump.tick()<=1
                &&jump.from().getWorld().equals(from.getWorld())&&samePosition(jump.from(),from)) {
            x-=jump.to().getX()-jump.from().getX();z-=jump.to().getZ()-jump.from().getZ();
        }
        if(active!=null&&(CombatService.paused(active)||retreating(active)||recoil(mob,to,x,z)))return false;
        if(x*x+z*z<1e-6)return false;
        float yaw=movementYaw(x,z);to.setYaw(yaw);
        var facing=combatFacing.get(mob);var target=facing==null?null:facing.target().get();
        if(target instanceof org.bukkit.entity.Player player&&active!=null&&!paused(active)&&player.isOnline()&&player.isValid()&&!player.isDead()
                &&player.equals(mob.getTarget())&&player.getGameMode()!=org.bukkit.GameMode.CREATIVE&&player.getGameMode()!=org.bukkit.GameMode.SPECTATOR
                &&active.session().scheduler().currentTick()<facing.until()&&active.session().players().contains(player)
                &&player.getWorld().equals(to.getWorld())) {
            yaw=movementYaw(to,player.getLocation());to.setYaw(yaw);
        } else if(facing!=null)combatFacing.remove(mob);
        // Native rotations also update internal head/interpolation state: write only a change.
        if((mob.getYaw()-yaw)%360!=0||mob.getPitch()!=to.getPitch())mob.setRotation(yaw,to.getPitch());
        if((mob.getBodyYaw()-yaw)%360!=0)mob.setBodyYaw(yaw);
        return true;
    }
    public static void retreat(ActiveMob mob, int ticks) {
        retreats.put(mob.entity(), mob.session().scheduler().currentTick() + ticks);
    }
    public static boolean retreating(ActiveMob mob) {
        var until = retreats.get(mob.entity());
        if (until == null) return false;
        if (mob.session().scheduler().currentTick() < until) return true;
        retreats.remove(mob.entity());return false;
    }
}
