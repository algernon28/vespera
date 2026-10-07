package io.algernon.vespera.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.extraction.ExtractionBeans;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import picocli.CommandLine;

/**
 * The refusal, reached the way an operator reaches it: nothing named, nothing configured (ADR-066).
 *
 * <p>Why this is a class of its own, and not a fourth test in {@link ConfiguredRootTest}: the whole
 * point is that no corpus root is bound to the context, and a class that binds one for its other
 * tests cannot also be the class that binds none. {@code ConfiguredRootTest} covers the same refusal
 * by constructing the command by hand, which pins the branch but not the wiring — and the wiring is
 * where the refusal actually lives, because nothing gives this property a value by default. The
 * shipped {@code application.yaml} binds {@code vespera.corpus-root} empty, so the key is visible where
 * an operator looks for configuration, and an empty value refuses because the command checks for
 * blank. The {@code :} default in the command's own {@code @Value} is the second guard: without the
 * key and without that colon, Spring would hand the command the literal string
 * {@code ${vespera.corpus-root}}, which is not blank, so the run would walk a path named after a
 * placeholder and fail as though the disk were at fault.
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
 * <p>What that binding costs, said plainly: with the key present-but-empty, here and in the shipped
 * file, dropping the {@code :} default from the command's own {@code @Value} would resolve cleanly
 * rather than yielding the literal placeholder, so <b>no test guards that any more</b>. The trade is
 * deliberate — a hypothetical regression against a foot-gun that has already fired.
 */
@CascadeSliceTest
@Import(ExtractionBeans.class)
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

    /**
     * The sentence of the refusal, which only its exit code held until ADR-208. That record took the
     * root requirement away from {@code vespera label --auto} and kept it here, so this is the one
     * command left that says a root is never guessed.
     */
    @Test
    @Issue("451")
    @Story("Where the root comes from")
    @DisplayName("The refusal says what to supply, and that the folder to read is never guessed")
    @Link(name = "ADR-208", url = Adr.LABEL_AUTO_NEEDS_NO_CORPUS_ROOT, type = "adr")
    @ExtendWith(OutputCaptureExtension.class)
    void saysWhatToSupplyAndThatARootIsNeverGuessed(CapturedOutput output) {
        claim(
                "no corpus root is bound in this context, the precondition of the refusal",
                () -> assertThat(environment.getProperty("vespera.corpus-root")).isBlank());

        cli.run("run");

        claim(
                "the invocation reports a usage error",
                () -> assertThat(cli.getExitCode()).isEqualTo(CommandLine.ExitCode.USAGE));
        claim(
                "on standard error it names what was missing and the two ways to supply it, and ends by"
                        + " saying why it will not pick a folder itself",
                () -> assertThat(output.getErr())
                        .contains("vespera run named no root and vespera.corpus-root is not set")
                        .contains("give the root as the argument -- vespera run <root> -- or set"
                                + " vespera.corpus-root")
                        .contains("A root is never guessed, because a census of the wrong tree reports"
                                + " success."));
    }

    private long walkCount() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM walk", Long.class);
    }
}
