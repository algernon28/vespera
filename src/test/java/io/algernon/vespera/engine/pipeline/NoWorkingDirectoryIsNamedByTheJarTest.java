package io.algernon.vespera.engine.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import io.algernon.vespera.Adr;
import io.algernon.vespera.TheShippedCopy;
import io.algernon.vespera.VesperaApplication;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.UnaryOperator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringApplicationHook;
import org.springframework.boot.bootstrap.DefaultBootstrapContext;
import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.boot.context.logging.LoggingApplicationListener;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.support.EnvironmentPostProcessorApplicationListener;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.mock.env.MockEnvironment;

/**
 * The jar names no working directory of its own (ADR-436 §1, §2, §8): the shipped {@code application.yaml} reads
 * {@code working-dir: ${db-dir:}}, so that {@code --db-dir=<path>} still names one and nothing else does, and its
 * comment says the {@code .vespera} folder under the current directory is no longer used; a start of the command line
 * through Spring whose {@code vespera.working-dir} names no folder is refused by {@code WorkingDirectoryNamedCheck},
 * which hears the environment prepared after the configuration files are read and before Logback is set up, and which
 * {@code VesperaApplication.main} registers. How the entry point says the refusal is {@code EntryPointTest}'s.
 *
 * <p>The shipped file is read alone into an environment that holds nothing of this machine's, so that no variable the
 * machine running the test exports reaches it.
 */
@Epic("Operation")
@Feature("Shipped configuration")
@Issue("905")
@Link(name = "ADR-436", url = Adr.NOTHING_USES_A_WORKING_DIRECTORY_NOTHING_NAMES, type = "adr")
class NoWorkingDirectoryIsNamedByTheJarTest {

    /** The property every command opens its working directory by (ADR-054). */
    private static final String WORKING_DIR = "vespera.working-dir";

    /** The property {@code --db-dir=<path>} sets, which the shipped line reads. */
    private static final String DB_DIR = "db-dir";

    /** The line a start naming no working directory is refused in, whole (ADR-436 §1). */
    private static final String NO_WORKING_DIRECTORY = "Vespera is not started: no working directory is named. Name one"
            + " with --db-dir=<path>, the environment variable VESPERA_WORKING_DIR, or vespera.working-dir in"
            + " application.yaml beside the jar.";

    /** The sentence the shipped comment above the line holds, its lines joined by a space (ADR-436 §1). */
    private static final String THE_COMMENTS_SENTENCE = "When nothing names one, run, label, publish and serve refuse"
            + " to start: the .vespera folder under the current directory, which earlier versions used, is no longer used.";

    /** The shipped line's key, indented under {@code vespera:} as the file writes it. */
    private static final String THE_LINES_KEY = "  working-dir:";

    @TempDir
    Path base;

    @Test
    @Story("A command names its working directory")
    @DisplayName("The shipped configuration names no working directory where nothing sets db-dir, and names the one db-dir or vespera.working-dir sets")
    void theShippedLineNamesNoFolderWhereNothingSetsDbDir() throws IOException {
        String nothingSet = shippedResolving(environment -> environment);
        String dbDirSet = shippedResolving(environment -> environment.withProperty(DB_DIR, folder("from-db-dir")));
        String bothSet = shippedResolving(environment -> environment.withProperty(DB_DIR, folder("from-db-dir"))
                .withProperty(WORKING_DIR, folder("from-the-property")));

        claim("with nothing setting db-dir or vespera.working-dir, the shipped line resolves to the empty string: no"
                        + " folder, and no .vespera under the folder the command is started in",
                () -> assertThat(nothingSet).isEmpty());
        claim("with db-dir set, as --db-dir=<path> sets it, the line resolves to that folder",
                () -> assertThat(dbDirSet).isEqualTo(folder("from-db-dir")));
        claim("and a vespera.working-dir set as a property comes before the file's line, db-dir notwithstanding",
                () -> assertThat(bothSet).isEqualTo(folder("from-the-property")));
    }

