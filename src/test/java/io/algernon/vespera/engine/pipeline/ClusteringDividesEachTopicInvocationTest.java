package io.algernon.vespera.engine.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.engine.corpus.Walk;
import io.algernon.vespera.engine.profile.ProfileStore;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The clustering step clusters each topic's placed survivors, and divides a topic of at least the subtopic floor's
 * survivors into subtopics where they form two communities or more, its clusters then found and numbered within each
 * (ADR-406 §1, §2; ADR-409 §3, §8). Each survivor's row names its topic's key, its subtopic, or -1 where the topic has
 * none, and its cluster. {@code cluster-sizes.json} has the topic's row and, under a divided topic, a row for each
 * subtopic, numbered by its place in ADR-406 §4's order counted from 1, each with its share of the documents grouped
 * (ADR-416 §5, §6). The three subtopics here hold a document each, so that order is the order of their recorded
 * numbers, and the place of each is its recorded number and one; {@code TheClusterSizesPageHasARowForEachSubtopicTest}
 * holds that the page numbers subtopics by their place and is handed no recorded number.
 *
 * <p>One seed of one passage gives one topic, keyed by {@code seed.txt} and its first chunk, 0, and every document is
 * placed under it. Every vector of the fixture is the same, and at this machine's resolution of {@value
 * #A_FINER_RESOLUTION} three such documents form three communities, one each, as {@code TheClusteringPresetsInvocationTest}
 * measured them through {@code Communities.of}; this machine's subtopic floor is {@value #THE_FLOOR}. The report is
 * read from the class's working directory as soon as the invocation that wrote it ends.
 */
@CascadeSliceTest
@Import(SeedScriptedExtractionBeans.class)
@Epic("Relevance")
@Feature("Clustering")
@Issue("832")
@Issue("834")
@Link(name = "ADR-409", url = Adr.TOPICS_AND_ALL_ARRANGED_UNDER_THEM_ARE_KEPT_IN_TABLES_KEYED_BY_THE_TOPIC, type = "adr")
@Link(name = "ADR-406", url = Adr.A_CROWDED_TOPIC_IS_DIVIDED_INTO_SUBTOPICS, type = "adr")
@Link(name = "ADR-416", url = Adr.THE_TOPICS_ANALYSIS_REPORT_LISTS_WHAT_FEEDS_EACH_TOPIC, type = "adr")
class ClusteringDividesEachTopicInvocationTest {

    /** This machine's subtopic floor: a topic of three survivors is crowded, one of two is not. */
    private static final int THE_FLOOR = 3;

    /** A resolution at which documents of one vector form a community each. */
    private static final double A_FINER_RESOLUTION = 2.5;

    /** What a row's subtopic is where its topic has none. */
    private static final int NO_SUBTOPIC = -1;

    /** The one topic's key, its first passage's seed path and chunk index. */
    private static final String THE_TOPIC = "seed.txt 0";

    /** The one topic's key as the group sizes page writes it. */
    private static final String THE_TOPICS_ROW_KEY = "seed.txt chunk 0";

    /** How alike two documents of one vector are, as the page shows it to two places. */
    private static final double ALIKE = 1.0;

    /** A third of the documents, to three places. */
    private static final double A_THIRD = 0.333;

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @TempDir
    static Path workingDirectory;

    @DynamicPropertySource
    static void thisMachinesValues(DynamicPropertyRegistry registry) {
        registry.add("vespera.working-dir", workingDirectory::toString);
        registry.add("vespera.clustering.subtopic-floor", () -> String.valueOf(THE_FLOOR));
        registry.add("vespera.clustering.resolution", () -> String.valueOf(A_FINER_RESOLUTION));
    }

    @Autowired
    private VesperaCli cli;

