package io.algernon.vespera.embedding;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.ledger.Ledger;
import io.algernon.vespera.ledger.OccurrenceId;
import io.algernon.vespera.ledger.OccurrencePath;
import io.algernon.vespera.ledger.RunId;
import io.algernon.vespera.ledger.VerdictKind;
import io.algernon.vespera.ledger.WalkId;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * How much the seed set resembles the survivors it will be scored against (ADR-086): four
 * comparisons over stored {@code extraction_metric} columns, written under the measurement run's own
 * id and stated in words that judge nothing.
 *
 * <p>Every claim here is about the measurement, so the fixture writes {@code extraction_metric} rows
 * directly on both sides rather than converting anything — the corpus side under the extraction run
 * that produced it, the seed side under the measurement run, which is the only run a seed occurrence
 * is ever measured under (ADR-083 mints no other). ADR-086 assumed those seed rows already existed;
 * they did not, and ADR-092 settles where they come from — stage 5's seed extraction pass writes them
 * from where it already writes its unusable rows, which is the first point at which the run exists.
 * The fixture below states the shape the comparison expects to read.
 *
 * <p>The corpus side is survivors and not every occurrence (ADR-086): what is being asked is whether
 * the seeds resemble what would actually be scored, so a blocked occurrence is out of the population
 * whatever its columns say.
 *
 * <p>What this class deliberately does <em>not</em> pin is the quartile interpolation rule. ADR-086
 * settles that the figure is a median with quartiles either side of it and never a mean; which of the
 * several textbook quartile conventions is used was not decided, so the claims below bracket the
 * quartiles rather than fixing them to a value.
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Epic("Relevance")
@Feature("Seed set")
@Issue("106")
@Link(name = "ADR-086", url = Adr.SEED_CORPUS_MISMATCH_IS_MEASURED_AND_REPORTED, type = "adr")
@Link(name = "ADR-092", url = Adr.THE_SEED_SIDE_IS_MEASURED_BY_SEED_EXTRACTION, type = "adr")
@Link(name = "ADR-070", url = Adr.EXTRACTION_FAILED_SPLITS_ON_DOCLINGS_STATUS, type = "adr")
@Link(name = "ADR-073", url = Adr.STAGE_2_WRITES_DERIVED_METRICS, type = "adr")
@Link(name = "ADR-077", url = Adr.A_REGENERATED_MEASUREMENT_IS_KEYED_PER_RUN, type = "adr")
class SeedCorpusComparisonTest {

    /**
     * The confidence a converted document comes back with here: excellent on Docling's own scale,
     * where 0.9 and above is its top grade. Chosen high on purpose — what says "converted" is that a
     * confidence figure exists at all, so a fixture whose converted documents scored badly would let a
     * value-reading implementation pass.
     */
    private static final double EXCELLENT_CONFIDENCE = 0.95;

    /** The other end of the same scale, below Docling's own 0.5 cut-point: still just as converted. */
    private static final double POOR_CONFIDENCE = 0.05;

    /** Any confidence in a language detection that did answer; nothing here reads the value. */
    private static final double DETECTION_CONFIDENCE = 0.99;

    /** Lingua's own name for the language, which is what the column stores. */
    private static final String ENGLISH = "ENGLISH";

    private static final String ITALIAN = "ITALIAN";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @Story("Only what would be scored is compared")
    @DisplayName("A corpus document carrying a blocking verdict is outside the population compared against")
    void leavesABlockedOccurrenceOutOfTheCorpusPopulation() {
        Fixture fixture = fixture("survivors-only");
        fixture.seed("seed.docx", MetricRow.bornDigital(ENGLISH, 200));
        fixture.survivor("standing.pdf", MetricRow.converted(ENGLISH, 200, 10, EXCELLENT_CONFIDENCE));
        fixture.blocked(
                "ruled-out.pdf",
                MetricRow.converted(ITALIAN, 200, 10, EXCELLENT_CONFIDENCE),
                VerdictKind.EXTRACTION_FAILED);

        SeedCorpusComparison.Comparison comparison = fixture.measure();

        claim(
                "one corpus document is compared, not two: what the comparison answers is whether the"
                        + " seeds resemble the documents that would actually be scored, and a document an"
                        + " earlier pass ruled out is never scored",
                () -> assertThat(comparison.corpusDocumentCount()).isEqualTo(1));
        claim(
                "so the language the ruled-out document was written in is not part of the corpus mix at"
                        + " all -- it would otherwise report a corpus half in Italian that nothing will read",
                () -> assertThat(comparison.languageMix().stream()
                                .map(SeedCorpusComparison.Proportion::category)
                                .toList())
                        .doesNotContain(ITALIAN));
    }

