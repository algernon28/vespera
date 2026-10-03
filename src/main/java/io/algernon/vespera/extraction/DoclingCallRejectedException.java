package io.algernon.vespera.extraction;

import java.nio.file.Path;

/**
 * {@code docling-serve} answered one document's call with an HTTP error status (ADR-175): an answer,
 * and an answer about that call, so neither silence ({@link DoclingCallTimeoutException}) nor a
 * response whose {@code errors[]} says what went wrong.
 *
 * <p>Its own type so the call site can tell it apart by type, as it tells a timeout apart. What a
 * rejection earns the document is {@code pipeline}'s to decide, not this client's.
 */
public final class DoclingCallRejectedException extends RuntimeException {

    /** How much of the answer's body the message keeps, so one page of HTML cannot become one reason. */
    private static final int BODY_CHARACTERS_KEPT = 300;

    private final int status;
    private final String body;

    public DoclingCallRejectedException(Path file, int status, String body) {
        super("docling-serve answered HTTP " + status + " for " + file + ": " + trimmed(body));
        this.status = status;
        this.body = body;
    }

    /** The HTTP status the call was answered with. */
    public int status() {
        return status;
    }

    /** The answer's body, whole. */
    public String body() {
        return body;
    }

    private static String trimmed(String body) {
        return body.length() <= BODY_CHARACTERS_KEPT ? body : body.substring(0, BODY_CHARACTERS_KEPT);
    }
}
