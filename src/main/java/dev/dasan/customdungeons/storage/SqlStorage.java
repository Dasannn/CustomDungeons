package dev.dasan.customdungeons.storage;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import dev.dasan.customdungeons.config.PluginConfig.DatabaseSettings;
import dev.dasan.customdungeons.model.Point;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.bukkit.inventory.ItemStack;

/** JDBC work and item codecs run on the storage executor, never on the calling game thread. */
public final class SqlStorage implements Storage, ExitPersistence, DisconnectPersistence, PendingExitPersistence {
    enum Dialect {
        SQLITE, MYSQL;

        String lock() { return this == MYSQL ? " FOR UPDATE" : ""; }

        String upsert(String table, List<String> columns, List<String> keys) {
            String values = String.join(", ", java.util.Collections.nCopies(columns.size(), "?"));
            var changes = columns.stream().filter(column -> !keys.contains(column)).toList();
            String insert = "INSERT INTO " + table + " (" + String.join(", ", columns) + ") VALUES (" + values + ")";
            if (this == SQLITE) {
                return insert + " ON CONFLICT (" + String.join(", ", keys) + ") DO UPDATE SET "
                        + String.join(", ", changes.stream().map(column -> column + " = excluded." + column).toList());
            }
            return insert + " ON DUPLICATE KEY UPDATE "
                    + String.join(", ", changes.stream().map(column -> column + " = VALUES(" + column + ")").toList());
        }

        // Acquires a stable row even when there was no previous value. Never overwrites existing data.
        String ensureRow(String table, List<String> columns, String key) {
            String values = String.join(", ", java.util.Collections.nCopies(columns.size(), "?"));
            String insert = "INSERT INTO " + table + " (" + String.join(", ", columns) + ") VALUES (" + values + ")";
            return insert + (this == SQLITE ? " ON CONFLICT (" + key + ") DO NOTHING"
                    : " ON DUPLICATE KEY UPDATE " + key + " = " + key);
        }
    }

    private static final List<String> POINT_COLUMNS = List.of("exit_world", "exit_x", "exit_y", "exit_z", "exit_yaw", "exit_pitch");
    private static final List<String> STATS_COLUMNS = List.of("player_id", "runs", "completions", "kills", "deaths");
    private final HikariDataSource pool;
    private final Dialect dialect;
    private final ExecutorService executor;
    private final Object lifecycle = new Object();
    private final ThreadLocal<Boolean> worker = ThreadLocal.withInitial(() -> false);
    private boolean closed;

    private SqlStorage(HikariDataSource pool, Dialect dialect, int threads) {
        this.pool = pool;
        this.dialect = dialect;
        this.executor = Executors.newFixedThreadPool(threads, task -> {
            Thread thread = new Thread(() -> {
                worker.set(true);
                try { task.run(); } finally { worker.remove(); }
            }, "customdungeons-storage-" + dialect.name().toLowerCase(Locale.ROOT));
            thread.setDaemon(true);
            return thread;
        });
    }

