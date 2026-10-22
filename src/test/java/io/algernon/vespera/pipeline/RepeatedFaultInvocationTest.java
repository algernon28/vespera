package io.algernon.vespera.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.WholeRun;
import io.algernon.vespera.confluence.Publisher;
import io.algernon.vespera.corpus.Walk;
import io.algernon.vespera.ledger.Ledger;
import io.algernon.vespera.ledger.RunId;
import io.algernon.vespera.pipeline.GenerationScriptedBeans.ScriptedAnswer;
import io.algernon.vespera.profile.Profile;
import io.algernon.vespera.profile.ProfileFixture;
import io.algernon.vespera.profile.ProfileStore;
import io.algernon.vespera.synthesis.ClusterFaultKind;
import io.algernon.vespera.synthesis.ListedFault;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;
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
import picocli.CommandLine;

/**
 * A cluster whose answer is turned down the last two times it is asked stands without writing, and the generation
 * step finishes with it (ADR-319): what makes a fault a repeated one, when the step records completion, that nothing
 * more is asked under the run once it has, and what the page, the account and the publish make of it.
 *
 * <p><b>One sequence is built once for the class</b>, since it is three invocations long and five tests read it
 * (REVIEW.md, "shares its setup"): the fixture's one cluster answered with writing that ends with a stray closing
 * brace, the shape ADR-162 turns down, on the invocation that first reaches the writing and on the next one, and a
 * third invocation after that. What each test claims is read off what {@link #aClusterTurnedDownTwiceAndRunAgain}
 * kept after each invocation, because the tree is drawn again in place by every invocation and what the first one
 * drew is gone by the time a test runs. The three tests of the boundary build their own sequences.
 *
 * <p>The report says <em>group</em> where the code says cluster (ADR-122), as {@link GenerationFaultInvocationTest}
 * explains.
 */
@CascadeSliceTest
@Import(SeedScriptedExtractionBeans.class)
@Epic("Synthesis")
@Feature("Writing over the groups")
@Link(name = "ADR-319", url = Adr.A_CLUSTER_TURNED_DOWN_THE_LAST_TWO_TIMES_STANDS_WITHOUT_WRITING, type = "adr")
class RepeatedFaultInvocationTest {

    /** A floor of 1.0 opens stage 4's gate the way every other invocation test opens it. */
    private static final String BOILERPLATE_FLOOR = "1.0";

    /** The embedding model this fixture names, so gate 3 and everything behind it open. */
    private static final String EMBEDDING_MODEL_NAME = "qwen3-embedding:0.6b";

    /** The window every call in this class reads in, stated so a test claiming an overrun knows what it overran. */
    private static final String THE_READING_WINDOW = "4096";

    /**
     * How much of the question an answer reports having read when the question did not fit: more than the window of
     * {@value #THE_READING_WINDOW} tokens holds, so the answer is turned down for the question's length.
     */
    private static final int A_COUNT_PAST_THE_WINDOW = 5000;

    /**
     * Writing that ends with a closing brace belonging to no opening one, after its last sentence: the shape of the
     * measured answers ADR-319 rests on, and the one ADR-162 turns down. Its one citation is in range, so nothing but
     * the brace is wrong with it.
     */
    private static final String WRITING_ENDING_WITH_A_STRAY_BRACE = GenerationScriptedBeans.GENERATED_PROSE + " }";

    /** The detail ADR-162 keeps for that answer. */
    private static final String THE_STRAY_BRACE_DETAIL =
            "the answer's writing ends with a closing brace that belongs to no opening one";

    /** Writing citing a document number the call never sent: the call sends two, numbered 1 and 2. */
    private static final String WRITING_CITING_A_DOCUMENT_NEVER_SENT = "The audits [7] agree on every finding.";

    /** The title scripted beside writing whose citations are under test. */
    private static final String A_TITLE = "What The Two Stubbed Documents Have In Common";

    /** The heading of the membership list, which follows the reason on a page that says nothing more (ADR-104). */
    private static final String THE_MEMBERSHIP_HEADING = "The documents in this group";

