package io.algernon.vespera.pipeline;

import io.algernon.vespera.corpus.DetectedFormat;
import io.algernon.vespera.extraction.DoclingDocumentTexts;
import io.algernon.vespera.extraction.DoclingExtractor;
import io.algernon.vespera.extraction.DoclingResponse;
import io.algernon.vespera.extraction.ResponseScope;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The control conversion (ADR-184): the one call stage 2 makes that is about no file occurrence. When
 * five failures in a row of either kind have been counted, this says whether the converter still
 * converts anything at all, so the five can be read as the files' own or as the converter's.
 *
 * <p>The step thread asks, and in this order (section 2): it waits until every call dispatched and not
 * yet taken has finished, so that the control conversion is the only call the sidecar is handling and
 * cannot be killed by another file's; it waits for {@code /health}; and it sends the shipped PDF alone,
 * as a PDF with no subtype -- the options stage 2 sends a born-digital PDF (ADR-100) -- through {@link
 * DoclingExtractor#convertUncached}, which reads and writes no cache. The PDF is never a file occurrence:
 * it gets no row, no verdict, no metric, no shingle and no fault, and it does not touch the extractor
 * identity. It is materialised outside the corpus root, as a temporary file removed afterwards.
 *
 * <p>It converted when the answer is a conversion whose text carries {@link #SENTENCE}. The converter's
 * own answers and failures otherwise count as not converting: an answer that is not a conversion or
 * lacks the sentence, a rejection, a timeout, a dropped connection, which is not retried. A local fault
 * (the shipped PDF missing, the temporary file unwritable) says nothing about the converter and
 * propagates as itself.
 *
 * <p>Each count that stops the step keeps its own tally; {@link #conversions} is how both learn that a
 * control conversion converted since they last looked, so that both start again at zero (section 2).
 * Step-scoped, like the counts, and read on the step thread only.
 *
 * <p>Lives in {@code pipeline}, beside the two counts, and not in {@code extraction}: so {@code
 * extraction}'s implementation version does not move (ADR-058).
 */
class ControlConversion {

    private static final Logger log = LoggerFactory.getLogger(ControlConversion.class);

    /** The one line of text the shipped PDF carries, in an uncompressed content stream. */
    static final String SENTENCE = "Vespera control document";

    private static final String RESOURCE = "control-conversion.pdf";

    private final DoclingExtractor extractor;
    private final SidecarRecovery sidecarRecovery;
    private final PendingConversions pending;

    private long conversions;

    ControlConversion(DoclingExtractor extractor, SidecarRecovery sidecarRecovery, PendingConversions pending) {
        this.extractor = extractor;
        this.sidecarRecovery = sidecarRecovery;
        this.pending = pending;
    }

    /**
     * What a count gets where nothing can send a control conversion: a converter that never converts it,
     * so a count that reaches five stops as it did before ADR-184. For a caller built without the
     * collaborators the real one needs.
     */
    static ControlConversion never() {
        return new ControlConversion(null, null, null) {
            @Override
            boolean converts() {
                return false;
            }
        };
    }

    /** How many times a control conversion has converted in this step. */
    long conversions() {
        return conversions;
    }

    /**
     * Waits for every dispatched call, then for the sidecar's health, then sends the control conversion
     * alone.
     *
     * @return whether it converted: a conversion whose text carries {@link #SENTENCE}
     * @throws DoclingDidNotComeBackException if the sidecar does not answer its health check within the
     *     bound (ADR-175 section 3)
     */
    boolean converts() {
        pending.awaitAllDispatched();
        sidecarRecovery.awaitHealthy();
        boolean converted = carriesTheSentence(send());
        if (converted) {
            conversions++;
        }
        return converted;
    }

    /**
     * Only the converter call's own failures count as not converting, and answer null. A missing
     * resource or a temporary file that cannot be written is a local fault, not the converter's, and
     * propagates.
     */
    private DoclingResponse send() {
        Path file = null;
        try {
            file = Files.createTempFile("vespera-control-conversion-", ".pdf");
            try (InputStream shipped = ControlConversion.class.getResourceAsStream(RESOURCE)) {
                if (shipped == null) {
                    throw new IllegalStateException("the control conversion's PDF is not shipped: " + RESOURCE);
                }
                Files.copy(shipped, file, StandardCopyOption.REPLACE_EXISTING);
            }
            try {
                return extractor.convertUncached(file, DetectedFormat.PDF, null);
            } catch (RuntimeException notConverted) {
                // A rejection, a timeout, a lost connection: the converter did not convert it. Not retried.
                log.info(
                        "Stage 2 (extraction): the control conversion did not come back: {}",
                        notConverted.toString());
                return null;
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

    private static boolean carriesTheSentence(DoclingResponse response) {
        if (response == null) {
            return false;
        }
        if (!(ResponseScope.of(response) instanceof ResponseScope.Conversion)) {
            log.info("Stage 2 (extraction): the control conversion failed because the answer was not a conversion");
            return false;
        }
        boolean carries = DoclingDocumentTexts.lines(response.rawResponse()).contains(SENTENCE);
        if (!carries) {
            log.info("Stage 2 (extraction): the control conversion failed because the answer lacks the sentence");
        }
        return carries;
    }
}
