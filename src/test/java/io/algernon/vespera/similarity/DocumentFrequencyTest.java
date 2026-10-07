package io.algernon.vespera.similarity;

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
import java.util.OptionalLong;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * Stage 3's own measurement (ADR-038, ADR-074): document frequency over {@code shingle} rows,
 * restricted to stage-2 survivors -- an occurrence carrying no blocking verdict from stage 2, which
 * includes a {@code partial_success} document (that distinction lives in {@code extraction_metric},
 * never in the verdict table, so a partial_success document simply carries no verdict at all).
 *
 * <p>One walk, one stage-2 run, several occurrences with hand-written shingle rows: the granularity
 * this class tests is the aggregation itself, not the shingling function {@link ShinglerTest} already
 * covers.
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Epic("Extraction")
@Feature("Content census")
@Issue("58")
@Link(name = "ADR-038", url = Adr.SHINGLING_MOVES_TO_STAGE_3, type = "adr")
@Link(name = "ADR-041", url = Adr.LEDGER_OWNS_IDENTITY_AND_VERDICTS, type = "adr")
@Link(name = "ADR-074", url = Adr.STAGE_3_MEASURES_SHINGLE_DOCUMENT_FREQUENCY, type = "adr")
class DocumentFrequencyTest {

    private static final String PARAMETER_IDENTITY = "5-word-window";

    /** A hash present in exactly one surviving document. */
    private static final long SINGLETON_HASH = 100L;

    /** A hash repeated within one document, and present once in another. */
    private static final long SHARED_HASH = 200L;

    /** A hash present only in occurrences that earned a blocking stage-2 verdict. */
    private static final long BLOCKED_ONLY_HASH_A = 300L;

    private static final long BLOCKED_ONLY_HASH_B = 400L;

    /** A hash present only in a lone survivor -- singleton, but still a shingled document. */
    private static final long ANOTHER_SINGLETON_HASH = 500L;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @Story("Document frequency over stage-2 survivors")
    @DisplayName("A shingle hash seen in exactly one surviving document gets no row")
    void aSingletonHashGetsNoRow() {
        Fixture fixture = fixture();
        RunId stage3RunId = fixture.measure();

        claim(
                "an absent row means exactly one surviving document, never zero -- so the singleton hash"
                        + " earns nothing here",
                () -> assertThat(documentFrequencyRow(stage3RunId, SINGLETON_HASH)).isNull());
    }

    @Test
    @Story("Document frequency over stage-2 survivors")
    @DisplayName("A shingle hash seen in two or more surviving documents gets one row with distinct and total counts")
    void aSharedHashGetsOneRowWithCorrectCounts() {
        Fixture fixture = fixture();
        RunId stage3RunId = fixture.measure();

        Row row = documentFrequencyRow(stage3RunId, SHARED_HASH);
        claim("the shared hash earned exactly one row", () -> assertThat(row).isNotNull());
        claim(
                "it appears in exactly two distinct surviving documents",
                () -> assertThat(row.documentCount()).isEqualTo(2));
        claim(
                "and three times in total -- twice inside one document, once in the other -- so a phrase"
                        + " repeated within one document is distinguishable from the same phrase spread"
                        + " across many",
                () -> assertThat(row.totalCount()).isEqualTo(3));
    }

    @Test
    @Story("Document frequency over stage-2 survivors")
    @DisplayName("A hash belonging only to a blocked occurrence is excluded from every count")
    void aHashOnlyInABlockedOccurrenceIsExcluded() {
        Fixture fixture = fixture();
        RunId stage3RunId = fixture.measure();

        claim(
                "a hash only ever seen in an extraction-failed occurrence earns no row at all -- not even"
                        + " a singleton one, since that occurrence never survived to be counted",
                () -> assertThat(documentFrequencyRow(stage3RunId, BLOCKED_ONLY_HASH_A)).isNull());
        claim(
                "the same holds for a hash only ever seen in a degenerate-output occurrence",
                () -> assertThat(documentFrequencyRow(stage3RunId, BLOCKED_ONLY_HASH_B)).isNull());
        claim(
                "and a blocked occurrence sharing the survivors' own hash does not inflate their count --"
                        + " the shared hash's document count stays exactly 2",
                () -> assertThat(documentFrequencyRow(stage3RunId, SHARED_HASH).documentCount())
                        .isEqualTo(2));
    }

