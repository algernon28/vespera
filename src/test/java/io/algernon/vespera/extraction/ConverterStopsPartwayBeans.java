package io.algernon.vespera.extraction;

import io.algernon.vespera.corpus.DetectedFormat;
import io.algernon.vespera.corpus.DetectedSubtype;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.client.ResourceAccessException;

/**
 * The <b>real</b> {@link DoclingExtractor} over the <b>real</b> {@link ExtractionCache}, with a client
 * that counts every conversion it is asked for and can be told to stop answering after a number of
 * them, wherever in the corpus that falls -- the way {@code docling-serve} goes away partway through
 * stage 2 (ADR-181, #379).
 *
 * <p>{@link ConverterStopsAnsweringBeans} stops answering inside one named folder, so that the corpus
 * pass ahead of seed extraction cannot use up its count. This one counts the corpus itself, because
 * the pass it stops is stage 2's.
 *
 * <p><b>What it answers is decided by the order documents are first asked about, never by their
 * names.</b> The walk records a folder in the order the file system lists it, which is not name order
 * on every file system, and stage 2 reads occurrences in the order the walk recorded them. A script
 * keyed to names therefore put a scripted outcome wherever the file system happened to list it: on one
 * machine the document meant to fault fell after the stop, was never answered, and the fault path went
 * untested while every claim still passed. So a test {@linkplain #script scripts} outcomes by
 * position in the sequence of distinct documents the converter is first asked about, and the first
 * time a document is asked about, its outcome is pinned to its content hash. Every later call for the
 * same bytes -- in a resumed invocation, or over a second folder holding the same corpus -- gets the
 * same answer. The outcomes are:
 *
 * <ul>
 *   <li>{@link Outcome#WITHOUT_TEXT}: converts with no text, which the degeneracy floor's first tier
 *       removes;
 *   <li>{@link Outcome#UNCONVERTIBLE}: fails in a way Docling blames on the document, which is an
 *       extraction-failed verdict with a metric row beside it, written in the chunk;
 *   <li>{@link Outcome#CONVERTER_FAULT}: fails in a way the converter blames on itself, which is held
 *       as an extraction fault until the end of the step (ADR-139);
 *   <li>{@link Outcome#CONVERTS}, every position the script does not name: converts, and its text is
 *       the occurrence's own bytes, so each one's shingles differ and a count of them says something.
 * </ul>
 *
 * <p>Positions within one chunk's worth of reads are asked about in whatever order the worker threads
 * reach the client (ADR-140), so which occurrence of a chunk takes a position may differ from run to
 * run; which chunk it falls in does not, because the next chunk is read only after this one is
 * processed. A test that needs an outcome reached before a stop says so in a claim of its own, so an
 * order that defeats it fails loudly rather than passing an untested path.
 *
 * <p>Every answer that converts carries a mean confidence of {@link #MEAN_SCORE}, so stage 3's
 * confidence distribution counts it. The answers are also cached (the same production seam {@link
 * CountingDoclingBeans} uses), so a test that wants to see which occurrences a later invocation asked
 * about empties {@code extraction_cache} first. The cache is keyed outside the run, and emptying it
 * changes nothing a run id is derived from. A cache hit never reaches the client, so it takes no
 * position.
 *
 * <p>The script, the pins, the arming and the count are static, because the client is one bean per
 * Spring context and every method of a class shares it. {@link #script} starts a test afresh; {@link
 * #stopAnsweringAfter} and {@link #keepAnswering} reset only the stop and the count, so the pins
 * outlive the invocations of one test.
 *
 * <p>{@code @TestConfiguration} rather than {@code @Configuration}, for the reason {@code
 * StubbedExtractionBeans} documents.
 */
@TestConfiguration
public class ConverterStopsPartwayBeans {

    /** What the converter answers about one document. */
    public enum Outcome {
        /** Converts, with the document's own bytes as its text. */
        CONVERTS,
        /** Converts, with no text at all. */
        WITHOUT_TEXT,
        /** Fails, blaming the document: a document-scope failure. */
        UNCONVERTIBLE,
        /** Fails, blaming itself: a service-scope failure, held as an extraction fault. */
        CONVERTER_FAULT
    }

    /** The mean confidence every converted answer carries: inside the top grade, so it is bucketed. */
    public static final double MEAN_SCORE = 0.95;

