package dev.dasan.customdungeons.mob;

import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.persistence.PersistentDataType;

/** Authoritative double HP in PDC; native float HP is only a bounded mirror. */
public final class MobHealth {
    public static final double PHYSICAL_LIMIT = 1024;
    private MobHealth() {}
    public static double scaledMaximum(double base,double multiplier) {
        return base > Double.MAX_VALUE / multiplier ? Double.MAX_VALUE : base * multiplier;
    }
    public static double physicalMaximum(double maximum) { return Math.clamp(maximum,1,PHYSICAL_LIMIT); }
    public static double maximum(LivingEntity entity) {
        var physical=entity.getAttribute(Attribute.MAX_HEALTH);
        if(physical==null) return 0;
        if(entity.getPersistentDataContainer()==null) return physical.getValue();
        Double configured=entity.getPersistentDataContainer().get(MobKeys.VIRTUAL_MAX_HEALTH,PersistentDataType.DOUBLE);
        return configured==null ? physical.getValue() : configured;
    }
    public static boolean virtual(LivingEntity entity) {
        return entity.getPersistentDataContainer()!=null && entity.getPersistentDataContainer().has(MobKeys.VIRTUAL_MAX_HEALTH,PersistentDataType.DOUBLE);
    }
    public static double current(LivingEntity entity) {
        if(!virtual(entity)) return entity.getHealth();
        Double current=entity.getPersistentDataContainer().get(MobKeys.VIRTUAL_HEALTH,PersistentDataType.DOUBLE);
        return current==null ? maximum(entity) : current;
    }
    public static double fraction(LivingEntity entity) {
        double maximum=maximum(entity);
        return maximum<=0 ? 0 : Math.clamp(current(entity)/maximum,0,1);
    }
    public static double fractionAfterDamage(LivingEntity entity,double damage) {
        double maximum=maximum(entity);
        return maximum<=0 ? 0 : Math.clamp((current(entity)-Math.max(0,damage))/maximum,0,1);
    }
    public static double fractionAfterDamage(LivingEntity entity,EntityDamageEvent event) {
        double maximum=maximum(entity);
        return maximum<=0 ? 0 : Math.clamp(remainingAfterDamage(entity,event)/maximum,0,1);
    }
    static double remainingAfterDamage(LivingEntity entity,EntityDamageEvent event) {
        if(event.isCancelled()) return current(entity);
        return switch(event.getCause()) {
            case KILL, VOID -> 0;
            case POISON -> Math.max(Math.min(1,current(entity)),current(entity)-Math.max(0,event.getFinalDamage()));
            default -> Math.max(0,current(entity)-Math.max(0,event.getFinalDamage()));
        };
    }
    public static void configure(LivingEntity entity,double maximum,boolean preserveFraction) {
        var attribute=entity.getAttribute(Attribute.MAX_HEALTH);if(attribute==null)return;
        double fraction=preserveFraction ? fraction(entity) : 1;
        double physical=physicalMaximum(maximum);
        attribute.setBaseValue(physical);
        var data=entity.getPersistentDataContainer();
        if(maximum!=physical) data.set(MobKeys.VIRTUAL_MAX_HEALTH,PersistentDataType.DOUBLE,maximum);
        else {
            data.remove(MobKeys.VIRTUAL_MAX_HEALTH);data.remove(MobKeys.VIRTUAL_HEALTH);
        }
        // Remove the obsolete float snapshot from mobs configured by the old implementation.
        data.remove(MobKeys.PHYSICAL_HEALTH_SNAPSHOT);
        setCurrent(entity,maximum*fraction);
    }
    public static void setCurrent(LivingEntity entity,double health) {
        var attribute=entity.getAttribute(Attribute.MAX_HEALTH);if(attribute==null)return;
        double value=Math.clamp(health,0,maximum(entity));
        if(virtual(entity)) {
            remember(entity,value);
            mirror(entity);
        } else entity.setHealth(value);
    }
    /** Above one virtual HP the mirror must pass PoisonMobEffect's native HP > 1 gate. */
    static double mirroredHealth(LivingEntity entity) {
        double current=current(entity);
        if(current<=0)return 0;
        var attribute=entity.getAttribute(Attribute.MAX_HEALTH);
        double proportional=(current/maximum(entity))*attribute.getValue();
        // A representable positive float also protects underflow and nonlethal double remainders.
        double floor=physicalFloor(current);
        double physical=Math.max(floor,proportional);
        if(current<=1) physical=Math.min(1,physical);
        // RegenerationMobEffect compares floats. A double below the maximum can
        // still round up to it, so reserve one native ULP while virtual HP is missing.
        double ceiling=current<maximum(entity)
                ? Math.min(attribute.getValue(),Math.nextDown((float)attribute.getValue()))
                : attribute.getValue();
        return Math.min(ceiling,physical);
    }
    static float physicalFloor(double current) { return current>1 ? Math.nextUp(1.0f) : Float.MIN_NORMAL; }
    /** Plugin-owned death/removal must make the authoritative HP terminal first. */
    public static void terminate(Entity entity,boolean emitDeath) {
        if(entity instanceof LivingEntity living) {
            remember(living,0);
            if(emitDeath) { living.setHealth(0);return; }
        }
        entity.remove();
    }
    static void mirror(LivingEntity entity) {
        if(virtual(entity)) entity.setHealth(mirroredHealth(entity));
    }
    public static void heal(LivingEntity entity,double amount) {
        if(entity.isDead() || !Double.isFinite(amount) || amount<=0)return;
        setCurrent(entity,Math.min(maximum(entity),current(entity)+amount));
    }
    /** Record the event's final HP delta without consulting the lossy physical mirror. */
    static void remember(LivingEntity entity,double current) {
        if(virtual(entity)) entity.getPersistentDataContainer().set(MobKeys.VIRTUAL_HEALTH,
                PersistentDataType.DOUBLE,Math.clamp(current,0,maximum(entity)));
    }
}