    /** Synchronous initialization is intended for onEnable; all Storage operations are asynchronous. */
    public static SqlStorage create(DatabaseSettings settings, Path dataFolder) {
        Dialect dialect = switch (settings.type().toLowerCase(Locale.ROOT)) {
            case "sqlite" -> Dialect.SQLITE;
            case "mysql" -> Dialect.MYSQL;
            default -> throw new IllegalArgumentException("Unsupported database type");
        };
        var config = new HikariConfig();
        config.setPoolName("CustomDungeons-" + dialect.name());
        int threads = dialect == Dialect.SQLITE ? 1 : settings.poolSize();
        if (threads < 1) throw new IllegalArgumentException("Database pool size must be positive");
        config.setMaximumPoolSize(threads);
        config.setMinimumIdle(1);
        if (dialect == Dialect.SQLITE) {
            try { Files.createDirectories(dataFolder); }
            catch (IOException failure) { throw new IllegalStateException("Cannot create storage directory", failure); }
            config.setJdbcUrl("jdbc:sqlite:" + dataFolder.resolve("data.db").toAbsolutePath());
            config.setDriverClassName("org.sqlite.JDBC");
            config.addDataSourceProperty("busy_timeout", "5000");
            config.setConnectionInitSql("PRAGMA foreign_keys=ON");
        } else {
            // DataSource properties avoid interpolating identifiers or credentials into a JDBC URL.
            config.setDataSourceClassName("com.mysql.cj.jdbc.MysqlDataSource");
            config.addDataSourceProperty("serverName", settings.host());
            config.addDataSourceProperty("port", settings.port());
            config.addDataSourceProperty("databaseName", settings.database());
            config.setUsername(settings.user());
            config.setPassword(settings.password());
        }
        var pool = new HikariDataSource(config);
        try (var connection = pool.getConnection()) {
            if (dialect == Dialect.SQLITE) {
                try (var statement = connection.createStatement()) { statement.execute("PRAGMA journal_mode=WAL"); }
            }
            Migrations.migrate(connection, dialect);
            return new SqlStorage(pool, dialect, threads);
        } catch (SQLException | RuntimeException failure) {
            pool.close();
            throw new IllegalStateException("Cannot initialize storage", failure);
        }
    }

    @FunctionalInterface private interface SqlWork<T> { T run(Connection connection) throws SQLException; }

    private <T> CompletableFuture<T> submit(SqlWork<T> work) {
        synchronized (lifecycle) {
            if (closed) return CompletableFuture.failedFuture(new IllegalStateException("Storage is closed"));
            return CompletableFuture.supplyAsync(() -> {
                try (var connection = pool.getConnection()) {
                    connection.setAutoCommit(false);
                    try {
                        T value = work.run(connection);
                        connection.commit();
                        return value;
                    } catch (SQLException | RuntimeException failure) {
                        try { connection.rollback(); } catch (SQLException rollback) { failure.addSuppressed(rollback); }
                        throw failure;
                    }
                } catch (SQLException failure) { throw new CompletionException(failure); }
            }, executor);
        }
    }

    private static PreparedStatement prepare(Connection connection, String sql, Object... args) throws SQLException {
        var statement = connection.prepareStatement(sql);
        try {
            bind(statement, args);
            return statement;
        } catch (SQLException | RuntimeException failure) {
            statement.close();
            throw failure;
        }
    }

    private static void bind(PreparedStatement statement, Object... args) throws SQLException {
        for (int i = 0; i < args.length; i++) {
            Object value = args[i];
            if (value instanceof UUID || value instanceof Instant) value = value.toString();
            statement.setObject(i + 1, value);
        }
    }

    private static int update(Connection connection, String sql, Object... args) throws SQLException {
        try (var statement = prepare(connection, sql, args)) { return statement.executeUpdate(); }
    }

    @Override public CompletableFuture<Long> startRun(String dungeonId, Instant start, Collection<UUID> players) {
        var snapshot = Set.copyOf(players).stream().sorted().toList();
        return submit(connection -> {
            lockStats(connection, snapshot);
            long runId;
            try (var statement = connection.prepareStatement(
                    "INSERT INTO runs (dungeon_id, started_at) VALUES (?, ?)", Statement.RETURN_GENERATED_KEYS)) {
                bind(statement, dungeonId, start);
                statement.executeUpdate();
                try (var keys = statement.getGeneratedKeys()) {
                    if (!keys.next()) throw new SQLException("Missing generated run id");
                    runId = keys.getLong(1);
                }
            }
            for (UUID player : snapshot) {
                update(connection, "INSERT INTO run_players (run_id, player_id, kills, deaths, survived, rewarded) VALUES (?, ?, 0, 0, 0, 0)", runId, player);
                refreshStats(connection, player);
            }
            return runId;
        });
    }

