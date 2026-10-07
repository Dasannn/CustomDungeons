package dev.dasan.customdungeons.storage;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.ArrayList;

/** Startup-only, versioned schema changes. DDL is restartable because MySQL auto-commits DDL. */
public final class Migrations {
    private Migrations() {}

    static void migrate(Connection connection, SqlStorage.Dialect dialect) throws SQLException {
        int version;
        try (var statement = connection.createStatement()) {
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS schema_version (version INTEGER PRIMARY KEY)");
            try (var rows = statement.executeQuery("SELECT COALESCE(MAX(version), 0) FROM schema_version")) {
                rows.next();
                version = rows.getInt(1);
            }
            if (version > 4) throw new SQLException("Database schema is newer than this plugin supports");
            if (version == 4) return;
        }
        boolean autoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try {
            if (version == 0) {
                for (String sql : initialSchema(dialect)) {
                    try (var statement = connection.createStatement()) { statement.executeUpdate(sql); }
                }
                try (var statement = connection.createStatement()) {
                    statement.executeUpdate("INSERT INTO schema_version (version) VALUES (1)");
                }
            }
            if(version<2) {
                // Restartable even if MySQL committed ALTER TABLE before the version insert.
                boolean restoredColumn=false;
                try (var columns=connection.getMetaData().getColumns(connection.getCatalog(),null,"temp_blocks","restored")) {
                    while (columns.next())
                        if ("temp_blocks".equals(columns.getString("TABLE_NAME"))) restoredColumn=true;
                }
                try (var statement=connection.createStatement()) {
                    if (!restoredColumn) statement.executeUpdate("ALTER TABLE temp_blocks ADD COLUMN restored INTEGER NOT NULL DEFAULT 0");
                    statement.executeUpdate("INSERT INTO schema_version (version) VALUES (2)");
                }
            }
            String suffix=dialect==SqlStorage.Dialect.MYSQL?" ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin":"";
            if(version<3) try(var statement=connection.createStatement()) {
                statement.executeUpdate("CREATE TABLE IF NOT EXISTS session_returns (player_id VARCHAR(36) PRIMARY KEY, session_id VARCHAR(36) NOT NULL, "
                        +"destination VARCHAR(16) NOT NULL, exit_world VARCHAR(191) NOT NULL, exit_x DOUBLE NOT NULL, exit_y DOUBLE NOT NULL, "
                        +"exit_z DOUBLE NOT NULL, exit_yaw REAL NOT NULL, exit_pitch REAL NOT NULL, previous_world VARCHAR(191) NOT NULL, "
                        +"previous_x DOUBLE NOT NULL, previous_y DOUBLE NOT NULL, previous_z DOUBLE NOT NULL, previous_yaw REAL NOT NULL, previous_pitch REAL NOT NULL)"+suffix);
                statement.executeUpdate("INSERT INTO schema_version (version) VALUES (3)");
            }
            try(var statement=connection.createStatement()) {
                statement.executeUpdate("CREATE TABLE IF NOT EXISTS disconnects (player_id VARCHAR(36) PRIMARY KEY, "
                        +"id VARCHAR(36) NOT NULL, session_id VARCHAR(36) NOT NULL, dungeon_id VARCHAR(32) NOT NULL, "
                        +"mode VARCHAR(32) NOT NULL, keep_inventory INTEGER NOT NULL, "
                        +"position_world VARCHAR(191) NOT NULL, position_x DOUBLE NOT NULL, position_y DOUBLE NOT NULL, "
                        +"position_z DOUBLE NOT NULL, position_yaw REAL NOT NULL, position_pitch REAL NOT NULL, "
                        +"exit_world VARCHAR(191) NOT NULL, exit_x DOUBLE NOT NULL, exit_y DOUBLE NOT NULL, "
                        +"exit_z DOUBLE NOT NULL, exit_yaw REAL NOT NULL, exit_pitch REAL NOT NULL)"+suffix);
                statement.executeUpdate("INSERT INTO schema_version (version) VALUES (4)");
            }
            connection.commit();
        } catch (SQLException failure) {
            try { connection.rollback(); } catch (SQLException rollback) { failure.addSuppressed(rollback); }
            throw failure;
        } finally {
            connection.setAutoCommit(autoCommit);
        }
    }

