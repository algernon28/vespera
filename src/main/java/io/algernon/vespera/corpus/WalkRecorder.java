package io.algernon.vespera.corpus;

import io.algernon.vespera.ledger.Ledger;
import io.algernon.vespera.ledger.OccurrencePath;
import io.algernon.vespera.ledger.WalkId;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import org.springframework.stereotype.Component;

/**
 * Drives a live {@link Walk}, persisting what it finds: file occurrences to {@link Ledger},
 * anomalies to {@link AnomalyLog} — both under the one {@link WalkId} minted for the walk.
 */
@Component
public class WalkRecorder {

    private final Ledger ledger;
    private final AnomalyLog anomalyLog;

    public WalkRecorder(Ledger ledger, AnomalyLog anomalyLog) {
        this.ledger = ledger;
        this.anomalyLog = anomalyLog;
    }

    /** Walks {@code corpusRoot}, persisting everything it finds, and returns the walk's own id. */
    public WalkId walk(Path corpusRoot) throws IOException {
        WalkId walkId = ledger.startWalk(corpusRoot);
        Walk.walk(corpusRoot, new Walk.Observer() {
            @Override
            public void fileOccurrence(OccurrencePath path, long sizeInBytes, Instant lastModified) {
                ledger.fileOccurrence(walkId, path, sizeInBytes, lastModified);
            }

            @Override
            public void anomaly(String pathRendering, WalkAnomalyKind kind, String detail) {
                anomalyLog.anomaly(walkId, pathRendering, kind, detail);
            }
        });
        return walkId;
    }
}
