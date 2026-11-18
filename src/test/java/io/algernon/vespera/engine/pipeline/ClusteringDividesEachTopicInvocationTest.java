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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * The clustering step clusters each topic's placed survivors, and divides a topic of at least the subtopic floor's
 * survivors into subtopics where they form two communities or more, its clusters then found and numbered within each
 * (ADR-406 §1, §2; ADR-409 §3, §8). Each survivor's row names its topic's key, its subtopic, or -1 where the topic has
 * none, and its cluster.
 *
 * <p>One seed of one passage gives one topic, keyed by {@code seed.txt} and its first chunk, 0, and every document is
 * placed under it. Every vector of the fixture is the same, and at this machine's resolution of {@value
 * #A_FINER_RESOLUTION} three such documents form three communities, one each, as {@code TheClusteringPresetsInvocationTest}
 * measured them through {@code Communities.of}; this machine's subtopic floor is {@value #THE_FLOOR}.
 */
@CascadeSliceTest
@Import(SeedScriptedExtractionBeans.class)
@Epic("Relevance")
@Feature("Clustering")
@Issue("832")
@Link(name = "ADR-409", url = Adr.TOPICS_AND_ALL_ARRANGED_UNDER_THEM_ARE_KEPT_IN_TABLES_KEYED_BY_THE_TOPIC, type = "adr")
@Link(name = "ADR-406", url = Adr.A_CROWDED_TOPIC_IS_DIVIDED_INTO_SUBTOPICS, type = "adr")
class ClusteringDividesEachTopicInvocationTest {

    /** This machine's subtopic floor: a topic of three survivors is crowded, one of two is not. */
    private static final int THE_FLOOR = 3;

    /** A resolution at which documents of one vector form a community each. */
    private static final double A_FINER_RESOLUTION = 2.5;

    /** What a row's subtopic is where its topic has none. */
    private static final int NO_SUBTOPIC = -1;

    /** The one topic's key, its first passage's seed path and chunk index. */
    private static final String THE_TOPIC = "seed.txt 0";

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

        claim("the invocation ended well", () -> assertThat(cli.getExitCode()).isZero());
        claim("each of the " + THE_FLOOR + " documents is kept as a member of the one topic, in subtopics 0, 1 and 2,"
                        + " one each, and in cluster 0 of its subtopic, the clusters being numbered within a subtopic",
                () -> assertThat(membersOf(root)).containsExactlyInAnyOrder(
                        THE_TOPIC + " subtopic 0 cluster 0",
                        THE_TOPIC + " subtopic 1 cluster 0",
                        THE_TOPIC + " subtopic 2 cluster 0"));
    }

    @Test
    @Story("A crowded topic gets one level more, and no other does")
    @DisplayName("A topic of fewer documents than the floor has no subtopic")
    void aTopicUnderTheFloorHasNoSubtopic(@TempDir Path root, @TempDir Path seeds) throws IOException {
        int underTheFloor = THE_FLOOR - 1;
        aCorpusOf(underTheFloor, root, seeds);

        cli.run("run", root.toString());

        claim("the invocation ended well", () -> assertThat(cli.getExitCode()).isZero());
        claim("each of the " + underTheFloor + " documents is kept as a member of the one topic with the subtopic that"
                        + " says it has none, " + NO_SUBTOPIC,
                () -> assertThat(membersOf(root))
                        .hasSize(underTheFloor)
                        .allSatisfy(member -> assertThat(member).startsWith(THE_TOPIC + " subtopic " + NO_SUBTOPIC + " ")));
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
}
