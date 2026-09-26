package io.algernon.vespera.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.corpus.Walk;
import io.algernon.vespera.ledger.RunId;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Which pieces of work the job records as finished, under which run, by which name (ADR-116, ADR-157).
 *
 * <p>A step's completion is recorded per step of a run, and a later invocation skips a step only by
 * finding that record. So the record has to name the right run and the step's persisted name, byte for
 * byte. A step that records under the wrong run, or under a name one character off, goes on working
 * correctly and simply does all of its work again on every invocation. No behaviour test notices
 * that, because the work is still right. This test is what notices.
 *
 * <p><b>Written before ADR-157's refactor, and green on the code it replaces.</b> That refactor moves
 * every tasklet's completion into one shell and merges the two listeners that record a chunk step's
 * completion into one. This is the whole of what those changes must leave as it was: which
 * {@code (run, step)} pairs stand after the job has run end to end. Before this test nothing asserted
 * that the signature step's completion is recorded at all.
 *
 * <p>Two invocations, the way an operator reaches the writing: the first stops at the arrangement,
 * the arrangement is approved, and the second writes. Every claim reads the rows over this test's own
 * corpus walk.
 */
@CascadeSliceTest
@Import(SeedScriptedExtractionBeans.class)
@Epic("Pipeline")
@Feature("Work that is already done")
@Issue("303")
@Link(name = "ADR-116", url = Adr.A_RUNS_COMPLETION_IS_RECORDED_PER_STEP, type = "adr")
@Link(name = "ADR-157", url = Adr.A_STAGE_ASKS_FOR_ITS_RUN_AFTER_ITS_OWN_GATE, type = "adr")
class FinishedStepsInvocationTest {

    /** A floor of 1.0 opens stage 4's gate the way every other whole-job test opens it. */
    private static final String BOILERPLATE_FLOOR = "1.0";

    /** The embedding model this fixture names, so the embedding-model gate opens. */
    private static final String EMBEDDING_MODEL = "qwen3-embedding:0.6b";

    /** The writing model this fixture names, so the second invocation writes. */
    private static final String GENERATION_MODEL = "a-named-writing-model:8b";

    /**
     * Every piece of work the job records as finished, as {@code the run's stage / the step's name}.
     *
     * <p>Twelve of the fifteen steps. Census works under no run, so it has nothing to record under.
     * The relevance-floor and relevance-report steps record nothing at all (ADR-118): both read the
     * answers a person gave on the labelling page, which no run names, so both decide again on every
     * invocation and an answer given between two of them takes effect. Written out literally, since
     * each half is a persisted name.
     */
    private static final List<String> EVERY_FINISHED_STEP = List.of(
            "byte-level-reduction / byte-level-reduction",
            "extraction / extraction",
            "content-census / content-census",
            "content-redundancy / redundancy-signature",
            "content-redundancy / content-redundancy",
            "seed-measurement / seed-extraction",
            "seed-measurement / seed-corpus-comparison",
            "embedding-scoring / embedding-scoring",
            "embedding-scoring / relevance-scoring",
            "embedding-scoring / clustering",
            "arrangement / arrangement",
            "generation / generation");

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
     * Before each test as well as after it, because another class may have scripted the serving
     * runtime to refuse a model and died before its own clean-up, and class order is decided per
     * machine.
     */
    @BeforeEach
    @AfterEach
    void theRuntimeServesEveryModel() {
        EmbeddingScriptedBeans.servesEveryModel();
    }

    @Test
    @Story("Work that is done is recorded as done, under the work it belongs to")
    @DisplayName("Run end to end, every step that records its work records it once, under its own run and its own name")
    void everyStepRecordsItsWorkUnderItsOwnRunAndName(@TempDir Path root, @TempDir Path seeds) throws IOException {
        Files.writeString(root.resolve("corpus.txt"), "a corpus document");
        Files.writeString(seeds.resolve("seed.txt"), "a seed document");
        profileStore.save(ProfileFixture.profile()
                .seedFolder(seeds.toString(), "set by this test")
                .boilerplateDocumentFrequencyFloor(BOILERPLATE_FLOOR, "set by this test")
                .embeddingModel(EMBEDDING_MODEL, "set by this test")
                .build());
        cli.run("run", root.toString());
        profileStore.save(ProfileFixture.profileFrom(profileStore.load())
                .arrangementApproved(ArrangementGate.shortNameOf(theArrangementOver(root)), "set by this test")
                .generationModel(GENERATION_MODEL, "set by this test")
                .build());

        cli.run("run", root.toString());

        claim("the second invocation reports success", () -> assertThat(cli.getExitCode()).isZero());
        claim(
                "and the finished work over this corpus is exactly these " + EVERY_FINISHED_STEP.size()
                        + " pieces, each recorded once, each under the run of its own stage and under its"
                        + " own name: the two steps of redundancy under one run, the two of the seed"
                        + " measurement under another, and three of the scoring steps under a third. A"
                        + " name one character off, or a record under a neighbour's run, would make a"
                        + " later invocation do that work all over again",
                () -> assertThat(finishedStepsOver(root)).containsExactlyInAnyOrderElementsOf(EVERY_FINISHED_STEP));
    }

    private RunId theArrangementOver(Path root) {
        return new RunId(jdbcTemplate.queryForObject(
                "SELECT r.id FROM run r JOIN walk w ON w.id = r.walk_id WHERE r.stage = ? AND w.root = ?",
                String.class,
                "arrangement",
                Walk.canonicalRoot(root).toString()));
    }

    /** Every {@code finished_step} row over this corpus, as {@code the run's stage / the step's name}. */
    private List<String> finishedStepsOver(Path root) {
        return jdbcTemplate.queryForList(
                "SELECT r.stage || ' / ' || f.step FROM finished_step f"
                        + " JOIN run r ON r.id = f.run_id JOIN walk w ON w.id = r.walk_id WHERE w.root = ?",
                String.class,
                Walk.canonicalRoot(root).toString());
    }
}
