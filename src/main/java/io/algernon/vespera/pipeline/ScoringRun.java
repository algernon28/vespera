package io.algernon.vespera.pipeline;

import io.algernon.vespera.corpus.Walk;
import io.algernon.vespera.ledger.ImplementationVersions;
import io.algernon.vespera.ledger.Ledger;
import io.algernon.vespera.ledger.RunId;
import io.algernon.vespera.ledger.WalkId;
import java.nio.file.Path;
import java.util.List;
import org.springframework.batch.core.configuration.annotation.JobScope;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * Mints gate 3's scoring run, once per job execution (ADR-084, #107): {@link SeedMeasurementRun}'s
 * sibling, minted only once an embedding model is named and stage 5's earlier gates are open.
 *
 * <p><b>Its upstream is the measurement run, not stage 4's run beneath it.</b> Naming the measurement
 * run folds every earlier stage's identity in already (ADR-048's transitivity), and the scoring run
 * only exists at all once the measurement run does — gate 3 sits between them (ADR-084), so a
 * measurement run is always this run's immediate predecessor rather than a run this ticket must
 * re-derive from scratch the way {@link SeedMeasurementRun} re-derives stage 4's and stage 2's.
 *
 * <p>Every caller reaches this bean through an {@code ObjectProvider}, so it — and the run row its
 * constructor mints — is never instantiated while {@link EmbeddingModelGate} is shut (ADR-080's rule,
 * applied a fourth time). The constructor checks the gate itself as well, for the reason
 * {@link SeedMeasurementRun} already documents: a bean must not depend on every caller remembering to
 * check first.
 *
 * <p>{@code @JobScope} rather than {@code @StepScope}: one instance serves every step of stage 5's
 * scoring half within one invocation, which is what keeps {@link Ledger#startRun} — which has no
 * continuation clause — from being called twice with the same content-derived id.
 */
@Component
@JobScope
class ScoringRun {

    /** The stage name this run is minted under, matching {@code embedding-scoring}'s own step name. */
    static final String STAGE = "embedding-scoring";

    /** The module owning the vectors this run's steps write (ADR-084, ADR-085). */
    static final String OWNING_MODULE = "embedding";

    /** Named alongside {@link #OWNING_MODULE}: re-chunking is {@code extraction}'s own instrument. */
    static final String EXTRACTION_MODULE = "extraction";

    /** The composing steps' own module. */
    static final String PIPELINE_MODULE = "pipeline";

    private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();

    private final RunId runId;

    ScoringRun(
            Ledger ledger,
            ImplementationVersions implementationVersions,
            EmbeddingModelGate embeddingModelGate,
            ObjectProvider<SeedMeasurementRun> seedMeasurementRun,
            @Value("#{jobParameters['root']}") Path root) {
        String modelName = embeddingModelGate
                .modelName()
                .orElseThrow(() -> new IllegalStateException(
                        "ScoringRun must not be instantiated while embeddingModel is unset"));
        SeedMeasurementRun measurementRun = seedMeasurementRun.getObject();

        Path canonicalRoot = Walk.canonicalRoot(root);
        WalkId walkId = ledger.finishedWalkFor(canonicalRoot)
                .orElseThrow(() -> new IllegalStateException(
                        "no finished walk is recorded for " + canonicalRoot + "; census must run before stage 5"));

        this.runId = ledger.startRun(
                STAGE,
                implementationVersions.of(OWNING_MODULE, EXTRACTION_MODULE, PIPELINE_MODULE),
                configConsumed(canonicalRoot, modelName, measurementRun.runId()),
                walkId,
                List.of(measurementRun.runId()));
    }

    /**
     * {@code configConsumed} names the corpus root, the embedding model and the upstream measurement
     * run — the model because naming a different one is a different scoring run by definition
     * (ADR-084), and the root for the same reason every other stage's own {@code configConsumed} names
     * it.
     */
    static String configConsumed(Path canonicalRoot, String modelName, RunId measurementRunId) {
        return JSON_MAPPER.writeValueAsString(
                new ConfigConsumed(canonicalRoot.toString(), modelName, measurementRunId.value()));
    }

    RunId runId() {
        return runId;
    }

    private record ConfigConsumed(String root, String embeddingModel, String measurementRunId) {}
}
