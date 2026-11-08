package io.algernon.vespera.engine.pipeline;

import io.algernon.vespera.publish.website.WebsiteServer;
import java.io.IOException;
import java.io.PrintStream;
import java.net.BindException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.function.Function;
import picocli.CommandLine;
import picocli.CommandLine.Command;

/**
 * {@code vespera serve}, started with no Spring context (ADR-333 §8). {@code VesperaApplication.main} reads its first
 * argument before {@code SpringApplication} is built: where it is {@value #NAME}, {@link #handedOn} gives the arguments
 * after it and {@code main} exits with the code {@link #call} returns. So none of the four listeners {@code main} adds
 * runs, no datasource is made, no SQL is run, no pool is opened and no log file is set up: it writes to the console
 * alone, takes neither the working-directory lock nor the corpus-root lock, reads no profile and starts no sidecar, so a
 * publish can refresh the website while it serves. It builds the {@code website} module's server, so the root package
 * names no class of that module.
 *
 * <p><b>What it reads, itself.</b> The working directory resolves as the shipped configuration line for it does,
 * {@code ${db-dir:${user.dir}/.vespera}} (ADR-054): {@value #WORKING_DIR_PROPERTY} where it is set, else {@code db-dir},
 * which {@code --db-dir=<path>} sets, else {@code .vespera} under {@code user.dir}. Each value is read from the command's
 * arguments, then the JVM's system properties, then the environment, which is Spring's order for those three sources;
 * {@value #PORT_PROPERTY} is read from the same three. No file of configuration is read, so a value set only in one does
 * not reach it. It reads no OCR preset, so a placeholder nothing resolves does not stop it.
 *
 * <p>It refuses in one line, exit 1, creating nothing: a {@code --db-dir} that disagrees with a set working directory (by
 * {@link WorkingDirectoryOption#disagreement}, the line every command gives), a working directory that is not there, a
 * working directory whose website folder holds no {@code vespera-website.json}, and a port in use. A value for the port
 * that is no port is refused too, in a line of the same shape.
 */
public final class ServeCommand {

    /** The first argument of the jar that starts it. */
    public static final String NAME = "serve";

    /** The port it listens at is named by this property, from the arguments, the system properties or the environment. */
    public static final String PORT_PROPERTY = "vespera.serve.port";

    /** The port where nothing names one (ADR-333 §8): a port the system picks would change the address every reader holds. */
    public static final int DEFAULT_PORT = 24332;

    private static final String WORKING_DIR_PROPERTY = WorkingDirectoryPreparer.PROPERTY;
    private static final String DB_DIR_PROPERTY = "db-dir";
    private static final String DEFAULT_FOLDER = ".vespera";
    private static final String WEBSITE_FOLDER = "website";
    private static final String WEBSITE_LIST = "vespera-website.json";
    private static final int LOWEST_PORT = 1;
    private static final int HIGHEST_PORT = 65535;

    private final Properties systemProperties;
    private final Map<String, String> environment;
    private final PrintStream out;
    private final PrintStream err;

    /** As the process has it: its system properties, its environment, and the console. */
    public ServeCommand() {
        this(System.getProperties(), System.getenv(), System.out, System.err);
    }

    ServeCommand(Properties systemProperties, Map<String, String> environment, PrintStream out, PrintStream err) {
        this.systemProperties = systemProperties;
        this.environment = environment;
        this.out = out;
        this.err = err;
    }

    /**
     * The arguments after {@code serve} where {@code serve} is the first of {@code arguments}, and none where it is
     * not: any other first argument, and none at all, is left to Spring.
     */
    public static Optional<List<String>> handedOn(String... arguments) {
        if (arguments.length == 0 || !NAME.equals(arguments[0])) {
            return Optional.empty();
        }
        return Optional.of(List.copyOf(Arrays.asList(arguments).subList(1, arguments.length)));
    }

