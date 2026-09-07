package io.algernon.vespera.embedding;

/**
 * What the Ollama runtime reports about the model it is serving (ADR-091): the two parts of an
 * {@link EmbedderIdentity} that come from the runtime rather than from what the client sends.
 *
 * @param digest the <em>manifest</em> digest, over the layers and the modelfile — strictly more than
 *     the weights, which is what makes it satisfy ADR-084's purpose
 * @param weightDtype the dtype the weights are stored in, {@code F16} for a model nobody quantized
 */
public record ModelArtefact(String digest, String weightDtype) {}
