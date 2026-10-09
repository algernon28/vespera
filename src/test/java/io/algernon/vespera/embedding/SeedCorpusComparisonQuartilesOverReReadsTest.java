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
 * leading 16 bits; every later read narrows each quartile's value by 16 more bits, keeping the least and
 * greatest of the values that could still be it, or gathers them once they are 1,000 or fewer. A quartile
 * whose candidates turn out to be one value is answered there, and a key has 64 bits, so the reads are at
 * most four. Each read goes through the survivors a page at a time, through {@code MeasuredForms.eachOf}, and
 * is reported as a read of its own: the first as {@code CORPUS_METRICS}, every later one as {@code
 * CORPUS_METRICS_AGAIN}, each started with the span of stage 2's rows, told its rows after each page, and
 * ended.
 *
 * <p>Four ledgers, each of 2,500 corpus survivors and 50 ruled-out occurrences whose values lie far above
 * every survivor's, which would move every upper quartile if they were counted. The expected quartiles are
 * {@link SeedCorpusComparison.Quartiles#of}'s own, over the survivors' values as each test wrote them:
 *
 * <ul>
 *   <li>values spread wide, a third with no page count and some with no words, so no signal can be answered
 *       from the first 1,000 values or from one value shared by all;
 *   <li>word counts below zero, and ratios below zero, at -0.0 and at 0.0, which a key that did not order
 *       every number as the sort does would misplace. No count is negative in a real run; nothing in the
 *       schema says so;
 *   <li>three fifths of the page counts tied at one, the usual shape of page counts, which must cost two reads
 *       and not four;
 *   <li>two word counts one apart with over a thousand documents at each, which take all four: the third read
 *       tells them apart, and the fourth finds every value that could be each quartile the same one.
 * </ul>
 *
 * <p>Values that differ only in the last 16 bits of their keys, which the fourth read itself has to tell apart,
 * are {@link SignalQuartilesFourthReadTest}'s: no two whole numbers this ledger can hold lie that close.
 *
 * <p>The equality of each spread with {@code Quartiles.of} holds at {@code 4b99a03} too, where every value is
 * held and sorted. What fails there is how the rows are asked for.
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

    /** Of every 25 survivors of the ledger with values below zero: 8 have a ratio below zero, 800 in all. */
    private static final int OF_25_WITH_A_RATIO_BELOW_ZERO = 8;

    /** The next 6 of every 25 have no such word among a negative count of words, a ratio of -0.0: 600 in all. */
    private static final int OF_25_UP_TO_MINUS_ZERO = 14;

    /** The next 6 have none among a positive count, a ratio of 0.0: 600 in all. The last 5 have a ratio above zero. */
    private static final int OF_25_UP_TO_ZERO = 20;

    /** Of every 5 survivors of the ledger with tied page counts, 3 have one page: 1,500 in all, over a page of 1,000. */
    private static final int OF_5_WITH_ONE_PAGE = 3;

    /** A word count whose neighbour, one more, shares its leading 32 bits as a number and differs in the next 16. */
    private static final int A_WORD_COUNT = 1 << 30;

    /** Of every 25 survivors of the ledger with two word counts, 13 have the lower: 1,300 and 1,200, each over 1,000. */
    private static final int OF_25_WITH_THE_LOWER = 13;

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

    /** What extraction measured of one survivor: the four columns the comparison's signals are taken from. */
    private record Measured(int wordCount, Integer pageCount, int vowelless, int singleCharacter) {}

    /** What the {@code j}th survivor of a ledger measured. */
    @FunctionalInterface
    private interface SurvivorValues {

        Measured of(int j);
    }

    @BeforeEach
    void aPool() throws SQLException, IOException {
        pool = new PoolOfTwo(folder);
        jdbcTemplate = pool.jdbcTemplate();
    }

    @AfterEach
    void closeThePool() {
        pool.close();
    }

    /**
     * A corpus walk of {@link #RECORDED} occurrences, one in {@link #ONE_IN} ruled out under stage 2 with
     * values far above any survivor's, the survivors measured as {@code values} says; and one seed.
     */
    private void survivorsMeasuring(SurvivorValues values) throws SQLException {
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
                        Measured measured = values.of(survivor++);
                        if (measured.pageCount() == null) {
                            metric.setNull(3, Types.INTEGER);
                        } else {
                            metric.setInt(3, measured.pageCount());
                            pages.add(measured.pageCount().doubleValue());
                        }
                        metric.setInt(4, measured.wordCount());
                        metric.setInt(5, measured.vowelless());
                        metric.setInt(6, measured.singleCharacter());
                        words.add((double) measured.wordCount());
                        if (measured.wordCount() != 0) {
                            vowellessRatios.add((double) measured.vowelless() / measured.wordCount());
                            singleCharacterRatios.add((double) measured.singleCharacter() / measured.wordCount());
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

    @Test
    @Story("Comparing the seeds with the collection")
    @DisplayName("The collection's quartiles are exact, found by reading the surviving documents a few times, a page at a time, each reading reported on its own")
    void exactQuartilesOverAFewReadsOfTheSurvivors() throws SQLException {
        survivorsMeasuring(j -> {
            int wordCount = j % 97 == 0 ? 0 : 1 + (int) ((j * 7_919L) % 200_000);
            Integer pageCount = j % 3 == 0 ? null : 1 + (int) ((j * 31L) % 500);
            int vowelless = (int) ((j * 13L) % (wordCount / 5 + 1));
            int singleCharacter = (int) ((j * 17L) % (wordCount / 5 + 1));
            return new Measured(wordCount, pageCount, vowelless, singleCharacter);
        });
        Asked asked = new Asked(new ExtractionMetrics(jdbcTemplate, new LanguageDetection()));
        Recorder recorder = new Recorder();

        SeedCorpusComparison.Comparison comparison = new SeedCorpusComparison(jdbcTemplate, new Ledger(jdbcTemplate))
                .measure(measurementRun, extractionRun, seedWalk, asked, recorder);

        everySpreadIsTheSurvivorsOwn(comparison);
        int reads = readsOfTheSurvivorsBy(asked);
        claim(
                "the survivors are read between " + AT_LEAST_TWO_READS + " and " + AT_MOST_FOUR_READS
                        + " times: more than once, their values being too many and too spread to answer from"
                        + " the first reading, and never a fifth time",
                () -> assertThat(reads).isBetween(AT_LEAST_TWO_READS, AT_MOST_FOUR_READS));
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

    /**
     * ADR-211 section 4's key orders every number as {@code Quartiles.of}'s sort does. A key made of a value's
     * raw bits, compared unsigned, puts every value below zero above every value above it, and -0.0 above them
     * all; here that would move all three quartiles of two signals.
     */
    @Test
    @Story("Comparing the seeds with the collection")
    @DisplayName("Values below zero, and a zero with a minus sign, are ordered as numbers are: the quartiles are still exact")
    void valuesBelowZeroAndMinusZeroAreOrderedAsTheSortOrdersThem() throws SQLException {
        survivorsMeasuring(j -> {
            int magnitude = 1 + (int) ((j * 7_919L) % 200_000);
            int of25 = j % 25;
            if (of25 < OF_25_WITH_A_RATIO_BELOW_ZERO) {
                return new Measured(-magnitude, null, 1 + j % 5, 0);
            }
            if (of25 < OF_25_UP_TO_MINUS_ZERO) {
                return new Measured(-magnitude, null, 0, 0);
            }
            if (of25 < OF_25_UP_TO_ZERO) {
                return new Measured(magnitude, null, 0, 0);
            }
            return new Measured(magnitude, null, 1 + j % 5, 0);
        });
        SeedCorpusComparison.Quartiles ofTheRatios =
                SeedCorpusComparison.Quartiles.of(vowellessRatios).orElseThrow();
        claim(
                "the fixture is what it says: of the " + SURVIVORS + " ratios, sorted, the middle two are zeros"
                        + " with a minus sign and the upper quartile's two are plain zeros, which are different"
                        + " values to the sort, and the lower quartile is below zero",
                () -> {
                    assertThat(Double.doubleToRawLongBits(ofTheRatios.median()))
                            .as("the bits of the median, against those of -0.0")
                            .isEqualTo(Double.doubleToRawLongBits(-0.0));
                    assertThat(Double.doubleToRawLongBits(ofTheRatios.upperQuartile()))
                            .as("the bits of the upper quartile, against those of 0.0")
                            .isEqualTo(Double.doubleToRawLongBits(0.0));
                    assertThat(ofTheRatios.lowerQuartile()).isNegative();
                });
        Asked asked = new Asked(new ExtractionMetrics(jdbcTemplate, new LanguageDetection()));

        SeedCorpusComparison.Comparison comparison = new SeedCorpusComparison(jdbcTemplate, new Ledger(jdbcTemplate))
                .measure(measurementRun, extractionRun, seedWalk, asked, new Recorder());

        everySpreadIsTheSurvivorsOwn(comparison);
        int reads = readsOfTheSurvivorsBy(asked);
        claim(
                "and the survivors are read no more than " + AT_MOST_FOUR_READS + " times",
                () -> assertThat(reads).isBetween(1, AT_MOST_FOUR_READS));
    }

    /**
     * ADR-211 section 4, "a quartile that many values share is answered in two reads": the second read finds the
     * least and the greatest of the values that could be the quartile equal. Narrowed 16 bits a read with no
     * such check, the same quartile takes all four.
     */
    @Test
    @Story("Comparing the seeds with the collection")
    @DisplayName("Where most documents have one page, the collection's page-count quartiles are found in two readings")
    void aQuartileTiedOverMoreThanAThousandValuesTakesTwoReads() throws SQLException {
        survivorsMeasuring(j -> new Measured(
                100, j % 5 < OF_5_WITH_ONE_PAGE ? 1 : 2 + (int) ((j * 31L) % 499), 0, 0));
        SeedCorpusComparison.Quartiles ofThePages = SeedCorpusComparison.Quartiles.of(pages).orElseThrow();
        claim(
                "the fixture is what it says: " + SURVIVORS * OF_5_WITH_ONE_PAGE / 5 + " documents of one page,"
                        + " more than a page of " + A_PAGE + ", so the lower quartile and the median are one page"
                        + " and the upper quartile is among the rest",
                () -> {
                    assertThat(ofThePages.lowerQuartile()).isEqualTo(1.0);
                    assertThat(ofThePages.median()).isEqualTo(1.0);
                    assertThat(ofThePages.upperQuartile()).isGreaterThan(1.0);
                });
        Asked asked = new Asked(new ExtractionMetrics(jdbcTemplate, new LanguageDetection()));

        SeedCorpusComparison.Comparison comparison = new SeedCorpusComparison(jdbcTemplate, new Ledger(jdbcTemplate))
                .measure(measurementRun, extractionRun, seedWalk, asked, new Recorder());

        everySpreadIsTheSurvivorsOwn(comparison);
        int reads = readsOfTheSurvivorsBy(asked);
        claim(
                "the survivors are read exactly " + AT_LEAST_TWO_READS + " times: the second reading finds that"
                        + " every value that could be the lower quartile or the median is the same one, and"
                        + " gathers the few that could be the upper quartile",
                () -> assertThat(reads).isEqualTo(AT_LEAST_TWO_READS));
    }

    /**
     * A shape that needs the fourth read: two values so close that their first 32 bits as numbers are the
     * same, each shared by more than 1,000 documents, so no reading can gather the candidates and none before the
     * fourth finds them all equal. They differ in the third 16 bits, so the third read has already told them
     * apart; values the fourth read has to tell apart itself are {@link SignalQuartilesFourthReadTest}'s.
     */
    @Test
    @Story("Comparing the seeds with the collection")
    @DisplayName("Two word counts one apart, each over a thousand documents', are told apart in four readings and never a fifth")
    void twoValuesOneApartEachOverAThousandTimesTakeFourReads() throws SQLException {
        survivorsMeasuring(j -> new Measured(
                j % 25 < OF_25_WITH_THE_LOWER ? A_WORD_COUNT : A_WORD_COUNT + 1, null, 0, 0));
        SeedCorpusComparison.Quartiles ofTheWords = SeedCorpusComparison.Quartiles.of(words).orElseThrow();
        claim(
                "the fixture is what it says: the lower quartile and the median are the lower count and the upper"
                        + " quartile the one above it",
                () -> assertThat(ofTheWords)
                        .isEqualTo(new SeedCorpusComparison.Quartiles(A_WORD_COUNT, A_WORD_COUNT, A_WORD_COUNT + 1.0)));
        Asked asked = new Asked(new ExtractionMetrics(jdbcTemplate, new LanguageDetection()));

        SeedCorpusComparison.Comparison comparison = new SeedCorpusComparison(jdbcTemplate, new Ledger(jdbcTemplate))
                .measure(measurementRun, extractionRun, seedWalk, asked, new Recorder());

        everySpreadIsTheSurvivorsOwn(comparison);
        int reads = readsOfTheSurvivorsBy(asked);
        claim(
                "the survivors are read exactly " + AT_MOST_FOUR_READS + " times: 16 bits of each quartile's value"
                        + " are found at a reading, the two counts differ only after the first 32, and each is"
                        + " shared by too many documents to gather",
                () -> assertThat(reads).isEqualTo(AT_MOST_FOUR_READS));
    }

    /** That each of the four spreads is {@code Quartiles.of} over the survivors' values as this test wrote them. */
    private void everySpreadIsTheSurvivorsOwn(SeedCorpusComparison.Comparison comparison) {
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
            Optional<SeedCorpusComparison.Quartiles> quartiles = expected.get(s);
            if (quartiles.isEmpty()) {
                continue;
            }
            claim(
                    "the collection's " + signal + " quartiles are exactly those of every surviving document's value,"
                            + " the ruled-out documents' values, far above them all, left out",
                    () -> assertThat(comparison.spreads())
                            .filteredOn(spread -> spread.signal().equals(signal))
                            .singleElement()
                            .satisfies(spread -> assertThat(spread.corpus()).isEqualTo(quartiles.get())));
        }
    }

    /**
     * That the corpus side was asked for by naming its occurrences, a page at a time, a whole number of times;
     * and how many times that was.
     */
    private int readsOfTheSurvivorsBy(Asked asked) {
        claim(
                "the collection's side is asked for by naming its documents, a page at a time, and never as every"
                        + " row of the run that measured it",
                () -> assertThat(asked.everyRowOf).doesNotContain(extractionRun));
        int named = asked.namedAtOnce.stream().mapToInt(Integer::intValue).sum();
        claim(
                "each question names at most " + A_PAGE + " documents, and together they name the " + SURVIVORS
                        + " survivors a whole number of times: the survivors are read again, never held",
                () -> {
                    assertThat(asked.namedAtOnce).isNotEmpty().allSatisfy(count -> assertThat(count).isBetween(1, A_PAGE));
                    assertThat(named % SURVIVORS).isZero();
                });
        return named / SURVIVORS;
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
