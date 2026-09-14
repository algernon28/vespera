package io.algernon.vespera.corpus;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.abort;

import io.algernon.vespera.Adr;
import io.algernon.vespera.ledger.Ledger;
import io.algernon.vespera.ledger.OccurrencePath;
import io.algernon.vespera.ledger.RecordedOccurrence;
import io.algernon.vespera.ledger.WalkId;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import javax.sql.DataSource;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * A live walk against a real filesystem, persisted through {@link Ledger} and {@link AnomalyLog}
 * rather than only reported to an in-memory recorder, as {@link WalkTest} does. Read back through
 * the same reader seams those tests already establish.
 *
 * <p>Two of these tests interrupt a walk, which no fixture can arrange against a real filesystem: a
 * walk stops when the machine does. They drive the traversal through the seam
 * {@link WalkRecorder}'s package-private constructor exposes, and use a commit interval of one so
 * that a three-file corpus reaches a checkpoint at all.
 *
 * <p>Four of them walk the same root twice and ask which walk id came back (ADR-115): a traversal
 * that observed exactly what the previous finished walk observed is discarded, and the earlier id
 * is what census returns. What "exactly" covers is the whole of what a walk records — the occurrence
 * rows, the anomaly rows and {@code directories_entered} — so one of the four differs in nothing but
 * an anomaly, and one changes the root and changes it back.
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Epic("Census")
@Feature("Walk recording")
class WalkRecorderTest {

    /** Checkpoint at every completed directory, so a small fixture exercises resuming at all. */
    private static final int EVERY_DIRECTORY = 1;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DataSource dataSource;

    private WalkRecorder recorder() {
        return new WalkRecorder(ledger(), anomalyLog(), transactionManager());
    }

    private WalkRecorder recorder(WalkRecorder.Traversal traversal) {
        return new WalkRecorder(ledger(), anomalyLog(), transactions(), traversal, EVERY_DIRECTORY);
    }

    private Ledger ledger() {
        return new Ledger(jdbcTemplate);
    }

    private AnomalyLog anomalyLog() {
        return new AnomalyLog(jdbcTemplate);
    }

    private JdbcTransactionManager transactionManager() {
        return new JdbcTransactionManager(dataSource);
    }

    private TransactionTemplate transactions() {
        return new TransactionTemplate(transactionManager());
    }

    @Test
    @Story("A live walk is persisted")
    @DisplayName("A file occurrence found by a live walk is recorded under that walk's id")
    @Issue("6")
    @Link(name = "ADR-056", url = Adr.EXCLUDES_NOTHING_IS_RECONCILED, type = "adr")
    void aLiveWalkRecordsItsOccurrencesUnderOneWalkId(@TempDir Path root) throws IOException {
        Files.writeString(root.resolve("a.txt"), "hi");
        Ledger ledger = ledger();

        WalkId walkId = new WalkRecorder(ledger, anomalyLog(), transactionManager()).walk(root);

        claim(
                "the one file written was recorded as an occurrence under the walk's own id",
                () -> assertThat(ledger.occurrencesForWalk(walkId))
                        .extracting(RecordedOccurrence::path)
                        .containsExactly(new OccurrencePath("a.txt")));
    }

    @Test
    @Story("A live walk is persisted")
    @DisplayName("A walk anomaly found by a live walk is recorded under that same walk's id")
    @Issue("6")
    @Link(name = "ADR-056", url = Adr.EXCLUDES_NOTHING_IS_RECONCILED, type = "adr")
    void aLiveWalkRecordsItsAnomaliesUnderTheSameWalkId(@TempDir Path root) throws IOException {
        Path unstorable;
        try {
            unstorable = root.resolve("orphan-" + (char) 0xD800 + ".txt");
            Files.writeString(unstorable, "content");
        } catch (InvalidPathException | IOException e) {
            abort("this filesystem will not create a name with an unpaired surrogate: " + e.getMessage());
        }
        AnomalyLog anomalyLog = anomalyLog();

        WalkId walkId = new WalkRecorder(ledger(), anomalyLog, transactionManager()).walk(root);

        claim(
                "the unstorable name was recorded as one anomaly under the walk's own id",
                () -> assertThat(anomalyLog.anomaliesForWalk(walkId))
                        .extracting(RecordedAnomaly::kind)
                        .containsExactly(WalkAnomalyKind.UNENCODABLE_PATH));
    }

