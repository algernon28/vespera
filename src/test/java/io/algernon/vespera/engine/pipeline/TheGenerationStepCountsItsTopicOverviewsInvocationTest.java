package io.algernon.vespera.engine.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.algernon.vespera.Adr;
import io.algernon.vespera.engine.corpus.Walk;
import io.algernon.vespera.engine.ledger.RunId;
import io.algernon.vespera.engine.profile.ProfileStore;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * The generation step's line on finishing counts the topic overviews recorded under its run, written and turned down
 * (ADR-416 §7): the one place they are counted.
 *
 * <p>One seed of one passage gives one topic, and its one corpus document one cluster, as {@link
 * TheTopicOverviewIsRecordedAtGenerationInvocationTest} has it. This fixture's model writes the overview citing the
 * cluster it was sent, which the overview's check believes; made to write {@link
 * GenerationScriptedBeans#OVERVIEW_WRITING} alone, its one sentence cites no group and the overview is turned down at
 * both askings. <b>The arrangement is made once for the class</b> (ADR-337 §4), and each test approves it naming a
 * writing model of its own, so that each works under a generation run of its own, which has asked no overview yet. The
 * line is read off the application's logger, where the step logs it.
 */
@CascadeSliceTest
@Import(SeedScriptedExtractionBeans.class)
@Epic("Generation")
@Feature("Topic overview")
@Issue("834")
@Link(name = "ADR-416", url = Adr.THE_TOPICS_ANALYSIS_REPORT_LISTS_WHAT_FEEDS_EACH_TOPIC, type = "adr")
class TheGenerationStepCountsItsTopicOverviewsInvocationTest {

    /** The writing model of the approval whose overview is written. */
    private static final String A_WRITING_MODEL = "a-named-writing-model:8b";

    /** The writing model of the approval whose overview is turned down, so that its generation run is another. */
    private static final String ANOTHER_WRITING_MODEL = "another-named-writing-model:8b";

    /** The writing model of the approval whose tree's counter is read, so that its generation run is a third. */
    private static final String A_THIRD_WRITING_MODEL = "a-third-named-writing-model:8b";

    /** The label of the tree's counter of the topics written (ADR-426 §2). */
    private static final String TOPICS_WRITTEN = "Stage 6b (generation, topics written)";

    /** How many topics the arrangement holds: one seed of one passage gives one. */
    private static final long ONE_TOPIC = 1;

    /** What the generation step's line on finishing opens with. */
    private static final String THE_FINISHING_LINE = "The generation step finished under ";

    /** What the line holds, after the synthesis docs it counts and before the tree, where the one overview is written. */
    private static final String ONE_WRITTEN = "they were; 1 topic overview(s) written and 0 turned down -- the"
            + " deliverable tree is at ";

    /** What it holds where the one overview is turned down. */
    private static final String ONE_TURNED_DOWN = "they were; 0 topic overview(s) written and 1 turned down -- the"
            + " deliverable tree is at ";

    /** The logger every operator-facing line of the application is written through, the step's among them. */
    private static final String APPLICATION_LOGGER = "io.algernon.vespera";

    @TempDir
    static Path workingDirectory;

    /** The corpus root of the one-topic arrangement the class makes once. */
    @TempDir
    static Path theRoot;

    /** The seed folder of that arrangement. */
    @TempDir
    static Path theSeeds;

    @DynamicPropertySource
    static void thisMachinesValues(DynamicPropertyRegistry registry) {
        registry.add("vespera.working-dir", workingDirectory::toString);
    }

    /** The approval name of the one-topic arrangement, made the first time a test asks for it. */
    private static final Once<String> arrangement = new Once<>();

    @Autowired
    private VesperaCli cli;

    @Autowired
    private ProfileStore profileStore;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private ListAppender<ILoggingEvent> applicationLines;

    @BeforeEach
    void hearTheApplicationsLinesAndScriptNothing() {
        GenerationScriptedBeans.forgetScriptedAnswers();
        EmbeddingScriptedBeans.servesEveryModel();
        applicationLines = new ListAppender<>();
        applicationLines.start();
        ((Logger) LoggerFactory.getLogger(APPLICATION_LOGGER)).addAppender(applicationLines);
    }

