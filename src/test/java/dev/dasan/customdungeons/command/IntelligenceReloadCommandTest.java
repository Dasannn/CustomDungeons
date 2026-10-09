package dev.dasan.customdungeons.command;

import com.mojang.brigadier.CommandDispatcher;
import dev.dasan.customdungeons.CustomDungeonsPlugin;
import dev.dasan.customdungeons.config.*;
import dev.dasan.customdungeons.intelligence.IntelligenceService;
import dev.dasan.customdungeons.mob.LiveTestService;
import dev.dasan.customdungeons.session.SessionManager;
import dev.dasan.customdungeons.text.Messages;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import java.util.*;
import org.bukkit.*;
import org.bukkit.plugin.ServicesManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class IntelligenceReloadCommandTest {
    @TempDir java.nio.file.Path directory;
    @Test void actualReloadCommandStopsLiveTestsAndRollsBackIntelligenceBeforeLoadingDefinitions() throws Exception {
        var plugin=mock(CustomDungeonsPlugin.class);var server=mock(Server.class);var services=mock(ServicesManager.class);
        var definitions=mock(DefinitionStore.class);when(definitions.dungeons()).thenReturn(Map.of());
        when(definitions.reloadAsync(any())).thenReturn(java.util.concurrent.CompletableFuture.completedFuture(null));
        when(plugin.getServer()).thenReturn(server);when(server.getServicesManager()).thenReturn(services);when(server.getOnlinePlayers()).thenReturn(List.of());
        when(services.load(DefinitionStore.class)).thenReturn(definitions);when(plugin.sessionManager()).thenReturn(mock(SessionManager.class));
        when(plugin.messages()).thenReturn(mock(Messages.class));when(plugin.getDataFolder()).thenReturn(directory.toFile());when(plugin.getConfig()).thenReturn(new org.bukkit.configuration.file.YamlConfiguration());
        when(plugin.getResource(anyString())).thenAnswer(i->new java.io.ByteArrayInputStream("{}".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        var source=mock(CommandSourceStack.class);var sender=mock(org.bukkit.command.ConsoleCommandSender.class);when(source.getSender()).thenReturn(sender);when(sender.hasPermission("customdungeons.admin.reload")).thenReturn(true);
        var build=mock(dev.dasan.customdungeons.tool.BuildModeService.class);
        when(services.load(dev.dasan.customdungeons.tool.BuildModeService.class)).thenReturn(build);
        var sequence=new ArrayList<String>();doAnswer(i->{sequence.add("build");return null;}).when(build).exitAll();
        var dispatcher=new CommandDispatcher<CommandSourceStack>();
        try(var arguments=mockStatic(io.papermc.paper.command.brigadier.argument.ArgumentTypes.class);var migration=mockStatic(ConfigMigration.class);var live=mockStatic(LiveTestService.class);var intelligence=mockStatic(IntelligenceService.class)) {
            live.when(LiveTestService::stopAll).thenAnswer(i->{sequence.add("live");return null;});
            arguments.when(io.papermc.paper.command.brigadier.argument.ArgumentTypes::players).thenReturn(mock(com.mojang.brigadier.arguments.ArgumentType.class));
            dispatcher.register(new CustomDungeonCommand(plugin).tree());
            assertEquals(1,dispatcher.execute("customdungeon reload",source));
            assertEquals(List.of("build","live"),sequence);live.verify(LiveTestService::stopAll);intelligence.verify(IntelligenceService::reset);verify(definitions).reloadAsync(any());
        }
    }
}
