package dev.dasan.customdungeons.ability.impl.borrowed;

import dev.dasan.customdungeons.mob.MobsPlatform;
import dev.dasan.customdungeons.ability.*;
import dev.dasan.customdungeons.model.TargetMode;
import dev.dasan.customdungeons.runtime.ActiveMob;
import java.util.*;
import java.util.function.Function;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.persistence.PersistentDataType;

/** Registration and participant guards shared only by this group of abilities. */
public final class BorrowedAbilitiesA implements Listener {
    private static final Set<Entity> spawned = Collections.newSetFromMap(new WeakHashMap<>());
    static final NamespacedKey SESSION = MobsPlatform.key("session");
    static final NamespacedKey TEMPLATE = MobsPlatform.key("template");
    private BorrowedAbilitiesA() {}
    public static void register(AbilityRegistry registry) {
        if (Bukkit.getServer() != null) {
            var plugin = Bukkit.getPluginManager().getPlugin("CustomDungeons");
            if (plugin != null) Bukkit.getPluginManager().registerEvents(new BorrowedAbilitiesA(), plugin);
        }
        for (Ability ability : List.of(new WitherSkullsAbility(), new WitherShockwaveAbility(),
                new DragonBreathAbility(), new DragonRoarAbility(), new SonicBoomAbility(),
                new DarknessPulseAbility(), new EvokerFangsAbility(), new SummonVexesAbility())) {
            registry.register(ability);
            if (ability instanceof Listener listener && Bukkit.getServer() != null) {
                var plugin = Bukkit.getPluginManager().getPlugin("CustomDungeons");
                if (plugin == null) throw new IllegalStateException("CustomDungeons plugin unavailable");
                Bukkit.getPluginManager().registerEvents(listener, plugin);
            }
        }
    }
    static List<LivingEntity> targets(AbilityContext ctx, double range) {
        var allowed = TargetSelector.select(ctx.caster(), TargetMode.ALL_IN_RADIUS, Double.MAX_VALUE);
        var candidates = ctx.targets().stream().filter(allowed::contains).distinct().toList();
        return withinRadius(candidates, LivingEntity::getLocation, ctx.caster().entity().getLocation(), range);
    }
    /** Pure spherical radius selection; membership and player eligibility are checked by targets. */
    static <T> List<T> withinRadius(Collection<T> candidates, Function<T, Location> location,
                                   Location origin, double radius) {
        if (!Double.isFinite(radius) || radius < 0) return List.of();
        return candidates.stream().filter(candidate -> {
            var at = location.apply(candidate);
            return Objects.equals(origin.getWorld(), at.getWorld())
                    && origin.toVector().distanceSquared(at.toVector()) <= radius * radius;
        }).toList();
    }
    static boolean allowed(ActiveMob caster, Entity target) {
        return target instanceof LivingEntity living &&
                TargetSelector.select(caster, TargetMode.ALL_IN_RADIUS, Double.MAX_VALUE).contains(living);
    }
    static void mark(Entity entity, ActiveMob caster, String template) {
        spawned.add(entity);
        entity.getPersistentDataContainer().set(SESSION, PersistentDataType.STRING, caster.session().id().toString());
        entity.getPersistentDataContainer().set(TEMPLATE, PersistentDataType.STRING, template);
    }
    @EventHandler
    public void disable(PluginDisableEvent event) {
        if (!event.getPlugin().getName().equals("CustomDungeons")) return;
        for (var entity : List.copyOf(spawned)) dev.dasan.customdungeons.mob.MobHealth.terminate(entity,false);
        spawned.clear();
    }
    static boolean inRoom(ActiveMob caster, Location at) {
        var room = caster.session().area();
        return room == null || room.contains(at);
    }
}
