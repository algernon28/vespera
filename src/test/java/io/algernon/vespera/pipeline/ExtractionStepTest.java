package io.algernon.vespera.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.corpus.Walk;
import io.algernon.vespera.extraction.ConversionStatus;
import io.algernon.vespera.extraction.DoclingError;
import io.algernon.vespera.extraction.DoclingExtractor;
import io.algernon.vespera.extraction.DoclingResponse;
import io.algernon.vespera.extraction.FailureCategory;
import io.algernon.vespera.extraction.ScriptedExtractor;
import io.algernon.vespera.ledger.Ledger;
import io.algernon.vespera.ledger.OccurrenceId;
import io.algernon.vespera.ledger.OccurrencePath;
import io.algernon.vespera.ledger.WalkId;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * The assembled stage-2 step (ADR-071), driven through the real job rather than through its parts in
 * isolation. {@link ExtractionItemProcessorTest} pins the per-occurrence judgement,
 * {@link ExtractionCircuitBreakerTest} pins the streak counter on its own, and {@link ExtractionRunTest} pins
 * what the run records — none of them drives {@link ExtractionJobConfiguration}'s own {@code extractionStep}
 * bean, so nothing claims the reader, processor, writer and listeners are actually wired to each
 * other rather than merely each individually correct. This class is that claim.
 *
 * <p>{@code @DirtiesContext} per method: each test scripts {@link ScriptedExtractor}'s queue before
 * running the job, and the bean it comes from ({@link StubbedExtractionBeans}) is a class-scoped
 * singleton — a context shared across methods would let one test's leftover queue answer another
 * test's conversions.
 *
 * <p><b>One seam is deliberately not covered here: that the circuit breaker's exception actually
 * fails the step.</b> A test of it has to drive the job to failure, and Spring Batch reports a failed
 * step by logging the whole cause chain at {@code ERROR} — there is no quiet way to fail a step. That
 * left a passing build printing a stack trace, which teaches a reader to skim past stack traces, and
 * the real one then goes past with them. The rule itself is pinned where it can be caught and
 * asserted instead: {@link ExtractionCircuitBreakerTest} holds the breaker to throwing after the
 * streak. What nothing now catches is a future {@code .skip(ExtractorStoppedAnsweringException.class)}
 * on this step, which would make a dead sidecar produce a successful run — worth remembering when
 * editing {@link ExtractionJobConfiguration}'s fault tolerance.
 *
 * <p><b>One claim here exists because no unit can make it</b> (ADR-140): that the assembled step really
 * converts several documents at once, and really does so on threads other than the one it was invoked
 * on. {@link ExtractionConcurrencyTest} pins the rule those conversions' outcomes are read by and the
 * width the code fixes; neither says anything about how the step is wired, and the wiring is where the
 * width is either honoured or quietly absent. The second half of that claim — that the converting
 * threads are not the invoking one — is what catches the width being wired out, not a step-level task
 * executor: under one, Spring Batch still reads on the step thread and hands only the processing to
 * the executor, so the reader still dispatches to its own workers and both halves pass unchanged. The
 * data race ADR-140 section 3 refuses is caught by the other claim here, made through {@link
 * DrainThreadProbe}: every metric stage 2 writes is written from the one thread the command was
 * invoked on. A step-level executor moves the processor, the streak counters and the fault recorder's
 * list onto its threads; wired in on this profile, measured, the step did not finish within 400
 * seconds, which is why that test carries a timeout on a thread of its own — a pin that fails by
 * hanging is a build that never reports, and with the timeout it failed at two minutes. Where the step
 * does finish, the one-thread claim is what fails.
 */
@CascadeSliceTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@Import({DrainThreadProbe.class, StubbedExtractionBeans.class})
@Epic("Extraction")
@Feature("Stage 2 step")
@Issue("47")
@Link(name = "ADR-071", url = Adr.DOCLING_INVOCATION_CONTRACT_IS_ONE_SYNC_CALL, type = "adr")
class ExtractionStepTest {

    /** Documents walked in the "every survivor gets processed" corpus. */
    private static final int SURVIVORS = 3;

    /**
     * How many identical failing answers to queue for a corpus smaller than the chunk size: more than
     * the corpus holds, so that Spring Batch's fault-tolerant chunk-scan recovery — which can
     * reprocess an already-succeeded item in the same chunk while isolating the one that skips — never
     * runs the script dry and never falls through to the stub's default success answer.
     */
    private static final int GENEROUS_ANSWER_COUNT = 20;

