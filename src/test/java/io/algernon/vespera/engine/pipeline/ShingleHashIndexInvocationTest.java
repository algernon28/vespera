package io.algernon.vespera.engine.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.engine.corpus.Walk;
import io.algernon.vespera.engine.extraction.ConverterStopsPartwayBeans;
import io.algernon.vespera.engine.profile.ProfileFixture;
import io.algernon.vespera.engine.profile.ProfileStore;
import io.algernon.vespera.engine.similarity.TheRunsHashIndex;
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
 * When {@code shingle_by_hash} exists, across stops, resumes and re-runs (ADR-182, #381): never while
 * stage 2 writes shingles, and always by the time stage 4b's containment retrieval reads it.
 *
 * <p>Stage 2 drops the index before its first chunk whenever it has work to do, and stage 4b builds it,
 * announced, before its first read whenever it is missing. Each test drives whole invocations over
 * {@link ConverterStopsPartwayBeans}, the converter ADR-181's tests stop partway, and reads what the
 * database holds between them. A stop leaves the database as it stood while stage 2 was writing, which
 * is how a test sees the index's absence during stage 2 without reaching into the step.
 *
 * <p><b>There is one index of that name in the database, and since ADR-221 it is over the rows of one
 * stage-2 run</b>: the run of whichever stage 4b built it last. So every test starts from whatever another
 * test's invocations left in this context's database, an index of another corpus's run as a rule, and none
 * relies on it: where a claim needs an index present beforehand, the test plants one, and every claim about
 * absence follows an invocation that had to drop it. The last three tests are ADR-221's own: stage 4b
 * leaves its run's index alone, and builds it again where the one it finds is another run's or is the
 * index over every run's rows that an earlier build left.
 *
 * <p>A schema that created the index and a stage 2 that did not drop it would fail every claim that it is
 * absent, and announce no build, because none would be made. The test of an invocation with every step
 * finished is what stops an implementation from dropping the index on every invocation and paying for the
 * build again on the next (#681).
 *
 * <p>The containment plans are of a read of one hash's occurrences written out here as {@code
 * RedundancyResolution} sends it: they hold that the index can answer that read, not which statement the
 * search sends.
 */
@CascadeSliceTest
@Import(ConverterStopsPartwayBeans.class)
@ExtendWith(OutputCaptureExtension.class)
@Epic("Redundancy")
@Feature("Shingling")
@Issue("381")
@Issue("468")
@Link(name = "ADR-182", url = Adr.STAGE_4B_BUILDS_THE_BY_HASH_INDEX_STAGE_2_WRITES_WITHOUT, type = "adr")
@Link(name = "ADR-221", url = Adr.THE_HASH_INDEX_IS_OVER_THE_ROWS_OF_THE_RUN_IN_HAND, type = "adr")
class ShingleHashIndexInvocationTest {

    /** How many occurrences one commit of stage 2 holds. */
    private static final int CHUNK = ExtractionJobConfiguration.CHUNK_SIZE;

    /** Three chunks' worth, so a stop can fall after some have committed and before the last. */
    private static final int CORPUS_SIZE = 3 * CHUNK;

    /** Two whole chunks and half of the third: the stop lands inside a chunk, never on its edge. */
    private static final int ANSWERED_BEFORE_THE_STOP = 2 * CHUNK + CHUNK / 2;

    /** A boilerplate floor no shingle reaches below every document, set only to open stage 4's gate. */
    private static final String BOILERPLATE_FLOOR = "1.0";

    /** The index containment retrieval reads. */
    private static final String BY_HASH = "shingle_by_hash";

    /**
     * How a database last run under a build before ADR-221 holds the index: over every run's rows. {@code IF
     * NOT EXISTS} so that planting it where another test left an index of any form leaves that one, which
     * serves the claims as well: stage 2 removes whatever is there.
     */
    private static final String BUILD_BY_HASH = "CREATE INDEX IF NOT EXISTS shingle_by_hash"
            + " ON shingle (run_id, shingle_parameter_identity, shingle_hash)";

    /** Another boilerplate floor, which mints a second run of stage 4 over the same stage-2 run. */
    private static final String ANOTHER_BOILERPLATE_FLOOR = "0.9";

    /** The start of the line stage 4b writes before it builds the index (ADR-182 §2.4). */
    private static final String BUILDING = "Stage 4b (redundancy resolution) is building shingle_by_hash";

    /** The start of the line stage 4b writes once the build has committed. */
    private static final String BUILT = "Stage 4b (redundancy resolution) built shingle_by_hash in";

    /** The start of the line stage 4b writes as its resolution begins. */
    private static final String RESOLUTION_STARTING = "Stage 4b (redundancy resolution) starting under run";

    /** One file added between two invocations, which is enough to make it a different observation. */
    private static final int ADDED = 1;

    /**
     * A granularity identity to bind in a query plan. The plan does not depend on its value, and the
     * real one is {@code similarity}'s own.
     */
    private static final String A_GRANULARITY = "a-granularity";

    /** Nothing at all. */
    private static final long NONE = 0;

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
     * The converter forgets every scripted and pinned outcome, answers, and counts from zero, and stage
     * 4's gate is shut until a test opens it. The script, the pins and the count are static and shared
     * with every other class importing the converter, and class order differs between machines.
     */
    @BeforeEach
    void startClean() {
        ConverterStopsPartwayBeans.script(List.of(), List.of(), List.of());
        ConverterStopsPartwayBeans.keepAnswering();
        profileStore.save(ProfileFixture.profile().build());
    }

    @AfterEach
    void bringTheConverterBack() {
        ConverterStopsPartwayBeans.keepAnswering();
    }

    @Test
    @Story("When the index on word-sequence hashes exists")
    @DisplayName("An extraction stopped partway has written its word sequences with no hash index, and the invocation that finishes it builds one before the redundancy check reads it")
    void aStoppedStageTwoHasNoHashIndexAndTheInvocationThatResumesItBuildsOneBeforeStageFourBReads(
            CapturedOutput output, @TempDir Path root) throws IOException {
        writeCorpus(root, "stopped and resumed");
        jdbcTemplate.execute(BUILD_BY_HASH);

        ConverterStopsPartwayBeans.stopAnsweringAfter(ANSWERED_BEFORE_THE_STOP);
        cli.run("run", root.toString());
        String extractionRun = onlyRunOf(root, StageModules.EXTRACTION);

        claim(
                "the first invocation stops, because the converter stopped answering partway through",
                () -> assertThat(cli.getExitCode()).isNotZero());
        claim(
                "and before it stopped it had saved the word sequences of at least one batch, so the extraction"
                        + " had begun writing",
                () -> assertThat(shingleRowsUnder(extractionRun)).isGreaterThan(NONE));
        claim(
                "yet the database holds no index on the word-sequence hash, though one was there when the"
                        + " invocation began: the extraction removes it before it writes, rather than adding"
                        + " every row to it at a random place",
                () -> assertThat(indexExists(BY_HASH)).isFalse());

        openStageFoursGate();
        ConverterStopsPartwayBeans.keepAnswering();
        int before = output.getAll().length();
        cli.run("run", root.toString());
        String second = output.getAll().substring(before);

        claim(
                "the second invocation finishes the extraction under the same run as the first",
                () -> assertThat(stepFinished(extractionRun, StepNames.EXTRACTION)).isTrue());
        String redundancyRun = onlyRunOf(root, StageModules.CONTENT_REDUNDANCY);
        claim(
                "and goes on to finish the redundancy check over it",
                () -> assertThat(stepFinished(redundancyRun, StepNames.CONTENT_REDUNDANCY)).isTrue());
        claim(
                "and it ends with the hash index on the measurement settings, the hash and the document, in"
                        + " that order, which is what the search for containing documents looks up and reads"
                        + " in order, built over the word sequences of this extraction and no other's",
                () -> assertThat(TheRunsHashIndex.statementInTheDatabase(jdbcTemplate))
                        .contains(TheRunsHashIndex.statementFor(extractionRun)));
        claim(
                "it says it is building that index, and then that it has built it, before the redundancy check"
                        + " starts resolving, so a long build is not mistaken for a run that has stopped moving",
                () -> assertThat(second).containsSubsequence(BUILDING, BUILT, RESOLUTION_STARTING));
        claim(
                "and a read of one hash's occurrences in this extraction, as the search for documents containing"
                        + " another writes it, is planned through it",
                () -> assertThat(containmentPlanFor(extractionRun)).contains(BY_HASH));
    }

    /**
     * A file added between the invocations makes the second a different observation, hence a different
     * walk and a different stage-2 run (ADR-115); that is the lever a test has on the run id, as in
     * ADR-181's tests.
     *
     * <p>The new run has to stop partway, or its stage 4b builds the index again and the drop cannot be
     * seen. Conversions are cached outside the run (ADR-070), so a second invocation over the same files
     * would ask the converter only about the added one and never reach the stop. The test empties
     * {@code extraction_cache} first, as {@link ConverterStopsPartwayBeans} says to, and claims the stop
     * happened. Emptying the cache changes nothing a run id is derived from.
     */
    @Test
    @Story("When the index on word-sequence hashes exists")
    @DisplayName("An extraction under changed conditions removes the hash index an earlier run built, and leaves that run's word sequences alone")
    void aStageTwoUnderANewRunDropsTheIndexAnEarlierRunBuiltAndLeavesThatRunsRows(@TempDir Path root)
            throws IOException {
        writeCorpus(root, "changed between invocations");
        openStageFoursGate();
        cli.run("run", root.toString());
        String earlier = onlyRunOf(root, StageModules.EXTRACTION);
        long earlierShingles = shingleRowsUnder(earlier);

        claim(
                "the first invocation finishes the redundancy check and ends with the hash index in place",
                () -> assertThat(indexExists(BY_HASH)).isTrue());

        Files.writeString(root.resolve("99-added-later.txt"), "A document added after the first invocation finished.");
        jdbcTemplate.update("DELETE FROM extraction_cache");
        ConverterStopsPartwayBeans.stopAnsweringAfter(ANSWERED_BEFORE_THE_STOP);
        cli.run("run", root.toString());
        List<String> runs = runsOf(root, StageModules.EXTRACTION);

        claim(
                "the second invocation stops partway through its extraction, because the converter stopped"
                        + " answering, so it never reaches the redundancy check that would build the index again",
                () -> assertThat(cli.getExitCode()).isNotZero());
        claim(
                "the second invocation extracts its " + (CORPUS_SIZE + ADDED) + " documents under a run of its"
                        + " own, because the corpus it reads is not the one the first read",
                () -> assertThat(runs).hasSize(2).doesNotHaveDuplicates());
        claim(
                "the database then holds no hash index: the new extraction removed the one the earlier run's"
                        + " redundancy check had built, before it wrote anything",
                () -> assertThat(indexExists(BY_HASH)).isFalse());
        claim(
                "and the " + earlierShingles + " word sequences the earlier run saved are as they were: removing"
                        + " an index removes no row",
                () -> assertThat(shingleRowsUnder(earlier)).isEqualTo(earlierShingles));
    }

    /**
     * Writes {@link #CORPUS_SIZE} files whose names sort in position order, each with bytes of its own, so
     * stage 1 removes none as a copy of another. {@code salt} keeps one test's corpus from sharing content
     * with another's.
     */
    private static void writeCorpus(Path root, String salt) throws IOException {
        for (int position = 1; position <= CORPUS_SIZE; position++) {
            Files.writeString(
                    root.resolve(String.format("%02d-plain.txt", position)),
                    "Document " + position + " of the " + salt + " corpus describes harbour cranes, tide tables"
                            + " and the order in which ships were unloaded during the winter of year " + position
                            + ", with notes on weather and cargo.");
        }
    }

    private void openStageFoursGate() {
        openStageFoursGate(BOILERPLATE_FLOOR);
    }

    private void openStageFoursGate(String floor) {
        profileStore.save(ProfileFixture.profile()
                .boilerplateDocumentFrequencyFloor(floor, "set by this test, so stage 4's gate is open")
                .build());
    }

    /** Every run of {@code stage} over {@code root}, oldest walk first. */
    private List<String> runsOf(Path root, StageModules stage) {
        return jdbcTemplate.queryForList(
                "SELECT r.id FROM run r JOIN walk w ON w.id = r.walk_id"
                        + " WHERE w.root = ? AND r.stage = ? ORDER BY w.id",
                String.class,
                Walk.canonicalRoot(root).toString(),
                stage.stage());
    }

    private String onlyRunOf(Path root, StageModules stage) {
        List<String> runs = runsOf(root, stage);
        String which = stage == StageModules.EXTRACTION ? "the extraction" : "the redundancy check";
        claim(
                which + " over this corpus has one run so far, so the claims below are about that run",
                () -> assertThat(runs).hasSize(1));
        return runs.getFirst();
    }

    private boolean stepFinished(String run, String step) {
        Long rows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM finished_step WHERE run_id = ? AND step = ?", Long.class, run, step);
        return rows != null && rows > NONE;
    }

    private long shingleRowsUnder(String run) {
        Long rows = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM shingle WHERE run_id = ?", Long.class, run);
        return rows == null ? NONE : rows;
    }

    private boolean indexExists(String index) {
        Long rows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sqlite_master WHERE type = 'index' AND tbl_name = 'shingle' AND name = ?",
                Long.class,
                index);
        return rows != null && rows > NONE;
    }

    private List<String> columnsOf(String index) {
        return jdbcTemplate.queryForList(
                "SELECT name FROM pragma_index_info(?) ORDER BY seqno", String.class, index);
    }

    /**
     * SQLite's plan for containment retrieval's read of one hash's occurrences, as {@code RedundancyResolution}
     * sends it since ADR-225 section 2, with {@code run} written into its text since ADR-237.
     */
    private String containmentPlanFor(String run) {
        return String.join(
                " | ",
                jdbcTemplate.query(
                        "EXPLAIN QUERY PLAN SELECT DISTINCT occurrence_id FROM shingle"
                                + " WHERE run_id = '" + run + "' AND shingle_parameter_identity = ? AND shingle_hash = ?"
                                + " AND occurrence_id > ? ORDER BY occurrence_id LIMIT 1000",
                        (resultSet, rowNumber) -> resultSet.getString("detail"),
                        A_GRANULARITY,
                        NONE,
                        NONE));
    }
}
