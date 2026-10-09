package io.algernon.vespera.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.corpus.Walk;
import io.algernon.vespera.extraction.ConverterStopsPartwayBeans;
import io.algernon.vespera.ledger.SuccessiveBuildsBeans;
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
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Whether stage 3's grouping of the shingle rows runs while {@code shingle_by_hash} exists (#473, following
 * ADR-218's "What this does not decide").
 *
 * <p><b>A characterisation of what ships today.</b> ADR-218 read from {@code StageModules} and ADR-182
 * that stage 3 can meet the index and ran no invocation to see it. These tests run the invocations, and
 * ADR-219 records them. Stage 2 drops the index only when its own step is unfinished under the run it arrives at, and
 * stage 4b builds it, so the index stands from stage 4b until a stage 2 next has work; a stage 3 that runs in
 * between, under a stage 2 with nothing to do, groups with the index there. Two sequences do that, and one
 * that looks alike does not:
 *
 * <ul>
 *   <li>a build that moves {@code pipeline} alone, which stage 3's run names and stage 2's does not;
 *   <li>a stage 3 stopped over one corpus root, while another corpus root of the same working directory goes
 *       on to stage 4b, and is then invoked again;
 *   <li>not a build that moves {@code similarity}: stage 2's run names it too, so stage 2 writes again and
 *       drops the index first.
 * </ul>
 *
 * <p><b>How the index is seen at the moment of the grouping.</b> A trigger the test adds to {@code
 * shingle_document_frequency} records, for every row written there, whether {@code sqlite_master} holds the
 * index at that moment, beside the run the row is written under. The corpus's texts share most of their
 * shingles, so the grouping writes rows and the trigger fires inside that statement.
 *
 * <p><b>How a build moves.</b> {@link SuccessiveBuildsBeans} moves one module's recorded version between
 * two invocations of one context, which is what installing the next build does between two processes. A
 * stop is a trigger refusing the row that records stage 3 finished, as {@code ShingleHashIndexInvocationTest}
 * stops stage 4b.
 *
 * <p><b>What ADR-219's clause turns here, and what it leaves.</b> ADR-219 decides that the grouping names
 * its index, {@code INDEXED BY shingle_by_run_id}, and that the clause ships with the next change to {@code
 * similarity}. That change turns one claim of this class and one constant: the last claim of {@link
 * #aBuildThatMovesOnlyPipelineHasStageThreeGroupWithTheHashIndexThere}, that the grouping is planned
 * through {@code shingle_by_hash} and sorts nothing for its {@code GROUP BY}, becomes that it is planned
 * through {@code shingle_by_run_id} and does sort for it; and {@link #GROUPING} gains the clause, so that
 * the plan asked for is of the statement sent. Every other claim stands with the clause shipped: the clause
 * changes which index the statement reads through, not when the index exists, so stage 3 still runs with
 * the index in the database in the same two sequences.
 */
@CascadeSliceTest
@Import({ConverterStopsPartwayBeans.class, SuccessiveBuildsBeans.class})
@ExtendWith(OutputCaptureExtension.class)
@Epic("Redundancy")
@Feature("Shingling")
@Issue("473")
@Link(name = "ADR-182", url = Adr.STAGE_4B_BUILDS_THE_BY_HASH_INDEX_STAGE_2_WRITES_WITHOUT, type = "adr")
@Link(name = "ADR-218", url = Adr.EVERY_STATEMENT_WHOSE_TEMPORARY_FILES_GROW_IS_AN_EXCEPTION_WITH_ITS_SIZE, type = "adr")
@Link(name = "ADR-219", url = Adr.STAGE_3S_GROUPING_IS_PINNED_TO_THE_INDEX_ON_THE_RUN, type = "adr")
class StageThreeMeetsTheHashIndexInvocationTest {

    /** Three commits' worth of stage 2, as {@code ShingleHashIndexInvocationTest} writes. */
    private static final int CORPUS_SIZE = 3 * ExtractionJobConfiguration.CHUNK_SIZE;

    /** A boilerplate floor set only to open stage 4's gate, so that stage 4b builds the index. */
    private static final String BOILERPLATE_FLOOR = "1.0";

    private static final String BY_HASH = "shingle_by_hash";

    /** What the trigger records where the index is there, and where it is not. */
    private static final int PRESENT = 1;

    private static final int ABSENT = 0;

    /** The start of the line stage 2 writes before it drops the index. */
    private static final String REMOVING = "Stage 2 (extraction) is removing shingle_by_hash";

    /** The start of the line stage 4b writes before it builds the index. */
    private static final String BUILDING = "Stage 4b (redundancy resolution) is building shingle_by_hash";

    /** The start of the line stage 3 writes when it has work to do, before the run's id. */
    private static final String STAGE_3_STARTING = "Stage 3 (content census) starting under run ";

    /** Stage 3's grouping without its insert, as {@code DocumentFrequency} issues it, for its plan. */
    private static final String GROUPING = "SELECT shingle_parameter_identity, shingle_hash,"
            + " COUNT(DISTINCT occurrence_id), COUNT(*) FROM shingle WHERE run_id = ?"
            + " GROUP BY shingle_parameter_identity, shingle_hash HAVING COUNT(DISTINCT occurrence_id) >= 2";

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

    /**
     * The converter answers and counts from zero, the build is the first, stage 4's gate is shut, and the
     * trigger that looks for the index stands with nothing recorded. The converter's script and the build's
     * versions are static and shared with other classes, whose order differs between machines.
     */
    @BeforeEach
    void startClean() {
        ConverterStopsPartwayBeans.script(List.of(), List.of(), List.of());
        ConverterStopsPartwayBeans.keepAnswering();
        SuccessiveBuildsBeans.theFirstBuild();
        profileStore.save(ProfileFixture.profile().build());
        removeWhatThisClassAdded();
        jdbcTemplate.execute("CREATE TABLE hash_index_as_the_grouping_found_it (run_id TEXT, present INTEGER)");
        jdbcTemplate.execute("CREATE TRIGGER the_grouping_looks_for_the_hash_index"
                + " BEFORE INSERT ON shingle_document_frequency BEGIN"
                + " INSERT INTO hash_index_as_the_grouping_found_it (run_id, present)"
                + " SELECT NEW.run_id, COUNT(*) FROM sqlite_master WHERE type = 'index' AND name = '" + BY_HASH + "';"
                + " END");
    }

    @AfterEach
    void theFirstBuildAgain() {
        removeWhatThisClassAdded();
        SuccessiveBuildsBeans.theFirstBuild();
        ConverterStopsPartwayBeans.keepAnswering();
    }

    private void removeWhatThisClassAdded() {
        jdbcTemplate.execute("DROP TRIGGER IF EXISTS the_grouping_looks_for_the_hash_index");
        jdbcTemplate.execute("DROP TRIGGER IF EXISTS stage_three_stops");
        jdbcTemplate.execute("DROP TABLE IF EXISTS hash_index_as_the_grouping_found_it");
    }

    @Test
    @Story("When the index on word-sequence hashes exists")
    @DisplayName("After a new build that changes only the code that runs the stages, the count of repeated word sequences is made again with the hash index still in place")
    void aBuildThatMovesOnlyPipelineHasStageThreeGroupWithTheHashIndexThere(CapturedOutput output, @TempDir Path root)
            throws IOException {
        writeCorpus(root, "pipeline alone");
        openStageFoursGate();
        cli.run("run", root.toString());
        String extractionRun = onlyRunOf(root, StageModules.EXTRACTION);
        String firstCensus = onlyRunOf(root, StageModules.CONTENT_CENSUS);

        claim(
                "the first invocation counts the repeated word sequences with no hash index, the extraction"
                        + " before it having had everything to write",
                () -> assertThat(howTheGroupingFoundTheIndexUnder(firstCensus)).containsExactly(ABSENT));
        claim(
                "and ends with the hash index in place, built for the redundancy check",
                () -> assertThat(indexExists()).isTrue());

        SuccessiveBuildsBeans.aCommitTo("pipeline");
        ConverterStopsPartwayBeans.keepAnswering();
        int before = output.getAll().length();
        cli.run("run", root.toString());
        String second = output.getAll().substring(before);
        List<String> censusRuns = runsOf(root, StageModules.CONTENT_CENSUS);

        claim("the second invocation, under the new build, completes", () -> assertThat(cli.getExitCode())
                .isZero());
        claim(
                "its extraction is the one already finished, so it converts nothing",
                () -> assertThat(runsOf(root, StageModules.EXTRACTION)).containsExactly(extractionRun));
        claim(
                "and says nothing about removing the hash index, nor about building it, since it was there",
                () -> assertThat(second).doesNotContain(REMOVING).doesNotContain(BUILDING));
        claim(
                "the count of repeated word sequences is made again, under a run of its own, because the new"
                        + " build changed code it is identified by",
                () -> assertThat(censusRuns).hasSize(2).doesNotHaveDuplicates().startsWith(firstCensus));
        String secondCensus = censusRuns.getLast();
        claim("and it says it is starting under that run", () -> assertThat(second)
                .contains(STAGE_3_STARTING + secondCensus));
        claim(
                "every row that second count wrote was written with the hash index in the database",
                () -> assertThat(howTheGroupingFoundTheIndexUnder(secondCensus)).containsExactly(PRESENT));
        claim(
                "and with the index there the database plans that count through it, and not through the index"
                        + " by run it uses otherwise",
                () -> assertThat(planOfTheGroupingOver(extractionRun))
                        .contains("USING INDEX " + BY_HASH)
                        .doesNotContain("FOR GROUP BY"));
    }

    @Test
    @Story("When the index on word-sequence hashes exists")
    @DisplayName("After a new build that changes the code that makes the word sequences, the extraction removes the hash index before the repeated word sequences are counted again")
    void aBuildThatMovesSimilarityHasStageTwoDropTheHashIndexBeforeStageThreeGroups(
            CapturedOutput output, @TempDir Path root) throws IOException {
        writeCorpus(root, "similarity too");
        openStageFoursGate();
        cli.run("run", root.toString());
        String firstCensus = onlyRunOf(root, StageModules.CONTENT_CENSUS);

        claim("the first invocation ends with the hash index in place", () -> assertThat(indexExists())
                .isTrue());

        SuccessiveBuildsBeans.aCommitTo("similarity");
        ConverterStopsPartwayBeans.keepAnswering();
        int before = output.getAll().length();
        cli.run("run", root.toString());
        String second = output.getAll().substring(before);
        List<String> censusRuns = runsOf(root, StageModules.CONTENT_CENSUS);

        claim("the second invocation, under the new build, completes", () -> assertThat(cli.getExitCode())
                .isZero());
        claim(
                "its extraction runs again under a run of its own, the new build having changed code it is"
                        + " identified by too",
                () -> assertThat(runsOf(root, StageModules.EXTRACTION)).hasSize(2).doesNotHaveDuplicates());
        claim(
                "and removes the hash index before it writes, then the redundancy check builds it again",
                () -> assertThat(second).containsSubsequence(REMOVING, BUILDING));
        claim(
                "the count of repeated word sequences is made again too, under a run of its own",
                () -> assertThat(censusRuns).hasSize(2).doesNotHaveDuplicates().startsWith(firstCensus));
        claim(
                "so the second count of repeated word sequences found no hash index for any row it wrote",
                () -> assertThat(howTheGroupingFoundTheIndexUnder(censusRuns.getLast()))
                        .containsExactly(ABSENT));
    }

    @Test
    @Story("When the index on word-sequence hashes exists")
    @DisplayName("A count of repeated word sequences stopped over one folder is made with the hash index in place, once another folder kept in the same place has reached the redundancy check")
    void aStageThreeStoppedOverOneCorpusRootGroupsWithTheHashIndexAnotherCorpusRootsStageFourBBuilt(
            CapturedOutput output, @TempDir Path finished, @TempDir Path stopped) throws IOException {
        writeCorpus(finished, "the corpus that goes on");
        writeCorpus(stopped, "the corpus that stops");

        cli.run("run", finished.toString());
        jdbcTemplate.execute("CREATE TRIGGER stage_three_stops BEFORE INSERT ON finished_step WHEN NEW.step = '"
                + StepNames.CONTENT_CENSUS + "' BEGIN SELECT RAISE(ABORT, 'stage 3 stopped'); END");
        try {
            cli.run("run", stopped.toString());
        } finally {
            jdbcTemplate.execute("DROP TRIGGER IF EXISTS stage_three_stops");
        }
        String stoppedExtraction = onlyRunOf(stopped, StageModules.EXTRACTION);

        claim(
                "the invocation over the second folder stops while it counts the repeated word sequences",
                () -> assertThat(cli.getExitCode()).isNotZero());
        claim(
                "with its extraction finished",
                () -> assertThat(stepFinished(stoppedExtraction, StepNames.EXTRACTION)).isTrue());
        claim(
                "and nothing of its count kept, not even the run it was made under: the stop undid the whole"
                        + " of it",
                () -> assertThat(runsOf(stopped, StageModules.CONTENT_CENSUS)).isEmpty());
        claim(
                "and no hash index in the database: both extractions wrote, and nothing has built one since",
                () -> assertThat(indexExists()).isFalse());

        openStageFoursGate();
        cli.run("run", finished.toString());

        claim(
                "the first folder, invoked again with the redundancy check opened, ends with the hash index"
                        + " built over every word sequence the database holds, the second folder's included",
                () -> assertThat(indexExists()).isTrue());

        jdbcTemplate.update("DELETE FROM hash_index_as_the_grouping_found_it");
        ConverterStopsPartwayBeans.keepAnswering();
        int before = output.getAll().length();
        cli.run("run", stopped.toString());
        String resumed = output.getAll().substring(before);

        claim("the second folder, invoked again, completes", () -> assertThat(cli.getExitCode())
                .isZero());
        String stoppedCensus = onlyRunOf(stopped, StageModules.CONTENT_CENSUS);
        claim(
                "under the same extraction, which has nothing to write and says nothing about removing the"
                        + " hash index",
                () -> assertThat(resumed).doesNotContain(REMOVING));
        claim(
                "its count of repeated word sequences is made in this invocation",
                () -> assertThat(resumed).contains(STAGE_3_STARTING + stoppedCensus));
        claim(
                "over the extraction that finished before the stop, the only one this folder has had",
                () -> assertThat(runsOf(stopped, StageModules.EXTRACTION)).containsExactly(stoppedExtraction));
        claim(
                "and every row it wrote was written with the hash index in the database",
                () -> assertThat(howTheGroupingFoundTheIndexUnder(stoppedCensus)).containsExactly(PRESENT));
    }

    /**
     * Writes {@link #CORPUS_SIZE} files, each with bytes of its own, so stage 1 removes none as a copy of
     * another, and most of their words in common, so stage 3's grouping has rows to write. {@code salt}
     * keeps one test's corpus from sharing content with another's.
     */
    private static void writeCorpus(Path root, String salt) throws IOException {
        for (int position = 1; position <= CORPUS_SIZE; position++) {
            Files.writeString(
                    root.resolve(String.format("%02d-plain.txt", position)),
                    "Document " + position + " of " + salt + " describes harbour cranes, tide tables"
                            + " and the order in which ships were unloaded during the winter of year " + position
                            + ", with notes on weather and cargo.");
        }
    }

    private void openStageFoursGate() {
        profileStore.save(ProfileFixture.profile()
                .boilerplateDocumentFrequencyFloor(BOILERPLATE_FLOOR, "set by this test, so stage 4's gate is open")
                .build());
    }

    /** Every run of {@code stage} over {@code root}, in the order they were minted. */
    private List<String> runsOf(Path root, StageModules stage) {
        return jdbcTemplate.queryForList(
                "SELECT r.id FROM run r JOIN walk w ON w.id = r.walk_id"
                        + " WHERE w.root = ? AND r.stage = ? ORDER BY r.rowid",
                String.class,
                Walk.canonicalRoot(root).toString(),
                stage.stage());
    }

    private String onlyRunOf(Path root, StageModules stage) {
        List<String> runs = runsOf(root, stage);
        claim(
                "the stage named " + stage.stage() + " has one run over this folder so far, so the claims below"
                        + " are about that run",
                () -> assertThat(runs).hasSize(1));
        return runs.getFirst();
    }

    private boolean stepFinished(String run, String step) {
        Long rows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM finished_step WHERE run_id = ? AND step = ?", Long.class, run, step);
        return rows != null && rows > 0;
    }

    private boolean indexExists() {
        Long rows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sqlite_master WHERE type = 'index' AND tbl_name = 'shingle' AND name = ?",
                Long.class,
                BY_HASH);
        return rows != null && rows > 0;
    }

    /** The distinct answers the trigger recorded for the rows written under {@code censusRun}: 1, 0, both or none. */
    private List<Integer> howTheGroupingFoundTheIndexUnder(String censusRun) {
        return jdbcTemplate.queryForList(
                "SELECT DISTINCT present FROM hash_index_as_the_grouping_found_it WHERE run_id = ? ORDER BY present",
                Integer.class,
                censusRun);
    }

    private String planOfTheGroupingOver(String extractionRun) {
        return String.join(
                " | ",
                jdbcTemplate.query(
                        "EXPLAIN QUERY PLAN " + GROUPING,
                        (resultSet, rowNumber) -> resultSet.getString("detail"),
                        extractionRun));
    }
}
