package io.algernon.vespera.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.algernon.vespera.Adr;
import io.algernon.vespera.corpus.Walk;
import io.algernon.vespera.ledger.RunId;
import io.algernon.vespera.pipeline.GenerationScriptedBeans.ScriptedAnswer;
import io.algernon.vespera.profile.Profile;
import io.algernon.vespera.profile.ProfileFixture;
import io.algernon.vespera.profile.ProfileStore;
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
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * What stage 6b says while it goes through the arrangement's clusters and writes the tree (ADR-192
 * sections 4 and 5, #412).
 *
 * <p>Since ADR-190 the walk over a run's clusters is {@code synthesis.ClusterGeneration.write}, which tells
 * {@code GenerationTasklet} about the clusters it leaves unwritten through {@code GenerationProgress}. ADR-192
 * gives that interface two more methods, {@code toGoThrough(long)} once before the walk and {@code
 * clusterGoneThrough()} at the end of every cluster's path, so the step can count every cluster whatever
 * became of it: written, turned down, the fifth turned down that stops the walk, already written, nothing
 * sendable, no room counted, nothing fitting the window. The tree is written on each of the walk's three
 * exits, and its counters with it.
 *
 * <p><b>The clusters are written straight into the arrangement</b>, as {@code GenerationBreakerInvocationTest}
 * writes them and for its reason: every document this fixture converts comes back alike, so no corpus
 * clusters into several groups of its own. Its fixture is restated here rather than shared, so that this
 * class can move into the tree with part (d) on its own. The membership under the scoring run can carry
 * other methods' documents, one working directory serving the class, so the totals of the counters that read
 * the membership are read from the database rather than written here.
 *
 * <p><b>Part (d) of ADR-192.</b> This compiles against main and is red there at the claims that the counters'
 * lines are there, each after a claim that the step ran and how it ended; part (d) moves it into {@code
 * src/test} and turns it green. The running counter of cluster documents opened writes its first line at the
 * 1,000th, which no fixture here reaches, and is not pinned. Report text says <em>group</em> where these names
 * say cluster (ADR-122).
 */
@CascadeSliceTest
@Import(SeedScriptedExtractionBeans.class)
@Epic("Pipeline")
@Feature("Progress reporting")
@Issue("412")
@Link(name = "ADR-192", url = Adr.EVERY_LOOP_REPORTS_ITS_PROGRESS, type = "adr")
@Link(name = "ADR-190", url = Adr.STAGE_6_RULES_LIVE_IN_SYNTHESIS, type = "adr")
class GenerationReportsItsProgressInvocationTest {

    private static final String BOILERPLATE_FLOOR = "1.0";
    private static final String EMBEDDING_MODEL_NAME = "qwen3-embedding:0.6b";
    private static final String THE_READING_WINDOW = "4096";
    private static final int THE_WINDOW = 4096;
    private static final int THE_CEILING = THE_WINDOW - 1;

    /** Counted at the ceiling however few documents it carries: past the room at every length. */
    private static final int COUNTED_TOO_LONG_AT_ANY_LENGTH = THE_CEILING;

    private static final int A_COUNT_PAST_THE_CEILING = THE_WINDOW + 904;
    private static final int THE_WHOLE_ANSWER_ALLOWANCE = 1024;
    private static final String AN_ANSWER_NOTHING_CAN_READ = "{\"title\":\"Site Safety Audits\",\"prose\":";
    private static final String A_TITLE = "What The Stubbed Document Says";
    private static final String PROSE_POINTING_AT_NOTHING = "The audit [7] states every finding.";

    /** A window with room for one word and nothing more, too small for any of this fixture's documents. */
    private static final String A_WINDOW_WITH_ROOM_FOR_A_SINGLE_WORD = "1282";

    private static final List<String> CLUSTER_NAMES = List.of(
            "Group one", "Group two", "Group three", "Group four", "Group five", "Group six", "Group seven");

    private static final int THE_STREAK_THAT_STOPS_THE_STEP = 5;
    private static final int ALL_FOUR_WAYS_AN_ANSWER_IS_TURNED_DOWN = 4;

    /** Six clusters: five to stop on and one the walk never reaches. */
    private static final int SIX_CLUSTERS = THE_STREAK_THAT_STOPS_THE_STEP + 1;

    /** Seven clusters: one either side of a streak with one cluster walked past inside it. */
    private static final int SEVEN_CLUSTERS = THE_STREAK_THAT_STOPS_THE_STEP + 2;

    /** Two clusters, for the window that fits nothing. */
    private static final int TWO_CLUSTERS = 2;

