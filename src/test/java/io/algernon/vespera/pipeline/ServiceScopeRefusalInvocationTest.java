package io.algernon.vespera.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.corpus.Walk;
import io.algernon.vespera.extraction.ConverterStopsPartwayBeans;
import io.algernon.vespera.extraction.FailuresInARow;
import io.algernon.vespera.extraction.DoclingExtractor;
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
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Stage 2 run again over content the converter refused while blaming itself asks the converter again,
 * and is not served the stored refusal (ADR-183, #383); content the converter answered about is still
 * served from the extraction cache.
 *
 * <p>Every test runs a first invocation over {@link ConverterStopsPartwayBeans} with outcomes scripted
 * by the order documents are first asked about, then scripts it afresh with nothing, which is the
 * sidecar having recovered: every document it is asked about from then on converts. <b>Unlike {@code
 * ExtractionResumeInvocationTest}, nothing here empties {@code extraction_cache} between
 * invocations</b>, because what the cache keeps is what these tests are about. The converter's count
 * therefore reads exactly the documents the second invocation could not be answered about from the
 * cache.
 *
 * <p>Fail today: the cache keeps the refusal, so the second invocation never asks the converter. In
 * the first test the refused document is faulted again. In the second the five stored refusals reach
 * the drain one after another and stop the step, with the converter never asked. In the third the
 * refusal an earlier build stored is served, and the document is faulted.
 */
@CascadeSliceTest
@Import({ConverterStopsPartwayBeans.class, SuccessiveBuildsBeans.class})
@Epic("Extraction")
@Feature("Stage 2 step")
@Issue("383")
@Link(name = "ADR-183", url = Adr.THE_EXTRACTION_CACHE_KEEPS_ONLY_ANSWERS_ABOUT_THE_DOCUMENT, type = "adr")
@Link(name = "ADR-181", url = Adr.A_STOPPED_STAGE_2_RESUMES_FROM_WHAT_ITS_COMMITTED_CHUNKS_RECORDED, type = "adr")
class ServiceScopeRefusalInvocationTest {

    /** How many occurrences one commit of stage 2 holds. */
    private static final int CHUNK = ExtractionJobConfiguration.CHUNK_SIZE;

    /** Three chunks' worth. */
    private static final int CORPUS_SIZE = 3 * CHUNK;

    /** None of the positions: every document converts. */
    private static final List<Integer> NOWHERE = List.of();

    /** The one position the converter cannot convert, as a property of the document. */
    private static final List<Integer> UNCONVERTIBLE_AT = List.of(7);

    /** The one position the converter refuses while blaming itself. */
    private static final List<Integer> CONVERTER_FAULT_AT = List.of(5);

    /**
     * Five positions the converter refuses while blaming itself: two in each of the first two chunks
     * and one in the third. Five of them cannot reach the drain in a row, in whatever order the workers
     * reach the converter, so the first invocation completes. The first position falls to one of the
     * first eleven occurrences read, because at most seven other workers can be holding an earlier
     * one. The last falls to none of the first sixteen, because the reader has dispatched only
     * thirty-two occurrences until the first chunk has been processed whole (ADR-176). Five is the
     * number of consecutive refusals that stops the step.
     */
    private static final List<Integer> SPREAD_CONVERTER_FAULTS_AT = List.of(4, 12, 20, 28, 40);

    /** The number of consecutive refusals that stops stage 2's step. */
    private static final int REFUSALS_THAT_STOP_THE_STEP = FailuresInARow.CONSECUTIVE_SERVICE_SCOPE_FAILURE_COUNT;

    /** No row at all. */
    private static final long NONE = 0;

    /** Exactly one row. */
    private static final long ONCE = 1;

    /** The exit code of an invocation that completed. */
    private static final int COMPLETED = 0;

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
    private DoclingExtractor extractor;

    /**
     * Nothing scripted, the cache holding nothing from another test, the first build, and a profile
     * naming nothing, so stage 4's gate stays shut and each invocation ends after stage 3.
     */
    @BeforeEach
    void startClean() {
        ConverterStopsPartwayBeans.script(NOWHERE, NOWHERE, NOWHERE);
        SuccessiveBuildsBeans.theFirstBuild();
        jdbcTemplate.update("DELETE FROM extraction_cache");
        profileStore.save(ProfileFixture.profile().build());
    }

    @AfterEach
    void bringTheConverterAndTheFirstBuildBack() {
        ConverterStopsPartwayBeans.keepAnswering();
        SuccessiveBuildsBeans.theFirstBuild();
    }

    /**
     * A commit to {@code extraction} mints a new stage-2 run over the same walk, so every survivor is
     * read again: the shape of ADR-140 section 5's "run it again" that reads the whole stage. The
     * converter, scripted afresh, would convert the document it could not convert the first time, so
     * that document's removal under the new run shows it was answered from the cache, not asked again.
     */
    @Test
    @Story("A refusal the converter blamed on itself is asked about again")
    @DisplayName("Extraction run again asks the converter again about a document it refused while blaming itself, and nothing else")
    void aNewRunAsksAgainAboutTheRefusalAndIsServedEveryOtherAnswer(@TempDir Path root) throws IOException {
        ConverterStopsPartwayBeans.script(NOWHERE, UNCONVERTIBLE_AT, CONVERTER_FAULT_AT);
        writeCorpus(root, "run again");

        cli.run("run", root.toString());
        String first = onlyExtractionRunOf(root);
        List<Long> refused = faultedOccurrencesUnder(first);
        List<Long> unconvertible = removedWithAMeasurementUnder(first);

        claim(
                "the first invocation completes, the converter having blamed itself for "
                        + CONVERTER_FAULT_AT.size() + " document and blamed " + UNCONVERTIBLE_AT.size()
                        + " other on the document itself",
                () -> {
                    assertThat(cli.getExitCode()).isEqualTo(COMPLETED);
                    assertThat(refused).hasSize(CONVERTER_FAULT_AT.size());
                    assertThat(unconvertible).hasSize(UNCONVERTIBLE_AT.size());
                });
        claim(
                "and since the stage completed, the document the converter blamed itself for is removed as a"
                        + " failed conversion",
                () -> assertThat(extractionFailedVerdictsAgainst(refused.getFirst(), first)).isEqualTo(ONCE));

        ConverterStopsPartwayBeans.script(NOWHERE, NOWHERE, NOWHERE);
        SuccessiveBuildsBeans.aCommitTo("extraction");
        cli.run("run", root.toString());
        List<String> runs = extractionRunsOf(root);
        String second = runs.getLast();

        claim(
                "the second invocation is under a new run of the extraction, which reads every document again",
                () -> assertThat(runs).hasSize(2).doesNotHaveDuplicates());
        claim(
                "it asks the converter about the " + CONVERTER_FAULT_AT.size() + " document it refused while"
                        + " blaming itself and about none of the other " + (CORPUS_SIZE - CONVERTER_FAULT_AT.size())
                        + ", which it had answered about and which are answered from storage",
                () -> assertThat(ConverterStopsPartwayBeans.conversions()).isEqualTo(CONVERTER_FAULT_AT.size()));
        claim(
                "the converter, asked again, converts that document, so it is measured under the new run and"
                        + " neither removed nor held as a fault there",
                () -> {
                    assertThat(metricRowsAgainst(refused.getFirst(), second)).isEqualTo(ONCE);
                    assertThat(extractionFailedVerdictsAgainst(refused.getFirst(), second)).isEqualTo(NONE);
                    assertThat(faultRowsAgainst(refused.getFirst(), second)).isEqualTo(NONE);
                });
        claim(
                "while the document the converter could not convert is removed again under the new run, from"
                        + " the answer stored for it: asked again, the converter would now have converted it",
                () -> assertThat(extractionFailedVerdictsAgainst(unconvertible.getFirst(), second)).isEqualTo(ONCE));
    }

    /**
     * ADR-181's lost-end-of-step state, which is also the smallest discard that runs stage 2 again under
     * the same run (ADR-183 section 6): the completion record of a stage that completed is deleted, so
     * the next invocation deletes the faults and the removals that resolved them and reads exactly the
     * faulted documents again. Every other document carries a measurement under the run and is not
     * read, so the faulted ones reach the drain one after another, where in the first invocation each
     * had converted documents between it and the next.
     */
    @Test
    @Story("A refusal the converter blamed on itself is asked about again")
    @DisplayName("Refusals spread through a completed extraction, read again together, are asked about again rather than stopping it")
    void refusalsReadAgainTogetherAreAskedAgainRatherThanStoppingTheStage(@TempDir Path root) throws IOException {
        ConverterStopsPartwayBeans.script(NOWHERE, NOWHERE, SPREAD_CONVERTER_FAULTS_AT);
        writeCorpus(root, "read again together");

        cli.run("run", root.toString());
        String run = onlyExtractionRunOf(root);

        claim(
                "the first invocation completes, the " + SPREAD_CONVERTER_FAULTS_AT.size() + " documents the"
                        + " converter blamed itself for having been spread through the stage, never "
                        + REFUSALS_THAT_STOP_THE_STEP + " in a row",
                () -> {
                    assertThat(cli.getExitCode()).isEqualTo(COMPLETED);
                    assertThat(stageTwoFinished(run)).isTrue();
                    assertThat(faultedOccurrencesUnder(run)).hasSize(SPREAD_CONVERTER_FAULTS_AT.size());
                });

        jdbcTemplate.update("DELETE FROM finished_step WHERE run_id = ? AND step = ?", run, StepNames.EXTRACTION);
        ConverterStopsPartwayBeans.script(NOWHERE, NOWHERE, NOWHERE);
        cli.run("run", root.toString());

        claim(
                "with the record that the stage finished gone, the next invocation reads those "
                        + SPREAD_CONVERTER_FAULTS_AT.size() + " documents again, together, and asks the"
                        + " converter about each of them rather than reading its earlier refusal from storage",
                () -> assertThat(ConverterStopsPartwayBeans.conversions()).isEqualTo(SPREAD_CONVERTER_FAULTS_AT.size()));
        claim(
                "so the invocation completes and records the stage as finished, where "
                        + REFUSALS_THAT_STOP_THE_STEP + " stored refusals in a row would have stopped it",
                () -> {
                    assertThat(cli.getExitCode()).isEqualTo(COMPLETED);
                    assertThat(stageTwoFinished(run)).isTrue();
                });
        claim(
                "and the converter having converted all " + SPREAD_CONVERTER_FAULTS_AT.size() + ", none is held"
                        + " as a fault or removed, and every one of the " + CORPUS_SIZE + " documents is measured",
                () -> {
                    assertThat(faultedOccurrencesUnder(run)).isEmpty();
                    assertThat(verdictKindsUnder(run)).doesNotContain("EXTRACTION_FAILED");
                    assertThat(metricRowsUnder(run)).isEqualTo(CORPUS_SIZE);
                });
    }

    /**
     * The upgrade's replay (ADR-183 section 3) over a working directory an earlier build left a refusal
     * in. Stage 2's reader looks a row up through {@code DoclingExtractor.cached} and writes the answer
     * back through {@code remember} inside the chunk's transaction, so this is the path on which a row
     * that is served when it should be passed over, or a write that cannot replace it, shows. A write
     * that throws on the existing key would fail the chunk, which is not a skip, and stop stage 2 on
     * every invocation.
     *
     * <p>The refusal is planted by rewriting one converted document's row with SQL, into the columns
     * the earlier build's {@code ExtractionCache.put} wrote for a {@code capacity} refusal. The document
     * chosen is the lowest-numbered one, and it is found by its content hash, so the claim does not
     * depend on which position the converter answered it at.
     */
    @Test
    @Story("A refusal stored by an earlier version is asked about again")
    @DisplayName("Extraction run again by a new version asks the converter again about a refusal an earlier version stored, and replaces it")
    void aReplayAsksAgainAboutARefusalAnEarlierBuildStoredAndReplacesIt(@TempDir Path root) throws IOException {
        writeCorpus(root, "stored by an earlier build");

        cli.run("run", root.toString());
        String first = onlyExtractionRunOf(root);

        claim(
                "the first invocation completes, converting every one of the " + CORPUS_SIZE + " documents",
                () -> {
                    assertThat(cli.getExitCode()).isEqualTo(COMPLETED);
                    assertThat(metricRowsUnder(first)).isEqualTo(CORPUS_SIZE);
                });

        long planted = lowestOccurrenceUnder(first);
        String plantedHash = contentHashOf(root, planted);
        int rewritten = jdbcTemplate.update(
                "UPDATE extraction_cache SET status = 'failure', errors_json = ?, confidence_json = NULL,"
                        + " processing_time = 0.1, response_json = ? WHERE content_hash = ?",
                "[{\"component_type\":\"document_backend\",\"module_name\":\"docling\","
                        + "\"error_message\":\"" + ConverterStopsPartwayBeans.FAULT_MESSAGE + "\","
                        + "\"category\":\"capacity\",\"page_no\":null}]",
                "{\"status\":\"failure\"}",
                plantedHash);

        claim(
                "one document's stored conversion now reads as a capacity refusal, as an earlier version"
                        + " stored every refusal",
                () -> {
                    assertThat(rewritten).isEqualTo((int) ONCE);
                    assertThat(storedStatusesFor(plantedHash)).containsExactly("failure");
                });

        ConverterStopsPartwayBeans.script(NOWHERE, NOWHERE, NOWHERE);
        SuccessiveBuildsBeans.aCommitTo("extraction");
        cli.run("run", root.toString());
        String second = extractionRunsOf(root).getLast();

        claim(
                "the new version's run of the extraction asks the converter about that one document and about"
                        + " none of the other " + (CORPUS_SIZE - 1) + ", which are answered from storage",
                () -> assertThat(ConverterStopsPartwayBeans.conversions()).isEqualTo((int) ONCE));
        claim(
                "it completes, the stored refusal standing in the way of neither the call nor the answer",
                () -> {
                    assertThat(cli.getExitCode()).isEqualTo(COMPLETED);
                    assertThat(stageTwoFinished(second)).isTrue();
                });
        claim(
                "the conversion replaces the stored refusal, as the one answer stored for that content",
                () -> assertThat(storedStatusesFor(plantedHash)).containsExactly("success"));
        claim(
                "and the document is measured under the new run, neither removed nor held as a fault there",
                () -> {
                    assertThat(metricRowsAgainst(planted, second)).isEqualTo(ONCE);
                    assertThat(extractionFailedVerdictsAgainst(planted, second)).isEqualTo(NONE);
                    assertThat(faultRowsAgainst(planted, second)).isEqualTo(NONE);
                });
    }

    /**
     * Writes {@link #CORPUS_SIZE} files, each with bytes of its own. {@code salt} keeps one test's corpus
     * from sharing content, and so cache rows, with another's.
     */
    private static void writeCorpus(Path root, String salt) throws IOException {
        for (int position = 1; position <= CORPUS_SIZE; position++) {
            Files.writeString(
                    root.resolve(String.format("document-%02d.txt", position)),
                    "Document " + position + " of the " + salt + " corpus records the lighthouse keeper's log,"
                            + " the lamp oil delivered and the ships sighted in the spring of year " + position
                            + ", with remarks on fog and the state of the lens.");
        }
    }

    /** The spelling the walk recorded for {@code root} (ADR-055), which is what {@code walk.root} holds. */
    private static String walkRoot(Path root) {
        return Walk.canonicalRoot(root).toString();
    }

    /** Every stage-2 run over {@code root}, oldest walk first, then in the order the runs were minted. */
    private List<String> extractionRunsOf(Path root) {
        return jdbcTemplate.queryForList(
                "SELECT r.id FROM run r JOIN walk w ON w.id = r.walk_id"
                        + " WHERE w.root = ? AND r.stage = ? ORDER BY w.id, r.rowid",
                String.class,
                walkRoot(root),
                StageModules.EXTRACTION.stage());
    }

    private String onlyExtractionRunOf(Path root) {
        List<String> runs = extractionRunsOf(root);
        claim(
                "the extraction over this corpus has one run so far, so every count below is about that run",
                () -> assertThat(runs).hasSize(1));
        return runs.getFirst();
    }

    /** The occurrences carrying an extraction fault row under {@code run}. */
    private List<Long> faultedOccurrencesUnder(String run) {
        return jdbcTemplate.queryForList(
                "SELECT occurrence_id FROM extraction_fault WHERE run_id = ? ORDER BY occurrence_id", Long.class, run);
    }

    /**
     * The occurrences removed as a failed conversion under {@code run} that also carry a measurement
     * there: a failure the converter blamed on the document, which is measured as it is removed. One
     * the converter blamed on itself carries no measurement.
     */
    private List<Long> removedWithAMeasurementUnder(String run) {
        return jdbcTemplate.queryForList(
                "SELECT v.occurrence_id FROM verdict v WHERE v.run_id = ? AND v.kind = 'EXTRACTION_FAILED'"
                        + " AND EXISTS (SELECT 1 FROM extraction_metric m"
                        + " WHERE m.occurrence_id = v.occurrence_id AND m.run_id = v.run_id)"
                        + " ORDER BY v.occurrence_id",
                Long.class,
                run);
    }

    /** The lowest-numbered occurrence carrying a measurement under {@code run}. */
    private long lowestOccurrenceUnder(String run) {
        return jdbcTemplate.queryForObject(
                "SELECT MIN(occurrence_id) FROM extraction_metric WHERE run_id = ?", Long.class, run);
    }

    /** The content hash the extraction cache files {@code occurrence}'s file under. */
    private String contentHashOf(Path root, long occurrence) {
        String path = jdbcTemplate.queryForObject("SELECT path FROM file_occurrence WHERE id = ?", String.class, occurrence);
        return extractor.contentHashFor(Walk.canonicalRoot(root).resolve(path));
    }

    private List<String> storedStatusesFor(String contentHash) {
        return jdbcTemplate.queryForList(
                "SELECT status FROM extraction_cache WHERE content_hash = ?", String.class, contentHash);
    }

    private long metricRowsUnder(String run) {
        return countOf("SELECT COUNT(*) FROM extraction_metric WHERE run_id = ?", run);
    }

    private long metricRowsAgainst(long occurrence, String run) {
        return countOf("SELECT COUNT(*) FROM extraction_metric WHERE occurrence_id = ? AND run_id = ?", occurrence, run);
    }

    private long faultRowsAgainst(long occurrence, String run) {
        return countOf("SELECT COUNT(*) FROM extraction_fault WHERE occurrence_id = ? AND run_id = ?", occurrence, run);
    }

    private long extractionFailedVerdictsAgainst(long occurrence, String run) {
        return countOf(
                "SELECT COUNT(*) FROM verdict WHERE occurrence_id = ? AND run_id = ? AND kind = 'EXTRACTION_FAILED'",
                occurrence,
                run);
    }

    private List<String> verdictKindsUnder(String run) {
        return jdbcTemplate.queryForList("SELECT kind FROM verdict WHERE run_id = ? ORDER BY kind", String.class, run);
    }

    private boolean stageTwoFinished(String run) {
        return countOf("SELECT COUNT(*) FROM finished_step WHERE run_id = ? AND step = ?", run, StepNames.EXTRACTION)
                > NONE;
    }

    private long countOf(String sql, Object... arguments) {
        Long count = jdbcTemplate.queryForObject(sql, Long.class, arguments);
        return count == null ? NONE : count;
    }
}
