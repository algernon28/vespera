package io.algernon.vespera.engine.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;

/**
 * The line {@link WorkingDirectoryOption#disagreement} gives every command whose {@code --db-dir} is not the working
 * directory in use (ADR-333 §8, §15): {@code <command> was given --db-dir <typed> but the working directory in use is
 * <actual>;} and the rest as it stood, from {@code --db-dir has to be given as --db-dir=<path>} on. It said "the
 * database and profile were opened in" the working directory, which is false of {@code vespera serve}, which opens
 * neither, so it names neither, for every command. No test pinned the line before ADR-333.
 *
 * <p>The option is parsed by picocli into the mixin of a command named as each of the jar's is, under {@code vespera},
 * so that the command the line names is the one the operator typed, as {@code VesperaCommand}'s tree names it.
 */
@Epic("Operation")
@Feature("The working directory")
@Link(name = "ADR-333", url = Adr.A_PLUGIN_DRAWS_ONE_RENDERING_AND_DELIVERS_IT_TO_ONE_DESTINATION, type = "adr")
@Link(name = "ADR-054", url = Adr.CORPUS_IS_ITS_ROOT_PATH, type = "adr")
class WorkingDirectoryOptionTest {

    /** The line from {@code --db-dir has to be given} on, as it stood before ADR-333 and stands after it. */
    private static final String THE_REST_OF_THE_LINE = " --db-dir has to be given as --db-dir=<path>, because it is read"
            + " as the db-dir property vespera.working-dir is resolved from, before this command is parsed";

    @Test
    @Story("The working directory can be named on every command")
    @DisplayName("A --db-dir that is not the working directory in use is refused in a line naming the command, both folders and how to give the option, and neither a database nor a profile")
    void aDisagreementNamesTheWorkingDirectoryInUse(@TempDir Path base) {
        Path typed = base.resolve("typed-on-the-command-line");
        Path inUse = base.resolve("the-working-directory-in-use");

        String run = optionOf("run", "--db-dir=" + typed).disagreement(inUse);
        String serve = optionOf("serve", "--db-dir=" + typed).disagreement(inUse);

        claim("vespera run is refused in the whole line: the command, the folder typed, the working directory in use,"
                        + " and how --db-dir has to be given",
                () -> assertThat(run).isEqualTo("vespera run was given --db-dir " + typed
                        + " but the working directory in use is " + inUse + ";" + THE_REST_OF_THE_LINE));
        claim("vespera serve is refused in the same line, naming itself",
                () -> assertThat(serve).isEqualTo("vespera serve was given --db-dir " + typed
                        + " but the working directory in use is " + inUse + ";" + THE_REST_OF_THE_LINE));
        claim("and neither line says a database or a profile was opened, which vespera serve does not do",
                () -> assertThat(java.util.List.of(run, serve)).allSatisfy(line -> assertThat(line)
                        .doesNotContain("database")
                        .doesNotContain("profile")));
    }

    @Test
    @Story("The working directory can be named on every command")
    @DisplayName("A --db-dir naming the working directory in use, however it is spelt, and none at all, give no line")
    void noLineWhereTheTwoAgreeOrNoneWasTyped(@TempDir Path base) {
        Path inUse = base.resolve("the-working-directory-in-use");
        Path spelledAnotherWay = base.resolve("elsewhere").resolve("..").resolve("the-working-directory-in-use");

        claim("a --db-dir naming the working directory in use gives no line",
                () -> assertThat(optionOf("run", "--db-dir=" + inUse).disagreement(inUse)).isNull());
        claim("nor does one naming it through a folder and back, which is the same folder once normalised",
                () -> assertThat(optionOf("serve", "--db-dir=" + spelledAnotherWay).disagreement(inUse)).isNull());
        claim("nor a command given no --db-dir at all",
                () -> assertThat(optionOf("serve").disagreement(inUse)).isNull());
    }

    /** The option {@code command}'s mixin holds once picocli has parsed {@code vespera <command> <arguments>}. */
    private static WorkingDirectoryOption optionOf(String command, String... arguments) {
        String[] line = new String[arguments.length + 1];
        line[0] = command;
        System.arraycopy(arguments, 0, line, 1, arguments.length);
        CommandLine.ParseResult parsed = new CommandLine(new Vespera()).parseArgs(line);
        return ((Mixed) parsed.subcommand().commandSpec().userObject()).option();
    }

    /** {@code vespera}, holding a command named as each of the two the line is held for. */
    @Command(name = "vespera", subcommands = {Run.class, Serve.class})
    static final class Vespera {}

    /** A command that mixes the option in, as each of the jar's does. */
    interface Mixed {
        WorkingDirectoryOption option();
    }

    @Command(name = "run")
    static final class Run implements Mixed {
        @Mixin
        private WorkingDirectoryOption option = new WorkingDirectoryOption();

        @Override
        public WorkingDirectoryOption option() {
            return option;
        }
    }

    @Command(name = "serve")
    static final class Serve implements Mixed {
        @Mixin
        private WorkingDirectoryOption option = new WorkingDirectoryOption();

        @Override
        public WorkingDirectoryOption option() {
            return option;
        }
    }
}
