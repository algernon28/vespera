package io.algernon.vespera.pipeline;

/**
 * {@code docling-serve} dropped the connection twice under each of several file occurrences in a row
 * while still answering its health check (ADR-175 section 3a), and the control conversion sent after the
 * fifth did not convert (ADR-184): read as a sidecar that is up and converts nothing. One such file is
 * the file's doing, and so is a run of them when the control conversion still converts, in which case
 * this is not thrown. The chunk the stop ends in is rolled back, so none of that chunk's files is
 * removed. Files of the run in a chunk that had already committed stay removed, at most four of them.
 *
 * <p>Deliberately not a {@link ServiceScopeFailureException}, so the step does not skip it. Its message
 * is what the step's closing line names, through {@link StepFailure}.
 */
final class DoclingKeepsDroppingConnectionsException extends RuntimeException {

    DoclingKeepsDroppingConnectionsException(int filesInARow) {
        super("docling-serve dropped the connection twice on each of " + filesInARow
                + " files in a row while still answering its health check"
                + ", and did not convert the control document either");
    }
}
