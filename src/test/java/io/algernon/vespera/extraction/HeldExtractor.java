package io.algernon.vespera.extraction;

import io.algernon.vespera.corpus.DetectedFormat;
import io.algernon.vespera.corpus.DetectedSubtype;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A {@link DoclingExtractor} whose conversions do not answer until the test lets them, for a test about
 * how far ahead of the document being handed over stage 2 has already asked (ADR-176).
 *
 * <p>{@link ScriptedExtractor} answers at once, so how many calls were dispatched and how many had
 * answered are the same number a moment later. Held, the two come apart: what was dispatched is what
 * {@link #lookedUp()} counts, because the reader asks {@link #cached} once for every document it is
 * about to dispatch, on its own thread, before any worker is involved (ADR-140 section 3).
 *
 * <p>It holds no cache, so every lookup is a miss and every document is dispatched. {@link
 * #remembered()} lists what a caller asked it to keep, which is how a test claims that nothing was
 * kept.
 *
 * <p>Lives in this package for the reason {@link ScriptedExtractor} gives: the constructor it extends
 * is package-private.
 */
public final class HeldExtractor extends DoclingExtractor {

    private static final DoclingResponse CONVERTED =
            new DoclingResponse(ConversionStatus.SUCCESS, List.of(), 0d, null, "{}");

    private final List<String> lookedUp = new CopyOnWriteArrayList<>();

    private final List<String> remembered = new CopyOnWriteArrayList<>();

    private final List<CountDownLatch> held = new CopyOnWriteArrayList<>();

    private final AtomicInteger converting = new AtomicInteger();

    private final AtomicInteger interrupted = new AtomicInteger();

    private volatile boolean released;

    public HeldExtractor() {
        super(null, null);
    }

    /** The content hashes the cache was asked about, one for each document dispatched, in that order. */
    public List<String> lookedUp() {
        return List.copyOf(lookedUp);
    }

    /** The content hashes a caller asked this extractor to keep an answer for. */
    public List<String> remembered() {
        return List.copyOf(remembered);
    }

    /** How many held conversions were interrupted before they were let go. */
    public int interrupted() {
        return interrupted.get();
    }

    /**
     * Lets every conversion answer. The ones held at this moment are let go latest first, so they
     * answer in roughly the reverse of the order they were asked in. Conversions asked after this
     * answer at once.
     */
    public void release() {
        released = true;
        for (CountDownLatch conversion : held.reversed()) {
            conversion.countDown();
        }
    }

    /** Whether no conversion is under way, waiting up to {@code atMost} for the last one to end. */
    public boolean noneConvertingWithin(Duration atMost) throws InterruptedException {
        long deadline = System.nanoTime() + atMost.toNanos();
        while (converting.get() > 0 && System.nanoTime() < deadline) {
            Thread.sleep(10);
        }
        return converting.get() == 0;
    }

    /** Whether {@code count} conversions have reached this extractor and are held, waiting up to {@code atMost}. */
    public boolean holdingWithin(int count, Duration atMost) throws InterruptedException {
        long deadline = System.nanoTime() + atMost.toNanos();
        while (held.size() < count && System.nanoTime() < deadline) {
            Thread.sleep(10);
        }
        return held.size() >= count;
    }

    @Override
    public Optional<DoclingResponse> cached(String contentHash, ExtractorIdentity extractorIdentity) {
        lookedUp.add(contentHash);
        return Optional.empty();
    }

    @Override
    public void remember(String contentHash, ExtractorIdentity extractorIdentity, DoclingResponse response) {
        remembered.add(contentHash);
    }

    @Override
    public DoclingResponse convertUncached(Path file, DetectedFormat format, DetectedSubtype subtype) {
        converting.incrementAndGet();
        try {
            CountDownLatch conversion = new CountDownLatch(1);
            held.add(conversion);
            if (!released) {
                conversion.await();
            }
            return CONVERTED;
        } catch (InterruptedException e) {
            interrupted.incrementAndGet();
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while held: " + file.getFileName(), e);
        } finally {
            converting.decrementAndGet();
        }
    }
}
