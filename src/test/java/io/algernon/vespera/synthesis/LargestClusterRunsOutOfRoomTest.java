package io.algernon.vespera.synthesis;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.ledger.OccurrenceId;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.ollama.api.OllamaChatOptions;

/**
 * The defect the first end-to-end run found in the largest cluster (ADR-161, #317): its answer ran
 * past the reply allowance and was turned down, so the cluster most worth connecting got nothing.
 *
 * <p><b>What was measured.</b> The 14-document GesPOS cluster {@code
 * CARD_SIMPLIGI_ESITI_TLG_20170426_105500}, its request rebuilt from the run's own database, sent to
 * {@code qwen3:8b} on Ollama 0.33.2 with the shipped prompt 14 times: 3 ran out of room copying the
 * documents' records back verbatim, 10 finished without a single citation, 1 passed. Raising the
 * allowance to 2048 fixed nothing (2 passes in 6). Moving the instruction after the documents and
 * naming a length there passed 15 times in 15, at 136 to 345 words and 232 to 529 tokens. The model
 * had not written too much prose: under some 4,000 tokens of record rows it had lost the
 * instruction, which sat above them.
 *
 * <p><b>What is pinned, and what is left free.</b> The instruction tests pin ADR-161's two rules
 * rather than its wording: after the last document, the request says how a citation is written, with
 * no example number past the documents sent (ADR-159's rule, carried to where the instruction now
 * is), and it names a length in words that fits the reply allowance at the pessimistic words-to-tokens
 * ratio the document budget already uses. Both fail on the shipped prompt, which ends with the last
 * document. The last two tests fail neither way: they pin the two alternatives ADR-161 turned down,
 * which are raising the allowance and asking a second time within one call.
 *
 * <p>A class of its own rather than more methods in {@code ClusterSynthesisTest}, so the defect has
 * one place that says what it was.
 */
@Epic("Synthesis")
@Feature("Writing over the groups")
@Issue("317")
@Link(name = "ADR-161", url = Adr.THE_INSTRUCTION_FOLLOWS_THE_DOCUMENTS_AND_NAMES_A_LENGTH, type = "adr")
@Link(name = "ADR-159", url = Adr.GENERATION_ASKS_FOR_NO_THINKING_AND_NAMES_THE_SQUARE_BRACKETS, type = "adr")
class LargestClusterRunsOutOfRoomTest {

    /** The model the call names; the shipped default, which is the one the defect was found under. */
    private static final String MODEL_NAME = "qwen3:8b";

    /** The name the cluster the defect was found on carries before anything is written for it. */
    private static final String LABEL = "CARD_SIMPLIGI_ESITI_TLG_20170426_105500";

    /** The document the operator supplied that the cluster sits under. */
    private static final String SEED_PATH = "Scontrini_1_19.docx";

    /** The heading the scripted model writes, which only has to be there. */
    private static final String A_HEADING = "Terminal records across fourteen supply files";

    /** Writing citing the first document only, which is in range whatever the call carried. */
    private static final String WRITING_CITING_THE_FIRST_DOCUMENT = "The records [1] are listed in full.";

    /** How many documents the cluster the defect was found on holds, and every one of them fits a call. */
    private static final int THE_LARGEST_CLUSTER = 14;

    /**
     * How many tokens the answer is allowed, written out rather than read off the code it checks: a
     * number taken from the thing under test would agree with it whatever it changed to.
     */
    private static final int THE_ANSWER_ALLOWANCE = 1024;

    /** A square-bracketed number: the form a citation is written in. */
    private static final Pattern A_BRACKETED_NUMBER = Pattern.compile("\\[(\\d+)\\]");

    /** A length named in words, the way an instruction names one. */
    private static final Pattern A_LENGTH_IN_WORDS = Pattern.compile("(\\d+)\\s+words");

    /** How many calls an answer that ran out of room costs: the one that was made, and no second. */
    private static final int ONE_CALL = 1;

    /** Why an answer stopped when it reached the length it was allowed rather than finishing. */
    private static final String STOPPED_AT_THE_LENGTH_ALLOWED = "length";

