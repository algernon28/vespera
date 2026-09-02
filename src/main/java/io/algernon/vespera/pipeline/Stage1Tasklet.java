package io.algernon.vespera.pipeline;

import io.algernon.vespera.corpus.BrokenCheck;
import io.algernon.vespera.corpus.ContentHash;
import io.algernon.vespera.corpus.ContentIdentity;
import io.algernon.vespera.corpus.DuplicateResolution;
import io.algernon.vespera.corpus.DuplicateResolution.Candidate;
import io.algernon.vespera.corpus.Walk;
import io.algernon.vespera.ledger.ImplementationVersions;
import io.algernon.vespera.ledger.Ledger;
import io.algernon.vespera.ledger.OccurrenceFacts;
import io.algernon.vespera.ledger.OccurrenceId;
import io.algernon.vespera.ledger.RunId;
import io.algernon.vespera.ledger.VerdictKind;
import io.algernon.vespera.ledger.WalkId;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
 * Stage 1: byte-level reduction. Two passes over one run, in order:
 *
 * <ol>
 *   <li>Every survivor of census is checked against {@link BrokenCheck}; a mechanically-corrupt
 *       occurrence is verdicted {@code broken} (ADR-068) — the cheapest filter in the cascade, so
 *       nothing broken ever reaches extraction.
 *   <li>Every occurrence still surviving (re-read from the ledger, so {@code broken} occurrences are
 *       excluded automatically) is grouped by size, hashed within any group of two or more, and
 *       every content-identity group resolves to one representative — the rest verdicted
 *       {@code superseded-by} (ADR-067, ADR-069).
 * </ol>
 *
 * <p>Lives here rather than in {@code corpus} because {@code corpus} does not know what a stage is
 * (ADR-040); {@link BrokenCheck}, {@link ContentHash} and {@link DuplicateResolution} are the
 * capabilities, this tasklet is the stage that drives them in order.
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
    private final ContentIdentity contentIdentity;
    private final ImplementationVersions implementationVersions;
    private final Path root;

    public Stage1Tasklet(
            Ledger ledger,
            ContentIdentity contentIdentity,
            ImplementationVersions implementationVersions,
            @Value("#{jobParameters['root']}") Path root) {
        this.ledger = ledger;
        this.contentIdentity = contentIdentity;
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

        verdictBrokenSurvivors(runId, canonicalRoot);
        resolveDuplicates(runId, canonicalRoot);

        return RepeatStatus.FINISHED;
    }

    private void verdictBrokenSurvivors(RunId runId, Path canonicalRoot) throws Exception {
        for (OccurrenceId occurrenceId : drain(ledger.survivors(runId))) {
            OccurrenceFacts facts = factsFor(occurrenceId);
            BrokenCheck.Result result = BrokenCheck.check(canonicalRoot.resolve(facts.path().value()));
            if (result.broken()) {
                ledger.verdict(occurrenceId, runId, VerdictKind.BROKEN, result.reason());
            }
        }
    }

    /**
     * Re-reads the survivor set: {@code broken} occurrences verdicted above are excluded by the same
     * anti-join {@link Ledger#survivors} always runs, so this stage's own boundary needs no
     * application-level filtering.
     */
    private void resolveDuplicates(RunId runId, Path canonicalRoot) throws Exception {
        Map<Long, List<OccurrenceId>> bySize = new LinkedHashMap<>();
        for (OccurrenceId occurrenceId : drain(ledger.survivors(runId))) {
            long sizeBytes = factsFor(occurrenceId).sizeBytes();
            bySize.computeIfAbsent(sizeBytes, ignored -> new ArrayList<>()).add(occurrenceId);
        }

        for (List<OccurrenceId> sameSize : bySize.values()) {
            if (sameSize.size() < 2) {
                continue;
            }
            resolveGroupSharingASize(runId, canonicalRoot, sameSize);
        }
    }

    private void resolveGroupSharingASize(RunId runId, Path canonicalRoot, List<OccurrenceId> sameSize)
            throws Exception {
        Map<String, List<Candidate>> byHash = new HashMap<>();
        for (OccurrenceId occurrenceId : sameSize) {
            OccurrenceFacts facts = factsFor(occurrenceId);
            String sha256 = ContentHash.sha256(canonicalRoot.resolve(facts.path().value()));
            contentIdentity.recordHash(occurrenceId, runId, sha256);
            byHash.computeIfAbsent(sha256, ignored -> new ArrayList<>())
                    .add(new Candidate(occurrenceId, facts.path(), facts.creationTime()));
        }

        for (List<Candidate> sameHash : byHash.values()) {
            if (sameHash.size() < 2) {
                continue;
            }
            verdictSuperseded(runId, DuplicateResolution.resolve(sameHash), sameHash);
        }
    }

    private void verdictSuperseded(RunId runId, DuplicateResolution.Resolution resolution, List<Candidate> group) {
        String representativePath = group.stream()
                .filter(candidate -> candidate.occurrenceId().equals(resolution.representative()))
                .findFirst()
                .orElseThrow()
                .path()
                .value();
        for (OccurrenceId superseded : resolution.superseded()) {
            contentIdentity.recordSupersededBy(superseded, runId, resolution.representative());
            ledger.verdict(
                    superseded,
                    runId,
                    VerdictKind.SUPERSEDED_BY,
                    "superseded by the representative at " + representativePath);
        }
    }

    private OccurrenceFacts factsFor(OccurrenceId occurrenceId) {
        return ledger.factsFor(occurrenceId)
                .orElseThrow(() -> new IllegalStateException("no facts are recorded for occurrence " + occurrenceId.value()));
    }

    /** Reads a survivors reader to exhaustion, since both passes need the whole set, not one chunk. */
    private static List<OccurrenceId> drain(ItemStreamReader<OccurrenceId> reader) throws Exception {
        List<OccurrenceId> read = new ArrayList<>();
        reader.open(new ExecutionContext());
        try {
            for (OccurrenceId id = reader.read(); id != null; id = reader.read()) {
                read.add(id);
            }
        } finally {
            reader.close();
        }
        return read;
    }
}