    @Test
    @Story("Document frequency over stage-2 survivors")
    @DisplayName("A partial_success occurrence's shingles are included, the same as any other survivor")
    void aPartialSuccessOccurrenceIsIncluded() {
        Fixture fixture = fixture();
        RunId stage3RunId = fixture.measure();

        claim(
                "the lone survivor's own singleton hash still counts it as a shingled document -- a"
                        + " partial_success document carries no verdict at all, so it is a survivor exactly"
                        + " like an unconditional success",
                () -> assertThat(documentFrequencyRow(stage3RunId, ANOTHER_SINGLETON_HASH)).isNull());
        claim(
                "and it is counted in the corpus size below, which is the fact that actually distinguishes"
                        + " it from never having been read at all",
                () -> assertThat(corpusSize(stage3RunId, PARAMETER_IDENTITY)).isEqualTo(3));
    }

    @Test
    @Story("The denominator a future proportion is computed against")
    @DisplayName("The corpus-size denominator equals the stage-2 survivors carrying at least one shingle")
    void corpusSizeIsTheSurvivorsWithAtLeastOneShingle() {
        Fixture fixture = fixture();
        RunId stage3RunId = fixture.measure();

        claim(
                "three surviving occurrences carried a shingle row under this granularity -- the two that"
                        + " share " + SHARED_HASH + "/" + SINGLETON_HASH + " and the lone one carrying "
                        + ANOTHER_SINGLETON_HASH + " -- while the two blocked occurrences never enter this count",
                () -> assertThat(corpusSize(stage3RunId, PARAMETER_IDENTITY)).isEqualTo(3));
    }

    @Test
    @Story("Stage 3 measures; it does not judge")
    @DisplayName("Stage 3 writes no verdict of any kind")
    void writesNoVerdict() {
        Fixture fixture = fixture();
        long verdictsBefore = countVerdicts();

        fixture.measure();

        claim(
                "the verdict table gains no rows from this run -- stage 3 measures document frequency, it"
                        + " renders no judgement",
                () -> assertThat(countVerdicts()).isEqualTo(verdictsBefore));
    }

    /**
     * The bound stage 3 states before it reads a run's shingle rows (ADR-191 sections 2 and 3): the span
     * of the rowids that run wrote, greatest less least plus one. It is the run's own, not the table's,
     * so the run asked about is written second, after another run's rows.
     */
    @Test
    @Story("How many rows a measurement is about to read")
    @DisplayName("The most rows a run can hold is its own count where it was written in one stretch, and less than the table's")
    @Issue("410")
    @Link(name = "ADR-191", url = Adr.STAGE_3_SAYS_HOW_MANY_SHINGLE_ROWS_IT_IS_ABOUT_TO_READ, type = "adr")
    void theBoundForARunWrittenInOneStretchIsItsOwnRowCount() {
        Ledger ledger = new Ledger(jdbcTemplate);
        WalkId walkId = ledger.walks().startWalk(Path.of("C:/corpus-bound-one-stretch"));
        OccurrenceId occurrence = occurrence(ledger, walkId, "one.txt");
        RunId earlier = ledger.runs().startRun("extraction", "earlier-build", "{}", walkId, List.of());
        RunId later = ledger.runs().startRun("extraction", "later-build", "{}", walkId, List.of());
        claim(
                "the fixture holds two different runs, so the table holds more rows than either",
                () -> assertThat(later).isNotEqualTo(earlier));
        writeRows(occurrence, earlier, ROWS_OF_THE_EARLIER_RUN);
        writeRows(occurrence, later, ROWS_OF_THE_LATER_RUN);

        OptionalLong bound = new DocumentFrequency(jdbcTemplate, ledger).shingleRowsUpTo(later);

        claim(
                "the later run was written in one stretch, so the most rows it can hold is the "
                        + ROWS_OF_THE_LATER_RUN + " it does hold",
                () -> assertThat(bound).hasValue(ROWS_OF_THE_LATER_RUN));
        claim(
                "and the fixture counts that many rows under it",
                () -> assertThat(rowsOf(later)).isEqualTo(ROWS_OF_THE_LATER_RUN));
        claim(
                "that is fewer than the greatest row number in the table, which counts the earlier run's "
                        + ROWS_OF_THE_EARLIER_RUN + " rows too, so the answer is this run's and not the table's",
                () -> assertThat(bound.getAsLong()).isLessThan(greatestShingleRow()));
    }

