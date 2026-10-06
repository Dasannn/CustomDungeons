package dev.dasan.customdungeons.update;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.dasan.customdungeons.CustomDungeonsPlugin;
import dev.dasan.customdungeons.config.DefinitionStore;
import dev.dasan.customdungeons.session.SessionManager;
import dev.dasan.customdungeons.text.Messages;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.concurrent.CompletableFuture;
import org.bukkit.Server;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.ServicesManager;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class UpdateCommandTest {
    static class Fixture {
        final CustomDungeonsPlugin plugin = mock(CustomDungeonsPlugin.class);
        final UpdateService updater = mock(UpdateService.class);
        final CommandSourceStack source = mock(CommandSourceStack.class);
        final ArrayDeque<Runnable> callbacks = new ArrayDeque<>();
        final CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        @SuppressWarnings("unchecked") Fixture() throws Exception {
            var server = mock(Server.class); var services = mock(ServicesManager.class); var scheduler = mock(BukkitScheduler.class);
            when(plugin.getServer()).thenReturn(server); when(server.getServicesManager()).thenReturn(services);
            when(server.getScheduler()).thenReturn(scheduler); when(plugin.isEnabled()).thenReturn(true);
            when(plugin.getConfig()).thenReturn(new YamlConfiguration());
            when(plugin.sessionManager()).thenReturn(mock(SessionManager.class));
            when(services.load(DefinitionStore.class)).thenReturn(mock(DefinitionStore.class));
            when(services.load(UpdateService.class)).thenReturn(updater);
            doAnswer(invocation -> { callbacks.add(invocation.getArgument(1)); return null; })
                    .when(scheduler).runTask(eq(plugin), any(Runnable.class));
            var yaml = new YamlConfiguration();
            try (var reader = new InputStreamReader(getClass().getResourceAsStream("/messages.yml"), StandardCharsets.UTF_8)) { yaml.load(reader); }
            var messages = new Messages(); messages.load(yaml, "[CD] "); when(plugin.messages()).thenReturn(messages);
            var type = Class.forName("dev.dasan.customdungeons.command.CustomDungeonCommand");
            var constructor = type.getDeclaredConstructor(CustomDungeonsPlugin.class); constructor.setAccessible(true);
            var tree = type.getDeclaredMethod("tree"); tree.setAccessible(true);
            dispatcher.register((LiteralArgumentBuilder<CommandSourceStack>) tree.invoke(constructor.newInstance(plugin)));
        }
    }
    @Test void consoleMayConfirmAndCompletionReturnsThroughScheduler() throws Exception {
        var f = new Fixture(); var console = mock(ConsoleCommandSender.class); when(f.source.getSender()).thenReturn(console);
        var future = new CompletableFuture<UpdateService.Result>();
        when(f.updater.confirm(eq("console"), any())).thenReturn(future);
        f.dispatcher.execute("customdungeon update confirm", f.source);
        verify(console, times(1)).sendMessage(any(net.kyori.adventure.text.Component.class));
        future.complete(new UpdateService.Result("update.staged", "1.0.2", null));
        verify(console, times(1)).sendMessage(any(net.kyori.adventure.text.Component.class));
        assertEquals(1, f.callbacks.size()); f.callbacks.remove().run();
        verify(console, times(2)).sendMessage(any(net.kyori.adventure.text.Component.class));
    }
    @Test void executorImpersonationCannotBypassSenderPermission() throws Exception {
        var f = new Fixture(); var player = mock(Player.class); when(f.source.getSender()).thenReturn(player);
        var op = mock(Player.class); when(op.hasPermission(anyString())).thenReturn(true); when(f.source.getExecutor()).thenReturn(op);
        assertThrows(com.mojang.brigadier.exceptions.CommandSyntaxException.class,
                () -> f.dispatcher.execute("customdungeon update confirm", f.source));
        verifyNoInteractions(f.updater);
    }
    @Test void disabledPluginDoesNotPublishCompletion() throws Exception {
        var f = new Fixture(); var console = mock(ConsoleCommandSender.class); when(f.source.getSender()).thenReturn(console);
        var future = new CompletableFuture<UpdateService.Result>(); when(f.updater.check(any())).thenReturn(future);
        f.dispatcher.execute("customdungeon update check", f.source);
        when(f.plugin.isEnabled()).thenReturn(false);
        future.complete(new UpdateService.Result("update.available", "1.0.2", null));
        assertTrue(f.callbacks.isEmpty());
        verify(console, times(1)).sendMessage(any(net.kyori.adventure.text.Component.class));
    }
}
