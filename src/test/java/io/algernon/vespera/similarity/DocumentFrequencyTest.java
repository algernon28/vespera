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

    /** One walk, one stage-2 run, and the occurrences/shingle rows every test above shares. */
    private Fixture fixture() {
        Ledger ledger = new Ledger(jdbcTemplate);
        WalkId walkId = ledger.startWalk(Path.of("C:/corpus"));
        RunId stage2RunId = ledger.startRun("extraction", "abc123", "{}", walkId, List.of());

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

        ledger.verdict(extractionFailed, stage2RunId, VerdictKind.EXTRACTION_FAILED, "could not read it");
        ledger.verdict(degenerateOutput, stage2RunId, VerdictKind.DEGENERATE_OUTPUT, "below the floor");

        RunId stage3RunId = ledger.startRun("content-census", "def456", "{}", walkId, List.of(stage2RunId));

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
        ledger.fileOccurrence(
                walkId,
                new OccurrencePath(path),
                1,
                Instant.parse("2026-08-29T10:15:30Z"),
                Instant.parse("2026-08-20T08:00:00Z"));
        return ledger.occurrenceId(walkId, new OccurrencePath(path)).orElseThrow();
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
