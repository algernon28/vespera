package io.algernon.vespera.extraction;

import io.algernon.vespera.corpus.DetectedFormat;
import io.algernon.vespera.corpus.DetectedSubtype;
import java.net.SocketTimeoutException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * A {@link DoclingExtractor} that answers a prepared sequence instead of converting anything, so a
 * caller's judgement of a response can be pinned one response at a time — including the sequences a
 * streak rule needs, which no single response can express.
 *
 * <p>It lives in this package because it has to: {@link DoclingExtractor}'s constructor and
 * {@link DoclingCallTimeoutException}'s are both package-private, deliberately, so a real extractor is only
 * ever built by {@code extraction}'s own wiring and only this client ever declares that a call went
 * unanswered. Subclassing from inside the package is the one way to script both without widening
 * either. The same reason {@code InMemoryCorpus} sits in {@code corpus}'s test package rather than
 * beside the tests that use it.
 *
 * <p>No HTTP and no cache: {@code super(null, null)} is safe because every method that would reach
 * either is overridden here. Which is also the point — what a caller depends on is a response or a
 * declared silence, nothing else, and a script makes that dependency the whole of the seam.
 * {@code DoclingClientTest} and {@code DoclingExtractorTest} pin the real client and the real cache.
 *
 * <p><b>Every field here is safe to touch from several threads at once, and that is a requirement
 * rather than a precaution</b> (ADR-140). Stage 2 converts at a width, so this extractor is called from
 * several threads at a time, and a plain queue or a plain counter would lose answers and miscount
 * conversions in exactly the runs that are about concurrency. What it is <em>not</em> is a stand-in for
 * the step's own serial drain: the conversion is where the threads are, and everything downstream of it
 * stays on one thread, which is what {@link #convertingThreads()} lets a test claim.
 */
public final class ScriptedExtractor extends DoclingExtractor {

    private final Queue<Supplier<DoclingResponse>> answers = new ConcurrentLinkedQueue<>();

    private volatile Supplier<DoclingResponse> defaultAnswer;

    private final AtomicInteger conversions = new AtomicInteger();

    private final List<DetectedFormat> formatsAsked = new CopyOnWriteArrayList<>();

    private final List<Optional<DetectedSubtype>> subtypesAsked = new CopyOnWriteArrayList<>();

    private final AtomicInteger converting = new AtomicInteger();

    private final AtomicInteger mostEverConvertingAtOnce = new AtomicInteger();

    private final Set<String> convertingThreads = ConcurrentHashMap.newKeySet();

    private volatile CyclicBarrier wave;

    private volatile long waitForTheWaveMillis;

    public ScriptedExtractor() {
        super(null, null);
    }

    /** Queues one answer: what Docling returns for the next conversion asked of this extractor. */
    public ScriptedExtractor answering(DoclingResponse response) {
        answers.add(() -> response);
        return this;
    }

    /** Queues {@code count} copies of the same answer, for a sequence a streak rule reads. */
    public ScriptedExtractor answering(int count, DoclingResponse response) {
        for (int i = 0; i < count; i++) {
            answering(response);
        }
        return this;
    }

    /**
     * Queues one silence: the next conversion gets no response at all (ADR-071's client-side timeout,
     * distinct from a response whose {@code errors[]} reports Docling's own {@code timeout} category).
     */
    public ScriptedExtractor timingOut() {
        answers.add(() -> {
            throw new DoclingCallTimeoutException(Path.of("scripted"), new SocketTimeoutException("scripted silence"));
        });
        return this;
    }

    /** Queues {@code count} silences in a row. */
    public ScriptedExtractor timingOut(int count) {
        for (int i = 0; i < count; i++) {
            timingOut();
        }
        return this;
    }

    /**
     * Sets what this extractor answers once its queued script runs out, for a caller that only cares
     * that every conversion succeeds and does not want to predict how many it will ask for.
     */
    public ScriptedExtractor thenAlwaysAnswering(DoclingResponse response) {
        defaultAnswer = () -> response;
        return this;
    }

    /**
     * Makes each conversion wait for {@code width} of them to be under way before any of them answers,
     * so that how many ran at once becomes a fact a caller can claim rather than a race it hopes won.
     *
     * <p>A real conversion takes long enough that several overlap on their own; a scripted one answers
     * instantly, so without this the peak below would read 1 against a caller that was doing everything
     * right. The wait is bounded and its expiry is not an error here: a caller that never assembles a
     * wave of {@code width} simply records the peak it did reach, and the claim it then fails is the one
     * about concurrency rather than one about a timeout. Once that happens the wait stops applying, so a
     * caller converting one at a time pays {@code atMost} once over the whole run and not once per
     * document.
     */
    public ScriptedExtractor holdingEachConversionUntil(int width, Duration atMost) {
        wave = new CyclicBarrier(width);
        waitForTheWaveMillis = atMost.toMillis();
        return this;
    }

    /** What each conversion was asked to convert the document as (ADR-100), in the order asked. */
    public List<DetectedFormat> formatsAsked() {
        return List.copyOf(formatsAsked);
    }

    /** The subtype alongside each of {@link #formatsAsked()}, absent where nothing narrowed the class. */
    public List<Optional<DetectedSubtype>> subtypesAsked() {
        return List.copyOf(subtypesAsked);
    }

    /** How many conversions have actually been asked of this extractor. */
    public int conversions() {
        return conversions.get();
    }

    /**
     * The most conversions that were ever under way at the same moment (ADR-140's width, observed).
     * Only meaningful alongside {@link #holdingEachConversionUntil}, which is what makes an overlap
     * happen rather than leaving it to how fast the answers come back.
     */
    public int mostEverConvertingAtOnce() {
        return mostEverConvertingAtOnce.get();
    }

    /**
     * The threads conversions ran on, named. What a caller claims with this is that the conversions
     * happened somewhere other than the thread it was invoked on — everything downstream of them stays
     * on that one thread (ADR-140 section 3), and a step-level task executor is what this would catch.
     */
    public Set<String> convertingThreads() {
        return Set.copyOf(convertingThreads);
    }

    @Override
    public DoclingResponse convert(
            Path file,
            String contentHash,
            ExtractorIdentity extractorIdentity,
            DetectedFormat format,
            DetectedSubtype subtype) {
        return convertOne(format, subtype);
    }

    @Override
    public DoclingResponse convert(
            Path file, ExtractorIdentity extractorIdentity, DetectedFormat format, DetectedSubtype subtype) {
        return convertOne(format, subtype);
    }

    /** The seam a worker thread reaches (ADR-140 section 3): the same scripted answer, on that thread. */
    @Override
    public DoclingResponse convertUncached(Path file, DetectedFormat format, DetectedSubtype subtype) {
        return convertOne(format, subtype);
    }

    private DoclingResponse convertOne(DetectedFormat format, DetectedSubtype subtype) {
        formatsAsked.add(format);
        subtypesAsked.add(Optional.ofNullable(subtype));
        convertingThreads.add(Thread.currentThread().getName());
        int nowConverting = converting.incrementAndGet();
        mostEverConvertingAtOnce.accumulateAndGet(nowConverting, Math::max);
        try {
            holdForTheRestOfTheWave();
            return nextAnswer();
        } finally {
            converting.decrementAndGet();
        }
    }

    private void holdForTheRestOfTheWave() {
        CyclicBarrier assembling = wave;
        if (assembling == null) {
            return;
        }
        try {
            assembling.await(waitForTheWaveMillis, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            // Timed out, or another conversion's wait did and broke the barrier for everyone. Either
            // way the peak above already records how many did arrive, which is the thing being claimed.
        }
    }

    private DoclingResponse nextAnswer() {
        int conversion = conversions.incrementAndGet();
        Supplier<DoclingResponse> answer = answers.poll();
        if (answer == null && defaultAnswer != null) {
            return defaultAnswer.get();
        }
        if (answer == null) {
            throw new IllegalStateException(
                    "the script has no answer for conversion " + conversion + ": the caller converted more"
                            + " documents than this extractor was told about, so whatever the test claims next"
                            + " would rest on an answer nobody chose");
        }
        return answer.get();
    }
}
