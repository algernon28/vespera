package io.algernon.vespera.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.algernon.vespera.Adr;
import io.algernon.vespera.corpus.Walk;
import io.algernon.vespera.ledger.Ledger;
import io.algernon.vespera.ledger.RunId;
import io.algernon.vespera.profile.Profile;
import io.algernon.vespera.profile.ProfileFixture;
import io.algernon.vespera.profile.ProfileStore;
import io.algernon.vespera.profile.ProfileValue;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
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
 * Gate 3 open (ADR-084, #107): a corpus survivor is re-chunked from its cached Docling response once
 * an embedding model is named and stage 5's earlier gates are open, and each of its chunks is embedded
 * and stored as a vector under a scoring run, rather than left unchunked, unembedded, or re-converted
 * through Docling a second time.
 *
 * <p>A sibling of {@link SeedCorpusComparisonInvocationTest} for the same reason that class is a
 * sibling of {@link SeedExtractionInvocationTest}: this fixture additionally names an embedding
 * model, which every other invocation test in this package deliberately leaves unset.
 */
@CascadeSliceTest
@Import(SeedScriptedExtractionBeans.class)
@Epic("Relevance")
@Feature("Embedding")
@Issue("107")
@Link(name = "ADR-084", url = Adr.THE_EMBEDDING_MODEL_IS_A_PROFILE_GATE, type = "adr")
class EmbeddingScoringInvocationTest {

    /** A floor of 1.0 opens stage 4's gate the same way {@link SeedCorpusComparisonInvocationTest} does. */
    private static final String BOILERPLATE_FLOOR = "1.0";

    /** The model this fixture names, so gate 3 opens too. */
    private static final String MODEL_NAME = "qwen3-embedding:0.6b";

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
     * Disarms the seed fixture's move before as well as after each method: the hook is static, and an
     * {@code @AfterEach} alone leaks into the next class when a method dies before reaching it.
     */
    @BeforeEach
    void captureOperatorLines() {
        SeedScriptedExtractionBeans.stopMovingAway();
        logged = new ListAppender<>();
        logged.start();
        applicationLogger = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger("io.algernon.vespera");
        applicationLogger.addAppender(logged);
    }

    @AfterEach
    void releaseOperatorLines() {
        SeedScriptedExtractionBeans.stopMovingAway();
        applicationLogger.detachAppender(logged);
        logged.stop();
    }

    @Test
    @Story("A seed file that will not open stops stage 5 without failing the invocation")
    @DisplayName("With a model named, a seed file that will not open shuts scoring on its own reason, and scoring runs once it opens")
    @Issue("291")
    @Link(name = "ADR-155", url = Adr.A_SEED_FILE_THAT_WILL_NOT_OPEN_IS_RECORDED_UNDER_A_REASON_OF_ITS_OWN, type = "adr")
    void aSeedFileThatWillNotOpenShutsScoring(@TempDir Path root, @TempDir Path seeds, @TempDir Path elsewhere)
            throws IOException {
        Files.writeString(root.resolve("corpus.txt"), "a corpus document of its own, for ADR-155's scoring test");
        Files.writeString(seeds.resolve("seed.txt"), "a seed document that opens, for ADR-155's scoring test");
        Files.writeString(
                seeds.resolve(SeedScriptedExtractionBeans.MOVED_AWAY_WHEN_READ),
                "a seed document that is moved away when read, for ADR-155's scoring test");
        profile(seeds);
        SeedScriptedExtractionBeans.moveAwayWhenReadInto(elsewhere);

        cli.run("run", root.toString());

        claim(
                "the fixture really did take the file away when seed extraction read it",
                () -> assertThat(elsewhere.resolve(SeedScriptedExtractionBeans.MOVED_AWAY_WHEN_READ)).exists());
        claim(
                "the invocation reported success",
                () -> assertThat(cli.getExitCode()).isZero());
        claim(
                "every gate ahead of the new one was open: a model is named, the seed walk finished, and a"
                        + " seed produced text, so what shut scoring can only be the seed that would not open",
                () -> assertThat(operatorLines())
                        .noneMatch(line -> line.contains("stage 5's scoring step is gated: no embedding model")
                                || line.contains("stage 5's scoring step is gated: no seed")));
        claim(
                "embedding scoring says it is shut, on the reason that a seed file could not be opened",
                () -> assertThat(operatorLines())
                        .anyMatch(line -> line.contains(
                                "stage 5's scoring step is gated: a seed file could not be opened")));
        claim(
                "and so does relevance scoring, which would otherwise take its maximum over a seed set"
                        + " with a hole in it",
                () -> assertThat(operatorLines())
                        .anyMatch(line -> line.contains(
                                "stage 5's relevance-scoring step is gated: a seed file could not be opened")));
        claim(
                "no embedding-scoring run exists over this corpus, so nothing was scored and sealed"
                        + " against the seeds that happened to open. The relevance report asks the same"
                        + " seed-usability question before it reaches a scoring run (ADR-160), so no step of"
                        + " this invocation mints one, and the claim can be about the run row itself",
                () -> assertThat(runIdsOver(ScoringRun.STAGE, root)).isEmpty());

        Files.move(
                elsewhere.resolve(SeedScriptedExtractionBeans.MOVED_AWAY_WHEN_READ),
                seeds.resolve(SeedScriptedExtractionBeans.MOVED_AWAY_WHEN_READ),
                StandardCopyOption.ATOMIC_MOVE);
        logged.list.clear();

        cli.run("run", root.toString());

        claim(
                "once the file is back, the next invocation succeeds and scoring is no longer shut on it",
                () -> {
                    assertThat(cli.getExitCode()).isZero();
                    assertThat(operatorLines())
                            .noneMatch(line -> line.contains("a seed file could not be opened"));
                });
        claim(
                "and scoring ran, and is recorded as finished under its run over this corpus",
                () -> assertThat(scoringFinishedOver(root)).isTrue());
    }

    @Test
    @Story("A survivor is re-chunked once a model is named")
    @DisplayName("With a model named, a corpus survivor's chunks land in the chunk cache and each embeds")
    void chunksTheSurvivorFromTheExtractionCache(@TempDir Path root, @TempDir Path seeds) throws IOException {
        Files.writeString(root.resolve("corpus.txt"), "a corpus document");
        Files.writeString(seeds.resolve("seed.txt"), "a seed document");
        profile(seeds);

        cli.run("run", root.toString());

        claim(
                "the invocation reports success",
                () -> assertThat(cli.getExitCode()).isZero());
        claim(
                "and the surviving corpus document was chunked, its chunks landing in the chunk cache"
                        + " rather than nowhere",
                () -> assertThat(chunkCacheRowCount()).isPositive());
        claim(
                "and each of those chunks was embedded, its vector landing in the vector table rather"
                        + " than the re-chunk being the last thing gate 3 does",
                () -> assertThat(vectorRowCount()).isEqualTo(chunkCacheRowCount()));
        claim(
                "and a scoring run was minted for it -- a run row for a pass that scored a survivor"
                        + " would otherwise be missing from the ledger entirely",
                () -> assertThat(runIdsOver("embedding-scoring", root)).hasSize(1));
    }

    /** The seed folder named, stage 4's gate open, and gate 3 open too. */
    private void profile(Path seeds) {
        Profile profile = profileStore.load();
        profileStore.save(ProfileFixture.profile()
                .seedFolder(seeds.toString(), "set by this test")
                .degenerateOutputConfidenceFloor(profile.degenerateOutputConfidenceFloor())
                .boilerplateDocumentFrequencyFloor(BOILERPLATE_FLOOR, "set by this test, so stage 4's gate is open")
                .embeddingModel(MODEL_NAME, "set by this test, so gate 3 is open")
                .build());
    }

    /** Whether embedding scoring is recorded as finished under any scoring run over this test's corpus. */
    private boolean scoringFinishedOver(Path root) {
        return runIdsOver("embedding-scoring", root).stream()
                .anyMatch(runId -> ledger.stepFinished(new RunId(runId), "embedding-scoring"));
    }

    private long chunkCacheRowCount() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM chunk_cache", Long.class);
    }

    private long vectorRowCount() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM vector", Long.class);
    }

    /** Every operator-facing line this invocation wrote, in the order it wrote them. */
    private List<String> operatorLines() {
        return logged.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
    }

    /**
     * The runs one stage minted over this test's own corpus walk, scoped because the class shares one
     * database and another method's run would otherwise be counted.
     */
    private List<String> runIdsOver(String stage, Path root) {
        long walkId = ledger.finishedWalkFor(Walk.canonicalRoot(root))
                .orElseThrow(() -> new IllegalStateException("census recorded no finished walk of " + root))
                .value();
        return jdbcTemplate.queryForList(
                "SELECT id FROM run WHERE stage = ? AND walk_id = ?", String.class, stage, walkId);
    }
}
