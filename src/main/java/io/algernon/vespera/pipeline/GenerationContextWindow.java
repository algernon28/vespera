package io.algernon.vespera.pipeline;

import io.algernon.vespera.profile.Profile;
import io.algernon.vespera.profile.ProfileStore;
import io.algernon.vespera.synthesis.ClusterSynthesis;
import org.springframework.stereotype.Component;

/**
 * How much of the model's window one of stage 6b's calls may work in (ADR-108, #182).
 *
 * <p>{@link GenerationModel}'s sibling and the same kind of key: <b>not a gate</b>. An unanswered key
 * resolves to the number the code ships with and the invocation carries on. Stopping for this one
 * would be worse than stopping for a model name — the right value is a property of the machine doing
 * the serving rather than of the archive, and an operator on their way to a first run has no way to
 * arrive at it.
 *
 * <p><b>The default is in code rather than in application configuration</b>, unlike the model name.
 * That name has to agree with the chat model bean's own configured name or the call and the bean would
 * name two different models; this number has no such twin, and what it is <em>for</em> — how much of a
 * group one call carries — is a decision {@code synthesis} owns and documents.
 *
 * <p><b>An answer that is not a positive whole number stops rather than being read past.</b> Unset and
 * wrong are different states: somebody who wrote something here meant to change how much gets read,
 * and quietly falling back would run their archive under a window they did not choose and never
 * mention it — the same reason a mistyped arrangement approval must not behave like an approval.
 */
@Component
class GenerationContextWindow {

    /** The key as it is written in the profile, named once so the refusal and the read cannot disagree. */
    static final String KEY = "generationContextWindow";

    private final ProfileStore profileStore;

    GenerationContextWindow(ProfileStore profileStore) {
        this.profileStore = profileStore;
    }

    /** The window this invocation generates under: what the operator wrote, and otherwise the default. */
    int size() {
        Profile profile = profileStore.load();
        if (!profile.generationContextWindow().isSet()) {
            return ClusterSynthesis.CONTEXT_WINDOW;
        }
        String written = profile.generationContextWindow().value().trim();
        int window;
        try {
            window = Integer.parseInt(written);
        } catch (NumberFormatException e) {
            throw new IllegalStateException(
                    KEY + " is \"" + written + "\", which is not a number of tokens; write a whole number"
                            + " of tokens or leave the key empty to use the shipped window",
                    e);
        }
        if (window <= 0) {
            throw new IllegalStateException(KEY + " is \"" + written
                    + "\", and a window has to be a positive number of tokens; leave the key empty to use"
                    + " the shipped window");
        }
        return window;
    }
}
