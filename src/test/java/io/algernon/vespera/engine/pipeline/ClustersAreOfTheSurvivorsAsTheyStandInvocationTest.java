package io.algernon.vespera.engine.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.engine.corpus.Walk;
import io.algernon.vespera.engine.embedding.RelevanceDistribution;
import io.algernon.vespera.engine.embedding.RelevanceLabels;
import io.algernon.vespera.engine.ledger.OccurrencePath;
import io.algernon.vespera.engine.ledger.RunId;
import io.algernon.vespera.engine.profile.ProfileFixture;
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

/**
 * What stage 5's clustering step, 6a and 6b do once the floor's step has decided again under a scoring run
 * whose clustering was already recorded (ADR-230, #489).
 *
 * <p>The floor's step records no completion and decides again on every invocation (ADR-118), so the
 * survivors of a scoring run can change after the clustering step recorded its own: an answer given under
 * another embedder identity withdraws the removals, and an answer given under this run's own makes them.
 * ADR-230 has the clustering step do its work again wherever its rows are not of the survivors the run has
 * now, and has the arrangement's identity name the removals standing under the scoring run, so that the
 * arrangement of one set of survivors is never the arrangement of the other and an approval of one never
 * opens generation over the other (ADR-107).
 *
 * <p><b>Every score here is the same</b>, the scripted embedder answering every chunk with one vector. So
 * {@code survivorsTheFloorNoLongerRemovesAreClusteredAndArranged} and {@code
 * occurrencesTheFloorRemovesAfterClusteringLeaveTheirClusters} move the whole corpus across the floor, and
 * {@code anArrangementWithTheRemovalsStandingIsNotTheOneWithout} and {@code
 * theClustersAreComparedAsSetsAndNotAsCounts} each write one score under the floor themselves, standing for a
 * document that scored there: no fixture here can make one.
 *
 * <p>The identifiers say cluster and the report says group (ADR-122).
 */
@CascadeSliceTest
@Import(SeedScriptedExtractionBeans.class)
@Epic("Relevance")
@Feature("Relevance threshold")
@Issue("489")
@Link(name = "ADR-230", url = Adr.THE_CLUSTERS_UNDER_A_SCORING_RUN_ARE_OF_ITS_SURVIVORS_AS_THEY_STAND, type = "adr")
@Link(name = "ADR-118", url = Adr.THE_ANSWERS_NEVER_JOIN_A_RUNS_IDENTITY, type = "adr")
@Link(name = "ADR-087", url = Adr.CLUSTERS_ARE_MODULARITY_COMMUNITIES, type = "adr")
@Link(name = "ADR-107", url = Adr.THE_ARRANGEMENT_GATE_APPROVES_A_NAMED_RUN, type = "adr")
class ClustersAreOfTheSurvivorsAsTheyStandInvocationTest {

    /** Above anything the scripted embedder can produce, so every scored document falls under it. */
    private static final String A_FLOOR_ABOVE_EVERY_SCORE = "1.5";

    private static final String ANOTHER_MODELS_IDENTITY =
            "model=acme-embed-text;digest=0ff0ff0f;dtype=F32;dimension=768;instruction=none";

    /**
     * The corpus of {@code survivorsTheFloorNoLongerRemovesAreClusteredAndArranged} and {@code
     * occurrencesTheFloorRemovesAfterClusteringLeaveTheirClusters}: both documents score alike, so the floor
     * takes both or neither.
     */
    private static final int TWO_DOCUMENTS = 2;

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
    private RelevanceDistribution relevanceDistribution;

    @Autowired
    private RelevanceLabels relevanceLabels;

    @BeforeEach
    void forgetWhatAnEarlierClassScripted() {
        GenerationScriptedBeans.forgetScriptedAnswers();
        EmbeddingScriptedBeans.servesEveryModel();
    }

    @AfterEach
    void forgetWhatWasScripted() {
        GenerationScriptedBeans.forgetScriptedAnswers();
    }

    @Test
    @Story("Documents the threshold removes after the groups were formed are in no group")
    @DisplayName("When the threshold starts removing after the groups were formed, no removed document stays in a group, and with none left to arrange an earlier approval writes nothing")
    void occurrencesTheFloorRemovesAfterClusteringLeaveTheirClusters(@TempDir Path root, @TempDir Path seeds)
            throws IOException {
        aCorpus(root, seeds, TWO_DOCUMENTS);
        profile(seeds);
        cli.run("run", root.toString());
        String theFirstScoring = scoringRunsOf(root).getFirst();
        anAnswerGivenUnder(root, seeds, ANOTHER_MODELS_IDENTITY);
        theFloorIs(A_FLOOR_ABOVE_EVERY_SCORE);
        cli.run("run", root.toString());
        String scoring = theScoringRunAfter(root, theFirstScoring);
        long clusteredThen = clusteredUnder(scoring);
        String arrangedBeforeTheRemovals = arrangementsOver(scoring).getFirst();
        anAnswerGivenUnder(root, seeds, thisRunsIdentity());

        cli.run("run", root.toString());

        claim(
                "the number had been read off another model's scores, so nothing was removed and both of"
                        + " the " + TWO_DOCUMENTS + " documents were grouped",
                () -> assertThat(clusteredThen).isEqualTo(TWO_DOCUMENTS));
        claim(
                "answered again under the model that scored them, the threshold removes both, with no"
                        + " scoring of its own",
                () -> assertThat(removedUnder(scoring)).isEqualTo(TWO_DOCUMENTS));
        claim(
                "and no removed document is still in a group: a group is of documents the archive keeps,"
                        + " and what is written from the groups would otherwise be written from documents"
                        + " this same scoring has removed",
                () -> assertThat(removedAndClusteredUnder(scoring)).isZero());

        approve(arrangedBeforeTheRemovals);
        cli.run("run", root.toString());

        claim(
                "approving the arrangement made before the removals opens nothing: it arranges documents"
                        + " that have since been removed, and with both removed nothing is left to arrange, so"
                        + " nothing is written. That an approval is of the one set of documents it was given"
                        + " for, where some are left, is the claim of the test over three documents",
                () -> assertThat(generationRunsOf(root)).isEmpty());
        claim("and that invocation reports success, an approval that opens nothing being no failure", () -> assertThat(
                        cli.getExitCode())
                .isZero());
    }

