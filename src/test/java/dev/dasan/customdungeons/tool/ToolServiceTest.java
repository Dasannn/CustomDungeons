package dev.dasan.customdungeons.tool;

import dev.dasan.customdungeons.text.Messages;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.*;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedConstruction;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ToolServiceTest {
    private final Player player = mock(Player.class);
    private final PlayerInventory inventory = mock(PlayerInventory.class);
    private final PreviewRenderer previews = mock(PreviewRenderer.class);
    private final ItemStack[] contents = new ItemStack[41];
    private final YamlConfiguration config = new YamlConfiguration();

    ToolServiceTest() {
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.getInventory()).thenReturn(inventory);
        when(player.hasPermission("customdungeons.admin.tools")).thenReturn(true);
        when(inventory.getSize()).thenReturn(contents.length);
        when(inventory.getItem(anyInt())).thenAnswer(call -> contents[(int) call.getArgument(0)]);
        doAnswer(call -> { contents[(int) call.getArgument(0)] = call.getArgument(1); return null; })
                .when(inventory).setItem(anyInt(), nullable(ItemStack.class));
    }

    private Messages messages(String resource) throws Exception {
        try (var stream = Objects.requireNonNull(getClass().getResourceAsStream("/" + resource));
                var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            Messages messages = new Messages();
            messages.load(YamlConfiguration.loadConfiguration(reader), "");
            return messages;
        }
    }

    private static ItemStack tool(ToolType type) {
        ItemStack item = mock(ItemStack.class);
        ItemMeta meta = mock(ItemMeta.class);
        PersistentDataContainer pdc = mock(PersistentDataContainer.class);
        when(item.hasItemMeta()).thenReturn(true);
        when(item.getItemMeta()).thenReturn(meta);
        when(meta.getPersistentDataContainer()).thenReturn(pdc);
        when(pdc.has(ToolService.TOOL_KEY)).thenReturn(true);
        when(pdc.get(ToolService.TOOL_KEY, PersistentDataType.STRING)).thenReturn(type.name() + ":old");
        return item;
    }

    private static MockedConstruction<ItemStack> newItems(List<Material> materials) {
        return mockConstruction(ItemStack.class, (item, context) -> {
            materials.add((Material) context.arguments().getFirst());
            ItemMeta meta = mock(ItemMeta.class);
            when(item.getItemMeta()).thenReturn(meta);
            when(meta.getPersistentDataContainer()).thenReturn(mock(PersistentDataContainer.class));
        });
    }

    @Test void reissueReplacesEveryCopyIncludingOffhandAndCursorButKeepsOtherTools() throws Exception {
        for (ToolType type : ToolType.values()) {
            Arrays.fill(contents, null);
            contents[3] = tool(type);
            contents[11] = tool(type);
            contents[40] = tool(type);
            ItemStack other = tool(ToolType.values()[(type.ordinal() + 1) % ToolType.values().length]);
            contents[7] = other;
            ItemStack cursor = tool(type);
            when(player.getItemOnCursor()).thenReturn(cursor);
            List<Material> materials = new ArrayList<>();
            try (var items = newItems(materials)) {
                new ToolService(messages("messages.yml"), previews, () -> config).give(player, type, "new");
                ItemStack replacement = items.constructed().getFirst();
                assertSame(replacement, contents[3]);
                assertNull(contents[11]);
                assertNull(contents[40]);
                assertSame(other, contents[7]);
                assertEquals(List.of(ToolMaterials.defaultFor(type)), materials);
                verify(replacement.getItemMeta().getPersistentDataContainer()).set(
                        ToolService.TOOL_KEY, PersistentDataType.STRING, type.name() + ":new");
                verify(replacement.getItemMeta()).setMaxStackSize(1);
                verify(replacement.getItemMeta()).lore(argThat(lore -> lore.size() == 3));
            }
        }
        verify(player, times(4)).setItemOnCursor(null);
        verify(inventory, never()).addItem(any(ItemStack.class));
    }

    @Test void cursorOnlyToolIsReplacedEvenWhenInventoryIsFull() throws Exception {
        ItemStack cursor = tool(ToolType.POINT);
        when(player.getItemOnCursor()).thenReturn(cursor);
        try (var items = newItems(new ArrayList<>())) {
            new ToolService(messages("messages_en.yml"), previews, () -> config).give(player, ToolType.POINT, "new");
            verify(player).setItemOnCursor(items.constructed().getFirst());
            verify(inventory, never()).addItem(any(ItemStack.class));
        }
    }

    @Test void configurationIsUsedAndFullInventoryNeverDropsTool() throws Exception {
        config.set("tools.items.point", "FEATHER");
        List<Material> materials = new ArrayList<>();
        try (var items = newItems(materials)) {
            when(inventory.addItem(any(ItemStack.class))).thenAnswer(call -> new HashMap<>(Map.of(0, call.getArgument(0))));
            new ToolService(messages("messages.yml"), previews, () -> config).give(player, ToolType.POINT, null);
            assertEquals(List.of(Material.FEATHER), materials);
            ArgumentCaptor<Component> chat = ArgumentCaptor.forClass(Component.class);
            verify(player).sendMessage(chat.capture());
            assertTrue(plain(chat.getValue()).contains("No hay espacio"));
            verifyNoInteractions(previews);
            verify(player, never()).getWorld();
        }
    }

    @Test void selectionsShowCoordinatesInclusiveSizeAndNextStepInChatAndActionBarInBothLanguages() throws Exception {
        World world = mock(World.class);
        when(world.getName()).thenReturn("dungeons");
        for (String language : List.of("messages.yml", "messages_en.yml")) {
            clearInvocations(player);
            ToolService tools = new ToolService(messages(language), previews);
            tools.select(player, new Location(world, 7, 8, 9), true);
            tools.select(player, new Location(world, -3, 2, 1), false);
            ArgumentCaptor<Component> chat = ArgumentCaptor.forClass(Component.class);
            ArgumentCaptor<Component> bar = ArgumentCaptor.forClass(Component.class);
            verify(player, times(2)).sendMessage(chat.capture());
            verify(player, times(2)).sendActionBar(bar.capture());
            String first = plain(chat.getAllValues().getFirst());
            assertTrue(first.contains("Pos1: dungeons (7, 8, 9)"));
            assertTrue(first.contains(language.equals("messages.yml") ? "pendiente" : "pending"));
            String second = plain(chat.getAllValues().getLast());
            assertTrue(second.contains("Pos2: dungeons (-3, 2, 1)"));
            assertTrue(second.contains("11×7×9 (693"));
            assertTrue(second.contains("/customdungeon"));
            assertTrue(second.contains(language.equals("messages.yml") ? "Usar selección" : "Use selection"));
            assertFalse(second.matches(".*<(world|x|y|z|size|width|height|depth|blocks)>.*"));
            String actionBar = plain(bar.getAllValues().getLast());
            assertTrue(actionBar.contains("Pos2 (-3, 2, 1)"));
            assertTrue(actionBar.contains("11×7×9 (693"));
            assertTrue(actionBar.contains("/customdungeon"));
            assertTrue(actionBar.length() < second.length());
            tools.select(player, new Location(world, -3, 2, 1), true);
            assertEquals(1, tools.selection(player.getUniqueId()).orElseThrow().toRegion().volume());
        }
    }

    @Test void pointsAndSpawnersExplainTheirOwnNextStepAndPreserveOrientation() throws Exception {
        World world = mock(World.class);
        when(world.getName()).thenReturn("dungeons");
        for (String language : List.of("messages.yml", "messages_en.yml")) {
            for (ToolType type : List.of(ToolType.POINT, ToolType.SPAWNER)) {
                clearInvocations(player);
                ItemStack held = tool(type);
                when(inventory.getItemInMainHand()).thenReturn(held);
                ToolService tools = new ToolService(messages(language), previews);
                tools.point(player, new Location(world, 1.25, 64.5, -3.75, 45, -20));
                ArgumentCaptor<Component> chat = ArgumentCaptor.forClass(Component.class);
                verify(player).sendMessage(chat.capture());
                String text = plain(chat.getValue());
                assertTrue(text.contains("dungeons (1.25, 64.50, -3.75)"));
                assertTrue(text.contains("/customdungeon"));
                assertTrue(text.contains(type == ToolType.SPAWNER ? "Spawner" : language.equals("messages.yml") ? "Punto" : "Point"));
                ArgumentCaptor<Component> bar = ArgumentCaptor.forClass(Component.class);
                verify(player).sendActionBar(bar.capture());
                String actionBar = plain(bar.getValue());
                assertTrue(actionBar.contains("(1.25, 64.50, -3.75)"));
                assertTrue(actionBar.contains("/customdungeon"));
                assertTrue(actionBar.length() < text.length());
                assertEquals(45, tools.lastPoint(player.getUniqueId()).orElseThrow().getYaw());
                assertEquals(-20, tools.lastPoint(player.getUniqueId()).orElseThrow().getPitch());
            }
        }
    }

    private static String plain(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }
}
