package io.algernon.vespera.ledger;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sqlite.SQLiteDataSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * One schema version per module, refused independently (ADR-059).
 *
 * <p>The failure is the behaviour worth pinning: a mismatch has to stop the module before it reads
 * or writes anything, and has to say enough for an operator to know which way the mismatch runs.
 * There is no degraded mode to test, which is the decision.
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Epic("Architecture")
@Feature("Schema versioning")
@Issue("9")
@Link(name = "ADR-059", url = Adr.SCHEMA_VERSION_IS_ONE_ROW_PER_MODULE, type = "adr")
class SchemaVersionGuardTest {

    /** The version a module in these tests claims to have been built against. */
    private static final int EXPECTED = 3;

    /** A version the database might hold instead, from code that has since been changed. */
    private static final int STALE = 2;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @Story("A module states the schema it was built against")
    @DisplayName("The first run of a module records the version it expects")
    void recordsAModuleVersionOnItsFirstRun() {
        SchemaVersionGuard guard = new SchemaVersionGuard(jdbcTemplate);

        guard.require("fictional", EXPECTED);

        claim(
                "the module's first run recorded version " + EXPECTED + ", the version its code expects",
                () -> assertThat(jdbcTemplate.queryForObject(
                                "SELECT version FROM schema_version WHERE module = ?", Integer.class, "fictional"))
                        .isEqualTo(EXPECTED));
    }

    @Test
    @Story("A module states the schema it was built against")
    @DisplayName("A module whose recorded version matches carries on")
    void acceptsAMatchingVersion() {
        SchemaVersionGuard guard = new SchemaVersionGuard(jdbcTemplate);
        guard.require("fictional", EXPECTED);

        claim(
                "a second run against the same version is not a mismatch",
                () -> assertThatCode(() -> guard.require("fictional", EXPECTED)).doesNotThrowAnyException());
    }

    @Test
    @Story("A module states the schema it was built against")
    @DisplayName("A module whose recorded version differs refuses to start, naming both versions")
    void refusesAMismatchedVersionNamingBoth() {
        jdbcTemplate.update("INSERT INTO schema_version (module, version) VALUES (?, ?)", "fictional", STALE);
        SchemaVersionGuard guard = new SchemaVersionGuard(jdbcTemplate);

        claim(
                "a database recording version " + STALE + " for a module whose code expects " + EXPECTED
                        + " refuses, and says both numbers and the module",
                () -> assertThatThrownBy(() -> guard.require("fictional", EXPECTED))
                        .isInstanceOf(SchemaVersionMismatchException.class)
                        .hasMessageContaining("fictional")
                        .hasMessageContaining(String.valueOf(EXPECTED))
                        .hasMessageContaining(String.valueOf(STALE)));
    }

    @Test
    @Story("A module states the schema it was built against")
    @DisplayName("A mismatched database stops the application from starting at all")
    void refusesToStartAgainstAMismatchedDatabase() {
        int somethingOtherThanTheLedgerExpects = LedgerSchema.VERSION + 1;
        jdbcTemplate.update(
                "INSERT INTO schema_version (module, version) VALUES (?, ?)",
                LedgerSchema.MODULE,
                somethingOtherThanTheLedgerExpects);

        claim(
                "the check runs while the application is being built rather than on first use, so a"
                        + " mismatched database is refused before anything reads or writes a row",
                () -> assertThatThrownBy(() -> new LedgerSchema(new SchemaVersionGuard(jdbcTemplate)))
                        .isInstanceOf(SchemaVersionMismatchException.class)
                        .hasMessageContaining(LedgerSchema.MODULE)
                        .hasMessageContaining(String.valueOf(LedgerSchema.VERSION))
                        .hasMessageContaining(String.valueOf(somethingOtherThanTheLedgerExpects)));
    }

    @Test
    @Story("A module states the schema it was built against")
    @DisplayName("One module's mismatch says nothing about another module's tables")
    void refusesOneModuleWithoutRefusingAnother() {
        SchemaVersionGuard guard = new SchemaVersionGuard(jdbcTemplate);
        jdbcTemplate.update("INSERT INTO schema_version (module, version) VALUES (?, ?)", "stale-module", STALE);
        guard.require("current-module", EXPECTED);

        claim(
                "the module whose version matches carries on even while another module's is stale, which is"
                        + " what one row per module buys",
                () -> assertThatCode(() -> guard.require("current-module", EXPECTED)).doesNotThrowAnyException());
        claim(
                "and the stale module still refuses",
                () -> assertThatThrownBy(() -> guard.require("stale-module", EXPECTED))
                        .isInstanceOf(SchemaVersionMismatchException.class));
    }

    @Test
    @Issue("364")
    @Link(name = "ADR-177", url = Adr.ONE_INVOCATION_PER_WORKING_DIRECTORY, type = "adr")
    @Story("A module states the schema it was built against")
    @DisplayName("A mismatch names the database file it read, and how to name another working directory")
    void aMismatchNamesTheDatabaseFile(@TempDir Path workingDirectory) {
        Path databaseFile = workingDirectory.resolve("vespera.db");
        SQLiteDataSource onFile = new SQLiteDataSource();
        onFile.setUrl("jdbc:sqlite:" + databaseFile);
        JdbcTemplate overTheFile = new JdbcTemplate(onFile);
        overTheFile.execute("CREATE TABLE schema_version (module TEXT PRIMARY KEY, version INTEGER NOT NULL)");
        overTheFile.update("INSERT INTO schema_version (module, version) VALUES (?, ?)", "fictional", STALE);

        Throwable refused = catchThrowable(() -> new SchemaVersionGuard(overTheFile).require("fictional", EXPECTED));

        claim(
                "the refusal names the database file that recorded the other version, so an operator can see"
                        + " whether it is the one they meant",
                () -> assertThat(refused)
                        .isInstanceOf(SchemaVersionMismatchException.class)
                        .hasMessage("module fictional expects schema version " + EXPECTED + ", but the database file "
                                + databaseFile + " records version " + STALE + "; delete and recreate fictional's"
                                + " tables, then re-run census. If that is not the working directory you meant,"
                                + " name it with --db-dir=<path> or vespera.working-dir"));
    }

    @Test
    @Issue("364")
    @Link(name = "ADR-177", url = Adr.ONE_INVOCATION_PER_WORKING_DIRECTORY, type = "adr")
    @Story("A module states the schema it was built against")
    @DisplayName("A mismatch in a database held only in memory says so in place of a file")
    void aMismatchInMemorySaysSo() {
        jdbcTemplate.update("INSERT INTO schema_version (module, version) VALUES (?, ?)", "in-memory", STALE);

        claim(
                "a database with no file is named as an in-memory database",
                () -> assertThatThrownBy(() -> new SchemaVersionGuard(jdbcTemplate).require("in-memory", EXPECTED))
                        .isInstanceOf(SchemaVersionMismatchException.class)
                        .hasMessageContaining("but an in-memory database records version " + STALE));
    }
}
