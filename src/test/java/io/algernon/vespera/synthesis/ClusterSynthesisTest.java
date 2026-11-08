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

    /** What the cluster is called before anything is generated for it: the name stage 6a derived. */
    private static final String LABEL = "2019 Site Safety Audit";

    /** The document the operator supplied that this cluster sits under. */
    private static final String SEED_PATH = "seeds/site-safety.txt";

    /** Which model the call names, resolved from configuration and handed down as a plain string. */
    private static final String MODEL_NAME = "a-writing-model:8b";

    /** How many documents the two-document cluster holds, and so how many that call was written from. */
    private static final int DOCUMENTS_SENT = 2;

    /** How many the three-document cluster holds, which is what that call has to say it read. */
    private static final int THREE_DOCUMENTS = 3;

    /** The opening chunk of the document sitting closest to the seed, heading and all. */
    private static final String CLOSEST_CHUNK =
            """
            # 2019 Site Safety Audit

            Findings from the spring inspection.""";

    /** How close that one sits: the highest score here, so it is sent first. */
    private static final double CLOSEST_SCORE = 0.81;

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

    /** What each long document opens with, which is how a claim tells them apart. */
    private static final String CLOSEST_HEADING = "AUDIT-2019";

    private static final String MIDDLE_HEADING = "AUDIT-2020";

    private static final String FURTHEST_HEADING = "AUDIT-2021";

    /**
     * Which document the closest one is. The three fixture documents are the same three throughout,
     * so a claim can say which document an ordinal was minted against rather than only how many went.
     */
    private static final OccurrenceId THE_CLOSEST_DOCUMENT = new OccurrenceId(11);

    /** Which document the one between them is. */
    private static final OccurrenceId THE_MIDDLE_DOCUMENT = new OccurrenceId(12);

    /** And which the furthest one is. */
    private static final OccurrenceId THE_FURTHEST_DOCUMENT = new OccurrenceId(13);

    /** How the reason for an answer nothing could read opens, before it names where reading stopped. */
    private static final String COULD_NOT_BE_READ_BACK = "the answer did not read back into";

    /** An answer with something in it that is not the shape the call imposed, so reading it fails. */
    private static final String NOT_AN_ANSWER_AT_ALL = "I am afraid I cannot help with that.";

    @Test
    @Story("A group of documents becomes a piece of writing that connects them")
    @DisplayName("The writing comes back with its own heading, its text, and the number of documents behind it")
    void returnsTheTitleTheTextAndHowManyDocumentsItWasWrittenFrom() {
        ClusterSynthesis synthesis = new ClusterSynthesis(new ScriptedChatModel());

        SynthesisDoc doc = synthesis.docFor(aClusterOfTwo(), MODEL_NAME, THE_SHIPPED_WINDOW, DeliverableLanguage.ENGLISH);

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
                "and which documents it was written from, both " + DOCUMENTS_SENT + " of them here, in the"
                        + " order their numbers were minted -- the closest document first, the furthest"
                        + " second: a piece of writing that cannot say what it covers cannot be held against"
                        + " the group it claims to cover, and a count says how much of the group was read and nothing"
                        + " at all about which number in the writing means which document, and nothing"
                        + " downstream can work that out again",
                () -> assertThat(doc.sent()).containsExactly(THE_CLOSEST_DOCUMENT, THE_FURTHEST_DOCUMENT));
    }

    @Test
    @Issue("182")
    @Story("A group too big to read in one go sends what fits, rather than being skipped")
    @DisplayName("When the group will not fit, the closest documents go and the rest are left out")
    void sendsWhatFitsWhenTheClusterIsTooBigToReadInOneGo() {
        ScriptedChatModel model = new ScriptedChatModel();

        SynthesisDoc doc = new ClusterSynthesis(model)
                .docFor(aClusterOfThreeLongDocuments(), MODEL_NAME, A_SMALL_WINDOW, DeliverableLanguage.ENGLISH);

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
                "and the reason for the answer nothing could read still describes where reading it gave"
                        + " up, which is the one of the three that has such a place to name",
                () -> assertThat(unreadable.detail()).startsWith(COULD_NOT_BE_READ_BACK));
    }

    /**
     * A model that hands back the same response to every call asking for the writing, whatever it was
     * asked.
     *
     * <p>A counting call (ADR-166) is answered with a response carrying nothing — no answer and no
     * count, which leaves the documents proposed standing — so that what each claim here scripts is
     * read where the claim says it is: by the checks on the answer, not by the count before it.
     */
    private static ChatModel alwaysAnswering(ChatResponse response) {
        return prompt -> isACountingCall(prompt) ? new ChatResponse(List.of()) : response;
    }

    /** A response that came back carrying no answer at all, which is what {@code getResult} reads. */
    private static ChatResponse carryingNoAnswerAtAll() {
        return new ChatResponse(List.of());
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
                    .docFor(aClusterOfTwo(), MODEL_NAME, THE_SHIPPED_WINDOW, DeliverableLanguage.ENGLISH);
            return null;
        } catch (ClusterFaultException turnedDown) {
            return turnedDown.fault();
        }
    }

    /** A cluster of two documents, each opening with its own first chunk, the closer one scoring higher. */
    private static ClusterCall aClusterOfTwo() {
        return new ClusterCall(LABEL, SEED_PATH, List.of(closest(), furthest()));
    }

    /** The document sitting closest to the seed, and therefore the one the call leads with. */
    private static Exemplar closest() {
        return new Exemplar(THE_CLOSEST_DOCUMENT, CLOSEST_CHUNK, A_HANDFUL_OF_WORDS, CLOSEST_SCORE);
    }

    /** The document sitting furthest from the seed, and therefore the one the call sends last. */
    private static Exemplar furthest() {
        return new Exemplar(THE_FURTHEST_DOCUMENT, FURTHEST_CHUNK, A_HANDFUL_OF_WORDS, FURTHEST_SCORE);
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
                theDocumentHeaded(heading),
                heading + " " + String.join(" ", Collections.nCopies(words - 1, "word")),
                words,
                score);
    }

    /**
     * Which of the three fixture documents opens with {@code heading}.
     *
     * <p>Derived rather than passed in at every call site, because these fixtures have exactly three
     * documents and a heading already names which one: a claim about which document an ordinal was
     * minted against can then read the same three names every other claim here reads.
     */
    private static OccurrenceId theDocumentHeaded(String heading) {
        return switch (heading) {
            case CLOSEST_HEADING -> THE_CLOSEST_DOCUMENT;
            case MIDDLE_HEADING -> THE_MIDDLE_DOCUMENT;
            case FURTHEST_HEADING -> THE_FURTHEST_DOCUMENT;
            default -> throw new IllegalArgumentException(
                    "no fixture document opens with \"" + heading + "\"");
        };
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

        /**
         * Answers the call, and counts it and keeps what it asked only when it asks for the writing.
         *
         * <p>A counting call (ADR-166) — the same request asking for one token — is answered with no
         * count at all, which leaves the documents proposed standing, and is neither counted nor kept:
         * every claim here is about the one question the writing is asked for, and {@code
         * CountedBeforeItIsAnsweredTest} is where the counting call is the subject.
         */
        @Override
        public ChatResponse call(Prompt prompt) {
            ChatResponse answer = new ChatResponse(List.of(new Generation(
                    new AssistantMessage("{\"title\":\"" + GENERATED_TITLE + "\",\"prose\":\"" + prose + "\"}"))));
            if (isACountingCall(prompt)) {
                return answer;
            }
            this.asked = prompt;
            this.calls++;
            return answer;
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

    /** Whether {@code prompt} is a counting call: the answering request asking for one token (ADR-166). */
    static boolean isACountingCall(Prompt prompt) {
        Integer numPredict = ((OllamaChatOptions) prompt.getOptions()).getNumPredict();
        return numPredict != null && numPredict == 1;
    }
}
