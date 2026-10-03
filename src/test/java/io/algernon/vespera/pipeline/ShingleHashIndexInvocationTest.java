package io.algernon.vespera.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.corpus.Walk;
import io.algernon.vespera.extraction.ConverterStopsPartwayBeans;
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
import java.util.ArrayList;
import java.util.Collections;
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
 * <p><b>The index is one per table, not one per run.</b> So every test starts from whatever another
 * test's invocations left in this context's database, and none relies on it: where a claim needs the
 * index present beforehand, the test builds it as stage 4b would, and every claim about absence follows
 * an invocation that had to drop it.
 *
 * <p>Fail today: the shipped schema creates the index and nothing drops it, so every claim that it is
 * absent fails, and nothing announces a build, because none is made. The test of an invocation with
 * every step finished passes today and has to go on passing: it is what stops an implementation from
 * dropping the index on every invocation and paying for the build again on the next.
 */
@CascadeSliceTest
@Import(ConverterStopsPartwayBeans.class)
@ExtendWith(OutputCaptureExtension.class)
@Epic("Redundancy")
@Feature("Shingling")
@Issue("381")
@Link(name = "ADR-182", url = Adr.STAGE_4B_BUILDS_THE_BY_HASH_INDEX_STAGE_2_WRITES_WITHOUT, type = "adr")
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

    /** Its columns, in order, as ADR-081 gave them and ADR-182 keeps them. */
    private static final List<String> BY_HASH_COLUMNS = List.of("run_id", "shingle_parameter_identity", "shingle_hash");

    /** How an earlier stage 4b, or a database made before ADR-182, leaves the index. */
    private static final String BUILD_BY_HASH = "CREATE INDEX IF NOT EXISTS shingle_by_hash"
            + " ON shingle (run_id, shingle_parameter_identity, shingle_hash)";

    /** The start of the line stage 4b writes before it builds the index (ADR-182 §2.4). */
    private static final String BUILDING = "Stage 4b (redundancy resolution) is building shingle_by_hash";

    /** The start of the line stage 4b writes once the build has committed. */
    private static final String BUILT = "Stage 4b (redundancy resolution) built shingle_by_hash in";

    /** The start of the line stage 4b writes as its resolution begins. */
    private static final String RESOLUTION_STARTING = "Stage 4b (redundancy resolution) starting under run";

    /** How many rare shingles containment retrieval asks about for one occurrence (ADR-081). */
    private static final int RARE_SHINGLES = 32;

    /** How many of those another occurrence must hold to be a containment candidate (ADR-081). */
    private static final int HITS_FOR_A_CANDIDATE = 24;

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

    /** The converter answers and counts from zero, and stage 4's gate is shut until a test opens it. */
    @BeforeEach
    void startClean() {
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
                "and it ends with the hash index on the run, the measurement settings and the hash, in that"
                        + " order, which is what the search for containing documents looks up",
                () -> assertThat(columnsOf(BY_HASH)).containsExactlyElementsOf(BY_HASH_COLUMNS));
        claim(
                "it says it is building that index, and then that it has built it, before the redundancy check"
                        + " starts resolving, so a long build is not mistaken for a run that has stopped moving",
                () -> assertThat(second).containsSubsequence(BUILDING, BUILT, RESOLUTION_STARTING));
        claim(
                "and the search for documents containing another is answered through it",
                () -> assertThat(containmentPlanFor(extractionRun)).contains(BY_HASH));
    }

    @Test
    @Story("When the index on word-sequence hashes exists")
    @DisplayName("An extraction finished while the redundancy check waits for its value leaves no hash index, and the invocation that opens the check builds it without converting anything again")
    void aFinishedStageTwoBehindTheShutGateLeavesNoHashIndexAndOpeningTheGateBuildsIt(
            CapturedOutput output, @TempDir Path root) throws IOException {
        writeCorpus(root, "finished behind the gate");
        jdbcTemplate.execute(BUILD_BY_HASH);

        cli.run("run", root.toString());
        String extractionRun = onlyRunOf(root, StageModules.EXTRACTION);
        long savedShingles = shingleRowsUnder(extractionRun);

        claim(
                "the first invocation finishes the extraction, while the redundancy check stays shut for want of"
                        + " its value",
                () -> assertThat(stepFinished(extractionRun, StepNames.EXTRACTION)).isTrue());
        claim(
                "and leaves the database with no index on the word-sequence hash, since nothing has read one"
                        + " yet and the extraction removed the one that was there before it wrote",
                () -> assertThat(indexExists(BY_HASH)).isFalse());

        openStageFoursGate();
        ConverterStopsPartwayBeans.keepAnswering();
        int before = output.getAll().length();
        cli.run("run", root.toString());
        String second = output.getAll().substring(before);

        claim(
                "the second invocation converts nothing, because the extraction was already finished",
                () -> assertThat(ConverterStopsPartwayBeans.conversions()).isZero());
        claim(
                "and leaves the " + savedShingles + " word sequences the extraction saved as they were",
                () -> assertThat(shingleRowsUnder(extractionRun)).isEqualTo(savedShingles));
        claim(
                "it builds the hash index before the redundancy check reads it, and says so",
                () -> assertThat(second).containsSubsequence(BUILDING, BUILT, RESOLUTION_STARTING));
        claim(
                "and ends with that index in place",
                () -> assertThat(columnsOf(BY_HASH)).containsExactlyElementsOf(BY_HASH_COLUMNS));
    }

    @Test
    @Story("When the index on word-sequence hashes exists")
    @DisplayName("An invocation with nothing left to do neither removes the hash index nor builds it again")
    void anInvocationWithEveryStepFinishedLeavesTheHashIndexAndBuildsNothing(
            CapturedOutput output, @TempDir Path root) throws IOException {
        writeCorpus(root, "nothing left to do");
        openStageFoursGate();
        cli.run("run", root.toString());
        String redundancyRun = onlyRunOf(root, StageModules.CONTENT_REDUNDANCY);

        claim(
                "the first invocation finishes the redundancy check, so every step this corpus reaches is done",
                () -> assertThat(stepFinished(redundancyRun, StepNames.CONTENT_REDUNDANCY)).isTrue());
        claim(
                "and ends with the hash index in place",
                () -> assertThat(indexExists(BY_HASH)).isTrue());

        ConverterStopsPartwayBeans.keepAnswering();
        int before = output.getAll().length();
        cli.run("run", root.toString());
        String second = output.getAll().substring(before);

        claim(
                "the second invocation converts nothing",
                () -> assertThat(ConverterStopsPartwayBeans.conversions()).isZero());
        claim(
                "leaves the hash index where it was, because an extraction with nothing to write has no reason"
                        + " to remove it",
                () -> assertThat(indexExists(BY_HASH)).isTrue());
        claim(
                "and says nothing about building it, because nothing was built",
                () -> assertThat(second).doesNotContain(BUILDING).doesNotContain(BUILT));
    }

    /**
     * A file added between the invocations makes the second a different observation, hence a different
     * walk and a different stage-2 run (ADR-115); that is the lever a test has on the run id, as in
     * ADR-181's tests.
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
        ConverterStopsPartwayBeans.stopAnsweringAfter(ANSWERED_BEFORE_THE_STOP);
        cli.run("run", root.toString());
        List<String> runs = runsOf(root, StageModules.EXTRACTION);

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
        profileStore.save(ProfileFixture.profile()
                .boilerplateDocumentFrequencyFloor(BOILERPLATE_FLOOR, "set by this test, so stage 4's gate is open")
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

    /** SQLite's plan for containment retrieval's query, as {@code RedundancyResolution} sends it, under {@code run}. */
    private String containmentPlanFor(String run) {
        List<Object> arguments = new ArrayList<>();
        arguments.add(run);
        arguments.add(A_GRANULARITY);
        for (long hash = 1; hash <= RARE_SHINGLES; hash++) {
            arguments.add(hash);
        }
        arguments.add(HITS_FOR_A_CANDIDATE);
        return String.join(
                " | ",
                jdbcTemplate.query(
                        "EXPLAIN QUERY PLAN SELECT occurrence_id FROM shingle"
                                + " WHERE run_id = ? AND shingle_parameter_identity = ? AND shingle_hash IN ("
                                + String.join(",", Collections.nCopies(RARE_SHINGLES, "?")) + ")"
                                + " GROUP BY occurrence_id HAVING COUNT(DISTINCT shingle_hash) >= ?",
                        (resultSet, rowNumber) -> resultSet.getString("detail"),
                        arguments.toArray()));
    }
}
