package io.algernon.vespera.engine.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import io.algernon.vespera.Adr;
import io.algernon.vespera.engine.corpus.Walk;
import io.algernon.vespera.engine.profile.ProfileStore;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
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
 * Placement in the relevance-scoring step (ADR-400 §5, ADR-401 §1, §5): it is read in the scoring pass, from the
 * cosines the scorer takes, with no call to the embedder; it places under the topics that stand once the breadth floor
 * is applied, and under no topic the floor dropped; and where no topic stands the step stops in one line, naming the
 * floor and the largest topic's share, before any survivor is scored. A sibling of {@link
 * TheTopicsAreFoundAtTheHeadOfRelevanceScoringInvocationTest}, on its fixture, with this machine's floor at {@value
 * #THE_FLOOR} and its neighbours for the topics at {@value #TWO_NEIGHBOURS}.
 *
 * <p>Nothing stores a placement yet, that being #832's, so what is read is the step's own lines: how many topics it
 * found, how many stand, and how many survivors it placed under how many topics, the last counted off the placement it
 * built. Each line the application logs is kept, in order, with how many calls {@link EmbeddingScriptedBeans}'
 * embedding double had answered when it was logged.
 *
 * <p>Two tests set the seeds' passages apart: {@link SeedScriptedExtractionBeans} converts each of its files on sluices
 * and on pumps to a text of its own, so that each seed is a content and a passage of its own; a text holding {@value
 * #SLUICES} embeds along one axis, and one holding {@value #PUMPS} along another, a tenth of the way towards the first,
 * so that the passages of each word are one topic and the two are two. Every other file converts to one text, which
 * embeds to the vector of ones.
 */
@CascadeSliceTest
@Import(SeedScriptedExtractionBeans.class)
@Epic("Relevance")
@Feature("Topics")
@Issue("828")
@Link(name = "ADR-400", url = Adr.THE_WINNING_TOPIC_IS_THE_CENTROID_ITS_CHUNKS_ARE_NEAREST_ON_AVERAGE, type = "adr")
@Link(name = "ADR-401", url = Adr.A_TOPIC_UNDER_THE_BREADTH_FLOOR_IS_DROPPED, type = "adr")
class PlacementIsReadInTheScoringPassInvocationTest {

    /** The logger every operator-facing line in this application is written through. */
    private static final String APPLICATION_LOGGER = "io.algernon.vespera";

    /** What every line of the relevance-scoring step opens with, its counters' labels included. */
    private static final String THE_STEP = "Stage 5d (relevance scoring";

    /** This machine's breadth floor: a topic of two thirds of the passages stands, one of a half does not. */
    private static final double THE_FLOOR = 0.6;

    /**
     * This machine's neighbours for the topics: two, so that each passage's neighbours are passages of its own word,
     * where a seed set of six passages and the shipped ten would make one graph of every pair, and one topic.
     */
    private static final int TWO_NEIGHBOURS = 2;

    /** A word whose text embeds along the first axis. */
    private static final String SLUICES = "sluices";

    /** A word whose text embeds along the second axis, a tenth of the way towards the first. */
    private static final String PUMPS = "pumps";

    /** The one survivor scored, the counter's line at its one item. */
    private static final String THE_SURVIVOR_SCORED = "Stage 5d (relevance scoring, corpus survivors): 1 of 1 (100%)";

    @TempDir
    static Path workingDirectory;

    @DynamicPropertySource
    static void thisMachinesValues(DynamicPropertyRegistry registry) {
        registry.add("vespera.working-dir", workingDirectory::toString);
        registry.add("vespera.topics.breadth-floor", () -> String.valueOf(THE_FLOOR));
        registry.add("vespera.topics.neighbours", () -> String.valueOf(TWO_NEIGHBOURS));
    }

    @Autowired
    private VesperaCli cli;

    @Autowired
    private ProfileStore profileStore;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /** A line the application logged, and how many calls the embedding double had answered by then. */
    private record Logged(String line, int embeddingCallsMade) {}

    /** Keeps each line as it is logged, with the embedding double's count read at that moment. */
    private static final class Timeline extends AppenderBase<ILoggingEvent> {

        private final List<Logged> logged = Collections.synchronizedList(new ArrayList<>());

        @Override
        protected void append(ILoggingEvent event) {
            logged.add(new Logged(event.getFormattedMessage(), EmbeddingScriptedBeans.embeddingCallsMade()));
        }

        List<Logged> logged() {
            synchronized (logged) {
                return List.copyOf(logged);
            }
        }
    }

    private Timeline timeline;
    private ch.qos.logback.classic.Logger applicationLogger;

    @BeforeEach
    void keepTheTimeline() {
        EmbeddingScriptedBeans.servesEveryModel();
        EmbeddingScriptedBeans.embedsTextHolding(SLUICES, 1, 0, 0, 0, 0, 0, 0, 0);
        EmbeddingScriptedBeans.embedsTextHolding(PUMPS, 0.1f, 1, 0, 0, 0, 0, 0, 0);
        timeline = new Timeline();
        timeline.start();
        applicationLogger = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(APPLICATION_LOGGER);
        applicationLogger.addAppender(timeline);
    }

    @AfterEach
    void releaseTheTimeline() {
        applicationLogger.detachAppender(timeline);
        timeline.stop();
        EmbeddingScriptedBeans.servesEveryModel();
    }

    @Test
    @Story("Placement reads the similarities the score already takes")
    @DisplayName("Documents are placed in the scoring pass, after the topics are found and as each is scored, with no"
            + " call to the embedder")
    void isReadFromTheScorersOwnCosinesWithNoEmbeddingCall(@TempDir Path root, @TempDir Path seeds) throws IOException {
        Files.writeString(root.resolve("corpus.txt"), "a corpus document of " + root.getFileName());
        Files.writeString(seeds.resolve("seed.txt"), "a seed document of " + seeds.getFileName());
        EveryGateOpen.throughStageFive(profileStore, seeds);

        cli.run("run", root.toString());

        List<Logged> logged = timeline.logged();
        String found = topicsFound(1);
        String placed = placed(1, 1);
        claim("the invocation ended well", () -> assertThat(cli.getExitCode()).isZero());
        claim("the step found the one topic of the one seed's one passage, scored the one survivor and then said it"
                        + " placed it under that topic, in that order",
                () -> assertThat(linesAmong(logged, found, THE_SURVIVOR_SCORED, placed))
                        .containsExactly(found, THE_SURVIVOR_SCORED, placed));
        claim("the embedder answered no call between the step's first line and the line saying the survivor was placed",
                () -> assertThat(callsMadeBy(logged, placed)).isEqualTo(callsMadeByTheStepsFirstLine(logged)));
    }

    @Test
    @Story("A topic too narrow for the top level is not a topic")
    @DisplayName("Documents are placed under the topics that stand, and under none the breadth floor dropped")
    void placesUnderTheTopicsThatStandAlone(@TempDir Path root, @TempDir Path seeds) throws IOException {
        Files.writeString(
                root.resolve(SeedScriptedExtractionBeans.A_DOCUMENT_ON_PUMPS), "a corpus document of " + root.getFileName());
        seedsOf(seeds, SeedScriptedExtractionBeans.ON_SLUICES.subList(0, 4));
        seedsOf(seeds, SeedScriptedExtractionBeans.ON_PUMPS.subList(0, 2));
        EveryGateOpen.throughStageFive(profileStore, seeds);

        cli.run("run", root.toString());

        List<Logged> logged = timeline.logged();
        String found = topicsFound(2);
        String kept = "Stage 5d (relevance scoring) kept 1 of them standing at a breadth floor of " + THE_FLOOR + "; 2"
                + " seed(s) feed no topic";
        String placed = placed(1, 1);
        claim("the invocation ended well", () -> assertThat(cli.getExitCode()).isZero());
        claim("the step found two topics, the four seeds on sluices and the two on pumps; kept the first, four of six"
                        + " passages, at a floor of " + THE_FLOOR + ", and not the second, two of six, so that the two"
                        + " seeds on pumps feed none; and placed the one survivor, whose text is on pumps, under the one"
                        + " topic that stands, in that order",
                () -> assertThat(linesAmong(logged, found, kept, placed)).containsExactly(found, kept, placed));
    }

    @Test
    @Story("A topic too narrow for the top level is not a topic")
    @DisplayName("Where no topic stands at the breadth floor, the scoring stops before any document is scored, saying"
            + " the floor and the largest topic's share")
    void stopsBeforeScoringWhereNoTopicStands(@TempDir Path root, @TempDir Path seeds) throws IOException {
        Files.writeString(root.resolve("corpus.txt"), "a corpus document of " + root.getFileName());
        seedsOf(seeds, SeedScriptedExtractionBeans.ON_SLUICES.subList(0, 3));
        seedsOf(seeds, SeedScriptedExtractionBeans.ON_PUMPS.subList(0, 3));
        EveryGateOpen.throughStageFive(profileStore, seeds);

        cli.run("run", root.toString());

        String theStop = "stage 5's relevance-scoring step stopped: no topic stands at a breadth floor of " + THE_FLOOR
                + ", the largest of the 2 topics found holding 3 of the 6 seed passages, a share of 0.5. Nothing was"
                + " scored and the step is not recorded as finished.";
        List<String> runs = scoringRunIdsFor(root);
        claim("the invocation fails", () -> assertThat(cli.getExitCode()).isNotZero());
        claim("and says, in one line, that no topic stands at the floor of " + THE_FLOOR + ", the two topics found each"
                        + " holding three of the six passages",
                () -> assertThat(timeline.logged()).extracting(Logged::line).contains(theStop));
        claim("no survivor was scored or placed: the step did not reach the line counting survivors",
                () -> assertThat(timeline.logged())
                        .noneMatch(entry -> entry.line().startsWith("Stage 5d (relevance scoring, corpus survivors)")));
        claim("under the one scoring run, no score is recorded and the step is not recorded as finished",
                () -> assertThat(runs).singleElement().satisfies(run -> {
                    assertThat(countUnder("relevance_score", run)).isZero();
                    assertThat(jdbcTemplate.queryForObject(
                                    "SELECT COUNT(*) FROM finished_step WHERE run_id = ? AND step = ?",
                                    Long.class,
                                    run,
                                    StepNames.RELEVANCE_SCORING))
                            .isZero();
                }));
    }

    /** The step's line saying it found {@code topics} topics. */
    private static String topicsFound(int topics) {
        return "Stage 5d (relevance scoring) found " + topics + " topic(s) across the seeds' passages";
    }

    /** The step's line saying it placed {@code survivors} survivors, under the {@code topics} the placement read. */
    private static String placed(int survivors, int topics) {
        return "Stage 5d (relevance scoring) placed " + survivors + " survivor(s) under the " + topics + " topic(s) that"
                + " stand, the placement held and not yet stored (#832)";
    }

    /**
     * A seed of each of {@code names}, one chunk each, whose conversion is a text of its own holding the word its name
     * does; its bytes name the folder, so that no other test wrote them.
     */
    private static void seedsOf(Path seeds, List<String> names) throws IOException {
        for (String name : names) {
            Files.writeString(seeds.resolve(name), "a seed document " + name + " of " + seeds.getFileName());
        }
    }

    /** The lines of {@code logged} that are one of {@code lines}, in the order logged. */
    private static List<String> linesAmong(List<Logged> logged, String... lines) {
        List<String> wanted = List.of(lines);
        return logged.stream().map(Logged::line).filter(wanted::contains).toList();
    }

    /** How many calls the embedding double had answered when {@code line} was logged. */
    private static int callsMadeBy(List<Logged> logged, String line) {
        return logged.stream()
                .filter(entry -> entry.line().equals(line))
                .findFirst()
                .orElseThrow()
                .embeddingCallsMade();
    }

    /** How many calls the embedding double had answered when the relevance-scoring step logged its first line. */
    private static int callsMadeByTheStepsFirstLine(List<Logged> logged) {
        return logged.stream()
                .filter(entry -> entry.line().startsWith(THE_STEP))
                .findFirst()
                .orElseThrow()
                .embeddingCallsMade();
    }

    private List<String> scoringRunIdsFor(Path root) {
        return jdbcTemplate.queryForList(
                "SELECT run.id FROM run JOIN walk w ON w.id = run.walk_id"
                        + " WHERE run.stage = 'embedding-scoring' AND w.root = ? ORDER BY run.id",
                String.class,
                Walk.canonicalRoot(root).toString());
    }

    private long countUnder(String table, String run) {
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE run_id = ?", Long.class, run);
        return count == null ? 0 : count;
    }
}
