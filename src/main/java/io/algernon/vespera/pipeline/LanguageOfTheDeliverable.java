package io.algernon.vespera.pipeline;

import io.algernon.vespera.profile.ProfileStore;
import io.algernon.vespera.profile.TextValue;
import io.algernon.vespera.synthesis.DeliverableLanguage;
import org.springframework.stereotype.Component;

/**
 * The language the deliverable is written and drawn in (ADR-321 §1).
 *
 * <p>{@link GenerationContextWindow}'s sibling and the same kind of key: <b>not a gate</b>. An unset or blank key is
 * {@link DeliverableLanguage#DEFAULT}, and the invocation carries on. Any other answer that is not a language {@code
 * synthesis} holds stops: somebody who wrote a language meant to change what the readers are handed, and drawing
 * English instead would hand them a deliverable nobody chose.
 *
 * <p>It holds no rule in ADR-222's sense: which codes there are, their English names and the default are {@code
 * synthesis}'s, and this class only reads the key and hands the language over as a plain value.
 */
@Component
class LanguageOfTheDeliverable {

    /** The key as it is written in the profile, named once so the refusal and the read cannot disagree. */
    static final String KEY = "deliverableLanguage";

    private final ProfileStore profileStore;

    LanguageOfTheDeliverable(ProfileStore profileStore) {
        this.profileStore = profileStore;
    }

    /** The language this invocation draws the deliverable in: what the operator wrote, and otherwise English. */
    DeliverableLanguage language() {
        TextValue written = profileStore.load().deliverableLanguage();
        if (!written.isSet()) {
            return DeliverableLanguage.DEFAULT;
        }
        return DeliverableLanguage.ofCode(written.value())
                .orElseThrow(() -> new IllegalStateException(KEY + " is \"" + written.value() + "\", which is not a"
                        + " language the deliverable can be drawn in; write " + DeliverableLanguage.ENGLISH.code()
                        + " or " + DeliverableLanguage.ITALIAN.code() + ", or leave the key empty to mean "
                        + DeliverableLanguage.ENGLISH.code()));
    }
}