    /** The plain line the index cell of a cluster with nothing written over it keeps (ADR-174 §3). */
    private static final String THE_INDEX_CELLS_PLAIN_LINE = "<em>nothing was written over this group</em>";

    /** What a cluster's Confluence page body is called in its folder of the rendering (ADR-312). */
    private static final String A_CONFLUENCE_PAGE_BODY = "page.xml";

    /** What an invocation account's file name starts with, in the working directory's logs folder (ADR-270). */
    private static final String ACCOUNT_PREFIX = "invocation-account-";

    /** The working directory's folder the accounts are written in (ADR-270). */
    private static final String LOGS = "logs";

    /** What the account's line counting the clusters standing without writing opens with (ADR-319 §5.2). */
    private static final String THE_STANDING_COUNT = "counts clusters standing-without-writing=";

    /** The one cluster of the fixture, standing without writing once its second answer is turned down. */
    private static final int ONE_CLUSTER_STANDING = 1;

    /** What the account counts of the fault that cluster stands for, by its kind (ADR-198, ADR-224). */
    private static final String ONE_FAULT_OF_THE_SHAPE_ASKED_FOR = "counts cluster-fault kind=SCHEMA_VIOLATION count=1";

    /** One reason kept for the one cluster, though it was turned down twice: the second replaced the first. */
    private static final int ONE_REASON_KEPT = 1;

    /** Every invocation over an unchanged archive works under one generation run, so its work is continued. */
    private static final int ONE_GENERATION_RUN = 1;

    /** One question put on each of the two invocations that reach the cluster unwritten. */
    private static final int A_QUESTION_ON_EACH_OF_TWO_INVOCATIONS = 2;

    /** The name given to the cluster put beside the fixture's own, whose one document cannot be sent. */
    private static final String A_CLUSTER_NOTHING_CAN_BE_SENT_FOR = "A group nothing can be sent for";

    @TempDir
    static Path workingDirectory;

    /** The corpus root of the sequence built once for the class. */
    @TempDir
    static Path theSequencesRoot;

    /** The seed folder of that sequence. */
    @TempDir
    static Path theSequencesSeeds;

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

    /**
     * What the sequence built once for the class left after each of its invocations. Static, as the working directory
     * is, so the five tests that read it share one build: the first of them to run builds it.
     */
    private static AfterAnInvocation afterTheFirst;

    private static AfterAnInvocation afterTheSecond;

    private static AfterAnInvocation afterTheThird;

    /** The renderings the publish asked for, when it was made after the second invocation. */
    private static final List<String> publishAskedFor = new ArrayList<>();

    /** How the publish made after the second invocation ended. */
    private static int publishExit;

    /** The generation run the sequence worked under. */
    private static RunId theSequencesRun;

    /**
     * Builds the sequence five tests read, once for the class: the fixture's one cluster answered with writing ending
     * in a stray brace on the first invocation that reaches the writing and on the next, a publish after the second,
     * and a third invocation with nothing scripted. Not a {@code @BeforeAll}: the Spring context, and the beans it
     * needs, are there for a test method and not for a static method before them.
     */
    private void aClusterTurnedDownTwiceAndRunAgain() throws IOException {
        if (afterTheThird != null) {
            return;
        }
        GenerationScriptedBeans.forgetScriptedAnswers();
        anApprovedCorpus(theSequencesRoot, theSequencesSeeds);
        GenerationScriptedBeans.answerFor(theOnlyCluster(), anAnswerEndingWithAStrayBrace());

        afterTheFirst = anInvocationOver(theSequencesRoot);
        afterTheSecond = anInvocationOver(theSequencesRoot);
        theSequencesRun = new RunId(generationRuns(theSequencesRoot).getLast());

        Recording publisher = new Recording(publishAskedFor);
        publishExit = new VesperaCommand.Publish(new Ledger(jdbcTemplate), publisher, workingDirectory, "").call();

        afterTheThird = anInvocationOver(theSequencesRoot);
        GenerationScriptedBeans.forgetScriptedAnswers();
    }

