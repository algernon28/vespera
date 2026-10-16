package io.algernon.vespera.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.algernon.vespera.Adr;
import io.algernon.vespera.profile.ProfileFixture;
import io.algernon.vespera.profile.ProfileStore;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
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
 * The progress page opens itself and is named first (ADR-266 sections 1 and 2), reached through whole invocations.
 * The slice's opener is the one that records what it is asked and opens nothing.
 *
 * <p>The working directory is static, so each test starts by removing the page an earlier one left and forgetting
 * what the opener and the log were told. The page is rewritten every tenth of a second, so a run writes it many
 * times and the opener is still asked once.
 */
@CascadeSliceTest
@Import(SeedScriptedExtractionBeans.class)
@Epic("Pipeline")
@Feature("The progress page")
@Link(name = "ADR-266", url = Adr.EVERY_INVOCATION_NAMES_AND_OPENS_ITS_PROGRESS_PAGE, type = "adr")
@Link(name = "ADR-265", url = Adr.THE_RUN_REWRITES_A_PROGRESS_PAGE_WHILE_IT_RUNS, type = "adr")
class ProgressPageOpensInvocationTest {

    /** The page's name in the working directory. */
    private static final String PAGE = "progress.html";

    /** A tenth of a second, so the page is rewritten many times while the opener is asked once. */
    private static final String INTERVAL = "100ms";

    /** What the first line of a command that writes the page opens with, before the page's absolute path. */
    private static final String PATH_LINE = "Progress page: ";

    /** What the page says while the invocation runs, which its first write says. */
    private static final String RUNNING = "This invocation is running.";

    private static final String ENDED_COMPLETED = "This invocation has ended: completed.";

    /** The one warning an opener that throws leaves, up to the exception's class name. */
    private static final String COULD_NOT_BE_OPENED = "the progress page could not be opened: ";

    /** One request per invocation, whatever the number of writes. */
    private static final int ONCE = 1;

    private static final String BOILERPLATE_FLOOR = "1.0";

    private static final String MODEL_NAME = "qwen3-embedding:0.6b";

    /** The file a scoring run writes for a person to answer. */
    private static final String LABEL_FILE = "relevance-labels.yaml";

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

    @Autowired
    private RecordingPageOpener opener;

    private ListAppender<ILoggingEvent> logged;
    private ch.qos.logback.classic.Logger rootLogger;

    @BeforeEach
    void noPageAndNothingAskedOrLogged() throws IOException {
        Files.deleteIfExists(thePagesPath());
        opener.forget();
        logged = new ListAppender<>();
        logged.start();
        rootLogger = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        rootLogger.addAppender(logged);
    }

    @AfterEach
    void releaseTheLines() {
        rootLogger.detachAppender(logged);
        logged.stop();
        opener.forget();
    }

    @Test
    @Story("The page opens itself once, after it is written")
    @DisplayName("A run asks for its progress page to be opened once, for the page's own address, when the page is already there and says the run is going on")
    void theRunOpensThePageOnceAfterItsFirstWrite(@TempDir Path root, @TempDir Path seeds) throws IOException {
        aCorpus(root, seeds);

        cli.run("run", root.toString());

        claim("the invocation reported success", () -> assertThat(cli.getExitCode()).isZero());
        List<RecordingPageOpener.Request> requests = opener.requests();
        claim(
                "the opener was asked " + ONCE + " time, although the page was written when the run started, every"
                        + " tenth of a second while it ran and once more at its end",
                () -> assertThat(requests).hasSize(ONCE));
        RecordingPageOpener.Request request = requests.getFirst();
        claim(
                "it was asked for the page in the working directory, by its address",
                () -> assertThat(Path.of(request.page())).isEqualTo(thePagesPath()));
        claim(
                "and when it was asked, the page was already there and said the invocation was running: it is opened"
                        + " after its first write, never before",
                () -> {
                    assertThat(request.thePageWasThere()).isTrue();
                    assertThat(request.text()).contains(RUNNING);
                });
    }

    @Test
    @Story("The command's first line names the page")
    @DisplayName("The first line a run logs names the progress page by its full path")
    void theFirstLineTheRunLogsNamesThePagesPath(@TempDir Path root, @TempDir Path seeds) throws IOException {
        aCorpus(root, seeds);

        cli.run("run", root.toString());

        claim("the run logged lines", () -> assertThat(logged.list).isNotEmpty());
        ILoggingEvent first = logged.list.getFirst();
        claim(
                "its first line, before the line naming the root it walks, is \"" + PATH_LINE + "\" and the page's"
                        + " absolute path, with nothing after it, at the level the console shows",
                () -> {
                    assertThat(first.getFormattedMessage()).isEqualTo(PATH_LINE + thePagesPath());
                    assertThat(first.getLevel()).isEqualTo(Level.INFO);
                });
    }

