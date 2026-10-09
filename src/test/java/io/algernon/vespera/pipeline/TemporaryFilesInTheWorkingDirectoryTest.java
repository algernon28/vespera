package io.algernon.vespera.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.bootstrap.DefaultBootstrapContext;
import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.mock.env.MockEnvironment;

/**
 * ADR-211 section 12: when the process starts, before any connection pool exists, SQLite's directory for
 * temporary files is set to the working directory, once, so what a statement sorts on disk is written beside
 * the database and not in the system's temporary folder.
 *
 * <p>{@code TemporaryFilesInTheWorkingDirectory} is an environment listener that {@code
 * VesperaApplication.main} registers after the ones that create the working directory and take its lock. It
 * opens a connection of its own to no database, runs {@code PRAGMA temp_store_directory} with the working
 * directory's absolute path between single quotes, each apostrophe in the path doubled, and closes it. SQLite
 * keeps the directory for the whole process, so every connection opened afterwards uses it, and nothing runs
 * the pragma again while another connection is at work, which SQLite's documentation forbids. Where SQLite
 * refuses the directory the listener throws, naming it, and the start ends there.
 *
 * <p>The working directory here is a folder whose name has a space, an ampersand and an apostrophe in it.
 * Each test puts the default back when it is done, before and after: the setting outlives the listener and
 * would follow every later test in this JVM to a folder JUnit deletes.
 *
 * <p><b>These tests do in the suite's JVM what the listener never does in the application's</b>: they change
 * the process's setting while other connections are open, the ones the Spring contexts cached across test
 * classes hold. SQLite's documentation calls that undefined, and says never to change the setting while
 * another thread is running any SQLite interface. It is accepted here, for the suite and for nothing
 * shipped, on three conditions. The suite runs one test at a time in one JVM: the pom gives surefire no
 * parallel and no fork setting, and the test tree has no {@code junit-platform.properties}. No thread of a
 * cached context's pool touches SQLite while a test runs: the test profile's pool never retires its one
 * connection, never drops it for being idle and never checks it from a thread of its own ({@code
 * max-lifetime} and {@code idle-timeout} are 0 in {@code application-test.yaml} and {@code keepalive-time}
 * is 0 in the {@code application.yaml} it is layered over, all three of which {@code
 * NoPoolSetsSqlitesTemporaryDirectoryTest} holds), and a context that is not under the test profile is
 * closed when its class ends. And the connections left open are idle while a test here runs,
 * with the default back before the next test begins. Should any of the three stop holding, this class has
 * to run in a JVM of its own.
 *
 * <p>The last test looks for the file itself and runs on Windows only: on Unix SQLite is expected to remove
 * a temporary file's name as soon as it has opened it, so there is nothing to find in the folder there.
 *
 * <p><b>Parked</b> under {@code docs/adr/0211/tests/}: it names the listener ADR-211 adds, and would stop the
 * test tree compiling before it exists.
 */
@Epic("Architecture")
@Feature("Shipped configuration")
@Issue("456")
@Link(name = "ADR-211", url = Adr.NO_CLASS_HOLDS_EVERY_SURVIVOR_OF_A_RUN, type = "adr")
class TemporaryFilesInTheWorkingDirectoryTest {

    /** A folder name with a space, an ampersand and an apostrophe in it. */
    private static final String AN_AWKWARD_NAME = "R&D o'clock dir";

    /** The build's own output folder, under the current directory: where a relative path can be made and removed. */
    private static final String UNDER_THE_CURRENT_DIRECTORY = "target";

    /** Enough rows of about forty bytes for a sort to pass what SQLite keeps in memory several times. */
    private static final int ROWS_TO_SORT = 300_000;

    @TempDir
    Path parent;

    private Path workingDirectory;

    @BeforeEach
    void anAwkwardlyNamedWorkingDirectoryAndTheDefault() throws IOException, SQLException {
        workingDirectory = Files.createDirectories(parent.resolve(AN_AWKWARD_NAME));
        backToTheDefault();
    }