    @Test
    @Story("A group whose answer keeps being turned down stands without writing, and the work is recorded as done")
    @DisplayName("A group turned down again on the next run is the last thing left, and the work is recorded as done")
    @Link(name = "ADR-116", url = Adr.A_RUNS_COMPLETION_IS_RECORDED_PER_STEP, type = "adr")
    void recordsTheWorkAsDoneWhenTheOnlyClusterLeftIsTurnedDownAgain() throws IOException {
        aClusterTurnedDownTwiceAndRunAgain();
        claim(
                "the first time the group was asked, its answer ended with a closing brace that belongs to nothing and"
                        + " was turned down, the invocation succeeded, and the work was not recorded as done: an answer"
                        + " turned down once is asked again",
                () -> {
                    assertThat(afterTheFirst.exitCode()).isZero();
                    assertThat(afterTheFirst.finished()).isFalse();
                });
        claim(
                "the next invocation asked it again, one question on each of the two, "
                        + A_QUESTION_ON_EACH_OF_TWO_INVOCATIONS + " in all, under the same "
                        + ONE_GENERATION_RUN + " record of writing over the archive",
                () -> {
                    assertThat(afterTheSecond.questionsSoFar()).isEqualTo(A_QUESTION_ON_EACH_OF_TWO_INVOCATIONS);
                    assertThat(generationRuns(theSequencesRoot)).hasSize(ONE_GENERATION_RUN);
                });
        claim(
                "and its answer was turned down again for the same brace, so " + ONE_REASON_KEPT + " reason is kept,"
                        + " the second one",
                () -> assertThat(afterTheSecond.reasons()).singleElement().satisfies(kept -> {
                    assertThat(kept.fault().kind()).isEqualTo(ClusterFaultKind.SCHEMA_VIOLATION);
                    assertThat(kept.fault().detail()).isEqualTo(THE_STRAY_BRACE_DETAIL);
                }));
        claim(
                "and the work is now recorded as done: the group's last two answers were both turned down for what"
                        + " they wrote, it is the only thing left unwritten, and nothing else keeps the work open, so the"
                        + " finished pages can be sent",
                () -> {
                    assertThat(afterTheSecond.exitCode()).isZero();
                    assertThat(afterTheSecond.finished()).isTrue();
                });
    }

    @Test
    @Story("A group whose answer keeps being turned down stands without writing, and the work is recorded as done")
    @DisplayName("Once a group stands without writing, running again asks nothing more about it")
    void asksNothingMoreOnceTheClusterStands() throws IOException {
        aClusterTurnedDownTwiceAndRunAgain();
        claim(
                "the invocation after the work was recorded as done put no question: still "
                        + A_QUESTION_ON_EACH_OF_TWO_INVOCATIONS + " in all, the two the first two invocations put",
                () -> assertThat(afterTheThird.questionsSoFar()).isEqualTo(A_QUESTION_ON_EACH_OF_TWO_INVOCATIONS));
        claim(
                "it succeeded, the work still recorded as done, and the group's reason still kept",
                () -> {
                    assertThat(afterTheThird.exitCode()).isZero();
                    assertThat(afterTheThird.finished()).isTrue();
                    assertThat(afterTheThird.reasons()).hasSize(ONE_REASON_KEPT);
                });
        claim(
                "and the pages it drew again still say the group stands without writing, beneath why its answer was"
                        + " turned down",
                () -> {
                    assertThat(afterTheThird.reasonOnThePage())
                            .isEqualTo(UnwrittenPage.forKind(ClusterFaultKind.SCHEMA_VIOLATION));
                    assertThat(afterTheThird.lineUnderTheReason()).isEqualTo(UnwrittenPage.STANDS_WITHOUT_WRITING);
                });
    }

