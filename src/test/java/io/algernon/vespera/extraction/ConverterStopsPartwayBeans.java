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
import java.util.Optional;
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
 * <p><b>What it answers is decided by the order documents are first looked up in the cache and not
 * found there, never by their names.</b> The walk records a folder in the order the file system lists
 * it, which is not name order on every file system, and stage 2 reads occurrences in the order the
 * walk recorded them. A script keyed to names therefore put a scripted outcome wherever the file
 * system happened to list it: on one machine the document meant to fault fell after the stop, was
 * never answered, and the fault path went untested while every claim still passed. So a test
 * {@linkplain #script scripts} outcomes by position in the sequence of distinct documents looked up
 * and not found, and the first time that happens to a document, its outcome is pinned to its content
 * hash. Every later call for the same bytes -- in a resumed invocation, or over a second folder
 * holding the same corpus -- gets the same answer. The outcomes are:
 *
 * <ul>
 *   <li>{@link Outcome#WITHOUT_TEXT}: converts with no text, which the degeneracy floor's first tier
 *       removes;
 *   <li>{@link Outcome#UNCONVERTIBLE}: fails in a way Docling blames on the document, which is an
 *       extraction-failed verdict with a metric row beside it, written in the chunk;
 *   <li>{@link Outcome#CONVERTER_FAULT}: fails in a way the converter blames on itself, which is an
 *       extraction fault, its row written in the chunk it was set aside in and resolved at the end of a
 *       step that completed (ADR-139, ADR-220);
 *   <li>{@link Outcome#CONVERTS}, every position the script does not name: converts, and its text is
 *       the occurrence's own bytes, so each one's shingles differ and a count of them says something.
 * </ul>
 *
 * <p><b>A position, and whether the stop refuses the call, are decided at the lookup that misses, not
 * when the call reaches the client.</b> Stage 2 looks every occurrence up on the one thread that reads
 * them, in the order it reads them, before it hands the call to a worker (ADR-140); the workers reach
 * the client in whatever order they are scheduled, and a worker that finished its first call could
 * take queued ones before the other workers had started. Decided on arrival, the answers a stop left
 * then went to later occurrences and an earlier one was refused; a refusal thrown from here is no
 * skip, so it stops the stage at the first refused occurrence it reads, and a scripted outcome read
 * after that one was never processed. So the {@link DoclingExtractor}
 * here decides both as its lookup misses and the client only carries the decision out: positions and
 * a stop follow the order stage 2 reads in, on every schedule. A call no lookup of its own
 * bytes came before is decided as it arrives, on the thread that sends it: the control conversion, on
 * the thread that reads, and each part of a text over the converter's ceiling (ADR-178), on a worker.
 * No corpus used with this fixture holds such a text. Which file takes
 * a position is still the file system's to say, so a test that needs an outcome reached before a
 * stop still says so in a claim of its own.
 *
 * <p>Every answer that converts carries a mean confidence of {@link #MEAN_SCORE}, so stage 3's
 * confidence distribution counts it. The answers about the document -- every one but {@link
 * Outcome#CONVERTER_FAULT} -- are also cached (the same production seam {@link CountingDoclingBeans}
 * uses); a failure the converter blames on itself is never kept (ADR-183), so it reaches the client
 * again whenever it is read again. A test that wants to see which occurrences a later invocation asked
 * about therefore empties {@code extraction_cache} first, unless what the cache kept is what it is
 * about. The cache is keyed outside the run, and emptying it
 * changes nothing a run id is derived from. A cache hit never reaches the client, so it takes no
 * position. A lookup that misses and is followed by no call -- one read ahead and dropped at a stop,
 * or a later stage's read of the cache -- still takes its position and uses up an answer; its decision
 * waits until the next arming, disarming or miss for the same bytes.
 *
 * <p>The script, the pins, the arming, the count and the decisions waiting for their call are static,
 * because the client is one bean per Spring context and every method of a class shares it. {@link
 * #script} starts a test afresh; {@link #stopAnsweringAfter} and {@link #keepAnswering} reset the
 * stop, the count and the decisions still waiting, and leave the pins, so the pins outlive the
 * invocations of one test.
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
        /** Fails, blaming itself: a service-scope failure, written as an extraction fault in its chunk. */
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

    /** How many distinct documents have taken a position since the last {@link #script}. */
    private static final AtomicInteger FIRST_ASKED = new AtomicInteger();

    /** The outcome scripted for each position; absent means it converts. */
    private static final Map<Integer, Outcome> SCRIPT = new ConcurrentHashMap<>();

    /** The outcome each document was given the first time it took a position, by content hash. */
    private static final Map<String, Outcome> PINNED = new ConcurrentHashMap<>();

    /** Whether the call a missed lookup is about to place is answered, by content hash, until that call arrives. */
    private static final Map<String, Boolean> ANSWERED = new ConcurrentHashMap<>();

    /**
     * Starts a test afresh: forgets every pinned outcome and every position, and scripts the given
     * positions -- counted from 1, in the order distinct documents are first looked up and not found --
     * to the outcome each list names. A position named in two lists is a mistake in the test, and refused.
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

    /**
     * Arms the stop: the next {@code answered} conversions are answered, and every one after them fails,
     * counted in the order their lookups missed and not in the order they reach the client. A lookup
     * that misses and places no call uses up one of the {@code answered} all the same.
     */
    public static void stopAnsweringAfter(int answered) {
        ANSWERED.clear();
        ANSWERS_LEFT.set(answered);
        CONVERSIONS.set(0);
        ARMED.set(true);
    }

    /** Disarms it, which is the sidecar being brought back, and starts the count again from zero. */
    public static void keepAnswering() {
        ANSWERED.clear();
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
                String bytes = ContentHashing.sha256(file);
                Boolean decided = ANSWERED.remove(bytes);
                if (!(decided != null ? decided : decide(bytes))) {
                    throw new ResourceAccessException(CONNECTION_FAILURE);
                }
                return answer(PINNED.get(bytes), file);
            }
        };
    }

    @Bean
    ExtractionCache extractionCache(JdbcTemplate jdbcTemplate) {
        return new ExtractionCache(jdbcTemplate);
    }

    /** The real extractor, deciding what the client answers as each lookup misses: see the class comment. */
    @Bean
    DoclingExtractor doclingExtractor(DoclingClient doclingClient, ExtractionCache extractionCache) {
        return new DoclingExtractor(doclingClient, extractionCache) {

            @Override
            public Optional<DoclingResponse> cached(String contentHash, ExtractorIdentity extractorIdentity) {
                Optional<DoclingResponse> hit = super.cached(contentHash, extractorIdentity);
                if (hit.isEmpty()) {
                    ANSWERED.put(contentHash, decide(contentHash));
                }
                return hit;
            }
        };
    }

    /**
     * Pins the next scripted position to {@code bytes} if they have taken none, and says whether the
     * call about them is answered, using up one of the answers a stop left. A call the stopped converter
     * refuses still takes its position, so the positions are the order of asking, not of answering.
     */
    private static boolean decide(String bytes) {
        PINNED.computeIfAbsent(bytes, unseen -> SCRIPT.getOrDefault(FIRST_ASKED.incrementAndGet(), Outcome.CONVERTS));
        return !ARMED.get() || ANSWERS_LEFT.getAndDecrement() > 0;
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
