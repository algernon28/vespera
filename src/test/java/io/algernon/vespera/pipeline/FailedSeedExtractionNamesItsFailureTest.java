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
import io.algernon.vespera.profile.Profile;
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
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * What seed extraction says, and leaves behind, when its step fails part-way through the seed folder
 * because the converter stopped answering (#306).
 *
 * <p>Found on the first end-to-end run over a real archive: nine seeds of nineteen had converted, the
 * sidecar crashed, the chunk in flight rolled back, and the step failed. The writer's {@code afterStep}
 * runs whether or not the step completed, and it read the outcomes it had been handed as though they
 * were the whole folder. With none handed to it, it took ADR-083's no-usable-seed branch and told the
 * operator that no seed had any text and that the seed folder should be fixed. Every clause of that
 * was false, and the one thing to do, bring the converter back and run the same command again, was
 * said nowhere.
 *
 * <p>The same reading has a second consequence, where the failing chunk is not the first. The
 * outcomes of the chunks already written are usable, so the writer minted the measurement run, wrote
 * those seeds' rows under it, and recorded the step as finished (ADR-116) over a seed set that was
 * missing every seed after the crash. The next invocation then finds the step finished and writes
 * nothing, so the seeds the crash cut off are never measured under that run. That is the permanent
 * hole ADR-152 and ADR-155 refuse for a seed file that will not open, reached here by a different road.
 *
 * <p>The converter is stopped by {@link ConverterStopsAnsweringBeans}, one layer below the extractor,
 * so the cache and the conversion path around it are the production ones. The first test stops it
 * inside the first chunk, which is the case measured on 2026-09-26. The second stops it in a later
 * chunk, after one whole chunk of seeds has been written.
 *
 * <p>Both invocations that fail here have Spring Batch log the step's failure with its stack trace at
 * {@code ERROR}. That is the step failing as it should, not noise from the test: nothing here asks the
 * step to succeed while the converter is down.
 */
@CascadeSliceTest
@Import(ConverterStopsAnsweringBeans.class)
@Epic("Relevance")
@Feature("Seed set")
@Issue("306")
@Link(name = "ADR-083", url = Adr.THE_SEED_SET_IS_EXTRACTED_BY_STAGE_5, type = "adr")
@Link(name = "ADR-116", url = Adr.A_RUNS_COMPLETION_IS_RECORDED_PER_STEP, type = "adr")
class FailedSeedExtractionNamesItsFailureTest {

    /** The logger every operator-facing line in this application is written through. */
    private static final String APPLICATION_LOGGER = "io.algernon.vespera";

    /** The clause of ADR-083's no-usable-seed line that is false about a step that failed. */
    private static final String NONE_OF_THEM_WAS_USABLE = "none of them was usable";

    /** The clause of the same line that sends the operator to the wrong fix. */
    private static final String FIX_THE_SEED_FOLDER = "Fix the seed folder";

    /** What the operator should be told to do once the cause is fixed. */
    private static final String RUN_THE_SAME_COMMAND_AGAIN = "run the same command again";

    /**
     * A floor of 1.0, the least aggressive value that still opens stage 4's gate: nothing here is
     * about what stage 4 concludes, only about stage 5 being reached at all.
     */
    private static final String BOILERPLATE_FLOOR = "1.0";

    /** Seeds converted before the converter stops, inside the first chunk, as on 2026-09-26. */
    private static final int CONVERTED_INSIDE_THE_FIRST_CHUNK = 2;

    /** One seed more than that, so the step reaches a seed the converter no longer answers for. */
    private static final int SEEDS_IN_A_SHORT_FOLDER = CONVERTED_INSIDE_THE_FIRST_CHUNK + 1;

    /**
     * Seeds converted before the converter stops when the crash is in a later chunk: one whole chunk,
     * which the step writes, and one seed of the next.
     */
    private static final int CONVERTED_PAST_THE_FIRST_CHUNK = SeedExtractionJobConfiguration.CHUNK_SIZE + 1;

    /** Two seeds more than a whole chunk, so the second chunk holds a seed the converter answers and one it does not. */
    private static final int SEEDS_IN_A_LONGER_FOLDER = SeedExtractionJobConfiguration.CHUNK_SIZE + 2;

    @TempDir
    static Path workingDirectory;

    @DynamicPropertySource
    static void workingDirectory(DynamicPropertyRegistry registry) {
        registry.add("vespera.working-dir", workingDirectory::toString);
    }

    @Autowired
    private VesperaCli cli;

    @Autowired
    private Ledger ledger;

    @Autowired
    private ProfileStore profileStore;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private ListAppender<ILoggingEvent> logged;
    private ch.qos.logback.classic.Logger applicationLogger;

    @BeforeEach
    void captureOperatorLines() {
        ConverterStopsAnsweringBeans.keepAnswering();
        logged = new ListAppender<>();
        logged.start();
        applicationLogger = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(APPLICATION_LOGGER);
        applicationLogger.addAppender(logged);
    }

    @AfterEach
    void releaseOperatorLines() {
        ConverterStopsAnsweringBeans.keepAnswering();
        applicationLogger.detachAppender(logged);
        logged.stop();
    }

    @Test
    @Story("A seed extraction that failed says why, and says nothing about the seed folder")
    @DisplayName("When the converter stops answering inside the first batch of seeds, the operator is told the step failed and to run it again, not to fix the seed folder")
    void aConverterThatStopsInsideTheFirstChunkIsNamedAndTheSeedFolderIsNotBlamed(
            @TempDir Path root, @TempDir Path seeds) throws IOException {
        Files.writeString(root.resolve("corpus.txt"), "a corpus document, for #306's first test");
        writeSeeds(seeds, SEEDS_IN_A_SHORT_FOLDER, "first");
        profile(seeds);
        ConverterStopsAnsweringBeans.stopAnsweringAfter(seeds, CONVERTED_INSIDE_THE_FIRST_CHUNK);

        cli.run("run", root.toString());

        claim(
                "the converter really did stop answering part-way through the seed folder, so what follows"
                        + " is about a step that failed and not about one that finished",
                () -> assertThat(ConverterStopsAnsweringBeans.refusedConversions()).isPositive());
        claim(
                "the invocation failed, because the step did: a converter that stopped answering is not a"
                        + " fact about any seed",
                () -> assertThat(cli.getExitCode()).isNotZero());
        claim(
                "nothing tells the operator that none of the seeds was usable: the step never finished"
                        + " reading them, and the seeds it did read had text in them",
                () -> assertThat(operatorLines()).noneMatch(line -> line.contains(NONE_OF_THEM_WAS_USABLE)));
        claim(
                "and nothing sends the operator to fix a seed folder that was never the problem",
                () -> assertThat(operatorLines()).noneMatch(line -> line.contains(FIX_THE_SEED_FOLDER)));
        claim(
                "a line says that seed extraction failed, names the failure, and says to run the same"
                        + " command again once it is fixed",
                () -> assertThat(operatorLines()).anyMatch(this::namesTheFailureAndWhatToDo));
        claim(
                "no measurement run was minted: a run that has not seen the whole seed folder has nothing"
                        + " to stand for",
                () -> assertThat(runIdsFor("seed-measurement", root)).isEmpty());

        ConverterStopsAnsweringBeans.keepAnswering();
        logged.list.clear();

        cli.run("run", root.toString());

        RunId measurementRun = theOnlyMeasurementRunOver(root);
        claim(
                "with the converter back, running the same command again succeeds",
                () -> assertThat(cli.getExitCode()).isZero());
        claim(
                "and seed extraction is recorded as finished, with every seed in the folder measured",
                () -> {
                    assertThat(ledger.stepFinished(measurementRun, SeedExtractionJobConfiguration.STEP_NAME))
                            .isTrue();
                    assertThat(metricRowsAgainst(theSeedWalkOf(seeds), measurementRun))
                            .isEqualTo(SEEDS_IN_A_SHORT_FOLDER);
                });
    }

    @Test
    @Story("A seed extraction that failed says why, and says nothing about the seed folder")
    @DisplayName("When the converter stops answering after a whole batch of seeds was written, the step is not recorded as finished over the seeds it did not reach")
    @Link(name = "ADR-155", url = Adr.A_SEED_FILE_THAT_WILL_NOT_OPEN_IS_RECORDED_UNDER_A_REASON_OF_ITS_OWN, type = "adr")
    void aConverterThatStopsInALaterChunkLeavesTheStepUnfinishedAndEverySeedIsMeasuredNextTime(
            @TempDir Path root, @TempDir Path seeds) throws IOException {
        Files.writeString(root.resolve("corpus.txt"), "a corpus document, for #306's second test");
        writeSeeds(seeds, SEEDS_IN_A_LONGER_FOLDER, "second");
        profile(seeds);
        ConverterStopsAnsweringBeans.stopAnsweringAfter(seeds, CONVERTED_PAST_THE_FIRST_CHUNK);

        cli.run("run", root.toString());

        claim(
                "the converter really did stop answering part-way through the seed folder",
                () -> assertThat(ConverterStopsAnsweringBeans.refusedConversions()).isPositive());
        claim(
                "the invocation failed, because the step did",
                () -> assertThat(cli.getExitCode()).isNotZero());
        claim(
                "nothing sends the operator to fix the seed folder",
                () -> assertThat(operatorLines()).noneMatch(line -> line.contains(FIX_THE_SEED_FOLDER)));
        claim(
                "a line says that seed extraction failed, names the failure, and says to run the same"
                        + " command again once it is fixed",
                () -> assertThat(operatorLines()).anyMatch(this::namesTheFailureAndWhatToDo));
        claim(
                "seed extraction is recorded as finished under no run: the seeds after the crash were"
                        + " never read, and a finished step is one no later invocation does again",
                () -> assertThat(runIdsFor("seed-measurement", root))
                        .noneMatch(run -> ledger.stepFinished(run, SeedExtractionJobConfiguration.STEP_NAME)));
        claim(
                "and no measurement run was minted at all, because nothing in this invocation saw the whole"
                        + " seed folder",
                () -> assertThat(runIdsFor("seed-measurement", root)).isEmpty());

        ConverterStopsAnsweringBeans.keepAnswering();
        logged.list.clear();

        cli.run("run", root.toString());

        RunId measurementRun = theOnlyMeasurementRunOver(root);
        claim(
                "with the converter back, running the same command again succeeds",
                () -> assertThat(cli.getExitCode()).isZero());
        claim(
                "and every seed in the folder is measured under the run, the ones the crash cut off"
                        + " included, with seed extraction recorded as finished",
                () -> {
                    assertThat(ledger.stepFinished(measurementRun, SeedExtractionJobConfiguration.STEP_NAME))
                            .isTrue();
                    assertThat(metricRowsAgainst(theSeedWalkOf(seeds), measurementRun))
                            .isEqualTo(SEEDS_IN_A_LONGER_FOLDER);
                });
    }

    /** Whether one line is the one the operator needs: the step failed, why, and what to do. */
    private boolean namesTheFailureAndWhatToDo(String line) {
        return line.contains("seed extraction")
                && line.contains("failed")
                && line.contains(ConverterStopsAnsweringBeans.CONNECTION_FAILURE)
                && line.contains(RUN_THE_SAME_COMMAND_AGAIN);
    }

    /** {@code count} seeds, each with bytes of its own so that none is a cache hit for another. */
    private static void writeSeeds(Path seeds, int count, String test) throws IOException {
        for (int seed = 1; seed <= count; seed++) {
            Files.writeString(
                    seeds.resolve("seed-%02d.txt".formatted(seed)),
                    "seed document " + seed + " of " + count + ", for #306's " + test + " test");
        }
    }

    /** Every operator-facing line this invocation wrote, in the order it wrote them. */
    private List<String> operatorLines() {
        return logged.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
    }

    /** The seed folder named and stage 4's gate open. */
    private void profile(Path seeds) {
        Profile profile = profileStore.load();
        profileStore.save(ProfileFixture.profile()
                .seedFolder(seeds.toString(), "set by this test")
                .degenerateOutputConfidenceFloor(profile.degenerateOutputConfidenceFloor())
                .boilerplateDocumentFrequencyFloor(BOILERPLATE_FLOOR, "set by this test, so stage 4's gate is open")
                .build());
    }

    /** The one measurement run over this test's own corpus walk, which the retry has to have minted. */
    private RunId theOnlyMeasurementRunOver(Path root) {
        List<RunId> runs = runIdsFor("seed-measurement", root);
        claim(
                "exactly one measurement run stands over this corpus once seed extraction has read the"
                        + " whole seed folder",
                () -> assertThat(runs).hasSize(1));
        return runs.getFirst();
    }

    /**
     * The runs one stage minted over this test's own corpus walk: scoped to the walk because the whole
     * class shares one database, and two of the claims above are about a run being absent.
     */
    private List<RunId> runIdsFor(String stage, Path root) {
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

    private WalkId theSeedWalkOf(Path seeds) {
        return ledger.finishedWalkFor(Walk.canonicalRoot(seeds))
                .orElseThrow(() -> new IllegalStateException("census recorded no finished walk of " + seeds));
    }

    private WalkId theCorpusWalkOf(Path root) {
        return ledger.finishedWalkFor(Walk.canonicalRoot(root))
                .orElseThrow(() -> new IllegalStateException("census recorded no finished walk of " + root));
    }

    /** Metrics rows against occurrences of one walk, keyed by one run. */
    private long metricRowsAgainst(WalkId walkId, RunId runId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM extraction_metric m JOIN file_occurrence o ON o.id = m.occurrence_id"
                        + " WHERE o.walk_id = ? AND m.run_id = ?",
                Long.class,
                walkId.value(),
                runId.value());
    }
}
