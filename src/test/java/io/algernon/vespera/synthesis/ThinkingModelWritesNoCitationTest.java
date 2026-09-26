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
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.ai.ollama.api.ThinkOption;

/**
 * The defect the first end-to-end run found in 6b (ADR-159): under the shipped generation model,
 * every call came back with writing that cited nothing ADR-109's check could read, or ran out of
 * room thinking before it wrote.
 *
 * <p><b>What was measured.</b> Invocation 5 over GesPOS on 2026-09-26, {@code qwen3:8b} on Ollama
 * 0.33.2: of 9 clusters called, 8 were turned down as uncited and 1 as having run out of room after
 * 1024 tokens, and the step stopped on five turn-downs in a row. The same prompt sent straight to
 * {@code /api/chat} came back citing every document as {@code {1}}, {@code {2}}, {@code {3}} — never
 * {@code [1]} — with thinking on and with it off; with thinking on, it spent 113 s and 2,486
 * characters of reasoning, all of it counted against the reply allowance.
 *
 * <p><b>Two causes, and a test for each.</b> {@link ClusterSynthesis#optionsFor} never said whether
 * to think, and Ollama turns thinking on for a model that can; and the prompt said "the bracketed
 * numbers" without saying which brackets. The instruction tests pin ADR-159 §2's decision rather
 * than its wording: the instruction names square brackets, and no number it shows is greater than
 * the number of documents the call sent — at one, two and three documents, because a group of one is
 * an expected outcome and an example a model can copy out of range is a turn-down the prompt invited.
 * The last test fails neither way: it pins the alternative ADR-159 turned down, which is reading
 * {@code {n}} as a citation.
 *
 * <p>A class of its own rather than more methods in {@code ClusterSynthesisTest}, so the defect has
 * one place that says what it was.
 */
@Epic("Synthesis")
@Feature("Writing over the groups")
@Link(name = "ADR-159", url = Adr.GENERATION_ASKS_FOR_NO_THINKING_AND_NAMES_THE_SQUARE_BRACKETS, type = "adr")
@Link(name = "ADR-109", url = Adr.A_CITATION_IS_AN_ORDINAL_MINTED_FOR_ONE_CALL, type = "adr")
class ThinkingModelWritesNoCitationTest {

    /** The model the call names; the shipped default, which is the one the defect was found under. */
    private static final String MODEL_NAME = "qwen3:8b";

    /** The group's name before anything is written for it. */
    private static final String LABEL = "STB_SF_011_USIN_SPEC_M100_RISPO_E01_01_2010";

    /** The document the operator supplied that the group sits under. */
    private static final String SEED_PATH = "Scontrini_1_19.docx";

    /** The heading the scripted model writes, which only has to be there. */
    private static final String A_HEADING = "Terminal flows across three suppliers";

    /**
     * A square-bracketed number anywhere in the instruction: the form a citation is written in, and
     * so the form a model copying the instruction's example would write.
     */
    private static final Pattern A_BRACKETED_NUMBER = Pattern.compile("\\[(\\d+)\\]");

    /** Writing that cites the first document only, which is in range whatever the call carried. */
    private static final String WRITING_CITING_THE_FIRST_DOCUMENT = "The flows [1] are described in full.";

    /**
     * Writing citing both documents the way the shipped model actually cited them, word for word in
     * form: every document named, none of them in square brackets.
     */
    private static final String WRITING_CITING_IN_CURLY_BRACES =
            "The response flows {1} and the remote-management results {2} describe the same terminals.";

    /** How many documents the group below sends, and so how many the writing could cite. */
    private static final int DOCUMENTS_SENT = 2;

    @Test
    @Story("A model that can reason before it answers is asked not to")
    @DisplayName("Every call asks the model to answer without thinking first")
    void everyCallAsksTheModelNotToThinkFirst() {
        ScriptedChatModel model = new ScriptedChatModel("The flows [1] and the results [2] agree.");

        new ClusterSynthesis(model).docFor(aGroupOfTwo(), MODEL_NAME, ClusterSynthesis.CONTEXT_WINDOW);

        claim(
                "the call says outright that the model is not to think before answering. Left unsaid,"
                        + " a model able to think does, and its reasoning is counted against the same"
                        + " room the answer has: measured, one such call took 113 seconds and nearly"
                        + " 2,500 characters of reasoning, and another ran out of room before it finished",
                () -> assertThat(model.optionsUsed().getThinkOption())
                        .isEqualTo(ThinkOption.ThinkBoolean.DISABLED));
    }

    /**
     * Fails on the first wording of ADR-159 §2, which showed {@code [1] or [2][3]} to every call: a
     * group of one is an expected outcome, and a model copying that example cites a document it was
     * never sent.
     */
    @Test
    @Story("The request says exactly how a citation is written")
    @DisplayName("A request carrying one document names square brackets and shows no number past 1")
    void aRequestCarryingOneDocumentShowsNoNumberPastOne() {
        claimTheInstructionCitesWithinTheDocumentsSent(aGroupOf(1));
    }

    /** Fails on the first wording of ADR-159 §2, for the reason above: its example shows {@code [3]}. */
    @Test
    @Story("The request says exactly how a citation is written")
    @DisplayName("A request carrying two documents names square brackets and shows no number past 2")
    void aRequestCarryingTwoDocumentsShowsNoNumberPastTwo() {
        claimTheInstructionCitesWithinTheDocumentsSent(aGroupOf(2));
    }