    private static List<String> initialSchema(SqlStorage.Dialect dialect) {
        String suffix = dialect == SqlStorage.Dialect.MYSQL ? " ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin" : "";
        String id = dialect == SqlStorage.Dialect.MYSQL
                ? "BIGINT PRIMARY KEY AUTO_INCREMENT" : "INTEGER PRIMARY KEY AUTOINCREMENT";
        String blob = dialect == SqlStorage.Dialect.MYSQL ? "LONGBLOB" : "BLOB";
        String point = "exit_world VARCHAR(191) NOT NULL, exit_x DOUBLE NOT NULL, exit_y DOUBLE NOT NULL, "
                + "exit_z DOUBLE NOT NULL, exit_yaw REAL NOT NULL, exit_pitch REAL NOT NULL";
        var schema = new ArrayList<>(List.of(
                "CREATE TABLE IF NOT EXISTS runs (id " + id + ", dungeon_id VARCHAR(32) NOT NULL, "
                        + "started_at VARCHAR(40) NOT NULL, ended_at VARCHAR(40), result VARCHAR(16))" + suffix,
                "CREATE TABLE IF NOT EXISTS run_players (run_id BIGINT NOT NULL, player_id VARCHAR(36) NOT NULL, "
                        + "kills INTEGER NOT NULL, deaths INTEGER NOT NULL, survived INTEGER NOT NULL, rewarded INTEGER NOT NULL, "
                        + "PRIMARY KEY (run_id, player_id), FOREIGN KEY (run_id) REFERENCES runs(id) ON DELETE CASCADE"
                        + (dialect == SqlStorage.Dialect.MYSQL ? ", INDEX run_players_by_player (player_id)" : "") + ")" + suffix,
                "CREATE TABLE IF NOT EXISTS player_stats (player_id VARCHAR(36) PRIMARY KEY, runs INTEGER NOT NULL, "
                        + "completions INTEGER NOT NULL, kills INTEGER NOT NULL, deaths INTEGER NOT NULL)" + suffix,
                "CREATE TABLE IF NOT EXISTS cooldowns (player_id VARCHAR(36) NOT NULL, dungeon_id VARCHAR(32) NOT NULL, "
                        + "until_at VARCHAR(40) NOT NULL, PRIMARY KEY (player_id, dungeon_id))" + suffix,
                "CREATE TABLE IF NOT EXISTS claims (player_id VARCHAR(36) PRIMARY KEY, items " + blob + " NOT NULL)" + suffix,
                "CREATE TABLE IF NOT EXISTS active_sessions (session_id VARCHAR(36) PRIMARY KEY, "
                        + "dungeon_id VARCHAR(32) NOT NULL, " + point + ")" + suffix,
                "CREATE TABLE IF NOT EXISTS active_session_players (session_id VARCHAR(36) NOT NULL, player_id VARCHAR(36) NOT NULL, "
                        + "PRIMARY KEY (session_id, player_id), FOREIGN KEY (session_id) REFERENCES active_sessions(session_id) ON DELETE CASCADE)" + suffix,
                "CREATE TABLE IF NOT EXISTS temp_blocks (world VARCHAR(191) NOT NULL, x INTEGER NOT NULL, y INTEGER NOT NULL, "
                        + "z INTEGER NOT NULL, original_block_data TEXT NOT NULL, PRIMARY KEY (world, x, y, z))" + suffix,
                "CREATE TABLE IF NOT EXISTS pending_exits (player_id VARCHAR(36) PRIMARY KEY, " + point + ")" + suffix));
        if (dialect == SqlStorage.Dialect.SQLITE)
            schema.add("CREATE INDEX IF NOT EXISTS run_players_by_player ON run_players (player_id)");
        return List.copyOf(schema);
    }
}