    /** The third cluster, counting from zero. */
    private static final int THE_THIRD = 2;

    private static final int EVERY_CLUSTER_HOLDS_A_DOCUMENT = -1;

    /** Every cluster here sits under one seed, so the tree has one partition directory. */
    private static final int ONE_PARTITION = 1;

    /** Each cluster holds one document of this walk, so its file lists one membership entry with a document. */
    private static final int ONE_ENTRY_PER_CLUSTER = 1;

    private static final String CLUSTERS = "Stage 6b (generation, clusters)";
    private static final String SCORES = "Stage 6b (generation, scores read)";
    private static final String LISTED = "Stage 6b (generation, survivors listed)";
    private static final String PICTURES = "Stage 6b (generation, pictures listed)";
    private static final String PARTITIONS = "Stage 6b (generation, partitions written)";
    private static final String FILES = "Stage 6b (generation, cluster files written)";
    private static final String ENTRIES = "Stage 6b (generation, membership entries)";

    private static final String FINISHED = "The generation step finished under ";
    private static final String STOPPED = "the generation step stopped under run ";
    private static final String LEFT_UNWRITTEN = "the generation step left ";

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

    private ListAppender<ILoggingEvent> logged;
    private ch.qos.logback.classic.Logger applicationLogger;

    /** The scripted answers and the count of calls are static and shared: forgotten before each test as well as after. */
    @BeforeEach
    void captureOperatorLines() {
        GenerationScriptedBeans.forgetScriptedAnswers();
        SeedScriptedExtractionBeans.stopMovingAway();
        logged = new ListAppender<>();
        logged.start();
        applicationLogger = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger("io.algernon.vespera");
        applicationLogger.addAppender(logged);
    }

    @AfterEach
    void releaseOperatorLines() {
        applicationLogger.detachAppender(logged);
        logged.stop();
        GenerationScriptedBeans.forgetScriptedAnswers();
    }

    @Test
    @Story("Writing over the groups says how far it has got")
    @DisplayName("With every answer believed, the step counts each of the six groups, and the tree's loops each count theirs")
    void countsEveryClusterAndTheTreeOnACleanFinish(@TempDir Path root, @TempDir Path seeds) throws IOException {
        anApprovedArrangementOf(SIX_CLUSTERS, root, seeds);
        logged.list.clear();

        cli.run("run", root.toString());

        claim("the step finished, which its own line says", () -> assertThat(operatorLines())
                .anyMatch(line -> line.startsWith(FINISHED)));
        claim(
                "the counter over the groups reads one to six of six, one line as each is finished",
                () -> assertThat(progressOf(CLUSTERS))
                        .containsExactlyElementsOf(ProgressLines.expected(CLUSTERS, SIX_CLUSTERS)));
        theTreesCountersRan(root, SIX_CLUSTERS);
    }

    @Test
    @Story("Writing over the groups says how far it has got")
    @DisplayName("A group whose answer is turned down is counted, the fifth that stops the step included, and the tree is counted after the stop")
    void countsTurnedDownClustersAndStopsWhereTheStepStops(@TempDir Path root, @TempDir Path seeds)
            throws IOException {
        anApprovedArrangementOf(SIX_CLUSTERS, root, seeds);
        answersTurnedDownFor(List.of(0, 1, 2, 3, 4));
        logged.list.clear();

        cli.run("run", root.toString());

        claim(
                "the step stopped on the fifth answer turned down, which its own line and the invocation's"
                        + " failure say",
                () -> {
                    assertThat(cli.getExitCode()).isNotZero();
                    assertThat(operatorLines()).anyMatch(line -> line.startsWith(STOPPED));
                });
        claim(
                "the counter reads one to five of six: each group whose answer was turned down is counted, the"
                        + " fifth before the walk returns, and the sixth, never reached, is not",
                () -> assertThat(progressOf(CLUSTERS))
                        .containsExactlyElementsOf(ProgressLines.expected(CLUSTERS, SIX_CLUSTERS).subList(0, 5)));
        theTreesCountersRan(root, SIX_CLUSTERS);
    }

