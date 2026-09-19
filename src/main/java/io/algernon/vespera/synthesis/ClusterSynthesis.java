package io.algernon.vespera.synthesis;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.IntStream;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * One call per cluster, and the writing it comes back with (ADR-108, ADR-110).
 *
 * <p><b>Exemplar-first, never a rollup.</b> The call carries the cluster's own documents — highest
 * scoring first, each contributing the chunk it opens with — rather than a summary of each; a
 * rollup would cost a call per document and build exactly the per-document summary a synthesis doc
 * exists to refuse (ADR-021).
 *
 * <p><b>Injects {@link ChatModel} and nothing of this project's</b>, which is {@code ChunkEmbedder}
 * injecting {@code EmbeddingModel} one instrument along (ADR-110): a third-party bean crosses no
 * module boundary, and the options built here are Ollama's for the same reason that class's are.
 */
@Component
public class ClusterSynthesis {

    /**
     * How much of the window one call may work in, sent on every call (ADR-108).
     *
     * <p><b>Sent rather than left to the host</b>: unsent, {@code num_ctx} is whatever the serving
     * machine decided at startup — 4096, 32768 or 262144, picked from total VRAM — so the same corpus
     * synthesised on two machines could be two different corpora with nothing in the response saying
     * which one a reader is holding. An explicit value wins over every other source in Ollama's
     * precedence, and a value past the model's trained length is clamped down to it rather than
     * refused, which is what makes sending one here safe.
     *
     * <p><b>A code default rather than a gate</b> (ADR-108, ADR-114): the operator's path is already
     * five invocations, and a sixth required value nobody can reason about buys a stop, not
     * information.
     *
     * <p>8192, being the smallest window that comfortably holds several heading-led opening chunks
     * and is served by every model family this could run under. A cluster too large for it sends what
     * fits and says so, which ADR-108 already accepts.
     */
    public static final int CONTEXT_WINDOW = 8192;

    /**
     * The shape the answer has to arrive in, imposed rather than hoped for (ADR-106, ADR-108) — a
     * heading for the cluster, and the writing itself.
     *
     * <p>Ollama pushes this down as a decoding constraint rather than checking conformance, and no
     * primary source guarantees it — its own examples all validate client-side. Imposing it is what
     * makes the answer separable at all; what to do when it comes back unsatisfied is a later
     * ticket's.
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
     * How many tokens one word is budgeted as, deliberately more than one word costs.
     *
     * <p>No tokenizer on this side of the wire and no endpoint to ask for one (ADR-091), so the only
     * way to stay inside the window is to over-estimate: ordinary English prose runs nearer 1.3
     * tokens to the word, headings, punctuation, markup and anything not in English run higher, and
     * this is a corpus nobody has read.
     *
     * <p>The cost of being wrong is not symmetrical. Too cautious wastes part of a window; too
     * confident overruns it silently — the prompt is truncated, the answer looks like any other, and
     * a reader is handed writing about half a cluster with nothing saying so.
     */
    static final double TOKENS_PER_WORD = 2.0;

    /**
     * How much of the window is kept back for the answer, in tokens, and sent as the cap on it.
     *
     * <p>Reserved <em>and</em> sent — a reservation nothing enforces is arithmetic, not a limit, and
     * the answer would be free to run into the documents' room.
     *
     * <p>1024 is comfortably more than the few hundred words of connecting prose and a heading this
     * writes. An answer that hits it comes back marked as run out of room rather than finished.
     */
    public static final int REPLY_ALLOWANCE = 1024;

    /**
     * How much of the window is kept back for everything in the call that is not a document: the
     * instructions, the cluster's name, the seed path, the ordinals.
     *
     * <p>256 against instruction text of well under a hundred words, for the reason {@link
     * #TOKENS_PER_WORD} leans high: what it insures against is the two unfixed pieces — a long
     * derived label and a long path — since running out here costs a silently truncated prompt.
     */
    static final int INSTRUCTION_RESERVE = 256;

    /** Highest scoring first, which is the order ADR-108 sends the documents in. */
    private static final Comparator<Exemplar> CLOSEST_TO_THE_SEED_FIRST =
            Comparator.comparingDouble(Exemplar::score).reversed();

    /**
     * What Ollama's {@code done_reason} reads when an answer stopped because it reached the length
     * it was allowed, rather than because it was finished (ADR-108).
     */
    private static final String FINISH_REASON_LENGTH = "length";

    /** A citation: the bracketed ordinal a reader is meant to follow back to a document (ADR-109). */
    private static final Pattern CITATION = Pattern.compile("\\[(\\d+)\\]");

    private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();

    private final ChatModel chatModel;

    public ClusterSynthesis(ChatModel chatModel) {
        this.chatModel = chatModel;
    }

