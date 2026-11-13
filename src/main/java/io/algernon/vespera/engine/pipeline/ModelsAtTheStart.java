package io.algernon.vespera.engine.pipeline;

import io.algernon.vespera.engine.profile.Profile;
import io.algernon.vespera.engine.profile.ProfileStore;
import io.algernon.vespera.engine.profile.TextValue;
import java.util.Locale;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * The refusal at the start of {@code vespera run} and of {@code vespera label --auto} of a profile whose
 * {@code modelsRunOn} is not {@code local} (ADR-384 §1 to §3).
 *
 * <p>It reads the one key and nothing else: no model's name is read and Ollama is asked nothing. Unset or blank is
 * local, as is {@code local} in any case with the blanks around it ignored; {@code cloud} is not supported yet, and any
 * other value names neither. A profile that cannot be read refuses nothing here and is left to the job's own refusal.
 */
@Component
class ModelsAtTheStart {

    private static final String LOCAL = "local";
    private static final String CLOUD = "cloud";
    private static final String ADVICE = "set modelsRunOn to local in profile.yaml, or leave it unset, and the models"
            + " run on this machine";

    private final ProfileStore profileStore;

    @Autowired
    ModelsAtTheStart(ProfileStore profileStore) {
        this.profileStore = profileStore;
    }

    /** The line that ends the start of {@code vespera run} (ADR-384 §2), or empty where the models are local. */
    Optional<String> refusal() {
        return reason().map(why -> "vespera run stopped at its start: " + why
                + ", so nothing was walked and no run was minted -- " + ADVICE);
    }

    /** The line that ends {@code vespera label --auto} at its start (ADR-384 §3), or empty where the models are local. */
    Optional<String> refusalOfLabelAuto() {
        return reason().map(why -> "vespera label --auto recorded nothing: " + why + " -- " + ADVICE);
    }

    /** What is wrong with the key, in the words both lines share, or empty where it is unset or local. */
    private Optional<String> reason() {
        String stated;
        try {
            Profile profile = profileStore.load();
            TextValue where = profile.modelsRunOn();
            stated = where == null || where.value() == null ? "" : where.value().strip();
        } catch (RuntimeException unreadable) {
            return Optional.empty();
        }
        String read = stated.toLowerCase(Locale.ROOT);
        if (read.isEmpty() || read.equals(LOCAL)) {
            return Optional.empty();
        }
        if (read.equals(CLOUD)) {
            return Optional.of("modelsRunOn is cloud, and models that run in the cloud are not supported yet");
        }
        return Optional.of("modelsRunOn is \"" + stated + "\", which is neither local nor cloud");
    }
}
