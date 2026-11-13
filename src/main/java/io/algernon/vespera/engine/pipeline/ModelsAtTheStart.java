package io.algernon.vespera.engine.pipeline;

import io.algernon.vespera.engine.embedding.LocalOllamaModel;
import io.algernon.vespera.engine.embedding.OllamaClient;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * The refusal at the start of {@code vespera run} of a model it names that is not local (ADR-375 §2): the embedding
 * model first, then the generation model, each resolved as its step resolves it.
 *
 * <p>A cloud tag is refused from its name; otherwise ADR-202's check is asked once, and only a model it reports as
 * remote is refused. A 404 and a check that cannot be completed refuse nothing here.
 */
@Component
class ModelsAtTheStart {

    private static final String EMBEDDING = "embedding model";
    private static final String GENERATION = "generation model";
    private static final String REMOTE_MARK = " as remote, so it is forwarded to a hosted service";

    private final EmbeddingModelGate embeddingModel;
    private final GenerationModel generationModel;
    private final OllamaClient ollama;

    @Autowired
    ModelsAtTheStart(EmbeddingModelGate embeddingModel, GenerationModel generationModel, OllamaClient ollama) {
        this.embeddingModel = embeddingModel;
        this.generationModel = generationModel;
        this.ollama = ollama;
    }

    /** The line that ends the start, or empty where neither model is refused. */
    Optional<String> refusal() {
        Optional<String> embedding = resolved(() -> embeddingModel.modelName().orElse(null));
        if (embedding.isPresent()) {
            Optional<String> refused = refusalOf(EMBEDDING, embedding.get());
            if (refused.isPresent()) {
                return refused;
            }
        }
        Optional<String> generation = resolved(generationModel::name);
        return generation.isPresent() ? refusalOf(GENERATION, generation.get()) : Optional.empty();
    }

    /** A model that resolves to nothing, or whose profile cannot be read, is not checked. */
    private static Optional<String> resolved(java.util.function.Supplier<String> name) {
        try {
            String model = name.get();
            return model == null || model.isBlank() ? Optional.empty() : Optional.of(model);
        } catch (RuntimeException unresolved) {
            return Optional.empty();
        }
    }

    private Optional<String> refusalOf(String role, String model) {
        // ADR-202's check, once: a cloud tag is decided from the name, and only a cloud or remote reason refuses here.
        return LocalOllamaModel.refusalOf(model, ollama)
                .filter(words -> words.contains(" is a cloud model: ") || words.contains(REMOTE_MARK))
                .map(words -> "vespera run stopped at its start: the " + role + " " + model
                        + " was refused, so nothing was walked and no run was minted -- " + words);
    }
}
