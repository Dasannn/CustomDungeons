package dev.dasan.customdungeons.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.dasan.customdungeons.CustomDungeonsPlugin;
import dev.dasan.customdungeons.config.DefinitionStore;
import dev.dasan.customdungeons.session.*;
import dev.dasan.customdungeons.text.Messages;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver;
import java.util.*;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class KeyCommandTest {
    final CustomDungeonsPlugin plugin = mock(CustomDungeonsPlugin.class, RETURNS_DEEP_STUBS);
    final SessionManager sessions = mock(SessionManager.class);
    final Messages messages = mock(Messages.class);
    final Player first = mock(Player.class), second = mock(Player.class);
    final CommandSourceStack source = mock(CommandSourceStack.class);
    final Map<String,List<Player>> targets = Map.of("Ana",List.of(first), "@p",List.of(first), "@a",List.of(first,second));
    final List<CommandSourceStack> resolvedSources = new ArrayList<>();
    final List<String> resolvedTargets = new ArrayList<>();
    CommandDispatcher<CommandSourceStack> dispatcher() {
        when(first.getName()).thenReturn("Ana"); when(second.getName()).thenReturn("Luis");
        when(plugin.sessionManager()).thenReturn(sessions); when(plugin.messages()).thenReturn(messages);
        when(plugin.getServer().getServicesManager().load(DefinitionStore.class)).thenReturn(mock(DefinitionStore.class));
        ArgumentType<PlayerSelectorArgumentResolver> argument = reader -> {
            int start = reader.getCursor();
            while (reader.canRead() && reader.peek()!=' ') reader.skip();
            String token = reader.getString().substring(start,reader.getCursor());
            return stack -> { resolvedSources.add(stack); resolvedTargets.add(token); return targets.get(token); };
        };
        var dispatcher = new CommandDispatcher<CommandSourceStack>();
        try (var types = mockStatic(ArgumentTypes.class)) {
            types.when(ArgumentTypes::players).thenReturn(argument);
            dispatcher.register(new CustomDungeonCommand(plugin).tree());
        }
        return dispatcher;
    }
    @Test void consoleAndCommandBlockResolveNamesAndSelectorsUsingOriginalSource() throws Exception {
        var dispatcher = dispatcher();
        for (CommandSender sender : List.of(mock(ConsoleCommandSender.class),mock(BlockCommandSender.class))) {
            when(sender.hasPermission("customdungeons.admin.key")).thenReturn(true); when(source.getSender()).thenReturn(sender);
            try (var keys = mockStatic(KeyService.class)) {
                keys.when(() -> KeyService.give(sessions,first,null)).thenReturn(KeyService.GiveResult.GIVEN);
                keys.when(() -> KeyService.give(sessions,second,null)).thenReturn(KeyService.GiveResult.GIVEN);
                for (String target : List.of("Ana","@p","@a")) {
                    assertEquals(targets.get(target).size(),dispatcher.execute("customdungeon key give "+target,source));
                    keys.verify(() -> KeyService.give(sessions,first,null),atLeastOnce());
                }
                keys.verify(() -> KeyService.give(sessions,second,null));
            }
        }
        assertEquals(List.of("Ana","@p","@a","Ana","@p","@a"),resolvedTargets);
        assertTrue(resolvedSources.stream().allMatch(s -> s==source));
    }
    @Test void explicitDungeonRejectsTargetsOutsideThatRunAndReportsEachResult() throws Exception {
        var dispatcher = dispatcher(); var sender = mock(ConsoleCommandSender.class);
        when(sender.hasPermission("customdungeons.admin.key")).thenReturn(true); when(source.getSender()).thenReturn(sender);
        try (var keys = mockStatic(KeyService.class)) {
            keys.when(() -> KeyService.give(sessions,first,"puzzle")).thenReturn(KeyService.GiveResult.GIVEN);
            keys.when(() -> KeyService.give(sessions,second,"puzzle")).thenReturn(KeyService.GiveResult.NO_SESSION);
            assertEquals(1,dispatcher.execute("customdungeon key give @a puzzle",source));
            keys.verify(() -> KeyService.give(sessions,first,"puzzle"));
            keys.verify(() -> KeyService.give(sessions,second,"puzzle"));
            verify(messages).send(eq(sender),eq("command.key-no-session"),any(net.kyori.adventure.text.minimessage.tag.resolver.TagResolver[].class));
        }
    }
    @Test void permissionUsesSenderAndRestrictsKeyNode() throws Exception {
        var dispatcher = dispatcher(); var sender = mock(Player.class); var executor = mock(Player.class);
        when(source.getSender()).thenReturn(sender); when(source.getExecutor()).thenReturn(executor);
        when(executor.hasPermission(anyString())).thenReturn(true);
        assertThrows(CommandSyntaxException.class,() -> dispatcher.execute("customdungeon key give @a",source));
        assertFalse(dispatcher.getRoot().getChild("customdungeon").getChild("key").canUse(source));
        assertTrue(resolvedSources.isEmpty()); verifyNoInteractions(sessions);
    }
}
