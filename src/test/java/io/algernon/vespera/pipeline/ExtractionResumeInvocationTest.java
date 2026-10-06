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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * A stage 2 that stopped partway resumes under its own run id from what its committed chunks recorded,
 * and does only the rest (ADR-181, #379).
 *
 * <p>Most tests stop stage 2 the way the sidecar going away stops it: {@link ConverterStopsPartwayBeans}
 * answers {@link #ANSWERED_BEFORE_THE_STOP} conversions and refuses every later one with the connection
 * failure the real client lets through. That failure is not a skip, so the chunk it lands in rolls back
 * and the step fails. The reader dispatches sixteen occurrences beyond the chunk being read (ADR-176),
 * so which chunk the first refusal lands in is not fixed. The claims are phrased over what the ledger
 * holds after the stop, so they hold wherever it lands. The scripted outcomes are placed by the
 * order documents are first asked about, not by name, so they do not move with the order a file
 * system lists a folder in; where a test needs an outcome to have been reached before the stop, it
 * claims so before going on, and an order that defeats the script fails there rather than passing an
 * untested path.
 *
 * <p>{@link #aStageWhoseEndOfStepWasLostDoesTheFaultAgainAndRecordsItOnce} stops nothing. It plays the
 * one state no stop by the converter reaches: the end of the step recorded and resolved its faults,
 * and the completion record written after it was lost.
 *
 * <p><b>How a test sees which occurrences a later invocation asked about.</b> Conversions are cached
 * outside the run (ADR-070), so an invocation that redid the whole stage and one that resumed would ask
 * the converter about the same occurrences: none of those a committed chunk had cached. So each test
 * empties {@code extraction_cache} between the two invocations. That is safe because the cache is not
 * part of any run id. After that, the converter is asked about exactly the occurrences the resumed
 * invocation reads.
 *
 * <p>Under ADR-115/ADR-116's discard, before ADR-181 was implemented, every test that resumes failed:
 * each claims the second invocation converts, or counts its progress over, only what no committed
 * chunk recorded, and the discard made it redo the whole corpus. The changed-run test passed under
 * the discard too, because that code redid everything, and has to go on passing.
 *
 * <p>{@link SuccessiveBuildsBeans} stands in for the build's implementation versions in every test, so
 * the changed-run test can play a second build over the same walk; the others never move it.
 *
 * <p><b>Two tests here also hold the whole of what stage 2 says about its statements on a resume, in
 * order</b> (ADR-199 section 2, ADR-193 section 6, ADR-204 section 3, #411): the two reads the reader makes
 * first, of the faults the stopped run recorded and of the occurrences it measured, each said only where the
 * run holds a row of its table and over the span of the run's rows, and then the count of the survivors still
 * to read and the read for the review list. {@code UncoveredStatementsInvocationTest} holds the two reads'
 * lines on their own; what is added here is the order of all four and each read's total.
 */
@CascadeSliceTest
@ExtendWith(OutputCaptureExtension.class)
@Import({ConverterStopsPartwayBeans.class, SuccessiveBuildsBeans.class})
@Epic("Extraction")
@Feature("Stage 2 step")
@Issue("379")
@Link(name = "ADR-181", url = Adr.A_STOPPED_STAGE_2_RESUMES_FROM_WHAT_ITS_COMMITTED_CHUNKS_RECORDED, type = "adr")
class ExtractionResumeInvocationTest {

    /** How many occurrences one commit of stage 2 holds. */
    private static final int CHUNK = ExtractionJobConfiguration.CHUNK_SIZE;

    /** Three chunks' worth, so a stop can fall after some have committed and before the last. */
    private static final int CORPUS_SIZE = 3 * CHUNK;

    /** Two whole chunks and half of the third: the stop lands inside a chunk, never on its edge. */
    private static final int ANSWERED_BEFORE_THE_STOP = 2 * CHUNK + CHUNK / 2;

    /**
     * Positions, in the order documents are first asked about, whose conversion carries no text; spread
     * over the first two chunks, so they are reached before the stop.
     */
    private static final List<Integer> WITHOUT_TEXT_AT = List.of(3, 11, 19);

    /** Positions the converter cannot convert, as a property of the document; one in each of the first two chunks. */
    private static final List<Integer> UNCONVERTIBLE_AT = List.of(7, 25);

    /** The one position the converter fails on while blaming itself, inside the first chunk. */
    private static final List<Integer> CONVERTER_FAULT_AT = List.of(5);

    /** None of the positions above: every occurrence converts and carries text. */
    private static final List<Integer> NOWHERE = List.of();

    /** Survivors with a score: the corpus less what has no text and what could not be converted. */
    private static final long SCORED_SURVIVORS = CORPUS_SIZE - WITHOUT_TEXT_AT.size() - UNCONVERTIBLE_AT.size();

    /** Stage 2's progress line (ADR-093): how many it has done, of how many. */
    private static final Pattern STAGE_TWO_PROGRESS = Pattern.compile("Stage 2 \\(extraction\\): ([\\d,]+) of ([\\d,]+) \\(");

    /** Stage 2's own name, which its statement lines open with. */
    private static final String STAGE_TWO = "Stage 2 (extraction)";

    /** What the two reads before a resume read, as their lines name it (ADR-199 section 2). */
    private static final String FAULTS_ALREADY_RECORDED = "the faults the stopped run recorded";

    private static final String OCCURRENCES_ALREADY_MEASURED = "the occurrences the stopped run measured";

    /** The two statements every stage 2 that reads an occurrence times: its count, and its read for the review list. */
    private static final List<String> THE_COUNT_AND_THE_REVIEW_LIST_READ = StatementLines.inOrder(
            StatementLines.timedCount(STAGE_TWO, "the survivors still to read"),
            StatementLines.timedRead(STAGE_TWO, "the occurrences it could not read"));

    /** No row at all. */
    private static final long NONE = 0;

    /** Exactly one row: the count a once-only record has. */
    private static final long ONCE = 1;

    /** The two builds the changed-run test plays: before and after a commit to stage 2's code. */
    private static final int TWO_BUILDS = 2;

    /** One walk: an unchanged archive walked again is the walk it already was (ADR-115). */
    private static final int ONE_WALK = 1;

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
     * The converter answers with nothing scripted, its count starts at zero, the cache holds nothing
     * from another test, every module is at the first build, and the profile names nothing: stage 4's
     * gate stays shut, so each invocation ends after stage 3, which is as far as these claims reach.
     * Before each test rather than only after one, because class order differs between machines.
     */
    @BeforeEach
    void startClean() {
        ConverterStopsPartwayBeans.script(NOWHERE, NOWHERE, NOWHERE);
        SuccessiveBuildsBeans.theFirstBuild();
        emptyTheExtractionCache();
        profileStore.save(ProfileFixture.profile().build());
    }

    @AfterEach
    void bringTheConverterAndTheFirstBuildBack() {
        ConverterStopsPartwayBeans.keepAnswering();
        SuccessiveBuildsBeans.theFirstBuild();
    }

    @Test
    @Story("Stage 2 interrupted partway")
    @DisplayName("A resumed extraction converts only what the stopped one had not saved, and ends where an uninterrupted one ends")
    void aResumedStageReadsOnlyWhatNoCommittedChunkRecordedAndEndsAsAnUninterruptedOne(
            @TempDir Path root, @TempDir Path uninterrupted) throws IOException {
        ConverterStopsPartwayBeans.script(WITHOUT_TEXT_AT, UNCONVERTIBLE_AT, CONVERTER_FAULT_AT);
        writeCorpus(root, "resumed");
        writeCorpus(uninterrupted, "resumed");

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
        claim(
                "before it stopped, the converter had failed on " + CONVERTER_FAULT_AT.size() + " document"
                        + " while blaming itself, and that is recorded as a fault awaiting a stage that"
                        + " completes, so the second invocation has a fault to take up again",
                () -> assertThat(faultRowsUnder(run)).isEqualTo(CONVERTER_FAULT_AT.size()));
        claim(
                "and the saved part already holds a removal for no text and a removal for a failed"
                        + " conversion, so the second invocation has removals to keep rather than repeat",
                () -> assertThat(verdictKindsUnder(run)).contains("DEGENERATE_OUTPUT", "EXTRACTION_FAILED"));

        emptyTheExtractionCache();
        ConverterStopsPartwayBeans.keepAnswering();
        cli.run("run", root.toString());

        claim(
                "the second invocation reads again only the " + notRecorded + " documents the first one"
                        + " had not saved. A document that was saved is not converted, measured or judged a"
                        + " second time, and the one the converter had blamed on itself is among those it reads"
                        + " again, because a fault is only settled when the stage completes",
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
                        + (UNCONVERTIBLE_AT.size() + CONVERTER_FAULT_AT.size()) + " for a failed conversion, are"
                        + " the ones the same corpus gets in one go, one each, with none lost and none doubled",
                () -> assertThat(verdictKindsUnder(run)).containsExactlyElementsOf(verdictKindsUnder(reference)));
        claim(
                "and the document the converter blamed on itself is recorded once, as in one go, rather than"
                        + " once per invocation that met it",
                () -> assertThat(faultRowsUnder(run)).isEqualTo(faultRowsUnder(reference)));
    }

    /**
     * The conversion count after the resume is what this test discriminates on. The claims about what
     * the stop left -- a whole number of chunks, shingles only beside a metric row, the run ending with
     * one metric row per document -- pass today as well: they guard the chunk atomicity ADR-181 rests
     * on, and would fail only if a change broke it.
     */
    @Test
    @Story("Stage 2 interrupted partway")
    @DisplayName("A stop in the middle of a batch leaves no part of that batch saved, and the resumed extraction does it whole")
    void aStopInsideAChunkLeavesNoPartOfItAndTheResumeDoesItWhole(@TempDir Path root) throws IOException {
        writeCorpus(root, "inside a chunk");

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
     * The progress line's denominator on a resume is what this invocation reads, not the whole survivor
     * set (ADR-181 §1, ADR-093). Read off the log the second invocation writes, from the point it
     * starts, so the first invocation's lines over the whole corpus are not counted.
     */
    @Test
    @Story("Stage 2 interrupted partway")
    @DisplayName("A resumed extraction reports its progress out of what it has left to do, not out of the whole collection")
    void aResumedStageCountsItsProgressOverWhatItReads(CapturedOutput output, @TempDir Path root) throws IOException {
        writeCorpus(root, "progress");

        ConverterStopsPartwayBeans.stopAnsweringAfter(ANSWERED_BEFORE_THE_STOP);
        int beforeTheFirst = output.getAll().length();
        cli.run("run", root.toString());
        List<String> first = List.of(output.getAll().substring(beforeTheFirst).split("\\R"));
        long notRecorded = occurrencesNotRecordedUnder(onlyExtractionRunOf(root));
        long measured = rowSpanUnder("extraction_metric", onlyExtractionRunOf(root));

        emptyTheExtractionCache();
        ConverterStopsPartwayBeans.keepAnswering();
        int before = output.getAll().length();
        cli.run("run", root.toString());
        List<String[]> progress = progressLinesIn(output.getAll().substring(before));

        claim(
                "the second invocation reports its progress",
                () -> assertThat(progress).isNotEmpty());
        claim(
                "and every line of it counts out of the " + notRecorded + " documents the first invocation had"
                        + " not saved, not out of all " + CORPUS_SIZE,
                () -> assertThat(progress).allSatisfy(line -> assertThat(line[1]).isEqualTo(String.valueOf(notRecorded))));
        claim(
                "and the last line reaches all " + notRecorded + " of them",
                () -> assertThat(progress.getLast()[0]).isEqualTo(String.valueOf(notRecorded)));

        List<String> second = List.of(output.getAll().substring(before).split("\\R"));
        claim(
                "the first invocation, whose run held nothing when it began, says nothing about reading what an"
                        + " earlier one left: only that it counted what it had to read, and at its end that it"
                        + " read what it could not read",
                () -> assertThat(StatementLines.of(first, STAGE_TWO))
                        .containsExactlyElementsOf(THE_COUNT_AND_THE_REVIEW_LIST_READ));
        claim(
                "the second says first that it is reading what the first had already measured, over up to the "
                        + measured + " rows from the run's first to its last, and how long that took; then its"
                        + " count and its read at the end; and nothing about faults, the run holding none",
                () -> assertThat(StatementLines.of(second, STAGE_TWO))
                        .containsExactlyElementsOf(StatementLines.inOrder(
                                StatementLines.countedRead(STAGE_TWO, OCCURRENCES_ALREADY_MEASURED, measured),
                                THE_COUNT_AND_THE_REVIEW_LIST_READ)));
        claim(
                "that read ends before the line that says the stage resumes, and so few rows take far fewer steps"
                        + " than the database reports progress at, so no progress line is written for it",
                () -> {
                    assertThat(String.join("\n", second))
                            .containsSubsequence(
                                    STAGE_TWO + " read " + OCCURRENCES_ALREADY_MEASURED + " in ",
                                    STAGE_TWO + " resumes run ");
                    assertThat(second).noneMatch(line -> line.contains("the stopped run measured): about "));
                });
    }

    /**
     * A commit to {@code extraction} moves stage 2's implementation version (ADR-058), so the next
     * invocation mints another stage-2 run over the <b>same</b> walk: the archive is unchanged, so the
     * walk and every occurrence id are the ones the stopped run recorded against (ADR-115). That is what
     * makes this test discriminate. An implementation that left out "occurrences with a metric row"
     * without asking under which run would skip the stopped run's saved documents here and convert
     * only the rest; one that asks under the run reads them all. Any other change to the run id is the
     * same case to ADR-181's rule.
     *
     * <p>Passes today, because today's code redoes the whole stage under any run it starts; it has to
     * go on passing once a resume keeps what a run committed.
     */
    @Test
    @Story("Stage 2 interrupted partway")
    @DisplayName("When the extraction's code has changed, the next invocation converts every document again, the same files included")
    void aDifferentRunIdReadsEveryOccurrenceAndLeavesTheStoppedRunAlone(@TempDir Path root) throws IOException {
        writeCorpus(root, "changed between invocations");

        ConverterStopsPartwayBeans.stopAnsweringAfter(ANSWERED_BEFORE_THE_STOP);
        cli.run("run", root.toString());
        String stopped = onlyExtractionRunOf(root);
        long savedUnderTheStoppedRun = metricRowsUnder(stopped);

        claim(
                "the first invocation saved part of the corpus of " + CORPUS_SIZE + " before it stopped, so"
                        + " there is something a careless resume could wrongly keep",
                () -> assertThat(savedUnderTheStoppedRun).isBetween(1L, (long) CORPUS_SIZE - 1));

        SuccessiveBuildsBeans.aCommitTo("extraction");
        emptyTheExtractionCache();
        ConverterStopsPartwayBeans.keepAnswering();
        cli.run("run", root.toString());
        List<String> runs = extractionRunsOf(root);
        String changed = runs.getLast();

        claim(
                "the second invocation, under a new build of the extraction code, is under a different run",
                () -> assertThat(runs).hasSize(TWO_BUILDS).doesNotHaveDuplicates());
        claim(
                "over the same observation of the same files, so the documents the stopped run saved are"
                        + " the very ones the new run reads",
                () -> assertThat(walksOf(runs)).isEqualTo(ONE_WALK));
        claim(
                "so it converts every one of the " + CORPUS_SIZE + " documents, keeping nothing the stopped"
                        + " run saved, because that was saved by different code",
                () -> assertThat(ConverterStopsPartwayBeans.conversions()).isEqualTo(CORPUS_SIZE));
        claim(
                "and measures every one of them under its own run",
                () -> assertThat(metricRowsUnder(changed)).isEqualTo(CORPUS_SIZE));
        claim(
                "while the " + savedUnderTheStoppedRun + " measurements saved under the stopped run are left as"
                        + " they were: a different run never rewrites or removes another run's rows",
                () -> assertThat(metricRowsUnder(stopped)).isEqualTo(savedUnderTheStoppedRun));
    }

    /**
     * The conversion count is what this test discriminates on. The confidence-distribution total
     * passes today too, because today's full redo also covers every document; it guards ADR-181 §5's
     * rule that a report about stage 2 reads the ledger under the run rather than one invocation's
     * counts.
     */
    @Test
    @Story("Stage 2 interrupted partway")
    @DisplayName("After a resumed extraction, the confidence report counts every document of the stage, not only the resumed ones")
    void theConfidenceDistributionAfterAResumeCountsTheWholeStage(@TempDir Path root) throws IOException {
        ConverterStopsPartwayBeans.script(WITHOUT_TEXT_AT, UNCONVERTIBLE_AT, NOWHERE);
        writeCorpus(root, "reported");

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
                        + WITHOUT_TEXT_AT.size() + " with no text and " + UNCONVERTIBLE_AT.size() + " that could"
                        + " not be converted), the ones saved before the stop included",
                () -> assertThat(confidenceDistributionTotalFor(root)).isEqualTo(SCORED_SURVIVORS));
    }

    /**
     * ADR-181 §1's one kept deletion. The end of the step writes its fault rows and the
     * extraction-failed verdicts resolving them in one transaction, and the completion record after
     * it in another (ADR-139 §4); a stop between the two, or a power cut that loses only the later
     * commit (ADR-180 §2), leaves a run whose faults are resolved and whose step is not finished.
     * Deleting the completion record plays that state. The faulted document carries no metric row, so
     * the resume reads it again; the verdict that resolved it has to go before that, or the end of
     * this invocation's step resolves it a second time, and {@code verdict} has no unique key to stop
     * it.
     *
     * <p>The cache is emptied here as in every test of this class, and here it no longer matters. Before
     * ADR-183 it did: the faulted document would have been judged again from its cached refusal and
     * reached the converter not at all. A failure the converter blamed on itself is now never kept, so
     * the resume asks the converter about it either way, and every other document is left out by its
     * metric row, not by the cache. The count and the claims on its verdict and its fault row read the
     * same with or without the cache.
     */
    @Test
    @Story("Stage 2 interrupted partway")
    @DisplayName("When only the record that extraction finished was lost, the next invocation redoes only the document the converter failed on, and records its removal once")
    void aStageWhoseEndOfStepWasLostDoesTheFaultAgainAndRecordsItOnce(CapturedOutput output, @TempDir Path root)
            throws IOException {
        ConverterStopsPartwayBeans.script(NOWHERE, NOWHERE, CONVERTER_FAULT_AT);
        writeCorpus(root, "end of step lost");

        cli.run("run", root.toString());
        String run = onlyExtractionRunOf(root);
        List<Long> faulted = faultedOccurrencesUnder(run);

        claim(
                "the first invocation completes the stage, recording it as finished",
                () -> assertThat(stageTwoFinished(run)).isTrue());
        claim(
                "with the " + CONVERTER_FAULT_AT.size() + " document the converter blamed on itself recorded"
                        + " as a fault and removed as a failed conversion, since the stage completed",
                () -> {
                    assertThat(faulted).hasSize(CONVERTER_FAULT_AT.size());
                    assertThat(extractionFailedVerdictsAgainst(faulted.getFirst(), run)).isEqualTo(ONCE);
                });

        jdbcTemplate.update(
                "DELETE FROM finished_step WHERE run_id = ? AND step = ?", run, StepNames.EXTRACTION);
        emptyTheExtractionCache();
        ConverterStopsPartwayBeans.keepAnswering();
        long faultRows = rowSpanUnder("extraction_fault", run);
        long measured = rowSpanUnder("extraction_metric", run);
        int before = output.getAll().length();
        cli.run("run", root.toString());
        List<String> second = List.of(output.getAll().substring(before).split("\\R"));

        claim(
                "the next invocation says first that it is reading the faults already recorded, over up to the "
                        + faultRows + " the run holds, and how long that took; then the same of the " + measured
                        + " rows already measured; then its count and its read at the end: each line once, in"
                        + " that order",
                () -> assertThat(StatementLines.of(second, STAGE_TWO))
                        .containsExactlyElementsOf(StatementLines.inOrder(
                                StatementLines.countedRead(STAGE_TWO, FAULTS_ALREADY_RECORDED, faultRows),
                                StatementLines.countedRead(STAGE_TWO, OCCURRENCES_ALREADY_MEASURED, measured),
                                THE_COUNT_AND_THE_REVIEW_LIST_READ)));
        claim(
                "with only the record that the stage finished lost, the next invocation reads again the "
                        + CONVERTER_FAULT_AT.size() + " document the converter failed on and none of the "
                        + (CORPUS_SIZE - CONVERTER_FAULT_AT.size()) + " it had converted",
                () -> assertThat(ConverterStopsPartwayBeans.conversions()).isEqualTo(CONVERTER_FAULT_AT.size()));
        claim(
                "that document is removed as a failed conversion exactly once, not once per invocation that"
                        + " completed the stage",
                () -> assertThat(extractionFailedVerdictsAgainst(faulted.getFirst(), run)).isEqualTo(ONCE));
        claim(
                "and its fault is recorded exactly once",
                () -> assertThat(faultRowsAgainst(faulted.getFirst(), run)).isEqualTo(ONCE));
        claim(
                "and the stage is recorded as finished again",
                () -> assertThat(stageTwoFinished(run)).isTrue());
    }

    /**
     * The span of {@code run}'s rowids in {@code table}, greatest less least plus one, which is the total a
     * counted read of that run's rows is stated over (ADR-191 section 2); zero where the run holds none.
     */
    private long rowSpanUnder(String table, String run) {
        Long span = jdbcTemplate.queryForObject(
                "SELECT MAX(rowid) - MIN(rowid) + 1 FROM " + table + " WHERE run_id = ?", Long.class, run);
        return span == null ? 0 : span;
    }

    /**
     * Writes {@link #CORPUS_SIZE} files, each with bytes of its own, so stage 1 removes none as a copy
     * of another. What the converter answers about each is scripted by the order it is first asked
     * about (see {@link ConverterStopsPartwayBeans}), not by its name. {@code salt} keeps one test's
     * corpus from sharing content with another's; two folders written with one salt hold the same
     * documents.
     */
    private static void writeCorpus(Path root, String salt) throws IOException {
        for (int position = 1; position <= CORPUS_SIZE; position++) {
            Files.writeString(
                    root.resolve(String.format("document-%02d.txt", position)),
                    "Document " + position + " of the " + salt + " corpus describes harbour cranes, tide tables"
                            + " and the order in which ships were unloaded during the winter of year " + position
                            + ", with notes on weather and cargo.");
        }
    }

    /** Each stage-2 progress line in {@code log}, as its done count and its total, in order. */
    private static List<String[]> progressLinesIn(String log) {
        Matcher matcher = STAGE_TWO_PROGRESS.matcher(log);
        List<String[]> lines = new ArrayList<>();
        while (matcher.find()) {
            lines.add(new String[] {matcher.group(1), matcher.group(2)});
        }
        return lines;
    }

    private void emptyTheExtractionCache() {
        jdbcTemplate.update("DELETE FROM extraction_cache");
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

    /** How many distinct walks {@code runs} were recorded over. */
    private int walksOf(List<String> runs) {
        return (int) runs.stream()
                .map(run -> jdbcTemplate.queryForObject("SELECT walk_id FROM run WHERE id = ?", Long.class, run))
                .distinct()
                .count();
    }

    /**
     * The occurrences of the run's walk that stage 1 let through and stage 2 has not recorded: no metric
     * row and no stage-2 verdict under the run. A faulted occurrence carries neither (ADR-181 §1).
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

    /** The occurrences carrying an extraction fault row under {@code run}. */
    private List<Long> faultedOccurrencesUnder(String run) {
        return jdbcTemplate.queryForList(
                "SELECT occurrence_id FROM extraction_fault WHERE run_id = ? ORDER BY occurrence_id", Long.class, run);
    }

    private long faultRowsAgainst(long occurrence, String run) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM extraction_fault WHERE occurrence_id = ? AND run_id = ?",
                Long.class,
                occurrence,
                run);
        return count == null ? NONE : count;
    }

    private long extractionFailedVerdictsAgainst(long occurrence, String run) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM verdict WHERE occurrence_id = ? AND run_id = ? AND kind = 'EXTRACTION_FAILED'",
                Long.class,
                occurrence,
                run);
        return count == null ? NONE : count;
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
