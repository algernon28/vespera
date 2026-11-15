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
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringApplicationHook;

/**
 * ADR-397 §3: {@code VesperaApplication.main} registers the listener that prepares the folder beside the jar, writing
 * a missing file from the jar's shipped copy and saying which differ, ahead of the listeners that prepare the working
 * directory, so that it hears that a start has begun before Spring prepares its environment and reads the
 * configuration there.
 *
 * <p>It calls {@code main} under a {@link SpringApplicationHook} that reads the application's listeners and abandons
 * the run before any event is published, as {@code StartUpListenersAreRegisteredInOrderTest} does and for its reasons:
 * no listener of {@code main}'s is told anything, so nothing is written beside any jar, and the working directory it is
 * handed is never made. The listener is named by its simple class name, so that this compiles before it exists.
 */
@Epic("Architecture")
@Feature("Shipped configuration")
@Issue("836")
@Link(name = "ADR-397", url = Adr.THE_YAML_CONFIGURATION_LIES_BESIDE_THE_JAR, type = "adr")
class TheFolderBesideTheJarIsPreparedBeforeSpringReadsItTest {

    /** The listener ADR-397 §3 adds, then the first of those that prepare the working directory (ADR-054). */
    private static final List<String> IN_ORDER = List.of("BesideTheJar", "WorkingDirectoryPreparer");

    @TempDir
    Path parent;

    @Test
    @Story("The configuration lies beside the jar")
    @DisplayName("Starting the application registers the listener that prepares the folder beside the jar, ahead of the working directory's")
    void mainRegistersTheFolderBesideTheJarFirst() {
        Path neverPrepared = parent.resolve("never prepared");
        List<String> registered = new ArrayList<>();
        SpringApplicationHook readsTheListenersAndAbandons = application -> {
            application.getListeners().forEach(listener -> registered.add(listener.getClass().getSimpleName()));
            throw new SpringApplication.AbandonedRunException();
        };

        Throwable ended = catchThrowable(() -> SpringApplication.withHook(
                readsTheListenersAndAbandons,
                () -> VesperaApplication.main(new String[] {"--db-dir=" + neverPrepared})));

        claim("the start was abandoned where this test abandoned it, before any listener was told anything",
                () -> assertThat(ended).isInstanceOf(SpringApplication.AbandonedRunException.class));
        claim("the listener that prepares the folder beside the jar is registered, ahead of the one that creates the"
                        + " working directory, so it hears that a start has begun before Spring reads the configuration"
                        + " it prepares",
                () -> assertThat(registered).as("the application's listeners, in the order they hear an event")
                        .containsSubsequence(IN_ORDER));
    }
}
