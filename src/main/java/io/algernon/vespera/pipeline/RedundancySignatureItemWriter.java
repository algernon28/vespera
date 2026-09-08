package io.algernon.vespera.pipeline;

import io.algernon.vespera.ledger.Ledger;
import io.algernon.vespera.ledger.OccurrenceId;
import io.algernon.vespera.similarity.RedundancySignatures;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.infrastructure.item.Chunk;
import org.springframework.batch.infrastructure.item.ItemWriter;
import org.springframework.stereotype.Component;

/**
 * Writes one MinHash signature (plus its band rows) per stage-2 survivor in the chunk, delegating the
 * actual read-strip-compute-write to {@link RedundancySignatures} (ADR-080, ADR-081) — this class only
 * supplies the per-run identity everything in it needs to agree on.
 *
 * <p>Never invoked while the gate is closed: {@link RedundancyJobConfiguration}'s reader yields no items
 * in that case, so this writer's own step-scoped target is never even constructed — which is what keeps
 * {@link RedundancyRun}, reached here as a direct dependency, from minting a run row it should not.
 */
@Component
@StepScope
class RedundancySignatureItemWriter implements ItemWriter<OccurrenceId> {

    private static final Logger log = LoggerFactory.getLogger(RedundancySignatureItemWriter.class);

    private final RedundancySignatures redundancySignatures;
    private final RedundancyRun redundancyRun;
    private final RedundancyBoilerplate redundancyBoilerplate;

    /** Stage 4a's progress line (ADR-093), over the survivor set this step's reader was given. */
    private final StageProgress progress;

    RedundancySignatureItemWriter(
            Ledger ledger,
            RedundancySignatures redundancySignatures,
            RedundancyRun redundancyRun,
            RedundancyBoilerplate redundancyBoilerplate) {
        this.redundancySignatures = redundancySignatures;
        this.redundancyRun = redundancyRun;
        this.redundancyBoilerplate = redundancyBoilerplate;
        this.progress = StageProgress.over(
                "Stage 4a (redundancy signatures)", ledger.survivorCount(redundancyRun.runId()));
    }

    @Override
    public void write(Chunk<? extends OccurrenceId> chunk) {
        for (OccurrenceId occurrenceId : chunk) {
            redundancySignatures.write(
                    occurrenceId,
                    redundancyRun.runId(),
                    redundancyRun.extractionRunId(),
                    redundancyBoilerplate.hashes(),
                    redundancyRun.floor());
            log.info("[redundancy-signature] finished {}", occurrenceId.value());
            progress.itemDone();
        }
    }
}
