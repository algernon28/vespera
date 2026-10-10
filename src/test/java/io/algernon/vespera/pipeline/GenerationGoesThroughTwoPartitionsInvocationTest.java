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
 * Stage 6b over two seed partitions, through a whole invocation (ADR-223 sections 5 and 6): its walk reads a
 * partition's clusters when it comes to that partition and not before, reads a partition's members only where
 * a cluster of it is still to be written, and so reads nothing of a partition it never reaches; the tree then
 * goes through both partitions in turn.
 *
 * <p><b>Why an invocation, and why two partitions.</b> What the walk reads, and when, is the built tasklet's:
 * it hands {@code synthesis} the clusters as something to go through, and reads each partition's as the walk
 * asks for the first of them. Every other test of stage 6b has one partition, where reading a partition on
 * arrival and reading every partition up front are the same lines in the same order. Here they are not, and
 * the order of the step's timed lines tells them apart.
 *
 * <p><b>The partitions are written straight into the arrangement</b>, as {@code
 * GenerationReportsItsProgressInvocationTest} writes its clusters and for its reason: every document this
 * fixture converts comes back alike and there is one seed. Seven documents are placed one to a cluster, five
 * clusters under the seed that won them, placed first, and two under a second seed, placed second. The second
 * seed is the corpus's first document: stage 6b asks a seed for nothing but its path.
 *
 * <p>Report text says <em>group</em> and <em>exemplar</em> where these names say cluster and seed (ADR-122).
 */
@CascadeSliceTest
@Import(SeedScriptedExtractionBeans.class)
@Epic("Synthesis")
@Feature("Writing over the groups")
@Issue("472")
@Link(name = "ADR-223", url = Adr.THE_LAST_THREE_STAGES_GO_THROUGH_ONE_SEED_PARTITION_AT_A_TIME, type = "adr")
@Link(name = "ADR-190", url = Adr.STAGE_6_RULES_LIVE_IN_SYNTHESIS, type = "adr")
class GenerationGoesThroughTwoPartitionsInvocationTest {

    private static final String BOILERPLATE_FLOOR = "1.0";
    private static final String EMBEDDING_MODEL_NAME = "qwen3-embedding:0.6b";
    private static final String THE_READING_WINDOW = "4096";
    private static final int THE_WINDOW = 4096;
    private static final int A_COUNT_PAST_THE_CEILING = THE_WINDOW + 904;
    private static final int THE_WHOLE_ANSWER_ALLOWANCE = 1024;
    private static final String AN_ANSWER_NOTHING_CAN_READ = "{\"title\":\"Site Safety Audits\",\"prose\":";
    private static final String A_TITLE = "What The Stubbed Document Says";
    private static final String PROSE_POINTING_AT_NOTHING = "The audit [7] states every finding.";

    /** The seven clusters by label, which is what an answer is scripted against: five, then two. */
    private static final List<String> CLUSTER_NAMES = List.of(
            "Group one", "Group two", "Group three", "Group four", "Group five", "Group six", "Group seven");

    /** The clusters of the first partition: enough for five answers turned down in a row to stop the walk in it. */
    private static final int IN_THE_FIRST_PARTITION = 5;

    private static final int SEVEN_CLUSTERS = 7;

    /** The first cluster of the second partition, counting from zero across both. */
    private static final int THE_SIXTH = 5;

    /** An ordinal no cluster is arranged at, where every document waits before it is placed. */
    private static final int NO_CLUSTERS_ORDINAL = 99;

