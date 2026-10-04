package io.algernon.vespera.extraction;

import java.util.ArrayList;
import java.util.List;

/**
 * The three counts of failures in a row that decide whether stage 2 goes on (ADR-071, ADR-139,
 * ADR-175 section 3a, ADR-184): timeouts in a row, set-aside occurrences in a row, and occurrences in a
 * row that dropped the connection twice. With what follows from each: carry on, send the control
 * conversion, or stop (ADR-184 section 2).
 *
 * <p>Plain on purpose (ADR-140 section 3): not final, so that {@code pipeline} can scope it to the step;
 * not thread-safe; touched on the step thread only. The counts have to survive a chunk boundary, which
 * is why the instance lives as long as the step does.
 *
 * <p>Which occurrences are in a row is decided by what ends each row. An occurrence that brought no
 * evidence that the converter answers about files now (an answer from the cache, a timeout with no
 * response, a connection dropped twice, an occurrence with no recorded format) is marked, and
 * {@link #occurrenceCompleted()} then leaves the set-aside row as it is (ADR-184 section 4). A mark does
 * not outlive its occurrence: {@link #occurrenceStarted()} forgets it.
 */
public class FailuresInARow {

    /** ADR-071: three timeouts in a row flip the reading of a timeout from the document's to the converter's. */
    public static final int CONSECUTIVE_TIMEOUT_COUNT = 3;

    /** ADR-071: higher than the timeout count, because this one has to fire on a mix of categories. */
    public static final int CONSECUTIVE_SERVICE_SCOPE_FAILURE_COUNT = 5;

    /**
     * ADR-175 section 3a: five, the count the set-aside row uses, so that a sidecar that converts nothing
     * stops the step as soon whichever way it fails. Reaching it sends the control conversion; the step
     * stops only if that does not convert either (ADR-184).
     */
    public static final int CONSECUTIVE_DROPPED_TWICE_COUNT = 5;

    private final ControlConversion controlConversion;

    private int timeouts;
    private int setAsides;

    /**
     * The files that dropped the connection twice in a row, by the path census recorded each under. Kept,
     * not only counted, because the stop has to name the files: the chunk they are in rolls back, so
     * nothing else records which they were.
     */
    private final List<String> droppedTwice = new ArrayList<>();

    private boolean noEvidence;
    private boolean controlDue;

    public FailuresInARow(ControlConversion controlConversion) {
        this.controlConversion = controlConversion;
    }

    /** An occurrence starts: forgets whatever the last one that did not complete said. */
    public void occurrenceStarted() {
        noEvidence = false;
    }

    /** An occurrence completed: ends the set-aside row unless it brought no evidence; forgets that either way. */
    public void occurrenceCompleted() {
        if (noEvidence) {
            noEvidence = false;
            return;
        }
        setAsides = 0;
    }

    /** One more occurrence set aside: {@link InARow.Counted} below five, {@link InARow.Reached} at five and over. Sends nothing. */
    public InARow setAside() {
        setAsides++;
        if (setAsides >= CONSECUTIVE_SERVICE_SCOPE_FAILURE_COUNT) {
            controlDue = true;
            return new InARow.Reached(setAsides);
        }
        return new InARow.Counted(setAsides);
    }

    /**
     * Sends the control conversion the set-aside row is due: {@link InARow.ControlConverted} or
     * {@link InARow.ControlNotConverted} (with no recorded paths).
     *
     * @throws IllegalStateException, sending nothing, unless the last {@link #setAside()} was
     *     {@link InARow.Reached}
     */
    public InARow controlConversionAfterSetAsides() {
        if (!controlDue) {
            throw new IllegalStateException("no control conversion is due: the set-aside row has not reached its count");
        }
        controlDue = false;
        int reached = setAsides;
        ControlReading reading = ControlReading.of(controlConversion.send());
        if (reading.converted()) {
            controlConverted();
            return new InARow.ControlConverted(reached);
        }
        return new InARow.ControlNotConverted(reached, reading, List.of());
    }

    /** Counts a timeout and returns the timeout row's length. A flipped row is not started again. */
    int timedOut() {
        return ++timeouts;
    }

    /** Ends the timeout row. */
    void timeoutRowEnds() {
        timeouts = 0;
    }

    /** Ends the dropped-twice row. */
    void droppedRowEnds() {
        droppedTwice.clear();
    }

    /** Marks the occurrence as no evidence, so the set-aside row is left as it is. */
    void noEvidence() {
        noEvidence = true;
    }

    /**
     * Counts one occurrence that dropped the connection twice, marking it as no evidence. Below five the
     * row is {@link InARow.Counted}; at five the control conversion is sent and read.
     */
    InARow droppedTwice(String recordedPath) {
        droppedTwice.add(recordedPath);
        noEvidence = true;
        int row = droppedTwice.size();
        if (row < CONSECUTIVE_DROPPED_TWICE_COUNT) {
            return new InARow.Counted(row);
        }
        ControlReading reading = ControlReading.of(controlConversion.send());
        if (reading.converted()) {
            controlConverted();
            return new InARow.ControlConverted(row);
        }
        return new InARow.ControlNotConverted(row, reading, droppedTwice);
    }

    /** A control conversion that converted starts the other two rows again, never the timeout row. */
    private void controlConverted() {
        setAsides = 0;
        droppedTwice.clear();
    }
}
