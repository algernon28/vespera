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
import java.util.List;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * The ledger's record of file occurrences, read back through the same seam a real caller would use
 * rather than through the columns underneath it, so the shape of the tables is free to change
 * without these tests reacting.
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Epic("Census")
@Feature("Ledger")
class LedgerTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @Story("What the ledger records")
    @DisplayName("A file occurrence recorded against a walk is read back by that walk's id")
    @Link(name = "ADR-069", url = Adr.DUPLICATE_SET_RESOLVES_BY_EARLIEST_CREATION_TIME, type = "adr")
    void recordsAFileOccurrenceAndReadsItBackByWalkId() {
        Ledger ledger = new Ledger(jdbcTemplate);
        Instant lastModified = Instant.parse("2026-08-29T10:15:30Z");
        Instant creationTime = Instant.parse("2026-08-20T08:00:00Z");

        WalkId walkId = ledger.startWalk(Path.of("C:/corpus"));
        ledger.fileOccurrence(walkId, new OccurrencePath("a/b.txt"), 10, lastModified, creationTime);

        claim(
                "the occurrence recorded against this walk is read back exactly as it was written, creation"
                        + " time included",
                () -> assertThat(ledger.occurrencesForWalk(walkId))
                        .containsExactly(new RecordedOccurrence(
                                new OccurrencePath("a/b.txt"), 10, lastModified, creationTime)));
    }

    /** A stage name, so the run rows below are looked up the way a caller looks them up. */
    private static final String A_STAGE = "arrangement";

    /**
     * Two ids in the order a caller would meet them, chosen so that the one written second sorts
     * <em>earlier</em> than the one written first. A run id is a hash of what the run consumed, so it
     * carries no order at all — and a lookup that leaned on the id would answer with this pair
     * exactly backwards.
     */
    private static final String WRITTEN_FIRST = "ffffffffffffffffffffffffffffffff";

    private static final String WRITTEN_SECOND = "00000000000000000000000000000000";

    @Test
    @Story("What the ledger records")
    @DisplayName("The latest run of a stage is the one written last, not the one that sorts last")
    @Link(name = "ADR-048", url = Adr.WALK_AND_RUN_IDENTITY, type = "adr")
    void readsBackTheRunWrittenLastRatherThanTheOneThatSortsLast() {
        Ledger ledger = new Ledger(jdbcTemplate);
        WalkId walkId = ledger.startWalk(Path.of("C:/corpus"));
        aRunWithId(WRITTEN_FIRST, walkId);
        aRunWithId(WRITTEN_SECOND, walkId);

        claim(
                "the run written last is the one that comes back, even though it is the lesser of the two"
                        + " when they are compared as text: a run is named by a hash of what it consumed,"
                        + " so nothing about the name says when it happened, and a caller asking for the"
                        + " most recent one would otherwise be handed whichever name happened to sort"
                        + " highest",
                () -> assertThat(ledger.latestRunFor(A_STAGE, walkId))
                        .contains(new RunId(WRITTEN_SECOND)));
    }

    @Test
    @Story("What the ledger records")
    @DisplayName("A stage that has never run for this walk has no latest run")
    void hasNoLatestRunWhereTheStageNeverRan() {
        Ledger ledger = new Ledger(jdbcTemplate);
        WalkId walkId = ledger.startWalk(Path.of("C:/corpus"));

        claim(
                "a stage that never ran against this walk reports nothing rather than reaching for"
                        + " another walk's run: two corpora in one database are two separate histories",
                () -> assertThat(ledger.latestRunFor(A_STAGE, walkId)).isEmpty());
    }

    /** What one piece of work is worth: one record of it, however many times it is asked for. */
    private static final int ONE_RECORD = 1;

    /** The implementation version and configuration these two calls share, so both derive one name. */
    private static final String UNCHANGED_SINCE_LAST_TIME = "a version";

    @Test
    @Story("What the ledger records")
    @DisplayName("Asking twice for a record of the same work carries on under the one already there")
    @Issue("191")
    @Disabled("waits on issue 191: nothing ever reuses a finished walk, so no run id survives one invocation and this cannot hold yet")
    @Link(name = "ADR-115", url = Adr.A_REPEATED_OBSERVATION_IS_DISCARDED_AND_A_RUN_IS_CONTINUED, type = "adr")
    void askingTwiceForOnePieceOfWorkCarriesOnUnderTheRecordAlreadyThere() {
        Ledger ledger = new Ledger(jdbcTemplate);
        WalkId walkId = ledger.startWalk(Path.of("C:/corpus"));

        RunId first = ledger.startRun(A_STAGE, UNCHANGED_SINCE_LAST_TIME, "{}", walkId, List.of());
        RunId second = ledger.startRun(A_STAGE, UNCHANGED_SINCE_LAST_TIME, "{}", walkId, List.of());

        claim(
                "the second ask answers with the same name as the first: what a piece of work is called is"
                        + " worked out from what it was given, and nothing it was given had changed, so this"
                        + " is that work rather than another one like it",
                () -> assertThat(second).isEqualTo(first));
        claim(
                "and " + ONE_RECORD + " row stands under that name rather than two: a second row would"
                        + " agree with the first in every column it has, and the one thing it could do is"
                        + " break the rule that a name identifies one piece of work",
                () -> assertThat(rowsUnder(first)).isEqualTo(ONE_RECORD));
    }

    /** How many rows the run table holds under one name. */
    private int rowsUnder(RunId runId) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM run WHERE id = ?", Integer.class, runId.value());
    }

    /**
     * A run row with an id this test chose, written straight into the table because a run id is
     * derived from its inputs and cannot be steered into a chosen value through {@code startRun}.
     */
    private void aRunWithId(String runId, WalkId walkId) {
        jdbcTemplate.update(
                "INSERT INTO run (id, stage, implementation_version, config_consumed, walk_id)"
                        + " VALUES (?, ?, ?, ?, ?)",
                runId,
                A_STAGE,
                "a version",
                "{}",
                walkId.value());
    }
}