    @AfterEach
    void stopHearingThemAndScriptNothing() {
        ((Logger) LoggerFactory.getLogger(APPLICATION_LOGGER)).detachAppender(applicationLines);
        applicationLines.stop();
        GenerationScriptedBeans.forgetScriptedAnswers();
    }

    @Test
    @Story("Each topic gets a short overview of its groups")
    @DisplayName("The writing step's line on finishing counts the topic overview written")
    void aWrittenOverviewIsCountedInTheClosingLine() {
        approvedUnder(A_WRITING_MODEL);

        cli.run("run", theRoot.toString());

        claim("the invocation that writes reports success", () -> assertThat(cli.getExitCode()).isZero());
        claim("the step's line on finishing counts, after the synthesis docs, 1 topic overview written and none turned"
                        + " down, then names the tree",
                () -> assertThat(finishingLines()).singleElement().asString().contains(ONE_WRITTEN));
    }

    @Test
    @Story("Each topic gets a short overview of its groups")
    @DisplayName("The writing step's line on finishing counts the topic overview turned down")
    void aTurnedDownOverviewIsCountedInTheClosingLine() {
        approvedUnder(ANOTHER_WRITING_MODEL);
        GenerationScriptedBeans.writesEveryOverviewAs(GenerationScriptedBeans.OVERVIEW_WRITING);

        cli.run("run", theRoot.toString());

        claim("the invocation reports success: a turned-down overview stops nothing",
                () -> assertThat(cli.getExitCode()).isZero());
        claim("the step's line on finishing counts no topic overview written and 1 turned down, then names the tree",
                () -> assertThat(finishingLines()).singleElement().asString().contains(ONE_TURNED_DOWN));
    }

    /**
     * The tree's counter of topics written is labelled for topics (ADR-426 §2): over the one topic its one line, at 1 of
     * 1, under that label and no other.
     */
    @Test
    @Link(name = "ADR-426", url = Adr.THE_SEED_PARTITIONS_NAMES_ARE_RETIRED_FROM_THE_CODE_AND_THE_TESTS, type = "adr")
    @Issue("895")
    @Story("The writing step reports how far it has got")
    @DisplayName("The writing step counts the topics of the tree it writes, under a label that says topics")
    void theTreesTopicsAreCountedUnderTheirOwnLabel() {
        approvedUnder(A_THIRD_WRITING_MODEL);

        cli.run("run", theRoot.toString());

        claim("the invocation that writes reports success", () -> assertThat(cli.getExitCode()).isZero());
        claim("the counter of the topics written writes its one line over the arrangement's " + ONE_TOPIC + " topic,"
                        + " labelled " + TOPICS_WRITTEN,
                () -> assertThat(ProgressLines.of(applicationLines.list, TOPICS_WRITTEN))
                        .containsExactlyElementsOf(ProgressLines.expected(TOPICS_WRITTEN, ONE_TOPIC)));
    }

    /** The lines of the generation step's finishing this test heard. */
    private List<String> finishingLines() {
        return applicationLines.list.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .filter(line -> line.startsWith(THE_FINISHING_LINE))
                .toList();
    }

    /** The one-topic arrangement approved with {@code model} to write under, the profile set again before it. */
    private void approvedUnder(String model) {
        String approval = arrangement.get(() -> {
            Files.writeString(theRoot.resolve("corpus.txt"), "a corpus document of " + theRoot.getFileName());
            Files.writeString(theSeeds.resolve("seed.txt"), "a seed document of " + theSeeds.getFileName());
            EveryGateOpen.throughStageFive(profileStore, theSeeds);
            cli.run("run", theRoot.toString());
            return ArrangementGate.shortNameOf(new RunId(theArrangementRun()));
        });
        EveryGateOpen.throughStageFive(profileStore, theSeeds);
        EveryGateOpen.approve(profileStore, approval, model);
    }

    /** The one arrangement run over {@link #theRoot}. */
    private String theArrangementRun() {
        return jdbcTemplate.queryForObject(
                "SELECT r.id FROM run r JOIN walk w ON w.id = r.walk_id WHERE r.stage = 'arrangement' AND w.root = ?",
                String.class,
                Walk.canonicalRoot(theRoot).toString());
    }
}
