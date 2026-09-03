package io.algernon.vespera.extraction;

import java.nio.file.Path;

/**
 * A call to {@code docling-serve} exceeded the 5-minute call budget with no response at all
 * (ADR-071) — silence, not a signal from Docling, and therefore a distinct case from a response
 * whose {@code errors[]} reports its own {@code timeout} category.
 *
 * <p>Deliberately its own type rather than a generic timeout exception, so the two readings ADR-071
 * folds into the same consecutive-streak logic — a client-side timeout and a Docling-reported one —
 * stay distinguishable at the call site by type, the same way {@link DoclingResponse#status()} lets a
 * reported failure be told apart from one. Interpreting either reading (document-scope-versus-
 * consecutive, the streak-of-3 split) is {@code pipeline}'s job, not this client's — this ticket only
 * makes the two cases distinguishable.
 */
public final class DoclingCallTimedOut extends RuntimeException {

    DoclingCallTimedOut(Path file, Throwable cause) {
        super("no response from docling-serve for " + file + " within the call timeout", cause);
    }
}