    @Override public CompletableFuture<Void> finishRun(long runId, RunResult result, Instant end, List<RunPlayerRecord> players) {
        var snapshot = List.copyOf(players);
        return submit(connection -> {
            try (var statement = prepare(connection, "SELECT id FROM runs WHERE id = ?" + dialect.lock(), runId);
                 var rows = statement.executeQuery()) {
                if (!rows.next()) throw new SQLException("Unknown run id");
            }
            var participants = new ArrayList<UUID>();
            try (var statement = prepare(connection, "SELECT player_id FROM run_players WHERE run_id = ?" + dialect.lock(), runId);
                 var rows = statement.executeQuery()) {
                while (rows.next()) participants.add(UUID.fromString(rows.getString(1)));
            }
            participants.sort(null);
            lockStats(connection, participants);
            update(connection, "UPDATE runs SET result = ?, ended_at = ? WHERE id = ?", result.name(), end, runId);
            var seen = new HashSet<UUID>();
            for (var player : snapshot) {
                if (!seen.add(player.player()) || player.kills() < 0 || player.deaths() < 0)
                    throw new IllegalArgumentException("Invalid run player record");
                if (update(connection, "UPDATE run_players SET kills = ?, deaths = ?, survived = ?, rewarded = ? WHERE run_id = ? AND player_id = ?",
                        player.kills(), player.deaths(), player.survived() ? 1 : 0, player.rewarded() ? 1 : 0, runId, player.player()) != 1)
                    throw new SQLException("Player is not a participant of the run");
            }
            for (UUID player : participants) refreshStats(connection, player);
            return null;
        });
    }

    @Override public CompletableFuture<Integer> abortUnfinishedRuns(Instant end) {
        var finishedAt = java.util.Objects.requireNonNull(end, "end");
        // Portable SQL for SQLite and MySQL. The NULL predicate also makes repeated recovery idempotent.
        return submit(connection -> update(connection,
                "UPDATE runs SET result = ?, ended_at = ? WHERE ended_at IS NULL",
                RunResult.ABORTED.name(), finishedAt));
    }

    private void lockStats(Connection connection, Collection<UUID> players) throws SQLException {
        for (UUID player : players) {
            update(connection, dialect.ensureRow("player_stats", STATS_COLUMNS, "player_id"), player, 0, 0, 0, 0);
            if (dialect == Dialect.MYSQL) {
                try (var statement = prepare(connection, "SELECT player_id FROM player_stats WHERE player_id = ? FOR UPDATE", player);
                     var rows = statement.executeQuery()) { rows.next(); }
            }
        }
    }

    /** Completion is a completed run survived by this player; rewarding is recorded separately. */
    private void refreshStats(Connection connection, UUID player) throws SQLException {
        String sql = "SELECT COUNT(*), COALESCE(SUM(CASE WHEN r.result = 'COMPLETED' AND p.survived = 1 THEN 1 ELSE 0 END), 0), "
                + "COALESCE(SUM(p.kills), 0), COALESCE(SUM(p.deaths), 0) FROM run_players p JOIN runs r ON r.id = p.run_id WHERE p.player_id = ?";
        try (var statement = prepare(connection, sql, player); var rows = statement.executeQuery()) {
            rows.next();
            update(connection, "UPDATE player_stats SET runs = ?, completions = ?, kills = ?, deaths = ? WHERE player_id = ?",
                    rows.getInt(1), rows.getInt(2), rows.getInt(3), rows.getInt(4), player);
        }
    }

    @Override public CompletableFuture<PlayerStats> stats(UUID player) {
        return submit(connection -> {
            try (var statement = prepare(connection, "SELECT runs, completions, kills, deaths FROM player_stats WHERE player_id = ?", player);
                 var rows = statement.executeQuery()) {
                return rows.next() ? new PlayerStats(rows.getInt(1), rows.getInt(2), rows.getInt(3), rows.getInt(4))
                        : new PlayerStats(0, 0, 0, 0);
            }
        });
    }

    @Override public CompletableFuture<Optional<Instant>> cooldownUntil(UUID player, String dungeonId) {
        return submit(connection -> {
            try (var statement = prepare(connection, "SELECT until_at FROM cooldowns WHERE player_id = ? AND dungeon_id = ?", player, dungeonId);
                 var rows = statement.executeQuery()) {
                return rows.next() ? Optional.of(Instant.parse(rows.getString(1))) : Optional.empty();
            }
        });
    }

