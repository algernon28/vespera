package io.algernon.vespera.extraction;

import io.algernon.vespera.corpus.DetectedFormat;
import io.algernon.vespera.ledger.Ledger;
import io.algernon.vespera.ledger.OccurrenceId;
import io.algernon.vespera.ledger.RunId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * The words of stage 2's survivors, by detected format, and what each candidate word floor would remove
 * (ADR-282 section 4): the measure the operator reads before writing {@code degenerateOutputWordFloor}.
 *
 * <p>Reads {@code extraction_metric} only. The detected format is {@code corpus}'s table and is asked of
 * the caller, a page of occurrences at a time, so that no table of {@code corpus} is named here (ADR-209).
 * Made a page of stage 2's survivors at a time, and that page's {@code word_count}s by key: no set of the
 * survivors and no list of word counts is held (ADR-211, ADR-220).
 */
@Component
public class WordCountDistribution {

    /** One band of words, {@code [fewestWords, mostWords]}; the last has {@link Long#MAX_VALUE} for its most. */
    public record Band(long fewestWords, long mostWords) {}

    /** The fourteen bands, in ascending order. */
    public static final List<Band> BANDS = List.of(
            new Band(0, 1),
            new Band(2, 2),
            new Band(3, 3),
            new Band(4, 4),
            new Band(5, 5),
            new Band(6, 6),
            new Band(7, 7),
            new Band(8, 8),
            new Band(9, 9),
            new Band(10, 10),
            new Band(11, 19),
            new Band(20, 49),
            new Band(50, 99),
            new Band(100, Long.MAX_VALUE));

    /** The floors whose cost is shown: each is the lower edge of a band in {@link #BANDS}. */
    public static final List<Long> CANDIDATE_FLOORS = List.of(2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L, 20L, 50L, 100L);

    private final JdbcTemplate jdbcTemplate;
    private final Ledger ledger;

    public WordCountDistribution(JdbcTemplate jdbcTemplate, Ledger ledger) {
        this.jdbcTemplate = jdbcTemplate;
        this.ledger = ledger;
    }

    /**
     * Counts {@code extractionRunId}'s survivors that carry a metrics row under it, each once, in the band its
     * {@code word_count} falls in, under the format {@code formatsOf} gives it. {@code progress} is told of
     * the read of the metrics as {@link ConfidenceDistribution#measure} tells it.
     *
     * @throws IllegalStateException if a counted survivor is given no format by {@code formatsOf}
     */
    public Distribution measure(
            RunId extractionRunId,
            Function<List<OccurrenceId>, Map<OccurrenceId, DetectedFormat>> formatsOf,
            ExtractionStatementProgress progress) {
        Map<DetectedFormat, long[]> counts = new EnumMap<>(DetectedFormat.class);
        progress.statementStarting(
                ExtractionStatement.EXTRACTION_METRICS,
                ExtractionMetrics.metricRowsUpTo(jdbcTemplate, extractionRunId));
        long[] rowsRead = {0};
        ledger.verdicts().eachPageOfSurvivors(extractionRunId, page -> {
            rowsRead[0] += countWordsOf(extractionRunId, page, formatsOf.apply(page), counts);
            progress.rowsRead(ExtractionStatement.EXTRACTION_METRICS, rowsRead[0]);
        });
        progress.statementEnded(ExtractionStatement.EXTRACTION_METRICS);

        Map<DetectedFormat, List<Long>> byFormat = new EnumMap<>(DetectedFormat.class);
        counts.forEach((format, perBand) ->
                byFormat.put(format, Arrays.stream(perBand).boxed().toList()));
        return new Distribution(byFormat);
    }

