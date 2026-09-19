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
 * Mints stage 2's run exactly once per step execution, and hands out the identity everything else in
 * the step needs to agree on: the run just minted, and stage 1's run it reads as upstream.
 *
 * <p>Step-scoped so the mint happens once per step execution rather than once per bean lookup — the
 * reader, processor, writer and listeners below all depend on this bean, and Spring resolves a
 * step-scoped bean to the same instance for every dependant within one step execution, the same
 * caching a request-scoped bean gets within one request.
 *
 * <p>Reads whichever walk stage 1 read (ADR: "the step reads whichever walk it's given" — nothing
 * here hard-codes the corpus walk), by resolving the same {@code root} job parameter stage 1 resolved
 * its own walk from.
 *
 * <p>Stage 1's run id is learned rather than worked out: {@link UpstreamRuns} looks up the run of
 * stage 1 recorded against this walk (ADR-099). The stage must not depend on having run in the same
 * invocation as its predecessor — no job-execution-context handoff — and a query is not in-process
 * state. It replaces re-deriving the id from stage 1's known-fixed inputs, which would drift silently
 * if the JSON shape stage 1 hashed ever changed here (ADR-099).
 */
@Component
@StepScope
class ExtractionRun {

    /** The stage name this run is minted under. */
    static final String STAGE = "extraction";

    /** The module whose implementation version this stage's runs are versioned against (ADR-058). */
    static final String OWNING_MODULE = "extraction";

    /**
     * The second module stage 2's single pass writes into: its shingle table (ADR-073). Named
     * alongside {@link #OWNING_MODULE} when the run is minted, so a shingler-only commit — one that
     * never touches {@code extraction} at all — still mints a fresh run rather than shipping under a
     * run id nothing recomputed the shingles under.
     */
    static final String SIMILARITY_MODULE = "similarity";

    private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();

    private final RunId runId;
    private final RunId byteLevelReductionRunId;
    private final Path canonicalRoot;

    ExtractionRun(
            Ledger ledger,
            ImplementationVersions implementationVersions,
            ExtractorIdentity extractorIdentity,
            DegenerateOutputConfidenceFloor confidenceFloor,
            @Value("#{jobParameters['root']}") Path root) {
        this.canonicalRoot = Walk.canonicalRoot(root);
        WalkId walkId = ledger.finishedWalkFor(canonicalRoot)
                .orElseThrow(() -> new IllegalStateException(
                        "no finished walk is recorded for " + canonicalRoot + "; census must run before stage 2"));
        this.byteLevelReductionRunId =
                new UpstreamRuns(ledger).runOf(ByteLevelReductionTasklet.STAGE, walkId);
        this.runId = ledger.startRun(
                STAGE,
                implementationVersions.of(OWNING_MODULE, SIMILARITY_MODULE),
                configConsumed(extractorIdentity, confidenceFloor),
                walkId,
                List.of(this.byteLevelReductionRunId));
    }

    /**
     * {@code configConsumed} records the extractor identity and #48's tier-2 confidence-floor value —
     * what shaped this run's output is recoverable from the run row itself (hand-off spec #45's own
     * requirement).
     *
     * <p>Private since ADR-099: nothing outside this class needs it. It was package-visible only while
     * later stages re-derived this run's identity by reproducing this JSON shape, and they now look the
     * run up instead.
     */
    private static String configConsumed(ExtractorIdentity extractorIdentity, DegenerateOutputConfidenceFloor confidenceFloor) {
        return JSON_MAPPER.writeValueAsString(
                new ConfigConsumed(extractorIdentity.value(), confidenceFloor.value()));
    }

    RunId runId() {
        return runId;
    }

    RunId byteLevelReductionRunId() {
        return byteLevelReductionRunId;
    }

    Path canonicalRoot() {
        return canonicalRoot;
    }

    private record ConfigConsumed(String extractorIdentity, Double degenerateOutputConfidenceFloor) {}
}