    @Test
    @Story("A group whose answer keeps being turned down stands without writing, and its page says so")
    @DisplayName("The page of a group standing without writing says so beneath why its answer was turned down")
    @Link(name = "ADR-174", url = Adr.A_PAGE_NOTHING_WAS_WRITTEN_OVER_SAYS_WHY, type = "adr")
    void theClustersPageSaysItStandsBeneathWhyItsAnswerWasTurnedDown() throws IOException {
        aClusterTurnedDownTwiceAndRunAgain();
        String reason = UnwrittenPage.forKind(ClusterFaultKind.SCHEMA_VIOLATION);
        String standing = UnwrittenPage.STANDS_WITHOUT_WRITING;
        claim(
                "after the first answer was turned down, the page said why and nothing more: the membership list"
                        + " followed the reason, since the group was still to be asked again",
                () -> {
                    assertThat(afterTheFirst.reasonOnThePage()).isEqualTo(reason);
                    assertThat(afterTheFirst.lineUnderTheReason()).isEqualTo(THE_MEMBERSHIP_HEADING);
                });
        claim(
                "after the second, the page says why, and on the line beneath it that the group stands without"
                        + " writing and that running again asks nothing more about it",
                () -> {
                    assertThat(afterTheSecond.reasonOnThePage()).isEqualTo(reason);
                    assertThat(afterTheSecond.lineUnderTheReason()).isEqualTo(standing);
                });
        claim(
                "the group's page in the Confluence rendering says the same two things, each in italics on a line of"
                        + " its own, the reason first",
                () -> assertThat(afterTheSecond.confluenceBody()).contains(
                        "<p><em>" + UnwrittenPage.escaped(reason) + "</em></p>\n<p><em>" + UnwrittenPage.escaped(standing)
                                + "</em></p>"));
        claim(
                "and the index keeps its plain line for the group, with no link, as it does for every group nothing"
                        + " was written over",
                () -> assertThat(afterTheSecond.indexRow())
                        .contains(THE_INDEX_CELLS_PLAIN_LINE)
                        .doesNotContain("<a "));
    }

    @Test
    @Story("A group whose answer keeps being turned down stands without writing, and the account counts it")
    @DisplayName("The account of the run that records the work as done counts the groups standing without writing")
    @Link(name = "ADR-198", url = Adr.EVERY_INVOCATION_WRITES_AN_ACCOUNT_THAT_NAMES_NO_DOCUMENT, type = "adr")
    void theAccountCountsTheClustersStandingWithoutWriting() throws IOException {
        aClusterTurnedDownTwiceAndRunAgain();
        claim(
                "the account of the first invocation counts no group standing without writing: the group was still"
                        + " to be asked again",
                () -> assertThat(afterTheFirst.account()).noneMatch(line -> line.contains(THE_STANDING_COUNT)));
        claim(
                "the account of the second counts " + ONE_CLUSTER_STANDING + " group standing without writing, in one"
                        + " line of its own",
                () -> assertThat(afterTheSecond.account())
                        .filteredOn(line -> line.contains(THE_STANDING_COUNT))
                        .singleElement()
                        .satisfies(line -> assertThat(line).endsWith(" " + THE_STANDING_COUNT + ONE_CLUSTER_STANDING)));
        claim(
                "and it still says why, by the kind of the one fault standing: an answer that did not come back in"
                        + " the shape it was asked for",
                () -> assertThat(afterTheSecond.account())
                        .anySatisfy(line -> assertThat(line).endsWith(" " + ONE_FAULT_OF_THE_SHAPE_ASKED_FOR)));
    }

    @Test
    @Story("A group whose answer keeps being turned down stands without writing, and the pages can be sent")
    @DisplayName("The publish sends the pages of the run finished with a group standing without writing")
    @Link(name = "ADR-308", url = Adr.VESPERA_PUBLISHES_THE_CONFLUENCE_RENDERING_TO_THE_OPERATORS_SITE, type = "adr")
    void thePublishFindsTheRunFinishedWithAClusterStanding() throws IOException {
        aClusterTurnedDownTwiceAndRunAgain();
        claim(
                "the publish made after the second invocation ended as the publisher said, and asked for the"
                        + " rendering of the run the group stands under, in its tree's folder",
                () -> {
                    assertThat(publishExit).isEqualTo(CommandLine.ExitCode.OK);
                    assertThat(publishAskedFor).containsExactly(theSequencesRun.value() + " at "
                            + UnwrittenPage.treeOf(workingDirectory, theSequencesRun.value())
                                    .resolve(Publisher.RENDERING_FOLDER));
                });
    }

