package dev.dasan.customdungeons.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import dev.dasan.customdungeons.CustomDungeonsPlugin;
import dev.dasan.customdungeons.config.DefinitionStore;
import dev.dasan.customdungeons.config.PluginConfig;
import dev.dasan.customdungeons.gui.menu.DungeonListMenu;
import dev.dasan.customdungeons.session.*;
import dev.dasan.customdungeons.tool.*;
import dev.dasan.customdungeons.update.UpdateService;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

/** Command boundary: permissions use the sender (never an /execute impersonated executor). */
public final class CustomDungeonCommand implements Listener {
    private final CustomDungeonsPlugin plugin;
    private final DefinitionStore definitions;
    private final SessionManager sessions;
    private final JoinSpamGuard spam = new JoinSpamGuard();
    private final Set<UUID> debug = new HashSet<>();
    private final Set<UUID> loggingSessions = new HashSet<>();

    CustomDungeonCommand(CustomDungeonsPlugin plugin) {
        this.plugin = plugin;
        definitions = service(DefinitionStore.class);
        sessions = plugin.sessionManager();
    }
    public static void register(CustomDungeonsPlugin plugin) {
        var command = new CustomDungeonCommand(plugin);
        command.loadMessages();
        command.updateStartup();
        DungeonListMenu.dungeonBusy(command::busy);
        plugin.getServer().getPluginManager().registerEvents(command, plugin);
        sessionsListener(command);
        plugin.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event ->
                event.registrar().register(command.tree().build(), command.service(PluginConfig.class).commandAliases()));
    }
    private static void sessionsListener(CustomDungeonCommand command) {
        command.sessions.addListener(new SessionLifecycleListener() {
            @Override public void onStateChange(DungeonSession session, SessionState from, SessionState to) {
                if (to == SessionState.FREE) command.loggingSessions.remove(session.id());
                else if (to == SessionState.RUNNING) command.ensureLogging(session);
            }
        });
    }
    private <T> T service(Class<T> type) {
        return Objects.requireNonNull(plugin.getServer().getServicesManager().load(type));
    }
    private boolean busy(String id) {
        return sessions.session(id).filter(s -> s.state().state() != SessionState.FREE).isPresent();
    }
    private boolean permitted(CommandSourceStack source, String permission) {
        return source.getSender().hasPermission("customdungeons." + permission);
    }
    private boolean others(CommandSender sender) {
        return sender instanceof ConsoleCommandSender || sender.hasPermission("customdungeons.admin.join.others");
    }
    private LiteralArgumentBuilder<CommandSourceStack> node(String name, String permission) {
        return Commands.literal(name).requires(source -> permitted(source, permission));
    }
    private RequiredArgumentBuilder<CommandSourceStack, String> dungeon() {
        return Commands.argument("dungeon", StringArgumentType.word()).suggests((ctx, builder) -> {
            definitions.dungeons().keySet().stream().sorted().filter(id -> id.startsWith(builder.getRemainingLowerCase())).forEach(builder::suggest);
            return builder.buildFuture();
        });
    }
    LiteralArgumentBuilder<CommandSourceStack> tree() {
        var root = Commands.literal("customdungeon").executes(ctx -> player(ctx, "admin.edit", p -> {
            new DungeonListMenu(p).open(); send(p, "command.menu-opened");
        }));
        root.then(node("create","admin.edit").then(Commands.argument("id",StringArgumentType.word())
                .executes(ctx->player(ctx,"admin.edit",p->new DungeonListMenu(p).openWizard(StringArgumentType.getString(ctx,"id"))))));
        root.then(Commands.literal("join").requires(s -> permitted(s, "player.join") || others(s.getSender()))
            .executes(ctx -> reply(ctx, "command.join-usage"))
            .then(Commands.argument("target", StringArgumentType.word()).suggests((ctx, builder) -> {
                var sender = ctx.getSource().getSender();
                var names = new TreeSet<String>(definitions.dungeons().keySet());
                if (others(sender)) plugin.getServer().getOnlinePlayers().forEach(p -> names.add(p.getName()));
                else if (sender instanceof Player p) names.add(p.getName());
                names.stream().filter(n -> n.toLowerCase(Locale.ROOT).startsWith(builder.getRemainingLowerCase())).forEach(builder::suggest);
                return builder.buildFuture();
            }).executes(ctx -> player(ctx, "player.join", p -> join(p, StringArgumentType.getString(ctx, "target"))))
            .then(dungeon().executes(this::joinNamed))));
        root.then(node("leave", "player.leave").executes(ctx -> player(ctx, "player.leave", p -> {
            if (sessions.sessionOf(p.getUniqueId()).isEmpty()) send(p, "command.no-session");
            else { sessions.leave(p); send(p, "command.left"); }
        })));
        root.then(node("stats", "player.stats").executes(ctx -> player(ctx, "player.stats", p ->
            complete(p, plugin.storage().stats(p.getUniqueId()), stats -> plugin.messages().send(p, "command.stats",
                Placeholder.unparsed("runs", "" + stats.runs()), Placeholder.unparsed("completions", "" + stats.completions()),
                Placeholder.unparsed("kills", "" + stats.kills()), Placeholder.unparsed("deaths", "" + stats.deaths()))))));
        root.then(node("claim", "player.claim").executes(ctx -> player(ctx, "player.claim", p -> {
            var handler = plugin.getServer().getServicesManager().load(ClaimHandler.class);
            if (handler == null) send(p, "command.claim-unavailable");
            else complete(p, handler.claim(p), count -> plugin.messages().send(p, "command.claimed", Placeholder.unparsed("count", "" + count)));
        })));
        root.then(node("tool", "admin.tools").executes(ctx -> reply(ctx, "command.tool-usage"))
            .then(Commands.literal("clear").executes(ctx -> player(ctx, "admin.tools", p -> service(ToolService.class).clearTools(p))))
            .then(Commands.argument("type", StringArgumentType.word()).suggests((ctx, builder) -> {
                for (ToolType type : ToolType.values()) {
                    String name = type.name().toLowerCase(Locale.ROOT);
                    if (name.startsWith(builder.getRemainingLowerCase())) builder.suggest(name);
                }
                return builder.buildFuture();
            }).executes(ctx -> player(ctx, "admin.tools", p -> {
                try { service(ToolService.class).give(p, ToolType.valueOf(StringArgumentType.getString(ctx, "type").toUpperCase(Locale.ROOT)),
                        sessions.sessionOf(p.getUniqueId()).map(s -> s.def().id()).orElse(null)); }
                catch (IllegalArgumentException error) { send(p, "command.tool-usage"); }
            }))));
        for (String action : List.of("test", "start", "stop", "reset", "show")) {
            String permission = switch (action) { case "test" -> "admin.test"; case "show" -> "admin.edit"; default -> "admin.control"; };
            root.then(node(action, permission).executes(ctx -> reply(ctx, "command.dungeon-required"))
                    .then(dungeon().executes(ctx -> control(ctx, action, permission))));
        }
        root.then(node("livetest", "admin.edit")
            .then(Commands.literal("stop").executes(ctx -> player(ctx,"admin.edit",dev.dasan.customdungeons.mob.LiveTestService::stop))));
        root.then(node("update", "admin.update").requires(source ->
                source.getSender() instanceof ConsoleCommandSender || permitted(source, "admin.update"))
                .executes(ctx -> update(ctx, "prepare"))
                .then(Commands.literal("check").executes(ctx -> update(ctx, "check")))
                .then(Commands.literal("confirm").executes(ctx -> update(ctx, "confirm"))));
        root.then(node("reload", "admin.reload").executes(this::reload));
        root.then(node("debug", "admin.debug").executes(ctx -> player(ctx, "admin.debug", p -> {
            if (debug.remove(p.getUniqueId())) send(p, "command.debug-off");
            else { debug.add(p.getUniqueId()); send(p, "command.debug-on"); sessions.sessionOf(p.getUniqueId()).ifPresent(this::ensureLogging); }
            p.updateCommands();
        })));
        for (String action : List.of("skipwave", "invulnerable")) {
            root.then(Commands.literal(action).requires(s -> s.getSender() instanceof Player p && controls(p))
                .executes(ctx -> player(ctx, null, p -> {
                    var session = sessions.sessionOf(p.getUniqueId());
                    if (!controls(p) || session.isEmpty()) { send(p, "command.no-session"); return; }
                    if (action.equals("skipwave")) session.get().skipWave();
                    else session.get().setInvulnerable(p.getUniqueId(), !p.isInvulnerable());
                    send(p, "command." + action);
                })));
        }
        return root;
    }
    private UpdateService.Settings updateSettings() {
        return new UpdateService.Settings(plugin.getConfig().getBoolean("updater.enabled", true),
                plugin.getConfig().getString("updater.repository", "Dasannn/CustomDungeons"),
                java.net.URI.create(plugin.getConfig().getString("updater.api-base-url", "https://api.github.com/")));
    }
    private void updateStartup() {
        if (!plugin.getConfig().getBoolean("updater.check-on-startup", true)) return;
        var updater = plugin.getServer().getServicesManager().load(UpdateService.class);
        if (updater == null) return;
        try {
            var settings = updateSettings();
            if (settings.enabled()) updateComplete(plugin.getServer().getConsoleSender(), updater.check(settings), true);
        } catch (IllegalArgumentException error) { send(plugin.getServer().getConsoleSender(), "update.invalid-release"); }
    }
    private int update(CommandContext<CommandSourceStack> ctx, String action) {
        var sender = ctx.getSource().getSender();
        if (!(sender instanceof ConsoleCommandSender) && !permitted(ctx.getSource(), "admin.update"))
            return reply(ctx, "command.no-permission");
        var updater = plugin.getServer().getServicesManager().load(UpdateService.class);
        if (updater == null) return reply(ctx, "update.disabled");
        try {
            var settings = updateSettings();
            String identity = sender instanceof Player p ? "player:" + p.getUniqueId()
                    : sender instanceof ConsoleCommandSender ? "console" : "sender:" + sender.getName() + ":" + System.identityHashCode(sender);
            var future = switch (action) {
                case "check" -> updater.check(settings);
                case "confirm" -> updater.confirm(identity, settings);
                default -> updater.prepare(identity, settings);
            };
            if (!future.isDone()) send(sender, "update.working");
            updateComplete(sender, future, false);
        } catch (IllegalArgumentException error) { send(sender, "update.invalid-release"); }
        return 1;
    }
    private void updateComplete(CommandSender sender, CompletableFuture<UpdateService.Result> future, boolean startup) {
        future.whenComplete((result, error) -> {
            if (!plugin.isEnabled()) return;
            try {
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    if (!plugin.isEnabled() || sender instanceof Player p && !p.isOnline()) return;
                    if (error != null) { send(sender, "update.download-failed"); return; }
                    if (startup && result.key().equals("update.up-to-date")) return;
                    var notes = result.notes() == null ? net.kyori.adventure.text.Component.empty()
                            : net.kyori.adventure.text.Component.text(result.notes().toString())
                                    .clickEvent(net.kyori.adventure.text.event.ClickEvent.openUrl(result.notes().toString()));
                    plugin.messages().send(sender, result.key(), Placeholder.unparsed("version", result.version()),
                            Placeholder.component("notes", notes));
                });
            } catch (org.bukkit.plugin.IllegalPluginAccessException ignored) { /* Disabled between completion and scheduling. */ }
        });
    }
    private boolean controls(Player player) {
        return sessions.sessionOf(player.getUniqueId()).map(s ->
            (s.testMode() && player.hasPermission("customdungeons.admin.test")) ||
            (debug.contains(player.getUniqueId()) && player.hasPermission("customdungeons.admin.debug"))).orElse(false);
    }
    private int joinNamed(CommandContext<CommandSourceStack> ctx) {
        var sender = ctx.getSource().getSender();
        var target = plugin.getServer().getPlayerExact(StringArgumentType.getString(ctx, "target"));
        if (!others(sender) && !(sender instanceof Player p && target != null && p.getUniqueId().equals(target.getUniqueId())))
            return reply(ctx, "command.no-permission");
        if (target == null) return reply(ctx, "command.player-not-found");
        join(target, StringArgumentType.getString(ctx, "dungeon"));
        return 1;
    }
    private void join(Player player, String dungeon) {
        JoinResult result = sessions.join(player, dungeon);
        if (result == JoinResult.OK || spam.allow(player.getUniqueId()))
            send(player, "join." + result.name().toLowerCase(Locale.ROOT).replace('_', '-'));
    }
    private int control(CommandContext<CommandSourceStack> ctx, String action, String permission) {
        var sender = ctx.getSource().getSender();
        if (!permitted(ctx.getSource(), permission)) return reply(ctx, "command.no-permission");
        String id = StringArgumentType.getString(ctx, "dungeon");
        var def = definitions.dungeons().get(id);
        if (def == null) return reply(ctx, "command.dungeon-not-found");
        if (action.equals("show")) return player(ctx, permission, p -> {
            service(PreviewRenderer.class).showDungeon(p, def, 30); send(p, "command.shown");
        });
        if (action.equals("test") && definitions.isReloading()) return reply(ctx, "command.reloading");
        if (action.equals("test")) return player(ctx, permission, p -> {
            if (busy(id) || sessions.sessionOf(p.getUniqueId()).isPresent()) { send(p, "command.busy"); return; }
            sessions.startTest(p, id);
            p.updateCommands();
            send(p, sessions.sessionOf(p.getUniqueId()).isPresent() ? "command.test-started" : "command.test-unavailable");
        });
        if (!busy(id)) return reply(ctx, "command.no-session");
        if (action.equals("start")) {
            if (sessions.session(id).orElseThrow().state().state() != SessionState.LOBBY) return reply(ctx, "command.busy");
            sessions.forceStart(id);
        } else if (action.equals("stop")) sessions.stop(id);
        else sessions.reset(id);
        send(sender, "command." + action);
        return 1;
    }
    private int reload(CommandContext<CommandSourceStack> ctx) {
        if (!permitted(ctx.getSource(), "admin.reload")) return reply(ctx, "command.no-permission");
        if (definitions.dungeons().keySet().stream().anyMatch(this::busy)) return reply(ctx, "command.reload-busy");
        if (definitions.isReloading()) return reply(ctx, "command.reloading");
        var sender = ctx.getSource().getSender();
        // Invalidate pending dialog submissions and close editors before starting the worker.
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            dev.dasan.customdungeons.gui.Inputs.cancel(player);
            if (player.getOpenInventory().getTopInventory().getHolder() instanceof dev.dasan.customdungeons.gui.Menu)
                player.closeInventory();
        }
        try {
            dev.dasan.customdungeons.config.ConfigMigration.run(plugin);
            plugin.reloadConfig();
            loadMessages();
            definitions.reloadAsync(task -> plugin.getServer().getScheduler().runTask(plugin, task))
                    .whenComplete((unused,error) -> {
                        if (!plugin.isEnabled()) return;
                        Runnable reply = () -> {
                            if (error != null) reportReloadFailure();
                            send(sender, error == null ? "command.reloaded" : "command.failed");
                        };
                        if (org.bukkit.Bukkit.isPrimaryThread()) reply.run();
                        else plugin.getServer().getScheduler().runTask(plugin, reply);
                    });
            return reply(ctx, "command.reload-started");
        } catch (dev.dasan.customdungeons.config.ConfigMigration.MigrationException error) {
            plugin.messages().send(sender, error.messageKey(), Placeholder.unparsed("file", error.file()));
            return 0;
        } catch (Exception error) { reportReloadFailure(); return reply(ctx, "command.failed"); }
    }
    private void reportReloadFailure() {
        plugin.getLogger().warning(net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                .serialize(plugin.messages().get("config.load-failed")));
    }
    private void loadMessages() {
        String resource = "en".equals(plugin.getConfig().getString("language")) ? "messages_en.yml" : "messages.yml";
        var yaml = YamlConfiguration.loadConfiguration(new java.io.File(plugin.getDataFolder(), resource));
        // Preserve customized text and supply all bundled keys, including new T16 keys on upgrades.
        for (String fallback : resource.equals("messages.yml") ? List.of(resource) : List.of(resource, "messages.yml")) {
            try (var reader = new InputStreamReader(Objects.requireNonNull(plugin.getResource(fallback)), StandardCharsets.UTF_8)) {
                var defaults = new YamlConfiguration(); defaults.load(reader);
                for (String key : defaults.getKeys(true)) if (defaults.isString(key) && !yaml.isString(key)) yaml.set(key, defaults.getString(key));
            } catch (Exception error) { throw new IllegalStateException("Cannot load bundled messages", error); }
        }
        plugin.messages().load(yaml, plugin.getConfig().getString("prefix", ""));
    }
    private void ensureLogging(DungeonSession session) {
        if (session.state().state() != SessionState.RUNNING || session.players().stream().noneMatch(p -> debug.contains(p.getUniqueId()))
                || !loggingSessions.add(session.id())) return;
        logTick(session);
    }
    private void logTick(DungeonSession session) {
        if (!plugin.isEnabled() || session.state().state() == SessionState.FREE || session.players().stream().noneMatch(p ->
                debug.contains(p.getUniqueId()) && p.hasPermission("customdungeons.admin.debug"))) {
            loggingSessions.remove(session.id()); return;
        }
        plugin.getLogger().info(net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(
                plugin.messages().get("command.debug-tick",
                        Placeholder.unparsed("dungeon", session.def().id()),
                        Placeholder.unparsed("tick", Long.toString(session.scheduler().currentTick())),
                        Placeholder.unparsed("state", session.state().state().name()),
                        Placeholder.unparsed("room", Integer.toString(session.roomIndex())),
                        Placeholder.unparsed("wave", Integer.toString(session.waveNumber())),
                        Placeholder.unparsed("mobs", Integer.toString(session.mobs().size())))));
        session.scheduler().runLater(1, () -> logTick(session));
    }
    private int player(CommandContext<CommandSourceStack> ctx, String permission, Consumer<Player> action) {
        if (permission != null && !permitted(ctx.getSource(), permission)) return reply(ctx, "command.no-permission");
        if (!(ctx.getSource().getSender() instanceof Player player)) return reply(ctx, "command.player-only");
        action.accept(player);
        return 1;
    }
    private <T> void complete(Player player, CompletableFuture<T> future, Consumer<T> success) {
        future.whenComplete((value, error) -> {
            if (!plugin.isEnabled()) return;
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (!player.isOnline()) return;
                if (error != null) send(player, "command.failed"); else success.accept(value);
            });
        });
    }
    private int reply(CommandContext<CommandSourceStack> ctx, String key) { send(ctx.getSource().getSender(), key); return 1; }
    private void send(CommandSender sender, String key) { plugin.messages().send(sender, key); }
    @EventHandler public void quit(PlayerQuitEvent event) {
        debug.remove(event.getPlayer().getUniqueId()); spam.forget(event.getPlayer().getUniqueId());
    }
}
