package io.algernon.vespera.engine.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.engine.corpus.Walk;
import io.algernon.vespera.engine.profile.Profile;
import io.algernon.vespera.engine.profile.ProfileStore;
import io.algernon.vespera.engine.profile.ProfileValue;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * What a second invocation over an unchanged corpus does to the work the first one recorded
 * (ADR-115, ADR-116, #191).
 *
 * <p><b>Several steps share one run, and that is what this class is about.</b> Fifteen of the job's
 * sixteen steps write under eight run identities, the census minting none: stage 4's two steps share one,
 * the seed measurement's two share one, and stage 5's scoring half has six steps under a single run, four of
 * which record their completion. A record of completion kept against the run therefore says "all of this is recorded" on the strength
 * of whichever step reached the end first, and the steps behind it are told their work is done. So
 * completion is recorded against the step that did the work, and every claim here is a claim about
 * one step rather than about a stage.
 *
 * <p>The fixture is a whole invocation rather than a tasklet on purpose: what these claims are about
 * is what two invocations do to each other, which nothing below the command line can show.
 */
@CascadeSliceTest
@Import(SeedScriptedExtractionBeans.class)
@Epic("Pipeline")
@Feature("Repeated invocation")
@Issue("191")
@Link(name = "ADR-116", url = Adr.A_RUNS_COMPLETION_IS_RECORDED_PER_STEP, type = "adr")
@Link(name = "ADR-115", url = Adr.A_REPEATED_OBSERVATION_IS_DISCARDED_AND_A_RUN_IS_CONTINUED, type = "adr")
class RepeatedInvocationTest {

    /** How many corpus documents this fixture walks, every one of which survives to be clustered. */
    private static final int CORPUS_DOCUMENTS = 2;

    /** What each of those documents is worth once the job has run: one row each, per table. */
    private static final int ONE_ROW_EACH = CORPUS_DOCUMENTS;

    /** The seeds this fixture's seed folder holds, each measured once. */
    private static final int ONE_SEED = 1;

    /** The step of stage 5's scoring half whose work the second claim below puts back. */
    private static final String THE_CLUSTERING_STEP = "clustering";

    /** The stage name stage 5's scoring run is recorded under. */
    private static final String THE_SCORING_STAGE = "embedding-scoring";

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
    @Story("A second invocation over an unchanged corpus repeats nothing")
    @DisplayName("Invoking twice over an unchanged corpus succeeds and writes no second copy of anything")
    void aSecondInvocationOverAnUnchangedCorpusRepeatsNothing(@TempDir Path root, @TempDir Path seeds)
            throws IOException {
        aCorpus(root, seeds);
        EveryGateOpen.throughStageFive(profileStore, seeds);
        cli.run("run", root.toString());

        cli.run("run", root.toString());

        claim(
                "the second invocation reports success: everything it was asked to do was already"
                        + " recorded from the first, so it was left alone rather than done again",
                () -> assertThat(cli.getExitCode()).isZero());
        // Stage 4 is deliberately not asserted on here. This fixture's extractor answers every
        // document the same text, so every shingle occurs in all of them, and a boilerplate floor of
        // 1.0 -- the value that opens stage 4's gate -- strips all of them as boilerplate. Stage 4a
        // therefore signs an empty shingle set and writes no row for any document, whatever the step
        // completion records say, so a count here would pin the fixture rather than the behaviour.
        // The seed measurement below makes the same point about two steps sharing one run.
        claim(
                "the seed measurement still holds the " + ONE_SEED + " row of extraction metrics the first"
                        + " invocation wrote for the " + ONE_SEED + " seed: reading the seeds, which shares its run"
                        + " with comparing them, finds its own work recorded and neither discards nor rewrites it",
                () -> assertThat(rowsOver(seeds, "extraction_metric")).isEqualTo(ONE_SEED));
        claim(
                "each of the " + CORPUS_DOCUMENTS + " documents carries one relevance score rather than"
                        + " two, although four pieces of work record their completion together",
                () -> assertThat(rowsOver(root, "relevance_score")).isEqualTo(ONE_ROW_EACH));
        claim(
                "and each is in exactly one cluster -- the last of those four, and the one a shared"
                        + " record would have let the first answer for",
                () -> assertThat(rowsOver(root, "document_cluster")).isEqualTo(ONE_ROW_EACH));
    }

    @Test
    @Story("Work answers for itself, never for the work beside it")
    @DisplayName("Work that is missing is done again, although everything recorded alongside it is finished")
    void aStepWhoseWorkIsMissingDoesItAgainThoughItsStepMatesAreDone(@TempDir Path root, @TempDir Path seeds)
            throws IOException {
        aCorpus(root, seeds);
        EveryGateOpen.throughStageFive(profileStore, seeds);
        cli.run("run", root.toString());
        String scoringRun = scoringRunIdFor(root);
        aClusteringPassThatStoppedPartway(scoringRun);

        cli.run("run", root.toString());

        claim(
                "the invocation reports success rather than colliding with the row the interrupted"
                        + " invocation left behind: work that is not recorded as finished throws away"
                        + " what it wrote before and does it again",
                () -> assertThat(cli.getExitCode()).isZero());
        claim(
                "each of the " + CORPUS_DOCUMENTS + " documents is in exactly one cluster again -- "
                        + ONE_ROW_EACH + " rows, neither the single row the interrupted invocation left"
                        + " nor three -- so the clustering was done afresh although everything recorded"
                        + " alongside it had finished",
                () -> assertThat(rowsOver(root, "document_cluster")).isEqualTo(ONE_ROW_EACH));
        claim(
                "and it was recorded under the same name as before, because nothing it reads had"
                        + " changed: unfinished work is carried on rather than started somewhere new",
                () -> assertThat(scoringRunIdFor(root)).isEqualTo(scoringRun));
        claim(
                "while the scoring beside it wrote nothing a second time, so redoing one piece of"
                        + " work is not redoing all of it",
                () -> assertThat(rowsOver(root, "relevance_score")).isEqualTo(ONE_ROW_EACH));
    }

    private void aCorpus(Path root, Path seeds) throws IOException {
        for (int i = 0; i < CORPUS_DOCUMENTS; i++) {
            Files.writeString(root.resolve("corpus-" + i + ".txt"), "a corpus document " + i);
        }
        Files.writeString(seeds.resolve("seed.txt"), "a seed document");
    }

    /**
     * What the ledger holds after a clustering pass that stopped partway: one membership row written,
     * the other never reached, and no record that the step finished.
     *
     * <p>Written straight into the tables because no invocation can be stopped in the middle of one
     * step from out here. The completion record is removed by the name of the step it belongs to,
     * which is what the claims above turn on: the steps sharing this run keep theirs.
     */
    private void aClusteringPassThatStoppedPartway(String scoringRun) {
        jdbcTemplate.update(
                "DELETE FROM finished_step WHERE run_id = ? AND step = ?", scoringRun, THE_CLUSTERING_STEP);
        jdbcTemplate.update(
                "DELETE FROM document_cluster WHERE run_id = ? AND occurrence_id ="
                        + " (SELECT MIN(occurrence_id) FROM document_cluster WHERE run_id = ?)",
                scoringRun,
                scoringRun);
    }

    /** The one scoring run recorded over this corpus. */
    private String scoringRunIdFor(Path root) {
        return jdbcTemplate.queryForObject(
                "SELECT run.id FROM run JOIN walk w ON w.id = run.walk_id"
                        + " WHERE run.stage = ? AND w.root = ?",
                String.class,
                THE_SCORING_STAGE,
                walkRoot(root));
    }

    /** How many rows the named table holds against occurrences of this corpus, over every run. */
    private int rowsOver(Path root, String table) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + table + " t JOIN file_occurrence f ON f.id = t.occurrence_id"
                        + " JOIN walk w ON w.id = f.walk_id WHERE w.root = ?",
                Integer.class,
                walkRoot(root));
        return count == null ? 0 : count;
    }

    private String walkRoot(Path root) {
        return Walk.canonicalRoot(root).toString();
    }
}