    /**
     * The writing for one cluster, from one call to {@code modelName}.
     *
     * <p>The model is named per call rather than read here: which model generates is configuration,
     * and this module may not read it (ADR-110, ADR-114).
     *
     * <p><b>Refuses before the call is made where the fill comes back empty</b> (ADR-121): writing
     * over no documents is the one row ADR-113's invariant cannot survive, and it is the floor under
     * a caller that forgot to ask {@link #nothingFitsIn} first — a defect in that caller, not a state
     * an archive can be in. Throws {@link IllegalStateException}, never {@link ClusterFaultException}:
     * no call was made, so there is nothing for a fault to be about.
     *
     * <p><b>Once a call comes back, every one of ADR-108's and ADR-109's four checks runs before its
     * text is believed</b> (ADR-111), in the order those records state them: the prompt-evaluation
     * ceiling, the answer running out of room, a schema failure, and a citation outside the range the
     * call itself minted. The first that fails throws {@link ClusterFaultException} carrying the
     * {@link ClusterFault} to record — a call that came back and was rejected, distinct from the
     * refusal above.
     */
    public SynthesisDoc docFor(ClusterCall call, String modelName, int contextWindow) {
        List<Exemplar> sent = whatFitsIn(contextWindow, call.exemplars());
        if (sent.isEmpty()) {
            throw new IllegalStateException("the cluster \"" + call.label() + "\" has no document that fits"
                    + " a call in a window of " + contextWindow + " tokens, so there is nothing to write"
                    + " over -- check nothingFitsIn before calling docFor rather than reaching this");
        }
        ChatResponse response =
                chatModel.call(new Prompt(promptFor(call, sent), optionsFor(modelName, contextWindow)));
        checkPromptEvaluationCeiling(response, contextWindow);
        checkAnswerDidNotRunOutOfRoom(response);
        Answer parsed = parseAnswer(response);
        checkCitations(parsed.prose(), sent.size());
        return new SynthesisDoc(parsed.title(), parsed.prose(), sent.size());
    }

    /**
     * Fails the cluster where the prompt was shifted (ADR-108): {@code prompt_eval_count} at or above
     * the window sent means part of the cluster's documents never reached the model at all, and the
     * answer covers less than it was asked about with nothing in it saying which part.
     */
    private static void checkPromptEvaluationCeiling(ChatResponse response, int contextWindow) {
        Integer promptTokens = response.getMetadata().getUsage().getPromptTokens();
        if (promptTokens != null && promptTokens >= contextWindow) {
            throw new ClusterFaultException(new ClusterFault(
                    ClusterFaultKind.PROMPT_EVALUATION_CEILING,
                    "prompt evaluation count " + promptTokens + " at or above the ceiling of "
                            + contextWindow + " token(s)"));
        }
    }

    /**
     * Fails the cluster where the answer stopped because it ran out of room (ADR-108): {@code
     * done_reason: "length"} arrives looking exactly like a finished answer, ending mid-sentence with
     * nothing saying it was cut off.
     */
    private static void checkAnswerDidNotRunOutOfRoom(ChatResponse response) {
        String finishReason = response.getResult().getMetadata().getFinishReason();
        if (FINISH_REASON_LENGTH.equals(finishReason)) {
            Integer completionTokens = response.getMetadata().getUsage().getCompletionTokens();
            throw new ClusterFaultException(new ClusterFault(
                    ClusterFaultKind.ANSWER_RAN_OUT_OF_ROOM,
                    "the answer stopped after " + completionTokens + " token(s), having run out of"
                            + " room"));
        }
    }

    /**
     * Fails the cluster where the answer cannot be read back into the shape the call imposed
     * (ADR-108): a schema is imposed on the call, but Ollama pushes it down as a decoding constraint
     * rather than checking conformance, so this is validated client-side.
     */
    private static Answer parseAnswer(ChatResponse response) {
        String answer = response.getResult().getOutput().getText();
        try {
            return JSON_MAPPER.readValue(answer, Answer.class);
        } catch (JacksonException e) {
            throw new ClusterFaultException(new ClusterFault(
                    ClusterFaultKind.SCHEMA_VIOLATION,
                    "the answer did not read back into a heading and its writing: " + e.getMessage()
                            + " (line " + e.getLocation().getLineNr() + ", column "
                            + e.getLocation().getColumnNr() + ")"));
        }
    }

