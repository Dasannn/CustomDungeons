package dev.dasan.customdungeons.tool;

import dev.dasan.customdungeons.CustomDungeonsPlugin;
import dev.dasan.customdungeons.model.*;
import java.util.*;
import java.util.function.Consumer;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.world.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedConstruction;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SpawnerMarkersTest {
    private final CustomDungeonsPlugin plugin = mock(CustomDungeonsPlugin.class);
    private final Server server = mock(Server.class);
    private final World world = mock(World.class);
    private final Player player = mock(Player.class);
    private final List<Entity> spawned = new ArrayList<>();
    private final SpawnerMarkers markers = new SpawnerMarkers(plugin);
    private final ToolListener listener = new ToolListener(plugin, mock(ToolService.class),
            mock(PreviewRenderer.class), markers);
    private MockedConstruction<ItemStack> items;

    @BeforeEach void setUp() {
        items = mockConstruction(ItemStack.class);
        when(plugin.getServer()).thenReturn(server);
        when(server.getWorld("world")).thenReturn(world);
        doReturn(List.of(player)).when(server).getOnlinePlayers();
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.hasPermission("customdungeons.admin.edit")).thenReturn(true);
        stubSpawns(world);
    }
    @AfterEach void tearDown() { items.close(); }

    @SuppressWarnings("unchecked")
    private void stubSpawns(World target) {
        doAnswer(invocation -> {
            Location location = invocation.getArgument(0);
            Class<? extends Entity> type = invocation.getArgument(1);
            Entity entity = mock(type);
            when(entity.getUniqueId()).thenReturn(UUID.randomUUID());
            when(entity.getLocation()).thenReturn(location.clone());
            when(entity.isValid()).thenReturn(true);
            when(entity.getPersistentDataContainer()).thenReturn(mock(PersistentDataContainer.class));
            doAnswer(ignored -> { when(entity.isValid()).thenReturn(false); return null; }).when(entity).remove();
            Consumer<Entity> configure = invocation.getArgument(2);
            configure.accept(entity);
            spawned.add(entity);
            return entity;
        }).when(target).spawn(any(Location.class), any(Class.class), any(Consumer.class));
    }
    private DungeonDef dungeon(Point... points) {
        List<SpawnerDef> spawners = new ArrayList<>();
        for (int i = 0; i < points.length; i++)
            spawners.add(new SpawnerDef("spawner-" + i, points[i], 0, List.of()));
        return new DungeonDef("dungeon", "Dungeon", true, null, null, 1, 4, 0, 1, true,
                0, 0, false, null, Map.of(), null,
                List.of(new RoomDef("room", null, null, null, UnlockMode.AUTOMATIC, null, spawners)));
    }
    private DungeonDef dungeon() { return dungeon(new Point("world", 1, 64, 1, 0, 0)); }
    private Chunk chunk(World target, int x, int z) {
        Chunk chunk = mock(Chunk.class);
        when(chunk.getWorld()).thenReturn(target);
        when(chunk.getX()).thenReturn(x);
        when(chunk.getZ()).thenReturn(z);
        return chunk;
    }
    // Exercise registered event routing without a server.
    private void dispatch(Event event) throws Exception {
        boolean handled = false;
        for (var method : ToolListener.class.getDeclaredMethods()) {
            EventHandler handler = method.getAnnotation(EventHandler.class);
            if (handler == null || method.getParameterCount() != 1
                    || method.getParameterTypes()[0] != event.getClass()) continue;
            handled = true;
            method.invoke(listener, event);
        }
        assertTrue(handled, "Missing handler for " + event.getClass().getSimpleName());
    }

    @Test void validMarkersAreReusedAndRemainNonPersistent() {
        DungeonDef dungeon = dungeon();
        markers.showFor(dungeon, player);
        markers.showFor(dungeon, player);
        assertEquals(2, spawned.size());
        for (Entity entity : spawned) {
            verify(entity).setPersistent(false);
            verify(entity, never()).remove();
        }
    }

    @ParameterizedTest @ValueSource(ints = {0, 1})
    void invalidDisplayOrInteractionRemovesBothAndRecreates(int invalidIndex) {
        DungeonDef dungeon = dungeon();
        markers.showFor(dungeon, player);
        List<Entity> old = List.copyOf(spawned);
        when(old.get(invalidIndex).isValid()).thenReturn(false);
        markers.showFor(dungeon, player);
        assertEquals(4, spawned.size());
        old.forEach(entity -> verify(entity).remove());
        verify(player).showEntity(plugin, spawned.get(2));
        verify(player).showEntity(plugin, spawned.get(3));
        markers.release(dungeon.id(), player.getUniqueId());
        verify(spawned.get(2)).remove();
        verify(spawned.get(3)).remove();
    }

    @Test void recreationRestoresVisibilityForExistingViewers() {
        DungeonDef dungeon = dungeon();
        markers.showFor(dungeon, player);
        Player second = mock(Player.class);
        when(second.getUniqueId()).thenReturn(UUID.randomUUID());
        when(second.hasPermission("customdungeons.admin.edit")).thenReturn(true);
        doReturn(List.of(player, second)).when(server).getOnlinePlayers();
        when(spawned.getFirst().isValid()).thenReturn(false);
        markers.showFor(dungeon, second);
        assertEquals(4, spawned.size());
        for (Entity entity : spawned.subList(2, 4)) {
            verify(player).showEntity(plugin, entity);
            verify(second).showEntity(plugin, entity);
        }
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void unloadingRemovesOnlyThatChunkAndAllowsRecreation(boolean entitiesUnload) throws Exception {
        World otherWorld = mock(World.class);
        when(server.getWorld("other")).thenReturn(otherWorld);
        stubSpawns(otherWorld);
        DungeonDef dungeon = dungeon(new Point("world", -0.5, 64, -16.5, 0, 0),
                new Point("world", 1, 64, 1, 0, 0), new Point("other", -0.5, 64, -16.5, 0, 0));
        markers.showFor(dungeon, player);
        Chunk chunk = chunk(world, -1, -2);
        Event event = entitiesUnload ? new EntitiesUnloadEvent(chunk, List.copyOf(spawned.subList(0, 2)))
                : new ChunkUnloadEvent(chunk, true);
        dispatch(event);
        // Both unload notifications can arrive for the same chunk, in either order.
        dispatch(entitiesUnload ? new ChunkUnloadEvent(chunk, true)
                : new EntitiesUnloadEvent(chunk, List.copyOf(spawned.subList(0, 2))));
        for (Entity entity : spawned.subList(0, 2)) verify(entity).remove();
        for (Entity entity : spawned.subList(2, 6)) verify(entity, never()).remove();
        verify(world, never()).getChunkAt(anyInt(), anyInt());
        verify(otherWorld, never()).getChunkAt(anyInt(), anyInt());
        markers.showFor(dungeon, player);
        assertEquals(12, spawned.size());
        for (Entity entity : spawned.subList(0, 6)) {
            verify(entity).remove();
            verify(entity, never()).getChunk();
        }
        for (Entity entity : spawned.subList(6, 12)) verify(entity).setPersistent(false);
    }

    @Test void unrelatedChunkUnloadDoesNotInvalidateMarkers() throws Exception {
        DungeonDef dungeon = dungeon();
        markers.showFor(dungeon, player);
        dispatch(new ChunkUnloadEvent(chunk(world, 1, 0), true));
        markers.showFor(dungeon, player);
        assertEquals(2, spawned.size());
        spawned.forEach(entity -> verify(entity, never()).remove());
    }

    @Test void unloadingAlsoCleansEditingMarkersAndCloseIsIdempotent() throws Exception {
        markers.show(dungeon());
        dispatch(new ChunkUnloadEvent(chunk(world, 0, 0), true));
        spawned.forEach(entity -> verify(entity).remove());
        markers.close();
        markers.close();
        spawned.forEach(entity -> verify(entity).remove());
    }
}