    private static final String STAGE_SIX_B = "Stage 6b (generation)";
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
    @Story("Writing over the groups goes through one exemplar at a time")
    @DisplayName("The second exemplar's groups are read once the first exemplar's are all gone through, and a second invocation reads no document of an exemplar whose groups are all written")
    void readsAPartitionOnArrivalAndNoMemberOfOneAlreadyWritten(@TempDir Path root, @TempDir Path seeds)
            throws IOException {
        twoApprovedPartitions(root, seeds);
        GenerationScriptedBeans.answerFor(CLUSTER_NAMES.get(THE_SIXTH), ScriptedAnswer.arrivingAs(AN_ANSWER_NOTHING_CAN_READ));
        long membership = membershipBehind(theApprovedArrangement(root));
        logged.list.clear();

        cli.run("run", root.toString());

        claim(
                "the first invocation wrote six groups and left the first group of the second exemplar"
                        + " unwritten, which its own line says",
                () -> {
                    assertThat(operatorLines()).anyMatch(line -> line.startsWith(LEFT_UNWRITTEN));
                    assertThat(GenerationScriptedBeans.callsMade()).isEqualTo(SEVEN_CLUSTERS);
                });
        claim(
                "it read the exemplars of the arrangement, then the first exemplar's groups and its documents,"
                        + " and only after those the second exemplar's groups and its documents: an exemplar's"
                        + " groups are read when the writing comes to it. Then the faults still standing, and"
                        + " for the final documents every arranged document for its pictures, each exemplar's"
                        + " groups and documents in turn, and every arranged document for the listing",
                () -> assertThat(StatementLines.of(operatorLines(), STAGE_SIX_B))
                        .containsExactlyElementsOf(StatementLines.inOrder(
                                StatementLines.timedRead(STAGE_SIX_B, "the seed partitions of the arrangement"),
                                StatementLines.timedRead(STAGE_SIX_B, "the recorded clusters of partition 1 of 2"),
                                StatementLines.timedRead(STAGE_SIX_B, "the members of partition 1 of 2"),
                                StatementLines.timedRead(STAGE_SIX_B, "the recorded clusters of partition 2 of 2"),
                                StatementLines.timedRead(STAGE_SIX_B, "the members of partition 2 of 2"),
                                StatementLines.timedRead(STAGE_SIX_B, "the standing faults"),
                                theTreesReads(membership))));

        GenerationScriptedBeans.forgetScriptedAnswers();
        logged.list.clear();
        cli.run("run", root.toString());

        claim(
                "the second invocation asked for the one answer that was missing and finished",
                () -> {
                    assertThat(GenerationScriptedBeans.callsMade()).isEqualTo(1);
                    assertThat(operatorLines()).anyMatch(line -> line.startsWith(FINISHED));
                });
        claim(
                "it read the first exemplar's groups, found each written, and read none of that exemplar's"
                        + " documents before going on to the second exemplar, whose documents it did read, for"
                        + " the one group still to write. The first exemplar's documents are read once, later,"
                        + " for the final documents",
                () -> assertThat(StatementLines.of(operatorLines(), STAGE_SIX_B))
                        .containsExactlyElementsOf(StatementLines.inOrder(
                                StatementLines.timedRead(STAGE_SIX_B, "the seed partitions of the arrangement"),
                                StatementLines.timedRead(STAGE_SIX_B, "the recorded clusters of partition 1 of 2"),
                                StatementLines.timedRead(STAGE_SIX_B, "the recorded clusters of partition 2 of 2"),
                                StatementLines.timedRead(STAGE_SIX_B, "the members of partition 2 of 2"),
                                StatementLines.timedRead(STAGE_SIX_B, "the standing faults"),
                                theTreesReads(membership))));
    }

    @Test
    @Story("Writing over the groups goes through one exemplar at a time")
    @DisplayName("When the writing stops inside the first exemplar, nothing of the second exemplar is read until the final documents are written")
    void aWalkThatStopsInTheFirstPartitionReadsNothingOfTheSecond(@TempDir Path root, @TempDir Path seeds)
            throws IOException {
        twoApprovedPartitions(root, seeds);
        for (int cluster = 0; cluster < IN_THE_FIRST_PARTITION; cluster++) {
            GenerationScriptedBeans.answerFor(CLUSTER_NAMES.get(cluster), oneOfTheFourWaysAnAnswerIsTurnedDown(cluster));
        }
        long membership = membershipBehind(theApprovedArrangement(root));
        logged.list.clear();

        cli.run("run", root.toString());

        claim(
                "the step stopped on the fifth answer turned down, all five under the first exemplar, having"
                        + " asked for five answers and no more",
                () -> {
                    assertThat(cli.getExitCode()).isNotZero();
                    assertThat(operatorLines()).anyMatch(line -> line.startsWith(STOPPED));
                    assertThat(GenerationScriptedBeans.callsMade()).isEqualTo(IN_THE_FIRST_PARTITION);
                });
        claim(
                "while it was writing it read the first exemplar's groups and documents and nothing of the"
                        + " second exemplar's: the groups of an exemplar the writing never came to are not read"
                        + " for it. They are read once, afterwards, with that exemplar's documents, where the"
                        + " final documents are written, and a stop reads no fault still standing",
                () -> assertThat(StatementLines.of(operatorLines(), STAGE_SIX_B))
                        .containsExactlyElementsOf(StatementLines.inOrder(
                                StatementLines.timedRead(STAGE_SIX_B, "the seed partitions of the arrangement"),
                                StatementLines.timedRead(STAGE_SIX_B, "the recorded clusters of partition 1 of 2"),
                                StatementLines.timedRead(STAGE_SIX_B, "the members of partition 1 of 2"),
                                theTreesReads(membership))));
    }

