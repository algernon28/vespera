package io.algernon.vespera.synthesis;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.util.Collections;
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

    /**
     * Roughly what the short chunks above run to. Only its smallness matters: every test using them
     * works in the shipped window, which documents this size never come near filling.
     */
    private static final int A_HANDFUL_OF_WORDS = 10;

    /** The window the shipped code works in, which the tests above are not about the size of. */
    private static final int THE_SHIPPED_WINDOW = ClusterSynthesis.CONTEXT_WINDOW;

    /**
     * A window small enough that a group stops fitting in it.
     *
     * <p>What it leaves to read documents in is {@link #ROOM_FOR} words. Written out here rather than
     * worked out from the code that does the working out, so that a change to how the room is arrived
     * at fails this test instead of agreeing with itself.
     */
    private static final int A_SMALL_WINDOW = 2048;

    /**
     * How many words of documents {@link #A_SMALL_WINDOW} leaves room for, once the answer has been
     * allowed for and the instructions around the documents have been. The one number in this file a
     * reader has to take on the specification's word rather than the code's.
     */
    private static final int ROOM_FOR = 384;

    /** How long each of the three long documents below is, in words, and it really is this long. */
    private static final int LONG_DOCUMENT_WORDS = 150;

    /** How many of those three fit: two at 150 words each, where a third would pass 384. */
    private static final int WHAT_FITS = 2;

    /** Half the room exactly, so two such documents fill it to the word and neither is left out. */
    private static final int HALF_THE_ROOM = ROOM_FOR / 2;

    /**
     * A document longer than the whole room, so no call in this window could ever carry it — not
     * because others got there first, but because it does not fit an empty one.
     */
    private static final int TOO_BIG_FOR_ANY_CALL = ROOM_FOR + 1;

    /**
     * A document taking up most of the room on its own: {@link #ROOM_FOR} less this leaves 84 words
     * free, which is room for a short document and not for a middling one.
     */
    private static final int FILLS_MOST_OF_THE_ROOM = 300;

    /**
     * A document that fits an empty call and does not fit what is left after the one above. It is not
     * outsized -- a call carrying it alone would carry it -- so what happens to it is a statement about
     * the fill rather than about the document.
     */
    private static final int MORE_THAN_THE_ROOM_LEFT = 200;

    /**
     * A document short enough to have fitted in the 84 words left over, and it sits furthest from the
     * seed. It is the one document that tells a fill which stops from a fill which keeps looking.
     */
    private static final int SHORT_ENOUGH_FOR_WHAT_IS_LEFT = 50;

    /** How many of those three go: the closest one, and the fill stops where the next will not fit. */
    private static final int ONE_DOCUMENT = 1;

    /** What each long document opens with, which is how a claim tells them apart. */
    private static final String CLOSEST_HEADING = "AUDIT-2019";

    private static final String MIDDLE_HEADING = "AUDIT-2020";

    private static final String FURTHEST_HEADING = "AUDIT-2021";

    /**
     * How many times a group no document of which fits is worth asking about: not once, because there
     * would be nothing in the question.
     */
    private static final int NOTHING_WAS_ASKED = 0;

    @Test
    @Story("A group of documents becomes a piece of writing that connects them")
    @DisplayName("The writing comes back with its own heading, its text, and the number of documents behind it")
    void returnsTheHeadingTheTextAndHowManyDocumentsItWasWrittenFrom() {
        ClusterSynthesis synthesis = new ClusterSynthesis(new ScriptedChatModel());

        SynthesisDoc doc = synthesis.docFor(aGroupOfTwo(), MODEL_NAME, THE_SHIPPED_WINDOW);

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

        new ClusterSynthesis(model).docFor(aGroupOfThreeOfferedOutOfOrder(), MODEL_NAME, THE_SHIPPED_WINDOW);

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

        new ClusterSynthesis(model).docFor(aGroupOfTwo(), MODEL_NAME, THE_SHIPPED_WINDOW);

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

    @Test
    @Issue("182")
    @Story("A group too big to read in one go sends what fits, rather than being skipped")
    @DisplayName("When the group will not fit, the closest documents go and the rest are left out")
    void sendsWhatFitsWhenTheGroupIsTooBigToReadInOneGo() {
        ScriptedChatModel model = new ScriptedChatModel();

        SynthesisDoc doc = new ClusterSynthesis(model)
                .docFor(aGroupOfThreeLongDocuments(), MODEL_NAME, A_SMALL_WINDOW);

        claim(
                "the two closest documents went and the third did not: reading room is finite, and what a"
                        + " group is worth reading of is the part of it nearest the document the archive's"
                        + " owner supplied",
                () -> assertThat(model.whatWasAsked())
                        .contains(CLOSEST_HEADING)
                        .contains(MIDDLE_HEADING)
                        .doesNotContain(FURTHEST_HEADING));
        claim(
                "and the writing says it was made from " + WHAT_FITS + " documents rather than from all"
                        + " " + THREE_DOCUMENTS + ": the number is what lets the finished page tell a"
                        + " reader it was written from part of the group, which is the one thing that"
                        + " would otherwise be invisible to them",
                () -> assertThat(doc.documentsSent()).isEqualTo(WHAT_FITS));
    }

    @Test
    @Issue("182")
    @Story("A group too big to read in one go sends what fits, rather than being skipped")
    @DisplayName("A group that fills the room exactly is sent whole, with nothing left out")
    void sendsAGroupThatFillsTheRoomExactly() {
        ScriptedChatModel model = new ScriptedChatModel();

        SynthesisDoc doc = new ClusterSynthesis(model)
                .docFor(
                        new ClusterCall(
                                LABEL,
                                SEED_PATH,
                                List.of(
                                        aDocumentOf(HALF_THE_ROOM, CLOSEST_HEADING, CLOSEST_SCORE),
                                        aDocumentOf(HALF_THE_ROOM, MIDDLE_HEADING, MIDDLE_SCORE))),
                        MODEL_NAME,
                        A_SMALL_WINDOW);

        claim(
                "both documents went: together they come to exactly the " + ROOM_FOR + " words there is"
                        + " room for, and a group that fits is a group that is sent whole -- dropping the"
                        + " last one that fits would quietly cost every group a document for nothing",
                () -> assertThat(doc.documentsSent()).isEqualTo(DOCUMENTS_SENT));
        claim(
                "and both of them really are in what was asked, rather than merely counted",
                () -> assertThat(model.whatWasAsked()).contains(CLOSEST_HEADING).contains(MIDDLE_HEADING));
    }

    @Test
    @Issue("182")
    @Story("A group too big to read in one go sends what fits, rather than being skipped")
    @DisplayName("A document too big for any call is passed over, and the rest of its group is still written about")
    void passesOverADocumentTooBigForAnyCallAndWritesAboutTheRest() {
        ScriptedChatModel model = new ScriptedChatModel();

        SynthesisDoc doc = new ClusterSynthesis(model)
                .docFor(
                        new ClusterCall(
                                LABEL,
                                SEED_PATH,
                                List.of(
                                        aDocumentOf(TOO_BIG_FOR_ANY_CALL, CLOSEST_HEADING, CLOSEST_SCORE),
                                        aDocumentOf(HALF_THE_ROOM, MIDDLE_HEADING, MIDDLE_SCORE),
                                        aDocumentOf(HALF_THE_ROOM, FURTHEST_HEADING, FURTHEST_SCORE))),
                        MODEL_NAME,
                        A_SMALL_WINDOW);

        claim(
                "the outsized document was left out even though it is the closest one to the seed: it does"
                        + " not fit a call with nothing else in it, so there is no call that could ever"
                        + " carry it, and sending it anyway would mean the reading gets cut off somewhere"
                        + " in the middle with nobody told",
                () -> assertThat(model.whatWasAsked()).doesNotContain(CLOSEST_HEADING));
        claim(
                "and the group is still written about from the " + DOCUMENTS_SENT + " documents that do"
                        + " fit, rather than losing its writing because one member was too long -- stopping"
                        + " at the outsized one would have left this group as a hole in the finished work,"
                        + " which is the one outcome nobody reading it could account for",
                () -> assertThat(model.whatWasAsked()).contains(MIDDLE_HEADING).contains(FURTHEST_HEADING));
        claim(
                "and it says it was written from those " + DOCUMENTS_SENT + ", which is what the finished"
                        + " page discloses against a group of " + THREE_DOCUMENTS,
                () -> assertThat(doc.documentsSent()).isEqualTo(DOCUMENTS_SENT));
    }

    @Test
    @Issue("182")
    @Story("A group too big to read in one go sends what fits, rather than being skipped")
    @DisplayName("The fill stops at the first document that will not fit, rather than skipping down to a smaller one")
    void stopsAtTheFirstDocumentThatWillNotFitRatherThanReachingPastIt() {
        ScriptedChatModel model = new ScriptedChatModel();

        SynthesisDoc doc = new ClusterSynthesis(model)
                .docFor(
                        new ClusterCall(
                                LABEL,
                                SEED_PATH,
                                List.of(
                                        aDocumentOf(FILLS_MOST_OF_THE_ROOM, CLOSEST_HEADING, CLOSEST_SCORE),
                                        aDocumentOf(MORE_THAN_THE_ROOM_LEFT, MIDDLE_HEADING, MIDDLE_SCORE),
                                        aDocumentOf(
                                                SHORT_ENOUGH_FOR_WHAT_IS_LEFT,
                                                FURTHEST_HEADING,
                                                FURTHEST_SCORE))),
                        MODEL_NAME,
                        A_SMALL_WINDOW);

        claim(
                "the closest document went, filling " + FILLS_MOST_OF_THE_ROOM + " of the " + ROOM_FOR
                        + " words there is room for",
                () -> assertThat(model.whatWasAsked()).contains(CLOSEST_HEADING));
        claim(
                "the second one did not, because " + MORE_THAN_THE_ROOM_LEFT + " words do not fit what is"
                        + " left of the room",
                () -> assertThat(model.whatWasAsked()).doesNotContain(MIDDLE_HEADING));
        claim(
                "and neither did the third, even though its " + SHORT_ENOUGH_FOR_WHAT_IS_LEFT + " words"
                        + " would have fitted the room still free: what goes is a run of documents from the"
                        + " closest one down, and picking a further-off document over a nearer one that was"
                        + " passed by would mean the writing rests on the group's outskirts while a more"
                        + " central document it skipped is nowhere in the call",
                () -> assertThat(model.whatWasAsked()).doesNotContain(FURTHEST_HEADING));
        claim(
                "so the writing says it was made from " + ONE_DOCUMENT + " document of the "
                        + THREE_DOCUMENTS + " in the group, which is the number the finished page"
                        + " discloses",
                () -> assertThat(doc.documentsSent()).isEqualTo(ONE_DOCUMENT));
    }

    @Test
    @Issue("182")
    @Story("Writing that rests on no document at all is never produced")
    @DisplayName("Asked to write over a group none of whose documents fit, it refuses instead of asking")
    @Link(name = "ADR-121", url = Adr.A_WINDOW_WITH_NO_ROOM_IS_REFUSED, type = "adr")
    void refusesToWriteOverAGroupNoneOfWhoseDocumentsFit() {
        ScriptedChatModel model = new ScriptedChatModel();
        ClusterSynthesis synthesis = new ClusterSynthesis(model);
        ClusterCall nothingFits = new ClusterCall(
                LABEL,
                SEED_PATH,
                List.of(
                        aDocumentOf(TOO_BIG_FOR_ANY_CALL, CLOSEST_HEADING, CLOSEST_SCORE),
                        aDocumentOf(TOO_BIG_FOR_ANY_CALL, MIDDLE_HEADING, MIDDLE_SCORE)));

        claim(
                "being handed a group nothing of which fits the room is refused outright, rather than"
                        + " quietly turning into a question about no documents: whoever owns the reading"
                        + " budget is the only place that can know the fill came out empty, and a rule"
                        + " enforced only by everyone who calls in is a habit rather than a rule",
                () -> assertThatThrownBy(() -> synthesis.docFor(nothingFits, MODEL_NAME, A_SMALL_WINDOW))
                        .isInstanceOf(IllegalStateException.class));
        claim(
                "and the model was asked nothing at all -- " + NOTHING_WAS_ASKED + " calls: a piece of"
                        + " writing made from no documents could point at nothing a reader can open, and"
                        + " asking for one is the most expensive thing this system does",
                () -> assertThat(model.callsMade()).isEqualTo(NOTHING_WAS_ASKED));
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
        return new Exemplar(CLOSEST_CHUNK, A_HANDFUL_OF_WORDS, CLOSEST_SCORE);
    }

    /** The document sitting between the other two. */
    private static Exemplar middle() {
        return new Exemplar(MIDDLE_CHUNK, A_HANDFUL_OF_WORDS, MIDDLE_SCORE);
    }

    /** The document sitting furthest from the seed, and therefore the one the call sends last. */
    private static Exemplar furthest() {
        return new Exemplar(FURTHEST_CHUNK, A_HANDFUL_OF_WORDS, FURTHEST_SCORE);
    }

    /** Three documents of {@link #LONG_DOCUMENT_WORDS} words each, which is one more than fits. */
    private static ClusterCall aGroupOfThreeLongDocuments() {
        return new ClusterCall(
                LABEL,
                SEED_PATH,
                List.of(
                        aLongDocument(FURTHEST_HEADING, FURTHEST_SCORE),
                        aLongDocument(CLOSEST_HEADING, CLOSEST_SCORE),
                        aLongDocument(MIDDLE_HEADING, MIDDLE_SCORE)));
    }

    /**
     * One document that really is {@link #LONG_DOCUMENT_WORDS} words long, opening with {@code
     * heading} so a claim can say which of them went.
     *
     * <p>Built rather than written out, because the point of it is its length: a fixture of a few
     * short documents can never run out of room, and arithmetic nothing ever runs is arithmetic
     * nobody has checked.
     */
    private static Exemplar aLongDocument(String heading, double score) {
        return aDocumentOf(LONG_DOCUMENT_WORDS, heading, score);
    }

    /** One document that really is {@code words} words long, opening with {@code heading}. */
    private static Exemplar aDocumentOf(int words, String heading, double score) {
        return new Exemplar(
                heading + " " + String.join(" ", Collections.nCopies(words - 1, "word")), words, score);
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
