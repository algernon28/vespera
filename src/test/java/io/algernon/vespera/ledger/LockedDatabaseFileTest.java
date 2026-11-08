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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * A database file SQLite reports locked is named, and said to be held by another process, wherever a
 * statement meets the lock (ADR-177 §2, #364).
 *
 * <p>On 2026-09-28 stage 2's closing line named an SQL statement and {@code [SQLITE_BUSY] The database
 * file is locked}, and no file. The translator sits under the {@code JdbcTemplate} Spring Boot builds,
 * which every {@code JdbcTemplate} statement in every step goes through, so the sentence is the same in
 * every step. Spring Batch's paging readers build a template of their own and are not translated; under
 * the write-ahead log a reader is not blocked by a writer.
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
}
