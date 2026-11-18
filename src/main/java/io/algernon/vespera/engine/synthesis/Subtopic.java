package io.algernon.vespera.engine.synthesis;

import java.util.OptionalInt;

/**
 * How a row writes a cluster's subtopic: {@value #NONE} where its topic has no subtopics, and not {@code NULL}, which
 * a key holding it never makes unique in SQLite (ADR-409 section 3). A reader of the rows hands it on as an empty
 * {@link OptionalInt}.
 */
final class Subtopic {

    /** What a row holds where the topic has no subtopics. */
    static final int NONE = -1;

    private Subtopic() {}

    /** What a row holds for {@code subtopic}. */
    static int inRow(OptionalInt subtopic) {
        return subtopic.orElse(NONE);
    }

    /** The subtopic a row's value stands for. */
    static OptionalInt ofRow(int inRow) {
        return inRow == NONE ? OptionalInt.empty() : OptionalInt.of(inRow);
    }
}