    @Test
    @Story("A walk resumes where it stopped")
    @DisplayName("A walk that stopped partway is continued under its own id, and finishes complete")
    @Issue("5")
    @Link(name = "ADR-055", url = Adr.A_WALK_IS_RESUMED_UNDER_ITS_OWN_ID, type = "adr")
    void resumesAnUnfinishedWalkUnderItsOwnId(@TempDir Path root) throws IOException {
        Files.createDirectories(root.resolve("one"));
        Files.writeString(root.resolve("one/a.txt"), "a");
        Files.createDirectories(root.resolve("two"));
        Files.writeString(root.resolve("two/b.txt"), "b");
        Ledger ledger = ledger();

        WalkId stopped = recorder(stoppingAfterTheFirstDirectory()).walk(root);
        WalkId resumed = recorder().walk(root);

        claim(
                "the second walk continued the first rather than minting a second id",
                () -> assertThat(resumed).isEqualTo(stopped));
        claim(
                "both files are recorded exactly once between the two sessions",
                () -> assertThat(ledger.occurrencesForWalk(resumed))
                        .extracting(RecordedOccurrence::path)
                        .containsExactlyInAnyOrder(new OccurrencePath("one/a.txt"), new OccurrencePath("two/b.txt")));
        claim(
                "the walk is now finished, and so eligible as run input",
                () -> assertThat(ledger.walkFinished(resumed)).isTrue());
    }

    @Test
    @Story("A walk resumes where it stopped")
    @DisplayName("A walk that stopped partway is not finished, so nothing may be run against it")
    @Issue("5")
    @Link(name = "ADR-055", url = Adr.A_WALK_IS_RESUMED_UNDER_ITS_OWN_ID, type = "adr")
    void leavesAnInterruptedWalkUnfinished(@TempDir Path root) throws IOException {
        Files.createDirectories(root.resolve("one"));
        Files.writeString(root.resolve("one/a.txt"), "a");
        Files.createDirectories(root.resolve("two"));
        Files.writeString(root.resolve("two/b.txt"), "b");
        Ledger ledger = ledger();

        WalkId stopped = recorder(stoppingAfterTheFirstDirectory()).walk(root);

        claim(
                "a walk that did not reach the end of the tree is not marked finished",
                () -> assertThat(ledger.walkFinished(stopped)).isFalse());
        claim(
                "it is still offered for continuation, under the id it already has",
                () -> assertThat(ledger.unfinishedWalk(Walk.canonicalRoot(root)).orElseThrow().walkId())
                        .isEqualTo(stopped));
    }

    @Test
    @Story("Nothing goes unaccounted for")
    @DisplayName("A finished walk whose counts do not add up aborts the invocation")
    @Issue("6")
    @Link(name = "ADR-056", url = Adr.EXCLUDES_NOTHING_IS_RECONCILED, type = "adr")
    void refusesAFinishedWalkThatCannotAccountForEveryEntry(@TempDir Path root) throws IOException {
        Files.writeString(root.resolve("a.txt"), "a");

        // A traversal that says it met more entries than it reported. Nothing a real walk does, and
        // exactly what the reconciliation is a check against rather than an assertion of.
        WalkRecorder.Traversal miscounting = (walkRoot, observer, resumeFrom) ->
                new Walk.Outcome(walkRoot, new Walk.Progress(99, 1, 0, 0), true, null);

        claim(
                "a walk claiming 99 entries with nothing written for them is refused, saying how many"
                        + " are unaccounted for",
                () -> assertThatThrownBy(() -> recorder(miscounting).walk(root))
                        .isInstanceOf(ExcludesNothingViolationException.class)
                        .hasMessageContaining("unaccounted for"));
    }

