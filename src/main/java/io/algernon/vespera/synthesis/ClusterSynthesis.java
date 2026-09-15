package io.algernon.vespera.synthesis;

import java.util.ArrayList;
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

    /**
     * How many tokens one word is budgeted as, and it is deliberately more than one word costs.
     *
     * <p>There is no tokenizer on this side of the wire and there is no endpoint to ask for one
     * (ADR-091), so the only way to stay inside the window is to over-estimate what the text will cost
     * and leave room spare. Ordinary English prose runs nearer 1.3 tokens to the word; headings,
     * punctuation, markup and anything not in English run higher, and this is a corpus nobody has read.
     *
     * <p>The cost of being wrong is not symmetrical, which is why the number leans the way it does.
     * Too cautious wastes part of a window and sends fewer documents than would have fit. Too
     * confident overruns the window, and overrunning is silent: the middle of the prompt is dropped,
     * the answer comes back looking exactly like any other, and what a reader is handed is a piece of
     * writing about half a group with nothing anywhere saying so.
     */
    static final double TOKENS_PER_WORD = 2.0;

    /**
     * How much of the window is kept back for the answer, in tokens, and sent as the cap on it.
     *
     * <p>Reserved <em>and</em> sent, because a reservation nothing enforces is arithmetic rather than
     * a limit: the answer would be free to run into the room the documents were supposed to have.
     *
     * <p>1024 is comfortably more than a few hundred words of connecting prose and a heading, which is
     * what one of these is. An answer that hits this ceiling comes back marked as having run out of
     * room rather than as having finished, which is a thing to notice about that group.
     */
    public static final int REPLY_ALLOWANCE = 1024;

    /**
     * How much of the window is kept back for everything in the call that is not a document, in
     * tokens: the instructions, the group's name, the seed it sits under, and the ordinals.
     *
     * <p>256 against instruction text of well under a hundred words, for the same lopsided reason
     * {@link #TOKENS_PER_WORD} leans high: what this reserve is really insuring against is the two
     * pieces of it that are not fixed — a long derived label and a long path — and running out here
     * costs a truncated prompt nobody is told about.
     */
    static final int INSTRUCTION_RESERVE = 256;

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
    public SynthesisDoc docFor(ClusterCall call, String modelName, int contextWindow) {
        List<Exemplar> sent = whatFitsIn(contextWindow, call.exemplars());
        String answer = chatModel
                .call(new Prompt(promptFor(call, sent), optionsFor(modelName, contextWindow)))
                .getResult()
                .getOutput()
                .getText();
        Answer parsed = JSON_MAPPER.readValue(answer, Answer.class);
        return new SynthesisDoc(parsed.title(), parsed.prose(), sent.size());
    }

    /**
     * The documents one call can carry, closest to the seed first, filled until the room runs out
     * (ADR-108).
     *
     * <p><b>There is no fixed number of them.</b> A group of eight short documents sends all eight; a
     * group of four hundred sends what fits. What the caller gets back is what was actually sent, and
     * that count is what lets the finished page tell a reader it was written from part of the group.
     *
     * <p><b>A group too large is never skipped.</b> Refusing instead would leave the largest groups —
     * the ones most worth connecting — as the only ones with nothing written over them, and nothing is
     * concealed by sending part: the page lists every document in the group regardless (ADR-104), so
     * a reader sees all four hundred beneath prose written from forty.
     *
     * <p><b>A document too large for an empty call is passed over, and the fill carries on.</b> It is
     * the one document that can never be sent in any call, so stopping on it would cost the group its
     * writing entirely because of one outsized member — and sending it anyway would guarantee the
     * silent overrun this budget exists to avoid. Passing over it is the only choice left that still
     * writes about the group. A document that merely does not fit what is <em>left</em> stops the fill
     * where it stands, because everything after it is further from the seed still.
     */
    private static List<Exemplar> whatFitsIn(int contextWindow, List<Exemplar> exemplars) {
        int room = roomForDocumentsIn(contextWindow);
        List<Exemplar> sent = new ArrayList<>();
        int spent = 0;
        for (Exemplar exemplar : exemplars.stream().sorted(CLOSEST_TO_THE_SEED_FIRST).toList()) {
            if (exemplar.wordCount() > room) {
                continue;
            }
            if (spent + exemplar.wordCount() > room) {
                break;
            }
            sent.add(exemplar);
            spent += exemplar.wordCount();
        }
        return List.copyOf(sent);
    }

    /**
     * How many words of documents a call in {@code contextWindow} has room for: the window, less what
     * the answer is allowed and what everything around the documents is allowed, converted at the
     * pessimistic ratio above.
     */
    static int roomForDocumentsIn(int contextWindow) {
        return (int) ((contextWindow - REPLY_ALLOWANCE - INSTRUCTION_RESERVE) / TOKENS_PER_WORD);
    }

    /**
     * What every call this module makes is made under: the model, the window it may read in, and the
     * shape the answer has to arrive in.
     *
     * <p>Package-private rather than inlined above, so the integration test that puts these on a real
     * serving engine asserts about <em>this</em> request rather than about one it composed itself
     * (#181). What that test is checking is precisely whether the window survives the framework and
     * reaches the wire, and a test that rebuilt the options would answer that question about its own
     * copy of them.
     */
    static OllamaChatOptions optionsFor(String modelName, int contextWindow) {
        return OllamaChatOptions.builder()
                .model(modelName)
                .numCtx(contextWindow)
                .numPredict(REPLY_ALLOWANCE)
                .outputSchema(ANSWER_SCHEMA)
                .build();
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
