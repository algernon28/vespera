package io.algernon.vespera.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.corpus.Walk;
import io.algernon.vespera.extraction.ConverterStopsPartwayBeans;
import io.algernon.vespera.ledger.SuccessiveBuildsBeans;
import io.algernon.vespera.profile.ProfileFixture;
import io.algernon.vespera.profile.ProfileStore;
import io.algernon.vespera.similarity.TheRunsHashIndex;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.DynamicPropertyRegistry;

/**
 * What a working directory keeps of a stage-2 run, and of a stage-4 run, once a later invocation arrives at
 * another run of the same stage (#468, following ADR-218's "What this does not decide").
 *
 * <p><b>The rows stay, and the index is over the run in hand (ADR-221).</b> Nothing in {@code src/main}
 * deletes a row of {@code shingle}, and the only delete of {@code minhash_signature} and {@code
 * signature_band} is a stage 4a discarding its own unfinished work under its own run. So each new run of
 * stage 2 writes its rows beside the earlier runs', and each new run of stage 4 writes its signatures beside
 * the earlier runs'. Stage 4b builds {@code shingle_by_hash} over the rows of the stage-2 run it reads and
 * no other's, reading every row the table keeps to find them, and says both. The first test drives the
 * ordinary sequences in one working directory and counts rows by run after each. The second removes one
 * run's rows itself, as a decision to remove them would (#481), and shows what a later run of stage 4 over
 * that stage-2 run then does.
 *
 * <p>Since ADR-222 a build that moves {@code pipeline} alone mints no run of stage 4, which no longer names
 * it. So the first test's second stage-4 run comes from a changed boilerplate floor, the value stage 4's run
 * records, over the same stage-2 run; a build that moves {@code pipeline} alone is claimed to add nothing.
 *
 * <p>Written for #468 as a characterisation of the build over every run's rows. The first test's claims
 * about the line before the build turned with ADR-221, in the two places it reads that line: the line
 * stated one count, of the rows it built over, and now names a run and states the rows it reads. In the
 * same two places a claim was added that the index in the database is that run's. Every claim on the rows
 * kept stands as written.
 *
 * <p>The corpus is the one {@code StageThreeMeetsTheHashIndexInvocationTest} writes: each text is the
 * occurrence's own bytes, so shingles differ and stage 4 signs something, which the first claims hold.
 */
@CascadeSliceTest
@Import({ConverterStopsPartwayBeans.class, SuccessiveBuildsBeans.class})
@ExtendWith(OutputCaptureExtension.class)
@Epic("Redundancy")
@Feature("Shingling")
@Issue("468")
@Link(name = "ADR-221", url = Adr.THE_HASH_INDEX_IS_OVER_THE_ROWS_OF_THE_RUN_IN_HAND, type = "adr")
@Link(name = "ADR-182", url = Adr.STAGE_4B_BUILDS_THE_BY_HASH_INDEX_STAGE_2_WRITES_WITHOUT, type = "adr")
@Link(name = "ADR-218", url = Adr.EVERY_STATEMENT_WHOSE_TEMPORARY_FILES_GROW_IS_AN_EXCEPTION_WITH_ITS_SIZE, type = "adr")
@Link(name = "ADR-156", url = Adr.A_RUNS_SURVIVORS_ARE_READ_THROUGH_ITS_UPSTREAM_RUNS, type = "adr")
class AnEarlierRunsRowsStayInvocationTest {

    /** Three commits' worth of stage 2, as {@code ShingleHashIndexInvocationTest} writes. */
    private static final int CORPUS_SIZE = 3 * ExtractionJobConfiguration.CHUNK_SIZE;

    /** A boilerplate floor set only to open stage 4's gate. */
    private static final String BOILERPLATE_FLOOR = "1.0";

    /** Another floor, which mints a second run of stage 4 over the same stage-2 run. */
    private static final String ANOTHER_BOILERPLATE_FLOOR = "0.9";

    /** The value that has stage 2 ask the converter again under a run of its own (ADR-185). */
    private static final String THE_SECOND_ATTEMPT = "2";

    /**
     * The line stage 4b writes before it builds the index (ADR-221 section 5), with the run it builds for and
     * the rows it reads to find that run's.
     */
    private static final Pattern BUILDING = Pattern.compile("Stage 4b \\(redundancy resolution\\) is building"
            + " shingle_by_hash over the rows of run ([0-9a-f]{64}) alone, reading up to (\\d+) shingle rows");