    @Test
    @Story("Nothing goes unaccounted for")
    @DisplayName("A finished walk accounts for every entry it met, across both of its sessions")
    @Issue("6")
    @Link(name = "ADR-056", url = Adr.EXCLUDES_NOTHING_IS_RECONCILED, type = "adr")
    void accountsForEveryEntryAcrossAResumedWalk(@TempDir Path root) throws IOException {
        Files.createDirectories(root.resolve("one/deeper"));
        Files.writeString(root.resolve("one/a.txt"), "a");
        Files.writeString(root.resolve("one/deeper/b.txt"), "b");
        Files.writeString(root.resolve("c.txt"), "c");
        Ledger ledger = ledger();

        recorder(stoppingAfterTheFirstDirectory()).walk(root);
        WalkId walkId = recorder().walk(root);

        claim(
                "the three files written are recorded once each, no session having repeated another's work",
                () -> assertThat(ledger.occurrenceCount(walkId)).isEqualTo(3));
        claim(
                "reconciliation passed, which is the only way walk() returns at all once a walk finishes",
                () -> assertThat(ledger.walkFinished(walkId)).isTrue());
    }

    @Test
    @Story("Nothing goes unaccounted for")
    @DisplayName("A walk carrying an anomaly is reconciled with that anomaly in the arithmetic")
    @Issue("6")
    @Link(name = "ADR-056", url = Adr.EXCLUDES_NOTHING_IS_RECONCILED, type = "adr")
    void reconciliationCountsAnomaliesAndNotOnlyOccurrences(@TempDir Path root) throws IOException {
        Files.writeString(root.resolve("ordinary.txt"), "a");
        Path unstorable;
        try {
            unstorable = root.resolve("orphan-" + (char) 0xD800 + ".txt");
            Files.writeString(unstorable, "content");
        } catch (InvalidPathException | IOException e) {
            abort("this filesystem will not create a name with an unpaired surrogate: " + e.getMessage());
        }
        Ledger ledger = ledger();
        AnomalyLog anomalyLog = anomalyLog();

        WalkId walkId = new WalkRecorder(ledger, anomalyLog, transactionManager()).walk(root);

        claim(
                "one of the two files became an anomaly rather than an occurrence, so the anomaly term is"
                        + " not zero and the check has to count it to balance",
                () -> assertThat(anomalyLog.anomalyCount(walkId)).isEqualTo(1));
        claim(
                "the other became an occurrence",
                () -> assertThat(ledger.occurrenceCount(walkId)).isEqualTo(1));
        claim(
                "and the walk finished, which it only does once reconciliation has balanced",
                () -> assertThat(ledger.walkFinished(walkId)).isTrue());
    }

    @Test
    @Story("A walk resumes where it stopped")
    @DisplayName("Work a stopped session had reported but not committed is walked again, not lost")
    @Issue("5")
    @Link(name = "ADR-055", url = Adr.A_WALK_IS_RESUMED_UNDER_ITS_OWN_ID, type = "adr")
    void repeatsWorkReportedButNeverCommitted(@TempDir Path root) throws IOException {
        Files.createDirectories(root.resolve("one"));
        Files.writeString(root.resolve("one/a.txt"), "a");
        Files.createDirectories(root.resolve("two"));
        Files.writeString(root.resolve("two/b.txt"), "b");
        Ledger ledger = ledger();

        WalkId stopped = recorder(stoppingWithWorkStillBuffered()).walk(root);

        claim(
                "nothing was written for the entries the stopped session had reported, because it never"
                        + " reached a commit — which is what leaves the database at a point worth resuming from",
                () -> assertThat(ledger.occurrenceCount(stopped)).isZero());

        WalkId resumed = recorder().walk(root);

        claim(
                "the resumed session continued the same walk rather than minting a second",
                () -> assertThat(resumed).isEqualTo(stopped));
        claim(
                "both files are recorded exactly once: the entry the first session reported and dropped was"
                        + " walked again, not lost and not doubled",
                () -> assertThat(ledger.occurrencesForWalk(resumed))
                        .extracting(RecordedOccurrence::path)
                        .containsExactlyInAnyOrder(new OccurrencePath("one/a.txt"), new OccurrencePath("two/b.txt")));
    }


    /** One look at a folder, which is what two looks that saw the same thing amount to. */
    private static final int ONE_LOOK = 1;

    /** Two looks, kept apart because they did not see the same thing. */
    private static final int TWO_LOOKS = 2;

    /** Three looks: a folder that changed and changed back was looked at three times, not twice. */
    private static final int THREE_LOOKS = 3;

