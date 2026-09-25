package io.algernon.vespera.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.corpus.Walk;
import io.algernon.vespera.ledger.RunId;
import io.algernon.vespera.pipeline.GenerationScriptedBeans.ScriptedAnswer;
import io.algernon.vespera.profile.Profile;
import io.algernon.vespera.profile.ProfileFixture;
import io.algernon.vespera.profile.ProfileStore;
import io.algernon.vespera.synthesis.ClusterFaultKind;
import io.algernon.vespera.synthesis.ClusterFaults;
import io.algernon.vespera.synthesis.RecordedClusterFault;
import io.algernon.vespera.synthesis.RecordedSynthesisDoc;
import io.algernon.vespera.synthesis.SynthesisDocs;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Stage 6b when the answer is not believed (ADR-108, ADR-109, ADR-111, ADR-116, ADR-121, #183): four
 * checks run before a word of an answer is kept, and a cluster whose answer fails one of them costs
 * that cluster and nothing else.
 *
 * <p><b>Why this is an invocation test rather than a unit one.</b> What each check costs is spread
 * across a seam: the checking happens where the call is made, the reason is kept in a table, and
 * whether the run carries on and whether the work counts as done are decided by the step around it.
 * A test of the checking alone would leave every one of those unclaimed.
 *
 * <p><b>Each test fails exactly one check.</b> The fixture's ordinary answer passes all four, and
 * each test here scripts one property of one answer away from that. A fixture that failed two at once
 * would pin whichever the code happened to test first, which is an ordering no record decides.
 *
 * <p><b>The two citation failures ADR-109 states separately are recorded separately</b>, which is
 * settled here: its uncited-prose rule is a clause of its own beside its range check, so a cluster
 * whose prose cited nothing at all and a cluster whose prose cited {@code [0]} may not leave the same
 * detail behind. The wording is fixed as {@code "no citation at all in the writing, against N
 * document(s) sent"} for the first and the existing {@code "citation 0 against N document(s) sent"}
 * for the second, and {@link #turnsDownWritingThatPointsAtNothingAtAll} and {@link
 * #turnsDownWritingThatPointsBelowTheFirstDocument} each claim their own reading and the absence of
 * the other's.
 *
 * <p><b>The consecutive-run breaker is deliberately not reached here.</b> No test in this class
 * leaves five clusters turned down in a row, so nothing here depends on what happens when it fires;
 * {@link GenerationBreakerInvocationTest} is where that is claimed.
 *
 * <p><b>ADR-121's deferred question is settled here in the negative</b>, and the last test is the
 * pin: a cluster whose every document is larger than the reading room gets no reason recorded, because
 * the four ways an answer can be turned down are four things a returned answer can do, and no answer
 * was ever returned for that cluster. The step stays unfinished either way, so a fifth kind would buy
 * the operator a row and no change of behaviour.
 *
 * <p><b>The report says <em>group</em> and the code says <em>cluster</em>, and that is settled rather
 * than sloppy</b> (ADR-122). Everything a reader of the report sees -- the feature, the stories, the
 * display names, every claim -- renders the term as <em>group</em>, because the everyday sense of
 * "cluster" is a set of interchangeable things, which is what {@code CONTEXT.md} says a cluster is
 * not, and that reader cannot open {@code CONTEXT.md} to find out. Everything this project names for
 * itself -- the constants, the fixture methods, these comments -- says cluster. Do not reconcile the
 * two by changing either side.
 */
@CascadeSliceTest
@Import({ClusterFaults.class, SeedScriptedExtractionBeans.class})
@Epic("Synthesis")
@Feature("Writing over the groups")
@Issue("183")
@Link(name = "ADR-108", url = Adr.SIX_B_SENDS_ONE_EXEMPLAR_FIRST_CALL_PER_CLUSTER, type = "adr")
@Link(name = "ADR-109", url = Adr.A_CITATION_IS_AN_ORDINAL_MINTED_FOR_ONE_CALL, type = "adr")
@Link(name = "ADR-111", url = Adr.A_CLUSTER_FAULT_IS_A_ROW_IN_SYNTHESIS, type = "adr")
class GenerationFaultInvocationTest {

    /** A floor of 1.0 opens stage 4's gate the way every other invocation test opens it. */
    private static final String BOILERPLATE_FLOOR = "1.0";

    /** The embedding model this fixture names, so gate 3 and everything behind it open. */
    private static final String EMBEDDING_MODEL_NAME = "qwen3-embedding:0.6b";

    /**
     * How much every call in this class may read, in tokens, written into the profile by the fixture
     * rather than left at whatever the shipped default is.
     *
     * <p>Two reasons it is stated. One test sets it much lower and the profile outlives a test method,
     * so a window nobody stated would be whichever the last test left behind. And the number is what
     * the first of the four checks compares against, so a test claiming an answer reached it has to
     * know what it reached.
     */
    private static final String THE_READING_WINDOW = "4096";

    /** The same number as an integer, which is the ceiling the first check reads against. */
    private static final int THE_CEILING = 4096;

    /**
     * A prompt count past the ceiling: the model reports having read more of the question than the
     * window holds, which can only mean what it read was cut down to fit.
     */
    private static final int A_COUNT_PAST_THE_CEILING = 5000;

    /** A prompt count of exactly the ceiling, which is the boundary the check has to fail on. */
    private static final int A_COUNT_AT_THE_CEILING = THE_CEILING;

    /** One token short of the ceiling: the whole question arrived, and the answer stands. */
    private static final int A_COUNT_UNDER_THE_CEILING = THE_CEILING - 1;

    /**
     * How long an answer that ran out of room came to, in tokens: the whole of what an answer is
     * allowed, which is what reaching the end of the allowance looks like from outside.
     */
    private static final int THE_WHOLE_ANSWER_ALLOWANCE = 1024;

    /** An answer nothing can read back into a heading and its writing: it stops partway through. */
    private static final String AN_ANSWER_NOTHING_CAN_READ = "{\"title\":\"Site Safety Audits\",\"prose\":";

    /** The title scripted alongside prose whose numbers are under test. */
    private static final String A_TITLE = "What The Two Stubbed Documents Have In Common";

    /**
     * An answer that reads back perfectly well and carries no writing: a title arrived, and the
     * writing the call asked for is simply not there (ADR-124).
     */
    private static final String AN_ANSWER_WITH_NO_WRITING_IN_IT = "{\"title\":\"" + A_TITLE + "\"}";

    /**
     * Writing pointing at a document number the call never carried. The call sends two documents, under
     * the numbers 1 and 2, so 7 is a number this system never minted for it.
     */
    private static final int A_NUMBER_NO_DOCUMENT_WAS_SENT_UNDER = 7;

    private static final String PROSE_POINTING_AT_NOTHING =
            "The audits [" + A_NUMBER_NO_DOCUMENT_WAS_SENT_UNDER + "] agree on every finding.";

    /** Writing that points at nothing at all: prose that reads as sourced and is traceable to nothing. */
    private static final String PROSE_POINTING_AT_NOTHING_AT_ALL =
            "The audits agree on every finding, and on what should follow from them.";

    /**
     * A run of digits far too long to be a document number: a date written out, which is entirely
     * ordinary prose about audits and is bracketed like a pointer at a document.
     *
     * <p>It is the number this system cannot read as a number at all, and the reason it is here is that
     * the answer to that has to be the same as the answer to any other number past the end -- this
     * cluster turned down, the rest of the archive carried on -- rather than the run falling over on the
     * reading.
     */
    private static final String A_NUMBER_TOO_LONG_TO_BE_A_DOCUMENT = "20190412120000";

    private static final String PROSE_POINTING_AT_A_NUMBER_TOO_LONG =
            "The audits [" + A_NUMBER_TOO_LONG_TO_BE_A_DOCUMENT + "] agree on every finding.";

    /**
     * The number below the first document a call sends: the documents are numbered from one, so zero
     * is a number no call ever mints and a piece of writing pointing at it points at nothing.
     */
    private static final String THE_NUMBER_BELOW_THE_FIRST_DOCUMENT = "0";

    private static final String PROSE_POINTING_BELOW_THE_FIRST_DOCUMENT =
            "The audits [" + THE_NUMBER_BELOW_THE_FIRST_DOCUMENT + "] agree on every finding.";

    /**
     * What the reason reads when the writing pointed at no document at all.
     *
     * <p>It has to read differently from the reason kept when the writing pointed at a number that is
     * out of range, because those are two different things for the archive's owner to do something
     * about: one is writing that rests on nothing, the other is writing that rests on a document
     * nobody sent. A reason naming a number leaves the first of them indistinguishable from writing
     * that pointed at document zero.
     */
    private static final String THE_REASON_FOR_POINTING_AT_NOTHING_AT_ALL = "no citation at all";

    /** What the reason reads when the writing pointed at the number below the first document. */
    private static final String THE_REASON_FOR_POINTING_BELOW_THE_FIRST_DOCUMENT = "citation 0";

    /** What one cluster is worth: one call, and never a second one because the first was turned down. */
    private static final int ONE_CALL = 1;

    /** What two clusters are worth, and what a run that carried on past the first of them cost. */
    private static final int TWO_CALLS = 2;

    /**
     * What one cluster left unwritten costs over two invocations: one call each time.
     *
     * <p>The count is not dropped between the two invocations of a test, so this is both of them added
     * together. Fewer would mean the second invocation walked past a cluster it had left unwritten, which
     * is the opposite of what running again is for; more would mean it asked twice in one invocation.
     */
    private static final int A_CALL_FOR_EACH_OF_TWO_INVOCATIONS = 2;

    /** What one turned-down answer leaves behind: one reason, kept against the cluster it was about. */
    private static final int ONE_REASON_KEPT = 1;

    /** Two clusters turned down in one piece of work, which is what the second one must not undo. */
    private static final int TWO_REASONS_KEPT = 2;

    /** The reason kept for a call that came back with no answer in it at all (ADR-123). */
    private static final String NO_ANSWER_AT_ALL = "the call came back carrying no answer at all";

    /** The reason kept for an answer whose writing was missing or blank (ADR-124). */
    private static final String NO_WRITING_AT_ALL = "the answer came back with no writing in it";

    /**
     * An answer that reads back perfectly well and carries no title: the writing arrived, and the
     * title the call asked for is simply not there (ADR-125).
     *
     * <p>Its writing points at the first document the call sent, so nothing but the missing title is
     * wrong with it — an answer failing two checks would be pinning whichever runs first.
     */
    private static final String AN_ANSWER_WITH_NO_TITLE_ON_IT =
            "{\"prose\":\"" + GenerationScriptedBeans.GENERATED_PROSE + "\"}";

    /** A title of nothing but blank space, which is the other way one fails to arrive (ADR-125). */
    private static final String NOTHING_BUT_SPACES = "   ";

    /** The same answer with its title present and blank, which earlier versions of this tool kept. */
    private static final String AN_ANSWER_WHOSE_TITLE_IS_BLANK = "{\"title\":\"" + NOTHING_BUT_SPACES
            + "\",\"prose\":\"" + GenerationScriptedBeans.GENERATED_PROSE + "\"}";

    /** The reason kept for an answer whose title never arrived (ADR-125). */
    private static final String NO_TITLE_AT_ALL = "the answer came back with no heading on it";

    /** The reason kept for an answer whose title arrived blank (ADR-125). */
    private static final String A_BLANK_TITLE = "the answer came back with a blank heading";

    /**
     * How many records two invocations over an unchanged archive work under.
     *
     * <p>One: a record's name is derived from what it reads, and nothing about the archive moved
     * between them — which is what makes the second invocation carry on with the first one's work
     * rather than start a second attempt beside it.
     */
    private static final int ONE_RUN_FOR_BOTH_INVOCATIONS = 1;

    /** What the other cluster of a two-cluster run leaves behind when its own answer was believed. */
    private static final int ONE_PIECE_OF_WRITING = 1;

    /** Where the first of the two questions put about one cluster sits in the order they were asked. */
    private static final int THE_QUESTION_ASKED_FIRST = 0;

    /** Where the second sits: the one put after the first answer was turned down. */
    private static final int THE_QUESTION_ASKED_AGAIN = 1;

    /** What a cluster nothing fits into is worth asking about: nothing, because there is nothing to ask. */
    private static final int NOTHING_WAS_ASKED = 0;

    /**
     * The smallest window anything can be read in at all: room for one word, which no document in this
     * fixture opens with fewer than. Accepted as a window, and too small for any of these documents.
     */
    private static final String A_WINDOW_WITH_ROOM_FOR_A_SINGLE_WORD = "1282";

    /** The name given to the cluster whose answer is scripted away from the ordinary one. */
    private static final String THE_CLUSTER_ANSWERED_BADLY = "A group whose answer is not believed";

    @TempDir
    static Path workingDirectory;

    @DynamicPropertySource
    static void workingDirectory(DynamicPropertyRegistry registry) {
        registry.add("vespera.working-dir", workingDirectory::toString);
    }

    @Autowired
    private VesperaCli cli;

    @Autowired
    private ProfileStore profileStore;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private SynthesisDocs synthesisDocs;

    @Autowired
    private ClusterFaults clusterFaults;

    @Test
    @Story("An answer written from part of the question is never believed")
    @DisplayName("An answer reporting more question than the window holds leaves its group unwritten")
    void turnsDownAnAnswerThatReadMoreThanTheWindowHolds(@TempDir Path root, @TempDir Path seeds)
            throws IOException {
        anApprovedCorpus(root, seeds);
        GenerationScriptedBeans.answerFor(
                theOnlyCluster(), anOrdinaryAnswer().havingRead(A_COUNT_PAST_THE_CEILING));

        cli.run("run", root.toString());

        claim(
                "the reason kept says the question did not arrive whole: a model reporting it read "
                        + A_COUNT_PAST_THE_CEILING + " tokens of a question a window of " + THE_CEILING
                        + " tokens holds has been handed something cut down to fit, so what it wrote is"
                        + " about part of a group with nothing in the answer saying which part",
                () -> assertThat(reasonsKept(root)).singleElement().satisfies(kept -> assertThat(
                                kept.fault().kind())
                        .isEqualTo(ClusterFaultKind.PROMPT_EVALUATION_CEILING)));
        claim(
                "and it carries the count that failed, " + A_COUNT_PAST_THE_CEILING + ", so the archive's"
                        + " owner can tell a question that was cut short from an answer that was, without"
                        + " paying for the call a second time to find out",
                () -> assertThat(reasonsKept(root))
                        .singleElement()
                        .satisfies(kept -> assertThat(kept.fault().detail())
                                .contains(String.valueOf(A_COUNT_PAST_THE_CEILING))));
        claim(
                "and nothing was written over the group: an answer that failed a check is not kept in any"
                        + " form, because a page built from it would read exactly like a page nobody had"
                        + " any reason to doubt",
                () -> assertThat(writingKept(root)).isEmpty());
    }

    @Test
    @Story("An answer written from part of the question is never believed")
    @DisplayName("A question filling the window exactly is already too much, and its group is left unwritten")
    void turnsDownAnAnswerThatFilledTheWindowExactly(@TempDir Path root, @TempDir Path seeds)
            throws IOException {
        anApprovedCorpus(root, seeds);
        GenerationScriptedBeans.answerFor(
                theOnlyCluster(), anOrdinaryAnswer().havingRead(A_COUNT_AT_THE_CEILING));

        cli.run("run", root.toString());

        claim(
                "a question filling the window to the last token is turned down rather than let through:"
                        + " reaching the ceiling is how being cut down to fit looks from this side, since"
                        + " what was dropped was dropped before the count was taken -- so " + THE_CEILING
                        + " of a " + THE_CEILING + "-token window fails where " + A_COUNT_UNDER_THE_CEILING
                        + " does not",
                () -> assertThat(reasonsKept(root)).singleElement().satisfies(kept -> assertThat(
                                kept.fault().kind())
                        .isEqualTo(ClusterFaultKind.PROMPT_EVALUATION_CEILING)));
    }

    @Test
    @Story("An answer written from part of the question is never believed")
    @DisplayName("A question stopping one token short of the window is believed, and its group is written")
    void believesAnAnswerThatStoppedShortOfTheWindow(@TempDir Path root, @TempDir Path seeds)
            throws IOException {
        anApprovedCorpus(root, seeds);
        GenerationScriptedBeans.answerFor(
                theOnlyCluster(), anOrdinaryAnswer().havingRead(A_COUNT_UNDER_THE_CEILING));

        cli.run("run", root.toString());

        claim(
                "the group is written over: a question of " + A_COUNT_UNDER_THE_CEILING + " tokens in a"
                        + " window of " + THE_CEILING + " arrived whole, and a check that turned this one"
                        + " down would leave every group in the archive unwritten while looking exactly"
                        + " like a careful one",
                () -> assertThat(writingKept(root)).hasSize(ONE_PIECE_OF_WRITING));
        claim(
                "and no reason is kept, because there is nothing to explain",
                () -> assertThat(reasonsKept(root)).isEmpty());
    }

    @Test
    @Story("An answer that was cut off is never handed on as though it were finished")
    @DisplayName("An answer that stopped because it ran out of room leaves its group unwritten")
    void turnsDownAnAnswerThatRanOutOfRoom(@TempDir Path root, @TempDir Path seeds) throws IOException {
        anApprovedCorpus(root, seeds);
        GenerationScriptedBeans.answerFor(
                theOnlyCluster(), anOrdinaryAnswer().stoppedForRoomAfter(THE_WHOLE_ANSWER_ALLOWANCE));

        cli.run("run", root.toString());

        claim(
                "the reason kept says the answer ran out of room rather than finishing: an answer that was"
                        + " cut off arrives looking like any other, ends mid-sentence, and would be handed"
                        + " to a reader as a finished piece of writing",
                () -> assertThat(reasonsKept(root)).singleElement().satisfies(kept -> assertThat(
                                kept.fault().kind())
                        .isEqualTo(ClusterFaultKind.ANSWER_RAN_OUT_OF_ROOM)));
        claim(
                "and it carries how long the answer had run to when it stopped, "
                        + THE_WHOLE_ANSWER_ALLOWANCE + " tokens, which is the whole of what an answer is"
                        + " allowed -- the number that says the writing was stopped by the allowance"
                        + " rather than by having finished",
                () -> assertThat(reasonsKept(root))
                        .singleElement()
                        .satisfies(kept -> assertThat(kept.fault().detail())
                                .contains(String.valueOf(THE_WHOLE_ANSWER_ALLOWANCE))));
        claim(
                "and nothing was written over the group",
                () -> assertThat(writingKept(root)).isEmpty());
    }

    @Test
    @Story("An answer that cannot be read is turned down rather than stopping the whole run")
    @DisplayName("An answer nothing can read leaves its group unwritten and the invocation still succeeds")
    void turnsDownAnAnswerNothingCanRead(@TempDir Path root, @TempDir Path seeds) throws IOException {
        anApprovedCorpus(root, seeds);
        GenerationScriptedBeans.answerFor(
                theOnlyCluster(), ScriptedAnswer.arrivingAs(AN_ANSWER_NOTHING_CAN_READ));

        cli.run("run", root.toString());

        claim(
                "the invocation reports success: an answer that came back in the wrong shape costs the"
                        + " group it was about and nothing else, where a run that fell over on it would"
                        + " cost every group after it -- and on an archive of hundreds of thousands of"
                        + " documents that is a night's work thrown away over one answer",
                () -> assertThat(cli.getExitCode()).isZero());
        claim(
                "the reason kept says the answer did not come back in the shape the call asked for",
                () -> assertThat(reasonsKept(root)).singleElement().satisfies(kept -> assertThat(
                                kept.fault().kind())
                        .isEqualTo(ClusterFaultKind.SCHEMA_VIOLATION)));
        claim(
                "and it says where reading the answer stopped rather than being left blank: a reason"
                        + " holding nothing is a row that records that something happened and not what,"
                        + " which costs the most expensive call this system makes to find out",
                () -> assertThat(reasonsKept(root)).singleElement().satisfies(kept -> assertThat(
                                kept.fault().detail())
                        .isNotBlank()
                        .containsPattern("[0-9]")));
        claim(
                "and nothing was written over the group",
                () -> assertThat(writingKept(root)).isEmpty());
    }

    @Test
    @Story("Writing pointing at a document nobody sent is never published")
    @DisplayName("Writing pointing at a number the call never sent leaves its group unwritten")
    void turnsDownWritingThatPointsAtADocumentTheCallNeverSent(@TempDir Path root, @TempDir Path seeds)
            throws IOException {
        anApprovedCorpus(root, seeds);
        GenerationScriptedBeans.answerFor(theOnlyCluster(), A_TITLE, PROSE_POINTING_AT_NOTHING);

        cli.run("run", root.toString());

        claim(
                "the reason kept says a number in the writing points at no document the call carried: the"
                        + " numbers are minted here, one call at a time, which is what makes a made-up one"
                        + " out of range rather than merely wrong -- and a reader following it would land"
                        + " on nothing at all",
                () -> assertThat(reasonsKept(root)).singleElement().satisfies(kept -> assertThat(
                                kept.fault().kind())
                        .isEqualTo(ClusterFaultKind.CITATION_NOT_IN_RANGE)));
        claim(
                "and it carries the number that failed, " + A_NUMBER_NO_DOCUMENT_WAS_SENT_UNDER
                        + ", against a call that sent two documents under the numbers 1 and 2",
                () -> assertThat(reasonsKept(root))
                        .singleElement()
                        .satisfies(kept -> assertThat(kept.fault().detail())
                                .contains(String.valueOf(A_NUMBER_NO_DOCUMENT_WAS_SENT_UNDER))));
        claim(
                "and the writing is not kept with the offending number quietly taken out of it: stripped,"
                        + " the text still reads as pointing at a source while the source is gone, which is"
                        + " the one outcome that leaves the finished work looking better founded than it"
                        + " is. The group is left unwritten instead",
                () -> assertThat(writingKept(root)).isEmpty());
    }

    @Test
    @Story("Writing pointing at nothing at all is never published")
    @DisplayName("Writing that points at no document at all leaves its group unwritten")
    void turnsDownWritingThatPointsAtNothingAtAll(@TempDir Path root, @TempDir Path seeds)
            throws IOException {
        anApprovedCorpus(root, seeds);
        GenerationScriptedBeans.answerFor(theOnlyCluster(), A_TITLE, PROSE_POINTING_AT_NOTHING_AT_ALL);

        cli.run("run", root.toString());

        claim(
                "writing that points at no document at all is turned down, rather than passing a check"
                        + " that had nothing to look at: every number in it being in range is satisfied by"
                        + " there being no numbers, so this is the one case where the check passing would"
                        + " mean nothing was checked",
                () -> assertThat(reasonsKept(root)).singleElement().satisfies(kept -> assertThat(
                                kept.fault().kind())
                        .isEqualTo(ClusterFaultKind.CITATION_NOT_IN_RANGE)));
        claim(
                "and the reason says the writing pointed at nothing at all -- it reads \""
                        + THE_REASON_FOR_POINTING_AT_NOTHING_AT_ALL + "\" -- rather than naming a number,"
                        + " so it cannot be read as writing that pointed at the number "
                        + THE_NUMBER_BELOW_THE_FIRST_DOCUMENT + ". Those are two different things for the"
                        + " archive's owner to do something about, and a reason that reads the same for"
                        + " both leaves the record unable to say which happened",
                () -> assertThat(reasonsKept(root))
                        .singleElement()
                        .satisfies(kept -> assertThat(kept.fault().detail())
                                .contains(THE_REASON_FOR_POINTING_AT_NOTHING_AT_ALL)
                                .doesNotContain(THE_REASON_FOR_POINTING_BELOW_THE_FIRST_DOCUMENT)));
        claim(
                "and nothing was written over the group",
                () -> assertThat(writingKept(root)).isEmpty());
    }

    @Test
    @Story("Writing pointing at nothing at all is never published")
    @DisplayName("Writing pointing below the first document is told apart from writing pointing at nothing")
    void turnsDownWritingThatPointsBelowTheFirstDocument(@TempDir Path root, @TempDir Path seeds)
            throws IOException {
        anApprovedCorpus(root, seeds);
        GenerationScriptedBeans.answerFor(
                theOnlyCluster(), A_TITLE, PROSE_POINTING_BELOW_THE_FIRST_DOCUMENT);

        cli.run("run", root.toString());

        claim(
                "the group is left unwritten: the documents a call carries are numbered from one, so "
                        + THE_NUMBER_BELOW_THE_FIRST_DOCUMENT + " is a number no call ever handed the"
                        + " model and a reader following it would land on nothing",
                () -> assertThat(reasonsKept(root)).singleElement().satisfies(kept -> assertThat(
                                kept.fault().kind())
                        .isEqualTo(ClusterFaultKind.CITATION_NOT_IN_RANGE)));
        claim(
                "and the reason names that number -- it reads \""
                        + THE_REASON_FOR_POINTING_BELOW_THE_FIRST_DOCUMENT + "\" -- rather than reading"
                        + " as writing that pointed at nothing at all, which is what the same writing"
                        + " with the brackets taken out of it would have been turned down for",
                () -> assertThat(reasonsKept(root))
                        .singleElement()
                        .satisfies(kept -> assertThat(kept.fault().detail())
                                .contains(THE_REASON_FOR_POINTING_BELOW_THE_FIRST_DOCUMENT)
                                .doesNotContain(THE_REASON_FOR_POINTING_AT_NOTHING_AT_ALL)));
        claim(
                "and nothing was written over the group",
                () -> assertThat(writingKept(root)).isEmpty());
    }

    @Test
    @Story("Writing pointing at a document nobody sent is never published")
    @DisplayName("A number too long to be a document number costs its group and not the invocation")
    void turnsDownWritingThatPointsAtANumberTooLongToBeADocument(@TempDir Path root, @TempDir Path seeds)
            throws IOException {
        anApprovedCorpus(root, seeds);
        GenerationScriptedBeans.answerFor(theOnlyCluster(), A_TITLE, PROSE_POINTING_AT_A_NUMBER_TOO_LONG);

        cli.run("run", root.toString());

        claim(
                "the invocation reports success rather than falling over on a number it cannot read: "
                        + A_NUMBER_TOO_LONG_TO_BE_A_DOCUMENT + " is a date written out in bracketed digits,"
                        + " which is ordinary prose about audits, and nothing bounds what a model puts"
                        + " between brackets -- so reading it has to end the same way any other number past"
                        + " the last document does",
                () -> assertThat(cli.getExitCode()).isZero());
        claim(
                "the group is left unwritten, for pointing at a document the call never carried",
                () -> assertThat(reasonsKept(root)).singleElement().satisfies(kept -> assertThat(
                                kept.fault().kind())
                        .isEqualTo(ClusterFaultKind.CITATION_NOT_IN_RANGE)));
        claim(
                "and the reason carries the digits the model actually wrote, "
                        + A_NUMBER_TOO_LONG_TO_BE_A_DOCUMENT + ", so somebody accounting for the group can"
                        + " search the writing for that text and find it -- where a number this system"
                        + " stood in for it instead is one they would search for and never find",
                () -> assertThat(reasonsKept(root))
                        .singleElement()
                        .satisfies(kept -> assertThat(kept.fault().detail())
                                .contains(A_NUMBER_TOO_LONG_TO_BE_A_DOCUMENT)));
        claim(
                "and nothing was written over the group",
                () -> assertThat(writingKept(root)).isEmpty());
    }

    @Test
    @Story("One answer nobody believes costs one group and no more")
    @DisplayName("The groups after a turned-down answer are still written over")
    void carriesOnPastAClusterWhoseAnswerWasTurnedDown(@TempDir Path root, @TempDir Path seeds)
            throws IOException {
        anApprovedCorpus(root, seeds);
        aSecondClusterAheadOfTheFirst(theApprovedArrangement(root));
        thatClusterIsGivenADocumentItCanSend(theApprovedArrangement(root));
        GenerationScriptedBeans.answerFor(
                THE_CLUSTER_ANSWERED_BADLY, ScriptedAnswer.arrivingAs(AN_ANSWER_NOTHING_CAN_READ));

        cli.run("run", root.toString());

        claim(
                "both groups were asked about, all " + TWO_CALLS + " of them: the first answer was turned"
                        + " down and the group after it was still asked, where a run that stopped there"
                        + " would have made only " + ONE_CALL,
                () -> assertThat(GenerationScriptedBeans.callsMade()).isEqualTo(TWO_CALLS));
        claim(
                "the group whose answer was believed carries its writing, " + ONE_PIECE_OF_WRITING
                        + " piece of it, so one answer nobody believed cost its own group and nothing"
                        + " further along",
                () -> assertThat(writingKept(root)).hasSize(ONE_PIECE_OF_WRITING));
        claim(
                "and exactly " + ONE_REASON_KEPT + " reason is kept, for the one group whose answer was"
                        + " turned down",
                () -> assertThat(reasonsKept(root)).hasSize(ONE_REASON_KEPT));
        claim(
                "and the invocation reports success",
                () -> assertThat(cli.getExitCode()).isZero());
    }

    @Test
    @Issue("222")
    @Story("One answer nobody believes costs one group and no more")
    @DisplayName("A call that came back carrying no answer leaves the reasons kept before it standing")
    @Link(name = "ADR-123", url = Adr.AN_ANSWER_CARRYING_NOTHING_IS_A_SCHEMA_VIOLATION, type = "adr")
    void keepsTheReasonsFromEarlierInTheStepPastACallThatCameBackCarryingNoAnswer(
            @TempDir Path root, @TempDir Path seeds) throws IOException {
        anApprovedCorpus(root, seeds);
        aSecondClusterAheadOfTheFirst(theApprovedArrangement(root));
        thatClusterIsGivenADocumentItCanSend(theApprovedArrangement(root));
        GenerationScriptedBeans.answerFor(
                THE_CLUSTER_ANSWERED_BADLY, ScriptedAnswer.arrivingAs(AN_ANSWER_NOTHING_CAN_READ));
        GenerationScriptedBeans.answerFor(theOnlyCluster(), ScriptedAnswer.carryingNoAnswerAtAll());

        cli.run("run", root.toString());

        claim(
                "both reasons are kept, all " + TWO_REASONS_KEPT + " of them: a call coming back with no"
                        + " answer in it is turned down like any other unusable answer, where a failure"
                        + " of another kind would have rolled the piece of work back and taken the reason"
                        + " recorded before it with it -- leaving " + ONE_REASON_KEPT + " group looking"
                        + " like one the run never reached",
                () -> assertThat(reasonsKept(root)).hasSize(TWO_REASONS_KEPT));
        claim(
                "and the reason kept for the call that answered nothing says so in its own words, which"
                        + " is what tells it apart from the group before it whose answer nobody could"
                        + " read",
                () -> assertThat(reasonsKept(root))
                        .anySatisfy(kept -> assertThat(kept.fault().detail()).isEqualTo(NO_ANSWER_AT_ALL)));
        claim(
                "both are recorded as an answer that could not be read into the shape the call imposed,"
                        + " because nothing at all is the smallest case of that rather than a fifth way"
                        + " an answer is turned down",
                () -> assertThat(reasonsKept(root))
                        .allSatisfy(kept -> assertThat(kept.fault().kind())
                                .isEqualTo(ClusterFaultKind.SCHEMA_VIOLATION)));
        claim(
                "and the invocation reports success, because a group left unwritten is not a run that"
                        + " failed",
                () -> assertThat(cli.getExitCode()).isZero());
    }

    @Test
    @Issue("224")
    @Story("One answer nobody believes costs one group and no more")
    @DisplayName("An answer that came back with no writing in it leaves the reasons kept before it standing")
    @Link(name = "ADR-124", url = Adr.BOTH_FIELDS_OF_A_PARSED_ANSWER_ARE_REQUIRED, type = "adr")
    void keepsTheReasonsFromEarlierInTheStepPastAnAnswerWithNoWritingInIt(
            @TempDir Path root, @TempDir Path seeds) throws IOException {
        anApprovedCorpus(root, seeds);
        aSecondClusterAheadOfTheFirst(theApprovedArrangement(root));
        thatClusterIsGivenADocumentItCanSend(theApprovedArrangement(root));
        GenerationScriptedBeans.answerFor(
                THE_CLUSTER_ANSWERED_BADLY, ScriptedAnswer.arrivingAs(AN_ANSWER_NOTHING_CAN_READ));
        GenerationScriptedBeans.answerFor(
                theOnlyCluster(), ScriptedAnswer.arrivingAs(AN_ANSWER_WITH_NO_WRITING_IN_IT));

        cli.run("run", root.toString());

        claim(
                "both reasons are kept, all " + TWO_REASONS_KEPT + " of them: an answer that came back"
                        + " with no writing in it is turned down like any other unusable answer, where a"
                        + " failure of another kind would have rolled the piece of work back and taken"
                        + " the reason recorded before it with it -- leaving " + ONE_REASON_KEPT
                        + " group looking like one the run never reached",
                () -> assertThat(reasonsKept(root)).hasSize(TWO_REASONS_KEPT));
        claim(
                "and the reason kept for the answer with no writing says so in its own words, which is"
                        + " what tells it apart from the group before it whose answer nobody could read",
                () -> assertThat(reasonsKept(root))
                        .anySatisfy(kept ->
                                assertThat(kept.fault().detail()).isEqualTo(NO_WRITING_AT_ALL)));
        claim(
                "both are recorded as an answer that did not come back in the shape the call asked for,"
                        + " rather than one of them being recorded as writing that pointed at no"
                        + " document: nothing was written to point at anything with",
                () -> assertThat(reasonsKept(root))
                        .allSatisfy(kept -> assertThat(kept.fault().kind())
                                .isEqualTo(ClusterFaultKind.SCHEMA_VIOLATION)));
        claim(
                "nothing was written over either group",
                () -> assertThat(writingKept(root)).isEmpty());
        claim(
                "and the invocation reports success, because a group left unwritten is not a run that"
                        + " failed",
                () -> assertThat(cli.getExitCode()).isZero());
    }

    @Test
    @Issue("224")
    @Story("One answer nobody believes costs one group and no more")
    @DisplayName("The groups after an answer with no writing in it are still written over")
    @Link(name = "ADR-124", url = Adr.BOTH_FIELDS_OF_A_PARSED_ANSWER_ARE_REQUIRED, type = "adr")
    void carriesOnPastAnAnswerWithNoWritingInIt(@TempDir Path root, @TempDir Path seeds)
            throws IOException {
        anApprovedCorpus(root, seeds);
        aSecondClusterAheadOfTheFirst(theApprovedArrangement(root));
        thatClusterIsGivenADocumentItCanSend(theApprovedArrangement(root));
        GenerationScriptedBeans.answerFor(
                THE_CLUSTER_ANSWERED_BADLY, ScriptedAnswer.arrivingAs(AN_ANSWER_WITH_NO_WRITING_IN_IT));

        cli.run("run", root.toString());

        claim(
                "both groups were asked about, all " + TWO_CALLS + " of them: the answer with no writing"
                        + " in it was turned down and the group after it was still asked, where a run"
                        + " that fell over on it would have made only " + ONE_CALL,
                () -> assertThat(GenerationScriptedBeans.callsMade()).isEqualTo(TWO_CALLS));
        claim(
                "the group whose answer was believed carries its writing, " + ONE_PIECE_OF_WRITING
                        + " piece of it, so one answer arriving without its writing cost its own group"
                        + " and nothing further along",
                () -> assertThat(writingKept(root)).hasSize(ONE_PIECE_OF_WRITING));
        claim(
                "and exactly " + ONE_REASON_KEPT + " reason is kept, for the one group whose answer"
                        + " carried no writing",
                () -> assertThat(reasonsKept(root)).singleElement().satisfies(kept -> assertThat(
                                kept.fault().kind())
                        .isEqualTo(ClusterFaultKind.SCHEMA_VIOLATION)));
        claim(
                "and the invocation reports success",
                () -> assertThat(cli.getExitCode()).isZero());
    }

    @Test
    @Issue("216")
    @Story("One answer nobody believes costs one group and no more")
    @DisplayName("An answer that came back with no heading on it leaves the reasons kept before it standing")
    @Link(name = "ADR-125", url = Adr.AN_ABSENT_TITLE_AND_A_BLANK_ONE_ARE_TOLD_APART, type = "adr")
    void keepsTheReasonsFromEarlierInTheStepPastAnAnswerWithNoTitleOnIt(
            @TempDir Path root, @TempDir Path seeds) throws IOException {
        anApprovedCorpus(root, seeds);
        aSecondClusterAheadOfTheFirst(theApprovedArrangement(root));
        thatClusterIsGivenADocumentItCanSend(theApprovedArrangement(root));
        GenerationScriptedBeans.answerFor(
                THE_CLUSTER_ANSWERED_BADLY, ScriptedAnswer.arrivingAs(AN_ANSWER_NOTHING_CAN_READ));
        GenerationScriptedBeans.answerFor(
                theOnlyCluster(), ScriptedAnswer.arrivingAs(AN_ANSWER_WITH_NO_TITLE_ON_IT));

        cli.run("run", root.toString());

        claim(
                "both reasons are kept, all " + TWO_REASONS_KEPT + " of them: an answer that came back"
                        + " with no heading on it is turned down like any other unusable answer, where"
                        + " keeping it would have meant filing writing under a heading that does not"
                        + " exist -- a failure of a kind that takes the whole piece of work back with it,"
                        + " leaving " + ONE_REASON_KEPT + " group looking like one the run never reached",
                () -> assertThat(reasonsKept(root)).hasSize(TWO_REASONS_KEPT));
        claim(
                "and the reason kept for the answer with no heading says so in its own words, which is"
                        + " what tells it apart from the group before it whose answer nobody could read",
                () -> assertThat(reasonsKept(root))
                        .anySatisfy(kept -> assertThat(kept.fault().detail()).isEqualTo(NO_TITLE_AT_ALL)));
        claim(
                "both are recorded as an answer that did not come back in the shape the call asked for,"
                        + " because a heading was part of that shape and one of the two things asked for",
                () -> assertThat(reasonsKept(root))
                        .allSatisfy(kept -> assertThat(kept.fault().kind())
                                .isEqualTo(ClusterFaultKind.SCHEMA_VIOLATION)));
        claim(
                "nothing was written over either group",
                () -> assertThat(writingKept(root)).isEmpty());
        claim(
                "and the invocation reports success, because a group left unwritten is not a run that"
                        + " failed",
                () -> assertThat(cli.getExitCode()).isZero());
    }

    @Test
    @Issue("216")
    @Story("One answer nobody believes costs one group and no more")
    @DisplayName("The groups after an answer with no heading on it are still written over")
    @Link(name = "ADR-125", url = Adr.AN_ABSENT_TITLE_AND_A_BLANK_ONE_ARE_TOLD_APART, type = "adr")
    void carriesOnPastAnAnswerWithNoTitleOnIt(@TempDir Path root, @TempDir Path seeds)
            throws IOException {
        anApprovedCorpus(root, seeds);
        aSecondClusterAheadOfTheFirst(theApprovedArrangement(root));
        thatClusterIsGivenADocumentItCanSend(theApprovedArrangement(root));
        GenerationScriptedBeans.answerFor(
                THE_CLUSTER_ANSWERED_BADLY, ScriptedAnswer.arrivingAs(AN_ANSWER_WITH_NO_TITLE_ON_IT));

        cli.run("run", root.toString());

        claim(
                "both groups were asked about, all " + TWO_CALLS + " of them: the answer with no heading"
                        + " on it was turned down and the group after it was still asked, where a run"
                        + " that fell over on it would have made only " + ONE_CALL,
                () -> assertThat(GenerationScriptedBeans.callsMade()).isEqualTo(TWO_CALLS));
        claim(
                "the group whose answer was believed carries its writing, " + ONE_PIECE_OF_WRITING
                        + " piece of it, so one answer arriving without its heading cost its own group"
                        + " and nothing further along",
                () -> assertThat(writingKept(root)).hasSize(ONE_PIECE_OF_WRITING));
        claim(
                "and exactly " + ONE_REASON_KEPT + " reason is kept, for the one group whose answer"
                        + " carried no heading",
                () -> assertThat(reasonsKept(root)).singleElement().satisfies(kept -> assertThat(
                                kept.fault().kind())
                        .isEqualTo(ClusterFaultKind.SCHEMA_VIOLATION)));
        claim(
                "and the invocation reports success",
                () -> assertThat(cli.getExitCode()).isZero());
    }

    @Test
    @Issue("216")
    @Story("One answer nobody believes costs one group and no more")
    @DisplayName("A blank heading is kept apart from a heading that never came, and neither leaves any writing behind")
    @Link(name = "ADR-125", url = Adr.AN_ABSENT_TITLE_AND_A_BLANK_ONE_ARE_TOLD_APART, type = "adr")
    void keepsABlankTitleApartFromATitleThatNeverCame(@TempDir Path root, @TempDir Path seeds)
            throws IOException {
        anApprovedCorpus(root, seeds);
        aSecondClusterAheadOfTheFirst(theApprovedArrangement(root));
        thatClusterIsGivenADocumentItCanSend(theApprovedArrangement(root));
        GenerationScriptedBeans.answerFor(
                THE_CLUSTER_ANSWERED_BADLY, ScriptedAnswer.arrivingAs(AN_ANSWER_WHOSE_TITLE_IS_BLANK));
        GenerationScriptedBeans.answerFor(
                theOnlyCluster(), ScriptedAnswer.arrivingAs(AN_ANSWER_WITH_NO_TITLE_ON_IT));

        cli.run("run", root.toString());

        claim(
                "the two groups keep " + TWO_REASONS_KEPT + " reasons between them, and the reasons read"
                        + " differently: a heading of blank space and a heading that never came are two"
                        + " different things to act on, because a blank one was accepted and kept by"
                        + " earlier versions of this tool and may still be sitting in work already handed"
                        + " over, where a heading that never came could never be kept at all",
                () -> assertThat(reasonsKept(root))
                        .extracting(kept -> kept.fault().detail())
                        .containsExactlyInAnyOrder(A_BLANK_TITLE, NO_TITLE_AT_ALL));
        claim(
                "both are recorded as an answer that did not come back in the shape the call asked for,"
                        + " rather than one of them being kept as a heading at all: blank space is not a"
                        + " heading, and writing filed under one would show as an entry with nothing to"
                        + " click and be counted as a group that was written over",
                () -> assertThat(reasonsKept(root))
                        .allSatisfy(kept -> assertThat(kept.fault().kind())
                                .isEqualTo(ClusterFaultKind.SCHEMA_VIOLATION)));
        claim(
                "and nothing is kept as writing for either group -- which is the one thing both the"
                        + " listing and the closing line read, so the two of them cannot disagree about"
                        + " whether either group was written over",
                () -> assertThat(writingKept(root)).isEmpty());
        claim(
                "and the invocation reports success, because a group left unwritten is not a run that"
                        + " failed",
                () -> assertThat(cli.getExitCode()).isZero());
    }

    @Test
    @Story("A turned-down answer is never answered by asking again")
    @DisplayName("A group whose answer was turned down is not asked about a second time in that invocation")
    void asksNothingASecondTimeAboutATurnedDownAnswer(@TempDir Path root, @TempDir Path seeds)
            throws IOException {
        anApprovedCorpus(root, seeds);
        GenerationScriptedBeans.answerFor(
                theOnlyCluster(), ScriptedAnswer.arrivingAs(AN_ANSWER_NOTHING_CAN_READ));

        cli.run("run", root.toString());

        claim(
                "the one group cost exactly " + ONE_CALL + " call even though its answer was turned down:"
                        + " asking again because the first answer failed a check is one model judging"
                        + " another model's answer on the archive owner's behalf, and it turns a cost of"
                        + " one call per group into a cost nobody can put a bound on",
                () -> assertThat(GenerationScriptedBeans.callsMade()).isEqualTo(ONE_CALL));
    }

    @Test
    @Story("A group is written over, or left unwritten with a reason, and never both")
    @DisplayName("No group carries both a piece of writing and a reason it was left unwritten")
    void neverKeepsWritingAndAReasonForOneCluster(@TempDir Path root, @TempDir Path seeds) throws IOException {
        anApprovedCorpus(root, seeds);
        aSecondClusterAheadOfTheFirst(theApprovedArrangement(root));
        thatClusterIsGivenADocumentItCanSend(theApprovedArrangement(root));
        GenerationScriptedBeans.answerFor(
                THE_CLUSTER_ANSWERED_BADLY, ScriptedAnswer.arrivingAs(AN_ANSWER_NOTHING_CAN_READ));

        cli.run("run", root.toString());

        claim(
                "no group has both writing kept for it and a reason it was left unwritten: those two"
                        + " records answer the same question in opposite directions, and a group carrying"
                        + " both leaves the finished work and the record of it disagreeing about whether it"
                        + " was ever written",
                () -> assertThat(writingKept(root).stream()
                                .map(written -> written.winningSeed().value() + "/" + written.clusterOrdinal())
                                .filter(written -> reasonsKept(root).stream()
                                        .anyMatch(kept -> written.equals(
                                                kept.winningSeed().value() + "/" + kept.clusterOrdinal())))
                                .toList())
                        .isEmpty());
    }

    @Test
    @Story("Work that was not finished is not recorded as finished")
    @DisplayName("A run that left a group unwritten does not record the work as done")
    @Link(name = "ADR-116", url = Adr.A_RUNS_COMPLETION_IS_RECORDED_PER_STEP, type = "adr")
    void leavesTheWorkUnfinishedWhenAnAnswerWasTurnedDown(@TempDir Path root, @TempDir Path seeds)
            throws IOException {
        anApprovedCorpus(root, seeds);
        GenerationScriptedBeans.answerFor(
                theOnlyCluster(), ScriptedAnswer.arrivingAs(AN_ANSWER_NOTHING_CAN_READ));

        cli.run("run", root.toString());

        claim(
                "the work is not recorded as done: a group whose answer was turned down has nothing"
                        + " written over it, and the record of this step being finished is exactly what"
                        + " every later invocation reads to decide whether to walk past it -- so recording"
                        + " it here would make the hole permanent and unattended",
                () -> assertThat(theWorkIsRecordedAsFinished(root)).isFalse());
        claim(
                "and the invocation still reports success, because one answer nobody believed is something"
                        + " to look at and run again rather than a broken tool",
                () -> assertThat(cli.getExitCode()).isZero());
    }

    @Test
    @Story("Work that was not finished is not recorded as finished")
    @DisplayName("Running again after an answer was turned down asks again and keeps one reason, not two")
    void asksAgainOnASecondRunAndKeepsOneReason(@TempDir Path root, @TempDir Path seeds) throws IOException {
        anApprovedCorpus(root, seeds);
        GenerationScriptedBeans.answerFor(
                theOnlyCluster(), ScriptedAnswer.arrivingAs(AN_ANSWER_NOTHING_CAN_READ));

        cli.run("run", root.toString());
        cli.run("run", root.toString());

        claim(
                "the group was asked about again, which is " + A_CALL_FOR_EACH_OF_TWO_INVOCATIONS
                        + " calls over the two invocations: a group left unwritten because its answer was"
                        + " turned down is put to the model again the next time the archive's owner runs,"
                        + " and an invocation that walked past it instead would leave the hole there for"
                        + " good while reporting success every time",
                () -> assertThat(GenerationScriptedBeans.callsMade())
                        .isEqualTo(A_CALL_FOR_EACH_OF_TWO_INVOCATIONS));
        claim(
                "the second invocation reports success too: the same question put to the same model gets"
                        + " the same answer turned down for the same reason, which is the ordinary case"
                        + " this is built for and not a broken tool",
                () -> assertThat(cli.getExitCode()).isZero());
        claim(
                "one reason is kept and not two, though the group was asked about twice: it is one group"
                        + " under one run, so a second reason would have nowhere of its own to sit and the"
                        + " run would end on the collision rather than on the group",
                () -> assertThat(reasonsKept(root)).hasSize(ONE_REASON_KEPT));
        claim(
                "and both invocations worked under the same record, which is what makes the second one a"
                        + " continuation of the first rather than a fresh attempt beside it",
                () -> assertThat(generationRuns(root)).hasSize(ONE_RUN_FOR_BOTH_INVOCATIONS));
    }

    @Test
    @Story("A group asked about again and answered well keeps writing and no reason")
    @DisplayName("A group whose first answer was turned down keeps no reason once a later answer is believed")
    @Issue("185")
    void keepsNoReasonForAClusterARepairAnswered(@TempDir Path root, @TempDir Path seeds) throws IOException {
        anApprovedCorpus(root, seeds);
        GenerationScriptedBeans.answerFor(
                theOnlyCluster(), ScriptedAnswer.arrivingAs(AN_ANSWER_NOTHING_CAN_READ));

        cli.run("run", root.toString());

        GenerationScriptedBeans.answerFor(theOnlyCluster(), anOrdinaryAnswer());

        cli.run("run", root.toString());

        claim(
                "the group was asked about again -- " + A_CALL_FOR_EACH_OF_TWO_INVOCATIONS + " calls over"
                        + " the two invocations -- and the second answer was believed, so the group carries"
                        + " the " + ONE_PIECE_OF_WRITING + " piece of writing the second answer became",
                () -> {
                    assertThat(GenerationScriptedBeans.callsMade())
                            .isEqualTo(A_CALL_FOR_EACH_OF_TWO_INVOCATIONS);
                    assertThat(writingKept(root)).hasSize(ONE_PIECE_OF_WRITING);
                });
        claim(
                "and no reason is kept against it any more: the reason the first answer was turned down is"
                        + " gone, not kept beside the writing. A group holding both would have the record"
                        + " saying the same group under the same run was both written over and left"
                        + " unwritten, and somebody reading it would have no way to tell which is so",
                () -> assertThat(reasonsKept(root)).isEmpty());
        claim(
                "both invocations worked under the same record, which is what makes the second one repair"
                        + " the first rather than write a second group beside it",
                () -> assertThat(generationRuns(root)).hasSize(ONE_RUN_FOR_BOTH_INVOCATIONS));
        claim(
                "and the work is recorded as done now, which is what stops a third invocation asking about"
                        + " this group at all -- the point of asking again being to finish, not to keep"
                        + " asking",
                () -> assertThat(theWorkIsRecordedAsFinished(root)).isTrue());
    }

    @Test
    @Story("A group asked about again is asked the same question")
    @DisplayName("The second question put about a group is the first question over again, carrying nothing of the answer that was turned down")
    @Issue("185")
    void asksTheSameQuestionAgainCarryingNothingOfTheAnswerTurnedDown(
            @TempDir Path root, @TempDir Path seeds) throws IOException {
        anApprovedCorpus(root, seeds);
        GenerationScriptedBeans.answerFor(
                theOnlyCluster(), ScriptedAnswer.arrivingAs(AN_ANSWER_NOTHING_CAN_READ));

        cli.run("run", root.toString());

        RecordedClusterFault turnedDownFor =
                reasonsKept(root).stream().findFirst().orElseThrow();
        GenerationScriptedBeans.answerFor(theOnlyCluster(), anOrdinaryAnswer());

        cli.run("run", root.toString());

        List<String> questionsPut = GenerationScriptedBeans.promptsSent();
        claim(
                "the group was asked about twice, so there are " + A_CALL_FOR_EACH_OF_TWO_INVOCATIONS
                        + " questions to compare",
                () -> assertThat(questionsPut).hasSize(A_CALL_FOR_EACH_OF_TWO_INVOCATIONS));
        claim(
                "and the second question is the first one word for word. Asking again is the same question"
                        + " put a second time, not a correction: a question that mentioned how the first"
                        + " answer fell short would have the model marking the model's own work, and what"
                        + " came back could no longer be read as an answer about the documents alone",
                () -> assertThat(questionsPut.get(THE_QUESTION_ASKED_AGAIN))
                        .isEqualTo(questionsPut.get(THE_QUESTION_ASKED_FIRST)));
        claim(
                "neither question carries what the first answer was turned down for -- neither the check it"
                        + " failed nor the detail kept against it -- so there was nothing of the first"
                        + " attempt for the second to be steered by in the first place",
                () -> assertThat(questionsPut)
                        .allSatisfy(question -> assertThat(question)
                                .doesNotContain(turnedDownFor.fault().kind().name())
                                .doesNotContain(turnedDownFor.fault().detail())));
        claim(
                "and neither carries the text of the answer that was turned down, which is the thing a"
                        + " retry loop would have put back in front of the model",
                () -> assertThat(questionsPut)
                        .allSatisfy(question -> assertThat(question).doesNotContain(AN_ANSWER_NOTHING_CAN_READ)));
    }

    @Test
    @Story("The reason kept is the reason the answer standing now was turned down for")
    @DisplayName("A second answer turned down for a different reason replaces the first reason, not joins it")
    void keepsTheReasonTheSecondAnswerWasTurnedDownFor(@TempDir Path root, @TempDir Path seeds)
            throws IOException {
        anApprovedCorpus(root, seeds);
        GenerationScriptedBeans.answerFor(
                theOnlyCluster(), ScriptedAnswer.arrivingAs(AN_ANSWER_NOTHING_CAN_READ));

        cli.run("run", root.toString());

        GenerationScriptedBeans.answerFor(theOnlyCluster(), A_TITLE, PROSE_POINTING_AT_NOTHING);

        cli.run("run", root.toString());

        claim(
                "exactly " + ONE_REASON_KEPT + " reason is kept for the group, though it was asked about"
                        + " twice and turned down twice",
                () -> assertThat(reasonsKept(root)).hasSize(ONE_REASON_KEPT));
        claim(
                "and it is the reason the second answer was turned down for -- a number in the writing"
                        + " pointing at no document the call carried -- not the reason the first was, which"
                        + " was an answer nothing could read. Asking the same question again can get an"
                        + " answer that fails a different check, and what is kept has to be true of the"
                        + " answer standing now: a record that held on to the first reason would have the"
                        + " archive's owner accounting for the group by something that is no longer so",
                () -> assertThat(reasonsKept(root)).singleElement().satisfies(kept -> assertThat(
                                kept.fault().kind())
                        .isEqualTo(ClusterFaultKind.CITATION_NOT_IN_RANGE)));
        claim(
                "and the reason carries the number that failed the second time, "
                        + A_NUMBER_NO_DOCUMENT_WAS_SENT_UNDER + ", rather than where reading the first"
                        + " answer stopped",
                () -> assertThat(reasonsKept(root))
                        .singleElement()
                        .satisfies(kept -> assertThat(kept.fault().detail())
                                .contains(String.valueOf(A_NUMBER_NO_DOCUMENT_WAS_SENT_UNDER))));
        claim(
                "and nothing was written over the group",
                () -> assertThat(writingKept(root)).isEmpty());
    }

    @Test
    @Story("A group nothing could be sent for is not a group whose answer was turned down")
    @DisplayName("A group with room for none of its documents is left unwritten with no reason recorded")
    @Link(name = "ADR-121", url = Adr.A_WINDOW_WITH_NO_ROOM_IS_REFUSED, type = "adr")
    void keepsNoReasonForAClusterNothingWouldFitIn(@TempDir Path root, @TempDir Path seeds) throws IOException {
        anApprovedCorpus(root, seeds);
        setTheReadingWindowTo(A_WINDOW_WITH_ROOM_FOR_A_SINGLE_WORD);

        cli.run("run", root.toString());

        claim(
                "nothing was asked -- " + NOTHING_WAS_ASKED + " calls -- because no document fits a"
                        + " window with room for a single word, and writing resting on no document could"
                        + " point at nothing a reader can open",
                () -> assertThat(GenerationScriptedBeans.callsMade()).isEqualTo(NOTHING_WAS_ASKED));
        claim(
                "and no reason is kept for that group: the four reasons an answer can be turned down are"
                        + " four things a returned answer can do, and no answer was returned here because"
                        + " no question was asked. A fifth reason for a question never asked would put two"
                        + " different kinds of event in one closed set, and would buy nothing: the work is"
                        + " left unfinished either way, and the next invocation reaches the group either"
                        + " way",
                () -> assertThat(reasonsKept(root)).isEmpty());
        claim(
                "nothing was written over the group either, so the finished work keeps the hole under the"
                        + " name the arrangement already gave it",
                () -> assertThat(writingKept(root)).isEmpty());
        claim(
                "and the work is not recorded as done, which is what brings the next invocation back to"
                        + " this group after the archive's owner has widened the window",
                () -> assertThat(theWorkIsRecordedAsFinished(root)).isFalse());
    }

    /**
     * Drops what was scripted and what was counted before each test as well as after it, so that what
     * ran before this class cannot be read as this class's own calls.
     *
     * <p>Every test here that counts calls carries the same exposure {@link
     * GenerationBreakerInvocationTest} was caught by: the count is static on the fixture, eighteen
     * classes in this package share it, and only the three generation classes drop it. Clearing after
     * a test leaves this class trusting whichever class ran before it to have done the same.
     */
    @BeforeEach
    void forgetWhatAnEarlierClassScripted() {
        GenerationScriptedBeans.forgetScriptedAnswers();
    }

    @AfterEach
    void forgetWhatWasScripted() {
        GenerationScriptedBeans.forgetScriptedAnswers();
    }

    /**
     * The one cluster this fixture's corpus produces, named by the title every document it converts
     * carries — which is what the naming rule derives a cluster's name from, and what a scripted answer
     * is keyed on.
     */
    private static String theOnlyCluster() {
        return SeedScriptedExtractionBeans.STUBBED_TITLE;
    }

    /** An answer that passes every check, which each test scripts exactly one property away from. */
    private static ScriptedAnswer anOrdinaryAnswer() {
        return ScriptedAnswer.saying(
                GenerationScriptedBeans.GENERATED_TITLE, GenerationScriptedBeans.GENERATED_PROSE);
    }

    /**
     * A corpus of two documents, walked once, with the arrangement it produced approved — which is the
     * state every test here starts from, because none of them is about the gate in front of it.
     */
    private void anApprovedCorpus(Path root, Path seeds) throws IOException {
        Files.writeString(root.resolve("corpus.txt"), "a corpus document");
        Files.writeString(root.resolve("another-corpus-document.txt"), "a second corpus document");
        Files.writeString(seeds.resolve("seed.txt"), "a seed document");
        Profile profile = profileStore.load();
        profileStore.save(ProfileFixture.profile()
                .seedFolder(seeds.toString(), "set by this test")
                .degenerateOutputConfidenceFloor(profile.degenerateOutputConfidenceFloor())
                .boilerplateDocumentFrequencyFloor(BOILERPLATE_FLOOR, "set by this test, so stage 4's gate is open")
                .embeddingModel(EMBEDDING_MODEL_NAME, "set by this test, so gate 3 is open")
                .generationContextWindow(THE_READING_WINDOW, "set by this test, so every test here reads in the same window")
                .build());
        cli.run("run", root.toString());
        approve(ArrangementGate.shortNameOf(theLatestArrangement(root)));
    }

    /** Writes the reading window into the profile, leaving every other key as it was. */
    private void setTheReadingWindowTo(String window) {
        profileStore.save(ProfileFixture.profileFrom(profileStore.load())
                .generationContextWindow(window, "set by this test")
                .build());
    }

    /** Writes the approval, leaving every other key as the fixture left it. */
    private void approve(String approval) {
        profileStore.save(ProfileFixture.profileFrom(profileStore.load())
                .arrangementApproved(approval, "read by this test")
                .build());
    }

    /**
     * Puts a second cluster into the approved arrangement, ahead of the one that is there, under a name of
     * its own.
     *
     * <p>Written straight into the table, for the reason the colliding arrangement in this package's
     * other invocation test is: a fixture whose documents all convert alike cannot be steered into
     * producing two clusters. It copies the arranged cluster's row one place earlier in the order, so what
     * happens to it happens <em>before</em> the cluster that was already there — which is the whole point,
     * since a run that stopped at a turned-down answer and a run that carried on past it are told apart
     * only by what happened to the clusters after it.
     *
     * <p><b>It copies document_count unchanged</b>, so the injected row claims more documents than it
     * holds, which no arrangement run would write. Harmless here, because a call is sized from
     * membership and never from that column.
     */
    private void aSecondClusterAheadOfTheFirst(RunId arrangement) {
        jdbcTemplate.update(
                "INSERT INTO cluster (run_id, winning_seed_occurrence_id, cluster_ordinal, label,"
                        + " document_count, partition_order, cluster_order)"
                        + " SELECT run_id, winning_seed_occurrence_id, cluster_ordinal + 1, ?,"
                        + " document_count, partition_order, cluster_order - 1"
                        + " FROM cluster WHERE run_id = ?",
                THE_CLUSTER_ANSWERED_BADLY,
                arrangement.value());
    }

    /**
     * Moves one of the corpus documents into that second cluster, so it has something to send and is
     * therefore asked about at all.
     *
     * <p>Written straight into the table for the reason the cluster itself is. Which document moves does
     * not matter, so the query names one by taking the last.
     */
    private void thatClusterIsGivenADocumentItCanSend(RunId arrangement) {
        jdbcTemplate.update(
                "UPDATE document_cluster SET cluster_ordinal = cluster_ordinal + 1 WHERE rowid ="
                        + " (SELECT rowid FROM document_cluster WHERE run_id ="
                        + " (SELECT upstream_run_id FROM run_upstream WHERE run_id = ?)"
                        + " ORDER BY occurrence_id DESC LIMIT 1)",
                arrangement.value());
    }

    /** Everything stage 6b wrote over the clusters of {@code root}, under whichever run it wrote them. */
    private List<RecordedSynthesisDoc> writingKept(Path root) {
        return generationRuns(root).stream()
                .map(RunId::new)
                .flatMap(run -> synthesisDocs.forRun(run).stream())
                .toList();
    }

    /** Every reason stage 6b kept for a cluster of {@code root} it left unwritten. */
    private List<RecordedClusterFault> reasonsKept(Path root) {
        return generationRuns(root).stream()
                .map(RunId::new)
                .flatMap(run -> clusterFaults.forRun(run).stream())
                .toList();
    }

    /** Whether this step's own work is recorded as complete under the run it wrote. */
    private boolean theWorkIsRecordedAsFinished(Path root) {
        return generationRuns(root).stream()
                .anyMatch(run -> jdbcTemplate.queryForObject(
                                "SELECT COUNT(*) FROM finished_step WHERE run_id = ? AND step = ?",
                                Integer.class,
                                run,
                                GenerationRun.STAGE)
                        > 0);
    }

    /** The arrangement the most recent invocation over {@code root} recorded — the one its page names. */
    private RunId theLatestArrangement(Path root) {
        return new RunId(jdbcTemplate.queryForObject(
                "SELECT r.id FROM run r JOIN walk w ON w.id = r.walk_id"
                        + " WHERE r.stage = ? AND w.root = ? ORDER BY r.rowid DESC LIMIT 1",
                String.class,
                ArrangementRun.STAGE,
                Walk.canonicalRoot(root).toString()));
    }

    /** The arrangement of {@code root} the profile's approval names, whichever invocation recorded it. */
    private RunId theApprovedArrangement(Path root) {
        return new RunId(jdbcTemplate.queryForObject(
                "SELECT r.id FROM run r JOIN walk w ON w.id = r.walk_id"
                        + " WHERE r.stage = ? AND w.root = ? AND r.id LIKE ? ORDER BY r.rowid LIMIT 1",
                String.class,
                ArrangementRun.STAGE,
                Walk.canonicalRoot(root).toString(),
                profileStore.load().arrangementApproved().value() + "%"));
    }

    /**
     * Every record of this step having run over {@code root}, oldest first.
     *
     * <p>Scoped to the walk of this corpus rather than counted across the table. One working directory
     * serves the whole class and the database outlives each method, so an unscoped count would be a
     * claim about every corpus any method in this class ever walked.
     */
    private List<String> generationRuns(Path root) {
        return jdbcTemplate.queryForList(
                "SELECT r.id FROM run r JOIN walk w ON w.id = r.walk_id"
                        + " WHERE r.stage = ? AND w.root = ? ORDER BY r.rowid",
                String.class,
                GenerationRun.STAGE,
                Walk.canonicalRoot(root).toString());
    }
}
