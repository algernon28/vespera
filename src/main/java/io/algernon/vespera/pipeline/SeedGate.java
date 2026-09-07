package io.algernon.vespera.pipeline;

import io.algernon.vespera.corpus.Walk;
import io.algernon.vespera.ledger.Ledger;
import io.algernon.vespera.ledger.WalkId;
import io.algernon.vespera.profile.Profile;
import io.algernon.vespera.profile.ProfileStore;
import java.nio.file.Path;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Stage 5's first gate (ADR-064): whether an operator has named a seed folder, and whether census has
 * finished walking it.
 *
 * <p>{@code embedding} may depend only on {@code ledger} (ADR-040), so the profile is read here
 * rather than there — the shape {@link RedundancyGate} and {@link DegenerateOutputConfidenceFloor}
 * already use for stage 4's and stage 2's keys.
 *
 * <p>Checked before anything stage 5 does, and before {@link SeedMeasurementRun} mints a row,
 * because a run that did nothing should not exist in the {@code run} table (ADR-080).
 *
 * <p>An <em>unfinished</em> walk closes the gate just as an unset key does, and for a reason worth
 * stating: only a finished walk is eligible as run input, because a partial one may hold fewer
 * occurrences than the folder holds files. A seed set quietly missing entries scores relevance
 * against the wrong set, silently, for every stage downstream (ADR-064's own reasoning for why a
 * seed walk's reconciliation failure is fatal).
 *
 * <p><b>Stage 4's own gate closes this one too</b>, which is not an extra rule so much as ADR-089
 * read forwards. Stage 5's measurement run names stage 4's run upstream, and {@code
 * run_upstream.upstream_run_id} is a foreign key — so while the boilerplate floor is unset there is
 * no stage-4 run row for stage 5 to point at, and a stage-5 run could only exist by lying about its
 * ancestry. It is also what the delivered sequence already says out loud: the invocation that sets
 * the boilerplate floor is the one that extracts the seed set (#98's step 2).
 */
@Component
class SeedGate {

    private final ProfileStore profileStore;
    private final Ledger ledger;
    private final RedundancyGate redundancyGate;

    SeedGate(ProfileStore profileStore, Ledger ledger, RedundancyGate redundancyGate) {
        this.profileStore = profileStore;
        this.ledger = ledger;
        this.redundancyGate = redundancyGate;
    }

    /**
     * The finished walk of the seed folder the operator named — empty while the gate stays closed,
     * whether because nobody has named a folder, because the name is not a path at all, or because no
     * finished walk of it exists.
     *
     * <p>A mistyped seed path resolves to no finished walk and closes the gate rather than raising:
     * census already recorded why it could not be walked, in the profile and in the log, and that is
     * where an operator reads it (ADR-064). Raising here would report the same typo a second time as
     * a failed invocation.
     */
    Optional<SeedWalk> seedWalk() {
        if (redundancyGate.floor().isEmpty()) {
            return Optional.empty();
        }
        Profile profile = profileStore.load();
        if (!profile.seedFolder().isSet()) {
            return Optional.empty();
        }
        Path canonicalSeedFolder;
        try {
            canonicalSeedFolder = Walk.canonicalRoot(Path.of(profile.seedFolder().value()));
        } catch (IllegalArgumentException cannotBeResolved) {
            // InvalidPathException is itself an IllegalArgumentException, so one catch covers both a
            // name that is not a path at all and one that resolves to nothing.
            // A name that is not a path, or names nothing that is now a directory. Census already
            // recorded why it could not walk it, so this is not a second report of the same typo.
            return Optional.empty();
        }
        return ledger.finishedWalkFor(canonicalSeedFolder)
                .map(walkId -> new SeedWalk(walkId, canonicalSeedFolder));
    }

    /**
     * The seed folder's finished walk, and the root to resolve its occurrence paths against.
     *
     * <p>Both together because an occurrence path is stored relative to the root it was walked from
     * (ADR-051), so the walk id alone cannot reach a file.
     */
    record SeedWalk(WalkId walkId, Path canonicalRoot) {}
}
