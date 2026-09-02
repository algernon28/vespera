package io.algernon.vespera.pipeline;

import io.algernon.vespera.corpus.BrokenCheck;
import io.algernon.vespera.corpus.Walk;
import io.algernon.vespera.ledger.ImplementationVersions;
import io.algernon.vespera.ledger.Ledger;
import io.algernon.vespera.ledger.OccurrenceId;
import io.algernon.vespera.ledger.OccurrencePath;
import io.algernon.vespera.ledger.RunId;
import io.algernon.vespera.ledger.VerdictKind;
import io.algernon.vespera.ledger.WalkId;
import java.nio.file.Path;
import java.util.List;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.StepContribution;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.infrastructure.item.ExecutionContext;
import org.springframework.batch.infrastructure.item.ItemStreamReader;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Stage 1: byte-level reduction. Reads census's survivors and verdicts every mechanically-corrupt
 * occurrence as {@code broken} (ADR-068) — the cheapest filter in the cascade, so nothing broken
 * ever reaches extraction.
 *
 * <p>Lives here rather than in {@code corpus} because {@code corpus} does not know what a stage is
 * (ADR-040); {@link BrokenCheck} is the capability, this tasklet is the stage that drives it.
 *
 * <p>Later tickets extend this same step with content-identity and duplicate resolution (ADR-067,
 * ADR-069), always over what survives this check — a {@code broken} occurrence is never hashed or
 * grouped.
 */
@Component
@StepScope
public class Stage1Tasklet implements Tasklet {

    /** The stage name a run is minted under. */
    static final String STAGE = "byte-level-reduction";

    /** The module whose implementation version this stage's runs are versioned against (ADR-058). */
    static final String OWNING_MODULE = "corpus";

    /** Stage 1 is fully deterministic — no profile value shapes it, so a run's config is empty. */
    static final String CONFIG_CONSUMED = "{}";

    private final Ledger ledger;
    private final ImplementationVersions implementationVersions;
    private final Path root;

    public Stage1Tasklet(
            Ledger ledger, ImplementationVersions implementationVersions, @Value("#{jobParameters['root']}") Path root) {
        this.ledger = ledger;
        this.implementationVersions = implementationVersions;
        this.root = root;
    }

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) throws Exception {
        Path canonicalRoot = Walk.canonicalRoot(root);
        WalkId walkId = ledger.finishedWalkFor(canonicalRoot)
                .orElseThrow(() -> new IllegalStateException(
                        "no finished walk is recorded for " + canonicalRoot + "; census must run before stage 1"));
        RunId runId =
                ledger.startRun(STAGE, implementationVersions.of(OWNING_MODULE), CONFIG_CONSUMED, walkId, List.of());

        ItemStreamReader<OccurrenceId> survivors = ledger.survivors(runId);
        survivors.open(new ExecutionContext());
        try {
            for (OccurrenceId occurrenceId = survivors.read(); occurrenceId != null; occurrenceId = survivors.read()) {
                verdictIfBroken(runId, canonicalRoot, occurrenceId);
            }
        } finally {
            survivors.close();
        }
        return RepeatStatus.FINISHED;
    }

    private void verdictIfBroken(RunId runId, Path canonicalRoot, OccurrenceId occurrenceId) {
        OccurrencePath path = ledger.pathFor(occurrenceId)
                .orElseThrow(
                        () -> new IllegalStateException("no path is recorded for occurrence " + occurrenceId.value()));
        BrokenCheck.Result result = BrokenCheck.check(canonicalRoot.resolve(path.value()));
        if (result.broken()) {
            ledger.verdict(occurrenceId, runId, VerdictKind.BROKEN, result.reason());
        }
    }
}