    @Test
    @Story("A group whose answer keeps being turned down stands without writing, and the work is recorded as done")
    @DisplayName("A group turned down for a different reason the second time stands all the same")
    void standsWhenTurnedDownForADifferentCheckTheSecondTime(@TempDir Path root, @TempDir Path seeds)
            throws IOException {
        anApprovedCorpus(root, seeds);
        GenerationScriptedBeans.answerFor(theOnlyCluster(), anAnswerEndingWithAStrayBrace());
        cli.run("run", root.toString());
        GenerationScriptedBeans.answerFor(
                theOnlyCluster(), ScriptedAnswer.saying(A_TITLE, WRITING_CITING_A_DOCUMENT_NEVER_SENT));

        cli.run("run", root.toString());

        claim(
                "the second answer was turned down for citing a document the call never sent, not for the brace the"
                        + " first was",
                () -> assertThat(reasonsKept(root)).singleElement().satisfies(kept -> assertThat(kept.fault().kind())
                        .isEqualTo(ClusterFaultKind.CITATION_NOT_IN_RANGE)));
        claim(
                "and the work is recorded as done all the same: two answers in a row turned down for what they wrote"
                        + " is what keeps being turned down means, whichever check turned each one down",
                () -> assertThat(theWorkIsRecordedAsFinished(root)).isTrue());
    }

    @Test
    @Story("Only an answer turned down for what it wrote can leave a group standing")
    @DisplayName("A group whose question is too long for the window twice running does not stand, and the work stays open")
    void aQuestionLongerThanTheRoomTwiceLeavesTheWorkUnfinished(@TempDir Path root, @TempDir Path seeds)
            throws IOException {
        anApprovedCorpus(root, seeds);
        GenerationScriptedBeans.answerFor(theOnlyCluster(), anOrdinaryAnswer().havingRead(A_COUNT_PAST_THE_WINDOW));

        cli.run("run", root.toString());
        cli.run("run", root.toString());

        claim(
                "both answers were turned down because the question did not fit the window, asked once on each of"
                        + " the two invocations",
                () -> {
                    assertThat(GenerationScriptedBeans.callsMade()).isEqualTo(A_QUESTION_ON_EACH_OF_TWO_INVOCATIONS);
                    assertThat(reasonsKept(root)).singleElement().satisfies(kept -> assertThat(kept.fault().kind())
                            .isEqualTo(ClusterFaultKind.PROMPT_EVALUATION_CEILING));
                });
        claim(
                "and the work is not recorded as done: the same question is the same length each time it is asked,"
                        + " so a second overrun says the window is too small for the group, which the archive's owner"
                        + " can widen, and not that the writing keeps going wrong",
                () -> assertThat(theWorkIsRecordedAsFinished(root)).isFalse());
        claim(
                "so the page says why and nothing more, the membership list following the reason",
                () -> assertThat(UnwrittenPage.lineUnderTheReasonOf(UnwrittenPage.pageHeadedBy(theLatestTree(root),
                                theOnlyCluster())))
                        .isEqualTo(THE_MEMBERSHIP_HEADING));
    }

    @Test
    @Story("Only an answer turned down for what it wrote can leave a group standing")
    @DisplayName("A group whose question was too long for the window, then whose answer came back in the wrong shape, does not stand, and the work stays open")
    void aQuestionLongerThanTheRoomThenAWordingFaultLeavesTheWorkUnfinished(@TempDir Path root, @TempDir Path seeds)
            throws IOException {
        anApprovedCorpus(root, seeds);
        GenerationScriptedBeans.answerFor(theOnlyCluster(), anOrdinaryAnswer().havingRead(A_COUNT_PAST_THE_WINDOW));
        cli.run("run", root.toString());
        GenerationScriptedBeans.answerFor(theOnlyCluster(), anAnswerEndingWithAStrayBrace());

        cli.run("run", root.toString());

        claim(
                "the group was asked once on each of the two invocations, and the reason kept is the second's, an"
                        + " answer ending with a closing brace that belongs to nothing",
                () -> {
                    assertThat(GenerationScriptedBeans.callsMade()).isEqualTo(A_QUESTION_ON_EACH_OF_TWO_INVOCATIONS);
                    assertThat(reasonsKept(root)).singleElement().satisfies(kept -> {
                        assertThat(kept.fault().kind()).isEqualTo(ClusterFaultKind.SCHEMA_VIOLATION);
                        assertThat(kept.fault().detail()).isEqualTo(THE_STRAY_BRACE_DETAIL);
                    });
                });
        claim(
                "and the work is not recorded as done: the reason standing when the second question was put was the"
                        + " question's length, not anything an answer wrote, so only one of the two answers in a row was"
                        + " turned down for what it wrote, and the group is asked again",
                () -> assertThat(theWorkIsRecordedAsFinished(root)).isFalse());
        claim(
                "so the page says why and nothing more, the membership list following the reason",
                () -> assertThat(UnwrittenPage.lineUnderTheReasonOf(UnwrittenPage.pageHeadedBy(theLatestTree(root),
                                theOnlyCluster())))
                        .isEqualTo(THE_MEMBERSHIP_HEADING));
    }

