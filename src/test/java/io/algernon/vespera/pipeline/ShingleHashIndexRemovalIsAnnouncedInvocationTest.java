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
 * What stage 2 says when it removes {@code shingle_by_hash} before it writes (ADR-187 section 1, #401).
 *
 * <p>ADR-182 section 2.2 has stage 2 drop the index before its first chunk and write no line about it.
 * On 2026-10-04 that statement took 1 hour 58 minutes on a 16 GB database on a spinning disk, with
 * nothing in the log between the converter's health check and the first file, and the operator took the
 * invocation for one that had stopped moving. ADR-187 keeps the drop and has stage 2 say, when the index
 * is there to remove, that it is removing it and over how many rows at most, and say when it has removed
 * it and how long that took. Where there is no index to remove, or the step has no work to do and so
 * removes nothing, it says nothing, as stage 4b says nothing when it has nothing to build (ADR-182
 * section 2.4).
 *
 * <p>Each test drives whole invocations over {@link ConverterStopsPartwayBeans}, as {@link
 * ShingleHashIndexInvocationTest} does. The index is one per table and this context's database is shared
 * between the tests, so each test puts the index in the state its claims start from and none relies on
 * what another left.
 *
 * <p>The first test is the one that failed before this was built: stage 2 removed the index and said
 * nothing. The other two passed then and have to go on passing: they are what stops an implementation
 * from announcing a removal that is not made.
 */
@CascadeSliceTest
@Import(ConverterStopsPartwayBeans.class)
@ExtendWith(OutputCaptureExtension.class)
@Epic("Pipeline")
@Feature("Progress reporting")
@Issue("401")
@Link(name = "ADR-187", url = Adr.A_DATABASE_STATEMENT_THAT_CAN_TAKE_MINUTES_IS_ANNOUNCED, type = "adr")
class ShingleHashIndexRemovalIsAnnouncedInvocationTest {

    /** How many occurrences one commit of stage 2 holds. */
    private static final int CHUNK = ExtractionJobConfiguration.CHUNK_SIZE;

    /** Two chunks' worth: enough for stage 2 to write shingles in more than one commit. */
    private static final int CORPUS_SIZE = 2 * CHUNK;

    /** A boilerplate floor no shingle reaches below every document, set only to open stage 4's gate. */
    private static final String BOILERPLATE_FLOOR = "1.0";

    /** The index containment retrieval reads, and stage 2 removes before it writes (ADR-182). */
    private static final String BY_HASH = "shingle_by_hash";

    /** How an earlier stage 4b, or a database made before ADR-182, leaves the index. */
    private static final String BUILD_BY_HASH = "CREATE INDEX IF NOT EXISTS shingle_by_hash"
            + " ON shingle (run_id, shingle_parameter_identity, shingle_hash)";

    /** The line stage 2 writes once the converter has answered its health check. */
    private static final String CONVERTER_IS_HEALTHY = "Stage 2 (extraction) sidecar is healthy";

    /** The start of the line stage 2 writes before it removes the index; the greatest row number follows. */
    private static final String REMOVING = "Stage 2 (extraction) is removing shingle_by_hash, over up to ";

    /** What follows that number in the same line. */
    private static final String SHINGLE_ROWS = " shingle rows";

    /** The start of the line stage 2 writes once the index is removed; the seconds it took follow. */
    private static final String REMOVED = "Stage 2 (extraction) removed shingle_by_hash in ";

    /** What the line before the removal says about stopping part-way. */
    private static final String STOPPING_UNDOES_IT = "stopping before it ends undoes it";

    /** The line after the removal, with the time it took: seconds to one decimal place. */
    private static final String REMOVED_IN_SECONDS_TO_ONE_DECIMAL =
            "Stage 2 \\(extraction\\) removed shingle_by_hash in \\d+\\.\\d s";

    /** The start of the line stage 2 writes as it finishes with one file. */
    private static final String A_FILE_FINISHED = "[extraction] finished ";

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

    /**
     * A file added between the invocations makes the second a different observation, hence a different
     * walk and a different stage-2 run (ADR-115), which has everything to write: the case in which stage 2
     * meets an index an earlier run's stage 4b built. The first invocation's shingles are what the index
     * covers, so the row number the line states is not zero.
     */
    @Test
    @Story("A long wait inside the database is announced")
    @DisplayName("An extraction that removes the hash index before it writes says so first, says over how many rows at most, and says when it has removed it")
    void aStageTwoWithWorkToDoSaysItIsRemovingTheHashIndexAndWhenItHasRemovedIt(
            CapturedOutput output, @TempDir Path root) throws IOException {
        writeCorpus(root, "removal announced");
        cli.run("run", root.toString());
        jdbcTemplate.execute(BUILD_BY_HASH);
        long greatestRow = greatestShingleRow();

        claim(
                "the first invocation saved word sequences, so the hash index put in place after it, as a"
                        + " later redundancy check would have left it, covers some rows",
                () -> assertThat(greatestRow).isGreaterThan(NONE));

        Files.writeString(root.resolve("99-added-later.txt"), "A document added after the first invocation finished.");
        int before = output.getAll().length();
        cli.run("run", root.toString());
        String second = output.getAll().substring(before);

        claim(
                "the second invocation extracts under a run of its own, because the corpus it reads is not the"
                        + " one the first read, so it has every document to write again",
                () -> assertThat(runsOf(root, StageModules.EXTRACTION)).hasSize(2).doesNotHaveDuplicates());
        claim(
                "it checks the converter and then finishes files, which are the two lines the removal falls"
                        + " between",
                () -> assertThat(second).containsSubsequence(CONVERTER_IS_HEALTHY, A_FILE_FINISHED));
        claim(
                "it removed the hash index before it wrote, as it did before this was announced",
                () -> assertThat(indexExists(BY_HASH)).isFalse());
        claim(
                "between the converter's health check and the first file it says that it is removing the hash"
                        + " index, and then that it has removed it and how long that took, so a removal lasting"
                        + " hours is not mistaken for an invocation that has stopped moving",
                () -> assertThat(second)
                        .containsSubsequence(CONVERTER_IS_HEALTHY, REMOVING, REMOVED, A_FILE_FINISHED));
        claim(
                "the first of those lines says the index covers up to " + greatestRow + " rows, the greatest row"
                        + " number the word-sequence table held when the removal began",
                () -> assertThat(second).contains(REMOVING + greatestRow + SHINGLE_ROWS));
        claim(
                "and it says each of the two once",
                () -> assertThat(second).containsOnlyOnce(REMOVING).containsOnlyOnce(REMOVED));
        claim(
                "the line before warns that stopping before the removal ends undoes it, and the line after"
                        + " gives the time it took in seconds, to one decimal place",
                () -> assertThat(second)
                        .contains(STOPPING_UNDOES_IT)
                        .containsPattern(REMOVED_IN_SECONDS_TO_ONE_DECIMAL));
    }

    @Test
    @Story("A long wait inside the database is announced")
    @DisplayName("An extraction that finds no hash index to remove says nothing about removing one")
    void aStageTwoThatFindsNoHashIndexSaysNothingAboutRemovingOne(CapturedOutput output, @TempDir Path root)
            throws IOException {
        writeCorpus(root, "nothing to remove");
        jdbcTemplate.execute("DROP INDEX IF EXISTS " + BY_HASH);

        int before = output.getAll().length();
        cli.run("run", root.toString());
        String invocation = output.getAll().substring(before);

        claim(
                "the invocation extracts the corpus, so its extraction had work to do and reached the point"
                        + " where it would remove the hash index",
                () -> assertThat(invocation).containsSubsequence(CONVERTER_IS_HEALTHY, A_FILE_FINISHED));
        claim(
                "and says nothing about removing the index, because there was none: this is every invocation"
                        + " that resumes a stopped extraction, and every one over a new working directory",
                () -> assertThat(invocation).doesNotContain(REMOVING).doesNotContain(REMOVED));
    }

    @Test
    @Story("A long wait inside the database is announced")
    @DisplayName("An invocation with nothing left to do leaves the hash index where it is and says nothing about removing it")
    void anInvocationWithEveryStepFinishedSaysNothingAboutRemovingTheHashIndex(
            CapturedOutput output, @TempDir Path root) throws IOException {
        writeCorpus(root, "nothing left to do and nothing said");
        openStageFoursGate();
        cli.run("run", root.toString());

        claim(
                "the first invocation finishes the redundancy check and ends with the hash index in place",
                () -> assertThat(indexExists(BY_HASH)).isTrue());

        int before = output.getAll().length();
        cli.run("run", root.toString());
        String second = output.getAll().substring(before);

        claim(
                "the second invocation leaves the hash index where it was, because an extraction with nothing to"
                        + " write removes nothing",
                () -> assertThat(indexExists(BY_HASH)).isTrue());
        claim(
                "and says nothing about removing it",
                () -> assertThat(second).doesNotContain(REMOVING).doesNotContain(REMOVED));
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
                    "Document " + position + " of the " + salt + " corpus describes lighthouse keepers, lamp oil"
                            + " and the order in which the lamps were trimmed during the autumn of year " + position
                            + ", with notes on fog and shipping.");
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

    /** The greatest rowid in {@code shingle}: the bound the line states, which SQLite answers without reading a row. */
    private long greatestShingleRow() {
        Long greatest = jdbcTemplate.queryForObject("SELECT MAX(rowid) FROM shingle", Long.class);
        return greatest == null ? NONE : greatest;
    }

    private boolean indexExists(String index) {
        Long rows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sqlite_master WHERE type = 'index' AND tbl_name = 'shingle' AND name = ?",
                Long.class,
                index);
        return rows != null && rows > NONE;
    }
}
