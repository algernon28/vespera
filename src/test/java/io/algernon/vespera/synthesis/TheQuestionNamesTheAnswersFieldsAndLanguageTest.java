package io.algernon.vespera.synthesis;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.ledger.OccurrenceId;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.ollama.api.OllamaChatOptions;

/**
 * What the question says after the documents about the answer it imposes and the language it is written in (ADR-322
 * §2, §3): the answer's two fields, in the order the schema names them; that the writing ends with its last sentence;
 * and the deliverable's language, by its English name, the instruction staying English.
 *
 * <p><b>What is pinned, and what is left free.</b> The rules, as ADR-161's tests pin its rules: the fields named are
 * the ones the call imposes, read off the call's own schema; the language named is the one handed over and no other;
 * the two questions differ in that name alone; and the words after the documents stay inside the reserve the
 * proposal is filled to. The phrase saying where the writing ends is pinned because it is the decision. The rest of
 * the sentence's wording is the record's and is not held here.
 *
 * <p>The report says <em>group</em> where the code says cluster (ADR-122).
 */
@Epic("Synthesis")
@Feature("Writing over the groups")
@Link(name = "ADR-322", url = Adr.THE_DELIVERABLE_IS_IN_THE_LANGUAGE_THE_PROFILE_NAMES, type = "adr")
@Link(name = "ADR-161", url = Adr.THE_INSTRUCTION_FOLLOWS_THE_DOCUMENTS_AND_NAMES_A_LENGTH, type = "adr")
@Link(name = "ADR-162", url = Adr.WRITING_ENDING_WITH_THE_ANSWERS_CLOSING_BRACE_IS_TURNED_DOWN, type = "adr")
class TheQuestionNamesTheAnswersFieldsAndLanguageTest {

    private static final String MODEL_NAME = "qwen3:8b";

    private static final String LABEL = "Verbali_di_collaudo_2019";

    private static final String SEED_PATH = "Collaudi.docx";

    /** How many documents the call carries: more than one, so the citation example is not the whole instruction. */
    private static final int THREE_DOCUMENTS = 3;

    /** Writing citing the first document, in range whatever the call carried. */
    private static final String WRITING_CITING_THE_FIRST_DOCUMENT = "I verbali [1] descrivono lo stesso impianto.";

    /** A heading, which only has to be there. */
    private static final String A_HEADING = "Collaudi dell'impianto";

    /**
     * The tokens kept back for everything in the question that is not a document, written out rather than read off the
     * code it checks: a number taken from the thing under test would agree with it whatever it changed to (ADR-322 §3
     * keeps it at 256).
     */
    private static final int THE_INSTRUCTION_RESERVE = 256;

    /** What the record says of where the writing ends. */
    private static final String WHERE_THE_WRITING_ENDS = "ends with its last sentence";

    /** What the record calls the shape the answer comes back in. */
    private static final String THE_SHAPE = "JSON object";

    /** The detail ADR-162 keeps against writing that ends with a closing brace belonging to no opening one. */
    private static final String A_STRAY_CLOSING_BRACE =
            "the answer's writing ends with a closing brace that belongs to no opening one";

    @Test
    @Story("The question asks for the writing in the deliverable's language")
    @DisplayName("After the documents the question asks for the answer in the language it was given and names no other")
    void namesTheLanguageItWasGivenAndNoOther() {
        String italian = whatFollowsTheLastDocument(asked(DeliverableLanguage.ITALIAN));
        String english = whatFollowsTheLastDocument(asked(DeliverableLanguage.ENGLISH));

        claim(
                "a question for an Italian deliverable names Italian after the documents, and not English",
                () -> assertThat(italian).contains(DeliverableLanguage.ITALIAN.englishName())
                        .doesNotContain(DeliverableLanguage.ENGLISH.englishName()));
        claim(
                "and a question for an English one names English, and not Italian: a question naming no language"
                        + " leaves the model to write in whichever its documents draw it to",
                () -> assertThat(english).contains(DeliverableLanguage.ENGLISH.englishName())
                        .doesNotContain(DeliverableLanguage.ITALIAN.englishName()));
    }

    @Test
    @Story("The question asks for the writing in the deliverable's language")
    @DisplayName("The Italian question is the English one with the language's name changed, and nothing else")
    void theTwoQuestionsDifferInTheLanguagesNameAlone() {
        String italian = asked(DeliverableLanguage.ITALIAN).getContents();
        String english = asked(DeliverableLanguage.ENGLISH).getContents();

        claim(
                "the instruction is one wording, the measured one, in English whatever the language, so putting"
                        + " English where the Italian question says Italian gives the English question exactly",
                () -> assertThat(italian.replace(
                                DeliverableLanguage.ITALIAN.englishName(), DeliverableLanguage.ENGLISH.englishName()))
                        .isEqualTo(english));
    }

    @Test
    @Story("The question says what shape the answer is in")
    @DisplayName("After the documents the question names the answer as a JSON object and quotes each field the call imposes, in the schema's order")
    void namesEachFieldTheCallImposesInTheSchemasOrder() {
        Prompt prompt = asked(DeliverableLanguage.ITALIAN);
        String afterTheDocuments = whatFollowsTheLastDocument(prompt);
        List<String> required = requiredFieldsOf(prompt);

        claim(
                "the call imposes a schema of two required fields, the heading and the writing",
                () -> assertThat(required).containsExactly("title", "prose"));
        claim(
                "after the documents the question says the answer is a JSON object: the schema alone constrains the"
                        + " decoding and tells the model nothing",
                () -> assertThat(afterTheDocuments).contains(THE_SHAPE));
        claim(
                "and quotes each field the call requires, the heading before the writing, as the schema names them",
                () -> {
                    assertThat(required).allSatisfy(field -> assertThat(afterTheDocuments).contains("\"" + field + "\""));
                    assertThat(afterTheDocuments.indexOf("\"" + required.get(0) + "\""))
                            .isLessThan(afterTheDocuments.indexOf("\"" + required.get(1) + "\""));
                });
    }

