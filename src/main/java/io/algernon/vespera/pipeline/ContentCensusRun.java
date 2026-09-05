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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * Mints stage 3's run exactly once per step execution — content census, ADR-038/ADR-074's
 * document-frequency pass — the same shape {@link ExtractionRun} already uses for stage 2. Named for
 * what it mints ({@link #STAGE}) rather than for its place in the cascade: Spring Batch's own step
 * order already carries "stage 3."
 *
 * <p>Stage 2's run id is not read off any in-process state. It is recomputed instead, the same way
 * {@link ExtractionRun} recomputes stage 1's: a run's id is wholly determined by its four inputs
 * (ADR-048), stage 2's are every one of them fixed and known here (its owning modules'
 * implementation versions, {@link ExtractionRun#configConsumed}, this walk, stage 1's re-derived run as
 * upstream), so re-deriving the same {@link RunId#of} stage 2 minted its row under is exact, not a
 * guess.
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
            @Value("#{jobParameters['root']}") Path root) {
        Path canonicalRoot = Walk.canonicalRoot(root);
        WalkId walkId = ledger.finishedWalkFor(canonicalRoot)
                .orElseThrow(() -> new IllegalStateException(
                        "no finished walk is recorded for " + canonicalRoot + "; census must run before stage 3"));
        RunId byteLevelReductionRunId = RunId.of(
                implementationVersions.of(ByteLevelReductionTasklet.OWNING_MODULE), ByteLevelReductionTasklet.CONFIG_CONSUMED, walkId, List.of());
        this.extractionRunId = RunId.of(
                implementationVersions.of(ExtractionRun.OWNING_MODULE, ExtractionRun.SIMILARITY_MODULE),
                ExtractionRun.configConsumed(extractorIdentity, confidenceFloor),
                walkId,
                List.of(byteLevelReductionRunId));
        this.runId = ledger.startRun(
                STAGE,
                implementationVersions.of(OWNING_MODULE, EXTRACTION_MODULE, PIPELINE_MODULE),
                configConsumed(canonicalRoot, this.extractionRunId),
                walkId,
                List.of(this.extractionRunId));
    }

    /**
     * {@code configConsumed} names the walk/root stage 3 ran against and the stage-2 run it read
     * (ADR-048) — never {@code "{}"}, since both are recoverable from the run row itself.
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
