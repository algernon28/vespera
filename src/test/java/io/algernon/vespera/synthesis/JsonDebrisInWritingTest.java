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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;

/**
 * The defect one page of the first end-to-end run shipped with (ADR-162, #318): its writing ended
 * with a space and a closing brace, the brace that closes the answer's JSON object, typed inside the writing by the
 * model before it closed the object for real.
 *
 * <p><b>What was measured.</b> 1 of the 11 pieces of writing that run stored ends that way, and 3 of
 * 90 finished probe answers from {@code qwen3:8b} on Ollama 0.33.2, every one at the very end of the
 * writing. None of the 101 held an opening brace anywhere or any other fragment of the JSON frame.
 *
 * <p><b>What is pinned.</b> The first two tests fail today, because the writing is believed as it
 * came. The last two fail neither way: they pin what ADR-162 kept narrow, that a balanced brace is
 * content, and that nothing the model wrote is ever trimmed off to make an answer pass (ADR-109).
 *
 * <p>A class of its own rather than more methods in {@code ClusterSynthesisTest}, so the defect has
 * one place that says what it was.
 */
@Epic("Synthesis")
@Feature("Writing over the groups")
@Issue("318")
@Link(name = "ADR-162", url = Adr.WRITING_ENDING_WITH_THE_ANSWERS_CLOSING_BRACE_IS_TURNED_DOWN, type = "adr")
@Link(name = "ADR-109", url = Adr.A_CITATION_IS_AN_ORDINAL_MINTED_FOR_ONE_CALL, type = "adr")
class JsonDebrisInWritingTest {

    /** The model the call names; the shipped default, which is the one the defect was found under. */
    private static final String MODEL_NAME = "qwen3:8b";

    /** The name the cluster the defect was found on carries before anything is written for it. */
    private static final String LABEL = "AcquiringBancario_ProcessiGestioneParco_1_3";

    /** The document the operator supplied that the cluster sits under. */
    private static final String SEED_PATH = "CircuitoAperto_CardFlex_1_2.docx";

    /** The heading the scripted model writes, which only has to be there. */
    private static final String A_HEADING = "Card portfolio management";

    /** How many documents the scripted cluster sends, and so how many the writing could cite. */
    private static final int DOCUMENTS_SENT = 2;

    /** The writing's last sentence as the shipped page had it, before the stray brace. */
    private static final String WRITING =
            "The specifications [1] and the process descriptions [2] support the broader objectives of the"
                    + " card portfolio management process.";

    /** The writing exactly as the shipped page ended: a space and the answer's own closing brace. */
    private static final String WRITING_ENDING_IN_THE_CLOSING_BRACE = WRITING + " }";

    /** The shape the probe answers took: the brace right after the full stop, then a space. */
    private static final String WRITING_ENDING_IN_THE_CLOSING_BRACE_AND_A_SPACE =
            "The specifications [1] and the process descriptions [2] describe operational oversight.} ";

    /** Writing that ends in a brace which closes one it opened: content, not debris. */
    private static final String WRITING_ENDING_IN_A_BALANCED_BRACE =
            "The specifications [1] and the process descriptions [2] both address the terminal as {PORT}";

    /** The reason kept for writing carrying the answer's closing brace. */
    private static final String ENDS_WITH_A_STRAY_BRACE =
            "the answer's writing ends with a closing brace that belongs to no opening one";

    @Test
    @Story("Writing carrying a piece of the answer's own frame is turned down")
    @DisplayName("Writing that ends with a stray closing brace leaves its group unwritten")
    void writingEndingWithAStrayClosingBraceIsTurnedDown() {
        ClusterFault fault = faultFrom(WRITING_ENDING_IN_THE_CLOSING_BRACE);

        claim(
                "writing whose last character is a closing brace with no opening one before it is turned"
                        + " down as an answer in the wrong shape: the model closed its answer inside the"
                        + " writing, and a reader would otherwise be handed a page ending in a piece of the"
                        + " machinery that produced it",
                () -> assertThat(fault).isEqualTo(
                        new ClusterFault(ClusterFaultKind.SCHEMA_VIOLATION, ENDS_WITH_A_STRAY_BRACE)));
    }

    @Test
    @Story("Writing carrying a piece of the answer's own frame is turned down")
    @DisplayName("A stray closing brace followed by blank space is the same stray brace")
    void aStrayClosingBraceFollowedByBlankSpaceIsTurnedDown() {
        ClusterFault fault = faultFrom(WRITING_ENDING_IN_THE_CLOSING_BRACE_AND_A_SPACE);

        claim(
                "writing whose last character before trailing blank space is a stray closing brace is"
                        + " turned down the same way: this is the shape every such answer came back in"
                        + " when measured",
                () -> assertThat(fault).isEqualTo(
                        new ClusterFault(ClusterFaultKind.SCHEMA_VIOLATION, ENDS_WITH_A_STRAY_BRACE)));
    }

    @Test
    @Story("Writing carrying a piece of the answer's own frame is turned down")
    @DisplayName("Writing that ends by closing a brace it opened is believed as written")
    void writingEndingWithABalancedBraceIsBelieved() {
        SynthesisDoc doc = docFor(WRITING_ENDING_IN_A_BALANCED_BRACE);

        claim(
                "writing that ends in a closing brace it opened itself is believed, and kept exactly as"
                        + " written: a technical archive writes placeholders in braces, and only a brace"
                        + " belonging to nothing in the writing is the answer's own",
                () -> assertThat(doc.prose()).isEqualTo(WRITING_ENDING_IN_A_BALANCED_BRACE));
    }

    @Test
    @Story("Nothing the model wrote is trimmed off to make its answer pass")
    @DisplayName("Writing that is believed is kept character for character, trailing space and all")
    void believedWritingIsKeptCharacterForCharacter() {
        String writingWithTrailingSpace = WRITING + "  ";

        SynthesisDoc doc = docFor(writingWithTrailingSpace);

        claim(
                "the writing is kept exactly as it came back, down to the blank space after its last"
                        + " sentence: nothing is removed from an answer to make it pass, because deciding"
                        + " which of the model's characters to throw away is deciding which part of the"
                        + " answer to believe",
                () -> assertThat(doc.prose()).isEqualTo(writingWithTrailingSpace));
    }

    /** What turning down an answer carrying {@code prose} recorded, or {@code null} where it was believed. */
    private static ClusterFault faultFrom(String prose) {
        try {
            docFor(prose);
            return null;
        } catch (ClusterFaultException turnedDown) {
            return turnedDown.fault();
        }
    }

    /** The synthesis doc written from an answer carrying {@code prose}. */
    private static SynthesisDoc docFor(String prose) {
        ChatModel model = prompt -> new ChatResponse(List.of(new Generation(new AssistantMessage(
                "{\"title\":\"" + A_HEADING + "\",\"prose\":\"" + prose + "\"}"))));
        return new ClusterSynthesis(model).docFor(aClusterOfTwo(), MODEL_NAME, ClusterSynthesis.CONTEXT_WINDOW);
    }

    /** Two short documents, in descending score so the order they are sent in is the order listed. */
    private static ClusterCall aClusterOfTwo() {
        return new ClusterCall(
                LABEL,
                SEED_PATH,
                List.of(
                        new Exemplar(new OccurrenceId(1), "CARD_ESITI_TLG specifiche tecniche.", 4, 0.81),
                        new Exemplar(new OccurrenceId(2), "Processo di telegestione degli esiti.", 5, 0.77))
                        .subList(0, DOCUMENTS_SENT));
    }
}