    @Override public CompletableFuture<Void> setCooldown(UUID player, String dungeonId, Instant until) {
        return submit(connection -> {
            update(connection, dialect.upsert("cooldowns", List.of("player_id", "dungeon_id", "until_at"), List.of("player_id", "dungeon_id")), player, dungeonId, until);
            return null;
        });
    }

    @Override public CompletableFuture<Void> addClaims(UUID player, List<ItemStack> items) {
        // Items remain mutable despite the list snapshot; clone before the caller can reuse them.
        var snapshot = items.stream().map(ItemStack::clone).toList();
        return submit(connection -> {
            if (snapshot.isEmpty()) return null;
            update(connection, dialect.ensureRow("claims", List.of("player_id", "items"), "player_id"),
                    player, ItemStack.serializeItemsAsBytes(List.of()));
            var combined = new ArrayList<ItemStack>();
            try (var statement = prepare(connection, "SELECT items FROM claims WHERE player_id = ?" + dialect.lock(), player);
                 var rows = statement.executeQuery()) {
                if (rows.next()) combined.addAll(Arrays.asList(ItemStack.deserializeItemsFromBytes(rows.getBytes(1))));
            }
            combined.addAll(snapshot);
            update(connection, "UPDATE claims SET items = ? WHERE player_id = ?", ItemStack.serializeItemsAsBytes(combined), player);
            return null;
        });
    }

    @Override public CompletableFuture<List<ItemStack>> takeClaims(UUID player) {
        return submit(connection -> {
            List<ItemStack> items;
            try (var statement = prepare(connection, "SELECT items FROM claims WHERE player_id = ?" + dialect.lock(), player);
                 var rows = statement.executeQuery()) {
                if (!rows.next()) return List.of();
                // Decode before deleting; a codec failure rolls the transaction back.
                items = List.copyOf(Arrays.asList(ItemStack.deserializeItemsFromBytes(rows.getBytes(1))));
            }
            update(connection, "DELETE FROM claims WHERE player_id = ?", player);
            return items;
        });
    }

    @Override public CompletableFuture<Void> saveDisconnect(DisconnectRecord record) {
        return submit(connection -> {
            var columns=new ArrayList<>(List.of("player_id","id","session_id","dungeon_id","mode","keep_inventory",
                    "position_world","position_x","position_y","position_z","position_yaw","position_pitch"));
            columns.addAll(POINT_COLUMNS);
            var p=record.position();
            update(connection,dialect.upsert("disconnects",columns,List.of("player_id")),
                    pointArgs(record.exit(),record.player(),record.id(),record.sessionId(),record.dungeonId(),
                            record.mode().name(),record.keepInventory()?1:0,p.world(),p.x(),p.y(),p.z(),p.yaw(),p.pitch()));
            return null;
        });
    }
    @Override public CompletableFuture<Optional<DisconnectRecord>> disconnect(UUID player) {
        return submit(connection -> {
            try(var statement=prepare(connection,"SELECT * FROM disconnects WHERE player_id = ?",player);
                var rows=statement.executeQuery()) {
                if(!rows.next())return Optional.empty();
                var position=new Point(rows.getString("position_world"),rows.getDouble("position_x"),rows.getDouble("position_y"),
                        rows.getDouble("position_z"),rows.getFloat("position_yaw"),rows.getFloat("position_pitch"));
                return Optional.of(new DisconnectRecord(UUID.fromString(rows.getString("id")),player,
                        UUID.fromString(rows.getString("session_id")),rows.getString("dungeon_id"),position,readPoint(rows),
                        dev.dasan.customdungeons.model.DisconnectMode.valueOf(rows.getString("mode")),rows.getInt("keep_inventory")!=0));
            }
        });
    }
    @Override public CompletableFuture<Void> clearDisconnect(UUID player,UUID id) {
        return submit(connection -> {update(connection,"DELETE FROM disconnects WHERE player_id = ? AND id = ?",player,id);return null;});
    }

