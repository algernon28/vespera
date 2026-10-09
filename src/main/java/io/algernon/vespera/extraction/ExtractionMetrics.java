package io.algernon.vespera.extraction;

import io.algernon.vespera.ledger.OccurrenceId;
import io.algernon.vespera.ledger.RunId;
import io.algernon.vespera.ledger.StatementSteps;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalLong;
import java.util.Set;
import java.util.function.LongConsumer;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * {@code extraction}'s per-occurrence metrics row, written once while the document is still open
 * (ADR-019, ADR-070, ADR-073) — the seam {@code pipeline} calls from stage 2's step for every
 * response a document actually came back for, never for a service-scope failure.
 *
 * <p>Two entry points, not one, because the two verdicts a converted document can earn are mutually
 * exclusive (ADR-070): {@link #write} records the row for a document-scoped {@code extraction-failed}
 * response, where no degeneracy judgement is meaningful; {@link #writeAndJudge} does the same and
 * additionally applies {@link DegeneracyFloor} for a {@code success}/{@code partial_success} response,
 * the only status {@code degenerate-output} is reachable from.
 *
 * <p>{@link #measure} splits the computation from the insert, for the one caller that cannot do both
 * at once: stage 5's seed pass measures each seed as it is converted but cannot write a row until the
 * whole folder has been (ADR-092), and holding a converted document until then would hold a seed
 * folder's worth of extracted text in memory. It holds the measured row instead.
 */
@Component
public class ExtractionMetrics {

    /** The most occurrences one statement names: the ledger's own page. */
    private static final int OCCURRENCES_IN_A_STATEMENT = 1_000;

    private final JdbcTemplate jdbcTemplate;
    private final LanguageDetection languageDetection;
    private final ExtractionCacheKeys cacheKeys;

    public ExtractionMetrics(JdbcTemplate jdbcTemplate, LanguageDetection languageDetection) {
        this.jdbcTemplate = jdbcTemplate;
        this.languageDetection = languageDetection;
        this.cacheKeys = new ExtractionCacheKeys(jdbcTemplate);
    }

    /**
     * The table of cache keys, which is written wherever a metrics row is and nowhere else (ADR-206
     * section 2). It is reached from here because every step that writes a metrics row holds this class
     * and none holds a second collaborator for the keys: the writer of a row writes its key beside it.
     */
    public ExtractionCacheKeys cacheKeys() {
        return cacheKeys;
    }

    /** Records the metrics row for {@code response}, without judging {@code degenerate-output}. */
    public void write(OccurrenceId occurrenceId, RunId runId, DoclingResponse response) {
        computeAndInsert(occurrenceId, runId, response);
    }

    /**
     * Deletes every metrics row recorded under {@code runId} — the discard half of ADR-115/ADR-116,
     * for a step whose completion under this run is not recorded: {@code extraction_metric} is keyed
     * {@code (occurrence_id, run_id)}, so a second write over a stopped invocation's rows would collide on
     * the first one rather than silently double it.
     */
    public void discardForRun(RunId runId) {
        jdbcTemplate.update("DELETE FROM extraction_metric WHERE run_id = ?", runId.value());
    }

    /**
     * Which of {@code occurrences} carry a metrics row under {@code runId} (ADR-181 section 1, ADR-214
     * section 2): what a stopped stage 2's committed chunks recorded, which a resumed step does not read
     * again, asked by key for a page of survivors at a time and never read whole.
     *
     * <p>One statement for each {@value #OCCURRENCES_IN_A_STATEMENT} occurrences it is given, planned as a
     * lookup by the table's primary key; none for an empty collection.
     */
    public Set<OccurrenceId> recordedAmong(RunId runId, Collection<OccurrenceId> occurrences) {
        Set<OccurrenceId> recorded = new HashSet<>();
        for (List<OccurrenceId> batch : inBatches(occurrences)) {
            List<Object> arguments = new ArrayList<>();
            arguments.add(runId.value());
            batch.forEach(id -> arguments.add(id.value()));
            jdbcTemplate.query(
                    "SELECT occurrence_id FROM extraction_metric WHERE run_id = ? AND occurrence_id IN ("
                            + placeholders(batch.size()) + ")",
                    resultSet -> {
                        recorded.add(new OccurrenceId(resultSet.getLong("occurrence_id")));
                    },
                    arguments.toArray());
        }
        return recorded;
    }

    /**
     * How many occurrences carry a metrics row under {@code runId} (ADR-199 section 1, ADR-214 section 2):
     * what the resume line states. {@code progress} is told its total before the read (empty where the run
     * holds no row), the steps SQLite has taken at each callback, and that it ended. The read is always
     * issued, and where the run holds no row it finds none; the rows are counted as they are read and none
     * is kept.
     */
    public long recordedCount(RunId runId, ExtractionStatementProgress progress) {
        OptionalLong rowsUpTo = metricRowsUpTo(runId);
        ExtractionStatement statement = ExtractionStatement.RECORDED_OCCURRENCES;
        progress.statementStarting(statement, rowsUpTo);
        long recorded = StatementSteps.counted(
                jdbcTemplate, steps -> progress.stepsTaken(statement, steps), connection -> {
                    long found = 0;
                    try (PreparedStatement read = connection.prepareStatement(
                            "SELECT occurrence_id FROM extraction_metric WHERE run_id = ?")) {
                        read.setString(1, runId.value());
                        try (ResultSet rows = read.executeQuery()) {
                            while (rows.next()) {
                                found++;
                            }
                        }
                    }
                    return found;
                });
        progress.statementEnded(statement);
        return recorded;
    }

    /**
     * The span of {@code runId}'s metrics rows, empty where it holds none: two statements, each one descent of
     * {@code extraction_metric_by_run_id}.
     */
    public OptionalLong metricRowsUpTo(RunId runId) {
        return metricRowsUpTo(jdbcTemplate, runId);
    }

    /**
     * The alphanumeric character count recorded under {@code runId} for each of {@code occurrences},
     * holding only those that have a row (ADR-209 section 3.2): what redundancy resolution ranks the
     * members of a component by. Asked of the database {@value #OCCURRENCES_IN_A_STATEMENT} occurrences at
     * most to a statement (ADR-214 section 4); asking about no occurrence makes no statement.
     */
    public Map<OccurrenceId, Long> alphanumericCharCounts(RunId runId, Collection<OccurrenceId> occurrences) {
        Map<OccurrenceId, Long> counts = new HashMap<>();
        for (List<OccurrenceId> batch : inBatches(occurrences)) {
            List<Object> args = new ArrayList<>();
            args.add(runId.value());
            batch.forEach(id -> args.add(id.value()));
            jdbcTemplate.query(
                    "SELECT occurrence_id, alphanumeric_char_count FROM extraction_metric"
                            + " WHERE run_id = ? AND occurrence_id IN (" + placeholders(batch.size()) + ")",
                    resultSet -> {
                        counts.put(
                                new OccurrenceId(resultSet.getLong("occurrence_id")),
                                resultSet.getLong("alphanumeric_char_count"));
                    },
                    args.toArray());
        }
        return counts;
    }

    private static List<List<OccurrenceId>> inBatches(Collection<OccurrenceId> occurrences) {
        List<OccurrenceId> all = occurrences instanceof List<OccurrenceId> list ? list : new ArrayList<>(occurrences);
        List<List<OccurrenceId>> batches = new ArrayList<>();
        for (int from = 0; from < all.size(); from += OCCURRENCES_IN_A_STATEMENT) {
            batches.add(all.subList(from, Math.min(all.size(), from + OCCURRENCES_IN_A_STATEMENT)));
        }
        return batches;
    }

    private static String placeholders(int count) {
        return String.join(", ", Collections.nCopies(count, "?"));
    }

    /**
     * Gives each metrics row of {@code runId} to {@code row} as it is read, and the steps SQLite has taken
     * to {@code stepsTaken} (ADR-193): the values the seed/corpus comparison measures form from
     * (ADR-209 section 3.2). Nothing is held: the caller keeps what it wants.
     */
    public void eachMeasuredForm(RunId runId, LongConsumer stepsTaken, MeasuredFormRow row) {
        StatementSteps.counted(jdbcTemplate, stepsTaken, connection -> {
            try (PreparedStatement select = connection.prepareStatement(
                    "SELECT occurrence_id, primary_language, mean_score, word_count, page_count,"
                            + " vowelless_word_count, single_character_word_count FROM extraction_metric"
                            + " WHERE run_id = ?")) {
                select.setString(1, runId.value());
                try (ResultSet resultSet = select.executeQuery()) {
                    while (resultSet.next()) {
                        long occurrenceId = resultSet.getLong("occurrence_id");
                        String primaryLanguage = resultSet.getString("primary_language");
                        resultSet.getDouble("mean_score");
                        boolean meanScoreIsNull = resultSet.wasNull();
                        int wordCount = resultSet.getInt("word_count");
                        int pageCount = resultSet.getInt("page_count");
                        boolean pageCountIsNull = resultSet.wasNull();
                        int vowellessWordCount = resultSet.getInt("vowelless_word_count");
                        int singleCharacterWordCount = resultSet.getInt("single_character_word_count");
                        row.read(
                                new OccurrenceId(occurrenceId),
                                primaryLanguage,
                                meanScoreIsNull,
                                wordCount,
                                pageCountIsNull ? null : pageCount,
                                vowellessWordCount,
                                singleCharacterWordCount);
                    }
                }
            }
            return null;
        });
    }

    /**
     * Gives to {@code row} each metrics row of {@code runId} that belongs to one of {@code occurrences}, and
     * of no other (ADR-211 section 2): the seven values {@link #eachMeasuredForm} hands over, read for a page
     * of survivors at a time. Asking about no occurrence makes no statement. The caller keeps the page to at
     * most 1,000 occurrences, the ledger's own page size.
     */
    public void eachMeasuredFormOf(RunId runId, Collection<OccurrenceId> occurrences, MeasuredFormRow row) {
        if (occurrences.isEmpty()) {
            return;
        }
        String placeholders = occurrences.stream().map(id -> "?").collect(Collectors.joining(", "));
        List<Object> arguments = new ArrayList<>();
        arguments.add(runId.value());
        occurrences.forEach(id -> arguments.add(id.value()));
        jdbcTemplate.query(
                "SELECT occurrence_id, primary_language, mean_score, word_count, page_count,"
                        + " vowelless_word_count, single_character_word_count FROM extraction_metric"
                        + " WHERE run_id = ? AND occurrence_id IN (" + placeholders + ")",
                resultSet -> {
                    long occurrenceId = resultSet.getLong("occurrence_id");
                    String primaryLanguage = resultSet.getString("primary_language");
                    resultSet.getDouble("mean_score");
                    boolean meanScoreIsNull = resultSet.wasNull();
                    int wordCount = resultSet.getInt("word_count");
                    int pageCount = resultSet.getInt("page_count");
                    boolean pageCountIsNull = resultSet.wasNull();
                    int vowellessWordCount = resultSet.getInt("vowelless_word_count");
                    int singleCharacterWordCount = resultSet.getInt("single_character_word_count");
                    row.read(
                            new OccurrenceId(occurrenceId),
                            primaryLanguage,
                            meanScoreIsNull,
                            wordCount,
                            pageCountIsNull ? null : pageCount,
                            vowellessWordCount,
                            singleCharacterWordCount);
                },
                arguments.toArray());
    }

    /** The same span, for a caller that holds a {@link JdbcTemplate} and no instance of this class. */
    static OptionalLong metricRowsUpTo(JdbcTemplate jdbcTemplate, RunId runId) {
        Long least = jdbcTemplate.queryForObject(
                "SELECT MIN(rowid) FROM extraction_metric WHERE run_id = ?", Long.class, runId.value());
        Long greatest = jdbcTemplate.queryForObject(
                "SELECT MAX(rowid) FROM extraction_metric WHERE run_id = ?", Long.class, runId.value());
        if (least == null || greatest == null) {
            return OptionalLong.empty();
        }
        return OptionalLong.of(greatest - least + 1);
    }

    /**
     * Measures {@code response} now and writes nothing, for a caller that does not yet know the run its
     * row belongs to (ADR-092). What comes back is the row's values — a few dozen numbers — so the
     * caller holds those rather than the converted document they were derived from.
     */
    public Measurement measure(DoclingResponse response) {
        return new Measurement(compute(response));
    }

    /** Records a row already measured by {@link #measure}, judging nothing. */
    public void write(OccurrenceId occurrenceId, RunId runId, Measurement measurement) {
        insert(occurrenceId, runId, measurement.metric());
    }

    /**
     * Records the metrics row for {@code response}, and judges the two-tier {@code degenerate-output}
     * floor against it (ADR-070) — {@code confidenceFloor} is {@code pipeline}'s reading of the
     * profile's tier-2 key, {@code null} while it ships unset.
     */
    public DegeneracyVerdict writeAndJudge(
            OccurrenceId occurrenceId, RunId runId, DoclingResponse response, Double confidenceFloor) {
        ExtractionMetric metric = computeAndInsert(occurrenceId, runId, response);
        return DegeneracyFloor.evaluate(metric, confidenceFloor);
    }

    private ExtractionMetric computeAndInsert(OccurrenceId occurrenceId, RunId runId, DoclingResponse response) {
        ExtractionMetric metric = compute(response);
        insert(occurrenceId, runId, metric);
        return metric;
    }

    private ExtractionMetric compute(DoclingResponse response) {
        ExtractedText extracted = ExtractedText.from(response.rawResponse());
        String normalized = TextMetrics.normalizeWhitespace(extracted.text());
        long alphanumericCharCount = TextMetrics.alphanumericCharacterCount(normalized);
        List<String> words = TextMetrics.words(normalized);
        LanguageDetection.Detected detected = languageDetection.detect(normalized, alphanumericCharCount);

        return new ExtractionMetric(
                response.status(),
                errorSummary(response.errors()),
                response.confidence(),
                response.processingTimeSeconds(),
                extracted.pageCount(),
                TextMetrics.characterCount(normalized),
                alphanumericCharCount,
                words.size(),
                TextMetrics.wordCharacterLengthTotal(words),
                TextMetrics.vowellessWordCount(words),
                TextMetrics.singleCharacterWordCount(words),
                detected.primaryLanguage(),
                detected.confidence());
    }

    /**
     * One measured row, opaque to its holder: it carries the columns rather than the document, and the
     * only thing to do with it is hand it back to {@link #write}.
     */
    public static final class Measurement {

        private final ExtractionMetric metric;

        private Measurement(ExtractionMetric metric) {
            this.metric = metric;
        }

        ExtractionMetric metric() {
            return metric;
        }
    }

    private static String errorSummary(List<DoclingError> errors) {
        if (errors.isEmpty()) {
            return null;
        }
        return errors.stream()
                .map(error -> error.category().name().toLowerCase(Locale.ROOT))
                .distinct()
                .collect(Collectors.joining(","));
    }

    private void insert(OccurrenceId occurrenceId, RunId runId, ExtractionMetric metric) {
        ConfidenceScores confidence = metric.confidence();
        jdbcTemplate.update(
                "INSERT INTO extraction_metric"
                        + " (occurrence_id, run_id, status, error_summary, parse_score, layout_score, table_score,"
                        + " ocr_score, mean_score, low_score, mean_grade, low_grade, processing_time, page_count,"
                        + " character_count, alphanumeric_char_count, word_count, word_character_length_total,"
                        + " vowelless_word_count, single_character_word_count, primary_language, language_confidence)"
                        + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                occurrenceId.value(),
                runId.value(),
                metric.status().toWire(),
                metric.errorSummary(),
                confidence == null ? null : confidence.parseScore(),
                confidence == null ? null : confidence.layoutScore(),
                confidence == null ? null : confidence.tableScore(),
                confidence == null ? null : confidence.ocrScore(),
                confidence == null ? null : confidence.meanScore(),
                confidence == null ? null : confidence.lowScore(),
                confidence == null || confidence.meanGrade() == null ? null : confidence.meanGrade().toWire(),
                confidence == null || confidence.lowGrade() == null ? null : confidence.lowGrade().toWire(),
                metric.processingTimeSeconds(),
                metric.pageCount(),
                metric.characterCount(),
                metric.alphanumericCharCount(),
                metric.wordCount(),
                metric.wordCharacterLengthTotal(),
                metric.vowellessWordCount(),
                metric.singleCharacterWordCount(),
                metric.primaryLanguage(),
                metric.languageConfidence());
    }
}
