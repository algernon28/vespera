package io.algernon.vespera.embedding;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.PoolOfTwo;
import io.algernon.vespera.extraction.ExtractionMetrics;
import io.algernon.vespera.extraction.LanguageDetection;
import io.algernon.vespera.ledger.Ledger;
import io.algernon.vespera.ledger.OccurrenceId;
import io.algernon.vespera.ledger.OccurrencePath;
import io.algernon.vespera.ledger.RunId;
import io.algernon.vespera.ledger.WalkId;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.function.LongConsumer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * The comparison's quartiles of the corpus side are exact, and are found by reading the survivors' rows again
 * a few times rather than by holding a value for each survivor (ADR-211 section 4).
 *
 * <p>The first read counts each signal's values, keeps the first 1,000 of each, and counts each value's
 * leading 16 bits; every later read narrows each quartile's value by 16 more bits, or gathers the few values
 * left that could be it, so the reads are at most four. Each read goes through the survivors a page at a time,
 * through {@code MeasuredForms.eachOf}, and is reported as a read of its own: the first as {@code
 * CORPUS_METRICS}, every later one as {@code CORPUS_METRICS_AGAIN}, each started with the span of stage 2's
 * rows, told its rows after each page, and ended.
 *
 * <p>Over 2,500 corpus survivors whose word counts, page counts and two ratios are spread wide, a third with no
 * page count and some with no words, so no signal can be answered from the first 1,000 values or from one
 * value shared by all; and 50 ruled-out occurrences with values far above every survivor's, which would move
 * every upper quartile if they were counted. The expected quartiles are {@link
 * SeedCorpusComparison.Quartiles#of}'s own, over the survivors' values as this test wrote them.
 *
 * <p>{@code eachOf} and {@code rowsRead} are written without {@code @Override}: ADR-211 adds them, and this
 * class compiles before they exist. The constant of the later reads is named by its text for the same reason.
 */
@Epic("Relevance")
@Feature("Seed/corpus comparison")
@Issue("456")
@Link(name = "ADR-211", url = Adr.NO_CLASS_HOLDS_EVERY_SURVIVOR_OF_A_RUN, type = "adr")
@Link(name = "ADR-086", url = Adr.SEED_CORPUS_MISMATCH_IS_MEASURED_AND_REPORTED, type = "adr")
class SeedCorpusComparisonQuartilesOverReReadsTest {

    /** Two full pages of the ledger's 1,000 and a short third. */
    private static final int SURVIVORS = 2_500;

    /** One occurrence in every 51 is ruled out under stage 2, so they lie among the survivors' ids. */
    private static final int ONE_IN = 51;

    /** The occurrences recorded: the survivors and one ruled out after every fifty of them. */
    private static final int RECORDED = SURVIVORS + SURVIVORS / (ONE_IN - 1);

    /** The most occurrences a page of the ledger holds, and so the most one question may name. */
    private static final int A_PAGE = 1_000;

    /** The rows read once each page of 1,000 survivors has been read. */
    private static final List<Long> ROWS_AFTER_EACH_PAGE = List.of(1_000L, 2_000L, 2_500L);

    /** The fewest reads that can answer a signal of more than 1,000 different values, and the most there are. */
    private static final int AT_LEAST_TWO_READS = 2;

    private static final int AT_MOST_FOUR_READS = 4;

    /** A ruled-out occurrence's word count: far above every survivor's. */
    private static final int FAR_ABOVE = 50_000_000;

    private static final String METRIC_ROW = "INSERT INTO extraction_metric (occurrence_id, run_id, status,"
            + " processing_time, page_count, character_count, alphanumeric_char_count, word_count,"
            + " word_character_length_total, vowelless_word_count, single_character_word_count)"
            + " VALUES (?, ?, 'success', 0.5, ?, 600, 500, ?, 500, ?, ?)";

    @TempDir
    Path folder;

    private PoolOfTwo pool;
    private JdbcTemplate jdbcTemplate;
    private WalkId seedWalk;
    private RunId extractionRun;
    private RunId measurementRun;

    private final List<Double> words = new ArrayList<>();
    private final List<Double> pages = new ArrayList<>();
    private final List<Double> vowellessRatios = new ArrayList<>();
    private final List<Double> singleCharacterRatios = new ArrayList<>();

    @BeforeEach
    void survivorsWithSpreadValuesAndOneSeed() throws SQLException, IOException {
        pool = new PoolOfTwo(folder);
        jdbcTemplate = pool.jdbcTemplate();
        Ledger ledger = new Ledger(jdbcTemplate);
        WalkId corpusWalk = ledger.walks().startWalk(Path.of("C:/corpus/comparison-quartiles"));
        seedWalk = ledger.walks().startWalk(Path.of("C:/seeds/comparison-quartiles"));
        extractionRun = ledger.runs().startRun("extraction", "x211q", "{}", corpusWalk, List.of());
        measurementRun = ledger.runs().startRun("seed-measurement", "m211q", "{}", corpusWalk, List.of(extractionRun));
        try (Connection connection = pool.connection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement occurrence = connection.prepareStatement(
                    "INSERT INTO file_occurrence (walk_id, path, size_bytes, last_modified, creation_time)"
                            + " VALUES (?, ?, 1, '2026-01-01T00:00:00Z', '2026-01-01T00:00:00Z')")) {
                for (int i = 0; i < RECORDED; i++) {
                    occurrence.setLong(1, corpusWalk.value());
                    occurrence.setString(2, "f" + i + ".txt");
                    occurrence.addBatch();
                }
                occurrence.executeBatch();
            }
            connection.commit();
            connection.setAutoCommit(true);
        }
        List<Long> ids = jdbcTemplate.queryForList(
                "SELECT id FROM file_occurrence WHERE walk_id = ? ORDER BY id", Long.class, corpusWalk.value());
        try (Connection connection = pool.connection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement metric = connection.prepareStatement(METRIC_ROW);
                    PreparedStatement ruledOut = connection.prepareStatement(
                            "INSERT INTO verdict (occurrence_id, run_id, kind, reason)"
                                    + " VALUES (?, ?, 'DEGENERATE_OUTPUT', 'ruled out by this test')")) {
                int survivor = 0;
                for (int i = 0; i < ids.size(); i++) {
                    metric.setLong(1, ids.get(i));
                    metric.setString(2, extractionRun.value());
                    if (i % ONE_IN == ONE_IN - 1) {
                        metric.setInt(3, FAR_ABOVE);
                        metric.setInt(4, FAR_ABOVE);
                        metric.setInt(5, FAR_ABOVE);
                        metric.setInt(6, FAR_ABOVE);
                        ruledOut.setLong(1, ids.get(i));
                        ruledOut.setString(2, extractionRun.value());
                        ruledOut.addBatch();
                    } else {
                        int j = survivor++;
                        int wordCount = j % 97 == 0 ? 0 : 1 + (int) ((j * 7_919L) % 200_000);
                        Integer pageCount = j % 3 == 0 ? null : 1 + (int) ((j * 31L) % 500);
                        int vowelless = (int) ((j * 13L) % (wordCount / 5 + 1));
                        int singleCharacter = (int) ((j * 17L) % (wordCount / 5 + 1));
                        if (pageCount == null) {
                            metric.setNull(3, Types.INTEGER);
                        } else {
                            metric.setInt(3, pageCount);
                            pages.add(pageCount.doubleValue());
                        }
                        metric.setInt(4, wordCount);
                        metric.setInt(5, vowelless);
                        metric.setInt(6, singleCharacter);
                        words.add((double) wordCount);
                        if (wordCount != 0) {
                            vowellessRatios.add((double) vowelless / wordCount);
                            singleCharacterRatios.add((double) singleCharacter / wordCount);
                        }
                    }
                    metric.addBatch();
                }
                metric.executeBatch();
                ruledOut.executeBatch();
            }
            connection.commit();
            connection.setAutoCommit(true);
        }
        ledger.occurrences().fileOccurrence(
                seedWalk, new OccurrencePath("seed.txt"), 1L, Instant.parse("2026-09-06T10:15:30Z"),
                Instant.parse("2026-09-01T08:00:00Z"));
        OccurrenceId seed = ledger.occurrences().occurrenceId(seedWalk, new OccurrencePath("seed.txt")).orElseThrow();
        jdbcTemplate.update(METRIC_ROW, seed.value(), measurementRun.value(), 10, 300, 3, 4);
    }

    @AfterEach
    void closeThePool() {
        pool.close();
    }

    @Test
    @Story("Comparing the seeds with the collection")
    @DisplayName("The collection's quartiles are exact, found by reading the surviving documents a few times, a page at a time, each reading reported on its own")
    void exactQuartilesOverAFewReadsOfTheSurvivors() {
        Asked asked = new Asked(new ExtractionMetrics(jdbcTemplate, new LanguageDetection()));
        Recorder recorder = new Recorder();

        SeedCorpusComparison.Comparison comparison = new SeedCorpusComparison(jdbcTemplate, new Ledger(jdbcTemplate))
                .measure(measurementRun, extractionRun, seedWalk, asked, recorder);

        List<Optional<SeedCorpusComparison.Quartiles>> expected = List.of(
                SeedCorpusComparison.Quartiles.of(words),
                SeedCorpusComparison.Quartiles.of(pages),
                SeedCorpusComparison.Quartiles.of(vowellessRatios),
                SeedCorpusComparison.Quartiles.of(singleCharacterRatios));
        List<String> signals = List.of(
                SeedCorpusComparison.WORD_COUNT,
                SeedCorpusComparison.PAGE_COUNT,
                SeedCorpusComparison.VOWELLESS_WORD_RATIO,
                SeedCorpusComparison.SINGLE_CHARACTER_WORD_RATIO);
        for (int s = 0; s < signals.size(); s++) {
            String signal = signals.get(s);
            SeedCorpusComparison.Quartiles quartiles = expected.get(s).orElseThrow();
            claim(
                    "the collection's " + signal + " quartiles are exactly those of every surviving document's value,"
                            + " the ruled-out documents' values, far above them all, left out",
                    () -> assertThat(comparison.spreads())
                            .filteredOn(spread -> spread.signal().equals(signal))
                            .singleElement()
                            .satisfies(spread -> assertThat(spread.corpus()).isEqualTo(quartiles)));
        }
        claim(
                "the collection's side is asked for by naming its documents, a page at a time, and never as every"
                        + " row of the run that measured it",
                () -> assertThat(asked.everyRowOf).doesNotContain(extractionRun));
        int reads = asked.namedAtOnce.stream().mapToInt(Integer::intValue).sum() / SURVIVORS;
        claim(
                "each question names at most " + A_PAGE + " documents, and together they name the " + SURVIVORS
                        + " survivors a whole number of times, between " + AT_LEAST_TWO_READS + " and "
                        + AT_MOST_FOUR_READS + ": the survivors are read again, never held",
                () -> {
                    assertThat(asked.namedAtOnce).isNotEmpty().allSatisfy(named -> assertThat(named).isBetween(1, A_PAGE));
                    assertThat(asked.namedAtOnce.stream().mapToInt(Integer::intValue).sum() % SURVIVORS).isZero();
                    assertThat(reads).isBetween(AT_LEAST_TWO_READS, AT_MOST_FOUR_READS);
                });
        long span = jdbcTemplate.queryForObject(
                "SELECT MAX(rowid) - MIN(rowid) + 1 FROM extraction_metric WHERE run_id = ?", Long.class,
                extractionRun.value());
        List<String> expectedCalls = new ArrayList<>(oneRead("CORPUS_METRICS", span));
        for (int again = 1; again < Math.max(reads, AT_LEAST_TWO_READS); again++) {
            expectedCalls.addAll(oneRead("CORPUS_METRICS_AGAIN", span));
        }
        claim(
                "the first reading of the collection's metrics is reported as such and every later one as a"
                        + " reading again, each started over up to the " + span + " rows of the run, told "
                        + ROWS_AFTER_EACH_PAGE + " rows as each page is done, and ended",
                () -> assertThat(recorder.calls.stream().filter(call -> call.contains("(CORPUS_METRICS")).toList())
                        .containsExactlyElementsOf(expectedCalls));
        claim(
                "and only after the last of them is the seeds' side read",
                () -> assertThat(recorder.calls.indexOf("statementStarting(SEED_METRICS, OptionalLong[1])"))
                        .isGreaterThan(recorder.calls.lastIndexOf(expectedCalls.getLast())));
    }

    private static List<String> oneRead(String statement, long span) {
        List<String> calls = new ArrayList<>();
        calls.add("statementStarting(" + statement + ", " + OptionalLong.of(span) + ")");
        ROWS_AFTER_EACH_PAGE.forEach(rows -> calls.add("rowsRead(" + statement + ", " + rows + ")"));
        calls.add("statementEnded(" + statement + ")");
        return calls;
    }

    /** {@code extraction}'s rows, read as {@link RecordedForms} reads them, keeping what it was asked. */
    private static final class Asked implements MeasuredForms {

        private final ExtractionMetrics metrics;
        final List<RunId> everyRowOf = new ArrayList<>();
        final List<Integer> namedAtOnce = new ArrayList<>();

        Asked(ExtractionMetrics metrics) {
            this.metrics = metrics;
        }

        @Override
        public OptionalLong rowsUpTo(RunId runId) {
            return metrics.metricRowsUpTo(runId);
        }

        @Override
        public void each(RunId runId, LongConsumer stepsTaken, Row row) {
            everyRowOf.add(runId);
            metrics.eachMeasuredForm(runId, stepsTaken, row::read);
        }

        /** The rows of the named occurrences under {@code runId}, picked from the run's rows as they are read. */
        public void eachOf(RunId runId, Collection<OccurrenceId> occurrences, Row row) {
            namedAtOnce.add(occurrences.size());
            Set<OccurrenceId> named = Set.copyOf(occurrences);
            metrics.eachMeasuredForm(
                    runId,
                    ignored -> {},
                    (occurrence, language, meanScoreIsNull, wordCount, pageCount, vowelless, singleCharacter) -> {
                        if (named.contains(occurrence)) {
                            row.read(occurrence, language, meanScoreIsNull, wordCount, pageCount, vowelless,
                                    singleCharacter);
                        }
                    });
        }
    }

    /** Every callback, in the order it came, written as the call it was. */
    private static final class Recorder implements EmbeddingStatementProgress {

        final List<String> calls = new ArrayList<>();

        @Override
        public void statementStarting(EmbeddingStatement statement, OptionalLong rowsUpTo) {
            calls.add("statementStarting(" + statement + ", " + rowsUpTo + ")");
        }

        @Override
        public void stepsTaken(EmbeddingStatement statement, long steps) {
            calls.add("stepsTaken(" + statement + ", " + steps + ")");
        }

        /** ADR-211's callback, written without {@code @Override} so the class compiles before it exists. */
        public void rowsRead(EmbeddingStatement statement, long rows) {
            calls.add("rowsRead(" + statement + ", " + rows + ")");
        }

        @Override
        public void statementEnded(EmbeddingStatement statement) {
            calls.add("statementEnded(" + statement + ")");
        }
    }
}
