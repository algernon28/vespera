package io.algernon.vespera.pipeline;

import io.algernon.vespera.ledger.Ledger;
import io.algernon.vespera.ledger.RunId;
import io.algernon.vespera.ledger.WalkId;
import io.algernon.vespera.profile.Profile;
import io.algernon.vespera.profile.ProfileStore;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * The gate between arranging the documents and generating over them (ADR-107), and ADR-080's shape
 * applied a fourth time: a shut gate ends the invocation with the job succeeding, mints no run and
 * removes nothing.
 *
 * <p><b>It holds a name rather than a yes.</b> An approval is of a <em>specific</em> arrangement: a
 * boolean never expires, so an operator who approved one shape of the corpus would go on shipping
 * every later shape having looked at nothing, and the gate would have become a switch flipped once.
 * Naming the run means a re-arrangement closes the gate again, which is the only property that makes
 * it worth building.
 *
 * <p>{@code synthesis} may depend only on {@code ledger} (ADR-040), so the profile is read here, the
 * shape {@link EmbeddingModelGate}, {@link SeedGate} and {@link RedundancyGate} already use.
 */
@Component
class ArrangementGate {

    /**
     * How much of the arrangement's id the operator copies (ADR-107) — the whole of the concession to
     * typing. Forty-eight bits is far past collision risk at any corpus size, and the ledger keeps the
     * whole id regardless.
     */
    static final int APPROVAL_LENGTH = 12;

    /**
     * How an arrangement is named wherever a person has to read or type it — on the page, in the
     * closing line, and in {@code profile.yaml}. One method, so the three cannot drift into naming one
     * arrangement three ways.
     */
    static String shortNameOf(RunId arrangement) {
        return arrangement.value().substring(0, APPROVAL_LENGTH);
    }

    private final ProfileStore profileStore;
    private final Ledger ledger;

    ArrangementGate(ProfileStore profileStore, Ledger ledger) {
        this.profileStore = profileStore;
        this.ledger = ledger;
    }

    /** What the operator wrote, if anything — before it is known to name an arrangement. */
    Optional<String> approval() {
        Profile profile = profileStore.load();
        return profile.arrangementApproved().isSet()
                ? Optional.of(profile.arrangementApproved().value().trim())
                : Optional.empty();
    }

    /**
     * The arrangement of {@code walkId} the operator approved, if their approval names exactly one.
     *
     * <p>Empty covers both shut states — nothing written, and something written that names no
     * arrangement. They are one outcome on purpose: in each case no arrangement has been approved, and
     * the step that reads this says so rather than proceeding.
     *
     * @throws AmbiguousArrangementException if the approval names two arrangements of this corpus
     */
    Optional<RunId> approvedArrangement(WalkId walkId) {
        Optional<String> approval = approval();
        if (approval.isEmpty()) {
            return Optional.empty();
        }
        List<RunId> candidates = ledger.runsMatching(ArrangementRun.STAGE, walkId, approval.get());
        if (candidates.size() > 1) {
            throw new AmbiguousArrangementException(approval.get(), candidates);
        }
        return candidates.stream().findFirst();
    }
}
