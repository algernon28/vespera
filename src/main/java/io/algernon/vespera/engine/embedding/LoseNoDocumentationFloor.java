package io.algernon.vespera.engine.embedding;

import java.util.List;
import java.util.Optional;

/**
 * The relevance floor as a rule over the labels, and not a model (ADR-197 §4).
 *
 * <p>The floor is the lowest score among the labels that say relevant, so no document a person or a
 * model said to keep scores below it. A document scoring exactly the floor is kept, because the floor
 * removes only what scores below it. Whoever set a label, it counts; the scale is the one it was given
 * under, so a label given against another embedder is not read.
 */
public final class LoseNoDocumentationFloor {

    private LoseNoDocumentationFloor() {}

    /**
     * The lowest score among the labels that say relevant and were given under {@code scale}, the {@link
     * RelevanceScale} of the run (ADR-330 section 9). Empty where none does.
     */
    public static Optional<Double> over(List<RelevanceLabel> labels, String scale) {
        return labels.stream()
                .filter(RelevanceLabel::relevant)
                .filter(label -> label.embedderIdentity().equals(scale))
                .map(RelevanceLabel::scoreShown)
                .min(Double::compare);
    }
}