    /** The start of the line stage 2 writes before it drops the index. */
    private static final String REMOVING = "Stage 2 (extraction) is removing shingle_by_hash";

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

    /** The converter's script and the build's versions are static and shared with other classes. */
    @BeforeEach
    void startClean() {
        ConverterStopsPartwayBeans.script(List.of(), List.of(), List.of());
        ConverterStopsPartwayBeans.keepAnswering();
        SuccessiveBuildsBeans.theFirstBuild();
        openStageFoursGate(BOILERPLATE_FLOOR);
    }

    @AfterEach
    void theFirstBuildAgain() {
        SuccessiveBuildsBeans.theFirstBuild();
        ConverterStopsPartwayBeans.keepAnswering();
    }

    @Test
    @Story("What is kept of an earlier extraction")
    @DisplayName("Each new extraction of the same folder adds its word sequences beside the earlier ones, nothing removes them, and the hash index is built over all of them")
    void everyRunOfStageTwoLeavesItsShingleRowsAndStageFourBBuildsOverAllOfThem(
            CapturedOutput output, @TempDir Path root) throws IOException {
        writeCorpus(root, "kept", CORPUS_SIZE);

        cli.run("run", root.toString());
        String first = onlyRunOf(root, StageModules.EXTRACTION);
        String firstRedundancy = onlyRunOf(root, StageModules.CONTENT_REDUNDANCY);
        long shingles = rowsUnder("shingle", first);
        long bands = rowsUnder("signature_band", firstRedundancy);
        long signatures = rowsUnder("minhash_signature", firstRedundancy);

        claim("the first invocation writes word sequences under its extraction", () -> assertThat(shingles)
                .isPositive());
        claim(
                "and signs some of the " + CORPUS_SIZE + " texts for the redundancy check, sixteen bands a"
                        + " signature, so the counts below are of rows that exist",
                () -> assertThat(bands).isPositive().isEqualTo(16 * signatures));

        // A build that moves pipeline alone: neither stage 2 nor stage 4 names pipeline (ADR-222), so both
        // keep their runs.
        SuccessiveBuildsBeans.aCommitTo("pipeline");
        cli.run("run", root.toString());

        claim(
                "a new build that changes only the code that runs the stages extracts nothing again and adds no"
                        + " word sequence",
                () -> assertThat(shingleRowsByRun(root)).containsExactly(shingles));
        claim(
                "nor is the redundancy check made again: it has the one run it had, with the signatures it"
                        + " wrote",
                () -> assertThat(rowsByRun("signature_band", runsOf(root, StageModules.CONTENT_REDUNDANCY)))
                        .containsExactly(bands));

        // A changed boilerplate floor: stage 4 has a new run, over the same stage-2 run.
        openStageFoursGate(ANOTHER_BOILERPLATE_FLOOR);
        cli.run("run", root.toString());
        List<Long> bandsByRun = rowsByRun("signature_band", runsOf(root, StageModules.CONTENT_REDUNDANCY));

        claim(
                "a changed value the redundancy check is identified by has it made again under a run of its"
                        + " own, with no word sequence added, the extraction being the same",
                () -> assertThat(shingleRowsByRun(root)).containsExactly(shingles));
        claim(
                "and that second check's signatures are written beside the first one's, which stay as they"
                        + " were",
                () -> assertThat(bandsByRun).hasSize(2).startsWith(bands).allSatisfy(rows -> assertThat(rows)
                        .isPositive()));

        // A build that moves similarity: stage 2 has a new run, over the same walk.
        SuccessiveBuildsBeans.aCommitTo("similarity");
        ConverterStopsPartwayBeans.keepAnswering();
        int before = output.getAll().length();
        cli.run("run", root.toString());
        String afterSimilarity = output.getAll().substring(before);

        claim(
                "a new build that changes the code that makes the word sequences extracts again under a run of"
                        + " its own, and that run's word sequences are written beside the first one's, which stay",
                () -> assertThat(shingleRowsByRun(root)).containsExactly(shingles, shingles));
        claim(
                "with no text sent to the converter again, every conversion having been kept",
                () -> assertThat(ConverterStopsPartwayBeans.conversions()).isZero());
        String secondExtraction = runsOf(root, StageModules.EXTRACTION).getLast();
        claim(
                "the hash index is then built over the rows of the extraction in hand, the second, by the run"
                        + " its own line names",
                () -> assertThat(runTheBuildNamed(afterSimilarity)).isEqualTo(secondExtraction));
        claim(
                "reading more rows than that extraction wrote to find them: at least both extractions' rows,"
                        + " by the count the same line states",
                () -> assertThat(rowsTheBuildSaidItReads(afterSimilarity)).isGreaterThanOrEqualTo(2 * shingles));
        claim(
                "and the index in the database is over the second extraction's rows and not the first's, which"
                        + " stay in the table outside it",
                () -> assertThat(TheRunsHashIndex.statementInTheDatabase(jdbcTemplate))
                        .contains(TheRunsHashIndex.statementFor(secondExtraction)));
        claim(
                "every earlier extraction keeps its measurements too, one for each of the " + CORPUS_SIZE
                        + " texts under each run",
                () -> assertThat(rowsByRun("extraction_metric", runsOf(root, StageModules.EXTRACTION)))
                        .containsExactly((long) CORPUS_SIZE, (long) CORPUS_SIZE));

        // A value stage 2's run records is changed: a third run, over the same walk.
        ExtractionAttemptInProfile.write(profileStore, THE_SECOND_ATTEMPT, "written by this test");
        cli.run("run", root.toString());

        claim(
                "a changed answer in the profile that the extraction is identified by adds a third copy",
                () -> assertThat(shingleRowsByRun(root)).containsExactly(shingles, shingles, shingles));

        // The value is put back: the invocation arrives at the second run again (ADR-156).
        ExtractionAttemptInProfile.remove(profileStore);
        ConverterStopsPartwayBeans.keepAnswering();
        before = output.getAll().length();
        cli.run("run", root.toString());
        String afterPuttingBack = output.getAll().substring(before);

        claim("the invocation after the answer is put back completes", () -> assertThat(cli.getExitCode())
                .isZero());
        claim(
                "under the extraction made before the change, with no new run, nothing converted and nothing"
                        + " written",
                () -> assertThat(shingleRowsByRun(root)).containsExactly(shingles, shingles, shingles));
        claim("so it converts nothing", () -> assertThat(ConverterStopsPartwayBeans.conversions())
                .isZero());
        claim(
                "and it neither removes nor builds the hash index, every stage being finished under the runs"
                        + " it arrives at",
                () -> assertThat(afterPuttingBack).doesNotContain(REMOVING).doesNotContainPattern(BUILDING));

        // The build installed first is run again: the invocation arrives at the first runs.
        SuccessiveBuildsBeans.theFirstBuild();
        ConverterStopsPartwayBeans.keepAnswering();
        cli.run("run", root.toString());

        claim(
                "the first build, run again, arrives at the first extraction and adds nothing either",
                () -> assertThat(shingleRowsByRun(root)).containsExactly(shingles, shingles, shingles));
        claim(
                "nor a run of the redundancy check: it has the four it had, one for the first build, one for"
                        + " the changed value of its own, one for the build that changed how word sequences"
                        + " are made, and one for the changed answer the extraction is identified by",
                () -> assertThat(runsOf(root, StageModules.CONTENT_REDUNDANCY)).hasSize(4));

        // The archive changes: a new walk, and every run over it is new.
        Files.writeString(root.resolve("49-plain.txt"), text(CORPUS_SIZE + 1, "kept"));
        before = output.getAll().length();
        cli.run("run", root.toString());
        String afterTheArchiveChanged = output.getAll().substring(before);
        List<Long> byRun = shingleRowsByRun(root);

        claim(
                "one text added to the folder has the whole folder extracted under a fourth run, whose word"
                        + " sequences are every text's and not only the new one's; the three earlier copies stay",
                () -> assertThat(byRun).hasSize(4).startsWith(shingles, shingles, shingles));
        claim("the fourth copy holding the new text's as well", () -> assertThat(byRun.getLast())
                .isGreaterThan(shingles));
        String fourthExtraction = runsOf(root, StageModules.EXTRACTION).getLast();
        claim(
                "the hash index is built over the fourth copy alone, by the run its line names",
                () -> assertThat(runTheBuildNamed(afterTheArchiveChanged)).isEqualTo(fourthExtraction));
        claim(
                "and all four copies are read to find it, by the count the line states",
                () -> assertThat(rowsTheBuildSaidItReads(afterTheArchiveChanged))
                        .isGreaterThanOrEqualTo(3 * shingles + byRun.getLast()));
        claim(
                "the index in the database being over the fourth extraction's rows",
                () -> assertThat(TheRunsHashIndex.statementInTheDatabase(jdbcTemplate))
                        .contains(TheRunsHashIndex.statementFor(fourthExtraction)));
    }