    @Test
    @Story("A command names its working directory")
    @DisplayName("The shipped configuration's comment above the working directory says the .vespera folder is no longer used")
    void theShippedCommentSaysTheDotVesperaFolderIsNoLongerUsed() throws IOException {
        List<String> lines = Files.readAllLines(TheShippedCopy.APPLICATION_YAML_SOURCE, StandardCharsets.UTF_8);
        int key = -1;
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).startsWith(THE_LINES_KEY)) {
                key = i;
                break;
            }
        }
        List<String> comment = new ArrayList<>();
        for (int i = key - 1; i >= 0 && lines.get(i).strip().startsWith("#"); i--) {
            comment.add(lines.get(i).strip().substring(1).strip());
        }
        Collections.reverse(comment);
        String joined = String.join(" ", comment);
        int found = key;

        claim("the shipped application.yaml has the working directory's line under vespera:",
                () -> assertThat(found).as("the index of the line " + THE_LINES_KEY).isNotNegative());
        claim("and the comment right above it, its lines joined, holds the sentence saying run, label, publish and serve"
                        + " refuse where nothing names one, and that the .vespera folder earlier versions used is no"
                        + " longer used",
                () -> assertThat(joined).contains(THE_COMMENTS_SENTENCE));
    }

    @Test
    @Story("A command names its working directory")
    @DisplayName("A start whose working directory is not set, empty or blank is refused in one line once the configuration is read and before the log is set up")
    void aStartNamingNoFolderIsRefusedBeforeTheLogIsSetUp() {
        WorkingDirectoryNamedCheck check = new WorkingDirectoryNamedCheck();
        List<Throwable> refused = new ArrayList<>();
        refused.add(catchThrowable(() -> check.onApplicationEvent(prepared(new MockEnvironment()))));
        refused.add(catchThrowable(() -> check.onApplicationEvent(prepared(new MockEnvironment()
                .withProperty(WORKING_DIR, "")))));
        refused.add(catchThrowable(() -> check.onApplicationEvent(prepared(new MockEnvironment()
                .withProperty(WORKING_DIR, "   ")))));
        Path named = base.resolve("named-and-not-there");
        Throwable letThrough = catchThrowable(() -> check.onApplicationEvent(prepared(new MockEnvironment()
                .withProperty(WORKING_DIR, named.toString()))));
        int order = check.getOrder();

        claim("a working directory not set, empty and white space alone are each refused by the exception saying no"
                        + " working directory is named, its message the line whole",
                () -> {
                    assertThat(refused).allSatisfy(each -> assertThat(each)
                            .isExactlyInstanceOf(NoWorkingDirectoryNamedException.class));
                    assertThat(refused).extracting(Throwable::getMessage)
                            .containsExactly(NO_WORKING_DIRECTORY, NO_WORKING_DIRECTORY, NO_WORKING_DIRECTORY);
                });
        claim("a folder named is let through, and the check makes nothing of it",
                () -> {
                    assertThat(letThrough).isNull();
                    assertThat(named).doesNotExist();
                });
        claim("the check hears the environment after the configuration files are read into it and before Logback is"
                        + " set up, so a refused start opens no log",
                () -> assertThat(order)
                        .isGreaterThan(EnvironmentPostProcessorApplicationListener.DEFAULT_ORDER)
                        .isLessThan(LoggingApplicationListener.DEFAULT_ORDER));
    }

    @Test
    @Story("A command names its working directory")
    @DisplayName("Starting the application registers the check that refuses a start naming no working directory")
    void theApplicationRegistersTheCheck() {
        Path neverPrepared = base.resolve("never prepared");
        List<Object> registered = new ArrayList<>();
        SpringApplicationHook readsTheListenersAndAbandons = application -> {
            registered.addAll(application.getListeners());
            throw new SpringApplication.AbandonedRunException();
        };

        Throwable ended = catchThrowable(() -> SpringApplication.withHook(readsTheListenersAndAbandons,
                () -> VesperaApplication.main(new String[] {"--db-dir=" + neverPrepared})));

        claim("the start was abandoned where this test abandoned it, before any listener was told anything",
                () -> assertThat(ended).isInstanceOf(SpringApplication.AbandonedRunException.class));
        claim("and among the application's listeners is the check of a working directory named, once",
                () -> assertThat(registered).filteredOn(WorkingDirectoryNamedCheck.class::isInstance).hasSize(1));
    }

    // ---------- helpers ----------

    /** {@code name} under the test's folder, as a string. */
    private String folder(String name) {
        return base.resolve(name).toString();
    }

    /**
     * What {@code vespera.working-dir} resolves to over the shipped {@code application.yaml} alone, read below the
     * properties {@code setUp} sets, as Spring reads the file below a system property, the environment and the command
     * line.
     */
    private static String shippedResolving(UnaryOperator<MockEnvironment> setUp) throws IOException {
        MockEnvironment environment = setUp.apply(new MockEnvironment());
        List<PropertySource<?>> shipped = new YamlPropertySourceLoader()
                .load("shipped application.yaml", new FileSystemResource(TheShippedCopy.APPLICATION_YAML_SOURCE));
        shipped.forEach(source -> environment.getPropertySources().addLast(source));
        return environment.getProperty(WORKING_DIR);
    }

    private static ApplicationEnvironmentPreparedEvent prepared(MockEnvironment environment) {
        return new ApplicationEnvironmentPreparedEvent(new DefaultBootstrapContext(), new SpringApplication(),
                new String[0], environment);
    }
}
