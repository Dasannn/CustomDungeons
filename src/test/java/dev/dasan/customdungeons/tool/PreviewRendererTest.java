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
    @Test void pendingRecoveryReusesTheSharedTickerWithoutToolsOrSessionsAndStopsWhenEmpty() {
        var plugin=mock(CustomDungeonsPlugin.class,RETURNS_DEEP_STUBS);
        when(plugin.getLogger()).thenReturn(Logger.getAnonymousLogger());
        var server=plugin.getServer();doReturn(List.of()).when(server).getOnlinePlayers();
        var task=mock(BukkitTask.class);
        when(plugin.getServer().getScheduler().runTaskTimer(eq(plugin),any(Runnable.class),eq(0L),eq(10L))).thenReturn(task);
        var pending=new java.util.concurrent.atomic.AtomicBoolean(false);var attempts=new java.util.concurrent.atomic.AtomicInteger();
        var previews=new PreviewRenderer(plugin,mock(SpawnerMarkers.class));
        previews.recoveryWork(pending::get,()->{if(pending.get())attempts.incrementAndGet();});assertFalse(previews.running());
        pending.set(true);previews.refreshRecoveries();assertTrue(previews.running());previews.refreshRecoveries();
        var ticker=ArgumentCaptor.forClass(Runnable.class);
        verify(plugin.getServer().getScheduler(),times(1)).runTaskTimer(eq(plugin),ticker.capture(),eq(0L),eq(10L));
        ticker.getValue().run();assertEquals(1,attempts.get());
        pending.set(false);ticker.getValue().run();assertEquals(1,attempts.get());assertFalse(previews.running());verify(task).cancel();
        previews.close();pending.set(true);previews.refreshRecoveries();assertFalse(previews.running());
    }
    @Test void regularPlateToolRetainsBothColorsAlongsideConstructionPreviewSupport() {
        var plugin=mock(CustomDungeonsPlugin.class,RETURNS_DEEP_STUBS);var player=mock(Player.class,RETURNS_DEEP_STUBS);
        var world=mock(World.class);when(world.getName()).thenReturn("dungeons");when(player.getWorld()).thenReturn(world);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());when(player.getLocation()).thenReturn(new Location(world,0,64,0));
        when(player.hasPermission("customdungeons.admin.tools")).thenReturn(true);when(plugin.getConfig()).thenReturn(new YamlConfiguration());
        var server=plugin.getServer();doReturn(List.of(player)).when(server).getOnlinePlayers();
        var held=mock(ItemStack.class);var meta=mock(ItemMeta.class);var pdc=mock(PersistentDataContainer.class);
        when(held.hasItemMeta()).thenReturn(true);when(held.getItemMeta()).thenReturn(meta);when(meta.getPersistentDataContainer()).thenReturn(pdc);
        when(pdc.has(ToolService.TOOL_KEY)).thenReturn(true);when(pdc.get(ToolService.TOOL_KEY,PersistentDataType.STRING)).thenReturn("PLATE:d");
        when(player.getInventory().getItemInMainHand()).thenReturn(held);
        var task=mock(BukkitTask.class);when(plugin.getServer().getScheduler().runTaskTimer(eq(plugin),any(Runnable.class),eq(0L),eq(10L))).thenReturn(task);
        var previews=new PreviewRenderer(plugin,mock(SpawnerMarkers.class));var tools=new ToolService(mock(Messages.class),previews);previews.tools=tools;
        var d=new dev.dasan.customdungeons.config.DefinitionCodec().decodeDungeon("d",new YamlConfiguration())
            .withStart(StartMode.PLATES,List.of(new Point("dungeons",1.5,64,1.5,0,0)),3,null,false,true,false,10)
            .withFinish(FinishMode.NONE,60,FinishDestination.EXIT,List.of(new Point("dungeons",2.5,64,2.5,0,0)));
        tools.onPlateEdit((p,id)->null,()->List.of(d));previews.refresh();
        var tick=ArgumentCaptor.forClass(Runnable.class);verify(plugin.getServer().getScheduler()).runTaskTimer(eq(plugin),tick.capture(),eq(0L),eq(10L));tick.getValue().run();
        var colors=ArgumentCaptor.forClass(Particle.DustOptions.class);
        verify(player,times(2)).spawnParticle(eq(Particle.DUST),any(Location.class),eq(1),eq(0d),eq(0d),eq(0d),eq(0d),colors.capture());
        assertEquals(List.of(Color.LIME,Color.FUCHSIA),colors.getAllValues().stream().map(Particle.DustOptions::getColor).toList());previews.close();
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans={false,true})
    void outlineIsPrivateBoundedAndOnlyActiveForTheMainHandTool(boolean building) {
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
        when(player.hasPermission("customdungeons.admin.tools")).thenReturn(!building);
        when(player.hasPermission("customdungeons.admin.edit")).thenReturn(building);
        when(player.getWorld()).thenReturn(world);
        when(world.getName()).thenReturn("dungeons");
        when(player.getLocation()).thenReturn(new Location(world, 1, 64, 1));
        ItemStack held = mock(ItemStack.class);
        ItemMeta meta = mock(ItemMeta.class);
        PersistentDataContainer pdc = mock(PersistentDataContainer.class);
        when(held.hasItemMeta()).thenReturn(true);
        when(held.getItemMeta()).thenReturn(meta);
        when(meta.getPersistentDataContainer()).thenReturn(pdc);
        when(pdc.has(ToolService.TOOL_KEY)).thenReturn(!building);
        when(pdc.has(BuildTools.KEY)).thenReturn(building);
        if(building)when(pdc.get(BuildTools.KEY,PersistentDataType.INTEGER)).thenReturn(1);
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
        // PluginDisableEvent closes tools before the assistant removes its preview.
        previews.close();when(inventory.getItemInMainHand()).thenReturn(held);
        previews.stopWizard(player.getUniqueId());previews.refresh();
        assertFalse(previews.running());
        verify(scheduler,times(1)).runTaskTimer(eq(plugin),any(Runnable.class),eq(0L),eq(10L));
    }
}