    @Test
    @Story("Only an answer turned down for what it wrote can leave a group standing")
    @DisplayName("A group nothing could be sent for keeps the work open beside a group turned down twice")
    @Link(name = "ADR-121", url = Adr.A_WINDOW_WITH_NO_ROOM_IS_REFUSED, type = "adr")
    void aClusterNothingCouldBeSentForKeepsTheWorkUnfinishedBesideARepeatedFault(
            @TempDir Path root, @TempDir Path seeds) throws IOException {
        anApprovedCorpus(root, seeds);
        aClusterNothingCanBeSentFor(theApprovedArrangement(root), root);
        GenerationScriptedBeans.answerFor(theOnlyCluster(), anAnswerEndingWithAStrayBrace());

        cli.run("run", root.toString());
        cli.run("run", root.toString());

        claim(
                "the group with something to send was asked on both invocations and turned down both times, the"
                        + " other asked nothing at all",
                () -> {
                    assertThat(GenerationScriptedBeans.callsMade()).isEqualTo(A_QUESTION_ON_EACH_OF_TWO_INVOCATIONS);
                    assertThat(reasonsKept(root)).singleElement().satisfies(kept -> assertThat(kept.fault().kind())
                            .isEqualTo(ClusterFaultKind.SCHEMA_VIOLATION));
                });
        claim(
                "and the work is not recorded as done: a group nothing could be sent for keeps it open as it always"
                        + " has, whatever the group beside it stands for",
                () -> assertThat(theWorkIsRecordedAsFinished(root)).isFalse());
    }

    /**
     * Drops what was scripted and counted before each test as well as after it, for the reason {@link
     * GenerationFaultInvocationTest#forgetWhatAnEarlierClassScripted} gives: the counts are static on the fixture.
     */
    @BeforeEach
    void forgetWhatAnEarlierTestScripted() {
        GenerationScriptedBeans.forgetScriptedAnswers();
    }

    @AfterEach
    void forgetWhatWasScripted() {
        GenerationScriptedBeans.forgetScriptedAnswers();
    }

    /**
     * What one invocation of the sequence left, read at once, before the next draws the tree again in place.
     *
     * @param exitCode the invocation's exit code
     * @param finished whether the generation step's work is recorded as done under its run
     * @param questionsSoFar the questions put since the sequence began
     * @param reasons the reasons kept under the run
     * @param reasonOnThePage the line beneath the heading of the cluster's HTML page
     * @param lineUnderTheReason the line beneath that
     * @param confluenceBody the body of the cluster's page in the Confluence rendering
     * @param indexRow the index's row of the cluster
     * @param account the lines of the account this invocation wrote
     */
    private record AfterAnInvocation(
            int exitCode,
            boolean finished,
            int questionsSoFar,
            List<ListedFault> reasons,
            String reasonOnThePage,
            String lineUnderTheReason,
            String confluenceBody,
            String indexRow,
            List<String> account) {}

