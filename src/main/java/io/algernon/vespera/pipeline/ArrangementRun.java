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
 * Mints stage 6a's run, once per job execution (ADR-105, ADR-110): {@link ScoringRun}'s sibling one
 * stage along.
 *
 * <p><b>Its upstream is the scoring run the clusters were formed under.</b> Naming it folds every
 * earlier stage's identity in already (ADR-048's transitivity), and it is what makes the deliverable's
 * directory behave: a run id hashes its upstream runs, so a re-arrangement changes the generation
 * run's id — and therefore the tree it writes — by construction rather than by a rule someone has to
 * remember (ADR-103).
 *
 * <p>Every caller reaches this bean through an {@code ObjectProvider}, so the run row its constructor
 * mints never exists while the step is gated (ADR-080's rule, applied again).
 *
 * <p><b>Implementation versions name every module the pass invokes</b>, following {@link ScoringRun}'s
 * precedent rather than only the modules it writes to: this pass reads what the label rule needs out of
 * {@code extraction}'s cache and the scores out of {@code embedding}.
 *
 * <p>{@code @JobScope} rather than {@code @StepScope}, so that one instance serves the invocation and
 * {@code Ledger.startRun} — which has no continuation clause — is never called twice with one
 * content-derived id.
 */
@Component
@JobScope
class ArrangementRun {

    /**
     * The stage name this run is minted under, matching its step's own name. Capability-named with no
     * ordinal in it, like the six that exist — which is also what keeps an ordinal out of anything the
     * operator reads (ADR-098).
     */
    static final String STAGE = "arrangement";

    /** The module owning the rows this run writes. */
    static final String OWNING_MODULE = "synthesis";

    /** Named because the label rule reads the titles this module cached (ADR-106). */
    static final String EXTRACTION_MODULE = "extraction";

    /** Named because the order and the label both rest on the scores this module wrote. */
    static final String EMBEDDING_MODULE = "embedding";

    /** The composing step's own module. */
    static final String PIPELINE_MODULE = "pipeline";

    private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();

    private final RunId runId;

    ArrangementRun(
            Ledger ledger,
            ImplementationVersions implementationVersions,
            ObjectProvider<ScoringRun> scoringRun,
            @Value("#{jobParameters['root']}") Path root) {
        RunId scoring = scoringRun.getObject().runId();
        Path canonicalRoot = Walk.canonicalRoot(root);
        WalkId walkId = ledger.finishedWalkFor(canonicalRoot)
                .orElseThrow(() -> new IllegalStateException("no finished walk is recorded for " + canonicalRoot
                        + "; census must run before the documents can be arranged"));

        this.runId = ledger.startRun(
                STAGE,
                implementationVersions.of(OWNING_MODULE, EXTRACTION_MODULE, EMBEDDING_MODULE, PIPELINE_MODULE),
                configConsumed(canonicalRoot, scoring),
                walkId,
                List.of(scoring));
    }

    /**
     * {@code configConsumed} names the corpus root and the upstream scoring run, and nothing else: this
     * pass has no operator-supplied value of its own — the approval that follows it is about the run
     * this mints, not an input to it.
     */
    static String configConsumed(Path canonicalRoot, RunId scoringRunId) {
        return JSON_MAPPER.writeValueAsString(new ConfigConsumed(canonicalRoot.toString(), scoringRunId.value()));
    }

    RunId runId() {
        return runId;
    }

    private record ConfigConsumed(String corpusRoot, String scoringRunId) {}
}
