package io.algernon.vespera.synthesis;

import java.util.Comparator;
import java.util.List;
import java.util.stream.IntStream;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * One call per group, and the writing it comes back with (ADR-108, ADR-110).
 *
 * <p><b>Exemplar-first, never a rollup.</b> The call carries the group's own documents — highest
 * scoring first, each contributing the chunk it opens with — rather than a summary of each. A rollup
 * would cost a call per document across a corpus this size, and would build exactly the
 * per-document summary a synthesis doc exists to refuse (ADR-021).
 *
 * <p><b>It injects {@link ChatModel} and nothing of this project's</b>, which is {@code
 * ChunkEmbedder} injecting {@code EmbeddingModel} one instrument along (ADR-110). A Spring AI bean
 * is a third-party dependency rather than a module, so there is no boundary crossed here — and the
 * options this builds are Ollama's for the same reason that class already builds Ollama's.
 */
@Component
public class ClusterSynthesis {

    /**
     * How much of the window one call may work in, sent on every call (ADR-108).
     *
     * <p><b>Sent rather than left to the host.</b> Unsent, {@code num_ctx} is whatever the serving
     * machine decided at startup — 4096, 32768 or 262144, picked from total VRAM — so the same corpus
     * synthesised on two machines is two different corpora and nothing in the response says which one
     * a reader is holding. An explicit value wins over every other source in Ollama's precedence, and
     * a value past the model's trained length is clamped down to it rather than refused.
     *
     * <p><b>A code default rather than a gate</b> (ADR-108, and ADR-114's reasoning for the model
     * name): the operator's path is already five invocations, and a sixth required value — a number
     * most operators cannot reason about — buys a stop and no information.
     *
     * <p>8192 rather than something larger, because it is the smallest window that comfortably holds
     * several heading-led opening chunks and is served by every model family this could run under.
     * Nothing is lost by it that ADR-108 has not already accepted: a group too large for the window
     * sends what fits and says so in the document itself.
     */
    public static final int CONTEXT_WINDOW = 8192;

    /**
     * The shape the answer has to arrive in, imposed on the call rather than hoped for (ADR-106,
     * ADR-108) — a heading for the group, and the writing itself.
     *
     * <p>Ollama pushes this down to the inference server as a decoding constraint rather than
     * checking the output against it, and no primary source claims conformance is guaranteed: its own
     * examples all validate client-side afterwards. Imposing it is what makes the answer separable at
     * all; what to do when it comes back unsatisfied is a later ticket's.
     */
    private static final String ANSWER_SCHEMA =
            """
            {
              "type": "object",
              "properties": {
                "title": {"type": "string"},
                "prose": {"type": "string"}
              },
              "required": ["title", "prose"]
            }""";

    /** Highest scoring first, which is the order ADR-108 sends the documents in. */
    private static final Comparator<Exemplar> CLOSEST_TO_THE_SEED_FIRST =
            Comparator.comparingDouble(Exemplar::score).reversed();

    private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();

    private final ChatModel chatModel;

    public ClusterSynthesis(ChatModel chatModel) {
        this.chatModel = chatModel;
    }

    /**
     * The writing for one group, from one call to {@code modelName}.
     *
     * <p>The model is named per call rather than read here: which model generates is configuration,
     * and this module may not read it (ADR-110, ADR-114).
     */
    public SynthesisDoc docFor(ClusterCall call, String modelName) {
        List<Exemplar> inScoreOrder =
                call.exemplars().stream().sorted(CLOSEST_TO_THE_SEED_FIRST).toList();
        String answer = chatModel
                .call(new Prompt(
                        promptFor(call, inScoreOrder),
                        OllamaChatOptions.builder()
                                .model(modelName)
                                .numCtx(CONTEXT_WINDOW)
                                .outputSchema(ANSWER_SCHEMA)
                                .build()))
                .getResult()
                .getOutput()
                .getText();
        Answer parsed = JSON_MAPPER.readValue(answer, Answer.class);
        return new SynthesisDoc(parsed.title(), parsed.prose(), inScoreOrder.size());
    }

    /** What the call says: what the group is, what it sits under, and the documents under their ordinals. */
    private static String promptFor(ClusterCall call, List<Exemplar> inScoreOrder) {
        String exemplars = IntStream.range(0, inScoreOrder.size())
                .mapToObj(index -> "[" + (index + 1) + "] " + inScoreOrder.get(index).leadingChunk())
                .reduce((first, second) -> first + "\n\n" + second)
                .orElse("");
        return """
                Write one connected piece over a group of %d document(s) from an archive.

                The group is called "%s", and it sits under the seed document "%s".

                Each document opens below under the number to cite it by. Connect them: say what they
                share, where they differ and what they amount to together. Do not summarise them one
                by one. Cite with the bracketed numbers, inline, and use no other citation of any
                kind.

                %s
                """
                .formatted(inScoreOrder.size(), call.label(), call.seedPath(), exemplars);
    }

    /** The shape the answer comes back in: a heading for the group, and the writing itself. */
    private record Answer(String title, String prose) {}
}
