package io.algernon.vespera.corpus;

import io.algernon.vespera.corpus.DuplicateResolution.Candidate;
import io.algernon.vespera.ledger.Ledger;
import io.algernon.vespera.ledger.OccurrenceFacts;
import io.algernon.vespera.ledger.OccurrenceId;
import io.algernon.vespera.ledger.RunId;
import io.algernon.vespera.ledger.VerdictKind;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Stage 1's second pass: content identity over what the first pass left (ADR-188, moved from
 * {@code pipeline} unchanged). Every occurrence still surviving is grouped by size, hashed within any
 * group of two or more, and every content-identity group resolves to one representative, the rest
 * verdicted {@code superseded-by} (ADR-067, ADR-069).
 *
 * <p>This logs nothing per item: what an operator reads is written by the caller, through
 * {@link HashingProgress}.
 */
public final class ContentIdentityResolution {

    private final Ledger ledger;
    private final ContentIdentity contentIdentity;

    public ContentIdentityResolution(Ledger ledger, ContentIdentity contentIdentity) {
        this.ledger = ledger;
        this.contentIdentity = contentIdentity;
    }

    /**
     * Re-reads the survivor set: occurrences the first pass verdicted are excluded by the same
     * anti-join {@link Ledger#survivors} always runs, so this pass's own boundary needs no
     * application-level filtering.
     */
    public void resolve(RunId runId, Path canonicalRoot, HashingProgress progress) throws Exception {
        Map<Long, List<OccurrenceId>> bySize = new LinkedHashMap<>();
        List<OccurrenceId> survivors = SurvivorDrain.drain(ledger.survivors(runId));
        progress.toSize(survivors.size());
        for (OccurrenceId occurrenceId : survivors) {
            long sizeBytes = factsFor(occurrenceId).sizeBytes();
            bySize.computeIfAbsent(sizeBytes, ignored -> new ArrayList<>()).add(occurrenceId);
            progress.sized();
        }

        // The hash pass's own denominator, and not the survivor count: a file whose size is unique to it
        // is never hashed at all (ADR-057), so counting it in would leave this pass reporting a fraction
        // of a total it will never reach.
        long toHash = bySize.values().stream()
                .filter(sameSize -> sameSize.size() >= 2)
                .mapToLong(List::size)
                .sum();
        progress.toHash(toHash);

        for (List<OccurrenceId> sameSize : bySize.values()) {
            if (sameSize.size() < 2) {
                continue;
            }
            resolveGroupSharingASize(runId, canonicalRoot, sameSize, progress);
        }
    }

    private void resolveGroupSharingASize(
            RunId runId, Path canonicalRoot, List<OccurrenceId> sameSize, HashingProgress progress) throws Exception {
        Map<String, List<Candidate>> byHash = new HashMap<>();
        for (OccurrenceId occurrenceId : sameSize) {
            OccurrenceFacts facts = factsFor(occurrenceId);
            String sha256 = ContentHash.sha256(canonicalRoot.resolve(facts.path().value()));
            contentIdentity.recordHash(occurrenceId, runId, sha256);
            byHash.computeIfAbsent(sha256, ignored -> new ArrayList<>())
                    .add(new Candidate(occurrenceId, facts.path(), facts.creationTime()));
            progress.hashed(occurrenceId, sha256);
        }

        for (List<Candidate> sameHash : byHash.values()) {
            if (sameHash.size() < 2) {
                continue;
            }
            verdictSuperseded(runId, DuplicateResolution.resolve(sameHash), sameHash, progress);
        }
    }

    private void verdictSuperseded(
            RunId runId, DuplicateResolution.Resolution resolution, List<Candidate> group, HashingProgress progress) {
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
            progress.supersededRecorded();
        }
    }

    private OccurrenceFacts factsFor(OccurrenceId occurrenceId) {
        return ledger.factsFor(occurrenceId)
                .orElseThrow(() -> new IllegalStateException("no facts are recorded for occurrence " + occurrenceId.value()));
    }
}