    /** One invocation over {@code root}, and what it left, read before anything else is invoked. */
    private AfterAnInvocation anInvocationOver(Path root) throws IOException {
        List<Path> before = accounts();
        cli.run("run", root.toString());
        Path tree = theLatestTree(root);
        Path page = UnwrittenPage.pageHeadedBy(tree, theOnlyCluster());
        return new AfterAnInvocation(
                cli.getExitCode(),
                theWorkIsRecordedAsFinished(root),
                GenerationScriptedBeans.callsMade(),
                reasonsKept(root),
                UnwrittenPage.lineUnderTheHeadingOf(page),
                UnwrittenPage.lineUnderTheReasonOf(page),
                theConfluenceBodySaying(tree, UnwrittenPage.forKind(ClusterFaultKind.SCHEMA_VIOLATION)),
                UnwrittenPage.indexRowFor(tree, theOnlyCluster()),
                theOneAccountWrittenSince(before).lines().toList());
    }

    /** The one Confluence page body in {@code tree}'s rendering carrying {@code sentence}, as the rendering escapes it. */
    private static String theConfluenceBodySaying(Path tree, String sentence) throws IOException {
        String escaped = UnwrittenPage.escaped(sentence);
        try (Stream<Path> files = Files.walk(tree.resolve(Publisher.RENDERING_FOLDER))) {
            List<String> bodies = files.filter(file -> file.getFileName().toString().equals(A_CONFLUENCE_PAGE_BODY))
                    .map(UnwrittenPage::textOf)
                    .filter(body -> body.contains(escaped))
                    .toList();
            if (bodies.size() != 1) {
                throw new IllegalStateException("expected one Confluence page body saying '" + sentence + "' under "
                        + tree + ", found " + bodies.size());
            }
            return bodies.getFirst();
        }
    }

    /** A publisher that sends nothing, recording which rendering it was asked to publish. */
    private static final class Recording extends Publisher {
        private final List<String> asked;

        Recording(List<String> asked) {
            super("", "", "", "");
            this.asked = asked;
        }

        @Override
        public Outcome publish(Path confluence, String generationRun, Path workingDirectory, Consumer<String> out) {
            asked.add(generationRun + " at " + confluence);
            return new Outcome(true, "published");
        }
    }

    /** The fixture's one cluster, named by the title every document it converts carries. */
    private static String theOnlyCluster() {
        return SeedScriptedExtractionBeans.STUBBED_TITLE;
    }

    /** An answer that passes every check, which each scripted answer here is one property away from. */
    private static ScriptedAnswer anOrdinaryAnswer() {
        return ScriptedAnswer.saying(GenerationScriptedBeans.GENERATED_TITLE, GenerationScriptedBeans.GENERATED_PROSE);
    }

    /** The ordinary answer, its writing ending with a stray closing brace. */
    private static ScriptedAnswer anAnswerEndingWithAStrayBrace() {
        return ScriptedAnswer.saying(GenerationScriptedBeans.GENERATED_TITLE, WRITING_ENDING_WITH_A_STRAY_BRACE);
    }

