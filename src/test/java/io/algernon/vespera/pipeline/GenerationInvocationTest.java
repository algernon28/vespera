package io.algernon.vespera.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.corpus.Walk;
import io.algernon.vespera.ledger.Ledger;
import io.algernon.vespera.ledger.OccurrenceId;
import io.algernon.vespera.ledger.RunId;
import io.algernon.vespera.profile.Profile;
import io.algernon.vespera.profile.ProfileFixture;
import io.algernon.vespera.profile.ProfileStore;
import io.algernon.vespera.profile.ProfileValue;
import io.algernon.vespera.synthesis.Clusters;
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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Stage 6b end to end (ADR-107, ADR-108, ADR-110, ADR-114, #179, #180): the decision the step makes
 * before anything could be written, and then the writing itself.
 *
 * <p>Most of what is under test here is the gate: whether the operator has approved the arrangement
 * that would be written over, and what is recorded when they have. The last two tests are what
 * happens past it — one piece of writing per cluster, kept against the cluster it was about.
 *
 * <p>An assumption would have been the house idiom and is wrong here: it would abort on the very
 * condition under test, so a gate that opened and did the wrong thing would look exactly like a gate
 * that could not open.
 *
 * <p><b>The two shut states are one outcome and the ambiguous one is not.</b> Nothing approved, and
 * an approval naming no arrangement of this corpus, both end the invocation successfully having
 * written nothing — in each case nobody has approved anything, and a typo must not be able to behave
 * like an approval. An approval naming two arrangements stops instead, which is what this system
 * already does anywhere it would otherwise have to guess which of two runs was meant (ADR-099).
 *
 * <p><b>The tests that invoke more than once rest on ADR-115.</b> An approval names a 6a run id, a
 * run id hashes the walk it read; before that record a finished walk was never reused, so the
 * approval named an arrangement of a walk the next invocation was not looking at, and the gate could
 * not be opened by any value an operator could type. With walk churn stopped, the approval names the
 * very walk the next invocation reads, and the gate opens on it. The third-invocation test is the
 * other half of the same record: a re-derived generation run id meets the row it already wrote, and
 * {@code Ledger.startRun} continues under it rather than inserting a second time.
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
@Import(SeedScriptedExtractionBeans.class)
@Epic("Synthesis")
@Feature("Writing over the groups")
@Issue("179")
@Link(name = "ADR-107", url = Adr.THE_ARRANGEMENT_GATE_APPROVES_A_NAMED_RUN, type = "adr")
@Link(name = "ADR-108", url = Adr.SIX_B_SENDS_ONE_EXEMPLAR_FIRST_CALL_PER_CLUSTER, type = "adr")
@Link(name = "ADR-114", url = Adr.THE_GENERATION_MODEL_IS_CONFIGURATION_WITH_A_DEFAULT, type = "adr")
@Link(name = "ADR-099", url = Adr.AN_UPSTREAM_IS_LOOKED_UP_AND_TWO_CANDIDATES_STOP_THE_RUN, type = "adr")
@Link(name = "ADR-080", url = Adr.THE_BOILERPLATE_FLOOR_IS_A_GATE, type = "adr")
class GenerationInvocationTest {

    /** A floor of 1.0 opens stage 4's gate the way every other invocation test opens it. */
    private static final String BOILERPLATE_FLOOR = "1.0";

    /** The embedding model this fixture names, so gate 3 and everything behind it open. Named for
     * which model it is because two different ones matter in this class, and the one under test is
     * the other. */
    private static final String EMBEDDING_MODEL_NAME = "qwen3-embedding:0.6b";

    /** An approval of the right shape that is nobody's arrangement: twelve hexadecimal characters. */
    private static final String NAMES_NOTHING = "0123456789ab";

    /** What one approval is worth: one record of the work, and not a second over the same approval. */
    private static final int ONE_RECORD = 1;

    /** What one cluster of documents is worth: one piece of writing, however many documents are in it. */
    private static final int ONE_PIECE_OF_WRITING = 1;

    /** How many documents the two-document corpus puts in that one cluster. */
    private static final int TWO_DOCUMENTS = 2;

    /** How many of them fit once the window is set small enough that they no longer both do. */
    private static final int ONE_DOCUMENT = 1;

    /**
     * A window with room for one of this fixture's documents and not two.
     *
     * <p>Small on purpose, and that is the point of the test: a fixture of a few short documents never
     * runs out of room under the shipped window, so the arithmetic that decides what fits would ship
     * having never once been asked a real question.
     */
    private static final String A_WINDOW_WITH_ROOM_FOR_ONE = "1300";

    /**
     * What stage 6a calls that one cluster: the title every document this fixture converts carries, which
     * is what the naming rule derives a cluster's name from.
     */
    private static final String THE_CLUSTERS_NAME = SeedScriptedExtractionBeans.STUBBED_TITLE;

    /** A title scripted for that cluster alone, so a record holding any other answer is visible. */
    private static final String ITS_OWN_TITLE = "What The Two Stubbed Documents Have In Common";

    /** And the writing scripted with it. */
    private static final String ITS_OWN_PROSE = "Both of them [1] say the same thing twice.";

    /** A window wider than the one the code ships with, which is the change an operator makes here. */
    private static final String A_WIDER_WINDOW = "16384";

    /** What two different answers to how much may be read are worth: two records of the work. */
    private static final int TWO_RECORDS = 2;

    /** What one unfinished piece of work leaves behind: the one cluster that was written, and no more. */
    private static final int ONE_CLUSTER_WRITTEN = 1;

    /** What an invocation that reached the step under a standing approval records: one run of it. */
    private static final int ONE_GENERATION_RUN = 1;

    /** The name given to the cluster nothing in this run can be sent for, so a claim can name it plainly. */
    private static final String A_CLUSTER_WITH_NOTHING_TO_SEND = "A group whose documents cannot be opened";

    /**
     * The smallest window anything can be read in at all: room for one word, which no document in this
     * fixture opens with fewer than. Accepted as a window, and too small for any of these documents.
     */
    private static final String A_WINDOW_WITH_ROOM_FOR_A_SINGLE_WORD = "1282";

    /** What a cluster nothing fits into is worth asking about: nothing, because there is nothing to ask. */
    private static final int NOTHING_WAS_ASKED = 0;

    /** What a finished piece of work leaves behind here: writing over both of the two clusters. */
    private static final int TWO_CLUSTERS_WRITTEN = 2;

    /** The fewest documents any piece of writing may rest on, because writing over none is never made. */
    private static final int AT_LEAST_ONE_DOCUMENT = 1;

    /** The name given to the cluster no document has reached yet, so a claim can name it plainly. */
    private static final String A_CLUSTER_NO_DOCUMENT_HAS_REACHED_YET = "A group no document has reached yet";

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
    private Clusters clusters;

    @Test
    @Story("Nothing is written over the archive until a person approves what they read")
    @DisplayName("With the groups approved, the next invocation opens on exactly the groups that were read")
    void opensOnTheArrangementThatWasApproved(@TempDir Path root, @TempDir Path seeds) throws IOException {
        aCorpus(root, seeds);
        cli.run("run", root.toString());
        approve(ArrangementGate.shortNameOf(theLatestArrangement(root)));

        cli.run("run", root.toString());

        claim("the second invocation reports success", () -> assertThat(cli.getExitCode()).isZero());
        claim(
                "the step recorded its work once and no more: an approval is spent on the groups it"
                        + " named, and a second record against the same approval would be a second claim"
                        + " on work already accounted for",
                () -> assertThat(generationRuns(root)).hasSize(ONE_RECORD));
        claim(
                "and what it reads is the very arrangement the approval named, rather than whichever was"
                        + " arranged most recently -- an approval is of a particular shape of the archive,"
                        + " and writing over a later one would spend an approval nobody gave",
                () -> assertThat(upstreamOf(generationRuns(root).getFirst()))
                        .containsExactly(theApprovedArrangement(root).value()));
    }

    @Test
    @Story("Nothing is written over the archive until a person approves what they read")
    @DisplayName("With nothing approved, the invocation succeeds and nothing is opened")
    void opensNothingWhenNothingIsApproved(@TempDir Path root, @TempDir Path seeds) throws IOException {
        aCorpus(root, seeds);
        approve(null);

        cli.run("run", root.toString());

        claim(
                "the invocation succeeded rather than failing: a person who has not looked at the"
                        + " arrangement yet has done nothing wrong, and an invocation ending in red would"
                        + " be telling them they had",
                () -> assertThat(cli.getExitCode()).isZero());
        claim(
                "and nothing at all was recorded -- not an empty record, none: what is written down is"
                        + " what was actually done, and nothing was",
                () -> assertThat(generationRuns(root)).isEmpty());
    }

    @Test
    @Story("Nothing is written over the archive until a person approves what they read")
    @DisplayName("An approval matching nothing is treated exactly as no approval at all")
    void opensNothingWhenTheApprovalMatchesNothing(@TempDir Path root, @TempDir Path seeds) throws IOException {
        aCorpus(root, seeds);
        approve(NAMES_NOTHING);

        cli.run("run", root.toString());

        claim(
                "a mistyped name leaves the archive exactly where a blank one does: the one outcome this"
                        + " must never have is quietly behaving like an approval, because a person who"
                        + " mistyped believes they approved something",
                () -> assertThat(generationRuns(root)).isEmpty());
        claim(
                "and the invocation still succeeded, because a name matching nothing is something to"
                        + " correct and run again, not a broken tool",
                () -> assertThat(cli.getExitCode()).isZero());
    }

    @Test
    @Story("Nothing is written over the archive until a person approves what they read")
    @DisplayName("Opening on the approved groups records no judgement against any document")
    void recordsNoJudgementAgainstAnyDocument(@TempDir Path root, @TempDir Path seeds) throws IOException {
        aCorpus(root, seeds);
        cli.run("run", root.toString());
        approve(ArrangementGate.shortNameOf(theLatestArrangement(root)));

        cli.run("run", root.toString());

        claim(
                "the step ran at all over the approved groups, which is what the claim below is about --"
                        + " asserted first and separately, so that a step which never ran fails saying so"
                        + " rather than failing while reaching for something that is not there",
                () -> assertThat(generationRuns(root)).isNotEmpty());
        claim(
                "no judgement was recorded: every judgement this system records exists to take a document"
                        + " out of what gets published, and writing connecting text over what survived"
                        + " takes nothing out of anything",
                () -> assertThat(verdictsAgainstTheGeneratedWork(root)).isZero());
    }

    @Test
    @Story("Nothing is written over the archive until a person approves what they read")
    @DisplayName("Invoking a third time with the approval still standing adds nothing and breaks nothing")
    @Link(name = "ADR-115", url = Adr.A_REPEATED_OBSERVATION_IS_DISCARDED_AND_A_RUN_IS_CONTINUED, type = "adr")
    void aThirdInvocationUnderAStandingApprovalAddsNothing(@TempDir Path root, @TempDir Path seeds)
            throws IOException {
        aCorpus(root, seeds);
        cli.run("run", root.toString());
        approve(ArrangementGate.shortNameOf(theLatestArrangement(root)));
        cli.run("run", root.toString());

        cli.run("run", root.toString());

        claim(
                "the third invocation reports success: nothing about the archive, the values a person set"
                        + " or the tool had changed since the second, so there was nothing for it to do --"
                        + " and an invocation that ends in red for having nothing to do is telling a person"
                        + " something is wrong when nothing is",
                () -> assertThat(cli.getExitCode()).isZero());
        claim(
                "and the work is still recorded exactly " + ONE_RECORD + " time: the third invocation"
                        + " works out the same name for this work as the second did, finds that name already"
                        + " written down, and carries on under it -- writing it a second time is the one"
                        + " thing the record of work will not accept",
                () -> assertThat(generationRuns(root)).hasSize(ONE_RECORD));
    }

    @Test
    @Issue("180")
    @Story("Every approved group of documents is written over, once")
    @DisplayName("Each approved group comes out with one piece of writing over the whole group")
    void writesOnePieceOverEachApprovedCluster(@TempDir Path root, @TempDir Path seeds) throws IOException {
        aCorpusOfTwoDocuments(root, seeds);
        cli.run("run", root.toString());
        approve(ArrangementGate.shortNameOf(theLatestArrangement(root)));

        cli.run("run", root.toString());

        claim(
                "the invocation reports success, which the claims below are about",
                () -> assertThat(cli.getExitCode()).isZero());
        claim(
                "every group the person approved has writing over it, and one piece of writing for the"
                        + " whole group rather than one per document: the two documents here are one group,"
                        + " and a piece of writing about each of them separately would be the pile this"
                        + " system exists to replace",
                () -> assertThat(generatedDocs(root)).hasSize(ONE_PIECE_OF_WRITING));
        claim(
                "and what is kept is what came back -- the heading, the text with its markers untouched,"
                        + " and the number of documents it was written from, both " + TWO_DOCUMENTS + " of"
                        + " them, which is what lets the finished page say what it was written over",
                () -> assertThat(generatedDocs(root)).singleElement().satisfies(doc -> {
                    assertThat(doc.doc().title()).isEqualTo(GenerationScriptedBeans.GENERATED_TITLE);
                    assertThat(doc.doc().prose()).isEqualTo(GenerationScriptedBeans.GENERATED_PROSE);
                    assertThat(doc.doc().documentsSent()).isEqualTo(TWO_DOCUMENTS);
                }));
        claim(
                "and which documents those " + TWO_DOCUMENTS + " were, each under the number the model"
                        + " was given it as, rather than a count of them: the page a reader opens numbers"
                        + " its list from this, and which number meant which document is knowable only"
                        + " while the call is being built",
                () -> assertThat(generatedDocs(root))
                        .singleElement()
                        .satisfies(doc -> assertThat(doc.doc().sent())
                                .containsExactlyInAnyOrderElementsOf(theDocumentsOfEveryCluster(root))));
    }

    @Test
    @Issue("180")
    @Story("Every approved group of documents is written over, once")
    @DisplayName("What was written over a particular group is what is kept against that group")
    void keepsWhatWasWrittenAgainstTheClusterItWasAbout(@TempDir Path root, @TempDir Path seeds) throws IOException {
        GenerationScriptedBeans.answerFor(THE_CLUSTERS_NAME, ITS_OWN_TITLE, ITS_OWN_PROSE);
        aCorpusOfTwoDocuments(root, seeds);
        cli.run("run", root.toString());
        approve(ArrangementGate.shortNameOf(theLatestArrangement(root)));

        cli.run("run", root.toString());

        claim(
                "the writing kept against this group is the writing that was produced for this group, not"
                        + " whatever was produced last: every group gets its own call, and a record that"
                        + " could hold another group's answer would be a page about the wrong documents",
                () -> assertThat(generatedDocs(root)).singleElement().satisfies(doc -> {
                    assertThat(doc.doc().title()).isEqualTo(ITS_OWN_TITLE);
                    assertThat(doc.doc().prose()).isEqualTo(ITS_OWN_PROSE);
                }));
    }

    @Test
    @Issue("182")
    @Story("A group too big to read in one go sends what fits, rather than being skipped")
    @DisplayName("With the reading window set small, a group is written from part of itself and says so")
    void writesFromPartOfAClusterWhenTheWindowIsSetSmall(@TempDir Path root, @TempDir Path seeds)
            throws IOException {
        aCorpusOfTwoDocuments(root, seeds);
        cli.run("run", root.toString());
        approve(ArrangementGate.shortNameOf(theLatestArrangement(root)));
        setTheReadingWindowTo(A_WINDOW_WITH_ROOM_FOR_ONE);

        cli.run("run", root.toString());

        claim(
                "the writing says it was made from " + ONE_DOCUMENT + " of the group's documents rather"
                        + " than both: the window this archive's owner set has room for one of them, and"
                        + " what the page will tell a reader it was written from is this number",
                () -> assertThat(generatedDocs(root))
                        .singleElement()
                        .satisfies(doc -> assertThat(doc.doc().documentsSent()).isEqualTo(ONE_DOCUMENT)));
        claim(
                "and the group still holds both documents: what was read is a matter of how much would"
                        + " fit, and what belongs to the group is not -- a reader is shown every document"
                        + " under it, which is what keeps writing from part of a group honest",
                () -> assertThat(clusters.forRun(theApprovedArrangement(root)))
                        .singleElement()
                        .satisfies(recorded ->
                                assertThat(recorded.cluster().documentCount()).isEqualTo(TWO_DOCUMENTS)));
    }

    @Test
    @Issue("180")
    @Story("Work that was not finished is not recorded as finished")
    @DisplayName("A group nothing could be sent for is not recorded as done")
    void leavesTheStepOpenWhenAClusterCouldNotBeWrittenOver(@TempDir Path root, @TempDir Path seeds)
            throws IOException {
        aCorpusOfTwoDocuments(root, seeds);
        cli.run("run", root.toString());
        approve(ArrangementGate.shortNameOf(theLatestArrangement(root)));
        theArchiveNoLongerHandsOverItsDocuments(root);
        GenerationScriptedBeans.forgetScriptedAnswers();

        cli.run("run", root.toString());

        claim(
                "the step really ran: the approval still named the arrangement, so exactly "
                        + ONE_GENERATION_RUN + " record of writing over it was made -- the claims below"
                        + " would hold just as well if the gate had stayed shut and nothing ran at all, so"
                        + " this is what makes them about the step",
                () -> assertThat(generationRuns(root)).hasSize(ONE_GENERATION_RUN));
        claim(
                "and it asked the model nothing, since the group held nothing it could send -- "
                        + NOTHING_WAS_ASKED + " calls",
                () -> assertThat(GenerationScriptedBeans.callsMade()).isEqualTo(NOTHING_WAS_ASKED));
        claim(
                "nothing was written over the group, because there was nothing to write from",
                () -> assertThat(generatedDocs(root)).isEmpty());
        claim(
                "and the work is not recorded as done: a group that was never written over is not a"
                        + " finished job, and it is exactly that record which every later invocation reads"
                        + " to decide whether to walk past this step -- so marking it done here would leave"
                        + " a hole in the finished work that nothing anywhere reports and nothing retries",
                () -> assertThat(theWorkIsRecordedAsFinished(root)).isFalse());
        claim(
                "the invocation still reports success: an archive that moved underneath a run is something"
                        + " to look at and run again, not a broken tool",
                () -> assertThat(cli.getExitCode()).isZero());
    }

    @Test
    @Issue("182")
    @Story("Writing that rests on no document at all is never produced")
    @DisplayName("With room for less than one document, the group is left unwritten and nothing is asked")
    @Link(name = "ADR-121", url = Adr.A_WINDOW_WITH_NO_ROOM_IS_REFUSED, type = "adr")
    void asksNothingAndWritesNothingWhenNoDocumentFitsTheWindow(@TempDir Path root, @TempDir Path seeds)
            throws IOException {
        aCorpusOfTwoDocuments(root, seeds);
        cli.run("run", root.toString());
        approve(ArrangementGate.shortNameOf(theLatestArrangement(root)));
        setTheReadingWindowTo(A_WINDOW_WITH_ROOM_FOR_A_SINGLE_WORD);
        GenerationScriptedBeans.forgetScriptedAnswers();

        cli.run("run", root.toString());

        claim(
                "the invocation reports success: a window too small for the documents in front of it is"
                        + " something to widen and run again, not a broken tool",
                () -> assertThat(cli.getExitCode()).isZero());
        claim(
                "nothing was asked of the model at all -- " + NOTHING_WAS_ASKED + " calls: not one"
                        + " document fits the room this window leaves, so the only piece of writing that"
                        + " could come back would be about none of them, and that call is the most"
                        + " expensive thing this system does",
                () -> assertThat(GenerationScriptedBeans.callsMade()).isEqualTo(NOTHING_WAS_ASKED));
        claim(
                "and nothing was kept over the group: a piece of writing made from no documents points at"
                        + " nothing a reader could open to check it, and every other sentence in what this"
                        + " produces can be followed back to a file",
                () -> assertThat(generatedDocs(root)).isEmpty());
        claim(
                "and the work is not recorded as done, so a later invocation under a window that fits will"
                        + " write over this group rather than walking past it as finished",
                () -> assertThat(theWorkIsRecordedAsFinished(root)).isFalse());
    }

    @Test
    @Issue("208")
    @Story("Reading more of each group is a different piece of work, not the same one again")
    @DisplayName("With the reading window changed, the archive is written over again under a record of its own")
    void writesAgainUnderItsOwnRecordWhenTheReadingWindowChanges(@TempDir Path root, @TempDir Path seeds)
            throws IOException {
        aCorpusOfTwoDocuments(root, seeds);
        cli.run("run", root.toString());
        approve(ArrangementGate.shortNameOf(theLatestArrangement(root)));
        cli.run("run", root.toString());

        setTheReadingWindowTo(A_WIDER_WINDOW);
        cli.run("run", root.toString());

        claim(
                "the invocation after the change reports success, which the claims below are about",
                () -> assertThat(cli.getExitCode()).isZero());
        claim(
                "the work is recorded " + TWO_RECORDS + " times rather than once: how much of a group one"
                        + " call may read decides how much of it the writing rests on, so the same archive"
                        + " read two ways is two pieces of work and neither may be mistaken for the other",
                () -> assertThat(generationRuns(root)).hasSize(TWO_RECORDS));
        claim(
                "and the second record is its own name rather than the first one again -- if the number the"
                        + " archive's owner changed did not reach the name this work is recorded under, the"
                        + " invocation would find the earlier record, decide the work was done and write"
                        + " nothing, leaving them with writing made under a window they had stopped using",
                () -> assertThat(generationRuns(root)).doesNotHaveDuplicates());
        claim(
                "and the second record has writing of its own beneath it, one piece over the group, rather"
                        + " than pointing back at what the first read: a wider window is a different reading"
                        + " of the same documents, and what a reader opens has to be the reading that was"
                        + " asked for",
                () -> assertThat(synthesisDocs.forRun(new RunId(generationRuns(root).getLast())))
                        .hasSize(ONE_PIECE_OF_WRITING));
        claim(
                "and the first record keeps the writing it produced, so the earlier reading is still there"
                        + " to be compared against rather than having been written over",
                () -> assertThat(synthesisDocs.forRun(new RunId(generationRuns(root).getFirst())))
                        .hasSize(ONE_PIECE_OF_WRITING));
    }

    @Test
    @Issue("180")
    @Story("Work already paid for is never paid for twice")
    @DisplayName("A group already written over is left alone while the next invocation finishes the rest")
    @Link(name = "ADR-115", url = Adr.A_REPEATED_OBSERVATION_IS_DISCARDED_AND_A_RUN_IS_CONTINUED, type = "adr")
    @Link(name = "ADR-116", url = Adr.A_RUNS_COMPLETION_IS_RECORDED_PER_STEP, type = "adr")
    void keepsWhatAnEarlierInvocationWroteAndCarriesOnFromThere(@TempDir Path root, @TempDir Path seeds)
            throws IOException {
        aCorpusOfTwoDocuments(root, seeds);
        cli.run("run", root.toString());
        approve(ArrangementGate.shortNameOf(theLatestArrangement(root)));
        aClusterNothingCanBeSentFor(theApprovedArrangement(root), root);
        GenerationScriptedBeans.answerFor(THE_CLUSTERS_NAME, ITS_OWN_TITLE, ITS_OWN_PROSE);
        cli.run("run", root.toString());

        GenerationScriptedBeans.forgetScriptedAnswers();
        cli.run("run", root.toString());

        claim(
                "the second invocation reports success rather than falling over the writing the first one"
                        + " left behind: an invocation that stopped partway is the ordinary case, and"
                        + " meeting its own earlier work must not be a failure",
                () -> assertThat(cli.getExitCode()).isZero());
        claim(
                "the group written over the first time is written over exactly "
                        + ONE_CLUSTER_WRITTEN + " time in total, with no second copy beside it",
                () -> assertThat(generatedDocs(root)).hasSize(ONE_CLUSTER_WRITTEN));
        claim(
                "and what stands is the writing the first invocation produced, word for word -- the model"
                        + " was told to answer differently the second time, so writing that had been thrown"
                        + " away and asked for again would read differently here. Asking again is the most"
                        + " expensive thing this system does and it buys nothing: neither the group nor the"
                        + " documents under it have changed",
                () -> assertThat(generatedDocs(root)).singleElement().satisfies(doc -> {
                    assertThat(doc.doc().title()).isEqualTo(ITS_OWN_TITLE);
                    assertThat(doc.doc().prose()).isEqualTo(ITS_OWN_PROSE);
                }));
        claim(
                "and the work is still not recorded as done, because the other group still has nothing"
                        + " written over it: leaving what is already written alone is how the next"
                        + " invocation gets to the rest, not a claim that there is no rest",
                () -> assertThat(theWorkIsRecordedAsFinished(root)).isFalse());
    }

    @Test
    @Issue("185")
    @Story("Work left unfinished is finished by the next invocation, and only then recorded as done")
    @DisplayName("The next invocation writes over the group left behind and records the work as done")
    @Link(name = "ADR-115", url = Adr.A_REPEATED_OBSERVATION_IS_DISCARDED_AND_A_RUN_IS_CONTINUED, type = "adr")
    @Link(name = "ADR-116", url = Adr.A_RUNS_COMPLETION_IS_RECORDED_PER_STEP, type = "adr")
    @Link(name = "ADR-121", url = Adr.A_WINDOW_WITH_NO_ROOM_IS_REFUSED, type = "adr")
    void finishesTheClusterLeftBehindAndOnlyThenRecordsTheWorkAsDone(@TempDir Path root, @TempDir Path seeds)
            throws IOException {
        aCorpusOfTwoDocuments(root, seeds);
        cli.run("run", root.toString());
        approve(ArrangementGate.shortNameOf(theLatestArrangement(root)));
        byte[] whatItHeld =
                aClusterNothingCanBeSentFor(theApprovedArrangement(root), root, A_CLUSTER_NO_DOCUMENT_HAS_REACHED_YET);
        GenerationScriptedBeans.answerFor(THE_CLUSTERS_NAME, ITS_OWN_TITLE, ITS_OWN_PROSE);
        cli.run("run", root.toString());
        theSecondClustersDocumentCanBeSentAgain(theApprovedArrangement(root), root, whatItHeld);

        GenerationScriptedBeans.forgetScriptedAnswers();
        cli.run("run", root.toString());

        claim(
                "the second invocation reports success, which the claims below are about",
                () -> assertThat(cli.getExitCode()).isZero());
        claim(
                "both groups now have writing over them, all " + TWO_CLUSTERS_WRITTEN + " of them: the one"
                        + " written the first time round, and the one that had nothing to send then and has"
                        + " something to send now",
                () -> assertThat(generatedDocs(root)).hasSize(TWO_CLUSTERS_WRITTEN));
        claim(
                "the group written the first time round still carries that first writing, word for word:"
                        + " the model was told to answer differently this time, so writing that had been"
                        + " asked for a second time would read differently here -- and asking again buys"
                        + " nothing, because neither that group nor the documents under it have changed",
                () -> assertThat(generatedDocs(root)).anySatisfy(doc -> {
                    assertThat(doc.doc().title()).isEqualTo(ITS_OWN_TITLE);
                    assertThat(doc.doc().prose()).isEqualTo(ITS_OWN_PROSE);
                }));
        claim(
                "and the group left behind carries writing made this time round rather than the first:"
                        + " picking up an unfinished job means doing the part that was left, not counting"
                        + " it done",
                () -> assertThat(generatedDocs(root))
                        .anySatisfy(doc -> assertThat(doc.doc().title())
                                .isEqualTo(GenerationScriptedBeans.GENERATED_TITLE)));
        claim(
                "the work is now recorded as done, which is the point of picking it up at all: nothing is"
                        + " left unwritten, so every later invocation may walk past this step -- and a job"
                        + " that finished everything and never said so would be started again for ever,"
                        + " each time reading the whole archive to be told there was nothing to do",
                () -> assertThat(theWorkIsRecordedAsFinished(root)).isTrue());
        claim(
                "and neither piece of writing rests on fewer than " + AT_LEAST_ONE_DOCUMENT + " document:"
                        + " writing made from none would be text a reader cannot follow back to any file,"
                        + " so a record saying it was made from none is a sign of something broken rather"
                        + " than of a reading window set small",
                () -> assertThat(generatedDocs(root))
                        .allSatisfy(doc -> assertThat(doc.doc().documentsSent())
                                .isGreaterThanOrEqualTo(AT_LEAST_ONE_DOCUMENT)));
    }

    /**
     * Splits the approved arrangement's one cluster into two of one document each, and leaves the
     * second holding a document nothing of can be sent, so the invocation writes over one cluster and
     * leaves the other with nothing (ADR-121).
     *
     * <p>The two clusters are written straight into the tables, because every document this fixture
     * converts carries the same text and embeds alike, so nothing put in the corpus arranges itself into
     * two clusters. <b>They are rows an arrangement could have recorded</b>: each cluster holds exactly
     * the one document its count says it does, in the membership the scoring run records, so the
     * arrangement stays total and its page can be drawn from it (ADR-112, ADR-154 §2).
     *
     * <p>What leaves the second cluster with nothing to send is an ordinary cause rather than a row no
     * run would write: its one document is rewritten in place between the invocations, with its length
     * and timestamps unchanged, so the walk still sees the same archive and the approval still stands,
     * and stage 6b finds nothing cached under the text it now reads ({@link UnseenEditFixture}).
     *
     * @return the bytes that document held before, so a test can put them back
     */
    private byte[] aClusterNothingCanBeSentFor(RunId arrangement, Path root) throws IOException {
        return aClusterNothingCanBeSentFor(arrangement, root, A_CLUSTER_WITH_NOTHING_TO_SEND);
    }

    /** The same, under a name of the caller's choosing, so two tests can tell their clusters apart. */
    private byte[] aClusterNothingCanBeSentFor(RunId arrangement, Path root, String label) throws IOException {
        jdbcTemplate.update(
                "INSERT INTO cluster (run_id, winning_seed_occurrence_id, cluster_ordinal, label,"
                        + " document_count, partition_order, cluster_order)"
                        + " SELECT run_id, winning_seed_occurrence_id, cluster_ordinal + 1, ?,"
                        + " 1, partition_order, cluster_order + 1"
                        + " FROM cluster WHERE run_id = ?",
                label,
                arrangement.value());
        jdbcTemplate.update(
                "UPDATE cluster SET document_count = 1 WHERE run_id = ? AND label <> ?", arrangement.value(), label);
        long moved = theLastDocumentOf(arrangement);
        jdbcTemplate.update(
                "UPDATE document_cluster SET cluster_ordinal = cluster_ordinal + 1 WHERE occurrence_id = ?"
                        + " AND run_id = (SELECT upstream_run_id FROM run_upstream WHERE run_id = ?)",
                moved,
                arrangement.value());
        return UnseenEditFixture.editedWithoutTheWalkNoticing(root.resolve(pathOf(moved)));
    }

    /**
     * Puts back the bytes the second cluster's document held when it was arranged, so a cluster nothing
     * could be sent for now has something to send.
     *
     * <p>This is the obstruction being lifted. What left that cluster unwritten was that nothing its
     * document then held had ever been read into the cache; with its own text back, the very same
     * invocation, asking the very same question again, finishes the job.
     */
    private void theSecondClustersDocumentCanBeSentAgain(RunId arrangement, Path root, byte[] whatItHeld)
            throws IOException {
        UnseenEditFixture.restored(root.resolve(pathOf(theLastDocumentOf(arrangement))), whatItHeld);
    }

    /**
     * The document of the arrangement's membership with the highest occurrence id -- the one moved into
     * the second cluster. Which document moves does not matter, so the query names one by taking the last.
     */
    private long theLastDocumentOf(RunId arrangement) {
        return jdbcTemplate.queryForObject(
                "SELECT occurrence_id FROM document_cluster WHERE run_id ="
                        + " (SELECT upstream_run_id FROM run_upstream WHERE run_id = ?)"
                        + " ORDER BY occurrence_id DESC LIMIT 1",
                Long.class,
                arrangement.value());
    }

    private String pathOf(long occurrence) {
        return jdbcTemplate.queryForObject(
                "SELECT path FROM file_occurrence WHERE id = ?", String.class, occurrence);
    }

    /**
     * Rewrites both corpus documents in place, so nothing in the cluster can be sent when the call is
     * built.
     *
     * <p>An archive is a live filesystem and this is an ordinary version of that: a document rewritten
     * between being walked and being written about, by something that put its timestamp back. The walk
     * sees the same archive, so the approval still names the arrangement and the step really runs; it is
     * the text 6b now reads that nothing was ever cached under ({@link UnseenEditFixture}). Deleting the
     * documents instead would be a different archive, whose gate is shut before the step is reached.
     */
    private void theArchiveNoLongerHandsOverItsDocuments(Path root) throws IOException {
        UnseenEditFixture.editedWithoutTheWalkNoticing(root.resolve("corpus.txt"));
        UnseenEditFixture.editedWithoutTheWalkNoticing(root.resolve("another-corpus-document.txt"));
    }

    /** Whether this step's own work is recorded as complete under the run it wrote. */
    private boolean theWorkIsRecordedAsFinished(Path root) {
        return generationRuns(root).stream()
                .anyMatch(run -> jdbcTemplate.queryForObject(
                                "SELECT COUNT(*) FROM finished_step WHERE run_id = ? AND step = ?",
                                Integer.class,
                                run,
                                "generation")
                        > 0);
    }

    @AfterEach
    void forgetWhatWasScripted() {
        GenerationScriptedBeans.forgetScriptedAnswers();
    }

    /** Writes the reading window into the profile, leaving every other key as it was. */
    private void setTheReadingWindowTo(String window) {
        profileStore.save(ProfileFixture.profileFrom(profileStore.load())
                .generationContextWindow(window, "set by this test")
                .build());
    }

    /** The same corpus with a second document in it, so a cluster holds more than one. */
    private void aCorpusOfTwoDocuments(Path root, Path seeds) throws IOException {
        aCorpus(root, seeds);
        Files.writeString(root.resolve("another-corpus-document.txt"), "a second corpus document");
    }

    /**
     * Every document the clustering behind the approved arrangement put into a cluster.
     *
     * <p>The fixtures that read this arrange one cluster holding the whole corpus, so this is also
     * every document the one call could have carried — which is what lets a claim say the recorded
     * exemplars are those documents rather than a count of them.
     */
    private List<OccurrenceId> theDocumentsOfEveryCluster(Path root) {
        return jdbcTemplate
                .queryForList(
                        "SELECT occurrence_id FROM document_cluster WHERE run_id ="
                                + " (SELECT upstream_run_id FROM run_upstream WHERE run_id = ?)",
                        Long.class,
                        theApprovedArrangement(root).value())
                .stream()
                .map(OccurrenceId::new)
                .toList();
    }

    /** Everything stage 6b wrote over the clusters of {@code root}, under whichever run it wrote them. */
    private List<RecordedSynthesisDoc> generatedDocs(Path root) {
        return generationRuns(root).stream()
                .map(RunId::new)
                .flatMap(run -> synthesisDocs.forRun(run).stream())
                .toList();
    }

    /** One corpus document and one exemplar, with every gate before this one open. */
    private void aCorpus(Path root, Path seeds) throws IOException {
        Files.writeString(root.resolve("corpus.txt"), "a corpus document");
        Files.writeString(seeds.resolve("seed.txt"), "a seed document");
        Profile profile = profileStore.load();
        profileStore.save(ProfileFixture.profile()
                .seedFolder(seeds.toString(), "set by this test")
                .degenerateOutputConfidenceFloor(profile.degenerateOutputConfidenceFloor())
                .boilerplateDocumentFrequencyFloor(BOILERPLATE_FLOOR, "set by this test, so stage 4's gate is open")
                .embeddingModel(EMBEDDING_MODEL_NAME, "set by this test, so gate 3 is open")
                .build());
    }

    /** Writes the approval, leaving every other key as the fixture left it. */
    private void approve(String approval) {
        Profile loaded = profileStore.load();
        profileStore.save(ProfileFixture.profileFrom(loaded)
                .arrangementApproved(approval, approval == null ? null : "read by this test")
                .build());
    }

    /** The arrangement the most recent invocation over {@code root} recorded — the one its page names. */
    private RunId theLatestArrangement(Path root) {
        return new RunId(jdbcTemplate.queryForObject(
                "SELECT r.id FROM run r JOIN walk w ON w.id = r.walk_id"
                        + " WHERE r.stage = ? AND w.root = ? ORDER BY r.rowid DESC LIMIT 1",
                String.class,
                "arrangement",
                Walk.canonicalRoot(root).toString()));
    }

    /** The arrangement of {@code root} the profile's approval names, whichever invocation recorded it. */
    private RunId theApprovedArrangement(Path root) {
        return new RunId(jdbcTemplate.queryForObject(
                "SELECT r.id FROM run r JOIN walk w ON w.id = r.walk_id"
                        + " WHERE r.stage = ? AND w.root = ? AND r.id LIKE ? ORDER BY r.rowid LIMIT 1",
                String.class,
                "arrangement",
                Walk.canonicalRoot(root).toString(),
                profileStore.load().arrangementApproved().value() + "%"));
    }

    /**
     * Every record of this step having run over {@code root}, oldest first.
     *
     * <p>Scoped to the walk of this corpus rather than counted across the table. One working directory
     * serves the whole class and the database outlives each method, so an unscoped count would be a
     * claim about every corpus any method in this class ever walked — and "the step recorded its work
     * once" would quietly become a statement about test execution order.
     */
    private List<String> generationRuns(Path root) {
        return jdbcTemplate.queryForList(
                "SELECT r.id FROM run r JOIN walk w ON w.id = r.walk_id"
                        + " WHERE r.stage = ? AND w.root = ? ORDER BY r.rowid",
                String.class,
                "generation",
                Walk.canonicalRoot(root).toString());
    }

    private List<String> upstreamOf(String runId) {
        return jdbcTemplate.queryForList(
                "SELECT upstream_run_id FROM run_upstream WHERE run_id = ?", String.class, runId);
    }

    /**
     * Judgements recorded against anything this step wrote, counted without reaching for a particular
     * record — so a step that never ran answers zero rather than raising.
     */
    private int verdictsAgainstTheGeneratedWork(Path root) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM verdict WHERE run_id IN"
                        + " (SELECT r.id FROM run r JOIN walk w ON w.id = r.walk_id"
                        + " WHERE r.stage = ? AND w.root = ?)",
                Integer.class,
                "generation",
                Walk.canonicalRoot(root).toString());
    }
}
