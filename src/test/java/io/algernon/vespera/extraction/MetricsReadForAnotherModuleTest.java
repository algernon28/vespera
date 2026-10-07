package io.algernon.vespera.extraction;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
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
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.AbstractDataSource;
import org.springframework.test.context.ActiveProfiles;

/**
 * The three reads of {@code extraction_metric} that {@code extraction} makes for another module's work
 * (ADR-209 section 3): the alphanumeric character counts redundancy resolution ranks a set by, and the
 * rows and their span the seed/corpus comparison measures form from.
 *
 * <p>{@code similarity} and {@code embedding} each wrote the statement themselves until that record. The
 * statements are the ones they wrote; what is held here is what each answers, since the callers' own tests
 * reach them only through what the callers make of the rows.
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Epic("Extraction")
@Feature("Derived metrics")
@Issue("350")
@Link(name = "ADR-209", url = Adr.THE_LEDGER_IS_FOUR_RECORDS_AND_A_TABLES_SQL_IS_ITS_OWNERS, type = "adr")
class MetricsReadForAnotherModuleTest {

    private static final Instant WHEN = Instant.parse("2026-01-01T00:00:00Z");

    /** The alphanumeric character counts written for the first run's two documents. */
    private static final long FIRST_COUNT = 1_200;

    private static final long SECOND_COUNT = 34;

    /** The first run holds two rows written one after the other, so the span of its rows is two. */
    private static final long ROWS_OF_THE_FIRST_RUN = 2;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @Story("Another module's work reads the metrics through the module that wrote them")
    @DisplayName("The character counts asked for are those of the run and the documents named, and a document with no row is left out")
    void countsAreOfTheRunAndTheDocumentsNamed() {
        Fixture fixture = new Fixture();

        Map<OccurrenceId, Long> counts = fixture.metrics.alphanumericCharCounts(
                fixture.firstRun, List.of(fixture.first, fixture.second, fixture.neverMeasured));

        claim(
                "the two documents the first run measured come back with the counts written under that run,"
                        + " not the count the other run wrote for the same document, and the document no run"
                        + " measured is absent, which is how its caller knows to count it as nothing",
                () -> assertThat(counts)
                        .containsOnly(Map.entry(fixture.first, FIRST_COUNT), Map.entry(fixture.second, SECOND_COUNT)));
        claim(
                "and asking about no document asks the database nothing and answers with nothing: the read is"
                        + " made through a database that refuses every connection, so any statement at all would"
                        + " have failed it",
                () -> assertThat(new ExtractionMetrics(new JdbcTemplate(new RefusingEveryConnection()), new LanguageDetection())
                                .alphanumericCharCounts(fixture.firstRun, List.of()))
                        .isEmpty());
    }

    /** A data source no statement can be made through: each connection asked of it is refused. */
    private static final class RefusingEveryConnection extends AbstractDataSource {

        @Override
        public Connection getConnection() throws SQLException {
            throw new SQLException("this data source refuses every connection");
        }

        @Override
        public Connection getConnection(String username, String password) throws SQLException {
            return getConnection();
        }
    }

    @Test
    @Story("Another module's work reads the metrics through the module that wrote them")
    @DisplayName("Every row of a run is handed over as it is read, with a missing score and a missing page count told apart from zero")
    void everyRowOfARunIsHandedOver() {
        Fixture fixture = new Fixture();
        List<String> rows = new ArrayList<>();

        fixture.metrics.eachMeasuredForm(
                fixture.firstRun,
                steps -> {},
                (occurrence, language, meanScoreIsNull, words, pages, vowelless, singleCharacter) -> rows.add(
                        occurrence.value() + " " + language + " " + meanScoreIsNull + " " + words + " " + pages + " "
                                + vowelless + " " + singleCharacter));

        claim(
                "the first run's two rows are handed over and the other run's row is not: the first carries"
                        + " its language, a score, its page count and its three word counts, and the second,"
                        + " written with no score and no page count, says so instead of reading as zero",
                () -> assertThat(rows)
                        .containsExactlyInAnyOrder(
                                fixture.first.value() + " en false 200 3 4 5",
                                fixture.second.value() + " null true 7 null 0 1"));
    }

    @Test
    @Story("Another module's work reads the metrics through the module that wrote them")
    @DisplayName("The span of a run's rows is its row count where they were written together, and empty for a run with none")
    void theSpanOfARunsRows() {
        Fixture fixture = new Fixture();

        claim(
                "the first run's two rows were written one after the other, so their span is "
                        + ROWS_OF_THE_FIRST_RUN + ", the total a caller's progress line counts towards",
                () -> assertThat(fixture.metrics.metricRowsUpTo(fixture.firstRun))
                        .isEqualTo(OptionalLong.of(ROWS_OF_THE_FIRST_RUN)));
        claim(
                "a run that holds no row has no span, so its caller states no total",
                () -> assertThat(fixture.metrics.metricRowsUpTo(fixture.runWithNoRow)).isEmpty());
    }

    /** Three documents and three runs: two rows under the first run, one under the second, none under the third. */
    private final class Fixture {
        final ExtractionMetrics metrics = new ExtractionMetrics(jdbcTemplate, new LanguageDetection());
        final Ledger ledger = new Ledger(jdbcTemplate);
        final WalkId walk = ledger.walks().startWalk(Path.of("C:/corpus-" + System.nanoTime()));
        final OccurrenceId first = occurrence("first.pdf");
        final OccurrenceId second = occurrence("second.txt");
        final OccurrenceId neverMeasured = occurrence("never-measured.txt");
        final RunId firstRun = run("first");
        final RunId otherRun = run("other");
        final RunId runWithNoRow = run("empty");

        Fixture() {
            row(first, firstRun, 0.9, 3, FIRST_COUNT, 200, 4, 5, "en");
            row(second, firstRun, null, null, SECOND_COUNT, 7, 0, 1, null);
            row(first, otherRun, 0.1, 9, 999_999, 1, 1, 1, "it");
        }

        private OccurrenceId occurrence(String name) {
            OccurrencePath path = new OccurrencePath(name);
            ledger.occurrences().fileOccurrence(walk, path, 1, WHEN, WHEN);
            return ledger.occurrences().occurrenceId(walk, path).orElseThrow();
        }

        private RunId run(String version) {
            return ledger.runs().startRun("extraction", version + System.nanoTime(), "{}", walk, List.of());
        }

        private void row(
                OccurrenceId occurrence,
                RunId run,
                Double meanScore,
                Integer pages,
                long alphanumeric,
                int words,
                int vowelless,
                int singleCharacter,
                String language) {
            jdbcTemplate.update(
                    "INSERT INTO extraction_metric (occurrence_id, run_id, status, mean_score, processing_time,"
                            + " page_count, character_count, alphanumeric_char_count, word_count,"
                            + " word_character_length_total, vowelless_word_count, single_character_word_count,"
                            + " primary_language) VALUES (?, ?, 'success', ?, 0.5, ?, ?, ?, ?, ?, ?, ?, ?)",
                    occurrence.value(),
                    run.value(),
                    meanScore,
                    pages,
                    alphanumeric,
                    alphanumeric,
                    words,
                    words,
                    vowelless,
                    singleCharacter,
                    language);
        }
    }
}
