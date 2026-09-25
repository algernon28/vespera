package io.algernon.vespera.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.corpus.Walk;
import io.algernon.vespera.ledger.SuccessiveBuildsBeans;
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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * A stage's upstream run, once one walk holds two runs of the stage before it (ADR-154, amending
 * ADR-099; #290).
 *
 * <p>Since ADR-115 an invocation over an unchanged archive reuses the walk the last one recorded, and
 * run rows are never deleted. So anything that gives one stage a second run id — a new build touching
 * a module the stage names (ADR-058), or a new value in the configuration it consumed — leaves that
 * walk holding two runs of the stage. ADR-099 looked the upstream run up over the walk and stopped
 * the run on finding two, advising a fresh walk that ADR-115 makes impossible for an unchanged archive.
 * ADR-154 takes the upstream run a stage names from the invocation instead: the run of that stage this
 * invocation minted or continued, which every invocation holds, because every invocation starts at
 * census and passes through every step.
 *
 * <p>Every test here invokes twice or more over one database and one unchanged archive, changing one
 * thing between invocations, and claims that the later invocation completes and that the stage after
 * the one that moved names the run this invocation arrived at.
 *
 * <p><b>Each invocation reads the profile as a new process would.</b> In production every invocation
 * is its own process, and stage 2's confidence floor is read into a singleton when that process's
 * context starts. These tests share one context, so {@link #invoke} drops that singleton first, and
 * the next step to ask for it reads the profile afresh — which is what the next process would do.
 */
@CascadeSliceTest
@Import({SeedScriptedExtractionBeans.class, SuccessiveBuildsBeans.class})
@Epic("Pipeline")
@Feature("Repeated invocation")
@Issue("290")
@Link(name = "ADR-154", url = Adr.A_STAGE_READS_THE_UPSTREAM_RUN_THIS_INVOCATION_ARRIVED_AT, type = "adr")
@Link(name = "ADR-099", url = Adr.AN_UPSTREAM_IS_LOOKED_UP_AND_TWO_CANDIDATES_STOP_THE_RUN, type = "adr")
@Link(name = "ADR-115", url = Adr.A_REPEATED_OBSERVATION_IS_DISCARDED_AND_A_RUN_IS_CONTINUED, type = "adr")
class UpstreamRunOverAReusedWalkTest {

    /** A floor of 1.0 opens stage 4's gate, so every stage up to the arrangement runs. */
    private static final String BOILERPLATE_FLOOR = "1.0";

    /**
     * A second boilerplate floor, different from the first so it names a different piece of stage 4's
     * work. This fixture's documents share every fragment of text, so both floors strip all of them and
     * the work under each removes nothing: the claim is about which piece of work is read, not what it
     * removed.
     */
    private static final String A_RETUNED_BOILERPLATE_FLOOR = "0.9";

    /**
     * A confidence floor for stage 2's second tier. The fixture's converter reports no confidence at
     * all, so this removes nothing either; what it changes is the configuration stage 2 records.
     */
    private static final String A_CONFIDENCE_FLOOR = "0.5";

    /** The model the scripted embedder answers for, so the embedding gate opens. */
    private static final String MODEL_NAME = "qwen3-embedding:0.6b";

    /** The bean holding stage 2's confidence floor, read once when a process's context starts. */
    private static final String THE_CONFIDENCE_FLOOR_BEAN = "degenerateOutputConfidenceFloor";

    /** The key stage 2 records its confidence floor under, in the configuration its work is named by. */
    private static final String CONFIDENCE_FLOOR_KEY = "degenerateOutputConfidenceFloor";

    /** The key stage 4 records its boilerplate floor under, in the configuration its work is named by. */
    private static final String BOILERPLATE_FLOOR_KEY = "boilerplateDocumentFrequencyFloor";

    /** How many corpus documents the fixture writes. */
    private static final int CORPUS_DOCUMENTS = 2;

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
    private ConfigurableApplicationContext context;

    @AfterEach
    void theFirstBuildAndAnEmptyProfileAgain() {
        SuccessiveBuildsBeans.theFirstBuild();
        profileStore.save(ProfileFixture.profile().build());
        aNewProcessReadsTheProfile();
    }

    @ParameterizedTest(name = "a commit to the {0} code")
    @CsvSource({
        "corpus,     byte-level-reduction, extraction",
        "extraction, extraction,           content-census",
        "similarity, extraction,           content-census",
        "pipeline,   content-census,       content-redundancy",
    })
    @Story("A new build of the application does not strand an archive part-way through")
    @DisplayName("Invoking again under a new build of one part of the code completes")
    void aNewBuildOfOneModuleDoesNotStopTheNextInvocation(
            String module, String stageThatMoves, String stageAfterIt, @TempDir Path root, @TempDir Path seeds)
            throws IOException {
        aCorpus(root, seeds);
        everyGateOpen(seeds, BOILERPLATE_FLOOR);
        invoke(root);
        String firstRun = latestRunOf(root, stageThatMoves);

        SuccessiveBuildsBeans.aCommitTo(module);
        invoke(root);

        claim(
                "the second invocation reports success: the archive had not changed, so it was read"
                        + " from the same observation, and the step after the moved one is not stopped by"
                        + " the older build's work standing beside the new build's",
                () -> assertThat(cli.getExitCode()).isZero());
        claim(
                "the new build's code for this part is different code, so its work is a second piece of"
                        + " work beside the first rather than the first continued -- which is what the"
                        + " second invocation had to get past",
                () -> assertThat(runsOf(root, stageThatMoves)).hasSize(2).contains(firstRun));
        claim(
                "and the step after it reads the new build's work -- the piece this invocation did --"
                        + " not the older build's",
                () -> assertThat(upstreamOf(latestRunOf(root, stageAfterIt)))
                        .containsExactly(latestRunOf(root, stageThatMoves))
                        .doesNotContain(firstRun));
    }

    @Test
    @Story("A value read off the first report can be set without stranding the archive")
    @DisplayName("Setting the conversion-quality floor after the first invocation lets the next one complete")
    void settingTheConfidenceFloorAfterTheFirstInvocationDoesNotStopTheNext(
            @TempDir Path root, @TempDir Path seeds) throws IOException {
        aCorpus(root, seeds);
        everyGateOpen(seeds, BOILERPLATE_FLOOR);
        invoke(root);

        setTheConfidenceFloorTo(A_CONFIDENCE_FLOOR);
        invoke(root);

        claim(
                "the second invocation reports success, although the conversion-quality floor it was"
                        + " given is one the first invocation's report invited the operator to set",
                () -> assertThat(cli.getExitCode()).isZero());
        claim(
                "setting that floor made extraction a second piece of work over the same observation of"
                        + " the archive, one recording no floor and one recording " + A_CONFIDENCE_FLOOR,
                () -> assertThat(configurationsOf(root, "extraction"))
                        .hasSize(2)
                        .anySatisfy(recorded -> assertThat(recorded).contains(recordedAs(CONFIDENCE_FLOOR_KEY, "null")))
                        .anySatisfy(recorded -> assertThat(recorded)
                                .contains(recordedAs(CONFIDENCE_FLOOR_KEY, A_CONFIDENCE_FLOOR))));
        claim(
                "and the content census that followed read the extraction done under the floor just set",
                () -> assertThat(upstreamOf(latestRunOf(root, "content-census")))
                        .containsExactly(theRunOf(root, "extraction", recordedAs(CONFIDENCE_FLOOR_KEY, A_CONFIDENCE_FLOOR))));
    }

    @Test
    @Story("A value read off the first report can be set without stranding the archive")
    @DisplayName("Retuning the boilerplate floor after relevance was scored lets the next invocation complete")
    void retuningTheBoilerplateFloorAfterScoringDoesNotStopTheNext(@TempDir Path root, @TempDir Path seeds)
            throws IOException {
        aCorpus(root, seeds);
        everyGateOpen(seeds, BOILERPLATE_FLOOR);
        invoke(root);
        claim(
                "the first invocation got as far as measuring the seeds against the corpus, which is the"
                        + " step that reads the redundancy work -- so the retuning below comes after it",
                () -> assertThat(runsOf(root, "seed-measurement")).hasSize(1));

        everyGateOpen(seeds, A_RETUNED_BOILERPLATE_FLOOR);
        invoke(root);

        claim(
                "the second invocation reports success after the boilerplate floor was changed from "
                        + BOILERPLATE_FLOOR + " to " + A_RETUNED_BOILERPLATE_FLOOR,
                () -> assertThat(cli.getExitCode()).isZero());
        claim(
                "the redundancy work was done a second time under the new floor, beside the first",
                () -> assertThat(runsOf(root, "content-redundancy")).hasSize(2));
        claim(
                "and the seed measurement that followed read the redundancy work done under the new floor",
                () -> assertThat(upstreamOf(latestRunOf(root, "seed-measurement")))
                        .containsExactly(theRunOf(
                                root, "content-redundancy", recordedAs(BOILERPLATE_FLOOR_KEY, A_RETUNED_BOILERPLATE_FLOOR))));
    }

    @Test
    @Story("Putting a value back chooses the work done under it")
    @DisplayName("Putting the conversion-quality floor back picks up the earlier work and repeats none of it")
    void puttingAValueBackContinuesTheWorkDoneUnderItAndMintsNothing(@TempDir Path root, @TempDir Path seeds)
            throws IOException {
        aCorpus(root, seeds);
        everyGateOpen(seeds, BOILERPLATE_FLOOR);
        invoke(root);
        setTheConfidenceFloorTo(A_CONFIDENCE_FLOOR);
        invoke(root);
        int recordedAfterTheSecond = allRunsOf(root).size();

        setTheConfidenceFloorTo(null);
        invoke(root);

        claim(
                "the third invocation reports success with the floor unset again, as it was for the first",
                () -> assertThat(cli.getExitCode()).isZero());
        claim(
                "and it recorded no new piece of work at all: every step's inputs were exactly the first"
                        + " invocation's again, so it arrived at the first invocation's own work, found it"
                        + " finished, and left it alone. Putting a value back is how an operator chooses"
                        + " the work done under it",
                () -> assertThat(allRunsOf(root)).hasSize(recordedAfterTheSecond));
    }

    /**
     * One invocation, as a new process would make it: stage 2's confidence floor is read from the
     * profile afresh rather than carried over from the invocation before.
     */
    private void invoke(Path root) {
        aNewProcessReadsTheProfile();
        cli.run("run", root.toString());
    }

    /**
     * Drops the singleton that holds stage 2's confidence floor, so the next step asking for it builds
     * it again from the profile as it stands. Every bean that reads it is step-scoped, so nothing holds
     * the old one past the step that used it.
     */
    private void aNewProcessReadsTheProfile() {
        ((DefaultListableBeanFactory) context.getBeanFactory()).destroySingleton(THE_CONFIDENCE_FLOOR_BEAN);
    }

    private void aCorpus(Path root, Path seeds) throws IOException {
        for (int i = 0; i < CORPUS_DOCUMENTS; i++) {
            Files.writeString(root.resolve("corpus-" + i + ".txt"), "a corpus document " + i);
        }
        Files.writeString(seeds.resolve("seed.txt"), "a seed document");
    }

    /** The seed folder named, stage 4's gate open at {@code boilerplateFloor}, and the embedding gate open. */
    private void everyGateOpen(Path seeds, String boilerplateFloor) {
        profileStore.save(ProfileFixture.profileFrom(profileStore.load())
                .seedFolder(seeds.toString(), "set by this test")
                .boilerplateDocumentFrequencyFloor(boilerplateFloor, "set by this test, so stage 4's gate is open")
                .embeddingModel(MODEL_NAME, "set by this test, so the embedding gate is open")
                .build());
    }

    private void setTheConfidenceFloorTo(String floor) {
        profileStore.save(ProfileFixture.profileFrom(profileStore.load())
                .degenerateOutputConfidenceFloor(floor, floor == null ? null : "read off the first report by this test")
                .build());
    }

    /** Every run recorded over this corpus's walks, in the order written. */
    private List<String> allRunsOf(Path root) {
        return jdbcTemplate.queryForList(
                "SELECT run.id FROM run JOIN walk w ON w.id = run.walk_id WHERE w.root = ? ORDER BY run.rowid",
                String.class,
                walkRoot(root));
    }

    /** Every run of {@code stage} recorded over this corpus, in the order written. */
    private List<String> runsOf(Path root, String stage) {
        return jdbcTemplate.queryForList(
                "SELECT run.id FROM run JOIN walk w ON w.id = run.walk_id"
                        + " WHERE run.stage = ? AND w.root = ? ORDER BY run.rowid",
                String.class,
                stage,
                walkRoot(root));
    }

    /** The configuration each run of {@code stage} over this corpus recorded, in the order written. */
    private List<String> configurationsOf(Path root, String stage) {
        return jdbcTemplate.queryForList(
                "SELECT run.config_consumed FROM run JOIN walk w ON w.id = run.walk_id"
                        + " WHERE run.stage = ? AND w.root = ? ORDER BY run.rowid",
                String.class,
                stage,
                walkRoot(root));
    }

    /** The run of {@code stage} written last over this corpus. */
    private String latestRunOf(Path root, String stage) {
        return runsOf(root, stage).getLast();
    }

    /** How a value appears in the configuration a run records: its key, then the value as JSON writes it. */
    private static String recordedAs(String key, String value) {
        return "\"" + key + "\":" + value;
    }

    /** The one run of {@code stage} over this corpus whose recorded configuration carries {@code fragment}. */
    private String theRunOf(Path root, String stage, String fragment) {
        return jdbcTemplate.queryForObject(
                "SELECT run.id FROM run JOIN walk w ON w.id = run.walk_id"
                        + " WHERE run.stage = ? AND w.root = ? AND run.config_consumed LIKE ?",
                String.class,
                stage,
                walkRoot(root),
                "%" + fragment + "%");
    }

    /** The runs {@code run} names as its upstream. */
    private List<String> upstreamOf(String run) {
        return jdbcTemplate.queryForList(
                "SELECT upstream_run_id FROM run_upstream WHERE run_id = ?", String.class, run);
    }

    private String walkRoot(Path root) {
        return Walk.canonicalRoot(root).toString();
    }
}
