package io.algernon.vespera.deliverable;

import io.algernon.vespera.synthesis.ClusterFaultKind;
import java.util.List;

/**
 * What the page of a cluster nothing was written over says in place of writing (ADR-174, ADR-274 §1.4), copied out
 * of the record and not out of the code, so the code is held to the record and not the other way round.
 *
 * <p>Each is rendered prose for a reader of the tree, so it says <em>group</em> where every name here says cluster
 * (ADR-122), and names no kind of {@link ClusterFaultKind}. The page shows each in italics of HTML; the asterisks
 * that were Markdown's italics are gone with ADR-274.
 *
 * <p><b>{@link #forKind} is an exhaustive {@code switch} with no {@code default}</b>, so a fifth kind stops this
 * test tree compiling until the record gives it a sentence, the same guard ADR-174 asks of the code's own mapping.
 * Public, because the invocation tests of {@code pipeline} read the pages these sentences land on.
 */
public final class UnwrittenSentencesOnRecord {

    /** A question longer than the room, counted by the serving engine or refused by it as too long. */
    public static final String DOCUMENTS_CAME_TO_MORE_THAN_THE_ROOM = "Nothing was written over this group: its"
            + " documents came to more than the writing model was given room to read at once.";

    /** An answer that stopped at the length it was allowed. */
    public static final String THE_ANSWER_REACHED_ITS_LENGTH_LIMIT = "Nothing was written over this group: the"
            + " writing model's answer reached its length limit before it was finished, so it was not used.";

    /** An answer that could not be read into the shape the call imposed. */
    public static final String THE_ANSWER_CAME_BACK_IN_ANOTHER_SHAPE = "Nothing was written over this group: the"
            + " writing model's answer did not come back in the shape it was asked for, so it was not used.";

    /** Writing that cited outside the documents sent, or cited nothing. */
    public static final String THE_ANSWER_CITED_OUTSIDE_WHAT_IT_WAS_GIVEN = "Nothing was written over this group:"
            + " the writing model's answer cited documents it had not been given, or cited none, so it was"
            + " not used.";

    /** A cluster none of whose members this invocation could send (ADR-121's first route). */
    public static final String NO_DOCUMENT_COULD_BE_SENT = "Nothing was written over this group: none of its"
            + " documents could be read and sent to the writing model.";

    /** A cluster every sendable member of which the estimate found too long (ADR-121's second route). */
    public static final String NO_DOCUMENT_FITS_THE_ROOM = "Nothing was written over this group: each of its"
            + " documents that could be read was judged longer than the writing model was given room to"
            + " read at once, so none was sent.";

    /** A cluster after the five turned-down answers that stopped the step (ADR-111). */
    public static final String WRITING_STOPPED_BEFORE_IT = "Nothing has been written over this group yet: writing"
            + " stopped before it reached this group. Running the same command again carries the writing"
            + " on.";

    /** Every sentence ADR-174 fixes, in the order its table states them. */
    public static final List<String> EVERY_SENTENCE = List.of(
            DOCUMENTS_CAME_TO_MORE_THAN_THE_ROOM,
            THE_ANSWER_REACHED_ITS_LENGTH_LIMIT,
            THE_ANSWER_CAME_BACK_IN_ANOTHER_SHAPE,
            THE_ANSWER_CITED_OUTSIDE_WHAT_IT_WAS_GIVEN,
            NO_DOCUMENT_COULD_BE_SENT,
            NO_DOCUMENT_FITS_THE_ROOM,
            WRITING_STOPPED_BEFORE_IT);

    private UnwrittenSentencesOnRecord() {}

    /**
     * What the page of a cluster carrying a fault row says, by kind (ADR-174 §2). The {@code detail} plays no part:
     * it is never shown.
     */
    public static String forKind(ClusterFaultKind kind) {
        return switch (kind) {
            case PROMPT_EVALUATION_CEILING -> DOCUMENTS_CAME_TO_MORE_THAN_THE_ROOM;
            case ANSWER_RAN_OUT_OF_ROOM -> THE_ANSWER_REACHED_ITS_LENGTH_LIMIT;
            case SCHEMA_VIOLATION -> THE_ANSWER_CAME_BACK_IN_ANOTHER_SHAPE;
            case CITATION_NOT_IN_RANGE -> THE_ANSWER_CITED_OUTSIDE_WHAT_IT_WAS_GIVEN;
        };
    }
}
