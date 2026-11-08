package io.algernon.vespera.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.corpus.Walk;
import io.algernon.vespera.embedding.RelevanceLabels;
import io.algernon.vespera.ledger.OccurrencePath;
import io.algernon.vespera.ledger.RunId;
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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * The last removal in the cascade, end to end (ADR-088, #112): what a relevance threshold does to a
 * real invocation in each of its three states.
 *
 * <p><b>Two of the three states remove nothing, and that is the point of testing all three.</b> A
 * threshold that ships unset must not stop the run, because this run is what produces the data the
 * threshold is calibrated from; and a threshold read off another model's scores must not be applied,
 * because it would remove documents against a spread it was never read off. Only the third writes a
 * verdict.
 *
 * <p>Every fixture here scores identically, because the scripted embedder answers every chunk with
 * the same vector. That is enough: what these claims are about is whether a verdict is written at
 * all, and what happens to a document that earns one — not where a boundary falls, which
 * {@code LabelledSpreadTest} pins on scores a fixture can control exactly.
 */
@CascadeSliceTest
@Import(SeedScriptedExtractionBeans.class)
@Epic("Relevance")
@Feature("Relevance threshold")
@Issue("112")
@Link(name = "ADR-088", url = Adr.RELEVANCE_THRESHOLD_IS_SIXTY_LABELS, type = "adr")
class RelevanceFloorInvocationTest {

    /** Above anything the scripted embedder can produce, so every scored document falls under it. */
    private static final String A_FLOOR_ABOVE_EVERY_SCORE = "1.5";

    private static final int CORPUS_DOCUMENTS = 2;

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
    private RelevanceLabels relevanceLabels;

    @Test
    @Story("A floor on this run's own scale is the only state that removes")
    @DisplayName("A threshold on this run's scale removes what falls under it, and none of it is clustered")
    void anApplicableFloorRemovesAndNothingRemovedIsClustered(@TempDir Path root, @TempDir Path seeds)
            throws IOException {
        aCorpus(root, seeds);
        profile(seeds, null);
        cli.run("run", root.toString());
        anAnswerGivenUnder(root, seeds, thisRunsIdentity());
        String theFirstRun = scoringRunIdsFor(root).getFirst();
        profile(seeds, A_FLOOR_ABOVE_EVERY_SCORE);

        cli.run("run", root.toString());

        claim(
                "every scored document earned a below-threshold verdict, this being the one state in"
                        + " which stage 5 removes anything at all",
                () -> assertThat(belowThresholdCountFor(root)).isEqualTo(CORPUS_DOCUMENTS));
        claim(
                "and none of them was clustered under the second run: a removed document is not a survivor"
                        + " by the time clustering reads the partition, so it is never grouped and never"
                        + " given a page. That is the whole of ADR-087's 'a below-threshold document costs"
                        + " nothing', and it depends on the floor step running first",
                () -> assertThat(documentClusterCountFor(root, theRunAfter(root, theFirstRun)))
                        .isZero());
        claim(
                "the verdict says what it was measured against rather than standing bare, since a person"
                        + " reading it a year later has to know why. The sentence is written out here rather"
                        + " than read off the constant, so that moving the constant between modules cannot"
                        + " change a character of it unseen",
                () -> assertThat(aBelowThresholdReason(root))
                        .isEqualTo("relevance score below the floor set in the profile"));
    }

    @Test
    @Story("A floor on this run's own scale is the only state that removes")
    @DisplayName("A document with no text is never removed by the threshold, having no score to be below")
    void aDocumentWithNoTextIsNotRemovedByTheThreshold(@TempDir Path root, @TempDir Path seeds)
            throws IOException {
        aCorpus(root, seeds);
        // The shared scripted extractor answers this file name with no text at all, wherever it appears.
        Files.writeString(
                root.resolve(SeedScriptedExtractionBeans.EMPTY_SEED), "stands in for a scan with no text");
        profile(seeds, null);
        cli.run("run", root.toString());
        anAnswerGivenUnder(root, seeds, thisRunsIdentity());
        profile(seeds, A_FLOOR_ABOVE_EVERY_SCORE);

        cli.run("run", root.toString());

        claim(
                "the no-text document earned no below-threshold verdict: it was removed by stage 2 for"
                        + " having no text, and never reached scoring, so it has no score of zero to be"
                        + " read as irrelevance. A survivor with no chunks scoring zero would be removed"
                        + " for the wrong reason, and this confirms that set is empty rather than assuming"
                        + " it (#108)",
                () -> assertThat(belowThresholdCountFor(root)).isEqualTo(CORPUS_DOCUMENTS));
    }

    private void aCorpus(Path root, Path seeds) throws IOException {
        for (int i = 0; i < CORPUS_DOCUMENTS; i++) {
            Files.writeString(root.resolve("corpus-" + i + ".txt"), "a corpus document " + i);
        }
        Files.writeString(seeds.resolve("seed.txt"), "a seed document");
    }

    /** The seed folder named, stage 4's gate open, gate 3 open, and the threshold as given. */
    private void profile(Path seeds, String floor) {
        EveryGateOpen.throughStageFive(profileStore, seeds);
        profileStore.save(ProfileFixture.profileFrom(profileStore.load())
                .relevanceScoreFloor(floor, floor == null ? null : "set by this test")
                .build());
    }

    /** The embedder identity the scripted runtime actually produced, read back rather than assumed. */
    private String thisRunsIdentity() {
        // One row, or the read throws: no test of this class leaves a second identity behind it.
        return jdbcTemplate.queryForObject("SELECT DISTINCT embedder_identity FROM vector", String.class);
    }

    /**
     * One answer about one of this corpus's documents, recorded as though a person had given it while
     * {@code embedderIdentity} was in use — which is what makes a threshold calibrated or not.
     *
     * <p>Named by its path rather than resolved to an occurrence (ADR-097), which is also how the
     * label file already names it.
     */
    private void anAnswerGivenUnder(Path root, Path seeds, String embedderIdentity) {
        relevanceLabels.record(
                new OccurrencePath(aDocumentIn(root)),
                Walk.canonicalRoot(seeds).toString(),
                true,
                new RunId(scoringRunIdsFor(root).getFirst()),
                1.0,
                embedderIdentity);
    }

    /** The path of one document this corpus holds, taken from what census recorded. */
    private String aDocumentIn(Path root) {
        return jdbcTemplate.queryForObject(
                "SELECT f.path FROM file_occurrence f JOIN walk w ON w.id = f.walk_id"
                        + " WHERE w.root = ? ORDER BY f.path LIMIT 1",
                String.class,
                walkRoot(root));
    }

    private String walkRoot(Path root) {
        return Walk.canonicalRoot(root).toString();
    }

    private List<String> scoringRunIdsFor(Path root) {
        return jdbcTemplate.queryForList(
                "SELECT run.id FROM run JOIN walk w ON w.id = run.walk_id"
                        + " WHERE run.stage = 'embedding-scoring' AND w.root = ? ORDER BY run.id",
                String.class,
                walkRoot(root));
    }

    /**
     * The scoring run that is not {@code earlier} — the one the second invocation minted.
     *
     * <p>Named by exclusion rather than by taking the last row: a run id is content-derived (ADR-048),
     * so ordering by it is alphabetical and says nothing about which invocation came first.
     */
    private String theRunAfter(Path root, String earlier) {
        return scoringRunIdsFor(root).stream()
                .filter(run -> !run.equals(earlier))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "the second invocation minted no scoring run of its own"));
    }

    private long documentClusterCountFor(Path root) {
        return count("SELECT COUNT(*) FROM document_cluster c JOIN file_occurrence f ON f.id = c.occurrence_id"
                + " JOIN walk w ON w.id = f.walk_id WHERE w.root = ?", walkRoot(root));
    }

    private long documentClusterCountFor(Path root, String runId) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM document_cluster c JOIN file_occurrence f ON f.id = c.occurrence_id"
                        + " JOIN walk w ON w.id = f.walk_id WHERE w.root = ? AND c.run_id = ?",
                Long.class,
                walkRoot(root),
                runId);
        return count == null ? 0 : count;
    }

    private long belowThresholdCountFor(Path root) {
        return count("SELECT COUNT(*) FROM verdict v JOIN file_occurrence f ON f.id = v.occurrence_id"
                + " JOIN walk w ON w.id = f.walk_id WHERE w.root = ? AND v.kind = 'BELOW_THRESHOLD'",
                walkRoot(root));
    }

    private String aBelowThresholdReason(Path root) {
        return jdbcTemplate.queryForList(
                        "SELECT v.reason FROM verdict v JOIN file_occurrence f ON f.id = v.occurrence_id"
                                + " JOIN walk w ON w.id = f.walk_id WHERE w.root = ? AND v.kind = 'BELOW_THRESHOLD'",
                        String.class,
                        walkRoot(root))
                .getFirst();
    }

    private long count(String sql, Object... arguments) {
        Long count = jdbcTemplate.queryForObject(sql, Long.class, arguments);
        return count == null ? 0 : count;
    }
}
