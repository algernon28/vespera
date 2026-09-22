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
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * What is kept about an occurrence the converter refused to open (ADR-139, #265).
 *
 * <p><b>A refusal leaves a row, not an absence.</b> An occurrence with no measurement over it and
 * nothing saying why is indistinguishable from one the run never reached -- and it reads to every
 * later stage as one nothing had any reason to remove, which is the whole of #265. The precedents are
 * the two tables this project already has for a recorded fault that judges nobody: the unusable seed
 * and the cluster fault, and {@code ClusterFaultsTest} is this class's sibling in shape as well as in
 * subject.
 *
 * <p><b>Nothing here is a judgement.</b> The row alone removes nothing. It is the step-scoped listener
 * in {@code pipeline} that turns one into {@code extraction-failed}, and only where the step it
 * happened under went on to complete -- so a class that writes rows and nothing else has to be able to
 * show that it wrote no verdict while doing it.
 *
 * <p><b>The discard is what makes a second invocation possible.</b> The key is the occurrence and the
 * run together, so an invocation stopped before it recorded stage 2's completion has to be able to
 * clear its own rows and write them again; without that, the second attempt meets the first on the key
 * and ends the run that was retrying.
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Epic("Extraction")
@Feature("Stage 2 step")
@Issue("265")
@Link(name = "ADR-139", url = Adr.A_REFUSED_CONVERSION_LEAVES_A_FAULT_ROW, type = "adr")
class ExtractionFaultsTest {

    /** The category the eight containers on the real corpus came back under, and the one that means none. */
    private static final String UNCATEGORISED = "unknown";

    /** A category that is genuinely about the sidecar, so the column is shown telling two refusals apart. */
    private static final String THE_SIDECAR_WAS_FULL = "capacity";

    /** The converter's own message, kept word for word so nobody has to convert the file again to read it. */
    private static final String DETAIL = "An unexpected error occurred while opening the document document.xls.";

    /** A second message, so a row carrying the wrong one could not pass as the right one. */
    private static final String ANOTHER_DETAIL = "the converter had no capacity for this call";

    /** One refusal recorded under one run. */
    private static final long ONE_REFUSAL = 1;

    /** What a run the converter refused nothing under has recorded against it. */
    private static final long NO_REFUSALS = 0;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @Story("A file the converter will not open is recorded rather than left absent")
    @DisplayName("What the converter reported is kept against the occurrence it refused")
    void keepsWhatTheConverterReportedAgainstTheOccurrence() {
        ExtractionFaults faults = new ExtractionFaults(jdbcTemplate);
        OccurrenceId occurrence = anOccurrence("corpus/legacy.xls");
        RunId run = aRun(occurrence);

        faults.write(occurrence, run, UNCATEGORISED, DETAIL);

        claim(
                "the refusal is a row under the run it happened in, so how much of the corpus stage 2"
                        + " actually got an answer about is a count over two tables rather than a question"
                        + " about rows that are not there",
                () -> assertThat(faults.countForRun(run)).isEqualTo(ONE_REFUSAL));
        claim(
                "and the row carries the category the converter reported and the message it came with, in"
                        + " columns of their own: the category is what tells a removal caused by the sidecar"
                        + " from one caused by the file, and learning it any other way costs the conversion"
                        + " that already refused once",
                () -> assertThat(theRowFor(occurrence, run))
                        .containsEntry("category", UNCATEGORISED)
                        .containsEntry("detail", DETAIL));
    }

    @Test
    @Story("A recorded refusal takes nothing out of the archive by itself")
    @DisplayName("Writing the row records no judgement against the occurrence")
    void writesNoVerdict() {
        ExtractionFaults faults = new ExtractionFaults(jdbcTemplate);
        OccurrenceId occurrence = anOccurrence("corpus/legacy.xls");
        RunId run = aRun(occurrence);

        faults.write(occurrence, run, UNCATEGORISED, DETAIL);

        claim(
                "no judgement stands against the occurrence for the row alone. Whether a refusal is about"
                        + " the file or about the sidecar is not answerable at the moment it happens -- the"
                        + " evidence is what becomes of the rest of the step -- so the class that records"
                        + " the refusal leaves that question to whatever can answer it",
                () -> assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM verdict", Integer.class))
                        .isZero());
    }

    @Test
    @Story("A stopped invocation's rows are cleared before the same work is done again")
    @DisplayName("Discarding clears this run's refusals and lets them be written again, and touches no other run's")
    void discardsThisRunsRefusalsAndLetsThemBeWrittenAgain() {
        ExtractionFaults faults = new ExtractionFaults(jdbcTemplate);
        OccurrenceId occurrence = anOccurrence("corpus/legacy.xls");
        RunId run = aRun(occurrence);
        RunId anotherRun = aRun(occurrence);
        faults.write(occurrence, run, UNCATEGORISED, DETAIL);
        faults.write(occurrence, anotherRun, THE_SIDECAR_WAS_FULL, ANOTHER_DETAIL);

        faults.discardForRun(run);

        claim(
                "nothing is recorded under the discarded run any more, which is what a run whose step"
                        + " never recorded its completion is owed: the work is about to be done again, and"
                        + " rows from the attempt that stopped would be counted beside rows from the attempt"
                        + " that finished",
                () -> assertThat(faults.countForRun(run)).isEqualTo(NO_REFUSALS));
        claim(
                "so the same occurrence can be faulted again under the same run. The row is keyed by the"
                        + " occurrence and the run together, so without the clearing above a second attempt"
                        + " would not double the row -- it would collide on it and end the invocation that"
                        + " was retrying",
                () -> {
                    faults.write(occurrence, run, UNCATEGORISED, DETAIL);
                    assertThat(faults.countForRun(run)).isEqualTo(ONE_REFUSAL);
                });
        claim(
                "and the other run's refusal still stands, because a second pass over the same corpus"
                        + " writes beside the first rather than over it -- what one pass could not open says"
                        + " nothing about what another was told",
                () -> assertThat(faults.countForRun(anotherRun)).isEqualTo(ONE_REFUSAL));
    }

    @Test
    @Story("A count of refusals is a count of one pass's refusals")
    @DisplayName("A run the converter refused nothing under counts none")
    void countsNoneForARunWithNoRefusals() {
        ExtractionFaults faults = new ExtractionFaults(jdbcTemplate);
        OccurrenceId occurrence = anOccurrence("corpus/legacy.xls");
        RunId run = aRun(occurrence);

        claim(
                "a pass the converter opened everything for counts none rather than failing to answer."
                        + " The number goes on the page an operator reads to set a threshold, so a run with"
                        + " no refusals has to be able to say so",
                () -> assertThat(faults.countForRun(run)).isEqualTo(NO_REFUSALS));
    }

    /** The one row, read straight off the table: nothing in {@code src/main} reads these rows back. */
    private Map<String, Object> theRowFor(OccurrenceId occurrence, RunId run) {
        return jdbcTemplate.queryForMap(
                "SELECT category, detail FROM extraction_fault WHERE occurrence_id = ? AND run_id = ?",
                occurrence.value(),
                run.value());
    }

    private OccurrenceId anOccurrence(String path) {
        Ledger ledger = new Ledger(jdbcTemplate);
        WalkId walkId = ledger.startWalk(Path.of("C:/corpus"));
        ledger.fileOccurrence(
                walkId,
                new OccurrencePath(path),
                1,
                Instant.parse("2026-09-15T10:15:30Z"),
                Instant.parse("2026-09-01T08:00:00Z"));
        return ledger.occurrenceId(walkId, new OccurrencePath(path)).orElseThrow();
    }

    private RunId aRun(OccurrenceId anyOccurrenceInTheWalk) {
        Ledger ledger = new Ledger(jdbcTemplate);
        return ledger.startRun(
                "extraction", "abc" + System.nanoTime(), "{}", theWalkOf(anyOccurrenceInTheWalk), List.of());
    }

    private WalkId theWalkOf(OccurrenceId occurrence) {
        return new WalkId(jdbcTemplate.queryForObject(
                "SELECT walk_id FROM file_occurrence WHERE id = ?", Long.class, occurrence.value()));
    }
}
