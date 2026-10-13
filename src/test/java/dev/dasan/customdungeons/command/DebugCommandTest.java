package dev.dasan.customdungeons.command;

import com.mojang.brigadier.CommandDispatcher;
import dev.dasan.customdungeons.CustomDungeonsPlugin;
import dev.dasan.customdungeons.config.DefinitionStore;
import dev.dasan.customdungeons.mob.DebugView;
import dev.dasan.customdungeons.session.SessionManager;
import dev.dasan.customdungeons.text.Messages;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import java.util.UUID;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DebugCommandTest {
    @AfterEach void reset() {DebugView.clear();}
    private static class Fixture {
        final CustomDungeonsPlugin plugin=mock(CustomDungeonsPlugin.class,RETURNS_DEEP_STUBS);
        final Messages messages=mock(Messages.class);
        final Player player=mock(Player.class);
        final CommandSourceStack source=mock(CommandSourceStack.class);
        final CommandDispatcher<CommandSourceStack> dispatcher=new CommandDispatcher<>();
        final CustomDungeonCommand command;
        Fixture() {
            when(plugin.messages()).thenReturn(messages);when(plugin.sessionManager()).thenReturn(mock(SessionManager.class));
            when(plugin.getServer().getServicesManager().load(DefinitionStore.class)).thenReturn(mock(DefinitionStore.class));
            when(player.getUniqueId()).thenReturn(UUID.randomUUID());when(source.getSender()).thenReturn(player);
            when(player.hasPermission("customdungeons.admin.test")).thenReturn(true);
            command=new CustomDungeonCommand(plugin);
            try(var arguments=mockStatic(io.papermc.paper.command.brigadier.argument.ArgumentTypes.class)) {
                arguments.when(io.papermc.paper.command.brigadier.argument.ArgumentTypes::players).thenReturn(mock(com.mojang.brigadier.arguments.ArgumentType.class));
                dispatcher.register(command.tree());
            }
        }
    }
    @Test void adminTestAloneCanToggleAndGetTabCompletionAndHelp() throws Exception {
        var f=new Fixture();
        assertEquals(1,f.dispatcher.execute("customdungeon debug",f.source));assertTrue(DebugView.enabled(f.player.getUniqueId()));
        verify(f.messages).send(f.player,"command.debug-on");
        var suggestions=f.dispatcher.getCompletionSuggestions(f.dispatcher.parse("customdungeon de",f.source)).get();
        assertTrue(suggestions.getList().stream().anyMatch(s->s.getText().equals("debug")));
        f.dispatcher.execute("customdungeon debug help",f.source);verify(f.messages).send(f.player,"command.debug-help");
        f.dispatcher.execute("customdungeon debug",f.source);assertFalse(DebugView.active());verify(f.messages).send(f.player,"command.debug-off");
    }
    @Test void senderPermissionIsRecheckedEvenAfterParsing() throws Exception {
        var f=new Fixture();var parsed=f.dispatcher.parse("customdungeon debug",f.source);
        when(f.player.hasPermission("customdungeons.admin.test")).thenReturn(false);
        when(f.player.hasPermission("customdungeons.admin.debug")).thenReturn(true);
        f.dispatcher.execute(parsed);assertFalse(DebugView.active());verify(f.messages).send(f.player,"command.no-permission");
        assertFalse(f.dispatcher.getRoot().getChild("customdungeon").getChild("debug").canUse(f.source));
    }
    @Test void executorCannotGrantSenderPermissionAndConsoleCannotEnableTheView() throws Exception {
        var f=new Fixture();var console=mock(ConsoleCommandSender.class);when(console.hasPermission("customdungeons.admin.test")).thenReturn(true);
        when(f.source.getSender()).thenReturn(console);when(f.source.getExecutor()).thenReturn(f.player);
        var parsed=f.dispatcher.parse("customdungeon debug",f.source);when(console.hasPermission("customdungeons.admin.test")).thenReturn(false);
        f.dispatcher.execute(parsed);verify(f.messages).send(console,"command.no-permission");assertFalse(DebugView.active());
        when(console.hasPermission("customdungeons.admin.test")).thenReturn(true);
        f.dispatcher.execute("customdungeon debug",f.source);verify(f.messages).send(console,"command.player-only");assertFalse(DebugView.active());
    }
    @Test void quitForgetsThePlayerAndDebugDoesNotGrantAdministrativeControls() throws Exception {
        var f=new Fixture();f.dispatcher.execute("customdungeon debug",f.source);
        when(f.plugin.sessionManager().sessionOf(f.player.getUniqueId())).thenReturn(java.util.Optional.of(mock(dev.dasan.customdungeons.session.DungeonSession.class)));
        assertFalse(f.dispatcher.getRoot().getChild("customdungeon").getChild("skipwave").canUse(f.source));
        var quit=mock(org.bukkit.event.player.PlayerQuitEvent.class);when(quit.getPlayer()).thenReturn(f.player);f.command.quit(quit);
        assertFalse(DebugView.active());
    }
}
