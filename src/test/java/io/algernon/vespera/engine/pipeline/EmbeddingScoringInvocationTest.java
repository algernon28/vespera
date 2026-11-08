package io.algernon.vespera.engine.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.algernon.vespera.Adr;
import io.algernon.vespera.engine.corpus.Walk;
import io.algernon.vespera.engine.ledger.Ledger;
import io.algernon.vespera.engine.ledger.RunId;
import io.algernon.vespera.engine.profile.ProfileStore;
import io.algernon.vespera.engine.profile.ProfileValue;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
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
 * and stored as a vector under a scoring run, rather than left unchunked or unembedded.
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

    /** What the corpus document of the chunking test holds, a text no other test of this class writes. */
    private static final String WHAT_THE_CORPUS_DOCUMENT_SAYS = "a corpus document";

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
        EveryGateOpen.throughStageFive(profileStore, seeds);
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
                () -> assertThat(runIdsOver("embedding-scoring", root)).isEmpty());

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
        Files.writeString(root.resolve("corpus.txt"), WHAT_THE_CORPUS_DOCUMENT_SAYS);
        String theSurvivorsHash = sha256Of(WHAT_THE_CORPUS_DOCUMENT_SAYS);
        Files.writeString(seeds.resolve("seed.txt"), "a seed document");
        EveryGateOpen.throughStageFive(profileStore, seeds);

        cli.run("run", root.toString());

        claim(
                "the invocation reports success",
                () -> assertThat(cli.getExitCode()).isZero());
        claim(
                "and the surviving corpus document was chunked, its chunks landing in the chunk cache under"
                        + " the SHA-256 of its own bytes, computed here with the JDK, rather than nowhere: the"
                        + " seed's chunks and another test's are under hashes of their own",
                () -> assertThat(chunkRowsOf(theSurvivorsHash)).isPositive());
        claim(
                "and each of those chunks was embedded, its vector landing in the vector table under the same"
                        + " hash, rather than the re-chunk being the last thing gate 3 does",
                () -> assertThat(vectorRowsOf(theSurvivorsHash)).isEqualTo(chunkRowsOf(theSurvivorsHash)));
        claim(
                "and a scoring run was minted for it -- a run row for a pass that scored a survivor"
                        + " would otherwise be missing from the ledger entirely",
                () -> assertThat(runIdsOver("embedding-scoring", root)).hasSize(1));
    }

    /** Whether embedding scoring is recorded as finished under any scoring run over this test's corpus. */
    private boolean scoringFinishedOver(Path root) {
        return runIdsOver("embedding-scoring", root).stream()
                .anyMatch(runId -> ledger.runs().stepFinished(new RunId(runId), "embedding-scoring"));
    }

    /** How many chunks the chunk cache holds of the document whose bytes hash to {@code contentHash}. */
    private long chunkRowsOf(String contentHash) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM chunk_cache WHERE content_hash = ?", Long.class, contentHash);
    }

    /** How many vectors the vector table holds of that document's chunks. */
    private long vectorRowsOf(String contentHash) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM vector WHERE content_hash = ?", Long.class, contentHash);
    }

    /** The SHA-256 of {@code text}'s UTF-8 bytes as lowercase hex, computed with the JDK. */
    private static String sha256Of(String text) {
        try {
            return HexFormat.of()
                    .formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required of every JVM", e);
        }
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
        long walkId = ledger.walks().finishedWalkFor(Walk.canonicalRoot(root))
                .orElseThrow(() -> new IllegalStateException("census recorded no finished walk of " + root))
                .value();
        return jdbcTemplate.queryForList(
                "SELECT id FROM run WHERE stage = ? AND walk_id = ?", String.class, stage, walkId);
    }
}