    /**
     * Holds on the first wording as well as the decided one, whose example for three or more documents
     * is the same {@code [1] or [2][3]}: it is here so that the rule is pinned at the size where the
     * example reaches its largest number, not only below it.
     */
    @Test
    @Story("The request says exactly how a citation is written")
    @DisplayName("A request carrying three documents names square brackets and shows no number past 3")
    void aRequestCarryingThreeDocumentsShowsNoNumberPastThree() {
        claimTheInstructionCitesWithinTheDocumentsSent(aGroupOf(3));
    }

    /**
     * Sends {@code group} and claims two things of the instruction it was sent with — the request with
     * every document's own numbered opening taken out, so that only what the instruction itself shows
     * is read: that it names square brackets, and that no bracketed number in it is greater than the
     * number of documents sent. The exact wording is left free (ADR-159 §2).
     */
    private static void claimTheInstructionCitesWithinTheDocumentsSent(ClusterCall group) {
        ScriptedChatModel model = new ScriptedChatModel(WRITING_CITING_THE_FIRST_DOCUMENT);
        new ClusterSynthesis(model).docFor(group, MODEL_NAME, ClusterSynthesis.CONTEXT_WINDOW);
        int documentsSent = group.exemplars().size();

        String instruction = model.whatWasAsked();
        for (Exemplar document : group.exemplars()) {
            instruction = instruction.replaceAll("\\[\\d+\\] " + Pattern.quote(document.leadingChunk()), "");
        }
        String instructionText = instruction.replaceAll("\\s+", " ");
        List<Integer> numbersShown = A_BRACKETED_NUMBER.matcher(instructionText).results()
                .map(shown -> Integer.parseInt(shown.group(1)))
                .toList();

        claim(
                "the request says a citation is written in square brackets: asked only for \"the"
                        + " bracketed numbers\", the model shipped by default wrote every citation in"
                        + " curly braces, and writing whose citations cannot be read is turned down whole",
                () -> assertThat(instructionText).containsIgnoringCase("square brackets"));
        claim(
                "the request shows how a citation looks, with at least one square-bracketed number",
                () -> assertThat(numbersShown).isNotEmpty());
        claim(
                "no number the request shows as an example is greater than the " + documentsSent
                        + " document(s) this request sent: a model copying the example word for word"
                        + " would otherwise cite a document it was never given, and be turned down for it",
                () -> assertThat(numbersShown).allSatisfy(shown -> assertThat(shown)
                        .isBetween(1, documentsSent)));
    }

    @Test
    @Story("The request says exactly how a citation is written")
    @DisplayName("Writing that cites in curly braces is still turned down as citing nothing")
    void writingThatCitesInCurlyBracesIsStillTurnedDownAsCitingNothing() {
        ScriptedChatModel model = new ScriptedChatModel(WRITING_CITING_IN_CURLY_BRACES);

        ClusterFault fault = faultFrom(model);

        claim(
                "writing that points at both documents in curly braces is turned down as having no"
                        + " citation at all, against the " + DOCUMENTS_SENT + " documents sent: the fix"
                        + " is to ask for the right form, not to accept a second one, because the finished"
                        + " page turns only square-bracketed numbers into links and a curly-braced one"
                        + " would reach a reader as text pointing nowhere",
                () -> assertThat(fault).isEqualTo(new ClusterFault(
                        ClusterFaultKind.CITATION_NOT_IN_RANGE,
                        "no citation at all in the writing, against " + DOCUMENTS_SENT + " document(s) sent")));
    }

    /** What turning {@code model}'s answer down recorded, or {@code null} where it was not turned down. */
    private static ClusterFault faultFrom(ChatModel model) {
        try {
            new ClusterSynthesis(model).docFor(aGroupOfTwo(), MODEL_NAME, ClusterSynthesis.CONTEXT_WINDOW);
            return null;
        } catch (ClusterFaultException turnedDown) {
            return turnedDown.fault();
        }
    }

    /** Two short documents, opening the way two of the GesPOS documents the defect was found on open. */
    private static ClusterCall aGroupOfTwo() {
        return aGroupOf(DOCUMENTS_SENT);
    }

    /**
     * The first {@code size} of three short documents, opening the way three of the GesPOS documents
     * the defect was found on open, in descending score so the order they are sent in is theirs.
     */
    private static ClusterCall aGroupOf(int size) {
        List<Exemplar> threeDocuments = List.of(
                new Exemplar(
                        new OccurrenceId(1),
                        "SPECIFICHE FLUSSI NUOVE RISPOSTE ACCOR. Il documento descrive il tracciato dei"
                                + " flussi di risposta.",
                        14,
                        0.81),
                new Exemplar(
                        new OccurrenceId(2),
                        "Esiti Telegestione CARD_ESITI_TLG: il flusso riporta per ogni terminale l'esito"
                                + " della telegestione.",
                        14,
                        0.77),
                new Exemplar(
                        new OccurrenceId(3),
                        "Specifica del modulo M100: formato dello scontrino e dei campi di chiusura"
                                + " giornaliera.",
                        13,
                        0.74));
        return new ClusterCall(LABEL, SEED_PATH, threeDocuments.subList(0, size));
    }

    /** A model that answers every call with the writing it was given, keeping what it was asked. */
    private static final class ScriptedChatModel implements ChatModel {

        private final String prose;

        private Prompt asked;

        ScriptedChatModel(String prose) {
            this.prose = prose;
        }

        @Override
        public ChatResponse call(Prompt prompt) {
            this.asked = prompt;
            return new ChatResponse(List.of(new Generation(
                    new AssistantMessage("{\"title\":\"" + A_HEADING + "\",\"prose\":\"" + prose + "\"}"))));
        }

        String whatWasAsked() {
            return asked.getContents();
        }

        OllamaChatOptions optionsUsed() {
            return (OllamaChatOptions) asked.getOptions();
        }
    }
}
