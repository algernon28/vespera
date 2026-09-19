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
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.ollama.api.OllamaChatOptions;

/**
 * The first prose this system writes (ADR-108, ADR-110, #180): one call per cluster of documents, and
 * what comes back.
 *
 * <p>Nothing here reaches a database or a serving engine. The seam this module was given is exactly
 * <em>given this cluster's documents, produce a piece of writing over them</em> — everything it works
 * from arrives as plain values, which is what leaves the call itself checkable without either.
 *
 * <p><b>One call for the whole cluster, never one per document.</b> A call per document costs a call
 * per document, and what it produces is a summary of each — which is the pile this system exists to
 * refuse, rebuilt one layer up.
 */
@Epic("Synthesis")
@Feature("Writing over the groups")
@Issue("180")
@Link(name = "ADR-108", url = Adr.SIX_B_SENDS_ONE_EXEMPLAR_FIRST_CALL_PER_CLUSTER, type = "adr")
@Link(name = "ADR-110", url = Adr.PIPELINE_HANDS_SYNTHESIS_ITS_INPUTS, type = "adr")
class ClusterSynthesisTest {

    /** The title the model wrote for the cluster below, which the answer has to come back carrying. */
    private static final String GENERATED_TITLE = "Site Safety Audits, 2018 to 2021";

    /** The text the model wrote, with the two markers pointing back at the two documents it was given. */
    private static final String GENERATED_PROSE =
            "The earliest audit [1] sets the pattern the later one [2] is measured against.";

    /**
     * The same writing for a call that was given one document: it points at that one and no further.
     *
     * <p>The answer above points at two, which is right for the calls that send two and wrong for the
     * one that sends a single document — an answer pointing past what it was given is refused, so a
     * fixture that did it would be testing the refusal rather than the thing it is about.
     */
    private static final String PROSE_POINTING_AT_THE_ONE_SENT = "The earliest audit [1] sets the pattern.";

    /** What the cluster is called before anything is generated for it: the name stage 6a derived. */
    private static final String LABEL = "2019 Site Safety Audit";

    /** The document the operator supplied that this cluster sits under. */
    private static final String SEED_PATH = "seeds/site-safety.txt";

    /** Which model the call names, resolved from configuration and handed down as a plain string. */
    private static final String MODEL_NAME = "qwen3:8b";

    /** How many documents the two-document cluster holds, and so how many that call was written from. */
    private static final int DOCUMENTS_SENT = 2;

    /** How many the three-document cluster holds, which is what that call has to say it read. */
    private static final int THREE_DOCUMENTS = 3;

    /** What a cluster costs: one call, however many documents are in it. */
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
     * A window small enough that a cluster stops fitting in it.
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
     * How many times a cluster no document of which fits is worth asking about: not once, because there
     * would be nothing in the question.
     */
    private static final int NOTHING_WAS_ASKED = 0;

    /** The reason kept for a call that came back with no answer in it at all (ADR-123). */
    private static final String NO_ANSWER_AT_ALL = "the call came back carrying no answer at all";

    /** The reason kept for an answer that came back with no text, or with nothing but blank (ADR-123). */
    private static final String CAME_BACK_EMPTY = "the answer came back empty";

    /** How the reason for an answer nothing could read opens, before it names where reading stopped. */
    private static final String COULD_NOT_BE_READ_BACK = "the answer did not read back into";

    /** An answer of nothing but blank space, which is the empty answer rather than an unreadable one. */
    private static final String NOTHING_BUT_SPACES = "   ";

    /** An answer with something in it that is not the shape the call imposed, so reading it fails. */
    private static final String NOT_AN_ANSWER_AT_ALL = "I am afraid I cannot help with that.";

    /**
     * What a call reporting nothing about how much of the question it read arrives as: the serving
     * engine's absent count is substituted before this code ever sees it, and {@code 0} is below every
     * window, so such a call is never held against the ceiling (ADR-123).
     */
    private static final int NO_READING_REPORTED = 0;