    @Test
    @Story("What is kept of an earlier extraction")
    @DisplayName("With an extraction's word sequences gone from the database, a redundancy check made again over that extraction signs nothing, removes nothing and reports no error")
    void aRunOfStageFourOverAStageTwoRunWhoseShingleRowsAreGoneSignsNothingAndFailsNothing(@TempDir Path root)
            throws IOException {
        writeCorpus(root, "gone", CORPUS_SIZE);
        cli.run("run", root.toString());
        String extraction = onlyRunOf(root, StageModules.EXTRACTION);
        String firstRedundancy = onlyRunOf(root, StageModules.CONTENT_REDUNDANCY);

        claim(
                "the first redundancy check signs some of the texts",
                () -> assertThat(rowsUnder("minhash_signature", firstRedundancy)).isPositive());

        // What removing a run's rows would do, done here by the test: nothing that ships does it.
        jdbcTemplate.update("DELETE FROM shingle WHERE run_id = ?", extraction);
        openStageFoursGate(ANOTHER_BOILERPLATE_FLOOR);
        ConverterStopsPartwayBeans.keepAnswering();
        cli.run("run", root.toString());
        List<String> redundancy = runsOf(root, StageModules.CONTENT_REDUNDANCY);

        claim("the invocation after the rows are removed completes", () -> assertThat(cli.getExitCode())
                .isZero());
        claim(
                "under the same extraction, which is recorded as finished and so writes nothing again",
                () -> assertThat(runsOf(root, StageModules.EXTRACTION)).containsExactly(extraction));
        claim("and converts nothing", () -> assertThat(ConverterStopsPartwayBeans.conversions())
                .isZero());
        claim(
                "the changed answer has the redundancy check made again under a second run",
                () -> assertThat(redundancy).hasSize(2).startsWith(firstRedundancy));
        claim(
                "which finds no word sequence to sign and signs nothing, where the first signed some",
                () -> assertThat(rowsUnder("minhash_signature", redundancy.getLast()))
                        .isZero());
    }

