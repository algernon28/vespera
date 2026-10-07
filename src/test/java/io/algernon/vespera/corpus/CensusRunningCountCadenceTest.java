package io.algernon.vespera.corpus;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.algernon.vespera.Adr;
import io.algernon.vespera.ledger.Ledger;
import io.algernon.vespera.ledger.OccurrencePath;
import io.algernon.vespera.ledger.WalkId;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The census's running line, driven through {@code WalkRecorder}'s test seam by a scripted traversal that
 * reports entries through {@code Walk.Observer.entryWalked} and offers a checkpoint only where a test asks for
 * one (ADR-192 section 6).
 *
 * <p>It pins four things a walk of real files cannot reach cheaply: the running cadence above 100,000
 * entries, which is section 8's and the same as {@code StageProgress.running}'s (pinned in {@code
 * StageProgressRunningCountTest}); that the line is written from the per-entry callback, which commits
 * nothing, so a line can name entries the ledger does not hold; and that a resumed walk's lines start again
 * from the counts last committed, so a later line can state a lower count than an earlier one, which ADR-192
 * accepts; and that a checkpoint taken commits as before but writes no running line of its own.
 *
 * <p><b>Part (a) of ADR-192.</b> Does not compile until {@code Walk.Observer.entryWalked} exists; part (a)
 * moves it into {@code src/test}.
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Epic("Census")
@Feature("Walk recording")
@Issue("412")
@Link(name = "ADR-192", url = Adr.EVERY_LOOP_REPORTS_ITS_PROGRESS, type = "adr")
@Link(name = "ADR-055", url = Adr.A_WALK_IS_RESUMED_UNDER_ITS_OWN_ID, type = "adr")
class CensusRunningCountCadenceTest {

    /** Past the point where the running count widens tenfold, by one step of the wider kind. */
    private static final int A_HUNDRED_AND_TEN_THOUSAND = 110_000;

    /** 1,000 to 99,000 is ninety-nine lines; 100,000 and 110,000 are two more. */
    private static final int LINES_TO_A_HUNDRED_AND_TEN_THOUSAND = 101;

    /** Past the first running line, and short of the second; a checkpoint offered here is taken. */
    private static final int FIFTEEN_HUNDRED = 1_500;

    private static final int TWO_THOUSAND = 2_000;

    private static final int A_THOUSAND = 1_000;

    /** The commit interval production uses; a checkpoint offered at 1,500 entries is past it, so it is taken. */
    private static final int COMMIT_INTERVAL = 1_000;

    private static final String RUNNING = "entries walked so far";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DataSource dataSource;

    private ListAppender<ILoggingEvent> logged;
    private Logger walkLogger;

    @BeforeEach
    void captureTheWalksLines() {
        logged = new ListAppender<>();
        logged.start();
        walkLogger = (Logger) LoggerFactory.getLogger(WalkRecorder.class);
        walkLogger.addAppender(logged);
    }

    @AfterEach
    void releaseTheWalksLines() {
        walkLogger.detachAppender(logged);
        logged.stop();
    }

    @Test
    @Story("A walk with no total says how many entries it has walked so far")
    @DisplayName("Past 100,000 entries the census's running count widens to every 10,000")
    void theRunningCountWidensPastAHundredThousand(@TempDir Path root) throws Exception {
        recorderReporting(A_HUNDRED_AND_TEN_THOUSAND).walk(root);

        List<String> counts = runningCounts();
        claim(
                "a hundred and one running lines: every 1,000 to 99,000, then 100,000 and 110,000",
                () -> assertThat(counts)
                        .hasSize(LINES_TO_A_HUNDRED_AND_TEN_THOUSAND)
                        .startsWith("1,000", "2,000")
                        .endsWith("99,000", "100,000", "110,000"));
    }

    @Test
    @Story("A walk with no total says how many entries it has walked so far")
    @DisplayName("The running count can name entries the ledger does not hold yet, since the per-entry callback commits nothing")
    void theRunningLineNamesEntriesNotYetCommitted(@TempDir Path root) throws Exception {
        WalkId walk = recorderReporting(FIFTEEN_HUNDRED).walk(root);

        claim(
                "the walk wrote a running line at 1,000 entries",
                () -> assertThat(runningCounts()).containsExactly("1,000"));
        claim(
                "and the ledger holds none of the 1,500 file occurrences the walk reported: no checkpoint was"
                        + " taken and the walk did not finish, so nothing it buffered was committed",
                () -> assertThat(new Ledger(jdbcTemplate).occurrences().occurrenceCount(walk)).isZero());
    }