    @Test
    @Story("The question says what shape the answer is in")
    @DisplayName("After the documents the question says the writing ends with its last sentence, and forbids no brace")
    void saysTheWritingEndsWithItsLastSentence() {
        String afterTheDocuments = whatFollowsTheLastDocument(asked(DeliverableLanguage.ITALIAN));

        claim(
                "the question says where the writing stops, which is where the model was closing its object inside it",
                () -> assertThat(afterTheDocuments).contains(WHERE_THE_WRITING_ENDS));
        claim(
                "and says nothing of braces: a balanced pair is content a technical archive holds, and a question"
                        + " forbidding braces would have the model drop it",
                () -> assertThat(afterTheDocuments).doesNotContainIgnoringCase("brace").doesNotContain("{", "}"));
    }

    @Test
    @Story("The question says what shape the answer is in")
    @DisplayName("What the question says after the documents stays inside the room kept back for it")
    void whatFollowsTheDocumentsStaysInsideTheReserve() {
        String afterTheDocuments = whatFollowsTheLastDocument(asked(DeliverableLanguage.ITALIAN));
        long words = afterTheDocuments.strip().split("\\s+").length;

        claim(
                "the " + words + " words after the last document, counted at " + ClusterSynthesis.TOKENS_PER_WORD
                        + " tokens each as the documents are, come to no more than the " + THE_INSTRUCTION_RESERVE
                        + " tokens kept back for what is not a document",
                () -> assertThat(words * ClusterSynthesis.TOKENS_PER_WORD).isLessThanOrEqualTo(THE_INSTRUCTION_RESERVE));
    }

    @Test
    @Story("The question says what shape the answer is in")
    @DisplayName("Italian writing that ends with a stray closing brace is still turned down, and nothing of it is kept")
    void aClosingBraceAfterTheLastSentenceIsStillTurnedDown() {
        ScriptedChatModel model = new ScriptedChatModel(answering(
                "{\"title\":\"" + A_HEADING + "\",\"prose\":\"" + WRITING_CITING_THE_FIRST_DOCUMENT + " }\"}"));

        ClusterFault fault = null;
        try {
            new ClusterSynthesis(model).docFor(
                    aClusterOf(THREE_DOCUMENTS), MODEL_NAME, ClusterSynthesis.CONTEXT_WINDOW, DeliverableLanguage.ITALIAN);
        } catch (ClusterFaultException turnedDown) {
            fault = turnedDown.fault();
        }
        ClusterFault kept = fault;

        claim(
                "the question saying where the writing ends repairs nothing: an answer that closes its object inside the"
                        + " writing is turned down as not in the shape asked for, with the same detail as before",
                () -> assertThat(kept).isEqualTo(
                        new ClusterFault(ClusterFaultKind.SCHEMA_VIOLATION, A_STRAY_CLOSING_BRACE)));
    }

    /** The answering question put for a cluster of three, in {@code language}. */
    private static Prompt asked(DeliverableLanguage language) {
        ScriptedChatModel model = new ScriptedChatModel(answering(
                "{\"title\":\"" + A_HEADING + "\",\"prose\":\"" + WRITING_CITING_THE_FIRST_DOCUMENT + "\"}"));
        new ClusterSynthesis(model).docFor(
                aClusterOf(THREE_DOCUMENTS), MODEL_NAME, ClusterSynthesis.CONTEXT_WINDOW, language);
        return model.asked;
    }

    /** The fields the call's imposed schema requires, read off the call itself and not restated. */
    private static List<String> requiredFieldsOf(Prompt prompt) {
        Map<?, ?> schema = (Map<?, ?>) ((OllamaChatOptions) prompt.getOptions()).getFormat();
        return ((List<?>) schema.get("required")).stream().map(String::valueOf).toList();
    }

    /** Everything the question says after the last document's opening. */
    private static String whatFollowsTheLastDocument(Prompt prompt) {
        String asked = prompt.getContents();
        String lastOpening = aClusterOf(THREE_DOCUMENTS).exemplars().getLast().leadingChunk();
        int end = asked.lastIndexOf(lastOpening);
        assertThat(end).as("the last document's opening is in the question").isNotNegative();
        return asked.substring(end + lastOpening.length());
    }

    /** {@code size} short documents in descending score, so the order they are sent in is the order listed. */
    private static ClusterCall aClusterOf(int size) {
        List<Exemplar> documents = IntStream.rangeClosed(1, size)
                .mapToObj(ordinal -> new Exemplar(
                        new OccurrenceId(ordinal),
                        "Verbale di collaudo numero " + ordinal + " dell'impianto antincendio",
                        8,
                        0.9 - ordinal / 100.0))
                .toList();
        return new ClusterCall(LABEL, SEED_PATH, documents);
    }

    private static ChatResponse answering(String body) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(body))));
    }

    /** A model answering every call with one response, keeping the last question the writing was asked for. */
    private static final class ScriptedChatModel implements ChatModel {

        private final ChatResponse response;

        private Prompt asked;

        ScriptedChatModel(ChatResponse response) {
            this.response = response;
        }

        /** A counting call (ADR-166) is the same question asking for one token, and is not the one kept. */
        @Override
        public ChatResponse call(Prompt prompt) {
            if (!ClusterSynthesisTest.isACountingCall(prompt)) {
                this.asked = prompt;
            }
            return response;
        }
    }
}
