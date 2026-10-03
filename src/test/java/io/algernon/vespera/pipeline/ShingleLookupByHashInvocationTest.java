package io.algernon.vespera.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.algernon.vespera.Adr;
import io.algernon.vespera.corpus.Walk;
import io.algernon.vespera.extraction.ConverterStopsAnsweringBeans;
import io.algernon.vespera.ledger.Ledger;
import io.algernon.vespera.ledger.RunId;
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
import java.util.List;
import java.util.Locale;
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
 * When {@code shingle_by_hash} stands in the database, and when it does not (ADR-182, #381).
 *
 * <p>Measured on a synthetic database of ten million shingle rows, keeping the lookup by hash up to date
 * row by row was most of what one stage-2 chunk cost to commit, and nothing reads it until stage 4b. So
 * stage 2 writes without it, and stage 4b builds it, once, before it reads it. These tests drive whole
 * invocations and read the database's own catalogue, {@code sqlite_master}, because whether an index
 * exists is a fact about the file and not about any table's rows.
 *
 * <p>What stage 2 wrote with is seen by {@link ShingleWritesProbe}, from inside each write: the database
 * at the end of the job cannot say, since stage 4b may have built the lookup by then.
 *
 * <p>Stage 2 is stopped part-way by {@link ConverterStopsAnsweringBeans}: the converter answers one whole
 * chunk of the corpus and a little of the next, and then stops, so the breaker fails the step with one
 * chunk committed. The stopped invocation has Spring Batch log the step's failure with its stack trace
 * at {@code ERROR}; that is the step failing as it should.
 *
 * <p>Every claim reads the rows over this test's own corpus walk, because the whole class shares one
 * database. The index is not scoped that way, so each test sets up the state it starts from and every
 * claim about the index holds in either class order.
 */
@CascadeSliceTest
@Import({ConverterStopsAnsweringBeans.class, ShingleWritesProbe.class})
@Epic("Redundancy")
@Feature("Resolution")
@Issue("381")
@Link(name = "ADR-182", url = Adr.STAGE_2_WRITES_SHINGLES_WITHOUT_THE_LOOKUP_BY_HASH, type = "adr")
class ShingleLookupByHashInvocationTest {

    /** The logger every operator-facing line in this application is written through. */
    private static final String APPLICATION_LOGGER = "io.algernon.vespera";

    /** A floor of 1.0 opens stage 4's gate the way every other whole-job test opens it. */
    private static final String BOILERPLATE_FLOOR = "1.0";

    /** Stage 2's step, by its persisted name. */
    private static final String EXTRACTION_STEP = "extraction";

    /** Stage 4b's step, by its persisted name. */
    private static final String REDUNDANCY_RESOLUTION_STEP = "content-redundancy";

    /** The lookup's columns, in order: one run's shingles under one granularity, by hash. */
    private static final List<String> LOOKUP_COLUMNS = List.of("run_id", "shingle_parameter_identity", "shingle_hash");

    /** What stage 4b says before it builds the lookup. */
    private static final String BUILDING = "building the shingle lookup by hash over ";

    /** What stage 4b says once it is built. */
    private static final String BUILT = "built the shingle lookup by hash in ";

    /** What stage 2 says before it drops a lookup it finds standing. */
    private static final String DROPPING = "dropping the shingle lookup by hash";

    /** What stage 2 says once it is dropped. */
    private static final String DROPPED = "dropped the shingle lookup by hash in ";

    /** The lookup as every start built it before ADR-182, and as stage 4b builds it now. */
    private static final String BUILD_THE_LOOKUP =
            "CREATE INDEX IF NOT EXISTS shingle_by_hash ON shingle (run_id, shingle_parameter_identity, shingle_hash)";

    /** One whole stage-2 chunk is answered and committed, then two documents of the next. */
    private static final int ANSWERED_BEFORE_THE_CONVERTER_STOPS = ExtractionJobConfiguration.CHUNK_SIZE + 2;

    /** Three chunks of documents, so the converter stops with a whole chunk still unread. */
    private static final int CORPUS_SIZE = 3 * ExtractionJobConfiguration.CHUNK_SIZE;

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

    /**
     * Before each test as well as after it, because another class may have armed the converter or left
     * the probe holding its writes, and class order is decided per machine.
     */
    @BeforeEach
    void startClean() {
        ConverterStopsAnsweringBeans.keepAnswering();
        ShingleWritesProbe.forget();
        logged = new ListAppender<>();
        logged.start();
        applicationLogger = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(APPLICATION_LOGGER);
        applicationLogger.addAppender(logged);
    }

    @AfterEach
    void cleanUp() {
        ConverterStopsAnsweringBeans.keepAnswering();
        ShingleWritesProbe.forget();
        applicationLogger.detachAppender(logged);
        logged.stop();
    }

    @Test
    @Story("Stage 2 writes shingles without keeping the lookup by hash up to date")
    @DisplayName("A stage 2 that stopped part-way wrote every shingle without the lookup by hash, and leaves none behind")
    void aStage2StoppedPartwayLeavesNoLookupByHash(@TempDir Path root, @TempDir Path seeds) throws IOException {
        writeCorpus(root, "first");
        profile(seeds, BOILERPLATE_FLOOR);
        ConverterStopsAnsweringBeans.stopAnsweringAfter(root, ANSWERED_BEFORE_THE_CONVERTER_STOPS);

        cli.run("run", root.toString());

        claim(
                "the converter really did stop part-way through the corpus, and the invocation failed",
                () -> {
                    assertThat(ConverterStopsAnsweringBeans.refusedConversions()).isPositive();
                    assertThat(cli.getExitCode()).isNotZero();
                });
        claim(
                "stage 2 is not recorded as finished, and the chunk committed before the stop left shingles",
                () -> {
                    assertThat(ledger.stepFinished(theOnlyRunOver(root, "extraction"), EXTRACTION_STEP)).isFalse();
                    assertThat(shingleRowsOver(root)).isPositive();
                });
        claim(
                "every document stage 2 wrote shingles for was written while no lookup by hash stood, so no"
                        + " write paid for keeping it up to date",
                () -> assertThat(ShingleWritesProbe.LOOKUP_STOOD_WHEN_AN_OCCURRENCE_WAS_WRITTEN)
                        .isNotEmpty()
                        .containsOnly(false));
        claim(
                "and the stopped invocation leaves no lookup by hash in the database",
                () -> assertThat(lookupByHashStands()).isFalse());
    }

    @Test
    @Story("Stage 2 writes shingles without keeping the lookup by hash up to date")
    @DisplayName("Resumed after a stop, stage 2 writes without the lookup by hash, and the redundancy step ends with it built and says so")
    void aResumedStage2WritesWithoutTheLookupAndRedundancyResolutionEndsWithItBuilt(
            @TempDir Path root, @TempDir Path seeds) throws IOException {
        writeCorpus(root, "second");
        profile(seeds, BOILERPLATE_FLOOR);
        ConverterStopsAnsweringBeans.stopAnsweringAfter(root, ANSWERED_BEFORE_THE_CONVERTER_STOPS);
        cli.run("run", root.toString());
        claim(
                "the first invocation stopped inside stage 2",
                () -> assertThat(ledger.stepFinished(theOnlyRunOver(root, "extraction"), EXTRACTION_STEP))
                        .isFalse());
        ConverterStopsAnsweringBeans.keepAnswering();
        ShingleWritesProbe.forget();
        logged.list.clear();

        cli.run("run", root.toString());

        claim(
                "with the converter back, stage 2 and the redundancy step are both recorded as finished",
                () -> {
                    assertThat(ledger.stepFinished(theOnlyRunOver(root, "extraction"), EXTRACTION_STEP)).isTrue();
                    assertThat(ledger.stepFinished(
                                    theOnlyRunOver(root, "content-redundancy"), REDUNDANCY_RESOLUTION_STEP))
                            .isTrue();
                });
        claim(
                "every document the resumed stage 2 wrote shingles for was written while no lookup by hash"
                        + " stood",
                () -> assertThat(ShingleWritesProbe.LOOKUP_STOOD_WHEN_AN_OCCURRENCE_WAS_WRITTEN)
                        .isNotEmpty()
                        .containsOnly(false));
        claim(
                "the invocation ends with the lookup by hash built, over one run's shingles under one"
                        + " granularity, by hash, so the redundancy step's containment search can use it",
                () -> {
                    assertThat(lookupByHashStands()).isTrue();
                    assertThat(lookupColumns()).containsExactlyElementsOf(LOOKUP_COLUMNS);
                });
        long rows = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM shingle", Long.class);
        String rowCount = String.format(Locale.ROOT, "%,d", rows);
        claim(
                "before building it the operator is told it is being built and over how many shingle rows ("
                        + rowCount + ", every row in the database, since the lookup covers them all), and once"
                        + " it is built, how long it took, so a long silence reads as work and not as a hang",
                () -> {
                    List<String> lines = operatorLines();
                    int building = indexOfLineContaining(lines, BUILDING + rowCount + " shingle rows");
                    int built = indexOfLineContaining(lines, BUILT);
                    assertThat(building).isNotNegative();
                    assertThat(built).isGreaterThan(building);
                });
    }

    @Test
    @Story("The lookup by hash is built by the step that reads it")
    @DisplayName("With the redundancy gate shut, a finished stage 2 leaves no lookup by hash, because nothing will read it")
    void stage2WithTheRedundancyGateShutLeavesNoLookupByHash(@TempDir Path root, @TempDir Path seeds)
            throws IOException {
        writeCorpus(root, "third");
        profile(seeds, null);

        cli.run("run", root.toString());

        claim(
                "stage 2 is recorded as finished, and no redundancy run was minted behind the shut gate",
                () -> {
                    assertThat(ledger.stepFinished(theOnlyRunOver(root, "extraction"), EXTRACTION_STEP)).isTrue();
                    assertThat(runIdsOver(root, "content-redundancy")).isEmpty();
                });
        claim(
                "every document stage 2 wrote shingles for was written while no lookup by hash stood",
                () -> assertThat(ShingleWritesProbe.LOOKUP_STOOD_WHEN_AN_OCCURRENCE_WAS_WRITTEN)
                        .isNotEmpty()
                        .containsOnly(false));
        claim(
                "and the invocation leaves no lookup by hash: only the redundancy step reads it, and it did"
                        + " not run",
                () -> assertThat(lookupByHashStands()).isFalse());
        claim(
                "nothing told the operator a lookup was being built",
                () -> assertThat(operatorLines()).noneMatch(line -> line.contains(BUILDING)));
    }

    /**
     * A database written before ADR-182 carries the lookup, built by every start, and so does one where
     * stage 4b has built it since. Either way, the next stage 2 with work to do finds it standing.
     */
    @Test
    @Story("Stage 2 writes shingles without keeping the lookup by hash up to date")
    @DisplayName("A lookup by hash left standing is dropped before stage 2 writes, and the operator is told while it happens")
    void aLookupLeftStandingIsDroppedBeforeStage2Writes(@TempDir Path root, @TempDir Path seeds) throws IOException {
        writeCorpus(root, "fourth");
        profile(seeds, null);
        jdbcTemplate.execute(BUILD_THE_LOOKUP);
        claim("the lookup by hash stands before the invocation starts", () -> assertThat(lookupByHashStands()).isTrue());

        cli.run("run", root.toString());

        claim(
                "stage 2 is recorded as finished",
                () -> assertThat(ledger.stepFinished(theOnlyRunOver(root, "extraction"), EXTRACTION_STEP)).isTrue());
        claim(
                "every file occurrence stage 2 wrote shingles for was written after the lookup was gone",
                () -> assertThat(ShingleWritesProbe.LOOKUP_STOOD_WHEN_AN_OCCURRENCE_WAS_WRITTEN)
                        .isNotEmpty()
                        .containsOnly(false));
        claim(
                "the operator is told the lookup is being dropped and then that it was, since on a large"
                        + " archive dropping it takes a while of its own",
                () -> {
                    List<String> lines = operatorLines();
                    int dropping = indexOfLineContaining(lines, DROPPING);
                    int dropped = indexOfLineContaining(lines, DROPPED);
                    assertThat(dropping).isNotNegative();
                    assertThat(dropped).isGreaterThan(dropping);
                });
        claim(
                "and with the redundancy gate shut nothing builds it again",
                () -> assertThat(lookupByHashStands()).isFalse());
    }

    /** {@code CORPUS_SIZE} corpus files, each with bytes of its own so none is a cache hit or a duplicate. */
    private static void writeCorpus(Path root, String test) throws IOException {
        for (int ordinal = 1; ordinal <= CORPUS_SIZE; ordinal++) {
            Files.writeString(
                    root.resolve("document-%02d.txt".formatted(ordinal)),
                    "corpus document " + ordinal + " of " + CORPUS_SIZE + ", for #381's " + test + " test");
        }
    }

    /** A seed folder named, and stage 4's gate open at {@code boilerplateFloor}, or shut where it is null. */
    private void profile(Path seeds, String boilerplateFloor) throws IOException {
        Files.writeString(seeds.resolve("seed.txt"), "a seed document");
        ProfileFixture profile = ProfileFixture.profile().seedFolder(seeds.toString(), "set by this test");
        if (boilerplateFloor != null) {
            profile.boilerplateDocumentFrequencyFloor(boilerplateFloor, "set by this test, so stage 4's gate is open");
        }
        profileStore.save(profile.build());
    }

    /** Whether {@code shingle_by_hash} stands in the database, read from SQLite's own catalogue. */
    private boolean lookupByHashStands() {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sqlite_master WHERE type = 'index' AND name = ?",
                Long.class,
                ShingleWritesProbe.LOOKUP_BY_HASH);
        return count != null && count > 0;
    }

    /** The lookup's columns in index order. */
    private List<String> lookupColumns() {
        return jdbcTemplate.query(
                "SELECT name FROM pragma_index_info(?) ORDER BY seqno",
                (resultSet, rowNumber) -> resultSet.getString("name"),
                ShingleWritesProbe.LOOKUP_BY_HASH);
    }

    private static int indexOfLineContaining(List<String> lines, String text) {
        for (int line = 0; line < lines.size(); line++) {
            if (lines.get(line).contains(text)) {
                return line;
            }
        }
        return -1;
    }

    /** Every operator-facing line written since the last clear, in the order written. */
    private List<String> operatorLines() {
        return logged.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
    }

    private long shingleRowsOver(Path root) {
        Long rows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM shingle s JOIN file_occurrence o ON o.id = s.occurrence_id WHERE o.walk_id = ?",
                Long.class,
                theCorpusWalkOf(root).value());
        return rows == null ? 0 : rows;
    }

    private RunId theOnlyRunOver(Path root, String stage) {
        List<RunId> runs = runIdsOver(root, stage);
        claim(
                "exactly one " + stage + " run stands over this test's corpus, so the claims about it read that run",
                () -> assertThat(runs).hasSize(1));
        return runs.getFirst();
    }

    private List<RunId> runIdsOver(Path root, String stage) {
        return jdbcTemplate
                .queryForList(
                        "SELECT id FROM run WHERE stage = ? AND walk_id = ?",
                        String.class,
                        stage,
                        theCorpusWalkOf(root).value())
                .stream()
                .map(RunId::new)
                .toList();
    }

    private WalkId theCorpusWalkOf(Path root) {
        return ledger.finishedWalkFor(Walk.canonicalRoot(root))
                .orElseThrow(() -> new IllegalStateException("census recorded no finished walk of " + root));
    }
}
