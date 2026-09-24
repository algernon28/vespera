package io.algernon.vespera.embedding;

import java.util.List;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.ollama.api.OllamaEmbeddingOptions;
import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.stereotype.Component;

/**
 * Embeds one chunk and stores its vector (ADR-084, ADR-085, #107) — {@code pipeline}'s only way into
 * {@code embedding} for this, the same shape {@link OllamaClient} already gives it for identity.
 *
 * <p><b>Idempotent at the row, not at the call.</b> A chunk already stored under the embedder
 * identity this call would produce is left alone rather than a second row overwriting or duplicating
 * the first — but composing that identity needs the returned vector's own length (see below), so the
 * embed call itself is not skipped on a cache hit. What this buys is a database that never gains a
 * duplicate or a corrupted row on a re-run; a re-run that also skips the network call is a further
 * optimisation nothing here needs yet.
 *
 * <p><b>{@code truncate: false} (ADR-091).</b> The runtime is the only tokenizer here, and it refuses
 * an input it cannot fit rather than silently returning a vector describing only the input's opening.
 * A refusal is not this call failing — {@link #embed} splits the rejected text in half and embeds each
 * half the same way, recursively, then averages the resulting vectors component-wise into the one
 * vector this chunk's ordinal is stored under, since ADR-084 stores a vector row per chunk and never
 * more than one. The path is live (ADR-144, #272): Ollama refuses an input past its batch size, 2,048
 * tokens by default, whatever the model's own context length, and 512 words of machine identifiers
 * can cost more than that. A single word too long to fit is split by code points, so the split always
 * ends in pieces the runtime takes. Only that refusal is split; any other reaches the caller at once.
 *
 * <p>No {@code dimensions} option is sent: nothing in this ticket asks for a truncated embedding, so
 * the identity's {@code outputDimension} is always the returned vector's own length, by construction
 * rather than by a check against a value nothing here ever sends.
 *
 * <p>Both sides of ADR-020's eventual comparison — seed chunks and corpus chunks — are embedded
 * through this one method with no instruction (ADR-084): an instruction-aware asymmetry would give
 * ADR-020's maximum-over-seeds no way to express a direction.
 */
@Component
public class ChunkEmbedder {

    /** The part of Ollama's length refusal that names the reason: {@code the input length exceeds the context length}. */
    private static final String INPUT_TOO_LONG = "input length exceeds";

    private final EmbeddingModel embeddingModel;
    private final OllamaClient ollamaClient;
    private final VectorCache vectorCache;

    ChunkEmbedder(EmbeddingModel embeddingModel, OllamaClient ollamaClient, VectorCache vectorCache) {
        this.embeddingModel = embeddingModel;
        this.ollamaClient = ollamaClient;
        this.vectorCache = vectorCache;
    }

    /**
     * Embeds {@code text} — one chunk at {@code ordinal} within the document {@code contentHash} names,
     * cut by the chunker/rule identity pair — under {@code modelName}, storing the result unless a
     * vector already exists under the exact identity this call would compose.
     */
    public void embed(
            String contentHash,
            String chunkerIdentity,
            String chunkingRuleIdentity,
            int ordinal,
            String text,
            String modelName) {
        ModelArtefact artefact = ollamaClient.artefactOf(modelName);
        float[] vector = embedRejectingOverflow(text, modelName);
        EmbedderIdentity identity =
                EmbedderIdentity.withoutInstruction(modelName, artefact.digest(), artefact.weightDtype(), vector.length);
        if (vectorCache.exists(contentHash, chunkerIdentity, chunkingRuleIdentity, ordinal, identity.value())) {
            return;
        }
        vectorCache.put(contentHash, chunkerIdentity, chunkingRuleIdentity, ordinal, identity.value(), vector);
    }

    private float[] embedRejectingOverflow(String text, String modelName) {
        try {
            return callOnce(text, modelName);
        } catch (NonTransientAiException rejected) {
            // ADR-144: only a refusal on length is the input's fault. Any other refusal would come back
            // for every piece too, so splitting it only multiplies the calls before the same failure.
            if (!isTooLong(rejected)) {
                throw rejected;
            }
            List<String> halves = splitInHalf(text);
            if (halves.size() < 2) {
                throw rejected;
            }
            float[] first = embedRejectingOverflow(halves.get(0), modelName);
            float[] second = embedRejectingOverflow(halves.get(1), modelName);
            return averaged(first, second);
        }
    }

    private float[] callOnce(String text, String modelName) {
        EmbeddingRequest request = new EmbeddingRequest(
                List.of(text), OllamaEmbeddingOptions.builder().model(modelName).truncate(false).build());
        return embeddingModel.call(request).getResult().getOutput();
    }

    /** Ollama's refusal of an input past its limit under {@code truncate: false}, as #272 recorded it. */
    private static boolean isTooLong(NonTransientAiException rejected) {
        return rejected.getMessage() != null && rejected.getMessage().contains(INPUT_TOO_LONG);
    }

    /**
     * Splits {@code text} into two roughly equal halves by word count, or, where it is a single word, by
     * code points (ADR-144). Only a single code point cannot split further, and no runtime refuses one
     * on length.
     */
    private static List<String> splitInHalf(String text) {
        String[] words = text.trim().split("\\s+");
        if (words.length >= 2) {
            int mid = words.length / 2;
            return List.of(
                    String.join(" ", List.of(words).subList(0, mid)),
                    String.join(" ", List.of(words).subList(mid, words.length)));
        }
        String word = words[0];
        int codePoints = word.codePointCount(0, word.length());
        if (codePoints < 2) {
            return List.of(text);
        }
        // By code point rather than by char, so a character outside the Basic Multilingual Plane is never
        // cut into two unpaired surrogates the runtime would read as neither.
        int mid = word.offsetByCodePoints(0, codePoints / 2);
        return List.of(word.substring(0, mid), word.substring(mid));
    }

    private static float[] averaged(float[] first, float[] second) {
        if (first.length != second.length) {
            throw new IllegalStateException("two halves of one chunk embedded to different dimensions ("
                    + first.length + " and " + second.length
                    + "), which one model reporting one native dimension should never produce");
        }
        float[] mean = new float[first.length];
        for (int i = 0; i < mean.length; i++) {
            mean[i] = (first[i] + second[i]) / 2f;
        }
        return mean;
    }
}