    /**
     * Enough documents that a wave of the full width assembles inside one chunk and more work follows
     * it, so the peak measured below is a width the step sustained rather than one it happened to reach
     * on its way to running out of documents.
     */
    private static final int DOCUMENTS_FOR_MORE_THAN_ONE_WAVE = 24;

    /**
     * How long one scripted conversion waits for the rest of its wave before answering anyway. It
     * bounds a dispatch delay and nothing else -- a scripted conversion computes nothing -- so it is
     * generous, and a step that converts one document at a time pays it once for the whole run rather
     * than once per document.
     */
    private static final Duration UNTIL_A_WHOLE_WAVE_IS_CONVERTING = Duration.ofSeconds(5);

    @BeforeEach
    void forgetWhatTheProbeSaw() {
        DrainThreadProbe.forget();
    }

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
    @Story("How many conversions run at once")
    @Issue("264")
    @Link(name = "ADR-140", url = Adr.STAGE_2_CONVERTS_EIGHT_AT_A_TIME, type = "adr")
    @DisplayName("The step converts several documents at once, on threads other than the one it runs on")
    void convertsAtTheWidthTheCodeFixesAndOffTheInvokingThread(@TempDir Path root) throws IOException {
        for (int i = 0; i < DOCUMENTS_FOR_MORE_THAN_ONE_WAVE; i++) {
            Files.writeString(root.resolve("document-" + i + ".txt"), "content " + i);
        }
        scripted()
                .holdingEachConversionUntil(
                        ExtractionJobConfiguration.CONVERSION_CONCURRENCY, UNTIL_A_WHOLE_WAVE_IS_CONVERTING);
        String invokedOn = Thread.currentThread().getName();

        cli.run("run", root.toString());

        claim("the command reported success", () -> assertThat(cli.getExitCode()).isZero());
        claim(
                "as many documents were being converted at the same moment as the code says a machine converts"
                        + " at once -- measured by holding each conversion until that many were under way, so a"
                        + " step converting one document at a time reports one here however many documents it"
                        + " was handed",
                () -> assertThat(scripted().mostEverConvertingAtOnce())
                        .isEqualTo(ExtractionJobConfiguration.CONVERSION_CONCURRENCY));
        claim(
                "and the chunk is a whole number of those waves -- the chunk loop is the read-ahead, so a chunk pays"
                        + " one tick per wave, and a chunk of ten at a width of eight paid a second tick for two"
                        + " documents",
                () -> assertThat(ExtractionJobConfiguration.CHUNK_SIZE % ExtractionJobConfiguration.CONVERSION_CONCURRENCY)
                        .isZero());
        claim(
                "none of those conversions ran on the thread the command itself was invoked on -- that thread is"
                        + " where the step reads, writes and counts, and a conversion sharing it would mean the"
                        + " counting had been spread across threads too, which is how a run that should stop"
                        + " quietly carries on",
                () -> assertThat(scripted().convertingThreads()).isNotEmpty().doesNotContain(invokedOn));
    }

    @Test
    @Story("How many conversions run at once")
    @Issue("264")
    @Link(name = "ADR-140", url = Adr.STAGE_2_CONVERTS_EIGHT_AT_A_TIME, type = "adr")
    @DisplayName("Every metric row the step writes is written from the one thread it was invoked on")
    @Timeout(value = 2, unit = TimeUnit.MINUTES, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    void theDrainIsOneThreadAndItIsTheInvokingOne(@TempDir Path root) throws IOException {
        for (int i = 0; i < DOCUMENTS_FOR_MORE_THAN_ONE_WAVE; i++) {
            Files.writeString(root.resolve("document-" + i + ".txt"), "content " + i);
        }
        String invokedOn = Thread.currentThread().getName();

        cli.run("run", root.toString());

        claim("the command reported success", () -> assertThat(cli.getExitCode()).isZero());
        claim(
                "every metric row stage 2 wrote was written from exactly one thread, and that thread is the one the"
                        + " command was invoked on -- the drain. Conversions run on other threads; reading, writing and"
                        + " counting do not, and that is what keeps five failures in a row meaning five in a row. A"
                        + " step that processed on a pool of threads would write from several here, which is the"
                        + " data race that lets a run which should stop carry on",
                () -> assertThat(DrainThreadProbe.THREADS_THAT_WROTE_A_METRIC).containsExactly(invokedOn));
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
