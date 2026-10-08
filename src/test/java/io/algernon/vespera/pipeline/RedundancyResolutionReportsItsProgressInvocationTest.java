package io.algernon.vespera.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.algernon.vespera.Adr;
import io.algernon.vespera.corpus.Walk;
import io.algernon.vespera.extraction.ConverterStopsPartwayBeans;
import io.algernon.vespera.ledger.Ledger;
import io.algernon.vespera.ledger.WalkId;
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * What stage 3 and stage 4's second half say while they go through their loops (ADR-192 section 4, #412).
 *
 * <p>Stage 3's frequency rows are written in a loop over every distinct shingle hash
 * {@code DocumentFrequency.measure} counted, and stage 4b's {@code RedundancyResolution.resolve} goes
 * through the candidate pairs, the facts of the members of near-duplicate components, the components and
 * each member written as redundant, and then every signed occurrence looking for a container. On
 * 2026-10-04 4b's resolution took about 26 minutes 40 seconds, by #411's figures, with nothing between its
 * starting and finishing lines. ADR-192 has {@code similarity} tell {@code pipeline} each loop's total and
 * each completion, and {@code pipeline} write the line.
 *
 * <p>The corpus is two texts that differ in their last word, and a third that shares no five-word run with
 * either, so the signatures put the first two forward as a pair. Every total is read from the database, not
 * written here: the pairs from {@code signature_band}, the signed occurrences from {@code minhash_signature},
 * the distinct hashes from {@code shingle}. Each counter's lines are compared with the lines ADR-192 section
 * 8's rule gives over that total, and every one must come from {@code StageProgress}'s logger. 4b's running
 * counter over containment candidates writes its first line at the 1,000th candidate, which this corpus does
 * not reach; its callback is pinned in {@code RedundancyResolutionReportsItsCountsTest}.
 *
 * <p><b>Part (b) of ADR-192.</b> This compiles against main and its first test is red there at the claims
 * that the counters' lines are there; part (b) moves it into {@code src/test} and turns it green. The other
 * two pass on main and have to go on passing: a stage with nothing signed counts nothing, and a stage already
 * recorded does not run its loops.
 *
 * <p><b>The claims about statements came with part (b) of ADR-193</b> (ADR-193 section 6, ADR-204 section 3,
 * #411): stage 3's two drains and its read of the extraction metrics, and the four reads 4b's resolution
 * makes, each with a line before it and a line after it with the seconds it took, and none of them where the
 * stage issued no statement. 4a's count of the survivors it signs is ADR-199's, and {@code
 * UncoveredStatementsInvocationTest} holds its lines.
 */
@CascadeSliceTest
@Import(ConverterStopsPartwayBeans.class)
@Epic("Pipeline")
@Feature("Progress reporting")
@Issue("412")
@Link(name = "ADR-192", url = Adr.EVERY_LOOP_REPORTS_ITS_PROGRESS, type = "adr")
@Link(name = "ADR-093", url = Adr.LOGGING_IS_EXPLICIT_AND_PROCESS_SCOPED, type = "adr")
class RedundancyResolutionReportsItsProgressInvocationTest {

    /** A floor of 1.0 opens stage 4's gate, and strips only what every document shares, which is nothing here. */
    private static final String BOILERPLATE_FLOOR = "1.0";

    private static final String FREQUENCY_ROWS = "Stage 3 (content census, frequency rows)";
    private static final String PAIRS = "Stage 4b (redundancy resolution, near-duplicate candidates)";
    private static final String PROFILES = "Stage 4b (redundancy resolution, occurrence profiles)";
    private static final String COMPONENTS = "Stage 4b (redundancy resolution, near-duplicate components)";
    private static final String VERDICTS = "Stage 4b (redundancy resolution, near-duplicate verdicts)";
    private static final String CONTAINMENT = "Stage 4b (redundancy resolution, containment)";

    /** Every counter this class names, for the claims about who wrote their lines. */
    private static final List<String> EVERY_COUNTER =
            List.of(FREQUENCY_ROWS, PAIRS, PROFILES, COMPONENTS, VERDICTS, CONTAINMENT);

    /** Each stage's own name, which its statement lines open with. */
    private static final String STAGE_THREE = "Stage 3 (content census)";

    private static final String STAGE_FOUR_B = "Stage 4b (redundancy resolution)";

    /** How stage 3's line before its read of the shingle rows opens; the rest is the bound and what a stop costs. */
    private static final String READING_UP_TO = "Stage 3 (content census) is reading up to ";

    private static final String STARTING = "Stage 4b (redundancy resolution) starting under run ";
    private static final String FINISHED = "Stage 4b (redundancy resolution) finished under run ";
    private static final String ALREADY_RECORDED = "Stage 4b (redundancy resolution) was already recorded under run ";

    /** How many documents the corpus holds, each signed. */
    private static final int THREE_DOCUMENTS = 3;

    /** The one near-duplicate pair forms one component of two members, one of which is written redundant. */
    private static final int TWO_MEMBERS = 2;

    private static final int ONE_COMPONENT = 1;

    private static final int ONE_VERDICT = 1;

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
    private Ledger ledger;

    private ListAppender<ILoggingEvent> logged;
    private ch.qos.logback.classic.Logger applicationLogger;

    /** The converter's script and pins are static and shared with every class importing it: reset before as well as after. */
    @BeforeEach
    void startClean() {
        ConverterStopsPartwayBeans.script(List.of(), List.of(), List.of());
        ConverterStopsPartwayBeans.keepAnswering();
        profileStore.save(ProfileFixture.profile()
                .boilerplateDocumentFrequencyFloor(BOILERPLATE_FLOOR, "set by this test, so stage 4's gate is open")
                .build());
        logged = new ListAppender<>();
        logged.start();
        applicationLogger = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger("io.algernon.vespera");
        applicationLogger.addAppender(logged);
    }

    @AfterEach
    void bringTheConverterBack() {
        ConverterStopsPartwayBeans.keepAnswering();
        applicationLogger.detachAppender(logged);
        logged.stop();
    }

    @Test
    @Story("Measuring and resolving redundancy say how far they have got")
    @DisplayName("Stage 3 counts its frequency rows, and resolving redundancy counts each of its loops against its own total")
    void countsEveryLoop(@TempDir Path root) throws IOException {
        aCorpusWithOneNearDuplicatePair(root);

        cli.run("run", root.toString());

        String run = theRun(root, StageModules.CONTENT_REDUNDANCY.stage());
        long signed = documentsSigned(run);
        long pairs = pairsSharingABand(run);
        long hashes = distinctHashes(theRun(root, StageModules.EXTRACTION.stage()));
        claim(
                "the invocation reported success, every one of the three documents was signed, one pair was"
                        + " recorded as near-duplicates, and the step started and finished",
                () -> {
                    assertThat(cli.getExitCode()).isZero();
                    assertThat(signed).isEqualTo(THREE_DOCUMENTS);
                    assertThat(nearDuplicatesRecorded(run)).isEqualTo(ONE_VERDICT);
                    assertThat(operatorLines()).anyMatch(line -> line.startsWith(STARTING))
                            .anyMatch(line -> line.startsWith(FINISHED));
                });
        claim(
                "stage 3 counts the " + hashes + " distinct shingle hashes it goes through for a frequency row,"
                        + " those seen in one document included",
                () -> assertThat(ProgressLines.of(logged.list, FREQUENCY_ROWS))
                        .containsExactlyElementsOf(ProgressLines.expected(FREQUENCY_ROWS, hashes)));
        claim(
                "the pair counter counts the " + pairs + " pairs that share a band under this run",
                () -> assertThat(ProgressLines.of(logged.list, PAIRS))
                        .containsExactlyElementsOf(ProgressLines.expected(PAIRS, pairs)));
        claim(
                "the two members of the one component are read for their facts, the one component is resolved,"
                        + " and its one member that is not the survivor is written redundant with it",
                () -> {
                    assertThat(ProgressLines.of(logged.list, PROFILES))
                            .containsExactlyElementsOf(ProgressLines.expected(PROFILES, TWO_MEMBERS));
                    assertThat(ProgressLines.of(logged.list, COMPONENTS))
                            .containsExactlyElementsOf(ProgressLines.expected(COMPONENTS, ONE_COMPONENT));
                    assertThat(ProgressLines.of(logged.list, VERDICTS))
                            .containsExactlyElementsOf(ProgressLines.expected(VERDICTS, ONE_VERDICT));
                });
        claim(
                "the containment counter reads one, two and three of three: every signed document, the one the"
                        + " pair removed included, because the step has been through it",
                () -> assertThat(ProgressLines.of(logged.list, CONTAINMENT))
                        .containsExactlyElementsOf(ProgressLines.expected(CONTAINMENT, signed)));
        claim(
                "4b's counters lie between its starting and finishing lines, in the order it goes through its"
                        + " loops, a component's verdicts written before the component is counted as resolved",
                () -> assertThat(String.join("\n", operatorLines()))
                        .containsSubsequence(
                                STARTING, PAIRS + ": 1 of ", PROFILES + ": 1 of ", VERDICTS + ": 1 of ",
                                COMPONENTS + ": 1 of ", CONTAINMENT + ": 1 of ", FINISHED));
        long metricRows = rowSpanUnder("extraction_metric", theRun(root, StageModules.EXTRACTION.stage()));
        long signatureRows = rowSpanUnder("minhash_signature", run);
        claim(
                "stage 3 says what it is reading and how long each read took, each line once, in the order it"
                        + " reads: the documents stage 2 left, for the shingle frequencies; the shingle rows; the"
                        + " documents stage 2 left again, for the confidence spread; and the extraction metrics,"
                        + " over up to the " + metricRows + " rows stage 2's run holds",
                () -> assertThat(shingleRowsLineShortened(StatementLines.of(operatorLines(), STAGE_THREE)))
                        .containsExactlyElementsOf(StatementLines.inOrder(
                                StatementLines.timedRead(STAGE_THREE, "stage 2's survivors for the shingle frequencies"),
                                List.of(READING_UP_TO),
                                StatementLines.timedRead(
                                        STAGE_THREE, "stage 2's survivors for the confidence distribution"),
                                StatementLines.countedRead(STAGE_THREE, "the extraction metrics", metricRows))));
        claim(
                "resolving says what it is reading and how long each read took, each line once, in the order it"
                        + " reads: the signed documents, over up to the " + signatureRows + " signature rows of its"
                        + " run; the signature bands; the extraction metrics of the near-duplicates; and the"
                        + " shingle frequencies",
                () -> assertThat(StatementLines.of(operatorLines(), STAGE_FOUR_B))
                        .containsExactlyElementsOf(StatementLines.inOrder(
                                StatementLines.countedRead(STAGE_FOUR_B, "the signed occurrences", signatureRows),
                                StatementLines.timedRead(STAGE_FOUR_B, "the signature bands"),
                                StatementLines.timedRead(STAGE_FOUR_B, "the near-duplicates' extraction metrics"),
                                StatementLines.timedRead(STAGE_FOUR_B, "the shingle document frequencies"))));
        claim(
                "each of resolving's reads lies where the step makes it: the signed documents and the bands"
                        + " before the first pair is scored, the near-duplicates' metrics before the first of"
                        + " them is counted as read, and the shingle frequencies before the first document is"
                        + " checked for a container",
                () -> assertThat(String.join("\n", operatorLines()))
                        .containsSubsequence(
                                STARTING,
                                STAGE_FOUR_B + " read the signed occurrences in ",
                                STAGE_FOUR_B + " read the signature bands in ",
                                PAIRS + ": 1 of ",
                                STAGE_FOUR_B + " is reading the near-duplicates' extraction metrics",
                                PROFILES + ": 1 of ",
                                STAGE_FOUR_B + " read the shingle document frequencies in ",
                                CONTAINMENT + ": 1 of ",
                                FINISHED));
        for (String counter : EVERY_COUNTER) {
            claim(
                    "every line of " + counter + " was written by the progress counter itself",
                    () -> assertThat(ProgressLines.loggersOf(logged.list, counter))
                            .isNotEmpty()
                            .containsOnly(ProgressLines.theCountersLogger()));
        }
    }

    @Test
    @Story("Measuring and resolving redundancy say how far they have got")
    @DisplayName("Resolving redundancy over documents too short to sign has nothing to count and counts nothing")
    void countsNothingWhenNoDocumentWasSigned(@TempDir Path root) throws IOException {
        for (int position = 1; position <= THREE_DOCUMENTS; position++) {
            Files.writeString(root.resolve(String.format("%02d-brief.txt", position)), "Memo " + position + " brief");
        }

        cli.run("run", root.toString());

        claim(
                "no document was signed, because each text is shorter than one five-word run",
                () -> assertThat(documentsSigned(theRun(root, StageModules.CONTENT_REDUNDANCY.stage()))).isZero());
        claim(
                "the step started and finished all the same",
                () -> assertThat(operatorLines()).anyMatch(line -> line.startsWith(STARTING))
                        .anyMatch(line -> line.startsWith(FINISHED)));
        claim(
                "and no 4b counter wrote a line: resolution returns before any loop when nothing is signed",
                () -> assertThat(List.of(PAIRS, PROFILES, COMPONENTS, VERDICTS, CONTAINMENT))
                        .allSatisfy(counter -> assertThat(ProgressLines.of(logged.list, counter)).isEmpty()));
        claim(
                "nor does it say it is reading anything: the read of the signed documents has no row to go"
                        + " through, so it has nothing to wait for, and no later read is made",
                () -> assertThat(StatementLines.of(operatorLines(), STAGE_FOUR_B)).isEmpty());
    }

    @Test
    @Story("Measuring and resolving redundancy say how far they have got")
    @DisplayName("Measuring and resolving redundancy that are already recorded run no loop and count nothing")
    void countsNothingWhenTheStepIsAlreadyRecorded(@TempDir Path root) throws IOException {
        aCorpusWithOneNearDuplicatePair(root);
        cli.run("run", root.toString());
        logged.list.clear();

        cli.run("run", root.toString());

        claim(
                "the second invocation finds 4b already recorded and does not start it again",
                () -> assertThat(operatorLines())
                        .anyMatch(line -> line.startsWith(ALREADY_RECORDED))
                        .noneMatch(line -> line.startsWith(STARTING)));
        claim(
                "so no counter of stage 3 or of 4b writes a line: the loops it counts did not run",
                () -> assertThat(EVERY_COUNTER)
                        .allSatisfy(counter -> assertThat(ProgressLines.of(logged.list, counter)).isEmpty()));
        claim(
                "and neither stage 3 nor resolving says it is reading anything: a step already recorded makes"
                        + " none of its reads",
                () -> {
                    assertThat(StatementLines.of(operatorLines(), STAGE_THREE)).isEmpty();
                    assertThat(StatementLines.of(operatorLines(), STAGE_FOUR_B)).isEmpty();
                });
    }

    /** {@code said} with stage 3's line before its read of the shingle rows cut to how it opens. */
    private static List<String> shingleRowsLineShortened(List<String> said) {
        return said.stream()
                .map(line -> line.startsWith(READING_UP_TO) ? READING_UP_TO : line)
                .toList();
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
     * What resolving again over synthetic signatures leaves alone (ADR-204, Tests): the verdicts under the
     * redundancy run. The first invocation writes one, for the near-duplicate pair. Then signature rows are
     * written under its run against the files of a folder nobody walked, enough for the read of the signed
     * occurrences to pass one callback, the record that 4b finished is deleted, and the next invocation
     * resolves again under the same run with those rows among the signed. Which of the pair is the one
     * removed is not written here: the verdicts after are compared with the verdicts before.
     */
    @Test
    @Story("Measuring and resolving redundancy say how far they have got")
    @DisplayName("Resolving again with thousands of synthetic signatures among the signed writes the verdict it wrote before and no other")
    void resolvingAgainOverSyntheticSignaturesWritesTheSameVerdicts(@TempDir Path root) throws IOException {
        aCorpusWithOneNearDuplicatePair(root);
        cli.run("run", root.toString());
        String run = theRun(root, StageModules.CONTENT_REDUNDANCY.stage());
        List<String> verdictsOfTheFirstInvocation = verdictsUnder(run);
        claim(
                "the first invocation wrote one verdict under the redundancy run, for the one of the pair it"
                        + " removed, so there is a verdict for the second to keep or to lose",
                () -> assertThat(verdictsOfTheFirstInvocation).hasSize(ONE_VERDICT));
        List<Long> nobodysFiles = filesOfAFolderNobodyWalked(SIGNATURES_PAST_ONE_CALLBACK);
        List<Object[]> signatures = new ArrayList<>();
        for (Long file : nobodysFiles) {
            signatures.add(new Object[] {file, run});
        }
        jdbcTemplate.batchUpdate(
                "INSERT INTO minhash_signature (occurrence_id, run_id, signature_identity, signature)"
                        + " VALUES (?, ?, 'synthetic', x'00')",
                signatures);
        jdbcTemplate.update(
                "DELETE FROM finished_step WHERE run_id = ? AND step = ?", run, StepNames.CONTENT_REDUNDANCY);
        logged.list.clear();

        cli.run("run", root.toString());

        claim(
                "the second invocation reported success and resolved again, under the same run",
                () -> {
                    assertThat(cli.getExitCode()).isZero();
                    assertThat(operatorLines()).anyMatch(line -> line.startsWith(STARTING + run));
                });
        claim(
                "the read of the signed occurrences went through the synthetic rows: it said about how far it"
                        + " had gone, which a read of three rows never does",
                () -> assertThat(operatorLines())
                        .anyMatch(line -> line.startsWith(
                                "Stage 4b (redundancy resolution, reading signed occurrences): about ")));
        claim(
                "the verdicts under the redundancy run are the one the first invocation wrote and no other: the"
                        + " same document, kind and reason",
                () -> assertThat(verdictsUnder(run)).isEqualTo(verdictsOfTheFirstInvocation));
        claim(
                "one pair is still recorded as near-duplicates, and no verdict of any run is against a file of"
                        + " the folder nobody walked",
                () -> {
                    assertThat(nearDuplicatesRecorded(run)).isEqualTo(ONE_VERDICT);
                    assertThat(jdbcTemplate.queryForObject(
                                    "SELECT COUNT(*) FROM verdict WHERE occurrence_id >= ? AND occurrence_id <= ?",
                                    Long.class,
                                    nobodysFiles.getFirst(),
                                    nobodysFiles.getLast()))
                            .isZero();
                });
    }

    /** Enough signature rows, at five steps a row, to pass the 100,000 steps at which SQLite first calls back. */
    private static final int SIGNATURES_PAST_ONE_CALLBACK = 21_000;

    /** Every verdict under {@code run}, as the occurrence, the kind and the reason, in a fixed order. */
    private List<String> verdictsUnder(String run) {
        return jdbcTemplate.queryForList(
                "SELECT occurrence_id || ' ' || kind || ' ' || reason FROM verdict WHERE run_id = ?"
                        + " ORDER BY occurrence_id, kind, reason",
                String.class,
                run);
    }

    /** {@code files} recorded under a walk of a folder that does not exist: in no run's survivors. */
    private List<Long> filesOfAFolderNobodyWalked(int files) {
        WalkId walk = ledger.walks().startWalk(Path.of("C:/synthetic-" + System.nanoTime()));
        List<Object[]> rows = new ArrayList<>();
        for (int file = 0; file < files; file++) {
            rows.add(new Object[] {walk.value(), "synthetic-" + file + ".txt"});
        }
        jdbcTemplate.batchUpdate(
                "INSERT INTO file_occurrence (walk_id, path, size_bytes, last_modified, creation_time)"
                        + " VALUES (?, ?, 1, 1, 1)",
                rows);
        return jdbcTemplate.queryForList(
                "SELECT id FROM file_occurrence WHERE walk_id = ? ORDER BY id", Long.class, walk.value());
    }

    /**
     * Two texts that differ in their last word only, so their five-word runs are nearly all shared, and a
     * third about something else. Their bytes differ, so stage 1 removes none as a copy of another.
     */
    private static void aCorpusWithOneNearDuplicatePair(Path root) throws IOException {
        String harbour = "The harbour master recorded every vessel entering the northern basin during the morning"
                + " tide, noting cargo weights, crew lists, berth assignments, pilot fees and customs stamps"
                + " before the afternoon fog rolled across the breakwater and delayed all departures until ";
        Files.writeString(root.resolve("01-harbour-log.txt"), harbour + "evening.");
        Files.writeString(root.resolve("02-harbour-log-copy.txt"), harbour + "sunrise.");
        Files.writeString(
                root.resolve("03-turbine-maintenance.txt"),
                "Quarterly turbine maintenance requires lubricant sampling, blade inspection, vibration analysis,"
                        + " thermal imaging, torque verification and calibration of governor sensors, followed by"
                        + " written certification from the chief engineer before any generator returns to service.");
    }

    /** The one run of {@code stage} over {@code root}. */
    private String theRun(Path root, String stage) {
        return jdbcTemplate.queryForObject(
                "SELECT r.id FROM run r JOIN walk w ON w.id = r.walk_id WHERE r.stage = ? AND w.root = ?",
                String.class,
                stage,
                Walk.canonicalRoot(root).toString());
    }

    private long documentsSigned(String run) {
        Long signed = jdbcTemplate.queryForObject(
                "SELECT COUNT(DISTINCT occurrence_id) FROM minhash_signature WHERE run_id = ?", Long.class, run);
        return signed == null ? 0 : signed;
    }

    private long nearDuplicatesRecorded(String run) {
        Long recorded = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM redundant_with WHERE run_id = ? AND relation = 'near-duplicate'",
                Long.class,
                run);
        return recorded == null ? 0 : recorded;
    }

    /** Distinct pairs of occurrences that share a band under {@code run}: what candidate generation reads. */
    private long pairsSharingABand(String run) {
        Long pairs = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM (SELECT DISTINCT a.occurrence_id AS a, b.occurrence_id AS b"
                        + " FROM signature_band a JOIN signature_band b ON a.run_id = b.run_id"
                        + " AND a.band_ordinal = b.band_ordinal AND a.band_hash = b.band_hash"
                        + " AND a.occurrence_id < b.occurrence_id WHERE a.run_id = ?)",
                Long.class,
                run);
        return pairs == null ? 0 : pairs;
    }

    /** Distinct (granularity, hash) pairs among stage 2's shingle rows of {@code run}, every occurrence a survivor here. */
    private long distinctHashes(String run) {
        Long hashes = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM (SELECT DISTINCT shingle_parameter_identity, shingle_hash FROM shingle"
                        + " WHERE run_id = ?)",
                Long.class,
                run);
        return hashes == null ? 0 : hashes;
    }

    private List<String> operatorLines() {
        return logged.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
    }
}
