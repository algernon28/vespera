package io.algernon.vespera.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.corpus.AnomalyLog;
import io.algernon.vespera.corpus.ContentIdentity;
import io.algernon.vespera.corpus.Walk;
import io.algernon.vespera.corpus.WalkRecorder;
import io.algernon.vespera.extraction.ConversionStatus;
import io.algernon.vespera.extraction.DoclingError;
import io.algernon.vespera.extraction.DoclingExtractor;
import io.algernon.vespera.extraction.DoclingResponse;
import io.algernon.vespera.extraction.FailureCategory;
import io.algernon.vespera.extraction.ScriptedExtractor;
import io.algernon.vespera.ledger.ImplementationVersions;
import io.algernon.vespera.ledger.Ledger;
import io.algernon.vespera.ledger.OccurrenceId;
import io.algernon.vespera.ledger.OccurrencePath;
import io.algernon.vespera.ledger.WalkId;
import io.algernon.vespera.profile.ProfileStore;
import io.algernon.vespera.similarity.Shingler;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.batch.autoconfigure.BatchAutoConfiguration;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * The assembled stage-2 step (ADR-071), driven through the real job rather than through its parts in
 * isolation. {@link Stage2ItemProcessorTest} pins the per-occurrence judgement,
 * {@link Stage2CircuitBreakerTest} pins the streak counter on its own, and {@link Stage2RunTest} pins
 * what the run records — none of them drives {@link Stage2JobConfiguration}'s own {@code stage2Step}
 * bean, so nothing claims the reader, processor, writer and listeners are actually wired to each
 * other rather than merely each individually correct. This class is that claim.
 *
 * <p>{@code @DirtiesContext} per method: each test scripts {@link ScriptedExtractor}'s queue before
 * running the job, and the bean it comes from ({@link StubbedExtractionBeans}) is a class-scoped
 * singleton — a context shared across methods would let one test's leftover queue answer another
 * test's conversions.
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@ImportAutoConfiguration(BatchAutoConfiguration.class)
@Import({
    CensusJobConfiguration.class,
    CensusTasklet.class,
    Stage1JobConfiguration.class,
    Stage1Tasklet.class,
    Stage2JobConfiguration.class,
    Stage2ItemProcessor.class,
    Stage2ItemWriter.class,
    Stage2Run.class,
    Stage2TimeoutStreak.class,
    Stage2CircuitBreaker.class,
    Stage2HealthCheckListener.class,
    Shingler.class,
    StubbedExtractionBeans.class,
    ContentIdentity.class,
    WalkRecorder.class,
    AnomalyLog.class,
    Ledger.class,
    ImplementationVersions.class,
    ProfileStore.class,
    VesperaCommand.class,
    VesperaCommand.Run.class,
    VesperaCommand.Publish.class,
    VesperaCli.class
})
@Epic("Extraction")
@Feature("Stage 2 step")
@Issue("47")
@Link(name = "ADR-071", url = Adr.DOCLING_INVOCATION_CONTRACT_IS_ONE_SYNC_CALL, type = "adr")
class Stage2StepTest {

    /** Documents walked in the "every survivor gets processed" corpus. */
    private static final int SURVIVORS = 3;

    /**
     * How many identical failing answers to queue for a corpus smaller than the chunk size: more than
     * the corpus holds, so that Spring Batch's fault-tolerant chunk-scan recovery — which can
     * reprocess an already-succeeded item in the same chunk while isolating the one that skips — never
     * runs the script dry and never falls through to the stub's default success answer.
     */
    private static final int GENEROUS_ANSWER_COUNT = 20;

    @TempDir
    static Path workingDirectory;

    @DynamicPropertySource
    static void workingDirectory(DynamicPropertyRegistry registry) {
        registry.add("vespera.working-dir", workingDirectory::toString);
    }

    @Autowired
    private VesperaCli cli;

    @Autowired
    private Ledger ledger;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DoclingExtractor doclingExtractor;

    @Test
    @Story("The assembled step reaches every survivor")
    @DisplayName("The processor is invoked exactly once per survivor the step is handed")
    void invokesTheProcessorOncePerSurvivor(@TempDir Path root) throws IOException {
        for (int i = 0; i < SURVIVORS; i++) {
            Files.writeString(root.resolve("document-" + i + ".txt"), "content " + i);
        }

        cli.run("run", root.toString());

        claim("the command reported success", () -> assertThat(cli.getExitCode()).isZero());
        claim(
                "the scripted converter was asked about every survivor stage 1 handed the step, exactly"
                        + " once each -- not zero, which is what a step that never reached the converter would"
                        + " also report as success",
                () -> assertThat(scripted().conversions()).isEqualTo(SURVIVORS));
    }

    @Test
    @Story("A document-scoped failure is recorded")
    @DisplayName("A document the converter blames on itself earns an extraction-failed verdict in the ledger")
    void anExtractionFailedOutcomeReachesTheLedger(@TempDir Path root) throws IOException {
        Files.writeString(root.resolve("broken.txt"), "content");
        scripted()
                .answering(
                        GENEROUS_ANSWER_COUNT,
                        new DoclingResponse(
                                ConversionStatus.FAILURE,
                                List.of(new DoclingError(
                                        "document_backend",
                                        "docling",
                                        "could not read it",
                                        FailureCategory.BACKEND_FAILURE,
                                        null)),
                                0d,
                                null,
                                "{}"));

        cli.run("run", root.toString());

        claim(
                "the job still completes -- one document blamed on itself is a verdict, not a reason to"
                        + " abort the run",
                () -> assertThat(cli.getExitCode()).isZero());
        claim(
                "the document earned an extraction-failed verdict in the ledger, under the run that judged it",
                () -> assertThat(verdictKindsFor(occurrenceOf(root, "broken.txt"))).containsExactly("EXTRACTION_FAILED"));
    }

    @Test
    @Story("A dead sidecar stops the run")
    @DisplayName("A consecutive run of service-scope failures fails the step rather than completing it")
    void aDeadSidecarFailsTheStepRatherThanCompletingIt(@TempDir Path root) throws IOException {
        int deadSidecar = Stage2CircuitBreaker.CONSECUTIVE_SERVICE_SCOPE_FAILURE_COUNT;
        for (int i = 0; i < deadSidecar; i++) {
            Files.writeString(root.resolve("document-" + i + ".txt"), "content " + i);
        }
        scripted()
                .answering(
                        GENEROUS_ANSWER_COUNT,
                        new DoclingResponse(
                                ConversionStatus.FAILURE,
                                List.of(new DoclingError(
                                        "task", "docling", "no capacity", FailureCategory.CAPACITY, null)),
                                0d,
                                null,
                                "{}"));

        cli.run("run", root.toString());

        claim(
                "the run does not report success having examined nothing usable: a converter that sets"
                        + " every document aside in a row is read as broken, not the documents as unlucky"
                        + " (ADR-071)",
                () -> assertThat(cli.getExitCode()).isNotZero());
    }

    private ScriptedExtractor scripted() {
        return (ScriptedExtractor) doclingExtractor;
    }

    private OccurrenceId occurrenceOf(Path root, String fileName) {
        WalkId walkId = ledger.finishedWalkFor(Walk.canonicalRoot(root)).orElseThrow();
        return ledger.occurrenceId(walkId, new OccurrencePath(fileName)).orElseThrow();
    }

    private List<String> verdictKindsFor(OccurrenceId occurrenceId) {
        return jdbcTemplate.query(
                "SELECT kind FROM verdict WHERE occurrence_id = ?",
                (resultSet, rowNumber) -> resultSet.getString("kind"),
                occurrenceId.value());
    }
}
