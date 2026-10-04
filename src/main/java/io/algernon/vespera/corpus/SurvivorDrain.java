package io.algernon.vespera.corpus;

import io.algernon.vespera.ledger.OccurrenceId;
import java.util.ArrayList;
import java.util.List;
import org.springframework.batch.infrastructure.item.ExecutionContext;
import org.springframework.batch.infrastructure.item.ItemStreamReader;

/** Reads a survivors reader to exhaustion, since both of stage 1's passes need the whole set, not one chunk. */
final class SurvivorDrain {

    private SurvivorDrain() {}

    static List<OccurrenceId> drain(ItemStreamReader<OccurrenceId> reader) throws Exception {
        List<OccurrenceId> read = new ArrayList<>();
        reader.open(new ExecutionContext());
        try {
            for (OccurrenceId id = reader.read(); id != null; id = reader.read()) {
                read.add(id);
            }
        } finally {
            reader.close();
        }
        return read;
    }
}