    /**
     * Writes {@code size} texts, each with bytes of its own, so stage 1 removes none as a copy of another.
     * {@code salt} keeps one test's corpus from sharing content with another's.
     */
    private static void writeCorpus(Path root, String salt, int size) throws IOException {
        for (int position = 1; position <= size; position++) {
            Files.writeString(root.resolve(String.format("%02d-plain.txt", position)), text(position, salt));
        }
    }

    private static String text(int position, String salt) {
        return "Document " + position + " of " + salt + " describes harbour cranes, tide tables"
                + " and the order in which ships were unloaded during the winter of year " + position
                + ", with notes on weather and cargo.";
    }

    private void openStageFoursGate(String floor) {
        profileStore.save(ProfileFixture.profile()
                .boilerplateDocumentFrequencyFloor(floor, "set by this test, so stage 4's gate is open")
                .build());
    }

    /** Every run of {@code stage} over {@code root}, whichever walk of it, in the order they were minted. */
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

    private long rowsUnder(String table, String run) {
        Long rows = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE run_id = ?", Long.class, run);
        return rows == null ? 0 : rows;
    }

    private List<Long> rowsByRun(String table, List<String> runs) {
        return runs.stream().map(run -> rowsUnder(table, run)).toList();
    }

    /** The shingle rows under each run of stage 2 over {@code root}, in the order the runs were minted. */
    private List<Long> shingleRowsByRun(Path root) {
        return rowsByRun("shingle", runsOf(root, StageModules.EXTRACTION));
    }

    /** The run named in the one line about building the index that {@code said} holds. */
    private static String runTheBuildNamed(String said) {
        return theBuildingLineOf(said).group(1);
    }

    /** The rows that line says the build reads. */
    private static long rowsTheBuildSaidItReads(String said) {
        return Long.parseLong(theBuildingLineOf(said).group(2));
    }

    private static Matcher theBuildingLineOf(String said) {
        Matcher line = BUILDING.matcher(said);
        claim(
                "the invocation says it is building the hash index, over the rows of one run it names, and how"
                        + " many rows it reads to find them",
                () -> assertThat(line.find()).isTrue());
        return line;
    }
}
