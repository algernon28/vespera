package io.algernon.vespera.engine.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.objenesis.ObjenesisStd;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.IFactory;

/**
 * {@code --help} and {@code --version} are handed over before Spring (ADR-432 §1, §8), as {@code vespera serve} is:
 * {@code pipeline.HelpAndVersion} answers a command line whose one argument is {@code --help}, {@code -h},
 * {@code --version} or {@code -V}, and nothing else, printing what picocli prints for that option over
 * {@code VesperaCommand}'s model, built with no application, and exiting 0. That {@code EntryPoint} hands them over
 * before the machine lock and the command line is {@code EntryPointTest}'s.
 *
 * <p>The model the output is held against is built here as {@code ServeCommandTest} builds it, each command made without
 * its constructor, whose collaborators are the application's: no context, no configuration and no working directory.
 */
@Epic("Operation")
@Feature("Shipped configuration")
@Issue("905")
@Link(name = "ADR-432", url = Adr.NOTHING_USES_A_WORKING_DIRECTORY_NOTHING_NAMES, type = "adr")
class HelpAndVersionTest {

    /** The four arguments that ask for the usage or the version alone. */
    private static final List<String> ASKING = List.of("--help", "-h", "--version", "-V");

    private static final IFactory WITHOUT_ITS_COLLABORATORS = new IFactory() {
        private final ObjenesisStd objenesis = new ObjenesisStd();

        @Override
        public <K> K create(Class<K> type) throws Exception {
            return type.isAnnotationPresent(Command.class)
                    ? objenesis.newInstance(type)
                    : CommandLine.defaultFactory().create(type);
        }
    };

    @Test
    @Story("A command names its working directory")
    @DisplayName("Help and version alone are answered before Spring, and no other command line is")
    void helpAndVersionAloneAreAskedAndNothingElse() {
        List<Boolean> asked = ASKING.stream().map(HelpAndVersion::asks).toList();
        Map<String, Boolean> notAsked = Map.of(
                "--help run", HelpAndVersion.asks("--help", "run"),
                "run --help", HelpAndVersion.asks("run", "--help"),
                "run <root>", HelpAndVersion.asks("run", "a corpus root"),
                "serve", HelpAndVersion.asks("serve"),
                "no argument", HelpAndVersion.asks());

        claim("each of --help, -h, --version and -V given alone is answered before Spring",
                () -> assertThat(asked).containsExactly(true, true, true, true));
        claim("with another argument beside it, a command, serve, or no argument at all, it is not",
                () -> assertThat(notAsked).allSatisfy((arguments, answered) ->
                        assertThat(answered).as(arguments).isFalse()));
    }

    @Test
    @Story("A command names its working directory")
    @DisplayName("Help prints the commands' usage and version prints the version, as picocli prints them, and each exits 0")
    void eachPrintsWhatPicocliPrintsOverTheCommandsModelAndExitsZero() {
        CommandLine model = new CommandLine(VesperaCommand.class, WITHOUT_ITS_COLLABORATORS);
        ByteArrayOutputStream expectedVersion = new ByteArrayOutputStream();
        model.printVersionHelp(new PrintStream(expectedVersion, true, StandardCharsets.UTF_8));

        ByteArrayOutputStream help = new ByteArrayOutputStream();
        int helpExit = new HelpAndVersion(new PrintStream(help, true, StandardCharsets.UTF_8)).call("--help");
        ByteArrayOutputStream version = new ByteArrayOutputStream();
        int versionExit = new HelpAndVersion(new PrintStream(version, true, StandardCharsets.UTF_8)).call("-V");

        claim("--help prints, whole, the usage picocli gives of the vespera command and its subcommands",
                () -> assertThat(help.toString(StandardCharsets.UTF_8).lines().toList())
                        .containsExactlyElementsOf(model.getUsageMessage().lines().toList()));
        claim("-V prints, whole, the version help picocli gives of it",
                () -> assertThat(version.toString(StandardCharsets.UTF_8))
                        .isEqualTo(expectedVersion.toString(StandardCharsets.UTF_8)));
        claim("and each exits 0", () -> assertThat(List.of(helpExit, versionExit))
                .containsExactly(CommandLine.ExitCode.OK, CommandLine.ExitCode.OK));
    }
}
