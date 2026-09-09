package io.algernon.vespera.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.corpus.AnomalyLog;
import io.algernon.vespera.corpus.ContentIdentity;
import io.algernon.vespera.corpus.DetectedFormats;
import io.algernon.vespera.corpus.WalkRecorder;
import io.algernon.vespera.embedding.ChunkEmbedderBeans;
import io.algernon.vespera.embedding.RelevanceScoringBeans;
import io.algernon.vespera.embedding.SeedCorpusComparison;
import io.algernon.vespera.embedding.UnusableSeeds;
import io.algernon.vespera.extraction.ConfidenceDistribution;
import io.algernon.vespera.extraction.ExtractionBeans;
import io.algernon.vespera.ledger.ImplementationVersions;
import io.algernon.vespera.ledger.Ledger;
import io.algernon.vespera.profile.ProfileStore;
import io.algernon.vespera.similarity.BoilerplateShingles;
import io.algernon.vespera.similarity.DocumentFrequency;
import io.algernon.vespera.similarity.RedundancyResolution;
import io.algernon.vespera.similarity.RedundancySignatures;
import io.algernon.vespera.similarity.Shingler;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.batch.autoconfigure.BatchAutoConfiguration;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import picocli.CommandLine;

/**
 * The refusal, reached the way an operator reaches it: nothing named, nothing configured (ADR-066).
 *
 * <p>Why this is a class of its own, and not a fourth test in {@link ConfiguredRootTest}: the whole
 * point is that no corpus root is bound to the context, and a class that binds one for its other
 * tests cannot also be the class that binds none. {@code ConfiguredRootTest} covers the same refusal
 * by constructing the command by hand, which pins the branch but not the wiring — and the wiring is
 * where the refusal actually lives, because nothing configures this property anywhere. The shipped
 * {@code application.yaml} carries no {@code vespera.corpus-root} key at all, so what makes an
 * unconfigured invocation refuse is the {@code :} default in the command's own {@code @Value}. Drop
 * that colon and Spring hands the command the literal string {@code ${vespera.corpus-root}}, which
 * is not blank, so the run walks a path named after a placeholder and fails as though the disk were
 * at fault. Only an invocation with nothing bound catches it.
 *
 * <p>{@code application-test.yaml} binds {@code vespera.corpus-root} empty so that this context
 * cannot inherit one from the shipped {@code application.yaml} — a profile-specific file layers over
 * that one rather than replacing it, so a root added there for a local archive would otherwise reach
 * here, and this test would walk it for half a minute before failing about an exit code. Empty and
 * absent are the same thing to the command, which checks for blank.
 *
 * <p>The precondition is <b>claimed rather than assumed</b> all the same, because the binding is
 * configuration and configuration drifts: the first claim below names the cause, so a root that does
 * reach here fails in seconds against the file that has to change instead of against the exit code.
 *
 * <p>What that binding costs, said plainly: with the key present-but-empty, dropping the {@code :}
 * default from the command's own {@code @Value} would resolve cleanly rather than yielding the
 * literal placeholder, so <b>no test guards that any more</b>. The trade is deliberate — a
 * hypothetical regression against a foot-gun that has already fired.
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@ImportAutoConfiguration(BatchAutoConfiguration.class)
@Import({
    CensusJobConfiguration.class,
    SeedExtractionJobConfiguration.class,
    SeedExtractionItemProcessor.class,
    SeedExtractionItemWriter.class,
    SeedCorpusComparisonJobConfiguration.class,
    SeedCorpusComparisonTasklet.class,
    EmbeddingModelJobConfiguration.class,
    EmbeddingScoringTasklet.class,
    RelevanceScoringJobConfiguration.class,
    RelevanceScoringTasklet.class,
    EmbeddingModelGate.class,
    ScoringRun.class,
    ChunkEmbedderBeans.class,
    RelevanceScoringBeans.class,
    EmbeddingScriptedBeans.class,
    SeedMeasurementRun.class,
    SeedGate.class,
    UsableSeedGate.class,
    CensusTasklet.class,
    ByteLevelReductionJobConfiguration.class,
    ByteLevelReductionTasklet.class,
    ExtractionJobConfiguration.class,
    ExtractionItemProcessor.class,
    ExtractionItemWriter.class,
    ExtractionRun.class,
    ExtractionTimeoutStreak.class,
    ExtractionCircuitBreaker.class,
    ExtractionHealthCheckListener.class,
    ContentCensusJobConfiguration.class,
    ContentCensusTasklet.class,
    ContentCensusRun.class,
    RedundancyJobConfiguration.class,
    RedundancyRun.class,
    RedundancyGate.class,
    RedundancyBoilerplate.class,
    RedundancySignatureItemWriter.class,
    RedundancyResolutionTasklet.class,
    RedundancySignatures.class,
    RedundancyResolution.class,
    BoilerplateShingles.class,
    DocumentFrequency.class,
    ConfidenceDistribution.class,
    Shingler.class,
    ExtractionBeans.class,
    ContentIdentity.class,
    DetectedFormats.class,
    WalkRecorder.class,
    AnomalyLog.class,
    Ledger.class,
    SeedCorpusComparison.class,
    UnusableSeeds.class,
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
@Link(name = "ADR-066", url = Adr.THE_COMMAND_LINE_NAMES_THE_ROOT, type = "adr")
class UnconfiguredRootTest {

    /**
     * The working directory, and the only property this context binds.
     *
     * <p>No corpus root is registered here, deliberately — that absence is the test.
     */
    @TempDir
    static Path workingDirectory;

    @DynamicPropertySource
    static void configuration(DynamicPropertyRegistry registry) {
        registry.add("vespera.working-dir", workingDirectory::toString);
    }

    @Autowired
    private VesperaCli cli;

    @Autowired
    private Environment environment;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @Story("Where the root comes from")
    @DisplayName("An invocation with no root named and none configured refuses, and walks nothing")
    void refusesWhenNothingNamesARoot() {
        claim(
                "no corpus root worth walking is bound in this context, which is the precondition the"
                        + " rest of this test rests on. It is claimed rather than assumed because it is"
                        + " configuration, not code: a real root reaching here would make every claim"
                        + " below pass or fail for a reason that has nothing to do with the wiring under"
                        + " test, and would walk that archive to do it",
                () -> assertThat(environment.getProperty("vespera.corpus-root")).isBlank());

        cli.run("run");

        claim(
                "the invocation reports a usage error, which is what says the operator has something to"
                        + " supply rather than something to debug",
                () -> assertThat(cli.getExitCode()).isEqualTo(CommandLine.ExitCode.USAGE));
        claim(
                "and it walked nothing at all, the refusal coming before any tree was chosen",
                () -> assertThat(walkCount()).isZero());
    }

    private long walkCount() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM walk", Long.class);
    }
}