    /**
     * Fails the cluster where a citation points outside {@code 1..documentsSent}, or the prose carries
     * none at all (ADR-109). The numbers are minted here, which is what makes a fabricated one out of
     * range rather than merely wrong; uncited prose is the one case "every citation is in range" would
     * otherwise pass by having nothing to check.
     *
     * <p><b>The two are kept as different failures.</b> ADR-109 states uncited prose as a clause of
     * its own beside the range check, so a shared detail of {@code citation 0} would leave an operator
     * unable to tell writing that rests on nothing from writing pointing below the first document.
     */
    private static void checkCitations(String prose, int documentsSent) {
        List<String> citations =
                CITATION.matcher(prose).results().map(match -> match.group(1)).toList();
        if (citations.isEmpty()) {
            throw new ClusterFaultException(new ClusterFault(
                    ClusterFaultKind.CITATION_NOT_IN_RANGE,
                    "no citation at all in the writing, against " + documentsSent + " document(s) sent"));
        }
        for (String citation : citations) {
            int ordinal = asOrdinal(citation);
            if (ordinal < 1 || ordinal > documentsSent) {
                throw new ClusterFaultException(new ClusterFault(
                        ClusterFaultKind.CITATION_NOT_IN_RANGE,
                        "citation " + citation + " against " + documentsSent + " document(s) sent"));
            }
        }
    }

    /**
     * What a bracketed run of digits compares as, which is only ever used to compare.
     *
     * <p>A bracketed number is not always a pointer at a document — {@code [20190412120000]} reads
     * as a plausible date — and nothing bounds what the model puts between brackets. Parsed as an
     * {@code int} that overflows would end the run instead of turning the cluster down like any other
     * out-of-range number; {@link Integer#MAX_VALUE} gets the same outcome with no second branch.
     *
     * <p><b>This number never reaches the operator.</b> What is kept against the cluster is the digits
     * the model actually wrote — a reason built from an invented number is one nobody could search
     * the writing for and find.
     */
    private static int asOrdinal(String digits) {
        try {
            return Integer.parseInt(digits);
        } catch (NumberFormatException tooLargeToPointAtAnything) {
            return Integer.MAX_VALUE;
        }
    }

    /**
     * The documents one call can carry, closest to the seed first, filled until the room runs out
     * (ADR-108).
     *
     * <p><b>No fixed number of them.</b> Eight short documents send all eight; four hundred sends what
     * fits, and the count returned is what lets the finished page disclose it was written from part
     * of the cluster.
     *
     * <p><b>A cluster too large is never skipped.</b> Refusing would leave the largest clusters — the
     * ones most worth connecting — with nothing written over them, and nothing is concealed by
     * sending part: the page lists every document regardless (ADR-104).
     *
     * <p><b>A document too large for an empty call is passed over, and the fill carries on</b> — it
     * can never be sent, so stopping on it would cost the whole cluster its writing, and sending it
     * anyway guarantees the silent overrun this budget exists to avoid. A document that merely does
     * not fit what is <em>left</em> stops the fill instead, since everything after it is further out.
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
     * How many words of documents a call in {@code contextWindow} has room for: the window, less the
     * answer's allowance and everything around the documents, converted at the pessimistic ratio
     * above.
     *
     * <p><b>Public because the refusal that reads it lives outside this module</b> (ADR-121):
     * {@code GenerationContextWindow.size()} refuses any window this is not greater than zero for,
     * since a window with no room for a single document would send every call in the corpus carrying
     * none. {@code pipeline} reading the formula is ADR-110's rule working, not an exception to it —
     * so leave this public rather than narrowing it back.
     */
    public static int roomForDocumentsIn(int contextWindow) {
        return (int) ((contextWindow - REPLY_ALLOWANCE - INSTRUCTION_RESERVE) / TOKENS_PER_WORD);
    }

    /**
     * Whether nothing among {@code exemplars} fits {@code contextWindow} at all (ADR-121): every one
     * is larger than the room, so the fill would be empty before a call is even made.
     *
     * <p>Read by the tasklet before {@link #docFor} is reached, so a cluster of unusually large
     * documents never costs a call — the other route to an empty fill ADR-121 names, where the window
     * itself is reasonable but one outsized cluster still gets nothing while its neighbours write fine.
     */
    public static boolean nothingFitsIn(int contextWindow, List<Exemplar> exemplars) {
        return whatFitsIn(contextWindow, exemplars).isEmpty();
    }

    /**
     * What every call this module makes is made under: the model, the window it may read in, and the
     * shape the answer has to arrive in.
     *
     * <p>Package-private rather than inlined above, so the integration test that puts these on a real
     * serving engine asserts about <em>this</em> request (#181) — checking whether the window
     * survives the framework, not whether a rebuilt copy of it would.
     */
    static OllamaChatOptions optionsFor(String modelName, int contextWindow) {
        return OllamaChatOptions.builder()
                .model(modelName)
                .numCtx(contextWindow)
                .numPredict(REPLY_ALLOWANCE)
                .outputSchema(ANSWER_SCHEMA)
                .build();
    }

    /** What the call says: what the cluster is, what it sits under, and the documents under their ordinals. */
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

    /** The shape the answer comes back in: a heading for the cluster, and the writing itself. */
    private record Answer(String title, String prose) {}
}
