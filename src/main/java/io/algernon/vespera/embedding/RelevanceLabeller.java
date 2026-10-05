package io.algernon.vespera.embedding;

import java.util.Optional;

/**
 * Something other than a person that answers a relevance question about one document (ADR-197).
 *
 * <p>It is an interface because which model labels is decided by a measurement and not by this code:
 * {@link OllamaRelevanceLabeller} is the first implementation, and a typed-decision head over an
 * embedding backbone is the planned second. Nothing that records or reads an answer knows which one
 * gave it beyond {@link #identity()}.
 *
 * <p><b>Nothing behind this interface may reach a service that is not on this machine.</b> A document's
 * opening is what is put to it, and the operator's archives can hold documents that must not leave.
 * An implementation that could be pointed elsewhere says so in {@link #refusal()}, and the caller does
 * not ask it anything while that is present.
 */
public interface RelevanceLabeller {

    /** What recorded provenance names as the thing that set a label, such as {@code ollama:qwen3:8b}. */
    String identity();

    /** Why this labeller must not be asked anything, or empty where it may be. */
    Optional<String> refusal();

    /**
     * The answer to one question, or empty where this labeller has none to give. Empty is never read
     * as "not relevant": the entry stays blank for the operator.
     */
    Optional<Boolean> answer(LabelQuestion question);
}
