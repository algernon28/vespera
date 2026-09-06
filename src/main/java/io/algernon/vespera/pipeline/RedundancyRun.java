package io.algernon.vespera.pipeline;

import io.algernon.vespera.corpus.Walk;
import io.algernon.vespera.extraction.ExtractorIdentity;
import io.algernon.vespera.ledger.ImplementationVersions;
import io.algernon.vespera.ledger.Ledger;
import io.algernon.vespera.ledger.RunId;
import io.algernon.vespera.ledger.WalkId;
import java.nio.file.Path;
import java.util.List;
import org.springframework.batch.core.configuration.annotation.JobScope;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * Mints stage 4's run exactly once per job execution, shared by both of its steps — the #75 hand-off
 * comment's settlement of the map's open fog. {@code Ledger.startRun} has no continuation clause: a
 * second call minting the same content-derived id (ADR-048) would collide against {@code run}'s primary
 * key, so one bean held for the whole job is what avoids that, rather than a {@code Ledger} change that
 * would make "a run already exists" unremarkable for every stage.
 *
 * <p>{@code @JobScope} rather than {@code @StepScope}: {@link ContentCensusRun}'s reason for being
 * step-scoped is reading {@code jobParameters['root']}, which a job-scoped bean reads equally well, and
 * job scope is what lets one instance serve both {@code redundancySignatureStep} and {@code
 * redundancyResolutionStep}.
 *
 * <p>Every caller reaches this bean through an {@code ObjectProvider}, so it — and therefore the run row
 * its constructor mints — is never instantiated while {@link RedundancyGate#floor()} is empty (ADR-080:
 * "a stage-4 run that did nothing should not exist in the run table"). The constructor itself still
 * checks the gate and refuses to run otherwise, since a bean must not depend on every caller remembering
 * to check first.
 *
 * <p>Stage 3's run id is re-derived from its known-fixed inputs, the same way {@link ContentCensusRun}
 * re-derives stage 2's and {@link ContentCensusRun} in turn re-derives stage 1's — never read off
 * job-execution-context, which would also tie this run to having executed inside the same invocation
 * stage 3 did.
 */
@Component
@JobScope
class RedundancyRun {

    /** The stage name this run is minted under. */
    static final String STAGE = "content-redundancy";

    /** The module owning the signature and resolution passes (ADR-081, ADR-082). */
    static final String OWNING_MODULE = "similarity";

    /**
     * Named alongside {@link #OWNING_MODULE}: the near-duplicate survivor rule reads {@code
     * extraction_metric.alphanumeric_char_count} (ADR-079), so a change to how that column is computed
     * is itself a change to what this stage's runs would produce.
     */
    static final String EXTRACTION_MODULE = "extraction";

    /** The composing steps' own module (ADR-058's reading, the same narrowing every prior stage applies). */
    static final String PIPELINE_MODULE = "pipeline";

    private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();

    private final RunId runId;
    private final RunId stage3RunId;
    private final RunId extractionRunId;
    private final double floor;

    RedundancyRun(
            Ledger ledger,
            ImplementationVersions implementationVersions,
            ExtractorIdentity extractorIdentity,
            DegenerateOutputConfidenceFloor confidenceFloor,
            RedundancyGate redundancyGate,
            @Value("#{jobParameters['root']}") Path root) {
        this.floor = redundancyGate
                .floor()
                .orElseThrow(() ->
                        new IllegalStateException("RedundancyRun must not be instantiated while the gate is closed"));

        Path canonicalRoot = Walk.canonicalRoot(root);
        WalkId walkId = ledger.finishedWalkFor(canonicalRoot)
                .orElseThrow(() -> new IllegalStateException(
                        "no finished walk is recorded for " + canonicalRoot + "; census must run before stage 4"));

        RunId byteLevelReductionRunId = RunId.of(
                implementationVersions.of(ByteLevelReductionTasklet.OWNING_MODULE),
                ByteLevelReductionTasklet.CONFIG_CONSUMED,
                walkId,
                List.of());
        this.extractionRunId = RunId.of(
                implementationVersions.of(ExtractionRun.OWNING_MODULE, ExtractionRun.SIMILARITY_MODULE),
                ExtractionRun.configConsumed(extractorIdentity, confidenceFloor),
                walkId,
                List.of(byteLevelReductionRunId));
        this.stage3RunId = RunId.of(
                implementationVersions.of(
                        ContentCensusRun.OWNING_MODULE, ContentCensusRun.EXTRACTION_MODULE, ContentCensusRun.PIPELINE_MODULE),
                ContentCensusRun.configConsumed(canonicalRoot, this.extractionRunId),
                walkId,
                List.of(this.extractionRunId));

        this.runId = ledger.startRun(
                STAGE,
                implementationVersions.of(OWNING_MODULE, EXTRACTION_MODULE, PIPELINE_MODULE),
                configConsumed(canonicalRoot, this.stage3RunId, this.floor),
                walkId,
                List.of(this.stage3RunId));
    }

    /**
     * {@code configConsumed} names the walk/root, the stage-3 run id read, and the boilerplate floor
     * value: a run under a different floor must be a different run, since the floor sits in every
     * signature's identity and changes what every signature this run writes actually means (ADR-080).
     */
    private static String configConsumed(Path canonicalRoot, RunId stage3RunId, double floor) {
        return JSON_MAPPER.writeValueAsString(
                new ConfigConsumed(canonicalRoot.toString(), stage3RunId.value(), floor));
    }

    RunId runId() {
        return runId;
    }

    /** Stage 3's run id, stage 4's own upstream (ADR-080). */
    RunId stage3RunId() {
        return stage3RunId;
    }

    /** Stage 2's run id — where {@code shingle} and {@code extraction_metric} rows actually live. */
    RunId extractionRunId() {
        return extractionRunId;
    }

    /** The boilerplate floor this run's signatures were stripped against. */
    double floor() {
        return floor;
    }

    private record ConfigConsumed(String root, String stage3RunId, double boilerplateDocumentFrequencyFloor) {}
}