    /** What the client throws once it has stopped answering, as the real one does when the sidecar is gone. */
    public static final String CONNECTION_FAILURE =
            "I/O error on POST request for http://localhost:5001/v1/convert/file: Unexpected end of file from server";

    /** The converter's own message for a document it could not read. */
    public static final String UNCONVERTIBLE_MESSAGE = "the document backend could not read this document";

    /** The converter's own message when it blames itself. */
    public static final String FAULT_MESSAGE = "the converter had no worker free to take this document";

    private static final String WITHOUT_ANY_TEXT = "{\"document\":{\"json_content\":{\"texts\":[]}}}";

    private static final AtomicBoolean ARMED = new AtomicBoolean();

    private static final AtomicInteger ANSWERS_LEFT = new AtomicInteger();

    private static final AtomicInteger CONVERSIONS = new AtomicInteger();

    /** How many distinct documents the client has been asked about since the last {@link #script}. */
    private static final AtomicInteger FIRST_ASKED = new AtomicInteger();

    /** The outcome scripted for each position in the order of first asking; absent means it converts. */
    private static final Map<Integer, Outcome> SCRIPT = new ConcurrentHashMap<>();

    /** The outcome each document was given the first time it was asked about, by content hash. */
    private static final Map<String, Outcome> PINNED = new ConcurrentHashMap<>();

    /**
     * Starts a test afresh: forgets every pinned outcome and every position, and scripts the given
     * positions -- counted from 1, in the order distinct documents are first asked about -- to the
     * outcome each list names. A position named in two lists is a mistake in the test, and refused.
     */
    public static void script(List<Integer> withoutText, List<Integer> unconvertible, List<Integer> converterFault) {
        Map<Integer, Outcome> scripted = new HashMap<>();
        withoutText.forEach(position -> scriptOnce(scripted, position, Outcome.WITHOUT_TEXT));
        unconvertible.forEach(position -> scriptOnce(scripted, position, Outcome.UNCONVERTIBLE));
        converterFault.forEach(position -> scriptOnce(scripted, position, Outcome.CONVERTER_FAULT));
        SCRIPT.clear();
        SCRIPT.putAll(scripted);
        PINNED.clear();
        FIRST_ASKED.set(0);
        keepAnswering();
    }

    private static void scriptOnce(Map<Integer, Outcome> scripted, int position, Outcome outcome) {
        if (scripted.putIfAbsent(position, outcome) != null) {
            throw new IllegalArgumentException("position " + position + " is scripted twice");
        }
    }

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
    DoclingClient doclingClient(@Value("${vespera.docling.image}") String configuredImage) {
        return new DoclingClient("unused") {

            @Override
            public void checkHealth() {}

            @Override
            public Map<String, String> version() {
                return SidecarVersionReport.runningImage(configuredImage);
            }

            @Override
            DoclingResponse convert(Path file, DetectedFormat format, DetectedSubtype subtype) {
                CONVERSIONS.incrementAndGet();
                Outcome outcome = outcomeOf(file);
                if (ARMED.get() && ANSWERS_LEFT.getAndDecrement() <= 0) {
                    throw new ResourceAccessException(CONNECTION_FAILURE);
                }
                return answer(outcome, file);
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

    /**
     * The outcome pinned to {@code file}'s bytes, pinning the next scripted position to them if they
     * have not been asked about before. A call the stopped converter refuses still takes its position,
     * so the positions are the order of asking, not of answering.
     */
    private static Outcome outcomeOf(Path file) {
        return PINNED.computeIfAbsent(
                ContentHashing.sha256(file),
                bytes -> SCRIPT.getOrDefault(FIRST_ASKED.incrementAndGet(), Outcome.CONVERTS));
    }

    private static DoclingResponse answer(Outcome outcome, Path file) {
        return switch (outcome) {
            case CONVERTER_FAULT -> failing(FailureCategory.INTERNAL, FAULT_MESSAGE);
            case UNCONVERTIBLE -> failing(FailureCategory.BACKEND_FAILURE, UNCONVERTIBLE_MESSAGE);
            case WITHOUT_TEXT -> converted(WITHOUT_ANY_TEXT);
            case CONVERTS ->
                converted("{\"document\":{\"json_content\":{\"texts\":[{\"text\":\"" + jsonText(file) + "\"}]}}}");
        };
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