    /**
     * How much of the question a scripted call reports having read: well inside the window.
     *
     * <p>4,098 is what the cluster the defect was found on reported, and ADR-166 found what it is:
     * exactly the length Ollama 0.33.2 cuts a question longer than a window of 8,192 down to, keeping
     * the first four tokens and the end. The instruction that model "lost" sat before the documents,
     * so it was cut away. Kept as the number measured; inside the window it still is.
     */
    private static final int A_QUESTION_WELL_INSIDE_THE_WINDOW = 4098;

    /**
     * Fails on the shipped prompt, which ends with the last document: measured, a model handed 14
     * documents of record rows under an instruction it read first copied the rows back or cited
     * nothing, and one handed the same instruction after them cited in range 15 times in 15.
     */
    @Test
    @Story("The request says what to write after the documents, where the model reads it last")
    @DisplayName("A request carrying one document ends by saying how to cite, showing no number past 1")
    void aRequestCarryingOneDocumentEndsBySayingHowToCite() {
        claimTheRequestEndsBySayingHowToCite(aClusterOf(1));
    }

    @Test
    @Story("The request says what to write after the documents, where the model reads it last")
    @DisplayName("A request carrying two documents ends by saying how to cite, showing no number past 2")
    void aRequestCarryingTwoDocumentsEndsBySayingHowToCite() {
        claimTheRequestEndsBySayingHowToCite(aClusterOf(2));
    }

    @Test
    @Story("The request says what to write after the documents, where the model reads it last")
    @DisplayName("A request carrying three documents ends by saying how to cite, showing no number past 3")
    void aRequestCarryingThreeDocumentsEndsBySayingHowToCite() {
        claimTheRequestEndsBySayingHowToCite(aClusterOf(3));
    }

    /** The size the defect was found at. */
    @Test
    @Story("The request says what to write after the documents, where the model reads it last")
    @DisplayName("A request carrying fourteen documents ends by saying how to cite, showing no number past 14")
    void aRequestCarryingFourteenDocumentsEndsBySayingHowToCite() {
        claimTheRequestEndsBySayingHowToCite(aClusterOf(THE_LARGEST_CLUSTER));
    }

    /**
     * Fails on the shipped prompt, which names no length at all. The length is read from the text
     * after the last document, and held against the allowance at the words-to-tokens ratio the
     * document budget already uses (ADR-108), so a length the model obeys cannot on its own run the
     * answer out of room.
     */
    @Test
    @Story("The request names a length the answer's room can hold")
    @DisplayName("After the documents, the request names a length in words that fits the room the answer has")
    void afterTheDocumentsTheRequestNamesALengthTheAnswersRoomCanHold() {
        ScriptedChatModel model = new ScriptedChatModel(answering(WRITING_CITING_THE_FIRST_DOCUMENT));
        ClusterCall cluster = aClusterOf(THE_LARGEST_CLUSTER);
        new ClusterSynthesis(model).docFor(cluster, MODEL_NAME, ClusterSynthesis.CONTEXT_WINDOW);

        Matcher length = A_LENGTH_IN_WORDS.matcher(whatFollowsTheLastDocument(model, cluster));

        claim(
                "after the last document, the request names how many words the writing may run to: the"
                        + " shipped request named none, and a cluster of " + THE_LARGEST_CLUSTER
                        + " documents was turned down for running out of room",
                () -> assertThat(length.find()).isTrue());
        int words = Integer.parseInt(length.group(1));
        claim(
                "the " + words + " words it names fit inside the " + THE_ANSWER_ALLOWANCE + " tokens the"
                        + " answer is allowed, counting each word as " + ClusterSynthesis.TOKENS_PER_WORD
                        + " tokens, the same cautious count the documents are measured by: a length that"
                        + " would not fit is a length the answer runs out of room obeying",
                () -> assertThat(words * ClusterSynthesis.TOKENS_PER_WORD)
                        .isLessThanOrEqualTo(THE_ANSWER_ALLOWANCE));
    }

