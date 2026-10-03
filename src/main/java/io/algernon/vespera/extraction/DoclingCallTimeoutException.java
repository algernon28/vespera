package io.algernon.vespera.extraction;

import java.nio.file.Path;

/**
 * A call to {@code docling-serve} brought back no conversion in time (ADR-071): either the 5-minute
 * call budget passed with no response at all, or the sidecar answered that its own synchronous wait
 * had run out first (ADR-172, ADR-175). Neither is a response whose {@code errors[]} reports its own
 * {@code timeout} category, which is a distinct case. The message says which of the two it was,
 * because they point at different settings.
 *
 * <p>Deliberately its own type rather than a generic timeout exception, so the two readings ADR-071
 * folds into the same consecutive-streak logic — a client-side timeout and a Docling-reported one —
 * stay distinguishable at the call site by type, the same way {@link DoclingResponse#status()} lets a
 * reported failure be told apart from one. Interpreting either reading (document-scope-versus-
 * consecutive, the streak-of-3 split) is {@code pipeline}'s job, not this client's — this ticket only
 * makes the two cases distinguishable.
 */
public final class DoclingCallTimeoutException extends RuntimeException {

    DoclingCallTimeoutException(Path file, Throwable cause) {
        this("no response from docling-serve for " + file + " within the call timeout", cause);
    }

    private DoclingCallTimeoutException(String message, Throwable cause) {
        super(message, cause);
    }

    /** The sidecar answered that its own wait for {@code file}'s conversion ran out. */
    static DoclingCallTimeoutException sidecarGaveUp(Path file, Throwable cause) {
        return new DoclingCallTimeoutException(
                "docling-serve gave up on " + file + ": its own wait for a conversion ran out before an answer"
                        + " was ready",
                cause);
    }
}
