package io.algernon.vespera.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.algernon.vespera.Adr;
import io.algernon.vespera.corpus.Walk;
import io.algernon.vespera.ledger.Ledger;
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
 * ADR-160: the relevance-report step asks both facts {@link UsableSeedGate} carries before it reaches
 * the scoring run, so with a model named it mints nothing behind ADR-083's gate or ADR-155's (#309).
 *
 * <p>{@link SeedExtractionInvocationTest#mintsNoRunWhenNoSeedIsUsable} pins the no-usable-seed state
 * with no embedding model named. There the relevance-report step shuts on the model gate before it can
 * reach anything, so nothing showed that, with a model named, the step went on to resolve the scoring
 * run — and through it the seed measurement run — and minted both behind a gate every other stage-5
 * step reports as shut (ADR-080). {@code EmbeddingScoringInvocationTest} names a model for ADR-155's
 * case, but checks that embedding scoring is not recorded finished, not that no scoring run exists.
 */
@CascadeSliceTest
@Import(SeedScriptedExtractionBeans.class)
@Epic("Relevance")
@Feature("Seed set")
@Issue("309")
@Link(name = "ADR-160", url = Adr.THE_RELEVANCE_REPORT_ASKS_BOTH_SEED_USABILITY_QUESTIONS, type = "adr")
@Link(name = "ADR-083", url = Adr.THE_SEED_SET_IS_EXTRACTED_BY_STAGE_5, type = "adr")
@Link(name = "ADR-080", url = Adr.THE_BOILERPLATE_FLOOR_IS_A_GATE, type = "adr")
class RelevanceReportMintsNothingBehindTheUsableSeedGateTest {

    /** The logger every operator-facing line in this application is written through. */
    private static final String APPLICATION_LOGGER = "io.algernon.vespera";

    /** A floor of 1.0 opens stage 4's gate, as in {@link SeedExtractionInvocationTest}. */
    private static final String BOILERPLATE_FLOOR = "1.0";

    /** The model this fixture names, so the model gate is open and cannot be what shuts the step. */
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
    private Ledger ledger;

    @Autowired
    private ProfileStore profileStore;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private ListAppender<ILoggingEvent> logged;
    private ch.qos.logback.classic.Logger applicationLogger;

    /**
     * Disarms the seed fixture's move before as well as after: the hook is static and shared by every
     * class importing the fixture, and class order differs from one machine to the next.
     */
    @BeforeEach
    void captureOperatorLines() {
        SeedScriptedExtractionBeans.stopMovingAway();
        logged = new ListAppender<>();
        logged.start();
        applicationLogger = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(APPLICATION_LOGGER);
        applicationLogger.addAppender(logged);
    }

    @AfterEach
    void releaseOperatorLines() {
        SeedScriptedExtractionBeans.stopMovingAway();
        applicationLogger.detachAppender(logged);
        logged.stop();
    }

    @Test
    @Story("A gate ends the invocation rather than failing it")
    @DisplayName("With a model named and no seed producing text, stage 5 mints no run and the relevance report names none")
    void mintsNoRunWhenNoSeedIsUsableAndAModelIsNamed(@TempDir Path root, @TempDir Path seeds) throws IOException {
        Files.writeString(root.resolve("corpus.txt"), "a corpus document, for #309");
        Files.writeString(seeds.resolve(SeedScriptedExtractionBeans.EMPTY_SEED), "not a document");
        theFloorTheSeedFolderAndTheModel(seeds);

        cli.run("run", root.toString());

        claim(
                "the invocation reports success: ADR-083's gate ends stage 5, not the invocation",
                () -> assertThat(cli.getExitCode()).isZero());
        claim(
                "the runs over this corpus are exactly the four stages ahead of stage 5. No seed produced"
                        + " text, so there is no seed set to measure or score against, and a seed-measurement"
                        + " or embedding-scoring row here would read afterwards as a pass that found nothing"
                        + " (ADR-080, ADR-083)",
                () -> assertThat(stagesOfRunsOver(root))
                        .containsExactlyInAnyOrder(
                                "byte-level-reduction", "extraction", "content-census", "content-redundancy"));
        claim(
                "no line says the relevance-report step was gated for want of scores under a scoring run:"
                        + " that sentence names a run the gate should have left unminted",
                () -> assertThat(operatorLines())
                        .noneMatch(line -> line.contains("relevance-report step is gated")
                                && line.contains("no survivor carries a relevance score under")));
        claim(
                "the relevance-report step says it was gated because no seed produced text, in the sentence"
                        + " its sibling stage-5 steps already share",
                () -> assertThat(operatorLines())
                        .anyMatch(line -> line.equals(
                                "stage 5's relevance-report step is gated: no seed document produced any text.")));
    }

    /**
     * ADR-155's gate is the second fact {@link UsableSeedGate} carries, and the same route opened through
     * it: seed extraction mints the measurement run, because one seed produced text, but records no
     * completion, and every later step is shut. The relevance-report step asked neither fact, so it went
     * on to mint a scoring run over a seed set with a hole in it.
     */
    @Test
    @Story("A seed file that will not open stops stage 5 without failing the invocation")
    @DisplayName("With a model named and a seed file that will not open, no scoring run is minted and the relevance report says why")
    @Link(name = "ADR-155", url = Adr.A_SEED_FILE_THAT_WILL_NOT_OPEN_IS_RECORDED_UNDER_A_REASON_OF_ITS_OWN, type = "adr")
    void mintsNoScoringRunWhenASeedFileWillNotOpenAndAModelIsNamed(
            @TempDir Path root, @TempDir Path seeds, @TempDir Path elsewhere) throws IOException {
        Files.writeString(root.resolve("corpus.txt"), "a corpus document, for #309's second route");
        Files.writeString(seeds.resolve("seed.txt"), "a seed document that opens, for #309's second route");
        Files.writeString(
                seeds.resolve(SeedScriptedExtractionBeans.MOVED_AWAY_WHEN_READ),
                "a seed document that is moved away when read, for #309's second route");
        theFloorTheSeedFolderAndTheModel(seeds);
        SeedScriptedExtractionBeans.moveAwayWhenReadInto(elsewhere);

        cli.run("run", root.toString());

        claim(
                "the fixture really did take the file away when seed extraction read it, so what follows"
                        + " is about a seed that would not open and not about one that did",
                () -> assertThat(elsewhere.resolve(SeedScriptedExtractionBeans.MOVED_AWAY_WHEN_READ)).exists());
        claim(
                "the invocation reports success: a seed file that cannot be read today ends stage 5, not"
                        + " the invocation",
                () -> assertThat(cli.getExitCode()).isZero());
        claim(
                "the runs over this corpus are the four stages ahead of stage 5 and the seed measurement"
                        + " run, which seed extraction minted because the other seed produced text. There is"
                        + " no scoring run: scoring against a seed set missing a file that was only locked"
                        + " would be recorded as a pass over the whole seed set",
                () -> assertThat(stagesOfRunsOver(root))
                        .containsExactlyInAnyOrder(
                                "byte-level-reduction",
                                "extraction",
                                "content-census",
                                "content-redundancy",
                                "seed-measurement"));
        claim(
                "the relevance-report step says it was gated because a seed file could not be opened, in"
                        + " the sentence its sibling stage-5 steps already share",
                () -> assertThat(operatorLines())
                        .anyMatch(line -> line.equals(
                                "stage 5's relevance-report step is gated: a seed file could not be opened.")));
    }

    /** Stage 4's gate open, the seed folder named, and a model named. */
    private void theFloorTheSeedFolderAndTheModel(Path seeds) {
        Profile profile = profileStore.load();
        profileStore.save(ProfileFixture.profile()
                .seedFolder(seeds.toString(), "set by this test")
                .degenerateOutputConfidenceFloor(profile.degenerateOutputConfidenceFloor())
                .boilerplateDocumentFrequencyFloor(BOILERPLATE_FLOOR, "set by this test, so stage 4's gate is open")
                .embeddingModel(MODEL_NAME, "set by this test, so the model gate is open")
                .build());
    }

    /** Every operator-facing line this invocation wrote, in the order it wrote them. */
    private List<String> operatorLines() {
        return logged.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
    }

    /**
     * The stage of every run minted over this test's own corpus walk, scoped to the walk because the
     * class shares one database with whatever else runs in its context.
     */
    private List<String> stagesOfRunsOver(Path root) {
        long walkId = ledger.finishedWalkFor(Walk.canonicalRoot(root))
                .orElseThrow(() -> new IllegalStateException("census recorded no finished walk of " + root))
                .value();
        return jdbcTemplate.queryForList("SELECT stage FROM run WHERE walk_id = ?", String.class, walkId);
    }
}
