package io.algernon.vespera.engine.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.WholeRun;
import io.algernon.vespera.engine.ledger.RunId;
import io.algernon.vespera.engine.profile.ProfileStore;
import io.algernon.vespera.engine.synthesis.Clusters;
import io.algernon.vespera.engine.synthesis.RecordedCluster;
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
 * Stage 6a, end to end (ADR-105, ADR-106, ADR-110, ADR-112, #175): once every survivor carries a
 * score, a winning seed and a cluster, the clusters themselves are given a name, a size and a place.
 *
 * <p>A sibling of {@link ClusteringInvocationTest} for the reason that class is a sibling of the
 * scoring one: this step reads what the step before it wrote rather than re-deriving it.
 *
 * <p><b>What this step must be shown not to do matters as much as what it does.</b> It restates no
 * membership — which documents are in a cluster stays exactly where the previous step put it — and it
 * writes no judgement against any document, because it takes nothing out of the archive.
 */
@CascadeSliceTest
@Import(SeedScriptedExtractionBeans.class)
@Epic("Arrangement")
@Feature("Arranging the documents")
@Issue("175")
@Link(name = "ADR-105", url = Adr.STAGE_6A_NAMES_THE_ARRANGEMENT_STAGE_5_BUILT, type = "adr")
@Link(name = "ADR-112", url = Adr.THE_ARRANGEMENT_IS_ORDERED_BY_SIZE_AND_MEAN_SCORE, type = "adr")
@Link(name = "ADR-104", url = Adr.THE_ORIGINALS_STAY_WHERE_THEY_ARE_AND_ARE_REFERENCED, type = "adr")
class ArrangementInvocationTest {

    /** How many corpus documents this fixture walks, all of which survive to be arranged. */
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

    @Autowired
    private Clusters clusters;

    @Test
    @Story("Every group of documents is given a name and a place")
    @DisplayName("Every group the previous step formed is recorded with a name, a size and a place")
    void recordsEveryClusterWithANameASizeAndAPlace(@TempDir Path root, @TempDir Path seeds) throws IOException {
        for (int i = 0; i < CORPUS_DOCUMENTS; i++) {
            Files.writeString(root.resolve("corpus-" + i + ".txt"), "a corpus document " + i);
        }
        Files.writeString(seeds.resolve("seed.txt"), "a seed document");
        profile(seeds);

        cli.run("run", root.toString());

        claim("the invocation reports success", () -> assertThat(cli.getExitCode()).isZero());
        List<RecordedCluster> arranged = WholeRun.clusters(jdbcTemplate,theArrangementRun());
        claim(
                "every group the previous step formed has a row of its own, which is the level that did"
                        + " not exist before it, and each arrives named, counted and placed, so whatever"
                        + " renders the arrangement never works any of that out a second time -- the"
                        + " arrangement a person approves and"
                        + " the arrangement a reader receives have to be the same one",
                () -> assertThat(arranged).allSatisfy(recorded -> {
                    assertThat(recorded.label().value()).isNotBlank();
                    assertThat(recorded.cluster().documentCount()).isPositive();
                    assertThat(recorded.cluster().partitionOrder()).isPositive();
                    assertThat(recorded.cluster().clusterOrder()).isPositive();
                }));
        claim(
                "and the documents across those groups add up to the " + CORPUS_DOCUMENTS + " that"
                        + " survived, so the arrangement covers the archive rather than part of it",
                () -> assertThat(arranged.stream()
                                .mapToInt(recorded -> recorded.cluster().documentCount())
                                .sum())
                        .isEqualTo(CORPUS_DOCUMENTS));
    }

    @Test
    @Story("Arranging documents takes nothing out of the archive")
    @DisplayName("Arranging the documents records no judgement against any of them")
    void writesNoVerdict(@TempDir Path root, @TempDir Path seeds) throws IOException {
        Files.writeString(root.resolve("corpus.txt"), "a corpus document");
        Files.writeString(seeds.resolve("seed.txt"), "a seed document");
        profile(seeds);

        cli.run("run", root.toString());

        claim(
                "no judgement was recorded because the documents were arranged: every kind of judgement"
                        + " this system records exists to take a document out of what gets published, and"
                        + " arranging takes nothing out of anything",
                () -> assertThat(verdictCountUnder(theArrangementRun())).isZero());
    }

    /** The arrangement run this invocation minted — the one this test's claims are about. */
    private RunId theArrangementRun() {
        return new RunId(jdbcTemplate.queryForObject(
                "SELECT id FROM run WHERE stage = ? ORDER BY rowid DESC LIMIT 1",
                String.class,
                "arrangement"));
    }

    private int verdictCountUnder(RunId runId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM verdict WHERE run_id = ?", Integer.class, runId.value());
    }

    /** The seed folder named, stage 4's gate open, and gate 3 open too. */
    private void profile(Path seeds) {
        EveryGateOpen.throughStageFive(profileStore, seeds);
    }
}
