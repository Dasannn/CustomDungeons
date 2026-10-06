package dev.dasan.customdungeons.command;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class JoinSpamGuardTest {
    @Test void spamGuardSilencesWithinWindow() {
        var clock = new AtomicLong(100);
        var guard = new JoinSpamGuard(clock::get);
        var player = UUID.randomUUID();
        assertTrue(guard.allow(player));
        clock.addAndGet(2999);
        assertFalse(guard.allow(player));
        assertTrue(guard.allow(UUID.randomUUID()));
    }
    @Test void spamGuardAllowsAfterWindow() {
        var clock = new AtomicLong(100);
        var guard = new JoinSpamGuard(clock::get);
        var player = UUID.randomUUID();
        assertTrue(guard.allow(player));
        clock.addAndGet(2000);
        assertFalse(guard.allow(player));
        clock.addAndGet(1000);
        assertTrue(guard.allow(player));
        assertFalse(guard.allow(player));
    }
    @Test void forgettingPlayerAllowsImmediately() {
        var guard = new JoinSpamGuard(() -> 0L);
        var player = UUID.randomUUID();
        assertTrue(guard.allow(player));
        guard.forget(player);
        assertTrue(guard.allow(player));
    }

    private static final class CommandFixture {
        final dev.dasan.customdungeons.CustomDungeonsPlugin plugin = org.mockito.Mockito.mock(dev.dasan.customdungeons.CustomDungeonsPlugin.class);
        final org.bukkit.Server server = org.mockito.Mockito.mock(org.bukkit.Server.class);
        final org.bukkit.plugin.ServicesManager services = org.mockito.Mockito.mock(org.bukkit.plugin.ServicesManager.class);
        final dev.dasan.customdungeons.config.DefinitionStore definitions = org.mockito.Mockito.mock(dev.dasan.customdungeons.config.DefinitionStore.class);
        final dev.dasan.customdungeons.session.SessionManager sessions = org.mockito.Mockito.mock(dev.dasan.customdungeons.session.SessionManager.class);
        final org.bukkit.entity.Player player = org.mockito.Mockito.mock(org.bukkit.entity.Player.class);
        final io.papermc.paper.command.brigadier.CommandSourceStack source = org.mockito.Mockito.mock(io.papermc.paper.command.brigadier.CommandSourceStack.class);
        final com.mojang.brigadier.CommandDispatcher<io.papermc.paper.command.brigadier.CommandSourceStack> dispatcher = new com.mojang.brigadier.CommandDispatcher<>();
        CommandFixture() {
            org.mockito.Mockito.when(plugin.getServer()).thenReturn(server);
            org.mockito.Mockito.when(server.getServicesManager()).thenReturn(services);
            org.mockito.Mockito.when(services.load(dev.dasan.customdungeons.config.DefinitionStore.class)).thenReturn(definitions);
            org.mockito.Mockito.when(plugin.sessionManager()).thenReturn(sessions);
            var messages = new dev.dasan.customdungeons.text.Messages();
            var yaml = new org.bukkit.configuration.file.YamlConfiguration();
            try (var reader = new java.io.InputStreamReader(getClass().getResourceAsStream("/messages.yml"), java.nio.charset.StandardCharsets.UTF_8)) {
                yaml.load(reader);
            } catch (Exception e) { throw new AssertionError(e); }
            messages.load(yaml, "[CD] ");
            org.mockito.Mockito.when(plugin.messages()).thenReturn(messages);
            org.mockito.Mockito.when(player.getUniqueId()).thenReturn(UUID.randomUUID());
            org.mockito.Mockito.when(player.getName()).thenReturn("PortalPlayer");
            org.mockito.Mockito.when(source.getSender()).thenReturn(player);
            org.mockito.Mockito.when(server.getPlayerExact("PortalPlayer")).thenReturn(player);
            org.mockito.Mockito.when(player.hasPermission(org.mockito.ArgumentMatchers.startsWith("customdungeons.player."))).thenReturn(true);
            org.mockito.Mockito.when(sessions.join(player, "demo")).thenReturn(dev.dasan.customdungeons.session.JoinResult.OK);
            dispatcher.register(new CustomDungeonCommand(plugin).tree());
        }
    }
    @Test void normalPlayerOnlySeesPlayerCommands() {
        var f = new CommandFixture();
        // Paper sends the client only nodes whose requirement passes for its source.
        var children = f.dispatcher.getRoot().getChild("customdungeon").getChildren();
        assertEquals(java.util.Set.of("join", "leave", "stats", "claim"), children.stream()
                .filter(node -> node.canUse(f.source)).map(com.mojang.brigadier.tree.CommandNode::getName)
                .collect(java.util.stream.Collectors.toSet()));
    }
    @Test void portalPlayerMayNameThemselvesButCannotJoinOthers() throws Exception {
        var f = new CommandFixture();
        f.dispatcher.execute("customdungeon join PortalPlayer demo", f.source);
        org.mockito.Mockito.verify(f.sessions).join(f.player, "demo");
        var other = org.mockito.Mockito.mock(org.bukkit.entity.Player.class);
        org.mockito.Mockito.when(other.getUniqueId()).thenReturn(UUID.randomUUID());
        org.mockito.Mockito.when(f.server.getPlayerExact("OtherPlayer")).thenReturn(other);
        f.dispatcher.execute("customdungeon join OtherPlayer demo", f.source);
        org.mockito.Mockito.verify(f.sessions, org.mockito.Mockito.never()).join(other, "demo");
    }
    @Test void consolePortalMayJoinPlayerWithoutSenderJoinPermission() throws Exception {
        var f = new CommandFixture();
        org.mockito.Mockito.when(f.source.getSender()).thenReturn(org.mockito.Mockito.mock(org.bukkit.command.ConsoleCommandSender.class));
        f.dispatcher.execute("customdungeon join PortalPlayer demo", f.source);
        org.mockito.Mockito.verify(f.sessions).join(f.player, "demo");
    }
    @Test void namedJoinStillUsesTargetPermissionsAndThrottlesRejections() throws Exception {
        var f = new CommandFixture();
        org.mockito.Mockito.when(f.sessions.join(f.player, "demo")).thenReturn(dev.dasan.customdungeons.session.JoinResult.NO_PERMISSION);
        f.dispatcher.execute("customdungeon join PortalPlayer demo", f.source);
        f.dispatcher.execute("customdungeon join PortalPlayer demo", f.source);
        org.mockito.Mockito.verify(f.player, org.mockito.Mockito.times(1)).sendMessage(org.mockito.ArgumentMatchers.any(net.kyori.adventure.text.Component.class));
        org.mockito.Mockito.verify(f.sessions, org.mockito.Mockito.times(2)).join(f.player, "demo");
    }
    @Test void executorImpersonationCannotGrantSenderPermissions() {
        var f = new CommandFixture();
        org.mockito.Mockito.when(f.source.getExecutor()).thenReturn(org.mockito.Mockito.mock(org.bukkit.entity.Player.class));
        assertThrows(com.mojang.brigadier.exceptions.CommandSyntaxException.class,
                () -> f.dispatcher.execute("customdungeon stop demo", f.source));
        org.mockito.Mockito.verify(f.sessions, org.mockito.Mockito.never()).stop("demo");
    }
}