    @Override public CompletableFuture<Void> saveReturnTarget(UUID player,ReturnTarget target) {
        return submit(connection -> {
            var columns=new ArrayList<>(List.of("player_id","session_id","destination"));
            columns.addAll(POINT_COLUMNS);
            columns.addAll(List.of("previous_world","previous_x","previous_y","previous_z","previous_yaw","previous_pitch"));
            var args=pointArgs(target.exit(),player,target.sessionId(),target.destination().name());
            var all=Arrays.copyOf(args,args.length+6);var p=target.previous();
            System.arraycopy(new Object[]{p.world(),p.x(),p.y(),p.z(),p.yaw(),p.pitch()},0,all,args.length,6);
            update(connection,dialect.upsert("session_returns",columns,List.of("player_id")),all);
            return null;
        });
    }
    @Override public CompletableFuture<Optional<ReturnTarget>> returnTarget(UUID player) {
        return submit(connection -> {
            try(var statement=prepare(connection,"SELECT * FROM session_returns WHERE player_id = ?",player);
                var rows=statement.executeQuery()) {
                if(!rows.next())return Optional.empty();
                var previous=new Point(rows.getString("previous_world"),rows.getDouble("previous_x"),rows.getDouble("previous_y"),
                        rows.getDouble("previous_z"),rows.getFloat("previous_yaw"),rows.getFloat("previous_pitch"));
                return Optional.of(new ReturnTarget(UUID.fromString(rows.getString("session_id")),previous,readPoint(rows),
                        dev.dasan.customdungeons.model.FinishDestination.valueOf(rows.getString("destination"))));
            }
        });
    }
    @Override public CompletableFuture<Void> clearReturnTarget(UUID player,UUID session) {
        return submit(connection -> {update(connection,"DELETE FROM session_returns WHERE player_id = ? AND session_id = ?",player,session);return null;});
    }

    @Override public CompletableFuture<Void> markActive(ActiveSessionRecord record) {
        return submit(connection -> {
            var columns = new ArrayList<>(List.of("session_id", "dungeon_id"));
            columns.addAll(POINT_COLUMNS);
            update(connection, dialect.upsert("active_sessions", columns, List.of("session_id")),
                    pointArgs(record.exit(), record.sessionId(), record.dungeonId()));
            update(connection, "DELETE FROM active_session_players WHERE session_id = ?", record.sessionId());
            for (UUID player : record.players())
                update(connection, "INSERT INTO active_session_players (session_id, player_id) VALUES (?, ?)", record.sessionId(), player);
            return null;
        });
    }

    @Override public CompletableFuture<Void> clearActive(UUID sessionId) {
        return submit(connection -> {
            update(connection, "DELETE FROM active_sessions WHERE session_id = ?", sessionId);
            return null;
        });
    }

    @Override public CompletableFuture<List<ActiveSessionRecord>> loadActive() {
        return submit(connection -> {
            Map<UUID, Set<UUID>> players = new HashMap<>();
            try (var statement = prepare(connection, "SELECT session_id, player_id FROM active_session_players");
                 var rows = statement.executeQuery()) {
                while (rows.next()) players.computeIfAbsent(UUID.fromString(rows.getString(1)), ignored -> new HashSet<>())
                        .add(UUID.fromString(rows.getString(2)));
            }
            var records = new ArrayList<ActiveSessionRecord>();
            try (var statement = prepare(connection, "SELECT * FROM active_sessions ORDER BY session_id");
                 var rows = statement.executeQuery()) {
                while (rows.next()) {
                    UUID id = UUID.fromString(rows.getString("session_id"));
                    records.add(new ActiveSessionRecord(id, rows.getString("dungeon_id"), players.getOrDefault(id, Set.of()), readPoint(rows)));
                }
            }
            return List.copyOf(records);
        });
    }

    @Override public CompletableFuture<Void> addTempBlock(TempBlockRecord record) {
        return submit(connection -> {
            update(connection, dialect.upsert("temp_blocks", List.of("world", "x", "y", "z", "original_block_data", "placed_block_data", "restored"), List.of("world", "x", "y", "z")),
                    record.world(), record.x(), record.y(), record.z(), record.originalBlockData(),record.placedBlockData(), 0);
            return null;
        });
    }