    /** The single file these fixtures put in the folder before anyone looks at it. */
    private static final int THE_ONE_FILE = 1;

    @Test
    @Story("Looking twice at a folder nothing has happened to is one look")
    @DisplayName("Looking again at an unchanged folder keeps the record of the first look")
    @Issue("191")
    @Disabled("waits on issue 191: nothing ever reuses a finished walk, so no run id survives one invocation and this cannot hold yet")
    @Link(name = "ADR-115", url = Adr.A_REPEATED_OBSERVATION_IS_DISCARDED_AND_A_RUN_IS_CONTINUED, type = "adr")
    void aSecondLookAtAnUnchangedFolderKeepsTheFirstLooksRecord(@TempDir Path root) throws IOException {
        Files.writeString(root.resolve("a.txt"), "hi");

        WalkId first = recorder().walk(root);
        WalkId second = recorder().walk(root);

        claim(
                "the second look answers with the first look's own record: nothing about the folder had"
                        + " changed, so there is only one thing here that was ever seen, and two records of"
                        + " seeing it would be the tool disagreeing with itself about how many times this"
                        + " folder has existed",
                () -> assertThat(second).isEqualTo(first));
        claim(
                "exactly " + ONE_LOOK + " record of looking at this folder is kept, which is the number of"
                        + " distinct things that were seen -- everything recorded later against a corpus"
                        + " points at one of these records, so a spare one is a second corpus nobody has",
                () -> assertThat(looksAt(root)).isEqualTo(ONE_LOOK));
        claim(
                "and the one file that was there is recorded once beneath it, not " + TWO_LOOKS + " times:"
                        + " the second look's copy of it went with the look that was discarded",
                () -> assertThat(ledger().occurrencesForWalk(first)).hasSize(THE_ONE_FILE));
    }

    @Test
    @Story("Looking twice at a folder nothing has happened to is one look")
    @DisplayName("Looking again after a file is added is a second look, kept separately")
    @Issue("191")
    @Link(name = "ADR-115", url = Adr.A_REPEATED_OBSERVATION_IS_DISCARDED_AND_A_RUN_IS_CONTINUED, type = "adr")
    void aSecondLookAtAChangedFolderIsItsOwnRecord(@TempDir Path root) throws IOException {
        Files.writeString(root.resolve("a.txt"), "hi");
        WalkId first = recorder().walk(root);
        Files.writeString(root.resolve("b.txt"), "and one more");

        WalkId second = recorder().walk(root);

        claim(
                "a folder with a file in it that was not there before is not the folder that was seen"
                        + " before, so the second look is its own record -- everything judged about the"
                        + " first one was judged without that file in front of it",
                () -> assertThat(second).isNotEqualTo(first));
        claim(
                "both records are kept, " + TWO_LOOKS + " of them, because what each of them saw is"
                        + " different and neither is a copy of the other",
                () -> assertThat(looksAt(root)).isEqualTo(TWO_LOOKS));
    }

    @Test
    @Story("Looking twice at a folder nothing has happened to is one look")
    @DisplayName("Something new that could not be recorded as a file still makes it a second look")
    @Issue("191")
    @Link(name = "ADR-115", url = Adr.A_REPEATED_OBSERVATION_IS_DISCARDED_AND_A_RUN_IS_CONTINUED, type = "adr")
    void somethingNewThatIsNotAFileStillMakesItASecondLook(@TempDir Path root) throws IOException {
        Files.writeString(root.resolve("a.txt"), "hi");
        WalkId first = recorder().walk(root);
        try {
            Files.writeString(root.resolve("orphan-" + (char) 0xD800 + ".txt"), "content");
        } catch (InvalidPathException | IOException e) {
            abort("this filesystem will not create a name with an unpaired surrogate: " + e.getMessage());
        }

        WalkId second = recorder().walk(root);

        claim(
                "the files are exactly the files that were there before, and the look is still a new one:"
                        + " something appeared that could not be taken in as a file, it was written down as"
                        + " such, and discarding this look would throw away the only note anyone has of it",
                () -> assertThat(second).isNotEqualTo(first));
        claim(
                "so " + TWO_LOOKS + " records are kept rather than one, for a difference that never touched"
                        + " a single file",
                () -> assertThat(looksAt(root)).isEqualTo(TWO_LOOKS));
    }