    @Test
    @Story("A walk with no total says how many entries it has walked so far")
    @DisplayName("A checkpoint taken at 1,500 entries commits them and writes no running line of its own")
    void aCheckpointTakenCommitsAndWritesNoRunningLine(@TempDir Path root) throws Exception {
        WalkId walk = recorderReporting(FIFTEEN_HUNDRED, true).walk(root);

        claim(
                "the walk wrote one running line, at 1,000 entries, from the per-entry callback, and none at"
                        + " 1,500, where the checkpoint was taken: the checkpoint no longer writes the line",
                () -> assertThat(runningCounts()).containsExactly("1,000"));
        claim(
                "and the checkpoint still committed what the walk had buffered: the ledger holds the 1,500 file"
                        + " occurrences, though the walk did not finish",
                () -> assertThat(new Ledger(jdbcTemplate).occurrences().occurrenceCount(walk)).isEqualTo(FIFTEEN_HUNDRED));
    }

    @Test
    @Story("A walk with no total says how many entries it has walked so far")
    @DisplayName("After a walk stops between checkpoints, its resumed lines count again from what was committed")
    void aResumedWalksCountStartsAgainFromWhatWasCommitted(@TempDir Path root) throws Exception {
        recorderReporting(TWO_THOUSAND).walk(root);
        recorderReporting(A_THOUSAND).walk(root);

        claim(
                "the first session wrote 1,000 and 2,000 and stopped with nothing committed; the resumed session"
                        + " starts from the committed count, zero, and writes 1,000 again: a later line states a"
                        + " lower count than an earlier one",
                () -> assertThat(runningCounts()).containsExactly("1,000", "2,000", "1,000"));
        claim(
                "and the resumed session said it was resuming before its running line",
                () -> assertThat(String.join("\n", messages())).containsSubsequence("2,000 " + RUNNING, "Resuming walk", "1,000 " + RUNNING));
    }

    /**
     * A recorder whose traversal reports {@code entries} file occurrences, each followed by {@code
     * entryWalked}, offers no checkpoint, and ends unfinished, so nothing is committed and nothing is
     * reconciled.
     */
    private WalkRecorder recorderReporting(int entries) {
        return recorderReporting(entries, false);
    }

    /**
     * The same, and where {@code offerACheckpoint} is set, a checkpoint offered after the last entry, as the
     * walk offers one when it finishes a directory beneath the root. The walk still ends unfinished, so
     * whatever the ledger holds afterwards was committed by that checkpoint.
     */
    private WalkRecorder recorderReporting(int entries, boolean offerACheckpoint) {
        return new WalkRecorder(
                new Ledger(jdbcTemplate),
                new AnomalyLog(jdbcTemplate),
                new TransactionTemplate(new JdbcTransactionManager(dataSource)),
                (canonical, observer, resumeFrom) -> {
                    Walk.Progress progress = new Walk.Progress(0, 1, 0, 0);
                    for (int entry = 1; entry <= entries; entry++) {
                        observer.fileOccurrence(
                                new OccurrencePath(String.format(Locale.ROOT, "entry-%06d.txt", entry)),
                                1,
                                Instant.EPOCH,
                                Instant.EPOCH);
                        progress = new Walk.Progress(entry, 1, entry, 0);
                        observer.entryWalked(progress);
                    }
                    if (offerACheckpoint) {
                        observer.checkpoint(new Checkpoint(List.of(0), "the directory this test finished"), progress);
                    }
                    return new Walk.Outcome(canonical, progress, false, "stopped by this test between checkpoints");
                },
                COMMIT_INTERVAL);
    }

    /** The count each running line states, in order. */
    private List<String> runningCounts() {
        List<String> counts = new ArrayList<>();
        for (String line : messages()) {
            if (line.contains(RUNNING)) {
                counts.add(line.substring("Stage 0 (census): ".length(), line.indexOf(" " + RUNNING)));
            }
        }
        return counts;
    }

    private List<String> messages() {
        return logged.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
    }
}
