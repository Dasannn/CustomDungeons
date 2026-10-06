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
        CommandFixture() { this("messages.yml"); }
        CommandFixture(String resource) {
            org.mockito.Mockito.when(plugin.getServer()).thenReturn(server);
            org.mockito.Mockito.when(server.getServicesManager()).thenReturn(services);
            org.mockito.Mockito.when(services.load(dev.dasan.customdungeons.config.DefinitionStore.class)).thenReturn(definitions);
            org.mockito.Mockito.when(plugin.sessionManager()).thenReturn(sessions);
            var messages = new dev.dasan.customdungeons.text.Messages();
            var yaml = new org.bukkit.configuration.file.YamlConfiguration();
            try (var reader = new java.io.InputStreamReader(getClass().getResourceAsStream("/" + resource), java.nio.charset.StandardCharsets.UTF_8)) {
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
            try (var arguments = org.mockito.Mockito.mockStatic(io.papermc.paper.command.brigadier.argument.ArgumentTypes.class)) {
                arguments.when(io.papermc.paper.command.brigadier.argument.ArgumentTypes::players)
                        .thenReturn(org.mockito.Mockito.mock(com.mojang.brigadier.arguments.ArgumentType.class));
                dispatcher.register(new CustomDungeonCommand(plugin).tree());
            }
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

    @org.junit.jupiter.api.io.TempDir java.nio.file.Path directory;

    @Test void reloadNamesInvalidYamlFileAndLogsDiagnosticWithoutAdminValues() throws Exception {
        for (String language : java.util.List.of("messages.yml", "messages_en.yml")) {
            for (String file : java.util.List.of("config.yml", "messages.yml", "messages_en.yml")) {
                var data = java.nio.file.Files.createTempDirectory(directory, "yaml-");
                java.nio.file.Files.writeString(data.resolve(file), "private: [PRIVATE_VALUE\n");
                assertMigrationFailure(language, file, data, "YAML inválido", "invalid YAML");
                assertEquals("private: [PRIVATE_VALUE\n", java.nio.file.Files.readString(data.resolve(file)));
            }
        }
    }

    @Test void reloadNamesIoFailureFileAndLogsDiagnostic() throws Exception {
        for (String language : java.util.List.of("messages.yml", "messages_en.yml")) {
            for (String file : java.util.List.of("config.yml", "messages.yml", "messages_en.yml")) {
                var data = java.nio.file.Files.createTempDirectory(directory, "io-");
                java.nio.file.Files.createDirectory(data.resolve(file));
                assertMigrationFailure(language, file, data, "no se pudo escribir", "could not write");
                assertTrue(java.nio.file.Files.isDirectory(data.resolve(file)));
            }
        }
    }

    private void assertMigrationFailure(String language, String file, java.nio.file.Path data,
                                        String spanishReason, String englishReason) throws Exception {
        var f = new CommandFixture(language);
        var logger = org.mockito.Mockito.mock(java.util.logging.Logger.class);
        org.mockito.Mockito.when(f.plugin.getDataFolder()).thenReturn(data.toFile());
        org.mockito.Mockito.when(f.plugin.getLogger()).thenReturn(logger);
        org.mockito.Mockito.when(f.player.hasPermission("customdungeons.admin.reload")).thenReturn(true);
        org.mockito.Mockito.when(f.server.getOnlinePlayers()).thenReturn(java.util.List.of());
        f.dispatcher.execute("customdungeon reload", f.source);
        var message = org.mockito.ArgumentCaptor.forClass(net.kyori.adventure.text.Component.class);
        org.mockito.Mockito.verify(f.player).sendMessage(message.capture());
        String plain = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                .serialize(message.getValue());
        assertTrue(plain.contains(file), plain);
        assertTrue(plain.contains(language.equals("messages.yml") ? spanishReason : englishReason), plain);
        assertFalse(plain.contains("PRIVATE_VALUE"));
        var diagnostic = org.mockito.ArgumentCaptor.forClass(Throwable.class);
        org.mockito.Mockito.verify(logger).log(org.mockito.Mockito.eq(java.util.logging.Level.SEVERE),
                org.mockito.Mockito.contains(file), diagnostic.capture());
        assertNotNull(diagnostic.getValue().getCause());
        var trace = new java.io.StringWriter();
        diagnostic.getValue().printStackTrace(new java.io.PrintWriter(trace));
        assertFalse(trace.toString().contains("PRIVATE_VALUE"));
        assertTrue(trace.toString().contains("Caused by:"));
        if (spanishReason.equals("YAML inválido")) {
            assertTrue(trace.toString().contains("InvalidConfigurationException"));
            assertTrue(trace.toString().contains("línea"));
            assertTrue(trace.toString().contains("columna"));
        }
        org.mockito.Mockito.verify(f.plugin, org.mockito.Mockito.never()).reloadConfig();
        org.mockito.Mockito.verify(f.definitions, org.mockito.Mockito.never()).reloadAsync(org.mockito.Mockito.any());
    }

    @Test void reloadRejectsLobbyAndRunningSessions() throws Exception {
        for (boolean running : new boolean[] {false, true}) {
            var f = new CommandFixture();
            org.mockito.Mockito.when(f.player.hasPermission("customdungeons.admin.reload")).thenReturn(true);
            org.mockito.Mockito.when(f.definitions.dungeons()).thenReturn(java.util.Map.of("demo",
                    org.mockito.Mockito.mock(dev.dasan.customdungeons.model.DungeonDef.class)));
            var session = org.mockito.Mockito.mock(dev.dasan.customdungeons.session.DungeonSession.class);
            var state = new dev.dasan.customdungeons.session.SessionStateMachine();
            state.openLobby();
            if (running) state.start();
            org.mockito.Mockito.when(session.state()).thenReturn(state);
            org.mockito.Mockito.when(f.sessions.session("demo")).thenReturn(java.util.Optional.of(session));

            f.dispatcher.execute("customdungeon reload", f.source);

            org.mockito.Mockito.verify(f.plugin, org.mockito.Mockito.never()).reloadConfig();
            org.mockito.Mockito.verify(f.definitions, org.mockito.Mockito.never()).reload();
            org.mockito.Mockito.verify(f.player).sendMessage(org.mockito.ArgumentMatchers.argThat(
                    (net.kyori.adventure.text.Component message) ->
                            net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                                    .serialize(message).contains("No se puede recargar mientras haya partidas activas.")));
        }
    }
}