    /** A corpus of two documents, walked once, with the arrangement it produced approved, as in {@link GenerationFaultInvocationTest}. */
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
        profileStore.save(ProfileFixture.profileFrom(profileStore.load())
                .arrangementApproved(ArrangementGate.shortNameOf(theLatestArrangement(root)), "read by this test")
                .build());
    }

    /**
     * Puts a second cluster into the approved arrangement after the fixture's own, moves one of the two documents into
     * it, and takes that document's conversion off the record, so nothing of the second cluster can be sent: the
     * shape {@code GenerationInvocationTest} builds the same case with.
     */
    private void aClusterNothingCanBeSentFor(RunId arrangement, Path root) throws IOException {
        jdbcTemplate.update(
                "INSERT INTO cluster (run_id, winning_seed_occurrence_id, cluster_ordinal, label,"
                        + " document_count, partition_order, cluster_order)"
                        + " SELECT run_id, winning_seed_occurrence_id, cluster_ordinal + 1, ?,"
                        + " 1, partition_order, cluster_order + 1"
                        + " FROM cluster WHERE run_id = ?",
                A_CLUSTER_NOTHING_CAN_BE_SENT_FOR,
                arrangement.value());
        jdbcTemplate.update(
                "UPDATE cluster SET document_count = 1 WHERE run_id = ? AND label <> ?",
                arrangement.value(),
                A_CLUSTER_NOTHING_CAN_BE_SENT_FOR);
        long moved = jdbcTemplate.queryForObject(
                "SELECT occurrence_id FROM document_cluster WHERE run_id ="
                        + " (SELECT upstream_run_id FROM run_upstream WHERE run_id = ?)"
                        + " ORDER BY occurrence_id DESC LIMIT 1",
                Long.class,
                arrangement.value());
        jdbcTemplate.update(
                "UPDATE document_cluster SET cluster_ordinal = cluster_ordinal + 1 WHERE occurrence_id = ?"
                        + " AND run_id = (SELECT upstream_run_id FROM run_upstream WHERE run_id = ?)",
                moved,
                arrangement.value());
        String path = jdbcTemplate.queryForObject(
                "SELECT path FROM file_occurrence WHERE id = ?", String.class, moved);
        ConversionOffTheRecordFixture.takenOffTheRecord(jdbcTemplate, root.resolve(path));
    }

    /** The tree the most recent invocation over {@code root} drew, under its latest generation run. */
    private Path theLatestTree(Path root) {
        return UnwrittenPage.treeOf(workingDirectory, generationRuns(root).getLast());
    }

    /** Every reason stage 6b kept for a cluster of {@code root}, under whichever run it kept it. */
    private List<ListedFault> reasonsKept(Path root) {
        return generationRuns(root).stream()
                .map(RunId::new)
                .flatMap(run -> WholeRun.clusterFaults(jdbcTemplate, run).stream())
                .toList();
    }

    /** Whether the generation step's own work is recorded as complete under a run of {@code root}. */
    private boolean theWorkIsRecordedAsFinished(Path root) {
        return generationRuns(root).stream()
                .anyMatch(run -> jdbcTemplate.queryForObject(
                                "SELECT COUNT(*) FROM finished_step WHERE run_id = ? AND step = ?",
                                Integer.class,
                                run,
                                StepNames.GENERATION)
                        > 0);
    }

    /** The arrangement the most recent invocation over {@code root} recorded. */
    private RunId theLatestArrangement(Path root) {
        return new RunId(jdbcTemplate.queryForObject(
                "SELECT r.id FROM run r JOIN walk w ON w.id = r.walk_id"
                        + " WHERE r.stage = ? AND w.root = ? ORDER BY r.rowid DESC LIMIT 1",
                String.class,
                "arrangement",
                Walk.canonicalRoot(root).toString()));
    }

    /** The arrangement of {@code root} the profile's approval names. */
    private RunId theApprovedArrangement(Path root) {
        return new RunId(jdbcTemplate.queryForObject(
                "SELECT r.id FROM run r JOIN walk w ON w.id = r.walk_id"
                        + " WHERE r.stage = ? AND w.root = ? AND r.id LIKE ? ORDER BY r.rowid LIMIT 1",
                String.class,
                "arrangement",
                Walk.canonicalRoot(root).toString(),
                profileStore.load().arrangementApproved().value() + "%"));
    }

    /** Every generation run over {@code root}, oldest first, scoped to its walk since the database serves the class. */
    private List<String> generationRuns(Path root) {
        return jdbcTemplate.queryForList(
                "SELECT r.id FROM run r JOIN walk w ON w.id = r.walk_id"
                        + " WHERE r.stage = ? AND w.root = ? ORDER BY r.rowid",
                String.class,
                "generation",
                Walk.canonicalRoot(root).toString());
    }

    /** Every account in the working directory's logs folder now. */
    private static List<Path> accounts() throws IOException {
        Path logs = workingDirectory.resolve(LOGS);
        if (!Files.isDirectory(logs)) {
            return List.of();
        }
        try (Stream<Path> files = Files.list(logs)) {
            return files.filter(file -> file.getFileName().toString().startsWith(ACCOUNT_PREFIX)).toList();
        }
    }

    /** The one account written since {@code before} was listed. */
    private static String theOneAccountWrittenSince(List<Path> before) throws IOException {
        List<Path> written = accounts().stream().filter(file -> !before.contains(file)).toList();
        if (written.size() != 1) {
            throw new IllegalStateException("expected the invocation to write one account, found " + written.size());
        }
        try {
            return Files.readString(written.getFirst());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