    /**
     * A cluster nothing could be sent for leaves the step unfinished (ADR-116), and is counted. The second
     * invocation over the same arrangement walks past the six it wrote, which are counted too, and finds the
     * third unsendable again.
     */
    @Test
    @Story("Writing over the groups says how far it has got")
    @DisplayName("A group nothing could be sent for and groups already written are counted, and the tree is counted when the step is left unfinished")
    void countsUnsendableAndAlreadyWrittenClustersWhenLeftUnfinished(@TempDir Path root, @TempDir Path seeds)
            throws IOException {
        anApprovedArrangementOf(SEVEN_CLUSTERS, THE_THIRD, root, seeds);
        logged.list.clear();

        cli.run("run", root.toString());

        claim(
                "the step left a group unwritten and so did not finish, which its own line says",
                () -> assertThat(operatorLines()).anyMatch(line -> line.startsWith(LEFT_UNWRITTEN)));
        claim(
                "the counter reads one to seven of seven, the group nothing could be sent for included",
                () -> assertThat(progressOf(CLUSTERS))
                        .containsExactlyElementsOf(ProgressLines.expected(CLUSTERS, SEVEN_CLUSTERS)));
        theTreesCountersRan(root, SEVEN_CLUSTERS);

        GenerationScriptedBeans.forgetScriptedAnswers();
        logged.list.clear();
        cli.run("run", root.toString());

        claim(
                "the second invocation made no call: six groups were written by the first and the third still"
                        + " has nothing to send",
                () -> assertThat(GenerationScriptedBeans.callsMade()).isZero());
        claim(
                "and its counter still reads one to seven of seven: a group walked past because it was already"
                        + " written is a group the step has been through",
                () -> assertThat(progressOf(CLUSTERS))
                        .containsExactlyElementsOf(ProgressLines.expected(CLUSTERS, SEVEN_CLUSTERS)));
    }

    @Test
    @Story("Writing over the groups says how far it has got")
    @DisplayName("A group the engine finds no room for is counted when the step goes past it")
    void countsAClusterTheEngineFindsNoRoomFor(@TempDir Path root, @TempDir Path seeds) throws IOException {
        anApprovedArrangementOf(SEVEN_CLUSTERS, root, seeds);
        answersTurnedDownFor(List.of(0, 1, 3, 4, 5, 6));
        GenerationScriptedBeans.answerFor(
                CLUSTER_NAMES.get(THE_THIRD), anOrdinaryAnswer().countedAt(COUNTED_TOO_LONG_AT_ANY_LENGTH));
        logged.list.clear();

        cli.run("run", root.toString());

        claim(
                "five answers were asked for, one for each turned down, and none for the third group, which the"
                        + " engine counted past the room",
                () -> assertThat(GenerationScriptedBeans.callsMade()).isEqualTo(THE_STREAK_THAT_STOPS_THE_STEP));
        claim(
                "the counter reads one to six of seven, the group the engine found no room for included, and the"
                        + " seventh, never reached, not",
                () -> assertThat(progressOf(CLUSTERS))
                        .containsExactlyElementsOf(ProgressLines.expected(CLUSTERS, SEVEN_CLUSTERS).subList(0, 6)));
        everyLineIsTheCounters(CLUSTERS);
    }

    @Test
    @Story("Writing over the groups says how far it has got")
    @DisplayName("A group no document of which fits the reading window is counted, and so is the next")
    void countsAClusterNothingFitsTheWindowFor(@TempDir Path root, @TempDir Path seeds) throws IOException {
        anApprovedArrangementOf(TWO_CLUSTERS, root, seeds);
        profileStore.save(ProfileFixture.profileFrom(profileStore.load())
                .generationContextWindow(A_WINDOW_WITH_ROOM_FOR_A_SINGLE_WORD, "set by this test")
                .build());
        logged.list.clear();

        cli.run("run", root.toString());

        claim(
                "no call was made, and the step said that the window leaves room for none of a group's documents",
                () -> {
                    assertThat(GenerationScriptedBeans.callsMade()).isZero();
                    assertThat(operatorLines()).anyMatch(line -> line.contains("leaves room for none of them"));
                });
        claim(
                "the counter reads one and two of two, both groups counted",
                () -> assertThat(progressOf(CLUSTERS))
                        .containsExactlyElementsOf(ProgressLines.expected(CLUSTERS, TWO_CLUSTERS)));
        everyLineIsTheCounters(CLUSTERS);
    }

