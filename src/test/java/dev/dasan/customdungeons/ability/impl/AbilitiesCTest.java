package dev.dasan.customdungeons.ability.impl;

import dev.dasan.customdungeons.ability.*;
import dev.dasan.customdungeons.ability.impl.custom.*;
import dev.dasan.customdungeons.ability.impl.borrowed.*;
import dev.dasan.customdungeons.runtime.*;
import dev.dasan.customdungeons.model.*;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.attribute.*;
import org.bukkit.entity.*;
import org.bukkit.inventory.*;
import org.bukkit.persistence.*;
import org.bukkit.util.Vector;
import java.util.function.Consumer;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;
import io.papermc.paper.registry.*;
import static org.junit.jupiter.api.Assertions.*;

@SuppressWarnings({"unchecked", "rawtypes", "removal"})
class AbilitiesCTest {
    /** Paper 26.3 constants resolve through registries even in API-only tests. */
    @BeforeAll @SuppressWarnings({"unchecked", "rawtypes"})
    static void initializeApiRegistries() {
        var access = mock(RegistryAccess.class);
        var registries = new HashMap<Object, Registry>();
        try (var api = mockStatic(RegistryAccess.class)) {
            api.when(RegistryAccess::registryAccess).thenReturn(access);
            when(access.getRegistry(any(Class.class))).thenAnswer(call -> {
                Class type = call.getArgument(0);
                return registries.computeIfAbsent(type, ignored -> testRegistry(type));
            });
            when(access.getRegistry(any(RegistryKey.class))).thenAnswer(call -> {
                RegistryKey key = call.getArgument(0);
                Class type = key == RegistryKey.ATTRIBUTE ? Attribute.class
                        : key == RegistryKey.BLOCK ? org.bukkit.block.BlockType.class
                        : key == RegistryKey.ITEM ? ItemType.class
                        : key == RegistryKey.SOUND_EVENT ? Sound.class
                        : key == RegistryKey.MOB_EFFECT ? org.bukkit.potion.PotionEffectType.class : Keyed.class;
                return registries.computeIfAbsent(type, ignored -> testRegistry(type));
            });
            // Initialize while the registry accessor is available; entries are cached afterwards.
            assertNotNull(Registry.BLOCK);
            assertNotNull(Sound.ENTITY_RAVAGER_ROAR);
            assertNotNull(Attribute.MAX_HEALTH);
            assertNotNull(org.bukkit.potion.PotionEffectType.BLINDNESS);
        }
    }
    @SuppressWarnings({"unchecked", "rawtypes"})
    static Registry testRegistry(Class type) {
        return new Registry() {
            final Map<NamespacedKey, Keyed> entries = new HashMap<>();
            public Keyed get(NamespacedKey key) {
                return entries.computeIfAbsent(key, k -> {
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
        };
    }

    @Test void shuffleIsAPermutationAndDoesNotMutateInput() {
        int[] slots = {0, 1, 2, 3, 4, 5, 6, 7, 8};
        for (int seed = 0; seed < 100; seed++) {
            int[] shuffled = ChaosAbility.shuffleHotbar(slots, new Random(seed));
            int[] sorted = shuffled.clone();
            Arrays.sort(sorted);
            assertArrayEquals(slots, sorted);
            assertArrayEquals(new int[]{0, 1, 2, 3, 4, 5, 6, 7, 8}, slots);
            assertNotSame(slots, shuffled);
        }
    }
    @Test void shuffleUsesRandomAndIsReproducible() {
        int[] slots = {0, 1, 2, 3, 4, 5, 6, 7, 8};
        int[] shuffled = ChaosAbility.shuffleHotbar(slots, new Random(42));
        assertFalse(Arrays.equals(slots, shuffled));
        assertArrayEquals(shuffled, ChaosAbility.shuffleHotbar(slots, new Random(42)));
        assertArrayEquals(new int[0], ChaosAbility.shuffleHotbar(new int[0], new Random(42)));
        assertArrayEquals(new int[]{7}, ChaosAbility.shuffleHotbar(new int[]{7}, new Random(42)));
    }

    private AbilityRegistry registry() {
        var r = new AbilityRegistry();
        BorrowedAbilitiesC.register(r);
        CustomAbilitiesA.register(r);
        return r;
    }
    @Test void registerAddsTwelveUniqueIdsWithValidSpecs() {
        var r = registry();
        assertEquals(Set.of("ender_blink", "roar_knockback", "launch_up", "cobweb", "split_on_death",
                "blindness", "hook", "anchor", "freeze", "swap", "chaos", "disarm"),
                r.all().stream().map(Ability::id).collect(java.util.stream.Collectors.toSet()));
        for (var a : r.all()) {
            assertNotNull(a.icon());
            var keys = new HashSet<String>();
            for (var spec : a.params()) {
                assertTrue(keys.add(spec.key()));
                assertNotNull(spec.type());
                assertNotNull(spec.defaultValue());
                if (spec.defaultValue() instanceof Number n) {
                    assertTrue(Double.isFinite(n.doubleValue()));
                    assertTrue(spec.min() <= n.doubleValue() && n.doubleValue() <= spec.max());
                }
            }
        }
    }
    @Test void defaultsRegisterBothGroups() {
        var r = new AbilityRegistry();
        Abilities.registerDefaults(r);
        for (var a : registry().all()) assertTrue(r.get(a.id()).isPresent());
    }
    static class Fixture extends BorrowedAbilitiesATest.Fixture {
        final Block feet = mock(Block.class), head = mock(Block.class), ground = mock(Block.class);
        Fixture() {
            when(world.getName()).thenReturn("dungeon");
            when(world.getMinHeight()).thenReturn(-64);
            when(world.getMaxHeight()).thenReturn(320);
            when(world.isChunkLoaded(anyInt(), anyInt())).thenReturn(true);
            var border = mock(WorldBorder.class);
            when(world.getWorldBorder()).thenReturn(border);
            when(border.isInside(any())).thenReturn(true);
            when(world.getBlockAt(anyInt(), anyInt(), anyInt())).thenReturn(feet);
            when(world.getBlockAt(any(Location.class))).thenReturn(feet);
            when(feet.getType()).thenReturn(Material.AIR);
            when(feet.getRelative(0, 1, 0)).thenReturn(head);
            when(head.getType()).thenReturn(Material.AIR);
            when(feet.getRelative(0, -1, 0)).thenReturn(ground);
            when(ground.getType()).thenReturn(Material.STONE);
            when(ground.isSolid()).thenReturn(true);
            when(player.getVelocity()).thenReturn(new Vector(0.3, 0, 0.7));
        }
    }
    @Test void allAbilitiesRejectOutsidersCreativeAndSpectators() {
        var f = new Fixture();
        for (var a : registry().all()) {
            a.execute(new AbilityContext(f.caster, List.of(f.outsider),
                    new ParamValues(Map.of(), a.params()), f.session, null));
            for (var mode : List.of(GameMode.CREATIVE, GameMode.SPECTATOR)) {
                when(f.player.getGameMode()).thenReturn(mode);
                a.execute(f.context(a, Map.of()));
            }
        }
        verify(f.outsider, never()).setVelocity(any());
        verify(f.outsider, never()).teleport(any(Location.class));
        verify(f.outsider, never()).getInventory();
        verify(f.player, never()).setVelocity(any());
        verify(f.player, never()).setFreezeTicks(anyInt());
        verify(f.player, never()).getInventory();
        verify(f.world, never()).getBlockAt(anyInt(), anyInt(), anyInt());
        verify(f.session, never()).spawnMinion(anyString(), any(), any());
    }
    @Test void forcesUseConfiguredValuesAndHookPullsTowardsCaster() {
        var f = new Fixture();
        var roar = new RoarKnockbackAbility();
        roar.execute(f.context(roar, Map.of("knockback", 2, "up", 0.5)));
        verify(f.player).setVelocity(new Vector(2, 0.5, 0));
        var hook = new HookAbility();
        hook.execute(f.context(hook, Map.of("power", 1, "up", 0.2)));
        verify(f.player).setVelocity(new Vector(-1, 0.2, 0));
        var launch = new LaunchUpAbility();
        launch.execute(f.context(launch, Map.of("power", 2)));
        verify(f.player).setVelocity(new Vector(0.3, 2, 0.7));
        var freeze = new FreezeAbility();
        freeze.execute(f.context(freeze, Map.of("ticks", 123)));
        verify(f.player).setFreezeTicks(123);
    }
    @Test void blinkRequiresTwoAirBlocksOnSafeGroundAndLoadedRoom() {
        var f = new Fixture();
        var a = new EndermanBlinkAbility();
        var targetAt = new Location(f.world, 2, 64, 0, 0, 0);
        when(f.player.getLocation()).thenReturn(targetAt);
        when(f.entity.teleport(any(Location.class))).thenReturn(true);
        a.execute(f.context(a, Map.of()));
        var location = org.mockito.ArgumentCaptor.forClass(Location.class);
        verify(f.entity).teleport(location.capture());
        assertEquals(2.5, location.getValue().getX());
        assertEquals(-1.5, location.getValue().getZ());
        clearInvocations(f.entity, f.world);
        when(f.head.getType()).thenReturn(Material.STONE);
        a.execute(f.context(a, Map.of()));
        verify(f.entity, never()).teleport(any(Location.class));
        when(f.head.getType()).thenReturn(Material.AIR);
        when(f.world.isChunkLoaded(anyInt(), anyInt())).thenReturn(false);
        clearInvocations(f.world);
        a.execute(f.context(a, Map.of()));
        verify(f.world, never()).getBlockAt(anyInt(), anyInt(), anyInt());
        when(f.world.isChunkLoaded(anyInt(), anyInt())).thenReturn(true);
        when(f.session.currentRoomRegion()).thenReturn(Region.of("dungeon", new BlockPos(0, 60, 0), new BlockPos(3, 70, 3)));
        clearInvocations(f.world);
        a.execute(f.context(a, Map.of()));
        verify(f.world, never()).getBlockAt(anyInt(), anyInt(), anyInt());
    }
    @Test void swapExchangesOnceAndRollsBackWhenPlayerTeleportFails() {
        var f = new Fixture();
        var a = new SwapAbility();
        when(f.entity.teleport(any(Location.class))).thenReturn(true);
        when(f.player.teleport(any(Location.class))).thenReturn(true);
        a.execute(f.context(a, Map.of()));
        verify(f.entity).teleport(new Location(f.world, 2, 64, 0));
        verify(f.player).teleport(new Location(f.world, 0, 64, 0));
        clearInvocations(f.entity, f.player);
        when(f.player.teleport(any(Location.class))).thenReturn(false);
        a.execute(f.context(a, Map.of()));
        verify(f.entity).teleport(new Location(f.world, 2, 64, 0));
        verify(f.entity).teleport(new Location(f.world, 0, 64, 0));
        clearInvocations(f.entity, f.player);
        when(f.ground.isSolid()).thenReturn(false);
        a.execute(f.context(a, Map.of()));
        verify(f.entity, never()).teleport(any(Location.class));
        verify(f.player, never()).teleport(any(Location.class));
    }
    @Test void cobwebUsesTempBlocksOnlyInAirWithConfiguredTtl() {
        var f = new Fixture();
        var temp = mock(TempBlocks.class);
        var data = mock(BlockData.class);
        when(f.session.tempBlocks()).thenReturn(temp);
        var a = new CobwebAbility();
        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.createBlockData(Material.COBWEB)).thenReturn(data);
            a.execute(f.context(a, Map.of("ttl", 123)));
            verify(temp).place(f.feet, data, 123);
            clearInvocations(temp);
            when(f.feet.getType()).thenReturn(Material.STONE);
            a.execute(f.context(a, Map.of()));
            verifyNoInteractions(temp);
        }
    }
    @Test void chaosPreservesAllNineStacksAndDoesNotTouchOtherSlots() {
        var f = new Fixture();
        var inv = mock(PlayerInventory.class);
        when(f.player.getInventory()).thenReturn(inv);
        ItemStack[] items = new ItemStack[9];
        for (int i = 0; i < 9; i++) {
            items[i] = mock(ItemStack.class);
            when(inv.getItem(i)).thenReturn(items[i]);
        }
        var a = new ChaosAbility();
        a.execute(f.context(a, Map.of()));
        var slots = org.mockito.ArgumentCaptor.forClass(Integer.class);
        var stacks = org.mockito.ArgumentCaptor.forClass(ItemStack.class);
        verify(inv, times(9)).setItem(slots.capture(), stacks.capture());
        assertEquals(List.of(0, 1, 2, 3, 4, 5, 6, 7, 8), slots.getAllValues());
        assertEquals(new HashSet<>(List.of(items)), new HashSet<>(stacks.getAllValues()));
    }
    @Test void disarmPreservesStackAndOwnerAndRefusesUnsafeGround() {
        var f = new Fixture();
        var inv = mock(PlayerInventory.class);
        ItemStack held = mock(ItemStack.class), copy = mock(ItemStack.class);
        when(held.getType()).thenReturn(Material.DIAMOND_SWORD);
        when(held.clone()).thenReturn(copy);
        when(inv.getItemInMainHand()).thenReturn(held);
        when(f.player.getInventory()).thenReturn(inv);
        var owner = UUID.randomUUID();
        when(f.player.getUniqueId()).thenReturn(owner);
        var item = mock(Item.class);
        when(item.isValid()).thenReturn(true);
        when(f.world.dropItem(any(Location.class), eq(copy), org.mockito.ArgumentMatchers.<Consumer<Item>>any()))
                .thenAnswer(call -> { Consumer<Item> init = call.getArgument(2); init.accept(item); return item; });
        var a = new DisarmAbility();
        a.execute(f.context(a, Map.of("distance", 4, "pickupDelay", 60)));
        verify(f.world).dropItem(eq(new Location(f.world, 6, 64, 0)), eq(copy), any());
        verify(item).setOwner(owner);
        verify(item).setCanMobPickup(false);
        verify(item).setInvulnerable(true);
        verify(item).setUnlimitedLifetime(true);
        verify(item).setPickupDelay(60);
        var order = inOrder(f.world, inv);
        order.verify(f.world).dropItem(any(), eq(copy), any());
        order.verify(inv).setItemInMainHand(null);
        clearInvocations(inv, f.world);
        when(f.ground.getType()).thenReturn(Material.MAGMA_BLOCK);
        a.execute(f.context(a, Map.of()));
        verify(f.world, never()).dropItem(any(), any(), any());
        verify(inv, never()).setItemInMainHand(any());
    }
    @Test void splitOnlyOnDeathReducesStatsAndNeverRecurses() {
        var f = new Fixture();
        var parentData = mock(PersistentDataContainer.class);
        when(f.entity.getPersistentDataContainer()).thenReturn(parentData);
        var entity = mock(Mob.class);
        var childData = mock(PersistentDataContainer.class);
        when(entity.getPersistentDataContainer()).thenReturn(childData);
        AttributeInstance health = mock(AttributeInstance.class), scale = mock(AttributeInstance.class);
        when(entity.getAttribute(Attribute.MAX_HEALTH)).thenReturn(health);
        when(entity.getAttribute(Attribute.SCALE)).thenReturn(scale);
        when(health.getValue()).thenReturn(20.0);
        when(health.getBaseValue()).thenReturn(10.0);
        when(scale.getValue()).thenReturn(1.0);
        var child = new ActiveMob(entity, f.caster.template(), f.session);
        child.abilities().add(new AbilityInstance("split_on_death", Trigger.ON_DEATH, 0, TargetMode.NEAREST,
                10, 0, 1, 0, Map.of()));
        var a = new SplitOnDeathAbility();
        a.execute(f.context(a, Map.of()));
        verify(f.session, never()).spawnMinion(anyString(), any(), any());
        when(f.entity.isDead()).thenReturn(true);
        when(f.session.spawnMinion(eq("test"), any(), eq(f.caster))).thenReturn(child);
        a.execute(f.context(a, Map.of("count", 2)));
        verify(f.session, times(2)).spawnMinion(eq("test"), any(), eq(f.caster));
        verify(health, times(2)).setBaseValue(10);
        verify(entity, times(2)).setHealth(10);
        verify(scale, times(2)).setBaseValue(0.5);
        assertTrue(child.abilities().isEmpty());
        when(entity.isDead()).thenReturn(true);
        when(childData.has(any(NamespacedKey.class), eq(PersistentDataType.BYTE))).thenReturn(true);
        clearInvocations(f.session);
        a.execute(new AbilityContext(child, List.of(), new ParamValues(Map.of(), a.params()), f.session, null));
        verify(f.session, never()).spawnMinion(anyString(), any(), any());
    }

    @Test void blindnessAndAnchorApplyExactDurationsAmplifiersAndChainVisual() {
        var f = new Fixture();
        var blindness = new BlindnessAbility();
        blindness.execute(f.context(blindness, Map.of("seconds", 2.5)));
        var effects = org.mockito.ArgumentCaptor.forClass(org.bukkit.potion.PotionEffect.class);
        verify(f.player).addPotionEffect(effects.capture());
        assertEquals(org.bukkit.potion.PotionEffectType.BLINDNESS, effects.getValue().getType());
        assertEquals(50, effects.getValue().getDuration());
        clearInvocations(f.player);
        var anchor = new AnchorAbility();
        var chain = mock(BlockData.class);
        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.createBlockData(Material.IRON_CHAIN)).thenReturn(chain);
            anchor.execute(f.context(anchor, Map.of("ticks", 75)));
        }
        verify(f.player, times(2)).addPotionEffect(effects.capture());
        var applied = effects.getAllValues();
        assertEquals(org.bukkit.potion.PotionEffectType.SLOWNESS, applied.get(1).getType());
        assertEquals(255, applied.get(1).getAmplifier());
        assertEquals(75, applied.get(1).getDuration());
        assertEquals(org.bukkit.potion.PotionEffectType.JUMP_BOOST, applied.get(2).getType());
        assertEquals(-128, applied.get(2).getAmplifier());
        assertEquals(75, applied.get(2).getDuration());
        verify(f.player).spawnParticle(eq(Particle.BLOCK), any(Location.class), eq(16),
                eq(0.3), eq(0.5), eq(0.3), eq(0.0), eq(chain));
        verify(f.outsider, never()).addPotionEffect(any());
    }
    @Test void failedDisarmDropKeepsOriginalItemAndSplitStopsAtSpawnLimit() {
        var f = new Fixture();
        var inv = mock(PlayerInventory.class);
        var held = mock(ItemStack.class);
        when(held.getType()).thenReturn(Material.STONE);
        when(held.clone()).thenReturn(held);
        when(inv.getItemInMainHand()).thenReturn(held);
        when(f.player.getInventory()).thenReturn(inv);
        when(f.world.dropItem(any(Location.class), eq(held), any())).thenReturn(mock(Item.class));
        var disarm = new DisarmAbility();
        disarm.execute(f.context(disarm, Map.of()));
        verify(inv, never()).setItemInMainHand(any());
        when(f.entity.getPersistentDataContainer()).thenReturn(mock(PersistentDataContainer.class));
        when(f.entity.isDead()).thenReturn(true);
        var split = new SplitOnDeathAbility();
        split.execute(f.context(split, Map.of("count", 8)));
        verify(f.session, times(1)).spawnMinion(eq("test"), any(), eq(f.caster));
    }
}
