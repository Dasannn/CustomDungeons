package dev.dasan.customdungeons.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.dasan.customdungeons.CustomDungeonsPlugin;
import dev.dasan.customdungeons.config.DefinitionStore;
import dev.dasan.customdungeons.mob.LiveTestService;
import dev.dasan.customdungeons.session.SessionManager;
import dev.dasan.customdungeons.text.Messages;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.plugin.ServicesManager;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LiveTestCommandTest {
    @Test void stopRequiresEditPermissionOnSenderAndStopsOnlyThatPlayer() throws Exception {
        var plugin=mock(CustomDungeonsPlugin.class);
        var server=mock(Server.class); var services=mock(ServicesManager.class);
        when(plugin.getServer()).thenReturn(server); when(server.getServicesManager()).thenReturn(services);
        when(services.load(DefinitionStore.class)).thenReturn(mock(DefinitionStore.class));
        when(plugin.sessionManager()).thenReturn(mock(SessionManager.class));
        when(plugin.messages()).thenReturn(mock(Messages.class));
        var dispatcher=new CommandDispatcher<CommandSourceStack>();
        try (var arguments = mockStatic(io.papermc.paper.command.brigadier.argument.ArgumentTypes.class)) {
                arguments.when(io.papermc.paper.command.brigadier.argument.ArgumentTypes::players)
                        .thenReturn(mock(com.mojang.brigadier.arguments.ArgumentType.class));
                dispatcher.register(new CustomDungeonCommand(plugin).tree());
            }
        var source=mock(CommandSourceStack.class); var sender=mock(Player.class); var executor=mock(Player.class);
        when(source.getSender()).thenReturn(sender); when(source.getExecutor()).thenReturn(executor);
        when(executor.hasPermission(anyString())).thenReturn(true);
        try(var live=mockStatic(LiveTestService.class)) {
            assertThrows(CommandSyntaxException.class,()->dispatcher.execute("customdungeon livetest stop",source));
            live.verifyNoInteractions();
            when(sender.hasPermission("customdungeons.admin.edit")).thenReturn(true);
            dispatcher.execute("customdungeon livetest stop",source);
            live.verify(()->LiveTestService.stop(sender));
            live.verifyNoMoreInteractions();
        }
    }
}