    @Test
    @Story("How many rows a measurement is about to read")
    @DisplayName("A run that saved no rows has no most to state")
    @Issue("410")
    @Link(name = "ADR-191", url = Adr.STAGE_3_SAYS_HOW_MANY_SHINGLE_ROWS_IT_IS_ABOUT_TO_READ, type = "adr")
    void theBoundForARunWithNoShingleRowIsEmpty() {
        Ledger ledger = new Ledger(jdbcTemplate);
        WalkId walkId = ledger.walks().startWalk(Path.of("C:/corpus-bound-no-rows"));
        OccurrenceId occurrence = occurrence(ledger, walkId, "one.txt");
        RunId withRows = ledger.runs().startRun("extraction", "earlier-build", "{}", walkId, List.of());
        RunId withNone = ledger.runs().startRun("extraction", "later-build", "{}", walkId, List.of());
        writeRows(occurrence, withRows, ROWS_OF_THE_EARLIER_RUN);

        OptionalLong bound = new DocumentFrequency(jdbcTemplate, ledger).shingleRowsUpTo(withNone);

        claim(
                "the table holds another run's rows and none of this run's, so there is nothing to wait for"
                        + " and no number to state",
                () -> assertThat(bound).isEmpty());
    }

    /**
     * Why the line says "up to": a run that was stopped, and resumed after another run had written, has
     * the other run's rows between its own, and the span takes them in.
     */
    @Test
    @Story("How many rows a measurement is about to read")
    @DisplayName("A run another run wrote in between is given more than it holds, never less")
    @Issue("410")
    @Link(name = "ADR-191", url = Adr.STAGE_3_SAYS_HOW_MANY_SHINGLE_ROWS_IT_IS_ABOUT_TO_READ, type = "adr")
    void theBoundForARunAnotherRunWroteInBetweenIsMoreThanItsRowCount() {
        Ledger ledger = new Ledger(jdbcTemplate);
        WalkId walkId = ledger.walks().startWalk(Path.of("C:/corpus-bound-interleaved"));
        OccurrenceId occurrence = occurrence(ledger, walkId, "one.txt");
        RunId resumed = ledger.runs().startRun("extraction", "earlier-build", "{}", walkId, List.of());
        RunId between = ledger.runs().startRun("extraction", "later-build", "{}", walkId, List.of());
        writeRows(occurrence, resumed, ROWS_OF_THE_EARLIER_RUN);
        writeRows(occurrence, between, ROWS_OF_THE_LATER_RUN);
        writeRows(occurrence, resumed, ROWS_OF_THE_EARLIER_RUN);

        OptionalLong bound = new DocumentFrequency(jdbcTemplate, ledger).shingleRowsUpTo(resumed);

        long held = 2L * ROWS_OF_THE_EARLIER_RUN;
        claim(
                "the run holds " + held + " rows, written in two stretches of " + ROWS_OF_THE_EARLIER_RUN,
                () -> assertThat(rowsOf(resumed)).isEqualTo(held));
        claim(
                "the most it is said to hold takes in the " + ROWS_OF_THE_LATER_RUN + " rows the other run wrote"
                        + " between the two stretches, so it is an upper limit and not a count",
                () -> assertThat(bound).hasValue(held + ROWS_OF_THE_LATER_RUN));
    }

    /** How many shingle rows the bound's tests write under the run written first. */
    private static final long ROWS_OF_THE_EARLIER_RUN = 3;

    /** How many they write under the run written second: a different number, so the two are told apart. */
    private static final long ROWS_OF_THE_LATER_RUN = 4;

    /** Writes {@code rows} shingle rows for {@code occurrenceId} under {@code runId}, each with a hash of its own. */
    private void writeRows(OccurrenceId occurrenceId, RunId runId, long rows) {
        for (long row = 0; row < rows; row++) {
            shingle(occurrenceId, runId, row);
        }
    }

    /** How many rows {@code runId} holds in {@code shingle}, counted. */
    private long rowsOf(RunId runId) {
        Long rows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM shingle WHERE run_id = ?", Long.class, runId.value());
        return rows == null ? 0 : rows;
    }

    /** The greatest rowid in {@code shingle}, over every run's rows. */
    private long greatestShingleRow() {
        Long greatest = jdbcTemplate.queryForObject("SELECT MAX(rowid) FROM shingle", Long.class);
        return greatest == null ? 0 : greatest;
    }

