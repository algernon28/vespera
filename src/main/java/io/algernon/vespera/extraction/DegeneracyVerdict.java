package io.algernon.vespera.extraction;

/**
 * Whether a converted document earns {@code degenerate-output} (ADR-070), and why — the judgement
 * {@code pipeline} turns into a verdict row, or discards when {@link #degenerate()} is {@code false}
 * (survival is the absence of a blocking verdict, never a written {@code passed} row).
 */
public record DegeneracyVerdict(boolean degenerate, String reason) {

    private static final DegeneracyVerdict NOT_DEGENERATE = new DegeneracyVerdict(false, null);

    /** A document that cleared both tiers of the floor. */
    public static DegeneracyVerdict notDegenerate() {
        return NOT_DEGENERATE;
    }
}
