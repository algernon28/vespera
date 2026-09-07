package io.algernon.vespera.extraction;

/**
 * Stage 2's tier-1 bar, exposed for the one other caller entitled to ask it: whether a document
 * produced any text at all (ADR-070, ADR-083).
 *
 * <p>This exists so the seed set's usability bar and the corpus's {@code degenerate-output} tier 1
 * are the same rule rather than two implementations of the same sentence. ADR-083 fixes the bar as
 * "stage 2's tier 1 exactly — no alphanumeric content at all" and <b>no stricter</b>, since a
 * confidence threshold for seeds is one nobody has measured; a second copy of the predicate is
 * exactly how a "no stricter" instruction drifts into a slightly different one.
 *
 * <p>Public where {@link TextMetrics} and {@link DegeneracyFloor} stay package-private, because the
 * seed pass lives in {@code pipeline} and a capability module may not be reached into. The whole
 * interface is one question, so nothing about the normalisation rule leaks with it.
 */
public final class UsableText {

    private UsableText() {}

    /**
     * Whether {@code text} carries any letter or digit once whitespace is collapsed — so empty,
     * whitespace-only and punctuation-only text all read the same way, which is the property tier 1
     * was written for.
     */
    public static boolean hasAlphanumericContent(String text) {
        return TextMetrics.alphanumericCharacterCount(TextMetrics.normalizeWhitespace(text)) > 0;
    }
}
