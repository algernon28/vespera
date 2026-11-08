package io.algernon.vespera.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.algernon.vespera.Adr;
import io.algernon.vespera.corpus.Walk;
import io.algernon.vespera.ledger.Ledger;
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
        EveryGateOpen.throughStageFive(profileStore, seeds);

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
                                "stage 5's relevance-report step is gated: no seed document produced any text outside its page headers and footers.")));
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
        long walkId = ledger.walks().finishedWalkFor(Walk.canonicalRoot(root))
                .orElseThrow(() -> new IllegalStateException("census recorded no finished walk of " + root))
                .value();
        return jdbcTemplate.queryForList("SELECT stage FROM run WHERE walk_id = ?", String.class, walkId);
    }
}
