package io.algernon.vespera.engine.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.algernon.vespera.Adr;
import io.algernon.vespera.engine.profile.ProfileFixture;
import io.algernon.vespera.engine.profile.ProfileStore;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * The progress page {@code vespera run} rewrites in the working directory while it runs (ADR-265), reached
 * through whole invocations: what it shows while a step runs, what its last write says, what it never holds,
 * how a step stopped at a gate and a failed step are shown, and that a page that cannot be written fails
 * nothing. The table of steps is read off the page of its own beside it (ADR-267 section 3).
 *
 * <p>The working directory is static, so each test starts by removing the page an earlier one left, and puts
 * back a profile that loads. The interval is a tenth of a second, so that a step this fixture runs lasts long
 * enough to be written more than once; the step is held at its progress line while the test looks, from the
 * thread that logged it, and the page is written by a thread of its own (ADR-265 section 2).
 */
@CascadeSliceTest
@Import(SeedScriptedExtractionBeans.class)
@Epic("Pipeline")
@Feature("The progress page")
@Link(name = "ADR-265", url = Adr.THE_RUN_REWRITES_A_PROGRESS_PAGE_WHILE_IT_RUNS, type = "adr")
@Link(name = "ADR-198", url = Adr.EVERY_INVOCATION_WRITES_AN_ACCOUNT_THAT_NAMES_NO_DOCUMENT, type = "adr")
@Link(name = "ADR-267", url = Adr.THE_PROGRESS_PAGE_DRAWS_CHARTS_AND_ITS_TABLES_ARE_PAGES_OF_THEIR_OWN, type = "adr")
class ProgressPageInvocationTest {

    /** The page's name in the working directory. */
    private static final String PAGE = "progress.html";

    /** The page beside it that holds the table of steps (ADR-267 section 3). */
    private static final String STEPS_PAGE = "progress-steps.html";

    /** The page beside it that holds a table of the samples the charts are drawn from (ADR-267 section 3). */
    private static final String SAMPLES_PAGE = "progress-samples.html";

    /** How often the page is rewritten in this class: a tenth of a second, so a short step is seen twice. */
    private static final String INTERVAL = "100ms";

    /** What every private name and text in the fixture carries, so one search finds any leak of any of them. */
    private static final String MARKER = "ZXQ-the-operators-private-word";

    private static final String MARKED_FOLDER = MARKER + "-folder";

    private static final List<String> MARKED_READABLE_FILES =
            List.of(MARKER + "-first.txt", MARKER + "-second.txt");

    /** The corpus file the scripted converter fails on while blaming itself. */
    private static final String FAULTED = SeedScriptedExtractionBeans.CONVERTER_FAULT;

    @TempDir
    static Path workingDirectory;

    @DynamicPropertySource
    static void workingDirectoryAndInterval(DynamicPropertyRegistry registry) {
        registry.add("vespera.working-dir", workingDirectory::toString);
        registry.add("vespera.progress-interval", () -> INTERVAL);
    }

    @Autowired
    private VesperaCli cli;

    @Autowired
    private ProfileStore profileStore;

    private ListAppender<ILoggingEvent> logged;
    private ch.qos.logback.classic.Logger rootLogger;

    /** The page an earlier test left, a file or the folder one test puts in its place, is gone before each. */
    @BeforeEach
    void noPageAndAProfileThatLoads() throws IOException {
        removeThePage();
        profileStore.save(ProfileFixture.profile().build());
        logged = new ListAppender<>();
        logged.start();
        rootLogger = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        rootLogger.addAppender(logged);
    }

    @AfterEach
    void releaseTheLinesAndPutAProfileBack() throws IOException {
        rootLogger.detachAppender(logged);
        logged.stop();
        removeThePage();
        profileStore.save(ProfileFixture.profile().build());
    }

    @Test
    @Story("The page can be sent to anyone: it names no path and no document")
    @DisplayName("A word marked in every name and text the invocation is given occurs nowhere on the page, and neither do the folders it was pointed at")
    void thePageNamesNoPathAndNoTextOfADocument(@TempDir Path root, @TempDir Path seeds) throws IOException {
        aMarkedCorpus(root, seeds);
        EveryGateOpen.throughStageFive(profileStore, seeds);

        cli.run("run", root.toString());

        for (String name : List.of(PAGE, STEPS_PAGE, SAMPLES_PAGE)) {
            String page = thePage(name);
            for (String forbidden : List.of(
                    MARKER,
                    MARKED_FOLDER,
                    FAULTED,
                    SeedScriptedExtractionBeans.CONVERTER_FAULT_MESSAGE,
                    root.toString(),
                    seeds.toString(),
                    workingDirectory.toString())) {
                claim(
                        name + " does not contain '" + forbidden + "', in any letter case, which the invocation was"
                                + " given or read",
                        () -> assertThat(page).doesNotContainIgnoringCase(forbidden));
            }
        }
    }

    /** The page {@code name} as the last write left it, failing with a plain message when there is none. */
    private String thePage(String name) throws IOException {
        Path page = workingDirectory.resolve(name);
        claim(
                "the invocation left " + name + " in the working directory, so there is a page to read",
                () -> assertThat(page).isRegularFile());
        return Files.readString(page, StandardCharsets.UTF_8);
    }

    /** The page, or the folder a test put in its place, removed with whatever it holds. */
    private static void removeThePage() throws IOException {
        Path page = workingDirectory.resolve(PAGE);
        if (!Files.exists(page)) {
            return;
        }
        try (Stream<Path> inside = Files.walk(page)) {
            for (Path path : inside.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(path);
            }
        }
    }

    /** Marks every directory, file name and text the invocation is given, and one file the converter faults on. */
    private static void aMarkedCorpus(Path root, Path seeds) throws IOException {
        Path folder = Files.createDirectory(root.resolve(MARKED_FOLDER));
        for (String name : MARKED_READABLE_FILES) {
            Files.writeString(folder.resolve(name), "the text of " + name + " says " + MARKER);
        }
        Files.writeString(folder.resolve(FAULTED), "a file the converter faults on, and " + MARKER);
        Files.writeString(seeds.resolve(MARKER + "-seed.txt"), "a seed that says " + MARKER);
    }

    /**
     * Hands each event to {@code onEvent} on the thread that logged it, so a step's thread can be held at one
     * of its lines while the page is looked at. Unsynchronised, so a line another thread logs meanwhile is not
     * held behind it.
     */
    private static final class HoldingAppender extends ch.qos.logback.core.UnsynchronizedAppenderBase<ILoggingEvent> {

        private final java.util.function.Consumer<ILoggingEvent> onEvent;

        HoldingAppender(java.util.function.Consumer<ILoggingEvent> onEvent) {
            this.onEvent = onEvent;
        }

        @Override
        protected void append(ILoggingEvent event) {
            onEvent.accept(event);
        }
    }
}
