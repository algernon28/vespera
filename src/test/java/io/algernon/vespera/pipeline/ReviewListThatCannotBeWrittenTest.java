package io.algernon.vespera.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.algernon.vespera.Adr;
import io.algernon.vespera.extraction.ExtractionBeans;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * What stage 2 does when the list of files it could not read cannot be written (ADR-175 section 7,
 * #392): it says so in one line and stops nothing. The removals are in the ledger either way, and a
 * failure thrown from the end of a step would only be logged with its stack trace, and would skip
 * whatever runs at the end of the step after it.
 *
 * <p>A class of its own because it needs a working directory in which the page's name is taken by a
 * folder, which would break every other test sharing that working directory. The converter is a
 * {@link LoopbackSidecar} that answers one document with an error status, so there is one file to
 * count.
 */
@CascadeSliceTest
@Import(ExtractionBeans.class)
@Epic("Extraction")
@Feature("Stage 2 step")
@Issue("392")
@Link(name = "ADR-175", url = Adr.A_FILE_THAT_FAILS_IS_MARKED_AND_SKIPPED, type = "adr")
class ReviewListThatCannotBeWrittenTest {

    /** A loopback port that was free when the class loaded; the test's sidecar listens on it. */
    private static final int SIDECAR_PORT = LoopbackSidecar.aFreePort();

    /** The corpus: few enough to be one batch. */
    private static final int DOCUMENTS = 3;

    /** The one document the converter answers with an error status. */
    private static final int THE_REJECTED_DOCUMENT = 2;

    /** How many files extraction could not read. */
    private static final int ONE = 1;

    /** The status the converter answers that document with. */
    private static final int NOT_FOUND = 404;

    /** The page stage 2 lists the files it could not read on, beside the database. */
    private static final String THE_REVIEW_LIST = "extraction-failures.html";

    /** Where the job framework logs from, and so where a failure thrown at the end of a step is reported. */
    private static final String THE_JOBS_OWN_LOGGERS = "org.springframework.batch";

    /** How stage 2's closing line opens when the step completed. */
    private static final String STAGE_2_FINISHED = "Stage 2 (extraction) finished";

    @TempDir
    static Path workingDirectory;

    @DynamicPropertySource
    static void sidecarAndWorkingDirectory(DynamicPropertyRegistry registry) {
        registry.add("vespera.working-dir", workingDirectory::toString);
        registry.add("vespera.docling.base-url", () -> "http://127.0.0.1:" + SIDECAR_PORT);
    }

    @Autowired
    private VesperaCli cli;

    @Value("${vespera.docling.image}")
    private String configuredImage;

    private ListAppender<ILoggingEvent> logged;
    private ch.qos.logback.classic.Logger rootLogger;
    private LoopbackSidecar sidecar;

    @BeforeEach
    void startTheSidecarAndCaptureEveryLine() throws IOException {
        sidecar = LoopbackSidecar.startedOn(SIDECAR_PORT, configuredImage);
        logged = new ListAppender<>();
        logged.start();
        rootLogger = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        rootLogger.addAppender(logged);
    }

    @AfterEach
    void stopTheSidecarAndReleaseEveryLine() {
        rootLogger.detachAppender(logged);
        logged.stop();
        sidecar.close();
    }

    @Test
    @Story("The list of files extraction could not read")
    @DisplayName("When the page listing the files extraction could not read cannot be written, one line says how many there are and where the page should have gone, and extraction still completes")
    void aPageThatCannotBeWrittenIsSaidInOneLineAndStopsNothing(@TempDir Path root) throws IOException {
        for (int document = 1; document <= DOCUMENTS; document++) {
            Files.writeString(
                    root.resolve("document-%02d.txt".formatted(document)),
                    "corpus document %02d of %d, for the page that cannot be written".formatted(document, DOCUMENTS));
        }
        sidecar.rejecting(THE_REJECTED_DOCUMENT, NOT_FOUND);
        Path page = Files.createDirectory(workingDirectory.resolve(THE_REVIEW_LIST));

        cli.run("run", root.toString());

        List<ILoggingEvent> aboutThePage = logged.list.stream()
                .filter(event -> event.getFormattedMessage().contains("the list of them could not be written"))
                .toList();
        claim(
                "one line, written as an error, says that " + ONE + " file could not be read and that the"
                        + " list of them could not be written to the page's place beside the database",
                () -> assertThat(aboutThePage).singleElement().satisfies(event -> {
                    assertThat(event.getLevel()).isEqualTo(Level.ERROR);
                    assertThat(event.getFormattedMessage())
                            .startsWith("Stage 2 (extraction): " + ONE + " file(s) could not be read, and the"
                                    + " list of them could not be written to " + page.toAbsolutePath() + ": ");
                }));
        claim(
                "that line carries no stack trace: the failure was said, not thrown",
                () -> assertThat(aboutThePage).singleElement().satisfies(event -> assertThat(event.getThrowableProxy())
                        .isNull()));
        claim(
                "the job itself reported no error: nothing was thrown from the end of the step for it to"
                        + " report on top of that line",
                () -> assertThat(logged.list)
                        .filteredOn(event -> event.getLevel().isGreaterOrEqual(Level.ERROR))
                        .noneMatch(event -> event.getLoggerName().startsWith(THE_JOBS_OWN_LOGGERS)));
        claim(
                "nothing claims the page was written",
                () -> assertThat(lines()).noneMatch(line -> line.contains("they are listed in")));
        claim(
                "extraction completed and the invocation succeeded: a page that cannot be written stops"
                        + " nothing",
                () -> {
                    assertThat(lines()).anyMatch(line -> line.startsWith(STAGE_2_FINISHED));
                    assertThat(cli.getExitCode()).isZero();
                });
    }

    private List<String> lines() {
        return logged.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
    }
}
