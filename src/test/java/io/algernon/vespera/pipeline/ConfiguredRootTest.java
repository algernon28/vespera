package io.algernon.vespera.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.corpus.AnomalyLog;
import io.algernon.vespera.corpus.Walk;
import io.algernon.vespera.corpus.WalkRecorder;
import io.algernon.vespera.ledger.Ledger;
import io.algernon.vespera.ledger.WalkId;
import io.algernon.vespera.profile.ProfileStore;
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
 * Where the root comes from when the command does not name one (ADR-066).
 *
 * <p>Three cases, and they are the whole rule: an invocation naming no root falls back to
 * {@code vespera.corpus-root}, an invocation naming one ignores that configuration entirely, and an
 * invocation with neither refuses rather than guessing. The last is why this class exists separately
 * from {@link CensusInvocationTest} — the property is bound per context, so a class that has one
 * configured cannot also be the class that has none.
 *
 * <p>The same slice, and the same {@code @Transactional(NOT_SUPPORTED)}, as
 * {@link CensusInvocationTest}: the census step runs outside a transaction so that a walk commits at
 * its own checkpoints, and a test-managed transaction around it would hold the only connection.
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@ImportAutoConfiguration(BatchAutoConfiguration.class)
@Import({
    CensusJobConfiguration.class,
    CensusTasklet.class,
    WalkRecorder.class,
    AnomalyLog.class,
    Ledger.class,
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
class ConfiguredRootTest {

    /** Where the database and the profile go — never the root, configured or otherwise (ADR-054). */
    @TempDir
    static Path workingDirectory;

    /** The root written into configuration, standing in for one long-lived archive. */
    @TempDir
    static Path configuredRoot;

    /** Files under the configured root, so a claim can say where its number comes from. */
    private static final int CONFIGURED_ROOT_FILES = 3;

    /** Files under the root an argument names, chosen to differ from the configured root's count. */
    private static final int NAMED_ROOT_FILES = 1;

    @DynamicPropertySource
    static void configuration(DynamicPropertyRegistry registry) throws IOException {
        for (int i = 0; i < CONFIGURED_ROOT_FILES; i++) {
            Files.writeString(configuredRoot.resolve("configured-" + i + ".txt"), "c");
        }
        registry.add("vespera.working-dir", workingDirectory::toString);
        registry.add(VesperaCommand.Run.ROOT_PROPERTY, configuredRoot::toString);
    }

    @Autowired
    private VesperaCli cli;

    @Autowired
    private Ledger ledger;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @Story("Where the root comes from")
    @DisplayName("An invocation naming no root walks the root that is configured")
    void walksTheConfiguredRootWhenTheCommandNamesNone() {
        cli.run("run");

        claim(
                "the command reported success, which is the only report an unattended invocation makes",
                () -> assertThat(cli.getExitCode()).isZero());
        claim(
                "the " + CONFIGURED_ROOT_FILES + " files under the configured root were recorded, so an"
                        + " archive that does not move need not be retyped",
                () -> assertThat(ledger.occurrenceCount(walkOver(configuredRoot)))
                        .isEqualTo(CONFIGURED_ROOT_FILES));
    }

    @Test
    @Story("Where the root comes from")
    @DisplayName("A root given as an argument is the one walked, and the configured root is left alone")
    void theArgumentWinsOverTheConfiguredRoot(@TempDir Path namedRoot) throws IOException {
        Files.writeString(namedRoot.resolve("named.txt"), "n");
        long walksOverTheConfiguredRootBefore = walkCountOver(configuredRoot);

        cli.run("run", namedRoot.toString());

        claim(
                "the " + NAMED_ROOT_FILES + " file under the root the argument named was recorded",
                () -> assertThat(ledger.occurrenceCount(walkOver(namedRoot))).isEqualTo(NAMED_ROOT_FILES));
        claim(
                "and configuration was not consulted at all, adding no walk of the configured root beside"
                        + " the one the operator asked for",
                () -> assertThat(walkCountOver(configuredRoot)).isEqualTo(walksOverTheConfiguredRootBefore));
    }

    @Test
    @Story("Where the root comes from")
    @DisplayName("A root named once does not become the root of every invocation after it")
    void anArgumentDoesNotOutliveTheInvocationThatNamedIt(@TempDir Path namedRoot) throws IOException {
        Files.writeString(namedRoot.resolve("named.txt"), "n");
        long walksOverTheConfiguredRootBefore = walkCountOver(configuredRoot);

        cli.run("run", namedRoot.toString());
        cli.run("run");

        claim(
                "the second invocation, naming no root of its own, went to configuration rather than"
                        + " repeating the root the first one named",
                () -> assertThat(walkCountOver(configuredRoot)).isEqualTo(walksOverTheConfiguredRootBefore + 1));
        claim(
                "and it reported success, so the fallback is a working path rather than a refusal",
                () -> assertThat(cli.getExitCode()).isZero());
    }

    @Test
    @Story("Where the root comes from")
    @DisplayName("An invocation with no root named anywhere refuses instead of guessing one")
    void refusesWhenNoRootIsNamedAnywhere() throws Exception {
        // Constructed rather than run through the context: this case is the absence of the property
        // this class configures, and a bound property cannot be absent. The blank string is what
        // Spring resolves the shipped, unset `corpus-root:` key to. The job operator and the job are
        // null because reaching either would itself be the defect -- the refusal comes first.
        VesperaCommand.Run run = new VesperaCommand.Run(null, null, workingDirectory, "");

        claim(
                "the invocation reports a usage error rather than censusing whatever tree it happened to"
                        + " start in, since a census of the wrong tree reports success",
                () -> assertThat(run.call()).isEqualTo(CommandLine.ExitCode.USAGE));
    }

    /**
     * The most recent walk over a root, found by the canonical spelling a walk records (ADR-051).
     *
     * <p>Most recent rather than the only one: these tests share a database, and a finished walk is
     * never resumed (ADR-055), so a root walked by two invocations has a row for each. Asking for the
     * last one keeps a claim about what an invocation recorded independent of what ran before it.
     */
    private WalkId walkOver(Path root) {
        return new WalkId(jdbcTemplate.queryForObject(
                "SELECT id FROM walk WHERE root = ? ORDER BY id DESC LIMIT 1",
                Long.class,
                Walk.canonicalRoot(root).toString()));
    }

    private long walkCountOver(Path root) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM walk WHERE root = ?", Long.class, Walk.canonicalRoot(root).toString());
    }
}
