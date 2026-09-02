package io.algernon.vespera.corpus;

import io.algernon.vespera.ledger.OccurrenceId;
import io.algernon.vespera.ledger.OccurrencePath;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;

/**
 * Which occurrence in a content-identity group becomes the representative, and which are
 * superseded by it (ADR-069): the earliest-created member, ties broken by lexicographically-lowest
 * path. Pure over what the caller already knows about each member — no filesystem or database
 * access here, so a group's resolution is exactly this one comparison, never a second computation
 * that could disagree with it.
 */
public final class DuplicateResolution {

    private DuplicateResolution() {}

    /** One member of a content-identity group: what the representative choice is made from. */
    public record Candidate(OccurrenceId occurrenceId, OccurrencePath path, Instant creationTime) {}

    /** A group's outcome: the representative, and every other member, now superseded by it. */
    public record Resolution(OccurrenceId representative, List<OccurrenceId> superseded) {}

    /**
     * Resolves one content-identity group of two or more members sharing a size and a SHA-256.
     *
     * @throws IllegalArgumentException if given fewer than two candidates — a group of one has
     *     nothing to resolve, and calling this on one is a caller error, not an empty result
     */
    public static Resolution resolve(List<Candidate> group) {
        if (group.size() < 2) {
            throw new IllegalArgumentException("a content-identity group of fewer than two has nothing to resolve");
        }
        Candidate representative = group.stream()
                .min(Comparator.comparing(Candidate::creationTime).thenComparing(c -> c.path().value()))
                .orElseThrow();
        List<OccurrenceId> superseded = group.stream()
                .filter(candidate -> !candidate.equals(representative))
                .map(Candidate::occurrenceId)
                .toList();
        return new Resolution(representative.occurrenceId(), superseded);
    }
}
