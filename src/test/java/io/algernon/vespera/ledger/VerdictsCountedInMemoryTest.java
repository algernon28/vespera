package io.algernon.vespera.ledger;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The counts of verdicts {@link Verdicts} keeps in memory for the progress page (ADR-268 section 2): in a
 * transaction, counted once when it commits, by one callback a transaction, and never where it rolls back; outside
 * any, counted at once; and inside {@code countingAtInsert}, the work of a step whose whole work is one
 * transaction, counted at the insert. The class runs outside the slice's own test transaction, so that each test
 * opens, commits and rolls back the transactions it is about, and removes what it committed when it ends.
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Epic("Ledger")
@Feature("Verdicts")
@Link(name = "ADR-268", url = Adr.THE_PROGRESS_PAGE_SHOWS_WHAT_IS_REMOVED_AND_DRAWS_TO_THE_DATA_VIZ_METHOD, type = "adr")
class VerdictsCountedInMemoryTest {

    private static final String EXTRACTION_FAILED = "extraction-failed";

    private static final String DEGENERATE_OUTPUT = "degenerate-output";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private Ledger ledger;
    private WalkId walk;
    private RunId run;
    private TransactionTemplate inATransaction;

    @BeforeEach
    void aWalkWithARun() {
        ledger = new Ledger(jdbcTemplate);
        walk = ledger.walks().startWalk(Path.of("C:/corpus-of-adr-268-" + System.nanoTime()));
        run = ledger.runs().startRun("extraction", "a version", "{}", walk, List.of());
        inATransaction = new TransactionTemplate(transactionManager);
    }

    /** What a test committed, removed, so that no other test of the slice reads it. */
    @AfterEach
    void removeWhatWasCommitted() {
        jdbcTemplate.update("DELETE FROM verdict WHERE run_id = ?", run.value());
        jdbcTemplate.update("DELETE FROM run WHERE id = ?", run.value());
        jdbcTemplate.update("DELETE FROM file_occurrence WHERE walk_id = ?", walk.value());
        jdbcTemplate.update("DELETE FROM walk WHERE id = ?", walk.value());
    }

    @Test
    @Story("The progress page counts the verdicts recorded, in memory")
    @DisplayName("Three verdicts of two kinds in one transaction are counted when it commits, once each, by kind, through one callback for the transaction")
    void aChunksVerdictsAreCountedOnceWhenItCommitsByOneCallback() {
        Verdicts verdicts = ledger.verdicts();
        AtomicReference<Map<String, Long>> beforeTheCommit = new AtomicReference<>();
        AtomicInteger callbacksAdded = new AtomicInteger();
        inATransaction.executeWithoutResult(status -> {
            int before = TransactionSynchronizationManager.getSynchronizations().size();
            verdicts.verdict(occurrence("a.pdf"), run, VerdictKind.EXTRACTION_FAILED, "one");
            verdicts.verdict(occurrence("b.pdf"), run, VerdictKind.EXTRACTION_FAILED, "two");
            verdicts.verdict(occurrence("c.pdf"), run, VerdictKind.DEGENERATE_OUTPUT, "three");
            callbacksAdded.set(TransactionSynchronizationManager.getSynchronizations().size() - before);
            beforeTheCommit.set(verdicts.recorded());
        });

        claim(
                "before the transaction commits, none of the three is counted: the chunk could still roll back",
                () -> assertThat(beforeTheCommit.get())
                        .containsEntry(EXTRACTION_FAILED, 0L)
                        .containsEntry(DEGENERATE_OUTPUT, 0L));
        claim(
                "after it commits, each is counted once, by its kind",
                () -> assertThat(verdicts.recorded())
                        .containsEntry(EXTRACTION_FAILED, 2L)
                        .containsEntry(DEGENERATE_OUTPUT, 1L));
        claim(
                "and the transaction carried one callback for its verdicts, holding a tally by kind, and not one a"
                        + " verdict, which over a chunk of a thousand would be a thousand",
                () -> assertThat(callbacksAdded.get()).isEqualTo(1));
    }

