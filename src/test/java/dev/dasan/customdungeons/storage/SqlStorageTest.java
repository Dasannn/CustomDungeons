package dev.dasan.customdungeons.storage;

import dev.dasan.customdungeons.config.PluginConfig.DatabaseSettings;
import dev.dasan.customdungeons.model.Point;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class SqlStorageTest {
    @TempDir Path folder;
    private final UUID player = UUID.randomUUID();
    private final Point exit = new Point("world", 1.25, 64.5, -3.75, 90.5f, -30.25f);
    private static final Instant START = Instant.parse("2026-10-05T12:00:00.123456789Z");

    private SqlStorage open() {
        return SqlStorage.create(new DatabaseSettings("sqlite", "", 0, "", "", "", 4), folder);
    }

    private static <T> T await(CompletableFuture<T> future) throws Exception {
        return future.get(10, TimeUnit.SECONDS);
    }

    @Test void cooldownRoundTrip() throws Exception {
        try (var storage = open()) {
            assertTrue(await(storage.cooldownUntil(player, "dungeon")).isEmpty());
            await(storage.setCooldown(player, "dungeon", START));
            assertEquals(START, await(storage.cooldownUntil(player, "dungeon")).orElseThrow());
            await(storage.setCooldown(player, "dungeon", START.plusSeconds(60)));
            assertEquals(START.plusSeconds(60), await(storage.cooldownUntil(player, "dungeon")).orElseThrow());
            assertTrue(await(storage.cooldownUntil(player, "other")).isEmpty());
        }
    }

    @Test void runLifecycleUpdatesStats() throws Exception {
        try (var storage = open()) {
            assertEquals(new PlayerStats(0, 0, 0, 0), await(storage.stats(player)));
            long first = await(storage.startRun("dungeon", START, List.of(player)));
            assertEquals(new PlayerStats(1, 0, 0, 0), await(storage.stats(player)));
            await(storage.finishRun(first, RunResult.COMPLETED, START.plusSeconds(30),
                    List.of(new RunPlayerRecord(player, 7, 1, true, true))));
            long second = await(storage.startRun("dungeon", START.plusSeconds(60), List.of(player)));
            assertTrue(second > first);
            await(storage.finishRun(second, RunResult.FAILED, START.plusSeconds(90),
                    List.of(new RunPlayerRecord(player, 3, 2, false, false))));
            assertEquals(new PlayerStats(2, 1, 10, 3), await(storage.stats(player)));
        }
    }

    @Test void abortUnfinishedRunsClosesOriginalRowsAndPreservesFinishedRuns() throws Exception {
        Instant end = START.plusSeconds(120);
        long unfinished, orphan, completed, failed, aborted;
        try (var storage = open()) {
            unfinished = await(storage.startRun("dungeon", START, List.of(player)));
            // No active_sessions row: recovery must still find this original run after restart.
            orphan = await(storage.startRun("other", START, List.of()));
            completed = await(storage.startRun("dungeon", START, List.of(player)));
            failed = await(storage.startRun("dungeon", START, List.of(player)));
            aborted = await(storage.startRun("dungeon", START, List.of(player)));
            await(storage.finishRun(completed, RunResult.COMPLETED, START.plusSeconds(30),
                    List.of(new RunPlayerRecord(player, 7, 1, true, true))));
            await(storage.finishRun(failed, RunResult.FAILED, START.plusSeconds(40), List.of()));
            await(storage.finishRun(aborted, RunResult.ABORTED, START.plusSeconds(50), List.of()));
        }
        try (var storage = open()) {
            var stats = await(storage.stats(player));
            assertEquals(2, await(storage.abortUnfinishedRuns(end)));
            assertEquals(0, await(storage.abortUnfinishedRuns(end.plusSeconds(60))));
            assertEquals(stats, await(storage.stats(player)));
        }
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + folder.resolve("data.db"));
             var statement = connection.createStatement();
             var rows = statement.executeQuery("SELECT id, result, ended_at FROM runs ORDER BY id")) {
            var expected = java.util.Map.of(
                    unfinished, List.of("ABORTED", end.toString()),
                    orphan, List.of("ABORTED", end.toString()),
                    completed, List.of("COMPLETED", START.plusSeconds(30).toString()),
                    failed, List.of("FAILED", START.plusSeconds(40).toString()),
                    aborted, List.of("ABORTED", START.plusSeconds(50).toString()));
            int count = 0;
            while (rows.next()) {
                assertEquals(expected.get(rows.getLong("id")), List.of(rows.getString("result"), rows.getString("ended_at")));
                count++;
            }
            assertEquals(expected.size(), count);
        }
    }

    @Test void abortUnfinishedRunsDoesNotBlockCallerOnDatabaseLock() throws Exception {
        try (var storage = open();
             var connection = DriverManager.getConnection("jdbc:sqlite:" + folder.resolve("data.db"));
             var statement = connection.createStatement()) {
            await(storage.startRun("dungeon", START, List.of(player)));
            statement.execute("BEGIN IMMEDIATE");
            CompletableFuture<Integer> write;
            try {
                write = assertTimeoutPreemptively(java.time.Duration.ofSeconds(1),
                        () -> storage.abortUnfinishedRuns(START.plusSeconds(10)));
                assertFalse(write.isDone(), "The storage worker must wait for the lock, not the caller");
            } finally {
                statement.execute("ROLLBACK");
            }
            assertEquals(1, await(write));
        }
    }

    @Test void eliminatedPlayerDoesNotGetCompletion() throws Exception {
        UUID survivor = UUID.randomUUID();
        try (var storage = open()) {
            long run = await(storage.startRun("dungeon", START, List.of(player, survivor)));
            await(storage.finishRun(run, RunResult.COMPLETED, START.plusSeconds(10), List.of(
                    new RunPlayerRecord(player, 1, 3, false, false),
                    new RunPlayerRecord(survivor, 2, 0, true, true))));
            assertEquals(new PlayerStats(1, 0, 1, 3), await(storage.stats(player)));
            assertEquals(new PlayerStats(1, 1, 2, 0), await(storage.stats(survivor)));
        }
    }

    @Test void finishingAgainDoesNotDoubleCountStats() throws Exception {
        try (var storage = open()) {
            long run = await(storage.startRun("dungeon", START, List.of(player)));
            var records = List.of(new RunPlayerRecord(player, 5, 1, true, true));
            await(storage.finishRun(run, RunResult.COMPLETED, START.plusSeconds(10), records));
            await(storage.finishRun(run, RunResult.COMPLETED, START.plusSeconds(10), records));
            assertEquals(new PlayerStats(1, 1, 5, 1), await(storage.stats(player)));
        }
    }

    @Test void invalidFinishRollsBackEntireTransaction() throws Exception {
        try (var storage = open()) {
            long run = await(storage.startRun("dungeon", START, List.of(player)));
            var failed = storage.finishRun(run, RunResult.COMPLETED, START.plusSeconds(10), List.of(
                    new RunPlayerRecord(player, 9, 1, true, true),
                    new RunPlayerRecord(UUID.randomUUID(), 1, 0, true, true)));
            assertThrows(Exception.class, () -> await(failed));
            assertEquals(new PlayerStats(1, 0, 0, 0), await(storage.stats(player)));
        }
    }

    @Test void activeSessionsAndTempBlocksPersist() throws Exception {
        var active = new ActiveSessionRecord(UUID.randomUUID(), "dungeon", Set.of(player, UUID.randomUUID()), exit);
        var block = new TempBlockRecord("world", -1, 64, 9, "minecraft:oak_door[facing=north,half=lower]");
        try (var storage = open()) {
            await(storage.markActive(active));
            await(storage.addTempBlock(block));
        }
        try (var storage = open()) {
            assertEquals(List.of(active), await(storage.loadActive()));
            assertEquals(List.of(block), await(storage.loadTempBlocks()));
            await(storage.clearActive(active.sessionId()));
            await(storage.removeTempBlock(block.world(), block.x(), block.y(), block.z()));
            assertTrue(await(storage.loadActive()).isEmpty());
            assertTrue(await(storage.loadTempBlocks()).isEmpty());
        }
    }

    @Test void activeSessionCanReplaceItsPlayersAndExit() throws Exception {
        UUID id = UUID.randomUUID();
        var updated = new ActiveSessionRecord(id, "other", Set.of(), new Point("nether", 0, 80, 0, 0, 0));
        try (var storage = open()) {
            await(storage.markActive(new ActiveSessionRecord(id, "dungeon", Set.of(player), exit)));
            await(storage.markActive(updated));
            assertEquals(List.of(updated), await(storage.loadActive()));
        }
    }

    @Test void tempBlockUpsertKeepsOneRowPerPosition() throws Exception {
        var updated = new TempBlockRecord("world", 1, 2, 3, "minecraft:stone");
        try (var storage = open()) {
            await(storage.addTempBlock(new TempBlockRecord("world", 1, 2, 3, "minecraft:air")));
            await(storage.addTempBlock(updated));
            assertEquals(List.of(updated), await(storage.loadTempBlocks()));
        }
    }

    @Test void pendingExitIsTakenOnce() throws Exception {
        try (var storage = open()) {
            assertTrue(await(storage.takePendingExit(player)).isEmpty());
            await(storage.addPendingExit(player, exit));
            var one = storage.takePendingExit(player);
            var two = storage.takePendingExit(player);
            assertEquals(exit, await(one).orElseThrow());
            assertTrue(await(two).isEmpty());
        }
    }

    @Test void pendingExitCanBeReplacedAndPersists() throws Exception {
        var replacement = new Point("other", -10, 100, 20, 180, 45);
        try (var storage = open()) {
            await(storage.addPendingExit(player, exit));
            await(storage.addPendingExit(player, replacement));
        }
        try (var storage = open()) {
            assertEquals(replacement, await(storage.takePendingExit(player)).orElseThrow());
        }
    }

    @Test void migrationsIdempotent() throws Exception {
        try (var ignored = open()) {}
        try (var ignored = open()) {}
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + folder.resolve("data.db"));
             var statement = connection.createStatement()) {
            try (var rows = statement.executeQuery("SELECT COUNT(*), MAX(version) FROM schema_version")) {
                assertTrue(rows.next());
                assertEquals(1, rows.getInt(1));
                assertEquals(1, rows.getInt(2));
            }
            try (var rows = statement.executeQuery("PRAGMA journal_mode")) {
                assertTrue(rows.next());
                assertEquals("wal", rows.getString(1));
            }
        }
    }

    @Test void databaseLockDoesNotBlockCaller() throws Exception {
        try (var storage = open();
             var connection = DriverManager.getConnection("jdbc:sqlite:" + folder.resolve("data.db"));
             var statement = connection.createStatement()) {
            statement.execute("BEGIN IMMEDIATE");
            CompletableFuture<Void> write;
            try {
                write = assertTimeoutPreemptively(java.time.Duration.ofSeconds(1),
                        () -> storage.setCooldown(player, "dungeon", START));
                assertFalse(write.isDone(), "The worker must wait for the lock, not the caller");
            } finally {
                statement.execute("ROLLBACK");
            }
            await(write);
        }
    }

    @Test void closeDrainsAcceptedOperationsAndRejectsNewOnes() throws Exception {
        var storage = open();
        var writes = new ArrayList<CompletableFuture<Void>>();
        try {
            for (int i = 0; i < 30; i++) writes.add(storage.setCooldown(player, "dungeon", START.plusSeconds(i)));
        } finally {
            storage.close();
        }
        assertTrue(writes.stream().allMatch(CompletableFuture::isDone));
        for (var write : writes) await(write);
        assertThrows(Exception.class, () -> await(storage.stats(player)));
        storage.close();
        try (var reopened = open()) {
            assertEquals(START.plusSeconds(29), await(reopened.cooldownUntil(player, "dungeon")).orElseThrow());
        }
    }

    @Test void mutableParticipantsAreSnapshottedBeforeQueueing() throws Exception {
        try (var storage = open()) {
            var players = new ArrayList<>(List.of(player));
            var future = storage.startRun("dungeon", START, players);
            players.clear();
            await(future);
            assertEquals(1, await(storage.stats(player)).runs());
        }
    }
    @Test void runHistoryHasPlayerLookupIndex() throws Exception {
        try (var ignored = open();
             var connection = DriverManager.getConnection("jdbc:sqlite:" + folder.resolve("data.db"));
             var statement = connection.createStatement();
             var rows = statement.executeQuery("PRAGMA index_list(run_players)")) {
            boolean found = false;
            while (rows.next()) found |= "run_players_by_player".equals(rows.getString("name"));
            assertTrue(found, "Stats must not scan the entire run history for each player");
        }
    }

    @Test void newerSchemaIsRejectedWithoutChangingIt() throws Exception {
        try (var ignored = open()) {}
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + folder.resolve("data.db"));
             var statement = connection.createStatement()) {
            statement.executeUpdate("INSERT INTO schema_version (version) VALUES (2)");
        }
        assertThrows(IllegalStateException.class, this::open);
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + folder.resolve("data.db"));
             var statement = connection.createStatement();
             var rows = statement.executeQuery("SELECT MAX(version) FROM schema_version")) {
            assertTrue(rows.next());
            assertEquals(2, rows.getInt(1));
        }
    }

    @Test void dialectsChooseTheirOwnUpsertAndRowLockSyntax() {
        var columns = List.of("player_id", "dungeon_id", "until_at");
        var keys = List.of("player_id", "dungeon_id");
        String sqlite = SqlStorage.Dialect.SQLITE.upsert("cooldowns", columns, keys);
        String mysql = SqlStorage.Dialect.MYSQL.upsert("cooldowns", columns, keys);
        assertTrue(sqlite.endsWith("ON CONFLICT (player_id, dungeon_id) DO UPDATE SET until_at = excluded.until_at"));
        assertTrue(mysql.endsWith("ON DUPLICATE KEY UPDATE until_at = VALUES(until_at)"));
        assertEquals("", SqlStorage.Dialect.SQLITE.lock());
        assertEquals(" FOR UPDATE", SqlStorage.Dialect.MYSQL.lock());
    }

}
