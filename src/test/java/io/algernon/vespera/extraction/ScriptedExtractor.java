package io.algernon.vespera.extraction;

import java.net.SocketTimeoutException;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.Supplier;

/**
 * A {@link DoclingExtractor} that answers a prepared sequence instead of converting anything, so a
 * caller's judgement of a response can be pinned one response at a time — including the sequences a
 * streak rule needs, which no single response can express.
 *
 * <p>It lives in this package because it has to: {@link DoclingExtractor}'s constructor and
 * {@link DoclingCallTimedOut}'s are both package-private, deliberately, so a real extractor is only
 * ever built by {@code extraction}'s own wiring and only this client ever declares that a call went
 * unanswered. Subclassing from inside the package is the one way to script both without widening
 * either. The same reason {@code InMemoryCorpus} sits in {@code corpus}'s test package rather than
 * beside the tests that use it.
 *
 * <p>No HTTP and no cache: {@code super(null, null)} is safe because every method that would reach
 * either is overridden here. Which is also the point — what a caller depends on is a response or a
 * declared silence, nothing else, and a script makes that dependency the whole of the seam.
 * {@code DoclingClientTest} and {@code DoclingExtractorTest} pin the real client and the real cache.
 */
public final class ScriptedExtractor extends DoclingExtractor {

    private final Deque<Supplier<DoclingResponse>> answers = new ArrayDeque<>();

    private Supplier<DoclingResponse> defaultAnswer;

    private int conversions;

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
            throw new DoclingCallTimedOut(Path.of("scripted"), new SocketTimeoutException("scripted silence"));
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

    /** How many conversions have actually been asked of this extractor. */
    public int conversions() {
        return conversions;
    }

    @Override
    public DoclingResponse convert(Path file, String contentHash, ExtractorIdentity extractorIdentity) {
        return nextAnswer();
    }

    @Override
    public DoclingResponse convert(Path file, ExtractorIdentity extractorIdentity) {
        return nextAnswer();
    }

    private DoclingResponse nextAnswer() {
        conversions++;
        Supplier<DoclingResponse> answer = answers.poll();
        if (answer == null && defaultAnswer != null) {
            return defaultAnswer.get();
        }
        if (answer == null) {
            throw new IllegalStateException(
                    "the script has no answer for conversion " + conversions + ": the caller converted more"
                            + " documents than this extractor was told about, so whatever the test claims next"
                            + " would rest on an answer nobody chose");
        }
        return answer.get();
    }
}