    private void aCorpus(Path root, Path seeds, int documents) throws IOException {
        for (int i = 0; i < documents; i++) {
            Files.writeString(root.resolve("corpus-" + i + ".txt"), "a corpus document " + i);
        }
        Files.writeString(seeds.resolve("seed.txt"), "a seed document");
    }

    /** The seed folder named, stage 4's gate and gate 3 open, no threshold and nothing approved. */
    private void profile(Path seeds) {
        EveryGateOpen.throughStageFive(profileStore, seeds);
    }

    private void theFloorIs(String floor) {
        profileStore.save(ProfileFixture.profileFrom(profileStore.load())
                .relevanceScoreFloor(floor, "set by this test")
                .build());
    }

    private void approve(String arrangement) {
        profileStore.save(ProfileFixture.profileFrom(profileStore.load())
                .arrangementApproved(ArrangementGate.shortNameOf(new RunId(arrangement)), "read by this test")
                .build());
    }

    private String thisRunsIdentity() {
        // One row, or the read throws: the vectors here are all the scripted runtime's (ADR-228).
        return jdbcTemplate.queryForObject("SELECT DISTINCT embedder_identity FROM vector", String.class);
    }

    /** One answer about one of this corpus's documents, as given while {@code embedderIdentity} was in use. */
    private void anAnswerGivenUnder(Path root, Path seeds, String embedderIdentity) {
        String aDocument = jdbcTemplate.queryForObject(
                "SELECT f.path FROM file_occurrence f JOIN walk w ON w.id = f.walk_id"
                        + " WHERE w.root = ? ORDER BY f.path LIMIT 1",
                String.class,
                walkRoot(root));
        relevanceLabels.record(
                new OccurrencePath(aDocument),
                Walk.canonicalRoot(seeds).toString(),
                true,
                new RunId(scoringRunsOf(root).getFirst()),
                1.0,
                embedderIdentity);
    }

    private List<String> scoringRunsOf(Path root) {
        return runsOf(root, "embedding-scoring");
    }

    private List<String> generationRunsOf(Path root) {
        return runsOf(root, "generation");
    }

    private List<String> runsOf(Path root, String stage) {
        return jdbcTemplate.queryForList(
                "SELECT r.id FROM run r JOIN walk w ON w.id = r.walk_id WHERE r.stage = ? AND w.root = ?"
                        + " ORDER BY r.id",
                String.class,
                stage,
                walkRoot(root));
    }

    /** The scoring run that is not {@code earlier}: named by exclusion, a run id saying nothing of order. */
    private String theScoringRunAfter(Path root, String earlier) {
        return scoringRunsOf(root).stream()
                .filter(run -> !run.equals(earlier))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("the changed threshold minted no scoring run"));
    }

    private List<String> arrangementsOver(String scoring) {
        return runsOver(scoring, "arrangement");
    }

    private List<String> runsOver(String upstream, String stage) {
        return jdbcTemplate.queryForList(
                "SELECT r.id FROM run r JOIN run_upstream u ON u.run_id = r.id"
                        + " WHERE r.stage = ? AND u.upstream_run_id = ? ORDER BY r.id",
                String.class,
                stage,
                upstream);
    }

    private long removedUnder(String scoring) {
        return count("SELECT COUNT(*) FROM verdict WHERE run_id = ? AND kind = 'BELOW_THRESHOLD'", scoring);
    }

    private long clusteredUnder(String scoring) {
        return count("SELECT COUNT(*) FROM document_cluster WHERE run_id = ?", scoring);
    }

    private long removedAndClusteredUnder(String scoring) {
        return count(
                "SELECT COUNT(*) FROM document_cluster c JOIN verdict v ON v.occurrence_id = c.occurrence_id"
                        + " AND v.run_id = c.run_id WHERE c.run_id = ? AND v.kind = 'BELOW_THRESHOLD'",
                scoring);
    }

    private long count(String sql, Object... arguments) {
        Long count = jdbcTemplate.queryForObject(sql, Long.class, arguments);
        return count == null ? 0 : count;
    }

    private String walkRoot(Path root) {
        return Walk.canonicalRoot(root).toString();
    }
}
