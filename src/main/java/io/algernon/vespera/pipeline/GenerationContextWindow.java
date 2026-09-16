package io.algernon.vespera.pipeline;

import io.algernon.vespera.profile.NumericValue;
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
 *
 * <p><b>This is the one numeric key that stops, and ADR-120 left room for exactly one.</b> That record
 * makes every other numeric key tolerate an unreadable value, because each is a threshold whose unset
 * state is already legal — and says in as many words that it does not decide "whether an unreadable
 * value should ever stop a run", leaving it to a key that cannot tolerate one. This is that key: a
 * floor nobody set removes nothing, but a window nobody chose still gets used, and the archive is
 * written under it. So the reading comes from {@link NumericValue} like every other key's, and what
 * differs is only what this class does with the unreadable one.
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
        NumericValue written = profileStore.load().generationContextWindow();
        return switch (written.reading()) {
            case NumericValue.Unset ignored -> ClusterSynthesis.CONTEXT_WINDOW;
            case NumericValue.Unreadable unreadable -> throw notANumberOfTokens(unreadable.text());
            case NumericValue.Answered answered -> aWholeNumberOfTokens(answered.number(), written.value().trim());
        };
    }

    /**
     * The window as a count of tokens, or a stop.
     *
     * <p>These checks are about what a window <em>is</em> rather than about whether a number could be
     * read, which is why they live here and not in {@link NumericValue}: half a token is as unusable as
     * none, and the key's own meaning is the only thing that knows it.
     *
     * <p><b>Every check is against the {@code double}, and the narrowing happens only once they have
     * all passed.</b> A cast is the one operation here that cannot fail loudly: {@code (int)} of
     * anything past {@code Integer.MAX_VALUE} — including {@code Infinity}, which is whole and positive
     * and passes both of the obvious tests — saturates silently, so {@code "1e18"} would have generated
     * the archive under a window of two billion tokens that nobody chose and never said so. That is the
     * harm this class exists to prevent, and the finite-and-in-range check is what actually prevents it.
     *
     * @param window the number read from the key
     * @param written what the operator actually typed, which is what any refusal quotes back at them —
     *     a message built from the parsed number tells somebody who wrote {@code 4.0965e3} about
     *     {@code 4096.5}
     */
    private static int aWholeNumberOfTokens(double window, String written) {
        if (!Double.isFinite(window) || window > Integer.MAX_VALUE) {
            throw notANumberOfTokens(written);
        }
        if (window != Math.floor(window)) {
            throw new IllegalStateException(KEY + " is \"" + written
                    + "\", and a window is counted in whole tokens; leave the key empty to use the shipped"
                    + " window");
        }
        if (window <= 0) {
            throw new IllegalStateException(KEY + " is \"" + written
                    + "\", and a window has to be a positive number of tokens; leave the key empty to use"
                    + " the shipped window");
        }
        return (int) window;
    }

    /** The refusal for text no count of tokens can be read from, worded once for both ways in. */
    private static IllegalStateException notANumberOfTokens(String written) {
        return new IllegalStateException(
                KEY + " is \"" + written + "\", which is not a number of tokens; write a whole number of"
                        + " tokens or leave the key empty to use the shipped window");
    }
}
