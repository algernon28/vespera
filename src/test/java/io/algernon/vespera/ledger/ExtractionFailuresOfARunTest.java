package io.algernon.vespera.ledger;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * {@link Verdicts#eachExtractionFailure}: what stage 2's review list is read from (ADR-175 section 7). It
 * hands over the occurrences carrying {@code extraction-failed} under one run, each with its path and the
 * verdict's reason, in path order, and nothing else; and {@link Verdicts#extractionFailureCount} counts them.
 * Since ADR-214 they are handed over one at a time as they are read, where {@code extractionFailures} handed
 * them out as one list, as large as the run's failures, which a disk that lists and will not read makes every
 * survivor of the run.
 *
 * <p>Until #392 this query was held only through whole invocations. Those never put a
 * verdict of another kind, or of another run, beside the ones the list should show, and the order
 * their documents are recorded in is the file system's, which no test chooses. Here the order is fixed
 * to disagree with the paths, and each thing the query must leave out sits beside the things it must
 * return.
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Epic("Ledger")
@Feature("Survivors")
@Issue("392")
@Link(name = "ADR-175", url = Adr.A_FILE_THAT_FAILS_IS_MARKED_AND_SKIPPED, type = "adr")
@Link(name = "ADR-214", url = Adr.NO_CLASS_HOLDS_EVERY_OCCURRENCE_OF_A_RUN, type = "adr")
class ExtractionFailuresOfARunTest {

    /** A stage name for the two run rows: both are runs of one stage over one walk. */
    private static final String STAGE = "extraction";

    /** Recorded first and named last, so the order recorded in and the order of the paths disagree. */
    private static final String RECORDED_FIRST = "reports/c-recorded-first.pdf";

    /** Recorded second and named first. */
    private static final String RECORDED_SECOND = "reports/a-recorded-second.pdf";

    /** Removed under the same run for another reason than a failed conversion. */
    private static final String REMOVED_AS_ANOTHER_KIND = "reports/b-another-kind.pdf";

    /** Removed as a failed conversion, under another run. */
    private static final String REMOVED_UNDER_ANOTHER_RUN = "reports/d-another-run.pdf";

    /** Why the document recorded first was removed. */
    private static final String WHY_THE_FIRST = "rejected: docling-serve answered HTTP 404";

    /** Why the document recorded second was removed. */
    private static final String WHY_THE_SECOND = "timeout: no response within the call timeout";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @Story("The documents a run could not read")
    @DisplayName("Asking which documents a run failed to convert returns those documents by path with the reason for each, and none removed for another reason or under another run")
    void returnsTheRunsFailedConversionsInPathOrderAndNothingElse() {
        Ledger ledger = new Ledger(jdbcTemplate);
        WalkId walkId = ledger.walks().startWalk(Path.of("C:/corpus-of-392"));
        RunId asked = ledger.runs().startRun(STAGE, "a version", "{}", walkId, List.of());
        RunId other = ledger.runs().startRun(STAGE, "another version", "{}", walkId, List.of());
        OccurrenceId first = record(ledger, walkId, RECORDED_FIRST);
        OccurrenceId second = record(ledger, walkId, RECORDED_SECOND);
        OccurrenceId anotherKind = record(ledger, walkId, REMOVED_AS_ANOTHER_KIND);
        OccurrenceId anotherRun = record(ledger, walkId, REMOVED_UNDER_ANOTHER_RUN);
        ledger.verdicts().verdict(first, asked, VerdictKind.EXTRACTION_FAILED, WHY_THE_FIRST);
        ledger.verdicts().verdict(second, asked, VerdictKind.EXTRACTION_FAILED, WHY_THE_SECOND);
        ledger.verdicts().verdict(anotherKind, asked, VerdictKind.DEGENERATE_OUTPUT, "another kind");
        ledger.verdicts().verdict(anotherRun, other, VerdictKind.EXTRACTION_FAILED, "another run");

        List<RemovedOccurrence> failures = new ArrayList<>();
        ledger.verdicts().eachExtractionFailure(asked, failures::add);

        claim(
                "the two documents the run failed to convert are returned, each with its path and the reason"
                        + " it was removed for, and the one named first comes first although it was"
                        + " recorded, and removed, second",
                () -> assertThat(failures)
                        .containsExactly(
                                new RemovedOccurrence(second, RECORDED_SECOND, WHY_THE_SECOND),
                                new RemovedOccurrence(first, RECORDED_FIRST, WHY_THE_FIRST)));
        claim(
                "the document removed under the same run for another reason is not among them, and neither"
                        + " is the one that failed to convert under another run",
                () -> assertThat(failures)
                        .extracting(RemovedOccurrence::path)
                        .doesNotContain(REMOVED_AS_ANOTHER_KIND, REMOVED_UNDER_ANOTHER_RUN));
        List<RemovedOccurrence> ofTheOtherRun = new ArrayList<>();
        ledger.verdicts().eachExtractionFailure(other, ofTheOtherRun::add);
        claim(
                "asked of the other run, it hands over that run's one failed conversion and nothing of this one's",
                () -> assertThat(ofTheOtherRun)
                        .extracting(RemovedOccurrence::path)
                        .containsExactly(REMOVED_UNDER_ANOTHER_RUN));
        claim(
                "and the count of each run's failed conversions is the number handed over: two for this run, one"
                        + " for the other",
                () -> assertThat(List.of(
                                ledger.verdicts().extractionFailureCount(asked),
                                ledger.verdicts().extractionFailureCount(other)))
                        .containsExactly(2L, 1L));
    }

    private static OccurrenceId record(Ledger ledger, WalkId walkId, String path) {
        OccurrencePath occurrencePath = new OccurrencePath(path);
        ledger.occurrences().fileOccurrence(
                walkId, occurrencePath, 1, Instant.parse("2026-10-03T10:15:30Z"), Instant.parse("2026-10-03T08:00:00Z"));
        return ledger.occurrences().occurrenceId(walkId, occurrencePath).orElseThrow();
    }
}
