package io.algernon.vespera.extraction;

import io.algernon.vespera.ledger.OccurrenceId;
import io.algernon.vespera.ledger.RunId;
import java.util.Locale;
import java.util.Optional;

/**
 * Decides what each occurrence's call earns, and keeps the counts through {@link FailuresInARow}
 * (ADR-189 section 3).
 *
 * <p>Three outcomes come from a call that brought no response to judge (ADR-071, ADR-175): a timeout,
 * an HTTP error status, and a connection dropped twice. Each is {@code extraction-failed} against that
 * occurrence unless the count says the converter is at fault. The step then goes on, or stops, as the
 * caller acts on the decision: this class returns decisions and throws nothing.
 *
 * <p>Nothing is chunked here (ADR-091), and the shingles are the caller's. A conversion that clears the
 * degeneracy floor earns no verdict, so that it is a survivor.
 *
 * <p>Touched on the step thread only (ADR-140 section 3), like the counts it holds.
 */
public final class OccurrenceJudge {

    /** Docling's own category for an error it did not classify, and the reason's for a failure naming none. */
    private static final String UNCATEGORISED = FailureCategory.UNKNOWN.name().toLowerCase(Locale.ROOT);

    /** What a reason says when the failed response carried no error at all. */
    private static final String NO_CATEGORIZED_ERROR = "no categorized error was reported";

    private final ExtractionMetrics metrics;
    private final FailuresInARow failuresInARow;
    private final RunId extractionRun;
    private final Double confidenceFloor;

    public OccurrenceJudge(
            ExtractionMetrics metrics, FailuresInARow failuresInARow, RunId extractionRun, Double confidenceFloor) {
        this.metrics = metrics;
        this.failuresInARow = failuresInARow;
        this.extractionRun = extractionRun;
        this.confidenceFloor = confidenceFloor;
    }

    /**
     * What one answered call earns its occurrence, by the scope the answer is read as (ADR-183 section 1).
     * An answer given by a call made now ends the dropped row; one read from the cache, or a failure the
     * converter blamed on itself, leaves it as it is (ADR-184 section 4).
     */
    public OccurrenceDecision answered(OccurrenceId occurrence, DoclingResponse response, boolean fromCache) {
        return switch (ResponseScope.of(response)) {
            case ResponseScope.Conversion conversion -> {
                // ADR-070: partial_success never earns extraction-failed on its own, whatever errors it
                // carries -- degenerate-output is the only verdict reachable from here.
                endsTheDroppedRow(fromCache);
                failuresInARow.timeoutRowEnds();
                DegeneracyVerdict verdict =
                        metrics.writeAndJudge(occurrence, extractionRun, response, confidenceFloor);
                yield new OccurrenceDecision.Converted(
                        verdict.degenerate() ? Optional.of(verdict.reason()) : Optional.empty());
            }
            case ResponseScope.ReportedTimeout timeout -> {
                // The converter answered about this file, so below the flip the dropped row ends; once the
                // timeout row flips the occurrence is set aside and nothing else is touched.
                OccurrenceDecision decision = timeout(occurrence, timeout.error().errorMessage(), response);
                if (decision instanceof OccurrenceDecision.Failed) {
                    endsTheDroppedRow(fromCache);
                }
                yield decision;
            }
            case ResponseScope.DocumentScope documentScope -> {
                // ADR-143: the reason names the error that decided the reading, or says none was reported.
                endsTheDroppedRow(fromCache);
                failuresInARow.timeoutRowEnds();
                String reason = documentScope
                        .blamed()
                        .map(error -> error.category().name().toLowerCase(Locale.ROOT) + ": " + error.errorMessage())
                        .orElse(UNCATEGORISED + ": " + NO_CATEGORIZED_ERROR);
                metrics.write(occurrence, extractionRun, response);
                yield new OccurrenceDecision.Failed(reason);
            }
            case ResponseScope.ServiceScope serviceScope -> {
                failuresInARow.timeoutRowEnds();
                yield new OccurrenceDecision.SetAside(
                        serviceScope.category(), serviceScope.error().errorMessage());
            }
        };
    }

    /** A call that brought no response because it timed out, whichever side gave up first (ADR-071). */
    public OccurrenceDecision timedOut(OccurrenceId occurrence, String detail) {
        return timeout(occurrence, detail, null);
    }

    /**
     * What an error status earns (ADR-175 section 1). It is an answer, and about this file's call: it ends
     * both rows of unanswered calls as any answer does, and it is this file's verdict.
     */
    public OccurrenceDecision rejected(OccurrenceId occurrence, String message) {
        failuresInARow.timeoutRowEnds();
        failuresInARow.droppedRowEnds();
        return new OccurrenceDecision.Failed("rejected: " + message);
    }

    /**
     * What a connection dropped twice earns (ADR-175 section 2). Five such files in a row are read as the
     * sidecar's doing only if the control conversion then fails too (ADR-184 section 2).
     */
    public OccurrenceDecision droppedTwice(OccurrenceId occurrence, String recordedPath, String cause) {
        InARow row = failuresInARow.droppedTwice(recordedPath);
        return new OccurrenceDecision.DroppedTwice(
                "crashed the converter: docling-serve dropped the connection twice while converting this file: "
                        + cause,
                row);
    }

    /**
     * What an occurrence earns when stage 1 recorded no format for it (ADR-100): a verdict against that
     * occurrence and not the pass. The reason names the occurrence and the run the row was sought under,
     * which is all that tells it apart from a conversion that really failed.
     */
    public OccurrenceDecision noDetectedFormat(OccurrenceId occurrence, RunId byteLevelReductionRun) {
        failuresInARow.noEvidence();
        return new OccurrenceDecision.Failed("no detected format is recorded for occurrence " + occurrence.value()
                + " under run " + byteLevelReductionRun.value());
    }

    /**
     * What an occurrence earns when its file could not be read (ADR-210 section 5): its file would not hash,
     * would not open when its call lost its connection, or would not read whole to be converted in parts.
     * The converter was never asked, or never received the file, so this is no evidence about it: it
     * neither ends nor extends any row (ADR-184 section 4). {@code cause} is what the file system
     * reported.
     */
    public OccurrenceDecision couldNotBeRead(String cause) {
        failuresInARow.noEvidence();
        return new OccurrenceDecision.Failed("could not be read: " + cause);
    }

    /**
     * ADR-071: a timeout is document scope while it is isolated, and flips to the converter's once three
     * land in a row. {@code response} is {@code null} for a call that brought none; non-null for a
     * timeout the converter reported, which earns a metric row like any other document-scoped failure.
     * The set-aside carries {@code detail} unchanged: the streak is a fact about this pass, not about the
     * document, so it is not spliced into the message.
     */
    private OccurrenceDecision timeout(OccurrenceId occurrence, String detail, DoclingResponse response) {
        int row = failuresInARow.timedOut();
        if (row >= FailuresInARow.CONSECUTIVE_TIMEOUT_COUNT) {
            return new OccurrenceDecision.SetAside("timeout", detail);
        }
        if (response != null) {
            metrics.write(occurrence, extractionRun, response);
        } else {
            // No response, so nothing about the converter now (ADR-184 section 4).
            failuresInARow.noEvidence();
        }
        return new OccurrenceDecision.Failed("timeout: " + detail);
    }

    private void endsTheDroppedRow(boolean fromCache) {
        if (fromCache) {
            failuresInARow.noEvidence();
        } else {
            failuresInARow.droppedRowEnds();
        }
    }
}
