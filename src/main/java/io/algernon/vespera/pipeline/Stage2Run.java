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
 * <p>Stage 1's run id is not read off any in-process state (no job-execution-context handoff, which
 * would also tie this to running inside the same job invocation stage 1 did rather than to whatever
 * "Run: minted when configuration changes, continued when work resumes" already promises). It is
 * recomputed instead: a run's id is wholly determined by its four inputs (ADR-048), stage 1's are
 * every one of them fixed and known here ({@link Stage1Tasklet#OWNING_MODULE},
 * {@link Stage1Tasklet#CONFIG_CONSUMED}, this walk, no upstream runs of its own), so re-deriving the
 * same {@link RunId#of} stage 1 minted its row under is exact, not a guess — and the foreign key
 * {@code run_upstream.upstream_run_id} enforces that a row actually exists under it.
 */
@Component
@StepScope
class Stage2Run {

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
    private final RunId stage1RunId;
    private final Path canonicalRoot;

    Stage2Run(
            Ledger ledger,
            ImplementationVersions implementationVersions,
            ExtractorIdentity extractorIdentity,
            @Value("#{jobParameters['root']}") Path root) {
        this.canonicalRoot = Walk.canonicalRoot(root);
        WalkId walkId = ledger.finishedWalkFor(canonicalRoot)
                .orElseThrow(() -> new IllegalStateException(
                        "no finished walk is recorded for " + canonicalRoot + "; census must run before stage 2"));
        this.stage1RunId = RunId.of(
                implementationVersions.of(Stage1Tasklet.OWNING_MODULE), Stage1Tasklet.CONFIG_CONSUMED, walkId, List.of());
        this.runId = ledger.startRun(
                STAGE,
                implementationVersions.of(OWNING_MODULE, SIMILARITY_MODULE),
                configConsumed(extractorIdentity),
                walkId,
                List.of(this.stage1RunId));
    }

    /**
     * {@code configConsumed} records the extractor identity, per this ticket's acceptance criteria;
     * #48 extends this with the tier-2 confidence-floor profile value once that key exists.
     */
    private static String configConsumed(ExtractorIdentity extractorIdentity) {
        return JSON_MAPPER.writeValueAsString(new ConfigConsumed(extractorIdentity.value()));
    }

    RunId runId() {
        return runId;
    }

    RunId stage1RunId() {
        return stage1RunId;
    }

    Path canonicalRoot() {
        return canonicalRoot;
    }

    private record ConfigConsumed(String extractorIdentity) {}
}