    @Autowired
    private ProfileStore profileStore;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @Story("A crowded topic gets one level more, and no other does")
    @DisplayName("A topic of as many documents as the floor is divided into subtopics, and its groups are numbered within each")
    void aTopicAtTheFloorIsDividedAndItsClustersAreNumberedWithinEachSubtopic(@TempDir Path root, @TempDir Path seeds)
            throws IOException {
        aCorpusOf(THE_FLOOR, root, seeds);

        cli.run("run", root.toString());
        List<List<Object>> sizes = theClusterSizesRows();

        claim("the invocation ended well", () -> assertThat(cli.getExitCode()).isZero());
        claim("each of the " + THE_FLOOR + " documents is kept as a member of the one topic, in subtopics 0, 1 and 2,"
                        + " one each, and in cluster 0 of its subtopic, the clusters being numbered within a subtopic",
                () -> assertThat(membersOf(root)).containsExactlyInAnyOrder(
                        THE_TOPIC + " subtopic 0 cluster 0",
                        THE_TOPIC + " subtopic 1 cluster 0",
                        THE_TOPIC + " subtopic 2 cluster 0"));
        claim("the group sizes have the topic's row, its " + THE_FLOOR + " documents all of those grouped, in "
                        + THE_FLOOR + " groups of one, its links those of its whole graph, the documents being alike;"
                        + " then a row for each of its three subtopics, numbered 1, 2 and 3 by their place in the"
                        + " topic's order of subtopics, as every other view numbers them, and not 0, 1 and 2 as the"
                        + " division recorded them; one document and a third each, with no link, a subtopic of one"
                        + " document having no pair",
                () -> assertThat(sizes).containsExactly(
                        Arrays.asList(THE_TOPICS_ROW_KEY, null, 3.0, 1.0, 3.0, 1.0, 1.0, 3.0, ALIKE, ALIKE, ALIKE),
                        Arrays.asList(THE_TOPICS_ROW_KEY, 1.0, 1.0, A_THIRD, 1.0, 1.0, 1.0, 1.0, null, null, null),
                        Arrays.asList(THE_TOPICS_ROW_KEY, 2.0, 1.0, A_THIRD, 1.0, 1.0, 1.0, 1.0, null, null, null),
                        Arrays.asList(THE_TOPICS_ROW_KEY, 3.0, 1.0, A_THIRD, 1.0, 1.0, 1.0, 1.0, null, null, null)));
    }

    @Test
    @Story("A crowded topic gets one level more, and no other does")
    @DisplayName("A topic of fewer documents than the floor has no subtopic")
    void aTopicUnderTheFloorHasNoSubtopic(@TempDir Path root, @TempDir Path seeds) throws IOException {
        int underTheFloor = THE_FLOOR - 1;
        aCorpusOf(underTheFloor, root, seeds);

        cli.run("run", root.toString());
        List<List<Object>> sizes = theClusterSizesRows();

        claim("the invocation ended well", () -> assertThat(cli.getExitCode()).isZero());
        claim("each of the " + underTheFloor + " documents is kept as a member of the one topic with the subtopic that"
                        + " says it has none, " + NO_SUBTOPIC,
                () -> assertThat(membersOf(root))
                        .hasSize(underTheFloor)
                        .allSatisfy(member -> assertThat(member).startsWith(THE_TOPIC + " subtopic " + NO_SUBTOPIC + " ")));
        claim("and the group sizes have the topic's row alone, its subtopic empty, holding the " + underTheFloor
                        + " documents, all of those grouped",
                () -> assertThat(sizes).singleElement().satisfies(row -> assertThat(row.subList(0, 4))
                        .containsExactly(THE_TOPICS_ROW_KEY, null, (double) underTheFloor, 1.0)));
    }

    /** {@code documents} corpus documents and one seed of one passage, every gate up to the approval open. */
    private void aCorpusOf(int documents, Path root, Path seeds) throws IOException {
        for (int document = 0; document < documents; document++) {
            Files.writeString(root.resolve("corpus-" + document + ".txt"), "a corpus document, the " + document + "th");
        }
        Files.writeString(seeds.resolve("seed.txt"), "a seed document");
        EveryGateOpen.throughStageFive(profileStore, seeds);
    }

    /** Each membership row of {@code root}'s walk: the topic's key, the subtopic and the cluster. */
    private List<String> membersOf(Path root) {
        return jdbcTemplate.queryForList(
                "SELECT m.topic_seed_path || ' ' || m.topic_chunk_index || ' subtopic ' || m.subtopic_ordinal"
                        + " || ' cluster ' || m.cluster_ordinal"
                        + " FROM cluster_member m JOIN file_occurrence f ON f.id = m.occurrence_id"
                        + " JOIN walk w ON w.id = f.walk_id WHERE w.root = ?",
                String.class,
                Walk.canonicalRoot(root).toString());
    }

    /** The rows of {@code cluster-sizes.json}'s first part, as the last invocation of the class wrote them. */
    private static List<List<Object>> theClusterSizesRows() throws IOException {
        JsonNode json = JSON.readTree(
                Files.readString(workingDirectory.resolve("cluster-sizes.json"), StandardCharsets.UTF_8));
        return AnalysisReportRows.of(json.get("sections").get(0));
    }
}
