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
     * A confidence floor for stage 2's second tier. The fixture's converter reports no confidence at
     * all, so this removes nothing either; what it changes is the configuration stage 2 records.
     */
    private static final String A_CONFIDENCE_FLOOR = "0.5";

    /** The bean holding stage 2's confidence floor, read once when a process's context starts. */
    private static final String THE_CONFIDENCE_FLOOR_BEAN = "degenerateOutputConfidenceFloor";

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
                .embeddingModel(EveryGateOpen.EMBEDDING_MODEL, "set by this test, so the embedding gate is open")
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
