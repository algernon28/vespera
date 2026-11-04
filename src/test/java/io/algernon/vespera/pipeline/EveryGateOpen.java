package io.algernon.vespera.pipeline;

import io.algernon.vespera.profile.Profile;
import io.algernon.vespera.profile.ProfileFixture;
import io.algernon.vespera.profile.ProfileStore;
import java.nio.file.Path;

/**
 * The profile a full-run test writes so that an invocation goes as far as it can without an approval: a
 * seed folder, stage 4's floor and an embedding model named, and the degenerate-output floor left as the
 * working directory already holds it (#678).
 *
 * <p>Each full-run class wrote this profile itself, with the same floor and the same model, under a
 * constant of its own; this is the one place they are written now.
 */
final class EveryGateOpen {

    /** A boilerplate floor of 1.0 opens stage 4's gate while marking no passage as boilerplate. */
    static final String BOILERPLATE_FLOOR = "1.0";

    /** The embedding model the scripted serving runtime answers for, so gate 3 and everything behind it open. */
    static final String EMBEDDING_MODEL = "qwen3-embedding:0.6b";

    private EveryGateOpen() {}

    /** Saves a profile naming {@code seeds}, the floor and the model: every gate up to the approval is open. */
    static void throughStageFive(ProfileStore store, Path seeds) {
        Profile held = store.load();
        store.save(ProfileFixture.profile()
                .seedFolder(seeds.toString(), "set by this test")
                .degenerateOutputConfidenceFloor(held.degenerateOutputConfidenceFloor())
                .boilerplateDocumentFrequencyFloor(BOILERPLATE_FLOOR, "set by this test, so stage 4's gate is open")
                .embeddingModel(EMBEDDING_MODEL, "set by this test, so gate 3 is open")
                .build());
    }

    /** Adds to the profile held the approval of {@code arrangement} and the model to write under. */
    static void approve(ProfileStore store, String arrangement, String model) {
        store.save(ProfileFixture.profileFrom(store.load())
                .arrangementApproved(arrangement, "read by this test")
                .generationModel(model, "set by this test")
                .build());
    }
}