    /**
     * Serves the website until the process ends, and returns only on a refusal or an interruption: exit 1 where it
     * refuses, 2 where its arguments cannot be read, 0 where it was interrupted.
     */
    public int call(String... arguments) {
        CommandLine.ParseResult parsed;
        try {
            parsed = new CommandLine(new Root()).setUnmatchedArgumentsAllowed(true).parseArgs(withServe(arguments));
        } catch (CommandLine.ParameterException unreadable) {
            err.println(unreadable.getMessage());
            return CommandLine.ExitCode.USAGE;
        }
        Path workingDirectory = workingDirectory(arguments);
        String disagreement = ((VesperaCommand.Serve) parsed.subcommand().commandSpec().userObject())
                .databaseDirectory()
                .disagreement(workingDirectory);
        if (disagreement != null) {
            err.println(disagreement);
            return CommandLine.ExitCode.SOFTWARE;
        }
        Path folder = workingDirectory.resolve(WEBSITE_FOLDER);
        if (!Files.isDirectory(workingDirectory)) {
            err.println("vespera serve has nothing to serve: " + workingDirectory + " is no folder that is there.");
            return CommandLine.ExitCode.SOFTWARE;
        }
        if (!Files.isRegularFile(folder.resolve(WEBSITE_LIST))) {
            err.println("vespera serve has nothing to serve: " + folder
                    + " holds no published website. Run vespera publish --to website first.");
            return CommandLine.ExitCode.SOFTWARE;
        }
        int port;
        try {
            port = port(arguments);
        } catch (IllegalArgumentException notAPort) {
            err.println("vespera serve could not listen on port " + notAPort.getMessage() + ": it is no port number from "
                    + LOWEST_PORT + " to " + HIGHEST_PORT + ". Name another in " + PORT_PROPERTY + ".");
            return CommandLine.ExitCode.SOFTWARE;
        }
        try (WebsiteServer server = WebsiteServer.start(folder, port)) {
            out.println("serving " + folder + " at http://127.0.0.1:" + port + "/ until this process ends");
            new CountDownLatch(1).await();
            return CommandLine.ExitCode.OK;
        } catch (BindException inUse) {
            err.println("vespera serve could not listen on port " + port + ": it is in use. Name another in "
                    + PORT_PROPERTY + ".");
            return CommandLine.ExitCode.SOFTWARE;
        } catch (IOException failed) {
            err.println("vespera serve could not listen on port " + port + ": " + failed.getClass().getSimpleName()
                    + ". Name another in " + PORT_PROPERTY + ".");
            return CommandLine.ExitCode.SOFTWARE;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return CommandLine.ExitCode.OK;
        }
    }

    /**
     * The working directory: {@value #WORKING_DIR_PROPERTY} where any source sets it, else {@code db-dir}, else {@code
     * .vespera} under {@code user.dir}.
     */
    Path workingDirectory(String... arguments) {
        Function<String, Optional<String>> read = name -> valueOf(name, arguments);
        return read.apply(WORKING_DIR_PROPERTY)
                .or(() -> read.apply(DB_DIR_PROPERTY))
                .map(Path::of)
                .orElseGet(() -> Path.of(systemProperties.getProperty("user.dir", "."), DEFAULT_FOLDER));
    }

    /**
     * The port: {@value #PORT_PROPERTY} where any source sets it, else {@value #DEFAULT_PORT}.
     *
     * @throws IllegalArgumentException with the value as its message, where it is no port number
     */
    int port(String... arguments) {
        Optional<String> named = valueOf(PORT_PROPERTY, arguments);
        if (named.isEmpty()) {
            return DEFAULT_PORT;
        }
        try {
            int port = Integer.parseInt(named.get().strip());
            if (port >= LOWEST_PORT && port <= HIGHEST_PORT) {
                return port;
            }
        } catch (NumberFormatException notANumber) {
            // refused below, as any other value outside the range is
        }
        throw new IllegalArgumentException(named.get());
    }

    /** The value of {@code name} from the arguments, then the system properties, then the environment; blank is unset. */
    private Optional<String> valueOf(String name, String[] arguments) {
        String prefix = "--" + name + "=";
        String fromArguments = null;
        for (String argument : arguments) {
            if (argument.startsWith(prefix)) {
                fromArguments = argument.substring(prefix.length());
            }
        }
        return nonBlank(fromArguments)
                .or(() -> nonBlank(systemProperties.getProperty(name)))
                .or(() -> nonBlank(fromEnvironment(name)));
    }

    /** The environment's name for {@code name} as Spring relaxes it: {@code .} and {@code -} as {@code _}, upper-case. */
    private String fromEnvironment(String name) {
        for (String candidate : List.of(
                name,
                name.replace('.', '_'),
                name.replace('-', '_'),
                name.replace('.', '_').replace('-', '_'))) {
            for (String spelling : List.of(candidate, candidate.toUpperCase(Locale.ROOT))) {
                String value = environment.get(spelling);
                if (value != null) {
                    return value;
                }
            }
        }
        return null;
    }

    private static Optional<String> nonBlank(String value) {
        return value == null || value.isBlank() ? Optional.empty() : Optional.of(value.strip());
    }

    private static String[] withServe(String[] arguments) {
        String[] line = new String[arguments.length + 1];
        line[0] = NAME;
        System.arraycopy(arguments, 0, line, 1, arguments.length);
        return line;
    }

    /** {@code vespera}, holding the one command this parses, so that the refusal names it as the operator typed it. */
    @Command(name = "vespera", subcommands = VesperaCommand.Serve.class)
    private static final class Root {}
}
