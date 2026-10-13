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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * The survivor list over whole invocations (ADR-249): of three files, one of which the converter answers
 * with an error status, stage 2 lists the other two by path on {@code extraction-survivors.html} and the one
 * on the review list, says how many came through and where their list is, and an invocation that finds the
 * stage finished writes the page again, converting nothing.
 *
 * <p>A class of its own, with a working directory of its own, because the page it reads would otherwise be
 * the one another class's invocation left. The converter is a {@link LoopbackSidecar}, one for each test.
 */
@CascadeSliceTest
@Import(ExtractionBeans.class)
@Epic("Extraction")
@Feature("Stage 2 step")
@Link(name = "ADR-249", url = Adr.STAGE_2_WRITES_THE_SURVIVOR_LIST_BESIDE_THE_REVIEW_LIST, type = "adr")
@Link(name = "ADR-175", url = Adr.A_FILE_THAT_FAILS_IS_MARKED_AND_SKIPPED, type = "adr")
class SurvivorListInvocationTest {

    /** A loopback port that was free when the class loaded; the test's sidecar listens on it. */
    private static final int SIDECAR_PORT = LoopbackSidecar.aFreePort();

    /** The corpus: few enough to be one chunk. */
    private static final int OCCURRENCES = 3;

    /** The one the converter answers with an error status. */
    private static final int THE_REJECTED = 2;

    /** How many the converter converts, and stage 2 keeps: the other two. */
    private static final int CAME_THROUGH = OCCURRENCES - 1;

    /** The status the converter answers the rejected one with. */
    private static final int NOT_FOUND = 404;

    private static final String THE_FIRST = "document-01.txt";

    private static final String THE_SECOND = "document-02.txt";

    private static final String THE_THIRD = "document-03.txt";

    /** The page stage 2 lists the files it converted and kept on, beside the database. */
    private static final String THE_SURVIVOR_LIST = "extraction-survivors.html";

    /** The page stage 2 lists the files it could not read on. */
    private static final String THE_REVIEW_LIST = "extraction-failures.html";

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
    @Story("The list of files extraction read and kept")
    @DisplayName("Extraction lists the files it converted and kept by path, leaves the one it could not read to the other list, says how many came through, and writes the list again when run once more")
    void theSurvivorListNamesWhatCameThroughAndIsWrittenAgain(@TempDir Path root) throws IOException {
        for (int occurrence = 1; occurrence <= OCCURRENCES; occurrence++) {
            Files.writeString(
                    root.resolve("document-%02d.txt".formatted(occurrence)),
                    "corpus document %02d of %d, for the list of what came through".formatted(occurrence, OCCURRENCES));
        }
        sidecar.rejecting(THE_REJECTED, NOT_FOUND);
        Path page = workingDirectory.resolve(THE_SURVIVOR_LIST);

        cli.run("run", root.toString());

        claim("the invocation reported success", () -> assertThat(cli.getExitCode()).isZero());
        claim(
                "the page states the " + CAME_THROUGH + " files the converter converted, and names them in the"
                        + " order of their paths",
                () -> assertThat(page)
                        .isRegularFile()
                        .content(StandardCharsets.UTF_8)
                        .contains("Files that came through: " + CAME_THROUGH + ".")
                        .containsSubsequence(THE_FIRST, THE_THIRD));
        claim(
                "the file the converter rejected is not on it, and is on the list of files that could not be"
                        + " read, which names neither of the other two: the two lists share no file",
                () -> {
                    assertThat(page).content(StandardCharsets.UTF_8).doesNotContain(THE_SECOND);
                    assertThat(workingDirectory.resolve(THE_REVIEW_LIST))
                            .content(StandardCharsets.UTF_8)
                            .contains(THE_SECOND)
                            .doesNotContain(THE_FIRST, THE_THIRD);
                });
        claim(
                "one line says that " + CAME_THROUGH + " files came through and where their list is, and no line"
                        + " is an error about it",
                () -> {
                    assertThat(lines())
                            .containsOnlyOnce("Stage 2 (extraction): " + CAME_THROUGH + " file(s) came through;"
                                    + " their list is " + page.toAbsolutePath());
                    assertThat(logged.list)
                            .filteredOn(event -> event.getLevel().isGreaterOrEqual(Level.ERROR))
                            .noneMatch(event -> event.getFormattedMessage().contains("their list could not be written"));
                });

        Files.delete(page);
        long conversionsSoFar = conversions();
        cli.run("run", root.toString());

        claim(
                "a second invocation, which finds extraction finished and asks the converter about no file,"
                        + " writes the page again with the same " + CAME_THROUGH + " files",
                () -> {
                    assertThat(conversions()).isEqualTo(conversionsSoFar);
                    assertThat(page)
                            .isRegularFile()
                            .content(StandardCharsets.UTF_8)
                            .contains("Files that came through: " + CAME_THROUGH + ".")
                            .containsSubsequence(THE_FIRST, THE_THIRD)
                            .doesNotContain(THE_SECOND);
                });
    }

    /** How many calls about a numbered file the sidecar has answered. */
    private long conversions() {
        return sidecar.callsPerDocument().entrySet().stream()
                .filter(calls -> calls.getKey() > 0)
                .mapToLong(calls -> calls.getValue())
                .sum();
    }

    private List<String> lines() {
        return logged.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
    }
}
