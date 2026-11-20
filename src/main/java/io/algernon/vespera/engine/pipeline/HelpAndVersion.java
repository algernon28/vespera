package io.algernon.vespera.engine.pipeline;

import java.io.PrintStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.objenesis.ObjenesisStd;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.IFactory;

/**
 * {@code --help} and {@code --version}, handed over before Spring as {@code vespera serve} is (ADR-436 §1, ADR-333 §8).
 * {@code EntryPoint} asks {@link #asks} of the command line's arguments, {@code --headless} dropped, and where it is
 * true {@link #call} prints what picocli prints for that option over {@link VesperaCommand}'s model and returns 0. The
 * model is built with no application: each command is made without its constructor, whose collaborators are the
 * application's, so no context is built, no configuration file is read, no working directory is named or opened, and
 * no database, log or lock is touched.
 */
public final class HelpAndVersion {

    /** The four arguments that ask for the usage or the version, alone. */
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

    private final PrintStream out;

    /** Printing on {@code out}, which {@code EntryPoint} gives standard output. */
    public HelpAndVersion(PrintStream out) {
        this.out = out;
    }

    /** True for one argument alone, among {@code --help}, {@code -h}, {@code --version} and {@code -V}. */
    public static boolean asks(String... arguments) {
        return arguments.length == 1 && ASKING.contains(arguments[0]);
    }

    /** Prints the usage or the version of {@code arguments}, as picocli does, and returns the exit code: 0. */
    public int call(String... arguments) {
        PrintWriter writer = new PrintWriter(out, true, StandardCharsets.UTF_8);
        CommandLine model = new CommandLine(VesperaCommand.class, WITHOUT_ITS_COLLABORATORS);
        model.setOut(writer);
        model.setErr(writer);
        int exit = model.execute(arguments);
        writer.flush();
        return exit;
    }
}
