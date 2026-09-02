package io.algernon.vespera.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.corpus.AnomalyLog;
import io.algernon.vespera.corpus.WalkRecorder;
import io.algernon.vespera.ledger.Ledger;
import io.algernon.vespera.profile.ProfileStore;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
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
    private JdbcTemplate jdbcTemplate;

    @Test
    @Story("Where the root comes from")
    @DisplayName("An invocation with no root named and none configured refuses, and walks nothing")
    void refusesWhenNothingNamesARoot() {
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
