package io.algernon.vespera.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.corpus.Walk;
import io.algernon.vespera.embedding.RelevanceDistribution;
import io.algernon.vespera.embedding.RelevanceLabels;
import io.algernon.vespera.ledger.OccurrencePath;
import io.algernon.vespera.ledger.RunId;
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

    private static final String BOILERPLATE_FLOOR = "1.0";
    private static final String MODEL_NAME = "qwen3-embedding:0.6b";

    /** Above anything the scripted embedder can produce, so every scored document falls under it. */
    private static final String A_FLOOR_ABOVE_EVERY_SCORE = "1.5";

    /** Below anything it can produce, so every scored document clears it. */
    private static final String A_FLOOR_BELOW_EVERY_SCORE = "0.1";

    private static final String ANOTHER_MODELS_IDENTITY =
            "model=nomic-embed-text;digest=0ff0ff0f;dtype=F32;dimension=768;instruction=none";

    /** The same number as {@link #A_FLOOR_BELOW_EVERY_SCORE}, written the way a second person might. */
    private static final String THE_SAME_FLOOR_WRITTEN_DIFFERENTLY = "0.10";

    /** What a person types when they answer the key with a sentence instead of a number. */
    private static final String A_FLOOR_THAT_IS_NOT_A_NUMBER = "about halfway";

    private static final int CORPUS_DOCUMENTS = 2;

    /** One piece of scoring work, however many times the corpus is invoked over. */
    private static final int ONE_SCORING_RUN = 1;

    /** Two pieces of scoring work: one under no threshold, one under the threshold that followed it. */
    private static final int TWO_SCORING_RUNS = 2;

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

    @Test
    @Story("An unset floor removes nothing and does not stop the run")
    @DisplayName("With no threshold set, scores and clusters are written and no verdict is")
    void anUnsetFloorRemovesNothing(@TempDir Path root, @TempDir Path seeds) throws IOException {
        aCorpus(root, seeds);
        profile(seeds, null);

        cli.run("run", root.toString());

        claim(
                "the invocation reports success -- an unset threshold is not a gate, because this run is"
                        + " what produces the data the threshold is calibrated from and gating on it would"
                        + " mean never producing the report that lets anyone set it",
                () -> assertThat(cli.getExitCode()).isZero());
        claim(
                "every document was scored",
                () -> assertThat(relevanceScoreCountFor(root)).isEqualTo(CORPUS_DOCUMENTS));
        claim(
                "and clustered, so the run did all of its work",
                () -> assertThat(documentClusterCountFor(root)).isEqualTo(CORPUS_DOCUMENTS));
        claim(
                "and no below-threshold verdict was written, because there is no threshold to be below",
                () -> assertThat(belowThresholdCountFor(root)).isZero());
    }

    @Test
    @Story("A floor calibrated against another model is ignored, and the reason is stated")
    @DisplayName("A threshold read off another model's labels removes nothing, and the page says why")
    void aFloorFromAnotherModelRemovesNothing(@TempDir Path root, @TempDir Path seeds) throws IOException {
        aCorpus(root, seeds);
        profile(seeds, null);
        cli.run("run", root.toString());
        anAnswerGivenUnder(root, seeds, ANOTHER_MODELS_IDENTITY);
        profile(seeds, A_FLOOR_ABOVE_EVERY_SCORE);

        cli.run("run", root.toString());

        claim(
                "no document was removed, though the threshold sits above every score here: a threshold is"
                        + " a number on a scale, the model is the scale, and applying this one would remove"
                        + " documents against a spread it was never read off",
                () -> assertThat(belowThresholdCountFor(root)).isZero());
        claim(
                "and the page tells the operator their number is not being applied rather than ignoring it"
                        + " silently -- a value that vanishes reads as an engine that lost it",
                () -> assertThat(theLabellingPage()).contains("not being applied"));
        claim(
                "naming the model the answers were given under, so the reader knows which of the two"
                        + " things to change",
                () -> assertThat(theLabellingPage()).contains(ANOTHER_MODELS_IDENTITY));
    }

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
                        + " reading it a year later has to know why",
                () -> assertThat(aBelowThresholdReason(root)).isEqualTo(RelevanceFloorTasklet.REASON));
    }

    @Test
    @Story("The engine does the arithmetic and never writes the number")
    @DisplayName("A run leaves the threshold exactly as the operator wrote it")
    void neverWritesTheNumber(@TempDir Path root, @TempDir Path seeds) throws IOException {
        aCorpus(root, seeds);
        profile(seeds, A_FLOOR_BELOW_EVERY_SCORE);

        cli.run("run", root.toString());

        claim(
                "the number in the profile is the one the operator wrote, untouched: a threshold the"
                        + " engine wrote would break both the rule that the profile is authored by a person"
                        + " and ADR-062's census that never touches an existing value",
                () -> assertThat(profileStore.load().relevanceScoreFloor().value())
                        .isEqualTo(A_FLOOR_BELOW_EVERY_SCORE));
        claim(
                "and the provenance the operator wrote beside it is untouched too -- nothing checks that a"
                        + " threshold was ever labelled, and what stands between a guess and the archive is"
                        + " exactly this line (ADR-031)",
                () -> assertThat(profileStore.load().relevanceScoreFloor().provenance())
                        .isEqualTo("set by this test"));
        claim(
                "a floor under every score removes nothing, so an applicable threshold is not a blanket"
                        + " removal -- it removes what falls under it and nothing else",
                () -> assertThat(belowThresholdCountFor(root)).isZero());
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

    @Test
    @Story("A changed threshold is a different run")
    @DisplayName("Setting a threshold where there was none is a scoring run of its own")
    @Issue("199")
    @Link(name = "ADR-117", url = Adr.THE_RELEVANCE_FLOOR_JOINS_THE_SCORING_RUNS_IDENTITY, type = "adr")
    void aChangedFloorIsADifferentScoringRun(@TempDir Path root, @TempDir Path seeds) throws IOException {
        aCorpus(root, seeds);
        profile(seeds, null);
        cli.run("run", root.toString());
        String theRunWithNoThreshold = scoringRunIdsFor(root).getFirst();
        profile(seeds, A_FLOOR_ABOVE_EVERY_SCORE);

        cli.run("run", root.toString());

        claim(
                "the second invocation scored under a run of its own, so this corpus now carries"
                        + " " + TWO_SCORING_RUNS + " scoring runs rather than " + ONE_SCORING_RUN + ": a run"
                        + " name is worked out from everything the run was given, and the threshold is"
                        + " something it was given -- one that removes every document and one that removes"
                        + " none cannot answer to the same name",
                () -> assertThat(scoringRunIdsFor(root)).hasSize(TWO_SCORING_RUNS));
        claim(
                "and every removal is recorded under the newer of the two, never under the run that"
                        + " scored with no threshold at all: the run a verdict sits under is the record of"
                        + " what produced it, and nothing was produced by the earlier one",
                () -> assertThat(belowThresholdRunIdsFor(root))
                        .isNotEmpty()
                        .doesNotContain(theRunWithNoThreshold));
    }

    @Test
    @Story("A changed threshold is a different run")
    @DisplayName("Invoking twice with the same threshold stays one scoring run")
    @Issue("199")
    @Link(name = "ADR-117", url = Adr.THE_RELEVANCE_FLOOR_JOINS_THE_SCORING_RUNS_IDENTITY, type = "adr")
    void anUnchangedFloorStaysOneScoringRun(@TempDir Path root, @TempDir Path seeds) throws IOException {
        aCorpus(root, seeds);
        profile(seeds, A_FLOOR_BELOW_EVERY_SCORE);
        cli.run("run", root.toString());

        cli.run("run", root.toString());

        claim(
                "the second invocation carried on under the run the first one made, leaving"
                        + " " + ONE_SCORING_RUN + " scoring run rather than two: nothing the run reads had"
                        + " changed, so this is that work rather than another piece of work like it",
                () -> assertThat(scoringRunIdsFor(root)).hasSize(ONE_SCORING_RUN));
    }

    @Test
    @Story("A changed threshold is a different run")
    @DisplayName("The same threshold written two ways is one threshold")
    @Issue("199")
    @Link(name = "ADR-117", url = Adr.THE_RELEVANCE_FLOOR_JOINS_THE_SCORING_RUNS_IDENTITY, type = "adr")
    void aThresholdWrittenDifferentlyIsOneThreshold(@TempDir Path root, @TempDir Path seeds) throws IOException {
        aCorpus(root, seeds);
        profile(seeds, A_FLOOR_BELOW_EVERY_SCORE);
        cli.run("run", root.toString());
        profile(seeds, THE_SAME_FLOOR_WRITTEN_DIFFERENTLY);

        cli.run("run", root.toString());

        claim(
                "a trailing zero left " + ONE_SCORING_RUN + " scoring run standing: what a run is named"
                        + " after is the number the removal will actually be measured against, not the"
                        + " characters someone typed, and a second run here would remove exactly what the"
                        + " first one did under a second name",
                () -> assertThat(scoringRunIdsFor(root)).hasSize(ONE_SCORING_RUN));
    }

    @Test
    @Story("A changed threshold is a different run")
    @DisplayName("A threshold that is not a number is the same run as no threshold")
    @Issue("199")
    @Link(name = "ADR-117", url = Adr.THE_RELEVANCE_FLOOR_JOINS_THE_SCORING_RUNS_IDENTITY, type = "adr")
    void aThresholdThatIsNotANumberIsTheSameRunAsNoThreshold(@TempDir Path root, @TempDir Path seeds)
            throws IOException {
        aCorpus(root, seeds);
        profile(seeds, null);
        cli.run("run", root.toString());
        profile(seeds, A_FLOOR_THAT_IS_NOT_A_NUMBER);

        cli.run("run", root.toString());

        claim(
                "a value nobody can read as a number left " + ONE_SCORING_RUN + " scoring run standing:"
                        + " this run does exactly what it did when the key was blank -- it removes nothing"
                        + " -- so the two are one piece of work, and the operator is told about the typo"
                        + " where they are already looking rather than by a second run appearing",
                () -> assertThat(scoringRunIdsFor(root)).hasSize(ONE_SCORING_RUN));
        claim(
                "and nothing was removed on the strength of it",
                () -> assertThat(belowThresholdCountFor(root)).isZero());
    }

    /** The scoring runs the below-threshold verdicts over this corpus were written under. */
    @Test
    @Story("An answer given after the run is read by the next invocation")
    @DisplayName("Re-answering under the model this run used gets the threshold applied on the next invocation")
    @Issue("191")
    @Link(name = "ADR-118", url = Adr.THE_ANSWERS_NEVER_JOIN_A_RUNS_IDENTITY, type = "adr")
    void anAnswerReAnsweredUnderThisModelGetsTheThresholdApplied(@TempDir Path root, @TempDir Path seeds)
            throws IOException {
        aCorpus(root, seeds);
        profile(seeds, null);
        cli.run("run", root.toString());
        anAnswerGivenUnder(root, seeds, ANOTHER_MODELS_IDENTITY);
        profile(seeds, A_FLOOR_ABOVE_EVERY_SCORE);
        cli.run("run", root.toString());
        anAnswerGivenUnder(root, seeds, thisRunsIdentity());

        cli.run("run", root.toString());

        claim(
                "the third invocation changed nothing the run is named after, so this corpus still carries"
                        + " " + TWO_SCORING_RUNS + " scoring runs: an answer is a fact about a document"
                        + " rather than something the run was configured with, and naming it would mint a"
                        + " fresh run every time a person replied to the page asking them to reply",
                () -> assertThat(scoringRunIdsFor(root)).hasSize(TWO_SCORING_RUNS));
        claim(
                "and all " + CORPUS_DOCUMENTS + " documents were removed, the threshold now sitting on a"
                        + " scale the answers were given on. Re-answering the sample under the model that"
                        + " actually scored it is the one repair available to an operator whose number was"
                        + " being ignored, and it has to take effect without them editing anything",
                () -> assertThat(belowThresholdCountFor(root)).isEqualTo(CORPUS_DOCUMENTS));
    }

    @Test
    @Story("An answer given after the run is read by the next invocation")
    @DisplayName("An answer given under another model withdraws removals the threshold had already made")
    @Issue("191")
    @Link(name = "ADR-118", url = Adr.THE_ANSWERS_NEVER_JOIN_A_RUNS_IDENTITY, type = "adr")
    void anAnswerGivenUnderAnotherModelWithdrawsTheRemovals(@TempDir Path root, @TempDir Path seeds)
            throws IOException {
        aCorpus(root, seeds);
        profile(seeds, null);
        cli.run("run", root.toString());
        anAnswerGivenUnder(root, seeds, thisRunsIdentity());
        profile(seeds, A_FLOOR_ABOVE_EVERY_SCORE);
        cli.run("run", root.toString());
        anAnswerGivenUnder(root, seeds, ANOTHER_MODELS_IDENTITY);

        cli.run("run", root.toString());

        claim(
                "nothing stands removed any more. The same answer that let the number be applied now says"
                        + " it was read off another model's scores, and a removal a run can no longer"
                        + " justify must not outlive the reason it was made -- the two directions are one"
                        + " event, and leaving the removals standing would keep the harsher half of it",
                () -> assertThat(belowThresholdCountFor(root)).isZero());
        claim(
                "and still " + TWO_SCORING_RUNS + " scoring runs, because withdrawing them is this run"
                        + " deciding again rather than a second run deciding differently",
                () -> assertThat(scoringRunIdsFor(root)).hasSize(TWO_SCORING_RUNS));
    }

    private List<String> belowThresholdRunIdsFor(Path root) {
        return jdbcTemplate.queryForList(
                "SELECT DISTINCT v.run_id FROM verdict v JOIN file_occurrence f ON f.id = v.occurrence_id"
                        + " JOIN walk w ON w.id = f.walk_id WHERE w.root = ? AND v.kind = 'BELOW_THRESHOLD'",
                String.class,
                walkRoot(root));
    }

    private void aCorpus(Path root, Path seeds) throws IOException {
        for (int i = 0; i < CORPUS_DOCUMENTS; i++) {
            Files.writeString(root.resolve("corpus-" + i + ".txt"), "a corpus document " + i);
        }
        Files.writeString(seeds.resolve("seed.txt"), "a seed document");
    }

    /** The seed folder named, stage 4's gate open, gate 3 open, and the threshold as given. */
    private void profile(Path seeds, String floor) {
        profileStore.save(ProfileFixture.profile()
                .seedFolder(seeds.toString(), "set by this test")
                .degenerateOutputConfidenceFloor(profileStore.load().degenerateOutputConfidenceFloor())
                .boilerplateDocumentFrequencyFloor(BOILERPLATE_FLOOR, "set by this test, so stage 4's gate is open")
                .embeddingModel(MODEL_NAME, "set by this test, so gate 3 is open")
                .relevanceScoreFloor(floor, floor == null ? null : "set by this test")
                .build());
    }

    /** The embedder identity the scripted runtime actually produced, read back rather than assumed. */
    private String thisRunsIdentity() {
        return relevanceDistribution.embedderIdentityFor(MODEL_NAME).orElseThrow();
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

    private String theLabellingPage() throws IOException {
        return Files.readString(workingDirectory.resolve(RelevanceLabellingReport.FILE_NAME));
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

    private long relevanceScoreCountFor(Path root) {
        return count("SELECT COUNT(*) FROM relevance_score r JOIN file_occurrence f ON f.id = r.occurrence_id"
                + " JOIN walk w ON w.id = f.walk_id WHERE w.root = ?", walkRoot(root));
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
