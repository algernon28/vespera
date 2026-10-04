package io.algernon.vespera.extraction;

import java.util.List;

/** Where one count of failures in a row stands after an event (ADR-184 section 2). */
public sealed interface InARow {

    /** How long the row is. */
    int inARow();

    /** Below its threshold: nothing is sent. */
    record Counted(int inARow) implements InARow {}

    /** Set-asides only: due, and nothing sent yet. */
    record Reached(int inARow) implements InARow {}

    /** The control conversion was sent and converted: both rows start again. */
    record ControlConverted(int inARow) implements InARow {}

    /**
     * The control conversion was sent and did not convert: stop. {@code recordedPaths} are the files of a
     * dropped-twice row, in the order they were added, and empty for a set-aside row.
     */
    record ControlNotConverted(int inARow, ControlReading reading, List<String> recordedPaths) implements InARow {
        public ControlNotConverted {
            recordedPaths = List.copyOf(recordedPaths);
        }
    }
}
