package dev.dasan.customdungeons.ability.impl;

import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import java.util.*;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.BlockType;
import org.bukkit.inventory.ItemType;
import org.bukkit.potion.PotionEffectType;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * One bootstrap for Paper's JVM-wide, cached API constants. Closing a static
 * RegistryAccess mock cannot undo class initialization, so every suite must use
 * the same keyed registries rather than installing competing implementations.
 */
@SuppressWarnings({"unchecked", "rawtypes", "removal"})
public final class PaperApiTestBootstrap {
    private static final Map<Class<?>, TestRegistry> REGISTRIES = new HashMap<>();
    private static boolean initialized;

    private PaperApiTestBootstrap() {}

    public static synchronized void initialize() {
        if (initialized) return;
        var access = mock(RegistryAccess.class);
        try (var api = mockStatic(RegistryAccess.class)) {
            api.when(RegistryAccess::registryAccess).thenReturn(access);
            when(access.getRegistry(any(Class.class))).thenAnswer(call -> registry(call.getArgument(0)));
            when(access.getRegistry(any(RegistryKey.class))).thenAnswer(call -> {
                RegistryKey key = call.getArgument(0);
                Class type = key == RegistryKey.ATTRIBUTE ? Attribute.class
                        : key == RegistryKey.BLOCK ? BlockType.class
                        : key == RegistryKey.ITEM ? ItemType.class
                        : key == RegistryKey.SOUND_EVENT ? Sound.class
                        : key == RegistryKey.DIALOG ? io.papermc.paper.dialog.Dialog.class
                        : key == RegistryKey.MOB_EFFECT ? PotionEffectType.class : Keyed.class;
                return registry(type);
            });
            assertNotNull(Registry.BLOCK);
            assertNotNull(Sound.ENTITY_RAVAGER_ROAR);
            assertNotNull(Attribute.MAX_HEALTH);
            assertNotNull(PotionEffectType.BLINDNESS);
            assertNotNull(io.papermc.paper.dialog.Dialog.CUSTOM_OPTIONS);
            assertSame(registry(PotionEffectType.class), Registry.EFFECT);
        }
        initialized = true;
    }

    private static TestRegistry registry(Class<?> type) {
        return REGISTRIES.computeIfAbsent(type, TestRegistry::new);
    }

    /** Override a lookup only within a try-with-resources scope, including on assertion failure. */
    public static EffectOverride withEffect(NamespacedKey key, PotionEffectType effect) {
        initialize();
        var entries = registry(PotionEffectType.class).entries;
        var previous = entries.put(key, effect);
        return new EffectOverride(key, previous);
    }

    /** Model a missing real registry entry instead of the default lazy test constant. */
    public static MissingEntryOverride withoutEntry(Class<? extends Keyed> type, NamespacedKey key) {
        initialize();
        return new MissingEntryOverride(registry(type), key);
    }

    public static final class MissingEntryOverride implements AutoCloseable {
        private final TestRegistry registry;
        private final NamespacedKey key;
        private final boolean previous;
        private MissingEntryOverride(TestRegistry registry, NamespacedKey key) {
            this.registry=registry;this.key=key;previous=registry.missing.contains(key);
            registry.missing.add(key);
        }
        @Override public void close() { if(!previous)registry.missing.remove(key); }
    }

    public static final class EffectOverride implements AutoCloseable {
        private final NamespacedKey key;
        private final Keyed previous;

        private EffectOverride(NamespacedKey key, Keyed previous) {
            this.key = key;
            this.previous = previous;
        }

        @Override public void close() {
            var entries = registry(PotionEffectType.class).entries;
            if (previous == null) entries.remove(key);
            else entries.put(key, previous);
        }
    }

    private static final class TestRegistry implements Registry {
        private final Set<NamespacedKey> missing = new HashSet<>();
        private final Class<?> type;
        private final Map<NamespacedKey, Keyed> entries = new HashMap<>();

        private TestRegistry(Class<?> type) { this.type = type; }

        public Keyed get(NamespacedKey key) {
            if(missing.contains(key))return null;
            return entries.computeIfAbsent(key, k -> {
                if (type == PotionEffectType.class) {
                    return mock(PotionEffectType.class, call -> switch (call.getMethod().getName()) {
                        case "getKey", "key" -> k;
                        case "isInstant" -> Set.of("instant_health", "instant_damage", "saturation").contains(k.getKey());
                        default -> RETURNS_DEFAULTS.answer(call);
                    });
                }
                if (!type.isInterface()) return (Keyed) mock(type);
                return (Keyed) java.lang.reflect.Proxy.newProxyInstance(type.getClassLoader(),
                        new Class<?>[]{type}, (proxy, method, args) -> switch (method.getName()) {
                            case "getKey", "key" -> k;
                            case "isAir" -> Set.of("air", "cave_air", "void_air").contains(k.getKey());
                            case "equals" -> proxy == args[0];
                            case "hashCode" -> System.identityHashCode(proxy);
                            case "toString" -> k.toString();
                            default -> method.getReturnType() == boolean.class ? false : null;
                        });
            });
        }
        public NamespacedKey getKey(Keyed value) { return value.getKey(); }
        public boolean hasTag(io.papermc.paper.registry.tag.TagKey key) { return false; }
        public io.papermc.paper.registry.tag.Tag getTag(io.papermc.paper.registry.tag.TagKey key) {
            throw new UnsupportedOperationException();
        }
        public Collection getTags() { return List.of(); }
        public java.util.stream.Stream keyStream() { return entries.keySet().stream(); }
        public int size() { return entries.size(); }
        public Iterator iterator() { return entries.values().iterator(); }
        public java.util.stream.Stream stream() { return entries.values().stream(); }
    }
}
