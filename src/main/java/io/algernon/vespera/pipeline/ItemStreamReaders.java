package io.algernon.vespera.pipeline;

import io.algernon.vespera.ledger.OccurrenceId;
import java.util.HashSet;
import java.util.Set;
import org.springframework.batch.infrastructure.item.ExecutionContext;
import org.springframework.batch.infrastructure.item.ItemStreamReader;

/**
 * Drains an {@link ItemStreamReader} of occurrence ids into a plain set, opening and closing it
 * around the read — the one piece both {@link EmbeddingScoringTasklet} and {@link
 * RelevanceScoringTasklet} need to turn {@link io.algernon.vespera.ledger.Ledger#survivors} and
 * {@link io.algernon.vespera.ledger.Ledger#occurrencesOf} into something a tasklet can iterate more
 * than once.
 */
final class ItemStreamReaders {

    private ItemStreamReaders() {
    }

    static Set<OccurrenceId> drain(ItemStreamReader<OccurrenceId> reader) {
        Set<OccurrenceId> ids = new HashSet<>();
        try {
            reader.open(new ExecutionContext());
            try {
                for (OccurrenceId id = reader.read(); id != null; id = reader.read()) {
                    ids.add(id);
                }
            } finally {
                reader.close();
            }
        } catch (Exception e) {
            throw new IllegalStateException("could not read stage 5's corpus survivors", e);
        }
        return ids;
    }
}
