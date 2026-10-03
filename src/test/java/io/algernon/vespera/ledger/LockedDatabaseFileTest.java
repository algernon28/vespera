package io.algernon.vespera.ledger;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import javax.sql.DataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sqlite.SQLiteErrorCode;
import org.sqlite.SQLiteException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * A database file SQLite reports locked is named, and said to be held by another process, wherever a
 * statement meets the lock (ADR-177 §2, #364).
 *
 * <p>On 2026-09-28 stage 2's closing line named an SQL statement and {@code [SQLITE_BUSY] The database
 * file is locked}, and no file. The translator sits under the one {@code JdbcTemplate} every statement
 * goes through, so the sentence is the same in every step.
 *
 * <p><b>Profile-free on purpose</b>, like {@code WriteAheadDatabaseTest}: the {@code test} profile's
 * in-memory database on a pool of one cannot be locked by a second connection, so this slice builds the
 * datasource {@code application.yaml} ships over a file in a temporary working directory, and imports
 * the translator, which a {@code @JdbcTest} slice does not scan for. The contention is the one measured
 * under the shipped write-ahead-log URL: a transaction that has read, then writes after another
 * connection committed, gets {@code SQLITE_BUSY_SNAPSHOT} at once, with no wait.
 *
 * <p>{@code @DirtiesContext} after the class, because the cached context keeps the database file open
 * and Windows refuses to delete a directory holding an open file.
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(LockedDatabaseFileTranslator.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Epic("Architecture")
@Feature("A locked database file is named")
@Issue("364")
@Link(name = "ADR-177", url = Adr.ONE_INVOCATION_PER_WORKING_DIRECTORY, type = "adr")
@Link(name = "ADR-180", url = Adr.THE_DATABASE_USES_SQLITES_WRITE_AHEAD_LOG, type = "adr")
class LockedDatabaseFileTest {

    /** The database file's name, as the shipped URL names it under the working directory. */
    private static final String DATABASE_FILE = "vespera.db";

    /** A version number for the rows these tests write; nothing reads it. */
    private static final int ANY_VERSION = 1;

    @TempDir
    static Path workingDirectory;

    @DynamicPropertySource
    static void workingDirectory(DynamicPropertyRegistry registry) {
        registry.add("vespera.working-dir", workingDirectory::toString);
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DataSource dataSource;

    @Value("${spring.datasource.url}")
    private String shippedUrl;

    @Test
    @Story("A locked database file says which file, and that another process holds it")
    @DisplayName("A write that meets another connection's commit fails naming the database file and another process")
    void aWriteThatMeetsAnotherCommitNamesTheFile() throws SQLException {
        // The test's own transaction has read, so it holds a snapshot of the file as it was.
        jdbcTemplate.queryForObject("SELECT COUNT(*) FROM schema_version", Long.class);
        try (Connection anotherProgram = DriverManager.getConnection(shippedUrl);
                Statement statement = anotherProgram.createStatement()) {
            statement.executeUpdate("INSERT INTO schema_version (module, version) VALUES ('another program', 1)");
        }

        Throwable failed = catchThrowable(() -> jdbcTemplate.update(
                "INSERT INTO schema_version (module, version) VALUES (?, ?)", "this invocation", ANY_VERSION));

        Path databaseFile = workingDirectory.resolve(DATABASE_FILE);
        claim("the write fails as a locked database file rather than as an uncategorised SQL failure",
                () -> assertThat(failed).isInstanceOf(DatabaseFileLockedException.class));
        claim("naming the database file in the working directory",
                () -> {
                    assertThat(((DatabaseFileLockedException) failed).databaseFile()).isEqualTo(databaseFile);
                    assertThat(failed.getMessage()).contains(databaseFile.toString());
                });
        claim("and saying another process holds it", () -> assertThat(failed.getMessage()).contains("another process"));
    }

    @Test
    @Story("A locked database file says which file, and that another process holds it")
    @DisplayName("Any other failure is reported exactly as it was before")
    void anyOtherFailureIsReportedAsBefore() {
        jdbcTemplate.update("INSERT INTO schema_version (module, version) VALUES (?, ?)", "written twice", ANY_VERSION);

        Throwable throughTheShippedTemplate = catchThrowable(() -> jdbcTemplate.update(
                "INSERT INTO schema_version (module, version) VALUES (?, ?)", "written twice", ANY_VERSION));
        Throwable withNoTranslatorOfOurs = catchThrowable(() -> new JdbcTemplate(dataSource).update(
                "INSERT INTO schema_version (module, version) VALUES (?, ?)", "written twice", ANY_VERSION));

        claim("a key written twice is not mistaken for a locked database file",
                () -> assertThat(throughTheShippedTemplate).isNotInstanceOf(DatabaseFileLockedException.class));
        claim("and fails as the same kind of failure a template with no translator of ours reports",
                () -> assertThat(throughTheShippedTemplate).hasSameClassAs(withNoTranslatorOfOurs));
    }

    @Test
    @Story("A locked database file says which file, and that another process holds it")
    @DisplayName("Busy, busy-snapshot and locked are all a locked database file, and a constraint failure is not")
    void everyLockCodeIsALockedDatabaseFile() {
        LockedDatabaseFileTranslator translator = new LockedDatabaseFileTranslator(workingDirectory);
        Path databaseFile = workingDirectory.resolve(DATABASE_FILE);
        SQLiteException busy = new SQLiteException(
                "[SQLITE_BUSY] The database file is locked (database is locked)", SQLiteErrorCode.SQLITE_BUSY);

        DataAccessException fromBusy = translator.translate("an insert", "INSERT", busy);
        DataAccessException fromSnapshot = translator.translate("an insert", "INSERT", new SQLiteException(
                "[SQLITE_BUSY_SNAPSHOT] Another database connection has already written to the database"
                        + " (database is locked)",
                SQLiteErrorCode.SQLITE_BUSY_SNAPSHOT));
        DataAccessException fromLocked = translator.translate("an insert", "INSERT", new SQLiteException(
                "[SQLITE_LOCKED] A table in the database is locked", SQLiteErrorCode.SQLITE_LOCKED));
        DataAccessException fromWrapped = translator.translate("an insert", "INSERT", new SQLException("wrapped", busy));
        DataAccessException fromConstraint = translator.translate("an insert", "INSERT", new SQLiteException(
                "[SQLITE_CONSTRAINT] Abort due to constraint violation", SQLiteErrorCode.SQLITE_CONSTRAINT));

        claim("a busy database file is a locked one, and the sentence names the file, says another process"
                        + " holds it, and keeps what SQLite said",
                () -> assertThat(fromBusy)
                        .isInstanceOf(DatabaseFileLockedException.class)
                        .hasMessage("the database file " + databaseFile + " is held by another process, which is"
                                + " writing to it or keeping a transaction open on it (" + busy.getMessage() + ")"));
        claim("so is a snapshot another connection's commit made stale, which the busy timeout never waits on",
                () -> assertThat(fromSnapshot).isInstanceOf(DatabaseFileLockedException.class));
        claim("and a locked table", () -> assertThat(fromLocked).isInstanceOf(DatabaseFileLockedException.class));
        claim("and a busy database file found beneath another SQL failure",
                () -> assertThat(fromWrapped).isInstanceOf(DatabaseFileLockedException.class));
        claim("a constraint failure is not a locked database file",
                () -> assertThat(fromConstraint instanceof DatabaseFileLockedException).isFalse());
    }
}
