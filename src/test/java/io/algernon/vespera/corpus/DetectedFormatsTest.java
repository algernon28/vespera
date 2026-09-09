package io.algernon.vespera.corpus;

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
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * What stage 1 found each file to be, kept as a stage-1 output rather than as a fact about the file
 * itself: the same content examined under a changed rule is a different answer, so the answer is
 * kept under the run that produced it (ADR-095).
 *
 * <p>Read back through the same seam a real caller uses rather than through the columns underneath,
 * matching {@code ContentIdentityTest}'s pattern for corpus's other side tables.
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Epic("Byte-level reduction")
@Feature("Format detection")
@Issue("123")
@Link(name = "ADR-095", url = Adr.DETECTED_FORMAT_IS_A_STAGE_1_OUTPUT, type = "adr")
class DetectedFormatsTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @Story("What was found is kept under the run that found it")
    @DisplayName("A recorded format is read back exactly, scoped to its run")
    void recordsAFormatAndReadsItBackByRun() {
        DetectedFormats formats = new DetectedFormats(jdbcTemplate);
        OccurrenceId occurrenceId = anOccurrence();
        RunId runId = aRun(occurrenceId);
        RunId anotherRun = aRun(occurrenceId);

        formats.record(occurrenceId, runId, DetectedFormat.PDF, Optional.empty());

        claim(
                "what was found for this file under this run is read back exactly as written",
                () -> assertThat(formats.formatFor(occurrenceId, runId)).contains(DetectedFormat.PDF));
        claim(
                "a second examination of the same file is a separate answer, so reading under a run that"
                        + " recorded nothing finds nothing rather than borrowing the first run's answer",
                () -> assertThat(formats.formatFor(occurrenceId, anotherRun)).isEmpty());
    }

    @Test
    @Story("The name's contribution is kept apart from the content's")
    @DisplayName("A finer label is stored beside the class, and is absent when nothing named one")
    void storesTheFinerLabelBesideTheClass() {
        DetectedFormats formats = new DetectedFormats(jdbcTemplate);
        OccurrenceId named = anOccurrence();
        OccurrenceId unnamed = anOccurrence();
        RunId runId = aRun(named);

        formats.record(named, runId, DetectedFormat.OLE_COMPOUND, Optional.of(DetectedSubtype.LEGACY_WORD));
        formats.record(unnamed, runId, DetectedFormat.OLE_COMPOUND, Optional.empty());

        claim(
                "the finer label is read back beside the class it belongs to, since it means nothing on"
                        + " its own -- it says which member of that one class this is",
                () -> assertThat(formats.subtypeFor(named, runId)).contains(DetectedSubtype.LEGACY_WORD));
        claim(
                "two files can share a class and differ only in whether anything named them, so an absent"
                        + " label is stored as absent rather than guessed at",
                () -> assertThat(formats.subtypeFor(unnamed, runId)).isEmpty());
        claim(
                "the unlabelled file still carries its class, which came from the content and cannot be"
                        + " taken away by a name that says nothing",
                () -> assertThat(formats.formatFor(unnamed, runId)).contains(DetectedFormat.OLE_COMPOUND));
    }

    private OccurrenceId anOccurrence() {
        Ledger ledger = new Ledger(jdbcTemplate);
        WalkId walkId = ledger.startWalk(Path.of("C:/corpus"));
        String path = "occurrence-" + jdbcTemplate.queryForObject("SELECT COUNT(*) FROM file_occurrence", Long.class);
        ledger.fileOccurrence(
                walkId,
                new OccurrencePath(path),
                1,
                java.time.Instant.parse("2026-08-29T10:15:30Z"),
                java.time.Instant.parse("2026-08-20T08:00:00Z"));
        return ledger.occurrenceId(walkId, new OccurrencePath(path)).orElseThrow();
    }

    private RunId aRun(OccurrenceId anyOccurrenceInTheWalk) {
        Ledger ledger = new Ledger(jdbcTemplate);
        WalkId walkId = new WalkId(jdbcTemplate.queryForObject(
                "SELECT walk_id FROM file_occurrence WHERE id = ?", Long.class, anyOccurrenceInTheWalk.value()));
        return ledger.startRun("byte-level-reduction", "abc" + System.nanoTime(), "{}", walkId, List.of());
    }
}
