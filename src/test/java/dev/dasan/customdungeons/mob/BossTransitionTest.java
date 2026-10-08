package dev.dasan.customdungeons.mob;

import dev.dasan.customdungeons.config.PluginConfig;
import dev.dasan.customdungeons.model.MobTemplate;
import dev.dasan.customdungeons.runtime.ActiveMob;
import dev.dasan.customdungeons.mob.MobHost;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BossTransitionTest {
    @Test void entrySignalsSessionAndGlowExpiresExactlyWithoutMusic() {
        Fixture f = new Fixture();
        f.controller.beginTransition(f.boss, 100, 10);
        assertEquals(110, f.boss.invulnerableUntil());
        assertEquals(List.of(true), f.glowing);
        assertEquals(List.of(Particle.TOTEM_OF_UNDYING), f.particles);
        assertEquals(List.of("minecraft:item.totem.use", "minecraft:item.totem.use"), f.sounds);
        f.controller.tickMusic(109);
        assertEquals(List.of(true), f.glowing);
        f.controller.tickMusic(110);
        assertEquals(List.of(true, false), f.glowing);
        f.controller.tickMusic(111);
        assertEquals(List.of(true, false), f.glowing);
    }

    @Test void overlappingPhasesExtendGlowWithoutReplayingEntry() {
        Fixture f = new Fixture();
        f.controller.beginTransition(f.boss, 100, 10);
        f.controller.beginTransition(f.boss, 105, 20);
        f.controller.beginTransition(f.boss, 106, 1);
        assertEquals(125, f.boss.invulnerableUntil());
        assertEquals(List.of(true), f.glowing);
        assertEquals(2, f.sounds.size());
        f.controller.tickMusic(110);
        assertEquals(List.of(true), f.glowing);
        f.controller.tickMusic(125);
        assertEquals(List.of(true, false), f.glowing);
        f.controller.beginTransition(f.boss, 130, 5);
        assertEquals(List.of(true, false, true), f.glowing);
        assertEquals(4, f.sounds.size());
    }

    @Test void nonPositiveDurationDoesNotShowTransition() {
        for (int duration : new int[]{0, -1}) {
            Fixture f = new Fixture();
            f.controller.beginTransition(f.boss, 100, duration);
            assertTrue(f.glowing.isEmpty());
            assertTrue(f.particles.isEmpty());
            assertTrue(f.sounds.isEmpty());
        }
    }

    @Test void cleanupAndDeathRemoveGlow() {
        Fixture f = new Fixture();
        f.controller.beginTransition(f.boss, 100, 10);
        f.controller.cleanup(f.boss);
        assertEquals(List.of(true, false), f.glowing);
        f.controller.beginTransition(f.boss, 120, 10);
        f.alive = false;
        f.controller.tickMusic(121);
        assertEquals(List.of(true, false, true, false), f.glowing);
    }

    private static final class Fixture {
        final List<Boolean> glowing = new ArrayList<>();
        final List<Particle> particles = new ArrayList<>();
        final List<String> sounds = new ArrayList<>();
        boolean alive = true;
        final BossController controller;
        final ActiveMob boss;

        Fixture() {
            UUID id = UUID.randomUUID();
            World world = proxy(World.class, (obj, method, args) -> {
                throw new AssertionError(method);
            });
            Location at = new Location(world, 0, 0, 0);
            Mob mob = proxy(Mob.class, (obj, method, args) -> switch (method.getName()) {
                case "getUniqueId" -> id;
                case "isValid" -> alive;
                case "isDead" -> !alive;
                case "getLocation" -> at.clone();
                case "setGlowing" -> { glowing.add((Boolean) args[0]); yield null; }
                default -> throw new AssertionError(method);
            });
            Player nearby = player(new Location(world, 1, 0, 0));
            Player far = player(new Location(world, 100, 0, 0));
            MobHost session = proxy(MobHost.class, (obj, method, args) -> {
                if (method.getName().equals("audience")) return List.of(nearby, far);
                if (method.getName().equals("players")) return List.of(nearby, far);
                throw new AssertionError(method);
            });
            MobTemplate template = new MobTemplate("boss", "ZOMBIE", "Boss", 100, 1, 1, 0, 1,
                    Map.of(), List.of(), List.of(), List.of(), true, "PURPLE", null, List.of(), false);
            boss = new ActiveMob(mob, template, session);
            PluginConfig config = new PluginConfig("", "es", null, "world", false, null,
                    new PluginConfig.PerformanceLimits(50, 0.5, 48), Set.of(), List.of(), null,
                    null, 60, Map.of());
            controller = new BossController(new MobFactory(dev.dasan.customdungeons.mob.TestMobsPlatform.of(config)), Map.of());
        }

        Player player(Location at) {
            return proxy(Player.class, (obj, method, args) -> switch (method.getName()) {
                case "getLocation" -> at.clone();
                case "isOnline" -> true;
                case "isDead" -> false;
                case "spawnParticle" -> {
                    particles.add((Particle) args[0]);
                    assertEquals(15, args[2]); // Configured particle density is respected.
                    yield null;
                }
                case "playSound" -> {
                    sounds.add((String) args[1]);
                    assertEquals(SoundCategory.HOSTILE, args[2]);
                    yield null;
                }
                default -> throw new AssertionError(method);
            });
        }
    }

    private static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
                (obj, method, args) -> switch (method.getName()) {
                    case "equals" -> obj == args[0];
                    case "hashCode" -> System.identityHashCode(obj);
                    case "toString" -> type.getSimpleName() + " test double";
                    default -> handler.invoke(obj, method, args);
                }));
    }
}
