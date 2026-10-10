package io.algernon.vespera.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.corpus.Walk;
import io.algernon.vespera.embedding.RelevanceDistribution;
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
 * the first two tests move the whole corpus across the floor, and the third writes one score under the
 * floor itself, standing for a document that scored there: no fixture here can make one.
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

    private static final String BOILERPLATE_FLOOR = "1.0";
    private static final String MODEL_NAME = "qwen3-embedding:0.6b";

    /** Above anything the scripted embedder can produce, so every scored document falls under it. */
    private static final String A_FLOOR_ABOVE_EVERY_SCORE = "1.5";

    /** Under every score the scripted embedder produces, and over {@link #A_SCORE_UNDER_THE_FLOOR}. */
    private static final String A_FLOOR_UNDER_EVERY_SCORE = "0.5";

    /** The score the third test writes for one document, so that the floor removes that one and no other. */
    private static final double A_SCORE_UNDER_THE_FLOOR = 0.2;

    private static final String ANOTHER_MODELS_IDENTITY =
            "model=nomic-embed-text;digest=0ff0ff0f;dtype=F32;dimension=768;instruction=none";

    /** The corpus of the first two tests: both documents score alike, so the floor takes both or neither. */
    private static final int TWO_DOCUMENTS = 2;

    /** The corpus of the third test. */
    private static final int THREE_DOCUMENTS = 3;

    /** The one document of the three whose score the third test writes under the floor. */
    private static final int ONE_REMOVED = 1;

    /** The documents of the three the floor leaves once it has removed that one. */
    private static final int TWO_LEFT = THREE_DOCUMENTS - ONE_REMOVED;

    /** One arrangement of the documents the floor leaves and one of all of them, over one scoring. */
    private static final int TWO_ARRANGEMENTS = 2;

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
    @Story("Documents the threshold no longer removes are grouped and arranged")
    @DisplayName("When the threshold's removals are taken back after the groups were formed, the documents that came back are grouped and arranged")
    void survivorsTheFloorNoLongerRemovesAreClusteredAndArranged(@TempDir Path root, @TempDir Path seeds)
            throws IOException {
        aCorpus(root, seeds, TWO_DOCUMENTS);
        profile(seeds);
        cli.run("run", root.toString());
        String theFirstScoring = scoringRunsOf(root).getFirst();
        anAnswerGivenUnder(root, seeds, thisRunsIdentity());
        theFloorIs(A_FLOOR_ABOVE_EVERY_SCORE);
        cli.run("run", root.toString());
        String scoring = theScoringRunAfter(root, theFirstScoring);
        long removedThen = removedUnder(scoring);
        long clusteredThen = clusteredUnder(scoring);
        List<String> theScoringRunsBefore = scoringRunsOf(root);
        anAnswerGivenUnder(root, seeds, ANOTHER_MODELS_IDENTITY);

        cli.run("run", root.toString());

        claim(
                "the threshold had removed both of the " + TWO_DOCUMENTS + " documents, so the groups were"
                        + " formed over none of them and that step recorded its work as done",
                () -> assertThat(List.of(removedThen, clusteredThen)).containsExactly((long) TWO_DOCUMENTS, 0L));
        claim("the invocation after the answer changed reports success", () -> assertThat(cli.getExitCode())
                .isZero());
        claim(
                "the removals were taken back, the answer now saying the number was read off another"
                        + " model's scores",
                () -> assertThat(removedUnder(scoring)).isZero());
        claim(
                "and both documents are in a group under the same scoring: they are documents of the"
                        + " archive again, and a document that is not removed and is in no group is in no"
                        + " arrangement and no written output, with nothing saying it was left out",
                () -> assertThat(clusteredUnder(scoring)).isEqualTo(TWO_DOCUMENTS));
        claim(
                "and they are arranged, in an arrangement of that scoring holding both, which is what a"
                        + " person is then asked to approve",
                () -> assertThat(arrangementsOver(scoring))
                        .singleElement()
                        .satisfies(arrangement ->
                                assertThat(documentsArrangedUnder(arrangement)).isEqualTo(TWO_DOCUMENTS)));
        claim(
                "under the scorings there were before: nothing was scored again to do it",
                () -> assertThat(scoringRunsOf(root)).isEqualTo(theScoringRunsBefore));
    }

    @Test
    @Story("Documents the threshold removes after the groups were formed are in no group")
    @DisplayName("When the threshold starts removing after the groups were formed, no removed document stays in a group and an earlier approval opens nothing")
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
                        + " that have since been removed, so nothing is written over it",
                () -> assertThat(generationRunsOf(root)).isEmpty());
        claim("and that invocation reports success, an approval that opens nothing being no failure", () -> assertThat(
                        cli.getExitCode())
                .isZero());
    }

    @Test
    @Story("An approval is of the documents that were arranged when it was given")
    @DisplayName("The documents the threshold leaves and all the documents are two arrangements, each approved and written over by itself")
    void anArrangementWithTheRemovalsStandingIsNotTheOneWithout(@TempDir Path root, @TempDir Path seeds)
            throws IOException {
        aCorpus(root, seeds, THREE_DOCUMENTS);
        profile(seeds);
        cli.run("run", root.toString());
        String theFirstScoring = scoringRunsOf(root).getFirst();
        anAnswerGivenUnder(root, seeds, ANOTHER_MODELS_IDENTITY);
        theFloorIs(A_FLOOR_UNDER_EVERY_SCORE);
        cli.run("run", root.toString());
        String scoring = theScoringRunAfter(root, theFirstScoring);
        String ofAllThree = arrangementsOver(scoring).getFirst();
        approve(ofAllThree);
        cli.run("run", root.toString());
        List<String> writtenOverAllThree = generationRunsOf(root);
        oneDocumentScoresUnderTheFloor(scoring);
        anAnswerGivenUnder(root, seeds, thisRunsIdentity());

        cli.run("run", root.toString());

        claim(
                "all " + THREE_DOCUMENTS + " documents were arranged, approved and written over while the"
                        + " number was read off another model's scores and removed nothing",
                () -> assertThat(writtenOverAllThree).hasSize(1));
        claim(
                "answered again under the model that scored them, the threshold removes the "
                        + ONE_REMOVED + " document under it",
                () -> assertThat(removedUnder(scoring)).isEqualTo(ONE_REMOVED));
        claim(
                "and the groups are formed again over the " + TWO_LEFT + " it leaves, the removed one in"
                        + " none",
                () -> assertThat(List.of(clusteredUnder(scoring), removedAndClusteredUnder(scoring)))
                        .containsExactly((long) TWO_LEFT, 0L));
        claim(
                "those " + TWO_LEFT + " are an arrangement of their own, beside the one of all "
                        + THREE_DOCUMENTS + " and over the same scoring: " + TWO_ARRANGEMENTS
                        + " arrangements, so that what a person approved names the documents they read",
                () -> assertThat(arrangementsOver(scoring)).hasSize(TWO_ARRANGEMENTS));
        String ofTheTwoLeft = theOtherArrangementOver(scoring, ofAllThree);
        claim(
                "holding the " + TWO_LEFT + " documents",
                () -> assertThat(documentsArrangedUnder(ofTheTwoLeft)).isEqualTo(TWO_LEFT));
        claim(
                "it is told apart by how many removals stand under the scoring, " + ONE_REMOVED + " here,"
                        + " written after the corpus root and the scoring among the settings it records",
                () -> assertThat(settingsOf(ofTheTwoLeft))
                        .isEqualTo("{\"corpusRoot\":\"%s\",\"scoringRunId\":\"%s\",\"standingRemovals\":%d}"
                                .formatted(inJson(Walk.canonicalRoot(root)), scoring, ONE_REMOVED)));
        claim(
                "while the arrangement of all " + THREE_DOCUMENTS + " records what it did before, with no"
                        + " such setting: an arrangement over a scoring with no removal standing is"
                        + " identified as it always was",
                () -> assertThat(settingsOf(ofAllThree))
                        .isEqualTo("{\"corpusRoot\":\"%s\",\"scoringRunId\":\"%s\"}"
                                .formatted(inJson(Walk.canonicalRoot(root)), scoring)));
        claim(
                "the approval still names the arrangement of all " + THREE_DOCUMENTS + ", so nothing more"
                        + " was written: an approval of one set of documents is not an approval of another",
                () -> assertThat(generationRunsOf(root)).isEqualTo(writtenOverAllThree));

        approve(ofTheTwoLeft);
        cli.run("run", root.toString());

        claim(
                "approved, the arrangement of the " + TWO_LEFT + " is written over in a piece of work of"
                        + " its own, from those " + TWO_LEFT + " documents and not the removed one",
                () -> assertThat(generationRunsOver(ofTheTwoLeft))
                        .singleElement()
                        .satisfies(generation -> assertThat(documentsSentUnder(generation)).isEqualTo(TWO_LEFT)));

        anAnswerGivenUnder(root, seeds, ANOTHER_MODELS_IDENTITY);
        cli.run("run", root.toString());

        claim(
                "the answer changed back, the removal is taken back and all " + THREE_DOCUMENTS
                        + " documents are in a group again",
                () -> assertThat(List.of(removedUnder(scoring), clusteredUnder(scoring)))
                        .containsExactly(0L, (long) THREE_DOCUMENTS));
        claim(
                "which is the arrangement of all " + THREE_DOCUMENTS + " arrived at again, not a third:"
                        + " the page a person approves from names it",
                () -> assertThat(arrangementsOver(scoring)).hasSize(TWO_ARRANGEMENTS));
        claim("by the name an approval copies", () -> assertThat(theArrangementPage())
                .contains(ArrangementGate.shortNameOf(new RunId(ofAllThree))));
        claim(
                "and what was written over it from all " + THREE_DOCUMENTS + " stands as it was, nothing"
                        + " having been written again under the approval of the other",
                () -> assertThat(writtenOverAllThree)
                        .singleElement()
                        .satisfies(generation ->
                                assertThat(documentsSentUnder(generation)).isEqualTo(THREE_DOCUMENTS)));
    }

    private void aCorpus(Path root, Path seeds, int documents) throws IOException {
        for (int i = 0; i < documents; i++) {
            Files.writeString(root.resolve("corpus-" + i + ".txt"), "a corpus document " + i);
        }
        Files.writeString(seeds.resolve("seed.txt"), "a seed document");
    }

    /** The seed folder named, stage 4's gate and gate 3 open, no threshold and nothing approved. */
    private void profile(Path seeds) {
        profileStore.save(ProfileFixture.profile()
                .seedFolder(seeds.toString(), "set by this test")
                .degenerateOutputConfidenceFloor(profileStore.load().degenerateOutputConfidenceFloor())
                .boilerplateDocumentFrequencyFloor(BOILERPLATE_FLOOR, "set by this test, so stage 4's gate is open")
                .embeddingModel(MODEL_NAME, "set by this test, so gate 3 is open")
                .build());
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
        return relevanceDistribution.embedderIdentityFor(MODEL_NAME).orElseThrow();
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

    /**
     * Writes one document's score under {@code scoring} below the floor. The scoring step's completion is
     * recorded, so the score stays as written, as a score computed there would.
     */
    private void oneDocumentScoresUnderTheFloor(String scoring) {
        jdbcTemplate.update(
                "UPDATE relevance_score SET score = ? WHERE run_id = ? AND occurrence_id ="
                        + " (SELECT MAX(occurrence_id) FROM relevance_score WHERE run_id = ?)",
                A_SCORE_UNDER_THE_FLOOR,
                scoring,
                scoring);
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

    private List<String> generationRunsOver(String arrangement) {
        return runsOver(arrangement, "generation");
    }

    private List<String> runsOver(String upstream, String stage) {
        return jdbcTemplate.queryForList(
                "SELECT r.id FROM run r JOIN run_upstream u ON u.run_id = r.id"
                        + " WHERE r.stage = ? AND u.upstream_run_id = ? ORDER BY r.id",
                String.class,
                stage,
                upstream);
    }

    private String theOtherArrangementOver(String scoring, String notThisOne) {
        return arrangementsOver(scoring).stream()
                .filter(arrangement -> !arrangement.equals(notThisOne))
                .findFirst()
                .orElseThrow();
    }

    private String settingsOf(String run) {
        return jdbcTemplate.queryForObject("SELECT config_consumed FROM run WHERE id = ?", String.class, run);
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

    private long documentsArrangedUnder(String arrangement) {
        return count("SELECT COALESCE(SUM(document_count), 0) FROM cluster WHERE run_id = ?", arrangement);
    }

    private long documentsSentUnder(String generation) {
        return count("SELECT COUNT(*) FROM call_exemplar WHERE run_id = ?", generation);
    }

    private String theArrangementPage() throws IOException {
        return Files.readString(workingDirectory.resolve(ArrangementTasklet.ARRANGEMENT_FILE_NAME));
    }

    private long count(String sql, Object... arguments) {
        Long count = jdbcTemplate.queryForObject(sql, Long.class, arguments);
        return count == null ? 0 : count;
    }

    private String walkRoot(Path root) {
        return Walk.canonicalRoot(root).toString();
    }

    private static String inJson(Path path) {
        return path.toString().replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
