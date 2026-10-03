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
 * {@link Ledger#discardVerdictsAgainst}: the deletion a resumed stage 2 makes before it reads its
 * faulted occurrences again (ADR-181 §1). It removes the verdicts of one kind, under one run, against
 * exactly the occurrences it is handed, and nothing else.
 *
 * <p>{@code verdict} has no unique key, so a verdict this leaves behind is written a second time when
 * the resumed step resolves the fault again, and a verdict it removes by mistake takes a judgement
 * out of a run that never asked for it. The resume tests reach this method with one occurrence; this
 * one hands it more than two of its statement batches' worth, ending in a partial batch, beside a
 * verdict of every other kind, run and occurrence it must leave alone.
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Epic("Ledger")
@Feature("Survivors")
@Issue("379")
@Link(name = "ADR-181", url = Adr.A_STOPPED_STAGE_2_RESUMES_FROM_WHAT_ITS_COMMITTED_CHUNKS_RECORDED, type = "adr")
class DiscardingVerdictsAgainstOccurrencesTest {

    /**
     * The occurrences the deletion is handed: two whole batches of the 500 one statement takes, and
     * one more, so the last batch is partial and holds a single occurrence.
     */
    private static final int NAMED = 1_001;

    /** The occurrences under the same run and kind that it is not handed. */
    private static final int NOT_NAMED = 2;

    /** A stage name for the two run rows: both are runs of one stage over one walk. */
    private static final String STAGE = "extraction";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @Story("A resumed extraction")
    @DisplayName("Taking back the failed-conversion removals of named documents under one run takes back those and nothing else, however many are named")
    void removesOnlyTheNamedOccurrencesVerdictsOfThatKindUnderThatRun() {
        Ledger ledger = new Ledger(jdbcTemplate);
        WalkId walkId = ledger.startWalk(Path.of("C:/corpus"));
        RunId resumed = ledger.startRun(STAGE, "a version", "{}", walkId, List.of());
        RunId other = ledger.startRun(STAGE, "another version", "{}", walkId, List.of());
        List<OccurrenceId> named = record(ledger, walkId, "named", NAMED);
        List<OccurrenceId> notNamed = record(ledger, walkId, "not-named", NOT_NAMED);
        for (OccurrenceId occurrence : named) {
            ledger.verdict(occurrence, resumed, VerdictKind.EXTRACTION_FAILED, "resolved fault");
            ledger.verdict(occurrence, resumed, VerdictKind.DEGENERATE_OUTPUT, "another kind");
            ledger.verdict(occurrence, other, VerdictKind.EXTRACTION_FAILED, "another run");
        }
        for (OccurrenceId occurrence : notNamed) {
            ledger.verdict(occurrence, resumed, VerdictKind.EXTRACTION_FAILED, "not named");
        }

        ledger.discardVerdictsAgainst(resumed, named, VerdictKind.EXTRACTION_FAILED);

        claim(
                "none of the " + NAMED + " named documents keeps its failed-conversion removal under the run"
                        + " it was asked about, the last one included, which travels alone in a group of its own",
                () -> assertThat(countOf(resumed, VerdictKind.EXTRACTION_FAILED, named)).isZero());
        claim(
                "the " + NOT_NAMED + " documents it was not handed keep theirs under that same run",
                () -> assertThat(countOf(resumed, VerdictKind.EXTRACTION_FAILED, notNamed)).isEqualTo(NOT_NAMED));
        claim(
                "the named documents keep their removals of another kind under that run, one each",
                () -> assertThat(countOf(resumed, VerdictKind.DEGENERATE_OUTPUT, named)).isEqualTo(NAMED));
        claim(
                "and their failed-conversion removals under another run, one each",
                () -> assertThat(countOf(other, VerdictKind.EXTRACTION_FAILED, named)).isEqualTo(NAMED));
    }

    /** Records {@code count} occurrences against {@code walkId}, named {@code prefix-n.txt}, in order. */
    private static List<OccurrenceId> record(Ledger ledger, WalkId walkId, String prefix, int count) {
        List<OccurrenceId> recorded = new ArrayList<>(count);
        for (int n = 1; n <= count; n++) {
            OccurrencePath path = new OccurrencePath(prefix + "-" + n + ".txt");
            ledger.fileOccurrence(
                    walkId, path, 1, Instant.parse("2026-10-03T10:15:30Z"), Instant.parse("2026-10-03T08:00:00Z"));
            recorded.add(ledger.occurrenceId(walkId, path).orElseThrow());
        }
        return recorded;
    }

    /**
     * Verdicts of {@code kind} under {@code runId} against any of {@code occurrences}. Each group was
     * recorded in one run of inserts, so its ids are one unbroken range, and the two ranges do not meet.
     */
    private long countOf(RunId runId, VerdictKind kind, List<OccurrenceId> occurrences) {
        long lowest = occurrences.getFirst().value();
        long highest = occurrences.getLast().value();
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM verdict WHERE run_id = ? AND kind = ? AND occurrence_id BETWEEN ? AND ?",
                Long.class,
                runId.value(),
                kind.name(),
                lowest,
                highest);
        return count == null ? 0 : count;
    }
}
