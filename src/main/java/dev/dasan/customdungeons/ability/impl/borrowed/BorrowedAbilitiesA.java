package dev.dasan.customdungeons.ability.impl.borrowed;

import dev.dasan.customdungeons.ability.*;
import dev.dasan.customdungeons.model.TargetMode;
import dev.dasan.customdungeons.runtime.ActiveMob;
import java.util.*;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.persistence.PersistentDataType;

/** Registration and participant guards shared only by this group of abilities. */
public final class BorrowedAbilitiesA implements Listener {
    private static final Set<Entity> spawned = Collections.newSetFromMap(new WeakHashMap<>());
    static final NamespacedKey SESSION = new NamespacedKey("customdungeons", "session");
    static final NamespacedKey TEMPLATE = new NamespacedKey("customdungeons", "template");
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
        var allowed = TargetSelector.select(ctx.caster(), TargetMode.ALL_IN_RADIUS, range);
        return ctx.targets().stream().filter(allowed::contains).distinct().toList();
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
        for (var entity : List.copyOf(spawned)) entity.remove();
        spawned.clear();
    }
    static boolean inRoom(ActiveMob caster, Location at) {
        var room = caster.session().currentRoomRegion();
        return room == null || room.contains(at.getWorld().getName(), at.getBlockX(), at.getBlockY(), at.getBlockZ());
    }
}
