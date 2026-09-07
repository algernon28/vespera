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
 * Mints stage 5's measurement run, once per job execution (ADR-083, ADR-089).
 *
 * <p><b>Its upstream is stage 4's run, not stage 2's.</b> ADR-086 originally said stage 2's, reasoning
 * from which tables the seed/corpus comparison reads — every signal is an {@code extraction_metric}
 * column. ADR-089 corrected it: the corpus side of that comparison is <em>survivors</em>, and
 * {@link Ledger#survivors} counts blocking verdicts from every run, so stage 4's {@code
 * redundant-with} verdicts change what this run measures. Naming stage 4 folds stage 3's, stage 2's
 * and stage 1's identities in anyway (ADR-048), so nothing is lost by it and a run id that would
 * otherwise describe two different corpora is avoided.
 *
 * <p>Every caller reaches this bean through an {@code ObjectProvider}, so it — and the run row its
 * constructor mints — is never instantiated while {@link SeedGate} is closed or while no seed proved
 * usable (ADR-080's rule, ADR-083's gate). The constructor checks the gate itself as well, since a
 * bean must not depend on every caller remembering to check first.
 *
 * <p>{@code @JobScope} rather than {@code @StepScope}, following {@link RedundancyRun}: one instance
 * serves every step of stage 5 within one invocation, which is what keeps {@code Ledger.startRun} —
 * which has no continuation clause — from being called twice with the same content-derived id and
 * colliding on {@code run}'s primary key.
 *
 * <p>The walk this run is recorded against is the <b>corpus</b> walk, not the seed walk. A run's walk
 * is the corpus it judges (and stage 5 goes on to judge corpus survivors); the seed folder is
 * configuration this run consumed, which is why it is named in {@code configConsumed} instead. That
 * also keeps the upstream chain intact: stage 4's run is against the corpus walk, and a run may only
 * name an upstream over the same corpus.
 */
@Component
@JobScope
class SeedMeasurementRun {

    /** The stage name this run is minted under. */
    static final String STAGE = "seed-measurement";

    /** The module owning the seed set's own tables (ADR-083). */
    static final String OWNING_MODULE = "embedding";

    /**
     * Named alongside {@link #OWNING_MODULE}: seed extraction and chunking are {@code extraction}'s
     * instruments, so a change to either changes what this run produces (ADR-058's reading, the same
     * one every prior stage applies).
     */
    static final String EXTRACTION_MODULE = "extraction";

    /** The composing steps' own module. */
    static final String PIPELINE_MODULE = "pipeline";

    private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();

    private final RunId runId;
    private final RunId redundancyRunId;

    SeedMeasurementRun(
            Ledger ledger,
            ImplementationVersions implementationVersions,
            ExtractorIdentity extractorIdentity,
            DegenerateOutputConfidenceFloor confidenceFloor,
            RedundancyGate redundancyGate,
            SeedGate seedGate,
            @Value("#{jobParameters['root']}") Path root) {
        SeedGate.SeedWalk seedWalk = seedGate.seedWalk()
                .orElseThrow(() -> new IllegalStateException(
                        "SeedMeasurementRun must not be instantiated while the seed gate is closed"));
        double floor = redundancyGate
                .floor()
                .orElseThrow(() -> new IllegalStateException(
                        "SeedMeasurementRun must not be instantiated while stage 4's gate is closed"));

        Path canonicalRoot = Walk.canonicalRoot(root);
        WalkId walkId = ledger.finishedWalkFor(canonicalRoot)
                .orElseThrow(() -> new IllegalStateException(
                        "no finished walk is recorded for " + canonicalRoot + "; census must run before stage 5"));

        this.redundancyRunId = redundancyRunId(
                implementationVersions, extractorIdentity, confidenceFloor, canonicalRoot, walkId, floor);
        this.runId = ledger.startRun(
                STAGE,
                implementationVersions.of(OWNING_MODULE, EXTRACTION_MODULE, PIPELINE_MODULE),
                configConsumed(canonicalRoot, seedWalk.canonicalRoot(), this.redundancyRunId),
                walkId,
                List.of(this.redundancyRunId));
    }

    /**
     * Stage 4's run id, re-derived from its known-fixed inputs rather than read off any in-process
     * state — the same re-derivation {@link RedundancyRun} performs for stages 3, 2 and 1, and for the
     * same reason: a run's id is wholly determined by ADR-048's four inputs, so recomputing it is
     * exact rather than a guess, and the {@code run_upstream} foreign key enforces that a row actually
     * exists under it.
     */
    private static RunId redundancyRunId(
            ImplementationVersions implementationVersions,
            ExtractorIdentity extractorIdentity,
            DegenerateOutputConfidenceFloor confidenceFloor,
            Path canonicalRoot,
            WalkId walkId,
            double floor) {
        RunId byteLevelReductionRunId = RunId.of(
                implementationVersions.of(ByteLevelReductionTasklet.OWNING_MODULE),
                ByteLevelReductionTasklet.CONFIG_CONSUMED,
                walkId,
                List.of());
        RunId extractionRunId = RunId.of(
                implementationVersions.of(ExtractionRun.OWNING_MODULE, ExtractionRun.SIMILARITY_MODULE),
                ExtractionRun.configConsumed(extractorIdentity, confidenceFloor),
                walkId,
                List.of(byteLevelReductionRunId));
        RunId contentCensusRunId = RunId.of(
                implementationVersions.of(
                        ContentCensusRun.OWNING_MODULE,
                        ContentCensusRun.EXTRACTION_MODULE,
                        ContentCensusRun.PIPELINE_MODULE),
                ContentCensusRun.configConsumed(canonicalRoot, extractionRunId),
                walkId,
                List.of(extractionRunId));
        return RunId.of(
                implementationVersions.of(
                        RedundancyRun.OWNING_MODULE, RedundancyRun.EXTRACTION_MODULE, RedundancyRun.PIPELINE_MODULE),
                RedundancyRun.configConsumed(canonicalRoot, contentCensusRunId, floor),
                walkId,
                List.of(contentCensusRunId));
    }

    /**
     * {@code configConsumed} names the corpus root, the seed folder and the upstream run id — ADR-083
     * leans on the middle one: a corpus scored against a changed seed set is a different run, which is
     * what makes "scoring proceeds against the seeds that survived extraction" safe without a check.
     */
    static String configConsumed(Path canonicalRoot, Path canonicalSeedFolder, RunId redundancyRunId) {
        return JSON_MAPPER.writeValueAsString(new ConfigConsumed(
                canonicalRoot.toString(), canonicalSeedFolder.toString(), redundancyRunId.value()));
    }

    RunId runId() {
        return runId;
    }

    /** Stage 4's run id, this run's own upstream (ADR-089). */
    RunId redundancyRunId() {
        return redundancyRunId;
    }

    private record ConfigConsumed(String root, String seedFolder, String redundancyRunId) {}
}
