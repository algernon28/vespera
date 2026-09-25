package io.algernon.vespera.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.corpus.Walk;
import io.algernon.vespera.profile.Profile;
import io.algernon.vespera.profile.ProfileFixture;
import io.algernon.vespera.profile.ProfileStore;
import io.algernon.vespera.profile.ProfileValue;
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
 * <p>A sibling of {@link RelevanceScoringInvocationTest} for the reason that class is a sibling of
 * {@link EmbeddingScoringInvocationTest}: this ticket's own step reads what the step before it wrote
 * rather than re-deriving it.
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

    /** A floor of 1.0 opens stage 4's gate the same way {@link RelevanceScoringInvocationTest} does. */
    private static final String BOILERPLATE_FLOOR = "1.0";

    /** The model this fixture names, so gate 3 -- and this ticket's own step -- open too. */
    private static final String MODEL_NAME = "qwen3-embedding:0.6b";

    /** How many corpus documents this fixture walks, all of which survive to be clustered. */
    private static final int CORPUS_DOCUMENTS = 3;

    /**
     * Every key the profile is allowed to carry after this step has run — the same set it had before,
     * which is the claim. The list grows when a <em>later</em> stage adds a key of its own, as the
     * arrangement's approval did (ADR-107, #175); it must never grow because of this step.
     */
    private static final List<String> THE_KNOWN_PROFILE_KEYS = List.of(
            "seedFolder",
            "degenerateOutputConfidenceFloor",
            "boilerplateDocumentFrequencyFloor",
            "embeddingModel",
            "arrangementApproved",
            "relevanceScoreFloor",
            "generationModel",
            "generationContextWindow");

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

    @Test
    @Story("The parameters are code defaults, never profile keys")
    @DisplayName("Clustering adds no profile key, so nothing new ships unset")
    void addsNoProfileKey(@TempDir Path root, @TempDir Path seeds) throws IOException {
        Files.writeString(root.resolve("corpus.txt"), "a corpus document");
        Files.writeString(seeds.resolve("seed.txt"), "a seed document");
        profile(seeds);

        cli.run("run", root.toString());

        claim(
                "clustering contributes none of the keys the profile carries: k and the"
                        + " resolution are operational numbers, and cluster granularity is a preference"
                        + " about page size discoverable only from output that does not exist yet -- a key"
                        + " for it would ship unset, gate nothing, and be unanswerable",
                () -> assertThat(topLevelProfileKeys())
                        .containsExactlyInAnyOrderElementsOf(THE_KNOWN_PROFILE_KEYS));
        claim(
                "and no edge similarity floor appears among them either: an unmeasured threshold is what"
                        + " observe-before-enforce refuses, and k already bounds the edges",
                () -> assertThat(topLevelProfileKeys()).noneSatisfy(key -> assertThat(key).contains("similarity")));
    }

    @Test
    @Story("The spread of cluster sizes is reported per partition")
    @DisplayName("The size report is written beside the profile, naming the exemplar's partition")
    void writesTheSizeReport(@TempDir Path root, @TempDir Path seeds) throws IOException {
        Files.writeString(root.resolve("corpus.txt"), "a corpus document");
        Files.writeString(seeds.resolve("seed.txt"), "a seed document");
        profile(seeds);

        cli.run("run", root.toString());

        Path report = workingDirectory.resolve(ClusteringTasklet.CLUSTER_SIZES_FILE_NAME);
        claim(
                "the report was written to the working directory rather than into the corpus (ADR-054),"
                        + " beside the profile the operator was already told to look at",
                () -> assertThat(Files.exists(report)).isTrue());
        claim(
                "and it names the exemplar whose partition was grouped, so the sizes belong to something"
                        + " a reader can find",
                () -> assertThat(Files.readString(report)).contains("seed.txt"));
        claim(
                "and it reports how alike the documents on the kept links were, which is the one thing"
                        + " the sizes cannot say: a partition grouped by resemblance and one grouped"
                        + " because k links every document to its nearest few produce the same rows"
                        + " (ADR-096)",
                () -> assertThat(Files.readString(report))
                        .contains("Weakest link")
                        .contains("Strongest link"));
        claim(
                "and this partition holds one document, so it shows no resemblance at all rather than"
                        + " 0.00: there is no pair of documents for a number to describe, and a zero"
                        + " would read as a document resembling nothing",
                () -> assertThat(Files.readString(report)).contains("&mdash;"));
    }

    @Test
    @Story("The retained-edge spread is reported beside the sizes")
    @DisplayName("A partition with links reports how alike the documents on them were")
    void reportsTheResemblanceOfAPartitionThatHasLinks(@TempDir Path root, @TempDir Path seeds)
            throws IOException {
        Files.writeString(root.resolve("corpus-a.txt"), "a corpus document");
        Files.writeString(root.resolve("corpus-b.txt"), "another corpus document");
        Files.writeString(seeds.resolve("seed.txt"), "a seed document");
        profile(seeds);

        cli.run("run", root.toString());

        claim(
                "two documents make one link, and the page carries the number that link is worth --"
                        + " read off the graph this invocation built rather than recomputed afterwards,"
                        + " since the similarities exist only inside the pass that chose the edges",
                () -> assertThat(theSizeReport()).containsPattern(">[01]\\.\\d\\d<"));
    }

    /** The size report as written, for a claim about what a reader would see. */
    private String theSizeReport() throws IOException {
        return Files.readString(workingDirectory.resolve(ClusteringTasklet.CLUSTER_SIZES_FILE_NAME));
    }

    /** The seed folder named, stage 4's gate open, and gate 3 open too. */
    private void profile(Path seeds) {
        Profile profile = profileStore.load();
        profileStore.save(ProfileFixture.profile()
                .seedFolder(seeds.toString(), "set by this test")
                .degenerateOutputConfidenceFloor(profile.degenerateOutputConfidenceFloor())
                .boilerplateDocumentFrequencyFloor(BOILERPLATE_FLOOR, "set by this test, so stage 4's gate is open")
                .embeddingModel(MODEL_NAME, "set by this test, so gate 3 is open")
                .build());
    }

    /** The keys the profile file actually carries, read as text rather than through the record. */
    private List<String> topLevelProfileKeys() throws IOException {
        return Files.readAllLines(profileStore.file()).stream()
                .filter(line -> !line.isBlank() && !Character.isWhitespace(line.charAt(0)) && line.contains(":"))
                .map(line -> line.substring(0, line.indexOf(':')).trim())
                .toList();
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
