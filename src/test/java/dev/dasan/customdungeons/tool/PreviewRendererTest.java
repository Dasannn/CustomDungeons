package dev.dasan.customdungeons.tool;

import dev.dasan.customdungeons.CustomDungeonsPlugin;
import dev.dasan.customdungeons.model.*;
import dev.dasan.customdungeons.text.Messages;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;
import org.bukkit.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.*;
import org.bukkit.scheduler.*;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PreviewRendererTest {
    @Test void outlineIsPrivateBoundedAndOnlyActiveForTheMainHandTool() {
        CustomDungeonsPlugin plugin = mock(CustomDungeonsPlugin.class);
        Server server = mock(Server.class);
        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        BukkitTask task = mock(BukkitTask.class);
        Player player = mock(Player.class);
        Player other = mock(Player.class);
        PlayerInventory inventory = mock(PlayerInventory.class);
        World world = mock(World.class);
        SpawnerMarkers markers = mock(SpawnerMarkers.class);
        YamlConfiguration config = new YamlConfiguration();
        when(plugin.getServer()).thenReturn(server);
        when(plugin.getConfig()).thenReturn(config);
        when(plugin.getLogger()).thenReturn(Logger.getAnonymousLogger());
        when(server.getScheduler()).thenReturn(scheduler);
        doReturn(List.of(player, other)).when(server).getOnlinePlayers();
        when(player.getInventory()).thenReturn(inventory);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.hasPermission("customdungeons.admin.tools")).thenReturn(true);
        when(player.getWorld()).thenReturn(world);
        when(world.getName()).thenReturn("dungeons");
        when(player.getLocation()).thenReturn(new Location(world, 1, 64, 1));
        ItemStack held = mock(ItemStack.class);
        ItemMeta meta = mock(ItemMeta.class);
        PersistentDataContainer pdc = mock(PersistentDataContainer.class);
        when(held.hasItemMeta()).thenReturn(true);
        when(held.getItemMeta()).thenReturn(meta);
        when(meta.getPersistentDataContainer()).thenReturn(pdc);
        when(pdc.has(ToolService.TOOL_KEY)).thenReturn(true);
        when(pdc.get(ToolService.TOOL_KEY, PersistentDataType.STRING)).thenReturn("REGION:");
        when(inventory.getItemInOffHand()).thenReturn(held);
        when(scheduler.runTaskTimer(eq(plugin), any(Runnable.class), eq(0L), eq(10L))).thenReturn(task);
        PreviewRenderer previews = new PreviewRenderer(plugin, markers);
        Messages messages = mock(Messages.class);
        when(messages.get(anyString(), any(net.kyori.adventure.text.minimessage.tag.resolver.TagResolver[].class)))
                .thenReturn(net.kyori.adventure.text.Component.empty());
        ToolService tools = new ToolService(messages, previews);
        previews.tools = tools;
        previews.refresh();
        assertFalse(previews.running());
        verify(scheduler, never()).runTaskTimer(any(), any(Runnable.class), anyLong(), anyLong());
        when(inventory.getItemInMainHand()).thenReturn(held);
        tools.select(player, new Location(world, 0, 64, 0), true);
        tools.select(player, new Location(world, 2, 66, 2), false);
        ArgumentCaptor<Runnable> ticker = ArgumentCaptor.forClass(Runnable.class);
        verify(scheduler).runTaskTimer(eq(plugin), ticker.capture(), eq(0L), eq(10L));
        ticker.getValue().run();
        ArgumentCaptor<Location> particles = ArgumentCaptor.forClass(Location.class);
        verify(player, atLeastOnce()).spawnParticle(eq(Particle.DUST), particles.capture(), eq(1),
                eq(0d), eq(0d), eq(0d), eq(0d), any(Particle.DustOptions.class));
        assertTrue(particles.getAllValues().size() <= 256);
        for (Location point : particles.getAllValues()) {
            assertTrue(point.getX() == 0 || point.getX() == 3 || point.getY() == 64
                    || point.getY() == 67 || point.getZ() == 0 || point.getZ() == 3);
        }
        verify(other, never()).spawnParticle(any(Particle.class), any(Location.class), anyInt(),
                anyDouble(), anyDouble(), anyDouble(), anyDouble(), any(Particle.DustOptions.class));
        when(inventory.getItemInMainHand()).thenReturn(null);
        previews.refresh();
        assertFalse(previews.running());
        verify(task).cancel();
    }
}
