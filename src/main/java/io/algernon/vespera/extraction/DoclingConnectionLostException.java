package io.algernon.vespera.extraction;

import java.nio.file.Path;
import org.springframework.web.client.ResourceAccessException;

/**
 * The connection to {@code docling-serve} was lost under one document's call (ADR-175): refused, reset,
 * or closed with no status line. Not a timeout ({@link DoclingCallTimeoutException}) and not an answer
 * ({@link DoclingCallRejectedException}): nothing came back, and nothing says whether the document or
 * the sidecar is why.
 *
 * <p>Its own type so the call site can wait for the sidecar and ask once more. Deciding that is
 * {@code pipeline}'s job, not this client's.
 */
public final class DoclingConnectionLostException extends RuntimeException {

    public DoclingConnectionLostException(Path file, ResourceAccessException cause) {
        super("docling-serve dropped the connection while converting " + file + ": " + cause.getMessage(), cause);
    }
}