    @Test
    @Story("Only what would be scored is compared")
    @DisplayName("A seed that produced no usable text is outside the population compared against")
    void leavesAnUnusableSeedOutOfTheSeedPopulation() {
        Fixture fixture = fixture("usable-seeds-only");
        fixture.seed("usable.docx", MetricRow.bornDigital(ENGLISH, 200));
        fixture.unusableSeed("empty.pdf", MetricRow.converted(ITALIAN, 200, 10, POOR_CONFIDENCE));
        fixture.survivor("standing.pdf", MetricRow.converted(ENGLISH, 200, 10, EXCELLENT_CONFIDENCE));

        SeedCorpusComparison.Comparison comparison = fixture.measure();

        claim(
                "one seed is compared, not two: a score is the best a document does against the seeds"
                        + " that produced text, so a seed that produced none is never scored against and"
                        + " what it looked like says nothing about what the scoring will use",
                () -> assertThat(comparison.seedDocumentCount()).isEqualTo(1));
        claim(
                "so the language that seed was written in is not part of the seed mix at all -- it would"
                        + " otherwise report a seed set half in Italian that nothing will score against",
                () -> assertThat(comparison.languageMix().stream()
                                .map(SeedCorpusComparison.Proportion::category)
                                .toList())
                        .doesNotContain(ITALIAN));
        claim(
                "and leaving it out is not a further count of documents nothing measured: it was measured,"
                        + " and the reason it is unusable is already reported to the operator by name",
                () -> assertThat(comparison.unmeasuredSeedDocumentCount()).isZero());
    }

    @Test
    @Story("A second measurement is a row set of its own")
    @DisplayName("Measuring again writes fresh rows under the new run and leaves the earlier run's alone")
    void writesAFreshRowSetUnderEachMeasurementRun() {
        Fixture first = fixture("first-measurement");
        first.seed("seed.docx", MetricRow.bornDigital(ENGLISH, 200));
        first.survivor("scan.pdf", MetricRow.converted(ENGLISH, 200, 10, EXCELLENT_CONFIDENCE));
        first.measure();
        long firstRunRows = comparisonRowsFor(first.measurementRunId);

        Fixture second = fixture("second-measurement");
        second.seed("seed.docx", MetricRow.bornDigital(ITALIAN, 200));
        second.seed("altro.docx", MetricRow.bornDigital(ENGLISH, 200));
        second.survivor("scan.pdf", MetricRow.converted(ITALIAN, 200, 10, EXCELLENT_CONFIDENCE));
        second.measure();

        claim(
                "the first measurement wrote rows of its own, keyed by the run that measured it",
                () -> assertThat(firstRunRows).isPositive());
        claim(
                "the second measurement's rows are keyed by its own run",
                () -> assertThat(comparisonRowsFor(second.measurementRunId)).isPositive());
        claim(
                "the first run's rows are exactly as many as they were before the second measurement ran:"
                        + " a re-measurement is a fresh row set beside the earlier one, never an overwrite,"
                        + " so what an earlier seed folder looked like stays answerable",
                () -> assertThat(comparisonRowsFor(first.measurementRunId)).isEqualTo(firstRunRows));
        claim(
                "and the two row sets together are every row in the table, so neither run's measurement"
                        + " landed anywhere the other's could reach",
                () -> assertThat(comparisonRowsFor(first.measurementRunId)
                                + comparisonRowsFor(second.measurementRunId))
                        .isEqualTo(allComparisonRows()));
        claim(
                "no row is keyed by the extraction run whose columns were read instead of by the run that"
                        + " did the measuring -- a re-measurement over the same extracted text has to be"
                        + " able to exist beside this one",
                () -> assertThat(comparisonRowsFor(first.extractionRunId)).isZero());
    }

    private long comparisonRowsFor(RunId runId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM seed_corpus_comparison WHERE run_id = ?", Long.class, runId.value());
    }

