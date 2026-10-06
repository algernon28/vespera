package io.algernon.vespera.embedding;

import java.util.Locale;
import java.util.Optional;
import org.springframework.web.client.HttpClientErrorException;

/**
 * Whether an Ollama model may be sent a document's text: the one check every call Vespera makes to
 * Ollama passes (ADR-202, extending ADR-197 section 6).
 *
 * <p>A loopback address alone does not keep text on this machine. Ollama serves a cloud model through
 * the local daemon and forwards every request to ollama.com. The labeller, embedding scoring and
 * generation each ask {@link #refusalOf} for the name they are about to send under, once, and send
 * nothing when it answers with a reason. There is one implementation of the rule, here, and it is a
 * static method rather than a bean so that no bean is added: the two stages that call it each take the
 * one {@link OllamaClient} bean as a constructor argument. {@code synthesis} may not name
 * {@code embedding} (ADR-110), so it never asks: {@code pipeline} asks for it and hands it a plain name.
 *
 * <p>This is the check on a model. The labeller's check of the endpoint it calls is its own and is not
 * here (ADR-202 section 5).
 */
public final class LocalOllamaModel {

    private LocalOllamaModel() {}

    /**
     * The reason {@code modelName} must not be sent anything, or empty where it is served on this machine.
     *
     * <p>It refuses in four cases, in this order. A name ending in {@code cloud} is refused from the
     * name alone, with no request to Ollama. Otherwise {@link OllamaClient#isRemote} is asked, and a
     * model it reports as remote is refused. A 404 from {@code /api/show} means the daemon serves no model
     * of that name, one never pulled, and is refused in those words. Any other failure to answer is
     * refused as well, whatever the cause: a model whose remoteness cannot be established is never
     * assumed local.
     *
     * @param modelName the Ollama model the caller will send under
     * @param ollama the client {@code /api/show} is asked through
     */
    public static Optional<String> refusalOf(String modelName, OllamaClient ollama) {
        if (modelName.toLowerCase(Locale.ROOT).endsWith("cloud")) {
            return Optional.of("the model " + modelName + " is a cloud model: Ollama forwards it to a hosted"
                    + " service, and no document's text may be sent there");
        }
        try {
            if (ollama.isRemote(modelName)) {
                return Optional.of("Ollama reports the model " + modelName + " as remote, so it is forwarded to a"
                        + " hosted service, and no document's text may be sent there");
            }
        } catch (HttpClientErrorException.NotFound neverPulled) {
            return Optional.of("Ollama serves no model named " + modelName + ": it has not been pulled on this"
                    + " machine, so nothing is sent to it");
        } catch (RuntimeException unknown) {
            return Optional.of("it could not be established that the model " + modelName + " runs on this"
                    + " machine (" + unknown.getMessage() + "), and a model is not assumed to be local");
        }
        return Optional.empty();
    }
}
