package io.algernon.vespera.extraction;

import io.algernon.vespera.ledger.Ledger;
import io.algernon.vespera.ledger.OccurrenceId;
import io.algernon.vespera.ledger.OccurrencePath;
import io.algernon.vespera.ledger.RunId;
import io.algernon.vespera.ledger.WalkId;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * File occurrences of one walk and one stage-2 run over it, in the test database, for the classes that
 * judge answers in {@code extraction}: a metric row needs an occurrence and a run that exist, and its
 * key is the two together, so each answer that is measured needs an occurrence of its own.
 *
 * <p>Nothing here walks a folder or runs stage 1. The rules under test read a response and the counts,
 * never a file, so the occurrences are recorded straight into the ledger, as {@code ExtractionFaultsTest}
 * records its own.
 */
final class OccurrencesUnderARun {

    private final List<OccurrenceId> occurrences;
    private final RunId run;

    private OccurrencesUnderARun(List<OccurrenceId> occurrences, RunId run) {
        this.occurrences = occurrences;
        this.run = run;
    }

    /** {@code count} occurrences, recorded under paths {@code corpus/occurrence-<i>.txt}, and a run over them. */
    static OccurrencesUnderARun of(JdbcTemplate jdbcTemplate, int count) {
        Ledger ledger = new Ledger(jdbcTemplate);
        WalkId walkId = ledger.startWalk(Path.of("C:/corpus-" + System.nanoTime()));
        List<OccurrenceId> occurrences = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            OccurrencePath path = new OccurrencePath(pathOf(i));
            ledger.fileOccurrence(
                    walkId, path, 1, Instant.parse("2026-10-04T10:15:30Z"), Instant.parse("2026-10-01T08:00:00Z"));
            occurrences.add(ledger.occurrenceId(walkId, path).orElseThrow());
        }
        RunId run = ledger.startRun("extraction", "judging-" + System.nanoTime(), "{}", walkId, List.of());
        return new OccurrencesUnderARun(List.copyOf(occurrences), run);
    }

    /** The path the {@code index}th occurrence is recorded under. */
    static String pathOf(int index) {
        return "corpus/occurrence-" + index + ".txt";
    }

    OccurrenceId get(int index) {
        return occurrences.get(index);
    }

    int size() {
        return occurrences.size();
    }

    RunId run() {
        return run;
    }
}