    @Override public CompletableFuture<Void> markTempBlockRestored(String world, int x, int y, int z) {
        return submit(connection -> {
            update(connection, "UPDATE temp_blocks SET restored = 1 WHERE world = ? AND x = ? AND y = ? AND z = ?", world, x, y, z);
            return null;
        });
    }

    @Override public CompletableFuture<Void> removeTempBlock(String world, int x, int y, int z) {
        return submit(connection -> {
            update(connection, "DELETE FROM temp_blocks WHERE world = ? AND x = ? AND y = ? AND z = ?", world, x, y, z);
            return null;
        });
    }

    @Override public CompletableFuture<List<TempBlockRecord>> loadTempBlocks() {
        return submit(connection -> {
            var blocks = new ArrayList<TempBlockRecord>();
            try (var statement = prepare(connection, "SELECT world, x, y, z, original_block_data, placed_block_data FROM temp_blocks ORDER BY world, x, y, z");
                 var rows = statement.executeQuery()) {
                while (rows.next()) blocks.add(new TempBlockRecord(rows.getString(1), rows.getInt(2), rows.getInt(3), rows.getInt(4), rows.getString(5),rows.getString(6)));
            }
            return List.copyOf(blocks);
        });
    }

    @Override public CompletableFuture<Void> addPendingExit(UUID player, Point exit) {
        return submit(connection -> {
            var columns = new ArrayList<>(List.of("player_id", "id"));
            columns.addAll(POINT_COLUMNS);
            update(connection, dialect.upsert("pending_exits", columns, List.of("player_id")), pointArgs(exit, player, UUID.randomUUID()));
            return null;
        });
    }

    @Override public CompletableFuture<Optional<PendingExitRecord>> pendingExit(UUID player) {
        return submit(connection -> {
            try(var statement=prepare(connection,"SELECT * FROM pending_exits WHERE player_id = ?",player);
                var rows=statement.executeQuery()) {
                return rows.next()?Optional.of(new PendingExitRecord(UUID.fromString(rows.getString("id")),readPoint(rows))):Optional.empty();
            }
        });
    }

    @Override public CompletableFuture<Void> clearPendingExit(UUID player,UUID generation) {
        return submit(connection -> {
            update(connection,"DELETE FROM pending_exits WHERE player_id = ? AND id = ?",player,generation);
            return null;
        });
    }

    @Override public CompletableFuture<Optional<Point>> takePendingExit(UUID player) {
        return submit(connection -> {
            Point point;
            try (var statement = prepare(connection, "SELECT * FROM pending_exits WHERE player_id = ?" + dialect.lock(), player);
                 var rows = statement.executeQuery()) {
                if (!rows.next()) return Optional.empty();
                point = readPoint(rows);
            }
            update(connection, "DELETE FROM pending_exits WHERE player_id = ?", player);
            return Optional.of(point);
        });
    }

    private static Object[] pointArgs(Point point, Object... prefix) {
        Object[] args = Arrays.copyOf(prefix, prefix.length + 6);
        System.arraycopy(new Object[]{point.world(), point.x(), point.y(), point.z(), point.yaw(), point.pitch()}, 0, args, prefix.length, 6);
        return args;
    }

    private static Point readPoint(ResultSet rows) throws SQLException {
        return new Point(rows.getString("exit_world"), rows.getDouble("exit_x"), rows.getDouble("exit_y"),
                rows.getDouble("exit_z"), rows.getFloat("exit_yaw"), rows.getFloat("exit_pitch"));
    }

    @Override public void close() {
        if (worker.get()) throw new IllegalStateException("Close storage from the lifecycle thread, not a storage callback");
        synchronized (lifecycle) {
            if (closed) return;
            closed = true;
            executor.shutdown();
        }
        boolean interrupted = false;
        try {
            while (true) {
                try { if (executor.awaitTermination(1, TimeUnit.SECONDS)) break; }
                catch (InterruptedException ignored) { interrupted = true; }
            }
        } finally {
            pool.close();
            if (interrupted) Thread.currentThread().interrupt();
        }
    }
}
