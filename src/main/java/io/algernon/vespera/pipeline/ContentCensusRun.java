package io.algernon.vespera.pipeline;

import io.algernon.vespera.corpus.Walk;
import io.algernon.vespera.extraction.ExtractorIdentity;
import io.algernon.vespera.ledger.ImplementationVersions;
import io.algernon.vespera.ledger.Ledger;
import io.algernon.vespera.ledger.RunId;
import io.algernon.vespera.ledger.WalkId;
import java.nio.file.Path;
import java.util.List;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.infrastructure.item.ExecutionContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * Mints stage 3's run exactly once per step execution — content census, ADR-038/ADR-074's
 * document-frequency pass — the same shape {@link ExtractionRun} already uses for stage 2. Named for
 * what it mints ({@link #STAGE}) rather than for its place in the cascade: Spring Batch's own step
 * order already carries "stage 3."
 *
 * <p>Stage 2's run id is learned rather than worked out: {@link UpstreamRuns} reads the run of stage 2
 * this invocation minted or continued, from {@link InvocationRuns} (ADR-154 §1, amending ADR-099). It
 * replaces re-deriving the id from stage 2's known-fixed inputs, which required stage 1's id in turn
 * and would drift silently if either earlier stage's configuration shape changed here.
 *
 * <p>Because {@code vesperaJob} wires this step after {@code extractionStep} ({@link
 * CensusJobConfiguration}), and Spring Batch's default step transition only proceeds to the next step
 * once the previous one has completed, this step is only ever reached once stage 2's run has fully
 * finished — never over a partial pass a resumed step left behind mid-corpus.
 *
 * <p>Reads whichever walk stage 2 read, resolved the same way {@link ExtractionRun} resolves it from the
 * {@code root} job parameter — nothing here hard-codes the corpus walk.
 */
@Component
@StepScope
class ContentCensusRun {

    /** The stage name this run is minted under. */
    static final String STAGE = "content-census";

    /** The module whose implementation version this stage's runs are versioned against (ADR-074). */
    static final String OWNING_MODULE = "similarity";

    /**
     * {@code extraction}'s own module: named here too because {@code pipeline}'s stage-3 hand-off
     * spec's report half (a separate ticket) reads {@code extraction_metric} — until that ticket
     * lands, naming it here already reflects what "stage 3's implementation" spans (ADR-074, ADR-075).
     */
    static final String EXTRACTION_MODULE = "extraction";

    /**
     * The composing step's own module: a change to the tasklet driving this pass is itself an
     * implementation change to stage 3's output (ADR-058's reading, the same narrowing already applied
     * to stage 2's {@code SIMILARITY_MODULE}).
     */
    static final String PIPELINE_MODULE = "pipeline";

    private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();

    private final RunId runId;
    private final RunId extractionRunId;

    ContentCensusRun(
            Ledger ledger,
            ImplementationVersions implementationVersions,
            ExtractorIdentity extractorIdentity,
            DegenerateOutputConfidenceFloor confidenceFloor,
            @Value("#{jobParameters['root']}") Path root,
            @Value("#{stepExecution.jobExecution.executionContext}") ExecutionContext executionContext) {
        Path canonicalRoot = Walk.canonicalRoot(root);
        WalkId walkId = ledger.finishedWalkFor(canonicalRoot)
                .orElseThrow(() -> new IllegalStateException(
                        "no finished walk is recorded for " + canonicalRoot + "; census must run before stage 3"));
        InvocationRuns invocationRuns = new InvocationRuns(executionContext);
        this.extractionRunId = new UpstreamRuns(invocationRuns).runOf(ExtractionRun.STAGE);
        this.runId = ledger.startRun(
                STAGE,
                implementationVersions.of(OWNING_MODULE, EXTRACTION_MODULE, PIPELINE_MODULE),
                configConsumed(canonicalRoot, this.extractionRunId),
                walkId,
                List.of(this.extractionRunId));
        invocationRuns.record(STAGE, this.runId);
    }

    /**
     * {@code configConsumed} names the walk/root stage 3 ran against and the stage-2 run it read
     * (ADR-048) — never {@code "{}"}, since both are recoverable from the run row itself.
     *
     * <p>Private since ADR-099: nothing outside this class needs it. It was package-visible only while
     * later stages re-derived this run's identity by reproducing this JSON shape, and they now
     * read the run this invocation recorded instead (ADR-154).
     */
    private static String configConsumed(Path canonicalRoot, RunId extractionRunId) {
        return JSON_MAPPER.writeValueAsString(new ConfigConsumed(canonicalRoot.toString(), extractionRunId.value()));
    }

    RunId runId() {
        return runId;
    }

    RunId extractionRunId() {
        return extractionRunId;
    }

    private record ConfigConsumed(String root, String extractionRunId) {}
}