    /**
     * What writing the tree reads, over two partitions: every arranged occurrence a page at a time for the
     * pictures, each partition's clusters and then its members, and every arranged occurrence again for the
     * manifest (ADR-223 section 8, L9, L11 and L12).
     */
    private static List<String> theTreesReads(long membership) {
        return StatementLines.inOrder(
                StatementLines.countedRead(STAGE_SIX_B, "the arranged occurrences, for their pictures", membership),
                StatementLines.timedRead(STAGE_SIX_B, "the recorded clusters of partition 1 of 2"),
                StatementLines.timedRead(STAGE_SIX_B, "the members of partition 1 of 2"),
                StatementLines.timedRead(STAGE_SIX_B, "the recorded clusters of partition 2 of 2"),
                StatementLines.timedRead(STAGE_SIX_B, "the members of partition 2 of 2"),
                StatementLines.countedRead(STAGE_SIX_B, "the arranged occurrences, for the manifest", membership));
    }

    private List<String> operatorLines() {
        return logged.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
    }

    private long membershipBehind(RunId arrangement) {
        Long rows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM document_cluster WHERE run_id ="
                        + " (SELECT upstream_run_id FROM run_upstream WHERE run_id = ?)",
                Long.class,
                arrangement.value());
        return rows == null ? 0 : rows;
    }

    private static ScriptedAnswer oneOfTheFourWaysAnAnswerIsTurnedDown(int position) {
        return switch (position % 4) {
            case 0 -> anOrdinaryAnswer().havingRead(A_COUNT_PAST_THE_CEILING);
            case 1 -> anOrdinaryAnswer().stoppedForRoomAfter(THE_WHOLE_ANSWER_ALLOWANCE);
            case 2 -> ScriptedAnswer.arrivingAs(AN_ANSWER_NOTHING_CAN_READ);
            default -> ScriptedAnswer.saying(A_TITLE, PROSE_POINTING_AT_NOTHING);
        };
    }

    private static ScriptedAnswer anOrdinaryAnswer() {
        return ScriptedAnswer.saying(GenerationScriptedBeans.GENERATED_TITLE, GenerationScriptedBeans.GENERATED_PROSE);
    }

    /**
     * A corpus of seven documents, walked once and arranged, with that arrangement approved and then replaced
     * by seven clusters of one document each in two partitions: five under the seed that won the documents,
     * placed first, and two under the corpus's first document as a second seed, placed second.
     */
    private void twoApprovedPartitions(Path root, Path seeds) throws IOException {
        for (int document = 1; document <= SEVEN_CLUSTERS; document++) {
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

        RunId arrangement = theApprovedArrangement(root);
        String scoring = jdbcTemplate.queryForObject(
                "SELECT upstream_run_id FROM run_upstream WHERE run_id = ?", String.class, arrangement.value());
        List<Long> documents = jdbcTemplate.queryForList(
                "SELECT occurrence_id FROM document_cluster WHERE run_id = ? ORDER BY occurrence_id", Long.class, scoring);
        if (documents.size() != SEVEN_CLUSTERS) {
            throw new IllegalStateException("this fixture needs exactly " + SEVEN_CLUSTERS + " documents grouped"
                    + " under the scoring run, one for each group, and this walk produced " + documents.size());
        }
        Long firstSeed = jdbcTemplate.queryForObject(
                "SELECT winning_seed_occurrence_id FROM document_cluster WHERE run_id = ? AND occurrence_id = ?",
                Long.class,
                scoring,
                documents.getFirst());
        Long secondSeed = documents.getFirst();
        jdbcTemplate.update("UPDATE document_cluster SET cluster_ordinal = ? WHERE run_id = ?", NO_CLUSTERS_ORDINAL, scoring);
        jdbcTemplate.update("DELETE FROM cluster WHERE run_id = ?", arrangement.value());
        for (int cluster = 0; cluster < SEVEN_CLUSTERS; cluster++) {
            boolean inTheFirst = cluster < IN_THE_FIRST_PARTITION;
            Long seed = inTheFirst ? firstSeed : secondSeed;
            int ordinal = inTheFirst ? cluster : cluster - IN_THE_FIRST_PARTITION;
            jdbcTemplate.update(
                    "UPDATE document_cluster SET cluster_ordinal = ?, winning_seed_occurrence_id = ?"
                            + " WHERE run_id = ? AND occurrence_id = ?",
                    ordinal,
                    seed,
                    scoring,
                    documents.get(cluster));
            // The score names the winning seed too, and stage 6a, which writes its page again on every
            // invocation, learns which seeds have members from the scores.
            jdbcTemplate.update(
                    "UPDATE relevance_score SET winning_seed_occurrence_id = ? WHERE run_id = ? AND occurrence_id = ?",
                    seed,
                    scoring,
                    documents.get(cluster));
            jdbcTemplate.update(
                    "INSERT INTO cluster (run_id, winning_seed_occurrence_id, cluster_ordinal, label,"
                            + " document_count, partition_order, cluster_order) VALUES (?, ?, ?, ?, 1, ?, ?)",
                    arrangement.value(),
                    seed,
                    ordinal,
                    CLUSTER_NAMES.get(cluster),
                    inTheFirst ? 1 : 2,
                    ordinal + 1);
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