    /**
     * Holds on the shipped code and on ADR-161's: it pins the alternative that record turned down. A
     * larger allowance took room from the documents and fixed nothing measured, because the answers
     * that ran out of room were copying the documents back, and a copy runs to whatever cap is set.
     */
    @Test
    @Story("The request names a length the answer's room can hold")
    @DisplayName("The room the answer is allowed is not raised to make a long answer fit")
    void theRoomTheAnswerIsAllowedIsNotRaised() {
        ScriptedChatModel model = new ScriptedChatModel(answering(WRITING_CITING_THE_FIRST_DOCUMENT));

        new ClusterSynthesis(model).docFor(aClusterOf(THE_LARGEST_CLUSTER), MODEL_NAME, ClusterSynthesis.CONTEXT_WINDOW);

        claim(
                "the call still allows the answer " + THE_ANSWER_ALLOWANCE + " tokens. Measured on the"
                        + " cluster that ran out of room, twice that fixed nothing: 2 answers in 6 passed,"
                        + " the rest cited nothing, and every token added to the answer is taken from the"
                        + " room the documents are read in",
                () -> assertThat(model.optionsUsed().getNumPredict()).isEqualTo(THE_ANSWER_ALLOWANCE));
    }

    /**
     * Holds on the shipped code and on ADR-161's: it pins the second alternative that record turned
     * down. A second call made because the first ran out of room, with a tighter instruction, is the
     * first answer's failure reaching the prompt, which ADR-109 and ADR-111 refuse; a cluster turned
     * down is asked the same question again by the next invocation instead.
     */
    @Test
    @Story("An answer that runs out of room is turned down, not asked for again")
    @DisplayName("An answer that runs out of room costs one call and leaves its group unwritten")
    void anAnswerThatRunsOutOfRoomCostsOneCall() {
        ScriptedChatModel model = new ScriptedChatModel(runningOutOfRoom());

        ClusterFault fault = null;
        try {
            new ClusterSynthesis(model).docFor(aClusterOf(THE_LARGEST_CLUSTER), MODEL_NAME, ClusterSynthesis.CONTEXT_WINDOW);
        } catch (ClusterFaultException turnedDown) {
            fault = turnedDown.fault();
        }
        ClusterFault kept = fault;

        claim(
                "the answer is turned down as having run out of room after the " + THE_ANSWER_ALLOWANCE
                        + " tokens it was allowed, and the group is left for the next time the command is"
                        + " run to ask about again",
                () -> assertThat(kept).isEqualTo(new ClusterFault(
                        ClusterFaultKind.ANSWER_RAN_OUT_OF_ROOM,
                        "the answer stopped after " + THE_ANSWER_ALLOWANCE + " token(s), having run out of"
                                + " room")));
        claim(
                "and it cost exactly " + ONE_CALL + " call: nothing asks again, with a tighter request,"
                        + " before the command ends, because a request shaped by the answer that failed is"
                        + " one answer judging another",
                () -> assertThat(model.callsMade()).isEqualTo(ONE_CALL));
    }

    /**
     * Sends {@code cluster} and claims three things of what the request says after its last
     * document's opening: that it names square brackets, that it shows at least one bracketed number,
     * and that no number it shows is greater than the documents sent.
     */
    private static void claimTheRequestEndsBySayingHowToCite(ClusterCall cluster) {
        ScriptedChatModel model = new ScriptedChatModel(answering(WRITING_CITING_THE_FIRST_DOCUMENT));
        new ClusterSynthesis(model).docFor(cluster, MODEL_NAME, ClusterSynthesis.CONTEXT_WINDOW);
        int documentsSent = cluster.exemplars().size();

        String afterTheDocuments = whatFollowsTheLastDocument(model, cluster).replaceAll("\\s+", " ");
        List<Integer> numbersShown = A_BRACKETED_NUMBER.matcher(afterTheDocuments).results()
                .map(shown -> Integer.parseInt(shown.group(1)))
                .toList();

        claim(
                "after the last of the " + documentsSent + " document(s), the request says a citation is"
                        + " written in square brackets: said only before them, it was lost under some 4,000"
                        + " tokens of record rows, and the model copied the rows back or cited nothing",
                () -> assertThat(afterTheDocuments).containsIgnoringCase("square brackets"));
        claim(
                "and it shows there how a citation looks, with at least one square-bracketed number",
                () -> assertThat(numbersShown).isNotEmpty());
        claim(
                "and no number it shows there is greater than the " + documentsSent + " document(s) sent:"
                        + " a model copying the example word for word would otherwise cite a document it"
                        + " was never given, and be turned down for it",
                () -> assertThat(numbersShown).allSatisfy(shown -> assertThat(shown)
                        .isBetween(1, documentsSent)));
    }