    @Test
    @Story("The command's first line names the page")
    @DisplayName("The label command names the progress page on its first line, and asks for it to be opened once, when its first write is there")
    void theLabelCommandNamesThePageFirstAndOpensItOnceItIsWritten(@TempDir Path root, @TempDir Path seeds)
            throws IOException {
        aCorpus(root, seeds);
        cli.run("run", root.toString());
        answerEveryQuestion();
        Files.deleteIfExists(thePagesPath());
        opener.forget();
        logged.list.clear();

        cli.run("label");

        claim("the label command reported success", () -> assertThat(cli.getExitCode()).isZero());
        claim("the label command logged lines", () -> assertThat(logged.list).isNotEmpty());
        claim(
                "its first line is \"" + PATH_LINE + "\" and the page's absolute path, at the level the console"
                        + " shows",
                () -> {
                    assertThat(logged.list.getFirst().getFormattedMessage()).isEqualTo(PATH_LINE + thePagesPath());
                    assertThat(logged.list.getFirst().getLevel()).isEqualTo(Level.INFO);
                });
        List<RecordingPageOpener.Request> requests = opener.requests();
        claim(
                "the opener was asked " + ONCE + " time, for the page in the working directory",
                () -> {
                    assertThat(requests).hasSize(ONCE);
                    assertThat(Path.of(requests.getFirst().page())).isEqualTo(thePagesPath());
                });
        claim(
                "and when it was asked, the label command's first write was there, saying the invocation was"
                        + " running",
                () -> {
                    assertThat(requests.getFirst().thePageWasThere()).isTrue();
                    assertThat(requests.getFirst().text()).contains(RUNNING);
                });
    }

    @Test
    @Story("A page that cannot be opened fails nothing")
    @DisplayName("When opening the page throws, the run completes, its page ends as usual, and one warning names what was thrown")
    void anOpenerThatThrowsFailsNothingAndIsOneLineOfTheLog(@TempDir Path root, @TempDir Path seeds)
            throws IOException {
        aCorpus(root, seeds);
        opener.throwing(new IllegalStateException("no program here opens a page"));

        cli.run("run", root.toString());

        claim(
                "the invocation reported success, although the page could not be opened",
                () -> assertThat(cli.getExitCode()).isZero());
        claim(
                "the page was written to its end all the same, saying the invocation completed",
                () -> assertThat(thePagesPath())
                        .isRegularFile()
                        .content(StandardCharsets.UTF_8)
                        .contains(ENDED_COMPLETED));
        claim(
                "exactly one warning says the page could not be opened, naming the class of what was thrown and not"
                        + " its message",
                () -> assertThat(logged.list)
                        .filteredOn(event -> event.getLevel() == Level.WARN
                                && event.getFormattedMessage().startsWith(COULD_NOT_BE_OPENED))
                        .extracting(ILoggingEvent::getFormattedMessage)
                        .containsExactly(COULD_NOT_BE_OPENED + IllegalStateException.class.getName()));
        claim(
                "and no line at error says anything of the page",
                () -> assertThat(logged.list)
                        .filteredOn(event -> event.getLevel().isGreaterOrEqual(Level.ERROR))
                        .noneMatch(event -> event.getFormattedMessage().contains("progress page")));
    }

    /** The page's absolute path in the working directory, as the command names it and the opener is handed it. */
    private static Path thePagesPath() {
        return workingDirectory.resolve(PAGE).toAbsolutePath().normalize();
    }

    /** One corpus document and one seed, with every value the run needs, so it goes as far as the label file. */
    private void aCorpus(Path root, Path seeds) throws IOException {
        Files.writeString(root.resolve("corpus.txt"), "a corpus document");
        Files.writeString(seeds.resolve("seed.txt"), "a seed document");
        profileStore.save(ProfileFixture.profile()
                .seedFolder(seeds.toString(), "set by this test")
                .degenerateOutputConfidenceFloor(profileStore.load().degenerateOutputConfidenceFloor())
                .boilerplateDocumentFrequencyFloor(BOILERPLATE_FLOOR, "set by this test, so stage 4's gate is open")
                .embeddingModel(MODEL_NAME, "set by this test, so gate 3 is open")
                .build());
    }

    /** Fills in every blank answer, the way a person working through the file would. */
    private static void answerEveryQuestion() throws IOException {
        Path labels = workingDirectory.resolve(LABEL_FILE);
        Files.writeString(labels, Files.readString(labels).replace("relevant: null", "relevant: true"));
    }
}
