package io.algernon.vespera.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.corpus.AnomalyLog;
import io.algernon.vespera.corpus.ContentIdentity;
import io.algernon.vespera.corpus.WalkRecorder;
import io.algernon.vespera.extraction.ExtractionMetrics;
import io.algernon.vespera.extraction.LanguageDetection;
import io.algernon.vespera.ledger.ImplementationVersions;
import io.algernon.vespera.ledger.Ledger;
import io.algernon.vespera.ledger.WalkId;
import io.algernon.vespera.profile.ProfileStore;
import io.algernon.vespera.similarity.Shingler;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.batch.core.job.Job;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.batch.autoconfigure.BatchAutoConfiguration;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import picocli.CommandLine;

/**
 * One invocation, from the command line down to the rows (ADR-047, ADR-054).
 *
 * <p>What this covers is the wiring, and only the wiring: that {@code vespera run <root>} reaches the
 * job, that the job's one step reaches census, and that the root the operator typed arrives as the
 * root that gets walked. Everything census then does is pinned by {@link CensusTaskletTest}.
 *
 * <p>It is a slice rather than the whole application, because the whole application starts Chroma
 * and Ollama and this question does not involve either. The one non-obvious piece is
 * {@code @Transactional(NOT_SUPPORTED)}: the census step deliberately runs outside a transaction so
 * that a walk commits at its own checkpoints, and a test-managed transaction wrapped around it would
 * be suspended and then hold the only connection the test datasource has.
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@ImportAutoConfiguration(BatchAutoConfiguration.class)
@Import({
    CensusJobConfiguration.class,
    CensusTasklet.class,
    Stage1JobConfiguration.class,
    Stage1Tasklet.class,
    Stage2JobConfiguration.class,
    Stage2ItemProcessor.class,
    Stage2ItemWriter.class,
    Stage2Run.class,
    Stage2TimeoutStreak.class,
    Stage2CircuitBreaker.class,
    Stage2HealthCheckListener.class,
    Shingler.class,
    StubbedExtractionBeans.class,
    ExtractionMetrics.class,
    LanguageDetection.class,
    ContentIdentity.class,
    WalkRecorder.class,
    AnomalyLog.class,
    Ledger.class,
    ImplementationVersions.class,
    ProfileStore.class,
    VesperaCommand.class,
    VesperaCommand.Run.class,
    VesperaCommand.Publish.class,
    VesperaCli.class
})
@Epic("Census")
@Feature("Invocation")
@Issue("11")
@Link(name = "ADR-054", url = Adr.CORPUS_IS_ITS_ROOT_PATH, type = "adr")
class CensusInvocationTest {

    /** The working directory the profile is written to; the corpus root is the command's argument. */
    @TempDir
    static Path workingDirectory;

    /** Files written under the corpus root, so a claim can say where its number comes from. */
    private static final int CORPUS_FILES = 2;

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
    private Job vesperaJob;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @Story("What census does in one invocation")
    @DisplayName("vespera run <root> walks that root and leaves the profile beside the database")
    void runsCensusOverTheRootTheOperatorNamed(@TempDir Path root) throws IOException {
        Files.writeString(root.resolve("a.txt"), "a");
        Files.writeString(root.resolve("b.txt"), "b");

        cli.run("run", root.toString());

        claim(
                "the command reported success, which is the only report an unattended invocation makes",
                () -> assertThat(cli.getExitCode()).isZero());
        claim(
                "the " + CORPUS_FILES + " files under the root the operator named were recorded",
                () -> assertThat(ledger.occurrenceCount(theWalk())).isEqualTo(CORPUS_FILES));
        claim(
                "the walk finished, so what it recorded may be judged",
                () -> assertThat(ledger.walkFinished(theWalk())).isTrue());
        claim(
                "the profile was written to the working directory rather than into the corpus",
                () -> assertThat(profileStore.file().startsWith(workingDirectory)).isTrue());
        claim(
                "and it is there to be edited",
                () -> assertThat(Files.exists(profileStore.file())).isTrue());
    }

    @Test
    @Story("What census does in one invocation")
    @DisplayName("The job is one job named for the tool, with census as its first step")
    @Link(name = "ADR-047", url = Adr.THE_PIPELINE_NEVER_BLOCKS, type = "adr")
    void assemblesOneJobForTheWholePipeline() {
        claim(
                "there is one job, named vespera, for later slices to add their stages to rather than to"
                        + " stand beside",
                () -> assertThat(vesperaJob.getName()).isEqualTo("vespera"));
    }

    @Test
    @Story("The commands the tool offers")
    @DisplayName("Publishing reports that it is not built yet rather than appearing to have run")
    void publishSaysItIsNotBuiltYet() {
        cli.run("publish");

        claim(
                "publishing fails, because a command that quietly does nothing is worse than one that says"
                        + " it cannot",
                () -> assertThat(cli.getExitCode()).isEqualTo(CommandLine.ExitCode.SOFTWARE));
    }

    @Test
    @Story("The commands the tool offers")
    @DisplayName("Naming no command prints what the commands are, and does not report success")
    void namingNoCommandPrintsTheCommands() {
        cli.run();

        claim(
                "an invocation naming no command does not exit as though work was done",
                () -> assertThat(cli.getExitCode()).isEqualTo(CommandLine.ExitCode.USAGE));
    }

    @Test
    @Story("The commands the tool offers")
    @DisplayName("A database directory that disagrees with the one actually opened stops the run")
    void refusesADatabaseDirectoryThatWasNotTheOneOpened(@TempDir Path root, @TempDir Path elsewhere)
            throws IOException {
        Files.writeString(root.resolve("a.txt"), "a");
        // The context, and so the database, is shared with the other tests in this class, which do
        // record walks. What matters is that this invocation adds none.
        long walksBefore = walkCount();

        cli.run("run", root.toString(), "--db-dir=" + elsewhere);

        claim(
                "the run refuses rather than writing to one directory while the operator believes they"
                        + " named another",
                () -> assertThat(cli.getExitCode()).isEqualTo(CommandLine.ExitCode.SOFTWARE));
        claim(
                "and this invocation recorded no walk of its own, the refusal coming before any walking",
                () -> assertThat(walkCount()).isEqualTo(walksBefore));
    }

    private long walkCount() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM walk", Long.class);
    }

    private WalkId theWalk() {
        return new WalkId(jdbcTemplate.queryForObject("SELECT id FROM walk", Long.class));
    }
}
