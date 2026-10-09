package io.algernon.vespera;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

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
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringApplicationHook;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;

/**
 * ADR-211 section 12: {@code VesperaApplication.main} registers the listener that sets SQLite's directory for
 * temporary files, and registers it last of the four that hear the environment prepared: after the one that
 * creates the working directory (ADR-054), the one that takes its lock (ADR-177) and the check of the
 * profile's shape (#321).
 *
 * <p>Leaving the registration out is silent: every other test passes, and the process writes its temporary
 * files to the system drive. So this calls {@code main} itself, under a {@link SpringApplicationHook}, which
 * Spring Boot asks for a run listener once the application is built and its run has begun, before it tells
 * any listener anything. The hook reads the application's listeners there and abandons the run by throwing
 * Boot's own {@link SpringApplication.AbandonedRunException}, which {@code main} does not take for either of
 * its two refusals and throws again, so it never reaches {@code System.exit}.
 *
 * <p><b>What of {@code main} runs here</b> is the building of the {@code SpringApplication}, the registration
 * read below, and the beginning of {@code run}, read in Boot 4.1.1's source: the object that times the start;
 * {@code enableShutdownHookAddition()}, which sets a flag in a static field of Boot's and by itself adds no
 * hook to the JVM, one being added only when a context is registered, and none is here; a bootstrap context
 * that is thrown away; and the {@code java.awt.headless} system property, which every Spring Boot test sets
 * too. No event is published, not
 * even that a start has begun, so no listener of {@code main}'s is called: no working directory is created,
 * no lock taken, no connection opened and SQLite's setting not touched. The test holds each of those: it
 * hands {@code main} a working directory that does not exist and finds it still missing, hears no event
 * through a listener of its own, and reads SQLite's setting afterwards. Throwing from a run listener's
 * {@code starting}, the other place a hook offers, comes after the event that a start has begun is published,
 * which sets up logging for a start that never happens; that was read in Boot 4.1.1's source and not used.
 *
 * <p>The listeners are read as {@link SpringApplication#getListeners()} hands them out, ordered as an event is
 * delivered to them: the four declare no order, so they keep the order {@code main} added them in.
 *
 * <p>Compiles before ADR-211 is built, naming the listeners by their text, and fails there: three of the four
 * are registered.
 */
@Epic("Architecture")
@Feature("Shipped configuration")
@Issue("456")
@Link(name = "ADR-211", url = Adr.NO_CLASS_HOLDS_EVERY_SURVIVOR_OF_A_RUN, type = "adr")
@Link(name = "ADR-177", url = Adr.ONE_INVOCATION_PER_WORKING_DIRECTORY, type = "adr")
class StartUpListenersAreRegisteredInOrderTest {

    /** The three registered before ADR-211, by simple class name, in the order they hear the event. */
    private static final List<String> ALREADY_REGISTERED =
            List.of("WorkingDirectoryPreparer", "WorkingDirectoryLock", "ProfileShapeCheck");

    /** The listener ADR-211 section 12 adds, named by its text so that this compiles before it exists. */
    private static final String SETS_THE_TEMPORARY_FILES_DIRECTORY = "TemporaryFilesInTheWorkingDirectory";

    @TempDir
    Path parent;

    @Test
    @Story("What the database sorts on disk is written in the working directory")
    @DisplayName("Starting the application registers the listener that sends the database's temporary files to the working directory, after the ones that create the working directory, lock it and check its profile")
    void mainRegistersTheTemporaryFilesListenerAfterTheOthers() throws SQLException {
        Path neverPrepared = parent.resolve("never prepared");
        List<String> registered = new ArrayList<>();
        List<ApplicationEvent> heard = new ArrayList<>();
        SpringApplicationHook readsTheListenersAndAbandons = application -> {
            application.getListeners().forEach(listener -> registered.add(listener.getClass().getSimpleName()));
            application.addListeners(new ApplicationListener<ApplicationEvent>() {
                @Override
                public void onApplicationEvent(ApplicationEvent event) {
                    heard.add(event);
                }
            });
            throw new SpringApplication.AbandonedRunException();
        };

        Throwable ended = catchThrowable(() -> SpringApplication.withHook(
                readsTheListenersAndAbandons,
                () -> VesperaApplication.main(new String[] {"--db-dir=" + neverPrepared})));

        claim(
                "the start was abandoned where this test abandoned it, and that is what came out of it: nothing"
                        + " else failed, and the process was not ended",
                () -> assertThat(ended).isInstanceOf(SpringApplication.AbandonedRunException.class));
        claim(
                "no listener was told anything, not even that a start had begun, so none of the application's"
                        + " own ran",
                () -> assertThat(heard).isEmpty());
        claim(
                "the working directory the start was given does not exist, as before: nothing created it, so"
                        + " nothing locked it or opened a database in it",
                () -> assertThat(neverPrepared).doesNotExist());
        claim(
                "and the database still has no directory set for its temporary files in this process",
                () -> assertThat(temporaryFilesDirectory()).isEmpty());
        claim(
                "the three listeners that create the working directory, take its lock and check its profile are"
                        + " registered, in that order",
                () -> assertThat(registered).containsSubsequence(ALREADY_REGISTERED));
        List<String> inOrder = new ArrayList<>(ALREADY_REGISTERED);
        inOrder.add(SETS_THE_TEMPORARY_FILES_DIRECTORY);
        claim(
                "the listener that tells the database to write its temporary files in the working directory is"
                        + " registered too, after those three: the directory exists by then and is this start's"
                        + " alone, and a start refused for a directory in use or a profile that does not load has"
                        + " changed nothing about the database",
                () -> assertThat(registered).containsSubsequence(inOrder));
    }

    /** What SQLite reports as the directory it writes temporary files in: empty where none is set. */
    private static String temporaryFilesDirectory() throws SQLException {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:");
                Statement statement = connection.createStatement();
                ResultSet result = statement.executeQuery("PRAGMA temp_store_directory")) {
            return result.next() && result.getString(1) != null ? result.getString(1) : "";
        }
    }
}
