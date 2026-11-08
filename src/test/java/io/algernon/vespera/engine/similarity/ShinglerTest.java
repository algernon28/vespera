package io.algernon.vespera.engine.similarity;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.algernon.vespera.Adr;
import io.algernon.vespera.engine.ledger.Ledger;
import io.algernon.vespera.engine.ledger.OccurrenceId;
import io.algernon.vespera.engine.ledger.OccurrencePath;
import io.algernon.vespera.engine.ledger.RunId;
import io.algernon.vespera.engine.ledger.WalkId;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * {@code similarity}'s shingling function and its table (ADR-038, ADR-073): raw shingle hashes over
 * extracted text, computed standalone against plain text — independent of Docling entirely, per the
 * stage-2 hand-off spec's testing decision — and, separately, that a stored row is filed under the
 * granularity that produced it, and (issue #367) that writing the rows in JDBC batches stores exactly
 * the rows one insert per hash used to.
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Epic("Extraction")
@Feature("Shingling")
@Issue("50")
@Link(name = "ADR-038", url = Adr.SHINGLING_MOVES_TO_STAGE_3, type = "adr")
@Link(name = "ADR-073", url = Adr.STAGE_2_WRITES_DERIVED_METRICS, type = "adr")
class ShinglerTest {

    /** The window the application ships: five words (vespera.redundancy.shingle-words). */
    private static final ShingleParameters FIVE_WORD_SHINGLES = new ShingleParameters(5);

    /**
     * The most rows {@link Shingler#write} sends in one JDBC batch, as issue #367 fixes it. It mirrors
     * {@code Shingler.SHINGLE_INSERT_BATCH}, which the spec makes {@code private}, so the test states the
     * value rather than reading it, and assumes the long text crosses two batches of it.
     */
    private static final int ROWS_PER_BATCH = 5_000;

    /**
     * Distinct words in the long text: under the five-word default it gives 11,996 shingles, more than
     * two full batches, so the write crosses at least two batch boundaries and ends on a partial batch.
     */
    private static final int LONG_TEXT_WORDS = 12_000;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @Story("What a stored shingle row is filed under")
    @DisplayName("A stored shingle set is read back under the run and granularity it was written with")
    void storesTheComputedHashesUnderTheirRunAndGranularity() {
        Shingler shingler = new Shingler(jdbcTemplate);
        OccurrenceId occurrenceId = anOccurrence();
        RunId runId = aRun(occurrenceId);
        String text = "the quick brown fox jumps over the lazy dog again";

        shingler.write(occurrenceId, runId, text, FIVE_WORD_SHINGLES);

        claim(
                "every hash the function computed for this text is present among the rows stored for"
                        + " this occurrence, run and granularity",
                () -> assertThat(storedHashes(occurrenceId, runId, FIVE_WORD_SHINGLES.identity()))
                        .containsExactlyInAnyOrderElementsOf(
                                shingler.hashesOf(text, FIVE_WORD_SHINGLES)));
    }

    @Test
    @Issue("367")
    @Story("A long document's shingle set is stored whole")
    @DisplayName("A document with more shingles than one database batch holds has every one of them stored")
    void aShingleSetLargerThanOneBatchIsStoredWhole() {
        Shingler shingler = new Shingler(jdbcTemplate);
        OccurrenceId occurrenceId = anOccurrence();
        RunId runId = aRun(occurrenceId);
        String text = IntStream.range(0, LONG_TEXT_WORDS)
                .mapToObj(index -> "word" + index)
                .collect(Collectors.joining(" "));
        List<Long> computed = shingler.hashesOf(text, FIVE_WORD_SHINGLES);

        shingler.write(occurrenceId, runId, text, FIVE_WORD_SHINGLES);

        assumeTrue(
                computed.size() > 2 * ROWS_PER_BATCH,
                "a text of " + LONG_TEXT_WORDS + " distinct words gives more shingles than two batches of "
                        + ROWS_PER_BATCH + " rows");
        claim(
                "every shingle the function computed is stored for this occurrence and run, exactly once,"
                        + " none lost or repeated at a batch boundary, in the order the function computed them",
                () -> assertThat(hashesInInsertionOrder(occurrenceId, runId, FIVE_WORD_SHINGLES.identity()))
                        .isEqualTo(computed));
    }

    private List<Long> storedHashes(OccurrenceId occurrenceId, RunId runId, String parameterIdentity) {
        return jdbcTemplate.queryForList(
                "SELECT shingle_hash FROM shingle WHERE occurrence_id = ? AND run_id = ?"
                        + " AND shingle_parameter_identity = ?",
                Long.class,
                occurrenceId.value(),
                runId.value(),
                parameterIdentity);
    }

    private List<Long> hashesInInsertionOrder(OccurrenceId occurrenceId, RunId runId, String parameterIdentity) {
        return jdbcTemplate.queryForList(
                "SELECT shingle_hash FROM shingle WHERE occurrence_id = ? AND run_id = ?"
                        + " AND shingle_parameter_identity = ? ORDER BY rowid",
                Long.class,
                occurrenceId.value(),
                runId.value(),
                parameterIdentity);
    }

    private OccurrenceId anOccurrence() {
        Ledger ledger = new Ledger(jdbcTemplate);
        WalkId walkId = ledger.walks().startWalk(Path.of("C:/corpus"));
        String path = "occurrence-" + jdbcTemplate.queryForObject("SELECT COUNT(*) FROM file_occurrence", Long.class);
        ledger.occurrences().fileOccurrence(
                walkId, new OccurrencePath(path), 1, Instant.parse("2026-08-29T10:15:30Z"), Instant.parse(
                        "2026-08-20T08:00:00Z"));
        return ledger.occurrences().occurrenceId(walkId, new OccurrencePath(path)).orElseThrow();
    }

    private RunId aRun(OccurrenceId anyOccurrenceInTheWalk) {
        Ledger ledger = new Ledger(jdbcTemplate);
        WalkId walkId = new WalkId(jdbcTemplate.queryForObject(
                "SELECT walk_id FROM file_occurrence WHERE id = ?", Long.class, anyOccurrenceInTheWalk.value()));
        return ledger.runs().startRun("extraction", "abc123", "{}", walkId, List.of());
    }
}
