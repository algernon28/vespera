package io.algernon.vespera.synthesis;

/**
 * Why nothing was written over a cluster, as the line its own page carries in place of the writing
 * (ADR-174).
 *
 * <p>Each {@link #sentence()} is prose for a reader of the tree, who has no database to look a reason
 * up in (ADR-103): it says <em>group</em> where the code says cluster and names no
 * {@link ClusterFaultKind} (ADR-122), and it never shows a fault's {@code detail}. Each describes what
 * happened in one run and nothing more, so no sentence says the gap is for good: the next invocation
 * that reaches the writing rewrites the page, and a repaired cluster simply carries its writing
 * (ADR-111).
 */
public enum Unwritten {
    /** The question came to more than the window, by the engine's count or by its refusal. */
    FAULT_PROMPT_EVALUATION_CEILING("*Nothing was written over this group: its documents came to more than the"
            + " writing model was given room to read at once.*"),
    /** The answer stopped at the length it was allowed. */
    FAULT_ANSWER_RAN_OUT_OF_ROOM("*Nothing was written over this group: the writing model's answer reached its"
            + " length limit before it was finished, so it was not used.*"),
    /** The answer could not be read into the shape the call imposed. */
    FAULT_SCHEMA_VIOLATION("*Nothing was written over this group: the writing model's answer did not come back"
            + " in the shape it was asked for, so it was not used.*"),
    /** The writing cited outside the documents sent, or cited nothing. */
    FAULT_CITATION_NOT_IN_RANGE("*Nothing was written over this group: the writing model's answer cited"
            + " documents it had not been given, or cited none, so it was not used.*"),
    /** None of the cluster's members could be sent (ADR-121's first route). */
    NO_SENDABLE_DOCUMENT("*Nothing was written over this group: none of its documents could be read and sent"
            + " to the writing model.*"),
    /** Every sendable member was estimated too long for the window (ADR-121's second route). */
    NOTHING_FITS_THE_WINDOW("*Nothing was written over this group: each of its documents that could be read"
            + " was judged longer than the writing model was given room to read at once, so none was sent.*"),
    /** The step stopped after five turned-down answers before reaching this cluster (ADR-111). */
    NOT_REACHED("*Nothing has been written over this group yet: writing stopped before it reached this group."
            + " Running the same command again carries the writing on.*");

    private final String sentence;

    Unwritten(String sentence) {
        this.sentence = sentence;
    }

    /** The line the cluster's page carries under its heading, where the writing would stand. */
    public String sentence() {
        return sentence;
    }

    /**
     * Why a cluster without a synthesis doc went unwritten, for its page to say (ADR-174 §4, ADR-226).
     *
     * <p><b>What this invocation found wins over a stored fault row.</b> A row an earlier invocation
     * kept stands until an answer is believed (ADR-111), but if this invocation could send nothing for
     * the cluster at all, that is why it is unwritten now, and a page naming the old answer's reason
     * would have the reader expect a re-run to help. The fault on record is asked for only where this
     * invocation found nothing. A cluster with neither, and no doc, was never reached: the step stopped
     * after five turned-down answers before it.
     */
    public static Unwritten of(
            java.util.Optional<Unwritten> foundThisRun,
            java.util.function.Supplier<java.util.Optional<ClusterFault>> faultOnRecord) {
        if (foundThisRun.isPresent()) {
            return foundThisRun.get();
        }
        return faultOnRecord.get().map(fault -> of(fault.kind())).orElse(NOT_REACHED);
    }

    /**
     * The case a stored fault row stands for. An exhaustive {@code switch} with no {@code default}, so
     * a fifth kind of fault stops this compiling until the record gives it a sentence (ADR-174).
     */
    public static Unwritten of(ClusterFaultKind kind) {
        return switch (kind) {
            case PROMPT_EVALUATION_CEILING -> FAULT_PROMPT_EVALUATION_CEILING;
            case ANSWER_RAN_OUT_OF_ROOM -> FAULT_ANSWER_RAN_OUT_OF_ROOM;
            case SCHEMA_VIOLATION -> FAULT_SCHEMA_VIOLATION;
            case CITATION_NOT_IN_RANGE -> FAULT_CITATION_NOT_IN_RANGE;
        };
    }
}
