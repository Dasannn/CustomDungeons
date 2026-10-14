package dev.dasan.customdungeons.command;

import com.mojang.brigadier.CommandDispatcher;
import dev.dasan.customdungeons.CustomDungeonsPlugin;
import dev.dasan.customdungeons.ability.AbilityEngine;
import dev.dasan.customdungeons.config.DefinitionStore;
import dev.dasan.customdungeons.mob.DebugView;
import dev.dasan.customdungeons.mob.MobKeys;
import dev.dasan.customdungeons.model.DungeonDef;
import dev.dasan.customdungeons.runtime.ActiveMob;
import dev.dasan.customdungeons.runtime.TickScheduler;
import dev.dasan.customdungeons.session.*;
import dev.dasan.customdungeons.text.Messages;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DebugCommandTest {
    static { dev.dasan.customdungeons.ability.impl.PaperApiTestBootstrap.initialize(); }
    @AfterEach void reset() { DebugView.clear(); }

    private static Player admin() {
        var player=mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.isOnline()).thenReturn(true);
        when(player.hasPermission("customdungeons.admin.debug")).thenReturn(true);
        return player;
    }
    private static class Fixture {
        final CustomDungeonsPlugin plugin=mock(CustomDungeonsPlugin.class,RETURNS_DEEP_STUBS);
        final Messages messages=spy(new Messages());
        final Logger logger=mock(Logger.class);
        final SessionManager sessions=mock(SessionManager.class);
        final Player player=admin();
        final CommandSourceStack source=mock(CommandSourceStack.class);
        final CommandDispatcher<CommandSourceStack> dispatcher=new CommandDispatcher<>();
        final CustomDungeonCommand command;
        Fixture() {
            messages.load(org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(new InputStreamReader(
                    Objects.requireNonNull(getClass().getResourceAsStream("/messages.yml")),StandardCharsets.UTF_8)),"");
            when(plugin.messages()).thenReturn(messages);when(plugin.sessionManager()).thenReturn(sessions);
            when(plugin.getLogger()).thenReturn(logger);when(plugin.isEnabled()).thenReturn(true);
            when(plugin.getServer().getServicesManager().load(DefinitionStore.class)).thenReturn(mock(DefinitionStore.class));
            when(source.getSender()).thenReturn(player);
            command=new CustomDungeonCommand(plugin);
            command.sessionsListener();
            try(var arguments=mockStatic(io.papermc.paper.command.brigadier.argument.ArgumentTypes.class)) {
                arguments.when(io.papermc.paper.command.brigadier.argument.ArgumentTypes::players).thenReturn(mock(com.mojang.brigadier.arguments.ArgumentType.class));
                dispatcher.register(command.tree());
            }
        }
        void toggle() throws Exception { assertEquals(1,dispatcher.execute("customdungeon debug",source)); }
        SessionLifecycleListener lifecycle() {
            var listener=ArgumentCaptor.forClass(SessionLifecycleListener.class);
            verify(sessions).addListener(listener.capture());return listener.getValue();
        }
        boolean control(String action) { return dispatcher.getRoot().getChild("customdungeon").getChild(action).canUse(source); }
        void quit() {
            var event=mock(PlayerQuitEvent.class);when(event.getPlayer()).thenReturn(player);command.quit(event);
        }
    }
    private static class SessionFixture {
        final DungeonSession session=mock(DungeonSession.class);
        final SessionStateMachine state=new SessionStateMachine();
        final TickScheduler scheduler=mock(TickScheduler.class);
        final AtomicLong tick=new AtomicLong();
        final Deque<Runnable> queued=new ArrayDeque<>();
        SessionFixture(Fixture command) {
            var def=mock(DungeonDef.class);when(def.id()).thenReturn("crypt");
            when(session.def()).thenReturn(def);when(session.id()).thenReturn(UUID.randomUUID());
            when(session.state()).thenReturn(state);when(session.players()).thenReturn(List.of(command.player));
            when(session.scheduler()).thenReturn(scheduler);when(scheduler.currentTick()).thenAnswer(i->tick.get());
            when(session.roomIndex()).thenReturn(2);when(session.waveNumber()).thenReturn(3);
            doAnswer(i->{assertEquals(1,i.getArgument(0,Integer.class).intValue());queued.add(i.getArgument(1));return null;})
                    .when(scheduler).runLater(anyInt(),any(Runnable.class));
            when(command.sessions.sessionOf(command.player.getUniqueId())).thenReturn(Optional.of(session));
            state.openLobby();
        }
        void advance() { tick.incrementAndGet();queued.remove().run(); }
        void running() { state.start(); }
    }
    @Test void adminDebugAloneCanToggleAndGetTabCompletionAndHelp() throws Exception {
        var f=new Fixture();f.toggle();assertTrue(DebugView.enabled(f.player.getUniqueId()));
        assertFalse(f.control("skipwave"));assertFalse(f.control("invulnerable"));
        verify(f.messages).send(f.player,"command.debug-on");
        var suggestions=f.dispatcher.getCompletionSuggestions(f.dispatcher.parse("customdungeon de",f.source)).get();
        assertTrue(suggestions.getList().stream().anyMatch(s->s.getText().equals("debug")));
        f.dispatcher.execute("customdungeon debug help",f.source);verify(f.messages).send(f.player,"command.debug-help");
        f.toggle();assertFalse(DebugView.active());verify(f.messages).send(f.player,"command.debug-off");
        verify(f.player,times(2)).updateCommands();
    }
    @Test void adminTestAloneCannotToggleDebugButKeepsTestMatchControls() throws Exception {
        var f=new Fixture();var s=new SessionFixture(f);s.running();
        when(f.player.hasPermission("customdungeons.admin.debug")).thenReturn(false);
        when(f.player.hasPermission("customdungeons.admin.test")).thenReturn(true);
        assertThrows(com.mojang.brigadier.exceptions.CommandSyntaxException.class,f::toggle);
        assertFalse(DebugView.toggle(f.player));assertFalse(DebugView.active());
        assertFalse(f.control("skipwave"));assertFalse(f.control("invulnerable"));
        when(s.session.testMode()).thenReturn(true);
        assertTrue(f.control("skipwave"));assertTrue(f.control("invulnerable"));
        f.dispatcher.execute("customdungeon skipwave",f.source);verify(s.session).skipWave();
    }
    @Test void senderPermissionIsRecheckedEvenAfterParsing() throws Exception {
        var f=new Fixture();var parsed=f.dispatcher.parse("customdungeon debug",f.source);
        var help=f.dispatcher.parse("customdungeon debug help",f.source);
        when(f.player.hasPermission("customdungeons.admin.debug")).thenReturn(false);
        when(f.player.hasPermission("customdungeons.admin.test")).thenReturn(true);
        f.dispatcher.execute(parsed);f.dispatcher.execute(help);assertFalse(DebugView.active());
        verify(f.messages,times(2)).send(f.player,"command.no-permission");
        assertFalse(f.dispatcher.getRoot().getChild("customdungeon").getChild("debug").canUse(f.source));
    }
    @Test void executorCannotGrantSenderPermissionAndConsoleCannotEnableTheView() throws Exception {
        var f=new Fixture();var console=mock(ConsoleCommandSender.class);when(console.hasPermission("customdungeons.admin.debug")).thenReturn(true);
        when(f.source.getSender()).thenReturn(console);when(f.source.getExecutor()).thenReturn(f.player);
        var parsed=f.dispatcher.parse("customdungeon debug",f.source);when(console.hasPermission("customdungeons.admin.debug")).thenReturn(false);
        f.dispatcher.execute(parsed);verify(f.messages).send(console,"command.no-permission");assertFalse(DebugView.active());
        when(console.hasPermission("customdungeons.admin.debug")).thenReturn(true);
        f.dispatcher.execute("customdungeon debug",f.source);verify(f.messages).send(console,"command.player-only");assertFalse(DebugView.active());
    }
    @Test void oneToggleEnablesSessionTicksMobActionBarAndControlsThenDisablesAllThree() throws Exception {
        var f=new Fixture();var s=new SessionFixture(f);s.running();
        var mob=mock(ActiveMob.class);var entity=mock(Mob.class);var data=mock(PersistentDataContainer.class);
        when(mob.entity()).thenReturn(entity);when(entity.getPersistentDataContainer()).thenReturn(data);
        when(data.has(MobKeys.SESSION,PersistentDataType.STRING)).thenReturn(true);
        when(data.has(MobKeys.TEMPLATE,PersistentDataType.STRING)).thenReturn(true);
        when(entity.isValid()).thenReturn(true);when(entity.hasAI()).thenReturn(true);when(entity.name()).thenReturn(Component.text("Custodio"));
        when(f.player.getTargetEntity(24,false)).thenReturn(entity);when(s.session.mobs()).thenReturn(List.of(mob));
        f.toggle();assertEquals(1,s.queued.size());
        verify(f.logger).info("Sesión crypt tick=0 state=RUNNING room=2 wave=3 mobs=1");
        assertTrue(f.control("skipwave"));assertTrue(f.control("invulnerable"));
        f.dispatcher.execute("customdungeon skipwave",f.source);f.dispatcher.execute("customdungeon invulnerable",f.source);
        verify(s.session).skipWave();verify(s.session).setInvulnerable(f.player.getUniqueId(),true);
        verify(f.messages).send(f.player,"command.skipwave");verify(f.messages).send(f.player,"command.invulnerable");
        try(var bukkit=mockStatic(Bukkit.class)) {
            bukkit.when(()->Bukkit.getPlayer(f.player.getUniqueId())).thenReturn(f.player);
            DebugView.tick(List.of(mob),mock(AbilityEngine.class),f.messages,0);
            verify(f.player).sendActionBar(argThat((Component c)->net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(c).contains("Custodio")));
            s.advance();verify(f.logger).info("Sesión crypt tick=1 state=RUNNING room=2 wave=3 mobs=1");
            f.toggle();verify(f.player).sendActionBar(Component.empty());
            assertFalse(f.control("skipwave"));assertFalse(f.control("invulnerable"));
            clearInvocations(f.player,f.logger);s.advance();
            DebugView.tick(List.of(mob),mock(AbilityEngine.class),f.messages,5);
            verifyNoInteractions(f.logger);verify(f.player,never()).getTargetEntity(anyInt(),anyBoolean());
            assertTrue(s.queued.isEmpty());
        }
    }
    @Test void lobbyToggleStartsLoggingOnRunningAndStopsOnFree() throws Exception {
        var f=new Fixture();var s=new SessionFixture(f);var listener=f.lifecycle();f.toggle();
        verifyNoInteractions(f.logger);assertTrue(s.queued.isEmpty());
        s.running();listener.onStateChange(s.session,SessionState.LOBBY,SessionState.RUNNING);
        verify(f.logger).info("Sesión crypt tick=0 state=RUNNING room=2 wave=3 mobs=0");
        s.state.complete();s.state.beginReset();s.state.finishReset();
        listener.onStateChange(s.session,SessionState.RESETTING,SessionState.FREE);
        clearInvocations(f.logger);s.advance();verifyNoInteractions(f.logger);assertTrue(s.queued.isEmpty());
    }
    @Test void multipleAdminsAndRapidTogglesNeverDuplicateSessionLogging() throws Exception {
        var f=new Fixture();var s=new SessionFixture(f);s.running();var second=admin();
        when(s.session.players()).thenReturn(List.of(f.player,second));when(f.sessions.sessionOf(second.getUniqueId())).thenReturn(Optional.of(s.session));
        f.toggle();when(f.source.getSender()).thenReturn(second);f.toggle();
        assertEquals(1,s.queued.size());verify(f.logger,times(1)).info(anyString());
        when(f.source.getSender()).thenReturn(f.player);f.toggle();f.toggle();
        assertEquals(1,s.queued.size());verify(f.logger,times(1)).info(anyString());
        f.quit();s.advance();verify(f.logger,times(2)).info(anyString());
        when(f.source.getSender()).thenReturn(second);f.toggle();s.advance();
        verify(f.logger,times(2)).info(anyString());assertTrue(s.queued.isEmpty());assertFalse(DebugView.active());
    }
    @org.junit.jupiter.api.io.TempDir java.nio.file.Path directory;
    @Test void reloadClearsDebugUsersAndTheirActionBarBeforeReloadingDefinitions() throws Exception {
        var f=new Fixture();f.toggle();
        var definitions=f.plugin.getServer().getServicesManager().load(DefinitionStore.class);
        when(definitions.dungeons()).thenReturn(Map.of());
        when(definitions.reloadAsync(any())).thenReturn(java.util.concurrent.CompletableFuture.completedFuture(null));
        when(f.player.hasPermission("customdungeons.admin.reload")).thenReturn(true);
        var server=f.plugin.getServer();doReturn(List.of(f.player)).when(server).getOnlinePlayers();
        var view=mock(org.bukkit.inventory.InventoryView.class,RETURNS_DEEP_STUBS);
        when(f.player.getOpenInventory()).thenReturn(view);
        when(f.plugin.getDataFolder()).thenReturn(directory.toFile());
        when(f.plugin.getConfig()).thenReturn(new org.bukkit.configuration.file.YamlConfiguration());
        when(f.plugin.getResource(anyString())).thenAnswer(i->new java.io.ByteArrayInputStream("{}".getBytes(StandardCharsets.UTF_8)));
        try(var migration=mockStatic(dev.dasan.customdungeons.config.ConfigMigration.class);
            var bukkit=mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
            f.dispatcher.execute("customdungeon reload",f.source);
            assertFalse(DebugView.active());verify(f.player).sendActionBar(Component.empty());
            verify(definitions).reloadAsync(any());verify(f.messages).send(f.player,"command.reloaded");
        }
    }
    @Test void quitAndRevokedPermissionStopLoggingAndControlsWithoutAnActionBarTick() throws Exception {
        for(boolean quit:new boolean[]{false,true}) {
            var f=new Fixture();var s=new SessionFixture(f);s.running();f.toggle();
            var skip=f.dispatcher.parse("customdungeon skipwave",f.source);
            var invulnerable=f.dispatcher.parse("customdungeon invulnerable",f.source);
            if(quit)f.quit();else {
                when(f.player.hasPermission("customdungeons.admin.debug")).thenReturn(false);
                when(f.player.hasPermission("customdungeons.admin.test")).thenReturn(true);
            }
            f.dispatcher.execute(skip);f.dispatcher.execute(invulnerable);
            verify(s.session,never()).skipWave();verify(s.session,never()).setInvulnerable(any(),anyBoolean());
            clearInvocations(f.logger);s.advance();verifyNoInteractions(f.logger);assertTrue(s.queued.isEmpty());
            assertFalse(f.control("skipwave"));assertFalse(f.control("invulnerable"));DebugView.clear();
        }
    }
}
