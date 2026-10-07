package dev.dasan.customdungeons.mob;

import org.bukkit.attribute.Attribute;
import org.bukkit.entity.LivingEntity;
import org.bukkit.persistence.PersistentDataType;

/** The sole conversion between physical health and displayed/configured health. No ticker or retained entities. */
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
    public static double factor(LivingEntity entity) {
        var physical=entity.getAttribute(Attribute.MAX_HEALTH);
        return physical==null || physical.getValue()<=0 ? 1 : maximum(entity)/physical.getValue();
    }
    public static boolean virtual(LivingEntity entity) {
        return entity.getPersistentDataContainer()!=null && entity.getPersistentDataContainer().has(MobKeys.VIRTUAL_MAX_HEALTH,PersistentDataType.DOUBLE);
    }
    public static double current(LivingEntity entity) {
        if(!virtual(entity)) return entity.getHealth();
        var data=entity.getPersistentDataContainer();
        Double snapshot=data.get(MobKeys.PHYSICAL_HEALTH_SNAPSHOT,PersistentDataType.DOUBLE);
        Double current=data.get(MobKeys.VIRTUAL_HEALTH,PersistentDataType.DOUBLE);
        // Detect direct setHealth from other plugins and keep the virtual value in sync.
        if(snapshot!=null && current!=null && snapshot==entity.getHealth()) return current;
        var physical=entity.getAttribute(Attribute.MAX_HEALTH);
        double value=physical==null ? 0 : maximum(entity)*Math.clamp(entity.getHealth()/physical.getValue(),0,1);
        remember(entity,value,entity.getHealth());return value;
    }
    public static double fraction(LivingEntity entity) {
        double maximum=maximum(entity);
        return maximum<=0 ? 0 : Math.clamp(current(entity)/maximum,0,1);
    }
    public static double fractionAfterDamage(LivingEntity entity,double physicalDamage) {
        double maximum=maximum(entity);
        return maximum<=0 ? 0 : Math.clamp((current(entity)-physicalDamage*factor(entity))/maximum,0,1);
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
            data.remove(MobKeys.PHYSICAL_HEALTH_SNAPSHOT);
        }
        setCurrent(entity,maximum*fraction);
    }
    public static void setCurrent(LivingEntity entity,double health) {
        var attribute=entity.getAttribute(Attribute.MAX_HEALTH);if(attribute==null)return;
        double maximum=maximum(entity);
        double value=Math.clamp(health,0,maximum);
        double physical=maximum<=0 ? 0 : (value/maximum)*attribute.getValue();
        entity.setHealth(physical);
        if(virtual(entity)) remember(entity,value,entity.getHealth());
    }
    public static void heal(LivingEntity entity,double amount) {
        if(entity.isDead() || !Double.isFinite(amount) || amount<=0)return;
        setCurrent(entity,Math.min(maximum(entity),current(entity)+amount));
    }
    /** Called at MONITOR with Bukkit's final outcome; Paper stores the subsequent physical result as float. */
    static void remember(LivingEntity entity,double current,double physical) {
        if(!virtual(entity))return;
        var data=entity.getPersistentDataContainer();
        data.set(MobKeys.VIRTUAL_HEALTH,PersistentDataType.DOUBLE,Math.clamp(current,0,maximum(entity)));
        data.set(MobKeys.PHYSICAL_HEALTH_SNAPSHOT,PersistentDataType.DOUBLE,physical);
    }
}
