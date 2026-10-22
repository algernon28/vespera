package io.algernon.vespera.deliverable;

import io.algernon.vespera.synthesis.Unwritten;
import java.util.Optional;

/**
 * The line a cluster's page carries in place of the writing, by the reason nothing was written (ADR-174, ADR-274
 * §1.4): the same words as before, without the asterisks that were Markdown's, shown in italics of HTML by the page.
 *
 * <p>Each says <em>group</em> where the code says cluster (ADR-122), names no kind of fault, and describes one run
 * only, so none says the gap is for good: the next invocation that reaches the writing rewrites the page.
 */
final class UnwrittenSentences {

    /**
     * The line beneath the reason on the page of a cluster whose answer was turned down each time it was asked, in a
     * run whose writing is finished (ADR-319 §5.1): it says group, and none of permanent, never or always, and
     * describes this run alone.
     */
    private static final String STANDS_WITHOUT_WRITING = "Its answer was turned down each time it was asked, so this"
            + " group stands without writing; running the same command again asks nothing more about it.";

    private UnwrittenSentences() {}

    /**
     * The line saying the group stands, for a reason that is a fault on record in a run whose generation step is
     * recorded finished, or being recorded by the invocation drawing it; empty otherwise (ADR-319 §5.1). In a finished
     * run every fault standing is a repeated fault, so every such page may say it.
     */
    static Optional<String> standing(Unwritten why, boolean writingFinished) {
        if (!writingFinished || why == null) {
            return Optional.empty();
        }
        return switch (why) {
            case FAULT_PROMPT_EVALUATION_CEILING,
                    FAULT_ANSWER_RAN_OUT_OF_ROOM,
                    FAULT_SCHEMA_VIOLATION,
                    FAULT_CITATION_NOT_IN_RANGE -> Optional.of(STANDS_WITHOUT_WRITING);
            case NO_SENDABLE_DOCUMENT, NOTHING_FITS_THE_WINDOW, NOT_REACHED -> Optional.empty();
        };
    }

    /** The sentence for {@code why}; an exhaustive {@code switch}, so a new reason stops this compiling. */
    static String of(Unwritten why) {
        return switch (why) {
            case FAULT_PROMPT_EVALUATION_CEILING -> "Nothing was written over this group: its documents came to more"
                    + " than the writing model was given room to read at once.";
            case FAULT_ANSWER_RAN_OUT_OF_ROOM -> "Nothing was written over this group: the writing model's answer"
                    + " reached its length limit before it was finished, so it was not used.";
            case FAULT_SCHEMA_VIOLATION -> "Nothing was written over this group: the writing model's answer did not"
                    + " come back in the shape it was asked for, so it was not used.";
            case FAULT_CITATION_NOT_IN_RANGE -> "Nothing was written over this group: the writing model's answer cited"
                    + " documents it had not been given, or cited none, so it was not used.";
            case NO_SENDABLE_DOCUMENT -> "Nothing was written over this group: none of its documents could be read"
                    + " and sent to the writing model.";
            case NOTHING_FITS_THE_WINDOW -> "Nothing was written over this group: each of its documents that could be"
                    + " read was judged longer than the writing model was given room to read at once, so none was sent.";
            case NOT_REACHED -> "Nothing has been written over this group yet: writing stopped before it reached this"
                    + " group. Running the same command again carries the writing on.";
        };
    }
}
