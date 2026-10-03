package io.algernon.vespera;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import javax.sql.DataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * ADR-180: the database file the application ships with is opened in write-ahead-log mode, synced at
 * checkpoints rather than at every commit, on every connection the pool hands out.
 *
 * <p><b>Profile-free on purpose.</b> The {@code test} profile swaps the datasource for an in-memory
 * database on a pool of one, and an in-memory database has no journal to choose: {@code PRAGMA
 * journal_mode} answers {@code memory} whatever the URL asks for. So this slice activates no profile,
 * builds the datasource {@code application.yaml} ships, and moves only {@code vespera.working-dir}
 * into a temporary directory -- the same file-backed {@code vespera.db}, the same URL, the same Hikari
 * pool an invocation gets.
 *
 * <p>{@code @DirtiesContext} after the class, because the cached context keeps the database file open
 * and Windows refuses to delete a directory holding an open file.
 *
 * <p>The two tests that open their own connection read the shipped URL from the environment, resolved
 * the way the pool resolves it, and point it at a directory of their own, so neither competes with
 * the pool for the slice's database file.
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Epic("Architecture")
@Feature("Shipped configuration")
@Issue("378")
@Link(name = "ADR-180", url = Adr.THE_DATABASE_IS_WRITTEN_AHEAD_AND_SYNCED_AT_CHECKPOINTS, type = "adr")
class WriteAheadDatabaseTest {

    /** The database file's name, as the shipped URL names it under the working directory. */
    private static final String DATABASE_FILE = "vespera.db";

    /** Where SQLite appends committed pages until a checkpoint copies them into the database file. */
    private static final String LOG_FILE = DATABASE_FILE + "-wal";

    /** The index into the log SQLite keeps in shared memory while the database is open. */
    private static final String SHARED_MEMORY_FILE = DATABASE_FILE + "-shm";

    /** What {@code PRAGMA journal_mode} answers in write-ahead-log mode. */
    private static final String WRITE_AHEAD = "wal";

    /** What it answers under the rollback journal SQLite uses unless told otherwise. */
    private static final String ROLLBACK_JOURNAL = "delete";

    /**
     * {@code PRAGMA synchronous} as a number: 1 is {@code NORMAL}, which syncs the log at each
     * checkpoint; 2, {@code FULL}, the default, syncs at every commit.
     */
    private static final int SYNCED_AT_CHECKPOINTS = 1;

    /** The five-minute lock wait the shipped URL already names, which the new parameters must not drop. */
    private static final long LOCK_WAIT_MILLIS = 300_000L;

    /**
     * SQLite's own automatic checkpoint interval, in pages: a commit that leaves the log longer than
     * this copies it back into the database file. Left at the default rather than raised or turned off.
     */
    private static final long AUTOMATIC_CHECKPOINT_PAGES = 1_000L;

    /**
     * 512 MiB, in bytes: the size the log file is cut back to once a checkpoint has emptied it, so one
     * very large transaction -- a discard of a whole run's rows -- does not leave a log that size on disk
     * for the rest of the invocation. Above the roughly 230 MB one stage-2 chunk was measured to leave,
     * so an ordinary chunk's log is reused rather than cut back and grown again every chunk.
     */
    private static final long LOG_SIZE_LIMIT_BYTES = 512L * 1024 * 1024;

    /** How many connections the pool is asked for at once: more than one, since the setting is per connection. */
    private static final int CONNECTIONS_HELD_AT_ONCE = 2;

    @TempDir
    static Path workingDirectory;

    @DynamicPropertySource
    static void workingDirectory(DynamicPropertyRegistry registry) {
        registry.add("vespera.working-dir", workingDirectory::toString);
    }

    @Autowired
    private DataSource dataSource;

    @Value("${spring.datasource.url}")
    private String shippedUrl;

    @Test
    @Story("The database is synced at checkpoints rather than at every commit")
    @DisplayName("Every connection the shipped pool hands out writes ahead to a log and syncs it at checkpoints")
    void everyPooledConnectionWritesAheadAndSyncsAtCheckpoints() throws SQLException {
        Connection[] held = new Connection[CONNECTIONS_HELD_AT_ONCE];
        try {
            for (int i = 0; i < held.length; i++) {
                held[i] = dataSource.getConnection();
            }
            claim(
                    "the pool is the shipped one, over a database file in the working directory rather than"
                            + " the in-memory database the test profile uses, which has no journal to choose",
                    () -> assertThat(Path.of(databaseFile(held[0])))
                            .isEqualTo(workingDirectory.resolve(DATABASE_FILE)));
            for (Connection connection : held) {
                claim(
                        "each of the " + CONNECTIONS_HELD_AT_ONCE + " connections held at once appends commits"
                                + " to a log beside the database file instead of copying each page it changes"
                                + " into a rollback journal first",
                        () -> assertThat(textPragma(connection, "journal_mode")).isEqualTo(WRITE_AHEAD));
                claim(
                        "and syncs that log to disk at a checkpoint rather than at every commit: the sync"
                                + " level is per connection, so a connection the pool opens later must say so"
                                + " too, not only the first one",
                        () -> assertThat(numberPragma(connection, "synchronous")).isEqualTo(SYNCED_AT_CHECKPOINTS));
                claim(
                        "and copies the log back into the database file by itself once it passes "
                                + AUTOMATIC_CHECKPOINT_PAGES + " pages, SQLite's own interval, and cuts the"
                                + " emptied log file back to " + LOG_SIZE_LIMIT_BYTES + " bytes (512 MiB), so"
                                + " the log stays bounded through a long stage with nothing in the"
                                + " application calling a checkpoint",
                        () -> {
                            assertThat(numberPragma(connection, "wal_autocheckpoint"))
                                    .isEqualTo(AUTOMATIC_CHECKPOINT_PAGES);
                            assertThat(numberPragma(connection, "journal_size_limit")).isEqualTo(LOG_SIZE_LIMIT_BYTES);
                        });
                claim(
                        "and still waits five minutes -- " + LOCK_WAIT_MILLIS + " ms -- on a locked database,"
                                + " and still enforces foreign keys, so adding parameters to the URL dropped"
                                + " neither of the ones already there",
                        () -> {
                            assertThat(numberPragma(connection, "busy_timeout")).isEqualTo(LOCK_WAIT_MILLIS);
                            assertThat(numberPragma(connection, "foreign_keys")).isEqualTo(1L);
                        });
            }
        } finally {
            for (Connection connection : held) {
                if (connection != null) {
                    connection.close();
                }
            }
        }
    }

