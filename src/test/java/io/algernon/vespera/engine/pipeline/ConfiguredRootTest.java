package io.algernon.vespera.engine.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.engine.ledger.Ledger;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
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
@CascadeSliceTest
@Import(StubbedExtractionBeans.class)
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
    @DisplayName("An invocation with no root named anywhere refuses instead of guessing one")
    void refusesWhenNoRootIsNamedAnywhere() throws Exception {
        // Constructed rather than run through the context: this case is the absence of the property
        // this class configures, and a bound property cannot be absent. The blank string is what
        // Spring resolves the shipped, unset `corpus-root:` key to. The job operator and the job are
        // null because reaching either would itself be the defect -- the refusal comes first, and so
        // is the closing line, which a refused invocation never writes.
        VesperaCommand.Run run = new VesperaCommand.Run(null, null, null, workingDirectory, "");

        claim(
                "the invocation reports a usage error rather than censusing whatever tree it happened to"
                        + " start in, since a census of the wrong tree reports success",
                () -> assertThat(run.call()).isEqualTo(CommandLine.ExitCode.USAGE));
    }
}
