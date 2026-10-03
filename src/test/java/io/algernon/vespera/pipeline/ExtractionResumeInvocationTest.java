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
import java.util.Map;
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
 * A stage 2 that stopped partway resumes under its own run id from what its committed chunks recorded,
 * and does only the rest (ADR-180, #379).
 *
 * <p>Each test stops stage 2 the way the sidecar going away stops it: {@link ConverterStopsPartwayBeans}
 * answers {@link #ANSWERED_BEFORE_THE_STOP} conversions and refuses every later one with the connection
 * failure the real client lets through. That failure is not a skip, so the chunk it lands in rolls back
 * and the step fails. Today's chunk loop reads one chunk ahead and no further, so the first two chunks
 * commit and the third does not. Nothing here depends on that: every claim is phrased over what the
 * ledger holds after the stop, so it stays true if #369 widens the read-ahead across chunks, or if the
 * walk orders the corpus differently on another file system.
 *
 * <p><b>How a test sees which occurrences a later invocation asked about.</b> Conversions are cached
 * outside the run (ADR-070), so an invocation that redid the whole stage and one that resumed would ask
 * the converter about the same occurrences: none of those a committed chunk had cached. So each test
 * empties {@code extraction_cache} between the two invocations. That is safe because the cache is not
 * part of any run id. After that, the converter is asked about exactly the occurrences the resumed
 * invocation reads.
 *
 * <p>Fail today: every test that resumes claims the second invocation converts only what no committed
 * chunk recorded, and today's ADR-115/ADR-116 discard makes it convert the whole corpus. The changed-run
 * test passes today, and has to go on passing.
 */
@CascadeSliceTest
@Import(ConverterStopsPartwayBeans.class)
@Epic("Extraction")
@Feature("Stage 2 step")
@Issue("379")
@Link(name = "ADR-180", url = Adr.A_STOPPED_STAGE_2_RESUMES_FROM_WHAT_ITS_COMMITTED_CHUNKS_RECORDED, type = "adr")
class ExtractionResumeInvocationTest {

    /** How many occurrences one commit of stage 2 holds. */
    private static final int CHUNK = ExtractionJobConfiguration.CHUNK_SIZE;

    /** Three chunks' worth, so a stop can fall after some have committed and before the last. */
    private static final int CORPUS_SIZE = 3 * CHUNK;

    /** Two whole chunks and half of the third: the stop lands inside a chunk, never on its edge. */
    private static final int ANSWERED_BEFORE_THE_STOP = 2 * CHUNK + CHUNK / 2;

    /** Positions in the corpus whose conversion carries no text; spread over the first two chunks. */
    private static final List<Integer> WITHOUT_TEXT_AT = List.of(3, 11, 19);

    /** Positions in the corpus the converter refuses as a property of the document. */
    private static final List<Integer> REFUSED_AT = List.of(7, 25);

    /** The one position the converter fails on while blaming itself, early enough to be reached before the stop. */
    private static final List<Integer> CONVERTER_FAULT_AT = List.of(5);

    /** None of the positions above: every occurrence converts and carries text. */
    private static final List<Integer> NOWHERE = List.of();

    /** Survivors with a score: the corpus less what has no text and what was refused. */
    private static final long SCORED_SURVIVORS = CORPUS_SIZE - WITHOUT_TEXT_AT.size() - REFUSED_AT.size();

    /** No row at all. */
    private static final long NONE = 0;

    /** One file added between the two invocations, which is enough to make it a different observation. */
    private static final int ADDED = 1;

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
     * The converter answers, its count starts at zero, the cache holds nothing from another test, and
     * the profile names nothing: stage 4's gate stays shut, so each invocation ends after stage 3,
     * which is as far as these claims reach. Before each test rather than only after one, because class
     * order differs between machines.
     */
    @BeforeEach
    void startClean() {
        ConverterStopsPartwayBeans.keepAnswering();
        emptyTheExtractionCache();
        profileStore.save(ProfileFixture.profile().build());
    }

    @AfterEach
    void bringTheConverterBack() {
        ConverterStopsPartwayBeans.keepAnswering();
    }

    @Test
    @Story("Stage 2 interrupted partway")
    @DisplayName("A resumed extraction converts only what the stopped one had not saved, and ends where an uninterrupted one ends")
    void aResumedStageReadsOnlyWhatNoCommittedChunkRecordedAndEndsAsAnUninterruptedOne(
            @TempDir Path root, @TempDir Path uninterrupted) throws IOException {
        writeCorpus(root, "resumed", WITHOUT_TEXT_AT, REFUSED_AT, CONVERTER_FAULT_AT);
        writeCorpus(uninterrupted, "resumed", WITHOUT_TEXT_AT, REFUSED_AT, CONVERTER_FAULT_AT);

        ConverterStopsPartwayBeans.stopAnsweringAfter(ANSWERED_BEFORE_THE_STOP);
        cli.run("run", root.toString());
        String run = onlyExtractionRunOf(root);
        long notRecorded = occurrencesNotRecordedUnder(run);

        claim(
                "the first invocation stops, because the converter stopped answering partway through",
                () -> assertThat(cli.getExitCode()).isNotZero());
        claim(
                "and by then some of the corpus of " + CORPUS_SIZE + " was saved and some was not, so the"
                        + " second invocation has something to keep and something left to do",
                () -> assertThat(notRecorded).isBetween(1L, (long) CORPUS_SIZE - 1));

        emptyTheExtractionCache();
        ConverterStopsPartwayBeans.keepAnswering();
        cli.run("run", root.toString());

        claim(
                "the second invocation asks the converter only about the " + notRecorded + " documents the"
                        + " first one had not saved. A document that was saved is not converted, measured or"
                        + " judged a second time, and the one the converter had blamed on itself is among"
                        + " those asked about again, because a fault is only settled when the stage completes",
                () -> assertThat(ConverterStopsPartwayBeans.conversions()).isEqualTo((int) notRecorded));
        claim(
                "and it finishes the stage under the same run, recording it as complete",
                () -> assertThat(stageTwoFinished(run)).isTrue());

        cli.run("run", uninterrupted.toString());
        String reference = onlyExtractionRunOf(uninterrupted);

        claim(
                "the measurements kept from the first invocation and the ones added by the second come to"
                        + " exactly one per document, as many as the same corpus gets in one go",
                () -> assertThat(metricRowsUnder(run)).isEqualTo(metricRowsUnder(reference)));
        claim(
                "the word sequences recorded for later comparison number exactly what the same corpus gets in"
                        + " one go: none is missing and none is recorded twice",
                () -> assertThat(shingleRowsUnder(run)).isEqualTo(shingleRowsUnder(reference)));
        claim(
                "the removals, " + WITHOUT_TEXT_AT.size() + " for no text and "
                        + (REFUSED_AT.size() + CONVERTER_FAULT_AT.size()) + " for a failed conversion, are the"
                        + " ones the same corpus gets in one go, one each, with none lost and none doubled",
                () -> assertThat(verdictKindsUnder(run)).containsExactlyElementsOf(verdictKindsUnder(reference)));
        claim(
                "and the document the converter blamed on itself is recorded once, as in one go, rather than"
                        + " once per invocation that met it",
                () -> assertThat(faultRowsUnder(run)).isEqualTo(faultRowsUnder(reference)));
    }

    @Test
    @Story("Stage 2 interrupted partway")
    @DisplayName("A stop in the middle of a batch leaves no part of that batch saved, and the resumed extraction does it whole")
    void aStopInsideAChunkLeavesNoPartOfItAndTheResumeDoesItWhole(@TempDir Path root) throws IOException {
        writeCorpus(root, "inside a chunk", NOWHERE, NOWHERE, NOWHERE);

        ConverterStopsPartwayBeans.stopAnsweringAfter(ANSWERED_BEFORE_THE_STOP);
        cli.run("run", root.toString());
        String run = onlyExtractionRunOf(root);
        long saved = metricRowsUnder(run);

        claim(
                "the documents saved before the stop are a whole number of batches of " + CHUNK + ": the batch"
                        + " the converter stopped in left nothing, though the converter had answered for part"
                        + " of it before it stopped",
                () -> assertThat(saved % CHUNK).isZero());
        claim(
                "and at least one batch was saved, and not all of them, so the stop fell inside the stage",
                () -> assertThat(saved).isBetween((long) CHUNK, (long) CORPUS_SIZE - CHUNK));
        claim(
                "every saved document has its word sequences beside its measurement",
                () -> assertThat(occurrencesWithShinglesUnder(run)).isEqualTo(saved));
        claim(
                "and no document has word sequences without a measurement, so none was saved in part",
                () -> assertThat(occurrencesWithShinglesButNoMetricUnder(run)).isEqualTo(NONE));

        emptyTheExtractionCache();
        ConverterStopsPartwayBeans.keepAnswering();
        cli.run("run", root.toString());

        claim(
                "the second invocation converts the " + (CORPUS_SIZE - saved) + " documents that were not"
                        + " saved, the stopped batch among them, and none of the " + saved + " that were",
                () -> assertThat((long) ConverterStopsPartwayBeans.conversions()).isEqualTo(CORPUS_SIZE - saved));
        claim(
                "and the run ends with one measurement for each of the " + CORPUS_SIZE + " documents",
                () -> assertThat(metricRowsUnder(run)).isEqualTo(CORPUS_SIZE));
    }

    /**
     * Within one test context the profile-borne inputs of stage 2's identity are read once (the floor
     * bean and the extractor identity are both built once per application context), so the lever a test
     * has on the run id is the corpus: a file added between the invocations is a different observation,
     * hence a different walk and a different run (ADR-115). Any change to the id is the same case to
     * ADR-180's rule.
     */
    @Test
    @Story("Stage 2 interrupted partway")
    @DisplayName("When anything the extraction depends on has changed, the next invocation converts everything again")
    void aDifferentRunIdReadsEveryOccurrenceAndLeavesTheStoppedRunAlone(@TempDir Path root) throws IOException {
        writeCorpus(root, "changed between invocations", NOWHERE, NOWHERE, NOWHERE);

        ConverterStopsPartwayBeans.stopAnsweringAfter(ANSWERED_BEFORE_THE_STOP);
        cli.run("run", root.toString());
        String stopped = onlyExtractionRunOf(root);
        long savedUnderTheStoppedRun = metricRowsUnder(stopped);

        Files.writeString(root.resolve("99-added-later.txt"), "A document added after the first invocation stopped.");
        emptyTheExtractionCache();
        ConverterStopsPartwayBeans.keepAnswering();
        cli.run("run", root.toString());
        List<String> runs = extractionRunsOf(root);
        String changed = runs.getLast();

        claim(
                "the second invocation is under a different run, because the corpus it reads is not the one"
                        + " the first read",
                () -> assertThat(runs).hasSize(2).doesNotHaveDuplicates());
        claim(
                "so it converts every one of the " + (CORPUS_SIZE + ADDED) + " documents, keeping nothing the"
                        + " stopped run saved, because that was saved under different conditions",
                () -> assertThat(ConverterStopsPartwayBeans.conversions()).isEqualTo(CORPUS_SIZE + ADDED));
        claim(
                "and measures every one of them under its own run",
                () -> assertThat(metricRowsUnder(changed)).isEqualTo(CORPUS_SIZE + ADDED));
        claim(
                "while the " + savedUnderTheStoppedRun + " measurements saved under the stopped run are left as"
                        + " they were: a different run never rewrites or removes another run's rows",
                () -> assertThat(metricRowsUnder(stopped)).isEqualTo(savedUnderTheStoppedRun));
    }

    @Test
    @Story("Stage 2 interrupted partway")
    @DisplayName("After a resumed extraction, the confidence report counts every document of the stage, not only the resumed ones")
    void theConfidenceDistributionAfterAResumeCountsTheWholeStage(@TempDir Path root) throws IOException {
        writeCorpus(root, "reported", WITHOUT_TEXT_AT, REFUSED_AT, NOWHERE);

        ConverterStopsPartwayBeans.stopAnsweringAfter(ANSWERED_BEFORE_THE_STOP);
        cli.run("run", root.toString());
        String run = onlyExtractionRunOf(root);
        long notRecorded = occurrencesNotRecordedUnder(run);

        emptyTheExtractionCache();
        ConverterStopsPartwayBeans.keepAnswering();
        cli.run("run", root.toString());

        claim(
                "the second invocation resumed: it asked the converter only about the " + notRecorded
                        + " documents the first one had not saved",
                () -> assertThat(ConverterStopsPartwayBeans.conversions()).isEqualTo((int) notRecorded));
        claim(
                "and the confidence report written after it counts all " + SCORED_SURVIVORS + " documents"
                        + " that kept a score (the corpus of " + CORPUS_SIZE + " less "
                        + WITHOUT_TEXT_AT.size() + " with no text and " + REFUSED_AT.size() + " refused),"
                        + " the ones saved before the stop included",
                () -> assertThat(confidenceDistributionTotalFor(root)).isEqualTo(SCORED_SURVIVORS));
    }

    /**
     * Writes {@link #CORPUS_SIZE} files whose names sort in position order, each with bytes of its own,
     * so stage 1 removes none as a copy of another. The name carries the outcome the scripted converter
     * gives it. {@code salt} keeps one test's corpus from sharing content with another's.
     */
    private static void writeCorpus(
            Path root, String salt, List<Integer> withoutText, List<Integer> refused, List<Integer> converterFault)
            throws IOException {
        for (int position = 1; position <= CORPUS_SIZE; position++) {
            String outcome = withoutText.contains(position)
                    ? ConverterStopsPartwayBeans.WITHOUT_TEXT
                    : refused.contains(position)
                            ? ConverterStopsPartwayBeans.REFUSED
                            : converterFault.contains(position) ? ConverterStopsPartwayBeans.CONVERTER_FAULT : "plain";
            Files.writeString(
                    root.resolve(String.format("%02d-%s.txt", position, outcome)),
                    "Document " + position + " of the " + salt + " corpus describes harbour cranes, tide tables"
                            + " and the order in which ships were unloaded during the winter of year " + position
                            + ", with notes on weather and cargo.");
        }
    }

    private void emptyTheExtractionCache() {
        jdbcTemplate.update("DELETE FROM extraction_cache");
    }

    /** The spelling the walk recorded for {@code root} (ADR-055), which is what {@code walk.root} holds. */
    private static String walkRoot(Path root) {
        return Walk.canonicalRoot(root).toString();
    }

    /** Every stage-2 run over {@code root}, oldest walk first. */
    private List<String> extractionRunsOf(Path root) {
        return jdbcTemplate.queryForList(
                "SELECT r.id FROM run r JOIN walk w ON w.id = r.walk_id"
                        + " WHERE w.root = ? AND r.stage = ? ORDER BY w.id",
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

    /**
     * The occurrences of the run's walk that stage 1 let through and stage 2 has not recorded: no metric
     * row and no stage-2 verdict under the run. A faulted occurrence carries neither (ADR-180 §1).
     */
    private long occurrencesNotRecordedUnder(String run) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM file_occurrence f"
                        + " WHERE f.walk_id = (SELECT walk_id FROM run WHERE id = ?)"
                        + " AND NOT EXISTS (SELECT 1 FROM verdict o WHERE o.occurrence_id = f.id AND o.run_id <> ?)"
                        + " AND NOT EXISTS (SELECT 1 FROM extraction_metric m WHERE m.occurrence_id = f.id AND m.run_id = ?)"
                        + " AND NOT EXISTS (SELECT 1 FROM verdict v WHERE v.occurrence_id = f.id AND v.run_id = ?"
                        + " AND v.kind IN ('EXTRACTION_FAILED', 'DEGENERATE_OUTPUT'))",
                Long.class,
                run,
                run,
                run,
                run);
        return count == null ? NONE : count;
    }

    private long metricRowsUnder(String run) {
        return countUnder("SELECT COUNT(*) FROM extraction_metric WHERE run_id = ?", run);
    }

    private long shingleRowsUnder(String run) {
        return countUnder("SELECT COUNT(*) FROM shingle WHERE run_id = ?", run);
    }

    /** Occurrences with at least one shingle row under {@code run}. */
    private long occurrencesWithShinglesUnder(String run) {
        return countUnder("SELECT COUNT(DISTINCT occurrence_id) FROM shingle WHERE run_id = ?", run);
    }

    /** Occurrences with a shingle row under {@code run} and no metric row under it: half an occurrence. */
    private long occurrencesWithShinglesButNoMetricUnder(String run) {
        return countUnder(
                "SELECT COUNT(DISTINCT s.occurrence_id) FROM shingle s WHERE s.run_id = ?"
                        + " AND NOT EXISTS (SELECT 1 FROM extraction_metric m"
                        + " WHERE m.occurrence_id = s.occurrence_id AND m.run_id = s.run_id)",
                run);
    }

    private long faultRowsUnder(String run) {
        return countUnder("SELECT COUNT(*) FROM extraction_fault WHERE run_id = ?", run);
    }

    private List<String> verdictKindsUnder(String run) {
        return jdbcTemplate.queryForList(
                "SELECT kind FROM verdict WHERE run_id = ? ORDER BY kind", String.class, run);
    }

    private boolean stageTwoFinished(String run) {
        return countUnder("SELECT COUNT(*) FROM finished_step WHERE run_id = ? AND step = '" + StepNames.EXTRACTION + "'", run)
                > NONE;
    }

    /** The documents stage 3's confidence distribution counted over {@code root}'s walk, every bucket summed. */
    private long confidenceDistributionTotalFor(Path root) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT d.document_count FROM confidence_distribution d JOIN run r ON r.id = d.run_id"
                        + " JOIN walk w ON w.id = r.walk_id WHERE w.root = ? AND r.stage = ?",
                walkRoot(root),
                StageModules.CONTENT_CENSUS.stage());
        return rows.stream().mapToLong(row -> ((Number) row.get("document_count")).longValue()).sum();
    }

    private long countUnder(String sql, String run) {
        Long count = jdbcTemplate.queryForObject(sql, Long.class, run);
        return count == null ? NONE : count;
    }
}
