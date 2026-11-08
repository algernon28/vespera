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
 * Stage 5's fifth step, end to end (ADR-087, #109): once every survivor carries a score and a winning
 * seed, each seed's partition is divided into clusters under that same scoring run — rows, a size
 * report, and nothing else.
 *
 * <p>A sibling of {@link RelevanceScoringInvocationTest}: this ticket's own step reads what the step
 * before it wrote rather than re-deriving it.
 *
 * <p><b>What this step must be shown not to do matters as much as what it does.</b> Clustering
 * arranges what survived, so no verdict may appear because of it, and its two parameters are code
 * defaults — a profile that gained a key here would be a question shipped unset that nobody could
 * answer, since cluster granularity is only discoverable from output that does not exist yet.
 */
@CascadeSliceTest
@Import(SeedScriptedExtractionBeans.class)
@Epic("Relevance")
@Feature("Clustering")
@Issue("109")
@Link(name = "ADR-087", url = Adr.CLUSTERS_ARE_MODULARITY_COMMUNITIES, type = "adr")
class ClusteringInvocationTest {

    /** How many corpus documents this fixture walks, all of which survive to be clustered. */
    private static final int CORPUS_DOCUMENTS = 3;

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

    @Test
    @Story("Every survivor lands in exactly one cluster")
    @DisplayName("Every scored survivor is clustered under gate 3's own scoring run, with no verdict")
    void clustersEverySurvivorUnderTheScoringRun(@TempDir Path root, @TempDir Path seeds) throws IOException {
        for (int i = 0; i < CORPUS_DOCUMENTS; i++) {
            Files.writeString(root.resolve("corpus-" + i + ".txt"), "a corpus document " + i);
        }
        Files.writeString(seeds.resolve("seed.txt"), "a seed document");
        profile(seeds);

        cli.run("run", root.toString());

        claim(
                "the invocation reports success",
                () -> assertThat(cli.getExitCode()).isZero());
        claim(
                "each of the " + CORPUS_DOCUMENTS + " scored survivors carries exactly one membership row,"
                        + " so membership over the partition is total and disjoint through the real"
                        + " pipeline and not only at the unit",
                () -> assertThat(documentClusterRowCountFor(root)).isEqualTo(CORPUS_DOCUMENTS));
        claim(
                "recorded under the very run the scores were written beneath -- clustering needs the"
                        + " vectors and the vectors need the model, so it belongs to the scoring run"
                        + " rather than to one of its own",
                () -> assertThat(documentClusterRunIdsFor(root)).containsOnly(scoringRunIdFor(root)));
        claim(
                "no verdict of any kind was written: clustering removes nothing, it arranges what"
                        + " survived, and the verdict vocabulary is closed (ADR-042) with nothing in it"
                        + " for this",
                () -> assertThat(verdictCountFor(root)).isZero());
    }

    /** The seed folder named, stage 4's gate open, and gate 3 open too. */
    private void profile(Path seeds) {
        EveryGateOpen.throughStageFive(profileStore, seeds);
    }

    /**
     * The spelling the walk recorded for {@code root}, which is the walk's own canonicalisation of it
     * (ADR-055) rather than the path this test handed the CLI.
     */
    private String walkRoot(Path root) {
        return Walk.canonicalRoot(root).toString();
    }

    /**
     * Every query below joins back to {@code root}'s own walk rather than reading the tables whole:
     * this database is shared across every {@code @Test} method in the class, so an absolute count
     * would silently read another test's rows too.
     */
    private long documentClusterRowCountFor(Path root) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM document_cluster c JOIN file_occurrence f ON f.id = c.occurrence_id"
                        + " JOIN walk w ON w.id = f.walk_id WHERE w.root = ?",
                Long.class,
                walkRoot(root));
        return count == null ? 0 : count;
    }

    private List<String> documentClusterRunIdsFor(Path root) {
        return jdbcTemplate.queryForList(
                "SELECT c.run_id FROM document_cluster c JOIN file_occurrence f ON f.id = c.occurrence_id"
                        + " JOIN walk w ON w.id = f.walk_id WHERE w.root = ?",
                String.class,
                walkRoot(root));
    }

    private String scoringRunIdFor(Path root) {
        return jdbcTemplate.queryForObject(
                "SELECT run.id FROM run JOIN walk w ON w.id = run.walk_id"
                        + " WHERE run.stage = 'embedding-scoring' AND w.root = ?",
                String.class,
                walkRoot(root));
    }

    private long verdictCountFor(Path root) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM verdict v JOIN file_occurrence f ON f.id = v.occurrence_id"
                        + " JOIN walk w ON w.id = f.walk_id WHERE w.root = ?",
                Long.class,
                walkRoot(root));
        return count == null ? 0 : count;
    }
}
