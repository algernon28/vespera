package io.algernon.vespera.engine.pipeline;

import io.algernon.vespera.engine.profile.ProfileStore;
import io.algernon.vespera.engine.profile.TopicsValue;
import java.util.Optional;

/**
 * The refusal at the start of {@code vespera run} of a profile whose {@code topics} holds a blank entry or a duplicate
 * (ADR-435 §3), beside {@link ModelsAtTheStart}'s: after it, and before the corpus root is locked, so that nothing is
 * walked, no run is minted and no container is started.
 *
 * <p>It reads the one key and nothing else, and the line it gives is {@link TopicsValue}'s, the reading being the
 * profile's own. A profile that cannot be read refuses nothing here and is left to the job's own refusal.
 */
class ListedTopicsAtTheStart {

    private final ProfileStore profileStore;

    ListedTopicsAtTheStart(ProfileStore profileStore) {
        this.profileStore = profileStore;
    }

    /** The line that ends the start of {@code vespera run}, or empty where the list is unset or holds no such entry. */
    Optional<String> refusal() {
        try {
            if (profileStore.load().topics().reading() instanceof TopicsValue.Refused refused) {
                return Optional.of(refused.line());
            }
        } catch (RuntimeException unreadable) {
            return Optional.empty();
        }
        return Optional.empty();
    }
}