    @Test
    @Story("The progress page counts the verdicts recorded, in memory")
    @DisplayName("A verdict whose transaction rolls back is never counted")
    void aRolledBackTransactionCountsNothing() {
        Verdicts verdicts = ledger.verdicts();
        inATransaction.executeWithoutResult(status -> {
            verdicts.verdict(occurrence("a.pdf"), run, VerdictKind.EXTRACTION_FAILED, "rolled back");
            status.setRollbackOnly();
        });

        claim(
                "the verdict's row is gone with its transaction, and so is its count: none is counted",
                () -> {
                    assertThat(jdbcTemplate.queryForObject(
                                    "SELECT COUNT(*) FROM verdict WHERE run_id = ?", Long.class, run.value()))
                            .isZero();
                    assertThat(verdicts.recorded()).containsEntry(EXTRACTION_FAILED, 0L);
                });
    }

    @Test
    @Story("The progress page counts the verdicts recorded, in memory")
    @DisplayName("A verdict inserted with no transaction active is counted as soon as the insert returns")
    void aVerdictOutsideAnyTransactionIsCountedAtOnce() {
        Verdicts verdicts = ledger.verdicts();

        verdicts.verdict(occurrence("a.pdf"), run, VerdictKind.DEGENERATE_OUTPUT, "at once");

        claim(
                "with no transaction to wait for, the verdict is counted when its insert returns",
                () -> assertThat(verdicts.recorded()).containsEntry(DEGENERATE_OUTPUT, 1L));
    }

    @Test
    @Story("The progress page counts the verdicts recorded, in memory")
    @DisplayName("Inside countingAtInsert a verdict is counted at its insert, in an open transaction, and stays counted if it rolls back; after the work returns, a verdict is counted on commit again")
    void workCountingAtInsertCountsEachVerdictAsItIsInserted() throws Exception {
        Verdicts verdicts = ledger.verdicts();
        AtomicReference<Map<String, Long>> whileTheTransactionIsOpen = new AtomicReference<>();
        inATransaction.executeWithoutResult(status -> {
            try {
                verdicts.countingAtInsert(() -> {
                    verdicts.verdict(occurrence("a.pdf"), run, VerdictKind.EXTRACTION_FAILED, "a tasklet's");
                    return null;
                });
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
            whileTheTransactionIsOpen.set(verdicts.recorded());
            // a tasklet step whose transaction rolls back fails, and the page says so beside the count
            status.setRollbackOnly();
        });
        long afterTheRollback = verdicts.recorded().get(EXTRACTION_FAILED);
        AtomicReference<Map<String, Long>> afterTheWorkReturned = new AtomicReference<>();
        inATransaction.executeWithoutResult(status -> {
            verdicts.verdict(occurrence("b.pdf"), run, VerdictKind.EXTRACTION_FAILED, "a chunk's");
            afterTheWorkReturned.set(verdicts.recorded());
        });

        claim(
                "inside the work, the verdict was counted at its insert, while its transaction was still open: a"
                        + " tasklet step's one transaction commits only when the step ends",
                () -> assertThat(whileTheTransactionIsOpen.get()).containsEntry(EXTRACTION_FAILED, 1L));
        claim(
                "and it stays counted when that transaction rolls back, the step then failing",
                () -> assertThat(afterTheRollback).isEqualTo(1L));
        claim(
                "once the work has returned, a verdict in a transaction is counted on commit again: not while the"
                        + " transaction is open, and once when it commits",
                () -> {
                    assertThat(afterTheWorkReturned.get()).containsEntry(EXTRACTION_FAILED, 1L);
                    assertThat(verdicts.recorded()).containsEntry(EXTRACTION_FAILED, 2L);
                });
    }

    private OccurrenceId occurrence(String path) {
        OccurrencePath occurrencePath = new OccurrencePath(path);
        ledger.occurrences().fileOccurrence(
                walk, occurrencePath, 1, Instant.parse("2026-10-16T10:15:30Z"), Instant.parse("2026-10-16T08:00:00Z"));
        return ledger.occurrences().occurrenceId(walk, occurrencePath).orElseThrow();
    }
}