    /** One walk, one stage-2 run, and the occurrences/shingle rows every test above shares. */
    private Fixture fixture() {
        Ledger ledger = new Ledger(jdbcTemplate);
        WalkId walkId = ledger.walks().startWalk(Path.of("C:/corpus"));
        RunId stage2RunId = ledger.runs().startRun("extraction", "abc123", "{}", walkId, List.of());

        OccurrenceId survivorOne = occurrence(ledger, walkId, "survivor-one.txt");
        OccurrenceId survivorTwo = occurrence(ledger, walkId, "survivor-two.txt");
        OccurrenceId lonelySurvivor = occurrence(ledger, walkId, "survivor-three.txt");
        OccurrenceId extractionFailed = occurrence(ledger, walkId, "broken.pdf");
        OccurrenceId degenerateOutput = occurrence(ledger, walkId, "degenerate.pdf");

        // survivorOne: SINGLETON_HASH once, SHARED_HASH twice (a phrase repeated within one document).
        shingle(survivorOne, stage2RunId, SINGLETON_HASH);
        shingle(survivorOne, stage2RunId, SHARED_HASH);
        shingle(survivorOne, stage2RunId, SHARED_HASH);
        // survivorTwo: SHARED_HASH once.
        shingle(survivorTwo, stage2RunId, SHARED_HASH);
        // lonelySurvivor: its own singleton -- the "partial_success is included" fixture.
        shingle(lonelySurvivor, stage2RunId, ANOTHER_SINGLETON_HASH);
        // extractionFailed carries SINGLETON_HASH (never inflating it) and its own excluded hash.
        shingle(extractionFailed, stage2RunId, SINGLETON_HASH);
        shingle(extractionFailed, stage2RunId, BLOCKED_ONLY_HASH_A);
        // degenerateOutput also carries SHARED_HASH (never inflating it) and its own excluded hash.
        shingle(degenerateOutput, stage2RunId, SHARED_HASH);
        shingle(degenerateOutput, stage2RunId, BLOCKED_ONLY_HASH_B);

        ledger.verdicts().verdict(extractionFailed, stage2RunId, VerdictKind.EXTRACTION_FAILED, "could not read it");
        ledger.verdicts().verdict(degenerateOutput, stage2RunId, VerdictKind.DEGENERATE_OUTPUT, "below the floor");

        RunId stage3RunId = ledger.runs().startRun("content-census", "def456", "{}", walkId, List.of(stage2RunId));

        return new Fixture(ledger, stage2RunId, stage3RunId);
    }

    private class Fixture {
        private final Ledger ledger;
        private final RunId stage2RunId;
        private final RunId stage3RunId;

        Fixture(Ledger ledger, RunId stage2RunId, RunId stage3RunId) {
            this.ledger = ledger;
            this.stage2RunId = stage2RunId;
            this.stage3RunId = stage3RunId;
        }

        RunId measure() {
            new DocumentFrequency(jdbcTemplate, ledger).measure(stage3RunId, stage2RunId);
            return stage3RunId;
        }
    }

    private OccurrenceId occurrence(Ledger ledger, WalkId walkId, String path) {
        ledger.occurrences().fileOccurrence(
                walkId,
                new OccurrencePath(path),
                1,
                Instant.parse("2026-08-29T10:15:30Z"),
                Instant.parse("2026-08-20T08:00:00Z"));
        return ledger.occurrences().occurrenceId(walkId, new OccurrencePath(path)).orElseThrow();
    }

    private void shingle(OccurrenceId occurrenceId, RunId runId, long hash) {
        jdbcTemplate.update(
                "INSERT INTO shingle (occurrence_id, run_id, shingle_parameter_identity, shingle_hash)"
                        + " VALUES (?, ?, ?, ?)",
                occurrenceId.value(),
                runId.value(),
                PARAMETER_IDENTITY,
                hash);
    }

    private Row documentFrequencyRow(RunId runId, long hash) {
        return jdbcTemplate
                .query(
                        "SELECT document_count, total_count FROM shingle_document_frequency"
                                + " WHERE run_id = ? AND shingle_parameter_identity = ? AND shingle_hash = ?",
                        (resultSet, rowNumber) -> new Row(
                                resultSet.getInt("document_count"), resultSet.getInt("total_count")),
                        runId.value(),
                        PARAMETER_IDENTITY,
                        hash)
                .stream()
                .findFirst()
                .orElse(null);
    }

    private Integer corpusSize(RunId runId, String parameterIdentity) {
        return jdbcTemplate
                .query(
                        "SELECT shingled_document_count FROM shingle_corpus_size"
                                + " WHERE run_id = ? AND shingle_parameter_identity = ?",
                        (resultSet, rowNumber) -> resultSet.getInt("shingled_document_count"),
                        runId.value(),
                        parameterIdentity)
                .stream()
                .findFirst()
                .orElse(null);
    }

    private long countVerdicts() {
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM verdict", Long.class);
        return count == null ? 0 : count;
    }

    private record Row(int documentCount, int totalCount) {}
}