    /**
     * The tree's six counters after any exit: the scores read and the survivors listed over the membership
     * under the scoring run, the pictures asked for over the distinct survivors, which are the same here,
     * the one partition, the {@code clusters} cluster files, and one membership entry with a document for
     * each cluster.
     */
    private void theTreesCountersRan(Path root, int clusters) {
        long membership = membershipBehind(theApprovedArrangement(root));
        claim(
                "the scores of the " + membership + " members under the scoring run are read, and the same "
                        + membership + " survivors are listed for the tree and asked for their pictures",
                () -> {
                    assertThat(progressOf(SCORES)).containsExactlyElementsOf(ProgressLines.expected(SCORES, membership));
                    assertThat(progressOf(LISTED)).containsExactlyElementsOf(ProgressLines.expected(LISTED, membership));
                    assertThat(progressOf(PICTURES))
                            .containsExactlyElementsOf(ProgressLines.expected(PICTURES, membership));
                });
        claim(
                "the one partition is written, its " + clusters + " cluster files, and one membership entry in each",
                () -> {
                    assertThat(progressOf(PARTITIONS))
                            .containsExactlyElementsOf(ProgressLines.expected(PARTITIONS, ONE_PARTITION));
                    assertThat(progressOf(FILES)).containsExactlyElementsOf(ProgressLines.expected(FILES, clusters));
                    assertThat(progressOf(ENTRIES))
                            .containsExactlyElementsOf(ProgressLines.expected(ENTRIES, (long) clusters * ONE_ENTRY_PER_CLUSTER));
                });
        everyLineIsTheCounters(CLUSTERS, SCORES, LISTED, PICTURES, PARTITIONS, FILES, ENTRIES);
    }

    private void everyLineIsTheCounters(String... counters) {
        for (String counter : counters) {
            claim(
                    "every line of " + counter + " was written by the progress counter itself",
                    () -> assertThat(ProgressLines.loggersOf(logged.list, counter))
                            .isNotEmpty()
                            .containsOnly(ProgressLines.theCountersLogger()));
        }
    }

    private List<String> progressOf(String label) {
        return ProgressLines.of(logged.list, label);
    }

    private List<String> operatorLines() {
        return logged.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
    }