    @Test
    @Story("Looking twice at a folder nothing has happened to is one look")
    @DisplayName("A folder that changed and changed back is looked at afresh, not restored to an older record")
    @Issue("191")
    @Link(name = "ADR-115", url = Adr.A_REPEATED_OBSERVATION_IS_DISCARDED_AND_A_RUN_IS_CONTINUED, type = "adr")
    void aFolderThatChangedAndChangedBackIsLookedAtAfresh(@TempDir Path root) throws IOException {
        Files.writeString(root.resolve("a.txt"), "hi");
        WalkId first = recorder().walk(root);
        Path added = Files.writeString(root.resolve("b.txt"), "and one more");
        WalkId second = recorder().walk(root);
        Files.delete(added);

        WalkId third = recorder().walk(root);

        claim(
                "the third look is neither of the two before it: only the look immediately before is ever"
                        + " compared, so a folder that came back to how it started gets a fresh record"
                        + " rather than the oldest one handed back -- and anything a person approved while"
                        + " the extra file was there stays expired, which is the truthful answer",
                () -> assertThat(third).isNotEqualTo(first).isNotEqualTo(second));
        claim(
                "leaving " + THREE_LOOKS + " records: one per time the folder was seen to be different from"
                        + " the time before",
                () -> assertThat(looksAt(root)).isEqualTo(THREE_LOOKS));
    }

    /** How many records of looking at {@code root} the database holds. */
    private int looksAt(Path root) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM walk WHERE root = ?",
                Integer.class,
                Walk.canonicalRoot(root).toString());
    }

    /**
     * A session that reports an entry and then stops before any checkpoint is taken — the case the
     * recorder buffers for, and the one an interruption at a commit boundary never reaches.
     */
    private static WalkRecorder.Traversal stoppingWithWorkStillBuffered() {
        return (root, observer, resumeFrom) -> {
            observer.fileOccurrence(
                    new OccurrencePath("one/a.txt"), 1, java.time.Instant.EPOCH, java.time.Instant.EPOCH);
            return new Walk.Outcome(
                    root, new Walk.Progress(2, 2, 1, 0), false, "the test stopped this session mid-buffer");
        };
    }

    /**
     * A traversal that walks for real and stops as soon as the walk offers its first checkpoint —
     * the shape of a session killed partway, with the database left exactly at that checkpoint.
     */
    private static WalkRecorder.Traversal stoppingAfterTheFirstDirectory() {
        return (root, observer, resumeFrom) -> {
            StopAfterTheFirstCheckpoint stopping = new StopAfterTheFirstCheckpoint(observer);
            try {
                return Walk.walk(root, stopping, resumeFrom);
            } catch (StopAfterTheFirstCheckpoint.Stop stop) {
                return new Walk.Outcome(root, stopping.lastProgress, false, "the test stopped this session");
            }
        };
    }

    /** Passes everything through, then stops the walk the first time it offers a checkpoint. */
    private static final class StopAfterTheFirstCheckpoint implements Walk.Observer {

        private final Walk.Observer delegate;
        private Walk.Progress lastProgress = new Walk.Progress(0, 0, 0, 0);
        private boolean checkpointed;

        private StopAfterTheFirstCheckpoint(Walk.Observer delegate) {
            this.delegate = delegate;
        }

        @Override
        public void fileOccurrence(
                OccurrencePath path, long sizeInBytes, java.time.Instant lastModified, java.time.Instant creationTime) {
            delegate.fileOccurrence(path, sizeInBytes, lastModified, creationTime);
        }

        @Override
        public void anomaly(String pathRendering, WalkAnomalyKind kind, String detail) {
            delegate.anomaly(pathRendering, kind, detail);
        }

        @Override
        public void checkpoint(Checkpoint at, Walk.Progress progress) {
            delegate.checkpoint(at, progress);
            lastProgress = progress;
            if (checkpointed) {
                return;
            }
            checkpointed = true;
            throw new Stop();
        }

        /** Not an error: the way this test says "the machine stopped here". */
        private static final class Stop extends RuntimeException {
            private static final long serialVersionUID = 1L;
        }
    }
}