    /**
     * A reading count at the window sent, which is already the ceiling failing: at or above it means
     * the question was cut down to fit before the model ever read it.
     *
     * <p>Sitting on the boundary rather than over it is the stronger fixture, because a check written
     * as {@code >} instead of {@code >=} passes a count above and fails this one.
     */
    private static final int A_COUNT_AT_THE_CEILING = THE_SHIPPED_WINDOW;

    /** How much a call that came back carrying no answer wrote, which is nothing. */
    private static final int NOTHING_WRITTEN = 0;

    /**
     * An answer carrying a title and no writing at all: the key is simply absent, so reading it back
     * into the shape the call imposed succeeds and leaves the writing missing (ADR-124).
     */
    private static final String AN_ANSWER_WITH_NO_WRITING_IN_IT = "{\"title\":\"" + GENERATED_TITLE + "\"}";

    /**
     * The same answer with the writing present and blank, which is the other way a serving engine
     * returns no writing — and the same event, so the same reason is kept for it (ADR-124).
     */
    private static final String AN_ANSWER_WHOSE_WRITING_IS_BLANK =
            "{\"title\":\"" + GENERATED_TITLE + "\",\"prose\":\"" + NOTHING_BUT_SPACES + "\"}";

    /** The reason kept for an answer that came back with its writing missing or blank (ADR-124). */
    private static final String NO_WRITING_AT_ALL = "the answer came back with no writing in it";

    /**
     * An answer whose writing is there, is real prose, and points at no document at all — which is a
     * different thing from an answer with no writing in it, and is kept as a different failure.
     */
    private static final String AN_ANSWER_POINTING_AT_NOTHING_AT_ALL = "{\"title\":\"" + GENERATED_TITLE
            + "\",\"prose\":\"The audits agree on every finding, and on what should follow from them.\"}";

    /**
     * An answer carrying its writing and no title at all: the key is simply absent, so reading it
     * back into the shape the call imposed succeeds and leaves the title missing (ADR-125).
     */
    private static final String AN_ANSWER_WITH_NO_TITLE_ON_IT = "{\"prose\":\"" + GENERATED_PROSE + "\"}";

    /**
     * The same answer with the title present and blank, which is the other way a serving engine
     * returns no title — and a different event, so a different reason is kept for it (ADR-125).
     */
    private static final String AN_ANSWER_WHOSE_TITLE_IS_BLANK = "{\"title\":\"" + NOTHING_BUT_SPACES
            + "\",\"prose\":\"" + GENERATED_PROSE + "\"}";

    /** An answer that read back perfectly well and carried neither of the two things asked for. */
    private static final String AN_ANSWER_CARRYING_NEITHER = "{}";

    /** The reason kept for an answer whose title never arrived (ADR-125). */
    private static final String NO_TITLE_AT_ALL = "the answer came back with no heading on it";

    /** The reason kept for an answer whose title arrived blank (ADR-125). */
    private static final String A_BLANK_TITLE = "the answer came back with a blank heading";

    /**
     * How many ways one answer can be turned down: four, and the set is closed. Six different reasons
     * now share one of the four, which is honest only while the six read differently from each other.
     */
    private static final int THE_WAYS_AN_ANSWER_IS_TURNED_DOWN = 4;

