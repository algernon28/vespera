package io.algernon.vespera.pipeline;

import io.algernon.vespera.corpus.DetectedFormat;
import io.algernon.vespera.extraction.ControlConversion;
import io.algernon.vespera.extraction.ControlReading;
import io.algernon.vespera.extraction.DoclingExtractor;
import io.algernon.vespera.extraction.DoclingResponse;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Sends the control conversion (ADR-184): the one call stage 2 makes that is about no file occurrence.
 * {@code extraction} owns the interface, the count that asks for it and the reading of its answer
 * (ADR-189); sending it stays here because it waits for {@link PendingConversions} and {@link
 * SidecarRecovery}, which are {@code pipeline}'s, and its PDF is a {@code pipeline} resource.
 *
 * <p>The step thread asks, and in this order (ADR-184 section 2): it waits until every call dispatched
 * and not yet taken has finished, so that the control conversion is the only call the sidecar is handling
 * and cannot be killed by another file's; it waits for {@code /health}; and it sends the shipped PDF alone,
 * as a PDF with no subtype -- the options stage 2 sends a born-digital PDF (ADR-100) -- through {@link
 * DoclingExtractor#convertUncached}, which reads and writes no cache. The PDF is never a file occurrence:
 * it gets no row, no verdict, no metric, no shingle and no fault, and it does not touch the extractor
 * identity. It is materialised outside the corpus root, as a temporary file removed afterwards.
 *
 * <p>The converter's own failures (a rejection, a timeout, a dropped connection, which is not retried)
 * come back as no answer. A local fault (the shipped PDF missing, the temporary file unwritable) says
 * nothing about the converter and propagates as itself, as does {@link DoclingDidNotComeBackException}
 * from the health wait (ADR-175 section 3).
 *
 * <p>Step-scoped, like the counts that ask for it, and read on the step thread only.
 */
class DoclingControlConversion implements ControlConversion {

    private static final Logger log = LoggerFactory.getLogger(DoclingControlConversion.class);

    private static final String RESOURCE = "control-conversion.pdf";

    private final DoclingExtractor extractor;
    private final SidecarRecovery sidecarRecovery;
    private final PendingConversions pending;

    DoclingControlConversion(DoclingExtractor extractor, SidecarRecovery sidecarRecovery, PendingConversions pending) {
        this.extractor = extractor;
        this.sidecarRecovery = sidecarRecovery;
        this.pending = pending;
    }

    /**
     * Says why a control conversion that was answered did not convert, where it has a line for it. The
     * processor and the breaker call it after the send and before their own line, where the reading was
     * logged when it was made.
     */
    static void logReading(ControlReading reading) {
        switch (reading) {
            case NOT_A_CONVERSION ->
                    log.info("Stage 2 (extraction): the control conversion failed because the answer was not a conversion");
            case LACKS_THE_SENTENCE ->
                    log.info("Stage 2 (extraction): the control conversion failed because the answer lacks the sentence");
            case CONVERTED, NOT_ANSWERED -> {}
        }
    }

    @Override
    public Optional<DoclingResponse> send() {
        pending.awaitAllDispatched();
        sidecarRecovery.awaitHealthy();
        Path file = null;
        try {
            file = Files.createTempFile("vespera-control-conversion-", ".pdf");
            try (InputStream shipped = DoclingControlConversion.class.getResourceAsStream(RESOURCE)) {
                if (shipped == null) {
                    throw new IllegalStateException("the control conversion's PDF is not shipped: " + RESOURCE);
                }
                Files.copy(shipped, file, StandardCopyOption.REPLACE_EXISTING);
            }
            try {
                return Optional.ofNullable(extractor.convertUncached(file, DetectedFormat.PDF, null));
            } catch (RuntimeException notConverted) {
                // A rejection, a timeout, a lost connection: the converter did not convert it. Not retried.
                log.info(
                        "Stage 2 (extraction): the control conversion did not come back: {}",
                        notConverted.toString());
                return Optional.empty();
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } finally {
            if (file != null) {
                try {
                    Files.deleteIfExists(file);
                } catch (IOException ignored) {
                    // A temporary file left behind in the temp directory costs nothing.
                }
            }
        }
    }
}
