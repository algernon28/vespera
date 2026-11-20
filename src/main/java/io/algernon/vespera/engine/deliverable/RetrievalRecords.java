package io.algernon.vespera.engine.deliverable;

import java.io.IOException;

/**
 * One plugin's reading of the retrieval records of the tree being drawn (ADR-434 §4), a seam beside {@link Drawing}:
 * each record is handed as it is composed, in the tree's order, and the plugin holds none.
 */
public interface RetrievalRecords extends AutoCloseable {

    /** Whether member passages are handed to it; where it says no, none is handed, and none is composed for it alone. */
    boolean takesMemberPassages();

    /** One retrieval record, in the order of ADR-434 §5. */
    void take(RetrievalRecord record) throws IOException;

    /** Every topic is gone through. */
    void finish() throws IOException;

    @Override
    void close() throws IOException;
}
