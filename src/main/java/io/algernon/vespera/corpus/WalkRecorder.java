package io.algernon.vespera.corpus;

import io.algernon.vespera.ledger.Ledger;
import io.algernon.vespera.ledger.OccurrencePath;
import io.algernon.vespera.ledger.ResumableWalk;
import io.algernon.vespera.ledger.WalkCounts;
import io.algernon.vespera.ledger.WalkId;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Drives a live {@link Walk}, persisting what it finds: file occurrences to {@link Ledger},
 * anomalies to {@link AnomalyLog} — both under the one {@link WalkId} the walk is recorded against.
 *
 * <p>It is where three decisions meet, and none of them belongs in {@link Walk}, which knows only
 * about a filesystem:
 *
 * <ul>
 *   <li><b>Mint or resume</b> (ADR-055). An unfinished walk over this root is continued under its
 *       own id; a new id is minted only where none exists.
 *   <li><b>What a checkpoint costs</b>. {@link Walk} offers one at every completed directory; this
 *       takes one every {@value #COMMIT_INTERVAL} entries, because a checkpoint is a commit and
 *       commit frequency is the setting ticket #14 measured as worth a factor of 450.
 *   <li><b>Excludes nothing</b> (ADR-056). The moment a walk finishes, its cumulative counts are
 *       checked against what the tables actually hold.
 * </ul>
 *
 * <p>Everything between two checkpoints is buffered and written in one transaction with the
 * checkpoint itself. That is not a batching optimisation but the thing that makes resuming correct:
 * a session that dies leaves the database at its last checkpoint exactly, with nothing recorded past
 * it, so the resumed walk re-walks precisely what it must and re-records nothing.
 */
@Component
public class WalkRecorder {

    private static final Logger log = LoggerFactory.getLogger(WalkRecorder.class);

    /**
     * How many entries are walked before a checkpoint is taken. Entries walked since the last
     * checkpoint are what a killed session repeats, so this trades that repetition against commit
     * cost — and the measured cost of committing per entry is what makes the trade one-sided.
     */
    static final int COMMIT_INTERVAL = 1_000;

    private final Ledger ledger;
    private final AnomalyLog anomalyLog;
    private final TransactionTemplate transactions;
    private final Traversal traversal;
    private final int commitInterval;

    // Explicit, because there are two constructors and Spring will not choose between them.
    @Autowired
    public WalkRecorder(Ledger ledger, AnomalyLog anomalyLog, PlatformTransactionManager transactionManager) {
        this(ledger, anomalyLog, new TransactionTemplate(transactionManager), Walk::walk, COMMIT_INTERVAL);
    }

    /**
     * The seam a test needs and production does not: a traversal that can be made to stop partway,
     * and a commit interval small enough that stopping lands after a checkpoint rather than before
     * the first one.
     *
     * <p>Resuming is the one behaviour here that cannot be observed without interrupting a walk, and
     * interrupting a real filesystem walk at a chosen entry is not something a fixture can arrange.
     */
    WalkRecorder(
            Ledger ledger, AnomalyLog anomalyLog, TransactionTemplate transactions, Traversal traversal, int commitInterval) {
        this.ledger = ledger;
        this.anomalyLog = anomalyLog;
        this.transactions = transactions;
        this.traversal = traversal;
        this.commitInterval = commitInterval;
    }

    /** How this recorder reaches the filesystem. {@link Walk#walk} in production, always. */
    @FunctionalInterface
    interface Traversal {
        Walk.Outcome traverse(Path root, Walk.Observer observer, Optional<Checkpoint> resumeFrom) throws IOException;
    }

    /**
     * Walks {@code root}, persisting everything it finds, and returns the walk's own id — a new one,
     * or the one an unfinished walk over this root already carries.
     *
     * @throws CheckpointMismatch if a resumed walk finds a tree that no longer matches its checkpoint
     * @throws ExcludesNothingViolation if a finished walk cannot account for every entry it met
     */
    public WalkId walk(Path root) throws IOException {
        Path canonical = Walk.canonicalRoot(root);
        Optional<ResumableWalk> unfinished = ledger.unfinishedWalk(canonical);

        WalkId walkId = unfinished.map(ResumableWalk::walkId).orElseGet(() -> ledger.startWalk(canonical));
        Optional<Checkpoint> resumeFrom =
                unfinished.flatMap(walk -> Checkpoint.of(walk.checkpointOrdinals(), walk.checkpointPath()));
        WalkCounts alreadyCounted = unfinished.map(ResumableWalk::counts).orElse(new WalkCounts(0, 0));

        if (unfinished.isPresent()) {
            log.info("Resuming walk {} of {} from {}", walkId.value(), canonical, resumeFrom.orElse(null));
        }

        Session session = new Session(walkId, alreadyCounted);
        Walk.Outcome outcome = traversal.traverse(canonical, session, resumeFrom);

        if (!outcome.finished()) {
            // Everything since the last checkpoint is dropped, which is what leaves the database at
            // a point the next session can resume from. The walk stays unfinished, and therefore
            // ineligible as run input, until a later session finishes it.
            log.warn("Walk {} of {} did not finish and will be resumed: {}", walkId.value(), canonical, outcome.stoppedBecause());
            return walkId;
        }

        session.finish(outcome.progress());
        reconcile(walkId);
        return walkId;
    }

    /**
     * The excludes-nothing check (ADR-056): the walk's own cumulative counts against an independent
     * count of what was written.
     *
     * <p>It catches miscounting, not miscategorisation. A file the walk recorded as an occurrence but
     * cannot actually read still balances here, and two such holes are known and recorded — an
     * ACL-denied file, whose denial Windows does not report at enumeration, and a volume mount point,
     * indistinguishable from a symlink.
     */
    private void reconcile(WalkId walkId) {
        WalkCounts counts = ledger.countsFor(walkId);
        long occurrences = ledger.occurrenceCount(walkId);
        long anomalies = anomalyLog.anomalyCount(walkId);

        if (counts.entriesSeen() != occurrences + anomalies + counts.directoriesEntered() - 1) {
            throw new ExcludesNothingViolation(
                    walkId, counts.entriesSeen(), counts.directoriesEntered(), occurrences, anomalies);
        }
    }

    /**
     * One session of one walk: what it has been told and not yet written, and the counts that go with
     * it.
     */
    private final class Session implements Walk.Observer {

        private final WalkId walkId;
        private final WalkCounts alreadyCounted;

        private final List<PendingOccurrence> occurrences = new ArrayList<>();
        private final List<PendingAnomaly> anomalies = new ArrayList<>();

        /** Entries this session had walked when it last committed, so the cadence covers empty ground. */
        private long entriesAtLastCommit;

        private Session(WalkId walkId, WalkCounts alreadyCounted) {
            this.walkId = walkId;
            this.alreadyCounted = alreadyCounted;
        }

        @Override
        public void fileOccurrence(OccurrencePath path, long sizeInBytes, Instant lastModified) {
            occurrences.add(new PendingOccurrence(path, sizeInBytes, lastModified));
        }

        @Override
        public void anomaly(String pathRendering, WalkAnomalyKind kind, String detail) {
            anomalies.add(new PendingAnomaly(pathRendering, kind, detail));
        }

        /**
         * Takes the offer when enough ground has been covered since the last time it was taken.
         *
         * <p>The measure is entries walked, not rows buffered. Buffering alone would leave a walk over
         * mostly-empty directories committing nothing for hours and then resuming from the very top:
         * the expensive thing a resumed walk avoids is re-entering directories, and a directory that
         * held no files still cost a listing to find that out.
         */
        @Override
        public void checkpoint(Checkpoint at, Walk.Progress progress) {
            if (progress.entriesSeen() - entriesAtLastCommit < commitInterval) {
                return;
            }
            transactions.executeWithoutResult(status -> {
                writeBuffered();
                ledger.recordProgress(walkId, at.encodedOrdinals(), at.pathRendering(), cumulative(progress));
            });
            entriesAtLastCommit = progress.entriesSeen();
        }

        private void finish(Walk.Progress progress) {
            transactions.executeWithoutResult(status -> {
                writeBuffered();
                ledger.finishWalk(walkId, cumulative(progress));
            });
        }

        /** This session's counts added to what earlier sessions of the same walk already recorded. */
        private WalkCounts cumulative(Walk.Progress progress) {
            return new WalkCounts(
                    alreadyCounted.entriesSeen() + progress.entriesSeen(),
                    alreadyCounted.directoriesEntered() + progress.directoriesEntered());
        }

        private void writeBuffered() {
            for (PendingOccurrence occurrence : occurrences) {
                ledger.fileOccurrence(walkId, occurrence.path(), occurrence.sizeInBytes(), occurrence.lastModified());
            }
            for (PendingAnomaly anomaly : anomalies) {
                anomalyLog.anomaly(walkId, anomaly.pathRendering(), anomaly.kind(), anomaly.detail());
            }
            occurrences.clear();
            anomalies.clear();
        }
    }

    private record PendingOccurrence(OccurrencePath path, long sizeInBytes, Instant lastModified) {}

    private record PendingAnomaly(String pathRendering, WalkAnomalyKind kind, String detail) {}
}