    /** How many membership rows the scoring run behind {@code arrangement} holds: what 6b reads and lists. */
    private long membershipBehind(RunId arrangement) {
        Long rows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM document_cluster WHERE run_id ="
                        + " (SELECT upstream_run_id FROM run_upstream WHERE run_id = ?)",
                Long.class,
                arrangement.value());
        return rows == null ? 0 : rows;
    }

    /** Scripts each named cluster to be turned down, walking the four ways an answer can be. */
    private void answersTurnedDownFor(List<Integer> clusters) {
        for (int position = 0; position < clusters.size(); position++) {
            GenerationScriptedBeans.answerFor(
                    CLUSTER_NAMES.get(clusters.get(position)), oneOfTheFourWaysAnAnswerIsTurnedDown(position));
        }
    }

    private static ScriptedAnswer oneOfTheFourWaysAnAnswerIsTurnedDown(int position) {
        return switch (position % ALL_FOUR_WAYS_AN_ANSWER_IS_TURNED_DOWN) {
            case 0 -> anOrdinaryAnswer().havingRead(A_COUNT_PAST_THE_CEILING);
            case 1 -> anOrdinaryAnswer().stoppedForRoomAfter(THE_WHOLE_ANSWER_ALLOWANCE);
            case 2 -> ScriptedAnswer.arrivingAs(AN_ANSWER_NOTHING_CAN_READ);
            default -> ScriptedAnswer.saying(A_TITLE, PROSE_POINTING_AT_NOTHING);
        };
    }

    private static ScriptedAnswer anOrdinaryAnswer() {
        return ScriptedAnswer.saying(GenerationScriptedBeans.GENERATED_TITLE, GenerationScriptedBeans.GENERATED_PROSE);
    }

    private void anApprovedArrangementOf(int clusters, Path root, Path seeds) throws IOException {
        anApprovedArrangementOf(clusters, EVERY_CLUSTER_HOLDS_A_DOCUMENT, root, seeds);
    }

    /**
     * A corpus of {@code clusters} documents, walked once, arranged into {@code clusters} clusters of one
     * document each, with that arrangement approved; the cluster at {@code theClusterHoldingNothing}, unless
     * it is {@link #EVERY_CLUSTER_HOLDS_A_DOCUMENT}, holding a document nothing of can be sent.
     */
    private void anApprovedArrangementOf(int clusters, int theClusterHoldingNothing, Path root, Path seeds)
            throws IOException {
        for (int document = 1; document <= clusters; document++) {
            Files.writeString(root.resolve("corpus-" + document + ".txt"), "corpus document " + document);
        }
        Files.writeString(seeds.resolve("seed.txt"), "a seed document");
        Profile profile = profileStore.load();
        profileStore.save(ProfileFixture.profile()
                .seedFolder(seeds.toString(), "set by this test")
                .degenerateOutputConfidenceFloor(profile.degenerateOutputConfidenceFloor())
                .boilerplateDocumentFrequencyFloor(BOILERPLATE_FLOOR, "set by this test, so stage 4's gate is open")
                .embeddingModel(EMBEDDING_MODEL_NAME, "set by this test, so gate 3 is open")
                .generationContextWindow(THE_READING_WINDOW, "set by this test, so every call here reads in the same window")
                .build());
        cli.run("run", root.toString());
        profileStore.save(ProfileFixture.profileFrom(profileStore.load())
                .arrangementApproved(ArrangementGate.shortNameOf(theLatestArrangement(root)), "read by this test")
                .build());
        oneClusterPerDocument(theApprovedArrangement(root), clusters, theClusterHoldingNothing, root);
    }

    /**
     * Replaces the arranged clusters with {@code clusters} clusters of one document each, in the order they
     * will be written over, as {@code GenerationBreakerInvocationTest} does and for the reasons its javadoc
     * gives: every document under the run is first moved to an ordinal no cluster is arranged at, and only
     * then are this walk's documents placed one per cluster, each under the cluster rows' winning seed. The
     * cluster at {@code theClusterHoldingNothing} has its document rewritten in place with the walk none the
     * wiser, so the step finds nothing cached to send for it.
     */
    private void oneClusterPerDocument(RunId arrangement, int clusters, int theClusterHoldingNothing, Path root)
            throws IOException {
        String scoring = jdbcTemplate.queryForObject(
                "SELECT upstream_run_id FROM run_upstream WHERE run_id = ?", String.class, arrangement.value());
        List<Long> documents = jdbcTemplate.queryForList(
                "SELECT dc.occurrence_id FROM document_cluster dc"
                        + " JOIN file_occurrence fo ON fo.id = dc.occurrence_id"
                        + " JOIN walk w ON w.id = fo.walk_id"
                        + " WHERE dc.run_id = ? AND w.root = ? ORDER BY dc.occurrence_id",
                Long.class,
                scoring,
                Walk.canonicalRoot(root).toString());
        if (documents.size() < clusters) {
            throw new IllegalStateException("this fixture needs " + clusters + " documents in the arrangement to"
                    + " make " + clusters + " groups, and this walk produced " + documents.size());
        }
        Long winningSeed = jdbcTemplate.queryForObject(
                "SELECT winning_seed_occurrence_id FROM document_cluster WHERE run_id = ? AND occurrence_id = ?",
                Long.class,
                scoring,
                documents.getFirst());
        jdbcTemplate.update("UPDATE document_cluster SET cluster_ordinal = ? WHERE run_id = ?", clusters, scoring);
        for (int cluster = 0; cluster < clusters; cluster++) {
            jdbcTemplate.update(
                    "UPDATE document_cluster SET cluster_ordinal = ?, winning_seed_occurrence_id = ?"
                            + " WHERE run_id = ? AND occurrence_id = ?",
                    cluster,
                    winningSeed,
                    scoring,
                    documents.get(cluster));
        }
        jdbcTemplate.update("DELETE FROM cluster WHERE run_id = ?", arrangement.value());
        for (int cluster = 0; cluster < clusters; cluster++) {
            jdbcTemplate.update(
                    "INSERT INTO cluster (run_id, winning_seed_occurrence_id, cluster_ordinal, label,"
                            + " document_count, partition_order, cluster_order) VALUES (?, ?, ?, ?, ?, 0, ?)",
                    arrangement.value(),
                    winningSeed,
                    cluster,
                    CLUSTER_NAMES.get(cluster),
                    1,
                    cluster);
        }
        if (theClusterHoldingNothing != EVERY_CLUSTER_HOLDS_A_DOCUMENT) {
            UnseenEditFixture.editedWithoutTheWalkNoticing(root.resolve(jdbcTemplate.queryForObject(
                    "SELECT path FROM file_occurrence WHERE id = ?",
                    String.class,
                    documents.get(theClusterHoldingNothing))));
        }
    }

    private RunId theLatestArrangement(Path root) {
        return new RunId(jdbcTemplate.queryForObject(
                "SELECT r.id FROM run r JOIN walk w ON w.id = r.walk_id"
                        + " WHERE r.stage = ? AND w.root = ? ORDER BY r.rowid DESC LIMIT 1",
                String.class,
                "arrangement",
                Walk.canonicalRoot(root).toString()));
    }

    private RunId theApprovedArrangement(Path root) {
        return new RunId(jdbcTemplate.queryForObject(
                "SELECT r.id FROM run r JOIN walk w ON w.id = r.walk_id"
                        + " WHERE r.stage = ? AND w.root = ? AND r.id LIKE ? ORDER BY r.rowid LIMIT 1",
                String.class,
                "arrangement",
                Walk.canonicalRoot(root).toString(),
                profileStore.load().arrangementApproved().value() + "%"));
    }
}