    @Test
    @Story("The database is synced at checkpoints rather than at every commit")
    @DisplayName("A database written before this setting is switched to the log the first time it is opened, rows and all")
    void aDatabaseWrittenUnderTheRollbackJournalIsSwitchedOnFirstOpen(@TempDir Path earlierWorkingDirectory)
            throws SQLException {
        Path earlierDatabase = earlierWorkingDirectory.resolve(DATABASE_FILE);
        try (Connection earlier = DriverManager.getConnection("jdbc:sqlite:" + earlierDatabase);
                Statement statement = earlier.createStatement()) {
            statement.executeUpdate("CREATE TABLE kept (value TEXT)");
            statement.executeUpdate("INSERT INTO kept VALUES ('written before')");
            claim(
                    "the database this starts from uses the rollback journal, as every database the"
                            + " application wrote before this setting does",
                    () -> assertThat(textPragma(earlier, "journal_mode")).isEqualTo(ROLLBACK_JOURNAL));
        }

        try (Connection opened = DriverManager.getConnection(urlIn(earlierWorkingDirectory));
                Statement statement = opened.createStatement();
                ResultSet kept = statement.executeQuery("SELECT value FROM kept")) {
            claim(
                    "opened with the shipped URL it is in log mode, with nothing for the operator to run by"
                            + " hand first",
                    () -> assertThat(textPragma(opened, "journal_mode")).isEqualTo(WRITE_AHEAD));
            claim(
                    "and the row written before the switch is still there: the switch changes how the next"
                            + " commit is written, not what the file already holds",
                    () -> {
                        assertThat(kept.next()).isTrue();
                        assertThat(kept.getString(1)).isEqualTo("written before");
                    });
        }
    }

    @Test
    @Story("The database is synced at checkpoints rather than at every commit")
    @DisplayName("While the database is open its log sits beside it, and once the last connection closes the database file stands alone")
    void theLogIsFoldedBackWhenTheLastConnectionCloses(@TempDir Path ownWorkingDirectory) throws SQLException {
        try (Connection connection = DriverManager.getConnection(urlIn(ownWorkingDirectory));
                Statement statement = connection.createStatement()) {
            statement.executeUpdate("CREATE TABLE kept (value TEXT)");
            statement.executeUpdate("INSERT INTO kept VALUES ('committed')");
            claim(
                    "with a connection open and a commit made, the log and its shared-memory index sit beside"
                            + " the database file -- which is why a copy taken while a run is going has to"
                            + " take all three",
                    () -> assertThat(ownWorkingDirectory.resolve(LOG_FILE)).exists());
        }

        claim(
                "once the last connection closes, the log has been copied into the database file and both"
                        + " companions are gone, so a working directory an invocation left cleanly holds one"
                        + " database file that can be copied on its own",
                () -> {
                    assertThat(ownWorkingDirectory.resolve(LOG_FILE)).doesNotExist();
                    assertThat(ownWorkingDirectory.resolve(SHARED_MEMORY_FILE)).doesNotExist();
                });
        try (Connection plain = DriverManager.getConnection("jdbc:sqlite:" + ownWorkingDirectory.resolve(DATABASE_FILE));
                Statement statement = plain.createStatement();
                ResultSet kept = statement.executeQuery("SELECT count(*) FROM kept")) {
            claim(
                    "and the database file alone holds the commit",
                    () -> {
                        assertThat(kept.next()).isTrue();
                        assertThat(kept.getLong(1)).isEqualTo(1L);
                    });
            claim(
                    "and it still records that it is in log mode, so a connection opened later with a URL that"
                            + " names no journal -- an older build of the application, a database browser --"
                            + " writes ahead too rather than switching it back",
                    () -> assertThat(textPragma(plain, "journal_mode")).isEqualTo(WRITE_AHEAD));
        }
    }

    /** The shipped URL, resolved as the pool resolved it, pointed at {@code directory} instead. */
    private String urlIn(Path directory) {
        return shippedUrl.replace(workingDirectory.toString(), directory.toString());
    }

    /** The file SQLite reports for the connection's main database. */
    private static String databaseFile(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
                ResultSet result = statement.executeQuery("PRAGMA database_list")) {
            while (result.next()) {
                if ("main".equals(result.getString("name"))) {
                    return result.getString("file");
                }
            }
            throw new IllegalStateException("the connection reports no main database");
        }
    }

    private static String textPragma(Connection connection, String name) throws SQLException {
        try (Statement statement = connection.createStatement();
                ResultSet result = statement.executeQuery("PRAGMA " + name)) {
            result.next();
            return result.getString(1);
        }
    }

    private static long numberPragma(Connection connection, String name) throws SQLException {
        try (Statement statement = connection.createStatement();
                ResultSet result = statement.executeQuery("PRAGMA " + name)) {
            result.next();
            return result.getLong(1);
        }
    }
}
