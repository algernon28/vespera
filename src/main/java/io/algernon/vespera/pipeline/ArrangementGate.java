package io.algernon.vespera.pipeline;

import io.algernon.vespera.ledger.RunId;
import io.algernon.vespera.profile.Profile;
import io.algernon.vespera.profile.ProfileStore;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * The gate between arranging the documents and generating over them (ADR-107, narrowed by ADR-154
 * §2), and ADR-080's shape applied a fourth time: a shut gate ends the invocation with the job
 * succeeding, mints no run and removes nothing.
 *
 * <p><b>It holds a name rather than a yes.</b> An approval is of a <em>specific</em> arrangement: a
 * boolean never expires, so an operator who approved one shape of the corpus would go on shipping
 * every later shape having looked at nothing, and the gate would have become a switch flipped once.
 * Naming the run means a re-arrangement closes the gate again, which is the only property that makes
 * it worth building.
 *
 * <p><b>It is matched against one arrangement: the one this invocation made or continued.</b> Run rows
 * are never deleted and an unchanged archive keeps its walk (ADR-115), so an arrangement an earlier
 * approval named stays an arrangement of that walk after a later invocation has made a new one. Before
 * ADR-154 this was matched against every arrangement of the walk, which let a stale approval keep
 * opening the gate on an arrangement the documents were no longer in. It is never told the walk, and
 * never reaches {@link StageRuns#arrangement} itself: the caller passes the arrangement this invocation
 * arrived at, if it arrived at one, which is what keeps this class from minting one behind the
 * arrangement step's own gate (ADR-154, Context §3).
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

    ArrangementGate(ProfileStore profileStore) {
        this.profileStore = profileStore;
    }

    /** What the operator wrote, if anything — before it is known to name an arrangement. */
    Optional<String> approval() {
        Profile profile = profileStore.load();
        return profile.arrangementApproved().isSet()
                ? Optional.of(profile.arrangementApproved().value().trim())
                : Optional.empty();
    }

    /**
     * {@code thisInvocationsArrangement}, if the operator's approval names it as a prefix -- empty in
     * every other case: no arrangement this invocation, an approval naming nothing, and an approval
     * naming an arrangement this invocation did not arrive at (an older one, or a typo) are all one
     * outcome, a shut gate.
     *
     * <p>The prefix rule is ADR-107's, unchanged: the approval is compared as a prefix of the full id,
     * as {@code Ledger.runsMatching} compared it before this record. Only the set it is compared
     * against shrinks, from every arrangement of the walk to the one this invocation made or continued
     * -- so it can never match two, and there is nothing left to disambiguate.
     */
    Optional<RunId> approvedArrangement(Optional<RunId> thisInvocationsArrangement) {
        Optional<String> approval = approval();
        if (approval.isEmpty() || thisInvocationsArrangement.isEmpty()) {
            return Optional.empty();
        }
        RunId arrangement = thisInvocationsArrangement.get();
        return arrangement.value().startsWith(approval.get()) ? Optional.of(arrangement) : Optional.empty();
    }
}
