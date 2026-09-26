package io.algernon.vespera.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.corpus.Walk;
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
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
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
 * At every gate a stage stands behind, an invocation with that gate shut leaves no run of the stage
 * behind it, and keeps every run before it (ADR-080, as ADR-157 §4 amends what keeps it).
 *
 * <p><b>Why these are behaviour tests and not a look at the wiring.</b> Until ADR-157 the rule was
 * kept by where each mint sat: in a scoped bean's constructor, reached through a provider only once
 * the gate had been checked. ADR-157 moves the mint into an explicit call each stage makes after its
 * own gate. The rule is then a property of every call site rather than of one piece of wiring, so it
 * is pinned at every call site, as what an invocation leaves in the {@code run} table. Written before
 * that change, and green on the code it replaces, so the change has to keep every one of them green.
 *
 * <p>Two gates were already pinned this way and are not repeated here. {@code CensusInvocationTest}
 * pins stage 4 with nothing set, and {@code SeedExtractionInvocationTest} pins stage 5 with no seed
 * folder and with no usable seed, but in neither is any later gate opened, so neither shows that the
 * later stages stop <em>because of</em> the gate in front of them. Each case here opens every gate
 * except the one it is about, so the runs it does find prove the invocation got as far as that gate.
 *
 * <p><b>The usable-seed gate with a model named is pinned in its own class, and not repeated here.</b>
 * When this class was written, the relevance-report step broke the rule at that gate: it checked only
 * the model and the seed walk, and reaching the scoring run minted it and the seed measurement run
 * (#309). ADR-160 closed that. The step now asks every seed-usability question before it reaches the
 * scoring run. {@code RelevanceReportMintsNothingBehindTheUsableSeedGateTest} pins the gate in this
 * class's own shape: stage 4's floor, the seed folder and the model are all set, and the only runs
 * left are the ones in front of the gate. The same class also pins ADR-155's gate, a seed file that
 * will not open, the same way.
 *
 * <p>Every claim is scoped to this test's own corpus walk, because one working directory, and so one
 * database, serves the whole class ({@code @TempDir static}). The profile is written whole at the start
 * of every test for the same reason: one method's profile would otherwise be the next method's.
 */
@CascadeSliceTest
@Import(SeedScriptedExtractionBeans.class)
@Epic("Pipeline")
@Feature("Gates")
@Issue("303")
@Link(name = "ADR-080", url = Adr.THE_BOILERPLATE_FLOOR_IS_A_GATE, type = "adr")
@Link(name = "ADR-157", url = Adr.A_STAGE_ASKS_FOR_ITS_RUN_AFTER_ITS_OWN_GATE, type = "adr")
class NoRunBehindAShutGateTest {

    /** A floor of 1.0 opens stage 4's gate the way every other whole-job test opens it. */
    private static final String BOILERPLATE_FLOOR = "1.0";

    /** The embedding model this fixture names, so the embedding-model gate opens. */
    private static final String EMBEDDING_MODEL = "qwen3-embedding:0.6b";

    /** The stages that mint a run ahead of stage 4's gate: none of them stands behind a gate at all. */
    private static final List<String> BEFORE_STAGE_4 = List.of("byte-level-reduction", "extraction", "content-census");

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
    @Story("A gate that is shut leaves no record of work behind it")
    @DisplayName("With the repetition floor unset, nothing from redundancy on is recorded, though a seed folder and a model are named")
    void theBoilerplateGateStopsEverythingAfterIt(@TempDir Path root, @TempDir Path seeds) throws IOException {
        aCorpusDocument(root);
        aSeed(seeds);
        profileStore.save(ProfileFixture.profile()
                .seedFolder(seeds.toString(), "set by this test")
                .embeddingModel(EMBEDDING_MODEL, "set by this test, so only the repetition floor is missing")
                .build());

        cli.run("run", root.toString());

        claim(
                "the invocation reports success: a missing value is something to supply, not a fault",
                () -> assertThat(cli.getExitCode()).isZero());
        claim(
                "and the only work recorded over this corpus is the work in front of the repetition"
                        + " floor -- the byte-level reduction, extraction and the content census. Nothing"
                        + " from redundancy on is recorded, although a seed folder and a model are named,"
                        + " because every later stage reads what redundancy would have left",
                () -> assertThat(stagesRecordedOver(root)).containsExactlyElementsOf(BEFORE_STAGE_4));
    }

    @Test
    @Story("A gate that is shut leaves no record of work behind it")
    @DisplayName("With no seed folder named, nothing from the seed measurement on is recorded, though a model is named")
    void theSeedGateStopsEverythingAfterIt(@TempDir Path root) throws IOException {
        aCorpusDocument(root);
        profileStore.save(ProfileFixture.profile()
                .boilerplateDocumentFrequencyFloor(BOILERPLATE_FLOOR, "set by this test")
                .embeddingModel(EMBEDDING_MODEL, "set by this test, so only the seed folder is missing")
                .build());

        cli.run("run", root.toString());

        claim("the invocation reports success", () -> assertThat(cli.getExitCode()).isZero());
        claim(
                "and redundancy is recorded, so the invocation reached the seed gate, while nothing after"
                        + " it is: no seed measurement, no scoring and no arrangement, although a model is"
                        + " named",
                () -> assertThat(stagesRecordedOver(root)).containsExactlyElementsOf(
                        withAfterStage4("content-redundancy")));
    }

    @Test
    @Story("A gate that is shut leaves no record of work behind it")
    @DisplayName("With no embedding model named, the seeds are measured and nothing after them is recorded")
    void theEmbeddingModelGateStopsEverythingAfterIt(@TempDir Path root, @TempDir Path seeds) throws IOException {
        aCorpusDocument(root);
        aSeed(seeds);
        profileStore.save(ProfileFixture.profile()
                .seedFolder(seeds.toString(), "set by this test")
                .boilerplateDocumentFrequencyFloor(BOILERPLATE_FLOOR, "set by this test, so only the model is missing")
                .build());

        cli.run("run", root.toString());

        claim("the invocation reports success", () -> assertThat(cli.getExitCode()).isZero());
        claim(
                "and the seed measurement is recorded, so the invocation reached the model gate, while"
                        + " nothing after it is: no scoring, and so no arrangement either",
                () -> assertThat(stagesRecordedOver(root)).containsExactlyElementsOf(
                        withAfterStage4("content-redundancy", "seed-measurement")));
    }

    @Test
    @Story("A gate that is shut leaves no record of work behind it")
    @DisplayName("With nothing left to group, the scoring is recorded and no arrangement is")
    void anEmptyGroupingStopsTheArrangement(@TempDir Path root, @TempDir Path seeds) throws IOException {
        // The only document in the archive is one the converter refuses to open, so it is removed at
        // extraction and nothing survives to be scored, grouped or arranged.
        Files.writeString(root.resolve(SeedScriptedExtractionBeans.REFUSED_CONVERSION), "a document");
        aSeed(seeds);
        everyGateOpen(seeds);

        cli.run("run", root.toString());

        claim("the invocation reports success", () -> assertThat(cli.getExitCode()).isZero());
        claim(
                "and the scoring is recorded, so the invocation reached the arrangement step, while no"
                        + " arrangement is: there is no group to arrange, and a record of an arrangement of"
                        + " nothing would be one a person could approve",
                () -> assertThat(stagesRecordedOver(root)).containsExactlyElementsOf(
                        withAfterStage4("content-redundancy", "seed-measurement", "embedding-scoring")));
    }

    @Test
    @Story("A gate that is shut leaves no record of work behind it")
    @DisplayName("With every gate open but nothing approved, everything up to the arrangement is recorded and no writing is")
    void anUnapprovedArrangementStopsTheWriting(@TempDir Path root, @TempDir Path seeds) throws IOException {
        aCorpusDocument(root);
        aSeed(seeds);
        everyGateOpen(seeds);

        cli.run("run", root.toString());

        claim("the invocation reports success", () -> assertThat(cli.getExitCode()).isZero());
        claim(
                "and every stage up to the arrangement is recorded, each once, while the writing at the"
                        + " end is not: nothing is written over the archive until a person approves the"
                        + " arrangement they read",
                () -> assertThat(stagesRecordedOver(root)).containsExactlyElementsOf(
                        withAfterStage4("content-redundancy", "seed-measurement", "embedding-scoring", "arrangement")));
    }

    private static void aCorpusDocument(Path root) throws IOException {
        Files.writeString(root.resolve("corpus.txt"), "a corpus document");
    }

    private static void aSeed(Path seeds) throws IOException {
        Files.writeString(seeds.resolve("seed.txt"), "a seed document");
    }

    /** Every gate in front of the arrangement open, and nothing approved. */
    private void everyGateOpen(Path seeds) {
        profileStore.save(ProfileFixture.profile()
                .seedFolder(seeds.toString(), "set by this test")
                .boilerplateDocumentFrequencyFloor(BOILERPLATE_FLOOR, "set by this test")
                .embeddingModel(EMBEDDING_MODEL, "set by this test")
                .build());
    }

    /** The stages in front of stage 4's gate, then {@code later} in order. */
    private static List<String> withAfterStage4(String... later) {
        return Stream.concat(BEFORE_STAGE_4.stream(), Arrays.stream(later)).toList();
    }

    /**
     * The stage of every run recorded over this test's own corpus, in the order they were recorded.
     * Scoped to the walk because the database is shared by every test in the class.
     */
    private List<String> stagesRecordedOver(Path root) {
        return jdbcTemplate.queryForList(
                "SELECT r.stage FROM run r JOIN walk w ON w.id = r.walk_id WHERE w.root = ? ORDER BY r.rowid",
                String.class,
                Walk.canonicalRoot(root).toString());
    }
}
