package io.algernon.vespera.synthesis;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.IntStream;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.ai.retry.NonTransientAiException;
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
     * and is served by every model family this could run under. A cluster too large for it is written
     * from what the serving engine counts as fitting rather than what this side estimates, and says so
     * (ADR-166), which ADR-108 already accepted the shape of.
     */
    public static final int CONTEXT_WINDOW = 8192;

    /**
     * The shape the answer has to arrive in, imposed rather than hoped for (ADR-106, ADR-108) — a
     * title for the cluster, and the writing itself.
     *
     * <p>Ollama pushes this down as a decoding constraint rather than checking conformance, and no
     * primary source guarantees it — its own examples all validate client-side. Imposing it is what
     * makes the answer separable at all, and {@link #parseAnswer} is where the {@code required} list
     * is read as the conformance check: both fields named there are required of the parsed record, and
     * a missing one faults the cluster (ADR-124, ADR-125).
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
     * How many tokens one word is priced at when a cluster's documents are first proposed, before the
     * serving engine ever counts the question itself (ADR-166 §5).
     *
     * <p>No tokenizer on this side of the wire and no endpoint to ask for one (ADR-091), so a word is
     * priced at a fixed ratio to decide what to propose first: ordinary English prose runs nearer 1.3
     * tokens to the word, headings, punctuation, markup and anything not in English run higher, and a
     * record-heavy file was measured at three to seven times this.
     *
     * <p><b>It no longer guards against an overrun.</b> Every proposal this ratio builds is counted by
     * the serving engine before it is answered (ADR-166 §1–§2), and what is actually sent is the
     * longest leading run of the proposal the engine counts inside the room — so a proposal this ratio
     * got wrong costs a round of counting, never a silently truncated prompt. Raising it would spare
     * most prose clusters that round; it is kept at 2.0 because raising it was measured to send about
     * the same documents in the end, for no change in what a reader is handed.
     */
    static final double TOKENS_PER_WORD = 2.0;

    /**
     * How much of the window is kept back for the answer, in tokens, and sent as the cap on it.
     *
     * <p>Reserved <em>and</em> sent — a reservation nothing enforces is arithmetic, not a limit, and
     * the answer would be free to run into the documents' room.
     *
     * <p>1024 is comfortably more than the few hundred words of connecting prose and a title this
     * writes. An answer that hits it comes back marked as run out of room rather than finished.
     */
    public static final int REPLY_ALLOWANCE = 1024;

    /**
     * How much of the window is kept back for everything in the call that is not a document: the
     * instructions, the cluster's name, the seed path, the ordinals.
     *
     * <p>256 against instruction text of well under a hundred words, for the reason {@link
     * #TOKENS_PER_WORD} leans high: what it insures against is the two unfixed pieces — a long
     * derived label and a long path — since running out here now costs the cluster an extra round of
     * counting (ADR-166 §2) rather than the silently truncated prompt it used to.
     */
    static final int INSTRUCTION_RESERVE = 256;

    /**
     * The length, in words, the instruction after the documents asks the answer to keep to
     * (ADR-161 §2).
     *
     * <p>Chosen by measurement, held under {@code REPLY_ALLOWANCE / TOKENS_PER_WORD} (512): {@code
     * REPLY_ALLOWANCE / TOKENS_PER_WORD} is 512 words, the most the allowance can hold with nothing
     * left over for the title or the JSON frame around the writing. 400 leaves about a fifth of the
     * allowance for those two — 400 * {@link #TOKENS_PER_WORD} is 800, comfortably under {@code
     * REPLY_ALLOWANCE}'s 1024 — and it is what was measured: the longest of 45 passing calls came to
     * 345 words and 529 of the 1024 tokens, barely half the allowance.
     *
     * <p><b>The model is not bound by it, and nothing checks it.</b> An answer longer than 400 words
     * that still finishes inside the allowance is believed, because words are not what the checks
     * below verify — {@code done_reason} is. This is a request that measurably keeps the answer to
     * the task, not a limit.
     */
    private static final int WORD_LIMIT = 400;

    /** Highest scoring first, which is the order ADR-108 sends the documents in. */
    private static final Comparator<Exemplar> CLOSEST_TO_THE_SEED_FIRST =
            Comparator.comparingDouble(Exemplar::score).reversed();

    /**
     * What Ollama's {@code done_reason} reads when an answer stopped because it reached the length
     * it was allowed, rather than because it was finished (ADR-108).
     */
    private static final String FINISH_REASON_LENGTH = "length";

    /**
     * The part of a serving runner's refusal of a prompt past the window that names the reason, as it
     * arrived from llama-server on Ollama 0.33.2 (#332): {@code
     * request (8280 tokens) exceeds the available context size (8192 tokens), try increasing it}.
     */
    private static final String RUNNER_REFUSES_PAST_THE_WINDOW = "exceeds the available context size";

    /**
     * The part of Ollama's own refusal of the same thing that names the reason, from its prompt-length
     * check when the prompt is not shifted: {@code the prompt is longer than the context length
     * currently available to the model} ({@code docs/research/ollama-generation-surface.md} §2).
     */
    private static final String OLLAMA_REFUSES_PAST_THE_WINDOW = "the prompt is longer than the context length";

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
     * <p><b>Refuses before any call is made where the word-based estimate comes back empty</b>
     * (ADR-121): writing over no documents is the one row ADR-113's invariant cannot survive, and it
     * is the floor under a caller that forgot to ask {@link #nothingFitsIn} first — a defect in that
     * caller, not a state an archive can be in. Throws {@link IllegalStateException}, never {@link
     * ClusterFaultException}: no call was made, so there is nothing for a fault to be about.
     *
     * <p><b>What is actually sent is found by counting, not estimating</b> (ADR-166 §1–§2): {@link
     * #sentAfterCounting} puts the word-based proposal to the serving engine itself, one counting call
     * at a time, and keeps only the longest leading run of it the engine counts inside the room. A
     * cluster none of whose documents fits at all — every one passed over — throws {@link
     * ClusterFaultException} here too, carrying {@link ClusterFaultKind#PROMPT_EVALUATION_CEILING}: a
     * counting call is still a call that came back, so this is not ADR-121's call-never-made case
     * even though its message reads similarly.
     *
     * <p><b>What comes back carries the documents it was written from, in the order their ordinals
     * were minted</b> (ADR-133). Counting drops a document too large for the whole window, so the
     * documents sent are not in general the highest-scoring ones the caller handed over, and this is
     * the last point at which which-under-which-number is known at all. A {@link SynthesisDoc}
     * therefore carries the list rather than its length, and the deliverable numbers its membership
     * from it instead of deriving score order a second time.
     *
     * <p><b>Once the answering call comes back, every one of ADR-108's and ADR-109's four checks runs
     * before its text is believed</b> (ADR-111), in the order those records state them: the
     * prompt-evaluation ceiling, the answer running out of room, a schema failure, and a citation
     * outside the range the call itself minted. The first that fails throws {@link
     * ClusterFaultException} carrying the {@link ClusterFault} to record — a call that came back and
     * was rejected, distinct from the refusal above.
     *
     * <p><b>A call the serving engine refuses as longer than the window fails the cluster too</b>, as
     * the ceiling it would otherwise have been checked against (#332): see {@link
     * #callTurningDownARefusalOnLength}. Kept as the backstop for a question the counting call found to
     * fit and the answering call did not (ADR-166 §4); every other refusal reaches the caller unchanged.
     *
     * <p><b>No check dereferences a response nothing has established is there</b> (ADR-123). The
     * ceiling reads response-level metadata, every field of which is guaranteed; the answer itself is
     * established once between that check and the next, and a response carrying none — or one whose
     * text is null or blank — is a {@link ClusterFaultKind#SCHEMA_VIOLATION} like any other answer
     * that could not be read into the shape the call imposed, not an exception out of the step.
     *
     * <p>The same holds one level down, at the parsed record (ADR-124, ADR-125): {@code title} and
     * {@code prose} are both required of an {@link Answer} the way they are required of {@code
     * ANSWER_SCHEMA}, {@link #parseAnswer} is where that is established, and every reader below it
     * goes unguarded only because of that — {@link #checkCitations} dereferencing {@code prose}, and
     * the {@link SynthesisDoc} built here carrying a title into a column that will not take a null one.
     * ADR-124 recorded that second half as owed while it was; with ADR-125 built it is spent, and the
     * rule holds whole for both fields.
     */
    public SynthesisDoc docFor(ClusterCall call, String modelName, int contextWindow) {
        if (nothingFitsIn(contextWindow, call.exemplars())) {
            throw new IllegalStateException("the cluster \"" + call.label() + "\" has no document that fits"
                    + " a call in a window of " + contextWindow + " tokens, so there is nothing to write"
                    + " over -- check nothingFitsIn before calling docFor rather than reaching this");
        }
        List<Exemplar> sent = sentAfterCounting(call, modelName, contextWindow);
        ChatResponse response = callTurningDownARefusalOnLength(
                new Prompt(promptFor(call, sent), optionsFor(modelName, contextWindow)), contextWindow);
        checkPromptEvaluationCeiling(response, contextWindow);
        Generation answer = answerIn(response);
        checkAnswerDidNotRunOutOfRoom(answer, response);
        Answer parsed = parseAnswer(answer);
        checkCitations(parsed.prose(), sent.size());
        // Which documents were sent, in the order the ordinals were minted over -- not how many
        // (ADR-133). This is the one place both are known, and the correspondence is unrecoverable
        // anywhere after it.
        return new SynthesisDoc(
                parsed.title(), parsed.prose(), sent.stream().map(Exemplar::occurrence).toList());
    }

    /**
     * What is actually sent to be answered: the longest leading run of a word-based proposal that the
     * serving engine itself counts inside the room, found by halving (ADR-166 §1–§2).
     *
     * <p><b>The proposal is counted whole first.</b> {@link #whatFitsIn} builds it exactly as ADR-108
     * and ADR-121 always have — closest to the seed first, a document larger than the estimated room
     * passed over, the fill stopping at the first document that does not fit what is left. If the
     * engine counts that whole proposal inside {@code contextWindow - REPLY_ALLOWANCE}, it is what is
     * sent. So is it where the engine reports no count at all (ADR-123): an absent count arrives as
     * {@code 0} and so always fits, which leaves the proposal standing exactly as it was estimated. A
     * refusal that is not about length is not this method's to catch — {@link #countQuestion} rethrows
     * it, and it reaches the step the way any other refusal ADR-166 §4 leaves alone does.
     *
     * <p><b>Otherwise the longest leading run that fits is found by halving</b>: between a run known to
     * fit (at first, none) and one known not to (at first, the whole proposal), the run halfway between
     * them is counted and the half its answer says is kept, until the boundary is pinned down — at most
     * {@code 1 + ceil(log2(n))} counting calls for a proposal of {@code n} documents, scaling by the
     * count itself having been measured to cost more.
     *
     * <p><b>A document that will not fit a call on its own is passed over</b>, exactly as ADR-108
     * always meant to but now decided by the engine's own count rather than the word estimate: when not
     * even the first document of a proposal fits by itself, it can never be sent in this window, so it
     * is dropped and a new proposal is filled from the documents left, back to the top.
     *
     * <p><b>A cluster none of whose documents fits at all faults</b> (ADR-166 §4): every document has
     * been passed over and there is nothing left to propose. The fault carries what the engine said of
     * the last question it was asked to count — its count, or its refusal verbatim — so the archive's
     * owner can see by how much without paying for a call again.
     */
    private List<Exemplar> sentAfterCounting(ClusterCall call, String modelName, int contextWindow) {
        List<Exemplar> remaining = call.exemplars();
        QuestionCount lastCounted = null;
        while (true) {
            List<Exemplar> proposed = whatFitsIn(contextWindow, remaining);
            if (proposed.isEmpty()) {
                throw noDocumentFitsTheWindow(lastCounted);
            }
            QuestionCount whole = countQuestion(call, proposed, modelName, contextWindow);
            lastCounted = whole;
            if (whole.fits()) {
                return proposed;
            }
            int low = 0;
            int high = proposed.size();
            while (high - low > 1) {
                int mid = (low + high) / 2;
                QuestionCount outcome = countQuestion(call, proposed.subList(0, mid), modelName, contextWindow);
                lastCounted = outcome;
                if (outcome.fits()) {
                    low = mid;
                } else {
                    high = mid;
                }
            }
            if (low > 0) {
                return proposed.subList(0, low);
            }
            Exemplar passedOver = proposed.get(0);
            remaining = remaining.stream().filter(exemplar -> exemplar != passedOver).toList();
        }
    }

    /**
     * The fault kept where every document of a cluster has been passed over (ADR-166 §4): none of them
     * fits the room by the serving engine's own count, so there is no shorter question left to try.
     *
     * <p>Recorded as {@link ClusterFaultKind#PROMPT_EVALUATION_CEILING} and not a fifth kind: this is
     * still the question that did not reach the model whole, only found out before any answering call
     * rather than after one (ADR-166 §4). Not ADR-121's call-never-made case either — a counting call
     * is a call that came back, refused or counted, and what came back did not survive checking.
     *
     * <p><b>Built through {@link ClusterFaultException#noDocumentFitsTheWindow}</b>, not the ordinary
     * constructor: no answering call was ever made for this fault, so the exception's own {@code
     * noAnswerWasAskedFor()} reads {@code true} — what a caller does with that fact is that caller's to
     * decide, not this method's.
     */
    private static ClusterFaultException noDocumentFitsTheWindow(QuestionCount lastCounted) {
        String engineSaid = lastCounted == null ? "no document was ever counted" : lastCounted.detail();
        return ClusterFaultException.noDocumentFitsTheWindow(new ClusterFault(
                ClusterFaultKind.PROMPT_EVALUATION_CEILING,
                "no document of the cluster fits the window by the serving engine's own count -- the last"
                        + " question counted: " + engineSaid));
    }

    /**
     * Counts {@code documents} the way the answering call itself will ask about them: the same
     * request, {@code num_predict: 1}, its one token of answer never read (ADR-166 §1) — identical on
     * purpose, so the engine's prompt cache serves the answering call's evaluation from this one's.
     *
     * <p><b>A refusal on length does not fit</b>, told apart from every other refusal by its text
     * exactly as {@link #isRefusedAsPastTheWindow} tells it apart for the answering call, itself
     * carried over from how {@code ChunkEmbedder} tells its own refusal apart (ADR-144). Any other
     * refusal is about the model rather than this question and is not this method's to catch.
     *
     * <p><b>An absent count fits</b> (ADR-123): it arrives as {@code 0} from Spring AI's own
     * substitution, {@code 0} is never past the room, so the proposal stands as it was estimated —
     * exactly what happened before this record, and no worse.
     */
    private QuestionCount countQuestion(
            ClusterCall call, List<Exemplar> documents, String modelName, int contextWindow) {
        Prompt prompt = new Prompt(promptFor(call, documents), countingOptionsFor(modelName, contextWindow));
        try {
            ChatResponse response = chatModel.call(prompt);
            int reported = response.getMetadata().getUsage().getPromptTokens();
            return new QuestionCount(reported <= roomForAQuestionIn(contextWindow), String.valueOf(reported));
        } catch (NonTransientAiException refused) {
            if (!isRefusedAsPastTheWindow(refused)) {
                throw refused;
            }
            return new QuestionCount(false, refused.getMessage());
        }
    }

    /**
     * How many tokens a question may be counted at and still leave the answer its whole allowance: the
     * window less {@link #REPLY_ALLOWANCE} (ADR-166 §1), 7,168 tokens in the shipped window.
     */
    private static int roomForAQuestionIn(int contextWindow) {
        return contextWindow - REPLY_ALLOWANCE;
    }

    /** What one counting call found: whether the question fits the room, and what the engine said of it. */
    private record QuestionCount(boolean fits, String detail) {}

    /**
     * The call itself, failing the cluster where the serving engine refuses the prompt as longer than
     * the window rather than shifting it (ADR-108, #332).
     *
     * <p><b>The same overrun the ceiling check below looks for, reported before any answer exists.</b>
     * ADR-108 fails a cluster whose prompt did not fit the window, and read that off {@code
     * prompt_eval_count} only because the engine it measured shifted an oversized prompt silently. A
     * runner that refuses instead says the same thing outright — measured on
     * Ollama 0.33.2's llama-server path, HTTP 400, a prompt of 8280 tokens
     * against a window of 8192 — so it is recorded as {@link ClusterFaultKind#PROMPT_EVALUATION_CEILING}
     * rather than a fifth kind, which would change no behaviour and no remedy (ADR-121, ADR-123). The
     * detail carries the engine's own message, which is where the count that failed is.
     *
     * <p><b>Only a refusal on length.</b> Spring AI raises every 4xx as one {@link
     * NonTransientAiException} whose message is the status and the body, so the reason is told apart by
     * the text alone, as {@code ChunkEmbedder} tells its own length refusal apart one instrument along
     * (ADR-144). Any other refusal — a model never pulled, a model that cannot chat — would come back
     * for every cluster alike, so it is the step that is wrong rather than this cluster, and it
     * reaches the step unchanged.
     *
     * <p>Without this the refusal escaped {@code GenerationTasklet} as a step failure, rolling back the
     * step's transaction and every fault row recorded in it before this cluster, exactly as ADR-123
     * describes for an exception that is not a {@link ClusterFaultException}.
     */
    private ChatResponse callTurningDownARefusalOnLength(Prompt prompt, int contextWindow) {
        try {
            return chatModel.call(prompt);
        } catch (NonTransientAiException refused) {
            if (!isRefusedAsPastTheWindow(refused)) {
                throw refused;
            }
            throw new ClusterFaultException(new ClusterFault(
                    ClusterFaultKind.PROMPT_EVALUATION_CEILING,
                    "the serving engine refused the prompt as longer than the window of " + contextWindow
                            + " token(s): " + refused.getMessage()));
        }
    }

    /** Whether {@code refused} is the serving engine turning a prompt down as longer than the window. */
    private static boolean isRefusedAsPastTheWindow(NonTransientAiException refused) {
        String message = refused.getMessage();
        return message != null
                && (message.contains(RUNNER_REFUSES_PAST_THE_WINDOW)
                        || message.contains(OLLAMA_REFUSES_PAST_THE_WINDOW));
    }

    /**
     * Fails the cluster where the answering call's own question was cut down to fit (ADR-108, amended
     * by ADR-166 §3): {@code prompt_eval_count} at or above {@code contextWindow - 1} means part of the
     * cluster's documents never reached the model at all, and the answer covers less than it was asked
     * about with nothing in it saying which part.
     *
     * <p><b>The ceiling sits one token under the window, not at it.</b> Every call sends {@code
     * num_keep: -1} ({@link #optionsFor}), so the engine keeps a too-long question's head up to {@code
     * contextWindow - 1} rather than shifting it — {@code contextWindow - 1} is Ollama's own {@code
     * fullPromptLimit}, the most a question can be counted at whole, and exactly what every cut
     * question now reports. A whole question that long leaves no room for a single token of answer, so
     * nothing passing this check at the old ceiling could ever have been believed anyway.
     *
     * <p><b>This is the backstop, not the guard.</b> {@link #sentAfterCounting} already asked the
     * engine to count this very question before the answering call was made; this check exists for the
     * question the counting call found to fit and the answering call read differently (ADR-166 §4).
     *
     * <p><b>The count is read without a guard</b> (ADR-123). Spring AI declares it {@code Integer}
     * with no {@code @Nullable} in a {@code @NullMarked} package, {@code DefaultUsage} substitutes
     * {@code 0}, {@code EmptyUsage} returns {@code 0}, and {@code OllamaChatModel} builds it from
     * {@code Optional.ofNullable(...).orElse(0)}. So an absent {@code prompt_eval_count} arrives as
     * {@code 0}, {@code 0} is below every window, and a call whose token telemetry is missing is not
     * faulted for the ceiling — the safe direction, chosen rather than inherited.
     */
    private static void checkPromptEvaluationCeiling(ChatResponse response, int contextWindow) {
        int promptTokens = response.getMetadata().getUsage().getPromptTokens();
        int ceiling = contextWindow - 1;
        if (promptTokens >= ceiling) {
            throw new ClusterFaultException(new ClusterFault(
                    ClusterFaultKind.PROMPT_EVALUATION_CEILING,
                    "prompt evaluation count " + promptTokens + " at or above the ceiling of "
                            + ceiling + " token(s)"));
        }
    }

    /**
     * The one answer the call came back with, or fails the cluster where it came back with none
     * (ADR-123).
     *
     * <p><b>This is the first point a generation is needed, and so the first place its absence can be
     * told about.</b> {@code getResult()} hands back {@code null} for a response carrying none, so the
     * two checks below it would each dereference nothing — and an {@link NullPointerException} is not
     * a {@link ClusterFaultException}: it escapes {@code GenerationTasklet} as a step failure, rolling
     * back the step and taking with it every fault row recorded in it before this one (ADR-111).
     *
     * <p>Recorded as a {@link ClusterFaultKind#SCHEMA_VIOLATION} rather than a fifth kind: that kind
     * already names <em>what came back could not be read into the shape the call imposed</em>, and
     * nothing at all is the limiting case of it rather than a different case.
     */
    private static Generation answerIn(ChatResponse response) {
        Generation answer = response.getResult();
        if (answer == null) {
            throw new ClusterFaultException(new ClusterFault(
                    ClusterFaultKind.SCHEMA_VIOLATION, "the call came back carrying no answer at all"));
        }
        return answer;
    }

    /**
     * Fails the cluster where the answer stopped because it ran out of room (ADR-108): {@code
     * done_reason: "length"} arrives looking exactly like a finished answer, ending mid-sentence with
     * nothing saying it was cut off.
     */
    private static void checkAnswerDidNotRunOutOfRoom(Generation answer, ChatResponse response) {
        String finishReason = answer.getMetadata().getFinishReason();
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
     *
     * <p><b>A text that is null or blank is turned down before Jackson sees it</b> (ADR-123), kept
     * apart from an unreadable one by its detail the way ADR-109 keeps its two citation failures
     * apart. The guard cannot be left to the catch below: measured against Jackson 3, {@code
     * readValue} asserts its argument first and throws {@link IllegalArgumentException}, which is no
     * {@link JacksonException} and is not caught — so a null text would escape the step through a
     * method that looks guarded.
     *
     * <p><b>Once the text reads back, both fields {@code ANSWER_SCHEMA} requires are required of the
     * record</b> (ADR-124, ADR-125): an {@code Answer} whose {@code title()} or {@code prose()} is
     * {@code null} or blank is turned down here, before it is returned, rather than reaching {@link
     * #checkCitations} or {@link SynthesisDoc} and being dereferenced or stored. This establishes the
     * record-level half of ADR-123's rule for both of them: what {@link #parseAnswer} returns has a
     * non-null, non-blank title and writing, which is what lets every reader of it downstream go
     * unguarded.
     *
     * <p><b>The title is read first, in the order {@code ANSWER_SCHEMA} names the fields</b>
     * (ADR-125), so an answer carrying neither is reported on its title. Reading in the schema's own
     * order needs no case thought about, and a third required field added later has one obvious place.
     *
     * <p><b>The two ways a title fails to arrive keep different details, where the two ways writing
     * fails to arrive keep one</b> (ADR-124, ADR-125). ADR-124's test is what decides it, applied to
     * different facts: a blank title is storable — {@code synthesis_doc.title} takes it, the row is
     * written, and ADR-111's skip means no repair pass ever reaches it again — while an absent one met
     * {@code TEXT NOT NULL} and could leave no row at all. Neither missing {@code prose} could leave
     * anything behind, which is why that pair folds and this one does not.
     *
     * <p><b>The details themselves say <em>heading</em>, and that is not a slip</b> (ADR-125): a
     * {@code detail} is prose an operator reads, {@code CONTEXT.md}'s cluster title entry imposes no
     * rendering on it, and the reason beside these two already reads that way on {@code main}. What
     * the vocabulary binds is the names above — those take the entry's own term.
     */
    private static Answer parseAnswer(Generation answer) {
        String text = answer.getOutput().getText();
        if (text == null || text.isBlank()) {
            throw new ClusterFaultException(
                    new ClusterFault(ClusterFaultKind.SCHEMA_VIOLATION, "the answer came back empty"));
        }
        Answer parsed;
        try {
            parsed = JSON_MAPPER.readValue(text, Answer.class);
        } catch (JacksonException e) {
            throw new ClusterFaultException(new ClusterFault(
                    ClusterFaultKind.SCHEMA_VIOLATION,
                    "the answer did not read back into a heading and its writing: " + e.getMessage()
                            + " (line " + e.getLocation().getLineNr() + ", column "
                            + e.getLocation().getColumnNr() + ")"));
        }
        if (parsed.title() == null) {
            throw new ClusterFaultException(new ClusterFault(
                    ClusterFaultKind.SCHEMA_VIOLATION, "the answer came back with no heading on it"));
        }
        if (parsed.title().isBlank()) {
            throw new ClusterFaultException(new ClusterFault(
                    ClusterFaultKind.SCHEMA_VIOLATION, "the answer came back with a blank heading"));
        }
        if (parsed.prose() == null || parsed.prose().isBlank()) {
            throw new ClusterFaultException(new ClusterFault(
                    ClusterFaultKind.SCHEMA_VIOLATION, "the answer came back with no writing in it"));
        }
        checkNoTrailingBrace(parsed.prose());
        return parsed;
    }

    /**
     * Fails the cluster where the writing ends with the answer's own closing brace (ADR-162): the
     * model's JSON object closed once inside the string, typed as ordinary characters of {@code
     * prose}, and then closed again for real. That is a malformed answer no earlier check can see,
     * because the brace sits inside a well-formed string.
     *
     * <p><b>Narrow on purpose.</b> The last non-blank character has to be {@code }}, and the writing
     * has to hold more {@code }} than {@code {} — a balanced pair, such as {@code {PORT}}, is content
     * a technical corpus can legitimately hold, and is believed. Nothing is stripped either way: a
     * turned-down answer's writing is never reached by anything that stores it, and a believed one is
     * returned exactly as {@link #parseAnswer} read it, down to trailing blank space (ADR-109).
     */
    private static void checkNoTrailingBrace(String prose) {
        String withoutTrailingBlank = prose.stripTrailing();
        if (!withoutTrailingBlank.endsWith("}")) {
            return;
        }
        long closingBraces = withoutTrailingBlank.chars().filter(c -> c == '}').count();
        long openingBraces = withoutTrailingBlank.chars().filter(c -> c == '{').count();
        if (closingBraces > openingBraces) {
            throw new ClusterFaultException(new ClusterFault(
                    ClusterFaultKind.SCHEMA_VIOLATION,
                    "the answer's writing ends with a closing brace that belongs to no opening one"));
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
     *
     * <p><b>{@code prose} is dereferenced with no guard of its own</b> (ADR-124), because {@link
     * #parseAnswer} already established it is non-null and non-blank before this is ever reached.
     */
    private static void checkCitations(String prose, int documentsSent) {
        List<String> citations =
                Citation.AS_WRITTEN.matcher(prose).results().map(match -> match.group(1)).toList();
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
     * The <em>proposal</em> a cluster's documents are first put together as, closest to the seed first,
     * filled at the word estimate until the room runs out (ADR-108) — {@link #sentAfterCounting} is
     * what the serving engine's own count trims it down to before anything is actually sent (ADR-166
     * §1–§2).
     *
     * <p><b>No fixed number of them.</b> Eight short documents propose all eight; four hundred proposes
     * what fits the estimate, and what the finished page discloses as written from part of the cluster
     * is however many of this the engine went on to count as fitting.
     *
     * <p><b>A cluster too large is never skipped.</b> Refusing would leave the largest clusters — the
     * ones most worth connecting — with nothing written over them, and nothing is concealed by
     * sending part: the page lists every document regardless (ADR-104).
     *
     * <p><b>A document too large for an empty call is passed over here too, and the fill carries on</b>
     * — it can never be sent, so stopping on it would cost the whole cluster its writing. A document
     * that merely does not fit what is <em>left</em> stops the fill instead, since everything after it
     * is further out. {@link #sentAfterCounting} applies the same rule a second time, against the
     * engine's own count rather than this estimate, for a document the estimate under-priced.
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
     * <p>Read by {@link ClusterGeneration} before {@link #docFor} is reached, so a cluster of unusually large
     * documents never costs a call — the other route to an empty fill ADR-121 names, where the window
     * itself is reasonable but one outsized cluster still gets nothing while its neighbours write fine.
     */
    public static boolean nothingFitsIn(int contextWindow, List<Exemplar> exemplars) {
        return whatFitsIn(contextWindow, exemplars).isEmpty();
    }

    /**
     * How much of a too-long question the engine is asked to keep, sent as {@code num_keep} on every
     * call (ADR-166 §3): the whole of it, up to the window's last token, rather than the four leading
     * tokens and the tail Ollama keeps by default.
     *
     * <p><b>This is what makes a cut show in the count.</b> Left unsaid, a question longer than the
     * window is shifted to about half of it and counted there — a count that reads exactly like a
     * question that fit. Asked to keep the whole question instead, the engine counts a cut one at
     * {@code contextWindow - 1}, which {@link #checkPromptEvaluationCeiling} and {@link #countQuestion}
     * both read as past the room.
     *
     * <p><b>Public because it joins the generator identity outside this module</b> (ADR-166 §3):
     * {@code num_keep} is a member of Ollama's {@code options} object, so it is one of "the options
     * actually sent" {@code StageRuns.generation()} mints this run's id from, the way {@link
     * #REPLY_ALLOWANCE} already does.
     */
    public static final int KEEP_A_TOO_LONG_QUESTIONS_HEAD = -1;

    /** What a counting call asks the engine to write: one token of answer, never read (ADR-166 §1). */
    private static final int ONE_TOKEN_OF_ANSWER = 1;

    /**
     * What every answering call this module makes is made under: the model, the window it may read in,
     * how much of a too-long question to keep, the shape the answer has to arrive in, and that the
     * model is not to think before answering.
     *
     * <p>Package-private rather than inlined above, so the integration test that puts these on a real
     * serving engine asserts about <em>this</em> request (#181) — checking whether the window
     * survives the framework, not whether a rebuilt copy of it would.
     *
     * <p><b>Thinking is asked to be off, never left unsaid</b> (ADR-159). Unsaid, Ollama turns it on
     * for any model that can think — the shipped default among them — and every token of reasoning
     * counts against {@link #REPLY_ALLOWANCE}, so an answer can run out of room before its first word
     * of writing. Measured on {@code qwen3:8b}, CPU-served: 113 s and 2,486 characters of reasoning
     * with it on, 26 s and none with it off, the same prompt. Saying off to a model that cannot think
     * is accepted by the engine rather than refused; only asking one to think is refused.
     *
     * <p><b>{@code num_keep: -1} joins the options every call sends</b> (ADR-166 §3): see {@link
     * #KEEP_A_TOO_LONG_QUESTIONS_HEAD}. {@link #countingOptionsFor} is this same request with
     * one difference, so the engine's prompt cache serves the answering call's evaluation from the
     * counting call's.
     */
    static OllamaChatOptions optionsFor(String modelName, int contextWindow) {
        return optionsFor(modelName, contextWindow, REPLY_ALLOWANCE);
    }

    /**
     * The answering call's own request, asking for one token of answer instead of the reply allowance
     * (ADR-166 §1): the same model, window, {@code num_keep}, answer shape and thinking setting, so a
     * question that fits costs one evaluation shared between the two calls rather than two.
     */
    static OllamaChatOptions countingOptionsFor(String modelName, int contextWindow) {
        return optionsFor(modelName, contextWindow, ONE_TOKEN_OF_ANSWER);
    }

    private static OllamaChatOptions optionsFor(String modelName, int contextWindow, int numPredict) {
        return OllamaChatOptions.builder()
                .model(modelName)
                .numCtx(contextWindow)
                .numPredict(numPredict)
                .numKeep(KEEP_A_TOO_LONG_QUESTIONS_HEAD)
                .outputSchema(ANSWER_SCHEMA)
                .disableThinking()
                .build();
    }

    /**
     * What the call says: what the cluster is, what it sits under, the documents under their
     * ordinals, and — after them, where the model reads it last — what to write and how long
     * (ADR-108, ADR-161 §1).
     *
     * <p><b>The instruction comes after the documents, not before them</b> (ADR-161 §1). Measured on
     * the 14-document cluster the defect was found on: with the instruction first, some 4,000 tokens
     * of record rows sat between it and the point where the model starts writing, and the model
     * either copied the records back until it ran out of room or finished citing nothing. Moved
     * after the documents, the same request passed 15 calls of 15. The opening keeps the count, the
     * label, the seed document, and the line saying each document opens below under the number to
     * cite it by; the documents follow under their ordinals, exactly as ADR-108 and ADR-133 send
     * them.
     *
     * <p><b>The citation form is named, with an example, rather than left to "the bracketed
     * numbers"</b> (ADR-159 §2). Asked only that, and under the answer schema, the shipped model
     * cited every document as {@code {1}} — never {@code [1]} — so {@link Citation#AS_WRITTEN} found nothing
     * and 8 clusters of 9 were turned down as uncited. The example is derived from {@code
     * inScoreOrder.size()} rather than fixed, so a group of one is never shown a number past 1: a
     * probe of 12 calls carrying one, two and three documents each, against the same serving engine,
     * came back citing in range every time, where the first wording — a fixed {@code [1] or [2][3]}
     * shown even to a single-document call — invited an out-of-range citation from exactly the group
     * size ADR-087 says is an expected outcome. The check is not widened to match the model instead:
     * the deliverable resolves only {@code [n]} into a link (ADR-109).
     *
     * <p><b>The instruction names a length, in words, held under {@link #REPLY_ALLOWANCE}</b>
     * (ADR-161 §2): see {@link #WORD_LIMIT}. A length alone, said before the documents, fixed
     * nothing measured (0 passes of 8); placement is what fixed it, and the length is what keeps the
     * passing answers well inside the allowance rather than merely finished.
     */
    private static String promptFor(ClusterCall call, List<Exemplar> inScoreOrder) {
        String exemplars = IntStream.range(0, inScoreOrder.size())
                .mapToObj(index -> "[" + (index + 1) + "] " + inScoreOrder.get(index).leadingChunk())
                .reduce((first, second) -> first + "\n\n" + second)
                .orElse("");
        return """
                Write one connected piece over a group of %d document(s) from an archive.

                The group is called "%s", and it sits under the seed document "%s".

                Each document opens below under the number to cite it by.

                %s

                That is all %d document(s). Connect them in at most %d words: say what they
                share, where they differ and what they amount to together. Do not summarise them one
                by one. Cite with the bracketed numbers, inline, written in square brackets exactly as they
                appear above, such as %s, and use no other citation of any
                kind.
                """
                .formatted(
                        inScoreOrder.size(),
                        call.label(),
                        call.seedPath(),
                        exemplars,
                        inScoreOrder.size(),
                        WORD_LIMIT,
                        citationExample(inScoreOrder.size()));
    }

    /**
     * The citation example shown for a call carrying {@code documentsSent} documents (ADR-159 §2):
     * never a number past {@code documentsSent}, so a model that copies it literally cites only
     * documents the call actually sent, and ADR-109's range check has nothing to turn down.
     */
    private static String citationExample(int documentsSent) {
        return switch (documentsSent) {
            case 1 -> "[1]";
            case 2 -> "[1] or [1][2]";
            default -> "[1] or [2][3]";
        };
    }

    /** The shape the answer comes back in: a title for the cluster, and the writing itself. */
    private record Answer(String title, String prose) {}
}
