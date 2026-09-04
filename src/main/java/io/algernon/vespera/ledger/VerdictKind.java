package io.algernon.vespera.ledger;

/**
 * The closed verdict vocabulary: eight values, seven of them blocking (ADR-057). Owned by
 * {@code ledger} rather than by the cascade, so that one place decides what a verdict may say and
 * what it means for survival (ADR-042).
 *
 * <p>Adding a stage's verdict means adding a value here, in a pull request that carries an ADR. That
 * is the accepted cost of the decision, not a gap in it: an extension seam would reintroduce exactly
 * the opaque registry ADR-042 rejected, where the meaning of a verdict lives with whoever registered
 * it.
 */
public enum VerdictKind {

    /** Mechanically broken: unreadable, truncated, or not the format it claims to be. Stage 1. */
    BROKEN(true),

    /** A byte-identical copy of another occurrence, which was chosen as the representative. Stage 1. */
    DUPLICATE_OF(true),

    /** An older copy of content another occurrence carries a newer version of. Stage 1. */
    SUPERSEDED_BY(true),

    /** Extraction did not produce text. Stage 2. */
    EXTRACTION_FAILED(true),

    /** Extraction produced text carrying no usable content. Stage 3. */
    DEGENERATE_OUTPUT(true),

    /** The content is already carried by another surviving occurrence. Stage 4. */
    REDUNDANT_WITH(true),

    /** Relevance to the seed set scored below the threshold. Stage 5. */
    BELOW_THRESHOLD(true),

    /** The stage judged the occurrence and let it through. The one non-blocking value. */
    PASSED(false);

    private final boolean blocking;

    VerdictKind(boolean blocking) {
        this.blocking = blocking;
    }

    /**
     * Whether this verdict removes an occurrence from the survivor set. Survival is the absence of
     * any blocking verdict, never the presence of {@link #PASSED} (CONTEXT.md, "Survivor").
     */
    boolean blocking() {
        return blocking;
    }
}
