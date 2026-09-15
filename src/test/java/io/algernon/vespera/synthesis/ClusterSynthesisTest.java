package io.algernon.vespera.synthesis;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
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
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.ollama.api.OllamaChatOptions;

/**
 * The first prose this system writes (ADR-108, ADR-110, #180): one call per group of documents, and
 * what comes back.
 *
 * <p>Nothing here reaches a database or a serving engine. The seam this module was given is exactly
 * <em>given this group's documents, produce a piece of writing over them</em> — everything it works
 * from arrives as plain values, which is what leaves the call itself checkable without either.
 *
 * <p><b>One call for the whole group, never one per document.</b> A call per document costs a call
 * per document, and what it produces is a summary of each — which is the pile this system exists to
 * refuse, rebuilt one layer up.
 */
@Epic("Synthesis")
@Feature("Writing over the groups")
@Issue("180")
@Link(name = "ADR-108", url = Adr.SIX_B_SENDS_ONE_EXEMPLAR_FIRST_CALL_PER_CLUSTER, type = "adr")
@Link(name = "ADR-110", url = Adr.PIPELINE_HANDS_SYNTHESIS_ITS_INPUTS, type = "adr")
class ClusterSynthesisTest {

    /** The heading the model wrote for the group below, which the answer has to come back carrying. */
    private static final String GENERATED_TITLE = "Site Safety Audits, 2018 to 2021";

    /** The text the model wrote, with the two markers pointing back at the two documents it was given. */
    private static final String GENERATED_PROSE =
            "The earliest audit [1] sets the pattern the later one [2] is measured against.";

    /** What the group is called before anything is generated for it: the name stage 6a derived. */
    private static final String LABEL = "2019 Site Safety Audit";

    /** The document the operator supplied that this group sits under. */
    private static final String SEED_PATH = "seeds/site-safety.txt";

    /** Which model the call names, resolved from configuration and handed down as a plain string. */
    private static final String MODEL_NAME = "qwen3:8b";

    /** How many documents the two-document group holds, and so how many that call was written from. */
    private static final int DOCUMENTS_SENT = 2;

    /** How many the three-document group holds, which is what that call has to say it read. */
    private static final int THREE_DOCUMENTS = 3;

    /** What a group costs: one call, however many documents are in it. */
    private static final int ONE_CALL = 1;

    /**
     * How much the shipped code lets one call read, written out here rather than read off the code it
     * is checking: a number taken from the thing under test would agree with it whatever it changed to,
     * and the whole point of this one is that it does not move on its own.
     */
    private static final int READING_WINDOW = 8192;

    /** The opening chunk of the document sitting closest to the seed, heading and all. */
    private static final String CLOSEST_CHUNK =
            """
            # 2019 Site Safety Audit

            Findings from the spring inspection.""";

    /** How close that one sits: the highest score here, so it is sent first. */
    private static final double CLOSEST_SCORE = 0.81;

    /** The opening chunk of a document sitting between the other two. */
    private static final String MIDDLE_CHUNK =
            """
            # 2020 Site Safety Audit

            The inspection nobody attended.""";

    /** How close that one sits: between the other two, so it is sent second of three. */
    private static final double MIDDLE_SCORE = 0.77;

    /** The opening chunk of the document sitting furthest from the seed. */
    private static final String FURTHEST_CHUNK =
            """
            # 2021 Site Safety Audit

            Follow-up on the 2019 findings.""";

    /** How close that one sits: the lowest score here, so it is sent last. */
    private static final double FURTHEST_SCORE = 0.74;

    @Test
    @Story("A group of documents becomes a piece of writing that connects them")
    @DisplayName("The writing comes back with its own heading, its text, and the number of documents behind it")
    void returnsTheHeadingTheTextAndHowManyDocumentsItWasWrittenFrom() {
        ClusterSynthesis synthesis = new ClusterSynthesis(new ScriptedChatModel());

        SynthesisDoc doc = synthesis.docFor(aGroupOfTwo(), MODEL_NAME);

        claim(
                "the writing carries the heading the model gave it, rather than the name the group already"
                        + " had: a heading written by something that read the whole group is the one a"
                        + " reader is owed",
                () -> assertThat(doc.title()).isEqualTo(GENERATED_TITLE));
        claim(
                "and the text exactly as it came back, markers and all -- what is kept is what was said,"
                        + " because the page a reader opens is built from this rather than being this",
                () -> assertThat(doc.prose()).isEqualTo(GENERATED_PROSE));
        claim(
                "and it says how many documents it was written from, both " + DOCUMENTS_SENT + " of them"
                        + " here: a piece of writing that cannot say what it covers cannot be held against"
                        + " the group it claims to cover",
                () -> assertThat(doc.documentsSent()).isEqualTo(DOCUMENTS_SENT));
    }