    private long allComparisonRows() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM seed_corpus_comparison", Long.class);
    }

    /**
     * One corpus walk with an extraction run over it, one seed walk, and the measurement run that
     * names the extraction run upstream — the shape stage 5 mints (ADR-089 for the upstream, ADR-083
     * for the run itself), assembled by hand because what is under test is the comparison and not the
     * wiring.
     *
     * <p>{@code name} keeps each fixture's walks and runs distinct, since a run's id is derived from
     * what it consumed and two identical runs would collide on the primary key.
     */
    private Fixture fixture(String name) {
        Ledger ledger = new Ledger(jdbcTemplate);
        WalkId corpusWalk = ledger.walks().startWalk(Path.of("C:/corpus/" + name));
        WalkId seedWalk = ledger.walks().startWalk(Path.of("C:/seeds/" + name));
        RunId extractionRunId = ledger.runs().startRun("extraction", "extraction-" + name, "{}", corpusWalk, List.of());
        RunId measurementRunId = ledger.runs().startRun(
                "seed-measurement", "measurement-" + name, "{}", corpusWalk, List.of(extractionRunId));
        return new Fixture(ledger, corpusWalk, seedWalk, extractionRunId, measurementRunId);
    }

    /**
     * One {@code extraction_metric} row's worth of the columns this comparison reads, so a fixture
     * line says which kind of document it is adding rather than listing twenty columns.
     */
    private record MetricRow(
            String primaryLanguage,
            Double languageConfidence,
            Double meanScore,
            int wordCount,
            Integer pageCount,
            int vowellessWordCount,
            int singleCharacterWordCount) {

        /** A word processor's own file: no confidence figure was ever computed, and no page count. */
        static MetricRow bornDigital(String language, int wordCount) {
            return new MetricRow(language, DETECTION_CONFIDENCE, null, wordCount, null, 0, 0);
        }

        /** A document somebody converted: a confidence figure exists, whatever it says. */
        static MetricRow converted(String language, int wordCount, int pageCount, double meanScore) {
            return new MetricRow(language, DETECTION_CONFIDENCE, meanScore, wordCount, pageCount, 0, 0);
        }

        /** The same document, with detection having declined to guess its language at all. */
        MetricRow withDeclinedLanguage() {
            return new MetricRow(
                    null, null, meanScore, wordCount, pageCount, vowellessWordCount, singleCharacterWordCount);
        }

        /** The same document, carrying the two counts a misread scan leaves behind. */
        MetricRow withGarbageText(int vowellessWords, int singleCharacterWords) {
            return new MetricRow(
                    primaryLanguage, languageConfidence, meanScore, wordCount, pageCount, vowellessWords,
                    singleCharacterWords);
        }
    }

    private class Fixture {
        private final Ledger ledger;
        private final WalkId corpusWalk;
        private final WalkId seedWalk;
        private final RunId extractionRunId;
        private final RunId measurementRunId;

        Fixture(Ledger ledger, WalkId corpusWalk, WalkId seedWalk, RunId extractionRunId, RunId measurementRunId) {
            this.ledger = ledger;
            this.corpusWalk = corpusWalk;
            this.seedWalk = seedWalk;
            this.extractionRunId = extractionRunId;
            this.measurementRunId = measurementRunId;
        }

        /** A seed document, measured under the measurement run — the only run a seed is measured under. */
        void seed(String path, MetricRow row) {
            insert(occurrence(seedWalk, path), measurementRunId, row);
        }

        /**
         * A seed the extractor answered for and tier 1 found unusable. It is measured like any other
         * seed — the row records what the converter reported and judges nothing (ADR-092) — and the
         * unusable row stands beside it, written where the seed pass already writes those (ADR-083).
         */
        void unusableSeed(String path, MetricRow row) {
            OccurrenceId occurrenceId = occurrence(seedWalk, path);
            insert(occurrenceId, measurementRunId, row);
            new UnusableSeeds(jdbcTemplate)
                    .record(occurrenceId, measurementRunId, "recorded unusable by this test's fixture");
        }

        /**
         * A seed occurrence the seed walk recorded and nothing ever measured: walked, and then no
         * metrics row written for it under any run.
         */
        void unmeasuredSeed(String path) {
            occurrence(seedWalk, path);
        }

        /** A corpus document carrying no blocking verdict, measured under the extraction run. */
        void survivor(String path, MetricRow row) {
            insert(occurrence(corpusWalk, path), extractionRunId, row);
        }

        /** A corpus document an earlier pass ruled out, which is therefore never scored. */
        void blocked(String path, MetricRow row, VerdictKind kind) {
            OccurrenceId occurrenceId = occurrence(corpusWalk, path);
            insert(occurrenceId, extractionRunId, row);
            ledger.verdicts().verdict(occurrenceId, extractionRunId, kind, "ruled out by this test's fixture");
        }

        SeedCorpusComparison.Comparison measure() {
            return new SeedCorpusComparison(jdbcTemplate, ledger)
                    .measure(measurementRunId, extractionRunId, seedWalk, RecordedForms.over(jdbcTemplate));
        }

        private OccurrenceId occurrence(WalkId walkId, String path) {
            ledger.occurrences().fileOccurrence(
                    walkId,
                    new OccurrencePath(path),
                    1L,
                    Instant.parse("2026-09-06T10:15:30Z"),
                    Instant.parse("2026-09-01T08:00:00Z"));
            return ledger.occurrences().occurrenceId(walkId, new OccurrencePath(path))
                    .orElseThrow(() -> new IllegalStateException("the fixture's own occurrence was not recorded"));
        }

        private void insert(OccurrenceId occurrenceId, RunId runId, MetricRow row) {
            jdbcTemplate.update(
                    "INSERT INTO extraction_metric"
                            + " (occurrence_id, run_id, status, processing_time, page_count, character_count,"
                            + " alphanumeric_char_count, word_count, word_character_length_total,"
                            + " vowelless_word_count, single_character_word_count, mean_score,"
                            + " primary_language, language_confidence)"
                            + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    occurrenceId.value(),
                    runId.value(),
                    "success",
                    0.5,
                    row.pageCount(),
                    row.wordCount() * 6,
                    row.wordCount() * 5,
                    row.wordCount(),
                    row.wordCount() * 5,
                    row.vowellessWordCount(),
                    row.singleCharacterWordCount(),
                    row.meanScore(),
                    row.primaryLanguage(),
                    row.languageConfidence());
        }
    }
}