    @AfterEach
    void backToTheDefault() throws SQLException {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:");
                Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA temp_store_directory = ''");
        }
    }

    @Test
    @Story("What the database sorts on disk is written in the working directory")
    @DisplayName("Once the process has prepared its working directory, every connection opened afterwards writes its temporary files there")
    void aConnectionOpenedAfterwardsReportsTheWorkingDirectory() throws SQLException {
        new TemporaryFilesInTheWorkingDirectory().onApplicationEvent(prepared(workingDirectory.toString()));

        try (Connection later = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            claim(
                    "a connection opened after the listener has run, to a database of its own, reports the working"
                            + " directory, a folder named \"" + AN_AWKWARD_NAME + "\", as where SQLite writes its"
                            + " temporary files: the setting is the process's, made once",
                    () -> assertThat(Path.of(temporaryFilesDirectory(later)).toAbsolutePath().normalize())
                            .isEqualTo(workingDirectory.toAbsolutePath().normalize()));
        }
    }

    @Test
    @Story("What the database sorts on disk is written in the working directory")
    @DisplayName("With no working directory named, nothing is set")
    void withNoWorkingDirectoryNothingIsSet() throws SQLException {
        new TemporaryFilesInTheWorkingDirectory().onApplicationEvent(prepared(""));

        try (Connection later = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            claim(
                    "with the working directory's property blank, as the listener that creates the directory"
                            + " also leaves it, SQLite is told nothing and goes on using the system's folder",
                    () -> assertThat(temporaryFilesDirectory(later)).isEmpty());
        }
    }

    @Test
    @Story("What the database sorts on disk is written in the working directory")
    @DisplayName("A working directory named by a path relative to where the command was started is given to the database as an absolute path")
    void aRelativeWorkingDirectoryIsSetAsAnAbsolutePath() throws SQLException, IOException {
        Path relative = Path.of(UNDER_THE_CURRENT_DIRECTORY, "a relative working directory " + System.nanoTime());
        Files.createDirectories(relative);
        try {
            new TemporaryFilesInTheWorkingDirectory().onApplicationEvent(prepared(relative.toString()));

            try (Connection later = DriverManager.getConnection("jdbc:sqlite::memory:")) {
                String reported = temporaryFilesDirectory(later);
                claim(
                        "the path this test names is relative, \"" + relative + "\", as a working directory named"
                                + " on the command line may be",
                        () -> assertThat(relative).isRelative());
                claim(
                        "what the database is given, and reports, is an absolute path, so where its temporary files"
                                + " go does not depend on what the current directory is when it writes one",
                        () -> assertThat(Path.of(reported)).isAbsolute());
                claim(
                        "and it is the same folder: the relative path, read against the current directory",
                        () -> assertThat(Path.of(reported).normalize())
                                .isEqualTo(relative.toAbsolutePath().normalize()));
            }
        } finally {
            backToTheDefault();
            Files.deleteIfExists(relative);
        }
    }

    @Test
    @Story("What the database sorts on disk is written in the working directory")
    @DisplayName("A working directory the database will not take for its temporary files ends the start, naming the directory, and sets nothing")
    void aDirectoryTheDatabaseRefusesEndsTheStartNamingIt() throws SQLException {
        Path notThere = parent.resolve("not there");

        Throwable thrown = catchThrowable(
                () -> new TemporaryFilesInTheWorkingDirectory().onApplicationEvent(prepared(notThere.toString())));

        claim(
                "told of a working directory that does not exist, which the database refuses as a place for its"
                        + " temporary files, the listener does not go on as if nothing had happened: it throws, and"
                        + " so ends the start it is part of",
                () -> assertThat(thrown).isNotNull());
        claim(
                "what it throws names the directory it could not set, \"" + notThere + "\", so the operator reads"
                        + " which one",
                () -> assertThat(thrown).hasMessageContaining(notThere.toString()));
        try (Connection later = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            claim(
                    "and nothing was set: a connection opened afterwards reports no directory, the database's own"
                            + " default still in place",
                    () -> assertThat(temporaryFilesDirectory(later)).isEmpty());
        }
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    @Story("What the database sorts on disk is written in the working directory")
    @DisplayName("A sort too large for memory writes its temporary file in the working directory while it runs")
    void aSortTooLargeForMemoryWritesItsFileInTheWorkingDirectory() throws SQLException, IOException {
        new TemporaryFilesInTheWorkingDirectory().onApplicationEvent(prepared(workingDirectory.toString()));

        Path elsewhere = Files.createDirectories(parent.resolve("elsewhere"));
        try (Connection connection = DriverManager.getConnection(
                        "jdbc:sqlite:" + elsewhere.resolve("a-database.db").toString().replace('\\', '/'));
                Statement statement = connection.createStatement();
                ResultSet sorted = statement.executeQuery(
                        "WITH RECURSIVE numbered(i) AS (SELECT 1 UNION ALL SELECT i + 1 FROM numbered WHERE i < "
                                + ROWS_TO_SORT + ") SELECT (i * 7919) % 300007 AS shuffled,"
                                + " 'padding-padding-padding-' || i AS padding FROM numbered ORDER BY shuffled, padding")) {
            claim(
                    "the sorted read hands over its first row, so the " + ROWS_TO_SORT + " rows are sorted",
                    () -> assertThat(sorted.next()).isTrue());
            List<String> written = filesIn(workingDirectory);
            claim(
                    "while that read is open, over a database kept in another folder, the working directory, which"
                            + " held nothing, holds the temporary file the sort was written to",
                    () -> assertThat(written).isNotEmpty());
        }
        claim(
                "and once the read is closed the working directory holds nothing again: SQLite removes a"
                        + " temporary file when it is done with it",
                () -> assertThat(filesIn(workingDirectory)).isEmpty());
    }

    private static ApplicationEnvironmentPreparedEvent prepared(String workingDirectory) {
        return new ApplicationEnvironmentPreparedEvent(
                new DefaultBootstrapContext(),
                new SpringApplication(),
                new String[0],
                new MockEnvironment().withProperty(WorkingDirectoryPreparer.PROPERTY, workingDirectory));
    }

    /** What SQLite reports as the directory it writes temporary files in: empty where none is set. */
    private static String temporaryFilesDirectory(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
                ResultSet result = statement.executeQuery("PRAGMA temp_store_directory")) {
            return result.next() && result.getString(1) != null ? result.getString(1) : "";
        }
    }

    private static List<String> filesIn(Path directory) throws IOException {
        try (Stream<Path> files = Files.list(directory)) {
            return files.map(file -> file.getFileName().toString()).sorted().toList();
        }
    }
}
