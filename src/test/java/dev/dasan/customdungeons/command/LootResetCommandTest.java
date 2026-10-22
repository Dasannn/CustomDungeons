package dev.dasan.customdungeons.command;

import com.mojang.brigadier.CommandDispatcher;
import dev.dasan.customdungeons.CustomDungeonsPlugin;
import dev.dasan.customdungeons.config.DefinitionStore;
import dev.dasan.customdungeons.model.DungeonDef;
import dev.dasan.customdungeons.session.*;
import dev.dasan.customdungeons.text.Messages;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LootResetCommandTest {
    final CustomDungeonsPlugin plugin=mock(CustomDungeonsPlugin.class,RETURNS_DEEP_STUBS);
    final SessionManager sessions=mock(SessionManager.class);
    final CommandSender sender=mock(CommandSender.class);
    final CommandSourceStack source=mock(CommandSourceStack.class);
    final CommandDispatcher<CommandSourceStack> dispatcher=new CommandDispatcher<>();
    final Messages messages=new Messages();
    void sent(String key,net.kyori.adventure.text.minimessage.tag.resolver.TagResolver... placeholders) {
        var plain=net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText();
        String expected=plain.serialize(messages.get(key,placeholders));
        verify(sender).sendMessage(argThat((Component message)->plain.serialize(message).equals(expected)));
    }
    void setup(String resource) throws Exception {
        var definitions=mock(DefinitionStore.class);
        when(plugin.getServer().getServicesManager().load(DefinitionStore.class)).thenReturn(definitions);
        when(definitions.dungeons()).thenReturn(Map.of("test",mock(DungeonDef.class)));
        when(plugin.sessionManager()).thenReturn(sessions);when(plugin.messages()).thenReturn(messages);
        try(var reader=new InputStreamReader(Objects.requireNonNull(getClass().getResourceAsStream("/"+resource)),StandardCharsets.UTF_8)) {
            messages.load(YamlConfiguration.loadConfiguration(reader),"");
        }
        when(source.getSender()).thenReturn(sender);when(sender.hasPermission("customdungeons.admin.control")).thenReturn(true);
        try(var arguments=mockStatic(io.papermc.paper.command.brigadier.argument.ArgumentTypes.class)) {
            arguments.when(io.papermc.paper.command.brigadier.argument.ArgumentTypes::players).thenReturn(mock(com.mojang.brigadier.arguments.ArgumentType.class));
            dispatcher.register(new CustomDungeonCommand(plugin).tree());
        }
    }
    @ParameterizedTest @ValueSource(strings={"messages.yml","messages_en.yml"})
    void resetWithoutSessionConfirmsTheResolvedCountOnlyAfterDurableCompletion(String resource) throws Exception {
        setup(resource);var write=new CompletableFuture<Integer>();when(sessions.reset("test")).thenReturn(write);
        assertEquals(1,dispatcher.execute("customdungeon reset test",source));verify(sender,never()).sendMessage(any(Component.class));
        write.complete(2);sent("command.reset-loot-resolved",Placeholder.unparsed("count","2"));
    }
    @Test void resetWithoutAmbiguousRecordsKeepsTheNoSessionMessage() throws Exception {
        setup("messages.yml");when(sessions.reset("test")).thenReturn(CompletableFuture.completedFuture(0));
        dispatcher.execute("customdungeon reset test",source);sent("command.no-session");
    }
    @Test void resetWithSessionKeepsExistingConfirmationAndStopDoesNotResolve() throws Exception {
        setup("messages.yml");var session=mock(DungeonSession.class);var state=new SessionStateMachine();
        state.openLobby();when(session.state()).thenReturn(state);when(sessions.session("test")).thenReturn(Optional.of(session));
        dispatcher.execute("customdungeon reset test",source);verify(sessions).reset("test");sent("command.reset");
        dispatcher.execute("customdungeon stop test",source);verify(sessions).stop("test");verify(sessions,times(1)).reset("test");
    }
    @Test void failedDiscardDoesNotClaimSuccess() throws Exception {
        setup("messages.yml");when(sessions.reset("test")).thenReturn(CompletableFuture.failedFuture(new IllegalStateException("disk")));
        dispatcher.execute("customdungeon reset test",source);sent("loot.persistence-failed");
    }
    @Test void resetStillRequiresAdminControl() throws Exception {
        setup("messages.yml");when(sender.hasPermission("customdungeons.admin.control")).thenReturn(false);
        assertThrows(com.mojang.brigadier.exceptions.CommandSyntaxException.class,()->dispatcher.execute("customdungeon reset test",source));
        verify(sessions,never()).reset(anyString());
    }
}
