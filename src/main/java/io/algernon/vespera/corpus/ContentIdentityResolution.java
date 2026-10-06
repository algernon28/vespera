package io.algernon.vespera.corpus;

import io.algernon.vespera.corpus.DuplicateResolution.Candidate;
import io.algernon.vespera.ledger.Ledger;
import io.algernon.vespera.ledger.OccurrenceFacts;
import io.algernon.vespera.ledger.OccurrenceId;
import io.algernon.vespera.ledger.RunId;
import io.algernon.vespera.ledger.SizedOccurrence;
import io.algernon.vespera.ledger.VerdictKind;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.batch.infrastructure.item.ExecutionContext;
import org.springframework.batch.infrastructure.item.ItemStreamReader;

/**
 * Stage 1's second pass: content identity over what the first pass left (ADR-188, moved from
 * {@code pipeline}; reading one size at a time since ADR-200). Every occurrence still surviving is
 * grouped by size, hashed within any group of two or more, and every content-identity group resolves to
 * one representative, the rest verdicted {@code superseded-by} (ADR-067, ADR-069).
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
     * Reads the survivor set twice, in ascending size and never as a whole: the anti-join {@link
     * Ledger#survivorsBySize} always runs excludes what the first pass verdicted, so this pass's own
     * boundary needs no application-level filtering, and each read holds one size at a time (ADR-200).
     *
     * <p>The first read sizes every survivor and adds up what the second will hash, a size's members
     * counting only when two or more share it, so the hash pass's total is known before its first hash
     * (ADR-057, ADR-192). The second read resolves each size with more than one member.
     */
    public void resolve(RunId runId, Path canonicalRoot, HashingProgress progress) throws Exception {
        progress.toSize(ledger.survivorCount(runId));

        progress.toHash(sizeEverySurvivor(runId, progress));

        eachSize(runId, sameSize -> {
            if (sameSize.size() >= 2) {
                resolveGroupSharingASize(runId, canonicalRoot, sameSize, progress);
            }
        });
    }

    /**
     * The first read: reports each survivor as it is read and holds none, only how many have the size
     * in hand. Returns the hash pass's own denominator, and not the survivor count: a file whose size
     * is unique to it is never hashed at all (ADR-057), so counting it in would leave this pass
     * reporting a fraction of a total it will never reach.
     */
    private long sizeEverySurvivor(RunId runId, HashingProgress progress) throws Exception {
        long toHash = 0;
        ItemStreamReader<SizedOccurrence> survivors = ledger.survivorsBySize(runId);
        survivors.open(new ExecutionContext());
        try {
            long size = 0;
            long sharingIt = 0;
            for (SizedOccurrence next = survivors.read(); next != null; next = survivors.read()) {
                if (sharingIt > 0 && next.sizeBytes() != size) {
                    toHash += sharingIt >= 2 ? sharingIt : 0;
                    sharingIt = 0;
                }
                size = next.sizeBytes();
                sharingIt++;
                progress.sized();
            }
            toHash += sharingIt >= 2 ? sharingIt : 0;
        } finally {
            survivors.close();
        }
        return toHash;
    }

    /** What is done with every survivor of one size, once all of them have been read. */
    private interface SizeVisitor {
        void visit(List<OccurrenceId> sameSize) throws Exception;
    }

    /**
     * Reads the survivors by size and hands each size to {@code visitor} when its last member has been
     * read. The one survivor read past a size, to learn the size ended, starts the next, so no more than
     * one size and that one survivor are held.
     */
    private void eachSize(RunId runId, SizeVisitor visitor) throws Exception {
        ItemStreamReader<SizedOccurrence> survivors = ledger.survivorsBySize(runId);
        survivors.open(new ExecutionContext());
        try {
            List<OccurrenceId> sameSize = new ArrayList<>();
            long size = 0;
            for (SizedOccurrence next = survivors.read(); next != null; next = survivors.read()) {
                if (!sameSize.isEmpty() && next.sizeBytes() != size) {
                    visitor.visit(sameSize);
                    sameSize = new ArrayList<>();
                }
                size = next.sizeBytes();
                sameSize.add(next.occurrenceId());
            }
            if (!sameSize.isEmpty()) {
                visitor.visit(sameSize);
            }
        } finally {
            survivors.close();
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
