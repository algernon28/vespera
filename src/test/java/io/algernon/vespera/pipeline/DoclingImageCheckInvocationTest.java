package io.algernon.vespera.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.algernon.vespera.Adr;
import io.algernon.vespera.corpus.Walk;
import io.algernon.vespera.extraction.ConversionStatus;
import io.algernon.vespera.extraction.DoclingClient;
import io.algernon.vespera.extraction.DoclingResponse;
import io.algernon.vespera.extraction.ScriptedExtractor;
import io.algernon.vespera.extraction.SidecarVersionReport;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Stage 2 checks that the document converter runs the image {@code vespera.docling.image} names before
 * it records a single conversion under that name, and stops when it does not (ADR-179 §3).
 *
 * <p>Every conversion is recorded under an identity that carries the configured image, because two
 * images that report the same versions were measured to convert differently (ADR-147, ADR-170). On
 * 2026-09-30 the converter was swapped for the CPU build while the configuration still named the GPU
 * build, and 1,398 conversions were recorded under the wrong name, with nothing to notice it. The image
 * now reports its own name in {@code /version}, as {@code vespera-image}, and composing the identity
 * compares it with the configured one first.
 *
 * <p>The converter here is a stub {@link DoclingClient} whose {@code /version} report each test sets
 * through {@link #REPORTED}. That is static, because the stub is a bean built by Spring and the test has
 * no other way to reach into it before the invocation asks; it is reset in {@link #captureEveryLine} so
 * that no test inherits another's report, whatever order they run in. The extractor is a {@link
 * ScriptedExtractor} answering every conversion, so the matching case can finish stage 2 and the two
 * stopping cases can show it was never asked.
 *
 * <p>{@code @DirtiesContext} per method, because the extractor identity is a lazily built singleton:
 * once one test had composed it, a later one would never compare the images again.
 */
@CascadeSliceTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@Import(DoclingImageCheckInvocationTest.ReportedImageBeans.class)
@Epic("Extraction")
@Feature("The Docling sidecar")
@Issue("373")
@Link(name = "ADR-179", url = Adr.NO_ENTRY_POINT_STARTS_THE_SIDECARS, type = "adr")
class DoclingImageCheckInvocationTest {

    /**
     * What the converter answers {@code /version} with in the test running now. Set to a report naming
     * the configured image before each test, which a test then replaces to name another or none.
     */
    static final AtomicReference<Map<String, String>> REPORTED = new AtomicReference<>();

    /** The GPU build's name: the image that was running on 2026-09-30 while the configuration named the CPU one. */
    private static final String THE_GPU_IMAGE =
            "vespera/docling-serve-cu128-libreoffice:v1.32.0-docling-parse-7.17.0-r2";

    /** How stage 2's closing line opens when the stage did not complete. */
    private static final String STAGE_2_FAILED = "Stage 2 (extraction) failed";

    /** How stage 2's closing line opens when it completed. */
    private static final String STAGE_2_FINISHED = "Stage 2 (extraction) finished";

    /** The stages recorded ahead of stage 2: census mints no run, and stage 1 asks nothing of the converter. */
    private static final List<String> BEFORE_STAGE_2 = List.of(StageModules.BYTE_LEVEL_REDUCTION.stage());

    @TempDir
    static Path workingDirectory;

    @DynamicPropertySource
    static void workingDirectory(DynamicPropertyRegistry registry) {
        registry.add("vespera.working-dir", workingDirectory::toString);
    }

    @Autowired
    private VesperaCli cli;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ScriptedExtractor extractor;

    @Value("${vespera.docling.image}")
    private String configuredImage;

    private ListAppender<ILoggingEvent> logged;
    private ch.qos.logback.classic.Logger rootLogger;

    /**
     * The converter starts each test reporting the image the configuration names, and every line is
     * captured on the root logger, where stage 2's closing line arrives.
     */
    @BeforeEach
    void captureEveryLine() {
        REPORTED.set(SidecarVersionReport.runningImage(configuredImage));
        logged = new ListAppender<>();
        logged.start();
        rootLogger = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        rootLogger.addAppender(logged);
    }

    @AfterEach
    void releaseEveryLine() {
        rootLogger.detachAppender(logged);
        logged.stop();
        REPORTED.set(null);
    }

    @Test
    @Story("A converter running another image stops extraction before anything is recorded")
    @DisplayName("When the document converter runs another image than the one configured, extraction stops, says both names, and records nothing")
    void aSidecarRunningAnotherImageStopsExtraction(@TempDir Path root) throws IOException {
        Files.writeString(root.resolve("document.txt"), "content of the one document");
        REPORTED.set(SidecarVersionReport.runningImage(THE_GPU_IMAGE));

        cli.run("run", root.toString());

        claim(
                "the invocation failed, because stage 2 did: the converter runs " + THE_GPU_IMAGE + ", and"
                        + " every conversion would have been recorded under the configured image instead",
                () -> assertThat(cli.getExitCode()).isNotZero());
        claim(
                "a line says stage 2 failed and names both images, the one running and the one configured, so"
                        + " the operator can tell which of the two is the mistake",
                () -> assertThat(lines())
                        .anyMatch(line -> line.startsWith(STAGE_2_FAILED)
                                && line.contains(THE_GPU_IMAGE)
                                && line.contains(configuredImage)));
        claim(
                "no extraction is recorded over this corpus, only the byte-level reduction in front of it",
                () -> assertThat(stagesRecordedOver(root)).containsExactlyElementsOf(BEFORE_STAGE_2));
        claim(
                "and nothing was converted, so no conversion sits in the cache under a name that is not its own",
                () -> assertThat(extractor.conversions()).isZero());
    }

    @Test
    @Story("A converter running another image stops extraction before anything is recorded")
    @DisplayName("When the document converter does not say which image it runs, extraction stops, names the image configured, and records nothing")
    void aSidecarThatDoesNotReportItsImageStopsExtraction(@TempDir Path root) throws IOException {
        Files.writeString(root.resolve("document.txt"), "content of the one document");
        REPORTED.set(SidecarVersionReport.namingNoImage());

        cli.run("run", root.toString());

        claim(
                "the invocation failed, because stage 2 did: a converter that does not say which image it runs"
                        + " was built before it could, or is not Vespera's image at all",
                () -> assertThat(cli.getExitCode()).isNotZero());
        claim(
                "a line says stage 2 failed and names the image configured",
                () -> assertThat(lines())
                        .anyMatch(line -> line.startsWith(STAGE_2_FAILED) && line.contains(configuredImage)));
        claim(
                "no extraction is recorded over this corpus, only the byte-level reduction in front of it",
                () -> assertThat(stagesRecordedOver(root)).containsExactlyElementsOf(BEFORE_STAGE_2));
        claim(
                "and nothing was converted",
                () -> assertThat(extractor.conversions()).isZero());
    }

    /** Passes before the check exists too: it is here so that a check refusing every converter fails. */
    @Test
    @Story("A converter running another image stops extraction before anything is recorded")
    @DisplayName("When the document converter runs the image configured, extraction goes ahead as before")
    void aSidecarRunningTheConfiguredImageIsExtractedFrom(@TempDir Path root) throws IOException {
        Files.writeString(root.resolve("document.txt"), "content of the one document");

        cli.run("run", root.toString());

        claim(
                "a line says stage 2 finished",
                () -> assertThat(lines()).anyMatch(line -> line.startsWith(STAGE_2_FINISHED)));
        claim(
                "no line says it failed",
                () -> assertThat(lines()).noneMatch(line -> line.startsWith(STAGE_2_FAILED)));
        claim(
                "an extraction is recorded over this corpus",
                () -> assertThat(stagesRecordedOver(root)).contains(StageModules.EXTRACTION.stage()));
        claim(
                "and the document was converted",
                () -> assertThat(extractor.conversions()).isPositive());
    }

    private List<String> lines() {
        return logged.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
    }

    private List<String> stagesRecordedOver(Path root) {
        return jdbcTemplate.queryForList(
                "SELECT r.stage FROM run r JOIN walk w ON w.id = r.walk_id WHERE w.root = ? ORDER BY r.rowid",
                String.class,
                Walk.canonicalRoot(root).toString());
    }

    /**
     * A converter that passes its health check and answers {@code /version} with {@link #REPORTED}, and
     * an extractor that answers every conversion with real text. {@code @TestConfiguration}, for the
     * reason {@link StubbedExtractionBeans} gives.
     */
    @TestConfiguration
    static class ReportedImageBeans {

        @Bean
        DoclingClient doclingClient() {
            return new DoclingClient("unused") {
                @Override
                public void checkHealth() {}

                @Override
                public Map<String, String> version() {
                    return REPORTED.get();
                }
            };
        }

        @Bean
        ScriptedExtractor doclingExtractor() {
            return new ScriptedExtractor()
                    .thenAlwaysAnswering(new DoclingResponse(
                            ConversionStatus.SUCCESS,
                            List.of(),
                            0d,
                            null,
                            "{\"document\":{\"json_content\":{\"texts\":[{\"text\":\"stubbed but real content\"}]}}}"));
        }
    }
}
