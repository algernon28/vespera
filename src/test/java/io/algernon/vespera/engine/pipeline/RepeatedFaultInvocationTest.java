package io.algernon.vespera.engine.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.WholeRun;
import io.algernon.vespera.publish.confluence.Publisher;
import io.algernon.vespera.engine.corpus.Walk;
import io.algernon.vespera.engine.ledger.Ledger;
import io.algernon.vespera.engine.ledger.RunId;
import io.algernon.vespera.engine.pipeline.GenerationScriptedBeans.ScriptedAnswer;
import io.algernon.vespera.engine.profile.ProfileFixture;
import io.algernon.vespera.engine.profile.ProfileStore;
import io.algernon.vespera.engine.synthesis.ClusterFaultKind;
import io.algernon.vespera.engine.synthesis.ListedFault;
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
import tools.jackson.databind.JsonNode;

/**
 * A cluster whose answer is turned down the last two times it is asked stands without writing, and the generation
 * step finishes with it (ADR-319): what makes a fault a repeated one, when the step records completion, that nothing
 * more is asked under the run once it has, and what the report and the publish make of it.
 *
 * <p><b>One sequence is built once for the class</b>, since it is three invocations long and five tests read it
 * (REVIEW.md, "shares its setup"): the fixture's one cluster answered with writing that ends with a stray closing
 * brace, the shape ADR-162 turns down, on the invocation that first reaches the writing and on the next one, and a
 * third invocation after that. What each test claims is read off what {@link #aClusterTurnedDownTwiceAndRunAgain}
 * kept after each invocation, because the tree is drawn again in place by every invocation and what the first one
 * drew is gone by the time a test runs. The four tests of the boundary build their own sequences.
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

    /** The window every call in this class reads in, stated so a test claiming an overrun knows what it overran. */
    private static final String THE_READING_WINDOW = "4096";

    /**
     * Writing that ends with a closing brace belonging to no opening one, after its last sentence: the shape of the
     * measured answers ADR-319 rests on, and the one ADR-162 turns down. Its one citation is in range, so nothing but
     * the brace is wrong with it.
     */
    private static final String WRITING_ENDING_WITH_A_STRAY_BRACE = GenerationScriptedBeans.GENERATED_PROSE + " }";

    /** The detail ADR-162 keeps for that answer. */
    private static final String THE_STRAY_BRACE_DETAIL =
            "the answer's writing ends with a closing brace that belongs to no opening one";

    /** What a cluster's Confluence page body is called in its folder of the rendering (ADR-312). */
    private static final String A_CONFLUENCE_PAGE_BODY = "page.xml";


    /** One reason kept for the one cluster, though it was turned down twice: the second replaced the first. */
    private static final int ONE_REASON_KEPT = 1;

    /** Every invocation over an unchanged archive works under one generation run, so its work is continued. */
    private static final int ONE_GENERATION_RUN = 1;

    /** One question put on each of the two invocations that reach the cluster unwritten. */
    private static final int A_QUESTION_ON_EACH_OF_TWO_INVOCATIONS = 2;

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
     * and a third invocation with that answer still scripted, forgotten only once it has run. Not a {@code @BeforeAll}: the Spring context, and the beans it
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
     * @param counts the {@code counts} of the report this invocation wrote
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
            JsonNode counts) {}

    /** One invocation over {@code root}, and what it left, read before anything else is invoked. */
    private AfterAnInvocation anInvocationOver(Path root) throws IOException {
        List<Path> before = TheReportFile.copiesIn(workingDirectory);
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
                theOneCopyWrittenSince(before).path("counts"));
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

    /** The ordinary answer, its writing ending with a stray closing brace. */
    private static ScriptedAnswer anAnswerEndingWithAStrayBrace() {
        return ScriptedAnswer.saying(GenerationScriptedBeans.GENERATED_TITLE, WRITING_ENDING_WITH_A_STRAY_BRACE);
    }

    /** A corpus of two documents, walked once, with the arrangement it produced approved, as in {@link GenerationFaultInvocationTest}. */
    private void anApprovedCorpus(Path root, Path seeds) throws IOException {
        Files.writeString(root.resolve("corpus.txt"), "a corpus document");
        Files.writeString(root.resolve("another-corpus-document.txt"), "a second corpus document");
        Files.writeString(seeds.resolve("seed.txt"), "a seed document");
        EveryGateOpen.throughStageFive(profileStore, seeds);
        profileStore.save(ProfileFixture.profileFrom(profileStore.load())
                .generationContextWindow(THE_READING_WINDOW, "set by this test, so every test here reads in the same window")
                .build());
        cli.run("run", root.toString());
        profileStore.save(ProfileFixture.profileFrom(profileStore.load())
                .arrangementApproved(ArrangementGate.shortNameOf(
                        new RunsOver(jdbcTemplate, root).latest(StageModules.ARRANGEMENT.stage())), "read by this test")
                .build());
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

    /** Every generation run over {@code root}, oldest first, scoped to its walk since the database serves the class. */
    private List<String> generationRuns(Path root) {
        return jdbcTemplate.queryForList(
                "SELECT r.id FROM run r JOIN walk w ON w.id = r.walk_id"
                        + " WHERE r.stage = ? AND w.root = ? ORDER BY r.rowid",
                String.class,
                "generation",
                Walk.canonicalRoot(root).toString());
    }

    /** The one copy of a report written since {@code before} was listed. */
    private static JsonNode theOneCopyWrittenSince(List<Path> before) throws IOException {
        List<Path> written = TheReportFile.copiesIn(workingDirectory).stream()
                .filter(file -> !before.contains(file))
                .toList();
        if (written.size() != 1) {
            throw new IllegalStateException("expected the invocation to write one copy, found " + written.size());
        }
        return TheReportFile.parse(Files.readString(written.getFirst()));
    }
}
