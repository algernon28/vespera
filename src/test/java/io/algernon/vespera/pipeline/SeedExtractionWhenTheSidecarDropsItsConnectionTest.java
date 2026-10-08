package io.algernon.vespera.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.algernon.vespera.Adr;
import io.algernon.vespera.corpus.Walk;
import io.algernon.vespera.extraction.ExtractionBeans;
import io.algernon.vespera.ledger.Ledger;
import io.algernon.vespera.ledger.RunId;
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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * What seed extraction does when {@code docling-serve} drops the connection under a seed's call, and
 * when it answers a seed with an error status (#326, ADR-175 section 6). It waits and asks once more,
 * as stage 2 does. What differs is what a seed still failing earns: a seed is an input the operator
 * chose, so it stops the step, and the closing line names the seed.
 *
 * <p>The sidecar is a {@link LoopbackSidecar}, so both failures reach the step as the production
 * {@code DoclingClient} really raises them. The corpus document goes through it too, in stage 2, and is
 * converted: its text names no document number, so no script applies to it.
 */
@CascadeSliceTest
@Import(ExtractionBeans.class)
@Epic("Relevance")
@Feature("Seed set")
@Issue("326")
@Link(name = "ADR-175", url = Adr.A_FILE_THAT_FAILS_IS_MARKED_AND_SKIPPED, type = "adr")
@Link(name = "ADR-083", url = Adr.THE_SEED_SET_IS_EXTRACTED_BY_STAGE_5, type = "adr")
class SeedExtractionWhenTheSidecarDropsItsConnectionTest {

    /** A loopback port that was free when the class loaded; the test's sidecar listens on it. */
    private static final int SIDECAR_PORT = LoopbackSidecar.aFreePort();

    /** The logger every operator-facing line in this application is written through. */
    private static final String APPLICATION_LOGGER = "io.algernon.vespera";

    /** The seed folder: few enough to be one batch. */
    private static final int SEEDS = 3;

    /** The one seed a test scripts a failure for. */
    private static final int THE_FAILING_SEED = 2;

    /** How often a seed nothing went wrong with is posted. */
    private static final long ONCE = 1;

    /** How often a seed is posted when its first call was dropped: the call, and the one retry. */
    private static final long TWICE = 2;

    /** The status docling-serve answers a job that failed inside its worker with. */
    private static final int NOT_FOUND = 404;

    /**
     * A floor of 1.0, the least aggressive value that still opens stage 4's gate: nothing here is about
     * what stage 4 concludes, only about stage 5 being reached at all.
     */
    private static final String BOILERPLATE_FLOOR = "1.0";

    @TempDir
    static Path workingDirectory;

    @DynamicPropertySource
    static void sidecarAndWorkingDirectory(DynamicPropertyRegistry registry) {
        registry.add("vespera.working-dir", workingDirectory::toString);
        registry.add("vespera.docling.base-url", () -> "http://127.0.0.1:" + SIDECAR_PORT);
    }

    @Autowired
    private VesperaCli cli;

    @Autowired
    private Ledger ledger;

    @Autowired
    private ProfileStore profileStore;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Value("${vespera.docling.image}")
    private String configuredImage;

    private ListAppender<ILoggingEvent> logged;
    private ch.qos.logback.classic.Logger applicationLogger;
    private LoopbackSidecar sidecar;

    @BeforeEach
    void startTheSidecarAndCaptureOperatorLines() throws IOException {
        sidecar = LoopbackSidecar.startedOn(SIDECAR_PORT, configuredImage);
        logged = new ListAppender<>();
        logged.start();
        applicationLogger = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(APPLICATION_LOGGER);
        applicationLogger.addAppender(logged);
    }

    @AfterEach
    void stopTheSidecarAndReleaseOperatorLines() {
        applicationLogger.detachAppender(logged);
        logged.stop();
        sidecar.close();
    }

    @Test
    @Story("A converter that drops one connection under a seed and is back")
    @DisplayName("When the document converter drops the connection under one seed and answers its health check again, that seed is asked about once more and seed extraction completes")
    void aSeedWhoseCallDropsOnceIsRetriedAndSeedExtractionCompletes(@TempDir Path root, @TempDir Path seeds)
            throws IOException {
        Files.writeString(root.resolve("corpus.txt"), "a corpus file, for the seed that drops once");
        writeSeeds(seeds, "drops once");
        profile(seeds);
        sidecar.dropping(THE_FAILING_SEED, 1);

        cli.run("run", root.toString());

        claim(
                "the invocation succeeded: a connection dropped once under a seed did not fail it",
                () -> assertThat(cli.getExitCode()).isZero());
        claim(
                "seed " + THE_FAILING_SEED + " was posted " + TWICE + " times, the dropped call and one"
                        + " retry, and each of the other " + (SEEDS - 1) + " once",
                () -> {
                    for (int seed = 1; seed <= SEEDS; seed++) {
                        assertThat(sidecar.callsPerDocument()).containsEntry(seed, seed == THE_FAILING_SEED ? TWICE : ONCE);
                    }
                });
        claim(
                "and seed extraction is recorded as finished under the one measurement run over this corpus",
                () -> assertThat(measurementRunsOver(root))
                        .singleElement()
                        .satisfies(run -> assertThat(ledger.runs().stepFinished(run, "seed-extraction")).isTrue()));
    }

    @Test
    @Story("A seed the converter answers an error status for")
    @DisplayName("When the document converter answers a seed with an error status, seed extraction fails and the operator is told which seed and why")
    void aRejectedSeedStopsSeedExtractionAndIsNamed(@TempDir Path root, @TempDir Path seeds) throws IOException {
        Files.writeString(root.resolve("corpus.txt"), "a corpus file, for the seed that is rejected");
        writeSeeds(seeds, "is rejected");
        profile(seeds);
        sidecar.rejecting(THE_FAILING_SEED, NOT_FOUND);

        cli.run("run", root.toString());

        claim(
                "the invocation failed: a seed is an input the operator chose, so one that cannot be"
                        + " converted stops the step",
                () -> assertThat(cli.getExitCode()).isNotZero());
        claim(
                "a line says that seed extraction failed, names seed " + THE_FAILING_SEED + " by its file,"
                        + " and says the converter answered HTTP " + NOT_FOUND + " for it",
                () -> assertThat(operatorLines())
                        .anyMatch(line -> line.contains("seed extraction")
                                && line.contains("failed")
                                && line.contains(nameOf(THE_FAILING_SEED))
                                && line.contains("could not be converted")
                                && line.contains("HTTP " + NOT_FOUND)));
        claim(
                "seed " + THE_FAILING_SEED + " was posted once: an answer is not asked for again",
                () -> assertThat(sidecar.callsPerDocument()).containsEntry(THE_FAILING_SEED, ONCE));
        claim(
                "and no measurement run was minted, because nothing saw the whole seed folder",
                () -> assertThat(measurementRunsOver(root)).isEmpty());
    }

    /** {@link #SEEDS} seeds, each with text of its own so that none is a cache hit for another. */
    private static void writeSeeds(Path seeds, String test) throws IOException {
        for (int seed = 1; seed <= SEEDS; seed++) {
            Files.writeString(
                    seeds.resolve(nameOf(seed)),
                    "seed document " + seed + " of " + SEEDS + ", for the seed that " + test);
        }
    }

    private static String nameOf(int seed) {
        return "seed-%02d.txt".formatted(seed);
    }

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

    /** The measurement runs over this test's own corpus walk, since the class shares one database. */
    private List<RunId> measurementRunsOver(Path root) {
        return jdbcTemplate
                .queryForList(
                        "SELECT r.id FROM run r JOIN walk w ON w.id = r.walk_id WHERE r.stage = 'seed-measurement'"
                                + " AND w.root = ?",
                        String.class,
                        Walk.canonicalRoot(root).toString())
                .stream()
                .map(RunId::new)
                .toList();
    }
}