    /**
     * Everything the request says after the last document's opening, which is empty on a request that
     * ends with it. The documents are sent in score order and this fixture scores them in the order it
     * lists them, so the last one listed is the last one sent.
     */
    private static String whatFollowsTheLastDocument(ScriptedChatModel model, ClusterCall cluster) {
        String asked = model.whatWasAsked();
        String lastOpening = cluster.exemplars().getLast().leadingChunk();
        int end = asked.lastIndexOf(lastOpening);
        assertThat(end).as("the last document's opening is in the request").isNotNegative();
        return asked.substring(end + lastOpening.length());
    }

    /**
     * The first {@code size} of fourteen short documents, opening the way the records of the cluster
     * the defect was found on open, in descending score so the order they are sent in is the order
     * they are listed in.
     */
    private static ClusterCall aClusterOf(int size) {
        List<Exemplar> documents = IntStream.rangeClosed(1, size)
                .mapToObj(ordinal -> new Exemplar(
                        new OccurrenceId(ordinal),
                        "AggSoftware 9125" + ordinal + " VILLA SRL VIA BOVISASCA " + ordinal
                                + " 20157MILANO MI ICT220GEM-CLIIOBBGPRSVOGPRSVO 3 T 20170427MIG28004429"
                                + ordinal,
                        12,
                        0.9 - ordinal / 100.0))
                .toList();
        return new ClusterCall(LABEL, SEED_PATH, documents);
    }

    /** A response carrying {@code prose} under a heading, finished of its own accord. */
    private static ChatResponse answering(String prose) {
        return new ChatResponse(List.of(new Generation(
                new AssistantMessage("{\"title\":\"" + A_HEADING + "\",\"prose\":\"" + prose + "\"}"))));
    }

    /**
     * A response that stopped at the length it was allowed, having written the whole allowance: what
     * the cluster the defect was found on came back with, a JSON answer cut off part way through.
     */
    private static ChatResponse runningOutOfRoom() {
        return new ChatResponse(
                List.of(new Generation(
                        new AssistantMessage("{ \"title\": \"Extracted Data from Text\", \"prose\": \"AggSoftware"
                                + " 91240076 242135 ZENZERO E CAROTE SRL"),
                        ChatGenerationMetadata.builder().finishReason(STOPPED_AT_THE_LENGTH_ALLOWED).build())),
                ChatResponseMetadata.builder()
                        .usage(new DefaultUsage(A_QUESTION_WELL_INSIDE_THE_WINDOW, THE_ANSWER_ALLOWANCE))
                        .build());
    }

    /** A model that answers every call with the one response it was given, keeping what it was asked. */
    private static final class ScriptedChatModel implements ChatModel {

        private final ChatResponse response;

        private Prompt asked;

        private int calls;

        ScriptedChatModel(ChatResponse response) {
            this.response = response;
        }

        /**
         * Answers every call with the one response, counting and keeping only the call that asks for the
         * writing: a counting call (ADR-166) is the same question asking for one token, and what is
         * claimed here is about the question the writing was asked for.
         */
        @Override
        public ChatResponse call(Prompt prompt) {
            if (ClusterSynthesisTest.isACountingCall(prompt)) {
                return response;
            }
            this.asked = prompt;
            this.calls++;
            return response;
        }

        String whatWasAsked() {
            return asked.getContents();
        }

        int callsMade() {
            return calls;
        }

        OllamaChatOptions optionsUsed() {
            return (OllamaChatOptions) asked.getOptions();
        }
    }
}
