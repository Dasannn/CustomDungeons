package dev.dasan.customdungeons.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.dasan.customdungeons.CustomDungeonsPlugin;
import dev.dasan.customdungeons.config.DefinitionStore;
import dev.dasan.customdungeons.session.SessionManager;
import dev.dasan.customdungeons.text.Messages;
import dev.dasan.customdungeons.tool.ToolService;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.Server;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.ServicesManager;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ToolClearCommandTest {
    @Test void clearRequiresToolsPermissionAndTargetsSenderIncludingSuggestions() throws Exception {
        var plugin = mock(CustomDungeonsPlugin.class);
        var server = mock(Server.class);
        var services = mock(ServicesManager.class);
        var tools = mock(ToolService.class);
        when(plugin.getServer()).thenReturn(server);
        when(server.getServicesManager()).thenReturn(services);
        when(services.load(DefinitionStore.class)).thenReturn(mock(DefinitionStore.class));
        when(services.load(ToolService.class)).thenReturn(tools);
        when(plugin.sessionManager()).thenReturn(mock(SessionManager.class));
        var messages = mock(Messages.class);
        when(plugin.messages()).thenReturn(messages);
        var dispatcher = new CommandDispatcher<CommandSourceStack>();
        dispatcher.register(new CustomDungeonCommand(plugin).tree());
        var source = mock(CommandSourceStack.class);
        var sender = mock(Player.class);
        when(source.getSender()).thenReturn(sender);
        assertThrows(CommandSyntaxException.class, () -> dispatcher.execute("customdungeon tool clear", source));
        verifyNoInteractions(tools);
        when(sender.hasPermission("customdungeons.admin.tools")).thenReturn(true);
        assertTrue(dispatcher.getCompletionSuggestions(dispatcher.parse("customdungeon tool cl", source)).get()
                .getList().stream().anyMatch(s -> s.getText().equals("clear")));
        dispatcher.execute("customdungeon tool clear", source);
        verify(tools).clearTools(sender);
        var console = mock(ConsoleCommandSender.class);
        when(console.hasPermission("customdungeons.admin.tools")).thenReturn(true);
        when(source.getSender()).thenReturn(console);
        dispatcher.execute("customdungeon tool clear", source);
        verify(messages).send(console, "command.player-only");
        verifyNoMoreInteractions(tools);
    }
}
