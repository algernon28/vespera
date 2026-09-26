package io.algernon.vespera.pipeline;

import java.nio.file.Path;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

/**
 * {@code --db-dir=<path>}, declared once and mixed into every command that opens the working
 * directory (ADR-054, #310).
 *
 * <p>ADR-054 makes the working directory overridable per invocation on the command line, and an
 * invocation is any command, not only {@code run}. The option was declared on {@code run} alone, so
 * {@code vespera label --db-dir=<path>} was refused as an unknown option, and an operator who had moved
 * the working directory with {@code --db-dir} could reach it from invocation 3 only by also setting
 * {@code vespera.working-dir} in configuration. Declaring it here, once, is what keeps
 * every command agreeing on its name, its form and its check, rather than relying on two copies that
 * would drift the way the property and the option already can.
 *
 * <p>{@code --db-dir} is read twice by two different mechanisms: as the {@code db-dir} property that
 * {@code vespera.working-dir} is resolved from before the datasource exists, and as this option. That
 * is not redundancy — without the option picocli would reject the argument outright — but it does
 * mean the two could drift, and the way they drift is silent: rename the property placeholder, or
 * write {@code --db-dir <path>} with a space, which Spring does not read as that path, and the flag
 * keeps parsing while the database quietly opens somewhere else. {@link #disagreement} is what makes that loud.
 *
 * <p>picocli parses into whatever instance the command's field already holds, and the commands are
 * singletons, so each one swaps in a fresh instance before the next model is built, for the reason
 * {@link VesperaCommand#commandLine()} gives. The field is never {@code null}: a command called
 * without picocli parsing it, as a test may, reads a fresh instance as no option given.
 */
final class WorkingDirectoryOption {

    /** The command this option is mixed into, so a refusal names the command the operator typed. */
    @Spec(Spec.Target.MIXEE)
    private CommandSpec command;

    @Option(
            names = "--db-dir",
            paramLabel = "<path>",
            description = "Where the database and profile.yaml live. Must be given as --db-dir=<path>,"
                    + " because it is read before this command is parsed.")
    private Path named;

    /**
     * What to tell the operator when the directory they typed is not the one the application actually
     * opened, or {@code null} when the two agree or none was typed.
     *
     * <p>Returned as a message rather than thrown: this is an operator typing a flag the wrong way
     * round, and the useful answer is the sentence that says so plus a non-zero exit code. Thrown, it
     * leaves the command through picocli's default handler, which prints a Java stack trace at
     * somebody who mistyped an argument.
     *
     * @param opened the working directory the context was built over
     */
    String disagreement(Path opened) {
        if (named == null) {
            return null;
        }
        Path typed = named.toAbsolutePath().normalize();
        Path actual = opened.toAbsolutePath().normalize();
        if (typed.equals(actual)) {
            return null;
        }
        return ("%s was given --db-dir %s but the database and profile were opened in %s;"
                        + " --db-dir has to be given as --db-dir=<path>, because it is read as the db-dir"
                        + " property %s is resolved from, before this command is parsed")
                .formatted(command.qualifiedName(), typed, actual, WorkingDirectoryPreparer.PROPERTY);
    }
}
