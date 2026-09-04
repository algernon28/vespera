package io.algernon.vespera.similarity;

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
 * {@code similarity}'s shingling function and its table (ADR-038, ADR-073): raw shingle hashes over
 * extracted text, computed standalone against plain text — independent of Docling entirely, per the
 * stage-2 hand-off spec's testing decision — and, separately, that a stored row is filed under the
 * granularity that produced it.
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

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @Story("Boilerplate detection needs matching hashes for a shared passage")
    @DisplayName("Two documents sharing a passage produce matching shingle hashes for it")
    void sharedPassageProducesMatchingHashes() {
        Shingler shingler = new Shingler(jdbcTemplate);
        String sharedPassage = "all rights reserved no part of this publication";
        String documentOne = "Chapter One. " + sharedPassage + ". The story begins here.";
        String documentTwo = "Appendix B. " + sharedPassage + ". Figures follow below.";

        List<Long> hashesOne = shingler.hashesOf(documentOne, ShingleParameters.DEFAULT);
        List<Long> hashesTwo = shingler.hashesOf(documentTwo, ShingleParameters.DEFAULT);

        claim(
                "the two documents' shingle sets intersect on at least one hash — the shared passage's —"
                        + " which is what a later document-frequency pass counts against",
                () -> assertThat(hashesOne).containsAnyElementsOf(hashesTwo));
    }

    @Test
    @Story("A shingle set is comparable only within one granularity")
    @DisplayName("A different window size produces a different shingle count over the same text")
    void differentGranularityProducesADifferentHashCount() {
        Shingler shingler = new Shingler(jdbcTemplate);
        String text = "one two three four five six seven eight nine ten";

        List<Long> fiveWordWindows = shingler.hashesOf(text, ShingleParameters.DEFAULT);
        List<Long> threeWordWindows = shingler.hashesOf(text, new ShingleParameters(3));

        claim(
                "a three-word window over a ten-word text slides across eight overlapping positions",
                () -> assertThat(threeWordWindows).hasSize(8));
        claim(
                "the same ten-word text under the five-word default slides across six overlapping"
                        + " positions instead -- the sliding-window mechanism actually running rather than a"
                        + " fixed, ignored parameter",
                () -> assertThat(fiveWordWindows).hasSize(6));
    }

    @Test
    @Story("A shingle set is comparable only within one granularity")
    @DisplayName("A text shorter than the window produces no shingles rather than a short, misleading one")
    void aTextShorterThanTheWindowProducesNoShingles() {
        Shingler shingler = new Shingler(jdbcTemplate);

        claim(
                "three words under a five-word window is not a partial shingle -- it is no shingle at"
                        + " all, so it never contributes a hash that would collide with a genuine five-word"
                        + " window elsewhere",
                () -> assertThat(shingler.hashesOf("too short here", ShingleParameters.DEFAULT))
                        .isEmpty());
    }

    @Test
    @Story("What a stored shingle row is filed under")
    @DisplayName("A stored shingle set is read back under the run and granularity it was written with")
    void storesTheComputedHashesUnderTheirRunAndGranularity() {
        Shingler shingler = new Shingler(jdbcTemplate);
        OccurrenceId occurrenceId = anOccurrence();
        RunId runId = aRun(occurrenceId);
        String text = "the quick brown fox jumps over the lazy dog again";

        shingler.write(occurrenceId, runId, text, ShingleParameters.DEFAULT);

        claim(
                "every hash the function computed for this text is present among the rows stored for"
                        + " this occurrence, run and granularity",
                () -> assertThat(storedHashes(occurrenceId, runId, ShingleParameters.DEFAULT.identity()))
                        .containsExactlyInAnyOrderElementsOf(
                                shingler.hashesOf(text, ShingleParameters.DEFAULT)));
    }

    @Test
    @Story("A granularity change mints new rows instead of overwriting")
    @DisplayName("Writing the same document under a second granularity adds rows rather than replacing the first")
    void aSecondGranularityAddsRowsUnderItsOwnIdentity() {
        Shingler shingler = new Shingler(jdbcTemplate);
        OccurrenceId occurrenceId = anOccurrence();
        RunId runId = aRun(occurrenceId);
        String text = "the quick brown fox jumps over the lazy dog again";
        ShingleParameters wideWindow = new ShingleParameters(3);

        shingler.write(occurrenceId, runId, text, ShingleParameters.DEFAULT);
        shingler.write(occurrenceId, runId, text, wideWindow);

        claim(
                "the first granularity's rows are untouched by the second write -- still exactly what"
                        + " the function computed for it",
                () -> assertThat(storedHashes(occurrenceId, runId, ShingleParameters.DEFAULT.identity()))
                        .containsExactlyInAnyOrderElementsOf(
                                shingler.hashesOf(text, ShingleParameters.DEFAULT)));
        claim(
                "the second granularity's rows exist under their own identity, alongside the first"
                        + " rather than in place of it",
                () -> assertThat(storedHashes(occurrenceId, runId, wideWindow.identity()))
                        .containsExactlyInAnyOrderElementsOf(shingler.hashesOf(text, wideWindow)));
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

    private OccurrenceId anOccurrence() {
        Ledger ledger = new Ledger(jdbcTemplate);
        WalkId walkId = ledger.startWalk(Path.of("C:/corpus"));
        String path = "occurrence-" + jdbcTemplate.queryForObject("SELECT COUNT(*) FROM file_occurrence", Long.class);
        ledger.fileOccurrence(
                walkId, new OccurrencePath(path), 1, Instant.parse("2026-08-29T10:15:30Z"), Instant.parse(
                        "2026-08-20T08:00:00Z"));
        return ledger.occurrenceId(walkId, new OccurrencePath(path)).orElseThrow();
    }

    private RunId aRun(OccurrenceId anyOccurrenceInTheWalk) {
        Ledger ledger = new Ledger(jdbcTemplate);
        WalkId walkId = new WalkId(jdbcTemplate.queryForObject(
                "SELECT walk_id FROM file_occurrence WHERE id = ?", Long.class, anyOccurrenceInTheWalk.value()));
        return ledger.startRun("extraction", "abc123", "{}", walkId, List.of());
    }
}
