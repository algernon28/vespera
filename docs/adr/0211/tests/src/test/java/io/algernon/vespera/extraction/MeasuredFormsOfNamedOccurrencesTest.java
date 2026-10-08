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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.AbstractDataSource;
import org.springframework.test.context.ActiveProfiles;

/**
 * {@code ExtractionMetrics.eachMeasuredFormOf}, the read of the measurements of form of named occurrences
 * under one run, which the seed/corpus comparison is handed for its corpus side a page of survivors at a time
 * (ADR-211 section 2). The seven columns are {@code eachMeasuredForm}'s, held by {@code
 * MetricsReadForAnotherModuleTest}; what is held here is which rows come back.
 *
 * <p>Parked under {@code docs/adr/0211/tests/} until the method exists: it names it, and would stop the test
 * tree compiling.
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Epic("Extraction")
@Feature("Derived metrics")
@Issue("456")
@Link(name = "ADR-211", url = Adr.NO_CLASS_HOLDS_EVERY_SURVIVOR_OF_A_RUN, type = "adr")
class MeasuredFormsOfNamedOccurrencesTest {

    private static final Instant WHEN = Instant.parse("2026-01-01T00:00:00Z");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @Story("Another module's work reads the metrics through the module that wrote them")
    @DisplayName("The measurements asked for by name are those of the documents named under the run named, and no other")
    void theRowsOfTheNamedOccurrencesUnderTheRunAndNoOther() {
        ExtractionMetrics metrics = new ExtractionMetrics(jdbcTemplate, new LanguageDetection());
        Ledger ledger = new Ledger(jdbcTemplate);
        WalkId walk = ledger.walks().startWalk(Path.of("C:/corpus-named-" + System.nanoTime()));
        OccurrenceId scored = occurrence(ledger, walk, "scored.pdf");
        OccurrenceId unscored = occurrence(ledger, walk, "unscored.txt");
        OccurrenceId notNamed = occurrence(ledger, walk, "not-named.pdf");
        OccurrenceId onlyUnderTheOtherRun = occurrence(ledger, walk, "other-run.pdf");
        RunId run = ledger.runs().startRun("extraction", "named" + System.nanoTime(), "{}", walk, List.of());
        RunId otherRun = ledger.runs().startRun("extraction", "other" + System.nanoTime(), "{}", walk, List.of());
        row(scored, run, 0.9, 3, 200, 4, 5, "en");
        row(unscored, run, null, null, 7, 0, 1, null);
        row(notNamed, run, 0.5, 1, 10, 0, 0, "en");
        row(onlyUnderTheOtherRun, otherRun, 0.1, 9, 1, 1, 1, "it");
        row(scored, otherRun, 0.2, 8, 999, 9, 9, "de");
        List<String> rows = new ArrayList<>();

        metrics.eachMeasuredFormOf(
                run,
                List.of(scored, unscored, onlyUnderTheOtherRun),
                (occurrence, language, meanScoreIsNull, words, pages, vowelless, singleCharacter) -> rows.add(
                        occurrence.value() + " " + language + " " + meanScoreIsNull + " " + words + " " + pages + " "
                                + vowelless + " " + singleCharacter));

        claim(
                "the two named documents with a row under the run named come back with that run's values, a"
                        + " missing score and a missing page count told apart from zero; the document not named does"
                        + " not come back, nor the one whose only row is another run's, nor the other run's row of"
                        + " a named document",
                () -> assertThat(rows)
                        .containsExactlyInAnyOrder(
                                scored.value() + " en false 200 3 4 5",
                                unscored.value() + " null true 7 null 0 1"));
    }

    @Test
    @Story("Another module's work reads the metrics through the module that wrote them")
    @DisplayName("Asking for the measurements of no document asks the database nothing")
    void askingAboutNoOccurrenceMakesNoStatement() {
        List<String> rows = new ArrayList<>();

        new ExtractionMetrics(new JdbcTemplate(new RefusingEveryConnection()), new LanguageDetection())
                .eachMeasuredFormOf(
                        new RunId("a".repeat(64)),
                        List.of(),
                        (occurrence, language, meanScoreIsNull, words, pages, vowelless, singleCharacter) ->
                                rows.add(String.valueOf(occurrence.value())));

        claim(
                "nothing is handed over, and the read was made through a database that refuses every connection,"
                        + " so any statement at all would have failed it",
                () -> assertThat(rows).isEmpty());
    }

    private static OccurrenceId occurrence(Ledger ledger, WalkId walk, String name) {
        OccurrencePath path = new OccurrencePath(name);
        ledger.occurrences().fileOccurrence(walk, path, 1, WHEN, WHEN);
        return ledger.occurrences().occurrenceId(walk, path).orElseThrow();
    }

    private void row(
            OccurrenceId occurrence,
            RunId run,
            Double meanScore,
            Integer pages,
            int words,
            int vowelless,
            int singleCharacter,
            String language) {
        jdbcTemplate.update(
                "INSERT INTO extraction_metric (occurrence_id, run_id, status, mean_score, processing_time,"
                        + " page_count, character_count, alphanumeric_char_count, word_count,"
                        + " word_character_length_total, vowelless_word_count, single_character_word_count,"
                        + " primary_language) VALUES (?, ?, 'success', ?, 0.5, ?, 1, 1, ?, ?, ?, ?, ?)",
                occurrence.value(),
                run.value(),
                meanScore,
                pages,
                words,
                words,
                vowelless,
                singleCharacter,
                language);
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
}