    @Test
    @Story("A group of documents becomes a piece of writing that connects them")
    @DisplayName("The writing comes back with its own heading, its text, and the number of documents behind it")
    void returnsTheTitleTheTextAndHowManyDocumentsItWasWrittenFrom() {
        ClusterSynthesis synthesis = new ClusterSynthesis(new ScriptedChatModel());

        SynthesisDoc doc = synthesis.docFor(aClusterOfTwo(), MODEL_NAME, THE_SHIPPED_WINDOW);

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
    void sendsTheWholeClusterAtOnceInScoreOrderUnderTheNumbersToCiteThemBy() {
        ScriptedChatModel model = new ScriptedChatModel();

        new ClusterSynthesis(model).docFor(aClusterOfThreeOfferedOutOfOrder(), MODEL_NAME, THE_SHIPPED_WINDOW);

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

        new ClusterSynthesis(model).docFor(aClusterOfTwo(), MODEL_NAME, THE_SHIPPED_WINDOW);

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
    void sendsWhatFitsWhenTheClusterIsTooBigToReadInOneGo() {
        ScriptedChatModel model = new ScriptedChatModel();

        SynthesisDoc doc = new ClusterSynthesis(model)
                .docFor(aClusterOfThreeLongDocuments(), MODEL_NAME, A_SMALL_WINDOW);

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
    void sendsAClusterThatFillsTheRoomExactly() {
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
        ScriptedChatModel model = new ScriptedChatModel(PROSE_POINTING_AT_THE_ONE_SENT);

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
    void refusesToWriteOverAClusterNoneOfWhoseDocumentsFit() {
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

    @Test
    @Issue("222")
    @Story("A call that answered nothing costs its group and nothing else")
    @DisplayName("A call that came back carrying no answer leaves its group unwritten instead of throwing")
    @Link(name = "ADR-123", url = Adr.AN_ANSWER_CARRYING_NOTHING_IS_A_SCHEMA_VIOLATION, type = "adr")
    void turnsDownACallThatCameBackCarryingNoAnswer() {
        ClusterSynthesis synthesis = new ClusterSynthesis(alwaysAnswering(carryingNoAnswerAtAll()));

        claim(
                "a call that came back with nothing in it is turned down the way any other unusable"
                        + " answer is, rather than escaping as some other failure: the reasons kept for"
                        + " the groups already dealt with are written in the same piece of work as this"
                        + " one, and a failure that is not a turned-down answer takes every one of them"
                        + " with it",
                () -> assertThatThrownBy(
                                () -> synthesis.docFor(aClusterOfTwo(), MODEL_NAME, THE_SHIPPED_WINDOW))
                        .isInstanceOf(ClusterFaultException.class));
        claim(
                "and the reason kept says what came back could not be read into the shape that was asked"
                        + " for, in the words \"" + NO_ANSWER_AT_ALL + "\": nothing at all is the smallest"
                        + " case of unreadable rather than a different case, so it takes no new kind",
                () -> assertThat(faultFrom(carryingNoAnswerAtAll()))
                        .isEqualTo(new ClusterFault(ClusterFaultKind.SCHEMA_VIOLATION, NO_ANSWER_AT_ALL)));
    }

    @Test
    @Issue("222")
    @Story("A call that answered nothing costs its group and nothing else")
    @DisplayName("An answer with no text at all leaves its group unwritten instead of throwing")
    @Link(name = "ADR-123", url = Adr.AN_ANSWER_CARRYING_NOTHING_IS_A_SCHEMA_VIOLATION, type = "adr")
    void turnsDownAnAnswerWithNoTextAtAll() {
        ClusterSynthesis synthesis = new ClusterSynthesis(alwaysAnswering(answeringWithNoText()));

        claim(
                "an answer arriving with no text is turned down rather than handed on to the reader that"
                        + " would have to make sense of it: the reader asserts what it was given before"
                        + " it reads a character of it, and what it raises for nothing is not the kind of"
                        + " failure the catch around it is written for",
                () -> assertThatThrownBy(
                                () -> synthesis.docFor(aClusterOfTwo(), MODEL_NAME, THE_SHIPPED_WINDOW))
                        .isInstanceOf(ClusterFaultException.class));
        claim(
                "and the reason kept is \"" + CAME_BACK_EMPTY + "\", which says the answer was empty"
                        + " rather than describing where a reader of it gave up",
                () -> assertThat(faultFrom(answeringWithNoText()))
                        .isEqualTo(new ClusterFault(ClusterFaultKind.SCHEMA_VIOLATION, CAME_BACK_EMPTY)));
    }

    @Test
    @Issue("222")
    @Story("A call that answered nothing costs its group and nothing else")
    @DisplayName("An answer of nothing but spaces is the same empty answer, not a reading that failed")
    @Link(name = "ADR-123", url = Adr.AN_ANSWER_CARRYING_NOTHING_IS_A_SCHEMA_VIOLATION, type = "adr")
    void turnsDownAnAnswerOfNothingButSpaces() {
        claim(
                "an answer of nothing but blank space is kept as the empty answer it is, rather than as"
                        + " a reader running out of input at line 1: both are the same thing to whoever"
                        + " reads the reason, and only one of the two wordings says so",
                () -> assertThat(faultFrom(answeringWith(NOTHING_BUT_SPACES)))
                        .isEqualTo(new ClusterFault(ClusterFaultKind.SCHEMA_VIOLATION, CAME_BACK_EMPTY)));
    }

    @Test
    @Issue("222")
    @Story("A call that answered nothing costs its group and nothing else")
    @DisplayName("The three answers nothing can be made of are told apart by the reason kept for each")
    @Link(name = "ADR-123", url = Adr.AN_ANSWER_CARRYING_NOTHING_IS_A_SCHEMA_VIOLATION, type = "adr")
    void tellsTheThreeUnusableAnswersApartByTheReasonKept() {
        ClusterFault noAnswer = faultFrom(carryingNoAnswerAtAll());
        ClusterFault empty = faultFrom(answeringWithNoText());
        ClusterFault unreadable = faultFrom(answeringWith(NOT_AN_ANSWER_AT_ALL));

        claim(
                "all three are the same kind, because all three are an answer that could not be read"
                        + " into the shape the call imposed",
                () -> assertThat(List.of(noAnswer.kind(), empty.kind(), unreadable.kind()))
                        .containsOnly(ClusterFaultKind.SCHEMA_VIOLATION));
        claim(
                "and each keeps a different reason, so somebody reading the reasons can tell a call that"
                        + " answered nothing from an answer with nothing in it from an answer nothing"
                        + " could be made of -- one kind covering three things is only honest while the"
                        + " three stay distinguishable",
                () -> assertThat(List.of(noAnswer.detail(), empty.detail(), unreadable.detail()))
                        .doesNotHaveDuplicates());
        claim(
                "and the reason for the answer nothing could read still describes where reading it gave"
                        + " up, which is the one of the three that has such a place to name",
                () -> assertThat(unreadable.detail()).startsWith(COULD_NOT_BE_READ_BACK));
    }

    @Test
    @Issue("222")
    @Story("A call that answered nothing costs its group and nothing else")
    @DisplayName("A call that both overran the question and came back empty is kept as the overrun")
    @Link(name = "ADR-123", url = Adr.AN_ANSWER_CARRYING_NOTHING_IS_A_SCHEMA_VIOLATION, type = "adr")
    void keepsTheOverrunForACallThatBothOverranAndCameBackWithNothing() {
        claim(
                "a call reporting it read as much of the question as the window holds, and then coming"
                        + " back with nothing, is kept as the question that did not arrive whole rather"
                        + " than as the empty answer: reading the window's worth means the question was"
                        + " already cut down to fit, and that explains everything after it, the empty"
                        + " answer included -- so the check for it runs first and the reason kept is the"
                        + " one that explains the rest",
                () -> assertThat(faultFrom(carryingNoAnswerAtAllHavingRead(A_COUNT_AT_THE_CEILING))
                                .kind())
                        .isEqualTo(ClusterFaultKind.PROMPT_EVALUATION_CEILING));
    }

    @Test
    @Issue("222")
    @Story("A call reporting nothing about how much it read is believed, not turned down")
    @DisplayName("An answer reporting no reading count at all is believed rather than held against the ceiling")
    @Link(name = "ADR-123", url = Adr.AN_ANSWER_CARRYING_NOTHING_IS_A_SCHEMA_VIOLATION, type = "adr")
    void believesAnAnswerThatReportsNoReadingCountsAtAll() {
        ClusterSynthesis synthesis = new ClusterSynthesis(new ScriptedChatModel());

        SynthesisDoc doc = synthesis.docFor(aClusterOfTwo(), MODEL_NAME, THE_SHIPPED_WINDOW);

        claim(
                "a call whose answer reports no counts of its own is believed rather than failing on the"
                        + " way to being checked: the count is read as a plain number with nothing"
                        + " guarding it, and where a call reports none it arrives as "
                        + NO_READING_REPORTED + " -- which is below every window, so silence about how"
                        + " much was read costs a group nothing",
                () -> assertThat(doc.title()).isEqualTo(GENERATED_TITLE));
    }

    @Test
    @Issue("224")
    @Story("An answer with no writing in it costs its group and nothing else")
    @DisplayName("An answer that came back with a heading and no writing leaves its group unwritten instead of throwing")
    @Link(name = "ADR-124", url = Adr.BOTH_FIELDS_OF_A_PARSED_ANSWER_ARE_REQUIRED, type = "adr")
    void turnsDownAnAnswerWithNoWritingInIt() {
        ClusterSynthesis synthesis =
                new ClusterSynthesis(alwaysAnswering(answeringWith(AN_ANSWER_WITH_NO_WRITING_IN_IT)));

        claim(
                "an answer whose writing is simply not there is turned down the way any other unusable"
                        + " answer is, rather than escaping as some other failure: the reasons kept for"
                        + " the groups already dealt with are written in the same piece of work as this"
                        + " one, and a failure that is not a turned-down answer takes every one of them"
                        + " with it -- so one answer missing its writing would cost not its own group but"
                        + " the record of every group turned down before it",
                () -> assertThatThrownBy(
                                () -> synthesis.docFor(aClusterOfTwo(), MODEL_NAME, THE_SHIPPED_WINDOW))
                        .isInstanceOf(ClusterFaultException.class));
        claim(
                "and the reason kept is \"" + NO_WRITING_AT_ALL + "\", recorded as an answer that could"
                        + " not be read into the shape that was asked for: the shape asked for a heading"
                        + " and writing both, and an answer delivering one of the two did not arrive in"
                        + " it",
                () -> assertThat(faultFrom(answeringWith(AN_ANSWER_WITH_NO_WRITING_IN_IT)))
                        .isEqualTo(new ClusterFault(ClusterFaultKind.SCHEMA_VIOLATION, NO_WRITING_AT_ALL)));
    }

    @Test
    @Issue("224")
    @Story("An answer with no writing in it costs its group and nothing else")
    @DisplayName("An answer whose writing is nothing but blank space is the same missing writing, not a different failure")
    @Link(name = "ADR-124", url = Adr.BOTH_FIELDS_OF_A_PARSED_ANSWER_ARE_REQUIRED, type = "adr")
    void turnsDownAnAnswerWhoseWritingIsNothingButBlankSpace() {
        claim(
                "an answer whose writing came back blank is kept as an answer that did not arrive in the"
                        + " shape asked for, with the reason \"" + NO_WRITING_AT_ALL + "\" -- rather"
                        + " than as writing that pointed at no document, which is what blank writing"
                        + " would otherwise be turned down as. That reason would say the model wrote"
                        + " something and attributed none of it, and the model wrote nothing",
                () -> assertThat(faultFrom(answeringWith(AN_ANSWER_WHOSE_WRITING_IS_BLANK)))
                        .isEqualTo(new ClusterFault(ClusterFaultKind.SCHEMA_VIOLATION, NO_WRITING_AT_ALL)));
        claim(
                "and it keeps exactly the reason an answer with no writing at all keeps: both say the"
                        + " model returned no writing, and which of the two arrives is decided by the"
                        + " machine that answered rather than by anything the archive's owner did or"
                        + " could act on differently",
                () -> assertThat(faultFrom(answeringWith(AN_ANSWER_WHOSE_WRITING_IS_BLANK)))
                        .isEqualTo(faultFrom(answeringWith(AN_ANSWER_WITH_NO_WRITING_IN_IT))));
    }

    @Test
    @Issue("224")
    @Story("An answer with no writing in it costs its group and nothing else")
    @DisplayName("An answer with no writing is told apart from one nothing could read and from writing that points at nothing")
    @Link(name = "ADR-124", url = Adr.BOTH_FIELDS_OF_A_PARSED_ANSWER_ARE_REQUIRED, type = "adr")
    void keepsAnAnswerWithNoWritingApartFromTheOtherReasonsForTurningOneDown() {
        ClusterFault noWriting = faultFrom(answeringWith(AN_ANSWER_WITH_NO_WRITING_IN_IT));
        ClusterFault noAnswerAtAll = faultFrom(carryingNoAnswerAtAll());
        ClusterFault emptyAnswer = faultFrom(answeringWithNoText());
        ClusterFault unreadable = faultFrom(answeringWith(NOT_AN_ANSWER_AT_ALL));
        ClusterFault pointingAtNothing = faultFrom(answeringWith(AN_ANSWER_POINTING_AT_NOTHING_AT_ALL));

        claim(
                "an answer with no writing in it is kept as one that did not arrive in the shape asked"
                        + " for, the same as an answer nothing could read: what came back was readable"
                        + " and still was not the shape, which is the same thing to whoever has to do"
                        + " something about it",
                () -> assertThat(noWriting.kind()).isEqualTo(ClusterFaultKind.SCHEMA_VIOLATION));
        claim(
                "and it is not kept as writing that pointed at no document, which is what an answer with"
                        + " no writing would otherwise fall through to: that reason says the model wrote"
                        + " something and gave the reader no thread back into the archive, and here the"
                        + " model wrote nothing at all. The two want different things done about them",
                () -> assertThat(pointingAtNothing.kind()).isEqualTo(ClusterFaultKind.CITATION_NOT_IN_RANGE));
        claim(
                "and all four answers that arrived in the wrong shape keep four different reasons, so"
                        + " somebody reading them can tell a call that answered nothing from an answer"
                        + " with no text from an answer nothing could be made of from an answer carrying"
                        + " no writing -- one kind covering four things is only honest while the four"
                        + " stay distinguishable",
                () -> assertThat(List.of(
                                noAnswerAtAll.detail(),
                                emptyAnswer.detail(),
                                unreadable.detail(),
                                noWriting.detail()))
                        .doesNotHaveDuplicates());
        claim(
                "and the reason for an answer with no writing says nothing about its heading, because"
                        + " whether a heading arrived is not something this check established",
                () -> assertThat(noWriting.detail()).isEqualTo(NO_WRITING_AT_ALL));
    }

    @Test
    @Issue("216")
    @Story("An answer with no heading on it costs its group and nothing else")
    @DisplayName("An answer that came back with its writing and no heading leaves its group unwritten instead of throwing")
    @Link(name = "ADR-125", url = Adr.AN_ABSENT_TITLE_AND_A_BLANK_ONE_ARE_TOLD_APART, type = "adr")
    void turnsDownAnAnswerWithNoTitleOnIt() {
        ClusterSynthesis synthesis =
                new ClusterSynthesis(alwaysAnswering(answeringWith(AN_ANSWER_WITH_NO_TITLE_ON_IT)));

        claim(
                "an answer whose heading is simply not there is turned down the way any other unusable"
                        + " answer is, rather than escaping as some other failure: a group has to be"
                        + " given a heading before anything can be kept for it, so an answer without one"
                        + " used to take the whole piece of work down -- and with it the reasons kept"
                        + " for every group turned down before this one",
                () -> assertThatThrownBy(
                                () -> synthesis.docFor(aClusterOfTwo(), MODEL_NAME, THE_SHIPPED_WINDOW))
                        .isInstanceOf(ClusterFaultException.class));
        claim(
                "and the reason kept is \"" + NO_TITLE_AT_ALL + "\", recorded as an answer that could"
                        + " not be read into the shape that was asked for: the shape asked for a heading"
                        + " and writing both, and an answer delivering one of the two did not arrive in"
                        + " it",
                () -> assertThat(faultFrom(answeringWith(AN_ANSWER_WITH_NO_TITLE_ON_IT)))
                        .isEqualTo(new ClusterFault(ClusterFaultKind.SCHEMA_VIOLATION, NO_TITLE_AT_ALL)));
    }

    @Test
    @Issue("216")
    @Story("An answer with no heading on it costs its group and nothing else")
    @DisplayName("An answer whose heading is nothing but blank space is kept apart from one whose heading never came")
    @Link(name = "ADR-125", url = Adr.AN_ABSENT_TITLE_AND_A_BLANK_ONE_ARE_TOLD_APART, type = "adr")
    void keepsABlankTitleApartFromATitleThatNeverCame() {
        claim(
                "an answer whose heading came back blank is turned down too, with the reason \""
                        + A_BLANK_TITLE + "\" -- blank space is not a heading, and writing kept under"
                        + " one would show in the finished work as an entry with nothing to click",
                () -> assertThat(faultFrom(answeringWith(AN_ANSWER_WHOSE_TITLE_IS_BLANK)))
                        .isEqualTo(new ClusterFault(ClusterFaultKind.SCHEMA_VIOLATION, A_BLANK_TITLE)));
        claim(
                "and it does not keep the reason an answer with no heading at all keeps. The two are"
                        + " different events to whoever has to act on them: a blank heading was accepted"
                        + " and kept by earlier versions of this tool, so a finished work already handed"
                        + " over may carry one and nothing will ever go back and mend it, while a heading"
                        + " that never arrived could never be kept at all and so left nothing behind to"
                        + " find",
                () -> assertThat(faultFrom(answeringWith(AN_ANSWER_WHOSE_TITLE_IS_BLANK)))
                        .isNotEqualTo(faultFrom(answeringWith(AN_ANSWER_WITH_NO_TITLE_ON_IT))));
    }

    @Test
    @Issue("216")
    @Story("An answer with no heading on it costs its group and nothing else")
    @DisplayName("Every answer that arrived in the wrong shape keeps a reason of its own, under the one closed set of four")
    @Link(name = "ADR-125", url = Adr.AN_ABSENT_TITLE_AND_A_BLANK_ONE_ARE_TOLD_APART, type = "adr")
    void keepsEveryAnswerInTheWrongShapeApartFromTheOthers() {
        ClusterFault noTitle = faultFrom(answeringWith(AN_ANSWER_WITH_NO_TITLE_ON_IT));
        ClusterFault blankTitle = faultFrom(answeringWith(AN_ANSWER_WHOSE_TITLE_IS_BLANK));
        ClusterFault noWriting = faultFrom(answeringWith(AN_ANSWER_WITH_NO_WRITING_IN_IT));
        ClusterFault noAnswerAtAll = faultFrom(carryingNoAnswerAtAll());
        ClusterFault emptyAnswer = faultFrom(answeringWithNoText());
        ClusterFault unreadable = faultFrom(answeringWith(NOT_AN_ANSWER_AT_ALL));

        claim(
                "an answer missing its heading, and one whose heading is blank, are both kept as answers"
                        + " that did not arrive in the shape asked for -- the same as an answer nothing"
                        + " could read, because what came back was readable and still was not the shape",
                () -> assertThat(List.of(noTitle.kind(), blankTitle.kind()))
                        .containsOnly(ClusterFaultKind.SCHEMA_VIOLATION));
        claim(
                "and the six reasons now sharing that one way of being turned down all read differently"
                        + " from one another, so somebody reading them can tell a call that answered"
                        + " nothing from an answer with no text from an answer nothing could be made of"
                        + " from an answer carrying no writing from one carrying no heading from one"
                        + " whose heading was blank -- one way covering six things is only honest while"
                        + " the six stay distinguishable",
                () -> assertThat(List.of(
                                noAnswerAtAll.detail(),
                                emptyAnswer.detail(),
                                unreadable.detail(),
                                noWriting.detail(),
                                noTitle.detail(),
                                blankTitle.detail()))
                        .doesNotHaveDuplicates());
        claim(
                "and there are still only " + THE_WAYS_AN_ANSWER_IS_TURNED_DOWN + " ways an answer is"
                        + " turned down: a heading that did not arrive is one more thing an answer can"
                        + " fail to be, not a new kind of failure, and it leaves the archive's owner the"
                        + " same thing to do -- ask again, or accept the gap",
                () -> assertThat(ClusterFaultKind.values()).hasSize(THE_WAYS_AN_ANSWER_IS_TURNED_DOWN));
        claim(
                "and neither reason about the heading says anything about the writing, because whether"
                        + " the writing arrived is not something this check established",
                () -> assertThat(List.of(noTitle.detail(), blankTitle.detail()))
                        .noneMatch(reason -> reason.contains("writing")));
    }

    @Test
    @Issue("216")
    @Story("An answer with no heading on it costs its group and nothing else")
    @DisplayName("An answer carrying neither a heading nor any writing is reported on its heading, the first thing asked for")
    @Link(name = "ADR-125", url = Adr.AN_ABSENT_TITLE_AND_A_BLANK_ONE_ARE_TOLD_APART, type = "adr")
    void namesTheTitleFirstWhenNeitherOfTheTwoCameBack() {
        claim(
                "an answer carrying neither of the two things asked for is reported on the heading, which"
                        + " is the first of them the shape asks for: reading the answer in the order the"
                        + " shape lists is a rule that needs no case to be thought about, and without one"
                        + " which of the two an operator is told about would be decided by the order the"
                        + " code happens to read in",
                () -> assertThat(faultFrom(answeringWith(AN_ANSWER_CARRYING_NEITHER)))
                        .isEqualTo(new ClusterFault(ClusterFaultKind.SCHEMA_VIOLATION, NO_TITLE_AT_ALL)));
    }

    /** A model that hands back the same response to every call, whatever it was asked. */
    private static ChatModel alwaysAnswering(ChatResponse response) {
        return prompt -> response;
    }

    /** A response that came back carrying no answer at all, which is what {@code getResult} reads. */
    private static ChatResponse carryingNoAnswerAtAll() {
        return new ChatResponse(List.of());
    }

    /**
     * The same response, reporting that it read {@code promptTokens} tokens of the question.
     *
     * <p>A response with nothing in it still reports what it read, which is what lets a call be both
     * over the ceiling and empty — the one case that says which of the two checks runs first.
     */
    private static ChatResponse carryingNoAnswerAtAllHavingRead(int promptTokens) {
        return new ChatResponse(
                List.of(),
                ChatResponseMetadata.builder()
                        .usage(new DefaultUsage(promptTokens, NOTHING_WRITTEN))
                        .build());
    }

    /** A response carrying an answer whose text is missing altogether. */
    private static ChatResponse answeringWithNoText() {
        return answeringWith(null);
    }

    /** A response carrying an answer whose text is exactly {@code text}. */
    private static ChatResponse answeringWith(String text) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
    }

    /**
     * The reason kept for {@code response}, having put one cluster through a synthesis answering it.
     *
     * <p>Returns {@code null} where the answer was believed, so a claim expecting a reason says so by
     * failing on the reason rather than on an exception escaping from a helper.
     */
    private static ClusterFault faultFrom(ChatResponse response) {
        try {
            new ClusterSynthesis(alwaysAnswering(response))
                    .docFor(aClusterOfTwo(), MODEL_NAME, THE_SHIPPED_WINDOW);
            return null;
        } catch (ClusterFaultException turnedDown) {
            return turnedDown.fault();
        }
    }

    /** A cluster of two documents, each opening with its own first chunk, the closer one scoring higher. */
    private static ClusterCall aClusterOfTwo() {
        return new ClusterCall(LABEL, SEED_PATH, List.of(closest(), furthest()));
    }

    /** The same cluster with a third document in it, offered in an order no rule would produce. */
    private static ClusterCall aClusterOfThreeOfferedOutOfOrder() {
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
    private static ClusterCall aClusterOfThreeLongDocuments() {
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

        private final String prose;

        private Prompt asked;
        private int calls;

        /** Answers with prose pointing at two documents, which is what most of these calls are given. */
        ScriptedChatModel() {
            this(GENERATED_PROSE);
        }

        /**
         * Answers with prose of the caller's choosing.
         *
         * <p>A test that sends fewer documents than the usual answer points at needs its own, because
         * the answer is now checked against how many were sent and pointing past them is refused.
         */
        ScriptedChatModel(String prose) {
            this.prose = prose;
        }

        @Override
        public ChatResponse call(Prompt prompt) {
            this.asked = prompt;
            this.calls++;
            return new ChatResponse(List.of(new Generation(
                    new AssistantMessage("{\"title\":\"" + GENERATED_TITLE + "\",\"prose\":\"" + prose + "\"}"))));
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