    @Test
    @Story("A group of documents is read in one go, never one document at a time")
    @DisplayName("The whole group goes in one call, closest document first, each under the number to cite it by")
    void sendsTheWholeGroupAtOnceInScoreOrderUnderTheNumbersToCiteThemBy() {
        ScriptedChatModel model = new ScriptedChatModel();

        new ClusterSynthesis(model).docFor(aGroupOfThreeOfferedOutOfOrder(), MODEL_NAME);

        claim(
                "all " + THREE_DOCUMENTS + " documents went in " + ONE_CALL + " call: asking once per"
                        + " document would cost one call per document across an archive of hundreds of"
                        + " thousands, and what came back would be a summary of each -- which is the pile"
                        + " this system exists to avoid, rebuilt one level up",
                () -> assertThat(model.callsMade()).isEqualTo(ONE_CALL));
        claim(
                "the documents arrive closest-to-the-seed first, each under the number a reader will follow"
                        + " it back by -- offered here in an order no rule would produce, because putting"
                        + " them in order is this module's job and not the caller's to have done",
                () -> assertThat(model.whatWasAsked())
                        .containsSubsequence(
                                "[1] " + CLOSEST_CHUNK, "[2] " + MIDDLE_CHUNK, "[3] " + FURTHEST_CHUNK));
        claim(
                "the call says what the group is called and what it sits under, so what comes back is about"
                        + " this group of documents rather than about documents in general",
                () -> assertThat(model.whatWasAsked()).contains(LABEL).contains(SEED_PATH));
        claim(
                "and it says how many documents it is working from, all " + THREE_DOCUMENTS + " here: that"
                        + " number is what a later call, working from fewer, will differ from",
                () -> assertThat(model.whatWasAsked()).contains(THREE_DOCUMENTS + " document"));
    }

    @Test
    @Story("What the writing was made under is decided here, not by whichever machine answered")
    @DisplayName("Every call says how much it may read and what shape the answer has to come back in")
    void saysHowMuchItMayReadAndWhatShapeTheAnswerTakes() {
        ScriptedChatModel model = new ScriptedChatModel();

        new ClusterSynthesis(model).docFor(aGroupOfTwo(), MODEL_NAME);

        claim(
                "the call names the model it was told to use, rather than leaving the machine to pick one:"
                        + " which model wrote something is half of what makes it re-checkable later",
                () -> assertThat(model.optionsUsed().getModel()).isEqualTo(MODEL_NAME));
        claim(
                "and it says how much it may read -- " + READING_WINDOW + " -- on every single call."
                        + " Left unsaid, that number is whatever the machine serving happened to decide at"
                        + " startup, so the same archive written on two machines is two different pieces of"
                        + " work and nobody can tell which they are holding",
                () -> assertThat(model.optionsUsed().getNumCtx()).isEqualTo(READING_WINDOW));
        claim(
                "and it fixes the shape the answer comes back in, rather than hoping for one: what comes"
                        + " back has to be split into a heading and the writing itself, and text that has"
                        + " to be guessed at is text that will be guessed at wrongly",
                () -> assertThat(model.optionsUsed().getFormat()).isNotNull());
    }

    /** A group of two documents, each opening with its own first chunk, the closer one scoring higher. */
    private static ClusterCall aGroupOfTwo() {
        return new ClusterCall(LABEL, SEED_PATH, List.of(closest(), furthest()));
    }

    /** The same group with a third document in it, offered in an order no rule would produce. */
    private static ClusterCall aGroupOfThreeOfferedOutOfOrder() {
        return new ClusterCall(LABEL, SEED_PATH, List.of(furthest(), closest(), middle()));
    }

    /** The document sitting closest to the seed, and therefore the one the call leads with. */
    private static Exemplar closest() {
        return new Exemplar(CLOSEST_CHUNK, CLOSEST_SCORE);
    }

    /** The document sitting between the other two. */
    private static Exemplar middle() {
        return new Exemplar(MIDDLE_CHUNK, MIDDLE_SCORE);
    }

    /** The document sitting furthest from the seed, and therefore the one the call sends last. */
    private static Exemplar furthest() {
        return new Exemplar(FURTHEST_CHUNK, FURTHEST_SCORE);
    }

    /**
     * A model that answers every call with the one answer it was given, keeping what it was asked.
     *
     * <p>What it was asked is the point of half these claims: the call is the thing under test, and
     * there is nowhere else to read it from.
     */
    private static final class ScriptedChatModel implements ChatModel {

        private Prompt asked;
        private int calls;

        @Override
        public ChatResponse call(Prompt prompt) {
            this.asked = prompt;
            this.calls++;
            return new ChatResponse(List.of(new Generation(new AssistantMessage(
                    "{\"title\":\"" + GENERATED_TITLE + "\",\"prose\":\"" + GENERATED_PROSE + "\"}"))));
        }

        /** Everything the one call carried, as the text the model was handed. */
        String whatWasAsked() {
            return asked.getContents();
        }

        /** How many times it was asked anything at all. */
        int callsMade() {
            return calls;
        }

        /** What the one call was made under, which is where the reading window and the shape sit. */
        OllamaChatOptions optionsUsed() {
            return (OllamaChatOptions) asked.getOptions();
        }
    }
}
