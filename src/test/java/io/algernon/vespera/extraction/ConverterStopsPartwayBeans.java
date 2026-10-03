package io.algernon.vespera.extraction;

import io.algernon.vespera.corpus.DetectedFormat;
import io.algernon.vespera.corpus.DetectedSubtype;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.client.ResourceAccessException;

/**
 * The <b>real</b> {@link DoclingExtractor} over the <b>real</b> {@link ExtractionCache}, with a client
 * that counts every conversion it is asked for and can be told to stop answering after a number of
 * them, wherever in the corpus that falls -- the way {@code docling-serve} goes away partway through
 * stage 2 (ADR-180, #379).
 *
 * <p>{@link ConverterStopsAnsweringBeans} stops answering inside one named folder, so that the corpus
 * pass ahead of seed extraction cannot use up its count. This one counts the corpus itself, because
 * the pass it stops is stage 2's.
 *
 * <p>What it answers is read off the occurrence's own name and bytes, so a corpus can carry every
 * outcome stage 2 records inside a chunk, and the outcome it records only at the end of the step:
 *
 * <ul>
 *   <li>a name containing {@link #WITHOUT_TEXT} converts with no text, which the degeneracy floor's
 *       first tier removes;
 *   <li>a name containing {@link #REFUSED} fails in a way Docling blames on the document, which is an
 *       extraction-failed verdict with a metric row beside it;
 *   <li>a name containing {@link #CONVERTER_FAULT} fails in a way the converter blames on itself, which
 *       is held as a fault until the end of the step (ADR-139);
 *   <li>anything else converts, and its text is the occurrence's own bytes, so each one's shingles
 *       differ and a count of them says something.
 * </ul>
 *
 * <p>Every answer that converts carries a mean confidence of {@link #MEAN_SCORE}, so stage 3's
 * confidence distribution counts it. The answers are also cached (the same production seam {@link
 * CountingDoclingBeans} uses), so a test that wants to see which occurrences a later invocation asked
 * about empties {@code extraction_cache} first. The cache is keyed outside the run, and emptying it
 * changes nothing a run id is derived from.
 *
 * <p>The arming and the count are static, because the client is one bean per Spring context and every
 * method of a class shares it. A test resets them in both {@code @BeforeEach} and {@code @AfterEach}.
 *
 * <p>{@code @TestConfiguration} rather than {@code @Configuration}, for the reason {@code
 * StubbedExtractionBeans} documents.
 */
@TestConfiguration
public class ConverterStopsPartwayBeans {

    /** The part of a name that makes the converter return no text for it. */
    public static final String WITHOUT_TEXT = "without-text";

    /** The part of a name that makes the converter refuse it as a property of the document. */
    public static final String REFUSED = "refused";

    /** The part of a name that makes the converter fail on it while blaming itself. */
    public static final String CONVERTER_FAULT = "converter-fault";

    /** The mean confidence every converted answer carries: inside the top grade, so it is bucketed. */
    public static final double MEAN_SCORE = 0.95;

    /** What the client throws once it has stopped answering, as the real one does when the sidecar is gone. */
    public static final String CONNECTION_FAILURE =
            "I/O error on POST request for http://localhost:5001/v1/convert/file: Unexpected end of file from server";

    /** The converter's own message for a document it could not read. */
    public static final String REFUSAL_MESSAGE = "the document backend could not read this document";

    /** The converter's own message when it blames itself. */
    public static final String FAULT_MESSAGE = "the converter had no worker free to take this document";

    private static final String WITHOUT_ANY_TEXT = "{\"document\":{\"json_content\":{\"texts\":[]}}}";

    private static final AtomicBoolean ARMED = new AtomicBoolean();

    private static final AtomicInteger ANSWERS_LEFT = new AtomicInteger();

    private static final AtomicInteger CONVERSIONS = new AtomicInteger();

    /** Arms the stop: the next {@code answered} conversions are answered, and every one after them fails. */
    public static void stopAnsweringAfter(int answered) {
        ANSWERS_LEFT.set(answered);
        CONVERSIONS.set(0);
        ARMED.set(true);
    }

    /** Disarms it, which is the sidecar being brought back, and starts the count again from zero. */
    public static void keepAnswering() {
        ARMED.set(false);
        ANSWERS_LEFT.set(0);
        CONVERSIONS.set(0);
    }

    /** How many conversions reached the client since the last arming or disarming, refused ones included. */
    public static int conversions() {
        return CONVERSIONS.get();
    }

    @Bean
    DoclingClient doclingClient() {
        return new DoclingClient("unused") {

            @Override
            public void checkHealth() {}

            @Override
            public Map<String, String> version() {
                return Map.of("docling-serve", "1.32.0", "docling", "2.124.0");
            }

            @Override
            DoclingResponse convert(Path file, DetectedFormat format, DetectedSubtype subtype) {
                CONVERSIONS.incrementAndGet();
                if (ARMED.get() && ANSWERS_LEFT.getAndDecrement() <= 0) {
                    throw new ResourceAccessException(CONNECTION_FAILURE);
                }
                return answerFor(file);
            }
        };
    }

    @Bean
    ExtractionCache extractionCache(JdbcTemplate jdbcTemplate) {
        return new ExtractionCache(jdbcTemplate);
    }

    @Bean
    DoclingExtractor doclingExtractor(DoclingClient doclingClient, ExtractionCache extractionCache) {
        return new DoclingExtractor(doclingClient, extractionCache);
    }

    private static DoclingResponse answerFor(Path file) {
        String name = file.getFileName().toString();
        if (name.contains(CONVERTER_FAULT)) {
            return failing(FailureCategory.INTERNAL, FAULT_MESSAGE);
        }
        if (name.contains(REFUSED)) {
            return failing(FailureCategory.BACKEND_FAILURE, REFUSAL_MESSAGE);
        }
        if (name.contains(WITHOUT_TEXT)) {
            return converted(WITHOUT_ANY_TEXT);
        }
        return converted("{\"document\":{\"json_content\":{\"texts\":[{\"text\":\"" + jsonText(file) + "\"}]}}}");
    }

    private static DoclingResponse converted(String rawResponse) {
        return new DoclingResponse(
                ConversionStatus.SUCCESS,
                List.of(),
                0d,
                new ConfidenceScores(
                        null, null, null, null, MEAN_SCORE, MEAN_SCORE, QualityGrade.EXCELLENT, QualityGrade.EXCELLENT),
                rawResponse);
    }

    private static DoclingResponse failing(FailureCategory category, String message) {
        return new DoclingResponse(
                ConversionStatus.FAILURE,
                List.of(new DoclingError("document_backend", "docling", message, category, null)),
                0d,
                null,
                "{}");
    }

    /** The occurrence's own bytes as a JSON string body: the corpora this is used with are plain prose. */
    private static String jsonText(Path file) {
        try {
            return Files.readString(file).strip().replace("\\", "\\\\").replace("\"", "\\\"");
        } catch (IOException e) {
            throw new UncheckedIOException("the fixture could not read " + file, e);
        }
    }
}