    private long countWordsOf(
            RunId extractionRunId,
            List<OccurrenceId> page,
            Map<OccurrenceId, DetectedFormat> formats,
            Map<DetectedFormat, long[]> counts) {
        String placeholders = page.stream().map(id -> "?").collect(Collectors.joining(", "));
        List<Object> arguments = new ArrayList<>();
        arguments.add(extractionRunId.value());
        page.forEach(id -> arguments.add(id.value()));
        long[] rows = {0};
        jdbcTemplate.query(
                "SELECT occurrence_id, word_count FROM extraction_metric"
                        + " WHERE run_id = ? AND occurrence_id IN (" + placeholders + ")",
                resultSet -> {
                    rows[0]++;
                    OccurrenceId occurrence = new OccurrenceId(resultSet.getLong("occurrence_id"));
                    DetectedFormat format = formats.get(occurrence);
                    if (format == null) {
                        throw new IllegalStateException("no detected format was given for occurrence "
                                + occurrence.value() + ", a survivor of run " + extractionRunId.value());
                    }
                    counts.computeIfAbsent(format, key -> new long[BANDS.size()])[bandOf(resultSet.getLong("word_count"))]++;
                },
                arguments.toArray());
        return rows[0];
    }

    private static int bandOf(long words) {
        for (int index = 0; index < BANDS.size(); index++) {
            if (words <= BANDS.get(index).mostWords()) {
                return index;
            }
        }
        throw new IllegalStateException("no band holds " + words + " words");
    }

    /**
     * For each detected format with at least one survivor counted, one count per band in {@link #BANDS}' order.
     *
     * @throws IllegalArgumentException if a format's list is not one count for each band
     */
    public record Distribution(Map<DetectedFormat, List<Long>> survivorsByFormat) {

        public Distribution {
            EnumMap<DetectedFormat, List<Long>> copy = new EnumMap<>(DetectedFormat.class);
            survivorsByFormat.forEach((format, perBand) -> {
                if (perBand.size() != BANDS.size()) {
                    throw new IllegalArgumentException(
                            format + " holds " + perBand.size() + " counts, not one for each of the " + BANDS.size()
                                    + " bands");
                }
                copy.put(format, List.copyOf(perBand));
            });
            survivorsByFormat = copy;
        }

        /** The formats with a survivor counted, in {@link DetectedFormat}'s declaration order. */
        public List<DetectedFormat> formats() {
            return List.copyOf(survivorsByFormat.keySet());
        }

        /** The survivors of {@code format} in {@code band}; zero for a format with none counted. */
        public long survivors(DetectedFormat format, Band band) {
            List<Long> perBand = survivorsByFormat.get(format);
            return perBand == null ? 0 : perBand.get(indexOf(band));
        }

        /** The survivors in {@code band}, over every format. */
        public long survivors(Band band) {
            return survivorsByFormat.keySet().stream()
                    .mapToLong(format -> survivors(format, band))
                    .sum();
        }

        /**
         * How many survivors of {@code format} hold fewer words than {@code floor}.
         *
         * @throws IllegalArgumentException if {@code floor} is not in {@link #CANDIDATE_FLOORS}
         */
        public long removedBy(long floor, DetectedFormat format) {
            if (!CANDIDATE_FLOORS.contains(floor)) {
                throw new IllegalArgumentException(floor + " is not among the candidate floors " + CANDIDATE_FLOORS);
            }
            List<Long> perBand = survivorsByFormat.get(format);
            if (perBand == null) {
                return 0;
            }
            long removed = 0;
            for (int index = 0; index < BANDS.size() && BANDS.get(index).mostWords() < floor; index++) {
                removed += perBand.get(index);
            }
            return removed;
        }

        /** As {@link #removedBy(long, DetectedFormat)}, summed over every format. */
        public long removedBy(long floor) {
            return survivorsByFormat.keySet().stream()
                    .mapToLong(format -> removedBy(floor, format))
                    .sum();
        }

        private static int indexOf(Band band) {
            int index = BANDS.indexOf(band);
            if (index < 0) {
                throw new IllegalArgumentException(band + " is not one of the bands");
            }
            return index;
        }
    }
}
